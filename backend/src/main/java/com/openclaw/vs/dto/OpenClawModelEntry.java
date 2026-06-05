package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OpenClawModelEntry {
    /** OpenClaw 模型引用，如 qwen/qwen3.5-plus */
    private String modelRef;
    private String provider;
    private String displayName;
    private boolean primary;
    private boolean apiKeyConfigured;
}
