#!/usr/bin/env node

/**
 * 自动截图脚本 — 为 README 生成驭爪页面截图
 * 
 * 用法: node scripts/screenshot.mjs
 * 前提: 
 *   1. npm run dev 已经启动（前端 5173）
 *   2. 或者先用 npm run dev & 再跑这个
 */

import { chromium } from 'playwright';
import { fileURLToPath } from 'url';
import path from 'path';
import fs from 'fs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const screenshotDir = path.resolve(__dirname, '..', 'docs', 'screenshots');
fs.mkdirSync(screenshotDir, { recursive: true });

const BASE = 'http://localhost:5173';

const pages = [
  { path: '/#/dashboard',   name: '01-dashboard',   desc: '仪表盘' },
  { path: '/#/chat',        name: '02-chat',         desc: '对话' },
  { path: '/#/sessions',    name: '03-sessions',     desc: '会话管理' },
  { path: '/#/timers',      name: '04-timers',       desc: '定时任务' },
  { path: '/#/workflow',    name: '05-workflow',     desc: '工作流编排' },
  { path: '/#/analytics',   name: '06-analytics',    desc: '数据分析' },
  { path: '/#/config',      name: '07-config',       desc: '配置中心' },
  { path: '/#/models',      name: '08-models',       desc: '模型管理' },
  { path: '/#/marketplace', name: '09-marketplace',  desc: 'Skill 市场' },
  { path: '/#/knowledge',   name: '10-knowledge',    desc: '知识库' },
  { path: '/#/deployment',  name: '11-deployment',   desc: '部署管理' },
  { path: '/#/monitor',     name: '12-monitor',      desc: '系统监控' },
  { path: '/#/settings',    name: '13-settings',     desc: '设置' },
];

async function main() {
  console.log('🖥️  启动 Playwright 浏览器...');
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
  });
  const page = await context.newPage();

  const screenshots = [];

  for (const { path: route, name, desc } of pages) {
    const url = BASE + route;
    console.log(`📸 截图: ${desc} (${url})`);

    try {
      await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 15000 });

      // 等待 Vue 渲染（不等待后端 API）
      await page.waitForTimeout(3000);

      const filePath = path.join(screenshotDir, `${name}.png`);
      await page.screenshot({ path: filePath, fullPage: false });
      console.log(`   ✅ 已保存: ${name}.png`);
      screenshots.push({ name: `${name}.png`, desc, route });
    } catch (err) {
      console.warn(`   ⚠️  失败: ${err.message}`);
    }
  }

  await browser.close();
  console.log(`\n🎉 完成！共截图 ${screenshots.length}/${pages.length} 个页面`);
  console.log(`📁 截图目录: ${screenshotDir}`);

  // 生成 README 截图段
  const mdLines = screenshots.map(
    (s) => `![${s.desc}](docs/screenshots/${s.name})`
  );
  console.log('\n--- Markdown 片段 ---');
  console.log(mdLines.join('\n'));
}

main().catch((err) => {
  console.error('❌', err);
  process.exit(1);
});
