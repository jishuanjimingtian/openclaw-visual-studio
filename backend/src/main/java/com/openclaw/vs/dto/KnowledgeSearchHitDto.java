package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeSearchHitDto {
    private String path;
    private Integer lineStart;
    private Integer lineEnd;
    private String snippet;
    private Double score;
    private String nodeId;
}
