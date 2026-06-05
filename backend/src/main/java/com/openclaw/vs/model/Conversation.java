package com.openclaw.vs.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "conversations")
@NoArgsConstructor @AllArgsConstructor @Builder
public class Conversation {

    @Id
    @Column(length = 36)
    private String id;

    @NotBlank(message = "会话标题不能为空")
    @Size(max = 255, message = "标题长度不能超过 255")
    @Column(nullable = false, length = 255)
    private String title;

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称长度不能超过 100")
    @Column(nullable = false, length = 100)
    @Builder.Default
    private String model = "gpt-4";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Size(max = 500, message = "标签长度不能超过 500")
    @Column(length = 500)
    private String tags;

    @Builder.Default
    private boolean archived = false;

    @Column(name = "openclaw_session_key", length = 255)
    private String openclawSessionKey;

    @Column(name = "last_message_preview", length = 2000)
    private String lastMessagePreview;

    @Builder.Default
    @Column(name = "message_count")
    private int messageCount = 0;

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}