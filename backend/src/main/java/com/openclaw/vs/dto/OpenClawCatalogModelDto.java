package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Suggested OpenClaw model ref for UI pickers (not applied until user saves). */
@Data
@Builder
public class OpenClawCatalogModelDto {
    private String modelRef;
    private String modelId;
    private String displayName;
    private String provider;
    /** UI provider value (e.g. claude for anthropic refs). */
    private String uiProvider;
    private String category;
    private List<String> tags;
    private String description;
    private Integer contextWindow;
    private String defaultBaseUrl;
    /** Whether user already configured this model in the platform. */
    private Boolean configured;
    /** Local DB model id when configured. */
    private String configuredModelId;
}
