package com.openclaw.vs.service;

import com.openclaw.vs.dto.KnowledgeFileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Unified stat-only scan of allowed knowledge Markdown files in a workspace.
 */
@Component
@RequiredArgsConstructor
public class OpenClawKnowledgeFileScanner {

    private static final String[] ROOT_FILES = {
        "AGENTS.md", "SOUL.md", "USER.md", "MEMORY.md", "DREAMS.md"
    };

    private final OpenClawKnowledgePathGuard pathGuard;

    public List<KnowledgeFileDto> listFiles(Path workspace) throws Exception {
        List<KnowledgeFileDto> files = new ArrayList<>();
        for (String name : ROOT_FILES) {
            addIfPresent(files, workspace, workspace.resolve(name));
        }
        Path memoryDir = workspace.resolve("memory");
        if (Files.isDirectory(memoryDir)) {
            try (Stream<Path> stream = Files.list(memoryDir)) {
                stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .forEach(p -> addIfPresent(files, workspace, p));
            }
        }
        Path dreamsDir = workspace.resolve("memory").resolve(".dreams");
        if (Files.isDirectory(dreamsDir)) {
            try (Stream<Path> stream = Files.list(dreamsDir)) {
                stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .forEach(p -> addIfPresent(files, workspace, p));
            }
        }
        return files;
    }

    public List<Path> listPaths(Path workspace) throws Exception {
        List<Path> paths = new ArrayList<>();
        for (String name : ROOT_FILES) {
            Path p = workspace.resolve(name);
            if (Files.isRegularFile(p)) {
                paths.add(p);
            }
        }
        Path memoryDir = workspace.resolve("memory");
        if (Files.isDirectory(memoryDir)) {
            try (Stream<Path> stream = Files.list(memoryDir)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> pathGuard.isAllowedRelative(relativePath(workspace, p)))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(paths::add);
            }
        }
        Path dreamsDir = workspace.resolve("memory").resolve(".dreams");
        if (Files.isDirectory(dreamsDir)) {
            try (Stream<Path> stream = Files.list(dreamsDir)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> pathGuard.isAllowedRelative(relativePath(workspace, p)))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(paths::add);
            }
        }
        return paths;
    }

    public String fingerprint(Path workspace) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (KnowledgeFileDto f : listFiles(workspace)) {
            sb.append(f.getPath())
                .append('|')
                .append(f.getSizeBytes())
                .append('|')
                .append(f.getUpdatedAt() != null ? f.getUpdatedAt() : "")
                .append('\n');
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    private void addIfPresent(List<KnowledgeFileDto> files, Path workspace, Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            String rel = relativePath(workspace, file);
            if (!pathGuard.isAllowedRelative(rel)) {
                return;
            }
            files.add(KnowledgeFileDto.builder()
                .path(rel)
                .kind(pathGuard.fileKind(rel))
                .sizeBytes(Files.size(file))
                .updatedAt(Files.getLastModifiedTime(file).toInstant().toString())
                .lineCount(0)
                .build());
        } catch (Exception ignored) {
            // skip unreadable entries
        }
    }

    private static String relativePath(Path workspace, Path file) {
        return workspace.relativize(file).toString().replace('\\', '/');
    }

    public static int countByScanner(OpenClawKnowledgeFileScanner scanner, Path workspace) {
        try {
            return scanner.listFiles(workspace).size();
        } catch (Exception e) {
            return 0;
        }
    }
}
