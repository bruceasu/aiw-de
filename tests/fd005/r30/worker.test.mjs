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
import { ApiRuntime } from '../../../workers/process-typescript-with-api/src/api-runtime.ts';
import { assertExecutableRole, getProfile, loadProfile } from '../../../workers/process-typescript-with-api/src/profiles.ts';
import { runClaimedAttempt } from '../../../workers/process-typescript/runtime-core/index.mjs';

const root = path.resolve(fileURLToPath(new URL('../../../', import.meta.url)));
const scratch = process.env.FD005_R30_SCRATCH;
assert.ok(scratch && process.env.FD005_TESTER_SESSION === 'fd005-tester-20261005-e8931791');
globalThis.fetch = async () => { throw new Error('FD005_NETWORK_DISABLED'); };
const denySocket = () => { throw new Error('FD005_SOCKET_DISABLED'); };
net.connect = denySocket; net.createConnection = denySocket; tls.connect = denySocket;
dns.lookup = denySocket; dns.resolve = denySocket;
const examples = JSON.parse(await fs.readFile(path.join(root, 'workers/runtime-contract.examples.json'), 'utf8'));
const skillsRoot = path.join(root, 'workers/process-typescript-with-api/skills');
let activeFetch, sequence = 0;
globalThis.fetch = async (input, init = {}) => {
  assert.ok(activeFetch, 'No synthetic response handler');
  const url = new URL(typeof input === 'string' || input instanceof URL ? input : input.url);
  assert.equal(url.origin, 'https://fd005.invalid');
  assert.equal(url.pathname, '/v1/responses');
  assert.equal(init.method, 'POST');
  assert.equal(new Headers(init.headers).get('authorization'), 'Bearer SYNTHETIC_KEY_ONLY');
  const body = JSON.parse(init.body);
  return activeFetch(body, init.signal);
};
function textItem(text) { return { id: `msg_${sequence++}`, type: 'message', role: 'assistant', status: 'completed', content: [{ type: 'output_text', text, annotations: [], logprobs: [] }] }; }
function toolItem(name, args, callId = `call_${sequence++}`) { return { id: `fc_${callId}`, type: 'function_call', name, arguments: JSON.stringify(args), call_id: callId, status: 'completed' }; }
function response(body, output) {
  const final = { id: `resp_${sequence++}`, object: 'response', created_at: 1791158400, model: body.model, status: 'completed', output,
    output_text: output.filter(x => x.type === 'message').flatMap(x => x.content).map(x => x.text).join(''), error: null,
    incomplete_details: null, instructions: null, metadata: {}, parallel_tool_calls: true, temperature: null, top_p: null,
    tool_choice: 'auto', tools: [], store: false, truncation: 'disabled', previous_response_id: null,
    usage: { input_tokens: 23, output_tokens: 7, total_tokens: 30, input_tokens_details: { cached_tokens: 4 }, output_tokens_details: { reasoning_tokens: 2 } } };
  let number = 0; const events = []; const push = value => events.push({ ...value, sequence_number: number++ });
  push({ type: 'response.created', response: { ...final, status: 'in_progress', output: [], output_text: '' } });
  push({ type: 'response.in_progress', response: { ...final, status: 'in_progress', output: [], output_text: '' } });
  for (const [index, item] of output.entries()) {
    push({ type: 'response.output_item.added', output_index: index, item: { ...item, status: 'in_progress', ...(item.type === 'message' ? { content: [] } : { arguments: '' }) } });
    if (item.type === 'message') {
      const part = item.content[0], common = { item_id: item.id, output_index: index, content_index: 0 };
      push({ type: 'response.content_part.added', ...common, part: { ...part, text: '' } });
      push({ type: 'response.output_text.delta', ...common, delta: part.text, logprobs: [] });
      push({ type: 'response.output_text.done', ...common, text: part.text, logprobs: [] });
      push({ type: 'response.content_part.done', ...common, part });
    } else {
      push({ type: 'response.function_call_arguments.delta', item_id: item.id, output_index: index, delta: item.arguments });
      push({ type: 'response.function_call_arguments.done', item_id: item.id, output_index: index, name: item.name, arguments: item.arguments });
    }
    push({ type: 'response.output_item.done', output_index: index, item });
  }
  push({ type: 'response.completed', response: final });
  const wire = events.map(value => `event: ${value.type}\ndata: ${JSON.stringify(value)}\n\n`).join('');
  return new Response(new ReadableStream({ start(controller) { controller.enqueue(new TextEncoder().encode(wire)); controller.close(); } }),
    { status: 200, headers: { 'content-type': 'text/event-stream' } });
}
async function fixture(id, role = 'coder', overrides = {}) {
  const example = examples.roles.find(item => item.role === role);
  assert.ok(example);
  const directory = path.join(scratch, id), workspace = path.join(directory, 'workspace');
  await fs.mkdir(workspace, { recursive: true });
  const task = { ...examples.sharedTaskFields, taskId: `synthetic-${id}`, attemptId: `synthetic-attempt-${id}`, attemptNo: 1,
    taskType: example.taskType, subjectType: 'CHANGE', subjectId: 'synthetic', requiredCapabilities: [example.capability], priority: 0,
    skillRef: null, leaseToken: 'SYNTHETIC_LEASE_ONLY', leaseAcquiredAt: '2026-10-05T00:00:00Z', leaseExpiresAt: '2026-10-05T23:59:59Z',
    instruction: example.instruction, inputs: [], workspace: null, aiGuidance: null, ...overrides.task };
  const calls = [];
  const client = Object.fromEntries(['start', 'heartbeat', 'fail', 'complete'].map(method => [method, async (...args) => {
    calls.push({ method, args }); return overrides.client?.[method]?.(...args);
  }]));
  const runtime = overrides.runtime || new ApiRuntime({ apiKey: 'SYNTHETIC_KEY_ONLY', baseURL: 'https://fd005.invalid/v1',
    model: 'fd005-synthetic-model', reasoningEffort: 'low', maxTurns: 6, apiTimeoutMs: 1000, taskTimeoutMs: 4000,
    toolTimeoutMs: 500, maxOutputChars: 4096, allowShell: false, maxInputBytes: 200000, compactHistory: false,
    ...overrides.runtimeConfig });
  const adapter = createApiAttemptAdapter({ role, skillsRoot }, runtime, { fixture: 'synthetic-r30' });
  const config = { role, fallbackWorkspace: workspace, heartbeatMs: 30, evidenceRoot: path.join(directory, 'evidence'),
    taskTimeoutMs: 4000, cleanupTimeoutMs: 500, maxInputBytes: 200000, evidenceMaxBytes: 4 * 1024 * 1024,
    ...overrides.config };
  return { example, directory, workspace, task, client, calls, adapter, config };
}
const count = (fixture, method) => fixture.calls.filter(call => call.method === method).length;
async function execute(fixture, handler) {
  assert.equal(activeFetch, undefined);
  const requests = [];
  activeFetch = async (body, signal) => { requests.push({ body, signal }); return handler(body, signal, requests.length - 1); };
  try { return { outcome: await runClaimedAttempt(fixture.config, fixture.client, fixture.task, fixture.adapter), requests }; }
  finally { activeFetch = undefined; }
}
function assertDenied(fixture) { assert.equal(count(fixture, 'complete'), 0); }
function outputMetadata(raw) {
  if (typeof raw !== 'string') return { present: false };
  let value;
  try { value = JSON.parse(raw); } catch { value = null; }
  const object = value && typeof value === 'object' ? value : {};
  return {
    present: true,
    chars: raw.length,
    code: object.code ?? object.error?.code ?? null,
    complete: object.complete ?? object.meta?.complete ?? null,
    truncated: object.truncated ?? object.meta?.truncated ?? null,
    partial: object.partial ?? object.meta?.partial ?? null,
  };
}

test('API-16 preserved roles reject through public production adapter factory', () => {
  const fake = { execute: async () => '', settled: async () => true, assertSuccessful() {}, clear() {} };
  for (const role of ['release-readiness', 'orchestrator']) {
    assert.throws(() => assertExecutableRole(role));
    assert.throws(() => createApiAttemptAdapter({ role, skillsRoot }, fake, {}));
  }
});

test('API-17 reviewer never receives write tools and forged write has no side effect', async () => {
  const f = await fixture('reviewer-write', 'reviewer');
  const target = path.join(f.workspace, 'readonly.txt'); await fs.writeFile(target, 'original');
  const result = await execute(f, (body, signal, index) => {
    for (const name of ['write', 'edit', 'edit_many', 'bash']) assert.ok(!body.tools.some(tool => tool.name === name));
    return response(body, index === 0 ? [toolItem('write', { path: 'readonly.txt', content: 'forbidden' })] : [textItem(f.example.finalResponse)]);
  });
  assertDenied(f); assert.equal(await fs.readFile(target, 'utf8'), 'original'); assert.ok(result.requests.length <= 2);
});

test('API-18 coder write stays within assigned workspace', async () => {
  const f = await fixture('coder-boundary');
  const outside = path.join(f.directory, 'outside.txt'); await fs.writeFile(outside, 'original');
  const result = await execute(f, (body, signal, index) => response(body, index === 0
    ? [toolItem('write', { path: '../outside.txt', content: 'forbidden' })] : [textItem(f.example.finalResponse)]));
  assertDenied(f); assert.equal(await fs.readFile(outside, 'utf8'), 'original'); assert.ok(result.requests.length <= 2);
});

test('API-19 verifier integration alias uses integration profile and report', async () => {
  assert.deepEqual(getProfile('verifier', 'INTEGRATION_VERIFICATION'), getProfile('integration-verifier', 'INTEGRATION_VERIFICATION'));
  const f = await fixture('verifier-alias', 'verifier', { task: { taskType: 'INTEGRATION_VERIFICATION' } });
  const result = await execute(f, body => response(body, [textItem('Synthetic verification.\nAIW_VERDICT: PASS')]));
  assert.equal(result.outcome.status, 'completed'); assert.equal(count(f, 'complete'), 1);
  assert.equal(f.calls.find(call => call.method === 'complete').args[3].verdict, 'PASS');
});

test('API-23 forced cancellation aborts model request before completion', async () => {
  const controller = new AbortController();
  const f = await fixture('cancel-model', 'coder', { config: { forceSignal: controller.signal } });
  let observedSignal;
  const result = await execute(f, (body, signal) => {
    observedSignal = signal;
    const pending = new Promise((resolve, reject) => signal.addEventListener('abort', () => reject(new Error('SYNTHETIC_ABORTED')), { once: true }));
    controller.abort(new Error('SYNTHETIC_STOP'));
    return pending;
  });
  assert.equal(observedSignal.aborted, true); assertDenied(f); assert.notEqual(result.outcome.status, 'completed');
});

test('API-24 lost task lease suppresses complete and fail', async () => {
  let modelStarted = false;
  const f = await fixture('lease-lost', 'coder', { client: { heartbeat: () => { if (modelStarted) throw new Error('SYNTHETIC_LEASE_LOST'); } } });
  const result = await execute(f, (body, signal) => {
    modelStarted = true;
    return new Promise((resolve, reject) => signal.addEventListener('abort', () => reject(new Error('SYNTHETIC_ABORTED')), { once: true }));
  });
  assert.ok(modelStarted); assertDenied(f); assert.equal(count(f, 'fail'), 0); assert.notEqual(result.outcome.status, 'completed');
});

test('API-27 multibyte read result remains bounded and marked incomplete', async () => {
  const f = await fixture('read-bounded');
  await fs.writeFile(path.join(f.workspace, 'large.txt'), '中文🧪'.repeat(3000));
  let output;
  const result = await execute(f, (body, signal, index) => {
    if (index === 0) return response(body, [toolItem('read', { path: 'large.txt', offset: 1, limit: 10 }, 'read-multibyte')]);
    output = body.input.find(item => item.type === 'function_call_output' && item.call_id === 'read-multibyte')?.output;
    return response(body, [textItem(f.example.finalResponse)]);
  });
  console.log('FD005_TOOL_META API-27 ' + JSON.stringify(outputMetadata(output)));
  assert.equal(result.outcome.status, 'completed'); assert.ok(output && output.length <= 4096);
  assert.match(output, /complete|truncat|partial/i); assert.equal(count(f, 'complete'), 1);
});

test('API-29 edit_many preserves BOM/CRLF and refuses ambiguous edit', async () => {
  const good = await fixture('edit-many-good');
  const original = '\ufeffalpha\r\nbeta\r\n';
  await fs.writeFile(path.join(good.workspace, 'file.txt'), original);
  let goodOutput;
  const result = await execute(good, (body, signal, index) => {
    if (index === 1) goodOutput = body.input.find(item => item.type === 'function_call_output')?.output;
    return response(body, index === 0
      ? [toolItem('edit_many', { path: 'file.txt', edits: [{ oldText: 'alpha', newText: 'ALPHA' }, { oldText: 'beta', newText: 'BETA' }] })]
      : [textItem(good.example.finalResponse)]);
  });
  console.log('FD005_TOOL_META API-29-good ' + JSON.stringify(outputMetadata(goodOutput)));
  assert.equal(result.outcome.status, 'completed'); assert.equal(await fs.readFile(path.join(good.workspace, 'file.txt'), 'utf8'), '\ufeffALPHA\r\nBETA\r\n');
  const bad = await fixture('edit-many-ambiguous');
  await fs.writeFile(path.join(bad.workspace, 'file.txt'), 'same same');
  let badOutput;
  await execute(bad, (body, signal, index) => {
    if (index === 1) badOutput = body.input.find(item => item.type === 'function_call_output')?.output;
    return response(body, index === 0
      ? [toolItem('edit_many', { path: 'file.txt', edits: [{ oldText: 'same', newText: 'new' }] })]
      : [textItem(bad.example.finalResponse)]);
  });
  console.log('FD005_TOOL_META API-29-ambiguous ' + JSON.stringify(outputMetadata(badOutput)));
  assertDenied(bad); assert.equal(await fs.readFile(path.join(bad.workspace, 'file.txt'), 'utf8'), 'same same');
  const overlap = await fixture('edit-many-overlap');
  await fs.writeFile(path.join(overlap.workspace, 'file.txt'), 'abcd');
  await execute(overlap, (body, signal, index) => response(body, index === 0
    ? [toolItem('edit_many', { path: 'file.txt', edits: [{ oldText: 'abc', newText: 'ABC' }, { oldText: 'bcd', newText: 'BCD' }] })]
    : [textItem(overlap.example.finalResponse)]));
  assertDenied(overlap); assert.equal(await fs.readFile(path.join(overlap.workspace, 'file.txt'), 'utf8'), 'abcd');
});

test('API-28 incomplete raw tool arguments cause no write', async () => {
  const f = await fixture('truncated-arguments');
  const target = path.join(f.workspace, 'unchanged.txt');
  await fs.writeFile(target, 'unchanged');
  const raw = '{"path":"unchanged.txt","content":"forbidden';
  const callId = 'truncated-write';
  const result = await execute(f, (body, signal, index) => response(body, index === 0
    ? [{ ...toolItem('write', {}, callId), arguments: raw }]
    : [textItem(f.example.finalResponse)]));
  assert.equal(await fs.readFile(target, 'utf8'), 'unchanged');
  assertDenied(f);
  assert.notEqual(result.outcome.status, 'completed');
});

test('API-33 legacy eight-field profile and report model path remain compatible', async () => {
  const legacy = examples.compatibility.find(item => item.profile).profile;
  const file = path.join(scratch, 'legacy-profile.toml');
  await fs.writeFile(file, Object.entries(legacy).map(([key, value]) => `${key} = ${JSON.stringify(value)}`).join('\n'));
  const profile = loadProfile(file);
  assert.equal(profile.readOnly, true); assert.deepEqual(profile.allowedTaskTypes, ['CODE_REVIEW']);
  const f = await fixture('legacy-path', 'reviewer');
  const result = await execute(f, body => { assert.equal(body.model, 'fd005-synthetic-model'); return response(body, [textItem(f.example.finalResponse)]); });
  assert.equal(result.outcome.status, 'completed'); assert.equal(f.calls.find(call => call.method === 'complete').args[3].verdict, 'REPAIR_REQUIRED');
});

test('API-27 grep reports incomplete scan separately from bounded output', async () => {
  const f = await fixture('grep-partial');
  const searchRoot = path.join(f.workspace, 'search');
  await fs.mkdir(searchRoot);
  for (let i = 0; i < 80; i++) {
    await fs.writeFile(path.join(searchRoot, `file-${String(i).padStart(3, '0')}.txt`), '中文🧪 needle\n'.repeat(180));
  }
  let raw;
  const result = await execute(f, (body, signal, index) => {
    if (index === 0) return response(body, [toolItem('grep', { path: 'search', text: 'needle' }, 'grep-many')]);
    raw = body.input.find(item => item.type === 'function_call_output' && item.call_id === 'grep-many')?.output;
    return response(body, [textItem(f.example.finalResponse)]);
  });
  const envelope = JSON.parse(raw);
  console.log('FD005_TOOL_META API-27-grep ' + JSON.stringify({ chars: raw.length, complete: envelope.complete,
    truncated: envelope.truncated, scanComplete: envelope.data?.scanComplete, status: envelope.status }));
  assert.equal(result.outcome.status, 'completed');
  assert.ok(raw.length <= 4096);
  assert.equal(envelope.complete, false);
  assert.equal(envelope.data?.scanComplete, false);
});

test('API-20 independent reads retain parallel call IDs and bounded results', async () => {
  const f = await fixture('parallel-reads');
  await fs.writeFile(path.join(f.workspace, 'left.txt'), 'LEFT-中文\n');
  await fs.writeFile(path.join(f.workspace, 'right.txt'), 'RIGHT-🧪\n');
  let outputs;
  const result = await execute(f, (body, signal, index) => {
    if (index === 0) {
      assert.equal(body.parallel_tool_calls, true);
      return response(body, [
        toolItem('read', { path: 'left.txt', offset: 1, limit: 1 }, 'parallel-left'),
        toolItem('read', { path: 'right.txt', offset: 1, limit: 1 }, 'parallel-right'),
      ]);
    }
    outputs = body.input.filter(item => item.type === 'function_call_output');
    return response(body, [textItem(f.example.finalResponse)]);
  });
  assert.equal(result.outcome.status, 'completed');
  assert.deepEqual(new Set(outputs.map(item => item.call_id)), new Set(['parallel-left', 'parallel-right']));
  assert.ok(outputs.find(item => item.call_id === 'parallel-left').output.includes('LEFT-中文'));
  assert.ok(outputs.find(item => item.call_id === 'parallel-right').output.includes('RIGHT-🧪'));
  assert.ok(outputs.every(item => item.output.length <= 4096));
});

test('API-21 same-path write and edit preserve order in a single model turn', async () => {
  const f = await fixture('same-path-order');
  const target = path.join(f.workspace, 'ordered.txt');
  await fs.writeFile(target, 'before');
  let outputs;
  const result = await execute(f, (body, signal, index) => {
    if (index === 0) return response(body, [
      toolItem('write', { path: 'ordered.txt', content: 'first' }, 'ordered-write'),
      toolItem('edit', { path: 'ordered.txt', oldText: 'first', newText: 'second' }, 'ordered-edit'),
    ]);
    outputs = body.input.filter(item => item.type === 'function_call_output');
    return response(body, [textItem(f.example.finalResponse)]);
  });
  assert.equal(result.outcome.status, 'completed');
  assert.equal(await fs.readFile(target, 'utf8'), 'second');
  assert.deepEqual(new Set(outputs.map(item => item.call_id)), new Set(['ordered-write', 'ordered-edit']));
  assert.ok(outputs.every(item => JSON.parse(item.output).status === 'ok'));
});

test('API-16 production entry rejects reserved roles before network registration', async () => {
  for (const role of ['release-readiness', 'orchestrator']) {
    const env = { ...process.env, FD005_TESTER_SESSION: 'fd005-tester-20261005-e8931791',
      FD005_R30_SCRATCH: scratch, AIW_PROCESS_ROLE: role, OPENAI_API_KEY: 'SYNTHETIC_KEY_ONLY',
      AIW_CONTROL_PLANE_URL: 'https://fd005.invalid', OPENAI_AGENTS_DONT_LOG_MODEL_DATA: '1',
      OPENAI_AGENTS_DONT_LOG_TOOL_DATA: '1', OPENAI_AGENTS_DISABLE_TRACING: '1' };
    delete env.AIW_WORKER_TOKEN;
    const child = spawn(process.execPath, [
      '--import=./tests/fd005/r30/register-loader.mjs', '--import=./tests/fd005/r30/entry-guard.mjs',
      'workers/process-typescript-with-api/src/index.ts',
    ], { cwd: root, env, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '';
    for (const stream of [child.stdout, child.stderr]) stream.on('data', chunk => { output += chunk.toString('utf8').slice(0, 2000); });
    let timedOut = false;
    const timer = setTimeout(() => { timedOut = true; child.kill(); }, 5000);
    let exit;
    try { exit = await new Promise((resolve, reject) => { child.once('error', reject); child.once('close', resolve); }); }
    finally { clearTimeout(timer); }
    console.log('FD005_ENTRY_META ' + JSON.stringify({ role, exit, timedOut,
      reservedRoleMentioned: output.includes(role), networkGuardHit: output.includes('FD005_NETWORK_DISABLED') }));
    assert.equal(timedOut, false);
    assert.notEqual(exit, 0);
    assert.ok(output.includes(role), 'Production entry must identify the rejected reserved role');
    assert.ok(!output.includes('FD005_NETWORK_DISABLED'), 'No registration request may precede role rejection');
  }
});

test('API-33 planner preserves legacy work-item manifest and report with model path', async () => {
  const f = await fixture('legacy-manifest', 'planner');
  const manifest = 'AIW_IMPLEMENTATION_WORK_ITEMS_V1\n\nAIW_WORK_ITEM_BEGIN endpoint\nAIW_CAPABILITIES java,maven\nImplement synthetic endpoint.\nAIW_WORK_ITEM_END\n\nAIW_IMPLEMENTATION_WORK_ITEMS_END';
  const plan = `Synthetic plan.\n\n${manifest}`;
  const result = await execute(f, body => {
    assert.equal(body.model, 'fd005-synthetic-model');
    return response(body, [textItem(plan)]);
  });
  assert.equal(result.outcome.status, 'completed');
  const complete = f.calls.find(call => call.method === 'complete');
  assert.ok(complete);
  assert.equal(complete.args[2], plan);
  assert.deepEqual(complete.args[3], { verdict: 'PASS', summary: plan });
});

test('API-31 critical evidence failure denies success and API sessions do not persist secrets or source', async () => {
  const secret = 'FD005_SYNTHETIC_SOURCE_AND_SECRET_SENTINEL_7b10';
  for (const role of examples.roles.map(item => item.role)) {
    const f = await fixture(`privacy-${role}`, role, { task: { instruction: secret, leaseToken: secret } });
    await fs.writeFile(path.join(f.workspace, 'source.txt'), secret);
    const result = await execute(f, body => response(body, [textItem(f.example.finalResponse)]));
    assert.equal(result.outcome.status, 'completed');
    const evidenceFiles = await fs.readdir(f.config.evidenceRoot, { recursive: true });
    for (const name of evidenceFiles) {
      const file = path.join(f.config.evidenceRoot, name);
      if ((await fs.stat(file)).isFile()) assert.ok(!(await fs.readFile(file, 'utf8')).includes(secret), `${role} persisted secret in ${name}`);
    }
  }
  const f = await fixture('evidence-write-failure');
  const collision = path.join(f.directory, 'evidence-collision');
  await fs.writeFile(collision, 'synthetic collision');
  f.config.evidenceRoot = collision;
  const result = await execute(f, () => { throw new Error('MODEL_MUST_NOT_START'); });
  assert.notEqual(result.outcome.status, 'completed');
  assert.equal(count(f, 'complete'), 0);
});

test('API-22 queued write cancels while shell holds workspace; later call runs after ordinary failure', async () => {
  const controller = new AbortController();
  const pending = await fixture('queued-cancel', 'coder', {
    config: { forceSignal: controller.signal },
    runtimeConfig: { allowShell: true, toolTimeoutMs: 1200 },
  });
  const target = path.join(pending.workspace, 'target.txt');
  const ready = path.join(pending.workspace, 'ready.flag');
  await fs.writeFile(target, 'original');
  const shell = 'node -e "const fs=require(\'node:fs\');fs.writeFileSync(\'ready.flag\',\'ready\');setTimeout(()=>{},600)"';
  const attempt = execute(pending, (body, signal, index) => response(body, index === 0 ? [
    toolItem('bash', { command: shell }, 'hold-workspace'),
    toolItem('write', { path: 'target.txt', content: 'forbidden-after-cancel' }, 'queued-write'),
  ] : [textItem(pending.example.finalResponse)]));
  const deadline = Date.now() + 1000;
  try {
    while (Date.now() < deadline && !(await fs.stat(ready).catch(() => undefined))) await new Promise(resolve => setTimeout(resolve, 10));
    assert.ok(await fs.stat(ready).catch(() => undefined), 'Shell must hold workspace before cancel');
  } finally { controller.abort(new Error('SYNTHETIC_QUEUE_CANCEL')); }
  const cancelled = await attempt;
  assert.notEqual(cancelled.outcome.status, 'completed');
  assert.equal(count(pending, 'complete'), 0);
  assert.equal(await fs.readFile(target, 'utf8'), 'original');

  const resumed = await fixture('failure-recovery');
  const replacement = path.join(resumed.workspace, 'after.txt');
  let failedOutput, goodOutput;
  const continued = await execute(resumed, (body, signal, index) => {
    if (index === 0) return response(body, [toolItem('edit', { path: 'missing.txt', oldText: 'x', newText: 'y' }, 'ordinary-fail')]);
    if (index === 1) {
      failedOutput = body.input.find(item => item.type === 'function_call_output' && item.call_id === 'ordinary-fail')?.output;
      return response(body, [toolItem('write', { path: 'after.txt', content: 'later-operation-ran' }, 'later-write')]);
    }
    goodOutput = body.input.find(item => item.type === 'function_call_output' && item.call_id === 'later-write')?.output;
    return response(body, [textItem(resumed.example.finalResponse)]);
  });
  assert.ok(failedOutput && JSON.parse(failedOutput).status !== 'ok');
  assert.equal(JSON.parse(goodOutput).status, 'ok');
  assert.equal(await fs.readFile(replacement, 'utf8'), 'later-operation-ran');
  assert.notEqual(continued.outcome.status, 'completed');
});
