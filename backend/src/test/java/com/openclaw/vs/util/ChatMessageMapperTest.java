package com.openclaw.vs.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.vs.dto.ChatMessageDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void humanizeStreamError_usesErrorMessageWhenPresent() throws Exception {
        var msg = mapper.readTree("""
            {
              "role": "assistant",
              "stopReason": "error",
              "errorMessage": "401 Incorrect API key",
              "content": [{"type": "text", "text": "[assistant turn failed before producing content]"}]
            }
            """);

        ChatMessageDto dto = ChatMessageMapper.mapMessage(msg);
        assertThat(dto.getContent()).isEqualTo("模型调用失败：401 Incorrect API key");
    }

    @Test
    void isVisible_filtersInternalReplies() throws Exception {
        assertThat(ChatMessageMapper.isVisible(ChatMessageDto.builder()
            .role("assistant").content("HEARTBEAT_OK").build())).isFalse();
        assertThat(ChatMessageMapper.isVisible(ChatMessageDto.builder()
            .role("assistant").content("NO_REPLY").build())).isFalse();
        assertThat(ChatMessageMapper.isVisible(ChatMessageDto.builder()
            .role("assistant").content("你好").build())).isTrue();
    }

    @Test
    void sanitizePreview_hidesInternalReplies() {
        assertThat(ChatMessageMapper.sanitizePreview("HEARTBEAT_OK")).isNull();
        assertThat(ChatMessageMapper.sanitizePreview("  heartbeat_ok  ")).isNull();
        assertThat(ChatMessageMapper.sanitizePreview("任务已完成")).isEqualTo("任务已完成");
    }

    @Test
    void humanizeStreamError_genericFallbackWithoutDetails() throws Exception {
        var msg = mapper.readTree("""
            {
              "role": "assistant",
              "stopReason": "error",
              "content": [{"type": "text", "text": "[assistant turn failed before producing content]"}]
            }
            """);

        ChatMessageDto dto = ChatMessageMapper.mapMessage(msg);
        assertThat(dto.getContent()).contains("模型未返回有效内容");
        assertThat(dto.getContent()).contains("openclaw logs");
    }
}
