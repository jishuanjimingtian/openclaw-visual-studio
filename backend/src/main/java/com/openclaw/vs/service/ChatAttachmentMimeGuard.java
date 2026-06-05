package com.openclaw.vs.service;

import com.openclaw.vs.exception.BadRequestException;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ChatAttachmentMimeGuard {

    private static final long MAX_FILE_BYTES = 100L * 1024 * 1024;
    private static final int MAX_ATTACHMENTS_PER_MESSAGE = 10;
    private static final Pattern BLOCKED_EXT =
        Pattern.compile("\\.(exe|bat|cmd|com|msi|dll|sh|bash|ps1|vbs|scr|jar)$", Pattern.CASE_INSENSITIVE);

    private static final Set<String> ALLOWED_EXACT = Set.of(
        "application/pdf",
        "text/plain",
        "text/markdown",
        "text/csv",
        "text/x-markdown",
        "application/csv"
    );

    private ChatAttachmentMimeGuard() {}

    public static void validateInit(String name, String mime, long sizeBytes) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("文件名不能为空");
        }
        if (mime == null || mime.isBlank()) {
            throw new BadRequestException("MIME 类型不能为空");
        }
        if (sizeBytes <= 0 || sizeBytes > MAX_FILE_BYTES) {
            throw new BadRequestException("文件大小必须在 1B ~ 100MB 之间");
        }
        if (BLOCKED_EXT.matcher(name.trim()).find()) {
            throw new BadRequestException("不允许上传可执行文件");
        }
        String normalized = mime.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("image/")) {
            return;
        }
        if (normalized.startsWith("text/")) {
            return;
        }
        if (ALLOWED_EXACT.contains(normalized)) {
            return;
        }
        throw new BadRequestException("不支持的文件类型: " + mime);
    }

    public static long maxFileBytes() {
        return MAX_FILE_BYTES;
    }

    public static int maxAttachmentsPerMessage() {
        return MAX_ATTACHMENTS_PER_MESSAGE;
    }

    public static boolean isImageMime(String mime) {
        return mime != null && mime.toLowerCase(Locale.ROOT).startsWith("image/");
    }
}
