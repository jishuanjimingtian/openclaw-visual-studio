package com.openclaw.vs.controller;

import com.openclaw.vs.dto.*;
import com.openclaw.vs.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Tag(name = "数据分析", description = "Token 用量、模型分布、消息趋势分析 API")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    @Operation(summary = "分析概览（默认仅本地统计，较快）")
    public ApiResponse<AnalyticsOverviewDto> getOverview(
            @Parameter(description = "是否合并 Gateway Token 统计（较慢）")
            @RequestParam(defaultValue = "false") boolean includeGatewayTokens) {
        return ApiResponse.success(analyticsService.getOverview(includeGatewayTokens));
    }

    @GetMapping("/gateway-tokens")
    @Operation(summary = "Gateway 统计汇总（Token、消息、会话；可能较慢）")
    public ApiResponse<AnalyticsOverviewDto> getGatewayTokens() {
        return ApiResponse.success(analyticsService.getGatewayTokenSummary());
    }

    @GetMapping("/token-usage")
    @Operation(summary = "最近 N 天每日 Token 用量")
    public ApiResponse<List<DailyTokenUsageDto>> getDailyTokenUsage(
            @RequestParam(defaultValue = "14") int days,
            @RequestParam(defaultValue = "true") boolean includeGateway) {
        return ApiResponse.success(analyticsService.getDailyTokenUsage(days, includeGateway));
    }

    @GetMapping("/model-usage")
    @Operation(summary = "模型使用分布")
    public ApiResponse<List<ModelUsageDto>> getModelUsage() {
        return ApiResponse.success(analyticsService.getModelUsage());
    }

    @GetMapping("/message-trend")
    @Operation(summary = "近 7 天消息趋势")
    public ApiResponse<List<MessageTrendDto>> getMessageTrend() {
        return ApiResponse.success(analyticsService.getMessageTrend());
    }

    @GetMapping("/top-sessions")
    @Operation(summary = "高消耗会话 Top N（本地库 + Gateway）")
    public ApiResponse<List<TopSessionUsageDto>> getTopSessions(
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(analyticsService.getTopSessions(limit));
    }

    @GetMapping("/source-breakdown")
    @Operation(summary = "数据来源对账（本地库 vs Gateway）")
    public ApiResponse<AnalyticsSourceBreakdownDto> getSourceBreakdown() {
        return ApiResponse.success(analyticsService.getSourceBreakdown());
    }
}
