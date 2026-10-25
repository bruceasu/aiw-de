import * as runtime from '../../../extern-workers/process-typescript/runtime-core/index.mjs';
import { assert, fs, path, sandbox } from './harness.mjs';
import { appendDiagnostic } from './diagnostics.mjs';

export async function workspaceFixture(id) {
  const fixture = await sandbox(id);
  const recorder = await runtime.EvidenceRecorder.open(path.join(fixture.directory, 'evidence'), { taskId: `synthetic-${id}`, attemptId: `synthetic-${id}-1`, attemptNo: 1, role: 'coder', runtime: 'synthetic-test-only' }, { forbiddenRoots: [fixture.workspace] });
  const attempt = new runtime.AttemptRunner({ timeoutMs: 8000, settleTimeoutMs: 500, recorder });
  const config = { role: 'coder', fallbackWorkspace: fixture.workspace, evidenceRoot: path.join(fixture.directory, 'evidence'), gitTimeoutMs: 1000, cleanupTimeoutMs: 500 };
  return { ...fixture, recorder, attempt, config };
}
export async function git(cwd, args) {
  assert.ok(process.env.FD005_GIT_GUARD, 'Authorized Git guard required');
  const result = await runtime.runCommand(process.env.FD005_GIT_GUARD, args, { cwd, timeoutMs: 1000, maxBytes: 4096, exact: true, allowFailure: true });
  appendDiagnostic('git-fixture-calls.jsonl', { argv: args, cwd, exitCode: result.exitCode,
    stdoutBytes: Buffer.byteLength(result.stdout), stdoutDigest: runtime.sha256(result.stdout), stderr: result.stderr.slice(0,4096),
    complete: result.complete, processTree: result.processTree });
  assert.equal(result.exitCode,0, 'Isolated Git fixture command failed; use retained stderr/argv to separate setup from product');
  return result.stdout.trim();
}
export async function closeFixture(fixture, session) {
  const settled = await fixture.attempt.settled();
  fixture.attempt.dispose();
  if (!settled) {
    await fs.appendFile(path.join(process.env.FD005_RESULT_ROOT, 'resource-uncertain.jsonl'), JSON.stringify({ syntheticTestOnly: true, code: 'WORKSPACE_RESOURCE_NOT_SETTLED' }) + '\n');
    throw new Error('WORKSPACE_RESOURCE_NOT_SETTLED: owned workspace retained');
  }
  if (session) await session.cleanup();
}
export async function gitFixture(id, publish = false, seed) {
  const fixture = seed || await workspaceFixture(id);
  const repositoryRoot = path.join(fixture.directory, 'git-repository'), worktreeRoot = path.join(fixture.directory, 'worktrees');
  await fs.mkdir(repositoryRoot); await fs.mkdir(worktreeRoot);
  await git(repositoryRoot, ['init', '--initial-branch=main']);
  await fs.writeFile(path.join(repositoryRoot, 'baseline.txt'), 'synthetic baseline');
  await git(repositoryRoot, ['add', 'baseline.txt']); await git(repositoryRoot, ['commit', '-m', 'Synthetic test baseline']);
  const baseRevision = await git(repositoryRoot, ['rev-parse', 'HEAD']);
  const assignment = { repositoryRef: 'synthetic-local-only-repository', baseRevision, workspaceKey: `synthetic-${id}`, branchName: `fd005-${id}` };
  Object.assign(fixture.config, { gitTimeoutMs: 1000, repositoryRef: assignment.repositoryRef, repositoryRoot, worktreeRoot });
  const task = { ...fixture.task, taskId: `synthetic-${id}`, attemptId: `synthetic-${id}-1`, attemptNo: 1, taskType: 'IMPLEMENTATION', workspace: assignment };
  let remote;
  if (publish) {
    remote = path.join(fixture.directory, 'local-only-remote.git'); await fs.mkdir(remote);
    await git(remote, ['init', '--bare']); await git(repositoryRoot, ['remote', 'add', 'synthetic', remote]);
    fixture.config.gitPublishRemote = 'synthetic';
  }
  return { ...fixture, repositoryRoot, worktreeRoot, baseRevision, assignment, task, remote };
}
