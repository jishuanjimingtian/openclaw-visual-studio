package com.openclaw.vs.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;

/**
 * Writes {@code openclaw.json} via a temp file and replace, with retries for Windows file locks.
 */
public final class OpenClawConfigJsonWriter {

    private static final int MAX_ATTEMPTS = 6;
    private static final long RETRY_BASE_MS = 80;

    private OpenClawConfigJsonWriter() {}

    public static void writePretty(Path configPath, ObjectMapper mapper, Object root) {
        try {
            writePrettyInternal(configPath, mapper, root);
        } catch (IOException e) {
            throw new IllegalStateException(buildUserMessage(configPath, e), e);
        }
    }

    private static void writePrettyInternal(Path configPath, ObjectMapper mapper, Object root) throws IOException {
        Path parent = configPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempDir = parent != null ? parent : configPath.toAbsolutePath().getParent();
        if (tempDir == null) {
            throw new IOException("无法确定配置文件目录: " + configPath);
        }

        Path temp = Files.createTempFile(tempDir, ".openclaw-", ".json.tmp");
        boolean moved = false;
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), root);
            replaceWithRetry(temp, configPath);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }

    private static void replaceWithRetry(Path source, Path target) throws IOException {
        clearReadOnlyIfPresent(target);
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                moveReplacing(source, target);
                return;
            } catch (IOException e) {
                last = e;
                if (!isRetryable(e) || attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                sleep(attempt);
            }
        }
        if (last != null) {
            throw last;
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            if (Files.exists(target)) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isRetryable(IOException e) {
        if (e instanceof AccessDeniedException) {
            return true;
        }
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase();
        return msg.contains("拒绝访问")
            || lower.contains("access is denied")
            || lower.contains("being used by another process")
            || msg.contains("另一个程序正在使用");
    }

    private static void clearReadOnlyIfPresent(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            DosFileAttributeView dos = Files.getFileAttributeView(path, DosFileAttributeView.class);
            if (dos != null) {
                DosFileAttributes attrs = dos.readAttributes();
                if (attrs.isReadOnly()) {
                    dos.setReadOnly(false);
                }
            }
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static void sleep(int attempt) throws IOException {
        try {
            Thread.sleep(RETRY_BASE_MS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("写入配置被中断", e);
        }
    }

    private static String buildUserMessage(Path configPath, IOException cause) {
        String detail = cause.getMessage();
        StringBuilder msg = new StringBuilder("无法写入配置文件: ").append(configPath);
        msg.append("。请关闭正在占用该文件的程序（如 OpenClaw Gateway、VS Code 等），");
        msg.append("并确认当前用户对 ~/.openclaw 目录有写入权限。");
        if (detail != null && !detail.isBlank()) {
            msg.append(" 系统提示: ").append(detail);
        }
        return msg.toString();
    }
}
