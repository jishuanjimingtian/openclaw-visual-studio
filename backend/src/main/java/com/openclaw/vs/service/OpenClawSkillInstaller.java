package com.openclaw.vs.service;

import com.openclaw.vs.config.SkillMarketProperties;
import com.openclaw.vs.dto.SkillInstallRequest;
import com.openclaw.vs.dto.SkillInstallResultDto;
import com.openclaw.vs.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenClawSkillInstaller {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private final SkillMarketProperties marketProperties;
    private final OpenClawWorkspaceConfigService workspaceConfigService;

    public SkillInstallResultDto install(SkillInstallRequest request) {
        if (request.getSlug() == null || request.getSlug().isBlank()) {
            throw new BadRequestException("Skill slug 不能为空");
        }
        String slug = request.getSlug().trim();
        String scope = normalizeScope(request.getScope());
        List<String> paths = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        OpenClawWorkspaceConfigService.WorkspaceResolution workspaceResolution = null;

        if ("workspace".equals(scope) || "both".equals(scope)) {
            workspaceResolution = ensureWorkspaceForInstall();
            tryInstall(request, slug, false, paths, errors);
        }
        if ("global".equals(scope) || "both".equals(scope)) {
            tryInstall(request, slug, true, paths, errors);
        }

        if (paths.isEmpty()) {
            String detail = errors.isEmpty() ? "未知错误" : String.join("; ", errors);
            return SkillInstallResultDto.builder()
                .success(false)
                .message("安装到 OpenClaw 失败: " + detail)
                .openclawReady(false)
                .build();
        }

        String primary = paths.get(0);
        boolean ready = paths.stream().anyMatch(p -> hasSkillMd(Path.of(p)));
        String message = ready
            ? "已安装到 OpenClaw，新会话将自动加载该 Skill"
            : "已写入目录，但未找到 SKILL.md，请检查安装结果";
        if (workspaceResolution != null && workspaceResolution.autoConfigured()) {
            message += "；已自动写入 openclaw.json 工作区（"
                + workspaceResolution.workspacePath()
                + "）";
        }

        SkillInstallResultDto.SkillInstallResultDtoBuilder result = SkillInstallResultDto.builder()
            .success(true)
            .message(message)
            .installPath(primary)
            .installPaths(paths)
            .openclawReady(ready)
            .workspaceAutoConfigured(workspaceResolution != null && workspaceResolution.autoConfigured());
        if (workspaceResolution != null) {
            result.configPath(workspaceResolution.configPath().toString());
            result.workspacePath(workspaceResolution.workspacePath().toString());
        }
        return result.build();
    }

    public void uninstallFromDisk(String slug, String installPath) {
        if (slug == null || slug.isBlank()) {
            return;
        }
        List<Path> targets = new ArrayList<>();
        if (installPath != null && !installPath.isBlank()) {
            targets.add(Path.of(installPath));
        }
        targets.add(globalSkillDir(slug));
        resolveWorkspaceSkillsDir(false).map(dir -> dir.resolve(slug)).ifPresent(targets::add);

        for (Path target : targets) {
            if (Files.isDirectory(target)) {
                try {
                    deleteRecursive(target);
                    log.info("Removed skill directory: {}", target);
                } catch (Exception e) {
                    log.warn("Failed to remove {}: {}", target, e.getMessage());
                }
            }
        }
    }

    private void tryInstall(
        SkillInstallRequest request,
        String slug,
        boolean global,
        List<String> paths,
        List<String> errors
    ) {
        String target = resolveTargetDir(slug, global).toString();
        if (cliInstall(request, slug, global, errors)) {
            if (Files.isDirectory(Path.of(target))) {
                paths.add(target);
                return;
            }
        }
        try {
            manualInstall(request, slug, global);
            if (Files.isDirectory(Path.of(target)) && hasSkillMd(Path.of(target))) {
                paths.add(target);
            } else {
                errors.add((global ? "全局" : "工作区") + "目录未生成有效 Skill");
            }
        } catch (Exception e) {
            errors.add((global ? "全局" : "工作区") + ": " + e.getMessage());
        }
    }

    private boolean cliInstall(
        SkillInstallRequest request,
        String slug,
        boolean global,
        List<String> errors
    ) {
        List<String> cmd = new ArrayList<>();
        cmd.add(resolveOpenClawExecutable());
        cmd.add("skills");
        cmd.add("install");
        cmd.add(buildInstallSpec(request, slug));
        if (global) {
            cmd.add("--global");
        }
        if (request.getVersion() != null && !request.getVersion().isBlank()) {
            cmd.add("--version");
            cmd.add(request.getVersion().trim());
        }
        cmd.add("--force");

        CliResult result = runCommand(cmd, Duration.ofMinutes(3));
        if (result.exitCode() == 0) {
            log.info("openclaw skills install OK (global={}): {}", global, slug);
            return true;
        }
        log.warn("openclaw CLI install failed (global={}): {}", global, result.output());
        errors.add("CLI: " + truncate(result.output(), 200));
        return false;
    }

    private String buildInstallSpec(SkillInstallRequest request, String slug) {
        if ("GitHub".equalsIgnoreCase(request.getSource())) {
            String repo = request.getGithubRepo();
            if (repo == null || repo.isBlank()) {
                repo = githubRepoFromSlug(slug);
            }
            if (repo != null && repo.contains("/")) {
                return "git:" + repo.trim();
            }
        }
        return slug;
    }

    private void manualInstall(SkillInstallRequest request, String slug, boolean global) throws Exception {
        Path targetDir = resolveTargetDir(slug, global);
        if (Files.exists(targetDir)) {
            deleteRecursive(targetDir);
        }
        Files.createDirectories(targetDir);

        if ("GitHub".equalsIgnoreCase(request.getSource())) {
            installGitHubZip(request, slug, targetDir);
        } else {
            installClawHubZip(slug, request.getVersion(), targetDir);
        }
    }

    private void installClawHubZip(String slug, String version, Path targetDir) throws Exception {
        String base = marketProperties.getClawhubBaseUrl().replaceAll("/$", "");
        StringBuilder url = new StringBuilder(base)
            .append("/api/v1/download?slug=")
            .append(URLEncoder.encode(slug, StandardCharsets.UTF_8));
        if (version != null && !version.isBlank()) {
            url.append("&version=").append(URLEncoder.encode(version.trim(), StandardCharsets.UTF_8));
        }

        Path zipFile = Files.createTempFile("clawhub-skill-", ".zip");
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(90))
                .header("User-Agent", "OpenClaw-Visual-Studio/0.1")
                .GET()
                .build();
            HttpResponse<Path> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofFile(zipFile));
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("ClawHub 下载失败 HTTP " + resp.statusCode());
            }
            unzipToDirectory(zipFile, targetDir);
        } finally {
            Files.deleteIfExists(zipFile);
        }
    }

    private void installGitHubZip(SkillInstallRequest request, String slug, Path targetDir) throws Exception {
        String repo = request.getGithubRepo();
        if (repo == null || repo.isBlank()) {
            repo = githubRepoFromSlug(slug);
        }
        if (repo == null || !repo.contains("/")) {
            throw new IllegalArgumentException("无法解析 GitHub 仓库名");
        }
        String[] parts = repo.split("/", 2);
        String zipUrl = "https://github.com/" + parts[0] + "/" + parts[1] + "/archive/refs/heads/main.zip";
        Path zipFile = Files.createTempFile("github-skill-", ".zip");
        try {
            downloadFile(zipUrl, zipFile);
            unzipGitHubArchive(zipFile, targetDir);
        } catch (Exception e) {
            zipUrl = "https://github.com/" + parts[0] + "/" + parts[1] + "/archive/refs/heads/master.zip";
            downloadFile(zipUrl, zipFile);
            unzipGitHubArchive(zipFile, targetDir);
        } finally {
            Files.deleteIfExists(zipFile);
        }
    }

    private static void unzipGitHubArchive(Path zipFile, Path targetDir) throws Exception {
        Path tempRoot = Files.createTempDirectory("gh-skill-");
        try {
            unzipToDirectory(zipFile, tempRoot);
            try (Stream<Path> walk = Files.walk(tempRoot, 3)) {
                Path skillRoot = walk
                    .filter(p -> Files.isRegularFile(p) && "SKILL.md".equals(p.getFileName().toString()))
                    .findFirst()
                    .map(p -> p.getParent())
                    .orElse(null);
                if (skillRoot == null) {
                    throw new IllegalStateException("仓库中未找到 SKILL.md");
                }
                copyDirectory(skillRoot, targetDir);
            }
        } finally {
            deleteRecursive(tempRoot);
        }
    }

    private static void unzipToDirectory(Path zipFile, Path targetDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                Path out = targetDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(targetDir)) {
                    throw new IllegalStateException("非法 zip 路径: " + entry.getName());
                }
                Files.createDirectories(out.getParent());
                Files.copy(zis, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void downloadFile(String url, Path dest) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(90))
            .header("User-Agent", "OpenClaw-Visual-Studio/0.1")
            .GET()
            .build();
        HttpResponse<Path> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofFile(dest));
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("下载失败 HTTP " + resp.statusCode());
        }
    }

    private Path resolveTargetDir(String slug, boolean global) {
        if (global) {
            return globalSkillDir(slug);
        }
        return resolveWorkspaceSkillsDir(true)
            .map(dir -> dir.resolve(slug))
            .orElseThrow(() -> new BadRequestException(
                "无法配置 OpenClaw 工作区，请检查 openclaw.json 权限或改用「全局」安装"
            ));
    }

    private static Path globalSkillDir(String slug) {
        return Path.of(System.getProperty("user.home"), ".openclaw", "skills", slug);
    }

    private OpenClawWorkspaceConfigService.WorkspaceResolution ensureWorkspaceForInstall() {
        try {
            return workspaceConfigService.ensureWorkspaceConfigured(true);
        } catch (Exception e) {
            throw new BadRequestException(
                "无法写入 OpenClaw 工作区配置（"
                    + workspaceConfigService.resolveConfigPath()
                    + "）: "
                    + e.getMessage()
            );
        }
    }

    private java.util.Optional<Path> resolveWorkspaceSkillsDir(boolean autoConfigure) {
        try {
            OpenClawWorkspaceConfigService.WorkspaceResolution resolution =
                workspaceConfigService.ensureWorkspaceConfigured(autoConfigure);
            return java.util.Optional.of(resolution.skillsDir());
        } catch (Exception e) {
            log.warn("Failed to resolve workspace skills dir: {}", e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "workspace";
        }
        return switch (scope.toLowerCase(Locale.ROOT)) {
            case "global", "managed" -> "global";
            case "both", "all" -> "both";
            default -> "workspace";
        };
    }

    private static String githubRepoFromSlug(String slug) {
        if (slug == null || !slug.contains("--")) {
            return null;
        }
        int idx = slug.indexOf("--");
        if (idx <= 0 || idx >= slug.length() - 2) {
            return null;
        }
        return slug.substring(0, idx) + "/" + slug.substring(idx + 2);
    }

    private boolean hasSkillMd(Path dir) {
        return Files.isRegularFile(dir.resolve("SKILL.md"));
    }

    private static String resolveOpenClawExecutable() {
        return "openclaw";
    }

    private static CliResult runCommand(List<String> command, Duration timeout) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CliResult(-1, "命令超时: " + String.join(" ", command));
            }
            return new CliResult(process.exitValue(), output.toString().trim());
        } catch (Exception e) {
            return new CliResult(-1, e.getMessage());
        }
    }

    private static void copyDirectory(Path source, Path target) throws Exception {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path src : walk.toList()) {
                Path dest = target.resolve(source.relativize(src));
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteRecursive(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> paths = walk.sorted((a, b) -> b.compareTo(a)).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private record CliResult(int exitCode, String output) {}
}
