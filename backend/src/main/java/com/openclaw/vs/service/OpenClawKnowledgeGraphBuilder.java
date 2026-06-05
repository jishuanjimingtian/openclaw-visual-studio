package com.openclaw.vs.service;

import com.openclaw.vs.dto.KnowledgeGraphDto;
import com.openclaw.vs.dto.KnowledgeGraphEdgeDto;
import com.openclaw.vs.dto.KnowledgeGraphMetaDto;
import com.openclaw.vs.dto.KnowledgeGraphNodeDto;
import com.openclaw.vs.dto.KnowledgeGraphOverlayDto;
import com.openclaw.vs.dto.KnowledgeSearchHitDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.openclaw.vs.util.TextFileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class OpenClawKnowledgeGraphBuilder {

    public static final int DEFAULT_DAILY_WINDOW_DAYS = 90;

    private static final int MAX_CHUNKS_PER_FILE = 20;
    private static final Pattern TAG_PATTERN = Pattern.compile("#([\\p{L}\\p{N}_-]+)");
    private static final Pattern WIKILINK = Pattern.compile("\\[\\[([^\\]]+)]]");
    private static final Pattern MD_LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern MEMORY_PATH = Pattern.compile("memory/[\\w./-]+\\.md", Pattern.CASE_INSENSITIVE);
    private static final List<String> PROMOTE_KEYWORDS = List.of(
        "memory.md", "长期记忆", "晋升", "promote", "写入 memory", "durable memory"
    );

    private final OpenClawKnowledgePathGuard pathGuard;
    private final OpenClawKnowledgeFileScanner fileScanner;

    public KnowledgeGraphDto build(Path workspace, boolean includeChunks) throws Exception {
        return build(workspace, includeChunks, DEFAULT_DAILY_WINDOW_DAYS);
    }

    public KnowledgeGraphDto build(Path workspace, boolean includeChunks, int dailyWindowDays) throws Exception {
        List<Path> allFiles = fileScanner.listPaths(workspace);
        LocalDate cutoff = dailyWindowDays > 0
            ? LocalDate.now().minusDays(dailyWindowDays)
            : null;
        List<Path> files = allFiles.stream()
            .filter(p -> {
                String rel = workspace.relativize(p).toString().replace('\\', '/');
                if (!"daily".equals(pathGuard.fileKind(rel))) {
                    return true;
                }
                if (cutoff == null) {
                    return true;
                }
                LocalDate d = parseDailyDate(rel);
                return d != null && !d.isBefore(cutoff);
            })
            .toList();

        List<KnowledgeGraphNodeDto> nodes = new ArrayList<>();
        List<KnowledgeGraphEdgeDto> edges = new ArrayList<>();
        Map<String, String> pathToFileNodeId = new LinkedHashMap<>();
        Map<String, List<String>> tagToNodeIds = new HashMap<>();
        Map<String, String> contentByRel = new LinkedHashMap<>();
        Set<String> topicIds = new HashSet<>();

        for (Path file : files) {
            String rel = workspace.relativize(file).toString().replace('\\', '/');
            String kind = pathGuard.fileKind(rel);
            String fileNodeId = "file:" + rel;
            long size = Files.size(file);
            String updatedAt = Files.getLastModifiedTime(file).toInstant().toString();
            String label = labelForFile(rel, kind);

            pathToFileNodeId.put(rel.toLowerCase(Locale.ROOT), fileNodeId);
            nodes.add(KnowledgeGraphNodeDto.builder()
                .id(fileNodeId)
                .label(label)
                .kind(kind)
                .path(rel)
                .size((int) Math.min(size, Integer.MAX_VALUE))
                .updatedAt(updatedAt)
                .build());

            String content = TextFileReader.readString(file);
            contentByRel.put(rel, content);

            if (includeChunks) {
                addChunkNodes(nodes, edges, rel, content, updatedAt);
                collectTags(content, tagToNodeIds, fileNodeId);
            }
        }

        if (!includeChunks) {
            for (Map.Entry<String, String> entry : contentByRel.entrySet()) {
                String sourceId = pathToFileNodeId.get(entry.getKey().toLowerCase(Locale.ROOT));
                collectTags(entry.getValue(), tagToNodeIds, sourceId);
            }
        }

        addTopicNodes(nodes, tagToNodeIds, topicIds);
        addTagEdges(edges, tagToNodeIds, topicIds);
        addTemporalEdges(edges, files, workspace, pathToFileNodeId);

        for (Map.Entry<String, String> entry : contentByRel.entrySet()) {
            String rel = entry.getKey();
            String content = entry.getValue();
            String sourceId = pathToFileNodeId.get(rel.toLowerCase(Locale.ROOT));
            addLinkEdges(edges, rel, content, pathToFileNodeId, sourceId);
            addPromoteEdges(edges, rel, content, pathToFileNodeId, sourceId);
        }

        int totalDaily = (int) allFiles.stream()
            .map(p -> workspace.relativize(p).toString().replace('\\', '/'))
            .filter(r -> "daily".equals(pathGuard.fileKind(r)))
            .count();
        int hiddenDaily = totalDaily - (int) files.stream()
            .map(p -> workspace.relativize(p).toString().replace('\\', '/'))
            .filter(r -> "daily".equals(pathGuard.fileKind(r)))
            .count();

        return KnowledgeGraphDto.builder()
            .nodes(nodes)
            .edges(dedupeEdges(edges))
            .meta(KnowledgeGraphMetaDto.builder()
                .workspacePath(workspace.toString())
                .generatedAt(Instant.now().toString())
                .fileCount(files.size())
                .includeChunks(includeChunks)
                .dailyWindowDays(dailyWindowDays)
                .hiddenDailyCount(hiddenDaily)
                .build())
            .build();
    }

    public KnowledgeGraphOverlayDto buildSearchOverlay(List<KnowledgeSearchHitDto> hits) {
        if (hits == null || hits.isEmpty()) {
            return KnowledgeGraphOverlayDto.builder()
                .highlightNodeIds(List.of())
                .edges(List.of())
                .build();
        }
        List<String> highlight = new ArrayList<>();
        List<KnowledgeGraphEdgeDto> semanticEdges = new ArrayList<>();
        for (var hit : hits) {
            if (hit.getNodeId() != null) {
                highlight.add(hit.getNodeId());
            } else if (hit.getPath() != null) {
                highlight.add("file:" + hit.getPath());
            }
        }
        for (int i = 0; i < hits.size(); i++) {
            for (int j = i + 1; j < Math.min(hits.size(), i + 4); j++) {
                String a = nodeIdForHit(hits.get(i));
                String b = nodeIdForHit(hits.get(j));
                if (a == null || b == null || a.equals(b)) {
                    continue;
                }
                double score = averageScore(hits.get(i), hits.get(j));
                semanticEdges.add(KnowledgeGraphEdgeDto.builder()
                    .id("semantic:" + a + ":" + b)
                    .source(a)
                    .target(b)
                    .kind("semantic")
                    .value(score)
                    .label("语义相近")
                    .build());
            }
        }
        return KnowledgeGraphOverlayDto.builder()
            .highlightNodeIds(highlight.stream().distinct().toList())
            .edges(semanticEdges)
            .build();
    }

    private static String nodeIdForHit(KnowledgeSearchHitDto hit) {
        if (hit.getNodeId() != null) {
            return hit.getNodeId();
        }
        if (hit.getPath() != null) {
            return "file:" + hit.getPath();
        }
        return null;
    }

    private static double averageScore(KnowledgeSearchHitDto a, KnowledgeSearchHitDto b) {
        double sa = a.getScore() != null ? a.getScore() : 0.5;
        double sb = b.getScore() != null ? b.getScore() : 0.5;
        return (sa + sb) / 2.0;
    }

    private void addChunkNodes(
        List<KnowledgeGraphNodeDto> nodes,
        List<KnowledgeGraphEdgeDto> edges,
        String rel,
        String content,
        String updatedAt
    ) {
        String[] lines = content.split("\n", -1);
        String currentTitle = null;
        int chunkStart = 1;
        int chunkCount = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("## ") || line.startsWith("### ")) {
                if (currentTitle != null && chunkCount < MAX_CHUNKS_PER_FILE) {
                    addChunkNode(nodes, edges, rel, currentTitle, chunkStart, updatedAt);
                    chunkCount++;
                }
                currentTitle = line.replaceFirst("^#+\\s*", "").trim();
                chunkStart = i + 1;
            }
        }
        if (currentTitle != null && chunkCount < MAX_CHUNKS_PER_FILE) {
            addChunkNode(nodes, edges, rel, currentTitle, chunkStart, updatedAt);
        }
    }

    private void addChunkNode(
        List<KnowledgeGraphNodeDto> nodes,
        List<KnowledgeGraphEdgeDto> edges,
        String rel,
        String title,
        int lineStart,
        String updatedAt
    ) {
        String slug = slugify(title);
        if (slug.isBlank()) {
            slug = "line-" + lineStart;
        }
        String id = "chunk:" + rel + "#" + slug;
        nodes.add(KnowledgeGraphNodeDto.builder()
            .id(id)
            .label(truncate(title, 40))
            .kind("chunk")
            .path(rel)
            .lineStart(lineStart)
            .size(8)
            .updatedAt(updatedAt)
            .build());
        edges.add(KnowledgeGraphEdgeDto.builder()
            .id("parent:" + id)
            .source("file:" + rel)
            .target(id)
            .kind("link")
            .value(1.0)
            .label("条目")
            .build());
    }

    private void collectTags(String content, Map<String, List<String>> tagToNodeIds, String nodeId) {
        if (nodeId == null) {
            return;
        }
        Matcher m = TAG_PATTERN.matcher(content);
        while (m.find()) {
            String tag = m.group(1).toLowerCase(Locale.ROOT);
            tagToNodeIds.computeIfAbsent(tag, k -> new ArrayList<>()).add(nodeId);
        }
    }

    private void addTopicNodes(
        List<KnowledgeGraphNodeDto> nodes,
        Map<String, List<String>> tagToNodeIds,
        Set<String> topicIds
    ) {
        for (String tag : tagToNodeIds.keySet()) {
            String topicId = "topic:" + tag;
            if (topicIds.add(topicId)) {
                nodes.add(KnowledgeGraphNodeDto.builder()
                    .id(topicId)
                    .label("#" + tag)
                    .kind("topic")
                    .size(12)
                    .tags(List.of(tag))
                    .build());
            }
        }
    }

    private void addTagEdges(
        List<KnowledgeGraphEdgeDto> edges,
        Map<String, List<String>> tagToNodeIds,
        Set<String> topicIds
    ) {
        for (Map.Entry<String, List<String>> entry : tagToNodeIds.entrySet()) {
            String topicId = "topic:" + entry.getKey();
            if (!topicIds.contains(topicId)) {
                continue;
            }
            for (String nodeId : entry.getValue().stream().distinct().toList()) {
                edges.add(KnowledgeGraphEdgeDto.builder()
                    .id("tag:" + nodeId + ":" + topicId)
                    .source(nodeId)
                    .target(topicId)
                    .kind("tag")
                    .value(0.5)
                    .label("#" + entry.getKey())
                    .build());
            }
        }
    }

    private void addTemporalEdges(
        List<KnowledgeGraphEdgeDto> edges,
        List<Path> files,
        Path workspace,
        Map<String, String> pathToFileNodeId
    ) {
        List<String> dailyPaths = files.stream()
            .map(p -> workspace.relativize(p).toString().replace('\\', '/'))
            .filter(p -> "daily".equals(pathGuard.fileKind(p)))
            .sorted(Comparator.comparing(this::parseDailyDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        for (int i = 1; i < dailyPaths.size(); i++) {
            String prev = dailyPaths.get(i - 1);
            String curr = dailyPaths.get(i);
            String src = pathToFileNodeId.get(prev.toLowerCase(Locale.ROOT));
            String tgt = pathToFileNodeId.get(curr.toLowerCase(Locale.ROOT));
            if (src != null && tgt != null) {
                edges.add(KnowledgeGraphEdgeDto.builder()
                    .id("temporal:" + prev + ":" + curr)
                    .source(src)
                    .target(tgt)
                    .kind("temporal")
                    .value(0.6)
                    .label("时间")
                    .build());
            }
        }
    }

    private LocalDate parseDailyDate(String rel) {
        String name = rel.substring("memory/".length());
        if (name.endsWith(".md")) {
            name = name.substring(0, name.length() - 3);
        }
        int dashExtra = name.indexOf('-', 10);
        if (dashExtra > 0) {
            name = name.substring(0, dashExtra);
        }
        try {
            return LocalDate.parse(name, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void addLinkEdges(
        List<KnowledgeGraphEdgeDto> edges,
        String rel,
        String content,
        Map<String, String> pathToFileNodeId,
        String sourceId
    ) {
        if (sourceId == null) {
            return;
        }
        Set<String> targets = new HashSet<>();
        Matcher wiki = WIKILINK.matcher(content);
        while (wiki.find()) {
            targets.add(resolveLinkTarget(wiki.group(1)));
        }
        Matcher md = MD_LINK.matcher(content);
        while (md.find()) {
            targets.add(resolveLinkTarget(md.group(2)));
        }
        Matcher mem = MEMORY_PATH.matcher(content);
        while (mem.find()) {
            targets.add(mem.group().replace('\\', '/'));
        }
        String lower = content.toLowerCase(Locale.ROOT);
        if (lower.contains("memory.md")) {
            targets.add("MEMORY.md");
        }
        if (lower.contains("soul.md")) {
            targets.add("SOUL.md");
        }
        if (lower.contains("user.md")) {
            targets.add("USER.md");
        }
        if (lower.contains("agents.md")) {
            targets.add("AGENTS.md");
        }
        for (String targetRel : targets) {
            if (targetRel == null || targetRel.isBlank()) {
                continue;
            }
            String normalized = targetRel.replace('\\', '/');
            if (!pathGuard.isAllowedRelative(normalized)) {
                continue;
            }
            String targetId = pathToFileNodeId.get(normalized.toLowerCase(Locale.ROOT));
            if (targetId != null && !targetId.equals(sourceId)) {
                edges.add(KnowledgeGraphEdgeDto.builder()
                    .id("link:" + sourceId + ":" + targetId)
                    .source(sourceId)
                    .target(targetId)
                    .kind("link")
                    .value(0.85)
                    .label("引用")
                    .build());
            }
        }
    }

    private void addPromoteEdges(
        List<KnowledgeGraphEdgeDto> edges,
        String rel,
        String content,
        Map<String, String> pathToFileNodeId,
        String sourceId
    ) {
        if (sourceId == null || !rel.startsWith("memory/") || rel.startsWith("memory/.dreams/")) {
            return;
        }
        if (!"daily".equals(pathGuard.fileKind(rel))) {
            return;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        boolean promote = PROMOTE_KEYWORDS.stream().anyMatch(lower::contains);
        if (!promote) {
            return;
        }
        String hubId = pathToFileNodeId.get("memory.md");
        if (hubId != null && !hubId.equals(sourceId)) {
            edges.add(KnowledgeGraphEdgeDto.builder()
                .id("promote:" + sourceId + ":" + hubId)
                .source(sourceId)
                .target(hubId)
                .kind("promote")
                .value(0.7)
                .label("推断·晋升")
                .build());
        }
    }

    private String resolveLinkTarget(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().replace('\\', '/');
        if (t.isEmpty()) {
            return null;
        }
        if (t.startsWith("memory/") && t.endsWith(".md")) {
            return t;
        }
        if (t.equalsIgnoreCase("MEMORY.md") || t.endsWith("/MEMORY.md")) {
            return "MEMORY.md";
        }
        if (t.equalsIgnoreCase("SOUL.md") || t.endsWith("/SOUL.md")) {
            return "SOUL.md";
        }
        if (t.equalsIgnoreCase("USER.md") || t.endsWith("/USER.md")) {
            return "USER.md";
        }
        if (t.equalsIgnoreCase("AGENTS.md") || t.endsWith("/AGENTS.md")) {
            return "AGENTS.md";
        }
        if (t.equalsIgnoreCase("DREAMS.md") || t.endsWith("/DREAMS.md")) {
            return "DREAMS.md";
        }
        if (t.endsWith(".md") && !t.contains("/")) {
            String upper = t.toUpperCase(Locale.ROOT);
            if ("MEMORY.MD".equals(upper)) {
                return "MEMORY.md";
            }
            if ("DREAMS.MD".equals(upper)) {
                return "DREAMS.md";
            }
            if ("SOUL.MD".equals(upper)) {
                return "SOUL.md";
            }
            if ("USER.MD".equals(upper)) {
                return "USER.md";
            }
            if ("AGENTS.MD".equals(upper)) {
                return "AGENTS.md";
            }
            return "memory/" + t;
        }
        return null;
    }

    static String labelForFile(String rel, String kind) {
        return switch (kind) {
            case "hub" -> "MEMORY · 长期";
            case "dream" -> "DREAMS · 梦境";
            case "dream_shard" -> rel.substring("memory/.dreams/".length()).replace(".md", "");
            case "soul" -> "SOUL · 灵魂";
            case "user" -> "USER · 用户";
            case "agents" -> "AGENTS · 工作区";
            case "daily" -> rel.substring("memory/".length()).replace(".md", "");
            default -> rel;
        };
    }

    private static String slugify(String title) {
        return title.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
            .replaceAll("^-|-$", "");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static List<KnowledgeGraphEdgeDto> dedupeEdges(List<KnowledgeGraphEdgeDto> edges) {
        Map<String, KnowledgeGraphEdgeDto> map = new LinkedHashMap<>();
        for (KnowledgeGraphEdgeDto e : edges) {
            map.putIfAbsent(e.getId(), e);
        }
        return new ArrayList<>(map.values());
    }
}
