package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CronListPageDto {
    private boolean gatewayConnected;
    private Integer gatewayPort;
    private String gatewayWsUrl;
    private String connectionHint;
    private List<CronJobDto> jobs;
    private Integer total;
    private Integer limit;
    private Integer offset;
}
