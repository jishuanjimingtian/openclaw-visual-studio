package com.openclaw.vs.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "messages")
@NoArgsConstructor @AllArgsConstructor @Builder
public class Message {

    @Id
    @Column(length = 36)
    private String id;

    @NotBlank(message = "会话ID不能为空")
    @Size(max = 36, message = "会话ID长度不能超过 36")
    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    @NotNull(message = "角色不能为空")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;

    @NotBlank(message = "消息内容不能为空")
    @Lob
    @Column(nullable = false)
    private String content;

    @PositiveOrZero(message = "Token 数不能为负数")
    private Integer tokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum MessageRole {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }
}