package com.openclaw.vs.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class CronJobUpdateRequest {
    private JsonNode patch;
}
