package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardRecentDto {
    private List<DashboardRecentConversationDto> recentConversations;
    private List<DashboardRecentDeploymentDto> recentDeployments;
}
