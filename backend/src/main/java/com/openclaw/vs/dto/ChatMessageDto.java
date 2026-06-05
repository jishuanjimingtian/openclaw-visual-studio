package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatMessageDto {
    private String role;
    /** 纯文本摘要，兼容旧客户端 */
    private String content;
    private List<ChatPartDto> parts;
    private Long timestamp;
}
