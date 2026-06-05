package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SkillMarketItemDto {
    /** 市场唯一标识（ClawHub slug 或 GitHub repo full_name） */
    private String slug;
    private String name;
    private String version;
    private String author;
    private String description;
    /** ClawdHub | GitHub */
    private String source;
    /** not_installed | installed | update_available */
    private String status;
    private double rating;
    private int downloads;
    private int stars;
    private String license;
    private String homepageUrl;
    private String updatedAt;
    private List<String> tags;
    /** 本地已安装记录 ID（若已安装） */
    private String installedId;
    /** GitHub 仓库全名 owner/repo */
    private String githubRepo;
    /** OpenClaw 磁盘安装路径（若已安装） */
    private String installPath;

    /** 市场原始英文名（本地化后保留） */
    private String nameOriginal;
    private String descriptionOriginal;
    /** 中文译文（与 name/description 在 locale=zh 时一致） */
    private String nameZh;
    private String descriptionZh;
    private boolean localized;
}
