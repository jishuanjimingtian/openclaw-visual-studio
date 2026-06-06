import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { gatewayApi } from '@/api/gateway';
import type { GatewayInfo, GatewayStartupPhase } from '@shared/types';

export type GatewayStatus = 'running' | 'stopped' | 'starting' | 'stopping' | 'error';

const STARTUP_POLL_MS = 2000;
const STARTUP_WAIT_MS = 120_000;
const SHUTDOWN_STOP_TIMEOUT_MS = 8_000;

const STARTUP_PHASE_LABELS: Record<GatewayStartupPhase, string> = {
  idle: '',
  launching: '正在启动 OpenClaw Gateway…',
  port_wait: '等待 Gateway 端口就绪…',
  rpc_connect: '端口已监听，正在建立 RPC 连接…',
  running: '',
  failed: '',
};

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function normalizeStatus(raw?: string): GatewayStatus {
  switch (raw) {
    case 'running':
    case 'starting':
    case 'stopped':
    case 'stopping':
      return raw;
    case 'failed':
    case 'error':
      return 'error';
    default:
      return 'stopped';
  }
}

export const useGatewayStore = defineStore('gateway', () => {
  const status = ref<GatewayStatus>('stopped');
  const gatewayInfo = ref<GatewayInfo | null>(null);
  const logs = ref<string[]>([]);
  const loading = ref(false);
  const polling = ref(false);
  const lastRefreshedAt = ref<Date | null>(null);
  /** 用户主动停止后，忽略端口残留探测导致的「启动中」误判 */
  const userHalted = ref(false);

  const isRunning = computed(() => status.value === 'running');
  const isStarting = computed(() => status.value === 'starting');
  const isStopping = computed(() => status.value === 'stopping');
  const isError = computed(() => status.value === 'error');
  const isActive = computed(() => isRunning.value || isStarting.value || wsConnected.value);

  const endpoint = computed(() => gatewayInfo.value?.endpoint || '');
  const controlUrl = computed(
    () => gatewayInfo.value?.controlUrl || gatewayInfo.value?.endpoint || '',
  );
  const port = computed(() => gatewayInfo.value?.port || 18789);
  const version = computed(() => gatewayInfo.value?.version || '');
  const pid = computed(() => gatewayInfo.value?.pid ?? null);
  const uptime = computed(() => gatewayInfo.value?.uptime || '');
  const startTime = computed(() => gatewayInfo.value?.startTime || '');
  const message = computed(() => gatewayInfo.value?.message || '');
  const startupPhase = computed(() => gatewayInfo.value?.startupPhase);
  const startupProgress = computed(() => gatewayInfo.value?.startupProgress ?? 0);
  const startupMessage = computed(() => {
    const custom = gatewayInfo.value?.message;
    if (custom?.trim()) return custom;
    const phase = gatewayInfo.value?.startupPhase;
    if (phase && phase in STARTUP_PHASE_LABELS) {
      return STARTUP_PHASE_LABELS[phase as GatewayStartupPhase];
    }
    return '';
  });
  const isStartupInProgress = computed(
    () => isStarting.value || (loading.value && !isHealthy.value && status.value !== 'error'),
  );
  const wsConnected = computed(() => gatewayInfo.value?.wsConnected === true);
  const managedBy = computed(() => gatewayInfo.value?.managedBy || '');
  const serviceInstalled = computed(() => gatewayInfo.value?.serviceInstalled === true);

  const managedByLabel = computed(() => {
    switch (managedBy.value) {
      case 'daemon':
        return '后台服务';
      case 'vs-process':
        return '工具子进程';
      case 'external':
        return '外部实例';
      default:
        return '';
    }
  });

  const healthIssue = computed(() => gatewayInfo.value?.healthIssue || '');
  const recoveryHint = computed(() => gatewayInfo.value?.recoveryHint || '');
  const isZombie = computed(() => healthIssue.value === 'zombie' || healthIssue.value === 'probe_failed');
  const needsRecovery = computed(() => Boolean(recoveryHint.value) || isZombie.value);

  /** 进程存活（running / starting / RPC 已连接） */
  const processOnline = computed(() => isRunning.value || isStarting.value || wsConnected.value);

  /** 端口已监听（进程在线且有端口信息） */
  const portReady = computed(
    () => processOnline.value && Boolean(endpoint.value || port.value),
  );

  /** 完全就绪：进程 + RPC 均已连接 */
  const isHealthy = computed(() => (isRunning.value || wsConnected.value) && wsConnected.value);

  const statusLabel = computed(() => {
    if (isZombie.value) return 'Gateway 僵死';
    if (isHealthy.value) return '运行正常';
    if (wsConnected.value) return '运行中';
    if (isRunning.value && !wsConnected.value) return '等待 RPC 连接';
    if (isStarting.value) return '启动中';
    if (isStopping.value) return '停止中';
    if (isError.value) return '异常';
    return '已停止';
  });

  const lastRefreshedLabel = computed(() => {
    if (!lastRefreshedAt.value) return '';
    return lastRefreshedAt.value.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  });

  function applyInfo(data: GatewayInfo | null | undefined) {
    if (!data) return;
    const prevStatus = status.value;
    const prevWs = gatewayInfo.value?.wsConnected;
    const prevPort = gatewayInfo.value?.port;
    gatewayInfo.value = data;
    let normalized = normalizeStatus(data.status);
    if (data.healthIssue === 'zombie' || data.healthIssue === 'probe_failed') {
      normalized = 'error';
    }
    // 后端 HTTP 探测可能失败，但 RPC 已连接时仍应视为运行中
    if (data.wsConnected && normalized === 'stopped') {
      normalized = 'running';
    }
    // 用户已停止时，勿被端口仍监听误判为启动中
    if (userHalted.value && normalized === 'starting' && !data.wsConnected) {
      normalized = 'stopped';
    }
    status.value = normalized;
    const statusChanged =
      prevStatus !== normalized
      || prevWs !== data.wsConnected
      || prevPort !== data.port;
    const now = Date.now();
    if (
      statusChanged
      || !lastRefreshedAt.value
      || now - lastRefreshedAt.value.getTime() >= 10_000
    ) {
      lastRefreshedAt.value = new Date(now);
    }
  }

  /**
   * 静默刷新：不触发 loading/error；请求失败时保留当前状态。
   */
  async function fetchStatusSilent(full = false) {
    try {
      const res = await gatewayApi.getStatus(full);
      if (res.code !== 0) return null;
      applyInfo(res.data);
      return res.data;
    } catch {
      return null;
    }
  }

  async function requestStart(portNum?: number, daemon?: boolean) {
    userHalted.value = false;
    const res = await gatewayApi.startGateway(portNum, daemon);
    if (res.code !== 0) {
      throw new Error(res.message || 'Gateway 启动失败');
    }
    applyInfo(res.data);
    if (res.data.status === 'failed' || res.data.startupPhase === 'failed') {
      status.value = 'error';
      throw new Error(res.data.message || 'Gateway 启动失败');
    }
    return res.data;
  }

  async function waitForGatewayReady(timeoutMs = STARTUP_WAIT_MS): Promise<GatewayInfo | null> {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      const data = await fetchStatusSilent(true);
      if (data?.status === 'failed' || data?.startupPhase === 'failed') {
        status.value = 'error';
        return data;
      }
      if (isHealthy.value || (data?.wsConnected && normalizeStatus(data.status) === 'running')) {
        return data;
      }
      await sleep(STARTUP_POLL_MS);
    }
    return gatewayInfo.value;
  }

  async function startGateway(portNum?: number, daemon?: boolean) {
    loading.value = true;
    status.value = 'starting';
    try {
      return await requestStart(portNum, daemon);
    } catch (error: unknown) {
      status.value = 'error';
      throw error;
    } finally {
      loading.value = false;
    }
  }

  async function stopGateway(options?: { silent?: boolean; signal?: AbortSignal }) {
    if (shuttingDown && options?.silent) {
      return gatewayInfo.value;
    }
    stopPolling();
    userHalted.value = true;
    if (!options?.silent) {
      loading.value = true;
    }
    status.value = 'stopping';
    if (gatewayInfo.value) {
      gatewayInfo.value = {
        ...gatewayInfo.value,
        wsConnected: false,
        status: 'stopping',
        endpoint: undefined,
      };
    }
    try {
      const res = await gatewayApi.stopGateway(options?.signal);
      if (res.code !== 0) {
        if (!options?.silent) {
          status.value = 'error';
          throw new Error(res.message || 'Gateway 停止失败');
        }
        return gatewayInfo.value;
      }
      applyInfo(res.data);
      return res.data;
    } catch (error: unknown) {
      if (!options?.silent) {
        status.value = 'error';
        throw error;
      }
      return gatewayInfo.value;
    } finally {
      if (!options?.silent) {
        loading.value = false;
      }
    }
  }

  /**
   * 应用退出：停止轮询，并尽力停止由驭爪管理的 Gateway（vs-process）。
   */
  async function shutdown(options?: { stopManagedGateway?: boolean }) {
    if (shuttingDown) return;
    shuttingDown = true;
    stopPolling();

    const shouldStopGateway =
      options?.stopManagedGateway !== false
      && (managedBy.value === 'vs-process' || (processOnline.value && !serviceInstalled.value));

    if (shouldStopGateway && (processOnline.value || portReady.value)) {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), SHUTDOWN_STOP_TIMEOUT_MS);
      try {
        await stopGateway({ silent: true, signal: controller.signal });
      } catch {
        // best effort on quit
      } finally {
        clearTimeout(timer);
      }
    } else {
      status.value = 'stopped';
      if (gatewayInfo.value) {
        gatewayInfo.value = {
          ...gatewayInfo.value,
          wsConnected: false,
          status: 'stopped',
        };
      }
    }
  }

  async function connectGateway() {
    userHalted.value = false;
    loading.value = true;
    try {
      const res = await gatewayApi.connectGateway();
      if (res.code !== 0) {
        status.value = 'error';
        throw new Error(res.message || '连接 Gateway 失败');
      }
      applyInfo(res.data);
      return res.data;
    } catch (error: unknown) {
      status.value = 'error';
      throw error;
    } finally {
      loading.value = false;
    }
  }

  /**
   * 应用打开时：检测已有 Gateway 并自动连接，不主动启动新实例。
   */
  async function ensureConnected() {
    try {
      const res = await gatewayApi.connectGateway();
      if (res.code === 0 && res.data) {
        applyInfo(res.data);
        startHealthWatch();
        return res.data;
      }
    } catch {
      // 回退到状态查询
    }

    try {
      const data = await fetchStatusSilent(true);
      startHealthWatch();
      return data;
    } catch {
      startHealthWatch();
      return null;
    }
  }

  async function fetchStatus() {
    try {
      const res = await gatewayApi.getStatus(true);
      if (res.code !== 0) {
        status.value = 'error';
        throw new Error(res.message || '获取状态失败');
      }
      applyInfo(res.data);
      return res.data;
    } catch (error: unknown) {
      status.value = 'error';
      throw error;
    }
  }

  async function fetchLogs(limit = 100) {
    try {
      const res = await gatewayApi.getLogs(limit);
      if (res.code !== 0) {
        throw new Error(res.message || '获取日志失败');
      }
      logs.value = res.data || [];
      return res.data;
    } catch (error: unknown) {
      const err = error as { message?: string };
      logs.value = [`获取日志失败: ${err.message || '未知错误'}`];
      throw error;
    }
  }

  async function fetchInfo() {
    try {
      const res = await gatewayApi.getInfo();
      if (res.code !== 0) {
        status.value = 'error';
        throw new Error(res.message || '获取信息失败');
      }
      applyInfo(res.data);
      return res.data;
    } catch (error: unknown) {
      status.value = 'error';
      throw error;
    }
  }

  let pollTimer: ReturnType<typeof setTimeout> | null = null;
  let shuttingDown = false;
  const backgroundHeavy = ref(false);

  function setBackgroundHeavy(heavy: boolean) {
    backgroundHeavy.value = heavy;
  }

  function isStatusFresh(maxAgeMs = 2000): boolean {
    if (!lastRefreshedAt.value) return false;
    return Date.now() - lastRefreshedAt.value.getTime() < maxAgeMs;
  }

  /** 静默同步 Gateway 状态；已有新鲜轮询结果时跳过重复请求 */
  async function syncStatus(force = false) {
    if (!force && polling.value && isStatusFresh(3000)) {
      return gatewayInfo.value;
    }
    await fetchStatusSilent(force);
    if (!userHalted.value && (isActive.value || wsConnected.value)) {
      startPolling();
    }
    return gatewayInfo.value;
  }

  async function startAndPoll(portNum?: number, daemon?: boolean) {
    loading.value = true;
    status.value = 'starting';
    try {
      await requestStart(portNum, daemon);
      startPolling(STARTUP_POLL_MS);
      const final = await waitForGatewayReady();
      if (final && isHealthy.value) {
        return final;
      }
      if (final?.status === 'failed' || final?.startupPhase === 'failed') {
        throw new Error(final.message || 'Gateway 启动失败');
      }
      return gatewayInfo.value;
    } catch (error: unknown) {
      status.value = 'error';
      throw error;
    } finally {
      loading.value = false;
      if (polling.value) {
        stopPolling();
        if (isActive.value || isStarting.value || wsConnected.value) {
          startPolling();
        }
      }
    }
  }

  async function stopAndHalt() {
    return stopGateway();
  }

  function startPolling(intervalMs?: number) {
    if (polling.value) {
      if (intervalMs == null) return;
      stopPolling();
    }
    polling.value = true;

    const tick = async () => {
      if (!polling.value) return;
      await fetchStatusSilent();
      const delay =
        intervalMs ??
        (isZombie.value
          ? 15_000
          : backgroundHeavy.value
            ? 10_000
            : isStarting.value || (isRunning.value && !wsConnected.value)
              ? 2500
              : 5000);
      pollTimer = setTimeout(tick, delay);
    };

    tick();
  }

  /** 应用打开后持续探测 Gateway；有活动实例或曾连接过时保持轮询 */
  function startHealthWatch() {
    if (polling.value) return;
    startPolling();
  }

  function stopPolling() {
    polling.value = false;
    if (pollTimer) {
      clearTimeout(pollTimer);
      pollTimer = null;
    }
  }

  function reset() {
    status.value = 'stopped';
    gatewayInfo.value = null;
    logs.value = [];
    loading.value = false;
    polling.value = false;
    lastRefreshedAt.value = null;
    userHalted.value = false;
    stopPolling();
  }

  return {
    status,
    gatewayInfo,
    logs,
    loading,
    polling,
    lastRefreshedAt,
    isRunning,
    isStarting,
    isStopping,
    isError,
    isActive,
    isHealthy,
    processOnline,
    portReady,
    endpoint,
    controlUrl,
    port,
    version,
    pid,
    uptime,
    startTime,
    message,
    startupPhase,
    startupProgress,
    startupMessage,
    isStartupInProgress,
    wsConnected,
    managedBy,
    serviceInstalled,
    managedByLabel,
    healthIssue,
    recoveryHint,
    isZombie,
    needsRecovery,
    statusLabel,
    lastRefreshedLabel,
    startGateway,
    stopGateway,
    shutdown,
    connectGateway,
    ensureConnected,
    fetchStatus,
    fetchStatusSilent,
    syncStatus,
    fetchLogs,
    fetchInfo,
    startPolling,
    startHealthWatch,
    stopPolling,
    startAndPoll,
    stopAndHalt,
    waitForGatewayReady,
    setBackgroundHeavy,
    isStatusFresh,
    reset,
  };
});
