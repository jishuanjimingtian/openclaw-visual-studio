package com.openclaw.vs.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Windows 下静默启动子进程：stdout/stderr 重定向到 PIPE，避免继承控制台并弹出 CMD 窗口。
 */
public final class WindowsProcessUtils {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private WindowsProcessUtils() {
    }

    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * 构建 cmd /c 命令，I/O 重定向到 PIPE（JDK 在 Windows 上会使用 CREATE_NO_WINDOW）。
     */
    public static ProcessBuilder cmdProcessBuilder(String... command) {
        List<String> cmd = new ArrayList<>();
        cmd.add("cmd");
        cmd.add("/c");
        cmd.addAll(Arrays.asList(command));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        return pb;
    }

    public static Process start(ProcessBuilder pb) throws IOException {
        return pb.start();
    }

    /** OpenClaw Gateway Windows 计划任务默认名称 */
    public static final String OPENCLAW_GATEWAY_TASK_NAME = "OpenClaw Gateway";

    /**
     * 结束 Windows 计划任务（best-effort，用于停止僵死的 Gateway 服务）。
     */
    public static void endWindowsScheduledTask(String taskName) {
        if (!IS_WINDOWS || taskName == null || taskName.isBlank()) {
            return;
        }
        try {
            Process process = cmdProcessBuilder("schtasks", "/End", "/TN", taskName.trim()).start();
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // task may not be running
        }
    }

    /**
     * 强制终止进程树。Windows 使用 taskkill /F /T，其他平台使用 destroyForcibly。
     */
    public static boolean forceKillProcessTree(long pid) {
        if (pid <= 0) {
            return false;
        }
        if (IS_WINDOWS) {
            try {
                Process process = new ProcessBuilder(
                    "taskkill", "/PID", String.valueOf(pid), "/F", "/T"
                ).redirectErrorStream(true).start();
                boolean finished = process.waitFor(8, TimeUnit.SECONDS);
                return finished && process.exitValue() == 0;
            } catch (Exception e) {
                return false;
            }
        }
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty()) {
            return false;
        }
        handle.get().destroyForcibly();
        return true;
    }
}
