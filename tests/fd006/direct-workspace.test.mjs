import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { mkdtemp, mkdir, readFile, rm, stat, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { promisify } from 'node:util';

import * as runtime from '../../workers/process-typescript/runtime-core/index.mjs';

const execFileAsync = promisify(execFile);

async function localGit(ownedRoot, cwd, ...args) {
  const relative = path.relative(ownedRoot, cwd);
  assert.ok(relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative)), 'Git must stay in the test-owned directory');
  const result = await execFileAsync('git', args, {
    cwd,
    timeout: 5000,
    windowsHide: true,
    env: { ...process.env, GIT_TERMINAL_PROMPT: '0', GIT_CONFIG_NOSYSTEM: '1' },
  });
  return result.stdout.trim();
}

async function fixture(id, child = false) {
  const ownedRoot = await mkdtemp(path.join(os.tmpdir(), `fd006-${id}-`));
  const workspaceRoot = path.join(ownedRoot, 'registered-root');
  const repositoryPath = child ? path.join(workspaceRoot, 'registered-child') : workspaceRoot;
  const worktreeRoot = path.join(ownedRoot, 'worktrees');
  await mkdir(repositoryPath, { recursive: true });
  await writeFile(path.join(repositoryPath, 'before.txt'), 'existing user content');

  const recorder = await runtime.EvidenceRecorder.open(
    path.join(ownedRoot, 'evidence'),
    { taskId: `fd006-${id}-task`, attemptId: `fd006-${id}-attempt`, attemptNo: 1, role: 'coder', runtime: 'fd006-test' },
    { forbiddenRoots: [repositoryPath] },
  );
  const attempt = new runtime.AttemptRunner({ timeoutMs: 8000, settleTimeoutMs: 500, recorder });
  const assignment = {
    workspaceId: 'fd006-workspace',
    workspaceName: 'FD-006 test',
    workspaceRoot,
    repositoryPath,
    repositoryRef: child ? 'fd006-workspace/child' : 'fd006-workspace/root',
    mode: 'DIRECT',
    baseRevision: null,
    branchName: null,
    workspaceKey: `fd006-${id}`,
  };
  const task = { taskId: `fd006-${id}-task`, attemptId: `fd006-${id}-attempt`, attemptNo: 1, taskType: 'IMPLEMENTATION', workspace: assignment };
  const config = {
    role: 'coder',
    fallbackWorkspace: path.join(ownedRoot, 'unused-fallback'),
    repositoryRef: assignment.repositoryRef,
    repositoryRoot: repositoryPath,
    worktreeRoot,
    evidenceRoot: path.join(ownedRoot, 'evidence'),
    gitTimeoutMs: 5000,
    cleanupTimeoutMs: 2000,
  };
  return { ownedRoot, workspaceRoot, repositoryPath, worktreeRoot, attempt, assignment, task, config };
}

async function closeFixture(item, session) {
  const settled = await item.attempt.settled();
  item.attempt.dispose();
  if (!settled) throw new Error(`Test-owned directory retained because the attempt is unsettled: ${item.ownedRoot}`);
  if (session) await session.cleanup();
  assert.equal(path.resolve(path.dirname(item.ownedRoot)), path.resolve(os.tmpdir()));
  assert.ok(path.basename(item.ownedRoot).startsWith('fd006-'));
  await rm(item.ownedRoot, { recursive: true, force: true });
}

for (const target of ['root', 'child']) {
  test(`DIRECT ${target} assignment uses and preserves the registered directory without Git`, async () => {
    const item = await fixture(`direct-${target}`, target === 'child');
    let session;
    try {
      session = await runtime.WorkspaceSession.materialize(item.config, item.task, item.attempt);
      assert.equal(path.resolve(session.cwd), path.resolve(item.repositoryPath));
      await session.beforeExecution(false);
      await writeFile(path.join(session.cwd, 'written-by-worker.txt'), 'direct change');
      assert.equal(await session.finalize(item.task, false), null);
      assert.equal(await readFile(path.join(item.repositoryPath, 'before.txt'), 'utf8'), 'existing user content');
      await assert.rejects(stat(path.join(item.repositoryPath, '.git')), { code: 'ENOENT' });
      await assert.rejects(stat(item.worktreeRoot), { code: 'ENOENT' });
      await session.cleanup();
      session = null;
      assert.equal(await readFile(path.join(item.repositoryPath, 'written-by-worker.txt'), 'utf8'), 'direct change');
    } finally {
      await closeFixture(item, session);
    }
  });
}

async function initializedGitFixture(id) {
  const item = await fixture(id);
  await localGit(item.ownedRoot, item.repositoryPath, '-c', 'init.defaultBranch=main', 'init', '-q');
  await localGit(item.ownedRoot, item.repositoryPath, 'add', 'before.txt');
  await localGit(item.ownedRoot, item.repositoryPath, '-c', 'user.name=FD006 Test', '-c', 'user.email=fd006@example.invalid', 'commit', '-qm', 'baseline');
  await mkdir(item.worktreeRoot);
  item.assignment.mode = 'GIT_WORKTREE';
  item.assignment.branchName = `fd006-${id}`;
  return item;
}

test('GIT_WORKTREE materializes the selected full commit and keeps the source repository', async () => {
  const item = await initializedGitFixture('git-commit');
  let session;
  try {
    const commit = await localGit(item.ownedRoot, item.repositoryPath, 'rev-parse', 'HEAD');
    item.assignment.baseRevision = commit;
    session = await runtime.WorkspaceSession.materialize(item.config, item.task, item.attempt);
    assert.notEqual(path.resolve(session.cwd), path.resolve(item.repositoryPath));
    assert.equal(await localGit(item.ownedRoot, session.cwd, 'rev-parse', 'HEAD'), commit);
    assert.equal(await readFile(path.join(session.cwd, 'before.txt'), 'utf8'), 'existing user content');
    const worktreePath = session.cwd;
    await session.cleanup();
    session = null;
    await assert.rejects(stat(worktreePath), { code: 'ENOENT' });
    assert.equal(await readFile(path.join(item.repositoryPath, 'before.txt'), 'utf8'), 'existing user content');
  } finally {
    await closeFixture(item, session);
  }
});

test('GIT_WORKTREE rejects an unavailable full commit and preserves the source repository', async () => {
  const item = await initializedGitFixture('git-invalid');
  let session;
  try {
    item.assignment.baseRevision = 'f'.repeat(40);
    await assert.rejects(runtime.WorkspaceSession.materialize(item.config, item.task, item.attempt));
    assert.equal(await readFile(path.join(item.repositoryPath, 'before.txt'), 'utf8'), 'existing user content');
  } finally {
    await closeFixture(item, session);
  }
});
