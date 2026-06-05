import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const APP_VERSION = JSON.parse(readFileSync(path.join(ROOT, 'package.json'), 'utf8')).version;
const JRE_DIR = path.join(ROOT, 'packaging', 'jre');
const EB_CONFIG = path.join(ROOT, 'scripts', 'electron-builder.config.mjs');

const isLite =
  process.argv.includes('--lite') || process.env.PACK_LITE === '1' || process.env.PACK_LITE === 'true';
const outputDir = isLite ? 'out-lite' : 'out';
const winUnpacked = path.join(outputDir, 'win-unpacked');

const ghOwner =
  process.env.UPDATE_GITHUB_OWNER ||
  process.env.GITHUB_REPOSITORY_OWNER ||
  'jishuanjimingtian';
const ghRepo =
  process.env.UPDATE_GITHUB_REPO ||
  (process.env.GITHUB_REPOSITORY?.split('/')[1] ?? 'openclaw-visual-studio');

const ICON_PNG = path.join(ROOT, 'frontend', 'resources', 'icon.png');
const ICON_ICO = path.join(ROOT, 'frontend', 'resources', 'icon.ico');

function hasPackagingIcons() {
  try {
    return (
      existsSync(ICON_PNG) &&
      existsSync(ICON_ICO) &&
      statSync(ICON_PNG).size > 100 &&
      statSync(ICON_ICO).size > 100
    );
  } catch {
    return false;
  }
}

/** --prepackaged 不会生成 app-update.yml，需在 NSIS 前手动写入 */
function ensureAppUpdateYml(unpackedDir) {
  const resourcesDir = path.join(ROOT, unpackedDir, 'resources');
  const ymlPath = path.join(resourcesDir, 'app-update.yml');
  if (!existsSync(resourcesDir)) {
    throw new Error(`打包目录不存在: ${resourcesDir}`);
  }

  let content;
  const genericUrl = process.env.UPDATE_GENERIC_URL?.trim();
  if (genericUrl) {
    const url = genericUrl.endsWith('/') ? genericUrl : `${genericUrl}/`;
    content = `provider: generic\nurl: ${url}\n`;
  } else {
    const base = `https://github.com/${ghOwner}/${ghRepo}/releases/latest/download/`;
    const mirror = process.env.UPDATE_GITHUB_MIRROR || 'https://ghfast.top/';
    const prefix = mirror.endsWith('/') ? mirror : `${mirror}/`;
    content = `provider: generic\nurl: ${prefix}${base}\n`;
  }

  writeFileSync(ymlPath, content, 'utf8');
  console.log(`[package-win] 已写入 ${ymlPath}`);
}

function run(command, args, env = process.env) {
  console.log(`\n[package-win] > ${command} ${args.join(' ')}`);
  const result = spawnSync(command, args, {
    stdio: 'inherit',
    env,
    shell: process.platform === 'win32',
  });

  if (result.status !== 0) {
    throw new Error(`命令执行失败: ${command} ${args.join(' ')}`);
  }
}

const packageEnv = {
  ...process.env,
  PACK_OUTPUT_DIR: outputDir,
  CSC_IDENTITY_AUTO_DISCOVERY: 'false',
  ELECTRON_MIRROR: process.env.ELECTRON_MIRROR || 'https://npmmirror.com/mirrors/electron/',
  ELECTRON_BUILDER_BINARIES_MIRROR:
    process.env.ELECTRON_BUILDER_BINARIES_MIRROR || 'https://npmmirror.com/mirrors/electron-builder-binaries/',
};

if (process.env.PACK_SMOKE !== '1') {
  delete packageEnv.PACK_SMOKE;
}

const ebArgs = ['--config', EB_CONFIG, `-c.directories.output=${outputDir}`];
const shouldPublish =
  process.argv.includes('--publish') ||
  process.env.PACK_PUBLISH === '1' ||
  Boolean(process.env.GH_TOKEN);

if (shouldPublish && !process.env.GH_TOKEN?.trim()) {
  console.error('\n[package-win] 发布失败：未设置 GH_TOKEN');
  console.error('[package-win] 请在 PowerShell 中先执行：');
  console.error('  $env:GH_TOKEN = "你的 GitHub PAT"');
  console.error('  npm run package:win:release');
  console.error('[package-win] 或：$env:PACK_PUBLISH = "1"; $env:GH_TOKEN = "..."; npm run package:win');
  process.exit(1);
}

try {
  if (isLite) {
    console.log('[package-win] Lite build: skipping embedded JRE (requires Java 17+ on target machine)');
    if (existsSync(JRE_DIR)) {
      rmSync(JRE_DIR, { recursive: true, force: true });
    }
  }

  if (hasPackagingIcons() && process.env.FORCE_ICON_EXPORT !== '1') {
    console.log('[package-win] 已存在 icon.png / icon.ico，跳过 export-app-icon（设置 FORCE_ICON_EXPORT=1 可强制重新生成）');
  } else {
    run('node', ['scripts/export-app-icon.mjs'], packageEnv);
  }
  run('node', ['scripts/prepare-packaging.mjs'], packageEnv);

  if (!isLite) {
    run('node', ['scripts/download-jre.mjs'], packageEnv);
  }

  run('npm', ['run', 'build'], packageEnv);
  run('npx', ['electron-builder', '--win', '--dir', ...ebArgs], packageEnv);
  run('node', ['scripts/embed-win-icon.mjs'], packageEnv);
  ensureAppUpdateYml(winUnpacked);
  const nsisArgs = ['electron-builder', '--win', 'nsis', '--prepackaged', winUnpacked, ...ebArgs];
  if (shouldPublish) {
    nsisArgs.push('--publish', 'always');
    console.log('[package-win] 将发布到配置的更新源（GitHub Releases 或 UPDATE_GENERIC_URL）');
  } else {
    console.log('[package-win] 未设置 GH_TOKEN / PACK_PUBLISH=1，仅本地打包，不会上传到 GitHub');
  }
  run('npx', nsisArgs, packageEnv);

  const installer = path.join(ROOT, outputDir, `Clawhelm-${APP_VERSION}-setup.exe`);
  if (existsSync(installer)) {
    const sizeMb = (statSync(installer).size / (1024 * 1024)).toFixed(1);
    console.log(`\n[package-win] 安装包已生成: ${installer} (${sizeMb} MB)`);
    if (isLite) {
      console.log('[package-win] 轻量版：未内置 JRE，目标机器需已安装 Java 17+');
    } else {
      console.log('[package-win] 完整版：已内置 JRE，可从桌面快捷方式「驭爪」启动');
    }
  } else {
    console.log(`\n[package-win] 打包完成，请查看目录: ${path.join(ROOT, outputDir)}`);
  }
} catch (error) {
  const message = error instanceof Error ? error.message : String(error);
  console.error(`\n[package-win] ${message}`);
  process.exit(1);
}
