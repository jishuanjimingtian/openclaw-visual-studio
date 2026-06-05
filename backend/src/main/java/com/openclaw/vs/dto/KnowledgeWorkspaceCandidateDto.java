package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeWorkspaceCandidateDto {
    private String path;
    private int memoryFileCount;
    private String source;
    private boolean configured;
    private boolean active;
}
