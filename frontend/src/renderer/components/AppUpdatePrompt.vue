<template>
  <n-modal
    v-model:show="visible"
    preset="dialog"
    :title="title"
    :mask-closable="false"
    :close-on-esc="!downloading"
    :show-icon="false"
    style="width: 420px; max-width: 92vw"
    @after-leave="onAfterLeave"
  >
    <p class="app-update-desc">
      发现新版本 <strong>v{{ remoteVersion }}</strong>，当前版本 v{{ currentVersion }}。
    </p>
    <pre v-if="releaseNotes" class="app-update-notes">{{ releaseNotes }}</pre>
    <n-progress
      v-if="downloading"
      type="line"
      :percentage="progressPercent"
      :show-indicator="true"
      style="margin-top: 12px"
    />
    <p v-if="downloading && speedLabel" class="app-update-speed">{{ speedLabel }}</p>
    <p v-if="downloaded" class="app-update-hint">安装包已就绪，重启应用即可完成更新。</p>

    <template #action>
      <n-space>
        <n-button v-if="!downloading && !downloaded" @click="onLater">稍后</n-button>
        <n-button
          v-if="!downloading && !downloaded"
          type="primary"
          @click="onDownload"
        >
          立即更新
        </n-button>
        <n-button v-if="downloaded" type="primary" @click="onInstall">重启安装</n-button>
        <n-button v-if="downloading" :loading="true" disabled>下载中…</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue';
import { NModal, NButton, NSpace, NProgress } from 'naive-ui';
import { useAppUpdate } from '@/composables/useAppUpdate';

const {
  showAutoPrompt,
  remoteVersion,
  currentVersion,
  releaseNotes,
  status,
  progress,
  download,
  dismiss,
  install,
  closeAutoPrompt,
} = useAppUpdate();

const visible = computed({
  get: () => showAutoPrompt.value && Boolean(remoteVersion.value),
  set: (v: boolean) => {
    if (!v) closeAutoPrompt();
  },
});

const downloading = computed(() => status.value === 'downloading');
const downloaded = computed(() => status.value === 'downloaded');
const progressPercent = computed(() => Math.round(progress.value?.percent ?? 0));

const speedLabel = computed(() => {
  const p = progress.value;
  if (!p || p.bytesPerSecond <= 0) return '';
  const mbps = p.bytesPerSecond / (1024 * 1024);
  const speed = mbps >= 0.1 ? `${mbps.toFixed(1)} MB/s` : `${Math.round(p.bytesPerSecond / 1024)} KB/s`;
  if (p.total > 0) {
    const done = (p.transferred / (1024 * 1024)).toFixed(1);
    const total = (p.total / (1024 * 1024)).toFixed(1);
    return `${speed} · ${done} / ${total} MB`;
  }
  return speed;
});

const title = computed(() =>
  downloaded.value ? '更新已就绪' : `发现新版本 v${remoteVersion.value ?? ''}`,
);

watch(downloaded, (ready) => {
  if (ready && remoteVersion.value) {
    showAutoPrompt.value = true;
  }
});

function onLater(): void {
  void dismiss();
}

function onDownload(): void {
  void download();
}

function onInstall(): void {
  void install();
}

function onAfterLeave(): void {
  closeAutoPrompt();
}
</script>

<style scoped>
.app-update-desc {
  margin: 0 0 8px;
  line-height: 1.5;
  color: var(--n-text-color);
}

.app-update-notes {
  margin: 0;
  padding: 10px 12px;
  max-height: 160px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--n-action-color);
  border-radius: 6px;
}

.app-update-speed {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--n-text-color-3);
}

.app-update-hint {
  margin: 12px 0 0;
  font-size: 13px;
  color: var(--n-text-color-2);
}
</style>
