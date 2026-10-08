import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import net from 'node:net';
import tls from 'node:tls';
import dns from 'node:dns';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { createApiAttemptAdapter } from '../../../workers/process-typescript-with-api/src/api-adapter.ts';
import { getProfile } from '../../../workers/process-typescript-with-api/src/profiles.ts';
import { runClaimedAttempt } from '../../../workers/process-typescript/runtime-core/index.mjs';

const repository = path.resolve(fileURLToPath(new URL('../../../', import.meta.url)));
const scratch = process.env.FD005_R19_SCRATCH;
assert.ok(scratch && process.env.FD005_TESTER_SESSION === 'fd005-tester-20261005-58dba13b');
globalThis.fetch = async () => { throw new Error('FD005_NETWORK_DISABLED'); };
const denySocket = () => { throw new Error('FD005_SOCKET_DISABLED'); };
net.connect = denySocket;
net.createConnection = denySocket;
tls.connect = denySocket;
dns.lookup = denySocket;
dns.resolve = denySocket;
const publicCases = JSON.parse(await fs.readFile(path.join(repository, 'workers/runtime-contract.examples.json'), 'utf8'));
const skillsRoot = path.join(repository, 'workers/process-typescript-with-api/skills');

async function fixture(role, id, options = {}) {
  const example = publicCases.roles.find(item => item.role === role);
  assert.ok(example, `Unknown public role: ${role}`);
  const profile = getProfile(role, example.taskType);
  const directory = path.join(scratch, id);
  const workspace = path.join(directory, 'workspace');
  await fs.mkdir(workspace, { recursive: true });
  const task = {
    ...publicCases.sharedTaskFields,
    taskId: `synthetic-${id}`,
    attemptId: `synthetic-attempt-${id}`,
    attemptNo: 1,
    taskType: example.taskType,
    subjectType: 'CHANGE', subjectId: 'synthetic-subject',
    requiredCapabilities: [example.capability], priority: 0, skillRef: null,
    leaseToken: 'SYNTHETIC_LEASE_ONLY', leaseAcquiredAt: '2026-10-05T00:00:00Z',
    leaseExpiresAt: '2026-10-05T23:59:59Z', instruction: example.instruction,
    inputs: [], workspace: null, aiGuidance: null,
  };
  const calls = [];
  const client = Object.fromEntries(['start', 'heartbeat', 'fail', 'complete'].map(method => [method, async (...args) => {
    calls.push({ method, args });
    return options.client?.[method]?.(...args);
  }]));
  const runtime = {
    execute: async (...args) => options.execute ? options.execute({ workspace, task, args }) : example.finalResponse,
    settled: async () => true,
    assertSuccessful: () => options.toolFailure ? (() => { throw new Error('SYNTHETIC_TOOL_FAILURE'); })() : undefined,
    clear: () => {},
  };
  const adapter = createApiAttemptAdapter({ role, skillsRoot }, runtime, { fixture: 'synthetic-r19' });
  const config = { role, fallbackWorkspace: workspace, evidenceRoot: path.join(directory, 'evidence'),
    heartbeatMs: 50, taskTimeoutMs: 4000, cleanupTimeoutMs: 500,
    maxInputBytes: 200000, evidenceMaxBytes: 4 * 1024 * 1024 };
  return { example, profile, directory, workspace, task, calls, client, adapter, config };
}

for (const role of ['requirements', 'architect', 'planner', 'coder', 'integrator', 'reviewer', 'verifier', 'integration-verifier', 'repairer']) {
  test(`ROLE-${role}: public task completes through API adapter`, async () => {
    const f = await fixture(role, `role-${role}`);
    const result = await runClaimedAttempt(f.config, f.client, f.task, f.adapter);
    assert.equal(result.status, 'completed');
    assert.equal(f.calls.filter(call => call.method === 'complete').length, 1);
    assert.equal(f.calls.filter(call => call.method === 'fail').length, 0);
    assert.equal(f.calls.find(call => call.method === 'complete').args[1], `api:${f.task.attemptId}`);
  });
}

test('ROLE-reviewer: mismatched task is rejected before success report', async () => {
  const f = await fixture('reviewer', 'reviewer-mismatch');
  f.task.taskType = 'IMPLEMENTATION';
  const result = await runClaimedAttempt(f.config, f.client, f.task, f.adapter);
  assert.notEqual(result.status, 'completed');
  assert.equal(f.calls.filter(call => call.method === 'complete').length, 0);
});

test('REPORT-reviewer: absent verdict cannot complete', async () => {
  const f = await fixture('reviewer', 'reviewer-verdict', { execute: () => 'No terminal verdict.' });
  const result = await runClaimedAttempt(f.config, f.client, f.task, f.adapter);
  assert.notEqual(result.status, 'completed');
  assert.equal(f.calls.filter(call => call.method === 'complete').length, 0);
});

test('READONLY-reviewer: external workspace mutation cannot complete', async () => {
  const f = await fixture('reviewer', 'reviewer-dirty', { execute: async ({ workspace }) => {
    await fs.writeFile(path.join(workspace, 'changed.txt'), 'synthetic mutation');
    return f.example.finalResponse;
  } });
  const result = await runClaimedAttempt(f.config, f.client, f.task, f.adapter);
  assert.notEqual(result.status, 'completed');
  assert.equal(f.calls.filter(call => call.method === 'complete').length, 0);
});

test('TOOL-error: a model final response cannot erase failed tool evidence', async () => {
  const f = await fixture('coder', 'tool-error', { toolFailure: true });
  const result = await runClaimedAttempt(f.config, f.client, f.task, f.adapter);
  assert.notEqual(result.status, 'completed');
  assert.equal(f.calls.filter(call => call.method === 'complete').length, 0);
});

test('REPORT-unknown: ambiguous complete is sent only once and is not converted to fail', async () => {
  const f = await fixture('coder', 'report-unknown', { client: { complete: () => { throw new Error('SYNTHETIC_RESPONSE_LOST'); } } });
  const result = await runClaimedAttempt(f.config, f.client, f.task, f.adapter);
  assert.equal(result.status, 'report-unknown');
  assert.equal(f.calls.filter(call => call.method === 'complete').length, 1);
  assert.equal(f.calls.filter(call => call.method === 'fail').length, 0);
});

async function localGit(cwd, args) {
  const child = spawn('git', args, { cwd, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '', stderr = '';
  child.stdout.on('data', chunk => { stdout += chunk.toString('utf8'); });
  child.stderr.on('data', chunk => { stderr += chunk.toString('utf8'); });
  const exit = await new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('close', resolve);
  });
  assert.equal(exit, 0, `Local Git fixture failed (${args.join(' ')}): ${stderr.slice(0, 1000)}`);
  return stdout.trim();
}

test('CLEANUP-locked-worktree: real Git deletion failure preserves evidence and prevents complete', async () => {
  const id = 'locked-worktree';
  const f = await fixture('coder', id);
  const repositoryRoot = path.join(f.directory, 'repository');
  const worktreeRoot = path.join(f.directory, 'worktrees');
  await fs.mkdir(repositoryRoot);
  await fs.mkdir(worktreeRoot);
  await localGit(repositoryRoot, ['init', '--initial-branch=main']);
  await fs.writeFile(path.join(repositoryRoot, 'baseline.txt'), 'synthetic baseline');
  await localGit(repositoryRoot, ['add', 'baseline.txt']);
  await localGit(repositoryRoot, ['-c', 'user.name=FD005 Synthetic Tester', '-c', 'user.email=fd005@invalid', 'commit', '-m', 'synthetic baseline']);
  const baseRevision = await localGit(repositoryRoot, ['rev-parse', 'HEAD']);
  f.config.repositoryRef = 'synthetic-local-only-repository';
  f.config.repositoryRoot = repositoryRoot;
  f.config.worktreeRoot = worktreeRoot;
  f.config.gitTimeoutMs = 3000;
  f.task.workspace = { repositoryRef: f.config.repositoryRef, baseRevision,
    workspaceKey: `synthetic-${id}`, branchName: `fd005-${id}` };
  let lockedPath;
  const runtime = {
    execute: async () => {
      const listing = await localGit(repositoryRoot, ['worktree', 'list', '--porcelain']);
      const names = [...listing.matchAll(/^worktree (.+)$/gm)].map(match => match[1]);
      lockedPath = names.find(name => path.resolve(name) !== path.resolve(repositoryRoot));
      assert.ok(lockedPath, 'A real attempt worktree must exist before failure injection');
      const real = await fs.realpath(lockedPath);
      const relative = path.relative(await fs.realpath(worktreeRoot), real);
      assert.ok(relative && !relative.startsWith('..') && !path.isAbsolute(relative));
      await localGit(repositoryRoot, ['worktree', 'lock', lockedPath, '--reason', 'synthetic cleanup failure']);
      return f.example.finalResponse;
    },
    settled: async () => true,
    assertSuccessful: () => {},
    clear: () => {},
  };
  f.adapter = createApiAttemptAdapter({ role: 'coder', skillsRoot }, runtime, { fixture: 'synthetic-r19-git' });
  try {
    const result = await runClaimedAttempt(f.config, f.client, f.task, f.adapter);
    assert.notEqual(result.status, 'completed');
    assert.equal(f.calls.filter(call => call.method === 'complete').length, 0);
    assert.equal(f.calls.filter(call => call.method === 'fail').length, 1);
    assert.ok(lockedPath && (await fs.stat(lockedPath)).isDirectory(), 'Failed cleanup preserves the worktree');
    const evidenceDirs = await fs.readdir(f.config.evidenceRoot);
    assert.equal(evidenceDirs.length, 1);
    const evidenceDir = path.join(f.config.evidenceRoot, evidenceDirs[0]);
    const events = (await fs.readFile(path.join(evidenceDir, 'events.jsonl'), 'utf8'))
      .split('\n').filter(Boolean).map(line => JSON.parse(line));
    const terminal = JSON.parse(await fs.readFile(path.join(evidenceDir, 'terminal.json'), 'utf8').catch(() => '{}'));
    assert.ok(events.some(event => event.event === 'workspace-cleanup' && event.status === 'preserved'));
    assert.ok(!events.some(event => event.event === 'resource-terminal' && event.status === 'settled'));
    assert.ok(!events.some(event => event.event === 'attempt-terminal' && event.status === 'ready-to-report'));
    assert.notEqual(terminal.status, 'ready-to-report');
  } finally {
    if (lockedPath) {
      const real = await fs.realpath(lockedPath).catch(() => null);
      if (real) {
        const relative = path.relative(await fs.realpath(worktreeRoot), real);
        assert.ok(relative && !relative.startsWith('..') && !path.isAbsolute(relative));
        await localGit(repositoryRoot, ['worktree', 'unlock', lockedPath]);
        await localGit(repositoryRoot, ['worktree', 'remove', '--force', '--force', lockedPath]);
      }
    }
  }
});
