package com.openclaw.vs.controller;

import com.openclaw.vs.dto.ApiResponse;
import com.openclaw.vs.dto.SkillInstallRequest;
import com.openclaw.vs.dto.SkillInstallResultDto;
import com.openclaw.vs.dto.SkillMarketPageDto;
import com.openclaw.vs.service.SkillMarketService;
import com.openclaw.vs.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/skills/market")
@RequiredArgsConstructor
@Tag(name = "Skill 市场", description = "ClawHub / GitHub 热门 Skill 发现")
public class SkillMarketController {

    private final SkillMarketService skillMarketService;
    private final SkillService skillService;

    @GetMapping("/trending")
    @Operation(summary = "热门 Skill（ClawHub 下载榜 + GitHub 高星仓库）")
    public ApiResponse<SkillMarketPageDto> trending(
        @Parameter(description = "数量，默认 24") @RequestParam(defaultValue = "24") int limit,
        @Parameter(description = "语言：zh | en，默认 zh（术语表 + 可选 AI 润色）") @RequestParam(defaultValue = "zh") String locale,
        @Parameter(description = "是否用 Qwen 深度翻译描述（需 openclaw.json 配置 qwen apiKey）") @RequestParam(defaultValue = "false") boolean llm) {
        return ApiResponse.success(skillMarketService.trending(
            Math.min(Math.max(limit, 1), 50), locale, llm
        ));
    }

    @GetMapping("/browse")
    @Operation(summary = "浏览市场 Skill")
    public ApiResponse<SkillMarketPageDto> browse(
        @Parameter(description = "来源：all | ClawdHub | GitHub") @RequestParam(defaultValue = "all") String source,
        @Parameter(description = "排序：downloads | stars | trending | updated | newest") @RequestParam(defaultValue = "downloads") String sort,
        @Parameter(description = "数量") @RequestParam(defaultValue = "24") int limit,
        @Parameter(description = "ClawHub 分页游标") @RequestParam(required = false) String cursor,
        @Parameter(description = "GitHub 页码") @RequestParam(defaultValue = "1") int page,
        @Parameter(description = "语言：zh | en") @RequestParam(defaultValue = "zh") String locale,
        @RequestParam(defaultValue = "false") boolean llm) {
        return ApiResponse.success(skillMarketService.browse(
            source, sort, Math.min(Math.max(limit, 1), 50), cursor, page, locale, llm
        ));
    }

    @PostMapping("/install")
    @Operation(summary = "下载并安装 Skill 到 OpenClaw（工作区或全局 skills 目录）")
    public ApiResponse<SkillInstallResultDto> installFromMarket(
        @RequestBody SkillInstallRequest body) {
        return ApiResponse.success(skillService.installFromMarket(body));
    }

    @GetMapping("/search")
    @Operation(summary = "搜索市场 Skill")
    public ApiResponse<SkillMarketPageDto> search(
        @Parameter(description = "关键词") @RequestParam String q,
        @Parameter(description = "来源：all | ClawdHub | GitHub") @RequestParam(defaultValue = "all") String source,
        @Parameter(description = "数量") @RequestParam(defaultValue = "24") int limit,
        @Parameter(description = "GitHub 页码") @RequestParam(defaultValue = "1") int page,
        @Parameter(description = "语言：zh | en") @RequestParam(defaultValue = "zh") String locale,
        @RequestParam(defaultValue = "false") boolean llm) {
        return ApiResponse.success(skillMarketService.search(
            q, source, Math.min(Math.max(limit, 1), 50), page, locale, llm
        ));
    }
}
