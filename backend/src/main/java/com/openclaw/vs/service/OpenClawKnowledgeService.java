package com.openclaw.vs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openclaw.vs.dto.*;
import com.openclaw.vs.exception.NotFoundException;
import com.openclaw.vs.gateway.GatewayWebSocketClient;
import com.openclaw.vs.gateway.OpenClawGatewayConfigReader;
import com.openclaw.vs.gateway.OpenClawGatewayConfigReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import com.openclaw.vs.util.TextFileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenClawKnowledgeService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long RPC_TIMEOUT_MS = 15_000;
    private static final long INDEX_TIMEOUT_MS = 120_000;
    private static final Pattern SCORE_PATTERN = Pattern.compile("score[=:]\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE);

    private final OpenClawWorkspaceConfigService workspaceConfigService;
    private final OpenClawMemoryWorkspaceResolver workspaceResolver;
    private final OpenClawKnowledgePathGuard pathGuard;
    private final OpenClawKnowledgeGraphBuilder graphBuilder;
    private final OpenClawKnowledgeFileScanner fileScanner;
    private final OpenClawKnowledgeGraphCache graphCache;
    private final GatewayWebSocketClient gatewayClient;
    private final OpenClawInstallDiscoveryService installDiscoveryService;

    private KnowledgeOverviewDto buildOverview(WorkspaceCtx ctx) throws Exception {
        List<KnowledgeFileDto> files = fileScanner.listFiles(ctx.workspace());
        long memorySize = files.stream()
            .filter(f -> "MEMORY.md".equalsIgnoreCase(f.getPath()))
            .mapToLong(KnowledgeFileDto::getSizeBytes)
            .findFirst()
            .orElse(0L);
        int dailyCount = (int) files.stream().filter(f -> "daily".equals(f.getKind())).count();
        boolean dreams = files.stream().anyMatch(f -> "dream".equals(f.getKind()));
        int workspaceConfigCount = (int) files.stream()
            .filter(f -> List.of("soul", "user", "agents").contains(f.getKind()))
            .count();
        int dreamShardCount = (int) files.stream().filter(f -> "dream_shard".equals(f.getKind())).count();

        String indexSummary = "未知";
        if (gatewayClient.isConnected()) {
            try {
                KnowledgeIndexStatusDto status = getIndexStatus(false);
                indexSummary = status.getSummary() != null ? status.getSummary() : "已连接";
            } catch (Exception e) {
                indexSummary = "Gateway 已连接，索引状态不可用";
            }
        } else {
            indexSummary = "Gateway 未连接";
        }

        String activeWs = ctx.resolved().workspacePath().toString();
        List<KnowledgeWorkspaceCandidateDto> candidateDtos = ctx.resolved().candidates().stream()
            .map(c -> KnowledgeWorkspaceCandidateDto.builder()
                .path(c.path())
                .memoryFileCount(c.memoryFileCount())
                .source(c.source())
                .configured(c.configured())
                .active(activeWs.equalsIgnoreCase(c.path()))
                .build())
            .toList();

        return KnowledgeOverviewDto.builder()
            .workspacePath(activeWs)
            .configPath(ctx.resolved().configPath().toString())
            .configuredWorkspacePath(workspaceConfigService.readConfiguredWorkspacePath()
                .map(p -> p.toAbsolutePath().normalize().toString())
                .orElse(null))
            .resolutionSource(ctx.resolved().resolutionSource())
            .candidates(candidateDtos)
            .workspaceAutoConfigured(ctx.resolved().autoConfigured())
            .fileCount(files.size())
            .memoryMdSizeBytes(memorySize)
            .dailyNoteCount(dailyCount)
            .dreamsPresent(dreams)
            .workspaceConfigCount(workspaceConfigCount)
            .dreamShardCount(dreamShardCount)
            .gatewayConnected(gatewayClient.isConnected())
            .gatewayPort(OpenClawGatewayConfigReader.readGatewayPort()
                .orElse(OpenClawGatewayConfigReader.DEFAULT_GATEWAY_PORT))
            .indexStatusSummary(indexSummary)
            .lastSyncedAt(Instant.now().toString())
            .build();
    }

    public KnowledgeOverviewDto getOverview(boolean autoConfigure) throws Exception {
        return buildOverview(resolveWorkspace(autoConfigure));
    }

    public List<KnowledgeFileDto> listFiles(boolean autoConfigure) throws Exception {
        return fileScanner.listFiles(resolveWorkspace(autoConfigure).workspace());
    }

    public KnowledgeBootstrapDto bootstrap(
        boolean autoConfigure,
        boolean includeGraph,
        boolean includeChunks,
        int dailyWindowDays
    ) throws Exception {
        WorkspaceCtx ctx = resolveWorkspace(autoConfigure);
        KnowledgeOverviewDto overview = buildOverview(ctx);
        List<KnowledgeFileDto> files = fileScanner.listFiles(ctx.workspace());
        KnowledgeGraphDto graph = null;
        if (includeGraph) {
            graph = graphCache.getOrBuild(
                ctx.workspace(),
                includeChunks,
                dailyWindowDays > 0 ? dailyWindowDays : OpenClawKnowledgeGraphBuilder.DEFAULT_DAILY_WINDOW_DAYS
            );
        }
        return KnowledgeBootstrapDto.builder()
            .overview(overview)
            .files(files)
            .graph(graph)
            .build();
    }

    public KnowledgeGraphDto getGraph(boolean includeChunks, boolean autoConfigure) throws Exception {
        return getGraph(includeChunks, autoConfigure, OpenClawKnowledgeGraphBuilder.DEFAULT_DAILY_WINDOW_DAYS);
    }

    public KnowledgeGraphDto getGraph(boolean includeChunks, boolean autoConfigure, int dailyWindowDays) throws Exception {
        WorkspaceCtx ctx = resolveWorkspace(autoConfigure);
        return graphCache.getOrBuild(ctx.workspace(), includeChunks, dailyWindowDays);
    }

    public KnowledgeGraphDto getGraphForPath(String relativePath, boolean autoConfigure) throws Exception {
        WorkspaceCtx ctx = resolveWorkspace(autoConfigure);
        String rel = pathGuard.normalizeRelative(relativePath);
        return graphCache.subgraphFromCache(
            ctx.workspace(),
            rel,
            false,
            OpenClawKnowledgeGraphBuilder.DEFAULT_DAILY_WINDOW_DAYS
        );
    }

    public KnowledgeFileContentDto readFile(String relativePath, boolean autoConfigure) throws Exception {
        WorkspaceCtx ctx = resolveWorkspace(autoConfigure);
        Path file = pathGuard.resolveInWorkspace(ctx.workspace(), relativePath);
        if (!Files.isRegularFile(file)) {
            throw new NotFoundException("记忆文件", relativePath);
        }
        String rel = ctx.workspace().relativize(file).toString().replace('\\', '/');
        String content = TextFileReader.readString(file);
        return KnowledgeFileContentDto.builder()
            .path(rel)
            .content(content)
            .kind(pathGuard.fileKind(rel))
            .sizeBytes(Files.size(file))
            .updatedAt(Files.getLastModifiedTime(file).toInstant().toString())
            .build();
    }

    public KnowledgeFileContentDto writeFile(
        String relativePath,
        String content,
        boolean autoConfigure
    ) throws Exception {
        WorkspaceCtx ctx = resolveWorkspace(autoConfigure);
        Path file = pathGuard.resolveInWorkspace(ctx.workspace(), relativePath);
        Files.createDirectories(file.getParent());
        String body = content != null ? content : "";
        Files.write(file, TextFileReader.withUtf8Bom(body),
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        graphCache.invalidate(ctx.workspace());
        return readFile(pathGuard.normalizeRelative(relativePath), false);
    }

    public void deleteFile(String relativePath, boolean autoConfigure) throws Exception {
        WorkspaceCtx ctx = resolveWorkspace(autoConfigure);
        Path file = pathGuard.resolveInWorkspace(ctx.workspace(), relativePath);
        if (!Files.isRegularFile(file)) {
            throw new NotFoundException("记忆文件", relativePath);
        }
        Files.delete(file);
        graphCache.invalidate(ctx.workspace());
    }

    public KnowledgeSearchResultDto search(String query, Integer limit, boolean autoConfigure) throws Exception {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("搜索关键词不能为空");
        }
        int max = limit != null && limit > 0 ? Math.min(limit, 50) : 20;
        WorkspaceCtx ctx = resolveWorkspace(autoConfigure);

        List<KnowledgeSearchHitDto> hits = tryCliSearch(query, max);
        String source = "cli";
        boolean fallback = false;

        if (hits.isEmpty()) {
            hits = keywordSearch(ctx.workspace(), query, max);
            source = "keyword";
            fallback = true;
        }

        return KnowledgeSearchResultDto.builder()
            .hits(hits)
            .fallback(fallback)
            .source(source)
            .graphOverlay(graphBuilder.buildSearchOverlay(hits))
            .build();
    }

    public KnowledgeIndexStatusDto getIndexStatus(boolean probe) {
        if (!gatewayClient.isConnected()) {
            return KnowledgeIndexStatusDto.builder()
                .gatewayConnected(false)
                .available(false)
                .summary("Gateway 未连接")
                .error("Gateway WebSocket 未连接")
                .build();
        }
        try {
            ObjectNode params = MAPPER.createObjectNode();
            if (probe) {
                params.put("probe", true);
            }
            JsonNode payload = gatewayClient.request("doctor.memory.status", params, RPC_TIMEOUT_MS);
            String summary = summarizeMemoryStatus(payload);
            return KnowledgeIndexStatusDto.builder()
                .gatewayConnected(true)
                .available(true)
                .summary(summary)
                .raw(payload)
                .build();
        } catch (Exception e) {
            log.debug("doctor.memory.status failed: {}", e.getMessage());
            return KnowledgeIndexStatusDto.builder()
                .gatewayConnected(true)
                .available(false)
                .summary("索引状态获取失败")
                .error(e.getMessage())
                .build();
        }
    }

    public KnowledgeIndexRebuildResultDto rebuildIndex() {
        var discovery = installDiscoveryService.discover();
        if (!discovery.isInstalled() || discovery.getCommandPath() == null) {
            return KnowledgeIndexRebuildResultDto.builder()
                .success(false)
                .exitCode(-1)
                .message("未找到 openclaw CLI，请先安装 OpenClaw")
                .build();
        }
        CliResult result = runOpenClawCommand(
            List.of("memory", "index", "--force"),
            INDEX_TIMEOUT_MS,
            discovery.getCommandPath()
        );
        boolean ok = result.exitCode() == 0;
        return KnowledgeIndexRebuildResultDto.builder()
            .success(ok)
            .exitCode(result.exitCode())
            .message(ok ? "索引重建完成" : truncate(result.output(), 500))
            .build();
    }

    private List<KnowledgeSearchHitDto> tryCliSearch(String query, int limit) {
        var discovery = installDiscoveryService.discover();
        if (!discovery.isInstalled() || discovery.getCommandPath() == null) {
            return List.of();
        }
        CliResult result = runOpenClawCommand(
            List.of("memory", "search", query, "--limit", String.valueOf(limit)),
            30_000,
            discovery.getCommandPath()
        );
        if (result.exitCode() != 0 || result.output().isBlank()) {
            return List.of();
        }
        return parseCliSearchOutput(result.output(), limit);
    }

    private List<KnowledgeSearchHitDto> parseCliSearchOutput(String output, int limit) {
        List<KnowledgeSearchHitDto> hits = new ArrayList<>();
        String[] blocks = output.split("(?=^\\s*path[=:])", Pattern.MULTILINE);
        if (blocks.length <= 1) {
            blocks = output.split("\n\n+");
        }
        for (String block : blocks) {
            if (hits.size() >= limit) {
                break;
            }
            String path = extractField(block, "path");
            if (path == null) {
                continue;
            }
            path = path.replace('\\', '/');
            if (!pathGuard.isAllowedRelative(path)) {
                continue;
            }
            Integer lineStart = parseIntField(block, "line", "startLine", "lineStart");
            Integer lineEnd = parseIntField(block, "endLine", "lineEnd");
            String snippet = extractField(block, "snippet", "text", "content");
            if (snippet == null) {
                snippet = block.length() > 400 ? block.substring(0, 400) : block;
            }
            Double score = parseScore(block);
            hits.add(KnowledgeSearchHitDto.builder()
                .path(path)
                .lineStart(lineStart)
                .lineEnd(lineEnd)
                .snippet(snippet.trim())
                .score(score)
                .nodeId(lineStart != null ? "chunk:" + path + "#L" + lineStart : "file:" + path)
                .build());
        }
        if (hits.isEmpty() && !output.isBlank()) {
            hits.add(KnowledgeSearchHitDto.builder()
                .path("MEMORY.md")
                .snippet(truncate(output, 400))
                .score(0.3)
                .nodeId("file:MEMORY.md")
                .build());
        }
        return hits;
    }

    private List<KnowledgeSearchHitDto> keywordSearch(Path workspace, String query, int limit) throws Exception {
        String q = query.toLowerCase(Locale.ROOT);
        List<KnowledgeSearchHitDto> hits = new ArrayList<>();
        for (KnowledgeFileDto file : fileScanner.listFiles(workspace)) {
            if (hits.size() >= limit) {
                break;
            }
            Path path = pathGuard.resolveInWorkspace(workspace, file.getPath());
            String content = TextFileReader.readString(path);
            String lower = content.toLowerCase(Locale.ROOT);
            int idx = lower.indexOf(q);
            if (idx < 0) {
                continue;
            }
            int line = 1 + content.substring(0, idx).split("\n", -1).length - 1;
            int start = Math.max(0, idx - 80);
            int end = Math.min(content.length(), idx + q.length() + 120);
            hits.add(KnowledgeSearchHitDto.builder()
                .path(file.getPath())
                .lineStart(line)
                .lineEnd(line)
                .snippet(content.substring(start, end).replace('\n', ' ').trim())
                .score(0.4)
                .nodeId("file:" + file.getPath())
                .build());
        }
        return hits;
    }

    private static String extractField(String block, String... keys) {
        for (String key : keys) {
            Pattern p = Pattern.compile("^\\s*" + Pattern.quote(key) + "[=:]\\s*(.+)$",
                Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(block);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }

    private static Integer parseIntField(String block, String... keys) {
        String v = extractField(block, keys);
        if (v == null) {
            return null;
        }
        try {
            return Integer.parseInt(v.replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseScore(String block) {
        Matcher m = SCORE_PATTERN.matcher(block);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {
                return 0.5;
            }
        }
        return 0.5;
    }

    private static String summarizeMemoryStatus(JsonNode payload) {
        if (payload == null || payload.isMissingNode()) {
            return "无数据";
        }
        if (payload.has("enabled")) {
            return payload.get("enabled").asBoolean() ? "记忆索引已启用" : "记忆索引未启用";
        }
        if (payload.has("status")) {
            return payload.get("status").asText();
        }
        return "已就绪";
    }

    private CliResult runOpenClawCommand(List<String> args, long timeoutMs, String commandPath) {
        List<String> command = new ArrayList<>();
        if ("npx openclaw".equals(commandPath)) {
            command.add("cmd");
            command.add("/c");
            command.add("npx");
            command.add("openclaw");
        } else {
            command.add("cmd");
            command.add("/c");
            command.add(commandPath);
        }
        command.addAll(args);
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CliResult(-1, "命令超时");
            }
            return new CliResult(process.exitValue(), output.toString().trim());
        } catch (Exception e) {
            return new CliResult(-1, e.getMessage());
        }
    }

    public List<KnowledgeWorkspaceCandidateDto> listWorkspaceCandidates() throws Exception {
        Path configPath = workspaceConfigService.resolveConfigPath();
        String active = workspaceResolver.resolve(false).workspacePath().toString();
        return workspaceResolver.discoverCandidates(configPath).stream()
            .map(c -> KnowledgeWorkspaceCandidateDto.builder()
                .path(c.path())
                .memoryFileCount(c.memoryFileCount())
                .source(c.source())
                .configured(c.configured())
                .active(active.equalsIgnoreCase(c.path()))
                .build())
            .toList();
    }

    public KnowledgeDiscoverResultDto discoverWorkspace(boolean persistToConfig) throws Exception {
        OpenClawMemoryWorkspaceResolver.ResolvedWorkspace resolved =
            workspaceResolver.resolve(persistToConfig);
        KnowledgeOverviewDto overview = getOverview(false);
        int memCount = OpenClawMemoryWorkspaceResolver.countMemoryFiles(resolved.workspacePath());
        String activeWs = resolved.workspacePath().toString();
        List<KnowledgeWorkspaceCandidateDto> candidateDtos = resolved.candidates().stream()
            .map(c -> KnowledgeWorkspaceCandidateDto.builder()
                .path(c.path())
                .memoryFileCount(c.memoryFileCount())
                .source(c.source())
                .configured(c.configured())
                .active(activeWs.equalsIgnoreCase(c.path()))
                .build())
            .toList();
        return KnowledgeDiscoverResultDto.builder()
            .chosenWorkspacePath(activeWs)
            .resolutionSource(resolved.resolutionSource())
            .persistedToConfig(persistToConfig && resolved.autoConfigured())
            .memoryFileCount(memCount)
            .candidates(candidateDtos)
            .overview(overview)
            .build();
    }

    public KnowledgeOverviewDto useWorkspace(String workspacePath, boolean persistToConfig) throws Exception {
        Path ws = Path.of(workspacePath).toAbsolutePath().normalize();
        if (!Files.isDirectory(ws)) {
            throw new IllegalArgumentException("工作区目录不存在: " + workspacePath);
        }
        if (persistToConfig) {
            workspaceConfigService.writeWorkspaceToConfigPublic(
                workspaceConfigService.resolveConfigPath(),
                ws
            );
            OpenClawGatewayConfigReader.invalidateConfigCache();
        }
        return getOverview(false);
    }

    private WorkspaceCtx resolveWorkspace(boolean autoConfigure) throws Exception {
        OpenClawMemoryWorkspaceResolver.ResolvedWorkspace resolved =
            workspaceResolver.resolve(autoConfigure);
        Path workspace = resolved.workspacePath();
        if (!OpenClawKnowledgePathGuard.workspaceExists(workspace)) {
            Files.createDirectories(workspace);
            Files.createDirectories(workspace.resolve("memory"));
        }
        return new WorkspaceCtx(resolved, workspace);
    }

    public String defaultTodayDiaryPath() {
        return "memory/" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private record WorkspaceCtx(
        OpenClawMemoryWorkspaceResolver.ResolvedWorkspace resolved,
        Path workspace
    ) {}

    private record CliResult(int exitCode, String output) {}
}
