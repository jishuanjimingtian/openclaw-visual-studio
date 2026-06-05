package com.openclaw.vs.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "model_configs")
@NoArgsConstructor @AllArgsConstructor @Builder
public class ModelConfig {

    @Id
    @Column(length = 36)
    private String id;

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "名称长度不能超过 100")
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank(message = "Provider 不能为空")
    @Size(max = 50, message = "Provider 长度不能超过 50")
    @Column(nullable = false, length = 50)
    private String provider;

    @NotBlank(message = "Endpoint 不能为空")
    @Size(max = 500, message = "Endpoint 长度不能超过 500")
    @Column(nullable = false, length = 500)
    private String endpoint;

    @Column(name = "api_key", length = 500)
    private String apiKey;

    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}