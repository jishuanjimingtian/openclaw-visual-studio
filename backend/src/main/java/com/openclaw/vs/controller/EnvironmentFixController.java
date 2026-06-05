package com.openclaw.vs.controller;

import com.openclaw.vs.dto.*;
import com.openclaw.vs.service.EnvironmentCheckService;
import com.openclaw.vs.service.EnvironmentFixService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/deployment")
@RequiredArgsConstructor
@Tag(name = "环境修复", description = "一键修复缺失环境依赖 API")
public class EnvironmentFixController {

    private final EnvironmentFixService environmentFixService;
    private final EnvironmentCheckService environmentCheckService;

    @GetMapping("/fixable-items")
    @Operation(summary = "获取可修复项列表", description = "返回支持自动修复的检测项名称列表")
    public ApiResponse<List<String>> getFixableItems() {
        return ApiResponse.success(environmentCheckService.getFixableItems());
    }

    @GetMapping("/fix-suggestion/{checkName}")
    @Operation(summary = "获取修复建议", description = "为指定检测项提供修复建议")
    public ApiResponse<String> getFixSuggestion(
        @Parameter(description = "检测项名称") @PathVariable String checkName) {
        return ApiResponse.success(environmentCheckService.getFixSuggestion(checkName));
    }

    @PostMapping("/fix-environment")
    @Operation(summary = "启动一键修复", description = "根据检测结果自动修复缺失的环境依赖")
    public ApiResponse<FixProgress> fixEnvironment(
        @Parameter(description = "修复请求") @RequestBody(required = false) EnvironmentFixRequest request) {
        List<String> fixItems = request != null ? request.getFixItems() : null;
        String fixId = environmentFixService.startFix(fixItems);
        FixProgress progress = environmentFixService.getFixProgress(fixId, 0);
        return ApiResponse.success("环境修复已启动", progress);
    }

    @GetMapping("/fix-progress/{fixId}")
    @Operation(summary = "获取修复进度", description = "获取指定修复任务的实时进度；logOffset 指定后仅返回新增日志")
    public ApiResponse<FixProgress> getFixProgress(
        @Parameter(description = "修复ID") @PathVariable String fixId,
        @Parameter(description = "已接收的日志行数，用于增量拉取") @RequestParam(defaultValue = "0") int logOffset) {
        return ApiResponse.success(environmentFixService.getFixProgress(fixId, logOffset));
    }

    @GetMapping("/fix-results/{fixId}")
    @Operation(summary = "获取修复结果", description = "获取指定修复任务的最终结果")
    public ApiResponse<FixProgress> getFixResults(
        @Parameter(description = "修复ID") @PathVariable String fixId) {
        return ApiResponse.success(environmentFixService.getFixResults(fixId));
    }
}
