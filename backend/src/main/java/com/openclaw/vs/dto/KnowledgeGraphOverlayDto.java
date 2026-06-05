package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class KnowledgeGraphOverlayDto {
    private List<String> highlightNodeIds;
    private List<KnowledgeGraphEdgeDto> edges;
}
