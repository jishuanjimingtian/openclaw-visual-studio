package com.openclaw.vs.dto;

import com.openclaw.vs.model.ModelConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ModelConfigView extends ModelConfig {
    private boolean apiKeyConfigured;
    /** 本地库中已保存 Key 的脱敏预览（不含完整密钥） */
    private String apiKeyPreview;
    /** openclaw.json 对应 provider 是否已配置 apiKey */
    private boolean apiKeyInOpenClaw;
    /** OpenClaw 模型引用（provider 为 qwen/openclaw 时与 endpoint 相同） */
    private String modelRef;
    private boolean openclawPrimary;
    /** 是否已在 openclaw.json 的 agents.defaults.models 中注册 */
    private boolean registeredInOpenClaw;
}
