import { spawn, spawnSync } from 'node:child_process';

import { existsSync, mkdirSync, readdirSync, rmSync } from 'node:fs';

import { tmpdir } from 'node:os';

import path from 'node:path';

import { fileURLToPath } from 'node:url';



const __dirname = path.dirname(fileURLToPath(import.meta.url));

const ROOT = path.resolve(__dirname, '..');

const BACKEND_DIR = path.join(ROOT, 'backend');

const HEALTH_URL = 'http://127.0.0.1:8089/api/v1/actuator/health';

const SMOKE_TIMEOUT_MS = 90_000;



function findBuiltJar() {

  const targetDir = path.join(BACKEND_DIR, 'target');

  const jars = readdirSync(targetDir).filter(

    (name) => name.startsWith('openclaw-vs-backend-') && name.endsWith('.jar') && !name.includes('.original')

  );



  if (jars.length === 0) {

    throw new Error(`未在 ${targetDir} 找到后端 JAR，请先执行 mvn package`);

  }



  jars.sort();

  return path.join(targetDir, jars[jars.length - 1]);

}



function resolveJava() {

  const javaHome = process.env.JAVA_HOME;

  if (javaHome) {

    const name = process.platform === 'win32' ? 'java.exe' : 'java';

    const candidate = path.join(javaHome, 'bin', name);

    if (existsSync(candidate)) {

      return candidate;

    }

  }

  return 'java';

}



function buildBackend() {

  const useClean = process.env.PACK_CLEAN === '1';

  const mvnArgs = useClean ? ['clean', 'package', '-DskipTests'] : ['package', '-DskipTests'];

  console.log(`[prepare-packaging] Building backend (${useClean ? 'clean + ' : ''}package)...`);

  const mvnCommand = process.platform === 'win32' ? 'mvn.cmd' : 'mvn';

  const result = spawnSync(mvnCommand, mvnArgs, {

    cwd: BACKEND_DIR,

    stdio: 'inherit',

    shell: process.platform === 'win32',

  });



  if (result.status !== 0) {

    throw new Error('后端 Maven 构建失败');

  }

}



function ensurePackagingDirs() {

  mkdirSync(path.join(ROOT, 'packaging', 'jre', 'bin'), { recursive: true });

}



function sleep(ms) {

  return new Promise((resolve) => setTimeout(resolve, ms));

}



function toH2Path(filePath) {

  return filePath.replace(/\\/g, '/');

}



async function smokeTestDesktopJar(jarPath) {

  const smokeRoot = path.join(tmpdir(), `clawhelm-smoke-${Date.now()}`);

  const dbPath = toH2Path(path.join(smokeRoot, 'data', 'openclaw_vs'));

  mkdirSync(path.join(smokeRoot, 'data'), { recursive: true });



  const java = resolveJava();

  const args = [

    '-jar',

    jarPath,

    '--spring.profiles.active=desktop',

    '--server.port=8089',

    `--spring.datasource.url=jdbc:h2:file:${dbPath};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`,
    '--spring.datasource.username=sa',
    '--spring.datasource.password=',
    '--spring.datasource.driver-class-name=org.h2.Driver',

    `--app.deployment.workspace=${toH2Path(path.join(smokeRoot, 'deployments'))}`,

  ];



  console.log('[prepare-packaging] Desktop profile smoke test...');

  const proc = spawn(java, args, {

    cwd: smokeRoot,

    stdio: 'ignore',

    windowsHide: true,

  });



  let exitCode = null;

  proc.on('exit', (code) => {

    exitCode = code;

  });



  const startedAt = Date.now();

  try {

    while (Date.now() - startedAt < SMOKE_TIMEOUT_MS) {

      if (exitCode !== null) {

        throw new Error(`后端冒烟启动失败，退出码 ${exitCode}`);

      }



      try {

        const response = await fetch(HEALTH_URL);

        if (response.ok) {

          const body = await response.json();

          if (body.status === 'UP') {

            console.log('[prepare-packaging] Desktop smoke test passed');

            return;

          }

        }

      } catch {

        // still booting

      }

      await sleep(500);

    }

    throw new Error('后端冒烟启动超时');

  } finally {

    if (!proc.killed) {

      proc.kill();

      if (process.platform === 'win32' && proc.pid) {

        spawnSync('taskkill', ['/pid', String(proc.pid), '/f', '/t'], { stdio: 'ignore', windowsHide: true });

      }

    }

    rmSync(smokeRoot, { recursive: true, force: true });

  }

}



const backendOnly = process.argv.includes('--backend-only');



try {

  ensurePackagingDirs();

  if (!backendOnly || !existsSync(path.join(BACKEND_DIR, 'target'))) {

    buildBackend();

  }

  const jarPath = findBuiltJar();
  console.log(`[prepare-packaging] Backend JAR ready -> ${jarPath}`);

  if (process.env.PACK_SMOKE === '1') {
    await smokeTestDesktopJar(jarPath);
  }

} catch (error) {

  const message = error instanceof Error ? error.message : String(error);

  console.error(`[prepare-packaging] ${message}`);

  process.exit(1);

}


