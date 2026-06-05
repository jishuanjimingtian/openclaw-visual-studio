package com.openclaw.vs.service;

import com.openclaw.vs.dto.EnvironmentCheckResult;
import com.openclaw.vs.dto.SystemInfo;
import com.openclaw.vs.util.NodeVersionSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class EnvironmentCheckService {

    /**
     * 在 Windows 上通过 cmd /c 执行命令，解决 Runtime.exec() 无法识别 .cmd 文件的问题。
     * 返回 CommandResult，包含退出码、stdout、stderr。
     */
    private static class CommandResult {
        int exitCode;
        String stdout;
        String stderr;
        
        CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
    
    private CommandResult executeCommand(String... cmdParts) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmdParts);
            pb.redirectErrorStream(false);
            Process process = pb.start();
            
            // 并行读取 stdout 和 stderr，防止进程阻塞
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (stdout.length() > 0) stdout.append("\n");
                        stdout.append(line);
                    }
                } catch (IOException ignored) {}
            });
            
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (stderr.length() > 0) stderr.append("\n");
                        stderr.append(line);
                    }
                } catch (IOException ignored) {}
            });
            
            stdoutThread.start();
            stderrThread.start();
            
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                stdoutThread.join(1000);
                stderrThread.join(1000);
                return new CommandResult(-1, stdout.toString().trim(), "Command timed out");
            }
            
            stdoutThread.join(3000);
            stderrThread.join(3000);
            
            return new CommandResult(process.exitValue(), stdout.toString().trim(), stderr.toString().trim());
        } catch (Exception e) {
            return new CommandResult(-1, "", e.getMessage());
        }
    }
    
    /**
     * 在 Windows 上通过 cmd /c 执行命令字符串。
     */
    private CommandResult executeCommandWin(String command) {
        return executeCommand("cmd", "/c", command);
    }
    
    /**
     * checkAll() - 执行所有环境检测，返回 List<EnvironmentCheckResult>
     * 检测项：Node.js(>=22.19)、Python(>=3.10)、Git、npm、磁盘>=2GB、内存>=4GB、操作系统
     */
    public List<EnvironmentCheckResult> checkAll() {
        return checkEnvironment();
    }

    public List<EnvironmentCheckResult> checkEnvironment() {
        List<EnvironmentCheckResult> results = new ArrayList<>();
        
        // 1. 检查 Node.js
        results.add(checkNodeJs());
        
        // 2. 检查 Python
        results.add(checkPython());
        
        // 3. 检查 Git
        results.add(checkGit());
        
        // 4. 检查 npm/pnpm/yarn
        results.add(checkNodePackageManager());
        
        // 5. 检查磁盘空间
        results.add(checkDiskSpace());
        
        // 6. 检查内存
        results.add(checkMemory());
        
        // 7. 检查操作系统版本
        results.add(checkOSVersion());
        
        return results;
    }
    
    private EnvironmentCheckResult checkNodeJs() {
        EnvironmentCheckResult result = new EnvironmentCheckResult();
        result.setCheckName("Node.js 版本");
        
        CommandResult cmdResult = executeCommandWin("node --version");
        
        if (cmdResult.exitCode == 0 && !cmdResult.stdout.isEmpty()) {
            String output = cmdResult.stdout.trim();
            if (output.startsWith("v") || output.matches("v?\\d+\\.\\d+.*")) {
                String version = output.startsWith("v") ? output.substring(1) : output;
                
                if (NodeVersionSupport.isAcceptable(output)) {
                    result.setStatus("pass");
                    result.setMessage("Node.js " + version + " 已安装，版本符合要求 (" + NodeVersionSupport.requirementLabel() + ")");
                } else {
                    result.setStatus("fail");
                    result.setMessage("Node.js " + version + " 版本过低，需要 " + NodeVersionSupport.requirementLabel());
                    result.setSuggestion(buildNodeFixSuggestion());
                }
            } else {
                result.setStatus("fail");
                result.setMessage("无法解析 Node.js 版本: " + output);
                result.setSuggestion("请重新安装 Node.js");
            }
        } else {
            result.setStatus("fail");
            result.setMessage("Node.js 未安装或无法执行");
            result.setSuggestion("请安装 Node.js " + NodeVersionSupport.requirementLabel() + " 或更高版本");
        }
        
        return result;
    }

    private String buildNodeFixSuggestion() {
        return "请点击「修复」：若已安装 nvm 将自动下载并切换 Node "
            + NodeVersionSupport.NVM_INSTALL_TARGET
            + "；否则下载官方安装包（" + NodeVersionSupport.requirementLabel() + "）";
    }
    
    private EnvironmentCheckResult checkPython() {
        EnvironmentCheckResult result = new EnvironmentCheckResult();
        result.setCheckName("Python 版本");
        
        // 尝试多个可能的 Python 命令
        String[] pythonCommands = {"python --version", "python3 --version", "py -3 --version", "py --version"};
        String foundVersion = null;
        String errorMessage = null;
        
        for (String cmd : pythonCommands) {
            CommandResult cmdResult = executeCommandWin(cmd);
            if (cmdResult.exitCode == 0 && !cmdResult.stdout.isEmpty()) {
                String output = cmdResult.stdout.trim();
                // Python 版本输出格式: "Python 3.10.0"
                if (output.startsWith("Python ")) {
                    foundVersion = output.substring(7);
                    break;
                }
            } else if (errorMessage == null && !cmdResult.stderr.isEmpty()) {
                errorMessage = cmdResult.stderr.trim();
            }
        }
        
        if (foundVersion != null) {
            String[] parts = foundVersion.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            
            if (major > 3 || (major == 3 && minor >= 10)) {
                result.setStatus("pass");
                result.setMessage("Python " + foundVersion + " 已安装，版本符合要求 (>=3.10)");
            } else {
                result.setStatus("fail");
                result.setMessage("Python " + foundVersion + " 版本过低，需要 >=3.10");
                result.setSuggestion("请升级 Python 到 3.10 或更高版本");
            }
        } else {
            result.setStatus("fail");
            result.setMessage("Python 未安装或无法执行");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                result.setMessage("Python 未安装或无法执行: " + errorMessage);
            }
            result.setSuggestion("请安装 Python 3.10 或更高版本");
        }
        
        return result;
    }
    
    private EnvironmentCheckResult checkGit() {
        EnvironmentCheckResult result = new EnvironmentCheckResult();
        result.setCheckName("Git 安装");
        
        CommandResult cmdResult = executeCommandWin("git --version");
        
        if (cmdResult.exitCode == 0 && !cmdResult.stdout.isEmpty()) {
            String output = cmdResult.stdout.trim();
            result.setStatus("pass");
            result.setMessage("Git 已安装: " + output);
        } else {
            result.setStatus("fail");
            result.setMessage("Git 未安装或无法执行");
            result.setSuggestion("请安装 Git 以支持源码克隆功能");
        }
        
        return result;
    }
    
    private EnvironmentCheckResult checkNodePackageManager() {
        EnvironmentCheckResult result = new EnvironmentCheckResult();
        result.setCheckName("Node 包管理器");
        
        StringBuilder message = new StringBuilder();
        List<String> availableManagers = new ArrayList<>();
        
        // 1. 检查 npm（通过 cmd /c 执行，解决 Windows 上 .cmd 文件识别问题）
        CommandResult npmResult = executeCommandWin("npm --version");
        if (npmResult.exitCode == 0 && !npmResult.stdout.isEmpty()) {
            String version = npmResult.stdout.trim();
            message.append("npm ").append(version).append(" ✓ ");
            availableManagers.add("npm");
        } else {
            // npm 检测失败，尝试通过 node 路径定位 npm.cmd
            CommandResult whereNode = executeCommandWin("where node");
            if (whereNode.exitCode == 0 && !whereNode.stdout.isEmpty()) {
                String nodePath = whereNode.stdout.trim().lines().findFirst().orElse("");
                if (!nodePath.isEmpty()) {
                    java.nio.file.Path nodeDir = Paths.get(nodePath).getParent();
                    if (nodeDir != null) {
                        java.nio.file.Path npmCmd = nodeDir.resolve("npm.cmd");
                        java.nio.file.Path npmExe = nodeDir.resolve("npm");
                        java.nio.file.Path npmToTry = Files.exists(npmCmd) ? npmCmd : (Files.exists(npmExe) ? npmExe : null);
                        if (npmToTry != null) {
                            CommandResult npmFullPath = executeCommand(npmToTry.toString(), "--version");
                            if (npmFullPath.exitCode == 0 && !npmFullPath.stdout.isEmpty()) {
                                String version = npmFullPath.stdout.trim();
                                message.append("npm ").append(version).append(" (通过 node 路径) ✓ ");
                                availableManagers.add("npm");
                            }
                        }
                    }
                }
            }
        }
        
        // 2. 检查 pnpm
        CommandResult pnpmResult = executeCommandWin("pnpm --version");
        if (pnpmResult.exitCode == 0 && !pnpmResult.stdout.isEmpty()) {
            String version = pnpmResult.stdout.trim();
            message.append("pnpm ").append(version).append(" ✓ ");
            availableManagers.add("pnpm");
        }
        
        // 3. 检查 yarn
        CommandResult yarnResult = executeCommandWin("yarn --version");
        if (yarnResult.exitCode == 0 && !yarnResult.stdout.isEmpty()) {
            String version = yarnResult.stdout.trim();
            message.append("yarn ").append(version).append(" ✓ ");
            availableManagers.add("yarn");
        }
        
        if (!availableManagers.isEmpty()) {
            result.setStatus("pass");
            result.setMessage("可用的包管理器: " + message.toString());
        } else {
            result.setStatus("warn");
            result.setMessage("未检测到 Node 包管理器 (npm/pnpm/yarn)");
            result.setSuggestion("建议安装 npm (Node.js 自带) 或 pnpm/yarn");
        }
        
        return result;
    }
    
    private EnvironmentCheckResult checkDiskSpace() {
        EnvironmentCheckResult result = new EnvironmentCheckResult();
        result.setCheckName("磁盘空间");
        
        try {
            File file = new File(".");
            FileStore store = Files.getFileStore(Paths.get(file.getAbsolutePath()));
            
            long freeBytes = store.getUsableSpace();
            long totalBytes = store.getTotalSpace();
            long freeGB = freeBytes / (1024 * 1024 * 1024);
            long totalGB = totalBytes / (1024 * 1024 * 1024);
            
            if (freeGB >= 2) {
                result.setStatus("pass");
                result.setMessage(String.format("磁盘空间充足: %d GB 可用 / %d GB 总量", freeGB, totalGB));
            } else {
                result.setStatus("warn");
                result.setMessage(String.format("磁盘空间紧张: %d GB 可用 / %d GB 总量 (需要至少 2 GB)", freeGB, totalGB));
                result.setSuggestion("请清理磁盘空间，确保至少有 2 GB 可用空间");
            }
        } catch (IOException e) {
            log.error("检查磁盘空间失败", e);
            result.setStatus("warn");
            result.setMessage("无法检查磁盘空间: " + e.getMessage());
            result.setSuggestion("请确保磁盘有足够空间 (至少 2 GB)");
        }
        
        return result;
    }
    
    private EnvironmentCheckResult checkMemory() {
        EnvironmentCheckResult result = new EnvironmentCheckResult();
        result.setCheckName("系统内存");
        
        try {
            com.sun.management.OperatingSystemMXBean osBean = 
                (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            
            long totalMemory = osBean.getTotalPhysicalMemorySize();
            long freeMemory = osBean.getFreePhysicalMemorySize();
            long totalGB = totalMemory / (1024 * 1024 * 1024);
            long freeGB = freeMemory / (1024 * 1024 * 1024);
            
            if (totalGB >= 4) {
                if (freeGB >= 1) {
                    result.setStatus("pass");
                    result.setMessage(String.format("内存充足: %d GB 可用 / %d GB 总量", freeGB, totalGB));
                } else {
                    result.setStatus("warn");
                    result.setMessage(String.format("内存紧张: %d GB 可用 / %d GB 总量", freeGB, totalGB));
                    result.setSuggestion("请关闭不必要的程序以释放内存");
                }
            } else {
                result.setStatus("fail");
                result.setMessage(String.format("内存不足: %d GB 总量 (需要至少 4 GB)", totalGB));
                result.setSuggestion("请升级系统内存到至少 4 GB");
            }
        } catch (Exception e) {
            log.error("检查内存失败", e);
            result.setStatus("warn");
            result.setMessage("无法检查内存信息: " + e.getMessage());
            result.setSuggestion("请确保系统至少有 4 GB 内存");
        }
        
        return result;
    }
    
    private EnvironmentCheckResult checkOSVersion() {
        EnvironmentCheckResult result = new EnvironmentCheckResult();
        result.setCheckName("操作系统");
        
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        
        result.setStatus("pass");
        result.setMessage(String.format("%s %s (%s)", osName, osVersion, osArch));
        
        // 检查是否为 Windows 10/11 或 macOS/Linux
        if (osName.toLowerCase().contains("windows")) {
            if (osVersion.contains("10") || osVersion.contains("11")) {
                result.setSuggestion("Windows 10/11 系统兼容性良好");
            } else {
                result.setStatus("warn");
                result.setSuggestion("建议升级到 Windows 10 或更高版本");
            }
        } else if (osName.toLowerCase().contains("mac")) {
            result.setSuggestion("macOS 系统兼容性良好");
        } else if (osName.toLowerCase().contains("linux")) {
            result.setSuggestion("Linux 系统兼容性良好");
        }
        
        return result;
    }
    
    public SystemInfo getSystemInfo() {
        SystemInfo info = new SystemInfo();
        
        // 操作系统信息
        info.setOs(System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
        
        // CPU 信息
        info.setCpu(System.getProperty("os.arch") + " " + Runtime.getRuntime().availableProcessors() + " cores");
        
        // 内存信息
        try {
            com.sun.management.OperatingSystemMXBean osBean = 
                (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            long totalMemory = osBean.getTotalPhysicalMemorySize();
            long freeMemory = osBean.getFreePhysicalMemorySize();
            long usedMemory = totalMemory - freeMemory;
            info.setMemory(String.format("%.1f GB / %.1f GB (%.1f%%)", 
                usedMemory / (1024.0 * 1024 * 1024),
                totalMemory / (1024.0 * 1024 * 1024),
                (usedMemory * 100.0) / totalMemory));
        } catch (Exception e) {
            info.setMemory("无法获取内存信息");
        }
        
        // 磁盘信息
        try {
            File file = new File(".");
            FileStore store = Files.getFileStore(Paths.get(file.getAbsolutePath()));
            long totalSpace = store.getTotalSpace();
            long usableSpace = store.getUsableSpace();
            long usedSpace = totalSpace - usableSpace;
            info.setDisk(String.format("%.1f GB / %.1f GB (%.1f%%)", 
                usedSpace / (1024.0 * 1024 * 1024),
                totalSpace / (1024.0 * 1024 * 1024),
                (usedSpace * 100.0) / totalSpace));
        } catch (Exception e) {
            info.setDisk("无法获取磁盘信息");
        }
        
        // Node.js 版本
        CommandResult nodeResult = executeCommandWin("node --version");
        if (nodeResult.exitCode == 0 && !nodeResult.stdout.isEmpty()) {
            info.setNodeVersion(nodeResult.stdout.trim());
        } else {
            info.setNodeVersion("未安装");
        }
        
        // Python 版本
        String[] pythonCommands = {"python --version", "python3 --version", "py -3 --version", "py --version"};
        String pythonVersion = null;
        for (String cmd : pythonCommands) {
            CommandResult cmdResult = executeCommandWin(cmd);
            if (cmdResult.exitCode == 0 && !cmdResult.stdout.isEmpty() && cmdResult.stdout.startsWith("Python ")) {
                pythonVersion = cmdResult.stdout.trim();
                break;
            }
        }
        info.setPythonVersion(pythonVersion != null ? pythonVersion : "未安装");
        
        // Git 版本
        CommandResult gitResult = executeCommandWin("git --version");
        if (gitResult.exitCode == 0 && !gitResult.stdout.isEmpty()) {
            info.setGitVersion(gitResult.stdout.trim());
        } else {
            info.setGitVersion("未安装");
        }
        
        return info;
    }
    
    /**
     * 获取可自动修复的检测项列表
     */
    public List<String> getFixableItems() {
        List<String> fixableItems = new ArrayList<>();
        fixableItems.add("Node.js 版本");
        fixableItems.add("Python 版本");
        fixableItems.add("Git 安装");
        fixableItems.add("Node 包管理器");
        return fixableItems;
    }
    
    /**
     * 为检测项提供修复建议
     */
    public String getFixSuggestion(String checkName) {
        switch (checkName) {
            case "Node.js 版本":
                return "已安装 nvm 时将自动下载并切换 Node " + NodeVersionSupport.NVM_INSTALL_TARGET
                    + "；否则下载官方安装包（" + NodeVersionSupport.requirementLabel() + "）";
            case "Python 版本":
                return "自动下载并安装 Python 3.10+ 版本";
            case "Git 安装":
                return "自动下载并安装 Git for Windows";
            case "Node 包管理器":
                return "自动安装 npm (Node.js 自带)";
            case "磁盘空间":
                return "提供磁盘清理建议和脚本";
            case "系统内存":
                return "提供内存优化建议";
            default:
                return "请手动修复此问题";
        }
    }
}