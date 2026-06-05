package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class KnowledgeSearchResultDto {
    private List<KnowledgeSearchHitDto> hits;
    private boolean fallback;
    private String source;
    private KnowledgeGraphOverlayDto graphOverlay;
}
