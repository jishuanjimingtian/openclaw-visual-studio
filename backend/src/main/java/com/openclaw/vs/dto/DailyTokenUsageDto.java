package com.openclaw.vs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyTokenUsageDto {
    private String date;
    private long tokens;
    private long localTokens;
    private long gatewayTokens;
    private long messageCount;
}
