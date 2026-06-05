package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStatsDto {
    private long totalConversations;
    private int activeModels;
    private long totalModels;
    private int activeDeployments;
    private long totalDeployments;
    private int installedSkills;
    private long totalSkills;
    private long totalMessages;
    private long messagesToday;
}
