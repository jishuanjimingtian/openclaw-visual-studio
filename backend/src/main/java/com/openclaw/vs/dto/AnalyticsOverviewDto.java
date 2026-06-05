package com.openclaw.vs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsOverviewDto {
    private long totalMessages;
    private long totalConversations;
    private long totalTokens;
    private long todayMessages;
    private long todayTokens;
    private int activeModels;
}
