import { assert, fs, path, behavior, outcome, deferred, within, directoryText } from './harness.mjs';
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
    const evidence = await directoryText(path.join(fixture.directory, 'evidence'));
    if (known) { assert.ok(evidence.includes('"usageKnown":true')); assert.ok(evidence.includes('"inputTokens":23')); assert.ok(evidence.includes('"cachedTokens":4')); }
    else assert.ok(evidence.includes('unknown'));
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
async function summaryPrivacy(id) {
  const fixture = await apiFixture(id, 'coder', { runtime: { compactHistory: true, compactAfterBytes: 18000, summaryMaxTokens: 128, maxTurns: 16, maxOutputChars: 4096 } });
  await fs.writeFile(path.join(fixture.workspace, 'large-history.txt'), Array.from({ length: 600 }, (_, i) => `SYNTHETIC_HISTORY_SOURCE_${i} 中文 ${'x'.repeat(100)}`).join('\n'));
  let normalCalls = 0, summaries = 0;
  const run = await memoryFetch(() => outcome(executeFixture(fixture)), request => {
    assert.equal(request.model, 'fd005-synthetic-model'); assert.equal(request.store, false); assert.equal(request.truncation, 'disabled');
    if (request.stream !== true) { summaries++; return sseResponse(request, [textItem('SYNTHETIC_DERIVED_SUMMARY Keep approved instructions and source references.')]); }
    normalCalls++;
    if (normalCalls <= 10) return sseResponse(request, [toolItem('read', { path: 'large-history.txt', offset: 1, limit: 600 }, `history-call-${normalCalls}`)]);
    return sseResponse(request, [textItem(fixture.example.finalResponse)]);
  });
  await assertCompleted(fixture, run.value);
  assert.ok(summaries >= 1 && summaries <= 2); assert.equal(normalCalls, 11);
  assert.ok(run.requests.every(({ signal }) => signal && !signal.aborted));
  const evidence = await directoryText(path.join(fixture.directory, 'evidence'));
  assert.ok(!evidence.includes('SYNTHETIC_HISTORY_SOURCE_100'));
}
behavior('SDK-summary-privacy', () => summaryPrivacy('sdk-summary-privacy'));
behavior('API-store-false', async () => {
  for (const name of ['OPENAI_AGENTS_DONT_LOG_MODEL_DATA', 'OPENAI_AGENTS_DONT_LOG_TOOL_DATA', 'OPENAI_AGENTS_DISABLE_TRACING']) assert.equal(process.env[name], '1');
  await summaryPrivacy('api-store-false');
});
behavior('SDK-history-off', async () => {
  const fixture = await apiFixture('sdk-history-off');
  const run = await memoryFetch(() => outcome(executeFixture(fixture)), request => { assert.equal(request.stream, true); return sseResponse(request, [textItem(fixture.example.finalResponse)]); });
  await assertCompleted(fixture, run.value); assert.equal(run.requests.length, 1);
});
behavior('SDK-tool-parameters', async () => {
  const fixture = await apiFixture('sdk-tool-parameters');
  const file = path.join(fixture.workspace, 'input.txt'); await fs.writeFile(file, 'original');
  await memoryFetch(() => outcome(executeFixture(fixture)), (request, signal, index) => index === 0
    ? sseResponse(request, [toolItem('write', { path: 'input.txt', content: 'unexpected', undeclared: 'denied' })])
    : sseResponse(request, [textItem(fixture.example.finalResponse)]));
  assertNoComplete(fixture); assert.equal(await fs.readFile(file, 'utf8'), 'original');
});
