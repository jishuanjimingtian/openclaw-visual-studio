package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class KnowledgeBootstrapDto {
    private KnowledgeOverviewDto overview;
    private List<KnowledgeFileDto> files;
    private KnowledgeGraphDto graph;
}
