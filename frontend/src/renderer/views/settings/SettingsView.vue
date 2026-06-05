<template>
  <div class="page settings-page">
    <HeaderToolbar>
      <n-text v-if="lastCheckedLabel" class="refresh-hint">{{ lastCheckedLabel }}</n-text>
      <n-button quaternary size="small" :loading="refreshing" @click="refreshAll">
        <template #icon><n-icon><refresh-outline /></n-icon></template>
        刷新状态
      </n-button>
      <n-button quaternary size="small" @click="triggerImport">
        <template #icon><n-icon><cloud-upload-outline /></n-icon></template>
        导入
      </n-button>
      <n-button quaternary size="small" @click="onExport">
        <template #icon><n-icon><download-outline /></n-icon></template>
        导出
      </n-button>
    </HeaderToolbar>

    <input
      ref="importInputRef"
      type="file"
      accept="application/json,.json"
      class="settings-file-input"
      @change="onImportFile"
    />

    <div class="page-body">
      <div class="settings-pinned">
      <header class="settings-status">
        <div class="settings-status-leading">
          <span class="settings-status-dot" :class="healthDotClass" />
          <div>
            <span class="settings-status-title">{{ healthSummary }}</span>
            <span class="settings-status-sub">{{ healthSubline }}</span>
          </div>
        </div>
        <div class="settings-status-tags">
          <n-tag :type="backendOk ? 'success' : 'warning'" size="small" round :bordered="false">
            后端 {{ backendOk ? '正常' : '异常' }}
          </n-tag>
          <n-tag :type="gatewayStore.wsConnected ? 'success' : 'warning'" size="small" round :bordered="false">
            Gateway {{ gatewayStore.wsConnected ? '已连接' : '未连接' }}
          </n-tag>
          <n-tag size="small" round :bordered="false">{{ themeModeLabel }}</n-tag>
        </div>
      </header>

      <!-- 指标 -->
      <section class="settings-metrics">
        <div
          v-for="m in healthMetrics"
          :key="m.label"
          :class="['settings-metric', `settings-metric--${m.tone}`]"
        >
          <div class="settings-metric-icon">
            <n-icon :size="17"><component :is="m.icon" /></n-icon>
          </div>
          <div class="settings-metric-body">
            <span class="settings-metric-label">{{ m.label }}</span>
            <span class="settings-metric-value">{{ m.value }}</span>
          </div>
        </div>
      </section>
      </div>

      <div class="settings-scroll">
      <div class="settings-workspace">
        <div class="settings-stack">
          <!-- 通用 -->
          <section class="settings-panel">
            <header class="settings-panel-head">
              <div>
                <h2 class="settings-panel-title">
                  <n-icon size="18" class="settings-panel-title-icon"><sunny-outline /></n-icon>
                  外观与语言
                </h2>
                <p class="settings-panel-desc">主题与界面语言，修改后立即保存</p>
              </div>
            </header>
            <div class="settings-panel-body">
              <div class="settings-field-row">
                <span class="settings-field-label">主题模式</span>
                <div class="settings-field-control">
                  <n-radio-group v-model:value="themeStore.mode" @update:value="onThemeChange">
                    <n-radio-button value="system">跟随系统</n-radio-button>
                    <n-radio-button value="light">浅色</n-radio-button>
                    <n-radio-button value="dark">深色</n-radio-button>
                  </n-radio-group>
                </div>
              </div>
              <div class="settings-field-row">
                <span class="settings-field-label">界面语言</span>
                <div class="settings-field-control">
                  <n-select
                    v-model:value="appStore.lang"
                    :options="langOptions"
                    style="width: 200px"
                    @update:value="onLangChange"
                  />
                  <n-tag size="small" :bordered="false" type="info">界面文案仍为中文</n-tag>
                </div>
              </div>
            </div>
          </section>

          <!-- 通知 -->
          <section class="settings-panel settings-panel--grow">
            <header class="settings-panel-head">
              <div>
                <h2 class="settings-panel-title">
                  <n-icon size="18" class="settings-panel-title-icon"><chatbubbles-outline /></n-icon>
                  消息提醒
                </h2>
                <p class="settings-panel-desc">OpenClaw 回复完成后的应用内与桌面通知</p>
              </div>
            </header>
            <div class="settings-panel-body">
              <div class="settings-notify-grid">
                <div class="settings-notify-card">
                  <div class="settings-notify-card-head">
                    <span class="settings-notify-card-title">应用内提醒</span>
                    <n-switch v-model:value="chatNotifyEnabled" @update:value="onChatNotifyEnabledChange" />
                  </div>
                  <p class="settings-hint">不在对话页或窗口失焦时弹出应用内通知</p>
                  <n-button size="tiny" quaternary :disabled="!chatNotifyEnabled" @click="testInAppNotify">
                    发送测试
                  </n-button>
                </div>
                <div class="settings-notify-card">
                  <div class="settings-notify-card-head">
                    <span class="settings-notify-card-title">桌面通知</span>
                    <n-switch
                      v-model:value="chatNotifyDesktop"
                      :disabled="!chatNotifyEnabled"
                      @update:value="onChatNotifyDesktopChange"
                    />
                  </div>
                  <p class="settings-hint">需系统通知权限；关闭后仅保留应用内提醒</p>
                  <n-button
                    size="tiny"
                    quaternary
                    :disabled="!chatNotifyEnabled || !chatNotifyDesktop"
                    @click="testDesktopNotify"
                  >
                    发送测试
                  </n-button>
                </div>
              </div>
            </div>
          </section>
        </div>

        <!-- 连接侧栏 -->
        <aside class="settings-panel settings-panel--sticky">
          <header class="settings-panel-head">
            <div>
              <h2 class="settings-panel-title">
                <n-icon size="18" class="settings-panel-title-icon"><pulse-outline /></n-icon>
                服务连接
              </h2>
              <p class="settings-panel-desc">后端与 Gateway · 端口备忘需重启后生效</p>
            </div>
          </header>
          <div class="settings-panel-body">
            <div class="settings-connection-tiles">
              <div class="settings-conn-tile settings-conn-tile--wide">
                <span class="settings-conn-tile-k">API 地址</span>
                <span class="settings-conn-tile-v mono">{{ apiBaseUrl }}</span>
              </div>
              <div v-if="electronBackendUrl" class="settings-conn-tile settings-conn-tile--wide">
                <span class="settings-conn-tile-k">Electron 后端</span>
                <span class="settings-conn-tile-v mono">{{ electronBackendUrl }}</span>
              </div>
              <div class="settings-conn-tile">
                <span class="settings-conn-tile-k">Gateway</span>
                <n-space :size="6" align="center">
                  <n-tag :type="gatewayStore.wsConnected ? 'success' : 'warning'" size="tiny" round>
                    {{ gatewayStore.wsConnected ? 'RPC 已连接' : '未连接' }}
                  </n-tag>
                  <span class="settings-hint" style="flex: none">{{ gatewayStatusLabel }}</span>
                </n-space>
              </div>
              <div class="settings-conn-tile">
                <span class="settings-conn-tile-k">后端检测</span>
                <span class="settings-conn-tile-v">{{ backendDetail || (backendOk ? '正常' : '未检测') }}</span>
              </div>
              <div v-if="gatewayStore.endpoint" class="settings-conn-tile settings-conn-tile--wide">
                <span class="settings-conn-tile-k">端点</span>
                <span class="settings-conn-tile-v mono">{{ gatewayStore.endpoint }}</span>
              </div>
              <div v-if="chatGatewayHint" class="settings-conn-tile settings-conn-tile--wide">
                <span class="settings-conn-tile-k">对话通道</span>
                <span class="settings-hint">{{ chatGatewayHint }}</span>
              </div>
            </div>

            <n-form label-placement="top" size="small">
              <n-form-item label="后端端口（备忘）">
                <n-input-number
                  v-model:value="appStore.backendPort"
                  :min="1024"
                  :max="65535"
                  style="width: 100%"
                  @update:value="onBackendPortChange"
                />
              </n-form-item>
              <n-form-item label="Gateway 端口（备忘）">
                <n-input-number
                  v-model:value="appStore.gatewayPort"
                  :min="1024"
                  :max="65535"
                  style="width: 100%"
                  @update:value="onGatewayPortChange"
                />
                <template #feedback>
                  <span class="settings-hint">检测到 {{ detectedGatewayPort ?? '—' }}</span>
                </template>
              </n-form-item>
            </n-form>

            <div class="settings-actions-bar">
              <n-button size="small" block :loading="checkingBackend" @click="checkBackend">检测后端</n-button>
              <n-button size="small" block :loading="checkingGateway" @click="checkGateway">检测 Gateway</n-button>
              <n-button
                size="small"
                block
                :loading="gatewayStore.loading"
                :disabled="gatewayStore.wsConnected"
                @click="reconnectGateway"
              >
                重新连接 RPC
              </n-button>
              <n-button size="small" block quaternary @click="router.push('/deployment')">前往部署</n-button>
            </div>
          </div>
        </aside>
      </div>

      <!-- OpenClaw 入口 -->
      <section class="settings-panel settings-full">
        <header class="settings-panel-head">
          <div>
            <h2 class="settings-panel-title">
              <n-icon size="18" class="settings-panel-title-icon"><construct-outline /></n-icon>
              OpenClaw
            </h2>
            <p class="settings-panel-desc">Agent、模型、部署与知识库</p>
          </div>
        </header>
        <div class="settings-panel-body">
          <div class="settings-links">
            <button
              v-for="link in shortcutLinks"
              :key="link.path"
              type="button"
              class="settings-link"
              @click="router.push(link.path)"
            >
              <span class="settings-link-icon">
                <n-icon :size="18"><component :is="link.icon" /></n-icon>
              </span>
              <span class="settings-link-body">
                <span class="settings-link-title">{{ link.title }}</span>
                <span class="settings-link-desc">{{ link.desc }}</span>
              </span>
              <n-icon class="settings-link-chevron" :size="16"><chevron-forward-outline /></n-icon>
            </button>
          </div>
        </div>
      </section>

      <!-- 数据管理 -->
      <section class="settings-panel settings-full">
        <header class="settings-panel-head">
          <div>
            <h2 class="settings-panel-title">
              <n-icon size="18" class="settings-panel-title-icon"><folder-open-outline /></n-icon>
              数据管理
            </h2>
            <p class="settings-panel-desc">导出/导入偏好、清理缓存或恢复默认</p>
          </div>
        </header>
        <div class="settings-panel-body">
          <n-alert type="warning" :bordered="false" class="settings-inline-alert">
            清除缓存仅删除会话临时数据；恢复默认不会修改 openclaw.json。
          </n-alert>
          <div class="settings-data-grid">
            <button type="button" class="settings-data-action" @click="onExport">
              <span class="settings-data-action-title">导出设置</span>
              <span class="settings-data-action-desc">保存为 JSON，含主题、通知与端口备忘</span>
            </button>
            <button type="button" class="settings-data-action" @click="triggerImport">
              <span class="settings-data-action-title">导入设置</span>
              <span class="settings-data-action-desc">从 JSON 恢复已导出的偏好</span>
            </button>
            <button type="button" class="settings-data-action" @click="confirmClearCache">
              <span class="settings-data-action-title">清除会话缓存</span>
              <span class="settings-data-action-desc">清理 sessionStorage 中的 OpenClaw 临时数据</span>
            </button>
            <button type="button" class="settings-data-action settings-data-action--danger" @click="confirmClearAll">
              <span class="settings-data-action-title">恢复默认</span>
              <span class="settings-data-action-desc">重置主题、通知与端口备忘</span>
            </button>
          </div>
        </div>
      </section>

      <!-- 关于 -->
      <section class="settings-panel settings-full">
        <header class="settings-panel-head">
          <div>
            <h2 class="settings-panel-title">
              <n-icon size="18" class="settings-panel-title-icon"><cube-outline /></n-icon>
              关于
            </h2>
            <p class="settings-panel-desc">{{ APP_TAGLINE }}</p>
          </div>
        </header>
        <div class="settings-panel-body">
          <div class="settings-about-grid">
            <div v-for="item in aboutItems" :key="item.label" class="settings-about-item">
              <span class="settings-about-k">{{ item.label }}</span>
              <span class="settings-about-v">{{ item.value }}</span>
            </div>
          </div>
          <div v-if="appUpdate.isElectronPackaged" class="settings-update-row">
            <n-space align="center" wrap>
              <n-button
                size="small"
                :loading="appUpdate.status.value === 'checking'"
                @click="onCheckUpdate"
              >
                检查更新
              </n-button>
              <n-text v-if="appUpdate.statusLabel.value" class="settings-hint">
                {{ appUpdate.statusLabel.value }}
              </n-text>
            </n-space>
            <n-space
              v-if="appUpdate.status.value === 'available' || appUpdate.status.value === 'downloaded'"
              class="settings-update-actions"
              size="small"
            >
              <n-button
                v-if="appUpdate.status.value === 'available'"
                type="primary"
                size="small"
                @click="onDownloadUpdate"
              >
                立即更新
              </n-button>
              <n-button
                v-if="appUpdate.status.value === 'available'"
                size="small"
                quaternary
                @click="onDismissUpdate"
              >
                稍后
              </n-button>
              <n-button
                v-if="appUpdate.status.value === 'downloaded'"
                type="primary"
                size="small"
                @click="onInstallUpdate"
              >
                重启安装
              </n-button>
            </n-space>
            <n-progress
              v-if="appUpdate.status.value === 'downloading'"
              type="line"
              :percentage="updateProgressPercent"
              style="max-width: 320px; margin-top: 8px"
            />
          </div>
        </div>
      </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, type Component } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage, useDialog } from 'naive-ui';
import {
  NForm, NFormItem, NRadioGroup, NRadioButton, NSelect, NInputNumber,
  NButton, NIcon, NSpace, NTag, NText, NAlert, NSwitch, NProgress,
} from 'naive-ui';
import {
  DownloadOutline, RefreshOutline, CloudUploadOutline,
  SunnyOutline, ChatbubblesOutline, PulseOutline,
  ConstructOutline, FolderOpenOutline, CubeOutline,
  OptionsOutline, HardwareChipOutline, BookOutline,
  ServerOutline, DesktopOutline,
  ChevronForwardOutline,
} from '@vicons/ionicons5';
import { APP_NAME_ZH, APP_NAME_EN, APP_TAGLINE, APP_TEAM } from '@shared/brand';
import { getApiBaseUrl, getBackendHealthUrl } from '@shared/backend';
import HeaderToolbar from '@/components/common/HeaderToolbar.vue';
import { useThemeStore } from '@/stores/theme';
import { useAppSettingsStore } from '@/stores/appSettings';
import { useGatewayStore } from '@/stores/gateway';
import { openclawChatApi } from '@/api/openclawChat';
import {
  loadChatNotifyPrefs,
  setChatNotifyEnabled,
  setChatNotifyDesktop,
  requestDesktopNotifyPermission,
  showDesktopNotification,
  notifyQueue,
  notifyTick,
} from '@/utils/chatNotify';
import {
  downloadSettingsJson,
  applySettingsImport,
  clearOpenClawSessionCaches,
  resetAppSettingsToDefaults,
} from '@/utils/appSettingsIO';
import type { ThemeMode } from '@/stores/theme';
import type { AppLang } from '@/stores/appSettings';
import { useAppUpdate } from '@/composables/useAppUpdate';

const message = useMessage();
const appUpdate = useAppUpdate();
const dialog = useDialog();
const router = useRouter();
const themeStore = useThemeStore();
const appStore = useAppSettingsStore();
const gatewayStore = useGatewayStore();

const langOptions = [
  { label: '简体中文', value: 'zh-CN' as AppLang },
  { label: 'English', value: 'en-US' as AppLang },
];

const shortcutLinks: { title: string; desc: string; path: string; icon: Component }[] = [
  { title: '配置中心', desc: 'Agent 角色与全局 Prompt', path: '/config', icon: OptionsOutline },
  { title: '模型配置', desc: '主模型与 Provider', path: '/models', icon: HardwareChipOutline },
  { title: 'OpenClaw 部署', desc: '安装、Gateway 与运行环境', path: '/deployment', icon: ConstructOutline },
  { title: '知识库', desc: '工作区文件与检索', path: '/knowledge', icon: BookOutline },
];

const importInputRef = ref<HTMLInputElement | null>(null);
const chatNotifyEnabled = ref(true);
const chatNotifyDesktop = ref(true);
const backendOk = ref(false);
const backendDetail = ref('');
const checkingBackend = ref(false);
const checkingGateway = ref(false);
const refreshing = ref(false);
const chatGatewayHint = ref('');
const electronBackendUrl = ref('');
const appVersion = ref('v0.1.0');
const lastCheckedAt = ref<Date | null>(null);

const updateProgressPercent = computed(() =>
  Math.round(appUpdate.progress.value?.percent ?? 0),
);

async function onCheckUpdate(): Promise<void> {
  await appUpdate.checkManual();
  if (appUpdate.status.value === 'not-available') {
    message.success('当前已是最新版本');
  } else if (appUpdate.status.value === 'error') {
    message.error(appUpdate.error.value ?? '检查更新失败');
  }
}

function onDownloadUpdate(): void {
  void appUpdate.download();
}

function onDismissUpdate(): void {
  void appUpdate.dismiss();
  message.info('已跳过此版本，新版本发布后将再次提醒');
}

function onInstallUpdate(): void {
  void appUpdate.install();
}

const apiBaseUrl = computed(() => getApiBaseUrl(appStore.backendPort));
const detectedGatewayPort = computed(
  () => gatewayStore.port || gatewayStore.gatewayInfo?.port || null,
);
const lastCheckedLabel = computed(() => {
  if (!lastCheckedAt.value) return '';
  return `${lastCheckedAt.value.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })} 已检测`;
});

const gatewayStatusLabel = computed(() => {
  const s = gatewayStore.status;
  const map: Record<string, string> = {
    running: '运行中',
    stopped: '已停止',
    starting: '启动中',
    stopping: '停止中',
    error: '异常',
    unknown: '未知',
  };
  return map[s] ?? s;
});

const runtimeLabel = computed(() => {
  if (typeof window !== 'undefined' && window.electronAPI) {
    return `Electron · ${window.electronAPI.platform}`;
  }
  return '浏览器';
});

const themeModeLabel = computed(() => {
  const m = themeStore.mode;
  if (m === 'system') return '主题 · 跟随系统';
  if (m === 'dark') return '主题 · 深色';
  return '主题 · 浅色';
});

const systemHealth = computed(() => {
  if (backendOk.value && gatewayStore.wsConnected) return 'ok';
  if (backendOk.value || gatewayStore.wsConnected) return 'partial';
  return 'off';
});

const healthDotClass = computed(() => {
  if (systemHealth.value === 'ok') return 'is-ok';
  if (systemHealth.value === 'partial') return 'is-partial';
  return 'is-off';
});

const healthSummary = computed(() => {
  if (systemHealth.value === 'ok') return '服务状态良好';
  if (systemHealth.value === 'partial') return '部分服务可用';
  return '服务未就绪';
});

const healthSubline = computed(() => {
  const parts: string[] = [];
  if (lastCheckedAt.value) {
    parts.push(
      `上次检测 ${lastCheckedAt.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`,
    );
  }
  parts.push(runtimeLabel.value);
  return parts.join(' · ');
});

const healthMetrics = computed(() => [
  {
    label: '后端 API',
    value: backendOk.value ? '正常' : '不可用',
    tone: backendOk.value ? 'ok' : 'warn',
    icon: ServerOutline,
  },
  {
    label: 'Gateway RPC',
    value: gatewayStore.wsConnected ? '已连接' : '未连接',
    tone: gatewayStore.wsConnected ? 'rpc' : 'warn',
    icon: PulseOutline,
  },
  {
    label: '对话通道',
    value: chatGatewayHint.value
      ? (chatGatewayHint.value.includes('正常') ? '正常' : '异常')
      : '—',
    tone: chatGatewayHint.value?.includes('正常') ? 'ok' : 'warn',
    icon: ChatbubblesOutline,
  },
  {
    label: '运行环境',
    value: runtimeLabel.value,
    tone: 'runtime',
    icon: DesktopOutline,
  },
]);

const aboutItems = computed(() => [
  { label: '应用名称', value: APP_NAME_ZH },
  { label: '英文名', value: APP_NAME_EN },
  { label: '版本', value: appVersion.value },
  { label: '运行环境', value: runtimeLabel.value },
  { label: '前端', value: 'Vue 3 + Naive UI' },
  { label: '后端', value: 'Spring Boot 3 + Java 21' },
  { label: '桌面壳', value: 'Electron 33' },
  { label: '制作', value: APP_TEAM },
]);

function onThemeChange(mode: ThemeMode) {
  themeStore.setMode(mode);
  message.success('主题已更新');
}

function onLangChange(lang: AppLang) {
  appStore.setLang(lang);
  message.success('语言偏好已保存（界面文案将逐步支持）');
}

function onBackendPortChange(v: number | null) {
  if (v != null) appStore.setBackendPort(v);
}

function onGatewayPortChange(v: number | null) {
  if (v != null) appStore.setGatewayPort(v);
}

function onChatNotifyEnabledChange(value: boolean) {
  setChatNotifyEnabled(value);
  if (!value) message.info('已关闭 OpenClaw 回复提醒');
}

async function onChatNotifyDesktopChange(value: boolean) {
  setChatNotifyDesktop(value);
  if (!value) return;
  const perm = await requestDesktopNotifyPermission();
  if (perm === 'denied') {
    message.warning('系统通知权限被拒绝，将仅显示应用内通知');
    chatNotifyDesktop.value = false;
    setChatNotifyDesktop(false);
  } else if (perm === 'unsupported') {
    message.info('当前环境不支持系统桌面通知');
    chatNotifyDesktop.value = false;
    setChatNotifyDesktop(false);
  }
}

function testInAppNotify() {
  notifyQueue.value = [
    ...notifyQueue.value,
    {
      id: crypto.randomUUID(),
      sessionKey: 'test',
      sessionTitle: '设置 · 测试',
      preview: '这是一条应用内通知测试，用于确认提醒样式与开关生效。',
      isError: false,
      runId: 'test-notify',
    },
  ];
  notifyTick.value += 1;
  message.success('已发送应用内测试通知');
}

function testDesktopNotify() {
  showDesktopNotification(
    `${APP_NAME_ZH} · 测试`,
    '若能看到系统通知，说明桌面提醒已配置正确。',
  );
  message.info('已尝试发送桌面通知');
}

async function checkBackend() {
  checkingBackend.value = true;
  backendDetail.value = '';
  try {
    const url = getBackendHealthUrl(appStore.backendPort);
    const res = await fetch(url, { signal: AbortSignal.timeout(8000) });
    if (res.ok) {
      backendOk.value = true;
      const json = await res.json().catch(() => null);
      const status = json?.status ?? 'UP';
      backendDetail.value = `健康 ${status}`;
    } else {
      backendOk.value = false;
      backendDetail.value = `HTTP ${res.status}`;
    }
  } catch (e) {
    backendOk.value = false;
    backendDetail.value = e instanceof Error ? e.message : '连接失败';
  } finally {
    checkingBackend.value = false;
    lastCheckedAt.value = new Date();
  }
}

async function checkGateway() {
  checkingGateway.value = true;
  chatGatewayHint.value = '';
  try {
    await gatewayStore.syncStatus(true);
    const chatRes = await openclawChatApi.getStatus();
    const chat = chatRes.data;
    chatGatewayHint.value = chat.gatewayConnected
      ? `对话通道正常 · 默认 ${chat.defaultSessionKey}`
      : '对话通道未连接';
    if (detectedGatewayPort.value) {
      appStore.setGatewayPort(detectedGatewayPort.value);
    }
  } catch {
    chatGatewayHint.value = '无法获取（后端不可用）';
  } finally {
    checkingGateway.value = false;
    lastCheckedAt.value = new Date();
  }
}

async function reconnectGateway() {
  try {
    await gatewayStore.connectGateway();
    message.success('已请求重新连接 Gateway');
    await checkGateway();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '连接失败');
  }
}

async function refreshAll() {
  refreshing.value = true;
  try {
    await Promise.all([checkBackend(), checkGateway()]);
  } finally {
    refreshing.value = false;
  }
}

function onExport() {
  downloadSettingsJson();
  message.success('设置已导出');
}

function triggerImport() {
  importInputRef.value?.click();
}

async function onImportFile(ev: Event) {
  const input = ev.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  try {
    const text = await file.text();
    const json = JSON.parse(text) as unknown;
    const err = applySettingsImport(json);
    if (err) {
      message.error(err);
      return;
    }
    const prefs = loadChatNotifyPrefs();
    chatNotifyEnabled.value = prefs.enabled;
    chatNotifyDesktop.value = prefs.desktop;
    message.success('设置已导入');
    await refreshAll();
  } catch {
    message.error('无法解析配置文件');
  }
}

function confirmClearCache() {
  dialog.warning({
    title: '清除会话缓存',
    content: '将清除 sessionStorage 中的 OpenClaw 会话临时数据，不影响已保存的设置与 openclaw.json。',
    positiveText: '清除',
    negativeText: '取消',
    onPositiveClick: () => {
      const n = clearOpenClawSessionCaches();
      message.success(n > 0 ? `已清除 ${n} 项缓存` : '没有需要清除的缓存');
    },
  });
}

function confirmClearAll() {
  dialog.error({
    title: '恢复默认设置',
    content: '将恢复主题、语言备忘、通知开关、端口备忘，并清除会话缓存。不会删除 openclaw.json。',
    positiveText: '确认恢复',
    negativeText: '取消',
    onPositiveClick: () => {
      resetAppSettingsToDefaults();
      const prefs = loadChatNotifyPrefs();
      chatNotifyEnabled.value = prefs.enabled;
      chatNotifyDesktop.value = prefs.desktop;
      message.success('已恢复默认设置');
      void refreshAll();
    },
  });
}

onMounted(async () => {
  const prefs = loadChatNotifyPrefs();
  chatNotifyEnabled.value = prefs.enabled;
  chatNotifyDesktop.value = prefs.desktop;

  if (window.electronAPI?.getBackendUrl) {
    try {
      electronBackendUrl.value = await window.electronAPI.getBackendUrl();
    } catch {
      electronBackendUrl.value = '';
    }
  }
  if (window.electronAPI?.getVersion) {
    try {
      const v = await window.electronAPI.getVersion();
      appVersion.value = v ? `v${v}` : 'v0.1.0';
    } catch {
      /* keep default */
    }
  }

  gatewayStore.startPolling();
  await refreshAll();
});
</script>

<style scoped lang="scss">
@use './settings.scss';
@use './settings-panels.scss';
@use './settings-shortcuts.scss';
</style>

<style lang="scss">
.settings-file-input {
  display: none;
}

.settings-page code {
  font-family: var(--oc-config-font-mono);
  font-size: 11px;
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--oc-config-code-bg);
  color: var(--oc-config-code-fg);
  border: 1px solid var(--oc-config-code-border);
}
</style>
