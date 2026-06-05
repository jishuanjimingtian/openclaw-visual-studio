import apiClient from './client';
import type { GatewayInfo } from '@shared/types';

export const gatewayApi = {
  /** 启动 Gateway（默认后台服务长期运行） */
  startGateway(port?: number, daemon?: boolean) {
    const params: Record<string, string | number> = {};
    if (port !== undefined) {
      params.port = port;
    }
    if (daemon !== undefined) {
      params.daemon = daemon ? 'true' : 'false';
    }
    return apiClient.post<GatewayInfo>('/api/v1/gateway/start', undefined, { params });
  },

  /** 连接已在运行的 Gateway（不启动新进程） */
  connectGateway() {
    return apiClient.post<GatewayInfo>('/api/v1/gateway/connect');
  },

  /** 停止 Gateway */
  stopGateway() {
    return apiClient.post<GatewayInfo>('/api/v1/gateway/stop');
  },

  /** 获取 Gateway 状态；full=true 完整探测，默认轻量轮询 */
  getStatus(full = false) {
    return apiClient.get<GatewayInfo>('/api/v1/gateway/status', {
      params: full ? { full: 'true' } : undefined,
    });
  },

  /** 获取 Gateway 日志 */
  getLogs(limit = 100) {
    return apiClient.get<string[]>('/api/v1/gateway/logs', {
      params: { limit: limit.toString() }
    });
  },

  /** 获取 Gateway 信息 */
  getInfo() {
    return apiClient.get<GatewayInfo>('/api/v1/gateway/info');
  }
};