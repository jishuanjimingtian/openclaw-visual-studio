import apiClient from './client';
import type {
  KnowledgeBootstrap,
  KnowledgeDiscoverResult,
  KnowledgeFile,
  KnowledgeFileContent,
  KnowledgeGraph,
  KnowledgeIndexRebuildResult,
  KnowledgeIndexStatus,
  KnowledgeOverview,
  KnowledgeSearchResult,
  KnowledgeWorkspaceCandidate,
} from '@shared/types';

export const knowledgeApi = {
  getOverview(autoConfigure = false) {
    return apiClient.get<KnowledgeOverview>('/knowledge/overview', {
      params: { autoConfigure },
    });
  },

  bootstrap(options?: {
    autoConfigure?: boolean;
    includeGraph?: boolean;
    includeChunks?: boolean;
    dailyWindowDays?: number;
  }) {
    return apiClient.get<KnowledgeBootstrap>('/knowledge/bootstrap', {
      params: {
        autoConfigure: options?.autoConfigure ?? false,
        includeGraph: options?.includeGraph ?? false,
        includeChunks: options?.includeChunks ?? false,
        dailyWindowDays: options?.dailyWindowDays ?? 90,
      },
    });
  },

  getGraph(options?: {
    includeChunks?: boolean;
    autoConfigure?: boolean;
    focusPath?: string;
    dailyWindowDays?: number;
  }) {
    return apiClient.get<KnowledgeGraph>('/knowledge/graph', {
      params: {
        includeChunks: options?.includeChunks ?? false,
        autoConfigure: options?.autoConfigure ?? false,
        focusPath: options?.focusPath,
        dailyWindowDays: options?.dailyWindowDays ?? 90,
      },
    });
  },

  listFiles(autoConfigure = false) {
    return apiClient.get<KnowledgeFile[]>('/knowledge/files', {
      params: { autoConfigure },
    });
  },

  readFile(path: string, autoConfigure = false) {
    const encoded = path.split('/').map(encodeURIComponent).join('/');
    return apiClient.get<KnowledgeFileContent>(`/knowledge/files/${encoded}`, {
      params: { autoConfigure },
    });
  },

  writeFile(path: string, content: string, autoConfigure = false) {
    const encoded = path.split('/').map(encodeURIComponent).join('/');
    return apiClient.put<KnowledgeFileContent>(`/knowledge/files/${encoded}`, { content }, {
      params: { autoConfigure },
    });
  },

  deleteFile(path: string, autoConfigure = false) {
    const encoded = path.split('/').map(encodeURIComponent).join('/');
    return apiClient.delete<void>(`/knowledge/files/${encoded}`, {
      params: { autoConfigure },
    });
  },

  search(query: string, limit = 20, autoConfigure = false) {
    return apiClient.post<KnowledgeSearchResult>('/knowledge/search', { query, limit }, {
      params: { autoConfigure },
    });
  },

  getIndexStatus(probe = false) {
    return apiClient.get<KnowledgeIndexStatus>('/knowledge/index/status', {
      params: { probe },
    });
  },

  rebuildIndex() {
    return apiClient.post<KnowledgeIndexRebuildResult>('/knowledge/index/rebuild');
  },

  getTodayDiaryPath() {
    return apiClient.get<string>('/knowledge/defaults/today-diary');
  },

  listWorkspaces() {
    return apiClient.get<KnowledgeWorkspaceCandidate[]>('/knowledge/workspaces');
  },

  discoverWorkspace(persistToConfig = false) {
    return apiClient.post<KnowledgeDiscoverResult>('/knowledge/workspace/discover', {}, {
      params: { persistToConfig },
    });
  },

  useWorkspace(path: string, persistToConfig = true) {
    return apiClient.post<KnowledgeOverview>('/knowledge/workspace/use', {}, {
      params: { path, persistToConfig },
    });
  },
};
