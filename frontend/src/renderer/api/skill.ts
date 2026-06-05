import apiClient from './client';
import type {
  Skill, PageRequest, PageResult, SkillMarketPage, SkillInstallRequest, SkillInstallResult,
} from '@shared/types';

export const skillApi = {
  listSkills(params: PageRequest) {
    const query: Record<string, string | number> = {
      page: params.page - 1,
      size: params.pageSize,
    };
    if (params.sortBy) {
      query.sort = `${params.sortBy},${params.sortOrder || 'desc'}`;
    }
    return apiClient.get<PageResult<Skill>>('/skills', { params: query });
  },

  syncFromOpenClaw() {
    return apiClient.get<number>('/skills/sync');
  },

  getSkill(id: string) {
    return apiClient.get<Skill>(`/skills/${id}`);
  },

  searchInstalled(keyword: string, params: PageRequest) {
    return apiClient.get<PageResult<Skill>>('/skills/search', {
      params: {
        keyword,
        page: params.page - 1,
        size: params.pageSize,
      },
    });
  },

  getTrending(limit = 24, locale = 'zh', llm = false) {
    return apiClient.get<SkillMarketPage>('/skills/market/trending', {
      params: { limit, locale, llm },
    });
  },

  browseMarket(options: {
    source?: string;
    sort?: string;
    limit?: number;
    cursor?: string;
    page?: number;
    locale?: string;
    llm?: boolean;
  }) {
    return apiClient.get<SkillMarketPage>('/skills/market/browse', {
      params: {
        source: options.source ?? 'all',
        sort: options.sort ?? 'downloads',
        limit: options.limit ?? 24,
        cursor: options.cursor,
        page: options.page ?? 1,
        locale: options.locale ?? 'zh',
        llm: options.llm ?? false,
      },
    });
  },

  searchMarket(q: string, options?: {
    source?: string; limit?: number; page?: number; locale?: string; llm?: boolean;
  }) {
    return apiClient.get<SkillMarketPage>('/skills/market/search', {
      params: {
        q,
        source: options?.source ?? 'all',
        limit: options?.limit ?? 24,
        page: options?.page ?? 1,
        locale: options?.locale ?? 'zh',
        llm: options?.llm ?? false,
      },
    });
  },

  installFromMarket(body: SkillInstallRequest) {
    return apiClient.post<SkillInstallResult>('/skills/market/install', body);
  },

  installSkill(skill: Partial<Skill>) {
    return apiClient.post<Skill>('/skills', skill);
  },

  updateSkill(id: string, skill: Partial<Skill>) {
    return apiClient.put<Skill>(`/skills/${id}`, skill);
  },

  uninstallSkill(id: string) {
    return apiClient.delete<void>(`/skills/${id}`);
  },
};

/** @deprecated use searchInstalled */
export function searchSkills(keyword: string, params: PageRequest) {
  return skillApi.searchInstalled(keyword, params);
}
