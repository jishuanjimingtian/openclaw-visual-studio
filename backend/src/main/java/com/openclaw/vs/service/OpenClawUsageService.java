package com.openclaw.vs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openclaw.vs.dto.OpenClawSessionUsageDto;
import com.openclaw.vs.gateway.GatewayWebSocketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 从 OpenClaw Gateway 读取 Token / 费用统计（usage.cost、sessions.usage）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenClawUsageService {

    private static final long RPC_TIMEOUT_MS = 30_000;
    private static final long ANALYTICS_RPC_TIMEOUT_MS = 8_000;
    private static final long COST_CACHE_TTL_MS = 45_000;
    private static final int SESSIONS_USAGE_LIMIT = 500;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final GatewayWebSocketClient gatewayClient;
    private final OpenClawSessionService sessionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentHashMap<String, CachedCost> costCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedCost> sessionsUsageCache = new ConcurrentHashMap<>();
    /** 避免 analytics 页面并发触发多次 usage.cost RPC */
    private final ConcurrentHashMap<Integer, CompletableFuture<Optional<JsonNode>>> inflightCostRequests =
        new ConcurrentHashMap<>();
    /** 避免 analytics 页面并发触发多次 sessions.usage RPC */
    private final ConcurrentHashMap<String, CompletableFuture<Optional<JsonNode>>> inflightSessionsUsageRequests =
        new ConcurrentHashMap<>();

    private record CachedCost(long fetchedAtMs, JsonNode payload) {}

    public boolean isGatewayConnected() {
        return gatewayClient.isConnected();
    }

    /** 全量 Token（Gateway 侧，需 Gateway 支持 usage.cost range=all） */
    public long getAllTimeGatewayTokens() {
        if (!gatewayClient.isConnected()) {
            return 0L;
        }
        try {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("range", "all");
            JsonNode payload = gatewayClient.request("usage.cost", params, RPC_TIMEOUT_MS);
            return readTotalsTokens(payload);
        } catch (Exception e) {
            log.debug("usage.cost range=all unavailable: {}", e.getMessage());
            return getDailyTokenMap(90).values().stream().mapToLong(Long::longValue).sum();
        }
    }

    /** 今日 Token（Gateway 侧，仅统计 daily 中当天一行，不用 totals 累计） */
    public long getTodayTokens() {
        LocalDate today = LocalDate.now();
        String todayStr = today.format(ISO_DATE);
        long fromDailyMap = getDailyTokenMap(14).getOrDefault(todayStr, 0L);
        if (fromDailyMap > 0) {
            return fromDailyMap;
        }
        return getCostSummary(1)
            .map(payload -> readTodayTokensFromCostPayload(payload, today))
            .orElse(0L);
    }

    /** 最近 N 天每日 Token：date(yyyy-MM-dd) -> tokens */
    public Map<String, Long> getDailyTokenMap(int days) {
        int effectiveDays = Math.min(Math.max(days, 1), 90);
        Optional<JsonNode> payload = getCostSummary(effectiveDays);
        if (payload.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Long> map = new HashMap<>();
        JsonNode daily = payload.get().path("daily");
        if (daily.isArray()) {
            for (JsonNode row : daily) {
                String date = row.path("date").asText(null);
                if (date == null || date.isBlank()) {
                    continue;
                }
                long tokens = row.path("tokens").asLong(0L);
                if (tokens == 0L) {
                    tokens = row.path("totalTokens").asLong(0L);
                }
                map.merge(date, tokens, Long::sum);
            }
        }
        return map;
    }

    /** Gateway 侧 OpenClaw 会话数（sessions.list） */
    public long getGatewaySessionCount() {
        if (!gatewayClient.isConnected()) {
            return 0L;
        }
        try {
            var result = sessionService.listSessions(SESSIONS_USAGE_LIMIT, null, false);
            if (!result.getSessions().isEmpty()) {
                return result.getSessions().size();
            }
            return 0L;
        } catch (Exception e) {
            log.debug("sessions.list count unavailable: {}", e.getMessage());
            return countSessionsFromUsagePayload("all");
        }
    }

    /** 全量消息数（Gateway 侧，OpenClaw 对话不落本地 messages 表） */
    public long getAllTimeGatewayMessages() {
        Optional<JsonNode> allPayload = getSessionsUsageSummary("all");
        long fromAll = allPayload.map(this::readAggregateMessageTotal).orElse(0L);
        if (fromAll > 0) {
            return fromAll;
        }
        long fromDailySum = sumDailyMessages(getDailyMessageMap(90));
        if (fromDailySum > 0) {
            return fromDailySum;
        }
        return allPayload.map(this::countSessionsUsageMessages).orElse(0L);
    }

    /** 今日消息数（Gateway 侧） */
    public long getTodayGatewayMessages() {
        String today = LocalDate.now().format(ISO_DATE);
        long fromToday = getDailyMessageMap(1).getOrDefault(today, 0L);
        if (fromToday > 0) {
            return fromToday;
        }
        return getDailyMessageMap(14).getOrDefault(today, 0L);
    }

    /** 最近 N 天每日消息：date(yyyy-MM-dd) -> count */
    public Map<String, Long> getDailyMessageMap(int days) {
        int effectiveDays = Math.min(Math.max(days, 1), 90);
        Optional<JsonNode> payload = getSessionsUsageSummary("days:" + effectiveDays);
        if (payload.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Long> map = new HashMap<>();
        JsonNode daily = payload.get().path("aggregates").path("daily");
        if (daily.isArray()) {
            for (JsonNode row : daily) {
                String date = row.path("date").asText(null);
                if (date == null || date.isBlank()) {
                    continue;
                }
                long count = readDailyMessageCount(row);
                if (count > 0) {
                    map.merge(date, count, Long::sum);
                }
            }
        }
        return map;
    }

    public OpenClawSessionUsageDto getSessionUsage(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return emptyUsage(sessionKey);
        }
        String key = sessionKey.trim();
        if (!gatewayClient.isConnected()) {
            return fallbackFromSessionList(key);
        }

        try {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("key", key);
            params.put("limit", 1);
            params.put("range", "all");

            JsonNode payload = gatewayClient.request("sessions.usage", params, RPC_TIMEOUT_MS);
            JsonNode sessions = payload.path("sessions");
            if (sessions.isArray() && !sessions.isEmpty()) {
                return mapSessionUsage(key, sessions.get(0), true);
            }
        } catch (Exception e) {
            log.debug("sessions.usage unavailable for {}: {}", key, e.getMessage());
        }

        return fallbackFromSessionList(key);
    }

    private OpenClawSessionUsageDto fallbackFromSessionList(String sessionKey) {
        try {
            var result = sessionService.listSessions(200, null);
            if (!result.isGatewayConnected()) {
                return emptyUsage(sessionKey);
            }
            return result.getSessions().stream()
                .filter(s -> sessionKey.equals(s.getKey()))
                .findFirst()
                .map(s -> OpenClawSessionUsageDto.builder()
                    .sessionKey(sessionKey)
                    .totalTokens(s.getTotalTokens() != null ? s.getTotalTokens().longValue() : 0L)
                    .inputTokens(0L)
                    .outputTokens(0L)
                    .fromGateway(false)
                    .build())
                .orElse(emptyUsage(sessionKey));
        } catch (Exception e) {
            log.debug("session list fallback failed for {}: {}", sessionKey, e.getMessage());
            return emptyUsage(sessionKey);
        }
    }

    private OpenClawSessionUsageDto mapSessionUsage(String sessionKey, JsonNode session, boolean fromGateway) {
        JsonNode totals = session.path("totals");
        if (totals.isMissingNode() || totals.isNull()) {
            totals = session.path("usage").path("totals");
        }
        Double cost = totals.has("totalCost") && !totals.path("totalCost").isNull()
            ? totals.path("totalCost").asDouble()
            : null;
        return OpenClawSessionUsageDto.builder()
            .sessionKey(sessionKey)
            .totalTokens(totals.path("totalTokens").asLong(0L))
            .inputTokens(totals.path("input").asLong(0L))
            .outputTokens(totals.path("output").asLong(0L))
            .totalCost(cost)
            .fromGateway(fromGateway)
            .build();
    }

    private OpenClawSessionUsageDto emptyUsage(String sessionKey) {
        return OpenClawSessionUsageDto.builder()
            .sessionKey(sessionKey)
            .totalTokens(0L)
            .inputTokens(0L)
            .outputTokens(0L)
            .fromGateway(false)
            .build();
    }

    private Optional<JsonNode> getCostSummary(int days) {
        if (!gatewayClient.isConnected()) {
            return Optional.empty();
        }
        int effectiveDays = Math.min(Math.max(days, 1), 90);
        Optional<JsonNode> cached = findCachedCostSummary(effectiveDays);
        if (cached.isPresent()) {
            return cached;
        }

        CompletableFuture<Optional<JsonNode>> existing = inflightCostRequests.get(effectiveDays);
        if (existing != null) {
            try {
                return existing.get(ANALYTICS_RPC_TIMEOUT_MS + 2_000, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.debug("await inflight usage.cost failed: {}", e.getMessage());
                return Optional.empty();
            }
        }

        CompletableFuture<Optional<JsonNode>> future = inflightCostRequests.computeIfAbsent(
            effectiveDays,
            d -> CompletableFuture.supplyAsync(() -> fetchCostSummary(d))
        );
        try {
            return future.get(ANALYTICS_RPC_TIMEOUT_MS + 2_000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.debug("usage.cost fetch failed (days={}): {}", effectiveDays, e.getMessage());
            return Optional.empty();
        } finally {
            inflightCostRequests.remove(effectiveDays, future);
        }
    }

    private Optional<JsonNode> fetchCostSummary(int days) {
        long now = System.currentTimeMillis();
        try {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("days", days);
            JsonNode payload = gatewayClient.request("usage.cost", params, ANALYTICS_RPC_TIMEOUT_MS);
            costCache.put("days:" + days, new CachedCost(now, payload));
            return Optional.of(payload);
        } catch (Exception e) {
            log.debug("usage.cost unavailable (days={}): {}", days, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<JsonNode> findCachedCostSummary(int daysNeeded) {
        long now = System.currentTimeMillis();
        Optional<JsonNode> best = Optional.empty();
        int bestDays = -1;
        for (Map.Entry<String, CachedCost> entry : costCache.entrySet()) {
            if (!entry.getKey().startsWith("days:")) {
                continue;
            }
            CachedCost cached = entry.getValue();
            if (now - cached.fetchedAtMs() >= COST_CACHE_TTL_MS) {
                continue;
            }
            try {
                int cachedDays = Integer.parseInt(entry.getKey().substring("days:".length()));
                if (cachedDays >= daysNeeded && cachedDays > bestDays) {
                    bestDays = cachedDays;
                    best = Optional.of(cached.payload());
                }
            } catch (NumberFormatException ignored) {
                // skip malformed cache key
            }
        }
        return best;
    }

    /**
     * 从 usage.cost 的 daily 数组读取指定日期的 Token（今日统计必须用此路径）。
     */
    long readTodayTokensFromCostPayload(JsonNode payload, LocalDate day) {
        if (payload == null || payload.isMissingNode()) {
            return 0L;
        }
        String dayStr = day.format(ISO_DATE);
        JsonNode daily = payload.path("daily");
        if (daily.isArray()) {
            for (JsonNode row : daily) {
                if (!dayStr.equals(row.path("date").asText(null))) {
                    continue;
                }
                long tokens = row.path("tokens").asLong(0L);
                if (tokens > 0) {
                    return tokens;
                }
                return row.path("totalTokens").asLong(0L);
            }
        }
        return 0L;
    }

    /** 全量/区间累计 Token（usage.cost totals），勿用于「今日」 */
    private long readTotalsTokens(JsonNode payload) {
        JsonNode totals = payload.path("totals");
        if (totals.isMissingNode()) {
            return readTodayTokensFromCostPayload(payload, LocalDate.now());
        }
        return totals.path("totalTokens").asLong(0L);
    }

    private Optional<JsonNode> getSessionsUsageSummary(String cacheKey) {
        if (!gatewayClient.isConnected()) {
            return Optional.empty();
        }

        Optional<JsonNode> cached = findCachedSessionsUsage(cacheKey);
        if (cached.isPresent()) {
            return cached;
        }

        CompletableFuture<Optional<JsonNode>> existing = inflightSessionsUsageRequests.get(cacheKey);
        if (existing != null) {
            try {
                return existing.get(ANALYTICS_RPC_TIMEOUT_MS + 2_000, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.debug("await inflight sessions.usage failed: {}", e.getMessage());
                return Optional.empty();
            }
        }

        CompletableFuture<Optional<JsonNode>> future = inflightSessionsUsageRequests.computeIfAbsent(
            cacheKey,
            key -> CompletableFuture.supplyAsync(() -> fetchSessionsUsageSummary(key))
        );
        try {
            return future.get(ANALYTICS_RPC_TIMEOUT_MS + 2_000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.debug("sessions.usage fetch failed (key={}): {}", cacheKey, e.getMessage());
            return Optional.empty();
        } finally {
            inflightSessionsUsageRequests.remove(cacheKey, future);
        }
    }

    private Optional<JsonNode> fetchSessionsUsageSummary(String cacheKey) {
        long now = System.currentTimeMillis();
        try {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("limit", SESSIONS_USAGE_LIMIT);
            if ("all".equals(cacheKey)) {
                params.put("range", "all");
            } else if (cacheKey.startsWith("days:")) {
                int days = Integer.parseInt(cacheKey.substring("days:".length()));
                LocalDate end = LocalDate.now();
                LocalDate start = end.minusDays(Math.max(days, 1) - 1L);
                params.put("startDate", start.format(ISO_DATE));
                params.put("endDate", end.format(ISO_DATE));
            } else {
                return Optional.empty();
            }
            JsonNode payload = gatewayClient.request("sessions.usage", params, ANALYTICS_RPC_TIMEOUT_MS);
            sessionsUsageCache.put(cacheKey, new CachedCost(now, payload));
            return Optional.of(payload);
        } catch (Exception e) {
            log.debug("sessions.usage unavailable (key={}): {}", cacheKey, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<JsonNode> findCachedSessionsUsage(String cacheKey) {
        long now = System.currentTimeMillis();
        CachedCost exact = sessionsUsageCache.get(cacheKey);
        if (exact != null && now - exact.fetchedAtMs() < COST_CACHE_TTL_MS) {
            return Optional.of(exact.payload());
        }

        if (!cacheKey.startsWith("days:")) {
            return Optional.empty();
        }

        int daysNeeded;
        try {
            daysNeeded = Integer.parseInt(cacheKey.substring("days:".length()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        Optional<JsonNode> best = Optional.empty();
        int bestDays = -1;
        for (Map.Entry<String, CachedCost> entry : sessionsUsageCache.entrySet()) {
            if (!entry.getKey().startsWith("days:")) {
                continue;
            }
            CachedCost cached = entry.getValue();
            if (now - cached.fetchedAtMs() >= COST_CACHE_TTL_MS) {
                continue;
            }
            try {
                int cachedDays = Integer.parseInt(entry.getKey().substring("days:".length()));
                if (cachedDays >= daysNeeded && cachedDays > bestDays) {
                    bestDays = cachedDays;
                    best = Optional.of(cached.payload());
                }
            } catch (NumberFormatException ignored) {
                // skip malformed cache key
            }
        }
        return best;
    }

    static long readDailyMessageCount(JsonNode row) {
        long count = row.path("messages").asLong(0L);
        if (count > 0) {
            return count;
        }
        count = row.path("messageCount").asLong(0L);
        if (count > 0) {
            return count;
        }
        return row.path("totals").path("messages").asLong(0L);
    }

    long readAggregateMessageTotal(JsonNode payload) {
        JsonNode aggregates = payload.path("aggregates");
        long total = aggregates.path("messages").path("total").asLong(0L);
        if (total > 0) {
            return total;
        }
        if (aggregates.path("messages").isNumber()) {
            total = aggregates.path("messages").asLong(0L);
            if (total > 0) {
                return total;
            }
        }
        total = aggregates.path("messagesTotal").asLong(0L);
        if (total > 0) {
            return total;
        }
        total = aggregates.path("totals").path("messages").asLong(0L);
        if (total > 0) {
            return total;
        }
        total = payload.path("totals").path("messages").asLong(0L);
        if (total > 0) {
            return total;
        }
        total = sumDailyMessagesFromPayload(payload);
        if (total > 0) {
            return total;
        }
        return countSessionsUsageMessages(payload);
    }

    private long sumDailyMessagesFromPayload(JsonNode payload) {
        JsonNode daily = payload.path("aggregates").path("daily");
        if (!daily.isArray()) {
            daily = payload.path("daily");
        }
        if (!daily.isArray()) {
            return 0L;
        }
        long sum = 0L;
        for (JsonNode row : daily) {
            sum += readDailyMessageCount(row);
        }
        return sum;
    }

    private long sumDailyMessages(Map<String, Long> dailyMap) {
        return dailyMap.values().stream().mapToLong(Long::longValue).sum();
    }

    private long countSessionsUsageMessages(JsonNode payload) {
        JsonNode sessions = payload.path("sessions");
        if (!sessions.isArray()) {
            return 0L;
        }
        long sum = 0L;
        for (JsonNode session : sessions) {
            sum += session.path("messageCount").asLong(0L);
            sum += session.path("messages").asLong(0L);
            sum += session.path("usage").path("messages").asLong(0L);
            sum += session.path("totals").path("messages").asLong(0L);
        }
        return sum;
    }

    private long countSessionsFromUsagePayload(String cacheKey) {
        return getSessionsUsageSummary(cacheKey)
            .map(payload -> {
                JsonNode sessions = payload.path("sessions");
                return sessions.isArray() ? (long) sessions.size() : 0L;
            })
            .orElse(0L);
    }

    /** Gateway 按模型聚合：[0]=会话数 [1]=Token [2]=消息数 */
    public Map<String, long[]> aggregateGatewayUsageByModel() {
        if (!gatewayClient.isConnected()) {
            return Collections.emptyMap();
        }
        Map<String, long[]> result = new HashMap<>();
        Optional<JsonNode> usagePayload = getSessionsUsageSummary("all");
        if (usagePayload.isPresent()) {
            JsonNode sessions = usagePayload.get().path("sessions");
            if (sessions.isArray()) {
                for (JsonNode session : sessions) {
                    String model = resolveSessionModel(session, null);
                    if (model == null || model.isBlank()) {
                        continue;
                    }
                    long[] agg = result.computeIfAbsent(model, k -> new long[3]);
                    agg[0] += 1;
                    agg[1] += readSessionTotalTokens(session);
                    agg[2] += readSessionMessageCount(session);
                }
            }
        }
        if (result.isEmpty()) {
            try {
                var listResult = sessionService.listSessions(SESSIONS_USAGE_LIMIT, null, false);
                String defaultModel = listResult.getDefaultModel();
                for (var s : listResult.getSessions()) {
                    String model = s.getModel();
                    if (model == null || model.isBlank()) {
                        model = defaultModel;
                    }
                    if (model == null || model.isBlank()) {
                        continue;
                    }
                    long[] agg = result.computeIfAbsent(model, k -> new long[3]);
                    agg[0] += 1;
                    long tokens = s.getTotalTokens() != null ? s.getTotalTokens().longValue() : 0L;
                    agg[1] += tokens;
                }
            } catch (Exception e) {
                log.debug("listSessions model aggregate fallback failed: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * Gateway 会话用量明细（复用 sessions.usage 缓存；标题/模型优先 sessions.list）
     */
    public List<com.openclaw.vs.dto.TopSessionUsageDto> getGatewayTopSessions(int limit) {
        if (!gatewayClient.isConnected() || limit <= 0) {
            return List.of();
        }
        int effectiveLimit = Math.min(limit, SESSIONS_USAGE_LIMIT);
        Map<String, JsonNode> usageByKey = new HashMap<>();
        getSessionsUsageSummary("all").ifPresent(payload -> {
            JsonNode sessions = payload.path("sessions");
            if (sessions.isArray()) {
                for (JsonNode row : sessions) {
                    String key = firstNonBlankKey(row);
                    if (key != null) {
                        usageByKey.put(key, row);
                    }
                }
            }
        });

        List<com.openclaw.vs.dto.TopSessionUsageDto> rows = new ArrayList<>();
        try {
            var listResult = sessionService.listSessions(SESSIONS_USAGE_LIMIT, null, false);
            String defaultModel = listResult.getDefaultModel();
            for (var session : listResult.getSessions()) {
                String key = session.getKey();
                JsonNode usageRow = key != null ? usageByKey.get(key) : null;
                long tokens = session.getTotalTokens() != null ? session.getTotalTokens().longValue() : 0L;
                long messages = 0L;
                long input = 0L;
                long output = 0L;
                Double cost = null;
                if (usageRow != null) {
                    tokens = Math.max(tokens, readSessionTotalTokens(usageRow));
                    messages = readSessionMessageCount(usageRow);
                    JsonNode totals = usageRow.path("totals");
                    if (totals.isMissingNode()) {
                        totals = usageRow.path("usage").path("totals");
                    }
                    input = totals.path("input").asLong(0L);
                    output = totals.path("output").asLong(0L);
                    if (totals.has("totalCost") && !totals.path("totalCost").isNull()) {
                        cost = totals.path("totalCost").asDouble();
                    }
                }
                String id = session.getSessionId() != null && !session.getSessionId().isBlank()
                    ? session.getSessionId() : key;
                String updatedAt = null;
                if (session.getUpdatedAt() != null && session.getUpdatedAt() > 0) {
                    updatedAt = java.time.Instant.ofEpochMilli(session.getUpdatedAt())
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime()
                        .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
                rows.add(com.openclaw.vs.dto.TopSessionUsageDto.builder()
                    .id(id)
                    .title(session.getTitle() != null ? session.getTitle() : "未命名会话")
                    .model(session.getModel() != null ? session.getModel() : defaultModel)
                    .totalTokens(tokens)
                    .messageCount(messages)
                    .updatedAt(updatedAt)
                    .source("gateway")
                    .inputTokens(input)
                    .outputTokens(output)
                    .totalCost(cost)
                    .build());
            }
        } catch (Exception e) {
            log.debug("gateway top sessions failed: {}", e.getMessage());
        }
        return rows.stream()
            .sorted((a, b) -> Long.compare(b.getTotalTokens(), a.getTotalTokens()))
            .limit(effectiveLimit)
            .toList();
    }

    private static String firstNonBlankKey(JsonNode row) {
        String key = row.path("key").asText(null);
        if (key != null && !key.isBlank()) {
            return key;
        }
        return row.path("sessionKey").asText(null);
    }

    private String resolveSessionModel(JsonNode session, String defaultModel) {
        String model = session.path("model").asText(null);
        if (model == null || model.isBlank()) {
            String provider = session.path("modelProvider").asText(null);
            if (provider != null && !provider.isBlank() && defaultModel != null) {
                model = provider + "/" + defaultModel;
            } else {
                model = defaultModel;
            }
        }
        return model;
    }

    private long readSessionTotalTokens(JsonNode session) {
        JsonNode totals = session.path("totals");
        if (totals.isMissingNode()) {
            totals = session.path("usage").path("totals");
        }
        long tokens = totals.path("totalTokens").asLong(0L);
        if (tokens > 0) {
            return tokens;
        }
        return session.path("totalTokens").asLong(0L);
    }

    private long readSessionMessageCount(JsonNode session) {
        long count = session.path("messageCount").asLong(0L);
        if (count > 0) {
            return count;
        }
        count = session.path("messages").asLong(0L);
        if (count > 0) {
            return count;
        }
        return session.path("totals").path("messages").asLong(0L);
    }
}
