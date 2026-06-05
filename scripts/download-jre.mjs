import { createWriteStream, existsSync, readdirSync, rmSync, statSync, cpSync } from 'node:fs';
import { mkdir, rename } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execSync } from 'node:child_process';
import https from 'node:https';
import http from 'node:http';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const JRE_DIR = path.join(ROOT, 'packaging', 'jre');
const TEMP_DIR = path.join(ROOT, 'packaging', 'jre-temp');
const JAVA_BIN = process.platform === 'win32' ? 'java.exe' : 'java';

const JRE_VERSION = '17.0.13_11';

const DOWNLOAD_CANDIDATES = {
  win32: [
    `https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jre/x64/windows/OpenJDK17U-jre_x64_windows_hotspot_${JRE_VERSION}.zip`,
    `https://mirror.ghproxy.com/https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jre_x64_windows_hotspot_${JRE_VERSION}.zip`,
    `https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jre_x64_windows_hotspot_${JRE_VERSION}.zip`,
    'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk',
  ],
  darwin: [
    `https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jre/x64/mac/OpenJDK17U-jre_x64_mac_hotspot_${JRE_VERSION}.tar.gz`,
    'https://api.adoptium.net/v3/binary/latest/17/ga/mac/x64/jre/hotspot/normal/eclipse?project=jdk',
  ],
  linux: [
    `https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jre/x64/linux/OpenJDK17U-jre_x64_linux_hotspot_${JRE_VERSION}.tar.gz`,
    'https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jre/hotspot/normal/eclipse?project=jdk',
  ],
};

function getClient(url) {
  return url.startsWith('https:') ? https : http;
}

function formatBytes(bytes) {
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function downloadFile(url, destination, redirectCount = 0) {
  if (redirectCount > 8) {
    return Promise.reject(new Error('下载 JRE 时重定向次数过多'));
  }

  return new Promise((resolve, reject) => {
    const client = getClient(url);
    const request = client.get(url, { timeout: 30_000 }, (response) => {
      if ([301, 302, 303, 307, 308].includes(response.statusCode ?? 0) && response.headers.location) {
        const nextUrl = new URL(response.headers.location, url).toString();
        downloadFile(nextUrl, destination, redirectCount + 1).then(resolve).catch(reject);
        response.resume();
        return;
      }

      if (response.statusCode !== 200) {
        reject(new Error(`HTTP ${response.statusCode}`));
        response.resume();
        return;
      }

      const total = Number(response.headers['content-length'] || 0);
      let downloaded = 0;
      let lastLogAt = 0;
      const fileStream = createWriteStream(destination);

      response.on('data', (chunk) => {
        downloaded += chunk.length;
        const now = Date.now();
        if (now - lastLogAt > 3000) {
          if (total > 0) {
            const percent = ((downloaded / total) * 100).toFixed(1);
            process.stdout.write(`\r[download-jre] ${percent}% (${formatBytes(downloaded)} / ${formatBytes(total)})`);
          } else {
            process.stdout.write(`\r[download-jre] ${formatBytes(downloaded)} downloaded`);
          }
          lastLogAt = now;
        }
      });

      response.pipe(fileStream);
      fileStream.on('finish', () => {
        process.stdout.write('\n');
        fileStream.close(() => resolve());
      });
      fileStream.on('error', reject);
    });

    request.on('timeout', () => {
      request.destroy(new Error('下载超时'));
    });
    request.on('error', reject);
  });
}

function extractArchive(archivePath) {
  rmSync(TEMP_DIR, { recursive: true, force: true });
  mkdir(TEMP_DIR, { recursive: true });

  if (archivePath.endsWith('.zip')) {
    const escapedArchive = archivePath.replace(/'/g, "''");
    const escapedTemp = TEMP_DIR.replace(/'/g, "''");
    execSync(
      `powershell -NoProfile -Command "Expand-Archive -Path '${escapedArchive}' -DestinationPath '${escapedTemp}' -Force"`,
      { stdio: 'inherit' }
    );
    return;
  }

  execSync(`tar -xzf "${archivePath}" -C "${TEMP_DIR}"`, { stdio: 'inherit' });
}

function findExtractedJreRoot() {
  const entries = readdirSync(TEMP_DIR, { withFileTypes: true });
  const rootDir = entries.find((entry) => entry.isDirectory());
  if (!rootDir) {
    throw new Error('JRE 解压目录为空');
  }
  return path.join(TEMP_DIR, rootDir.name);
}

async function tryDownloadFrom(url, archivePath) {
  console.log(`[download-jre] Trying ${url}`);
  rmSync(archivePath, { force: true });
  await downloadFile(url, archivePath);

  const size = statSync(archivePath).size;
  if (size < 10 * 1024 * 1024) {
    throw new Error(`下载文件过小 (${formatBytes(size)})，可能不是有效的 JRE 包`);
  }
}

function copyLocalJavaHome() {
  const javaHome = process.env.JAVA_HOME;
  if (!javaHome || !existsSync(javaHome)) {
    return false;
  }

  const localJava = path.join(javaHome, 'bin', JAVA_BIN);
  if (!existsSync(localJava)) {
    return false;
  }

  rmSync(JRE_DIR, { recursive: true, force: true });
  cpSync(javaHome, JRE_DIR, { recursive: true });
  console.log(`[download-jre] 使用本机 JAVA_HOME 作为内置运行时 -> ${JRE_DIR}`);
  return true;
}

async function main() {
  if (existsSync(path.join(JRE_DIR, 'bin', JAVA_BIN))) {
    console.log('[download-jre] Bundled JRE already exists, skipping download');
    return;
  }

  if (!process.argv.includes('--no-local-java') && copyLocalJavaHome()) {
    return;
  }

  const candidates = DOWNLOAD_CANDIDATES[process.platform];
  if (!candidates) {
    throw new Error(`当前平台暂不支持自动下载 JRE: ${process.platform}`);
  }

  const archiveExtension = process.platform === 'win32' ? 'zip' : 'tar.gz';
  const archivePath = path.join(ROOT, 'packaging', `jre-download.${archiveExtension}`);
  await mkdir(path.dirname(archivePath), { recursive: true });

  let lastError = null;
  for (const url of candidates) {
    try {
      await tryDownloadFrom(url, archivePath);
      lastError = null;
      break;
    } catch (error) {
      lastError = error;
      const message = error instanceof Error ? error.message : String(error);
      console.warn(`[download-jre] Failed: ${message}`);
    }
  }

  if (lastError) {
    throw lastError;
  }

  console.log('[download-jre] Extracting JRE...');
  extractArchive(archivePath);

  const extractedRoot = findExtractedJreRoot();
  rmSync(JRE_DIR, { recursive: true, force: true });
  await rename(extractedRoot, JRE_DIR);
  rmSync(TEMP_DIR, { recursive: true, force: true });
  rmSync(archivePath, { force: true });

  if (!existsSync(path.join(JRE_DIR, 'bin', JAVA_BIN))) {
    throw new Error('JRE 安装不完整，未找到 java 可执行文件');
  }

  console.log(`[download-jre] JRE ready at ${JRE_DIR}`);
}

main().catch((error) => {
  const message = error instanceof Error ? error.message : String(error);
  console.error(`[download-jre] ${message}`);
  process.exit(1);
});
