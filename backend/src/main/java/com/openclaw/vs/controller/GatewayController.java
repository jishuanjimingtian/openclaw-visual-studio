package com.openclaw.vs.controller;

import com.openclaw.vs.dto.ApiResponse;
import com.openclaw.vs.dto.GatewayInfo;
import com.openclaw.vs.service.GatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/gateway")
@RequiredArgsConstructor
@Tag(name = "Gateway 管理", description = "OpenClaw Gateway 启动、停止和状态管理 API")
public class GatewayController {

    private final GatewayService gatewayService;

    @PostMapping("/start")
    @Operation(summary = "启动 Gateway", description = "异步分段启动 OpenClaw Gateway；立即返回 starting，通过 status 轮询进度")
    public ApiResponse<GatewayInfo> startGateway(
            @Parameter(description = "Gateway 端口，默认 18789")
            @RequestParam(value = "port", required = false) Integer port,
            @Parameter(description = "true=后台服务；false=前台子进程；省略则按 app.gateway.prefer-daemon")
            @RequestParam(value = "daemon", required = false) Boolean daemon) {
        GatewayInfo result = gatewayService.startGateway(port, daemon);
        if ("failed".equals(result.getStatus())) {
            String errorMsg = result.getMessage() != null ? result.getMessage() : "Gateway 启动失败";
            return ApiResponse.error(500, errorMsg, result);
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/connect")
    @Operation(summary = "连接已有 Gateway", description = "检测并连接已在运行的 Gateway，不启动新进程")
    public ApiResponse<GatewayInfo> connectGateway() {
        GatewayInfo result = gatewayService.connectExisting();
        return ApiResponse.success(result);
    }

    @PostMapping("/stop")
    @Operation(summary = "停止 Gateway", description = "停止 OpenClaw Gateway 进程")
    public ApiResponse<GatewayInfo> stopGateway() {
        GatewayInfo result = gatewayService.stopGateway();
        return ApiResponse.success(result);
    }

    @GetMapping("/status")
    @Operation(summary = "查询运行状态", description = "获取 Gateway 实时运行状态；full=true 时完整探测，默认轻量轮询")
    public ApiResponse<GatewayInfo> getStatus(
            @Parameter(description = "是否完整探测（connect/手动刷新用 true，轮询用 false）")
            @RequestParam(value = "full", defaultValue = "false") boolean full) {
        GatewayInfo result = gatewayService.getGatewayStatus(full);
        return ApiResponse.success(result);
    }

    @GetMapping("/logs")
    @Operation(summary = "获取运行日志", description = "获取 Gateway 最近运行日志")
    public ApiResponse<List<String>> getLogs(
            @Parameter(description = "返回日志行数上限，默认 100")
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        List<String> logs = gatewayService.getGatewayLogs(limit);
        return ApiResponse.success(logs);
    }

    @GetMapping("/info")
    @Operation(summary = "获取配置信息", description = "获取 Gateway 配置信息（端口、版本、路径等）")
    public ApiResponse<GatewayInfo> getInfo() {
        GatewayInfo info = gatewayService.getGatewayInfo();
        return ApiResponse.success(info);
    }
}