package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class KnowledgeDiscoverResultDto {
    private String chosenWorkspacePath;
    private String resolutionSource;
    private boolean persistedToConfig;
    private int memoryFileCount;
    private List<KnowledgeWorkspaceCandidateDto> candidates;
    private KnowledgeOverviewDto overview;
}
