package com.openclaw.vs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.vs.dto.ModelTestResult;
import com.openclaw.vs.gateway.OpenClawModelCatalog;
import com.openclaw.vs.model.ModelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConnectivityService {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OpenClawModelConfigService openClawModelConfigService;

    public ModelTestResult test(ModelConfig model, String plainApiKey) {
        if (!model.isEnabled()) {
            return ModelTestResult.builder()
                .success(false)
                .message("模型已禁用，请先启用")
                .build();
        }

        String apiKey = resolveApiKey(model, plainApiKey);
        if (apiKey == null || apiKey.isBlank()) {
            return ModelTestResult.builder()
                .success(false)
                .message("未配置 API Key（可在本平台保存，或写入 OpenClaw 配置）")
                .build();
        }

        String provider = model.getProvider() != null ? model.getProvider().toLowerCase(Locale.ROOT) : "";
        try {
            return switch (provider) {
                case "qwen", "openclaw" -> probeOpenAiCompatible(
                    resolveQwenBaseUrl(model),
                    apiKey,
                    extractModelId(model)
                );
                case "deepseek" -> probeOpenAiCompatible(
                    resolveDeepSeekBaseUrl(model),
                    apiKey,
                    extractModelId(model)
                );
                case "openai", "google", "openrouter" -> probeOpenAiCompatible(
                    resolveProviderBaseUrl(provider),
                    apiKey,
                    extractModelId(model)
                );
                case "custom" -> probeOpenAiCompatible(
                    normalizeOpenAiBaseUrl(model.getEndpoint()),
                    apiKey,
                    extractModelId(model)
                );
                case "claude" -> probeAnthropic(apiKey);
                case "ollama" -> probeOllama(model.getEndpoint());
                default -> ModelTestResult.builder()
                    .success(false)
                    .message("暂不支持该 Provider 的连通性探测: " + provider)
                    .build();
            };
        } catch (Exception e) {
            log.warn("Model connectivity test failed for {}: {}", model.getId(), e.getMessage());
            return ModelTestResult.builder()
                .success(false)
                .message("连接失败: " + e.getMessage())
                .build();
        }
    }

    private String resolveApiKey(ModelConfig model, String plainFromDb) {
        if (plainFromDb != null && !plainFromDb.isBlank()) {
            return plainFromDb.trim();
        }
        String provider = OpenClawModelConfigService.extractProvider(
            ModelService.resolveModelRef(model) != null
                ? ModelService.resolveModelRef(model)
                : model.getEndpoint()
        );
        if (openClawModelConfigService.hasProviderApiKey(provider)) {
            return readOpenClawProviderApiKey(provider);
        }
        return null;
    }

    private String readOpenClawProviderApiKey(String provider) {
        try {
            var path = openClawModelConfigService.getConfigPath();
            if (!java.nio.file.Files.isRegularFile(path)) {
                return null;
            }
            JsonNode root = MAPPER.readTree(path.toFile());
            JsonNode keyNode = root.path("models").path("providers").path(provider).path("apiKey");
            if (keyNode.isTextual() && !keyNode.asText().isBlank()) {
                return keyNode.asText().trim();
            }
        } catch (Exception e) {
            log.debug("Cannot read OpenClaw apiKey for {}: {}", provider, e.getMessage());
        }
        return null;
    }

    private String resolveQwenBaseUrl(ModelConfig model) {
        String fromOpenClaw = openClawModelConfigService.readProviderBaseUrl("qwen");
        if (fromOpenClaw != null && !fromOpenClaw.isBlank()) {
            return fromOpenClaw.trim();
        }
        return OpenClawModelCatalog.listQwenEndpoints().stream()
            .filter(e -> "coding-intl".equals(e.getId()))
            .map(e -> e.getBaseUrl())
            .findFirst()
            .orElse("https://coding.dashscope.aliyuncs.com/v1");
    }

    private String resolveDeepSeekBaseUrl(ModelConfig model) {
        return resolveProviderBaseUrl("deepseek");
    }

    private String resolveProviderBaseUrl(String provider) {
        String openclawProvider = "claude".equals(provider) ? "anthropic" : provider;
        String fromOpenClaw = openClawModelConfigService.readProviderBaseUrl(openclawProvider);
        if (fromOpenClaw != null && !fromOpenClaw.isBlank()) {
            return fromOpenClaw.trim();
        }
        return OpenClawModelCatalog.listProviderEndpoints(provider).stream()
            .map(e -> e.getBaseUrl())
            .findFirst()
            .orElseGet(() -> switch (provider) {
                case "openai" -> "https://api.openai.com/v1";
                case "google" -> "https://generativelanguage.googleapis.com/v1beta";
                case "openrouter" -> "https://openrouter.ai/api/v1";
                case "deepseek" -> "https://api.deepseek.com";
                default -> "https://api.openai.com/v1";
            });
    }

    private static String extractModelId(ModelConfig model) {
        String ref = ModelService.resolveModelRef(model);
        if (ref == null) {
            return null;
        }
        return OpenClawModelConfigService.extractModelId(ref);
    }

    private static String normalizeOpenAiBaseUrl(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "https://api.openai.com/v1";
        }
        String base = endpoint.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.endsWith("/v1")) {
            if (!base.contains("/v1")) {
                base = base + "/v1";
            }
        }
        return base;
    }

    private ModelTestResult probeOpenAiCompatible(String baseUrl, String apiKey, String modelId) throws Exception {
        String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String modelsUrl = root + "/models";

        long start = System.currentTimeMillis();
        HttpRequest listReq = HttpRequest.newBuilder()
            .uri(URI.create(modelsUrl))
            .timeout(TIMEOUT)
            .header("Authorization", "Bearer " + apiKey)
            .GET()
            .build();

        HttpResponse<String> listResp = HTTP.send(listReq, HttpResponse.BodyHandlers.ofString());
        long latency = System.currentTimeMillis() - start;

        if (listResp.statusCode() == 200) {
            return ModelTestResult.builder()
                .success(true)
                .message("API 连接正常")
                .latencyMs(latency)
                .build();
        }

        if (listResp.statusCode() == 401 || listResp.statusCode() == 403) {
            return ModelTestResult.builder()
                .success(false)
                .message("API Key 无效或无权访问（HTTP " + listResp.statusCode() + "）")
                .latencyMs(latency)
                .build();
        }

        if (modelId != null && !modelId.isBlank()) {
            return probeChatCompletion(root, apiKey, modelId, latency);
        }

        String detail = summarizeErrorBody(listResp.body());
        return ModelTestResult.builder()
            .success(false)
            .message("API 返回 HTTP " + listResp.statusCode() + detail)
            .latencyMs(latency)
            .build();
    }

    private ModelTestResult probeChatCompletion(String baseUrl, String apiKey, String modelId, long priorLatency)
        throws Exception {
        String url = baseUrl + "/chat/completions";
        String body = MAPPER.createObjectNode()
            .put("model", modelId)
            .put("max_tokens", 1)
            .set("messages", MAPPER.createArrayNode().add(
                MAPPER.createObjectNode().put("role", "user").put("content", "ping")
            ))
            .toString();

        long start = System.currentTimeMillis();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(TIMEOUT)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        long latency = priorLatency + (System.currentTimeMillis() - start);

        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            return ModelTestResult.builder()
                .success(true)
                .message("模型 " + modelId + " 响应正常")
                .latencyMs(latency)
                .build();
        }

        String detail = summarizeErrorBody(resp.body());
        return ModelTestResult.builder()
            .success(false)
            .message("模型调用失败 HTTP " + resp.statusCode() + detail)
            .latencyMs(latency)
            .build();
    }

    private ModelTestResult probeAnthropic(String apiKey) throws Exception {
        long start = System.currentTimeMillis();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .timeout(TIMEOUT)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"model\":\"claude-3-5-haiku-20241022\",\"max_tokens\":1,\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}]}"
            ))
            .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        long latency = System.currentTimeMillis() - start;

        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            return ModelTestResult.builder()
                .success(true)
                .message("Anthropic API 连接正常")
                .latencyMs(latency)
                .build();
        }
        if (resp.statusCode() == 401 || resp.statusCode() == 403) {
            return ModelTestResult.builder()
                .success(false)
                .message("API Key 无效（HTTP " + resp.statusCode() + "）")
                .latencyMs(latency)
                .build();
        }
        return ModelTestResult.builder()
            .success(false)
            .message("Anthropic API 返回 HTTP " + resp.statusCode() + summarizeErrorBody(resp.body()))
            .latencyMs(latency)
            .build();
    }

    private ModelTestResult probeOllama(String endpoint) throws Exception {
        String base = endpoint != null && !endpoint.isBlank() ? endpoint.trim() : "http://localhost:11434";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String url = base + "/api/tags";

        long start = System.currentTimeMillis();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(TIMEOUT)
            .GET()
            .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        long latency = System.currentTimeMillis() - start;

        if (resp.statusCode() == 200) {
            return ModelTestResult.builder()
                .success(true)
                .message("Ollama 服务可达")
                .latencyMs(latency)
                .build();
        }
        return ModelTestResult.builder()
            .success(false)
            .message("Ollama 返回 HTTP " + resp.statusCode())
            .latencyMs(latency)
            .build();
    }

    private static String summarizeErrorBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String trimmed = body.trim();
        if (trimmed.length() > 120) {
            trimmed = trimmed.substring(0, 120) + "…";
        }
        return ": " + trimmed;
    }
}
