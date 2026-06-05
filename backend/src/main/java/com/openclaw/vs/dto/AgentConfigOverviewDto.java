package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AgentConfigOverviewDto {
    private String configPath;
    private boolean configExists;
    private String workspace;
    private Integer timeoutSeconds;
    private String primaryModelRef;
    private List<String> fallbackModelRefs;
    private String toolsProfile;
    /** openclaw.json 内显式配置的 systemPrompt */
    private String systemPrompt;
    /** none | config | bootstrap */
    private String promptSource;
    private String bootstrapFile;
    private Integer bootstrapLineCount;
    private String bootstrapPreview;
    private Double defaultTemperature;
    private Integer defaultMaxTokens;
    private boolean temperatureFromConfig;
    private boolean maxTokensFromConfig;
}
