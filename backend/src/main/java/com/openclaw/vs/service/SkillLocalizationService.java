package com.openclaw.vs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openclaw.vs.config.SkillMarketProperties;
import com.openclaw.vs.dto.SkillMarketItemDto;
import com.openclaw.vs.gateway.OpenClawGatewayConfigReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillLocalizationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(12))
        .build();

    private final SkillMarketProperties properties;
    private final ConcurrentHashMap<String, String> llmCache = new ConcurrentHashMap<>();

    public void localizeItems(List<SkillMarketItemDto> items, String locale, boolean useLlm) {
        if (items == null || items.isEmpty() || !isChineseLocale(locale)) {
            return;
        }
        for (SkillMarketItemDto item : items) {
            localizeItem(item, useLlm);
        }
    }

    public void localizeItem(SkillMarketItemDto item, boolean useLlm) {
        if (item == null) {
            return;
        }
        item.setNameOriginal(item.getName());
        item.setDescriptionOriginal(item.getDescription());

        String nameZh = SkillGlossary.localizeName(item.getName(), item.getSlug());
        String descZh = SkillGlossary.localizeText(item.getDescription());

        if (useLlm && properties.isTranslateDescriptions() && shouldLlmTranslate(descZh)) {
            descZh = translateWithLlm(item.getSlug(), "description", item.getDescription(), descZh);
        }
        if (useLlm && properties.isTranslateNames() && shouldLlmTranslate(nameZh)) {
            nameZh = translateWithLlm(item.getSlug(), "name", item.getName(), nameZh);
        }

        item.setNameZh(nameZh);
        item.setDescriptionZh(descZh);
        item.setLocalized(true);
        item.setName(nameZh != null ? nameZh : item.getName());
        item.setDescription(descZh != null ? descZh : item.getDescription());
    }

    /**
     * 将已安装目录中的 SKILL.md 译为中文（保留 Markdown 结构）。
     */
    public boolean localizeInstalledSkill(Path skillDir) {
        if (skillDir == null || !Files.isDirectory(skillDir)) {
            return false;
        }
        Path skillMd = skillDir.resolve("SKILL.md");
        if (!Files.isRegularFile(skillMd)) {
            return false;
        }
        try {
            String original = Files.readString(skillMd, StandardCharsets.UTF_8);
            if (isMostlyChinese(original)) {
                return true;
            }
            String translated = translateSkillMarkdown(original);
            if (translated == null || translated.isBlank() || translated.equals(original)) {
                return false;
            }
            Files.writeString(skillMd, translated, StandardCharsets.UTF_8);
            log.info("Localized SKILL.md to Chinese: {}", skillDir);
            return true;
        } catch (Exception e) {
            log.warn("Failed to localize SKILL.md in {}: {}", skillDir, e.getMessage());
            return false;
        }
    }

    public String translateSkillMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank() || isMostlyChinese(markdown)) {
            return markdown;
        }
        String glossary = SkillGlossary.localizeText(markdown);
        if (!properties.isLocalizeOnInstall()) {
            return glossary;
        }
        return translateWithLlm("skill-md", "skill-md", markdown, glossary);
    }

    static boolean isChineseLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return true;
        }
        String l = locale.trim().toLowerCase(Locale.ROOT);
        return l.startsWith("zh") || "cn".equals(l);
    }

    static boolean isMostlyChinese(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        int cjk = 0;
        int letters = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (c >= 0x4E00 && c <= 0x9FFF) {
                    cjk++;
                }
            }
        }
        if (letters < 8) {
            return false;
        }
        return cjk * 100 / letters >= 25;
    }

    private boolean shouldLlmTranslate(String glossaryResult) {
        if (glossaryResult == null || glossaryResult.isBlank()) {
            return false;
        }
        if (isMostlyChinese(glossaryResult)) {
            return false;
        }
        long latin = glossaryResult.chars().filter(Character::isLetter).count();
        long cjk = glossaryResult.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        return latin > cjk * 2;
    }

    private String translateWithLlm(String slug, String field, String source, String fallback) {
        if (source == null || source.isBlank()) {
            return fallback;
        }
        String cacheKey = slug + ":" + field + ":" + source.hashCode();
        String cached = llmCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        LlmCredentials creds = resolveQwenCredentials();
        if (creds == null) {
            return fallback;
        }
        try {
            String prompt = field.equals("skill-md")
                ? "你是技术文档翻译。将下面的 SKILL.md 全文翻译为简体中文，保留 YAML frontmatter、Markdown 标题层级、代码块与命令行不变，只翻译自然语言说明。直接输出译文，不要解释：\n\n"
                + source
                : "将以下 OpenClaw Skill " + field + " 翻译为简洁自然的中文（保留 OpenClaw、API、GitHub 等专有名词）。只输出一行译文：\n" + source;

            String result = callChat(creds, prompt);
            if (result != null && !result.isBlank()) {
                llmCache.put(cacheKey, result);
                return result.trim();
            }
        } catch (Exception e) {
            log.debug("LLM translate failed for {}: {}", slug, e.getMessage());
        }
        return fallback;
    }

    private String callChat(LlmCredentials creds, String userContent) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", creds.model());
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", userContent);
        body.put("max_tokens", userContent.length() > 2000 ? 4096 : 512);
        body.put("temperature", 0.2);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(creds.baseUrl().replaceAll("/$", "") + "/chat/completions"))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + creds.apiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }
        JsonNode root = MAPPER.readTree(response.body());
        return root.path("choices").path(0).path("message").path("content").asText(null);
    }

    private LlmCredentials resolveQwenCredentials() {
        try {
            Path configPath = OpenClawGatewayConfigReader.defaultConfigPath();
            if (!Files.isRegularFile(configPath)) {
                return null;
            }
            JsonNode root = MAPPER.readTree(configPath.toFile());
            JsonNode qwen = root.path("models").path("providers").path("qwen");
            String apiKey = qwen.path("apiKey").asText(null);
            if (apiKey == null || apiKey.isBlank()) {
                return null;
            }
            String baseUrl = qwen.path("baseUrl").asText("https://dashscope.aliyuncs.com/compatible-mode/v1");
            String model = properties.getTranslateModel();
            if (model == null || model.isBlank()) {
                model = "qwen-plus";
            }
            return new LlmCredentials(apiKey.trim(), baseUrl.trim(), model.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private record LlmCredentials(String apiKey, String baseUrl, String model) {}
}
