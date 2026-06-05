package com.openclaw.vs.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/** Reads gateway auth settings from the local OpenClaw install (~/.openclaw). */
public final class OpenClawGatewayConfigReader {

    public static final int DEFAULT_GATEWAY_PORT = 18789;
    /** OpenClaw default when gateway.port is {@value DEFAULT_GATEWAY_PORT} (gateway + 2). */
    public static final int DEFAULT_BROWSER_CONTROL_PORT = 18791;
    private static final int BROWSER_CONTROL_PORT_OFFSET = 2;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(800))
        .build();
    private static final long CONFIG_CACHE_TTL_MS = 3_000;
    private static volatile CachedGatewayConfig CONFIG_CACHE;

    private record CachedGatewayConfig(long loadedAtMs, Optional<Integer> port, Optional<String> token) {}

    private OpenClawGatewayConfigReader() {}

    public static Path defaultIdentityPath() {
        return Path.of(System.getProperty("user.home"), ".openclaw", "identity", "device.json");
    }

    public static Path defaultConfigPath() {
        Path home = Path.of(System.getProperty("user.home"), ".openclaw");
        Path json = home.resolve("openclaw.json");
        if (Files.isRegularFile(json)) {
            return json;
        }
        return home.resolve("config.json");
    }

    public static Optional<String> readGatewayToken() {
        return cachedConfig().token();
    }

    public static Optional<Integer> readGatewayPort() {
        return cachedConfig().port();
    }

    public static void invalidateConfigCache() {
        CONFIG_CACHE = null;
    }

    private static CachedGatewayConfig cachedConfig() {
        CachedGatewayConfig cached = CONFIG_CACHE;
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.loadedAtMs() < CONFIG_CACHE_TTL_MS) {
            return cached;
        }
        Optional<JsonNode> gateway = readGatewaySection();
        Optional<String> token = gateway.flatMap(node -> {
            String value = node.path("auth").path("token").asText(null);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(value.trim());
        });
        Optional<Integer> port = gateway
            .map(node -> node.path("port"))
            .filter(JsonNode::isNumber)
            .map(JsonNode::asInt)
            .filter(p -> p > 0 && p < 65536);
        CachedGatewayConfig fresh = new CachedGatewayConfig(now, port, token);
        CONFIG_CACHE = fresh;
        return fresh;
    }

    /** Browser control port derived from gateway port (see OpenClaw {@code deriveDefaultBrowserControlPort}). */
    public static int deriveBrowserControlPort(int gatewayPort) {
        int derived = gatewayPort + BROWSER_CONTROL_PORT_OFFSET;
        return (derived > 0 && derived <= 65535) ? derived : DEFAULT_BROWSER_CONTROL_PORT;
    }

    /**
     * Resolves the port where Gateway WebSocket listens.
     * Corrects a common misconfiguration: {@code gateway.port: 18791} in openclaw.json when the real
     * Gateway is on {@value DEFAULT_GATEWAY_PORT} and 18791 is only the browser control HTTP API.
     */
    public static int resolveEffectiveGatewayPort(int defaultPort) {
        int configured = readGatewayPort().orElse(defaultPort);
        return correctPortIfBrowserControlMisconfigured(configured);
    }

    public static int correctPortIfBrowserControlMisconfigured(int port) {
        if (!isPortLikelyBrowserControl(port)) {
            return port;
        }
        if (isPortLikelyGateway(DEFAULT_GATEWAY_PORT)) {
            return DEFAULT_GATEWAY_PORT;
        }
        int derivedGateway = port - BROWSER_CONTROL_PORT_OFFSET;
        if (derivedGateway > 0 && isPortLikelyGateway(derivedGateway)) {
            return derivedGateway;
        }
        return port;
    }

    /**
     * WebSocket URL for OpenClaw Gateway (root path, no {@code /ws} suffix).
     */
    public static String resolveWebSocketUrl(int defaultPort) {
        return toWebSocketUrl(resolveEffectiveGatewayPort(defaultPort));
    }

    public static String toWebSocketUrl(int port) {
        return "ws://127.0.0.1:" + port;
    }

    /** Strips mistaken {@code /ws} suffix from legacy app config values. */
    public static String normalizeWebSocketUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();
        if (trimmed.endsWith("/ws")) {
            return trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed;
    }

    /** Browser control {@code GET /} returns JSON with CDP status fields. */
    public static boolean isPortLikelyBrowserControl(int port) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/"))
                .timeout(Duration.ofMillis(1500))
                .GET()
                .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return false;
            }
            String body = response.body();
            return body.contains("\"cdpReady\"") || body.contains("\"detectedBrowser\"");
        } catch (Exception e) {
            return false;
        }
    }

    /** Gateway exposes {@code /health} without WebSocket upgrade. */
    public static boolean isPortLikelyGateway(int port) {
        for (String path : new String[] {"/health", "/healthz"}) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofMillis(1500))
                    .GET()
                    .build();
                HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    return true;
                }
            } catch (Exception ignored) {
                // try next path
            }
        }
        return false;
    }

    private static Optional<JsonNode> readGatewaySection() {
        Path configPath = defaultConfigPath();
        if (!Files.isRegularFile(configPath)) {
            return Optional.empty();
        }
        try {
            JsonNode gateway = MAPPER.readTree(configPath.toFile()).path("gateway");
            return gateway.isMissingNode() || gateway.isNull() ? Optional.empty() : Optional.of(gateway);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
