package com.openclaw.vs.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeIndexStatusDto {
    private boolean gatewayConnected;
    private boolean available;
    private String summary;
    private JsonNode raw;
    private String error;
}
