import { test, expect } from '@playwright/test';

test('应用启动并加载仪表盘', async ({ page }) => {
  await page.goto('http://localhost:5173');
  await expect(page.locator('text=仪表盘')).toBeVisible();
  await expect(page.locator('text=OpenClaw 运行概览')).toBeVisible();
});

test('侧边栏导航', async ({ page }) => {
  await page.goto('http://localhost:5173');
  await page.click('text=会话管理');
  await expect(page.locator('text=会话管理模块开发中')).toBeVisible();
  await page.click('text=工作流编排');
  await expect(page.locator('text=工作流模块开发中')).toBeVisible();
});
