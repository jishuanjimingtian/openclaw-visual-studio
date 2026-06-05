package com.openclaw.vs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openclaw.vs.dto.CronJobDto;
import com.openclaw.vs.dto.CronListPageDto;
import com.openclaw.vs.dto.CronOverviewDto;
import com.openclaw.vs.dto.CronRunsPageDto;
import com.openclaw.vs.dto.CronStatusDto;
import com.openclaw.vs.gateway.GatewayWebSocketClient;
import com.openclaw.vs.gateway.OpenClawGatewayConfigReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenClawCronService {

    private static final long LIST_CACHE_TTL_MS = 5_000;
    private static final long STATUS_CACHE_TTL_MS = 10_000;
    private static final long RUNS_CACHE_TTL_MS = 8_000;
    private static final long LIST_TIMEOUT_MS = 10_000;
    private static final long STATUS_TIMEOUT_MS = 8_000;
    private static final long RUNS_TIMEOUT_MS = 12_000;
    private static final long WRITE_TIMEOUT_MS = 20_000;
    private static final int DEFAULT_LIST_LIMIT = 50;

    private final GatewayWebSocketClient gatewayClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentHashMap<String, CachedList> listCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<CronListPageDto>> inflightList = new ConcurrentHashMap<>();
    private volatile CachedStatus statusCache;
    private volatile CompletableFuture<CronStatusDto> inflightStatus;
    private final ConcurrentHashMap<String, CachedRuns> runsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<CronRunsPageDto>> inflightRuns = new ConcurrentHashMap<>();

    private record CachedList(long fetchedAtMs, CronListPageDto page) {}

    private record CachedStatus(long fetchedAtMs, CronStatusDto status) {}

    private record CachedRuns(long fetchedAtMs, CronRunsPageDto page) {}

    public record CronListQuery(
        Integer limit,
        Integer offset,
        String query,
        String enabled,
        String sortBy,
        String sortDir,
        Boolean includeDisabled
    ) {}

    public CronOverviewDto getOverview(CronListQuery query) {
        CronStatusDto status = getStatus();
        CronListPageDto list = listJobs(query != null ? query : defaultListQuery());
        return CronOverviewDto.builder()
            .gatewayConnected(list.isGatewayConnected())
            .gatewayPort(list.getGatewayPort())
            .gatewayWsUrl(list.getGatewayWsUrl())
            .connectionHint(list.getConnectionHint())
            .schedulerStatus(status.getStatus())
            .jobs(list.getJobs())
            .total(list.getTotal())
            .limit(list.getLimit())
            .offset(list.getOffset())
            .build();
    }

    public CronStatusDto getStatus() {
        long now = System.currentTimeMillis();
        CachedStatus cached = statusCache;
        if (cached != null && now - cached.fetchedAtMs() < STATUS_CACHE_TTL_MS) {
            return cached.status();
        }

        CompletableFuture<CronStatusDto> shared = inflightStatus;
        if (shared == null || shared.isDone()) {
            synchronized (this) {
                shared = inflightStatus;
                if (shared == null || shared.isDone()) {
                    shared = CompletableFuture.supplyAsync(this::fetchStatusOnce);
                    inflightStatus = shared;
                }
            }
        }

        try {
            CronStatusDto result = shared.get(STATUS_TIMEOUT_MS + 2_000, TimeUnit.MILLISECONDS);
            statusCache = new CachedStatus(System.currentTimeMillis(), result);
            return result;
        } catch (Exception e) {
            log.warn("cron.status failed: {}", e.getMessage());
            return disconnectedStatus();
        } finally {
            if (inflightStatus == shared && shared.isDone()) {
                inflightStatus = null;
            }
        }
    }

    public CronListPageDto listJobs(CronListQuery query) {
        String cacheKey = listCacheKey(query);
        long now = System.currentTimeMillis();
        CachedList cached = listCache.get(cacheKey);
        if (cached != null && now - cached.fetchedAtMs() < LIST_CACHE_TTL_MS) {
            return cached.page();
        }

        CompletableFuture<CronListPageDto> shared = inflightList.computeIfAbsent(
            cacheKey,
            key -> CompletableFuture.supplyAsync(() -> fetchListOnce(query))
        );

        try {
            CronListPageDto result = shared.get(LIST_TIMEOUT_MS + 2_000, TimeUnit.MILLISECONDS);
            listCache.put(cacheKey, new CachedList(System.currentTimeMillis(), result));
            return result;
        } catch (Exception e) {
            log.warn("cron.list failed: {}", e.getMessage());
            return disconnectedList();
        } finally {
            inflightList.remove(cacheKey, shared);
        }
    }

    public CronRunsPageDto listRuns(
        String jobId,
        Integer limit,
        Integer offset,
        String status,
        String sortDir
    ) {
        int effectiveLimit = limit != null && limit > 0 ? Math.min(limit, 100) : 30;
        int effectiveOffset = offset != null && offset >= 0 ? offset : 0;
        String cacheKey = jobId + "|" + effectiveLimit + "|" + effectiveOffset + "|"
            + (status != null ? status : "") + "|" + (sortDir != null ? sortDir : "desc");

        long now = System.currentTimeMillis();
        CachedRuns cached = runsCache.get(cacheKey);
        if (cached != null && now - cached.fetchedAtMs() < RUNS_CACHE_TTL_MS) {
            return cached.page();
        }

        CompletableFuture<CronRunsPageDto> shared = inflightRuns.computeIfAbsent(
            cacheKey,
            key -> CompletableFuture.supplyAsync(
                () -> fetchRunsOnce(jobId, effectiveLimit, effectiveOffset, status, sortDir)
            )
        );

        try {
            CronRunsPageDto result = shared.get(RUNS_TIMEOUT_MS + 2_000, TimeUnit.MILLISECONDS);
            runsCache.put(cacheKey, new CachedRuns(System.currentTimeMillis(), result));
            return result;
        } catch (Exception e) {
            log.warn("cron.runs failed for {}: {}", jobId, e.getMessage());
            return disconnectedRuns(jobId);
        } finally {
            inflightRuns.remove(cacheKey, shared);
        }
    }

    public JsonNode addJob(JsonNode body) throws Exception {
        requireConnected();
        JsonNode result = gatewayClient.request("cron.add", body, WRITE_TIMEOUT_MS);
        invalidateListCache();
        return result;
    }

    public JsonNode updateJob(String jobId, JsonNode patch) throws Exception {
        requireConnected();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("id", jobId);
        params.set("patch", patch != null ? patch : objectMapper.createObjectNode());
        JsonNode result = gatewayClient.request("cron.update", params, WRITE_TIMEOUT_MS);
        invalidateListCache();
        invalidateRunsForJob(jobId);
        return result;
    }

    public JsonNode removeJob(String jobId) throws Exception {
        requireConnected();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("id", jobId);
        JsonNode result = gatewayClient.request("cron.remove", params, WRITE_TIMEOUT_MS);
        invalidateListCache();
        invalidateRunsForJob(jobId);
        return result;
    }

    public JsonNode runJob(String jobId, String mode) throws Exception {
        requireConnected();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("id", jobId);
        params.put("mode", mode != null && !mode.isBlank() ? mode.trim() : "force");
        JsonNode result = gatewayClient.request("cron.run", params, WRITE_TIMEOUT_MS);
        invalidateRunsForJob(jobId);
        return result;
    }

    public void invalidateListCache() {
        listCache.clear();
        statusCache = null;
    }

    private void invalidateRunsForJob(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            runsCache.clear();
            return;
        }
        runsCache.keySet().removeIf(key -> key.startsWith(jobId + "|"));
    }

    private CronListQuery defaultListQuery() {
        return new CronListQuery(DEFAULT_LIST_LIMIT, 0, null, "all", "nextRunAtMs", "desc", false);
    }

    private static String listCacheKey(CronListQuery q) {
        return q.limit() + "|" + q.offset() + "|"
            + (q.query() != null ? q.query().trim() : "") + "|"
            + (q.enabled() != null ? q.enabled() : "all") + "|"
            + (q.sortBy() != null ? q.sortBy() : "nextRunAtMs") + "|"
            + (q.sortDir() != null ? q.sortDir() : "desc") + "|"
            + Boolean.TRUE.equals(q.includeDisabled());
    }

    private GatewayContext gatewayContext() {
        int gatewayPort = OpenClawGatewayConfigReader.resolveEffectiveGatewayPort(
            OpenClawGatewayConfigReader.DEFAULT_GATEWAY_PORT);
        String gatewayWsUrl = OpenClawGatewayConfigReader.toWebSocketUrl(gatewayPort);
        return new GatewayContext(gatewayPort, gatewayWsUrl, buildConnectionHint(gatewayPort));
    }

    private record GatewayContext(int port, String wsUrl, String hint) {}

    private CronStatusDto fetchStatusOnce() {
        GatewayContext ctx = gatewayContext();
        if (!gatewayClient.isConnected()) {
            return CronStatusDto.builder()
                .gatewayConnected(false)
                .gatewayPort(ctx.port())
                .gatewayWsUrl(ctx.wsUrl())
                .connectionHint(ctx.hint())
                .status(null)
                .build();
        }
        try {
            JsonNode payload = gatewayClient.request(
                "cron.status",
                objectMapper.createObjectNode(),
                STATUS_TIMEOUT_MS
            );
            return CronStatusDto.builder()
                .gatewayConnected(true)
                .gatewayPort(ctx.port())
                .gatewayWsUrl(ctx.wsUrl())
                .status(payload)
                .build();
        } catch (Exception e) {
            log.warn("Failed cron.status: {}", e.getMessage());
            return CronStatusDto.builder()
                .gatewayConnected(false)
                .gatewayPort(ctx.port())
                .gatewayWsUrl(ctx.wsUrl())
                .connectionHint(ctx.hint())
                .status(null)
                .build();
        }
    }

    private CronListPageDto fetchListOnce(CronListQuery query) {
        GatewayContext ctx = gatewayContext();
        int limit = query.limit() != null && query.limit() > 0 ? Math.min(query.limit(), 200) : DEFAULT_LIST_LIMIT;
        int offset = query.offset() != null && query.offset() >= 0 ? query.offset() : 0;

        if (!gatewayClient.isConnected()) {
            return CronListPageDto.builder()
                .gatewayConnected(false)
                .gatewayPort(ctx.port())
                .gatewayWsUrl(ctx.wsUrl())
                .connectionHint(ctx.hint())
                .jobs(List.of())
                .total(0)
                .limit(limit)
                .offset(offset)
                .build();
        }

        try {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("limit", limit);
            params.put("offset", offset);
            if (query.query() != null && !query.query().isBlank()) {
                params.put("query", query.query().trim());
            }
            if (query.enabled() != null && !query.enabled().isBlank() && !"all".equals(query.enabled())) {
                params.put("enabled", query.enabled());
            }
            if (query.sortBy() != null && !query.sortBy().isBlank()) {
                params.put("sortBy", query.sortBy());
            }
            if (query.sortDir() != null && !query.sortDir().isBlank()) {
                params.put("sortDir", query.sortDir());
            }
            if (Boolean.TRUE.equals(query.includeDisabled())) {
                params.put("includeDisabled", true);
            }

            JsonNode payload = gatewayClient.request("cron.list", params, LIST_TIMEOUT_MS);
            List<CronJobDto> jobs = parseJobs(payload.path("jobs"));
            Integer total = payload.has("total") ? payload.path("total").asInt(jobs.size()) : jobs.size();

            return CronListPageDto.builder()
                .gatewayConnected(true)
                .gatewayPort(ctx.port())
                .gatewayWsUrl(ctx.wsUrl())
                .jobs(jobs)
                .total(total)
                .limit(limit)
                .offset(offset)
                .build();
        } catch (Exception e) {
            log.warn("Failed cron.list: {}", e.getMessage());
            return CronListPageDto.builder()
                .gatewayConnected(false)
                .gatewayPort(ctx.port())
                .gatewayWsUrl(ctx.wsUrl())
                .connectionHint(ctx.hint())
                .jobs(List.of())
                .total(0)
                .limit(limit)
                .offset(offset)
                .build();
        }
    }

    private CronRunsPageDto fetchRunsOnce(
        String jobId,
        int limit,
        int offset,
        String status,
        String sortDir
    ) {
        GatewayContext ctx = gatewayContext();
        if (!gatewayClient.isConnected()) {
            return disconnectedRuns(jobId);
        }
        try {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("scope", "job");
            params.put("id", jobId);
            params.put("limit", limit);
            params.put("offset", offset);
            if (sortDir != null && !sortDir.isBlank()) {
                params.put("sortDir", sortDir);
            }
            if (status != null && !status.isBlank() && !"all".equals(status)) {
                params.put("status", status);
            }

            JsonNode payload = gatewayClient.request("cron.runs", params, RUNS_TIMEOUT_MS);
            List<JsonNode> entries = new ArrayList<>();
            JsonNode entriesNode = payload.path("entries");
            if (!entriesNode.isArray()) {
                entriesNode = payload.path("runs");
            }
            if (entriesNode.isArray()) {
                for (JsonNode row : entriesNode) {
                    entries.add(row);
                }
            }
            Integer total = payload.has("total") ? payload.path("total").asInt(entries.size()) : entries.size();

            return CronRunsPageDto.builder()
                .gatewayConnected(true)
                .gatewayPort(ctx.port())
                .gatewayWsUrl(ctx.wsUrl())
                .jobId(jobId)
                .entries(entries)
                .total(total)
                .limit(limit)
                .offset(offset)
                .build();
        } catch (Exception e) {
            log.warn("Failed cron.runs for {}: {}", jobId, e.getMessage());
            return disconnectedRuns(jobId);
        }
    }

    private List<CronJobDto> parseJobs(JsonNode jobsNode) {
        List<CronJobDto> jobs = new ArrayList<>();
        if (!jobsNode.isArray()) {
            return jobs;
        }
        for (JsonNode row : jobsNode) {
            jobs.add(mapJob(row));
        }
        return jobs;
    }

    private CronJobDto mapJob(JsonNode row) {
        JsonNode state = row.has("state") ? row.get("state") : null;
        String status = textOrNull(row, "status");
        if (status == null && state != null && state.has("lastRunStatus")) {
            if (row.has("enabled") && !row.path("enabled").asBoolean(true)) {
                status = "disabled";
            } else if (state.has("runningAtMs") && state.path("runningAtMs").asLong(0) > 0) {
                status = "running";
            } else {
                status = state.path("lastRunStatus").asText("idle");
            }
        }

        Long nextRun = row.has("nextRunAtMs") ? row.path("nextRunAtMs").asLong(0) : null;
        if (nextRun != null && nextRun == 0) {
            nextRun = null;
        }

        return CronJobDto.builder()
            .id(textOrNull(row, "id"))
            .name(textOrNull(row, "name"))
            .description(textOrNull(row, "description"))
            .enabled(row.has("enabled") ? row.path("enabled").asBoolean() : null)
            .status(status)
            .sessionTarget(textOrNull(row, "sessionTarget"))
            .agentId(textOrNull(row, "agentId"))
            .schedule(row.has("schedule") ? row.get("schedule") : null)
            .payload(row.has("payload") ? row.get("payload") : null)
            .delivery(row.has("delivery") ? row.get("delivery") : null)
            .state(state)
            .nextRunAtMs(nextRun)
            .updatedAtMs(longOrNull(row, "updatedAtMs"))
            .createdAtMs(longOrNull(row, "createdAtMs"))
            .build();
    }

    private static String textOrNull(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String v = node.path(field).asText();
        return v.isBlank() ? null : v;
    }

    private static Long longOrNull(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        long v = node.path(field).asLong(0);
        return v == 0 ? null : v;
    }

    private void requireConnected() {
        if (!gatewayClient.isConnected()) {
            throw new IllegalStateException("Gateway WebSocket 未连接，请确认 Gateway 已启动且握手成功");
        }
    }

    private CronStatusDto disconnectedStatus() {
        GatewayContext ctx = gatewayContext();
        return CronStatusDto.builder()
            .gatewayConnected(false)
            .gatewayPort(ctx.port())
            .gatewayWsUrl(ctx.wsUrl())
            .connectionHint(ctx.hint())
            .status(null)
            .build();
    }

    private CronListPageDto disconnectedList() {
        GatewayContext ctx = gatewayContext();
        return CronListPageDto.builder()
            .gatewayConnected(false)
            .gatewayPort(ctx.port())
            .gatewayWsUrl(ctx.wsUrl())
            .connectionHint(ctx.hint())
            .jobs(List.of())
            .total(0)
            .limit(DEFAULT_LIST_LIMIT)
            .offset(0)
            .build();
    }

    private CronRunsPageDto disconnectedRuns(String jobId) {
        GatewayContext ctx = gatewayContext();
        return CronRunsPageDto.builder()
            .gatewayConnected(false)
            .gatewayPort(ctx.port())
            .gatewayWsUrl(ctx.wsUrl())
            .connectionHint(ctx.hint())
            .jobId(jobId)
            .entries(List.of())
            .total(0)
            .limit(30)
            .offset(0)
            .build();
    }

    private static String buildConnectionHint(int gatewayPort) {
        return "Gateway 未连接（:" + gatewayPort + "）。请在「OpenClaw 部署」中启动 Gateway，并确认后端 RPC 已握手。";
    }
}
