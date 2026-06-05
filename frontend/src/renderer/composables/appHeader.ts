import { ref, type MaybeRefOrGetter, toValue, watchEffect, onUnmounted } from 'vue';

/** 路由 meta.description 之外的动态顶栏副标题（离开页面时自动清除） */
export const headerDescriptionOverride = ref<string | undefined>(undefined);

export function useHeaderDescription(source: MaybeRefOrGetter<string | undefined>) {
  watchEffect(() => {
    headerDescriptionOverride.value = toValue(source);
  });
  onUnmounted(() => {
    headerDescriptionOverride.value = undefined;
  });
}
