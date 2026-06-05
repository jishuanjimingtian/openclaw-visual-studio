import apiClient from './client';
import type {
  CronJob,
  CronJobCreatePayload,
  CronListPage,
  CronListParams,
  CronOverview,
  CronRunResult,
  CronRunsPage,
  CronStatusResult,
} from '@shared/types';

function listParams(params?: CronListParams) {
  return {
    limit: params?.limit,
    offset: params?.offset,
    query: params?.query,
    enabled: params?.enabled ?? 'all',
    sortBy: params?.sortBy ?? 'nextRunAtMs',
    sortDir: params?.sortDir ?? 'desc',
    includeDisabled: params?.includeDisabled,
  };
}

export const cronApi = {
  getOverview(params?: CronListParams) {
    return apiClient.get<CronOverview>('/openclaw/cron/overview', {
      params: listParams(params),
    });
  },

  getStatus() {
    return apiClient.get<CronStatusResult>('/openclaw/cron/status');
  },

  listJobs(params?: CronListParams) {
    return apiClient.get<CronListPage>('/openclaw/cron/jobs', {
      params: listParams(params),
    });
  },

  addJob(body: CronJobCreatePayload) {
    return apiClient.post<CronJob>('/openclaw/cron/jobs', body);
  },

  updateJob(id: string, patch: Record<string, unknown>) {
    return apiClient.put<CronJob>(`/openclaw/cron/jobs/${encodeURIComponent(id)}`, { patch });
  },

  removeJob(id: string) {
    return apiClient.delete<{ removed?: boolean }>(`/openclaw/cron/jobs/${encodeURIComponent(id)}`);
  },

  runJob(id: string, mode: 'force' | 'due' = 'force') {
    return apiClient.post<CronRunResult>(`/openclaw/cron/jobs/${encodeURIComponent(id)}/run`, { mode });
  },

  listRuns(
    jobId: string,
    opts?: { limit?: number; offset?: number; status?: string; sortDir?: string },
  ) {
    return apiClient.get<CronRunsPage>(`/openclaw/cron/jobs/${encodeURIComponent(jobId)}/runs`, {
      params: {
        limit: opts?.limit ?? 30,
        offset: opts?.offset ?? 0,
        status: opts?.status ?? 'all',
        sortDir: opts?.sortDir ?? 'desc',
      },
    });
  },
};
