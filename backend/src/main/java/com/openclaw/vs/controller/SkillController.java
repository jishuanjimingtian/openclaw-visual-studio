package com.openclaw.vs.controller;

import com.openclaw.vs.dto.ApiResponse;
import com.openclaw.vs.dto.PageResult;
import com.openclaw.vs.model.InstalledSkill;
import com.openclaw.vs.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/skills")
@RequiredArgsConstructor
@Tag(name = "Skill 市场", description = "Skill 安装与管理 API")
public class SkillController {

    private final SkillService skillService;

    @GetMapping
    @Operation(summary = "分页查询 Skill 列表")
    public ApiResponse<PageResult<InstalledSkill>> listSkills(
        @Parameter(description = "分页参数") @PageableDefault(size = 20, sort = "installedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(PageResult.from(skillService.listSkills(pageable)));
    }

    @GetMapping("/sync")
    @Operation(summary = "从 OpenClaw 磁盘同步已安装 Skill")
    public ApiResponse<Integer> syncFromOpenClaw() {
        return ApiResponse.success(skillService.syncFromOpenClaw());
    }

    @GetMapping("/search")
    @Operation(summary = "搜索 Skill")
    public ApiResponse<PageResult<InstalledSkill>> searchSkills(
        @Parameter(description = "搜索关键词") @RequestParam String keyword,
        @Parameter(description = "分页参数") @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(PageResult.from(skillService.searchSkills(keyword, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取 Skill 详情")
    public ApiResponse<InstalledSkill> getSkill(
        @Parameter(description = "Skill ID") @PathVariable String id) {
        return ApiResponse.success(skillService.getSkill(id));
    }

    @PostMapping
    @Operation(summary = "安装 Skill")
    public ApiResponse<InstalledSkill> installSkill(
        @Parameter(description = "Skill 信息") @Valid @RequestBody InstalledSkill skill) {
        return ApiResponse.success(skillService.installSkill(skill));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 Skill")
    public ApiResponse<InstalledSkill> updateSkill(
        @Parameter(description = "Skill ID") @PathVariable String id,
        @Parameter(description = "更新的 Skill 信息") @RequestBody InstalledSkill skill) {
        return ApiResponse.success(skillService.updateSkill(id, skill));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "卸载 Skill")
    public ApiResponse<Void> uninstallSkill(
        @Parameter(description = "Skill ID") @PathVariable String id) {
        skillService.uninstallSkill(id);
        return ApiResponse.success(null);
    }
}