import { defineConfig } from 'vite';
import { resolve } from 'path';
import { builtinModules } from 'module';

export default defineConfig({
  root: 'frontend',
  resolve: {
    alias: {
      '@shared': resolve(__dirname, 'frontend/src/shared'),
    },
  },
  build: {
    outDir: resolve(__dirname, 'dist/preload'),
    emptyOutDir: true,
    lib: {
      entry: resolve(__dirname, 'frontend/src/preload/index.ts'),
      formats: ['cjs'],
      fileName: () => 'index.js',
    },
    rollupOptions: {
      external: ['electron', ...builtinModules],
    },
  },
});
