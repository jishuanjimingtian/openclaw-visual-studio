package com.openclaw.vs.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CronJobDto {
    private String id;
    private String name;
    private String description;
    private Boolean enabled;
    /** disabled | running | ok | error | skipped | idle */
    private String status;
    private String sessionTarget;
    private String agentId;
    private JsonNode schedule;
    private JsonNode payload;
    private JsonNode delivery;
    private JsonNode state;
    private Long nextRunAtMs;
    private Long updatedAtMs;
    private Long createdAtMs;
}
