package com.openclaw.vs.controller;

import com.openclaw.vs.dto.*;
import com.openclaw.vs.service.OpenClawKnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
@Tag(name = "知识库", description = "Claw 工作区记忆 Markdown 浏览与管理")
public class KnowledgeController {

    private final OpenClawKnowledgeService knowledgeService;

    @GetMapping("/overview")
    @Operation(summary = "知识库概览")
    public ApiResponse<KnowledgeOverviewDto> overview(
        @Parameter(description = "缺失工作区时是否自动写入 openclaw.json")
        @RequestParam(defaultValue = "false") boolean autoConfigure
    ) throws Exception {
        return ApiResponse.success(knowledgeService.getOverview(autoConfigure));
    }

    @GetMapping("/bootstrap")
    @Operation(summary = "知识库首屏聚合（概览 + 文件列表，可选图谱）")
    public ApiResponse<KnowledgeBootstrapDto> bootstrap(
        @RequestParam(defaultValue = "false") boolean autoConfigure,
        @RequestParam(defaultValue = "false") boolean includeGraph,
        @RequestParam(defaultValue = "false") boolean includeChunks,
        @RequestParam(defaultValue = "90") int dailyWindowDays
    ) throws Exception {
        return ApiResponse.success(knowledgeService.bootstrap(
            autoConfigure, includeGraph, includeChunks, dailyWindowDays
        ));
    }

    @GetMapping("/graph")
    @Operation(summary = "知识关系图谱")
    public ApiResponse<KnowledgeGraphDto> graph(
        @RequestParam(defaultValue = "false") boolean includeChunks,
        @RequestParam(defaultValue = "false") boolean autoConfigure,
        @RequestParam(required = false) String focusPath,
        @RequestParam(defaultValue = "90") int dailyWindowDays
    ) throws Exception {
        if (focusPath != null && !focusPath.isBlank()) {
            return ApiResponse.success(knowledgeService.getGraphForPath(focusPath, autoConfigure));
        }
        return ApiResponse.success(knowledgeService.getGraph(includeChunks, autoConfigure, dailyWindowDays));
    }

    @GetMapping("/files")
    @Operation(summary = "列出记忆文件")
    public ApiResponse<List<KnowledgeFileDto>> listFiles(
        @RequestParam(defaultValue = "false") boolean autoConfigure
    ) throws Exception {
        return ApiResponse.success(knowledgeService.listFiles(autoConfigure));
    }

    @GetMapping("/files/**")
    @Operation(summary = "读取记忆文件")
    public ApiResponse<KnowledgeFileContentDto> readFile(
        HttpServletRequest request,
        @RequestParam(defaultValue = "false") boolean autoConfigure
    ) throws Exception {
        String path = extractFilePath(request);
        return ApiResponse.success(knowledgeService.readFile(path, autoConfigure));
    }

    @PutMapping("/files/**")
    @Operation(summary = "写入记忆文件")
    public ApiResponse<KnowledgeFileContentDto> writeFile(
        HttpServletRequest request,
        @RequestBody KnowledgeFileWriteRequest body,
        @RequestParam(defaultValue = "false") boolean autoConfigure
    ) throws Exception {
        String path = extractFilePath(request);
        return ApiResponse.success(
            knowledgeService.writeFile(path, body != null ? body.getContent() : "", autoConfigure)
        );
    }

    @DeleteMapping("/files/**")
    @Operation(summary = "删除记忆文件")
    public ApiResponse<Void> deleteFile(
        HttpServletRequest request,
        @RequestParam(defaultValue = "false") boolean autoConfigure
    ) throws Exception {
        String path = extractFilePath(request);
        knowledgeService.deleteFile(path, autoConfigure);
        return ApiResponse.success(null);
    }

    @PostMapping("/search")
    @Operation(summary = "搜索记忆")
    public ApiResponse<KnowledgeSearchResultDto> search(
        @RequestBody KnowledgeSearchRequest request,
        @RequestParam(defaultValue = "false") boolean autoConfigure
    ) throws Exception {
        return ApiResponse.success(knowledgeService.search(
            request.getQuery(),
            request.getLimit(),
            autoConfigure
        ));
    }

    @GetMapping("/index/status")
    @Operation(summary = "记忆索引状态")
    public ApiResponse<KnowledgeIndexStatusDto> indexStatus(
        @RequestParam(defaultValue = "false") boolean probe
    ) {
        return ApiResponse.success(knowledgeService.getIndexStatus(probe));
    }

    @PostMapping("/index/rebuild")
    @Operation(summary = "重建记忆索引")
    public ApiResponse<KnowledgeIndexRebuildResultDto> rebuildIndex() {
        return ApiResponse.success(knowledgeService.rebuildIndex());
    }

    @GetMapping("/defaults/today-diary")
    @Operation(summary = "今日日记默认路径")
    public ApiResponse<String> todayDiaryPath() {
        return ApiResponse.success(knowledgeService.defaultTodayDiaryPath());
    }

    @GetMapping("/workspaces")
    @Operation(summary = "列出可发现的 OpenClaw 工作区（含记忆文件数）")
    public ApiResponse<List<KnowledgeWorkspaceCandidateDto>> listWorkspaces() throws Exception {
        return ApiResponse.success(knowledgeService.listWorkspaceCandidates());
    }

    @PostMapping("/workspace/discover")
    @Operation(summary = "自动扫描并定位 Claw 知识库工作区（记忆文件最多的目录）")
    public ApiResponse<KnowledgeDiscoverResultDto> discoverWorkspace(
        @RequestParam(defaultValue = "false") boolean persistToConfig
    ) throws Exception {
        return ApiResponse.success(knowledgeService.discoverWorkspace(persistToConfig));
    }

    @PostMapping("/workspace/use")
    @Operation(summary = "切换知识库读取的工作区")
    public ApiResponse<KnowledgeOverviewDto> useWorkspace(
        @RequestParam String path,
        @RequestParam(defaultValue = "true") boolean persistToConfig
    ) throws Exception {
        return ApiResponse.success(knowledgeService.useWorkspace(path, persistToConfig));
    }

    private static String extractFilePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String marker = "/knowledge/files/";
        int idx = uri.indexOf(marker);
        if (idx < 0) {
            throw new IllegalArgumentException("无效的文件路径");
        }
        String encoded = uri.substring(idx + marker.length());
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }
}
