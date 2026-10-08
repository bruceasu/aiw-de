import * as runtime from '../../../workers/process-typescript/runtime-core/index.mjs';
import { assert, fs, path, behavior, outcome } from './harness.mjs';
import { workspaceFixture, git, closeFixture, gitFixture } from './workspace-fixture.mjs';

behavior('WORKSPACE-fallback', async () => {
  const fixture = await workspaceFixture('fallback');
  let session;
  try {
    session = await runtime.WorkspaceSession.materialize(fixture.config, { taskId: 'synthetic', attemptId: 'synthetic-1', workspace: null }, fixture.attempt);
    assert.equal(session.cwd, fixture.workspace); assert.equal(session.assignment, null);
    await session.beforeExecution(false); assert.equal(await session.finalize({}, false), null);
  } finally { await closeFixture(fixture, session); }
  assert.ok((await fs.stat(fixture.workspace)).isDirectory(), 'Fallback workspace is caller-owned');
});
behavior('WORKSPACE-readonly-baseline', async () => {
  const fixture = await workspaceFixture('readonly-baseline');
  let session;
  try {
    await fs.writeFile(path.join(fixture.workspace, 'input.txt'), 'baseline');
    session = await runtime.WorkspaceSession.materialize(fixture.config, { taskId: 'synthetic', attemptId: 'synthetic-1', workspace: null }, fixture.attempt);
    await session.beforeExecution(true); await fs.writeFile(path.join(session.cwd, 'input.txt'), 'synthetic external change');
    await assert.rejects(session.validateReadOnly(true));
  } finally { await closeFixture(fixture, session); }
});
behavior('WORKSPACE-git-materialize', async () => {
  const fixture = await gitFixture('materialize'); let session;
  try {
    session = await runtime.WorkspaceSession.materialize(fixture.config, fixture.task, fixture.attempt);
    assert.notEqual(session.cwd, fixture.repositoryRoot); assert.ok(runtime.isInside(fixture.worktreeRoot, session.cwd));
    assert.deepEqual(session.assignment, fixture.assignment); assert.equal(await git(session.cwd, ['rev-parse', 'HEAD']), fixture.baseRevision);
    assert.equal(await fs.readFile(path.join(session.cwd, 'baseline.txt'), 'utf8'), 'synthetic baseline');
  } finally { await closeFixture(fixture, session); }
});
behavior('WORKSPACE-git-finalize', async () => {
  const fixture = await gitFixture('finalize', true); let session;
  try {
    session = await runtime.WorkspaceSession.materialize(fixture.config, fixture.task, fixture.attempt);
    await session.beforeExecution(false); await fs.writeFile(path.join(session.cwd, 'approved.txt'), 'synthetic approved change');
    const result = await session.finalize(fixture.task, false);
    assert.ok(result && result.resultRevision !== fixture.baseRevision); assert.deepEqual(result.changedFiles, ['approved.txt']);
    assert.equal(await git(session.cwd, ['rev-parse', 'HEAD']), result.resultRevision);
    assert.ok(typeof result.publicationRef === 'string' && result.publicationRef.length > 0);
    assert.equal(await git(fixture.remote, ['rev-parse', `refs/heads/${fixture.assignment.branchName}`]), result.resultRevision);
    const tracked = await git(session.cwd, ['ls-files']); assert.ok(!tracked.includes('evidence')); assert.ok(!tracked.includes('events.jsonl'));
    assert.ok((await fs.stat(fixture.recorder.directory)).isDirectory());
  } finally { await closeFixture(fixture, session); }
});
behavior('WORKSPACE-base-reject', async () => {
  const fixture = await gitFixture('base-reject'); fixture.task.workspace.baseRevision = 'f'.repeat(40);
  let session;
  try { const result = await outcome(runtime.WorkspaceSession.materialize(fixture.config, fixture.task, fixture.attempt)); session = result.value; assert.equal(result.rejected, true); }
  finally { await closeFixture(fixture, session); }
});
