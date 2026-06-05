package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeGraphEdgeDto {
    private String id;
    private String source;
    private String target;
    private String kind;
    private Double value;
    private String label;
}
