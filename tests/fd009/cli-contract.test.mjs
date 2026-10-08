import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import test from 'node:test';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const launcher = resolve(repositoryRoot, 'workers/process-typescript/scripts/run-role.ps1');

function launch(...args) {
  return spawnSync('powershell.exe', [
    '-NoProfile', '-NonInteractive', '-File', launcher, ...args,
  ], {
    cwd: repositoryRoot,
    encoding: 'utf8',
    timeout: 10_000,
    env: { ...process.env, AIW_WORKER_ID: '' },
  });
}

test('旧 API Worker 包和入口目录已移除', () => {
  assert.equal(existsSync(resolve(repositoryRoot, 'workers/process-typescript-with-api')), false);
  assert.equal(existsSync(launcher), true);
});

test('角色启动脚本拒绝未指定 runtime', () => {
  const result = launch('requirements');
  assert.equal(result.error, undefined);
  assert.notEqual(result.status, 0);
});

test('角色启动脚本拒绝未知 runtime', () => {
  const result = launch('requirements', 'unknown-runtime');
  assert.equal(result.error, undefined);
  assert.notEqual(result.status, 0);
});
