package com.openclaw.vs.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openclaw.vs.dto.ChatPartDto;
import com.openclaw.vs.service.ChatAttachmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayChatPayloadBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void buildSendParams_usesMessageOnly_withoutContentField() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path file = tempDir.resolve("photo.jpg");
        Files.writeString(file, "fake");

        ChatAttachmentService attachmentService = mock(ChatAttachmentService.class);
        when(attachmentService.resolveAttachmentPath("att-1")).thenReturn(file);

        List<ChatPartDto> parts = List.of(
            ChatPartDto.builder().type("text").text("请分析这张图").build(),
            ChatPartDto.builder()
                .type("image")
                .attachmentId("att-1")
                .name("photo.jpg")
                .mime("image/jpeg")
                .build()
        );

        ObjectNode params = GatewayChatPayloadBuilder.buildSendParams(
            mapper, "agent:main:main", "run-1", parts, attachmentService);

        assertThat(params.has("content")).isFalse();
        assertThat(params.path("message").asText())
            .contains("请分析这张图")
            .contains("[附件] photo.jpg")
            .contains(file.toAbsolutePath().toString().replace('\\', '/'));
        assertThat(params.path("sessionKey").asText()).isEqualTo("agent:main:main");
        assertThat(params.path("idempotencyKey").asText()).isEqualTo("run-1");
    }
}
