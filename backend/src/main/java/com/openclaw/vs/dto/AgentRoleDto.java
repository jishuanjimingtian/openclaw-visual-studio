package com.openclaw.vs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentRoleDto {
    private String id;
    private String name;
    /** openclaw.json 内显式配置的 systemPrompt（不含工作区 bootstrap 文件） */
    private String systemPrompt;
    /** none | config | bootstrap */
    private String promptSource;
    private String bootstrapFile;
    private Integer bootstrapLineCount;
    private String bootstrapPreview;
    private double temperature;
    private int maxTokens;
    private boolean temperatureFromConfig;
    private boolean maxTokensFromConfig;
    private String defaultModel;
    /** OpenClaw 默认 Agent，不可删除 */
    private boolean builtin;
    /** 数据来自 openclaw.json */
    private boolean openclaw;
    /** agents.list[].default */
    private boolean defaultAgent;
    private String workspace;
    /** 保存全局默认时写入 agents.defaults.timeoutSeconds */
    private Integer timeoutSeconds;
    private long updatedAt;
}
