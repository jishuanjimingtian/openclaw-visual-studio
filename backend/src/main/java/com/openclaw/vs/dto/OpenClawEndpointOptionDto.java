package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OpenClawEndpointOptionDto {
    private String id;
    private String label;
    private String baseUrl;
    private String description;
}
