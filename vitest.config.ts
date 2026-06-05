import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';
import { resolve } from 'path';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'frontend/src/renderer'),
      '@shared': resolve(__dirname, 'frontend/src/shared'),
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    include: ['frontend/tests/unit/**/*.{test,spec}.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      reportsDirectory: 'frontend/tests/coverage',
      include: ['frontend/src/renderer/**/*.{ts,vue}'],
    },
  },
});
