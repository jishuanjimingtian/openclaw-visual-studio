package com.openclaw.vs.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ChatSendRequest {

    @NotBlank
    private String sessionKey;

    /** 纯文本消息；与 parts 二选一或组合 */
    private String message;

    /** 结构化消息块（text / image / file 引用） */
    private List<ChatPartDto> parts;

    /** 可选；不传则由服务端生成 UUID */
    private String runId;
}
