package com.openclaw.vs.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openclaw.vs.dto.ChatPartDto;
import com.openclaw.vs.service.ChatAttachmentService;

import java.nio.file.Path;
import java.util.List;

/**
 * Builds OpenClaw Gateway {@code chat.send} params for multimodal messages.
 *
 * <p>Gateway {@code chat.send} accepts {@code message} (string). Attachment paths are
 * embedded in the message body so agents can resolve local files; do not send root-level
 * {@code content} (rejected by current Gateway schema).
 */
public final class GatewayChatPayloadBuilder {

    private GatewayChatPayloadBuilder() {}

    public static ObjectNode buildSendParams(
        ObjectMapper mapper,
        String sessionKey,
        String runId,
        List<ChatPartDto> parts,
        ChatAttachmentService attachmentService
    ) throws Exception {
        ObjectNode params = mapper.createObjectNode();
        params.put("sessionKey", sessionKey.trim());
        params.put("deliver", false);
        params.put("idempotencyKey", runId);

        StringBuilder message = new StringBuilder();

        for (ChatPartDto part : parts) {
            if (part == null || part.getType() == null) {
                continue;
            }
            String type = part.getType().trim().toLowerCase();
            switch (type) {
                case "text" -> {
                    String text = part.getText() != null ? part.getText() : "";
                    if (!text.isBlank()) {
                        if (!message.isEmpty()) {
                            message.append('\n');
                        }
                        message.append(text);
                    }
                }
                case "image", "file" -> {
                    if (part.getAttachmentId() == null || part.getAttachmentId().isBlank()) {
                        continue;
                    }
                    Path filePath = attachmentService.resolveAttachmentPath(part.getAttachmentId().trim());
                    if (!message.isEmpty()) {
                        message.append('\n');
                    }
                    message.append("[附件] ")
                        .append(part.getName() != null ? part.getName() : part.getAttachmentId())
                        .append(" (")
                        .append(filePath.toAbsolutePath().toString().replace('\\', '/'))
                        .append(')');
                }
                default -> {
                    // ignore unknown part types
                }
            }
        }

        String messageText = message.toString().trim();
        if (!messageText.isBlank()) {
            params.put("message", messageText);
        }
        return params;
    }
}
