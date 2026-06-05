package com.openclaw.vs.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.openclaw.vs.dto.ChatMessageDto;
import com.openclaw.vs.dto.ChatPartDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 OpenClaw Gateway chat.history / chat 事件中的消息结构。
 */
public final class ChatMessageMapper {

    /** OpenClaw {@code STREAM_ERROR_FALLBACK_TEXT} when the model stream fails before any output. */
    public static final String STREAM_ERROR_FALLBACK_TEXT =
        "[assistant turn failed before producing content]";

    /** OpenClaw 内部占位回复，不应展示在 UI 中 */
    private static final Pattern INTERNAL_REPLY =
        Pattern.compile(
            "^\\s*(?:NO_REPLY|HEARTBEAT_OK)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ATTACHMENT_ID_IN_PATH =
        Pattern.compile("\\.openclaw-media/chat/[^/]+/([0-9a-fA-F-]{36})/");

    private ChatMessageMapper() {}

    public static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "assistant";
        }
        String r = role.trim().toLowerCase();
        return switch (r) {
            case "human", "user", "customer", "client" -> "user";
            case "assistant", "model", "ai", "bot", "agent" -> "assistant";
            case "system" -> "system";
            case "tool", "toolresult", "tool_result", "tool_result_message" -> "tool";
            default -> r;
        };
    }

    public static ChatMessageDto mapMessage(JsonNode msg) {
        String role = normalizeRole(firstNonBlank(
            msg.path("role").asText(null),
            msg.path("type").asText(null),
            msg.path("kind").asText(null)
        ));
        List<ChatPartDto> parts = extractParts(msg);
        String content = humanizeStreamError(msg, summarizeText(parts, extractText(msg)));
        Long ts = resolveTimestamp(msg);

        return ChatMessageDto.builder()
            .role(role)
            .content(content != null ? content : "")
            .parts(parts.isEmpty() ? null : parts)
            .timestamp(ts)
            .build();
    }

    public static boolean isVisible(ChatMessageDto dto) {
        if (dto == null) {
            return false;
        }
        if ("tool".equals(dto.getRole())) {
            return false;
        }
        if (dto.getParts() != null) {
            for (ChatPartDto part : dto.getParts()) {
                if (part == null || part.getType() == null) {
                    continue;
                }
                if ("image".equals(part.getType()) || "file".equals(part.getType())) {
                    if (part.getAttachmentId() != null && !part.getAttachmentId().isBlank()) {
                        return true;
                    }
                }
            }
        }
        String text = dto.getContent() != null ? dto.getContent().trim() : "";
        if (text.isEmpty()) {
            return false;
        }
        return !isHiddenContent(text);
    }

    public static boolean isHiddenContent(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        return INTERNAL_REPLY.matcher(text.trim()).matches();
    }

    /** 会话列表预览：隐藏内部占位回复 */
    public static String sanitizePreview(String preview) {
        if (isHiddenContent(preview)) {
            return null;
        }
        return preview;
    }

    public static JsonNode resolveMessagesArray(JsonNode payload) {
        if (payload == null || payload.isMissingNode()) {
            return payload;
        }
        JsonNode messages = payload.path("messages");
        if (messages.isArray()) {
            return messages;
        }
        messages = payload.path("items");
        if (messages.isArray()) {
            return messages;
        }
        messages = payload.path("transcript").path("messages");
        if (messages.isArray()) {
            return messages;
        }
        messages = payload.path("history");
        if (messages.isArray()) {
            return messages;
        }
        return payload.path("messages");
    }

    public static String humanizeStreamError(JsonNode msg, String content) {
        if (content == null) {
            content = "";
        }
        String trimmed = content.trim();
        boolean isFallback = STREAM_ERROR_FALLBACK_TEXT.equals(trimmed);
        boolean isErrorStop =
            msg != null
                && !msg.isMissingNode()
                && "error".equalsIgnoreCase(msg.path("stopReason").asText(""));
        if (!isFallback && !(isErrorStop && trimmed.isEmpty())) {
            return content;
        }
        String errorMessage = msg != null && !msg.isMissingNode()
            ? firstNonBlank(
                msg.path("errorMessage").asText(null),
                msg.path("error").asText(null))
            : null;
        if (errorMessage != null && !errorMessage.isBlank()) {
            String detail = errorMessage.trim();
            if (detail.toLowerCase().contains("timed out")
                || detail.contains("超时")
                || "LLM request timed out.".equalsIgnoreCase(detail)) {
                return "模型请求超时（通义千问响应过慢或网络不稳定）。"
                    + "可在 ~/.openclaw/openclaw.json 提高 agents.defaults.timeoutSeconds"
                    + " 与 models.providers.qwen.timeoutSeconds（例如 180），然后重启 Gateway。";
            }
            return "模型调用失败：" + detail;
        }
        if (isFallback || isErrorStop) {
            return "模型未返回有效内容。请检查 ~/.openclaw/openclaw.json 中的模型与 API Key，"
                + "并查看 Gateway 日志：openclaw logs --follow";
        }
        return content;
    }

    public static String extractText(JsonNode msg) {
        if (msg == null || msg.isMissingNode() || msg.isNull()) {
            return "";
        }
        if (msg.isTextual()) {
            return msg.asText();
        }
        if (msg.has("text") && msg.path("text").isTextual()) {
            return msg.path("text").asText("");
        }
        if (msg.has("content")) {
            String fromContent = extractTextFromContentArray(msg.path("content"));
            if (!fromContent.isBlank()) {
                return fromContent;
            }
        }
        if (msg.has("parts")) {
            String fromParts = extractTextFromContentArray(msg.path("parts"));
            if (!fromParts.isBlank()) {
                return fromParts;
            }
        }
        if (msg.has("body") && msg.path("body").isTextual()) {
            return msg.path("body").asText("");
        }
        return "";
    }

    public static List<ChatPartDto> extractParts(JsonNode msg) {
        List<ChatPartDto> parts = new ArrayList<>();
        if (msg == null || msg.isMissingNode()) {
            return parts;
        }
        if (msg.has("content") && msg.path("content").isArray()) {
            parseContentArray(msg.path("content"), parts);
        } else if (msg.has("parts") && msg.path("parts").isArray()) {
            parseContentArray(msg.path("parts"), parts);
        }
        if (parts.isEmpty() && msg.has("text") && msg.path("text").isTextual()) {
            String text = msg.path("text").asText("");
            if (!text.isBlank()) {
                parts.add(ChatPartDto.builder().type("text").text(text).build());
            }
        }
        return parts;
    }

    private static void parseContentArray(JsonNode content, List<ChatPartDto> parts) {
        if (!content.isArray()) {
            return;
        }
        for (JsonNode part : content) {
            if (part == null || part.isNull()) {
                continue;
            }
            if (part.isTextual()) {
                parts.add(ChatPartDto.builder().type("text").text(part.asText()).build());
                continue;
            }
            String type = part.path("type").asText("").trim().toLowerCase(Locale.ROOT);
            if (type.isBlank() || "text".equals(type)) {
                String text = part.path("text").asText("");
                if (!text.isBlank()) {
                    parts.add(ChatPartDto.builder().type("text").text(text).build());
                }
                continue;
            }
            if ("image".equals(type) || "file".equals(type) || "document".equals(type)) {
                String normalizedType = "document".equals(type) ? "file" : type;
                if (hasInlineBinary(part)) {
                    parts.add(buildPlaceholderAttachment(part, normalizedType));
                    continue;
                }
                ChatPartDto attachment = buildAttachmentPart(part, normalizedType);
                if (attachment != null) {
                    parts.add(attachment);
                }
            }
        }
    }

    private static boolean hasInlineBinary(JsonNode part) {
        if (part.has("data") && part.path("data").isTextual()
            && part.path("data").asText("").length() > 256) {
            return true;
        }
        if (part.has("base64") && part.path("base64").isTextual()
            && part.path("base64").asText("").length() > 256) {
            return true;
        }
        return false;
    }

    private static ChatPartDto buildPlaceholderAttachment(JsonNode part, String type) {
        String name = firstNonBlank(
            part.path("name").asText(null),
            part.path("filename").asText(null),
            "image".equals(type) ? "image" : "file"
        );
        String mime = firstNonBlank(
            part.path("mime").asText(null),
            part.path("mimeType").asText(null),
            "image".equals(type) ? "image/*" : "application/octet-stream"
        );
        long size = part.has("sizeBytes") ? part.path("sizeBytes").asLong(0L) : 0L;
        return ChatPartDto.builder()
            .type(type)
            .attachmentId("inline-stripped")
            .name(name)
            .mime(mime)
            .sizeBytes(size > 0 ? size : null)
            .thumbReady(false)
            .build();
    }

    private static ChatPartDto buildAttachmentPart(JsonNode part, String type) {
        String path = firstNonBlank(
            part.path("path").asText(null),
            part.path("filePath").asText(null),
            part.path("url").asText(null)
        );
        String name = firstNonBlank(
            part.path("name").asText(null),
            part.path("filename").asText(null),
            fileNameFromPath(path),
            "image".equals(type) ? "image" : "file"
        );
        String mime = firstNonBlank(
            part.path("mime").asText(null),
            part.path("mimeType").asText(null),
            "image".equals(type) ? "image/*" : "application/octet-stream"
        );
        String attachmentId = extractAttachmentId(path);
        if (attachmentId == null) {
            attachmentId = firstNonBlank(part.path("attachmentId").asText(null), part.path("id").asText(null));
        }
        if (attachmentId == null || attachmentId.isBlank()) {
            return null;
        }
        ChatPartDto.ChatPartDtoBuilder builder = ChatPartDto.builder()
            .type(type)
            .attachmentId(attachmentId)
            .name(name)
            .mime(mime);
        if (part.has("sizeBytes")) {
            builder.sizeBytes(part.path("sizeBytes").asLong());
        }
        if (part.has("width")) {
            builder.width(part.path("width").asInt());
        }
        if (part.has("height")) {
            builder.height(part.path("height").asInt());
        }
        builder.thumbReady("image".equals(type));
        return builder.build();
    }

    private static String extractAttachmentId(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Matcher matcher = ATTACHMENT_ID_IN_PATH.matcher(path.replace('\\', '/'));
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String fileNameFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static String extractTextFromContentArray(JsonNode content) {
        if (content.isTextual()) {
            return content.asText();
        }
        if (!content.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : content) {
            if (part == null || part.isNull()) {
                continue;
            }
            if (part.isTextual()) {
                sb.append(part.asText());
                continue;
            }
            String type = part.path("type").asText("");
            if ("text".equals(type) || type.isBlank()) {
                if (part.has("text")) {
                    sb.append(part.path("text").asText(""));
                }
            }
        }
        return sb.toString();
    }

    private static String summarizeText(List<ChatPartDto> parts, String fallback) {
        if (parts == null || parts.isEmpty()) {
            return fallback != null ? fallback : "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatPartDto part : parts) {
            if (part == null || part.getType() == null) {
                continue;
            }
            if ("text".equals(part.getType()) && part.getText() != null && !part.getText().isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(part.getText());
            }
        }
        if (!sb.isEmpty()) {
            return sb.toString();
        }
        return fallback != null ? fallback : "";
    }

    private static Long resolveTimestamp(JsonNode msg) {
        if (msg.has("timestamp") && !msg.path("timestamp").isNull()) {
            return msg.path("timestamp").asLong();
        }
        if (msg.has("ts") && !msg.path("ts").isNull()) {
            return msg.path("ts").asLong();
        }
        if (msg.has("createdAt") && !msg.path("createdAt").isNull()) {
            return msg.path("createdAt").asLong();
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
