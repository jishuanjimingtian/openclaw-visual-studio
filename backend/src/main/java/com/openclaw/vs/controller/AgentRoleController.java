package com.openclaw.vs.controller;

import com.openclaw.vs.dto.AgentConfigOverviewDto;
import com.openclaw.vs.dto.AgentRoleDto;
import com.openclaw.vs.dto.ApiResponse;
import com.openclaw.vs.dto.BootstrapSyncResultDto;
import com.openclaw.vs.service.AgentRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
@Tag(name = "配置中心", description = "Agent 角色与运行时参数配置 API（读写 openclaw.json）")
public class AgentRoleController {

    private final AgentRoleService agentRoleService;

    @GetMapping("/overview")
    @Operation(summary = "读取 OpenClaw Agent 配置概览")
    public ApiResponse<AgentConfigOverviewDto> getOverview() {
        return ApiResponse.success(agentRoleService.readOverview());
    }

    @GetMapping("/roles")
    @Operation(summary = "列出所有 Agent 角色（来自 openclaw.json）")
    public ApiResponse<List<AgentRoleDto>> listRoles() {
        return ApiResponse.success(agentRoleService.listAll());
    }

    @GetMapping("/roles/{id}")
    @Operation(summary = "获取单个角色")
    public ApiResponse<AgentRoleDto> getRole(@PathVariable String id) {
        return ApiResponse.success(agentRoleService.getById(id));
    }

    @PostMapping("/roles")
    @Operation(summary = "创建/更新角色并写入 openclaw.json")
    public ApiResponse<AgentRoleDto> saveRole(@RequestBody AgentRoleDto dto) throws Exception {
        return ApiResponse.success(agentRoleService.save(dto));
    }

    @DeleteMapping("/roles/{id}")
    @Operation(summary = "删除 agents.list 中的自定义 Agent")
    public ApiResponse<Map<String, Boolean>> deleteRole(@PathVariable String id) throws Exception {
        agentRoleService.delete(id);
        return ApiResponse.success(Map.of("deleted", true));
    }

    @PostMapping("/bootstrap/sync-prompt")
    @Operation(summary = "将工作区 AGENTS.md 同步写入 openclaw.json 的 systemPrompt")
    public ApiResponse<BootstrapSyncResultDto> syncBootstrapPrompt(
        @RequestParam(required = false) String roleId
    ) throws Exception {
        return ApiResponse.success(agentRoleService.syncBootstrapPrompt(roleId));
    }

    @PostMapping("/bootstrap/init")
    @Operation(summary = "初始化工作区默认 AGENTS.md（不存在时创建）")
    public ApiResponse<BootstrapSyncResultDto> initWorkspaceBootstrap() throws Exception {
        return ApiResponse.success(agentRoleService.initWorkspaceBootstrap());
    }

    @GetMapping("/bootstrap/content")
    @Operation(summary = "读取工作区 AGENTS.md 全文")
    public ApiResponse<Map<String, String>> getBootstrapContent() {
        return ApiResponse.success(Map.of(
            "content",
            agentRoleService.readBootstrapMarkdown()
        ));
    }
}
