package com.openclaw.vs.service;

import com.openclaw.vs.util.NodeVersionSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.function.Consumer;

/**
 * 通过 nvm（Windows 为 nvm-windows）安装并切换 Node 版本。
 */
@Slf4j
@Service
public class NvmNodeService {

    private static final int DEFAULT_TIMEOUT_SEC = 30;
    private static final int INSTALL_TIMEOUT_SEC = 600;

    private static class CommandResult {
        int exitCode;
        String stdout;
        String stderr;

        CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        String combinedOutput() {
            if (stderr == null || stderr.isBlank()) {
                return stdout != null ? stdout : "";
            }
            if (stdout == null || stdout.isBlank()) {
                return stderr;
            }
            return stdout + "\n" + stderr;
        }
    }

    public boolean isNvmAvailable() {
        CommandResult result = executeCommand("nvm version", DEFAULT_TIMEOUT_SEC);
        if (result.exitCode != 0) {
            return false;
        }
        String output = result.combinedOutput().toLowerCase();
        return !output.isBlank()
            && !output.contains("not recognized")
            && !output.contains("不是内部或外部命令")
            && !output.contains("command not found");
    }

    /**
     * 使用 nvm 安装（如需）并切换到满足 OpenClaw 要求的 Node 版本。
     *
     * @return 切换后的 node --version 输出；失败返回 null
     */
    public String installAndUse(Consumer<String> log) {
        if (!isNvmAvailable()) {
            return null;
        }

        log.accept("检测到 nvm，将通过 nvm 安装/切换 Node 版本");

        List<String> installed = listInstalledVersions(log);
        String existing = NodeVersionSupport.findBestAcceptable(installed);
        if (existing != null) {
            log.accept("nvm 中已有符合要求的版本: v" + existing);
            if (switchVersion(existing, log)) {
                return verifyNodeVersion(log);
            }
            return null;
        }

        String target = NodeVersionSupport.NVM_INSTALL_TARGET;
        log.accept("正在通过 nvm 下载并安装 Node " + target + "（可能需要几分钟）...");
        CommandResult install = executeCommand("nvm install " + target, INSTALL_TIMEOUT_SEC);
        appendCommandLog(log, install);

        if (install.exitCode != 0) {
            log.accept("nvm install 失败，尝试安装最低兼容版本 " + NodeVersionSupport.MIN_VERSION);
            install = executeCommand("nvm install " + NodeVersionSupport.MIN_VERSION, INSTALL_TIMEOUT_SEC);
            appendCommandLog(log, install);
            if (install.exitCode != 0) {
                return null;
            }
            target = NodeVersionSupport.MIN_VERSION;
        } else {
            String installedFromOutput = extractInstalledVersion(install.combinedOutput());
            if (installedFromOutput != null) {
                target = installedFromOutput;
            }
        }

        if (!switchVersion(target, log)) {
            return null;
        }
        return verifyNodeVersion(log);
    }

    private List<String> listInstalledVersions(Consumer<String> log) {
        CommandResult result = executeCommand("nvm list", DEFAULT_TIMEOUT_SEC);
        appendCommandLog(log, result);
        return NodeVersionSupport.parseNvmListVersions(result.combinedOutput());
    }

    private boolean switchVersion(String version, Consumer<String> log) {
        log.accept("正在切换 nvm 版本: nvm use " + version);
        CommandResult result = executeCommand("nvm use " + version, DEFAULT_TIMEOUT_SEC);
        appendCommandLog(log, result);
        return result.exitCode == 0;
    }

    private String verifyNodeVersion(Consumer<String> log) {
        CommandResult result = executeCommand("node --version", DEFAULT_TIMEOUT_SEC);
        if (result.exitCode == 0 && !result.stdout.isBlank()) {
            String version = result.stdout.trim();
            if (NodeVersionSupport.isAcceptable(version)) {
                log.accept("nvm 切换成功，当前 Node: " + version);
                return version;
            }
            log.accept("nvm 切换后版本仍不符合要求: " + version);
        }
        return null;
    }

    private String extractInstalledVersion(String output) {
        List<String> versions = NodeVersionSupport.parseNvmListVersions(output);
        if (versions.isEmpty()) {
            return null;
        }
        return versions.get(versions.size() - 1);
    }

    private void appendCommandLog(Consumer<String> log, CommandResult result) {
        String combined = result.combinedOutput();
        if (combined.isBlank()) {
            return;
        }
        for (String line : combined.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                log.accept("  " + trimmed);
            }
        }
    }

    private CommandResult executeCommand(String command, int timeoutSec) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread t1 = new Thread(() -> readStream(process.getInputStream(), stdout));
            Thread t2 = new Thread(() -> readStream(process.getErrorStream(), stderr));
            t1.start();
            t2.start();

            if (!process.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                t1.join(1000);
                t2.join(1000);
                return new CommandResult(-1, stdout.toString().trim(), "Command timed out");
            }
            t1.join(3000);
            t2.join(3000);
            return new CommandResult(process.exitValue(), stdout.toString().trim(), stderr.toString().trim());
        } catch (Exception e) {
            log.debug("执行命令失败: {} -> {}", command, e.getMessage());
            return new CommandResult(-1, "", e.getMessage());
        }
    }

    private void readStream(java.io.InputStream stream, StringBuilder target) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (target.length() > 0) {
                    target.append("\n");
                }
                target.append(line);
            }
        } catch (IOException ignored) {
        }
    }
}
