import apiClient from './client';
import type { AgentConfigOverview, AgentRole, BootstrapSyncResult } from '@shared/types';

export const configApi = {
  getOverview() {
    return apiClient.get<AgentConfigOverview>('/config/overview');
  },

  listRoles() {
    return apiClient.get<AgentRole[]>('/config/roles');
  },

  getRole(id: string) {
    return apiClient.get<AgentRole>(`/config/roles/${id}`);
  },

  saveRole(role: Partial<AgentRole>) {
    return apiClient.post<AgentRole>('/config/roles', role);
  },

  deleteRole(id: string) {
    return apiClient.delete<{ deleted: boolean }>(`/config/roles/${id}`);
  },

  /** 将 AGENTS.md 同步写入 openclaw.json systemPrompt */
  syncBootstrapPrompt(roleId?: string) {
    return apiClient.post<BootstrapSyncResult>('/config/bootstrap/sync-prompt', undefined, {
      params: roleId ? { roleId } : undefined,
    });
  },

  /** 初始化工作区默认 AGENTS.md */
  initWorkspaceBootstrap() {
    return apiClient.post<BootstrapSyncResult>('/config/bootstrap/init');
  },

  getBootstrapContent() {
    return apiClient.get<{ content: string }>('/config/bootstrap/content');
  },
};
