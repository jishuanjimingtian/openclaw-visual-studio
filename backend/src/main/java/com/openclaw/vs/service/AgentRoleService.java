package com.openclaw.vs.service;

import com.openclaw.vs.dto.AgentConfigOverviewDto;
import com.openclaw.vs.dto.AgentRoleDto;
import com.openclaw.vs.dto.BootstrapSyncResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentRoleService {

    private final OpenClawAgentConfigService openClawAgentConfigService;

    public AgentConfigOverviewDto readOverview() {
        return openClawAgentConfigService.readOverview();
    }

    public List<AgentRoleDto> listAll() {
        return openClawAgentConfigService.listRoles();
    }

    public AgentRoleDto getById(String id) {
        return openClawAgentConfigService.getRole(id);
    }

    public AgentRoleDto save(AgentRoleDto dto) throws Exception {
        return openClawAgentConfigService.saveRole(dto);
    }

    public void delete(String id) throws Exception {
        openClawAgentConfigService.deleteRole(id);
    }

    public BootstrapSyncResultDto syncBootstrapPrompt(String roleId) throws Exception {
        return openClawAgentConfigService.syncBootstrapPromptToConfig(roleId);
    }

    public BootstrapSyncResultDto initWorkspaceBootstrap() throws Exception {
        return openClawAgentConfigService.initDefaultAgentsMd();
    }

    public String readBootstrapMarkdown() {
        return openClawAgentConfigService.readBootstrapMarkdown();
    }
}
