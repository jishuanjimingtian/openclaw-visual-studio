package com.openclaw.vs.service;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class OpenClawKnowledgePathGuard {

    private static final Pattern DAILY_NAME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}(-.+)?\\.md$");
    private static final Pattern DREAM_SHARD_NAME = Pattern.compile("^[^/\\\\]+\\.md$");

    public String normalizeRelative(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("路径不能为空");
        }
        String normalized = raw.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("非法路径");
        }
        return normalized;
    }

    public Path resolveInWorkspace(Path workspace, String relative) {
        String rel = normalizeRelative(relative);
        if (!isAllowedRelative(rel)) {
            throw new IllegalArgumentException("不允许访问该记忆文件: " + rel);
        }
        Path resolved = workspace.resolve(rel).normalize().toAbsolutePath();
        Path workspaceAbs = workspace.toAbsolutePath().normalize();
        if (!resolved.startsWith(workspaceAbs)) {
            throw new IllegalArgumentException("路径越界");
        }
        return resolved;
    }

    public boolean isAllowedRelative(String rel) {
        if (rel == null || rel.isBlank()) {
            return false;
        }
        String lower = rel.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".md")) {
            return false;
        }
        if ("memory.md".equals(lower)) {
            return true;
        }
        if ("dreams.md".equals(lower)) {
            return true;
        }
        if ("soul.md".equals(lower)) {
            return true;
        }
        if ("user.md".equals(lower)) {
            return true;
        }
        if ("agents.md".equals(lower)) {
            return true;
        }
        if (lower.startsWith("memory/.dreams/")) {
            String name = rel.substring("memory/.dreams/".length());
            if (name.contains("/") || name.contains("\\")) {
                return false;
            }
            return DREAM_SHARD_NAME.matcher(name).matches();
        }
        if (lower.startsWith("memory/")) {
            String name = rel.substring("memory/".length());
            if (name.contains("/")) {
                return false;
            }
            return DAILY_NAME.matcher(name).matches();
        }
        return false;
    }

    public String fileKind(String rel) {
        String lower = rel.replace('\\', '/').toLowerCase(Locale.ROOT);
        if ("memory.md".equals(lower)) {
            return "hub";
        }
        if ("dreams.md".equals(lower)) {
            return "dream";
        }
        if ("soul.md".equals(lower)) {
            return "soul";
        }
        if ("user.md".equals(lower)) {
            return "user";
        }
        if ("agents.md".equals(lower)) {
            return "agents";
        }
        if (lower.startsWith("memory/.dreams/")) {
            return "dream_shard";
        }
        if (lower.startsWith("memory/")) {
            return "daily";
        }
        return "unknown";
    }

    public static boolean workspaceExists(Path workspace) {
        return workspace != null && Files.isDirectory(workspace);
    }
}
