<template>
  <n-space vertical :size="12">
    <n-alert v-if="sessionMode === 'main'" type="info" :show-icon="false">
      主会话任务通常不配置 Runner 投递；Agent 可通过 message 工具自行发送。
    </n-alert>

    <template v-else>
      <n-form-item label="投递模式">
        <n-radio-group v-model:value="model.deliveryMode">
          <n-radio value="announce">Announce（回退投递）</n-radio>
          <n-radio value="webhook">Webhook</n-radio>
          <n-radio value="none">无 Runner 投递</n-radio>
        </n-radio-group>
      </n-form-item>

      <template v-if="model.deliveryMode === 'webhook'">
        <n-form-item label="Webhook URL">
          <n-input v-model:value="model.webhookUrl" placeholder="https://..." />
        </n-form-item>
      </template>

      <template v-else-if="model.deliveryMode === 'announce'">
        <n-form-item label="频道">
          <n-input v-model:value="model.channel" placeholder="telegram / slack / last" />
        </n-form-item>
        <n-form-item label="目标">
          <n-input v-model:value="model.to" placeholder="频道 ID 或 telegram:123" />
        </n-form-item>
        <n-form-item label="Thread ID (可选)">
          <n-input v-model:value="model.threadId" />
        </n-form-item>
        <n-form-item label="尽力投递">
          <n-switch v-model:value="model.bestEffortDeliver" />
        </n-form-item>
      </template>
    </template>
  </n-space>
</template>

<script setup lang="ts">
import { NAlert, NFormItem, NInput, NRadio, NRadioGroup, NSpace, NSwitch } from 'naive-ui';
import type { TimerFormModel } from '../cronFormUtils';

defineProps<{ sessionMode: string }>();
const model = defineModel<TimerFormModel>({ required: true });
</script>
