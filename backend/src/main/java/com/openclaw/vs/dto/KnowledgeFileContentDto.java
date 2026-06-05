package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeFileContentDto {
    private String path;
    private String content;
    private String kind;
    private long sizeBytes;
    private String updatedAt;
}
