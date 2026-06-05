package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BootstrapSyncResultDto {
    /** agents.defaults.systemPrompt 或 agents.list[].systemPrompt */
    private String targetField;
    private String bootstrapFile;
    private int charCount;
    private int lineCount;
    private String message;
}
