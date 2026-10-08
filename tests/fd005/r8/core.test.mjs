import * as runtime from '../../../workers/process-typescript/runtime-core/index.mjs';
import { roles } from './cases.mjs';
import { assert, fs, path, behavior, sandbox, deferred, within, wait, outcome, assertDenied, directoryText, root } from './harness.mjs';

async function hostFixture(id, readOnly = false, extra = {}) {
  const paths = await sandbox(id);
  const recorder = await runtime.EvidenceRecorder.open(path.join(paths.directory, 'evidence'), {
    taskId: `synthetic-${id}`, attemptId: `synthetic-${id}-1`, attemptNo: 1,
    role: id, runtime: 'synthetic-test-only',
  }, { forbiddenRoots: [paths.workspace] });
  const coordinator = await runtime.ResourceCoordinator.forWorkspace(paths.workspace, { maxActive: 2, maxQueue: 32 });
  const controller = new AbortController();
  const host = new runtime.ToolHost({ cwd: paths.workspace, readOnly, allowShell: false, signal: controller.signal,
    recorder, coordinator, maxOutputBytes: 1024, toolTimeoutMs: 1000, abort: reason => controller.abort(new runtime.RuntimeFault(reason)), ...extra });
  return { ...paths, recorder, coordinator, controller, host };
}
const readDefinition = {
  name: 'synthetic-read', file: true,
  validate: value => typeof value?.path === 'string',
  run: async (_, context) => ({ content: await fs.readFile(context.file, 'utf8'), complete: true, status: 'success' }),
};
const writeDefinition = {
  name: 'synthetic-write', write: true, file: true,
  validate: value => typeof value?.path === 'string' && typeof value?.text === 'string',
  run: async (args, context) => { await fs.writeFile(context.file, args.text); return { content: 'written', complete: true, status: 'success' }; },
};

for (const [role, , readOnly] of roles) {
  behavior(`ROLE-${role}-tool-success`, async () => {
    const fixture = await hostFixture(role, readOnly);
    await fs.writeFile(path.join(fixture.workspace, 'input.txt'), 'synthetic Chinese 输入');
    const output = JSON.parse(await fixture.host.execute(readDefinition, { path: 'input.txt' }, { callId: `read-${role}` }));
    assert.equal(output.complete, true);
    assert.ok(JSON.stringify(output).includes('synthetic Chinese 输入'));
    fixture.host.assertSuccessful();
  });
  behavior(`ROLE-${role}-tool-failure`, async () => {
    const fixture = await hostFixture(role, readOnly);
    const result = await outcome(fixture.host.execute({ name: 'synthetic-failing', run: async () => { throw new Error('SYNTHETIC_IO_FAILURE'); } }, {}, { callId: `failure-${role}` }));
    assertDenied(result);
    assert.throws(() => fixture.host.assertSuccessful());
  });
  behavior(`ROLE-${role}-access`, async () => {
    const fixture = await hostFixture(role, readOnly);
    const file = path.join(fixture.workspace, 'change.txt');
    await fs.writeFile(file, 'original');
    const result = await outcome(fixture.host.execute(writeDefinition, { path: 'change.txt', text: 'approved synthetic change' }, { callId: `write-${role}` }));
    if (readOnly) {
      assertDenied(result);
      assert.equal(await fs.readFile(file, 'utf8'), 'original');
    } else {
      assert.equal(result.rejected, false);
      assert.equal(await fs.readFile(file, 'utf8'), 'approved synthetic change');
    }
  });
  behavior(`ROLE-${role}-cancel`, async () => {
    const fixture = await hostFixture(role, readOnly);
    fixture.controller.abort(new runtime.RuntimeFault('EXPLICIT_TERMINATION'));
    let calls = 0;
    assertDenied(await outcome(fixture.host.execute({ name: 'synthetic-cancel', run: async () => { calls++; return 'unexpected'; } }, {})));
    assert.equal(calls, 0);
  });
}

behavior('PATH-outside', async () => {
  const fixture = await sandbox('path-outside');
  const outside = path.join(fixture.directory, 'outside.txt');
  await fs.writeFile(outside, 'unchanged');
  await assert.rejects(runtime.safePath(fixture.workspace, '../outside.txt', true));
  await assert.rejects(runtime.safePath(fixture.workspace, outside, true));
  assert.equal(await fs.readFile(outside, 'utf8'), 'unchanged');
});
behavior('PATH-git', async () => {
  const { workspace } = await sandbox('path-git');
  await fs.mkdir(path.join(workspace, '.git'));
  await assert.rejects(runtime.safePath(workspace, '.git/config', true));
});
behavior('PATH-link', async () => {
  const fixture = await sandbox('path-link');
  const outside = path.join(fixture.directory, 'outside');
  await fs.mkdir(outside);
  await fs.symlink(outside, path.join(fixture.workspace, 'alias'), process.platform === 'win32' ? 'junction' : 'dir');
  await assert.rejects(runtime.safePath(fixture.workspace, 'alias/output.txt', true));
});
behavior('PATH-hardlink', async () => {
  const { workspace } = await sandbox('path-hardlink');
  const original = path.join(workspace, 'original.txt');
  await fs.writeFile(original, 'original');
  await fs.link(original, path.join(workspace, 'alias.txt'));
  await assert.rejects(runtime.safePath(workspace, 'alias.txt', true));
  assert.equal(await fs.readFile(original, 'utf8'), 'original');
});

const fileRequest = (key, mode) => ({ mode: mode === 'S' ? 'IS' : 'IX', files: [{ key, mode }] });
async function observePair(requestA, requestB, shouldOverlap, maxActive = 2) {
  const coordinator = new runtime.ResourceCoordinator({ maxActive });
  const firstStarted = deferred(), secondStarted = deferred(), release = deferred();
  let second = false;
  const a = coordinator.run(requestA, undefined, async () => { firstStarted.resolve(); await release.promise; });
  await within(firstStarted.promise);
  const b = coordinator.run(requestB, undefined, async () => { second = true; secondStarted.resolve(); });
  try {
    if (shouldOverlap) await within(secondStarted.promise);
    else { await wait(25); assert.equal(second, false); }
  } finally { release.resolve(); }
  await within(Promise.all([a, b]));
  assert.equal(second, true);
  await coordinator.settled();
}
behavior('RESOURCE-independent', () => observePair(fileRequest('a', 'X'), fileRequest('b', 'X'), true));
behavior('RESOURCE-readers', () => observePair(fileRequest('a', 'S'), fileRequest('a', 'S'), true));
behavior('RESOURCE-same-file', () => observePair(fileRequest('a', 'X'), fileRequest('a', 'X'), false));
behavior('RESOURCE-read-write', () => observePair(fileRequest('a', 'S'), fileRequest('a', 'X'), false));
behavior('RESOURCE-shell', () => observePair(fileRequest('a', 'X'), { mode: 'X' }, false));
behavior('RESOURCE-limit', () => observePair(fileRequest('a', 'S'), fileRequest('b', 'S'), false, 1));
behavior('RESOURCE-cancel-wait', async () => {
  const coordinator = new runtime.ResourceCoordinator();
  const started = deferred(), release = deferred(), controller = new AbortController();
  const a = coordinator.run({ mode: 'X' }, undefined, async () => { started.resolve(); await release.promise; });
  await within(started.promise);
  let calls = 0;
  const b = outcome(coordinator.run({ mode: 'X' }, controller.signal, async () => { calls++; }));
  controller.abort(new runtime.RuntimeFault('EXPLICIT_TERMINATION'));
  try { assert.equal((await within(b)).rejected, true); assert.equal(calls, 0); }
  finally { release.resolve(); }
  await a;
  await coordinator.run({ mode: 'X' }, undefined, async () => { calls++; });
  assert.equal(calls, 1);
});
behavior('RESOURCE-active-cancel', async () => {
  const coordinator = new runtime.ResourceCoordinator();
  const started = deferred(), release = deferred(), controller = new AbortController();
  let second = false;
  const a = outcome(coordinator.run({ mode: 'X' }, controller.signal, async () => { started.resolve(); await release.promise; }));
  await within(started.promise);
  controller.abort(new runtime.RuntimeFault('LEASE_LOST'));
  const b = coordinator.run({ mode: 'X' }, undefined, async () => { second = true; });
  try { await wait(25); assert.equal(second, false); }
  finally { release.resolve(); }
  await within(Promise.all([a, b]));
  assert.equal(second, true);
});
behavior('RESOURCE-failure-release', async () => {
  const coordinator = new runtime.ResourceCoordinator();
  await assert.rejects(coordinator.run({ mode: 'X' }, undefined, async () => { throw new Error('synthetic failure'); }));
  let next = false;
  await within(coordinator.run({ mode: 'X' }, undefined, async () => { next = true; }));
  assert.equal(next, true);
});

behavior('TOOL-parameters', async () => {
  const fixture = await hostFixture('tool-parameters');
  let calls = 0;
  assertDenied(await outcome(fixture.host.execute({ name: 'validated', validate: value => typeof value?.path === 'string', run: async () => { calls++; return 'unexpected'; } }, { path: 12 })));
  assert.equal(calls, 0);
});
behavior('TOOL-shell-denied', async () => {
  const fixture = await hostFixture('shell-denied');
  let calls = 0;
  assertDenied(await outcome(fixture.host.execute({ name: 'synthetic-shell', shell: true, write: true, run: async () => { calls++; return 'unexpected'; } }, {})));
  assert.equal(calls, 0);
});
behavior('TOOL-nested', async () => {
  const fixture = await hostFixture('nested');
  const child = { name: 'child', run: async () => 'child-result' };
  const parent = { name: 'parent', compose: true, run: async (_, context) => context.host.execute(child, {}, { callId: 'child-call', parentCallId: context.callId }) };
  const output = await within(fixture.host.execute(parent, {}, { callId: 'parent-call' }));
  assert.ok(output.includes('child-result'));
  const evidence = await directoryText(fixture.recorder.directory);
  assert.ok(evidence.includes('child-call') && evidence.includes('parent-call'));
});
behavior('TOOL-partial', async () => {
  const fixture = await hostFixture('partial');
  const result = JSON.parse(await fixture.host.execute({ name: 'synthetic-search', run: async () => ({ content: 'partial match', complete: false, status: 'partial', mode: 'head' }) }, {}));
  assert.equal(result.complete, false);
});
behavior('TOOL-output-bound', async () => {
  const fixture = await hostFixture('output-bound', false, { maxOutputBytes: 512 });
  const output = await fixture.host.execute({ name: 'large', run: async () => '中文🧪'.repeat(2000) }, {});
  assert.ok(Buffer.byteLength(output) <= 512);
  assert.equal(JSON.parse(output).complete, false);
});
behavior('TOOL-timeout-active', async () => {
  const fixture = await hostFixture('timeout-active', false, { toolTimeoutMs: 20 });
  const started = deferred(), release = deferred();
  const operation = outcome(fixture.host.execute({ name: 'uninterruptible-io', resource: { mode: 'X' }, run: async () => { started.resolve(); await release.promise; return 'terminated'; } }, {}));
  await within(started.promise);
  try { await wait(40); assert.equal(await fixture.host.settled(10), false); }
  finally { release.resolve(); }
  await within(operation);
  assert.equal(await fixture.host.settled(100), true);
  assert.throws(() => fixture.host.assertSuccessful());
});

behavior('OUTPUT-short', async () => {
  const value = runtime.boundedText('hello\n中文', { maxBytes: 256, maxLines: 10 });
  assert.equal(value.text, 'hello\n中文'); assert.equal(value.complete, true); assert.equal(value.truncated, false);
});
behavior('OUTPUT-chinese', async () => {
  assert.equal(runtime.utf8Bytes('中文🧪'), 10);
  const value = runtime.boundedText('中文🧪'.repeat(100), { maxBytes: 160 });
  assert.ok(Buffer.byteLength(value.text) <= 160); assert.equal(value.complete, false); assert.ok(!value.text.includes('\uFFFD'));
});
behavior('OUTPUT-long-line', async () => {
  const value = runtime.boundedText('🧪'.repeat(10000), { maxBytes: 256, maxLines: 3 });
  assert.ok(Buffer.byteLength(value.text) <= 256); assert.ok(!value.text.includes('\uFFFD')); assert.equal(value.truncated, true);
});
behavior('OUTPUT-lines', async () => {
  const value = runtime.boundedText(Array.from({ length: 100 }, (_, index) => `line-${index}`).join('\n'), { maxBytes: 1024, maxLines: 5 });
  assert.ok(value.text.split('\n').length <= 5); assert.equal(value.complete, false);
});
behavior('JSON-complete', async () => assert.deepEqual(runtime.checkedJson('{"ok":true,"value":"中文"}', 128, value => value.ok === true), { ok: true, value: '中文' }));
behavior('JSON-too-large', async () => assert.throws(() => runtime.checkedJson(JSON.stringify({ value: '中文'.repeat(100) }), 32, () => true)));
behavior('JSON-invalid', async () => {
  assert.throws(() => runtime.checkedJson('{"ok":', 128, () => true));
  assert.throws(() => runtime.checkedJson('{"ok":false}', 128, value => value.ok === true));
});

behavior('EDIT-multiple', async () => {
  const plan = runtime.planEdits('alpha\nbeta\ngamma\n', [{ oldText: 'alpha', newText: 'A' }, { oldText: 'gamma', newText: 'G' }]);
  assert.equal(plan.text, 'A\nbeta\nG\n'); assert.equal(plan.count, 2);
});
async function rejectsEdit(id, original, edits) {
  const { workspace } = await sandbox(id);
  const file = path.join(workspace, 'content.txt');
  await fs.writeFile(file, original);
  await assert.rejects(runtime.editFile(file, edits, new AbortController().signal));
  assert.equal(await fs.readFile(file, 'utf8'), original);
}
behavior('EDIT-missing', () => rejectsEdit('edit-missing', 'alpha\nbeta', [{ oldText: 'alpha', newText: 'changed' }, { oldText: 'absent', newText: 'bad' }]));
behavior('EDIT-duplicate-match', () => rejectsEdit('edit-ambiguous', 'alpha\nalpha', [{ oldText: 'alpha', newText: 'bad' }]));
behavior('EDIT-overlap', () => rejectsEdit('edit-overlap', 'abcdef', [{ oldText: 'abc', newText: 'X' }, { oldText: 'bcd', newText: 'Y' }]));
behavior('EDIT-duplicate-plan', () => rejectsEdit('edit-duplicate-plan', 'unique', [{ oldText: 'unique', newText: 'X' }, { oldText: 'unique', newText: 'Y' }]));
behavior('EDIT-format', async () => {
  const { workspace } = await sandbox('edit-format');
  const file = path.join(workspace, 'format.txt');
  await fs.writeFile(file, '\uFEFFalpha\r\nbeta\r\n');
  await runtime.editFile(file, [{ oldText: 'alpha', newText: 'A' }], new AbortController().signal);
  assert.equal(await fs.readFile(file, 'utf8'), '\uFEFFA\r\nbeta\r\n');
});
behavior('EDIT-baseline', async () => {
  const { workspace } = await sandbox('edit-baseline');
  const file = path.join(workspace, 'baseline.txt');
  await fs.writeFile(file, 'new baseline');
  await assert.rejects(runtime.atomicWrite(file, 'replacement', new AbortController().signal, runtime.sha256('old baseline')));
  assert.equal(await fs.readFile(file, 'utf8'), 'new baseline');
});
behavior('EDIT-diff-bound', async () => {
  const { workspace } = await sandbox('edit-diff');
  const file = path.join(workspace, 'diff.txt');
  await fs.writeFile(file, 'original');
  const result = await runtime.editFile(file, [{ oldText: 'original', newText: '中文'.repeat(2000) }], new AbortController().signal, { maxDiffBytes: 256 });
  assert.ok(Buffer.byteLength(result.text) <= 256); assert.equal(result.complete, false);
  assert.equal(await fs.readFile(file, 'utf8'), '中文'.repeat(2000));
});
behavior('EDIT-cancel', async () => {
  const { workspace } = await sandbox('edit-cancel');
  const file = path.join(workspace, 'cancel.txt');
  await fs.writeFile(file, 'original');
  const controller = new AbortController(); controller.abort(new runtime.RuntimeFault('EXPLICIT_TERMINATION'));
  await assert.rejects(runtime.atomicWrite(file, 'changed', controller.signal));
  assert.equal(await fs.readFile(file, 'utf8'), 'original');
});

behavior('CONTEXT-sources', async () => {
  const assembler = new runtime.ContextAssembler({ maxInputBytes: 1024 });
  const id = assembler.source('/synthetic/rules.md', '规则正文', 'rules');
  assert.ok(typeof id === 'string' && id.length > 0);
  const values = [...assembler.sources.values()];
  assert.ok(values.some(value => value.path === '/synthetic/rules.md' && value.digest === runtime.sha256('规则正文') && value.bytes === Buffer.byteLength('规则正文')));
});
behavior('CONTEXT-required', async () => {
  const assembler = new runtime.ContextAssembler({ maxInputBytes: 1024 });
  const parts = ['RULES required authority', 'ACCEPTANCE immutable', 'FROZEN_SKILL synthetic material'];
  const prompt = assembler.assemble(parts);
  for (const part of parts) assert.ok(prompt.includes(part));
});
behavior('CONTEXT-overflow', async () => {
  const assembler = new runtime.ContextAssembler({ maxInputBytes: 16 });
  assert.throws(() => assembler.assemble(['RULES', '中文'.repeat(100)]));
});
behavior('CONTEXT-skills', async () => {
  const fixture = await sandbox('context-skills');
  const skills = path.join(fixture.directory, 'skills');
  await fs.mkdir(path.join(skills, 'fixture', 'references'), { recursive: true });
  await fs.mkdir(path.join(skills, 'fixture', 'agents'));
  await fs.writeFile(path.join(skills, 'fixture', 'SKILL.md'), '# SYNTHETIC_SKILL_MAIN');
  await fs.writeFile(path.join(skills, 'fixture', 'references', 'reference.md'), '# SYNTHETIC_REFERENCE');
  await fs.writeFile(path.join(skills, 'fixture', 'agents', 'agent.md'), '# SYNTHETIC_AGENT');
  const assembler = new runtime.ContextAssembler({ maxInputBytes: 4096 });
  const prompt = runtime.readSkillPackage(skills, 'fixture', assembler);
  for (const value of ['SYNTHETIC_SKILL_MAIN', 'SYNTHETIC_REFERENCE', 'SYNTHETIC_AGENT']) assert.ok(prompt.includes(value));
  assert.ok(assembler.sources.size >= 3);
});

function historyRequest(seed = 'original') {
  const input = [];
  for (let i = 0; i < 12; i++) {
    input.push({ type: 'message', role: 'user', content: `${seed} user ${i} ` + '有来源的材料'.repeat(100) });
    input.push({ type: 'message', role: 'assistant', content: `${seed} assistant ${i}` });
  }
  input.push({ type: 'function_call', callId: 'paired-call', name: 'read', arguments: '{}' });
  input.push({ type: 'function_call_result', callId: 'paired-call', name: 'read', output: 'paired-result' });
  input.push({ type: 'message', role: 'user', content: 'LATEST_TASK_CONSTRAINT' });
  return { systemInstructions: 'IMMUTABLE_RULES_AND_ACCEPTANCE', input };
}
behavior('HISTORY-off', async () => {
  const compactor = new runtime.HistoryCompactor({ enabled: false });
  const request = historyRequest(); let calls = 0;
  const result = await compactor.prepare(request, async () => { calls++; return 'unused'; }, 1000000);
  assert.deepEqual(result, request); assert.equal(calls, 0);
});
behavior('HISTORY-budget', async () => assert.throws(() => new runtime.HistoryCompactor({ enabled: true })));
behavior('HISTORY-derived', async () => {
  const compactor = new runtime.HistoryCompactor({ enabled: true, thresholdBytes: 18000, summaryMaxTokens: 128, maxSourceBytes: 100000 });
  let sourceId, original;
  const result = await compactor.prepare(historyRequest(), async (text, id, budget) => { original = text; sourceId = id; assert.equal(budget, 128); return 'SYNTHETIC_DERIVED_SUMMARY'; }, 1000000);
  assert.ok(sourceId && original);
  assert.equal(compactor.source(sourceId), original);
  assert.ok(JSON.stringify(result).includes('SYNTHETIC_DERIVED_SUMMARY'));
  assert.equal(result.systemInstructions, 'IMMUTABLE_RULES_AND_ACCEPTANCE');
  const page = compactor.sourcePage(sourceId, 0, 128);
  assert.ok(Buffer.byteLength(page.text) <= 128); assert.equal(page.offset, 0);
  compactor.clear(); assert.throws(() => compactor.source(sourceId));
});
behavior('HISTORY-pairs', async () => {
  const compactor = new runtime.HistoryCompactor({ enabled: true, thresholdBytes: 18000, summaryMaxTokens: 128, maxSourceBytes: 100000 });
  const result = await compactor.prepare(historyRequest(), async () => 'derived', 1000000);
  const pair = result.input.filter(item => item.callId === 'paired-call');
  assert.equal(pair.length, 2); assert.equal(pair[0].type, 'function_call'); assert.equal(pair[1].type, 'function_call_result');
});
behavior('HISTORY-cancel', async () => {
  const controller = new AbortController(); controller.abort(new runtime.RuntimeFault('LEASE_LOST'));
  const compactor = new runtime.HistoryCompactor({ enabled: true, thresholdBytes: 18000, summaryMaxTokens: 128, signal: controller.signal });
  let calls = 0;
  await assert.rejects(compactor.prepare(historyRequest(), async () => { calls++; return 'unexpected'; }, 1000000));
  assert.equal(calls, 0);
});
behavior('HISTORY-failure', async () => {
  const compactor = new runtime.HistoryCompactor({ enabled: true, thresholdBytes: 18000, summaryMaxTokens: 128 });
  let calls = 0;
  await assert.rejects(compactor.prepare(historyRequest(), async () => { calls++; throw new Error('SYNTHETIC_SUMMARY_FAILURE'); }, 1000000));
  assert.equal(calls, 1);
});
behavior('HISTORY-max-two', async () => {
  const compactor = new runtime.HistoryCompactor({ enabled: true, thresholdBytes: 18000, summaryMaxTokens: 128, maxSourceBytes: 1000000 });
  let calls = 0;
  for (let i = 0; i < 4; i++) await outcome(compactor.prepare(historyRequest(`version-${i}`), async () => { calls++; return 'derived'; }, 1000000));
  assert.ok(calls <= 2); assert.ok(calls >= 1);
});

behavior('EVIDENCE-outside', async () => {
  const fixture = await hostFixture('evidence-outside');
  assert.equal(runtime.isInside(fixture.workspace, fixture.recorder.directory, true), false);
  await fixture.recorder.record('input-versions', { taskId: 'synthetic', sourceId: 'rules-1', digest: runtime.sha256('rules'), bytes: 5 });
  fixture.recorder.assertHealthy();
});
behavior('EVIDENCE-forbidden-root', async () => {
  const { workspace } = await sandbox('evidence-forbidden');
  await assert.rejects(runtime.EvidenceRecorder.open(path.join(workspace, 'logs'), { taskId: 'synthetic', attemptNo: 1 }, { forbiddenRoots: [workspace] }));
});
behavior('EVIDENCE-correlation', async () => {
  const fixture = await hostFixture('evidence-correlation');
  await fixture.recorder.record('input-versions', { sourceId: 'input-source', digest: runtime.sha256('input'), resultRevision: 'synthetic-revision' });
  await fixture.host.execute({ name: 'correlated', run: async () => 'result' }, {}, { callId: 'correlated-call' });
  const text = await directoryText(fixture.recorder.directory);
  assert.ok(text.includes('correlated-call')); assert.ok(text.includes(runtime.sha256('input'))); assert.ok(text.includes('synthetic-revision'));
});
behavior('EVIDENCE-redaction', async () => {
  const fixture = await hostFixture('redaction');
  await assert.rejects(fixture.recorder.record('input-versions', { phase: 'context', leaseToken: 'SYNTHETIC_LEASE_SECRET', apiKey: 'SYNTHETIC_API_SECRET', prompt: 'SYNTHETIC_PROMPT_BODY', source: 'SYNTHETIC_SOURCE_BODY', error: 'SYNTHETIC_RAW_ERROR_SECRET' }), error => error.code === 'EVIDENCE_FIELD_FORBIDDEN');
  assert.throws(() => fixture.recorder.assertHealthy());
  const text = await directoryText(fixture.recorder.directory);
  for (const value of ['SYNTHETIC_LEASE_SECRET', 'SYNTHETIC_API_SECRET', 'SYNTHETIC_PROMPT_BODY', 'SYNTHETIC_SOURCE_BODY', 'SYNTHETIC_RAW_ERROR_SECRET']) assert.ok(!text.includes(value));
});
behavior('EVIDENCE-capacity', async () => {
  const fixture = await sandbox('capacity');
  const recorder = await runtime.EvidenceRecorder.open(path.join(fixture.directory, 'evidence'), { taskId: 'synthetic', attemptNo: 1 }, { maxBytes: 4096, forbiddenRoots: [fixture.workspace] });
  let failed = false;
  for (let i = 0; i < 100; i++) {
    try { await recorder.record('tool-terminal', { callId: `call-${i}`, status: 'completed', digest: 'a'.repeat(64), bytes: 100 }); }
    catch { failed = true; break; }
  }
  assert.equal(failed, true); assert.throws(() => recorder.assertHealthy());
});
behavior('EVIDENCE-write-failure', async () => {
  const fixture = await hostFixture('write-failure');
  await fs.rename(fixture.recorder.directory, fixture.recorder.directory + '-owned-backup');
  await fs.writeFile(fixture.recorder.directory, 'synthetic path collision');
  await assert.rejects(fixture.recorder.record('result-validation', { status: 'completed' }));
  assert.throws(() => fixture.recorder.assertHealthy());
});
behavior('EVIDENCE-terminal-expiry', async () => {
  const fixture = await hostFixture('terminal-expiry');
  await fixture.recorder.finish('completed');
  await runtime.EvidenceRecorder.prune(path.join(fixture.directory, 'evidence'), Date.now() + 31 * 86400000);
  await assert.rejects(fs.stat(fixture.recorder.directory), { code: 'ENOENT' });
});
behavior('EVIDENCE-active-retain', async () => {
  const fixture = await hostFixture('active-retain');
  await fixture.recorder.record('input-versions', { status: 'running' });
  await runtime.EvidenceRecorder.prune(path.join(fixture.directory, 'evidence'), Date.now() + 31 * 86400000);
  assert.ok((await fs.stat(fixture.recorder.directory)).isDirectory());
});
behavior('EVIDENCE-foreign-retain', async () => {
  const fixture = await sandbox('foreign-retain');
  const evidenceRoot = path.join(fixture.directory, 'evidence');
  const foreign = path.join(evidenceRoot, 'not-owned');
  await fs.mkdir(foreign, { recursive: true }); await fs.writeFile(path.join(foreign, 'body.txt'), 'foreign-marker');
  await runtime.EvidenceRecorder.prune(evidenceRoot, Date.now() + 31 * 86400000);
  assert.equal(await fs.readFile(path.join(foreign, 'body.txt'), 'utf8'), 'foreign-marker');
});

for (const phase of ['prepare', 'model', 'tool', 'publish']) {
  behavior(`ATTEMPT-cancel-${phase}`, async () => {
    const fixture = await hostFixture(`attempt-cancel-${phase}`);
    const attempt = new runtime.AttemptRunner({ timeoutMs: 1000, settleTimeoutMs: 20, recorder: fixture.recorder });
    let later = 0;
    try {
      const result = await outcome(attempt.step(phase, async () => { attempt.cancel('LEASE_LOST'); return 'raced-result'; }));
      assert.equal(result.rejected, true);
      await assert.rejects(attempt.step('complete', async () => { later++; }));
      assert.equal(later, 0); assert.equal(attempt.signal.aborted, true);
    } finally { await attempt.settled(1000); attempt.dispose(); }
  });
}
behavior('ATTEMPT-timeout', async () => {
  const fixture = await hostFixture('attempt-timeout');
  const attempt = new runtime.AttemptRunner({ timeoutMs: 20, settleTimeoutMs: 100, recorder: fixture.recorder });
  try {
    await assert.rejects(attempt.step('prepare', async () => { await wait(40); return 'too-late'; }));
    assert.equal(attempt.signal.aborted, true);
  } finally { attempt.dispose(); }
});
behavior('ATTEMPT-cleanup-wait', async () => {
  const fixture = await hostFixture('attempt-cleanup-wait');
  const attempt = new runtime.AttemptRunner({ timeoutMs: 1000, settleTimeoutMs: 10, recorder: fixture.recorder });
  const started = deferred(), release = deferred();
  const operation = outcome(attempt.step('tool', async () => { started.resolve(); await release.promise; }));
  await within(started.promise); attempt.cancel('EXPLICIT_TERMINATION');
  try { assert.equal(await attempt.settled(), false); assert.equal(attempt.resourceUncertain, true); }
  finally { release.resolve(); await operation; assert.equal(await attempt.settled(1000), true); attempt.dispose(); }
});

const modelOutput = { responseId: 'synthetic-response', output: [], usage: { inputTokens: 0, outputTokens: 0, totalTokens: 0 }, providerData: { status: 'completed' } };
behavior('MODEL-completed', async () => runtime.validateModelResponse(modelOutput, { status: 'completed' }));
behavior('MODEL-incomplete', async () => assert.throws(() => runtime.validateModelResponse({ ...modelOutput, providerData: { status: 'incomplete' } }, { status: 'incomplete', incomplete_details: { reason: 'max_output_tokens' } })));
behavior('MODEL-unknown', async () => assert.throws(() => runtime.validateModelResponse({ ...modelOutput, providerData: {} }, {})));
behavior('MODEL-invalid-call', async () => assert.throws(() => runtime.validateModelResponse({ ...modelOutput, output: [{ type: 'function_call', callId: 'truncated-call', name: 'write', arguments: '{"path":' }] }, { status: 'completed' })));
behavior('MODEL-truncated-call', async () => {
  const output = [{ type: 'function_call', callId: 'parseable-but-truncated', name: 'write', arguments: '{}' }];
  assert.throws(() => runtime.validateModelResponse({ ...modelOutput, output, providerData: { status: 'incomplete' } }, { status: 'incomplete', output, incomplete_details: { reason: 'max_output_tokens' } }));
});
behavior('MODEL-stream-incomplete', async () => {
  const fixture = await hostFixture('stream-incomplete');
  const fake = { async *getStreamedResponse() { yield { type: 'response_started' }; yield { type: 'response_done', response: { ...modelOutput, providerData: { status: 'incomplete' } } }; } };
  const guarded = runtime.guardedModel(fake, { recorder: fixture.recorder, signal: fixture.controller.signal });
  await assert.rejects(async () => { for await (const event of guarded.getStreamedResponse({ systemInstructions: 'synthetic', input: [] })) void event; });
});
behavior('MODEL-usage-unknown', async () => {
  const fixture = await hostFixture('usage-unknown');
  const guarded = runtime.guardedModel({ getResponse: async () => ({ ...modelOutput }) }, { recorder: fixture.recorder, signal: fixture.controller.signal });
  await guarded.getResponse({ systemInstructions: 'synthetic', input: [] });
  const text = await directoryText(fixture.recorder.directory);
  assert.ok(text.includes('unknown'), 'Absent provider usage must be recorded as unknown');
});

behavior('PROCESS-success', async () => {
  const { workspace } = await sandbox('process-success');
  const result = await runtime.runCommand(process.execPath, ['-e', 'process.stdout.write("synthetic-process")'], { cwd: workspace, timeoutMs: 1000, maxBytes: 1024, exact: true });
  assert.equal(result.exitCode, 0); assert.equal(result.stdout, 'synthetic-process'); assert.equal(result.complete, true);
});
behavior('PROCESS-overflow', async () => {
  const { workspace } = await sandbox('process-overflow');
  await assert.rejects(runtime.runCommand(process.execPath, ['-e', 'process.stdout.write(JSON.stringify({body:"x".repeat(8192)}))'], { cwd: workspace, timeoutMs: 1000, maxBytes: 256, exact: true }));
});
behavior('PROCESS-cancel', async () => {
  const { workspace } = await sandbox('process-cancel');
  const controller = new AbortController();
  const operation = outcome(runtime.runCommand(process.execPath, ['-e', 'setInterval(()=>{},1000)'], { cwd: workspace, signal: controller.signal, timeoutMs: 1000, maxBytes: 1024 }));
  await wait(40); controller.abort(new runtime.RuntimeFault('EXPLICIT_TERMINATION'));
  assert.equal((await within(operation)).rejected, true);
});
behavior('ENV-minimum', async () => {
  const originals = new Map();
  for (const [key, value] of Object.entries({ AIW_WORKER_TOKEN: 'SYNTHETIC_CP_SECRET', OPENAI_API_KEY: 'SYNTHETIC_MODEL_SECRET', AIW_LEASE_TOKEN: 'SYNTHETIC_LEASE_SECRET', OTEL_EXPORTER_OTLP_ENDPOINT: 'synthetic-exporter' })) {
    originals.set(key, process.env[key]); process.env[key] = value;
  }
  try {
    const child = runtime.commandEnvironment();
    for (const value of ['SYNTHETIC_CP_SECRET', 'SYNTHETIC_MODEL_SECRET', 'SYNTHETIC_LEASE_SECRET', 'synthetic-exporter']) assert.ok(!Object.values(child).includes(value));
  } finally {
    for (const [key, value] of originals) { if (value === undefined) delete process.env[key]; else process.env[key] = value; }
  }
});
behavior('PLUGIN-disabled', async () => {
  const { workspace } = await sandbox('plugin-disabled');
  const adapter = await runtime.AiwAdapter.load(undefined, workspace);
  assert.equal(adapter.entries.length, 0);
});
behavior('PLUGIN-malformed', async () => {
  const fixture = await sandbox('plugin-malformed');
  const catalog = path.join(fixture.directory, 'catalog.json');
  await fs.writeFile(catalog, '{"schema":"unknown","entries":[]}');
  await assert.rejects(runtime.AiwAdapter.load(catalog, fixture.workspace));
});
behavior('PLUGIN-missing', async () => {
  const { workspace } = await sandbox('plugin-missing');
  const adapter = await runtime.AiwAdapter.load(undefined, workspace);
  await assert.rejects(adapter.query('synthetic-missing-plugin', {}, new AbortController().signal));
});

behavior('EVIDENCE-identity-attempt', async () => {
  const fixture = await sandbox('identity-attempt');
  const recorder = await runtime.EvidenceRecorder.open(path.join(fixture.directory, 'evidence'), { taskId: 'synthetic-task', attemptId: 'synthetic-attempt', attemptNo: 2, role: 'coder', runtime: 'synthetic-test-only', versions: { sdk: 'public-fixture' } }, { forbiddenRoots: [fixture.workspace] });
  recorder.assertHealthy();
  const text = await directoryText(recorder.directory);
  for (const value of ['synthetic-attempt', 'attemptNo', 'public-fixture']) assert.ok(text.includes(value));
});
behavior('EVIDENCE-identity-forbidden', async () => {
  const fixture = await sandbox('identity-forbidden');
  await assert.rejects(runtime.EvidenceRecorder.open(path.join(fixture.directory, 'evidence'), { taskId: 'synthetic', prompt: 'SYNTHETIC_FORBIDDEN_IDENTITY_BODY' }, { forbiddenRoots: [fixture.workspace] }), error => error.code === 'EVIDENCE_FIELD_FORBIDDEN');
  assert.ok(!(await directoryText(fixture.directory)).includes('SYNTHETIC_FORBIDDEN_IDENTITY_BODY'));
});
behavior('EVIDENCE-value-invalid', async () => {
  for (const value of [Number.NaN, Infinity, 'x'.repeat(4097), { nested: 'not-whitelisted' }]) {
    const fixture = await hostFixture('invalid-metadata');
    await assert.rejects(fixture.recorder.record('tool-terminal', { count: value }), error => error.code === 'EVIDENCE_VALUE_INVALID');
    assert.throws(() => fixture.recorder.assertHealthy());
  }
});
behavior('RUNNER-recorder-required', async () => {
  const attempt = new runtime.AttemptRunner({ timeoutMs: 1000 });
  let calls = 0;
  try { await assert.rejects(attempt.step('prepare', async () => { calls++; }), error => error.code === 'ATTEMPT_EVIDENCE_REQUIRED'); assert.equal(calls, 0); }
  finally { await attempt.settled(); attempt.dispose(); }
});
behavior('RUNNER-unhealthy-recorder', async () => {
  const fixture = await hostFixture('unhealthy-recorder');
  await assert.rejects(fixture.recorder.record('input', { prompt: 'SYNTHETIC_FORBIDDEN' }));
  const attempt = new runtime.AttemptRunner({ timeoutMs: 1000, recorder: fixture.recorder });
  let calls = 0;
  try { await assert.rejects(attempt.step('prepare', async () => { calls++; })); assert.equal(calls, 0); }
  finally { await attempt.settled(); attempt.dispose(); }
});
behavior('RUNNER-dispose-not-wait', async () => {
  const fixture = await hostFixture('dispose-not-wait');
  const attempt = new runtime.AttemptRunner({ timeoutMs: 1000, settleTimeoutMs: 10, recorder: fixture.recorder });
  const started = deferred(), release = deferred();
  const operation = outcome(attempt.step('tool', async () => { started.resolve(); await release.promise; }));
  await within(started.promise);
  attempt.dispose();
  try { assert.equal(await attempt.settled(), false); }
  finally { release.resolve(); await within(operation); assert.equal(await attempt.settled(), true); }
});
behavior('OUTPUT-invalid-budget', async () => {
  for (const options of [{ maxBytes: 127 }, { maxBytes: 128.5 }, { maxLines: 2 }, { maxLines: 3.5 }]) assert.throws(() => runtime.boundedText('synthetic', options), error => error.code === 'OUTPUT_BUDGET_INVALID');
});
behavior('TOOL-validator-false', async () => {
  const fixture = await hostFixture('validator-false'); let calls = 0;
  assertDenied(await outcome(fixture.host.execute({ name: 'synthetic-false-validator', validate: () => false, run: async () => { calls++; return 'unexpected'; } }, {})));
  assert.equal(calls, 0);
});
behavior('TOOL-failure-then-text', async () => {
  const fixture = await hostFixture('failure-then-text');
  assertDenied(await outcome(fixture.host.execute({ name: 'synthetic-failure', run: async () => { throw new Error('SYNTHETIC_FAILURE'); } }, {})));
  await fixture.host.execute({ name: 'synthetic-unrelated-text', run: async () => 'done' }, {});
  assert.throws(() => fixture.host.assertSuccessful());
});
behavior('HISTORY-impossible-budget', async () => {
  const compactor = new runtime.HistoryCompactor({ enabled: true, thresholdBytes: 18000, summaryMaxTokens: 128, maxSourceBytes: 100000 });
  await assert.rejects(compactor.prepare(historyRequest(), async () => 'derived', 32));
});

