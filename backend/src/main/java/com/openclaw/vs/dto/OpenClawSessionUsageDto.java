package com.openclaw.vs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenClawSessionUsageDto {

    private String sessionKey;
    private long totalTokens;
    private long inputTokens;
    private long outputTokens;
    private Double totalCost;
    /** 数据来自 Gateway usage API；false 表示仅会话列表近似值或不可用 */
    private boolean fromGateway;
}
