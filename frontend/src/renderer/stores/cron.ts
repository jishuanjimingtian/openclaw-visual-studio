import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { cronApi } from '@/api/cron';
import type {
  CronGatewayMeta,
  CronJob,
  CronJobCreatePayload,
  CronListParams,
  CronRunEntry,
  CronRunResult,
} from '@shared/types';

const RUNS_CACHE_TTL_MS = 8_000;
const OVERVIEW_STALE_MS = 5_000;

export const useCronStore = defineStore('cron', () => {
  const loading = ref(false);
  const loadingMore = ref(false);
  const runsLoading = ref(false);
  const saving = ref(false);
  const gatewayMeta = ref<CronGatewayMeta | null>(null);
  const schedulerStatus = ref<Record<string, unknown> | null>(null);
  const jobs = ref<CronJob[]>([]);
  const total = ref(0);
  const limit = ref(50);
  const offset = ref(0);
  const listParams = ref<CronListParams>({
    limit: 50,
    offset: 0,
    enabled: 'all',
    sortBy: 'nextRunAtMs',
    sortDir: 'desc',
    includeDisabled: true,
  });
  const selectedJobId = ref<string | null>(null);
  const lastFetchedAt = ref(0);
  const loadError = ref<string | null>(null);

  const runsByJobId = ref<Map<string, { fetchedAt: number; entries: CronRunEntry[]; total: number; offset: number }>>(
    new Map(),
  );

  let debounceTimer: ReturnType<typeof setTimeout> | null = null;
  let listAbort: AbortController | null = null;
  let overviewAbort: AbortController | null = null;
  let runsAbort: AbortController | null = null;
  let inflightListKey: string | null = null;
  let runPollTimer: ReturnType<typeof setTimeout> | null = null;
  let runPollAborted = false;

  const selectedJob = computed(() => {
    const list = jobs.value ?? [];
    return list.find((j) => j.id === selectedJobId.value) ?? null;
  });

  const hasMore = computed(() => {
    const list = jobs.value ?? [];
    return list.length < (total.value ?? 0);
  });

  function listKey(params: CronListParams): string {
    return JSON.stringify({
      limit: params.limit ?? 50,
      offset: params.offset ?? 0,
      query: params.query ?? '',
      enabled: params.enabled ?? 'all',
      sortBy: params.sortBy ?? 'nextRunAtMs',
      sortDir: params.sortDir ?? 'desc',
      includeDisabled: params.includeDisabled ?? false,
    });
  }

  function applyOverview(data: {
    gatewayConnected: boolean;
    gatewayPort?: number;
    gatewayWsUrl?: string;
    connectionHint?: string;
    schedulerStatus: Record<string, unknown> | null;
    jobs: CronJob[];
    total?: number;
    limit?: number;
    offset?: number;
  }, append = false) {
    gatewayMeta.value = {
      gatewayConnected: data.gatewayConnected,
      gatewayPort: data.gatewayPort,
      gatewayWsUrl: data.gatewayWsUrl,
      connectionHint: data.connectionHint,
    };
    schedulerStatus.value = data.schedulerStatus ?? null;
    const incoming = Array.isArray(data.jobs) ? data.jobs : [];
    if (append) {
      const current = Array.isArray(jobs.value) ? jobs.value : [];
      const seen = new Set(current.map((j) => j.id));
      jobs.value = [...current];
      for (const j of incoming) {
        if (!seen.has(j.id)) jobs.value.push(j);
      }
    } else {
      jobs.value = incoming;
    }
    total.value = data.total ?? incoming.length;
    limit.value = data.limit ?? 50;
    offset.value = data.offset ?? 0;
    lastFetchedAt.value = Date.now();
    if (!selectedJobId.value && jobs.value.length > 0) {
      selectedJobId.value = jobs.value[0].id;
    } else if (selectedJobId.value && !jobs.value.some((j) => j.id === selectedJobId.value)) {
      selectedJobId.value = jobs.value[0]?.id ?? null;
    }
  }

  async function fetchOverview(force = false, append = false) {
    const params = { ...listParams.value };
    const key = listKey(params);
    const jobCount = Array.isArray(jobs.value) ? jobs.value.length : 0;
    if (!force && !append && Date.now() - lastFetchedAt.value < OVERVIEW_STALE_MS && jobCount > 0) {
      return;
    }
    if (inflightListKey === key && loading.value) {
      return;
    }
    inflightListKey = key;
    overviewAbort?.abort();
    overviewAbort = new AbortController();

    if (append) loadingMore.value = true;
    else loading.value = true;
    loadError.value = null;

    try {
      const res = await cronApi.getOverview(params);
      if (res.data) applyOverview(res.data, append);
    } catch (e) {
      loadError.value = e instanceof Error ? e.message : '加载定时任务失败';
    } finally {
      loading.value = false;
      loadingMore.value = false;
      if (inflightListKey === key) inflightListKey = null;
    }
  }

  function scheduleFetchDebounced(delayMs = 300) {
    if (debounceTimer) clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      debounceTimer = null;
      listParams.value = { ...listParams.value, offset: 0 };
      fetchOverview(true);
    }, delayMs);
  }

  function setQuery(query: string) {
    listParams.value = { ...listParams.value, query: query || undefined, offset: 0 };
    scheduleFetchDebounced();
  }

  function setListFilters(patch: Partial<CronListParams>) {
    listParams.value = { ...listParams.value, ...patch, offset: 0 };
    fetchOverview(true);
  }

  async function loadMore() {
    if (!hasMore.value || loadingMore.value) return;
    const nextOffset = Array.isArray(jobs.value) ? jobs.value.length : 0;
    listParams.value = { ...listParams.value, offset: nextOffset };
    await fetchOverview(true, true);
  }

  function selectJob(id: string | null) {
    selectedJobId.value = id;
    runPollAborted = true;
    stopRunPoll();
  }

  function invalidateRuns(jobId?: string) {
    if (!jobId) {
      runsByJobId.value = new Map();
      return;
    }
    const next = new Map(runsByJobId.value);
    next.delete(jobId);
    runsByJobId.value = next;
  }

  async function fetchRuns(jobId: string, force = false, loadMoreRuns = false) {
    const cached = runsByJobId.value.get(jobId);
    if (!force && cached && Date.now() - cached.fetchedAt < RUNS_CACHE_TTL_MS && !loadMoreRuns) {
      return cached.entries;
    }

    runsAbort?.abort();
    runsAbort = new AbortController();
    runsLoading.value = true;

    const runOffset = loadMoreRuns && cached ? cached.offset + (cached.entries.length) : 0;
    try {
      const res = await cronApi.listRuns(jobId, {
        limit: 30,
        offset: runOffset,
        sortDir: 'desc',
      });
      const page = res.data;
      const pageEntries = Array.isArray(page?.entries) ? page.entries : [];
      const prev = runsByJobId.value.get(jobId);
      const entries = loadMoreRuns && prev
        ? [...prev.entries, ...pageEntries]
        : pageEntries;
      runsByJobId.value.set(jobId, {
        fetchedAt: Date.now(),
        entries,
        total: page?.total ?? entries.length,
        offset: runOffset,
      });
      return entries;
    } catch {
      return cached?.entries ?? [];
    } finally {
      runsLoading.value = false;
    }
  }

  function getRuns(jobId: string): CronRunEntry[] {
    return runsByJobId.value.get(jobId)?.entries ?? [];
  }

  function runsHasMore(jobId: string): boolean {
    const c = runsByJobId.value.get(jobId);
    if (!c) return false;
    return c.entries.length < (c.total ?? 0);
  }

  async function createJob(body: CronJobCreatePayload) {
    saving.value = true;
    try {
      const res = await cronApi.addJob(body);
      invalidateRuns();
      await fetchOverview(true);
      const job = res.data;
      if (job?.id) selectedJobId.value = job.id;
      return job;
    } finally {
      saving.value = false;
    }
  }

  async function patchJob(id: string, patch: Record<string, unknown>) {
    saving.value = true;
    try {
      const res = await cronApi.updateJob(id, patch);
      invalidateRuns(id);
      await fetchOverview(true);
      return res.data;
    } finally {
      saving.value = false;
    }
  }

  async function deleteJob(id: string) {
    await cronApi.removeJob(id);
    invalidateRuns(id);
    if (selectedJobId.value === id) selectedJobId.value = null;
    await fetchOverview(true);
  }

  async function toggleEnabled(job: CronJob) {
    return patchJob(job.id, { enabled: !job.enabled });
  }

  async function runJobNow(id: string, mode: 'force' | 'due' = 'force'): Promise<CronRunResult | null> {
    const res = await cronApi.runJob(id, mode);
    invalidateRuns(id);
    return res.data ?? null;
  }

  function stopRunPoll() {
    runPollAborted = true;
    if (runPollTimer) {
      clearTimeout(runPollTimer);
      runPollTimer = null;
    }
  }

  async function pollRunResult(
    jobId: string,
    runId: string,
    onUpdate?: (entry: CronRunEntry | null) => void,
  ): Promise<CronRunEntry | null> {
    stopRunPoll();
    runPollAborted = false;
    const delays = [2000, 4000, 8000, 8000, 8000, 8000, 8000, 8000];
    const started = Date.now();

    for (let i = 0; i < delays.length; i++) {
      if (runPollAborted || Date.now() - started > 90_000) break;
      await new Promise<void>((resolve) => {
        runPollTimer = setTimeout(resolve, delays[i]);
      });
      if (runPollAborted) break;

      invalidateRuns(jobId);
      const entries = await fetchRuns(jobId, true);
      const match = entries.find((e) => e.runId === runId);
      onUpdate?.(match ?? null);
      if (match?.status && ['ok', 'error', 'skipped'].includes(match.status)) {
        return match;
      }
    }
    return null;
  }

  function dispose() {
    if (debounceTimer) clearTimeout(debounceTimer);
    debounceTimer = null;
    overviewAbort?.abort();
    listAbort?.abort();
    runsAbort?.abort();
    stopRunPoll();
  }

  return {
    loading,
    loadingMore,
    runsLoading,
    saving,
    gatewayMeta,
    schedulerStatus,
    jobs,
    total,
    limit,
    offset,
    listParams,
    selectedJobId,
    selectedJob,
    lastFetchedAt,
    loadError,
    hasMore,
    fetchOverview,
    scheduleFetchDebounced,
    setQuery,
    setListFilters,
    loadMore,
    selectJob,
    fetchRuns,
    getRuns,
    runsHasMore,
    createJob,
    patchJob,
    deleteJob,
    toggleEnabled,
    runJobNow,
    pollRunResult,
    stopRunPoll,
    dispose,
    invalidateRuns,
  };
});
