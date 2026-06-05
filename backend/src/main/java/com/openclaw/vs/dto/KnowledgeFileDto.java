package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeFileDto {
    private String path;
    private String kind;
    private long sizeBytes;
    private String updatedAt;
    private int lineCount;
}
