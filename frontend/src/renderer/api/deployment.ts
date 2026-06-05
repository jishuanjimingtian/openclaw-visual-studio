import apiClient from './client';
import type {
  DeploymentTask,
  DeploymentListItem,
  PageRequest,
  PageResult,
  EnvironmentCheckResult,
  StartDeploymentRequest,
  StartDeploymentResponse,
  OpenClawInstallDiscovery,
  LinkExistingInstallRequest,
  DeployProgress,
  SystemInfo,
  EnvironmentFixRequest,
  FixProgress,
} from '@shared/types';

export const deploymentApi = {
  // ========== 原有 CRUD ==========

  listTasks(params: PageRequest) {
    return apiClient.get<PageResult<DeploymentTask>>('/api/v1/deployment/tasks', {
      params: {
        page: params.page - 1,
        size: params.pageSize,
        sort: params.sortBy ? `${params.sortBy},${params.sortOrder || 'asc'}` : undefined,
      },
    });
  },

  getTask(id: string) {
    return apiClient.get<DeploymentTask>(`/api/v1/deployment/tasks/${id}`);
  },

  createTask(task: Partial<DeploymentTask>) {
    return apiClient.post<DeploymentTask>('/api/v1/deployment/tasks', task);
  },

  updateTask(id: string, task: Partial<DeploymentTask>) {
    return apiClient.put<DeploymentTask>(`/api/v1/deployment/tasks/${id}`, task);
  },

  deleteTask(id: string) {
    return apiClient.delete<void>(`/api/v1/deployment/tasks/${id}`);
  },

  // ========== 一键部署新增 API ==========

  /** 获取部署历史列表 */
  getDeploymentList(page = 0, size = 20) {
    return apiClient.get<PageResult<DeploymentListItem>>('/api/v1/deployment/list', {
      params: { page, size },
    });
  },

  /** 获取当前部署实例 */
  getCurrentDeployment() {
    return apiClient.get<DeploymentListItem | null>('/api/v1/deployment/current');
  },

  /** 删除部署记录 */
  deleteDeployment(id: string) {
    return apiClient.delete<void>(`/api/v1/deployment/${id}`);
  },

  /** 探测本机 OpenClaw 安装 */
  discoverInstallation() {
    return apiClient.get<OpenClawInstallDiscovery>('/api/v1/deployment/discover');
  },

  /** 关联本机已有 OpenClaw */
  linkExistingInstallation(request?: LinkExistingInstallRequest) {
    return apiClient.post<DeploymentListItem>('/api/v1/deployment/link-existing', request ?? {});
  },

  /** 环境检测 */
  checkEnvironment() {
    return apiClient.get<EnvironmentCheckResult[]>('/api/v1/deployment/environment-check');
  },

  /** 获取系统信息 */
  getSystemInfo() {
    return apiClient.get<SystemInfo>('/api/v1/deployment/system-info');
  },

  /** 启动一键部署 */
  startDeployment(request: StartDeploymentRequest) {
    return apiClient.post<StartDeploymentResponse>('/api/v1/deployment/start', request);
  },

  /** 获取部署状态（进度）；logOffset 用于增量拉取日志 */
  getDeployStatus(deployId: string, logOffset = 0) {
    return apiClient.get<DeployProgress>(`/api/v1/deployment/${deployId}/status`, {
      params: { logOffset },
    });
  },

  /** 获取部署日志 */
  getDeployLogs(deployId: string) {
    return apiClient.get<string[]>(`/api/v1/deployment/${deployId}/logs`);
  },

  /** 取消部署 */
  cancelDeployment(deployId: string) {
    return apiClient.post<boolean>(`/api/v1/deployment/${deployId}/cancel`);
  },

  // ========== 环境修复新增 API ==========

  /** 获取可修复项列表 */
  getFixableItems() {
    return apiClient.get<string[]>('/api/v1/deployment/fixable-items');
  },

  /** 获取修复建议 */
  getFixSuggestion(checkName: string) {
    return apiClient.get<string>(`/api/v1/deployment/fix-suggestion/${checkName}`);
  },

  /** 启动一键修复 */
  fixEnvironment(request?: EnvironmentFixRequest) {
    return apiClient.post<FixProgress>('/api/v1/deployment/fix-environment', request);
  },

  /** 获取修复进度；logOffset 用于增量拉取日志 */
  getFixProgress(fixId: string, logOffset = 0) {
    return apiClient.get<FixProgress>(`/api/v1/deployment/fix-progress/${fixId}`, {
      params: { logOffset },
    });
  },

  /** 获取修复结果 */
  getFixResults(fixId: string) {
    return apiClient.get<FixProgress>(`/api/v1/deployment/fix-results/${fixId}`);
  },
};