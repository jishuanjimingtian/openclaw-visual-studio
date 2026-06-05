import { ref, computed, onMounted, onUnmounted } from 'vue';
import type { AppUpdateEvent, AppUpdateState, AppUpdateStatus } from '@shared/update';

const status = ref<AppUpdateStatus>('idle');
const currentVersion = ref('');
const remoteVersion = ref<string | null>(null);
const releaseNotes = ref<string | null>(null);
const progress = ref<AppUpdateState['progress']>(null);
const error = ref<string | null>(null);
const lastManualCheck = ref(false);

let unsubscribe: (() => void) | null = null;
let subscriberCount = 0;
let autoPromptVersion: string | null = null;

const showAutoPrompt = ref(false);

const updateApi = typeof window !== 'undefined' ? window.electronAPI?.update : undefined;

const isElectronPackaged = ref(false);

function applyState(snapshot: AppUpdateState): void {
  status.value = snapshot.status;
  currentVersion.value = snapshot.currentVersion;
  remoteVersion.value = snapshot.remoteVersion;
  releaseNotes.value = snapshot.releaseNotes;
  progress.value = snapshot.progress;
  error.value = snapshot.error;
}

function handleEvent(event: AppUpdateEvent): void {
  switch (event.type) {
    case 'checking':
      status.value = 'checking';
      error.value = null;
      break;
    case 'available':
      status.value = 'available';
      remoteVersion.value = event.version ?? null;
      releaseNotes.value =
        typeof event.releaseNotes === 'string' ? event.releaseNotes : null;
      error.value = null;
      if (event.manual) {
        lastManualCheck.value = true;
        showAutoPrompt.value = false;
      } else if (event.version) {
        autoPromptVersion = event.version;
        showAutoPrompt.value = true;
      }
      break;
    case 'not-available':
      status.value = 'not-available';
      remoteVersion.value = null;
      releaseNotes.value = null;
      error.value = null;
      break;
    case 'progress':
      status.value = 'downloading';
      progress.value = event.progress ?? null;
      break;
    case 'downloaded':
      status.value = 'downloaded';
      progress.value = null;
      remoteVersion.value = event.version ?? remoteVersion.value;
      break;
    case 'error':
      status.value = 'error';
      error.value = event.message ?? '检查更新失败';
      break;
    default:
      break;
  }
}

const statusLabel = computed(() => {
  switch (status.value) {
    case 'checking':
      return '正在检查更新…';
    case 'available':
      return remoteVersion.value ? `发现新版本 v${remoteVersion.value}` : '发现新版本';
    case 'not-available':
      return '当前已是最新版本';
    case 'downloading':
      return progress.value
        ? `正在下载 ${Math.round(progress.value.percent)}%`
        : '正在下载更新…';
    case 'downloaded':
      return '更新已下载，重启后完成安装';
    case 'error':
      return error.value ?? '更新检查失败';
    default:
      return '';
  }
});

async function refreshState(): Promise<void> {
  if (!updateApi) return;
  applyState(await updateApi.getState());
}

async function checkManual(): Promise<void> {
  if (!updateApi) return;
  lastManualCheck.value = true;
  showAutoPrompt.value = false;
  applyState(await updateApi.check({ manual: true }));
}

async function download(): Promise<void> {
  if (!updateApi) return;
  applyState(await updateApi.download());
}

async function dismiss(): Promise<void> {
  if (!updateApi) return;
  showAutoPrompt.value = false;
  autoPromptVersion = null;
  applyState(await updateApi.dismiss());
}

async function install(): Promise<void> {
  if (!updateApi) return;
  await updateApi.quitAndInstall();
}

function closeAutoPrompt(): void {
  showAutoPrompt.value = false;
}

export function useAppUpdate() {
  onMounted(async () => {
    if (!updateApi) return;
    if (window.electronAPI?.isPackaged) {
      try {
        isElectronPackaged.value = await window.electronAPI.isPackaged();
      } catch {
        isElectronPackaged.value = false;
      }
    }
    subscriberCount += 1;
    if (subscriberCount === 1) {
      unsubscribe = updateApi.onEvent(handleEvent);
      await refreshState();
      if (window.electronAPI?.getVersion) {
        try {
          currentVersion.value = await window.electronAPI.getVersion();
        } catch {
          /* ignore */
        }
      }
    }
  });

  onUnmounted(() => {
    if (!updateApi) return;
    subscriberCount = Math.max(0, subscriberCount - 1);
    if (subscriberCount === 0) {
      unsubscribe?.();
      unsubscribe = null;
    }
  });

  return {
    isElectronPackaged,
    status,
    currentVersion,
    remoteVersion,
    releaseNotes,
    progress,
    error,
    statusLabel,
    showAutoPrompt,
    lastManualCheck,
    autoPromptVersion,
    refreshState,
    checkManual,
    download,
    dismiss,
    install,
    closeAutoPrompt,
  };
}
