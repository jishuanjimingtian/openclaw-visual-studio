package com.openclaw.vs.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** 常见 Skill 英文术语与 slug 的中文对照（离线即时翻译） */
final class SkillGlossary {

    private static final Map<String, String> SLUG_TITLES = Map.ofEntries(
        Map.entry("self-improving-agent", "自我改进助手"),
        Map.entry("agent-browser", "浏览器自动化"),
        Map.entry("agent-browser-clawdbot", "浏览器自动化（Clawdbot）"),
        Map.entry("bsession", "浏览器会话"),
        Map.entry("gifgrep", "GIF 搜索"),
        Map.entry("openclaw-weixin", "微信集成"),
        Map.entry("duckduckgo", "DuckDuckGo 搜索"),
        Map.entry("1password", "1Password 密码管理"),
        Map.entry("apple-notes", "Apple 备忘录"),
        Map.entry("qwen", "通义千问"),
        Map.entry("coding", "编程助手"),
        Map.entry("summarize", "内容摘要"),
        Map.entry("weather", "天气查询")
    );

    private static final LinkedHashMap<String, String> TERMS = new LinkedHashMap<>();

    static {
        put("self-improving", "自我改进");
        put("openclaw", "OpenClaw");
        put("clawhub", "ClawHub");
        put("browser", "浏览器");
        put("automation", "自动化");
        put("automate", "自动化");
        put("agent", "智能体");
        put("agents", "智能体");
        put("assistant", "助手");
        put("skill", "技能");
        put("skills", "技能");
        put("search", "搜索");
        put("coding", "编程");
        put("code", "代码");
        put("coder", "编程");
        put("developer", "开发者");
        put("development", "开发");
        put("workflow", "工作流");
        put("workflows", "工作流");
        put("memory", "记忆");
        put("learning", "学习");
        put("learnings", "经验记录");
        put("error", "错误");
        put("errors", "错误");
        put("fallback", "备用");
        put("fallbacks", "备用");
        put("primary", "主");
        put("model", "模型");
        put("models", "模型");
        put("provider", "提供商");
        put("providers", "提供商");
        put("api", "API");
        put("endpoint", "端点");
        put("endpoints", "端点");
        put("install", "安装");
        put("download", "下载");
        put("downloads", "下载");
        put("github", "GitHub");
        put("git", "Git");
        put("repository", "仓库");
        put("repositories", "仓库");
        put("headless", "无头");
        put("snapshot", "快照");
        put("snapshots", "快照");
        put("accessibility", "无障碍");
        put("tree", "树");
        put("tool", "工具");
        put("tools", "工具");
        put("plugin", "插件");
        put("plugins", "插件");
        put("integration", "集成");
        put("configure", "配置");
        put("configuration", "配置");
        put("session", "会话");
        put("sessions", "会话");
        put("message", "消息");
        put("messages", "消息");
        put("chat", "对话");
        put("prompt", "提示词");
        put("prompts", "提示词");
        put("image", "图像");
        put("images", "图像");
        put("file", "文件");
        put("files", "文件");
        put("data", "数据");
        put("web", "网页");
        put("website", "网站");
        put("scrape", "抓取");
        put("scraping", "抓取");
        put("fetch", "获取");
        put("calendar", "日历");
        put("email", "邮件");
        put("note", "笔记");
        put("notes", "笔记");
        put("weather", "天气");
        put("summary", "摘要");
        put("summarize", "摘要");
        put("translate", "翻译");
        put("translation", "翻译");
        put("continuous", "持续");
        put("improvement", "改进");
        put("improvements", "改进");
        put("capture", "捕获");
        put("captures", "捕获");
        put("enable", "启用");
        put("enabled", "已启用");
        put("optional", "可选");
        put("required", "必需");
        put("default", "默认");
        put("version", "版本");
        put("update", "更新");
        put("updates", "更新");
    }

    private SkillGlossary() {}

    private static void put(String en, String zh) {
        TERMS.put(en.toLowerCase(Locale.ROOT), zh);
    }

    static String titleForSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        return SLUG_TITLES.get(slug.trim().toLowerCase(Locale.ROOT));
    }

    static String localizeText(String text) {
        if (text == null || text.isBlank() || SkillLocalizationService.isMostlyChinese(text)) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : TERMS.entrySet()) {
            String pattern = "\\b" + Pattern.quote(entry.getKey()) + "\\b";
            result = result.replaceAll("(?i)" + pattern, entry.getValue());
        }
        return cleanupSpaces(result);
    }

    static String localizeName(String name, String slug) {
        String bySlug = titleForSlug(slug);
        if (bySlug != null) {
            return bySlug;
        }
        if (name == null || name.isBlank()) {
            return name;
        }
        if (SkillLocalizationService.isMostlyChinese(name)) {
            return name;
        }
        return localizeText(name);
    }

    private static String cleanupSpaces(String s) {
        return s.replaceAll("\\s{2,}", " ").trim();
    }
}
