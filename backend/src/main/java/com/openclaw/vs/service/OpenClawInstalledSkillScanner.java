package com.openclaw.vs.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Scans OpenClaw workspace / global skills directories for installed Skill folders.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenClawInstalledSkillScanner {

    private final OpenClawWorkspaceConfigService workspaceConfigService;

    @Data
    @Builder
    public static class DiskSkill {
        private String slug;
        private String name;
        private String version;
        private String description;
        private String installPath;
        /** workspace | global */
        private String scope;
    }

    public Map<String, DiskSkill> scanBySlug() {
        Map<String, DiskSkill> bySlug = new LinkedHashMap<>();
        for (Path skillsRoot : listSkillsRoots()) {
            if (!Files.isDirectory(skillsRoot)) {
                continue;
            }
            String scope = isGlobalSkillsRoot(skillsRoot) ? "global" : "workspace";
            try (Stream<Path> dirs = Files.list(skillsRoot)) {
                for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                    if (!Files.isRegularFile(dir.resolve("SKILL.md"))) {
                        continue;
                    }
                    String slug = dir.getFileName().toString();
                    String key = slug.toLowerCase(Locale.ROOT);
                    if (bySlug.containsKey(key)) {
                        continue;
                    }
                    bySlug.put(key, readDiskSkill(dir, slug, scope));
                }
            } catch (Exception e) {
                log.debug("Failed to scan skills root {}: {}", skillsRoot, e.getMessage());
            }
        }
        return bySlug;
    }

    public List<DiskSkill> scanInstalledSkills() {
        return new ArrayList<>(scanBySlug().values());
    }

    public boolean isInstalledOnDisk(String slug) {
        if (slug == null || slug.isBlank()) {
            return false;
        }
        return scanBySlug().containsKey(slug.toLowerCase(Locale.ROOT));
    }

    public Optional<DiskSkill> findOnDisk(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(scanBySlug().get(slug.toLowerCase(Locale.ROOT)));
    }

    private List<Path> listSkillsRoots() {
        List<Path> roots = new ArrayList<>();
        workspaceConfigService.readConfiguredWorkspacePath()
            .map(path -> path.resolve("skills"))
            .ifPresent(roots::add);
        roots.add(Path.of(System.getProperty("user.home"), ".openclaw", "skills"));
        return roots;
    }

    private static boolean isGlobalSkillsRoot(Path root) {
        Path fileName = root.getFileName();
        return fileName != null && "skills".equals(fileName.toString())
            && root.getParent() != null
            && ".openclaw".equals(root.getParent().getFileName().toString());
    }

    private static DiskSkill readDiskSkill(Path dir, String slug, String scope) {
        Path skillMd = dir.resolve("SKILL.md");
        Map<String, String> meta = parseFrontmatter(readText(skillMd));
        String name = firstNonBlank(meta.get("name"), meta.get("title"), humanizeSlug(slug));
        String version = firstNonBlank(meta.get("version"), "1.0.0");
        String description = firstNonBlank(meta.get("description"), extractBodySummary(skillMd));
        return DiskSkill.builder()
            .slug(slug)
            .name(name)
            .version(version)
            .description(description != null ? description : "")
            .installPath(dir.toAbsolutePath().normalize().toString())
            .scope(scope)
            .build();
    }

    private static String readText(Path file) {
        try {
            return Files.readString(file);
        } catch (Exception e) {
            return "";
        }
    }

    static Map<String, String> parseFrontmatter(String content) {
        if (content == null || !content.startsWith("---")) {
            return Map.of();
        }
        int end = content.indexOf("\n---", 3);
        if (end < 0) {
            return Map.of();
        }
        String block = content.substring(3, end).trim();
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : block.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            result.put(key, value);
        }
        return result;
    }

    private static String extractBodySummary(Path skillMd) {
        String content = readText(skillMd);
        if (content.isBlank()) {
            return "";
        }
        int bodyStart = content.indexOf("\n---", 3);
        String body = bodyStart >= 0 ? content.substring(bodyStart + 4).trim() : content.trim();
        body = body.replaceAll("#+\\s*", "").trim();
        if (body.length() > 200) {
            return body.substring(0, 200) + "…";
        }
        return body;
    }

    private static String humanizeSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return "Skill";
        }
        return slug.replace('-', ' ').replace('_', ' ');
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
