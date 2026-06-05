import { spawnSync } from 'node:child_process';

const result = spawnSync(process.execPath, ['scripts/download-jre.mjs'], {
  stdio: 'inherit',
  shell: false,
});

if (result.status !== 0) {
  console.warn('[package] JRE 下载失败，将回退到系统 Java 运行时');
}
