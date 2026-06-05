package com.openclaw.vs.controller;

import com.openclaw.vs.dto.*;
import com.openclaw.vs.model.DeploymentTask;
import com.openclaw.vs.service.DeploymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/deployment")
@RequiredArgsConstructor
@Tag(name = "环境部署", description = "OpenClaw 一键部署 API")
public class DeploymentController {

    private final DeploymentService deploymentService;

    // ========== 原有 CRUD 方法 ==========
    
    @GetMapping("/tasks")
    @Operation(summary = "分页查询部署任务")
    public ApiResponse<Page<DeploymentTask>> listTasks(
        @Parameter(description = "分页参数") @PageableDefault(size = 20, sort = "startTime,desc") Pageable pageable) {
        return ApiResponse.success(deploymentService.listTasks(pageable));
    }

    @GetMapping("/tasks/{id}")
    @Operation(summary = "获取部署任务详情")
    public ApiResponse<DeploymentTask> getTask(
        @Parameter(description = "任务ID") @PathVariable String id) {
        return ApiResponse.success(deploymentService.getTask(id));
    }

    @PostMapping("/tasks")
    @Operation(summary = "创建部署任务")
    public ApiResponse<DeploymentTask> createTask(
        @Parameter(description = "部署任务信息") @Valid @RequestBody DeploymentTask task) {
        return ApiResponse.success(deploymentService.createTask(task));
    }

    @PutMapping("/tasks/{id}")
    @Operation(summary = "更新部署任务状态")
    public ApiResponse<DeploymentTask> updateTask(
        @Parameter(description = "任务ID") @PathVariable String id,
        @Parameter(description = "更新信息") @RequestBody DeploymentTask task) {
        return ApiResponse.success(deploymentService.updateTask(id, task));
    }

    @DeleteMapping("/tasks/{id}")
    @Operation(summary = "删除部署任务")
    public ApiResponse<Void> deleteTask(
        @Parameter(description = "任务ID") @PathVariable String id) {
        deploymentService.deleteTask(id);
        return ApiResponse.success(null);
    }

    // ========== 新增一键部署功能 ==========
    
    @GetMapping("/list")
    @Operation(summary = "获取部署历史列表", description = "获取所有历史部署记录，按创建时间倒序排列，支持分页")
    public ApiResponse<Page<DeploymentListItem>> getDeploymentList(
        @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(deploymentService.getDeploymentList(page, size));
    }

    @GetMapping("/current")
    @Operation(summary = "获取当前部署实例", description = "返回最近完成的部署记录，若无则返回最新一条")
    public ApiResponse<DeploymentListItem> getCurrentDeployment() {
        return ApiResponse.success(deploymentService.getCurrentDeployment());
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "删除部署记录", description = "删除指定的部署记录")
    public ApiResponse<Void> deleteDeployment(
        @Parameter(description = "部署ID") @PathVariable String id) {
        deploymentService.deleteDeployment(id);
        return ApiResponse.success(null);
    }
    
    @GetMapping("/discover")
    @Operation(summary = "探测本机 OpenClaw 安装", description = "扫描 PATH、npm 全局目录与 ~/.openclaw 配置，判断是否已有安装")
    public ApiResponse<OpenClawInstallDiscoveryDto> discoverInstallation() {
        return ApiResponse.success(deploymentService.discoverInstallation());
    }

    @PostMapping("/link-existing")
    @Operation(summary = "关联本机已有 OpenClaw", description = "将探测到的安装登记为当前实例，不重新执行安装")
    public ApiResponse<DeploymentListItem> linkExistingInstallation(
        @RequestBody(required = false) LinkExistingInstallRequest request) {
        return ApiResponse.success(deploymentService.linkExistingInstallation(request));
    }

    @GetMapping("/environment-check")
    @Operation(summary = "环境检测", description = "检测 Node.js、Python、Git、磁盘空间等环境依赖，执行所有检测项并返回结果")
    public ApiResponse<List<EnvironmentCheckResult>> checkEnvironment() {
        return ApiResponse.success(deploymentService.checkEnvironment());
    }

    @PostMapping("/start")
    @Operation(summary = "启动一键部署", description = "启动 OpenClaw 完整部署流程")
    public ApiResponse<StartDeploymentResponse> startDeployment(
        @Parameter(description = "部署配置") @Valid @RequestBody StartDeploymentRequest request) {
        String deployId = deploymentService.startDeployment(request);
        StartDeploymentResponse response = new StartDeploymentResponse();
        response.setDeployId(deployId);
        response.setMessage("部署任务已启动，ID: " + deployId);
        return ApiResponse.success(response);
    }

    @GetMapping("/{deployId}/status")
    @Operation(summary = "获取部署状态", description = "获取指定部署任务的实时状态和进度；logOffset 指定后仅返回新增日志")
    public ApiResponse<DeployProgress> getDeployStatus(
        @Parameter(description = "部署ID") @PathVariable String deployId,
        @Parameter(description = "已接收的日志行数，用于增量拉取") @RequestParam(defaultValue = "0") int logOffset) {
        return ApiResponse.success(deploymentService.getDeployProgress(deployId, logOffset));
    }

    @GetMapping("/{deployId}/logs")
    @Operation(summary = "获取部署日志", description = "获取指定部署任务的详细日志")
    public ApiResponse<List<String>> getDeployLogs(
        @Parameter(description = "部署ID") @PathVariable String deployId) {
        return ApiResponse.success(deploymentService.getDeployLogs(deployId));
    }

    @GetMapping("/system-info")
    @Operation(summary = "获取系统信息", description = "获取当前系统的详细配置信息")
    public ApiResponse<SystemInfo> getSystemInfo() {
        return ApiResponse.success(deploymentService.getSystemInfo());
    }

    @PostMapping("/{deployId}/cancel")
    @Operation(summary = "取消部署", description = "取消正在进行的部署任务")
    public ApiResponse<Boolean> cancelDeployment(
        @Parameter(description = "部署ID") @PathVariable String deployId) {
        boolean cancelled = deploymentService.cancelDeployment(deployId);
        return ApiResponse.success(cancelled);
    }
}