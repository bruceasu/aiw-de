import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import net from 'node:net';
import tls from 'node:tls';
import dns from 'node:dns';
import { fileURLToPath } from 'node:url';
import { createApiAttemptAdapter } from '../../../workers/process-typescript-with-api/src/api-adapter.ts';
import { ApiRuntime } from '../../../workers/process-typescript-with-api/src/api-runtime.ts';
import { assertExecutableRole, getProfile, loadProfile } from '../../../workers/process-typescript-with-api/src/profiles.ts';
import { runClaimedAttempt } from '../../../workers/process-typescript/runtime-core/index.mjs';

const root = path.resolve(fileURLToPath(new URL('../../../', import.meta.url)));
const scratch = process.env.FD005_R23_SCRATCH;
assert.ok(scratch && process.env.FD005_TESTER_SESSION === 'fd005-tester-20261005-b07656ac');
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
    toolTimeoutMs: 500, maxOutputChars: 256, allowShell: false, maxInputBytes: 200000, compactHistory: false,
    ...overrides.runtimeConfig });
  const adapter = createApiAttemptAdapter({ role, skillsRoot }, runtime, { fixture: 'synthetic-r23' });
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
  await fs.writeFile(path.join(f.workspace, 'large.txt'), '中文🧪'.repeat(300));
  let output;
  const result = await execute(f, (body, signal, index) => {
    if (index === 0) return response(body, [toolItem('read', { path: 'large.txt', offset: 1, limit: 10 }, 'read-multibyte')]);
    output = body.input.find(item => item.type === 'function_call_output' && item.call_id === 'read-multibyte')?.output;
    return response(body, [textItem(f.example.finalResponse)]);
  });
  assert.equal(result.outcome.status, 'completed'); assert.ok(output && output.length < 1500);
  assert.match(output, /complete|truncat|partial/i); assert.equal(count(f, 'complete'), 1);
});

test('API-29 edit_many preserves BOM/CRLF and refuses ambiguous edit', async () => {
  const good = await fixture('edit-many-good');
  const original = '\ufeffalpha\r\nbeta\r\n';
  await fs.writeFile(path.join(good.workspace, 'file.txt'), original);
  const result = await execute(good, (body, signal, index) => response(body, index === 0
    ? [toolItem('edit_many', { path: 'file.txt', edits: [{ oldText: 'alpha', newText: 'ALPHA' }, { oldText: 'beta', newText: 'BETA' }] })]
    : [textItem(good.example.finalResponse)]));
  assert.equal(result.outcome.status, 'completed'); assert.equal(await fs.readFile(path.join(good.workspace, 'file.txt'), 'utf8'), '\ufeffALPHA\r\nBETA\r\n');
  const bad = await fixture('edit-many-ambiguous');
  await fs.writeFile(path.join(bad.workspace, 'file.txt'), 'same same');
  await execute(bad, (body, signal, index) => response(body, index === 0
    ? [toolItem('edit_many', { path: 'file.txt', edits: [{ oldText: 'same', newText: 'new' }] })]
    : [textItem(bad.example.finalResponse)]));
  assertDenied(bad); assert.equal(await fs.readFile(path.join(bad.workspace, 'file.txt'), 'utf8'), 'same same');
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
