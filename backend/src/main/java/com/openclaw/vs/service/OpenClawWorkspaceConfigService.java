package com.openclaw.vs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openclaw.vs.gateway.OpenClawGatewayConfigReader;
import com.openclaw.vs.util.OpenClawConfigJsonWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves {@code ~/.openclaw/openclaw.json} (or {@code OPENCLAW_CONFIG}) and
 * {@code agents.defaults.workspace} for Skill installation.
 */
@Slf4j
@Service
public class OpenClawWorkspaceConfigService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record WorkspaceResolution(
        Path configPath,
        Path workspacePath,
        Path skillsDir,
        boolean autoConfigured
    ) {}

    /** Locates openclaw.json, preferring existing files then the standard path for creation. */
    public Path resolveConfigPath() {
        String envConfig = System.getenv("OPENCLAW_CONFIG");
        if (envConfig != null && !envConfig.isBlank()) {
            return Path.of(envConfig.trim());
        }

        Path preferred = OpenClawGatewayConfigReader.defaultConfigPath();
        if (Files.isRegularFile(preferred)) {
            return preferred;
        }

        Path home = Path.of(System.getProperty("user.home"), ".openclaw");
        Path openclawJson = home.resolve("openclaw.json");
        Path configJson = home.resolve("config.json");
        if (Files.isRegularFile(configJson)) {
            return configJson;
        }
        return openclawJson;
    }

    public Optional<Path> readConfiguredWorkspacePath() {
        Path configPath = resolveConfigPath();
        if (!Files.isRegularFile(configPath)) {
            return Optional.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(configPath.toFile());
            String workspace = root.path("agents").path("defaults").path("workspace").asText(null);
            if (workspace == null || workspace.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(Path.of(workspace.trim()));
        } catch (Exception e) {
            log.debug("Failed to read workspace from {}: {}", configPath, e.getMessage());
            return Optional.empty();
        }
    }

    /** Ensures workspace is configured; writes openclaw.json when {@code autoConfigure} is true. */
    public WorkspaceResolution ensureWorkspaceConfigured(boolean autoConfigure) throws Exception {
        Path configPath = resolveConfigPath();
        Optional<Path> configured = readConfiguredWorkspacePath();
        Path workspacePath;
        boolean autoConfigured = false;

        if (configured.isPresent()) {
            workspacePath = configured.get();
        } else {
            workspacePath = Path.of(discoverDefaultWorkspace());
            if (autoConfigure) {
                writeWorkspaceToConfig(configPath, workspacePath);
                OpenClawGatewayConfigReader.invalidateConfigCache();
                autoConfigured = true;
                log.info(
                    "Auto-configured agents.defaults.workspace={} in {}",
                    normalizePathForConfig(workspacePath),
                    configPath
                );
            }
        }

        Path skillsDir = workspacePath.resolve("skills");
        Files.createDirectories(skillsDir);
        return new WorkspaceResolution(configPath, workspacePath, skillsDir, autoConfigured);
    }

    public String discoverDefaultWorkspace() {
        String home = System.getProperty("user.home");
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of(home, ".openclaw", "workspace"));
        candidates.add(Path.of(home, "openclaw-workspace"));
        candidates.add(Path.of(System.getProperty("user.dir"), "openclaw-workspace"));

        Path bestMemory = null;
        int bestCount = 0;
        for (Path candidate : candidates) {
            int memCount = OpenClawMemoryWorkspaceResolver.countMemoryFiles(candidate);
            if (memCount > bestCount) {
                bestCount = memCount;
                bestMemory = candidate;
            }
            if (isLikelyWorkspace(candidate) && bestMemory == null) {
                return normalizePathForConfig(candidate);
            }
        }
        if (bestMemory != null && bestCount > 0) {
            return normalizePathForConfig(bestMemory);
        }
        return normalizePathForConfig(Path.of(home, ".openclaw", "workspace"));
    }

    public void writeWorkspaceToConfigPublic(Path configPath, Path workspacePath) throws Exception {
        writeWorkspaceToConfig(configPath, workspacePath);
    }

    private void writeWorkspaceToConfig(Path configPath, Path workspacePath) throws Exception {
        Files.createDirectories(configPath.getParent());
        ObjectNode root;
        if (Files.isRegularFile(configPath)) {
            root = (ObjectNode) MAPPER.readTree(configPath.toFile());
        } else {
            root = MAPPER.createObjectNode();
        }

        ObjectNode agents = ensureObject(root, "agents");
        ObjectNode defaults = ensureObject(agents, "defaults");
        defaults.put("workspace", normalizePathForConfig(workspacePath));

        OpenClawConfigJsonWriter.writePretty(configPath, MAPPER, root);
        OpenClawGatewayConfigReader.invalidateConfigCache();
    }

    private static boolean isLikelyWorkspace(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        return Files.isRegularFile(dir.resolve("package.json"))
            || Files.isRegularFile(dir.resolve("openclaw.config.yml"))
            || Files.isDirectory(dir.resolve("skills"))
            || Files.isRegularFile(dir.resolve("MEMORY.md"))
            || Files.isDirectory(dir.resolve("memory"));
    }

    static String normalizePathForConfig(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private static ObjectNode ensureObject(ObjectNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = MAPPER.createObjectNode();
        parent.set(field, created);
        return created;
    }
}
