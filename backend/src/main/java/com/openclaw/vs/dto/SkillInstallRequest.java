package com.openclaw.vs.dto;

import lombok.Data;

@Data
public class SkillInstallRequest {
    private String slug;
    private String source;
    private String version;
    /** workspace（默认，当前 Agent 工作区）| global | both */
    private String scope;
    private String name;
    private String author;
    private String description;
    private String license;
    private double rating;
    private int downloads;
    /** GitHub 仓库全名 owner/repo，source=GitHub 时使用 */
    private String githubRepo;
    /** 安装后将 SKILL.md 译为中文（默认 true） */
    private Boolean localizeToChinese;
}
