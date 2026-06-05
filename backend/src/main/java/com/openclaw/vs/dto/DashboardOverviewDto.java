package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardOverviewDto {
    private DashboardStatsDto stats;
    private SystemMetricsDto metrics;
    private DashboardGatewayDto gateway;
    private String openclawConfigPath;
    private String primaryModelRef;
    private List<DashboardRecentConversationDto> recentConversations;
    private List<DashboardRecentDeploymentDto> recentDeployments;
}
