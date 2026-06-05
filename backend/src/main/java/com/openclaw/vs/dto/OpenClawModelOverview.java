package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OpenClawModelOverview {
    private String configPath;
    private String primaryModelRef;
    /** Failover chain from agents.defaults.model.fallbacks */
    private List<String> fallbackModelRefs;
    /** models.providers.qwen.baseUrl when present */
    private String qwenBaseUrl;
    /** models.providers.deepseek.baseUrl when present */
    private String deepseekBaseUrl;
    private List<OpenClawModelEntry> models;
}
