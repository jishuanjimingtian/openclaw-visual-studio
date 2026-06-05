package com.openclaw.vs.dto;

import lombok.Data;

@Data
public class ModelConfigRequest {
    private String name;
    private String provider;
    private String endpoint;
    /** 明文 API Key，留空表示不修改 */
    private String apiKey;
    private Boolean enabled;
    /** 是否将 API Key 写入 ~/.openclaw/openclaw.json 的 models.providers */
    private Boolean syncApiKeyToOpenClaw;
    /** @deprecated 等同 registerInOpenClaw + setAsOpenClawPrimary；请使用下方独立开关 */
    private Boolean applyToOpenClaw;
    /** 仅注册到 openclaw.json（agents.defaults.models + provider.models） */
    private Boolean registerInOpenClaw;
    /** 设为 OpenClaw 主模型（agents.defaults.model.primary） */
    private Boolean setAsOpenClawPrimary;
    /** 与 setAsOpenClawPrimary 一起写入 agents.defaults.model.fallbacks */
    private java.util.List<String> fallbackModelRefs;
    /** 写入 models.providers.qwen.baseUrl（仅 Qwen/OpenClaw provider） */
    private String openclawBaseUrl;
}
