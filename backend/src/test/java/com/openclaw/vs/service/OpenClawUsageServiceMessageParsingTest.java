package com.openclaw.vs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawUsageServiceMessageParsingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final OpenClawUsageService service = new OpenClawUsageService(null, null);

    @Test
    void readAggregateMessageTotal_supportsDailySumFallback() throws Exception {
        var payload = MAPPER.readTree("""
            {
              "aggregates": {
                "daily": [
                  { "date": "2026-05-30", "messages": 100 },
                  { "date": "2026-05-31", "messageCount": 200 }
                ]
              }
            }
            """);

        assertThat(service.readAggregateMessageTotal(payload)).isEqualTo(300L);
    }

    @Test
    void readTodayTokensFromCostPayload_prefersDailyOverTotals() throws Exception {
        var payload = MAPPER.readTree("""
            {
              "totals": { "totalTokens": 6000000 },
              "daily": [
                { "date": "2026-06-02", "tokens": 125000 }
              ]
            }
            """);
        assertThat(service.readTodayTokensFromCostPayload(payload, LocalDate.of(2026, 6, 2)))
            .isEqualTo(125_000L);
        assertThat(service.readTodayTokensFromCostPayload(payload, LocalDate.of(2026, 6, 1)))
            .isZero();
    }

    @Test
    void readDailyMessageCount_readsAlternateFields() throws Exception {
        var row = MAPPER.readTree("""
            { "date": "2026-06-01", "messageCount": 42 }
            """);
        assertThat(OpenClawUsageService.readDailyMessageCount(row)).isEqualTo(42L);
    }
}
