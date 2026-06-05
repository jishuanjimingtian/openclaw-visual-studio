package com.openclaw.vs.gateway;

import com.openclaw.vs.dto.ModelMarketCategoryDto;
import com.openclaw.vs.dto.OpenClawCatalogModelDto;
import com.openclaw.vs.dto.OpenClawEndpointOptionDto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Built-in model market catalog for the UI. User selections are written to openclaw.json
 * only via model management save actions.
 */
public final class OpenClawModelCatalog {

    private record MarketEntry(
        String modelRef,
        String displayName,
        String description,
        String category,
        List<String> tags,
        Integer contextWindow,
        String defaultBaseUrl
    ) {}

    private static final List<ModelMarketCategoryDto> CATEGORIES = List.of(
        ModelMarketCategoryDto.builder().id("all").label("全部").description("所有可用模型").build(),
        ModelMarketCategoryDto.builder().id("domestic").label("国内厂商").description("Qwen、DeepSeek 等国内模型").build(),
        ModelMarketCategoryDto.builder().id("international").label("国际主流").description("OpenAI、Claude、Gemini 等").build(),
        ModelMarketCategoryDto.builder().id("local").label("本地部署").description("Ollama 本地推理").build(),
        ModelMarketCategoryDto.builder().id("aggregator").label("聚合路由").description("OpenRouter 等多模型网关").build(),
        ModelMarketCategoryDto.builder().id("coding").label("代码模型").description("编程与 Agent 场景").build(),
        ModelMarketCategoryDto.builder().id("reasoning").label("推理模型").description("深度思考与复杂推理").build()
    );

    private static final List<MarketEntry> MARKET = List.of(
        // —— 国内：Qwen ——
        entry("qwen/qwen3.5-plus", "Qwen 3.5 Plus", "通义千问主力模型，均衡性能", "domestic",
            List.of("chat", "fast"), 131_072, "https://coding.dashscope.aliyuncs.com/v1"),
        entry("qwen/qwen3.6-plus", "Qwen 3.6 Plus", "通义千问新一代 Plus", "domestic",
            List.of("chat"), 131_072, "https://coding.dashscope.aliyuncs.com/v1"),
        entry("qwen/qwen3-max-2026-01-23", "Qwen 3 Max", "旗舰级推理与综合能力", "domestic",
            List.of("chat", "reasoning", "premium"), 131_072, "https://coding.dashscope.aliyuncs.com/v1"),
        entry("qwen/qwen3-coder-next", "Qwen 3 Coder Next", "新一代代码模型", "domestic",
            List.of("coding"), 131_072, "https://coding.dashscope.aliyuncs.com/v1"),
        entry("qwen/qwen3-coder-plus", "Qwen 3 Coder Plus", "代码生成与修复", "domestic",
            List.of("coding"), 131_072, "https://coding.dashscope.aliyuncs.com/v1"),
        entry("qwen/glm-5", "GLM-5", "智谱 GLM 系列（Qwen 插件）", "domestic",
            List.of("chat"), 131_072, "https://coding.dashscope.aliyuncs.com/v1"),
        entry("qwen/glm-4.7", "GLM-4.7", "智谱 GLM 轻量版", "domestic",
            List.of("chat", "fast"), 131_072, "https://coding.dashscope.aliyuncs.com/v1"),
        entry("qwen/kimi-k2.5", "Kimi K2.5", "月之暗面 Kimi 模型", "domestic",
            List.of("chat"), 131_072, "https://coding.dashscope.aliyuncs.com/v1"),
        entry("qwen/MiniMax-M2.5", "MiniMax M2.5", "MiniMax 对话模型", "domestic",
            List.of("chat"), 131_072, "https://coding.dashscope.aliyuncs.com/v1"),

        // —— 国内：DeepSeek ——
        entry("deepseek/deepseek-v4-flash", "DeepSeek V4 Flash", "默认推荐，百万上下文", "domestic",
            List.of("chat", "reasoning", "fast"), 1_000_000, "https://api.deepseek.com"),
        entry("deepseek/deepseek-v4-pro", "DeepSeek V4 Pro", "更强 V4 模型", "domestic",
            List.of("chat", "reasoning", "premium"), 1_000_000, "https://api.deepseek.com"),
        entry("deepseek/deepseek-chat", "DeepSeek Chat", "V3.2 非思考模式", "domestic",
            List.of("chat"), 131_072, "https://api.deepseek.com"),
        entry("deepseek/deepseek-reasoner", "DeepSeek Reasoner", "V3.2 推理模式", "domestic",
            List.of("reasoning"), 131_072, "https://api.deepseek.com"),

        // —— 国际：OpenAI ——
        entry("openai/gpt-4o", "GPT-4o", "OpenAI 多模态旗舰", "international",
            List.of("chat", "premium"), 128_000, "https://api.openai.com/v1"),
        entry("openai/gpt-4o-mini", "GPT-4o Mini", "高性价比快速模型", "international",
            List.of("chat", "fast"), 128_000, "https://api.openai.com/v1"),
        entry("openai/o1", "OpenAI o1", "复杂推理专用", "international",
            List.of("reasoning", "premium"), 200_000, "https://api.openai.com/v1"),
        entry("openai/o1-mini", "OpenAI o1-mini", "轻量推理模型", "international",
            List.of("reasoning", "fast"), 128_000, "https://api.openai.com/v1"),

        // —— 国际：Anthropic ——
        entry("anthropic/claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", "Anthropic 均衡旗舰", "international",
            List.of("chat", "coding"), 200_000, "https://api.anthropic.com"),
        entry("anthropic/claude-3-5-haiku-20241022", "Claude 3.5 Haiku", "快速低成本", "international",
            List.of("chat", "fast"), 200_000, "https://api.anthropic.com"),
        entry("anthropic/claude-opus-4-20250514", "Claude Opus 4", "最强推理与创作", "international",
            List.of("chat", "reasoning", "premium"), 200_000, "https://api.anthropic.com"),

        // —— 国际：Google ——
        entry("google/gemini-2.0-flash", "Gemini 2.0 Flash", "Google 快速模型", "international",
            List.of("chat", "fast"), 1_048_576, "https://generativelanguage.googleapis.com/v1beta"),
        entry("google/gemini-2.5-pro-preview-05-06", "Gemini 2.5 Pro", "Google 旗舰预览", "international",
            List.of("chat", "reasoning", "premium"), 1_048_576, "https://generativelanguage.googleapis.com/v1beta"),

        // —— 本地：Ollama ——
        entry("ollama/llama3.2", "Llama 3.2", "Meta 开源本地模型", "local",
            List.of("chat"), 128_000, "http://localhost:11434"),
        entry("ollama/qwen2.5", "Qwen 2.5 (Ollama)", "通义千问本地版", "local",
            List.of("chat"), 32_768, "http://localhost:11434"),
        entry("ollama/deepseek-r1", "DeepSeek R1 (Ollama)", "本地推理模型", "local",
            List.of("reasoning"), 64_000, "http://localhost:11434"),
        entry("ollama/codellama", "Code Llama", "本地代码模型", "local",
            List.of("coding"), 16_384, "http://localhost:11434"),

        // —— 聚合：OpenRouter ——
        entry("openrouter/anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet (OR)", "经 OpenRouter 路由", "aggregator",
            List.of("chat", "coding"), 200_000, "https://openrouter.ai/api/v1"),
        entry("openrouter/openai/gpt-4o", "GPT-4o (OR)", "经 OpenRouter 路由", "aggregator",
            List.of("chat"), 128_000, "https://openrouter.ai/api/v1"),
        entry("openrouter/deepseek/deepseek-chat", "DeepSeek Chat (OR)", "经 OpenRouter 路由", "aggregator",
            List.of("chat"), 131_072, "https://openrouter.ai/api/v1"),
        entry("openrouter/google/gemini-2.0-flash-001", "Gemini 2.0 Flash (OR)", "经 OpenRouter 路由", "aggregator",
            List.of("chat", "fast"), 1_048_576, "https://openrouter.ai/api/v1")
    );

    private static final Map<String, String> UI_PROVIDER_MAP = Map.of(
        "anthropic", "claude",
        "google", "google",
        "gemini", "google"
    );

    private OpenClawModelCatalog() {}

    public static List<ModelMarketCategoryDto> listCategories() {
        return CATEGORIES;
    }

    public static List<OpenClawCatalogModelDto> listMarket(String category, String provider, String query) {
        String cat = normalize(category);
        String prov = normalize(provider);
        String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";

        return MARKET.stream()
            .filter(e -> matchesCategory(e, cat))
            .filter(e -> matchesProvider(e, prov))
            .filter(e -> matchesQuery(e, q))
            .map(OpenClawModelCatalog::toDto)
            .toList();
    }

    public static List<OpenClawEndpointOptionDto> listQwenEndpoints() {
        return List.of(
            OpenClawEndpointOptionDto.builder()
                .id("coding-cn")
                .label("Coding Plan（国内）")
                .baseUrl("https://coding.dashscope.aliyuncs.com/v1")
                .description("订阅制 Coding Plan，国内网络推荐")
                .build(),
            OpenClawEndpointOptionDto.builder()
                .id("coding-intl")
                .label("Coding Plan（国际）")
                .baseUrl("https://coding-intl.dashscope.aliyuncs.com/v1")
                .description("OpenClaw 未配置 baseUrl 时的默认值")
                .build(),
            OpenClawEndpointOptionDto.builder()
                .id("standard-cn")
                .label("标准 API（国内按量）")
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .description("DashScope 兼容 OpenAI 接口")
                .build(),
            OpenClawEndpointOptionDto.builder()
                .id("standard-intl")
                .label("标准 API（国际按量）")
                .baseUrl("https://dashscope-intl.aliyuncs.com/compatible-mode/v1")
                .description("海外/国际按量付费")
                .build()
        );
    }

    public static List<OpenClawEndpointOptionDto> listDeepSeekEndpoints() {
        return List.of(
            OpenClawEndpointOptionDto.builder()
                .id("official")
                .label("DeepSeek 官方 API")
                .baseUrl("https://api.deepseek.com")
                .description("OpenClaw 默认端点")
                .build(),
            OpenClawEndpointOptionDto.builder()
                .id("official-v1")
                .label("DeepSeek 官方 API（/v1）")
                .baseUrl("https://api.deepseek.com/v1")
                .description("兼容部分 OpenAI SDK 的 /v1 路径")
                .build()
        );
    }

    public static List<OpenClawEndpointOptionDto> listProviderEndpoints(String provider) {
        if (provider == null || provider.isBlank()) {
            return List.of();
        }
        return switch (provider.trim().toLowerCase(Locale.ROOT)) {
            case "qwen", "openclaw" -> listQwenEndpoints();
            case "deepseek" -> listDeepSeekEndpoints();
            case "openai" -> List.of(endpoint("openai", "OpenAI 官方", "https://api.openai.com/v1", "OpenAI API"));
            case "claude", "anthropic" -> List.of(
                endpoint("anthropic", "Anthropic 官方", "https://api.anthropic.com", "Claude API"));
            case "google" -> List.of(
                endpoint("google", "Google AI", "https://generativelanguage.googleapis.com/v1beta", "Gemini API"));
            case "ollama" -> List.of(
                endpoint("local", "本机 Ollama", "http://localhost:11434", "默认本地地址"));
            case "openrouter" -> List.of(
                endpoint("or", "OpenRouter", "https://openrouter.ai/api/v1", "聚合 API"));
            default -> List.of();
        };
    }

    public static List<OpenClawCatalogModelDto> listSuggestions(String provider) {
        if (provider == null || provider.isBlank()) {
            return List.of();
        }
        String p = provider.trim().toLowerCase(Locale.ROOT);
        if ("openclaw".equals(p)) {
            return listMarket(null, "qwen", null).stream()
                .map(m -> OpenClawCatalogModelDto.builder()
                    .modelRef("openclaw/" + m.getModelId())
                    .modelId(m.getModelId())
                    .displayName(m.getDisplayName())
                    .provider("openclaw")
                    .uiProvider("openclaw")
                    .category(m.getCategory())
                    .tags(m.getTags())
                    .description(m.getDescription())
                    .contextWindow(m.getContextWindow())
                    .defaultBaseUrl(m.getDefaultBaseUrl())
                    .build())
                .toList();
        }
        String marketProvider = "claude".equals(p) ? "anthropic" : p;
        return listMarket(null, marketProvider, null);
    }

    private static MarketEntry entry(
        String modelRef,
        String displayName,
        String description,
        String category,
        List<String> tags,
        int contextWindow,
        String defaultBaseUrl
    ) {
        return new MarketEntry(modelRef, displayName, description, category, tags, contextWindow, defaultBaseUrl);
    }

    private static OpenClawEndpointOptionDto endpoint(String id, String label, String baseUrl, String desc) {
        return OpenClawEndpointOptionDto.builder().id(id).label(label).baseUrl(baseUrl).description(desc).build();
    }

    private static OpenClawCatalogModelDto toDto(MarketEntry e) {
        String provider = extractProvider(e.modelRef());
        return OpenClawCatalogModelDto.builder()
            .modelRef(e.modelRef())
            .modelId(extractModelId(e.modelRef()))
            .displayName(e.displayName())
            .provider(provider)
            .uiProvider(toUiProvider(provider))
            .category(e.category())
            .tags(e.tags())
            .description(e.description())
            .contextWindow(e.contextWindow())
            .defaultBaseUrl(e.defaultBaseUrl())
            .build();
    }

    private static boolean matchesCategory(MarketEntry e, String category) {
        if (category == null || category.isBlank() || "all".equals(category)) {
            return true;
        }
        if (category.equals(e.category())) {
            return true;
        }
        return e.tags().contains(category);
    }

    private static boolean matchesProvider(MarketEntry e, String provider) {
        if (provider == null || provider.isBlank()) {
            return true;
        }
        String openclawProvider = extractProvider(e.modelRef());
        String ui = toUiProvider(openclawProvider);
        return provider.equals(openclawProvider) || provider.equals(ui);
    }

    private static boolean matchesQuery(MarketEntry e, String q) {
        if (q.isBlank()) {
            return true;
        }
        return e.modelRef().toLowerCase(Locale.ROOT).contains(q)
            || e.displayName().toLowerCase(Locale.ROOT).contains(q)
            || e.description().toLowerCase(Locale.ROOT).contains(q)
            || e.tags().stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(q));
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public static String extractProvider(String modelRef) {
        if (modelRef == null || !modelRef.contains("/")) {
            return "custom";
        }
        return modelRef.substring(0, modelRef.indexOf('/'));
    }

    public static String extractModelId(String modelRef) {
        if (modelRef == null || !modelRef.contains("/")) {
            return null;
        }
        String id = modelRef.substring(modelRef.indexOf('/') + 1);
        return id.isBlank() ? null : id;
    }

    public static String toUiProvider(String openclawProvider) {
        if (openclawProvider == null) {
            return "custom";
        }
        return UI_PROVIDER_MAP.getOrDefault(openclawProvider.toLowerCase(Locale.ROOT), openclawProvider);
    }

    public static Set<String> configuredRefsFromEndpoints(List<String> endpoints) {
        return endpoints.stream()
            .filter(e -> e != null && e.contains("/"))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
