import { chromium } from 'playwright';
import { readFileSync, copyFileSync, existsSync, readdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
import toIco from 'to-ico';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const SOURCE_CANDIDATES = [
  path.join(ROOT, 'assets', 'icon-openclaw-v3.png'),
  path.join(ROOT, 'assets', 'icon-openclaw-v2.png'),
];
const ICON_PNG = path.join(ROOT, 'frontend', 'resources', 'icon.png');
const ICON_ICO = path.join(ROOT, 'frontend', 'resources', 'icon.ico');
const PUBLIC_ICON = path.join(ROOT, 'frontend', 'public', 'icon.png');
const PROCESSED_TMP = path.join(ROOT, 'frontend', 'resources', '.icon-processed.png');

function resolveChromiumExecutable() {
  const playwrightDir = path.join(os.homedir(), 'AppData', 'Local', 'ms-playwright');
  if (!existsSync(playwrightDir)) {
    return undefined;
  }

  const chromiumDir = readdirSync(playwrightDir).find(
    (name) => name.startsWith('chromium-') && !name.includes('headless')
  );
  if (!chromiumDir) {
    return undefined;
  }

  const chromeExe = path.join(playwrightDir, chromiumDir, 'chrome-win64', 'chrome.exe');
  return existsSync(chromeExe) ? chromeExe : undefined;
}

/** 灰阶棋盘格 / 浅灰底 → 透明（从四边泛洪） */
function isBackgroundPixel(r, g, b, a) {
  if (a < 8) return true;
  if (Math.abs(r - g) > 14 || Math.abs(g - b) > 14) return false;
  const v = (r + g + b) / 3;
  return v >= 160 && v <= 255;
}

function removeCheckerboard(inputPath) {
  const png = PNG.sync.read(readFileSync(inputPath));
  const { width, height, data } = png;
  const visited = new Uint8Array(width * height);
  const queue = [];

  const push = (x, y) => {
    if (x < 0 || y < 0 || x >= width || y >= height) return;
    const i = y * width + x;
    if (visited[i]) return;
    const r = data[i * 4];
    const g = data[i * 4 + 1];
    const b = data[i * 4 + 2];
    const a = data[i * 4 + 3];
    if (!isBackgroundPixel(r, g, b, a)) return;
    visited[i] = 1;
    queue.push(i);
  };

  for (let x = 0; x < width; x++) {
    push(x, 0);
    push(x, height - 1);
  }
  for (let y = 0; y < height; y++) {
    push(0, y);
    push(width - 1, y);
  }

  while (queue.length > 0) {
    const i = queue.pop();
    data[i * 4 + 3] = 0;
    const x = i % width;
    const y = (i - x) / width;
    push(x - 1, y);
    push(x + 1, y);
    push(x, y - 1);
    push(x, y + 1);
  }

  return png;
}

function getOpaqueBounds(png) {
  const { width, height, data } = png;
  let minX = width;
  let minY = height;
  let maxX = 0;
  let maxY = 0;

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      if (data[(y * width + x) * 4 + 3] > 24) {
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
      }
    }
  }

  if (maxX <= minX || maxY <= minY) {
    return { x: 0, y: 0, w: width, h: height };
  }

  const pad = Math.round(Math.max(maxX - minX, maxY - minY) * 0.02);
  minX = Math.max(0, minX - pad);
  minY = Math.max(0, minY - pad);
  maxX = Math.min(width - 1, maxX + pad);
  maxY = Math.min(height - 1, maxY + pad);
  return { x: minX, y: minY, w: maxX - minX + 1, h: maxY - minY + 1 };
}

function cropPng(png, bounds) {
  const { x, y, w, h } = bounds;
  const out = new PNG({ width: w, height: h });
  PNG.bitblt(png, out, x, y, w, h, 0, 0);
  return out;
}

async function rasterizeSquare(pngBuffer, size = 1024) {
  const b64 = pngBuffer.toString('base64');
  const html = `<!DOCTYPE html>
<html>
  <head>
    <meta charset="UTF-8" />
    <style>
      html, body { margin: 0; padding: 0; background: transparent; }
      #wrap {
        width: ${size}px;
        height: ${size}px;
        display: flex;
        align-items: center;
        justify-content: center;
        background: transparent;
      }
      img {
        width: 98%;
        height: 98%;
        object-fit: contain;
        display: block;
      }
    </style>
  </head>
  <body>
    <div id="wrap">
      <img id="icon" src="data:image/png;base64,${b64}" alt="" />
    </div>
  </body>
</html>`;

  const executablePath = resolveChromiumExecutable();
  const browser = await chromium.launch({
    headless: true,
    ...(executablePath ? { executablePath } : {}),
  });
  const page = await browser.newPage({
    viewport: { width: size, height: size },
    deviceScaleFactor: 2,
  });
  await page.setContent(html, { waitUntil: 'load' });
  await page.locator('#wrap').screenshot({ path: ICON_PNG, omitBackground: true });
  await browser.close();
}

const sourcePath = SOURCE_CANDIDATES.find((p) => existsSync(p) && readFileSync(p).length > 100);
if (!sourcePath) {
  console.error('[export-app-icon] 请将图二保存为 assets/icon-openclaw-v3.png');
  process.exit(1);
}

console.log(`[export-app-icon] 设计稿 ${sourcePath}`);
const transparent = removeCheckerboard(sourcePath);
const bounds = getOpaqueBounds(transparent);
const cropped = cropPng(transparent, bounds);
const processedBuffer = PNG.sync.write(cropped);
writeFileSync(PROCESSED_TMP, processedBuffer);
console.log('[export-app-icon] 已去除棋盘格背景');

await rasterizeSquare(processedBuffer);
copyFileSync(ICON_PNG, PUBLIC_ICON);

const pngBuffer = readFileSync(ICON_PNG);
const icoBuffer = await toIco([pngBuffer], { resize: true, sizes: [16, 32, 48, 64, 128, 256] });
writeFileSync(ICON_ICO, icoBuffer);

const logoSvg = `<svg viewBox="0 0 32 32" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
  <image width="32" height="32" href="../../assets/icon-openclaw-v3.png" preserveAspectRatio="xMidYMid meet"/>
</svg>
`;
const appIconSvg = `<svg width="1024" height="1024" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
  <image width="1024" height="1024" href="../../assets/icon-openclaw-v3.png" preserveAspectRatio="xMidYMid meet"/>
</svg>
`;
writeFileSync(path.join(ROOT, 'frontend', 'resources', 'logo.svg'), logoSvg);
writeFileSync(path.join(ROOT, 'frontend', 'resources', 'app-icon.svg'), appIconSvg);

console.log(`[export-app-icon] ${ICON_PNG}`);
console.log(`[export-app-icon] ${ICON_ICO}`);
console.log(`[export-app-icon] ${PUBLIC_ICON}`);
