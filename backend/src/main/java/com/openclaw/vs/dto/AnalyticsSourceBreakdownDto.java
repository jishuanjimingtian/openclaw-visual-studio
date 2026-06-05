package com.openclaw.vs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSourceBreakdownDto {
    private List<SourceBreakdownRowDto> rows;
    private boolean gatewayConnected;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceBreakdownRowDto {
        private String metric;
        private String label;
        private long local;
        private long gateway;
        private long total;
    }
}
