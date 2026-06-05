package com.openclaw.vs.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "installed_skills")
@NoArgsConstructor @AllArgsConstructor @Builder
public class InstalledSkill {

    @Id
    @Column(length = 36)
    private String id;

    @NotBlank(message = "Skill 名称不能为空")
    @Size(max = 200, message = "名称长度不能超过 200")
    @Column(nullable = false, length = 200)
    private String name;

    @NotBlank(message = "版本号不能为空")
    @Size(max = 50, message = "版本号长度不能超过 50")
    @Column(nullable = false, length = 50)
    private String version;

    @Size(max = 200, message = "作者名长度不能超过 200")
    @Column(length = 200)
    private String author;

    @Lob
    private String description;

    @NotBlank(message = "来源不能为空")
    @Size(max = 50, message = "来源长度不能超过 50")
    @Column(nullable = false, length = 50)
    private String source;

    @Column(length = 20)
    @Builder.Default
    private String status = "installed";

    @Column(name = "installed_at", nullable = false)
    @Builder.Default
    private LocalDateTime installedAt = LocalDateTime.now();

    @Builder.Default
    private double rating = 0.0;

    @Builder.Default
    private int downloads = 0;

    @Size(max = 100, message = "许可证长度不能超过 100")
    @Column(length = 100)
    private String license;

    /** ClawHub slug 或 GitHub repo 标识，用于与市场条目关联 */
    @Column(name = "market_slug", length = 200)
    private String marketSlug;

    @Column(name = "install_path", length = 1000)
    private String installPath;
}