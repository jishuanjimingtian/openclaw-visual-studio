package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeIndexRebuildResultDto {
    private boolean success;
    private String message;
    private int exitCode;
}
