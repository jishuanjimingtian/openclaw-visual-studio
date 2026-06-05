package com.openclaw.vs.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CronRunsPageDto {
    private boolean gatewayConnected;
    private Integer gatewayPort;
    private String gatewayWsUrl;
    private String connectionHint;
    private String jobId;
    private List<JsonNode> entries;
    private Integer total;
    private Integer limit;
    private Integer offset;
}
