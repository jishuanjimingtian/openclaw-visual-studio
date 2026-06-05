package com.openclaw.vs.service;

import com.openclaw.vs.dto.OpenClawInstallDiscoveryDto;
import com.openclaw.vs.gateway.OpenClawGatewayConfigReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OpenClawInstallDiscoveryService {

    public OpenClawInstallDiscoveryDto discover() {
        OpenClawInstallDiscoveryDto dto = new OpenClawInstallDiscoveryDto();

        String commandPath = resolveOpenclawCommand();
        String resolvedPath = resolveWherePath(commandPath);
        dto.setCommandPath(commandPath);
        dto.setCommandResolvedPath(resolvedPath);

        Path configPath = resolveConfigPath();
        if (configPath != null) {
            dto.setConfigPath(configPath.toString());
        }

        if (commandPath != null) {
            dto.setVersion(readVersion(commandPath));
        }

        int gatewayPort = OpenClawGatewayConfigReader.readGatewayPort()
            .orElse(OpenClawGatewayConfigReader.DEFAULT_GATEWAY_PORT);
        dto.setGatewayPort(gatewayPort);

        String workDir = guessWorkDir(configPath);
        dto.setWorkDir(workDir);
        dto.setInstallMethod(guessInstallMethod(commandPath, resolvedPath, workDir));

        boolean hasCommand = commandPath != null;
        boolean hasConfig = configPath != null && Files.isRegularFile(configPath);
        dto.setInstalled(hasCommand || hasConfig);
        dto.setMessage(buildMessage(dto, hasCommand, hasConfig));

        return dto;
    }

    private String resolveOpenclawCommand() {
        String wherePath = resolveWherePath("openclaw");
        if (wherePath != null) {
            return "openclaw";
        }

        String npmGlobal = System.getenv("APPDATA");
        if (npmGlobal != null) {
            String npmCmd = npmGlobal + "\\npm\\openclaw.cmd";
            if (new File(npmCmd).exists()) {
                return npmCmd;
            }
        }

        if (canRunNpx()) {
            return "npx openclaw";
        }

        return null;
    }

    private String resolveWherePath(String command) {
        if (command == null || command.isBlank()) {
            return null;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "where", command);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor(3, TimeUnit.SECONDS);
            if (output.isEmpty() || output.contains("Could not find files")) {
                return null;
            }
            String[] paths = output.split("\\r?\\n");
            return paths[0].trim();
        } catch (Exception e) {
            log.debug("where {} 失败: {}", command, e.getMessage());
            return null;
        }
    }

    private boolean canRunNpx() {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "npx", "--version");
            Process process = pb.start();
            process.waitFor(3, TimeUnit.SECONDS);
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Path resolveConfigPath() {
        Path path = OpenClawGatewayConfigReader.defaultConfigPath();
        if (Files.isRegularFile(path)) {
            return path;
        }
        Path alt = path.getParent() != null ? path.getParent().resolve("openclaw.json") : null;
        if (alt != null && Files.isRegularFile(alt)) {
            return alt;
        }
        Path home = Path.of(System.getProperty("user.home"), ".openclaw");
        if (Files.isDirectory(home)) {
            return path;
        }
        return null;
    }

    private String readVersion(String commandPath) {
        try {
            ProcessBuilder pb;
            if ("npx openclaw".equals(commandPath)) {
                pb = new ProcessBuilder("cmd", "/c", "npx", "openclaw", "--version");
            } else {
                pb = new ProcessBuilder("cmd", "/c", commandPath, "--version");
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor(5, TimeUnit.SECONDS);
            if (!output.isEmpty() && process.exitValue() == 0) {
                return output.replace("openclaw", "").trim();
            }
        } catch (Exception e) {
            log.debug("读取 openclaw 版本失败: {}", e.getMessage());
        }
        return null;
    }

    private String guessWorkDir(Path configPath) {
        List<Path> candidates = new ArrayList<>();
        String home = System.getProperty("user.home");
        candidates.add(Path.of(home, "openclaw-workspace"));
        candidates.add(Path.of(System.getProperty("user.dir"), "openclaw-workspace"));

        for (Path candidate : candidates) {
            if (isOpenClawWorkspace(candidate)) {
                return candidate.toString();
            }
        }

        if (configPath != null) {
            Path parent = configPath.getParent();
            if (parent != null && parent.getFileName() != null
                && ".openclaw".equals(parent.getFileName().toString())) {
                return Path.of(home, "openclaw-workspace").toString();
            }
        }

        return Path.of(home, "openclaw-workspace").toString();
    }

    private boolean isOpenClawWorkspace(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        return Files.isRegularFile(dir.resolve("package.json"))
            || Files.isRegularFile(dir.resolve("openclaw.config.yml"));
    }

    private String guessInstallMethod(String commandPath, String resolvedPath, String workDir) {
        if (workDir != null) {
            Path workspace = Path.of(workDir);
            if (Files.isDirectory(workspace.resolve(".git"))
                && Files.isRegularFile(workspace.resolve("package.json"))) {
                return "github";
            }
        }
        if (resolvedPath != null && resolvedPath.toLowerCase().contains("\\npm\\")) {
            return "npm";
        }
        if (commandPath != null) {
            return "npm";
        }
        return "external";
    }

    private String buildMessage(OpenClawInstallDiscoveryDto dto, boolean hasCommand, boolean hasConfig) {
        if (!hasCommand && !hasConfig) {
            return "未在本机检测到 OpenClaw 命令或配置目录";
        }
        List<String> parts = new ArrayList<>();
        if (hasCommand) {
            parts.add("已找到 openclaw 命令"
                + (dto.getCommandResolvedPath() != null ? "（" + dto.getCommandResolvedPath() + "）" : ""));
        }
        if (hasConfig) {
            parts.add("已找到配置 " + dto.getConfigPath());
        }
        if (dto.getVersion() != null && !dto.getVersion().isBlank()) {
            parts.add("版本 " + dto.getVersion());
        }
        return String.join("；", parts);
    }
}
