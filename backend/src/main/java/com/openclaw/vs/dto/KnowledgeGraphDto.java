package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class KnowledgeGraphDto {
    private List<KnowledgeGraphNodeDto> nodes;
    private List<KnowledgeGraphEdgeDto> edges;
    private KnowledgeGraphMetaDto meta;
}
