import { ref, watch } from 'vue';
import { useNotification } from 'naive-ui';
import { useGatewayStore } from '@/stores/gateway';

/**
 * 当 Gateway 进入僵死/需恢复状态时弹出一次全局通知。
 */
export function useGatewayHealthNotify() {
  const gatewayStore = useGatewayStore();
  const notification = useNotification();
  const zombieNotified = ref(false);

  watch(
    () => gatewayStore.isZombie,
    (zombie) => {
      if (zombie && !zombieNotified.value) {
        zombieNotified.value = true;
        notification.warning({
          title: 'Gateway 可能已僵死',
          content: gatewayStore.recoveryHint || gatewayStore.message || '请以管理员权限重启 Gateway 进程',
          duration: 0,
          keepAliveOnHover: true,
        });
        return;
      }
      if (!zombie) {
        zombieNotified.value = false;
      }
    },
  );
}
