package com.openclaw.vs.service;

import com.openclaw.vs.dto.SystemMetricsDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemMetricsServiceTest {

    private final SystemMetricsService service = new SystemMetricsService();

    @Test
    void collect_returnsBoundedMetrics() {
        SystemMetricsDto metrics = service.collect(3, 1200);

        assertThat(metrics.getCpu()).isBetween(0.0, 100.0);
        assertThat(metrics.getMemory()).isBetween(0.0, 100.0);
        assertThat(metrics.getDisk()).isBetween(0.0, 100.0);
        assertThat(metrics.getUptime()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.getSessionCount()).isEqualTo(3);
        assertThat(metrics.getTokenUsage()).isEqualTo(1200);
    }
}
