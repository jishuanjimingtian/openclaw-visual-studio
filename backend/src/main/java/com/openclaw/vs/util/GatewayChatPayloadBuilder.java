package com.openclaw.vs.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openclaw.vs.dto.ChatPartDto;
import com.openclaw.vs.service.ChatAttachmentService;

import java.nio.file.Path;
import java.util.List;

/**
 * Builds OpenClaw Gateway {@code chat.send} params for multimodal messages.
 *
 * <p>Protocol (OpenClaw Gateway v4+): prefer structured {@code content} array with
 * {@code {type:text,text}} and {@code {type:image,path}} / {@code {type:file,path,name}}.
 * Also sets {@code message} as a text fallback for gateways that only read the string field.
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

        ArrayNode content = mapper.createArrayNode();
        StringBuilder textFallback = new StringBuilder();

        for (ChatPartDto part : parts) {
            if (part == null || part.getType() == null) {
                continue;
            }
            String type = part.getType().trim().toLowerCase();
            switch (type) {
                case "text" -> {
                    String text = part.getText() != null ? part.getText() : "";
                    if (!text.isBlank()) {
                        ObjectNode textNode = mapper.createObjectNode();
                        textNode.put("type", "text");
                        textNode.put("text", text);
                        content.add(textNode);
                        if (!textFallback.isEmpty()) {
                            textFallback.append('\n');
                        }
                        textFallback.append(text);
                    }
                }
                case "image", "file" -> {
                    if (part.getAttachmentId() == null || part.getAttachmentId().isBlank()) {
                        continue;
                    }
                    Path filePath = attachmentService.resolveAttachmentPath(part.getAttachmentId().trim());
                    ObjectNode mediaNode = mapper.createObjectNode();
                    mediaNode.put("type", type);
                    mediaNode.put("path", filePath.toAbsolutePath().toString().replace('\\', '/'));
                    if (part.getName() != null && !part.getName().isBlank()) {
                        mediaNode.put("name", part.getName().trim());
                    }
                    if (part.getMime() != null && !part.getMime().isBlank()) {
                        mediaNode.put("mime", part.getMime().trim());
                    }
                    content.add(mediaNode);
                    if (!textFallback.isEmpty()) {
                        textFallback.append('\n');
                    }
                    textFallback.append("[附件] ")
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

        if (!content.isEmpty()) {
            params.set("content", content);
        }
        String messageText = textFallback.toString().trim();
        if (!messageText.isBlank()) {
            params.put("message", messageText);
        }
        return params;
    }
}
