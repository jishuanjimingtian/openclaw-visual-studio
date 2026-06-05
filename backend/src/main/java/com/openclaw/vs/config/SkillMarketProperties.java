package com.openclaw.vs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.skill-market")
public class SkillMarketProperties {

    private int cacheSeconds = 300;
    private String clawhubBaseUrl = "https://clawhub.ai";
    private String githubSearchQuery = "openclaw skill in:name,description,readme";
    private boolean githubEnabled = true;
    private boolean clawhubEnabled = true;
    private List<SourceConfig> sources = new ArrayList<>();

    /** 市场列表默认语言 */
    private String defaultLocale = "zh";
    /** 安装后是否将 SKILL.md 译为中文 */
    private boolean localizeOnInstall = true;
    /** 列表描述是否调用 Qwen 深度翻译（较慢，需 openclaw.json 中 qwen apiKey） */
    private boolean translateDescriptions = false;
    private boolean translateNames = false;
    private String translateModel = "qwen-plus";

    @Data
    public static class SourceConfig {
        private String name;
        private String url;
        private boolean enabled = true;
    }
}
