package com.openclaw.vs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openclaw.vs.dto.OpenClawModelEntry;
import com.openclaw.vs.dto.OpenClawModelOverview;
import com.openclaw.vs.gateway.OpenClawGatewayConfigReader;
import com.openclaw.vs.util.OpenClawConfigJsonWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class OpenClawModelConfigService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Bundled OpenClaw providers: apiKey overlay only; catalog comes from the plugin. */
    private static final Set<String> BUILT_IN_PROVIDERS = Set.of(
        "qwen", "qwencloud", "modelstudio",
        "openai", "openai-codex", "anthropic", "google", "gemini",
        "openrouter", "deepseek", "ollama", "github-copilot"
    );

    private static final Map<String, String> LEGACY_MODEL_REF_ALIASES = Map.of(
        "qwen/qwen-plus", "qwen/qwen3.5-plus"
    );

    public Path getConfigPath() {
        return OpenClawGatewayConfigReader.defaultConfigPath();
    }

    public OpenClawModelOverview readOverview() {
        Path configPath = getConfigPath();
        if (!Files.isRegularFile(configPath)) {
            return OpenClawModelOverview.builder()
                .configPath(configPath.toString())
                .primaryModelRef(null)
                .fallbackModelRefs(List.of())
                .models(List.of())
                .build();
        }

        try {
            JsonNode root = MAPPER.readTree(configPath.toFile());
            JsonNode defaults = root.path("agents").path("defaults");
            JsonNode modelNode = defaults.path("model");
            String primary = modelNode.path("primary").asText(null);
            List<String> fallbacks = readFallbackRefs(modelNode);
            JsonNode modelsNode = defaults.path("models");

            List<OpenClawModelEntry> models = new ArrayList<>();
            if (modelsNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = modelsNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String modelRef = entry.getKey();
                    String provider = extractProvider(modelRef);
                    models.add(OpenClawModelEntry.builder()
                        .modelRef(modelRef)
                        .provider(provider)
                        .displayName(toDisplayName(modelRef))
                        .primary(modelRef.equals(primary))
                        .apiKeyConfigured(hasProviderApiKey(root, provider))
                        .build());
                }
            }

            String qwenBaseUrl = readProviderBaseUrl(root, "qwen");
            String deepseekBaseUrl = readProviderBaseUrl(root, "deepseek");

            return OpenClawModelOverview.builder()
                .configPath(configPath.toString())
                .primaryModelRef(primary)
                .fallbackModelRefs(fallbacks)
                .qwenBaseUrl(qwenBaseUrl)
                .deepseekBaseUrl(deepseekBaseUrl)
                .models(models)
                .build();
        } catch (Exception e) {
            log.warn("Failed to read OpenClaw model config: {}", e.getMessage());
            return OpenClawModelOverview.builder()
                .configPath(configPath.toString())
                .models(List.of())
                .build();
        }
    }

    public void setPrimaryModel(String modelRef) throws Exception {
        setPrimaryModel(modelRef, null);
    }

    public void setPrimaryModel(String modelRef, List<String> fallbackModelRefs) throws Exception {
        Path configPath = getConfigPath();
        if (!Files.isRegularFile(configPath)) {
            throw new IllegalStateException("OpenClaw 配置文件不存在: " + configPath);
        }

        ObjectNode root = (ObjectNode) MAPPER.readTree(configPath.toFile());
        ObjectNode agents = ensureObject(root, "agents");
        ObjectNode defaults = ensureObject(agents, "defaults");
        ObjectNode model = ensureObject(defaults, "model");

        applyModelRegistration(root, modelRef);
        String normalizedRef = normalizeModelRef(modelRef);
        String primary = normalizedRef != null ? normalizedRef : modelRef;
        model.put("primary", primary);
        writeFallbacks(root, model, primary, fallbackModelRefs);

        writeConfig(root, configPath);
        log.info("OpenClaw primary model set to {} (fallbacks: {})", primary, fallbackModelRefs);
    }

    /**
     * 从 openclaw.json 注销模型：移除 agents.defaults.models 条目、备用链中的引用，
     * 以及 models.providers 中对应的 catalog 条目。
     */
    public void unregisterModel(String modelRef) throws Exception {
        if (modelRef == null || modelRef.isBlank()) {
            return;
        }
        Path configPath = getConfigPath();
        if (!Files.isRegularFile(configPath)) {
            return;
        }

        ObjectNode root = (ObjectNode) MAPPER.readTree(configPath.toFile());
        unregisterModelInRoot(root, modelRef);
        writeConfig(root, configPath);
        log.info("OpenClaw model unregistered: {}", normalizeModelRef(modelRef.trim()));
    }

    /** Exposed for unit tests. */
    static void unregisterModelInRoot(ObjectNode root, String modelRef) {
        if (modelRef == null || modelRef.isBlank()) {
            return;
        }
        String normalizedRef = normalizeModelRef(modelRef.trim());

        ObjectNode agents = ensureObject(root, "agents");
        ObjectNode defaults = ensureObject(agents, "defaults");
        ObjectNode agentModels = defaults.path("models") instanceof ObjectNode modelsNode
            ? modelsNode
            : null;

        if (agentModels != null) {
            agentModels.remove(modelRef.trim());
            if (normalizedRef != null && !normalizedRef.equals(modelRef.trim())) {
                agentModels.remove(normalizedRef);
            }
            if (agentModels.isEmpty()) {
                defaults.remove("models");
            }
        }

        ObjectNode modelNode = defaults.path("model") instanceof ObjectNode mn ? mn : null;
        if (modelNode != null) {
            removeFromFallbacks(modelNode, modelRef.trim());
            if (normalizedRef != null && !normalizedRef.equals(modelRef.trim())) {
                removeFromFallbacks(modelNode, normalizedRef);
            }
            String primary = modelNode.path("primary").asText(null);
            if (refMatches(primary, modelRef.trim(), normalizedRef)) {
                modelNode.remove("primary");
            }
        }

        if (normalizedRef != null) {
            removeProviderModelEntry(root, normalizedRef);
        }
    }

    private static boolean refMatches(String candidate, String rawRef, String normalizedRef) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        return candidate.equals(rawRef) || (normalizedRef != null && candidate.equals(normalizedRef));
    }

    private static void removeFromFallbacks(ObjectNode modelNode, String modelRef) {
        if (modelRef == null || modelRef.isBlank()) {
            return;
        }
        JsonNode fb = modelNode.get("fallbacks");
        if (!(fb instanceof ArrayNode array)) {
            return;
        }
        ArrayNode kept = MAPPER.createArrayNode();
        for (JsonNode item : array) {
            String ref = item.asText(null);
            if (ref != null && !ref.isBlank() && !modelRef.equals(ref)) {
                kept.add(ref);
            }
        }
        if (kept.isEmpty()) {
            modelNode.remove("fallbacks");
        } else {
            modelNode.set("fallbacks", kept);
        }
    }

    private static void removeProviderModelEntry(ObjectNode root, String modelRef) {
        String provider = extractProvider(modelRef);
        String modelId = extractModelId(modelRef);
        if (modelId == null || provider == null || "custom".equals(provider)) {
            return;
        }
        JsonNode providerNodeRaw = root.path("models").path("providers").path(provider);
        if (!(providerNodeRaw instanceof ObjectNode providerNode)) {
            return;
        }
        JsonNode models = providerNode.get("models");
        if (!(models instanceof ArrayNode array)) {
            return;
        }
        for (int i = array.size() - 1; i >= 0; i--) {
            if (modelId.equals(array.get(i).path("id").asText(null))) {
                array.remove(i);
            }
        }
        if (array.isEmpty()) {
            providerNode.remove("models");
        }
    }

    private static List<String> readFallbackRefs(JsonNode modelNode) {
        JsonNode fb = modelNode.path("fallbacks");
        if (!fb.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : fb) {
            String ref = item.asText(null);
            if (ref != null && !ref.isBlank()) {
                out.add(ref);
            }
        }
        return out;
    }

    private static void writeFallbacks(
        ObjectNode root,
        ObjectNode modelNode,
        String primaryRef,
        List<String> fallbackModelRefs
    ) {
        if (fallbackModelRefs == null) {
            return;
        }
        if (fallbackModelRefs.isEmpty()) {
            modelNode.remove("fallbacks");
            return;
        }
        ArrayNode array = MAPPER.createArrayNode();
        for (String raw : fallbackModelRefs) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String normalized = normalizeModelRef(raw.trim());
            if (normalized == null || normalized.isBlank() || normalized.equals(primaryRef)) {
                continue;
            }
            applyModelRegistration(root, normalized);
            array.add(normalized);
        }
        if (array.isEmpty()) {
            modelNode.remove("fallbacks");
        } else {
            modelNode.set("fallbacks", array);
        }
    }

    public boolean hasProviderApiKey(String provider) {
        if (provider == null || provider.isBlank()) {
            return false;
        }
        Path configPath = getConfigPath();
        if (!Files.isRegularFile(configPath)) {
            return false;
        }
        try {
            JsonNode root = MAPPER.readTree(configPath.toFile());
            return hasProviderApiKey(root, provider);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 写入 models.providers.&lt;provider&gt;.apiKey，供 OpenClaw 运行时读取。
     */
    public void setProviderApiKey(String provider, String apiKey) throws Exception {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider 不能为空");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey 不能为空");
        }
        Path configPath = getConfigPath();
        if (!Files.isRegularFile(configPath)) {
            throw new IllegalStateException("OpenClaw 配置文件不存在: " + configPath);
        }

        ObjectNode root = (ObjectNode) MAPPER.readTree(configPath.toFile());
        ObjectNode models = ensureObject(root, "models");
        ObjectNode providers = ensureObject(models, "providers");
        ObjectNode providerNode = ensureObject(providers, provider);
        providerNode.put("apiKey", apiKey.trim());
        sanitizeAllProviderModels(providers);

        writeConfig(root, configPath);
        log.info("OpenClaw provider {} apiKey updated in config", provider);
    }

    public String readProviderBaseUrl(String provider) {
        Path configPath = getConfigPath();
        if (!Files.isRegularFile(configPath)) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(configPath.toFile());
            return readProviderBaseUrl(root, provider);
        } catch (Exception e) {
            return null;
        }
    }

    private static String readProviderBaseUrl(JsonNode root, String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        String baseUrl = root.path("models").path("providers").path(provider).path("baseUrl").asText(null);
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        return baseUrl.trim();
    }

    public void registerModel(String modelRef) throws Exception {
        applyModelToConfig(modelRef, true, false, null, null, null);
    }

    /**
     * 单次读写 openclaw.json：注册模型、可选设为主模型、可选写入 provider apiKey / baseUrl。
     */
    public void applyModelToConfig(
        String modelRef,
        boolean register,
        boolean setAsPrimary,
        String plainApiKey,
        List<String> fallbackModelRefs,
        String baseUrl
    ) throws Exception {
        Path configPath = getConfigPath();
        if (!Files.isRegularFile(configPath)) {
            throw new IllegalStateException("OpenClaw 配置文件不存在: " + configPath);
        }

        ObjectNode root = (ObjectNode) MAPPER.readTree(configPath.toFile());

        if (register || setAsPrimary) {
            applyModelRegistration(root, modelRef);
        }

        if (setAsPrimary) {
            ObjectNode agents = ensureObject(root, "agents");
            ObjectNode defaults = ensureObject(agents, "defaults");
            ObjectNode model = ensureObject(defaults, "model");
            String normalizedRef = normalizeModelRef(modelRef);
            String primary = normalizedRef != null ? normalizedRef : modelRef;
            model.put("primary", primary);
            writeFallbacks(root, model, primary, fallbackModelRefs);
        }

        String provider = extractProvider(modelRef);
        if ((plainApiKey != null && !plainApiKey.isBlank()) || (baseUrl != null && !baseUrl.isBlank())) {
            ObjectNode models = ensureObject(root, "models");
            ObjectNode providers = ensureObject(models, "providers");
            ObjectNode providerNode = ensureObject(providers, provider);
            if (plainApiKey != null && !plainApiKey.isBlank()) {
                providerNode.put("apiKey", plainApiKey.trim());
            }
            if (baseUrl != null && !baseUrl.isBlank()) {
                providerNode.put("baseUrl", baseUrl.trim());
            }
            sanitizeAllProviderModels(providers);
        }

        writeConfig(root, configPath);
        log.info(
            "OpenClaw config updated: modelRef={}, register={}, primary={}, apiKey={}, baseUrl={}",
            modelRef,
            register,
            setAsPrimary,
            plainApiKey != null && !plainApiKey.isBlank(),
            baseUrl != null && !baseUrl.isBlank()
        );
    }

    /** Applies agent + provider model entries; exposed for unit tests. */
    static void applyModelRegistration(ObjectNode root, String modelRef) {
        String normalizedRef = normalizeModelRef(modelRef);
        ObjectNode agents = ensureObject(root, "agents");
        ObjectNode defaults = ensureObject(agents, "defaults");
        ObjectNode agentModels = ensureObject(defaults, "models");
        if (modelRef != null && !modelRef.equals(normalizedRef) && agentModels.has(modelRef)) {
            agentModels.remove(modelRef);
        }
        ensureAgentModelEntry(agentModels, normalizedRef);
        ensureProviderModelEntry(root, normalizedRef);
        ObjectNode models = ensureObject(root, "models");
        sanitizeAllProviderModels(ensureObject(models, "providers"));
    }

    static String normalizeModelRef(String modelRef) {
        if (modelRef == null) {
            return null;
        }
        return LEGACY_MODEL_REF_ALIASES.getOrDefault(modelRef, modelRef);
    }

    static boolean isBuiltInProvider(String provider) {
        return provider != null && BUILT_IN_PROVIDERS.contains(provider.toLowerCase());
    }

    public static String extractProvider(String modelRef) {
        if (modelRef == null || !modelRef.contains("/")) {
            return "custom";
        }
        return modelRef.substring(0, modelRef.indexOf('/'));
    }

    /** Model id after the slash in a ref such as {@code qwen/qwen-plus}. */
    public static String extractModelId(String modelRef) {
        if (modelRef == null || !modelRef.contains("/")) {
            return null;
        }
        String id = modelRef.substring(modelRef.indexOf('/') + 1);
        return id.isBlank() ? null : id;
    }

    public static String toDisplayName(String modelRef) {
        if (modelRef == null) {
            return "";
        }
        int slash = modelRef.indexOf('/');
        String modelId = slash >= 0 ? modelRef.substring(slash + 1) : modelRef;
        return modelId.replace('-', ' ').replace('_', ' ');
    }

    private static boolean hasProviderApiKey(JsonNode root, String provider) {
        JsonNode apiKey = root.path("models").path("providers").path(provider).path("apiKey");
        if (apiKey.isMissingNode() || apiKey.isNull()) {
            return false;
        }
        if (apiKey.isTextual()) {
            return !apiKey.asText().isBlank();
        }
        if (apiKey.isObject() && apiKey.has("source")) {
            return true;
        }
        return false;
    }

    private static void ensureAgentModelEntry(ObjectNode agentModels, String modelRef) {
        if (!agentModels.has(modelRef)) {
            agentModels.set(modelRef, MAPPER.createObjectNode());
        }
    }

    /**
     * When {@code agents.defaults.models} lists a ref, OpenClaw also requires a matching
     * {@code models.providers[provider].models[]} entry with at least {@code id} and {@code name}.
     */
    private static void ensureProviderModelEntry(ObjectNode root, String modelRef) {
        String provider = extractProvider(modelRef);
        String modelId = extractModelId(modelRef);
        if (modelId == null || provider == null || "custom".equals(provider)) {
            return;
        }

        ObjectNode models = ensureObject(root, "models");
        ObjectNode providers = ensureObject(models, "providers");
        ObjectNode providerNode = ensureObject(providers, provider);

        JsonNode existing = providerNode.get("models");
        ArrayNode modelsArray;
        if (existing instanceof ArrayNode arrayNode) {
            modelsArray = arrayNode;
        } else {
            modelsArray = MAPPER.createArrayNode();
            providerNode.set("models", modelsArray);
        }

        removeLegacyProviderModelIds(modelsArray, provider, modelId);

        for (JsonNode item : modelsArray) {
            if (modelId.equals(item.path("id").asText(null))) {
                return;
            }
        }

        modelsArray.add(buildProviderModelEntry(modelId));
    }

    private static void removeLegacyProviderModelIds(ArrayNode modelsArray, String provider, String activeModelId) {
        for (Map.Entry<String, String> alias : LEGACY_MODEL_REF_ALIASES.entrySet()) {
            if (!provider.equals(extractProvider(alias.getValue()))) {
                continue;
            }
            if (!activeModelId.equals(extractModelId(alias.getValue()))) {
                continue;
            }
            String legacyId = extractModelId(alias.getKey());
            if (legacyId == null || legacyId.equals(activeModelId)) {
                continue;
            }
            for (int i = modelsArray.size() - 1; i >= 0; i--) {
                if (legacyId.equals(modelsArray.get(i).path("id").asText(null))) {
                    modelsArray.remove(i);
                }
            }
        }
    }

    private static ObjectNode buildProviderModelEntry(String modelId) {
        ObjectNode entry = MAPPER.createObjectNode();
        entry.put("id", modelId);
        entry.put("name", modelId);
        return entry;
    }

    private static void sanitizeAllProviderModels(ObjectNode providers) {
        Iterator<Map.Entry<String, JsonNode>> fields = providers.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getValue() instanceof ObjectNode providerNode) {
                if (isBuiltInProvider(entry.getKey())) {
                    sanitizeBuiltInProviderModels(providerNode);
                } else {
                    sanitizeCustomProviderModels(providerNode);
                }
            }
        }
    }

    /** Fix or drop invalid provider catalog entries (e.g. id-only objects break gateway startup). */
    private static void sanitizeBuiltInProviderModels(ObjectNode providerNode) {
        sanitizeProviderModelsArray(providerNode);
    }

    private static void sanitizeCustomProviderModels(ObjectNode providerNode) {
        sanitizeProviderModelsArray(providerNode);
    }

    private static void sanitizeProviderModelsArray(ObjectNode providerNode) {
        JsonNode models = providerNode.get("models");
        if (!(models instanceof ArrayNode array)) {
            return;
        }
        ArrayNode kept = MAPPER.createArrayNode();
        boolean changed = false;
        for (JsonNode item : array) {
            if (isValidProviderModelEntry(item)) {
                kept.add(item);
            } else if (item.isObject() && !item.path("id").asText("").isBlank()) {
                kept.add(buildProviderModelEntry(item.path("id").asText()));
                changed = true;
            } else {
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        if (kept.isEmpty()) {
            providerNode.remove("models");
        } else {
            providerNode.set("models", kept);
        }
    }

    private static boolean isValidProviderModelEntry(JsonNode item) {
        return item.isObject()
            && !item.path("id").asText("").isBlank()
            && !item.path("name").asText("").isBlank();
    }

    private static ObjectNode ensureObject(ObjectNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = MAPPER.createObjectNode();
        parent.set(field, created);
        return created;
    }

    private static void writeConfig(ObjectNode root, Path configPath) {
        OpenClawConfigJsonWriter.writePretty(configPath, MAPPER, root);
        OpenClawGatewayConfigReader.invalidateConfigCache();
    }
}
