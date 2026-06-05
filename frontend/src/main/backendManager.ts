import { spawn, type ChildProcess } from 'child_process';
import { existsSync, mkdirSync, readdirSync, readFileSync } from 'fs';
import { join } from 'path';
import { app } from 'electron';
import { DEFAULT_BACKEND_PORT, getBackendHealthUrl } from '@shared/backend';

/** Spring profile for packaged desktop production (see application-desktop.yml). */
const DESKTOP_SPRING_PROFILE = 'desktop';

const JAR_NAME = 'openclaw-vs-backend.jar';
const STARTUP_TIMEOUT_MS = 120_000;

let backendProcess: ChildProcess | null = null;

function toH2Path(filePath: string): string {
  return filePath.replace(/\\/g, '/');
}

function getJarPath(): string {
  const backendDir = join(process.resourcesPath, 'backend');
  const fixed = join(backendDir, JAR_NAME);
  if (existsSync(fixed)) {
    return fixed;
  }

  const versioned = readdirSync(backendDir)
    .filter(
      (name) =>
        name.startsWith('openclaw-vs-backend') && name.endsWith('.jar') && !name.includes('.original')
    )
    .sort();

  if (versioned.length > 0) {
    return join(backendDir, versioned[versioned.length - 1]);
  }

  return fixed;
}

function getBundledJava(): string | null {
  const javaName = process.platform === 'win32' ? 'java.exe' : 'java';
  const bundled = join(process.resourcesPath, 'jre', 'bin', javaName);
  return existsSync(bundled) ? bundled : null;
}

function getJavaHomeCandidate(): string | null {
  const javaHome = process.env.JAVA_HOME;
  if (!javaHome) return null;

  const javaName = process.platform === 'win32' ? 'java.exe' : 'java';
  const candidate = join(javaHome, 'bin', javaName);
  return existsSync(candidate) ? candidate : null;
}

async function verifyJava(javaPath: string): Promise<boolean> {
  return new Promise((resolve) => {
    const proc = spawn(javaPath, ['-version'], { stdio: 'ignore', windowsHide: true });
    proc.on('error', () => resolve(false));
    proc.on('close', (code) => resolve(code === 0));
  });
}

async function resolveJava(): Promise<string | null> {
  const bundled = getBundledJava();
  if (bundled && (await verifyJava(bundled))) {
    return bundled;
  }

  const javaHomeCandidate = getJavaHomeCandidate();
  if (javaHomeCandidate && (await verifyJava(javaHomeCandidate))) {
    return javaHomeCandidate;
  }

  if (await verifyJava('java')) {
    return 'java';
  }

  return null;
}

function readLogTail(logFile: string, maxLines = 12): string | null {
  if (!existsSync(logFile)) {
    return null;
  }

  try {
    const lines = readFileSync(logFile, 'utf8').split(/\r?\n/).filter(Boolean);
    if (lines.length === 0) {
      return null;
    }
    return lines.slice(-maxLines).join('\n');
  } catch {
    return null;
  }
}

function formatStartupFailure(logFile: string, reason: string): string {
  const tail = readLogTail(logFile);
  const parts = [reason, `日志文件：${logFile}`];
  if (tail) {
    parts.push('', '最近日志：', tail);
  }
  return parts.join('\n');
}

async function waitForHealthy(
  port: number,
  logFile: string,
  getExitCode: () => number | null
): Promise<void> {
  const healthUrl = getBackendHealthUrl(port);
  const startedAt = Date.now();

  while (Date.now() - startedAt < STARTUP_TIMEOUT_MS) {
    const exitCode = getExitCode();
    if (exitCode !== null) {
      throw new Error(
        formatStartupFailure(
          logFile,
          `后端进程已退出（退出码 ${exitCode}）。常见原因：数据库迁移失败或端口 ${port} 被占用。`
        )
      );
    }

    try {
      const response = await fetch(healthUrl);
      if (!response.ok) {
        await sleep(500);
        continue;
      }

      const body = (await response.json()) as { status?: string };
      if (body.status === 'UP') {
        return;
      }
    } catch {
      // Backend still booting.
    }

    await sleep(500);
  }

  throw new Error(formatStartupFailure(logFile, '后端服务启动超时，请查看日志后重试'));
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function isBackendManaged(): boolean {
  return app.isPackaged;
}

export async function startBackend(port = DEFAULT_BACKEND_PORT): Promise<void> {
  if (!app.isPackaged) {
    return;
  }

  const jarPath = getJarPath();
  if (!existsSync(jarPath)) {
    throw new Error(`未找到后端程序包：${jarPath}`);
  }

  const java = await resolveJava();
  if (!java) {
    throw new Error(
      '未找到 Java 运行环境。请安装 Java 17 或更高版本，或在打包时包含内置 JRE。'
    );
  }

  const dataDir = join(app.getPath('userData'), 'appdata');
  const dbDir = join(dataDir, 'data');
  const logsDir = join(dataDir, 'logs');
  const deploymentsDir = join(dataDir, 'deployments');

  for (const dir of [dataDir, dbDir, logsDir, deploymentsDir]) {
    mkdirSync(dir, { recursive: true });
  }

  const dbPath = toH2Path(join(dbDir, 'openclaw_vs'));
  const logFilePath = join(logsDir, 'backend.log');
  const logFile = toH2Path(logFilePath);
  const workspace = toH2Path(deploymentsDir);
  let backendExitCode: number | null = null;

  const args = [
    '-jar',
    jarPath,
    `--spring.profiles.active=${DESKTOP_SPRING_PROFILE}`,
    `--server.port=${port}`,
    `--spring.datasource.url=jdbc:h2:file:${dbPath};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`,
    '--spring.datasource.username=sa',
    '--spring.datasource.password=',
    '--spring.datasource.driver-class-name=org.h2.Driver',
    `--app.deployment.workspace=${workspace}`,
    `--logging.file.name=${logFile}`,
  ];

  backendProcess = spawn(java, args, {
    cwd: dataDir,
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true,
  });

  backendProcess.stdout?.on('data', (chunk: Buffer) => {
    console.log('[backend]', chunk.toString().trimEnd());
  });

  backendProcess.stderr?.on('data', (chunk: Buffer) => {
    console.error('[backend]', chunk.toString().trimEnd());
  });

  backendProcess.on('exit', (code, signal) => {
    backendExitCode = code;
    if (code !== null && code !== 0) {
      console.error(`[backend] exited with code ${code}`);
    }
    if (signal) {
      console.error(`[backend] killed by signal ${signal}`);
    }
    backendProcess = null;
  });

  backendProcess.on('error', (error) => {
    console.error('[backend] failed to start:', error);
  });

  await waitForHealthy(port, logFilePath, () => backendExitCode);
}

export function stopBackend(): void {
  if (!backendProcess || backendProcess.killed) {
    backendProcess = null;
    return;
  }

  const pid = backendProcess.pid;
  backendProcess.kill();

  if (process.platform === 'win32' && pid) {
    try {
      spawn('taskkill', ['/pid', String(pid), '/f', '/t'], { stdio: 'ignore', windowsHide: true });
    } catch {
      // Best effort cleanup for bundled Java backend.
    }
  }

  backendProcess = null;
}
