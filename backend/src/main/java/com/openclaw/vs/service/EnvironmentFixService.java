package com.openclaw.vs.service;

import com.openclaw.vs.dto.EnvironmentCheckResult;
import com.openclaw.vs.dto.EnvironmentFixResult;
import com.openclaw.vs.dto.FixProgress;
import com.openclaw.vs.util.NodeVersionSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EnvironmentFixService {

    private static final String MSI_NODE_VERSION = "22.19.0";

    private final EnvironmentCheckService environmentCheckService;
    private final NvmNodeService nvmNodeService;
    private final ExecutorService fixExecutor = Executors.newSingleThreadExecutor();
    
    /** 修复进度映射: fixId -> FixProgress */
    private final ConcurrentHashMap<String, FixProgress> fixProgressMap = new ConcurrentHashMap<>();

    private static final int MAX_LOG_LINES = 3000;
    
    public EnvironmentFixService(EnvironmentCheckService environmentCheckService, NvmNodeService nvmNodeService) {
        this.environmentCheckService = environmentCheckService;
        this.nvmNodeService = nvmNodeService;
    }
    
    /**
     * 在 Windows 上通过 cmd /c 执行命令，返回 CommandResult。
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
    
    private CommandResult executeCommandWin(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
            pb.redirectErrorStream(false);
            Process process = pb.start();
            
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            
            Thread t1 = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (stdout.length() > 0) stdout.append("\n");
                        stdout.append(line);
                    }
                } catch (IOException ignored) {}
            });
            Thread t2 = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (stderr.length() > 0) stderr.append("\n");
                        stderr.append(line);
                    }
                } catch (IOException ignored) {}
            });
            t1.start(); t2.start();
            
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new CommandResult(-1, "", "Command timed out");
            }
            t1.join(3000); t2.join(3000);
            return new CommandResult(process.exitValue(), stdout.toString().trim(), stderr.toString().trim());
        } catch (Exception e) {
            return new CommandResult(-1, "", e.getMessage());
        }
    }
    
    /**
     * 验证 Python 是否可用，依次尝试多个命令。
     * 返回版本字符串（如 "3.10.0"），失败返回 null。
     */
    private String verifyPythonInstalled() {
        String[] cmds = {"python --version", "python3 --version", "py -3 --version", "py --version"};
        for (String cmd : cmds) {
            CommandResult r = executeCommandWin(cmd);
            if (r.exitCode == 0 && r.stdout.startsWith("Python ")) {
                return r.stdout.substring(7).trim();
            }
        }
        return null;
    }

    private Integer parseNodeMajorVersion(String versionOutput) {
        return NodeVersionSupport.parseVersionParts(versionOutput)[0];
    }

    private boolean isNodeVersionAcceptable(String versionOutput) {
        return NodeVersionSupport.isAcceptable(versionOutput);
    }

    /** 从 PATH 与常见安装目录探测 Node 版本，优先返回最高主版本。 */
    private String detectBestNodeVersion() {
        String[] cmds = {
            "node --version",
            "\"C:\\Program Files\\nodejs\\node.exe\" --version",
            "\"C:\\Program Files (x86)\\nodejs\\node.exe\" --version"
        };
        String bestVersion = null;
        int bestMajor = -1;
        for (String cmd : cmds) {
            CommandResult result = executeCommandWin(cmd);
            if (result.exitCode != 0 || result.stdout.isEmpty()) {
                continue;
            }
            String version = result.stdout.trim();
            Integer major = parseNodeMajorVersion(version);
            if (major != null && major > bestMajor) {
                bestMajor = major;
                bestVersion = version;
            }
        }
        return bestVersion;
    }
    
    /**
     * 启动环境修复
     */
    public String startFix(List<String> fixItems) {
        String fixId = UUID.randomUUID().toString().substring(0, 8);
        
        FixProgress progress = new FixProgress();
        progress.setFixId(fixId);
        progress.setStage("queued");
        progress.setPercentage(0);
        progress.setCurrentAction("准备修复...");
        fixProgressMap.put(fixId, progress);
        
        addLog(fixId, "=" .repeat(50));
        addLog(fixId, "环境修复启动");
        addLog(fixId, "修复 ID: " + fixId);
        addLog(fixId, "=" .repeat(50));
        
        // 异步执行修复
        fixExecutor.submit(() -> {
            try {
                executeFix(fixId, fixItems);
            } catch (Exception e) {
                log.error("环境修复失败", e);
                addLog(fixId, "修复失败: " + e.getMessage());
                FixProgress p = fixProgressMap.get(fixId);
                if (p != null) {
                    p.setStage("failed");
                    p.setCurrentAction("修复失败: " + e.getMessage());
                }
            }
        });
        
        return fixId;
    }
    
    private void executeFix(String fixId, List<String> fixItems) {
        FixProgress progress = fixProgressMap.get(fixId);
        if (progress == null) return;
        
        progress.setStage("running");
        progress.setCurrentAction("正在执行环境检测...");
        
        // 获取当前环境检测结果
        List<EnvironmentCheckResult> checkResults = environmentCheckService.checkEnvironment();
        
        // 确定要修复的项目
        List<String> itemsToFix = new ArrayList<>();
        if (fixItems == null || fixItems.isEmpty()) {
            // 修复所有可修复的失败项
            for (EnvironmentCheckResult check : checkResults) {
                if ("fail".equals(check.getStatus()) && isFixable(check.getCheckName())) {
                    itemsToFix.add(check.getCheckName());
                }
            }
        } else {
            // 修复指定项
            itemsToFix.addAll(fixItems);
        }
        
        progress.setTotalItems(itemsToFix.size());
        progress.setCurrentAction("准备修复 " + itemsToFix.size() + " 个项目...");
        addLog(fixId, "需要修复的项目: " + itemsToFix);
        
        if (itemsToFix.isEmpty()) {
            progress.setStage("completed");
            progress.setPercentage(100);
            progress.setCurrentAction("无需修复");
            addLog(fixId, "✓ 所有检测项均已通过，无需修复");
            return;
        }
        
        // 逐个修复
        int successCount = 0;
        int failCount = 0;
        List<EnvironmentFixResult> results = new ArrayList<>();
        
        for (int i = 0; i < itemsToFix.size(); i++) {
            String itemName = itemsToFix.get(i);
            int currentItem = i + 1;
            
            progress.setPercentage((currentItem - 1) * 100 / itemsToFix.size());
            progress.setCurrentAction("正在修复 " + itemName + " (" + currentItem + "/" + itemsToFix.size() + ")");
            addLog(fixId, "");
            addLog(fixId, "[修复 " + currentItem + "/" + itemsToFix.size() + "] " + itemName);
            addLog(fixId, "-".repeat(40));
            
            EnvironmentFixResult fixResult = fixItem(fixId, itemName);
            results.add(fixResult);
            
            if ("success".equals(fixResult.getStatus())) {
                successCount++;
            } else if ("fail".equals(fixResult.getStatus())) {
                failCount++;
            }
            
            progress.setCompletedItems(currentItem);
            progress.setSuccessCount(successCount);
            progress.setFailCount(failCount);
            progress.setResults(results);
        }
        
        // 修复完成，重新检测
        progress.setCurrentAction("修复完成，正在重新检测环境...");
        addLog(fixId, "");
        addLog(fixId, "修复完成，重新检测环境...");
        
        List<EnvironmentCheckResult> finalCheckResults = environmentCheckService.checkEnvironment();
        boolean allPassed = true;
        
        for (EnvironmentCheckResult check : finalCheckResults) {
            if ("fail".equals(check.getStatus())) {
                allPassed = false;
                break;
            }
        }
        
        progress.setStage("completed");
        progress.setPercentage(100);
        
        if (allPassed) {
            progress.setCurrentAction("所有检测项已通过");
            addLog(fixId, "✓ 环境修复完成，所有检测项已通过");
        } else {
            progress.setCurrentAction("部分项目修复失败");
            addLog(fixId, "⚠ 环境修复完成，但仍有未通过项");
        }
        
        addLog(fixId, "=" .repeat(50));
        addLog(fixId, "修复统计:");
        addLog(fixId, "  总修复项: " + itemsToFix.size());
        addLog(fixId, "  成功: " + successCount);
        addLog(fixId, "  失败: " + failCount);
        addLog(fixId, "  跳过: " + (itemsToFix.size() - successCount - failCount));
        addLog(fixId, "=" .repeat(50));
    }
    
    private EnvironmentFixResult fixItem(String fixId, String itemName) {
        EnvironmentFixResult.EnvironmentFixResultBuilder resultBuilder = EnvironmentFixResult.builder()
            .itemName(itemName);
        
        try {
            switch (itemName) {
                case "Node.js 版本":
                    return fixNodeJs(fixId, resultBuilder);
                case "Python 版本":
                    return fixPython(fixId, resultBuilder);
                case "Git 安装":
                    return fixGit(fixId, resultBuilder);
                case "Node 包管理器":
                    return fixNodePackageManager(fixId, resultBuilder);
                case "磁盘空间":
                    return fixDiskSpace(fixId, resultBuilder);
                case "系统内存":
                    return fixMemory(fixId, resultBuilder);
                default:
                    resultBuilder.status("skipped")
                        .detail("不支持自动修复此项目")
                        .afterFixStatus("fail")
                        .afterFixMessage("需要手动修复");
                    return resultBuilder.build();
            }
        } catch (Exception e) {
            log.error("修复 {} 失败", itemName, e);
            resultBuilder.status("fail")
                .detail("修复失败: " + e.getMessage())
                .afterFixStatus("fail")
                .afterFixMessage("修复失败");
            return resultBuilder.build();
        }
    }
    
    private EnvironmentFixResult fixNodeJs(String fixId, EnvironmentFixResult.EnvironmentFixResultBuilder builder) {
        addLog(fixId, "开始修复 Node.js...");
        String requirement = NodeVersionSupport.requirementLabel();
        
        String currentVersion = detectBestNodeVersion();
        if (currentVersion != null && isNodeVersionAcceptable(currentVersion)) {
            addLog(fixId, "Node.js 版本已符合要求: " + currentVersion);
            builder.status("skipped")
                .detail("Node.js 版本已符合要求: " + currentVersion)
                .afterFixStatus("pass")
                .afterFixMessage("Node.js " + currentVersion + " 已满足 " + requirement);
            return builder.build();
        }

        if (currentVersion != null) {
            addLog(fixId, "当前 Node.js " + currentVersion + " 版本过低（需要 " + requirement + "）");
        } else {
            addLog(fixId, "未检测到符合要求的 Node.js");
        }

        if (nvmNodeService.isNvmAvailable()) {
            addLog(fixId, "优先使用 nvm 安装/切换 Node " + NodeVersionSupport.NVM_INSTALL_TARGET + "...");
            String nvmVersion = nvmNodeService.installAndUse(line -> addLog(fixId, line));
            if (nvmVersion != null && isNodeVersionAcceptable(nvmVersion)) {
                builder.status("success")
                    .detail("通过 nvm 切换 Node.js " + nvmVersion)
                    .afterFixStatus("pass")
                    .afterFixMessage("Node.js " + nvmVersion + " 已满足 " + requirement);
                return builder.build();
            }
            addLog(fixId, "nvm 修复未成功，将尝试官方安装包...");
        } else {
            addLog(fixId, "未检测到 nvm，将下载官方 Node.js 安装包...");
        }

        return installNodeViaMsi(fixId, builder, requirement);
    }

    private EnvironmentFixResult installNodeViaMsi(
        String fixId,
        EnvironmentFixResult.EnvironmentFixResultBuilder builder,
        String requirement
    ) {
        addLog(fixId, "正在下载 Node.js " + MSI_NODE_VERSION + " 安装包...");
        String downloadUrl = "https://nodejs.org/dist/v" + MSI_NODE_VERSION + "/node-v" + MSI_NODE_VERSION + "-x64.msi";
        String tempDir = System.getProperty("java.io.tmpdir");
        String installerPath = tempDir + "node-v" + MSI_NODE_VERSION + "-x64.msi";
        
        try {
            downloadFile(downloadUrl, installerPath);
            addLog(fixId, "下载完成: " + installerPath);
            
            addLog(fixId, "正在安装 Node.js (静默模式)...");
            Process installProcess = Runtime.getRuntime().exec(new String[]{
                "msiexec", "/i", installerPath, "/quiet", "/norestart"
            });
            installProcess.waitFor(120, TimeUnit.SECONDS);
            
            if (installProcess.exitValue() == 0) {
                addLog(fixId, "Node.js 安装程序执行完成");
                
                Thread.sleep(5000);
                String verifiedVersion = detectBestNodeVersion();
                
                if (verifiedVersion != null && isNodeVersionAcceptable(verifiedVersion)) {
                    addLog(fixId, "验证通过: " + verifiedVersion);
                    builder.status("success")
                        .detail("成功安装/升级 Node.js " + verifiedVersion)
                        .afterFixStatus("pass")
                        .afterFixMessage("Node.js " + verifiedVersion + " 已满足 " + requirement);
                } else {
                    String detail = verifiedVersion != null
                        ? "安装完成但当前仍检测到 " + verifiedVersion + "，PATH 可能仍指向旧版本（若使用 nvm 请手动 nvm use）"
                        : "Node.js 安装完成，但验证失败";
                    addLog(fixId, "验证失败: " + detail);
                    builder.status("fail")
                        .detail(detail)
                        .afterFixStatus("fail")
                        .afterFixMessage("请重启本工具，或使用 nvm use 切换到 " + requirement);
                }
            } else {
                addLog(fixId, "Node.js 安装失败，退出码: " + installProcess.exitValue());
                builder.status("fail")
                    .detail("Node.js 安装失败，退出码: " + installProcess.exitValue())
                    .afterFixStatus("fail")
                    .afterFixMessage("Node.js 安装失败，请从 https://nodejs.org 手动安装 " + requirement);
            }
        } catch (Exception e) {
            builder.status("fail")
                .detail("Node.js 安装异常: " + e.getMessage())
                .afterFixStatus("fail")
                .afterFixMessage("Node.js 安装异常");
        }
        
        return builder.build();
    }
    
    private EnvironmentFixResult fixPython(String fixId, EnvironmentFixResult.EnvironmentFixResultBuilder builder) {
        addLog(fixId, "开始修复 Python...");
        
        // 检查是否已安装
        String existingVersion = verifyPythonInstalled();
        if (existingVersion != null) {
            addLog(fixId, "Python 已安装: " + existingVersion);
            builder.status("skipped")
                .detail("Python 已安装: " + existingVersion)
                .afterFixStatus("pass")
                .afterFixMessage("Python " + existingVersion + " 已安装");
            return builder.build();
        }
        
        // 下载 Python 安装包
        addLog(fixId, "正在下载 Python 3.10+ 安装包...");
        String downloadUrl = "https://www.python.org/ftp/python/3.10.0/python-3.10.0-amd64.exe";
        String tempDir = System.getProperty("java.io.tmpdir");
        String installerPath = tempDir + "python-3.10.0-amd64.exe";
        
        try {
            downloadFile(downloadUrl, installerPath);
            addLog(fixId, "下载完成: " + installerPath);
            
            // 静默安装并添加到 PATH
            addLog(fixId, "正在安装 Python (静默模式)...");
            Process installProcess = Runtime.getRuntime().exec(new String[]{
                installerPath, "/quiet", "InstallAllUsers=1", "PrependPath=1", "Include_test=0"
            });
            installProcess.waitFor(120, TimeUnit.SECONDS);
            
            if (installProcess.exitValue() == 0) {
                addLog(fixId, "Python 安装成功");
                
                // 等待系统更新 PATH
                addLog(fixId, "等待系统更新 PATH...");
                Thread.sleep(8000);
                
                // 验证安装
                addLog(fixId, "验证 Python 安装...");
                String installedVersion = verifyPythonInstalled();
                if (installedVersion != null) {
                    addLog(fixId, "验证通过: " + installedVersion);
                    builder.status("success")
                        .detail("成功安装 Python " + installedVersion)
                        .afterFixStatus("pass")
                        .afterFixMessage("Python " + installedVersion + " 已成功安装");
                } else {
                    addLog(fixId, "验证失败: Python 安装完成但无法在 PATH 中找到");
                    builder.status("fail")
                        .detail("Python 安装完成，但验证失败（无法在 PATH 中找到）")
                        .afterFixStatus("fail")
                        .afterFixMessage("Python 安装失败，请手动检查安装");
                }
            } else {
                addLog(fixId, "Python 安装失败，退出码: " + installProcess.exitValue());
                builder.status("fail")
                    .detail("Python 安装失败，退出码: " + installProcess.exitValue())
                    .afterFixStatus("fail")
                    .afterFixMessage("Python 安装失败");
            }
        } catch (Exception e) {
            addLog(fixId, "Python 安装异常: " + e.getMessage());
            builder.status("fail")
                .detail("Python 安装异常: " + e.getMessage())
                .afterFixStatus("fail")
                .afterFixMessage("Python 安装异常");
        }
        
        return builder.build();
    }
    
    private EnvironmentFixResult fixGit(String fixId, EnvironmentFixResult.EnvironmentFixResultBuilder builder) {
        addLog(fixId, "开始修复 Git...");
        
        // 检查是否已安装
        CommandResult checkResult = executeCommandWin("git --version");
        if (checkResult.exitCode == 0 && !checkResult.stdout.isEmpty()) {
            String version = checkResult.stdout.trim();
            addLog(fixId, "Git 已安装: " + version);
            builder.status("skipped")
                .detail("Git 已安装: " + version)
                .afterFixStatus("pass")
                .afterFixMessage("Git " + version + " 已安装");
            return builder.build();
        }
        
        // 下载 Git for Windows
        addLog(fixId, "正在下载 Git for Windows...");
        String downloadUrl = "https://github.com/git-for-windows/git/releases/download/v2.45.0.windows.1/Git-2.45.0-64-bit.exe";
        String tempDir = System.getProperty("java.io.tmpdir");
        String installerPath = tempDir + "Git-2.45.0-64-bit.exe";
        
        try {
            downloadFile(downloadUrl, installerPath);
            addLog(fixId, "下载完成: " + installerPath);
            
            // 静默安装
            addLog(fixId, "正在安装 Git (静默模式)...");
            Process installProcess = Runtime.getRuntime().exec(new String[]{
                installerPath, "/VERYSILENT", "/NORESTART", "/NOCANCEL", "/SP-", "/CLOSEAPPLICATIONS", "/RESTARTAPPLICATIONS"
            });
            installProcess.waitFor(120, TimeUnit.SECONDS);
            
            if (installProcess.exitValue() == 0) {
                addLog(fixId, "Git 安装成功");
                
                // 验证安装
                Thread.sleep(8000); // 等待系统更新 PATH
                CommandResult verifyResult = executeCommandWin("git --version");
                
                if (verifyResult.exitCode == 0 && !verifyResult.stdout.isEmpty()) {
                    String version = verifyResult.stdout.trim();
                    addLog(fixId, "验证通过: " + version);
                    builder.status("success")
                        .detail("成功安装 Git " + version)
                        .afterFixStatus("pass")
                        .afterFixMessage("Git " + version + " 已成功安装");
                } else {
                    addLog(fixId, "验证失败: 退出码=" + verifyResult.exitCode + ", stderr=" + verifyResult.stderr);
                    builder.status("fail")
                        .detail("Git 安装完成，但验证失败（退出码=" + verifyResult.exitCode + "）")
                        .afterFixStatus("fail")
                        .afterFixMessage("Git 安装后验证失败，请手动检查");
                }
            } else {
                addLog(fixId, "Git 安装失败，退出码: " + installProcess.exitValue());
                builder.status("fail")
                    .detail("Git 安装失败，退出码: " + installProcess.exitValue())
                    .afterFixStatus("fail")
                    .afterFixMessage("Git 安装失败");
            }
        } catch (Exception e) {
            builder.status("fail")
                .detail("Git 安装异常: " + e.getMessage())
                .afterFixStatus("fail")
                .afterFixMessage("Git 安装异常");
        }
        
        return builder.build();
    }
    
    private EnvironmentFixResult fixNodePackageManager(String fixId, EnvironmentFixResult.EnvironmentFixResultBuilder builder) {
        addLog(fixId, "开始修复 Node 包管理器...");
        
        // 1. 检查 Node.js 是否已安装
        CommandResult nodeResult = executeCommandWin("node --version");
        if (nodeResult.exitCode != 0) {
            builder.status("skipped")
                .detail("Node.js 未安装，无法修复 npm")
                .afterFixStatus("fail")
                .afterFixMessage("请先安装 Node.js");
            return builder.build();
        }
        
        // 2. 检查 npm 是否已安装
        CommandResult npmResult = executeCommandWin("npm --version");
        if (npmResult.exitCode == 0 && !npmResult.stdout.isEmpty()) {
            String version = npmResult.stdout.trim();
            addLog(fixId, "npm 已安装: " + version);
            builder.status("skipped")
                .detail("npm 已安装: " + version)
                .afterFixStatus("pass")
                .afterFixMessage("npm " + version + " 已安装");
            return builder.build();
        }
        
        // 3. 尝试通过 node 路径定位 npm.cmd
        addLog(fixId, "npm 未在 PATH 中找到，尝试通过 node 路径定位 npm.cmd...");
        CommandResult whereNode = executeCommandWin("where node");
        if (whereNode.exitCode == 0 && !whereNode.stdout.isEmpty()) {
            String nodePath = whereNode.stdout.trim().lines().findFirst().orElse("");
            if (!nodePath.isEmpty()) {
                Path nodeDir = Paths.get(nodePath).getParent();
                if (nodeDir != null) {
                    Path npmCmd = nodeDir.resolve("npm.cmd");
                    Path npmExe = nodeDir.resolve("npm");
                    Path npmToTry = Files.exists(npmCmd) ? npmCmd : (Files.exists(npmExe) ? npmExe : null);
                    
                    if (npmToTry != null) {
                        addLog(fixId, "找到 npm 可执行文件: " + npmToTry);
                        try {
                            ProcessBuilder pb = new ProcessBuilder(npmToTry.toString(), "--version");
                            Process p = pb.start();
                            if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0) {
                                String version = new String(p.getInputStream().readAllBytes()).trim();
                                addLog(fixId, "npm 版本: " + version);
                                
                                // 尝试将 node 目录添加到用户 PATH（通过注册表）
                                addLog(fixId, "尝试将 node 目录添加到用户 PATH...");
                                String nodeDirStr = nodeDir.toString();
                                try {
                                    Process regProcess = Runtime.getRuntime().exec(new String[]{
                                        "reg", "add", "HKCU\\Environment", "/v", "Path", "/t", "REG_EXPAND_SZ", 
                                        "/d", "%PATH%;" + nodeDirStr, "/f"
                                    });
                                    regProcess.waitFor(10, TimeUnit.SECONDS);
                                    addLog(fixId, "已更新用户环境变量（需要重启终端或系统生效）");
                                } catch (Exception regEx) {
                                    addLog(fixId, "更新环境变量失败: " + regEx.getMessage());
                                }
                                
                                builder.status("success")
                                    .detail("找到 npm 在 " + nodeDirStr + "，已尝试更新 PATH（需要重启终端）")
                                    .afterFixStatus("warn")
                                    .afterFixMessage("npm 已找到但不在 PATH 中，请重启终端或手动添加 " + nodeDirStr + " 到 PATH");
                                return builder.build();
                            }
                        } catch (Exception e) {
                            addLog(fixId, "执行 npm --version 失败: " + e.getMessage());
                        }
                    }
                }
            }
        }
        
        // 4. 如果找不到，提供手动修复建议
        addLog(fixId, "无法自动修复 npm，提供手动修复建议");
        String manualFix = """
            npm 修复建议：
            1. 检查 Node.js 安装目录（通常在 C:\\Program Files\\nodejs）是否有 npm.cmd
            2. 如果存在 npm.cmd，请将该目录添加到系统 PATH：
               - 右键点击"此电脑" → 属性 → 高级系统设置 → 环境变量
               - 在"用户变量"或"系统变量"中找到 Path，点击编辑
               - 添加 Node.js 安装目录（如 C:\\Program Files\\nodejs）
               - 点击确定，重启所有终端窗口
            3. 如果不存在 npm.cmd，请重新安装 Node.js（选择"自动安装必要工具"选项）
            4. 或者安装 pnpm/yarn 作为替代包管理器
            """;
        
        builder.status("fail")
            .detail(manualFix)
            .afterFixStatus("fail")
            .afterFixMessage("无法自动修复 npm，需要手动操作");
        
        return builder.build();
    }
    
    private EnvironmentFixResult fixDiskSpace(String fixId, EnvironmentFixResult.EnvironmentFixResultBuilder builder) {
        addLog(fixId, "开始处理磁盘空间问题...");
        
        try {
            // 提供清理建议
            String tempDir = System.getProperty("java.io.tmpdir");
            String userTemp = System.getenv("TEMP");
            String windowsTemp = "C:\\Windows\\Temp";
            
            StringBuilder detail = new StringBuilder();
            detail.append("磁盘清理建议:\n");
            detail.append("1. 清理临时文件:\n");
            detail.append("   - ").append(tempDir).append("\n");
            detail.append("   - ").append(userTemp).append("\n");
            detail.append("   - ").append(windowsTemp).append("\n");
            detail.append("2. 清理下载文件夹\n");
            detail.append("3. 清理回收站\n");
            detail.append("4. 卸载不需要的程序\n");
            
            addLog(fixId, "提供磁盘清理建议");
            
            // 创建清理脚本
            String scriptContent = String.format("""
                @echo off
                echo 正在清理临时文件...
                del /q /f /s "%s\\*.*"
                del /q /f /s "%s\\*.*"
                del /q /f /s "%s\\*.*"
                echo 清理完成！
                pause
                """, tempDir, userTemp, windowsTemp);
            
            String scriptPath = System.getProperty("java.io.tmpdir") + "cleanup_disk.bat";
            Files.write(Paths.get(scriptPath), scriptContent.getBytes());
            
            detail.append("\n已生成清理脚本: ").append(scriptPath);
            detail.append("\n请以管理员身份运行此脚本");
            
            builder.status("success")
                .detail(detail.toString())
                .afterFixStatus("warn")
                .afterFixMessage("已提供磁盘清理建议和脚本");
            
        } catch (Exception e) {
            builder.status("fail")
                .detail("处理磁盘空间失败: " + e.getMessage())
                .afterFixStatus("fail")
                .afterFixMessage("处理失败");
        }
        
        return builder.build();
    }
    
    private EnvironmentFixResult fixMemory(String fixId, EnvironmentFixResult.EnvironmentFixResultBuilder builder) {
        addLog(fixId, "开始处理内存问题...");
        
        try {
            StringBuilder detail = new StringBuilder();
            detail.append("内存优化建议:\n");
            detail.append("1. 关闭不必要的程序\n");
            detail.append("2. 减少浏览器标签页\n");
            detail.append("3. 调整虚拟内存设置\n");
            detail.append("4. 禁用启动项:\n");
            detail.append("   - 按 Win+R 输入 msconfig\n");
            detail.append("   - 在'启动'标签页禁用不需要的程序\n");
            detail.append("5. 检查内存泄漏程序\n");
            detail.append("6. 考虑升级物理内存\n");
            
            addLog(fixId, "提供内存优化建议");
            
            builder.status("success")
                .detail(detail.toString())
                .afterFixStatus("warn")
                .afterFixMessage("已提供内存优化建议");
            
        } catch (Exception e) {
            builder.status("fail")
                .detail("处理内存问题失败: " + e.getMessage())
                .afterFixStatus("fail")
                .afterFixMessage("处理失败");
        }
        
        return builder.build();
    }
    
    private boolean isFixable(String checkName) {
        List<String> fixableItems = environmentCheckService.getFixableItems();
        return fixableItems.contains(checkName);
    }
    
    private void downloadFile(String fileUrl, String savePath) throws IOException {
        URL url = new URL(fileUrl);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        httpConn.setRequestMethod("GET");
        httpConn.setConnectTimeout(30000);
        httpConn.setReadTimeout(30000);
        
        try (InputStream in = httpConn.getInputStream();
             FileOutputStream out = new FileOutputStream(savePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }
    
    /**
     * 获取修复进度；logOffset 指定后仅返回该索引之后的新增日志行
     */
    public FixProgress getFixProgress(String fixId, int logOffset) {
        FixProgress progress = fixProgressMap.get(fixId);
        if (progress == null) {
            FixProgress notFound = new FixProgress();
            notFound.setFixId(fixId);
            notFound.setStage("not_found");
            notFound.setCurrentAction("修复任务不存在: " + fixId);
            return notFound;
        }
        return snapshotFixProgress(progress, logOffset);
    }

    private FixProgress snapshotFixProgress(FixProgress source, int logOffset) {
        FixProgress snap = new FixProgress();
        snap.setFixId(source.getFixId());
        snap.setStage(source.getStage());
        snap.setPercentage(source.getPercentage());
        snap.setTotalItems(source.getTotalItems());
        snap.setCompletedItems(source.getCompletedItems());
        snap.setSuccessCount(source.getSuccessCount());
        snap.setFailCount(source.getFailCount());
        snap.setCurrentAction(source.getCurrentAction());
        snap.setResults(source.getResults());
        List<String> allLogs;
        synchronized (source) {
            allLogs = source.getLogs();
        }
        int total = allLogs.size();
        snap.setLogTotal(total);
        int safeOffset = Math.max(0, Math.min(logOffset, total));
        if (safeOffset >= total) {
            snap.setLogs(new ArrayList<>());
        } else {
            snap.setLogs(new ArrayList<>(allLogs.subList(safeOffset, total)));
        }
        return snap;
    }
    
    /**
     * 获取修复结果
     */
    public FixProgress getFixResults(String fixId) {
        return getFixProgress(fixId, 0);
    }
    
    private void addLog(String fixId, String logLine) {
        FixProgress progress = fixProgressMap.get(fixId);
        if (progress != null) {
            synchronized (progress) {
                progress.getLogs().add("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + logLine);
                while (progress.getLogs().size() > MAX_LOG_LINES) {
                    progress.getLogs().remove(0);
                }
            }
        }
    }
}
