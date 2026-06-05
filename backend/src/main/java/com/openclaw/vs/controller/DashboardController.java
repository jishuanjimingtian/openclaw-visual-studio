package com.openclaw.vs.controller;

import com.openclaw.vs.dto.ApiResponse;
import com.openclaw.vs.dto.DashboardOpenClawMetaDto;
import com.openclaw.vs.dto.DashboardOverviewDto;
import com.openclaw.vs.dto.DashboardRecentDto;
import com.openclaw.vs.dto.DashboardStatsDto;
import com.openclaw.vs.dto.SystemMetricsDto;
import com.openclaw.vs.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "仪表盘", description = "仪表盘统计与概览 API")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    @Operation(summary = "仪表盘完整概览（统计、指标、Gateway、最近会话与部署）")
    public ApiResponse<DashboardOverviewDto> getOverview() {
        return ApiResponse.success(dashboardService.getOverview());
    }

    @GetMapping("/stats")
    @Operation(summary = "获取仪表盘统计数据")
    public ApiResponse<DashboardStatsDto> getStats() {
        return ApiResponse.success(dashboardService.getStats());
    }

    @GetMapping("/metrics")
    @Operation(summary = "获取系统资源指标")
    public ApiResponse<SystemMetricsDto> getMetrics(
            @Parameter(description = "是否合并 Gateway 今日 Token（较慢）")
            @RequestParam(value = "includeGatewayTokens", defaultValue = "false") boolean includeGatewayTokens) {
        return ApiResponse.success(dashboardService.getMetrics(includeGatewayTokens));
    }

    @GetMapping("/recent")
    @Operation(summary = "最近会话与部署")
    public ApiResponse<DashboardRecentDto> getRecent() {
        return ApiResponse.success(dashboardService.getRecent());
    }

    @GetMapping("/openclaw-meta")
    @Operation(summary = "OpenClaw 配置摘要")
    public ApiResponse<DashboardOpenClawMetaDto> getOpenClawMeta() {
        return ApiResponse.success(dashboardService.getOpenClawMeta());
    }

    @GetMapping("/token-usage")
    @Operation(summary = "今日 Token 用量（含 Gateway，可能较慢）")
    public ApiResponse<Long> getTodayTokenUsage() {
        return ApiResponse.success(dashboardService.getTodayTokenUsage());
    }
}
