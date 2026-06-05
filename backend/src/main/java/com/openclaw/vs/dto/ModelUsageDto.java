package com.openclaw.vs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelUsageDto {
    private String model;
    private long sessionCount;
    private double percentage;
    private long totalTokens;
    private long messageCount;
    private double tokenPercentage;
}
