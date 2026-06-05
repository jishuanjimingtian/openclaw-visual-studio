package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardOpenClawMetaDto {
    private String openclawConfigPath;
    private String primaryModelRef;
}
