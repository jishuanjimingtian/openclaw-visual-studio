package com.openclaw.vs.dto;

import lombok.Data;

import java.util.List;

@Data
public class SetOpenClawPrimaryRequest {
    private String modelRef;
    /** Optional failover chain (excluding primary). */
    private List<String> fallbackModelRefs;
}
