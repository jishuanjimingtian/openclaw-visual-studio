<template>
  <div class="part-text">{{ text }}</div>
</template>

<script setup lang="ts">
defineProps<{ text: string }>();
</script>

<style scoped>
.part-text {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
