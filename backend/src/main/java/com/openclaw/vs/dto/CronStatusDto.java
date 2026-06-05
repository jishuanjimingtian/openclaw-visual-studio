package com.openclaw.vs.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CronStatusDto {
    private boolean gatewayConnected;
    private Integer gatewayPort;
    private String gatewayWsUrl;
    private String connectionHint;
    private JsonNode status;
}
