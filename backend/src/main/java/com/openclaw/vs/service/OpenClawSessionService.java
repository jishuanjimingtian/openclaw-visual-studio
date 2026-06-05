package com.openclaw.vs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openclaw.vs.dto.ChatMessageDto;
import com.openclaw.vs.dto.OpenClawSessionDto;
import com.openclaw.vs.dto.OpenClawSessionsResult;
import com.openclaw.vs.gateway.GatewayWebSocketClient;
import com.openclaw.vs.gateway.OpenClawGatewayConfigReader;
import com.openclaw.vs.util.ChatHistoryOrder;
import com.openclaw.vs.util.ChatMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenClawSessionService {

    /** 与 OpenClaw 默认 chat.history 预算接近，过大易导致 Gateway 处理过久 */
    public static final int DEFAULT_HISTORY_MAX_CHARS = 12_000;
    /** 等待回复轮询：只拉最近几条，显著减轻 Gateway 负载 */
    public static final int POLL_HISTORY_LIMIT = 24;
    public static final int POLL_HISTORY_MAX_CHARS = 4_000;

    private static final long RPC_TIMEOUT_MS = 20_000;
    private static final long SESSIONS_LIST_TIMEOUT_MS = 12_000;
    private static final long SESSIONS_LIST_CACHE_TTL_MS = 5_000;
    private static final long CHAT_HISTORY_TIMEOUT_MS = 45_000;
    private static final long CHAT_HISTORY_LIGHT_TIMEOUT_MS = 12_000;
    private static final long CHAT_HISTORY_PREVIEW_TIMEOUT_MS = 8_000;
    private static final int CHAT_HISTORY_MAX_ATTEMPTS = 2;
    private static final long HISTORY_CACHE_TTL_MS = 350;
    private static final long HISTORY_PREVIEW_CACHE_TTL_MS = 10_000;

    private final GatewayWebSocketClient gatewayClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, CompletableFuture<List<ChatMessageDto>>> inflightHistory =
        new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedHistory> historyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<OpenClawSessionsResult>> inflightSessions =
        new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedSessions> sessionsCache = new ConcurrentHashMap<>();

    private record CachedHistory(long fetchedAtMs, List<ChatMessageDto> messages) {}

    /** Gateway 侧用量汇总（sessions.list + 少量 chat.history） */
    public record SessionUsageSummary(
        long tokensToday,
        long tokensTotal,
        long messagesTotal,
        long messagesToday
    ) {}

    private static final int DASHBOARD_HISTORY_SESSION_LIMIT = 8;
    private record CachedSessions(long fetchedAtMs, OpenClawSessionsResult result) {}

    /**
     * 从 Gateway 汇总 Token 与消息数。OpenClaw 对话不落本地 messages 表，仪表盘需走此路径。
     */
    public SessionUsageSummary collectUsageStats() {
        OpenClawSessionsResult result = listSessions(100, null);
        if (!result.isGatewayConnected() || result.getSessions().isEmpty()) {
            return null;
        }

        long startOfDayMs = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli();

        long tokensToday = 0;
        long tokensTotal = 0;
        long messagesTotal = 0;
        long messagesToday = 0;
        int historyFetches = 0;

        for (OpenClawSessionDto session : result.getSessions()) {
            Integer sessionTokens = session.getTotalTokens();
            if (sessionTokens != null && sessionTokens > 0) {
                tokensTotal += sessionTokens;
                if (session.getUpdatedAt() != null && session.getUpdatedAt() >= startOfDayMs) {
                    tokensToday += sessionTokens;
                }
            }

            if (historyFetches >= DASHBOARD_HISTORY_SESSION_LIMIT) {
                continue;
            }
            String key = session.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            List<ChatMessageDto> history = getChatHistory(
                key, POLL_HISTORY_LIMIT, POLL_HISTORY_MAX_CHARS, true);
            historyFetches++;
            for (ChatMessageDto msg : history) {
                messagesTotal++;
                Long ts = msg.getTimestamp();
                if (ts != null && ts >= startOfDayMs) {
                    messagesToday++;
                }
            }
        }

        return new SessionUsageSummary(tokensToday, tokensTotal, messagesTotal, messagesToday);
    }

    public OpenClawSessionsResult listSessions(Integer limit, String search) {
        return listSessions(limit, search, true);
    }

    public OpenClawSessionsResult listSessions(Integer limit, String search, boolean includePreview) {
        int effectiveLimit = limit != null ? limit : 50;
        String searchKey = search != null ? search.trim() : "";
        String cacheKey = effectiveLimit + "|" + searchKey + "|" + includePreview;

        CachedSessions cached = sessionsCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAtMs() < SESSIONS_LIST_CACHE_TTL_MS) {
            return cached.result();
        }

        CompletableFuture<OpenClawSessionsResult> shared = inflightSessions.computeIfAbsent(
            cacheKey,
            key -> CompletableFuture.supplyAsync(
                () -> fetchSessionsOnce(effectiveLimit, searchKey, includePreview)
            )
        );

        try {
            OpenClawSessionsResult result = shared.get(SESSIONS_LIST_TIMEOUT_MS + 2_000, TimeUnit.MILLISECONDS);
            sessionsCache.put(cacheKey, new CachedSessions(System.currentTimeMillis(), result));
            return result;
        } catch (Exception e) {
            log.warn("sessions.list failed: {}", e.getMessage());
            int gatewayPort = OpenClawGatewayConfigReader.resolveEffectiveGatewayPort(
                OpenClawGatewayConfigReader.DEFAULT_GATEWAY_PORT);
            return OpenClawSessionsResult.builder()
                .gatewayConnected(false)
                .gatewayPort(gatewayPort)
                .gatewayWsUrl(OpenClawGatewayConfigReader.toWebSocketUrl(gatewayPort))
                .connectionHint(buildConnectionHint(gatewayPort))
                .sessions(List.of())
                .build();
        } finally {
            inflightSessions.remove(cacheKey, shared);
        }
    }

    private OpenClawSessionsResult fetchSessionsOnce(int limit, String search, boolean includePreview) {
        int gatewayPort = OpenClawGatewayConfigReader.resolveEffectiveGatewayPort(
            OpenClawGatewayConfigReader.DEFAULT_GATEWAY_PORT);
        String gatewayWsUrl = OpenClawGatewayConfigReader.toWebSocketUrl(gatewayPort);
        if (!gatewayClient.isConnected()) {
            return OpenClawSessionsResult.builder()
                .gatewayConnected(false)
                .gatewayPort(gatewayPort)
                .gatewayWsUrl(gatewayWsUrl)
                .connectionHint(buildConnectionHint(gatewayPort))
                .sessions(List.of())
                .build();
        }

        try {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("limit", limit);
            if (includePreview) {
                params.put("includeDerivedTitles", true);
                params.put("includeLastMessage", true);
            }
            if (!search.isBlank()) {
                params.put("search", search);
            }

            JsonNode payload = gatewayClient.request("sessions.list", params, SESSIONS_LIST_TIMEOUT_MS);
            List<OpenClawSessionDto> sessions = new ArrayList<>();
            JsonNode sessionsNode = payload.path("sessions");
            if (sessionsNode.isArray()) {
                for (JsonNode row : sessionsNode) {
                    sessions.add(mapSession(row));
                }
            }

            JsonNode defaults = payload.path("defaults");
            String defaultModel = null;
            if (defaults.has("model") && defaults.has("modelProvider")) {
                defaultModel = defaults.path("modelProvider").asText() + "/" + defaults.path("model").asText();
            } else if (defaults.has("model")) {
                defaultModel = defaults.path("model").asText();
            }

            return OpenClawSessionsResult.builder()
                .gatewayConnected(true)
                .gatewayPort(gatewayPort)
                .gatewayWsUrl(gatewayWsUrl)
                .defaultModel(defaultModel)
                .sessions(sessions)
                .build();
        } catch (Exception e) {
            log.warn("Failed to list OpenClaw sessions: {}", e.getMessage());
            return OpenClawSessionsResult.builder()
                .gatewayConnected(false)
                .gatewayPort(gatewayPort)
                .gatewayWsUrl(gatewayWsUrl)
                .connectionHint(buildConnectionHint(gatewayPort))
                .sessions(List.of())
                .build();
        }
    }

    public void invalidateSessionsCache() {
        sessionsCache.clear();
    }

    private static String buildConnectionHint(int gatewayPort) {
        int browserPort = OpenClawGatewayConfigReader.deriveBrowserControlPort(
            OpenClawGatewayConfigReader.DEFAULT_GATEWAY_PORT);
        return "请确认 Gateway 已启动且后端 WebSocket 握手成功（目标 "
            + gatewayPort
            + "，ws://127.0.0.1:"
            + gatewayPort
            + "）。若 openclaw.json 中 gateway.port 误填为 "
            + browserPort
            + "（浏览器控制端口），请改为 "
            + OpenClawGatewayConfigReader.DEFAULT_GATEWAY_PORT
            + " 或实际 Gateway 端口。";
    }

    public List<ChatMessageDto> getChatHistory(String sessionKey, int limit) {
        return getChatHistory(sessionKey, limit, DEFAULT_HISTORY_MAX_CHARS, false);
    }

    public List<ChatMessageDto> getChatHistory(String sessionKey, int limit, int maxChars) {
        return getChatHistory(sessionKey, limit, maxChars, false);
    }

    /**
     * @param light true 时使用更小预算与更短超时，适合会话管理页预览
     */
    public List<ChatMessageDto> getChatHistory(String sessionKey, int limit, int maxChars, boolean light) {
        if (!gatewayClient.isConnected()) {
            log.warn("chat.history skipped: Gateway WebSocket not connected");
            return List.of();
        }

        int effectiveMaxChars = Math.min(
            Math.max(maxChars, 1_000),
            light ? 12_000 : 50_000
        );
        int effectiveLimit = Math.min(Math.max(limit, 1), light ? 30 : 200);
        long timeoutMs = light ? CHAT_HISTORY_PREVIEW_TIMEOUT_MS : CHAT_HISTORY_TIMEOUT_MS;
        String cacheKey = sessionKey + "|" + effectiveLimit + "|" + effectiveMaxChars + "|" + light;
        long cacheTtl = light ? HISTORY_PREVIEW_CACHE_TTL_MS : HISTORY_CACHE_TTL_MS;

        CachedHistory cached = historyCache.get(cacheKey);
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtMs() < cacheTtl) {
            return cached.messages();
        }

        CompletableFuture<List<ChatMessageDto>> shared = inflightHistory.computeIfAbsent(
            cacheKey,
            key -> CompletableFuture.supplyAsync(
                () -> fetchChatHistoryOnce(sessionKey, effectiveLimit, effectiveMaxChars, timeoutMs, light)
            )
        );

        try {
            List<ChatMessageDto> result = shared.get(timeoutMs + 2_000, TimeUnit.MILLISECONDS);
            if (!result.isEmpty()) {
                historyCache.put(cacheKey, new CachedHistory(System.currentTimeMillis(), result));
            }
            return result;
        } catch (Exception e) {
            log.warn("chat.history failed for sessionKey={}: {}", sessionKey, e.getMessage());
            return List.of();
        } finally {
            inflightHistory.remove(cacheKey, shared);
        }
    }

    private List<ChatMessageDto> fetchChatHistoryOnce(
        String sessionKey,
        int effectiveLimit,
        int effectiveMaxChars,
        long timeoutMs,
        boolean light
    ) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("sessionKey", sessionKey);
        params.put("limit", effectiveLimit);
        params.put("maxChars", effectiveMaxChars);

        Exception lastError = null;
        int maxAttempts = light ? 1 : CHAT_HISTORY_MAX_ATTEMPTS;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                JsonNode payload = gatewayClient.request("chat.history", params, timeoutMs);
                return parseHistoryMessages(payload);
            } catch (TimeoutException e) {
                lastError = e;
                if (!light) {
                    log.warn("chat.history timeout (attempt {}/{}): sessionKey={} limit={} maxChars={}",
                        attempt, CHAT_HISTORY_MAX_ATTEMPTS, sessionKey, effectiveLimit, effectiveMaxChars);
                }
            } catch (Exception e) {
                lastError = e;
                if (isRetryableHistoryError(e) && attempt < maxAttempts) {
                    log.debug("chat.history retryable (attempt {}/{}): {}", attempt, CHAT_HISTORY_MAX_ATTEMPTS,
                        e.getMessage());
                } else {
                    if (!light) {
                        log.warn("chat.history failed: {}", e.getMessage());
                    }
                    return List.of();
                }
            }

            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(light ? 200L : 400L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (!light) {
            log.warn("chat.history giving up for sessionKey={}: {}", sessionKey,
                lastError != null ? lastError.getMessage() : "unknown");
        }
        return List.of();
    }

    public void invalidateHistoryCache(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            historyCache.clear();
            return;
        }
        String prefix = sessionKey + "|";
        historyCache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private List<ChatMessageDto> parseHistoryMessages(JsonNode payload) {
        return ChatHistoryOrder.parseVisibleInGatewayOrder(payload);
    }

    private static boolean isRetryableHistoryError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return msg.contains("未连接")
            || msg.contains("not connected")
            || msg.contains("断开")
            || msg.contains("unavailable")
            || msg.contains("starting");
    }

    private OpenClawSessionDto mapSession(JsonNode row) {
        String title = firstNonBlank(
            row.path("derivedTitle").asText(null),
            row.path("label").asText(null),
            row.path("displayName").asText(null),
            row.path("key").asText("未命名会话")
        );

        String model = row.path("model").asText(null);
        String modelProvider = row.path("modelProvider").asText(null);
        String modelDisplay = model;
        if (modelProvider != null && !modelProvider.isBlank() && model != null) {
            modelDisplay = modelProvider + "/" + model;
        }

        Long updatedAt = row.has("updatedAt") && !row.path("updatedAt").isNull()
            ? row.path("updatedAt").asLong() : null;

        return OpenClawSessionDto.builder()
            .key(row.path("key").asText())
            .title(title)
            .model(modelDisplay)
            .modelProvider(modelProvider)
            .lastMessagePreview(ChatMessageMapper.sanitizePreview(
                row.path("lastMessagePreview").asText(null)))
            .updatedAt(updatedAt)
            .totalTokens(row.has("totalTokens") ? row.path("totalTokens").asInt() : null)
            .hasActiveRun(row.path("hasActiveRun").asBoolean(false))
            .kind(row.path("kind").asText(null))
            .channel(row.path("channel").asText(null))
            .sessionId(row.path("sessionId").asText(null))
            .build();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
