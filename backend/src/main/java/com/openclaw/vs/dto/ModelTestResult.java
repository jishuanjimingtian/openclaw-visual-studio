package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModelTestResult {
    private boolean success;
    private String message;
    /** 连通性探测耗时（毫秒），未探测时为 null */
    private Long latencyMs;
}
