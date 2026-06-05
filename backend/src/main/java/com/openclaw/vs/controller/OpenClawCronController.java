package com.openclaw.vs.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.openclaw.vs.dto.ApiResponse;
import com.openclaw.vs.dto.CronJobUpdateRequest;
import com.openclaw.vs.dto.CronListPageDto;
import com.openclaw.vs.dto.CronOverviewDto;
import com.openclaw.vs.dto.CronRunRequest;
import com.openclaw.vs.dto.CronRunsPageDto;
import com.openclaw.vs.dto.CronStatusDto;
import com.openclaw.vs.service.OpenClawCronService;
import com.openclaw.vs.service.OpenClawCronService.CronListQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/openclaw/cron")
@RequiredArgsConstructor
@Tag(name = "定时任务", description = "OpenClaw Gateway Cron 调度 API")
public class OpenClawCronController {

    private final OpenClawCronService cronService;

    @GetMapping("/overview")
    @Operation(summary = "首屏概览：调度器状态 + 任务列表")
    public ApiResponse<CronOverviewDto> overview(
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer offset,
        @RequestParam(required = false) String query,
        @RequestParam(required = false, defaultValue = "all") String enabled,
        @RequestParam(required = false, defaultValue = "nextRunAtMs") String sortBy,
        @RequestParam(required = false, defaultValue = "desc") String sortDir,
        @RequestParam(required = false) Boolean includeDisabled
    ) {
        return ApiResponse.success(cronService.getOverview(buildQuery(
            limit, offset, query, enabled, sortBy, sortDir, includeDisabled
        )));
    }

    @GetMapping("/status")
    @Operation(summary = "Cron 调度器状态")
    public ApiResponse<CronStatusDto> status() {
        return ApiResponse.success(cronService.getStatus());
    }

    @GetMapping("/jobs")
    @Operation(summary = "分页列出定时任务")
    public ApiResponse<CronListPageDto> listJobs(
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer offset,
        @RequestParam(required = false) String query,
        @RequestParam(required = false, defaultValue = "all") String enabled,
        @RequestParam(required = false, defaultValue = "nextRunAtMs") String sortBy,
        @RequestParam(required = false, defaultValue = "desc") String sortDir,
        @RequestParam(required = false) Boolean includeDisabled
    ) {
        return ApiResponse.success(cronService.listJobs(buildQuery(
            limit, offset, query, enabled, sortBy, sortDir, includeDisabled
        )));
    }

    @PostMapping("/jobs")
    @Operation(summary = "创建定时任务")
    public ApiResponse<JsonNode> addJob(@RequestBody JsonNode body) throws Exception {
        return ApiResponse.success(cronService.addJob(body));
    }

    @PutMapping("/jobs/{id}")
    @Operation(summary = "更新定时任务")
    public ApiResponse<JsonNode> updateJob(
        @PathVariable String id,
        @RequestBody CronJobUpdateRequest request
    ) throws Exception {
        return ApiResponse.success(cronService.updateJob(id, request != null ? request.getPatch() : null));
    }

    @DeleteMapping("/jobs/{id}")
    @Operation(summary = "删除定时任务")
    public ApiResponse<JsonNode> removeJob(@PathVariable String id) throws Exception {
        return ApiResponse.success(cronService.removeJob(id));
    }

    @PostMapping("/jobs/{id}/run")
    @Operation(summary = "立即运行定时任务")
    public ApiResponse<JsonNode> runJob(
        @PathVariable String id,
        @RequestBody(required = false) CronRunRequest request
    ) throws Exception {
        String mode = request != null ? request.getMode() : "force";
        return ApiResponse.success(cronService.runJob(id, mode));
    }

    @GetMapping("/jobs/{id}/runs")
    @Operation(summary = "任务执行历史")
    public ApiResponse<CronRunsPageDto> listRuns(
        @PathVariable String id,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer offset,
        @RequestParam(required = false, defaultValue = "all") String status,
        @RequestParam(required = false, defaultValue = "desc") String sortDir
    ) {
        return ApiResponse.success(cronService.listRuns(id, limit, offset, status, sortDir));
    }

    private static CronListQuery buildQuery(
        Integer limit,
        Integer offset,
        String query,
        String enabled,
        String sortBy,
        String sortDir,
        Boolean includeDisabled
    ) {
        return new CronListQuery(limit, offset, query, enabled, sortBy, sortDir, includeDisabled);
    }
}
