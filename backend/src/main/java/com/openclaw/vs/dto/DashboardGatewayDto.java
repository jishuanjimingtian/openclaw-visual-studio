package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardGatewayDto {
    private String status;
    private Integer port;
    private String endpoint;
    private Long pid;
    private boolean wsConnected;
    private String message;
}
