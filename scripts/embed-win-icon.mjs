import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { rcedit } from 'rcedit';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const outputDir = process.env.PACK_OUTPUT_DIR || 'out';
const EXE_PATH = path.join(ROOT, outputDir, 'win-unpacked', 'Clawhelm.exe');
const ICON_PATH = path.join(ROOT, 'frontend', 'resources', 'icon.ico');

if (!existsSync(EXE_PATH)) {
  console.error(`[embed-win-icon] 未找到可执行文件: ${EXE_PATH}`);
  process.exit(1);
}

if (!existsSync(ICON_PATH)) {
  console.error(`[embed-win-icon] 未找到图标: ${ICON_PATH}，请先运行 npm run icons:export`);
  process.exit(1);
}

await rcedit(EXE_PATH, { icon: ICON_PATH });
console.log(`[embed-win-icon] 已写入图标 -> ${EXE_PATH}`);
