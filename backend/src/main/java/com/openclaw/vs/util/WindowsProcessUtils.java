package com.openclaw.vs.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
}
