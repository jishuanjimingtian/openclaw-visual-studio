package com.openclaw.vs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.vs.gateway.GatewayWebSocketClient;
import com.openclaw.vs.gateway.OpenClawGatewayConfigReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Locates the OpenClaw agent workspace that actually contains memory Markdown files.
 * Prefers {@code agents.defaults.workspace} from openclaw.json, then scans known install paths.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenClawMemoryWorkspaceResolver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OpenClawWorkspaceConfigService workspaceConfigService;
    private final GatewayWebSocketClient gatewayClient;

    public record WorkspaceCandidate(
        String path,
        int memoryFileCount,
        String source,
        boolean configured
    ) {}

    public record ResolvedWorkspace(
        Path workspacePath,
        Path configPath,
        boolean autoConfigured,
        String resolutionSource,
        List<WorkspaceCandidate> candidates
    ) {}

    public ResolvedWorkspace resolve(boolean allowAutoConfigure) throws Exception {
        Path configPath = workspaceConfigService.resolveConfigPath();
        List<WorkspaceCandidate> candidates = discoverCandidates(configPath);
        Optional<Path> configured = workspaceConfigService.readConfiguredWorkspacePath();

        WorkspaceCandidate bestWithMemory = candidates.stream()
            .filter(c -> c.memoryFileCount() > 0)
            .max(Comparator.comparingInt(WorkspaceCandidate::memoryFileCount))
            .orElse(null);

        Path chosen;
        String source;
        boolean autoConfigured = false;

        if (bestWithMemory != null) {
            chosen = Path.of(bestWithMemory.path());
            if (bestWithMemory.configured()) {
                source = "openclaw.json agents.defaults.workspace";
            } else {
                source = "自动发现: " + bestWithMemory.source();
                if (configured.isPresent()) {
                    int cfgCount = countMemoryFiles(configured.get());
                    if (cfgCount < bestWithMemory.memoryFileCount()) {
                        log.info(
                            "Using discovered workspace {} ({} memory files) over configured {} ({} files)",
                            chosen, bestWithMemory.memoryFileCount(), configured.get(), cfgCount
                        );
                        source += "（比配置路径含更多记忆）";
                    }
                }
            }
            if (allowAutoConfigure
                && configured.isEmpty()
                && !bestWithMemory.configured()) {
                workspaceConfigService.writeWorkspaceToConfigPublic(configPath, chosen);
                OpenClawGatewayConfigReader.invalidateConfigCache();
                autoConfigured = true;
                source += "，已写入 openclaw.json";
            }
        } else if (configured.isPresent()) {
            chosen = configured.get().toAbsolutePath().normalize();
            source = "openclaw.json agents.defaults.workspace（未发现其它含记忆目录）";
        } else {
            chosen = Path.of(workspaceConfigService.discoverDefaultWorkspace());
            source = "默认工作区路径（尚未发现记忆文件）";
            if (allowAutoConfigure) {
                workspaceConfigService.writeWorkspaceToConfigPublic(configPath, chosen);
                OpenClawGatewayConfigReader.invalidateConfigCache();
                autoConfigured = true;
                source += "，已写入 openclaw.json";
            }
        }

        Files.createDirectories(chosen);
        Files.createDirectories(chosen.resolve("memory"));

        return new ResolvedWorkspace(
            chosen.toAbsolutePath().normalize(),
            configPath,
            autoConfigured,
            source,
            candidates
        );
    }

    public List<WorkspaceCandidate> discoverCandidates(Path configPath) {
        Set<String> seen = new LinkedHashSet<>();
        List<WorkspaceCandidate> out = new ArrayList<>();
        String home = System.getProperty("user.home");

        addCandidate(out, seen, workspaceConfigService.readConfiguredWorkspacePath().orElse(null),
            "openclaw.json", true);
        addCandidate(out, seen, Path.of(home, ".openclaw", "workspace"), "OpenClaw default (~/.openclaw/workspace)", false);
        addCandidate(out, seen, Path.of(home, "openclaw-workspace"), "legacy openclaw-workspace", false);
        addCandidate(out, seen, Path.of(System.getProperty("user.dir"), "openclaw-workspace"), "cwd/openclaw-workspace", false);

        String envWs = System.getenv("OPENCLAW_WORKSPACE");
        if (envWs != null && !envWs.isBlank()) {
            addCandidate(out, seen, Path.of(envWs.trim()), "OPENCLAW_WORKSPACE", false);
        }

        readAgentListWorkspaces(configPath).forEach(p ->
            addCandidate(out, seen, p, "agents.list[].workspace", false)
        );

        Optional<Path> gatewayWs = readWorkspaceFromGateway();
        gatewayWs.ifPresent(p -> addCandidate(out, seen, p, "Gateway doctor.memory.status", false));

        scanOpenClawHome(Path.of(home, ".openclaw"), out, seen);

        out.sort(Comparator.comparingInt(WorkspaceCandidate::memoryFileCount).reversed());
        return out;
    }

    /** Scan ~/.openclaw and immediate subdirectories for MEMORY.md */
    private void scanOpenClawHome(Path openclawDir, List<WorkspaceCandidate> out, Set<String> seen) {
        if (!Files.isDirectory(openclawDir)) {
            return;
        }
        addCandidate(out, seen, openclawDir.resolve("workspace"), "OpenClaw ~/.openclaw/workspace", false);
        try (Stream<Path> stream = Files.list(openclawDir)) {
            stream.filter(Files::isDirectory)
                .filter(dir -> {
                    Path memoryMd = dir.resolve("MEMORY.md");
                    return Files.isRegularFile(memoryMd);
                })
                .forEach(dir -> addCandidate(out, seen, dir, "扫描 " + openclawDir.getFileName() + " 子目录", false));
        } catch (Exception e) {
            log.debug("scanOpenClawHome failed: {}", e.getMessage());
        }
    }

    private Optional<Path> readWorkspaceFromGateway() {
        if (!gatewayClient.isConnected()) {
            return Optional.empty();
        }
        try {
            JsonNode payload = gatewayClient.request(
                "doctor.memory.status",
                MAPPER.createObjectNode(),
                10_000
            );
            for (String key : List.of("workspace", "workspacePath", "workspaceDir", "root")) {
                if (payload.has(key) && payload.get(key).isTextual()) {
                    String text = payload.get(key).asText().trim();
                    if (!text.isBlank()) {
                        return Optional.of(Path.of(text));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Gateway workspace hint unavailable: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private List<Path> readAgentListWorkspaces(Path configPath) {
        List<Path> paths = new ArrayList<>();
        if (!Files.isRegularFile(configPath)) {
            return paths;
        }
        try {
            JsonNode root = MAPPER.readTree(configPath.toFile());
            JsonNode list = root.path("agents").path("list");
            if (!list.isArray()) {
                return paths;
            }
            for (JsonNode agent : list) {
                String ws = agent.path("workspace").asText(null);
                if (ws != null && !ws.isBlank()) {
                    paths.add(Path.of(ws.trim()));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to read agents.list workspaces: {}", e.getMessage());
        }
        return paths;
    }

    private void addCandidate(
        List<WorkspaceCandidate> out,
        Set<String> seen,
        Path path,
        String source,
        boolean configured
    ) {
        if (path == null) {
            return;
        }
        Path abs = path.toAbsolutePath().normalize();
        String key = abs.toString().toLowerCase();
        if (!seen.add(key)) {
            return;
        }
        if (!Files.isDirectory(abs)) {
            return;
        }
        int count = countMemoryFiles(abs);
        out.add(new WorkspaceCandidate(
            OpenClawWorkspaceConfigService.normalizePathForConfig(abs),
            count,
            source,
            configured
        ));
    }

    public static int countMemoryFiles(Path workspace) {
        if (workspace == null || !Files.isDirectory(workspace)) {
            return 0;
        }
        OpenClawKnowledgePathGuard guard = new OpenClawKnowledgePathGuard();
        OpenClawKnowledgeFileScanner scanner = new OpenClawKnowledgeFileScanner(guard);
        return OpenClawKnowledgeFileScanner.countByScanner(scanner, workspace);
    }
}
