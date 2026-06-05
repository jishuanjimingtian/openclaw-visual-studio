package com.openclaw.vs.service;

import com.openclaw.vs.dto.KnowledgeGraphDto;
import com.openclaw.vs.dto.KnowledgeGraphEdgeDto;
import com.openclaw.vs.dto.KnowledgeGraphNodeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class OpenClawKnowledgeGraphCache {

    private record CacheKey(String workspace, boolean includeChunks, int dailyWindowDays, String fingerprint) {}

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(CacheKey key, KnowledgeGraphDto graph, long createdAt) {}

    private final OpenClawKnowledgeFileScanner fileScanner;
    private final OpenClawKnowledgeGraphBuilder graphBuilder;

    public KnowledgeGraphDto getOrBuild(
        Path workspace,
        boolean includeChunks,
        int dailyWindowDays
    ) throws Exception {
        String ws = workspace.toAbsolutePath().normalize().toString();
        String fingerprint = fileScanner.fingerprint(workspace);
        CacheKey key = new CacheKey(ws, includeChunks, dailyWindowDays, fingerprint);
        String cacheId = key.workspace() + "|" + key.includeChunks() + "|" + key.dailyWindowDays();
        CacheEntry existing = cache.get(cacheId);
        if (existing != null && existing.key().fingerprint().equals(fingerprint)) {
            return existing.graph();
        }
        KnowledgeGraphDto graph = graphBuilder.build(workspace, includeChunks, dailyWindowDays);
        cache.put(cacheId, new CacheEntry(key, graph, System.currentTimeMillis()));
        return graph;
    }

    public KnowledgeGraphDto subgraphFromCache(
        Path workspace,
        String relativePath,
        boolean includeChunks,
        int dailyWindowDays
    ) throws Exception {
        KnowledgeGraphDto full = getOrBuild(workspace, includeChunks, dailyWindowDays);
        String fileId = "file:" + relativePath.replace('\\', '/');
        Set<String> keep = new HashSet<>();
        keep.add(fileId);
        for (KnowledgeGraphEdgeDto edge : full.getEdges()) {
            if (fileId.equals(edge.getSource()) || fileId.equals(edge.getTarget())) {
                keep.add(edge.getSource());
                keep.add(edge.getTarget());
            }
        }
        List<KnowledgeGraphNodeDto> nodes = full.getNodes().stream()
            .filter(n -> keep.contains(n.getId()))
            .toList();
        List<KnowledgeGraphEdgeDto> edges = full.getEdges().stream()
            .filter(e -> keep.contains(e.getSource()) && keep.contains(e.getTarget()))
            .toList();
        return KnowledgeGraphDto.builder()
            .nodes(new ArrayList<>(nodes))
            .edges(new ArrayList<>(edges))
            .meta(full.getMeta())
            .build();
    }

    public void invalidate(Path workspace) {
        String prefix = workspace.toAbsolutePath().normalize().toString();
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
