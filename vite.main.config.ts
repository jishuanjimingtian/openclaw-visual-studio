import { defineConfig } from 'vite';
import { resolve } from 'path';
import { builtinModules } from 'module';

export default defineConfig({
  root: 'frontend',
  resolve: {
    alias: {
      '@main': resolve(__dirname, 'frontend/src/main'),
      '@preload': resolve(__dirname, 'frontend/src/preload'),
      '@shared': resolve(__dirname, 'frontend/src/shared'),
    },
  },
  build: {
    outDir: resolve(__dirname, 'dist/main'),
    emptyOutDir: true,
    lib: {
      entry: resolve(__dirname, 'frontend/src/main/index.ts'),
      formats: ['cjs'],
      fileName: () => 'index.js',
    },
    rollupOptions: {
      external: ['electron', 'electron-updater', ...builtinModules],
    },
  },
});
