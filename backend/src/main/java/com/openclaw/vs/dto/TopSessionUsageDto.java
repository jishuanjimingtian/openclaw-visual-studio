package com.openclaw.vs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopSessionUsageDto {
    private String id;
    private String title;
    private String model;
    private long totalTokens;
    private long messageCount;
    private String updatedAt;
    /** local | gateway */
    private String source;
    private long inputTokens;
    private long outputTokens;
    private Double totalCost;
}
