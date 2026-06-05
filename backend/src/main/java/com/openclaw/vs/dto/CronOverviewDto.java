package com.openclaw.vs.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CronOverviewDto {
    private boolean gatewayConnected;
    private Integer gatewayPort;
    private String gatewayWsUrl;
    private String connectionHint;
    private JsonNode schedulerStatus;
    private List<CronJobDto> jobs;
    private Integer total;
    private Integer limit;
    private Integer offset;
}
