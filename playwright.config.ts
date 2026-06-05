import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './frontend/tests/e2e',
  timeout: 30000,
  retries: 0,
  use: {
    baseURL: 'http://localhost:5173',
    headless: true,
    viewport: { width: 1400, height: 900 },
  },
  webServer: {
    command: 'npm run dev:renderer',
    port: 5173,
    reuseExistingServer: true,
  },
});
