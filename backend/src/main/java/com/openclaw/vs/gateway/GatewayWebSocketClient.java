package com.openclaw.vs.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * OpenClaw Gateway WebSocket client (protocol v4).
 * Handshake: wait for {@code connect.challenge}, then send {@code connect} request with Ed25519 device auth.
 */
@Slf4j
@Component
public class GatewayWebSocketClient {

    private static final int DEFAULT_GATEWAY_PORT = OpenClawGatewayConfigReader.DEFAULT_GATEWAY_PORT;
    private static final String CLIENT_ID = "gateway-client";
    private static final String CLIENT_MODE = "backend";
    private static final String CLIENT_VERSION = "0.1.0";
    private static final List<String> DEFAULT_SCOPES = List.of(
        "operator.read", "operator.write", "operator.admin"
    );
    /** Reassemble multi-frame WebSocket text messages before JSON parse. */
    private static final int MAX_INCOMING_TEXT_CHARS = 4 * 1024 * 1024;
    /** High-volume Gateway broadcasts that VS backend does not consume. */
    private static final Set<String> IGNORED_GATEWAY_EVENTS = Set.of("presence", "health", "tick", "heartbeat");

    private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private static final ExecutorService CHAT_EVENT_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "gateway-chat-events");
        t.setDaemon(true);
        return t;
    });

    @Value("${app.gateway.auto-connect:true}")
    private boolean autoConnect;

    /** 留空则从 ~/.openclaw/openclaw.json 的 gateway.port 解析 */
    @Value("${app.gateway.ws-url:}")
    private String wsUrl;

    @Value("${app.gateway.reconnect-interval-ms:10000}")
    private long reconnectIntervalMs;

    @Value("${app.gateway.max-reconnect-attempts:20}")
    private int maxReconnectAttempts;

    @Value("${app.gateway.handshake-timeout-sec:30}")
    private int handshakeTimeoutSec;

    /** Gateway auth token; empty = read from ~/.openclaw/openclaw.json */
    @Value("${app.gateway.token:}")
    private String gatewayToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebSocket webSocket;
    private volatile boolean socketOpen = false;
    private volatile boolean handshakeComplete = false;
    private int reconnectCount = 0;
    private volatile boolean running = true;
    /** 停止 Gateway 时暂停自动重连，避免停止过程中反复握手 */
    private volatile boolean connectionPaused = false;
    private OpenClawDeviceIdentity deviceIdentity;
    private String resolvedGatewayToken;
    /** When set (e.g. after GatewayService.startGateway), overrides config file port. */
    private volatile Integer runtimeGatewayPort;
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<GatewayEventListener> eventListeners = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void connect() {
        if (!autoConnect) {
            log.info("Gateway WebSocket auto-connect is disabled");
            return;
        }

        Optional<OpenClawDeviceIdentity> identity = OpenClawDeviceIdentity.load(
            OpenClawGatewayConfigReader.defaultIdentityPath());
        if (identity.isEmpty()) {
            log.warn("Gateway WebSocket: device identity not found at {} — skipping auto-connect. "
                    + "Run `openclaw` once to create ~/.openclaw/identity/device.json",
                OpenClawGatewayConfigReader.defaultIdentityPath());
            return;
        }
        deviceIdentity = identity.get();

        resolvedGatewayToken = resolveGatewayToken();
        if (resolvedGatewayToken == null) {
            log.warn("Gateway WebSocket: no auth token (set app.gateway.token or gateway.auth.token in ~/.openclaw/openclaw.json)");
            return;
        }

        CompletableFuture.runAsync(this::doConnect);
    }

    private String resolveGatewayToken() {
        if (gatewayToken != null && !gatewayToken.isBlank()) {
            return gatewayToken.trim();
        }
        return OpenClawGatewayConfigReader.readGatewayToken().orElse(null);
    }

    public void setRuntimeGatewayPort(int port) {
        if (port > 0 && port < 65536) {
            this.runtimeGatewayPort = port;
        }
    }

    /** 当前或最近一次成功连接使用的 Gateway 端口（供状态 API 使用）。 */
    public Integer getRuntimeGatewayPort() {
        return runtimeGatewayPort;
    }

    private int resolveConnectPort() {
        if (runtimeGatewayPort != null) {
            return OpenClawGatewayConfigReader.correctPortIfBrowserControlMisconfigured(runtimeGatewayPort);
        }
        return OpenClawGatewayConfigReader.resolveEffectiveGatewayPort(DEFAULT_GATEWAY_PORT);
    }

    private String resolveWsUrl() {
        if (wsUrl != null && !wsUrl.isBlank()) {
            return OpenClawGatewayConfigReader.normalizeWebSocketUrl(wsUrl);
        }
        return OpenClawGatewayConfigReader.toWebSocketUrl(resolveConnectPort());
    }

    private void doConnect() {
        if (connectionPaused) {
            return;
        }
        handshakeComplete = false;
        String token = resolveGatewayToken();
        if (token == null || token.isBlank()) {
            log.warn("Gateway WebSocket: no auth token for connect");
            return;
        }
        resolvedGatewayToken = token.trim();

        String targetUrl = resolveWsUrl();
        try {
            int connectedPort = URI.create(targetUrl).getPort();
            if (connectedPort > 0) {
                runtimeGatewayPort = connectedPort;
            }
        } catch (Exception ignored) {
            // keep existing runtime port
        }
        final StringBuilder incomingText = new StringBuilder(4096);
        try {
            WebSocket.Builder wsBuilder = SHARED_HTTP_CLIENT.newWebSocketBuilder()
                .header("Authorization", "Bearer " + resolvedGatewayToken);
            CompletableFuture<WebSocket> future = wsBuilder.buildAsync(URI.create(targetUrl), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket ws) {
                        log.info("Gateway WebSocket connected to {}", targetUrl);
                        socketOpen = true;
                        reconnectCount = 0;
                        webSocket = ws;
                        incomingText.setLength(0);
                        ws.request(Long.MAX_VALUE);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        appendIncomingText(incomingText, data);
                        if (last) {
                            String message = incomingText.toString();
                            incomingText.setLength(0);
                            if (!message.isEmpty()) {
                                handleMessage(ws, message);
                            }
                        }
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                        log.warn("Gateway WebSocket closed: {} - {}", statusCode, reason);
                        incomingText.setLength(0);
                        socketOpen = false;
                        handshakeComplete = false;
                        failPendingRequests("Gateway WebSocket 已断开");
                        scheduleReconnect();
                        return null;
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        log.error("Gateway WebSocket error: {}", error.getMessage());
                        incomingText.setLength(0);
                        socketOpen = false;
                        handshakeComplete = false;
                        failPendingRequests(error.getMessage());
                        scheduleReconnect();
                    }
                });
            future.get(handshakeTimeoutSec, TimeUnit.SECONDS);
        } catch (Exception e) {
            String message = rootCauseMessage(e);
            log.warn("Failed to connect Gateway WebSocket at {}: {}", targetUrl, message);
            if (message != null && message.contains("status code 200")) {
                int port = resolveConnectPort();
                int browserPort = OpenClawGatewayConfigReader.deriveBrowserControlPort(
                    OpenClawGatewayConfigReader.DEFAULT_GATEWAY_PORT);
                log.warn(
                    "端口 {} 返回 HTTP 200 而非 WebSocket 升级，可能连到了浏览器控制服务（常见为 {}）。"
                        + "请将 ~/.openclaw/openclaw.json 的 gateway.port 设为实际 Gateway 端口（默认 {}）。",
                    port, browserPort, OpenClawGatewayConfigReader.DEFAULT_GATEWAY_PORT);
            }
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!running || connectionPaused || reconnectCount >= maxReconnectAttempts) {
            if (reconnectCount >= maxReconnectAttempts) {
                log.error("Gateway WebSocket max reconnect attempts ({}) reached, giving up", maxReconnectAttempts);
            }
            return;
        }
        reconnectCount++;
        long rawDelay = (long) (reconnectIntervalMs * Math.pow(1.5, reconnectCount - 1));
        final long delay = Math.min(rawDelay, 60000);
        log.info("Reconnecting Gateway in {}ms (attempt {}/{})", delay, reconnectCount, maxReconnectAttempts);
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            doConnect();
        });
    }

    public void send(String message) {
        if (webSocket != null && handshakeComplete) {
            webSocket.sendText(message, true);
        }
    }

    /**
     * Gateway RPC（需已完成握手），返回响应 payload。
     */
    public JsonNode request(String method, JsonNode params, long timeoutMs) throws Exception {
        if (!isConnected()) {
            throw new IllegalStateException("Gateway WebSocket 未连接，请确认 Gateway 已启动且握手成功");
        }
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        ObjectNode frame = objectMapper.createObjectNode();
        frame.put("type", "req");
        frame.put("id", requestId);
        frame.put("method", method);
        if (params != null) {
            frame.set("params", params);
        } else {
            frame.set("params", objectMapper.createObjectNode());
        }
        webSocket.sendText(objectMapper.writeValueAsString(frame), true);

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(cause.getMessage(), cause);
        } catch (TimeoutException e) {
            pendingRequests.remove(requestId);
            throw new TimeoutException("Gateway 请求超时: " + method);
        }
    }

    public boolean isConnected() {
        return socketOpen && handshakeComplete;
    }

    /** 立即停止 WebSocket 并暂停自动重连（用于 Gateway 停止）。 */
    public void pauseConnection() {
        connectionPaused = true;
        closeConnection();
    }

    /** 恢复自动重连（Gateway 启动后调用）。 */
    public void resumeConnection() {
        connectionPaused = false;
    }

    public boolean isConnectionPaused() {
        return connectionPaused;
    }

    /** 关闭当前 WebSocket，不销毁客户端线程池。 */
    public void closeConnection() {
        socketOpen = false;
        handshakeComplete = false;
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            try {
                ws.sendClose(1000, "gateway stopped");
            } catch (Exception ignored) {
                // ignore
            }
        }
        failPendingRequests("Gateway 已停止");
    }

    /** 在 Gateway 进程启动或配置变更后触发重连（使用最新 port/token）。 */
    public void reconnectIfNeeded() {
        if (!running || !autoConnect || connectionPaused) {
            return;
        }
        if (deviceIdentity == null) {
            Optional<OpenClawDeviceIdentity> identity = OpenClawDeviceIdentity.load(
                OpenClawGatewayConfigReader.defaultIdentityPath());
            if (identity.isEmpty()) {
                return;
            }
            deviceIdentity = identity.get();
        }
        resolvedGatewayToken = resolveGatewayToken();
        if (resolvedGatewayToken == null) {
            return;
        }
        if (isConnected()) {
            return;
        }
        if (webSocket != null && socketOpen) {
            try {
                webSocket.sendClose(1000, "reconnect");
            } catch (Exception ignored) {
                // ignore
            }
        }
        socketOpen = false;
        handshakeComplete = false;
        reconnectCount = 0;
        CompletableFuture.runAsync(this::doConnect);
    }

    public void addEventListener(GatewayEventListener listener) {
        if (listener != null) {
            eventListeners.add(listener);
        }
    }

    public void removeEventListener(GatewayEventListener listener) {
        eventListeners.remove(listener);
    }

    @PreDestroy
    public void disconnect() {
        running = false;
        CHAT_EVENT_EXECUTOR.shutdown();
        if (webSocket != null) {
            webSocket.sendClose(1000, "Server shutdown");
        }
    }

    private void handleMessage(WebSocket ws, String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            String type = json.path("type").asText();

            if ("event".equals(type)) {
                String event = json.path("event").asText();
                if ("connect.challenge".equals(event)) {
                    handleConnectChallenge(ws, json.path("payload"));
                } else {
                    dispatchGatewayEvent(event, json.path("payload"), json.has("seq") ? json.path("seq").asLong() : null);
                }
                return;
            }

            if ("res".equals(type)) {
                handleResponse(json);
            }
        } catch (Exception e) {
            log.warn("Failed to parse Gateway message ({} chars): {}", message.length(), e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Gateway message preview: {}", previewForLog(message, 400));
            }
        }
    }

    private static void appendIncomingText(StringBuilder buffer, CharSequence data) {
        int nextLen = buffer.length() + data.length();
        if (nextLen > MAX_INCOMING_TEXT_CHARS) {
            log.warn("Gateway WebSocket message exceeded {} chars, discarding partial frame", MAX_INCOMING_TEXT_CHARS);
            buffer.setLength(0);
            return;
        }
        buffer.append(data);
    }

    private static String previewForLog(String message, int maxChars) {
        if (message.length() <= maxChars) {
            return message;
        }
        return message.substring(0, maxChars) + "…";
    }

    private void handleConnectChallenge(WebSocket ws, JsonNode payload) {
        String nonce = payload.path("nonce").asText(null);
        if (nonce == null || nonce.isBlank()) {
            log.error("Gateway connect.challenge missing nonce");
            ws.sendClose(1008, "connect challenge missing nonce");
            return;
        }
        nonce = nonce.trim();

        try {
            String connectJson = buildConnectRequest(nonce);
            log.debug("Sending Gateway connect request (deviceId={})", deviceIdentity.getDeviceId());
            ws.sendText(connectJson, true);
        } catch (Exception e) {
            log.error("Failed to build Gateway connect request: {}", e.getMessage());
            ws.sendClose(1008, "connect failed");
        }
    }

    private String buildConnectRequest(String nonce) throws Exception {
        long signedAtMs = System.currentTimeMillis();
        String scopesCsv = String.join(",", DEFAULT_SCOPES);
        String role = "operator";
        String platform = normalizePlatform();

        String payload = deviceIdentity.buildAuthPayloadV3(
            CLIENT_ID,
            CLIENT_MODE,
            role,
            scopesCsv,
            signedAtMs,
            resolvedGatewayToken,
            nonce,
            platform,
            ""
        );
        String signature = deviceIdentity.signPayload(payload);

        ObjectNode params = objectMapper.createObjectNode();
        params.put("minProtocol", 4);
        params.put("maxProtocol", 4);

        ObjectNode client = params.putObject("client");
        client.put("id", CLIENT_ID);
        client.put("version", CLIENT_VERSION);
        client.put("platform", platform);
        client.put("mode", CLIENT_MODE);

        params.putArray("caps");
        ObjectNode auth = params.putObject("auth");
        auth.put("token", resolvedGatewayToken);
        params.put("role", role);
        var scopesNode = params.putArray("scopes");
        for (String scope : DEFAULT_SCOPES) {
            scopesNode.add(scope);
        }

        ObjectNode device = params.putObject("device");
        device.put("id", deviceIdentity.getDeviceId());
        device.put("publicKey", deviceIdentity.getPublicKeyRawBase64Url());
        device.put("signature", signature);
        device.put("signedAt", signedAtMs);
        device.put("nonce", nonce);

        ObjectNode frame = objectMapper.createObjectNode();
        frame.put("type", "req");
        frame.put("id", UUID.randomUUID().toString());
        frame.put("method", "connect");
        frame.set("params", params);

        return objectMapper.writeValueAsString(frame);
    }

    private void handleResponse(JsonNode json) {
        String responseId = json.path("id").asText(null);
        if (responseId != null) {
            CompletableFuture<JsonNode> pending = pendingRequests.remove(responseId);
            if (pending != null) {
                completePendingRequest(pending, json);
                return;
            }
        }

        boolean ok = json.path("ok").asBoolean(false);
        if (!ok) {
            JsonNode error = json.path("error");
            String message = error.path("message").asText("unknown error");
            String code = error.path("code").asText("");
            log.warn("Gateway connect failed: {} ({})", message, code);
            if (message.toLowerCase().contains("pair") || message.toLowerCase().contains("not_paired")) {
                log.warn("Device may need approval: run `openclaw devices list` and `openclaw devices approve <id>`");
            }
            handshakeComplete = false;
            return;
        }

        JsonNode payload = json.path("payload");
        String helloType = payload.path("type").asText("");
        if ("hello-ok".equals(helloType) || payload.has("protocol")) {
            handshakeComplete = true;
            log.info("Gateway handshake complete (protocol={})", payload.path("protocol").asInt(-1));
        }
    }

    private void completePendingRequest(CompletableFuture<JsonNode> pending, JsonNode json) {
        if (json.path("ok").asBoolean(false)) {
            pending.complete(json.path("payload"));
        } else {
            JsonNode error = json.path("error");
            String message = error.path("message").asText("unknown error");
            pending.completeExceptionally(new RuntimeException(message));
        }
    }

    private void failPendingRequests(String message) {
        for (var entry : pendingRequests.entrySet()) {
            entry.getValue().completeExceptionally(new IllegalStateException(message));
        }
        pendingRequests.clear();
    }

    private void dispatchGatewayEvent(String event, JsonNode payload, Long seq) {
        if (event == null || event.isBlank() || IGNORED_GATEWAY_EVENTS.contains(event)) {
            return;
        }
        if (eventListeners.isEmpty()) {
            return;
        }
        if ("chat".equals(event)) {
            CHAT_EVENT_EXECUTOR.execute(() -> notifyEventListeners(event, payload, seq));
            return;
        }
        notifyEventListeners(event, payload, seq);
    }

    private void notifyEventListeners(String event, JsonNode payload, Long seq) {
        for (GatewayEventListener listener : eventListeners) {
            try {
                listener.onGatewayEvent(event, payload, seq);
            } catch (Exception e) {
                log.warn("Gateway event listener failed for {}: {}", event, e.getMessage());
            }
        }
    }

    private static String rootCauseMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private static String normalizePlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "win32";
        }
        if (os.contains("mac")) {
            return "darwin";
        }
        if (os.contains("linux")) {
            return "linux";
        }
        return os.isBlank() ? "unknown" : os;
    }
}
