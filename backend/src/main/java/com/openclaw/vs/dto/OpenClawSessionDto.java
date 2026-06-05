package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OpenClawSessionDto {
    private String key;
    private String title;
    private String model;
    private String modelProvider;
    private String lastMessagePreview;
    private Long updatedAt;
    private Integer totalTokens;
    private Boolean hasActiveRun;
    private String kind;
    private String channel;
    private String sessionId;
}
