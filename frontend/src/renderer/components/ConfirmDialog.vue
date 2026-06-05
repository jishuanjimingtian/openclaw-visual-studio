<template>
  <n-modal v-model:show="show" :mask-closable="false">
    <n-card
      style="width: 420px"
      :title="title"
      :bordered="false"
      size="small"
      role="dialog"
      aria-modal="true"
    >
      <div class="confirm-body">
        <n-icon v-if="type === 'warning'" size="48" :color="warningColor">
          <warning-outline />
        </n-icon>
        <n-icon v-else-if="type === 'danger'" size="48" :color="dangerColor">
          <close-circle-outline />
        </n-icon>
        <p>{{ content }}</p>
      </div>
      <template #footer>
        <div class="confirm-footer">
          <n-button @click="onCancel">取消</n-button>
          <n-button
            :type="type === 'danger' ? 'error' : type === 'warning' ? 'warning' : 'primary'"
            :loading="loading"
            @click="onConfirm"
          >
            {{ confirmText }}
          </n-button>
        </div>
      </template>
    </n-card>
  </n-modal>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NCard, NModal, NButton, NIcon } from 'naive-ui';
import { WarningOutline, CloseCircleOutline } from '@vicons/ionicons5';
import { ocColors } from '@/utils/chartColors';

const warningColor = ocColors.warning;
const dangerColor = ocColors.error;

const props = withDefaults(defineProps<{
  visible: boolean;
  title?: string;
  content?: string;
  type?: 'primary' | 'warning' | 'danger';
  confirmText?: string;
  loading?: boolean;
}>(), {
  title: '确认操作',
  content: '确定要执行此操作吗？',
  type: 'warning',
  confirmText: '确定',
  loading: false,
});

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void;
  (e: 'confirm'): void;
  (e: 'cancel'): void;
}>();

const show = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
});

function onConfirm() {
  emit('confirm');
}

function onCancel() {
  emit('update:visible', false);
  emit('cancel');
}
</script>

<style scoped>
.confirm-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 16px 0;
}

.confirm-body p {
  margin: 0;
  font-size: 15px;
  text-align: center;
  color: var(--n-text-color-2);
}

.confirm-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>