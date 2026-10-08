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
const scratch = process.env.FD005_R33_SCRATCH;
assert.ok(scratch && process.env.FD005_TESTER_SESSION === 'fd005-tester-20261005-r33-7e49c2');
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
  const adapter = createApiAttemptAdapter({ role, skillsRoot }, runtime, { fixture: 'synthetic-r33' });
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

test('API-16 production entry rejects reserved roles before registration', async () => {
  for (const role of ['release-readiness', 'orchestrator']) {
    const env = { ...process.env, FD005_TESTER_SESSION: 'fd005-tester-20261005-r33-7e49c2',
      FD005_R33_SCRATCH: scratch, AIW_PROCESS_ROLE: role, OPENAI_API_KEY: 'SYNTHETIC_KEY_ONLY',
      AIW_CONTROL_PLANE_URL: 'https://fd005.invalid', OPENAI_AGENTS_DONT_LOG_MODEL_DATA: '1',
      OPENAI_AGENTS_DONT_LOG_TOOL_DATA: '1', OPENAI_AGENTS_DISABLE_TRACING: '1' };
    delete env.AIW_WORKER_TOKEN;
    const child = spawn(process.execPath, [
      '--import=./tests/fd005/r33/register-loader.mjs', '--import=./tests/fd005/r33/entry-guard.mjs',
      'workers/process-typescript-with-api/src/index.ts',
    ], { cwd: root, env, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '';
    for (const stream of [child.stdout, child.stderr]) stream.on('data', chunk => {
      output = (output + chunk.toString('utf8')).slice(0, 4000);
    });
    let timedOut = false;
    const timer = setTimeout(() => { timedOut = true; child.kill(); }, 5000);
    let exit;
    try { exit = await new Promise((resolve, reject) => { child.once('error', reject); child.once('close', resolve); }); }
    finally { clearTimeout(timer); }
    const category = output.includes('FD005_NETWORK_DISABLED') ? 'network-attempt'
      : output.includes(role) ? 'reserved-role'
      : /(?:@openai\/agents|openai).*(?:does not provide an export|not a function|not a constructor)|(?:does not provide an export|not a function|not a constructor).*@openai\/agents/i.test(output)
        ? 'sdk-version-incompatible'
      : /ERR_MODULE_NOT_FOUND|ERR_UNKNOWN_FILE_EXTENSION|ERR_PACKAGE_PATH_NOT_EXPORTED|ERR_UNSUPPORTED_DIR_IMPORT|Cannot find package|Cannot find module|SyntaxError/.test(output)
        ? 'module-or-syntax-load-error'
      : /(?:missing|required|invalid).*(?:AIW_|OPENAI_|config|configuration)|(?:AIW_|OPENAI_).*(?:missing|required|invalid)/i.test(output)
        ? 'configuration-error' : 'other-startup-error';
    const firstLine = category === 'reserved-role' ? `reserved role ${role}` : category;
    console.log('FD005_ENTRY_META ' + JSON.stringify({ role, exit, timedOut, category, firstLine }));
    assert.equal(timedOut, false);
    assert.notEqual(exit, 0);
    assert.equal(category, 'reserved-role', 'Production entry must reject this role before registration');
  }
});

test('API-33 planner preserves legacy manifest, verdict and model path', async () => {
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
  assert.equal(complete.args[3].summary, plan);
  assert.ok(complete.args[3].summary.includes(manifest));
  assert.equal(complete.args[3].verdict, 'PASS');
  assert.equal(result.requests[0].body.model, 'fd005-synthetic-model');
});


