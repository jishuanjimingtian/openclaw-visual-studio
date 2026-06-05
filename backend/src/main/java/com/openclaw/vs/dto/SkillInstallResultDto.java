package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SkillInstallResultDto {
    private boolean success;
    private String message;
    private String skillId;
    /** 主安装目录（工作区优先） */
    private String installPath;
    private List<String> installPaths;
    private boolean openclawReady;
    /** 是否已将 SKILL.md 本地化为中文 */
    private boolean skillMdLocalized;
    /** openclaw.json 路径 */
    private String configPath;
    /** agents.defaults.workspace 路径 */
    private String workspacePath;
    /** 是否在本次安装中自动写入了工作区配置 */
    private boolean workspaceAutoConfigured;
}
