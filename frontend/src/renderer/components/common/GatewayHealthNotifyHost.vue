<template>
  <span class="gateway-health-notify-host" aria-hidden="true" />
</template>

<script setup lang="ts">
import { useGatewayHealthNotify } from '@/composables/useGatewayHealthNotify';

useGatewayHealthNotify();
</script>
