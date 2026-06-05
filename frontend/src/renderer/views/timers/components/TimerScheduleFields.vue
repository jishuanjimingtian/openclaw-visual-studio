<template>
  <n-space vertical :size="12">
    <n-form-item label="调度类型">
      <n-radio-group v-model:value="model.scheduleKind">
        <n-radio value="cron">Cron 表达式</n-radio>
        <n-radio value="every">固定间隔</n-radio>
        <n-radio value="at">一次性</n-radio>
      </n-radio-group>
    </n-form-item>

    <n-form-item v-if="model.scheduleKind === 'cron'" label="Cron 表达式">
      <n-input v-model:value="model.cronExpr" placeholder="0 9 * * *" />
    </n-form-item>
    <n-form-item v-if="model.scheduleKind === 'cron'" label="时区 (可选)">
      <n-input v-model:value="model.tz" placeholder="Asia/Shanghai" />
    </n-form-item>

    <n-form-item v-if="model.scheduleKind === 'every'" label="间隔 (分钟)">
      <n-input-number v-model:value="model.everyMinutes" :min="1" :max="10080" style="width: 100%" />
    </n-form-item>

    <n-form-item v-if="model.scheduleKind === 'at'" label="执行时间">
      <n-input v-model:value="model.atValue" placeholder="ISO 时间或 20m / 2h" />
      <n-text depth="3" style="font-size: 12px; margin-top: 4px; display: block">
        无时分区时按 UTC；可配合上方时区字段。
      </n-text>
    </n-form-item>

    <n-form-item v-if="model.scheduleKind === 'at'" label="成功后删除">
      <n-switch v-model:value="model.deleteAfterRun" />
    </n-form-item>
  </n-space>
</template>

<script setup lang="ts">
import { NFormItem, NInput, NInputNumber, NRadio, NRadioGroup, NSpace, NSwitch, NText } from 'naive-ui';
import type { TimerFormModel } from '../cronFormUtils';

const model = defineModel<TimerFormModel>({ required: true });
</script>
