import { assert, fs, path, behavior, outcome, deferred, within, directoryText } from './harness.mjs';
import { appendDiagnostic, evidenceRows, verifyAll } from './diagnostics.mjs';
import { runtime, apiFixture, memoryFetch, executeFixture, sseResponse, textItem, toolItem, announcedTool, httpModelError,
  cancelledFetch, assertCompleted, assertNoComplete, callCount } from './api-fixture.mjs';

behavior('SDK-text-stream', async () => {
  const fixture = await apiFixture('sdk-text-stream');
  const run = await memoryFetch(() => outcome(executeFixture(fixture)), request => sseResponse(request, [textItem('Synthetic SDK text 中文🧪.')]));
  await assertCompleted(fixture, run.value); assert.equal(run.requests[0].body.stream, true);
});
async function badTerminal(id, options) {
  const fixture = await apiFixture(id);
  const run = await memoryFetch(() => outcome(executeFixture(fixture)), request => {
    const call = announcedTool(request, 'write', { path: 'must-not-write.txt', content: 'SYNTHETIC_FORBIDDEN_WRITE' });
    return sseResponse(request, [call], options);
  });
  assertNoComplete(fixture); assert.equal(run.requests.length, 1);
  await assert.rejects(fs.stat(path.join(fixture.workspace, 'must-not-write.txt')), { code: 'ENOENT' });
}
behavior('SDK-incomplete-no-tool', () => badTerminal('sdk-incomplete', { status: 'incomplete' }));
behavior('SDK-missing-terminal', () => badTerminal('sdk-missing-terminal', { omitTerminal: true }));
behavior('SDK-unknown-terminal', () => badTerminal('sdk-unknown-terminal', { status: 'fd005_unknown_status' }));
behavior('SDK-model-error', async () => {
  const fixture = await apiFixture('sdk-model-error');
  const run = await memoryFetch(() => outcome(executeFixture(fixture)), () => httpModelError(429));
  assertNoComplete(fixture); assert.equal(run.requests.length, 1); assert.equal(callCount(fixture, 'fail'), 1);
  assert.equal(fixture.client.calls.find(call => call.method === 'fail').args[3], 'RATE_LIMIT');
});
behavior('SDK-tool-failure', async () => {
  const fixture = await apiFixture('sdk-tool-failure');
  const run = await memoryFetch(() => outcome(executeFixture(fixture)), (request, signal, index) => index === 0
    ? sseResponse(request, [announcedTool(request, 'read', { path: 'synthetic-missing-file.txt', offset: 1, limit: 10 })])
    : sseResponse(request, [textItem('Synthetic done text does not repair the failed read.')]));
  assertNoComplete(fixture); assert.equal(callCount(fixture, 'fail'), 1); assert.ok(run.requests.length >= 1 && run.requests.length <= 2);
});
behavior('SDK-readonly-write', async () => {
  const fixture = await apiFixture('sdk-readonly-write', 'reviewer');
  const file = path.join(fixture.workspace, 'readonly.txt'); await fs.writeFile(file, 'original');
  const run = await memoryFetch(() => outcome(executeFixture(fixture)), (request, signal, index) => {
    for (const name of ['write', 'edit', 'edit_many', 'bash']) assert.ok(!request.tools.some(tool => tool.name === name));
    return index === 0 ? sseResponse(request, [toolItem('write', { path: 'readonly.txt', content: 'unexpected' })])
      : sseResponse(request, [textItem(fixture.example.finalResponse)]);
  });
  assertNoComplete(fixture); assert.equal(await fs.readFile(file, 'utf8'), 'original'); assert.ok(run.requests.length <= 2);
});
behavior('SDK-cancel-fetch', async () => {
  const controller = new AbortController(), started = deferred();
  const fixture = await apiFixture('sdk-cancel-fetch', 'coder', { config: { forceSignal: controller.signal } });
  await memoryFetch(async () => {
    const pending = outcome(executeFixture(fixture)); const { signal } = await within(started.promise);
    controller.abort(new runtime.RuntimeFault('EXPLICIT_TERMINATION')); await within(pending, 2500);
    assert.equal(signal.aborted, true);
  }, cancelledFetch(started));
  assertNoComplete(fixture);
});
async function pairedTools(id) {
  const fixture = await apiFixture(id);
  await fs.writeFile(path.join(fixture.workspace, 'first.txt'), 'SYNTHETIC_FIRST_READ');
  await fs.writeFile(path.join(fixture.workspace, 'second.txt'), 'SYNTHETIC_SECOND_READ');
  const run = await memoryFetch(() => outcome(executeFixture(fixture)), (request, signal, index) => {
    assert.equal(request.parallel_tool_calls, true);
    if (index === 0) return sseResponse(request, [toolItem('read', { path: 'first.txt', offset: 1, limit: 10 }, 'call-first'), toolItem('read', { path: 'second.txt', offset: 1, limit: 10 }, 'call-second')]);
    const returned = request.input.filter(item => item.type === 'function_call_output');
    assert.ok(returned.find(item => item.call_id === 'call-first')?.output.includes('SYNTHETIC_FIRST_READ'));
    assert.ok(returned.find(item => item.call_id === 'call-second')?.output.includes('SYNTHETIC_SECOND_READ'));
    return sseResponse(request, [textItem(fixture.example.finalResponse)]);
  });
  await assertCompleted(fixture, run.value); assert.equal(run.requests.length, 2);
  const evidence = await directoryText(path.join(fixture.directory, 'evidence'));
  assert.ok(evidence.includes('call-first') && evidence.includes('call-second'));
}
behavior('SDK-tool-correlation', () => pairedTools('sdk-tool-correlation'));
behavior('API-parallel-setting', () => pairedTools('api-parallel-setting'));
behavior('SDK-usage-exact', async () => {
  for (const known of [true, false]) {
    const fixture = await apiFixture(`sdk-usage-${known}`);
    const run = await memoryFetch(() => outcome(executeFixture(fixture)), request => sseResponse(request, [textItem(fixture.example.finalResponse)], { usage: known }));
    await assertCompleted(fixture, run.value);
    const records = (await evidenceRows(path.join(fixture.directory, 'evidence'))).map(row => row.metadata);
    const terminals = records.filter(row => row.event === 'model-terminal');
    assert.equal(terminals.length, 1, 'Check this model terminal, not another resource/report unknown state');
    const terminal = terminals[0];
    if (known) await verifyAll({
      usageKnown: () => assert.equal(terminal.usageKnown, true),
      inputTokens: () => assert.equal(terminal.inputTokens, 23),
      outputTokens: () => assert.equal(terminal.outputTokens, 7),
      cachedTokens: () => assert.equal(terminal.cachedTokens, 4),
      exactUsageSource: () => assert.equal(terminal.usageSource, 'provider-response'),
    });
    else await verifyAll({
      usageKnown: () => assert.equal(terminal.usageKnown, false),
      usageSource: () => assert.equal(terminal.usageSource, 'unknown'),
      noFabricatedInputZero: () => assert.ok(terminal.inputTokens === undefined || terminal.inputTokens === null),
      noFabricatedOutputZero: () => assert.ok(terminal.outputTokens === undefined || terminal.outputTokens === null),
    });
  }
});
behavior('SDK-request-privacy', async () => {
  const fixture = await apiFixture('sdk-privacy');
  const run = await memoryFetch(() => outcome(executeFixture(fixture)), request => sseResponse(request, [textItem(fixture.example.finalResponse)]));
  await assertCompleted(fixture, run.value); assert.equal(run.requests.length, 1);
  const body = run.requests[0].body;
  assert.equal(body.store, false); assert.equal(body.truncation, 'disabled'); assert.equal(body.parallel_tool_calls, true);
  for (const name of ['OPENAI_AGENTS_DONT_LOG_MODEL_DATA', 'OPENAI_AGENTS_DONT_LOG_TOOL_DATA', 'OPENAI_AGENTS_DISABLE_TRACING']) assert.equal(process.env[name], '1');
});
function assertCallPairs(request) {
  const items = Array.isArray(request.input) ? request.input : [];
  const calls = items.filter(item => item.type === 'function_call'), results = items.filter(item => item.type === 'function_call_output');
  for (const call of calls) assert.ok(results.some(result => result.call_id === call.call_id), 'Historical call lacks its matching result');
  for (const result of results) {
    assert.ok(calls.some(call => call.call_id === result.call_id), 'Historical result lacks its matching call');
    const output = JSON.parse(result.output);
    assert.equal(output.complete, true, 'Summary fixture must use complete tool results, not truncation/failure');
    assert.equal(output.truncated, false);
  }
}
async function summaryExercise(id, options = {}) {
  // Each tool result fits 8 KiB. Total history crosses 24 KiB; immutable + recent input fits 256 KiB.
  const controller = options.cancelSummary ? new AbortController() : null;
  const started = deferred();
  const fixture = await apiFixture(id, 'coder', {
    runtime: { compactHistory: true, compactAfterBytes: 24000, summaryMaxTokens: 256,
      maxTurns: 28, maxOutputChars: 8192, maxInputBytes: 256000, taskTimeoutMs: 6000 },
    config: { maxInputBytes: 256000, taskTimeoutMs: 6000, ...(controller ? { forceSignal: controller.signal } : {}) },
  });
  const source = Array.from({ length: 32 }, (_, i) => 'SYNTHETIC_HISTORY_' + i + ' 中文 ' + 'x'.repeat(90)).join('\n');
  assert.ok(Buffer.byteLength(source) < 6000, 'Complete output fixture has static budget margin');
  await fs.writeFile(path.join(fixture.workspace, 'bounded-history.txt'), source);
  let normalCalls = 0, summaries = 0, summaryAborted = false;
  const run = await memoryFetch(async () => {
    const pending = outcome(executeFixture(fixture));
    if (options.cancelSummary) {
      try {
        const { signal } = await within(started.promise, 3500);
        assert.ok(signal && !signal.aborted, 'Summary target actually started before cancellation');
        controller.abort(new runtime.RuntimeFault('EXPLICIT_TERMINATION'));
        const result = await within(pending, 2500);
        summaryAborted = signal.aborted;
        return result;
      } finally { if (!controller.signal.aborted) controller.abort(new runtime.RuntimeFault('EXPLICIT_TERMINATION')); await within(pending,2500); }
    }
    return pending;
  }, (request, signal) => {
    assert.equal(request.model, 'fd005-synthetic-model'); assert.equal(request.store, false); assert.equal(request.truncation, 'disabled');
    if (request.stream !== true) {
      summaries++;
      appendDiagnostic('summary-target.jsonl', { fixtureId: fixture.task.attemptId, phase: 'summary-start', summaries,
        model: request.model, store: request.store, signalPresent: !!signal, signalAbortedAtStart: signal?.aborted === true,
        inputBytes: Buffer.byteLength(JSON.stringify(request.input || [])) });
      if (options.cancelSummary) return cancelledFetch(started)(request,signal);
      return sseResponse(request, [textItem('SYNTHETIC_DERIVED_SUMMARY Retain approved instructions and the public source reference.')]);
    }
    normalCalls++; assertCallPairs(request);
    const frozen = JSON.stringify({ instructions: request.instructions, input: request.input });
    for (const name of [...fixture.profile.protocolSkills, fixture.profile.primarySkill]) assert.ok(frozen.includes('SYNTHETIC_FULL_SKILL_MAIN_' + name), 'Frozen Skill input must survive compaction');
    if (normalCalls <= 18) return sseResponse(request, [toolItem('read', { path: 'bounded-history.txt', offset: 1, limit: 32 }, 'synthetic-history-call-' + normalCalls)]);
    return sseResponse(request, [textItem(fixture.example.finalResponse)]);
  });
  appendDiagnostic('summary-target.jsonl', { fixtureId: fixture.task.attemptId, phase: 'run-return', normalCalls, summaries,
    summaryAborted, completeCalls: callCount(fixture,'complete'), failCalls: callCount(fixture,'fail') });
  if (options.cancelSummary) await verifyAll({
    summaryReached: () => assert.ok(summaries >= 1 && summaries <= 2),
    summaryCancelled: () => assert.equal(summaryAborted,true),
    noComplete: () => assertNoComplete(fixture),
  });
  else await verifyAll({
    summaryReached: () => assert.ok(summaries >= 1 && summaries <= 2),
    normalCompletionReached: () => assert.equal(normalCalls,19),
    completionPosted: () => assertCompleted(fixture,run.value),
    noInitiallyCancelledFetch: () => assert.ok(run.requests.every(request => request.signal && !request.signalAbortedAtStart)),
    summaryAndNormalPrivacy: () => assert.ok(run.requests.every(({body}) => body.store === false && body.truncation === 'disabled' && body.model === 'fd005-synthetic-model')),
    metadataNoBody: async () => assert.ok(!(await directoryText(path.join(fixture.directory,'evidence'))).includes('SYNTHETIC_HISTORY_20')),
  });
  return { fixture, run, normalCalls, summaries };
}
behavior('SDK-summary-privacy', () => summaryExercise('sdk-summary-privacy'));
behavior('API-store-false', async () => {
  for (const name of ['OPENAI_AGENTS_DONT_LOG_MODEL_DATA', 'OPENAI_AGENTS_DONT_LOG_TOOL_DATA', 'OPENAI_AGENTS_DISABLE_TRACING']) assert.equal(process.env[name], '1');
  await summaryExercise('api-store-false');
});
behavior('SDK-summary-call-pairs', async () => {
  const { run, summaries } = await summaryExercise('sdk-summary-call-pairs');
  assert.ok(summaries >= 1);
  for (const {body} of run.requests.filter(request => request.body.stream === true)) assertCallPairs(body);
});
behavior('SDK-summary-parent-cancel', () => summaryExercise('sdk-summary-parent-cancel', { cancelSummary: true }));
behavior('SDK-history-off', async () => {
  const fixture = await apiFixture('sdk-history-off');
  const run = await memoryFetch(() => outcome(executeFixture(fixture)), request => { assert.equal(request.stream, true); return sseResponse(request, [textItem(fixture.example.finalResponse)]); });
  await assertCompleted(fixture, run.value); assert.equal(run.requests.length, 1);
});
async function invalidParameters(id, toolName, args) {
  const fixture = await apiFixture(id);
  const file = path.join(fixture.workspace, 'input.txt'); await fs.writeFile(file,'original');
  const run = await memoryFetch(() => outcome(executeFixture(fixture)), (request,signal,index) => index === 0
    ? sseResponse(request,[toolItem(toolName,args)])
    : sseResponse(request,[textItem(fixture.example.finalResponse)]));
  const after = await fs.readFile(file,'utf8');
  appendDiagnostic('file-side-effect.jsonl', { fixtureId: fixture.task.attemptId, file: 'input.txt',
    originalDigest: runtime.sha256('original'), actualDigest: runtime.sha256(after), unchanged: after === 'original',
    completeCalls: callCount(fixture,'complete'), requests: run.requests.length });
  await verifyAll({
    noComplete: () => assertNoComplete(fixture),
    noFileSideEffect: () => assert.equal(after,'original'),
  });
}
behavior('SDK-tool-parameters', () => invalidParameters('sdk-tool-parameters','write',{ path: 'input.txt', content: 'unexpected', undeclared: 'denied' }));
behavior('SDK-edit-many-strict-nested', () => invalidParameters('sdk-edit-many-strict-nested','edit_many',{ path: 'input.txt', edits: [{ oldText: 'original', newText: 'unexpected', undeclared: 'denied' }] }));
behavior('SDK-tool-parameter-type', () => invalidParameters('sdk-tool-parameter-type','write',{ path: 'input.txt', content: 123 }));
behavior('SDK-tool-parameter-json', () => invalidParameters('sdk-tool-parameter-json','write','{"path":"input.txt","content":'));
