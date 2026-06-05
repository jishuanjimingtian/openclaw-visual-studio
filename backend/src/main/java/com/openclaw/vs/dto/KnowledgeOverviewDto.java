package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeOverviewDto {
    private String workspacePath;
    private String configPath;
    private boolean workspaceAutoConfigured;
    private int fileCount;
    private long memoryMdSizeBytes;
    private int dailyNoteCount;
    private boolean dreamsPresent;
    private int workspaceConfigCount;
    private int dreamShardCount;
    private boolean gatewayConnected;
    private Integer gatewayPort;
    private String indexStatusSummary;
    private String lastSyncedAt;
    /** How the active workspace was chosen */
    private String resolutionSource;
    /** Configured agents.defaults.workspace (may differ when empty) */
    private String configuredWorkspacePath;
    private java.util.List<KnowledgeWorkspaceCandidateDto> candidates;
}
