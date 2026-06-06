import { onMounted, onUnmounted } from 'vue';
import { useGatewayStore } from '@/stores/gateway';
import { useOpenClawChatStore } from '@/stores/openclawChat';

const SHUTDOWN_TIMEOUT_MS = 8_000;

/**
 * 页面关闭 / 应用退出时优雅停止轮询，并尽力停止由驭爪拉起的 Gateway。
 */
export function useAppShutdown() {
  const gatewayStore = useGatewayStore();
  const openClawChatStore = useOpenClawChatStore();
  let shuttingDown = false;
  let disposeElectronQuit: (() => void) | null = null;

  async function runShutdown() {
    if (shuttingDown) return;
    shuttingDown = true;
    openClawChatStore.destroyGlobal();
    await gatewayStore.shutdown({ stopManagedGateway: true });
  }

  function onPageHide() {
    void runShutdown();
  }

  function onBeforeUnload() {
    void runShutdown();
  }

  onMounted(() => {
    window.addEventListener('pagehide', onPageHide);
    window.addEventListener('beforeunload', onBeforeUnload);

    const electronApi = window.electronAPI;
    if (electronApi?.onPrepareQuit) {
      disposeElectronQuit = electronApi.onPrepareQuit(() => {
        void runShutdown();
      });
    }
  });

  onUnmounted(() => {
    window.removeEventListener('pagehide', onPageHide);
    window.removeEventListener('beforeunload', onBeforeUnload);
    disposeElectronQuit?.();
    disposeElectronQuit = null;
    void runShutdown();
  });

  return {
    runShutdown,
    shutdownTimeoutMs: SHUTDOWN_TIMEOUT_MS,
  };
}
