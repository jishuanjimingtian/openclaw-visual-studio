package com.openclaw.vs.service;

import com.openclaw.vs.dto.GatewayInfo;
import com.openclaw.vs.gateway.GatewayWebSocketClient;
import com.openclaw.vs.gateway.OpenClawGatewayConfigReader;
import com.openclaw.vs.util.WindowsProcessUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessHandle;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayService {

    public static final String MANAGED_VS_PROCESS = "vs-process";
    public static final String MANAGED_DAEMON = "daemon";
    public static final String MANAGED_EXTERNAL = "external";

    private static final int DEFAULT_GATEWAY_PORT = OpenClawGatewayConfigReader.DEFAULT_GATEWAY_PORT;
    private static final int SOCKET_TIMEOUT_MS = 3000;
    private static final int SOCKET_TIMEOUT_QUICK_MS = 400;
    private static final long STATUS_CACHE_TTL_MS = 2_500;
    private static final long VERSION_CACHE_TTL_MS = 300_000;
    private static final long DAEMON_CHECK_CACHE_TTL_MS = 60_000;
    private static final long PORT_DISCOVERY_CACHE_MS = 5_000;
    private static final long RECONNECT_COOLDOWN_MS = 10_000;
    private static final long STARTUP_PORT_WAIT_MS = 120_000;
    private static final long STARTUP_RPC_WAIT_MS = 60_000;
    private static final long STOP_OPENCLAW_TIMEOUT_SEC = 12;
    private static final long STOP_PROCESS_WAIT_SEC = 2;
    private static final long STOP_PORT_CLOSE_WAIT_SEC = 5;
    private static final long STOP_PORT_POLL_MS = 100;

    public static final String PHASE_IDLE = "idle";
    public static final String PHASE_LAUNCHING = "launching";
    public static final String PHASE_PORT_WAIT = "port_wait";
    public static final String PHASE_RPC_CONNECT = "rpc_connect";
    public static final String PHASE_RUNNING = "running";
    public static final String PHASE_FAILED = "failed";
    private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GatewayWebSocketClient gatewayWebSocketClient;

    @Value("${app.gateway.auto-adopt-on-startup:true}")
    private boolean autoAdoptOnStartup;

    @Value("${app.gateway.prefer-daemon:true}")
    private boolean preferDaemon;

    private volatile Long gatewayPid;
    private volatile Process gatewayProcess;
    private volatile LocalDateTime startTime;
    private volatile int activePort = DEFAULT_GATEWAY_PORT;
    private volatile String workDir;
    /** vs-process | daemon | external */
    private volatile String managementMode;
    /** 后台读取 Gateway stdout，避免阻塞启动检测 */
    private volatile Thread gatewayOutputReader;
    private final AtomicReference<StringBuilder> gatewayOutputBuffer = new AtomicReference<>();

    /** 轻量 status 轮询缓存（避免 5s 一次的全量端口扫描 / subprocess） */
    private volatile GatewayInfo cachedLightStatus;
    private volatile long cachedLightStatusAt;
    private volatile String cachedVersion;
    private volatile long cachedVersionAt;
    private volatile Boolean cachedDaemonInstalled;
    private volatile long cachedDaemonInstalledAt;
    private volatile int cachedDiscoveredPort = -1;
    private volatile long cachedDiscoveredPortAt;
    private volatile long lastBackgroundReconnectMs;

    private final Object startupLock = new Object();
    private volatile CompletableFuture<Void> startupTask;
    private volatile String startupPhase = PHASE_IDLE;
    private volatile String startupMessage;
    private volatile int startupProgress;
    private volatile int startupTargetPort;

    @EventListener(ApplicationReadyEvent.class)
    public void adoptExistingOnStartup() {
        if (!autoAdoptOnStartup) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1500);
                int port = OpenClawGatewayConfigReader.resolveEffectiveGatewayPort(DEFAULT_GATEWAY_PORT);
                activePort = port;
                if (isGatewayPortReady(port)) {
                    adoptExistingGateway(GatewayInfo.builder(), port);
                    log.info("启动时检测到 Gateway 已在端口 {} 运行，已自动连接", port);
                } else if (!gatewayWebSocketClient.isConnected()) {
                    gatewayWebSocketClient.reconnectIfNeeded();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.debug("启动时自动连接 Gateway 失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 主动连接已在运行的 Gateway（不启动新进程）。
     */
    public synchronized GatewayInfo connectExisting() {
        gatewayWebSocketClient.resumeConnection();
        invalidateStatusCache();
        int port = discoverGatewayPort(true);
        activePort = port;

        if (!gatewayWebSocketClient.isConnected()) {
            if (isPortOpen(port)) {
                gatewayWebSocketClient.setRuntimeGatewayPort(port);
            }
            gatewayWebSocketClient.reconnectIfNeeded();
            waitForGatewayClientReady(8, TimeUnit.SECONDS);
        }

        if (!gatewayWebSocketClient.isConnected() && !isPortOpen(port)) {
            GatewayInfo.GatewayInfoBuilder builder = GatewayInfo.builder()
                    .port(port)
                    .status("stopped")
                    .wsConnected(false)
                    .message("未检测到运行中的 Gateway（端口 " + port + "）");
            applyManagedBy(builder);
            GatewayInfo stopped = builder.build();
            publishStatusSnapshot(stopped, true);
            return stopped;
        }

        return getGatewayStatus(true);
    }

    /**
     * 启动 OpenClaw Gateway 进程
     */
    public GatewayInfo startGateway(Integer port) {
        return startGateway(port, null);
    }

    /**
     * @param useDaemon true=后台服务长期运行；false=前台子进程；null=按配置 prefer-daemon
     */
    public GatewayInfo startGateway(Integer port, Boolean useDaemon) {
        cleanupStaleSessionLocks();
        gatewayWebSocketClient.resumeConnection();
        synchronized (startupLock) {
            if (startupTask != null && !startupTask.isDone()) {
                return buildStartupProgressResponse();
            }
        }

        invalidateStatusCache();
        cancelStartupTaskQuietly();

        boolean daemon = useDaemon != null ? useDaemon : preferDaemon;
        int targetPort = resolveTargetPort(port);
        activePort = targetPort;

        if (isGatewayPortReady(targetPort)) {
            clearStartupState();
            GatewayInfo.GatewayInfoBuilder builder = GatewayInfo.builder();
            managementMode = isDaemonServiceInstalledCached() ? MANAGED_DAEMON : MANAGED_EXTERNAL;
            return adoptExistingGateway(builder, targetPort);
        }

        GatewayInfo preflightFailure = runStartPreflight(targetPort, daemon);
        if (preflightFailure != null) {
            return preflightFailure;
        }

        startupTargetPort = targetPort;
        setStartupPhase(PHASE_LAUNCHING, "正在启动 OpenClaw Gateway…", 10);

        final boolean daemonMode = daemon;
        startupTask = CompletableFuture.runAsync(() -> runStartupCompletion(targetPort, daemonMode));

        return buildStartupProgressResponse();
    }

    private int resolveTargetPort(Integer port) {
        if (port != null && port > 0) {
            return port;
        }
        return OpenClawGatewayConfigReader.readGatewayPort().orElse(DEFAULT_GATEWAY_PORT);
    }

    /** 同步前置校验；失败时返回 GatewayInfo，成功返回 null */
    private GatewayInfo runStartPreflight(int targetPort, boolean daemon) {
        if (isPortOpenQuick(targetPort) && !OpenClawGatewayConfigReader.isPortLikelyGateway(targetPort)) {
            return failedStartupInfo(targetPort,
                "端口 " + targetPort + " 已被占用，但不是 OpenClaw Gateway（请更换端口或停止占用进程）");
        }
        if (resolveOpenclawPath() == null) {
            return failedStartupInfo(targetPort,
                "openclaw 命令未找到，请确保已通过 npm install -g openclaw 安装");
        }
        if (!daemon && isGatewayRunning()) {
            clearStartupState();
            return adoptExistingGateway(GatewayInfo.builder(), activePort);
        }
        return null;
    }

    private void runStartupCompletion(int targetPort, boolean daemon) {
        try {
            boolean launched = daemon ? executeDaemonLaunch(targetPort) : executeForegroundLaunch(targetPort);
            if (!launched || PHASE_FAILED.equals(startupPhase)) {
                return;
            }
            waitForPortPhase(targetPort, daemon);
            if (PHASE_FAILED.equals(startupPhase)) {
                return;
            }
            waitForRpcPhase(targetPort);
            if (PHASE_FAILED.equals(startupPhase)) {
                return;
            }
            finalizeStartupSuccess(targetPort);
        } catch (IOException e) {
            log.error("启动 Gateway 失败", e);
            failStartup("启动 Gateway 失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("启动 Gateway 异常", e);
            failStartup("启动 Gateway 失败: " + e.getMessage());
        } finally {
            synchronized (startupLock) {
                CompletableFuture<Void> task = startupTask;
                if (task != null && task.isDone()) {
                    startupTask = null;
                }
            }
        }
    }

    private boolean executeDaemonLaunch(int targetPort) throws IOException {
        setStartupPhase(PHASE_LAUNCHING, "正在安装/启动 Gateway 后台服务…", 15);

        if (!isDaemonServiceInstalled()) {
            OpenclawCommandResult install = runOpenclaw("gateway", "install", "--port", String.valueOf(targetPort));
            if (install.exitCode() != 0 && !install.outputContains("already installed", "registered", "已安装")) {
                log.warn("Gateway 服务安装输出: {}", install.output());
            }
        } else {
            runOpenclaw("gateway", "install", "--port", String.valueOf(targetPort), "--force");
        }

        OpenclawCommandResult start = runOpenclaw("gateway", "start");
        if (start.exitCode() != 0 && !isPortOpenQuick(targetPort)) {
            failStartup("启动 Gateway 后台服务失败: " + start.outputTail(500));
            return false;
        }

        gatewayProcess = null;
        managementMode = MANAGED_DAEMON;
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
        workDir = System.getProperty("user.dir");
        gatewayWebSocketClient.setRuntimeGatewayPort(targetPort);
        return true;
    }

    private boolean executeForegroundLaunch(int targetPort) throws IOException {
        setStartupPhase(PHASE_LAUNCHING, "正在拉起 Gateway 进程…", 15);

        String openclawPath = resolveOpenclawPath();
        if (openclawPath == null) {
            failStartup("openclaw 命令未找到");
            return false;
        }

        ProcessBuilder pb = WindowsProcessUtils.cmdProcessBuilder(
            openclawPath, "gateway", "--port", String.valueOf(targetPort), "run");
        pb.directory(new File(System.getProperty("user.dir")));
        log.debug("启动 Gateway: {} gateway --port {} run", openclawPath, targetPort);

        gatewayProcess = WindowsProcessUtils.start(pb);
        gatewayPid = gatewayProcess.pid();
        managementMode = MANAGED_VS_PROCESS;
        startTime = LocalDateTime.now();
        workDir = System.getProperty("user.dir");
        startGatewayOutputReader(gatewayProcess);
        gatewayWebSocketClient.setRuntimeGatewayPort(targetPort);
        return true;
    }

    private void waitForPortPhase(int targetPort, boolean daemon) {
        setStartupPhase(PHASE_PORT_WAIT, "等待 Gateway 端口 " + targetPort + " 就绪…", 25);
        long deadline = System.currentTimeMillis() + STARTUP_PORT_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (!daemon && gatewayProcess != null && !gatewayProcess.isAlive()) {
                int exitCode = gatewayProcess.exitValue();
                failStartup("Gateway 启动失败: 退出码 " + exitCode + ", 输出: " + snapshotGatewayOutput());
                gatewayPid = null;
                gatewayProcess = null;
                startTime = null;
                stopGatewayOutputReader();
                return;
            }
            if (isGatewayPortReady(targetPort) || isPortOpenQuick(targetPort)) {
                activePort = targetPort;
                if (!daemon) {
                    Optional<ProcessHandle> existing = findProcessByPort(targetPort);
                    gatewayPid = existing.map(ProcessHandle::pid).orElse(gatewayPid);
                } else {
                    findProcessByPort(targetPort).ifPresent(ph -> gatewayPid = ph.pid());
                }
                setStartupPhase(PHASE_PORT_WAIT, "Gateway 端口已就绪", 55);
                return;
            }
            int elapsed = (int) ((STARTUP_PORT_WAIT_MS - (deadline - System.currentTimeMillis())) * 30 / STARTUP_PORT_WAIT_MS);
            setStartupPhase(PHASE_PORT_WAIT, "等待 Gateway 端口 " + targetPort + " 就绪…", 25 + Math.min(25, elapsed));
            sleepQuiet(500);
        }
        if (daemon || (gatewayProcess != null && gatewayProcess.isAlive()) || isPortOpenQuick(targetPort)) {
            setStartupPhase(PHASE_PORT_WAIT, "Gateway 仍在启动，端口尚未就绪（将继续等待 RPC 阶段重试）", 50);
            return;
        }
        failStartup("Gateway 启动超时：端口 " + targetPort + " 在 " + (STARTUP_PORT_WAIT_MS / 1000) + " 秒内未就绪");
    }

    private void waitForRpcPhase(int targetPort) {
        setStartupPhase(PHASE_RPC_CONNECT, "端口已监听，正在建立 RPC 连接…", 60);
        gatewayWebSocketClient.setRuntimeGatewayPort(targetPort);
        gatewayWebSocketClient.reconnectIfNeeded();

        long deadline = System.currentTimeMillis() + STARTUP_RPC_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (gatewayWebSocketClient.isConnected()) {
                setStartupPhase(PHASE_RPC_CONNECT, "RPC 连接已建立", 90);
                return;
            }
            if (gatewayProcess != null && !gatewayProcess.isAlive()) {
                failStartup("Gateway 进程已退出: " + snapshotGatewayOutput());
                return;
            }
            int elapsed = (int) ((STARTUP_RPC_WAIT_MS - (deadline - System.currentTimeMillis())) * 25 / STARTUP_RPC_WAIT_MS);
            setStartupPhase(PHASE_RPC_CONNECT, "端口已监听，正在建立 RPC 连接…", 60 + Math.min(25, elapsed));
            sleepQuiet(500);
        }
        if (gatewayWebSocketClient.isConnected()) {
            return;
        }
        if (isPortOpenQuick(targetPort) && (gatewayProcess == null || gatewayProcess.isAlive())) {
            setStartupPhase(PHASE_RPC_CONNECT,
                "Gateway 端口已开放，后端 RPC 尚未连接（可稍后刷新或检查 device.json）", 85);
            return;
        }
        failStartup("Gateway RPC 连接超时，请检查 device.json / token / 端口配置");
    }

    private void finalizeStartupSuccess(int targetPort) {
        boolean clientReady = gatewayWebSocketClient.isConnected();
        GatewayInfo info = GatewayInfo.builder()
            .port(targetPort)
            .status(clientReady ? "running" : "starting")
            .startupPhase(clientReady ? PHASE_RUNNING : PHASE_RPC_CONNECT)
            .startupProgress(clientReady ? 100 : 85)
            .pid(gatewayPid)
            .endpoint("http://localhost:" + targetPort)
            .workDir(workDir)
            .wsConnected(clientReady)
            .serviceInstalled(isDaemonServiceInstalledCached())
            .version(getGatewayVersionCached())
            .message(clientReady ? null : "Gateway 端口已监听，后端正在连接（需完成 device 握手）")
            .build();
        if (startTime != null) {
            info.setStartTime(startTime.format(DT_FORMAT));
            info.setUptime(formatDuration(Duration.between(startTime, LocalDateTime.now())));
        }
        applyManagedByFields(info);
        setStartupPhase(clientReady ? PHASE_RUNNING : PHASE_RPC_CONNECT, info.getMessage(), info.getStartupProgress());
        publishStatusSnapshot(info, true);
        log.info("Gateway 启动完成, port={}, wsConnected={}", targetPort, clientReady);
    }

    private void applyManagedByFields(GatewayInfo info) {
        info.setManagedBy(managementMode);
        info.setServiceInstalled(isDaemonServiceInstalledCached());
    }

    private void setStartupPhase(String phase, String message, int progress) {
        startupPhase = phase;
        startupMessage = message;
        startupProgress = Math.max(0, Math.min(100, progress));
        invalidateStatusCache();
    }

    private void clearStartupState() {
        startupPhase = PHASE_IDLE;
        startupMessage = null;
        startupProgress = 0;
    }

    private void failStartup(String message) {
        log.warn("Gateway 启动失败: {}", message);
        setStartupPhase(PHASE_FAILED, message, 0);
        GatewayInfo.GatewayInfoBuilder builder = GatewayInfo.builder()
            .port(startupTargetPort > 0 ? startupTargetPort : activePort)
            .status("failed")
            .startupPhase(PHASE_FAILED)
            .startupProgress(0)
            .message(message)
            .wsConnected(false);
        applyManagedBy(builder);
        GatewayInfo failed = builder.build();
        publishStatusSnapshot(failed, true);
    }

    private GatewayInfo failedStartupInfo(int port, String message) {
        clearStartupState();
        GatewayInfo.GatewayInfoBuilder builder = GatewayInfo.builder()
            .port(port)
            .status("failed")
            .startupPhase(PHASE_FAILED)
            .startupProgress(0)
            .message(message)
            .wsConnected(false);
        applyManagedBy(builder);
        GatewayInfo info = builder.build();
        publishStatusSnapshot(info, true);
        return info;
    }

    private GatewayInfo buildStartupProgressResponse() {
        int port = startupTargetPort > 0 ? startupTargetPort : activePort;
        boolean clientReady = gatewayWebSocketClient.isConnected();
        String status = PHASE_FAILED.equals(startupPhase) ? "failed"
            : (clientReady ? "running" : "starting");
        if (clientReady && !PHASE_FAILED.equals(startupPhase)) {
            startupPhase = PHASE_RUNNING;
            startupProgress = 100;
        }

        GatewayInfo.GatewayInfoBuilder builder = GatewayInfo.builder()
            .port(port)
            .status(status)
            .startupPhase(startupPhase)
            .startupProgress(startupProgress)
            .message(startupMessage)
            .wsConnected(clientReady)
            .pid(gatewayPid)
            .serviceInstalled(isDaemonServiceInstalledCached())
            .version(getGatewayVersionCached());

        if (clientReady || isPortOpenQuick(port)) {
            builder.endpoint("http://localhost:" + port);
        }
        if (startTime != null) {
            builder.startTime(startTime.format(DT_FORMAT));
            builder.uptime(formatDuration(Duration.between(startTime, LocalDateTime.now())));
        }
        if (workDir != null) {
            builder.workDir(workDir);
        }
        applyManagedBy(builder);
        return builder.build();
    }

    private boolean isStartupInProgress() {
        CompletableFuture<Void> task = startupTask;
        return task != null && !task.isDone()
            && !PHASE_FAILED.equals(startupPhase)
            && !PHASE_IDLE.equals(startupPhase);
    }

    private void cancelStartupTaskQuietly() {
        synchronized (startupLock) {
            CompletableFuture<Void> task = startupTask;
            if (task != null && !task.isDone()) {
                task.cancel(true);
            }
            startupTask = null;
        }
    }

    /**
     * 清理旧进程残留的 session .lock 文件。
     * OpenClaw 运行时在 sessions 目录下为每个 session 创建 .lock 文件用于并发控制。
     * 旧进程异常退出后这些 .lock 文件不会被清理，新实例启动时因锁冲突触发
     * SessionWriteLockTimeoutError 导致超时崩溃。
     */
    private void cleanupStaleSessionLocks() {
        String homeDir = System.getProperty("user.home");
        File sessionsDir = new File(homeDir, ".openclaw\\agents\\main\\sessions");
        if (!sessionsDir.exists() || !sessionsDir.isDirectory()) {
            return;
        }
        File[] lockFiles = sessionsDir.listFiles((dir, name) -> name.endsWith(".lock"));
        if (lockFiles == null || lockFiles.length == 0) {
            return;
        }
        log.info("检测到 {} 个残留 session lock 文件，正在清理…", lockFiles.length);
        int cleaned = 0;
        for (File lockFile : lockFiles) {
            try {
                if (lockFile.delete()) {
                    cleaned++;
                } else {
                    log.warn("无法删除残留 session lock 文件: {}", lockFile.getAbsolutePath());
                }
            } catch (SecurityException e) {
                log.warn("删除 session lock 文件权限不足: {}", lockFile.getAbsolutePath());
            }
        }
        if (cleaned > 0) {
            log.info("已清理 {} 个残留 session lock 文件", cleaned);
        }
    }

    private void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 端口上已有 Gateway 在监听时复用现有实例，避免重复启动导致冲突退出。
     */
    private GatewayInfo adoptExistingGateway(GatewayInfo.GatewayInfoBuilder builder, int port) {
        Optional<ProcessHandle> existing = findProcessByPort(port);
        gatewayProcess = null;
        gatewayPid = existing.map(ProcessHandle::pid).orElse(null);
        activePort = port;
        if (managementMode == null) {
            managementMode = isDaemonServiceInstalled() ? MANAGED_DAEMON : MANAGED_EXTERNAL;
        }
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
        if (workDir == null) {
            workDir = System.getProperty("user.dir");
        }

        gatewayWebSocketClient.setRuntimeGatewayPort(port);
        gatewayWebSocketClient.reconnectIfNeeded();
        boolean clientReady = waitForGatewayClientReady(10, TimeUnit.SECONDS);

        builder.port(port)
                .pid(gatewayPid)
                .endpoint("http://localhost:" + port)
                .workDir(workDir)
                .wsConnected(clientReady)
                .serviceInstalled(isDaemonServiceInstalled());

        if (startTime != null) {
            builder.startTime(startTime.format(DT_FORMAT));
        }

        if (clientReady) {
            builder.status("running")
                .startupPhase(PHASE_RUNNING)
                .startupProgress(100);
            clearStartupState();
            log.info("Gateway 端口 {} 已有实例在运行, 已复用 (PID: {}, managedBy={})", port, gatewayPid, managementMode);
        } else {
            builder.status("starting")
                .startupPhase(PHASE_RPC_CONNECT)
                .startupProgress(70)
                    .message("Gateway 端口已占用，后端正在连接（需完成 device 握手）");
            log.info("Gateway 端口 {} 已有实例在运行 (PID: {}), 等待后端 RPC 连接", port, gatewayPid);
        }
        applyManagedBy(builder);
        return builder.build();
    }

    private void startGatewayOutputReader(Process process) {
        stopGatewayOutputReader();
        StringBuilder buffer = new StringBuilder();
        gatewayOutputBuffer.set(buffer);
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    log.debug("Gateway: {}", line);
                    synchronized (buffer) {
                        buffer.append(line).append('\n');
                    }
                }
            } catch (IOException e) {
                log.debug("Gateway 输出读取异常: {}", e.getMessage());
            }
        }, "gateway-stdout");
        reader.setDaemon(true);
        gatewayOutputReader = reader;
        reader.start();
    }

    private void stopGatewayOutputReader() {
        Thread reader = gatewayOutputReader;
        gatewayOutputReader = null;
        if (reader != null && reader.isAlive()) {
            reader.interrupt();
        }
        gatewayOutputBuffer.set(null);
    }

    private String snapshotGatewayOutput() {
        StringBuilder buffer = gatewayOutputBuffer.get();
        if (buffer == null) {
            return "";
        }
        synchronized (buffer) {
            return buffer.toString();
        }
    }

    /**
     * 停止 OpenClaw Gateway（后台服务、子进程或外部实例）
     */
    public synchronized GatewayInfo stopGateway() {
        cancelStartupTaskQuietly();
        clearStartupState();
        invalidateStatusCache();
        gatewayWebSocketClient.pauseConnection();

        GatewayInfo.GatewayInfoBuilder builder = GatewayInfo.builder().port(activePort);
        int port = resolveEffectivePort();
        builder.port(port);

        boolean portWasReady = isPortOpenQuick(port);
        if (gatewayProcess == null && gatewayPid == null && portWasReady) {
            findProcessByPort(port).ifPresent(ph -> gatewayPid = ph.pid());
        }

        if (!portWasReady && gatewayProcess == null && gatewayPid == null) {
            builder.status("stopped").wsConnected(false);
            applyManagedBy(builder);
            log.info("没有正在运行的 Gateway");
            GatewayInfo stopped = builder.build();
            publishStatusSnapshot(stopped, true);
            return stopped;
        }

        try {
            if (gatewayProcess != null && gatewayProcess.isAlive()) {
                managementMode = MANAGED_VS_PROCESS;
                gatewayProcess.destroy();
                boolean terminated = gatewayProcess.waitFor(STOP_PROCESS_WAIT_SEC, TimeUnit.SECONDS);
                if (!terminated) {
                    gatewayProcess.destroyForcibly();
                    log.warn("Gateway 子进程 (PID: {}) 被强制终止", gatewayPid);
                }
                waitForPortClosed(port, STOP_PORT_CLOSE_WAIT_SEC, TimeUnit.SECONDS);
            } else if (MANAGED_DAEMON.equals(managementMode) || isDaemonServiceInstalledCached()) {
                OpenclawCommandResult stop = runOpenclaw(STOP_OPENCLAW_TIMEOUT_SEC, "gateway", "stop");
                if (stop.exitCode() != 0) {
                    log.warn("openclaw gateway stop 输出: {}", stop.output());
                }
                waitForPortClosed(port, STOP_PORT_CLOSE_WAIT_SEC, TimeUnit.SECONDS);
            } else if (gatewayPid != null) {
                ProcessHandle.of(gatewayPid).ifPresent(ph -> {
                    ph.destroy();
                    try {
                        ph.onExit().get(STOP_PROCESS_WAIT_SEC, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        ph.destroyForcibly();
                    } catch (Exception e) {
                        ph.destroyForcibly();
                    }
                });
                waitForPortClosed(port, STOP_PORT_CLOSE_WAIT_SEC, TimeUnit.SECONDS);
            } else if (portWasReady) {
                findProcessByPort(port).ifPresent(ph -> {
                    gatewayPid = ph.pid();
                    ph.destroy();
                    try {
                        ph.onExit().get(STOP_PROCESS_WAIT_SEC, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        ph.destroyForcibly();
                    } catch (Exception e) {
                        ph.destroyForcibly();
                    }
                });
                if (isPortOpenQuick(port)) {
                    OpenclawCommandResult stop = runOpenclaw(STOP_OPENCLAW_TIMEOUT_SEC, "gateway", "stop");
                    if (stop.exitCode() != 0) {
                        log.warn("openclaw gateway stop 输出: {}", stop.output());
                    }
                    waitForPortClosed(port, STOP_PORT_CLOSE_WAIT_SEC, TimeUnit.SECONDS);
                }
            }

            builder.status("stopped").wsConnected(false);
            if (isPortOpenQuick(port)) {
                builder.message("Gateway 已停止，但端口 " + port + " 仍被占用（可能为残留进程或其他服务）");
            }
            if (gatewayPid != null) {
                builder.pid(gatewayPid);
            }
            log.info("Gateway 已停止 (port={}, managedBy={})", port, managementMode);

        } catch (Exception e) {
            log.error("停止 Gateway 失败", e);
            builder.status("error").pid(gatewayPid).message("停止 Gateway 失败: " + e.getMessage());
        } finally {
            stopGatewayOutputReader();
            gatewayPid = null;
            gatewayProcess = null;
            startTime = null;
            managementMode = null;
        }

        applyManagedBy(builder);
        publishStatusSnapshot(builder.build(), true);
        return builder.build();
    }

    /**
     * 获取 Gateway 运行状态。
     *
     * @param fullProbe true=完整探测（connect/手动刷新）；false=轻量轮询（默认，毫秒级）
     */
    public GatewayInfo getGatewayStatus() {
        return getGatewayStatus(false);
    }

    public GatewayInfo getGatewayStatus(boolean fullProbe) {
        if (isStartupInProgress()) {
            GatewayInfo progress = buildStartupProgressResponse();
            publishStatusSnapshot(progress, fullProbe);
            return withFreshUptime(progress);
        }
        if (!fullProbe && isLightCacheValid()) {
            return withFreshUptime(cachedLightStatus);
        }
        GatewayInfo info = fullProbe ? probeGatewayStatusFull() : probeGatewayStatusLight();
        publishStatusSnapshot(info, fullProbe);
        return info;
    }

    private void invalidateStatusCache() {
        cachedLightStatus = null;
        cachedLightStatusAt = 0;
        cachedDiscoveredPort = -1;
        cachedDiscoveredPortAt = 0;
    }

    private boolean isLightCacheValid() {
        return cachedLightStatus != null
            && System.currentTimeMillis() - cachedLightStatusAt < STATUS_CACHE_TTL_MS;
    }

    private void publishStatusSnapshot(GatewayInfo info, boolean fullProbe) {
        if (!fullProbe) {
            cachedLightStatus = info;
            cachedLightStatusAt = System.currentTimeMillis();
        } else {
            cachedLightStatus = info;
            cachedLightStatusAt = System.currentTimeMillis();
        }
    }

    private GatewayInfo withFreshUptime(GatewayInfo base) {
        if (startTime == null || base == null) {
            return base;
        }
        GatewayInfo copy = base.toBuilder().build();
        copy.setUptime(formatDuration(Duration.between(startTime, LocalDateTime.now())));
        return copy;
    }

    /** 轻量轮询：不阻塞重连、不跑版本 subprocess、尽量复用内存态 */
    private GatewayInfo probeGatewayStatusLight() {
        int port = resolveKnownPort();
        if (gatewayWebSocketClient.isConnectionPaused()) {
            return buildStoppedInfo(port, stoppedWhilePortOccupiedMessage(port));
        }

        boolean clientReady = gatewayWebSocketClient.isConnected();

        if (clientReady) {
            ensureManagementModeCached();
            return buildActiveGatewayInfo(port, true, true, null);
        }

        if (gatewayProcess != null && gatewayProcess.isAlive()) {
            ensureManagementModeCached();
            boolean portReady = isPortOpenQuick(port);
            return buildActiveGatewayInfo(port, false, portReady,
                portReady ? "Gateway 端口已开放，后端 RPC 客户端未连接（检查 device.json / token / 端口）" : "Gateway 进程存在但端口未监听");
        }

        if (isKnownPidAlive()) {
            ensureManagementModeCached();
            if (gatewayPid != null && port > 0) {
                findProcessByPort(port).ifPresent(ph -> gatewayPid = ph.pid());
            }
            boolean portReady = isPortOpenQuick(port);
            if (portReady) {
                maybeScheduleBackgroundReconnect(port);
            }
            return buildActiveGatewayInfo(port, false, portReady,
                portReady ? "Gateway 端口已开放，后端 RPC 客户端未连接（检查 device.json / token / 端口）" : "Gateway 进程存在但端口未监听");
        }

        boolean portReady = isPortOpenQuick(port);
        if (portReady) {
            activePort = port;
            maybeScheduleBackgroundReconnect(port);
            return buildActiveGatewayInfo(port, false, true,
                "Gateway 端口已开放，后端 RPC 客户端未连接（检查 device.json / token / 端口）");
        }

        return GatewayInfo.builder()
            .port(port)
            .status("stopped")
            .wsConnected(false)
            .serviceInstalled(isDaemonServiceInstalledCached())
            .version(getGatewayVersionCached())
            .managedBy(managementMode)
            .build();
    }

    /** 完整探测：用于 connect / 手动刷新，允许同步重连 */
    private GatewayInfo probeGatewayStatusFull() {
        int port = discoverGatewayPort(true);
        activePort = port;
        if (gatewayWebSocketClient.isConnectionPaused()) {
            return buildStoppedInfo(port, stoppedWhilePortOccupiedMessage(port));
        }

        GatewayInfo.GatewayInfoBuilder builder = GatewayInfo.builder().port(port);

        boolean clientReady = gatewayWebSocketClient.isConnected();
        boolean portReady = isGatewayPortReady(port) || isPortOpen(port);

        if (isGatewayRunningFast(clientReady, portReady)) {
            if (gatewayPid == null) {
                findProcessByPort(port).ifPresent(ph -> gatewayPid = ph.pid());
            }
            ensureManagementModeCached();

            if (portReady && !clientReady) {
                gatewayWebSocketClient.setRuntimeGatewayPort(port);
                gatewayWebSocketClient.reconnectIfNeeded();
                clientReady = waitForGatewayClientReady(3, TimeUnit.SECONDS);
            }

            builder.status(clientReady ? "running" : "starting")
                .pid(gatewayPid)
                .endpoint("http://localhost:" + port)
                .serviceInstalled(isDaemonServiceInstalledCached());
            if (startTime != null) {
                builder.startTime(startTime.format(DT_FORMAT));
                builder.uptime(formatDuration(Duration.between(startTime, LocalDateTime.now())));
            }
            builder.workDir(workDir);
            if (!clientReady) {
                builder.message(portReady
                    ? "Gateway 端口已开放，后端 RPC 客户端未连接（检查 device.json / token / 端口）"
                    : "Gateway 进程存在但端口未监听");
            }
        } else {
            builder.status("stopped").serviceInstalled(isDaemonServiceInstalledCached());
        }

        builder.version(getGatewayVersionCached());
        builder.wsConnected(clientReady);
        applyManagedBy(builder);
        return builder.build();
    }

    private GatewayInfo buildActiveGatewayInfo(int port, boolean clientReady, boolean portReady, String message) {
        GatewayInfo.GatewayInfoBuilder builder = GatewayInfo.builder()
            .port(port)
            .wsConnected(clientReady)
            .serviceInstalled(isDaemonServiceInstalledCached())
            .version(getGatewayVersionCached())
            .endpoint(portReady || clientReady ? "http://localhost:" + port : null)
            .pid(gatewayPid)
            .workDir(workDir);

        if (clientReady) {
            builder.status("running");
        } else if (portReady) {
            builder.status("starting");
        } else {
            builder.status("starting");
        }

        if (startTime != null) {
            builder.startTime(startTime.format(DT_FORMAT));
            builder.uptime(formatDuration(Duration.between(startTime, LocalDateTime.now())));
        }
        if (message != null) {
            builder.message(message);
        }
        applyManagedBy(builder);
        return builder.build();
    }

    private int resolveKnownPort() {
        Integer runtimePort = gatewayWebSocketClient.getRuntimeGatewayPort();
        if (runtimePort != null && runtimePort > 0) {
            return runtimePort;
        }
        if (activePort > 0) {
            return activePort;
        }
        return OpenClawGatewayConfigReader.resolveEffectiveGatewayPort(DEFAULT_GATEWAY_PORT);
    }

    private boolean isKnownPidAlive() {
        if (gatewayPid == null) {
            return false;
        }
        return ProcessHandle.of(gatewayPid).map(ProcessHandle::isAlive).orElse(false);
    }

    private void ensureManagementModeCached() {
        if (managementMode != null) {
            return;
        }
        if (gatewayProcess != null && gatewayProcess.isAlive()) {
            managementMode = MANAGED_VS_PROCESS;
        } else {
            managementMode = isDaemonServiceInstalledCached() ? MANAGED_DAEMON : MANAGED_EXTERNAL;
        }
    }

    private void maybeScheduleBackgroundReconnect(int port) {
        if (gatewayWebSocketClient.isConnectionPaused()) {
            return;
        }
        if (gatewayWebSocketClient.isConnected()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackgroundReconnectMs < RECONNECT_COOLDOWN_MS) {
            return;
        }
        lastBackgroundReconnectMs = now;
        CompletableFuture.runAsync(() -> {
            try {
                gatewayWebSocketClient.setRuntimeGatewayPort(port);
                gatewayWebSocketClient.reconnectIfNeeded();
            } catch (Exception e) {
                log.debug("后台 Gateway 重连失败: {}", e.getMessage());
            }
        });
    }

    private boolean isGatewayRunningFast(boolean clientReady, boolean portReady) {
        if (clientReady) {
            return true;
        }
        if (gatewayProcess != null && gatewayProcess.isAlive()) {
            return true;
        }
        if (isKnownPidAlive()) {
            return true;
        }
        return portReady;
    }

    private String getGatewayVersionCached() {
        long now = System.currentTimeMillis();
        if (cachedVersion != null && now - cachedVersionAt < VERSION_CACHE_TTL_MS) {
            return cachedVersion;
        }
        String version = getGatewayVersion();
        if (version != null) {
            cachedVersion = version;
            cachedVersionAt = now;
        }
        return version;
    }

    private boolean isDaemonServiceInstalledCached() {
        long now = System.currentTimeMillis();
        if (cachedDaemonInstalled != null && now - cachedDaemonInstalledAt < DAEMON_CHECK_CACHE_TTL_MS) {
            return cachedDaemonInstalled;
        }
        boolean installed = isDaemonServiceInstalled();
        cachedDaemonInstalled = installed;
        cachedDaemonInstalledAt = now;
        return installed;
    }

    private boolean isPortOpenQuick(int port) {
        return isPortOpen(port, SOCKET_TIMEOUT_QUICK_MS);
    }

    /**
     * 获取 Gateway 日志
     */
    public List<String> getGatewayLogs(int limit) {
        List<String> logs = new ArrayList<>();
        String logDir = workDir != null ? workDir + File.separator + "logs" : "logs";
        File logFile = new File(logDir, "openclaw.log");

        if (!logFile.exists()) {
            // 尝试 OpenClaw 默认日志位置
            String userHome = System.getProperty("user.home");
            logFile = new File(userHome, ".openclaw" + File.separator + "logs" + File.separator + "gateway.log");
        }

        if (!logFile.exists()) {
            logs.add("暂无 Gateway 日志文件");
            if (gatewayProcess != null && gatewayProcess.isAlive()) {
                logs.add("Gateway 进程运行中 (PID: " + gatewayPid + ")，但日志文件不可用");
            } else {
                logs.add("Gateway 未运行");
            }
            return logs;
        }

        try {
            List<String> allLines = java.nio.file.Files.readAllLines(logFile.toPath());
            int start = Math.max(0, allLines.size() - limit);
            logs.addAll(allLines.subList(start, allLines.size()));
        } catch (IOException e) {
            logs.add("读取日志失败: " + e.getMessage());
        }

        return logs;
    }

    /**
     * 获取 Gateway 配置信息
     */
    public GatewayInfo getGatewayInfo() {
        GatewayInfo info = getGatewayStatus(true);
        if (info.getVersion() == null) {
            info.setVersion(getGatewayVersionCached());
        }
        if (info.getWorkDir() == null) {
            info.setWorkDir(workDir != null ? workDir : System.getProperty("user.dir"));
        }
        return info;
    }

    /**
     * 检查 Gateway 是否在运行
     */
    private boolean isGatewayRunning() {
        if (gatewayWebSocketClient.isConnected()) {
            return true;
        }

        if (gatewayProcess != null && gatewayProcess.isAlive()) {
            return true;
        }

        if (gatewayPid != null) {
            Optional<ProcessHandle> ph = ProcessHandle.of(gatewayPid);
            if (ph.isPresent() && ph.get().isAlive()) {
                return true;
            }
        }

        int port = discoverGatewayPort(true);
        return port > 0 && isPortOpen(port);
    }

    private int discoverGatewayPort() {
        return discoverGatewayPort(false);
    }

    private int discoverGatewayPort(boolean forceRescan) {
        long now = System.currentTimeMillis();
        if (!forceRescan && cachedDiscoveredPort > 0 && now - cachedDiscoveredPortAt < PORT_DISCOVERY_CACHE_MS) {
            return cachedDiscoveredPort;
        }

        Integer runtimePort = gatewayWebSocketClient.getRuntimeGatewayPort();
        if (runtimePort != null && runtimePort > 0 && isPortOpenQuick(runtimePort)) {
            cacheDiscoveredPort(runtimePort);
            return runtimePort;
        }
        if (activePort > 0 && isPortOpenQuick(activePort)) {
            cacheDiscoveredPort(activePort);
            return activePort;
        }

        int configured = OpenClawGatewayConfigReader.resolveEffectiveGatewayPort(DEFAULT_GATEWAY_PORT);
        List<Integer> candidates = new ArrayList<>();
        candidates.add(configured);
        candidates.add(DEFAULT_GATEWAY_PORT);
        OpenClawGatewayConfigReader.readGatewayPort().ifPresent(candidates::add);
        candidates.add(OpenClawGatewayConfigReader.deriveBrowserControlPort(configured));
        candidates.add(OpenClawGatewayConfigReader.deriveBrowserControlPort(DEFAULT_GATEWAY_PORT));

        for (int port : candidates) {
            if (port > 0 && port < 65536 && (isGatewayPortReady(port) || isPortOpenQuick(port))) {
                cacheDiscoveredPort(port);
                return port;
            }
        }
        cacheDiscoveredPort(configured);
        return configured;
    }

    private void cacheDiscoveredPort(int port) {
        cachedDiscoveredPort = port;
        cachedDiscoveredPortAt = System.currentTimeMillis();
    }

    private int resolveEffectivePort() {
        if (gatewayWebSocketClient.isConnected()) {
            Integer runtimePort = gatewayWebSocketClient.getRuntimeGatewayPort();
            if (runtimePort != null && runtimePort > 0) {
                return runtimePort;
            }
        }
        if (activePort > 0 && (gatewayProcess != null && gatewayProcess.isAlive() || isPortOpen(activePort))) {
            return activePort;
        }
        return discoverGatewayPort();
    }

    /**
     * 检测端口是否开放
     */
    private boolean isPortOpen(int port) {
        return isPortOpen(port, SOCKET_TIMEOUT_MS);
    }

    private boolean isPortOpen(int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isGatewayPortReady(int port) {
        return isPortOpen(port) && OpenClawGatewayConfigReader.isPortLikelyGateway(port);
    }

    private boolean waitForGatewayPort(int port, long timeout, TimeUnit unit) {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < deadline) {
            if (isGatewayPortReady(port)) {
                return true;
            }
            if (gatewayProcess != null && !gatewayProcess.isAlive()) {
                return false;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return isGatewayPortReady(port);
    }

    /**
     * 等待端口就绪
     */
    private boolean waitForPort(int port, long timeout, TimeUnit unit) {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < deadline) {
            if (isPortOpen(port)) {
                return true;
            }
            // 检查进程是否已退出
            if (gatewayProcess != null && !gatewayProcess.isAlive()) {
                return false;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * 等待后端 {@link GatewayWebSocketClient} 完成 OpenClaw 协议握手（connect.challenge → connect → hello-ok）。
     * 不能用「连上即关」的探测，否则会触发 Gateway 的 handshake timeout。
     */
    private boolean waitForGatewayClientReady(long timeout, TimeUnit unit) {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < deadline) {
            if (gatewayWebSocketClient.isConnected()) {
                return true;
            }
            if (gatewayProcess != null && !gatewayProcess.isAlive()) {
                return false;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return gatewayWebSocketClient.isConnected();
    }

    /**
     * 通过端口查找进程
     */
    private Optional<ProcessHandle> findProcessByPort(int port) {
        // Windows: 使用 netstat 查找
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c",
                    "netstat -ano | findstr :" + port + " | findstr LISTENING");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor(3, TimeUnit.SECONDS);

            for (String line : output.split("\\r?\\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    String[] parts = trimmed.split("\\s+");
                    if (parts.length >= 5) {
                        try {
                            long pid = Long.parseLong(parts[parts.length - 1]);
                            return ProcessHandle.of(pid);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("查找端口进程失败: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 解析 openclaw 命令路径
     */
    private String resolveOpenclawPath() {
        // 尝试直接使用 openclaw 命令
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "where", "openclaw");
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor(3, TimeUnit.SECONDS);
            if (!output.isEmpty() && !output.contains("INFO: Could not find files for the given pattern(s)")) {
                // 返回第一个找到的路径
                String[] paths = output.split("\\r?\\n");
                if (paths.length > 0) {
                    return "openclaw";
                }
            }
        } catch (Exception ignored) {
        }

        // 尝试 npx openclaw
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "npx", "--version");
            Process process = pb.start();
            process.waitFor(3, TimeUnit.SECONDS);
            if (process.exitValue() == 0) {
                return "npx openclaw";
            }
        } catch (Exception ignored) {
        }

        // 尝试全局 npm 路径
        String npmPath = System.getenv("APPDATA") + "\\npm\\openclaw.cmd";
        if (new File(npmPath).exists()) {
            return npmPath;
        }

        return null;
    }

    /**
     * 获取 OpenClaw 版本
     */
    private String getGatewayVersion() {
        String openclawPath = resolveOpenclawPath();
        if (openclawPath == null) {
            return null;
        }
        
        try {
            ProcessBuilder pb;
            if (openclawPath.equals("npx openclaw")) {
                pb = new ProcessBuilder("cmd", "/c", "npx", "openclaw", "--version");
            } else {
                pb = new ProcessBuilder("cmd", "/c", openclawPath, "--version");
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor(3, TimeUnit.SECONDS);
            if (!output.isEmpty()) {
                return output.replace("openclaw", "").trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 格式化持续时间
     */
    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private void applyManagedBy(GatewayInfo.GatewayInfoBuilder builder) {
        builder.managedBy(managementMode);
        builder.serviceInstalled(isDaemonServiceInstalledCached());
    }

    private String stoppedWhilePortOccupiedMessage(int port) {
        if (port > 0 && isPortOpenQuick(port)) {
            return "Gateway 已停止，但端口 " + port + " 仍被占用（可能为残留进程或其他服务）";
        }
        return null;
    }

    private GatewayInfo buildStoppedInfo(int port, String message) {
        GatewayInfo.GatewayInfoBuilder builder = GatewayInfo.builder()
            .port(port)
            .status("stopped")
            .wsConnected(false)
            .serviceInstalled(isDaemonServiceInstalledCached())
            .version(getGatewayVersionCached())
            .managedBy(managementMode);
        if (message != null && !message.isBlank()) {
            builder.message(message);
        }
        return builder.build();
    }

    private boolean isDaemonServiceInstalled() {
        try {
            OpenclawCommandResult status = runOpenclaw("gateway", "status");
            String output = status.output().toLowerCase();
            return output.contains("scheduled task")
                || output.contains("registered")
                || output.contains("systemd")
                || output.contains("launchd")
                || output.contains("service file:");
        } catch (IOException e) {
            return false;
        }
    }

    private boolean waitForPortClosed(int port, long timeout, TimeUnit unit) {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < deadline) {
            if (!isPortOpenQuick(port)) {
                return true;
            }
            try {
                Thread.sleep(STOP_PORT_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !isPortOpenQuick(port);
    }

    private OpenclawCommandResult runOpenclaw(String... args) throws IOException {
        return runOpenclaw(30, args);
    }

    private OpenclawCommandResult runOpenclaw(long timeoutSec, String... args) throws IOException {
        String openclawPath = resolveOpenclawPath();
        if (openclawPath == null) {
            throw new IOException("openclaw 命令未找到");
        }

        List<String> command = new ArrayList<>();
        if ("npx openclaw".equals(openclawPath)) {
            command.add("npx");
            command.add("openclaw");
        } else {
            command.add(openclawPath);
        }
        command.addAll(Arrays.asList(args));

        ProcessBuilder pb = WindowsProcessUtils.cmdProcessBuilder(command.toArray(new String[0]));
        Process process = WindowsProcessUtils.start(pb);
        String output;
        try {
            output = new String(process.getInputStream().readAllBytes());
            process.waitFor(timeoutSec, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("openclaw 命令被中断");
        }
        return new OpenclawCommandResult(process.exitValue(), output);
    }

    private record OpenclawCommandResult(int exitCode, String output) {
        boolean outputContains(String... needles) {
            String lower = output.toLowerCase();
            for (String needle : needles) {
                if (lower.contains(needle.toLowerCase())) {
                    return true;
                }
            }
            return false;
        }

        String outputTail(int maxLen) {
            if (output == null || output.length() <= maxLen) {
                return output != null ? output.trim() : "";
            }
            return output.substring(output.length() - maxLen).trim();
        }
    }
}