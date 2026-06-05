package com.openclaw.vs.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.vs.dto.ChatMessageDto;
import com.openclaw.vs.dto.ChatPartDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageMapperPartsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapMessage_parsesImagePartMetadataWithoutBinary() throws Exception {
        var msg = mapper.readTree("""
            {
              "role": "user",
              "content": [
                {"type": "text", "text": "请看图"},
                {
                  "type": "image",
                  "path": "D:/ws/.openclaw-media/chat/2026-06/11111111-1111-1111-1111-111111111111/original.jpg",
                  "name": "photo.jpg",
                  "mime": "image/jpeg",
                  "sizeBytes": 1200
                }
              ]
            }
            """);

        ChatMessageDto dto = ChatMessageMapper.mapMessage(msg);
        assertThat(dto.getContent()).isEqualTo("请看图");
        assertThat(dto.getParts()).hasSize(2);
        ChatPartDto image = dto.getParts().get(1);
        assertThat(image.getType()).isEqualTo("image");
        assertThat(image.getAttachmentId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(image.getName()).isEqualTo("photo.jpg");
        assertThat(ChatMessageMapper.isVisible(dto)).isTrue();
    }

    @Test
    void mapMessage_stripsInlineBinaryToPlaceholder() throws Exception {
        var msg = mapper.readTree("""
            {
              "role": "user",
              "content": [
                {"type": "image", "name": "x.png", "mime": "image/png", "data": "%s"}
              ]
            }
            """.formatted("a".repeat(300)));

        ChatMessageDto dto = ChatMessageMapper.mapMessage(msg);
        List<ChatPartDto> parts = dto.getParts();
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).getAttachmentId()).isEqualTo("inline-stripped");
        assertThat(ChatMessageMapper.isVisible(dto)).isTrue();
    }
}
