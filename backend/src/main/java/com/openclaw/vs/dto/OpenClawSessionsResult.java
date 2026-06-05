package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OpenClawSessionsResult {
    private boolean gatewayConnected;
    private Integer gatewayPort;
    private String gatewayWsUrl;
    private String connectionHint;
    private String defaultModel;
    private List<OpenClawSessionDto> sessions;
}
