package com.openclaw.vs.dto;

import lombok.Data;

@Data
public class KnowledgeSearchRequest {
    private String query;
    private Integer limit;
}
