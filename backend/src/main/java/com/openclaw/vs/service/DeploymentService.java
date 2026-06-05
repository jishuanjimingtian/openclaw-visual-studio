package com.openclaw.vs.service;

import com.openclaw.vs.dto.*;
import com.openclaw.vs.exception.BadRequestException;
import com.openclaw.vs.exception.NotFoundException;
import com.openclaw.vs.model.DeploymentTask;
import com.openclaw.vs.repository.DeploymentTaskRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DeploymentService {

    private final DeploymentTaskRepository deploymentTaskRepository;
    private final EnvironmentCheckService environmentCheckService;
    private final EnvironmentFixService environmentFixService;
    private final OpenClawInstallDiscoveryService installDiscoveryService;

    /** 活跃的部署进度映射: deployId -> DeployProgress */
    private final ConcurrentHashMap<String, DeployProgress> deployProgressMap = new ConcurrentHashMap<>();

    /** 内存日志上限，防止 npm 安装输出撑爆内存 */
    private static final int MAX_LOG_LINES = 3000;

    /** 部署任务映射: deployId -> Future */
    private final ConcurrentHashMap<String, Future<?>> deployFutureMap = new ConcurrentHashMap<>();

    // ========== 原有 CRUD 方法 ==========
    
    public Page<DeploymentTask> listTasks(Pageable pageable) {
        return deploymentTaskRepository.findAll(pageable);
    }
    
    /**
     * 获取部署历史列表（轻量分页，不执行 subprocess / netstat）
     */
    public Page<DeploymentListItem> getDeploymentList(int page, int size) {
        Page<DeploymentTask> taskPage = deploymentTaskRepository.findAllByOrderByStartTimeDesc(
            Pageable.ofSize(size).withPage(page));
        return taskPage.map(this::convertToDeploymentListItemLight);
    }

    /**
     * 获取当前有效部署实例（优先最近完成的记录）
     */
    public DeploymentListItem getCurrentDeployment() {
        List<DeploymentTask> completed = deploymentTaskRepository.findByStatusOrderByStartTimeDesc("completed");
        if (!completed.isEmpty()) {
            return convertToDeploymentListItemLight(completed.get(0));
        }
        Page<DeploymentTask> latest = deploymentTaskRepository.findAllByOrderByStartTimeDesc(Pageable.ofSize(1));
        if (latest.isEmpty()) {
            return null;
        }
        return convertToDeploymentListItemLight(latest.getContent().get(0));
    }
    
    /**
     * 删除部署记录
     */
    public void deleteDeployment(String id) {
        if (!deploymentTaskRepository.existsById(id)) {
            throw new NotFoundException("部署记录", id);
        }
        deploymentTaskRepository.deleteById(id);
    }

    public DeploymentTask getTask(String id) {
        return deploymentTaskRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("部署任务", id));
    }

    public DeploymentTask createTask(DeploymentTask task) {
        task.setId(UUID.randomUUID().toString());
        task.setStatus("pending");
        task.setProgress(0);
        task.setStartTime(LocalDateTime.now());
        return deploymentTaskRepository.save(task);
    }

    public DeploymentTask updateTask(String id, DeploymentTask updated) {
        DeploymentTask existing = getTask(id);
        if (updated.getStatus() != null) {
            existing.setStatus(updated.getStatus());
            if ("completed".equals(updated.getStatus()) || "failed".equals(updated.getStatus())) {
                existing.setEndTime(LocalDateTime.now());
            }
        }
        if (updated.getProgress() >= 0) existing.setProgress(updated.getProgress());
        if (updated.getLog() != null) existing.setLog(updated.getLog());
        if (updated.getConfigJson() != null) existing.setConfigJson(updated.getConfigJson());
        return deploymentTaskRepository.save(existing);
    }

    public void deleteTask(String id) {
        if (!deploymentTaskRepository.existsById(id)) {
            throw new NotFoundException("部署任务", id);
        }
        deploymentTaskRepository.deleteById(id);
    }

    // ========== 新增部署功能 ==========

    /**
     * 探测本机是否已有 OpenClaw 安装（CLI、配置目录等）
     */
    public OpenClawInstallDiscoveryDto discoverInstallation() {
        return installDiscoveryService.discover();
    }

    /**
     * 将探测到的本机 OpenClaw 关联为当前实例（不重新安装）
     */
    public DeploymentListItem linkExistingInstallation(LinkExistingInstallRequest request) {
        OpenClawInstallDiscoveryDto discovery = installDiscoveryService.discover();
        if (!discovery.isInstalled()) {
            throw new BadRequestException("未在本机检测到 OpenClaw 安装，请先完成安装或检查 PATH");
        }

        String workDir = request != null && request.getWorkDir() != null && !request.getWorkDir().isBlank()
            ? request.getWorkDir().trim()
            : discovery.getWorkDir();
        int gatewayPort = request != null && request.getGatewayPort() != null
            ? request.getGatewayPort()
            : (discovery.getGatewayPort() != null ? discovery.getGatewayPort() : 18789);

        String installSource = discovery.getInstallMethod() != null ? discovery.getInstallMethod() : "external";
        String normalizedWorkDir = workDir != null ? workDir.replace("\\", "/") : "";
        String configJson = String.format(
            "{\"installSource\":\"%s\",\"gatewayPort\":%d,\"workDir\":\"%s\",\"autoFix\":false,\"linked\":true}",
            installSource,
            gatewayPort,
            normalizedWorkDir
        );

        String deployId = UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime now = LocalDateTime.now();
        DeploymentTask task = DeploymentTask.builder()
            .id(deployId)
            .status("completed")
            .progress(100)
            .startTime(now)
            .endTime(now)
            .configJson(configJson)
            .log("关联本机已有 OpenClaw 安装\n" + discovery.getMessage())
            .build();
        deploymentTaskRepository.save(task);

        return convertToDeploymentListItem(task);
    }

    /**
     * 获取环境检测结果
     */
    public List<EnvironmentCheckResult> checkEnvironment() {
        return environmentCheckService.checkEnvironment();
    }

    /**
     * 获取系统信息
     */
    public SystemInfo getSystemInfo() {
        return environmentCheckService.getSystemInfo();
    }

    /**
     * 启动一键部署 - 异步启动部署流程，写入数据库并返回任务ID
     */
    public String startDeployment(StartDeploymentRequest request) {
        String deployId = UUID.randomUUID().toString().substring(0, 8);
        
        // 写入数据库
        DeploymentTask task = DeploymentTask.builder()
            .id(deployId)
            .status("running")
            .progress(0)
            .startTime(LocalDateTime.now())
            .configJson(convertRequestToJson(request))
            .build();
        deploymentTaskRepository.save(task);
        
        DeployProgress progress = new DeployProgress();
        progress.setStage("checking");
        progress.setPercentage(0);
        progress.setCurrentAction("正在启动环境检测...");
        progress.setLogs(new ArrayList<>());
        deployProgressMap.put(deployId, progress);
        
        addLog(deployId, "=" .repeat(50));
        addLog(deployId, "OpenClaw 一键部署启动");
        addLog(deployId, "部署 ID: " + deployId);
        addLog(deployId, "=" .repeat(50));
        
        // 异步执行部署流程
        Future<?> future = CompletableFuture.runAsync(() -> {
            try {
                executeDeployment(deployId, request);
            } catch (Exception e) {
                log.error("部署失败", e);
                addLog(deployId, "部署失败: " + e.getMessage());
                DeployProgress p = deployProgressMap.get(deployId);
                if (p != null) {
                    p.setStage("failed");
                    p.setCurrentAction("部署失败: " + e.getMessage());
                }
                // 更新数据库状态
                updateTaskStatus(deployId, "failed");
            }
        });
        
        deployFutureMap.put(deployId, future);
        return deployId;
    }

    private void executeDeployment(String deployId, StartDeploymentRequest request) {
        // 阶段 1: 环境检测
        updateProgress(deployId, "checking", 5, "正在执行环境检测...");
        addLog(deployId, "");
        addLog(deployId, "[阶段 1/3] 环境检测");
        addLog(deployId, "-".repeat(40));
        
        List<EnvironmentCheckResult> checkResults = environmentCheckService.checkEnvironment();
        boolean hasFailure = false;
        
        for (EnvironmentCheckResult check : checkResults) {
            String icon = switch (check.getStatus()) {
                case "pass" -> "✓";
                case "fail" -> "✗";
                default -> "⚠";
            };
            addLog(deployId, String.format("  %s %s: %s", icon, check.getCheckName(), check.getMessage()));
            if ("fail".equals(check.getStatus())) {
                hasFailure = true;
                addLog(deployId, "    建议: " + (check.getSuggestion() != null ? check.getSuggestion() : "无"));
            }
        }
        
        if (hasFailure) {
            if (request.isAutoFix()) {
                // 自动修复模式
                addLog(deployId, "");
                addLog(deployId, "⚠ 环境检测不通过，正在尝试自动修复...");
                updateProgress(deployId, "checking", 10, "正在尝试自动修复...");
                
                // 启动自动修复
                String fixId = environmentFixService.startFix(null);
                addLog(deployId, "修复任务 ID: " + fixId);
                
                // 等待修复完成
                boolean fixSuccess = waitForFixCompletion(fixId, deployId);
                
                if (fixSuccess) {
                    addLog(deployId, "✓ 自动修复完成，重新检测环境...");
                    checkResults = environmentCheckService.checkEnvironment();
                    hasFailure = false;
                    
                    for (EnvironmentCheckResult check : checkResults) {
                        if ("fail".equals(check.getStatus())) {
                            hasFailure = true;
                            break;
                        }
                    }
                    
                    if (!hasFailure) {
                        addLog(deployId, "✓ 环境修复成功，继续部署流程");
                    } else {
                        addLog(deployId, "✗ 自动修复后仍有失败项，请手动修复");
                        updateProgress(deployId, "failed", 15, "自动修复后环境检测仍不通过");
                        updateTaskStatus(deployId, "failed");
                        return;
                    }
                } else {
                    addLog(deployId, "✗ 自动修复失败，请手动修复环境");
                    updateProgress(deployId, "failed", 15, "自动修复失败");
                    updateTaskStatus(deployId, "failed");
                    return;
                }
            } else {
                // 非自动修复模式
                addLog(deployId, "");
                addLog(deployId, "✗ 环境检测不通过！请修复以上失败项后重新部署。");
                updateProgress(deployId, "failed", 10, "环境检测不通过");
                updateTaskStatus(deployId, "failed");
                return;
            }
        }
        
        addLog(deployId, "✓ 环境检测通过");
        updateProgress(deployId, "checking", 15, "环境检测通过");
        
        // 阶段 2: 安装
        updateProgress(deployId, "installing", 20, "正在安装 OpenClaw...");
        addLog(deployId, "");
        addLog(deployId, "[阶段 2/3] 安装 OpenClaw");
        addLog(deployId, "-".repeat(40));
        
        String workDir = request.getWorkDir();
        if (workDir == null || workDir.isBlank()) {
            workDir = System.getProperty("user.home") + File.separator + "openclaw-workspace";
        }
        
        boolean installSuccess;
        if ("github".equals(request.getInstallSource())) {
            installSuccess = installFromGitHub(deployId, workDir);
        } else {
            installSuccess = installFromNpm(deployId);
        }
        
        if (!installSuccess) {
            updateProgress(deployId, "failed", 50, "安装失败");
            updateTaskStatus(deployId, "failed");
            return;
        }
        
        updateProgress(deployId, "installing", 50, "OpenClaw 安装完成");
        
        // 阶段 3: 配置
        updateProgress(deployId, "configuring", 55, "正在配置 OpenClaw Gateway...");
        addLog(deployId, "");
        addLog(deployId, "[阶段 3/3] 配置 OpenClaw Gateway");
        addLog(deployId, "-".repeat(40));
        
        boolean configSuccess = configureGateway(deployId, workDir, request.getGatewayPort());
        
        if (!configSuccess) {
            updateProgress(deployId, "failed", 80, "配置失败");
            updateTaskStatus(deployId, "failed");
            return;
        }
        
        updateProgress(deployId, "configuring", 85, "配置完成");
        
        // 阶段 4: 完成
        updateProgress(deployId, "completed", 100, "部署完成");
        addLog(deployId, "");
        addLog(deployId, "=" .repeat(50));
        addLog(deployId, "✓ OpenClaw 部署成功!");
        addLog(deployId, "  安装方式: " + ("github".equals(request.getInstallSource()) ? "GitHub 源码" : "npm 全局安装"));
        addLog(deployId, "  工作目录: " + workDir);
        addLog(deployId, "  Gateway 端口: " + request.getGatewayPort());
        addLog(deployId, "=" .repeat(50));
        
        // 填充部署摘要
        DeploySummary summary = new DeploySummary();
        summary.setVersion(getOpenClawVersion());
        summary.setPath(workDir);
        summary.setGatewayPort(request.getGatewayPort());
        summary.setConfigPath(workDir + File.separator + "openclaw.config.yml");
        summary.setWorkDir(workDir);
        summary.setInstallSource(request.getInstallSource());
        
        DeployProgress p = deployProgressMap.get(deployId);
        if (p != null) {
            p.setSummary(summary);
        }
        
        // 更新数据库状态为完成
        updateTaskStatus(deployId, "completed");
    }

    private boolean installFromNpm(String deployId) {
        addLog(deployId, "使用 npm 全局安装 OpenClaw...");
        updateProgress(deployId, "installing", 25, "正在执行 npm install -g openclaw...");
        
        try {
            // 使用 cmd /c 包装命令，确保在 Windows 上能正确识别 npm.cmd
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "npm install -g openclaw");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    addLog(deployId, "  " + line);
                    lineCount++;
                    if (lineCount % 5 == 0) {
                        int pct = Math.min(25 + lineCount / 2, 48);
                        updateProgress(deployId, "installing", pct, "npm install -g openclaw 进行中...");
                    }
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                addLog(deployId, "✓ npm install -g openclaw 完成");
                return true;
            } else {
                addLog(deployId, "✗ npm install 失败，退出码: " + exitCode);
                return false;
            }
        } catch (Exception e) {
            log.error("npm install 失败", e);
            addLog(deployId, "✗ npm install 异常: " + e.getMessage());
            return false;
        }
    }

    private boolean installFromGitHub(String deployId, String workDir) {
        addLog(deployId, "使用 GitHub 源码安装 OpenClaw...");
        
        try {
            Path workPath = Paths.get(workDir);
            Files.createDirectories(workPath);
            
            // 克隆仓库
            updateProgress(deployId, "installing", 25, "正在克隆 OpenClaw 源码...");
            addLog(deployId, "git clone https://github.com/openclaw/openclaw.git " + workDir);
            
            // 使用 cmd /c 包装命令，确保在 Windows 上能正确识别 git.cmd
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "git clone https://github.com/openclaw/openclaw.git " + workDir);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    addLog(deployId, "  " + line);
                }
            }
            
            int cloneExitCode = process.waitFor();
            if (cloneExitCode != 0) {
                addLog(deployId, "✗ git clone 失败，退出码: " + cloneExitCode);
                
                // 尝试使用国内镜像
                addLog(deployId, "尝试使用 Gitee 镜像...");
                // 使用 cmd /c 包装命令，确保在 Windows 上能正确识别 git.cmd
                ProcessBuilder pb2 = new ProcessBuilder("cmd", "/c", "git clone https://gitee.com/mirrors/openclaw.git " + workDir);
                pb2.redirectErrorStream(true);
                Process process2 = pb2.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process2.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        addLog(deployId, "  " + line);
                    }
                }
                int mirrorExitCode = process2.waitFor();
                if (mirrorExitCode != 0) {
                    addLog(deployId, "✗ Gitee 镜像克隆也失败，退出码: " + mirrorExitCode);
                    return false;
                }
            }
            
            addLog(deployId, "✓ 源码克隆完成");
            
            // npm install
            updateProgress(deployId, "installing", 35, "正在安装依赖 (npm install)...");
            addLog(deployId, "npm install...");
            
            // 使用 cmd /c 包装命令，确保在 Windows 上能正确识别 npm.cmd
            ProcessBuilder npmPb = new ProcessBuilder("cmd", "/c", "npm install");
            npmPb.directory(new File(workDir));
            npmPb.redirectErrorStream(true);
            Process npmProcess = npmPb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(npmProcess.getInputStream()))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    addLog(deployId, "  " + line);
                    lineCount++;
                    if (lineCount % 10 == 0) {
                        int pct = Math.min(35 + lineCount / 5, 48);
                        updateProgress(deployId, "installing", pct, "npm install 进行中...");
                    }
                }
            }
            
            int npmExitCode = npmProcess.waitFor();
            if (npmExitCode == 0) {
                addLog(deployId, "✓ 依赖安装完成");
                return true;
            } else {
                addLog(deployId, "✗ npm install 失败，退出码: " + npmExitCode);
                return false;
            }
        } catch (Exception e) {
            log.error("GitHub 安装失败", e);
            addLog(deployId, "✗ GitHub 安装异常: " + e.getMessage());
            return false;
        }
    }

    private boolean configureGateway(String deployId, String workDir, int port) {
        addLog(deployId, "正在生成 Gateway 配置文件...");
        
        try {
            Path configPath = Paths.get(workDir, "openclaw.config.yml");
            Files.createDirectories(Paths.get(workDir));
            
            String configContent = String.format("""
                # OpenClaw Gateway Configuration
                # Generated by 驭爪 Clawhelm - %s
                
                gateway:
                  port: %d
                  host: "0.0.0.0"
                  cors:
                    enabled: true
                    origins:
                      - "http://localhost:*"
                      - "http://127.0.0.1:*"
                
                logging:
                  level: "info"
                  file: "%s"
                
                workspace:
                  dir: "%s"
                
                agents:
                  max_concurrent: 5
                  timeout_seconds: 300
                """,
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()),
                port,
                workDir.replace("\\", "/") + "/logs/openclaw.log",
                workDir.replace("\\", "/")
            );
            
            Files.writeString(configPath, configContent);
            addLog(deployId, "  配置文件已写入: " + configPath.toAbsolutePath());
            
            updateProgress(deployId, "configuring", 70, "正在验证配置...");
            
            // 验证配置文件
            if (Files.exists(configPath)) {
                addLog(deployId, "✓ 配置文件验证通过");
                
                // 创建日志目录
                Path logDir = Paths.get(workDir, "logs");
                Files.createDirectories(logDir);
                addLog(deployId, "✓ 日志目录已创建: " + logDir.toAbsolutePath());
                
                return true;
            } else {
                addLog(deployId, "✗ 配置文件写入失败");
                return false;
            }
        } catch (IOException e) {
            log.error("配置 Gateway 失败", e);
            addLog(deployId, "✗ 配置异常: " + e.getMessage());
            return false;
        }
    }

    private String getOpenClawVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "openclaw --version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor(3, TimeUnit.SECONDS);
            if (process.exitValue() == 0) {
                return new String(process.getInputStream().readAllBytes()).trim();
            }
        } catch (Exception ignored) {
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "npx openclaw --version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor(3, TimeUnit.SECONDS);
            if (process.exitValue() == 0) {
                return new String(process.getInputStream().readAllBytes()).trim();
            }
        } catch (Exception ignored) {
        }
        return "0.1.0 (检测不到版本)";
    }

    /**
     * 获取部署进度；logOffset 指定后仅返回该索引之后的新增日志行
     */
    public DeployProgress getDeployProgress(String deployId, int logOffset) {
        DeployProgress progress = deployProgressMap.get(deployId);
        if (progress == null) {
            DeployProgress notFound = new DeployProgress();
            notFound.setStage("not_found");
            notFound.setCurrentAction("部署任务不存在: " + deployId);
            return notFound;
        }
        return snapshotDeployProgress(progress, logOffset);
    }

    private DeployProgress snapshotDeployProgress(DeployProgress source, int logOffset) {
        DeployProgress snap = new DeployProgress();
        snap.setStage(source.getStage());
        snap.setPercentage(source.getPercentage());
        snap.setCurrentAction(source.getCurrentAction());
        snap.setSummary(source.getSummary());
        List<String> allLogs;
        synchronized (source) {
            allLogs = source.getLogs();
        }
        int total = allLogs.size();
        snap.setLogTotal(total);
        int safeOffset = Math.max(0, Math.min(logOffset, total));
        if (safeOffset >= total) {
            snap.setLogs(Collections.emptyList());
        } else {
            snap.setLogs(new ArrayList<>(allLogs.subList(safeOffset, total)));
        }
        return snap;
    }

    /**
     * 获取部署日志
     */
    public List<String> getDeployLogs(String deployId) {
        DeployProgress progress = deployProgressMap.get(deployId);
        if (progress != null) {
            return progress.getLogs();
        }
        return Collections.singletonList("部署任务不存在: " + deployId);
    }

    /**
     * 取消部署
     */
    public boolean cancelDeployment(String deployId) {
        Future<?> future = deployFutureMap.get(deployId);
        if (future != null && !future.isDone()) {
            boolean cancelled = future.cancel(true);
            
            DeployProgress progress = deployProgressMap.get(deployId);
            if (progress != null) {
                progress.setStage("cancelled");
                progress.setCurrentAction("部署已取消");
                addLog(deployId, "部署已取消");
            }
            
            // 更新数据库状态
            updateTaskStatus(deployId, "cancelled");
            
            return cancelled;
        }
        return false;
    }

    /**
     * 更新数据库中的任务状态
     */
    private void updateTaskStatus(String deployId, String status) {
        try {
            deploymentTaskRepository.findById(deployId).ifPresent(task -> {
                task.setStatus(status);
                task.setProgress(deployProgressMap.containsKey(deployId) 
                    ? deployProgressMap.get(deployId).getPercentage() : task.getProgress());
                if ("completed".equals(status) || "failed".equals(status) || "cancelled".equals(status)) {
                    task.setEndTime(LocalDateTime.now());
                }
                deploymentTaskRepository.save(task);
            });
        } catch (Exception e) {
            log.error("更新任务状态失败", e);
        }
    }

    /**
     * 将部署请求转为 JSON 字符串存储
     */
    private String convertRequestToJson(StartDeploymentRequest request) {
        try {
            return String.format("{\"installSource\":\"%s\",\"gatewayPort\":%d,\"workDir\":\"%s\",\"autoFix\":%b}",
                request.getInstallSource(),
                request.getGatewayPort(),
                request.getWorkDir() != null ? request.getWorkDir().replace("\\", "/") : "",
                request.isAutoFix());
        } catch (Exception e) {
            return "{}";
        }
    }
    
    /**
     * 等待环境修复完成
     */
    private boolean waitForFixCompletion(String fixId, String deployId) {
        int maxWaitMinutes = 10;
        long startTime = System.currentTimeMillis();
        long maxWaitMs = maxWaitMinutes * 60 * 1000L;
        
        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            try {
                FixProgress progress = environmentFixService.getFixProgress(fixId, 0);
                
                if ("completed".equals(progress.getStage())) {
                    addLog(deployId, "修复完成 - 成功: " + progress.getSuccessCount() + ", 失败: " + progress.getFailCount());
                    return progress.getFailCount() == 0;
                } else if ("failed".equals(progress.getStage())) {
                    addLog(deployId, "修复失败: " + progress.getCurrentAction());
                    return false;
                }
                
                // 更新部署进度的子进度
                if (progress.getStage().equals("running")) {
                    updateProgress(deployId, "checking", 
                        10 + (progress.getPercentage() * 5 / 100), 
                        "自动修复中: " + progress.getCurrentAction());
                    addLog(deployId, "  [修复] " + progress.getCurrentAction());
                }
                
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                log.error("等待修复完成时出错", e);
                return false;
            }
        }
        
        addLog(deployId, "✗ 修复超时（超过 " + maxWaitMinutes + " 分钟）");
        return false;
    }

    private void addLog(String deployId, String logLine) {
        DeployProgress progress = deployProgressMap.get(deployId);
        if (progress != null) {
            synchronized (progress) {
                progress.getLogs().add("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + logLine);
                while (progress.getLogs().size() > MAX_LOG_LINES) {
                    progress.getLogs().remove(0);
                }
            }
        }
    }

    private void updateProgress(String deployId, String stage, int percentage, String currentAction) {
        DeployProgress progress = deployProgressMap.get(deployId);
        if (progress != null) {
            progress.setStage(stage);
            progress.setPercentage(percentage);
            progress.setCurrentAction(currentAction);
        }
    }

    /**
     * 将 DeploymentTask 转换为轻量 DTO（列表/当前实例用，不跑 subprocess）
     */
    private DeploymentListItem convertToDeploymentListItemLight(DeploymentTask task) {
        DeploymentListItem item = new DeploymentListItem();
        item.setId(task.getId());
        item.setCreatedAt(task.getStartTime());
        item.setStatus(task.getStatus());
        applyConfigJsonToItem(task.getConfigJson(), item);
        item.setVersion(null);
        item.setGatewayRunning(null);
        return item;
    }

    /**
     * 将 DeploymentTask 转换为 DeploymentListItem DTO（含实时版本与 Gateway 探测）
     */
    private DeploymentListItem convertToDeploymentListItem(DeploymentTask task) {
        DeploymentListItem item = convertToDeploymentListItemLight(task);
        String version = getOpenClawVersion();
        item.setVersion(version);
        
        // 检查 Gateway 运行状态
        boolean gatewayRunning = false;
        try {
            // 通过检查端口是否被占用来判断 Gateway 是否在运行
            if (item.getPort() != null) {
                ProcessBuilder pb = new ProcessBuilder("cmd", "/c", 
                    "netstat -ano | findstr :" + item.getPort());
                pb.redirectErrorStream(true);
                Process process = pb.start();
                process.waitFor(2, TimeUnit.SECONDS);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"))) {
                    String line = reader.readLine();
                    if (line != null && line.contains("LISTENING")) {
                        gatewayRunning = true;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("检查 Gateway 状态失败", e);
        }
        item.setGatewayRunning(gatewayRunning);
        
        // 构建部署摘要
        if (item.getPort() != null && item.getWorkDir() != null) {
            DeploySummary summary = new DeploySummary();
            summary.setVersion(version);
            summary.setGatewayPort(item.getPort());
            summary.setWorkDir(item.getWorkDir());
            summary.setInstallSource(item.getInstallMethod());
            summary.setPath(item.getWorkDir());
            summary.setConfigPath(item.getWorkDir() + File.separator + "openclaw.config.yml");
            item.setSummary(summary);
        }
        
        return item;
    }

    private void applyConfigJsonToItem(String configJson, DeploymentListItem item) {
        if (configJson == null || configJson.isEmpty()) {
            return;
        }
        try {
            String installSource = extractJsonValue(configJson, "installSource");
            item.setInstallMethod(installSource != null ? installSource : "未知");

            String portStr = extractJsonValue(configJson, "gatewayPort");
            if (portStr != null) {
                try {
                    item.setPort(Integer.parseInt(portStr));
                } catch (NumberFormatException ignored) {
                }
            }

            String workDir = extractJsonValue(configJson, "workDir");
            item.setWorkDir(workDir != null && !workDir.isEmpty() ? workDir : "默认");
        } catch (Exception e) {
            log.warn("解析部署配置失败", e);
            item.setInstallMethod("未知");
            item.setWorkDir("未知");
        }
    }

    /**
     * 从简单 JSON 字符串中提取指定 key 的值（仅支持简单字符串/数字值，不处理嵌套）
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;
        
        int colonIndex = json.indexOf(':', keyIndex + searchKey.length());
        if (colonIndex == -1) return null;
        
        int valueStart = -1;
        for (int i = colonIndex + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c != ' ' && c != '\t' && c != '\n') {
                valueStart = i;
                break;
            }
        }
        if (valueStart == -1) return null;
        
        char firstChar = json.charAt(valueStart);
        StringBuilder value = new StringBuilder();
        
        if (firstChar == '"') {
            // 字符串值
            for (int i = valueStart + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '"' && json.charAt(i - 1) != '\\') break;
                if (c != '\\') value.append(c);
            }
        } else if (firstChar == 't' || firstChar == 'f') {
            // 布尔值
            return json.startsWith("true", valueStart) ? "true" : "false";
        } else {
            // 数字值
            for (int i = valueStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == ' ' || c == ',' || c == '}' || c == '\n' || c == '\t') break;
                value.append(c);
            }
        }
        
        return value.toString();
    }
}