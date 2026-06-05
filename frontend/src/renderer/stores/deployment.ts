import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';
import { deploymentApi } from '@/api/deployment';
import { useGatewayStore } from '@/stores/gateway';
import type {
  DeploymentTask,
  DeploymentListItem,
  PageRequest,
  EnvironmentCheckResult,
  StartDeploymentRequest,
  DeployProgress,
  SystemInfo,
  EnvironmentFixRequest,
  FixProgress,
  OpenClawInstallDiscovery,
  LinkExistingInstallRequest,
} from '@shared/types';

export const useDeploymentStore = defineStore('deployment', () => {
  // ========== 原有状态 ==========
  const tasks = ref<DeploymentTask[]>([]);
  const currentTask = ref<DeploymentTask | null>(null);
  const loading = ref(false);
  const polling = ref(false);
  const totalElements = ref(0);
  const totalPages = ref(0);
  const currentPage = ref(1);

  // ========== 部署列表状态 ==========
  const deployments = ref<DeploymentListItem[]>([]);
  const deploymentsLoading = ref(false);
  const deploymentsTotal = ref(0);
  const deploymentsPage = ref(0);
  const deploymentsSize = ref(20);
  const deploymentsTotalPages = ref(0);
  
  // ========== 一键部署新增状态 ==========
  const environmentCheck = ref<EnvironmentCheckResult[]>([]);
  const checkLoading = ref(false);
  const deployProgress = ref<DeployProgress | null>(null);
  const systemInfo = ref<SystemInfo | null>(null);
  const activeDeployId = ref<string | null>(null);
  const deployPolling = ref(false);
  const fixableItems = ref<string[]>([]);
  const fixProgress = ref<FixProgress | null>(null);
  const fixLoading = ref(false);
  const activeFixId = ref<string | null>(null);
  const fixPolling = ref(false);
  const installDiscovery = ref<OpenClawInstallDiscovery | null>(null);
  const discoveryLoading = ref(false);
  const linkLoading = ref(false);
  const envCheckedAt = ref<number | null>(null);
  const ENV_CHECK_TTL_MS = 60_000;
  const MAX_STORE_LOG_LINES = 2000;
  const SESSION_DEPLOY_KEY = 'openclaw-active-deploy-id';
  const SESSION_FIX_KEY = 'openclaw-active-fix-id';

  let deployPollTimer: ReturnType<typeof setTimeout> | null = null;
  let fixPollTimer: ReturnType<typeof setTimeout> | null = null;

  const TERMINAL_DEPLOY_STAGES = new Set(['completed', 'failed', 'cancelled', 'not_found']);
  const TERMINAL_FIX_STAGES = new Set(['completed', 'failed', 'not_found']);

  function capStoreLogs(logs: string[]): string[] {
    if (logs.length <= MAX_STORE_LOG_LINES) return logs;
    return logs.slice(-MAX_STORE_LOG_LINES);
  }

  function persistActiveIds() {
    if (activeDeployId.value) {
      sessionStorage.setItem(SESSION_DEPLOY_KEY, activeDeployId.value);
    } else {
      sessionStorage.removeItem(SESSION_DEPLOY_KEY);
    }
    if (activeFixId.value) {
      sessionStorage.setItem(SESSION_FIX_KEY, activeFixId.value);
    } else {
      sessionStorage.removeItem(SESSION_FIX_KEY);
    }
  }

  function restoreActiveIdsFromSession() {
    const savedDeployId = sessionStorage.getItem(SESSION_DEPLOY_KEY);
    const savedFixId = sessionStorage.getItem(SESSION_FIX_KEY);
    if (savedDeployId && !activeDeployId.value) activeDeployId.value = savedDeployId;
    if (savedFixId && !activeFixId.value) activeFixId.value = savedFixId;
  }

  watch(activeDeployId, persistActiveIds);
  watch(activeFixId, persistActiveIds);

  function mergeDeltaLogs(prevLogs: string[], delta: string[]): string[] {
    if (!delta.length) return prevLogs;
    return capStoreLogs([...prevLogs, ...delta]);
  }

  function applyDeployProgress(next: DeployProgress) {
    const prev = deployProgress.value;
    const delta = next.logs ?? [];
    const logs = prev ? mergeDeltaLogs(prev.logs, delta) : capStoreLogs(delta);
    if (
      prev
      && prev.stage === next.stage
      && prev.percentage === next.percentage
      && prev.currentAction === next.currentAction
      && prev.summary === next.summary
      && logs === prev.logs
    ) {
      return;
    }
    deployProgress.value = { ...next, logs };
  }

  function applyFixProgress(next: FixProgress) {
    const prev = fixProgress.value;
    const delta = next.logs ?? [];
    const logs = prev ? mergeDeltaLogs(prev.logs, delta) : capStoreLogs(delta);
    if (
      prev
      && prev.stage === next.stage
      && prev.percentage === next.percentage
      && prev.currentAction === next.currentAction
      && prev.completedItems === next.completedItems
      && logs === prev.logs
    ) {
      return;
    }
    fixProgress.value = { ...next, logs };
  }

  // ========== Computed ==========
  const runningTasks = computed(() => tasks.value.filter((t: DeploymentTask) => t.status === 'running'));
  const completedTasks = computed(() => tasks.value.filter((t: DeploymentTask) => t.status === 'completed'));
  const failedTasks = computed(() => tasks.value.filter((t: DeploymentTask) => t.status === 'failed'));

  const hasCheckFailures = computed(() => 
    environmentCheck.value.some((check) => check.status === 'fail')
  );

  const checkPassCount = computed(() => 
    environmentCheck.value.filter((check) => check.status === 'pass').length
  );

  const checkWarnCount = computed(() => 
    environmentCheck.value.filter((check) => check.status === 'warn').length
  );

  const checkFailCount = computed(() => 
    environmentCheck.value.filter((check) => check.status === 'fail').length
  );

  // ========== 原有方法 ==========
  async function fetchTasks(page = 1, size = 20) {
    loading.value = true;
    try {
      const params: PageRequest = { page, pageSize: size, sortBy: 'startTime', sortOrder: 'desc' };
      const res = await deploymentApi.listTasks(params);
      tasks.value = res.data.content;
      totalElements.value = res.data.totalElements;
      totalPages.value = res.data.totalPages;
      currentPage.value = res.data.number + 1;
    } finally {
      loading.value = false;
    }
  }

  async function fetchTask(id: string) {
    const res = await deploymentApi.getTask(id);
    currentTask.value = res.data;
    return res.data;
  }

  async function createTask(task: Partial<DeploymentTask>) {
    const res = await deploymentApi.createTask(task);
    tasks.value.unshift(res.data);
    return res.data;
  }

  async function updateTask(id: string, task: Partial<DeploymentTask>) {
    const res = await deploymentApi.updateTask(id, task);
    const idx = tasks.value.findIndex((t: DeploymentTask) => t.id === id);
    if (idx !== -1) tasks.value[idx] = res.data;
    if (currentTask.value?.id === id) currentTask.value = res.data;
    return res.data;
  }

  async function deleteTask(id: string) {
    await deploymentApi.deleteTask(id);
    tasks.value = tasks.value.filter((t: DeploymentTask) => t.id !== id);
    if (currentTask.value?.id === id) currentTask.value = null;
  }

  function startPolling(taskId: string, intervalMs = 3000) {
    polling.value = true;
    const poll = async () => {
      if (!polling.value) return;
      try {
        const res = await deploymentApi.getTask(taskId);
        const idx = tasks.value.findIndex((t: DeploymentTask) => t.id === taskId);
        if (idx !== -1) tasks.value[idx] = res.data;
        if (currentTask.value?.id === taskId) currentTask.value = res.data;
        if (res.data.status === 'completed' || res.data.status === 'failed') {
          polling.value = false;
          return;
        }
      } catch {
        // ignore poll errors
      }
      setTimeout(poll, intervalMs);
    };
    poll();
  }

  function stopPolling() {
    polling.value = false;
  }

  // ========== 一键部署新增方法 ==========
  async function checkEnvironment(force = false) {
    if (
      !force
      && envCheckedAt.value
      && Date.now() - envCheckedAt.value < ENV_CHECK_TTL_MS
      && environmentCheck.value.length
    ) {
      return;
    }
    checkLoading.value = true;
    try {
      const res = await deploymentApi.checkEnvironment();
      environmentCheck.value = res.data;
      envCheckedAt.value = Date.now();
    } finally {
      checkLoading.value = false;
    }
  }

  async function fetchSystemInfo() {
    const res = await deploymentApi.getSystemInfo();
    systemInfo.value = res.data;
  }

  async function startDeployment(request: StartDeploymentRequest) {
    const res = await deploymentApi.startDeployment(request);
    activeDeployId.value = res.data.deployId;
    deployProgress.value = null;
    persistActiveIds();
    return res.data.deployId;
  }

  async function fetchDeployProgress() {
    if (!activeDeployId.value) return null;
    const logOffset = deployProgress.value?.logs.length ?? 0;
    const res = await deploymentApi.getDeployStatus(activeDeployId.value, logOffset);
    applyDeployProgress(res.data);
    return res.data;
  }

  async function fetchDeployLogs() {
    if (!activeDeployId.value) return [];
    const res = await deploymentApi.getDeployLogs(activeDeployId.value);
    return res.data;
  }

  async function cancelDeployment() {
    if (!activeDeployId.value) return false;
    const res = await deploymentApi.cancelDeployment(activeDeployId.value);
    return res.data;
  }

  function startDeployPolling(intervalMs = 3000) {
    if (!activeDeployId.value || deployPolling.value) return;
    deployPolling.value = true;
    useGatewayStore().setBackgroundHeavy(true);

    const tick = async () => {
      if (!deployPolling.value) return;
      try {
        const progress = await fetchDeployProgress();
        if (progress && TERMINAL_DEPLOY_STAGES.has(progress.stage)) {
          stopDeployPolling();
          return;
        }
      } catch {
        // ignore poll errors
      }
      deployPollTimer = setTimeout(tick, intervalMs);
    };

    tick();
  }

  function stopDeployPolling() {
    deployPolling.value = false;
    if (deployPollTimer) {
      clearTimeout(deployPollTimer);
      deployPollTimer = null;
    }
    if (!fixPolling.value) {
      useGatewayStore().setBackgroundHeavy(false);
    }
  }

  async function resumeDeployPollingIfNeeded() {
    restoreActiveIdsFromSession();
    if (!activeDeployId.value || deployPolling.value) return;
    try {
      const progress = await fetchDeployProgress();
      if (progress && !TERMINAL_DEPLOY_STAGES.has(progress.stage)) {
        startDeployPolling();
      } else if (progress && TERMINAL_DEPLOY_STAGES.has(progress.stage)) {
        activeDeployId.value = null;
        persistActiveIds();
      }
    } catch {
      // ignore resume errors
    }
  }

  function resetDeployment() {
    activeDeployId.value = null;
    deployProgress.value = null;
    stopDeployPolling();
    persistActiveIds();
  }

  // ========== 部署列表方法 ==========

  async function fetchCurrentDeployment() {
    deploymentsLoading.value = true;
    try {
      const res = await deploymentApi.getCurrentDeployment();
      deployments.value = res.data ? [res.data] : [];
      deploymentsTotal.value = res.data ? 1 : 0;
      deploymentsPage.value = 0;
      deploymentsSize.value = 1;
      deploymentsTotalPages.value = res.data ? 1 : 0;
      return res.data;
    } finally {
      deploymentsLoading.value = false;
    }
  }
  
  async function fetchDeployments(page = 0, size = 20) {
    deploymentsLoading.value = true;
    try {
      const res = await deploymentApi.getDeploymentList(page, size);
      deployments.value = res.data.content;
      deploymentsTotal.value = res.data.totalElements;
      deploymentsTotalPages.value = res.data.totalPages;
      deploymentsPage.value = res.data.number;
      deploymentsSize.value = res.data.size;
    } finally {
      deploymentsLoading.value = false;
    }
  }

  async function deleteDeployment(id: string) {
    await deploymentApi.deleteDeployment(id);
    // 从列表中移除
    deployments.value = deployments.value.filter(d => d.id !== id);
  }

  async function discoverInstallation() {
    discoveryLoading.value = true;
    try {
      const res = await deploymentApi.discoverInstallation();
      installDiscovery.value = res.data;
      return res.data;
    } finally {
      discoveryLoading.value = false;
    }
  }

  async function linkExistingInstallation(request?: LinkExistingInstallRequest) {
    linkLoading.value = true;
    try {
      const res = await deploymentApi.linkExistingInstallation(request);
      deployments.value.unshift(res.data);
      return res.data;
    } finally {
      linkLoading.value = false;
    }
  }

  // ========== 环境修复新增方法 ==========

  async function fetchFixableItems() {
    const res = await deploymentApi.getFixableItems();
    fixableItems.value = res.data;
  }

  async function fixEnvironment(request?: EnvironmentFixRequest) {
    fixLoading.value = true;
    try {
      const res = await deploymentApi.fixEnvironment(request);
      fixProgress.value = { ...res.data, logs: capStoreLogs(res.data.logs ?? []) };
      activeFixId.value = res.data.fixId;
      persistActiveIds();
      return res.data.fixId;
    } finally {
      fixLoading.value = false;
    }
  }

  async function fetchFixProgress() {
    if (!activeFixId.value) return null;
    const logOffset = fixProgress.value?.logs.length ?? 0;
    const res = await deploymentApi.getFixProgress(activeFixId.value, logOffset);
    applyFixProgress(res.data);
    return res.data;
  }

  async function fetchFixResults() {
    if (!activeFixId.value) return null;
    const res = await deploymentApi.getFixResults(activeFixId.value);
    fixProgress.value = res.data;
    return res.data;
  }

  function startFixPolling(intervalMs = 3000) {
    if (!activeFixId.value || fixPolling.value) return;
    fixPolling.value = true;
    useGatewayStore().setBackgroundHeavy(true);

    const tick = async () => {
      if (!fixPolling.value) return;
      try {
        const progress = await fetchFixProgress();
        if (progress && TERMINAL_FIX_STAGES.has(progress.stage)) {
          stopFixPolling();
          return;
        }
      } catch {
        // ignore poll errors
      }
      fixPollTimer = setTimeout(tick, intervalMs);
    };

    tick();
  }

  function stopFixPolling() {
    fixPolling.value = false;
    if (fixPollTimer) {
      clearTimeout(fixPollTimer);
      fixPollTimer = null;
    }
    if (!deployPolling.value) {
      useGatewayStore().setBackgroundHeavy(false);
    }
  }

  async function resumeFixPollingIfNeeded() {
    restoreActiveIdsFromSession();
    if (!activeFixId.value || fixPolling.value) return;
    try {
      const progress = await fetchFixProgress();
      if (progress && !TERMINAL_FIX_STAGES.has(progress.stage)) {
        startFixPolling();
      } else if (progress && TERMINAL_FIX_STAGES.has(progress.stage)) {
        activeFixId.value = null;
        persistActiveIds();
      }
    } catch {
      // ignore resume errors
    }
  }

  function resetFix() {
    activeFixId.value = null;
    fixProgress.value = null;
    stopFixPolling();
    persistActiveIds();
  }

  return {
    // 原有状态
    tasks,
    currentTask,
    loading,
    polling,
    totalElements,
    totalPages,
    currentPage,
    
    // 部署列表状态
    deployments,
    deploymentsLoading,
    deploymentsTotal,
    deploymentsPage,
    deploymentsSize,
    deploymentsTotalPages,
    
    // 一键部署新增状态
    environmentCheck,
    checkLoading,
    deployProgress,
    systemInfo,
    activeDeployId,
    deployPolling,
    
    // 环境修复状态
    fixableItems,
    fixProgress,
    fixLoading,
    activeFixId,
    fixPolling,
    installDiscovery,
    discoveryLoading,
    linkLoading,
    
    // computed
    runningTasks,
    completedTasks,
    failedTasks,
    hasCheckFailures,
    checkPassCount,
    checkWarnCount,
    checkFailCount,
    
    // 原有方法
    fetchTasks,
    fetchTask,
    createTask,
    updateTask,
    deleteTask,
    startPolling,
    stopPolling,
    
    // 部署列表方法
    fetchDeployments,
    fetchCurrentDeployment,
    deleteDeployment,
    discoverInstallation,
    linkExistingInstallation,
    
    // 一键部署新增方法
    checkEnvironment,
    fetchSystemInfo,
    startDeployment,
    fetchDeployProgress,
    fetchDeployLogs,
    cancelDeployment,
    startDeployPolling,
    stopDeployPolling,
    resumeDeployPollingIfNeeded,
    resetDeployment,
    
    // 环境修复方法
    fetchFixableItems,
    fixEnvironment,
    fetchFixProgress,
    fetchFixResults,
    startFixPolling,
    stopFixPolling,
    resumeFixPollingIfNeeded,
    resetFix,
  };
});