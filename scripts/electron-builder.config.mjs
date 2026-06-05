import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const JRE_JAVA = path.join(ROOT, 'packaging', 'jre', 'bin', process.platform === 'win32' ? 'java.exe' : 'java');

/** 自动更新 Release 仓库（可被 UPDATE_GITHUB_OWNER / UPDATE_GITHUB_REPO 覆盖） */
const ghOwner =
  process.env.UPDATE_GITHUB_OWNER ||
  process.env.GITHUB_REPOSITORY_OWNER ||
  'jishuanjimingtian';
const ghRepo =
  process.env.UPDATE_GITHUB_REPO ||
  (process.env.GITHUB_REPOSITORY?.split('/')[1] ?? 'openclaw-visual-studio');

/** @type {import('electron-builder').Configuration} */
const config = {
  appId: 'com.openclaw.clawhelm',
  productName: 'Clawhelm',
  copyright: 'Copyright 2025 驭爪 Studio',
  publish: process.env.UPDATE_GENERIC_URL
    ? [{ provider: 'generic', url: process.env.UPDATE_GENERIC_URL }]
    : [{
        provider: 'github',
        owner: ghOwner,
        repo: ghRepo,
        releaseType: 'release',
      }],
  directories: {
    output: 'out',
    buildResources: 'frontend/resources',
  },
  files: ['dist/**/*', 'package.json'],
  extraResources: [
    {
      from: 'backend/target',
      to: 'backend',
      filter: ['openclaw-vs-backend-*.jar', '!**/*.original'],
    },
    {
      from: 'frontend/resources/icon.ico',
      to: 'app-icon.ico',
    },
  ],
  win: {
    target: 'nsis',
    icon: 'icon.ico',
    artifactName: '${productName}-${version}-setup.${ext}',
    signAndEditExecutable: false,
    signDlls: false,
  },
  mac: {
    target: 'dmg',
    icon: 'icon.png',
    category: 'public.app-category.developer-tools',
    artifactName: '${productName}-${version}.${ext}',
  },
  linux: {
    target: 'AppImage',
    icon: 'icon.png',
    artifactName: '${productName}-${version}.${ext}',
  },
  nsis: {
    oneClick: false,
    perMachine: false,
    allowToChangeInstallationDirectory: true,
    allowElevation: true,
    createDesktopShortcut: 'always',
    createStartMenuShortcut: true,
    shortcutName: '驭爪',
    uninstallDisplayName: '驭爪 (Clawhelm)',
    installerIcon: 'icon.ico',
    uninstallerIcon: 'icon.ico',
    include: 'scripts/installer.nsh',
  },
};

if (existsSync(JRE_JAVA)) {
  config.extraResources.splice(1, 0, {
    from: 'packaging/jre',
    to: 'jre',
    filter: ['**/*'],
  });
  console.log('[electron-builder] Bundling embedded JRE');
} else {
  console.log('[electron-builder] No embedded JRE; packaged app will use system Java');
}

export default config;
