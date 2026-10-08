import * as runtime from '../../../workers/process-typescript/runtime-core/index.mjs';
import { createHash } from 'node:crypto';
import { appendFileSync } from 'node:fs';
import { assert, fs, path, sandbox, root, resultRoot, deferred, outcome } from './harness.mjs';

// All requests stop at this in-memory function. No socket or real credential is available.
let activeFetch;
globalThis.fetch = async (input, init = {}) => {
  if (!activeFetch) throw new Error('FD005_FETCH_WITHOUT_SYNTHETIC_SERVER');
  return activeFetch(input, init);
};
const { createApiAttemptAdapter } = await import('../../../workers/process-typescript-with-api/src/api-adapter.ts');
const { ApiRuntime } = await import('../../../workers/process-typescript-with-api/src/api-runtime.ts');
const profileApi = await import('../../../workers/process-typescript-with-api/src/profiles.ts');
const instructionApi = await import('../../../workers/process-typescript-with-api/src/instructions.ts');
export { runtime, createApiAttemptAdapter, ApiRuntime, profileApi, instructionApi };
export const examples = JSON.parse(await fs.readFile(path.join(root, 'workers', 'runtime-contract.examples.json'), 'utf8'));
export const rolesByName = new Map(examples.roles.map(item => [item.role, item]));
export const digest = value => createHash('sha256').update(value).digest('hex');
export const installedVersions = Object.fromEntries(await Promise.all(['@openai/agents', '@openai/agents-core', '@openai/agents-openai', 'openai', 'typescript'].map(async name => {
  const manifest = JSON.parse(await fs.readFile(path.join(root, 'workers', 'process-typescript-with-api', 'node_modules', name, 'package.json'), 'utf8'));
  return [name, manifest.version];
})));
for (const name of ['@openai/agents', '@openai/agents-core', '@openai/agents-openai']) assert.equal(installedVersions[name], '0.3.9', 'Installed SDK must match the public baseline');
assert.equal(installedVersions.openai, '6.49.0');
appendFileSync(path.join(resultRoot, 'sdk-version-metadata.jsonl'), JSON.stringify({ syntheticTestOnly: true, installedVersions }) + '\n');

let responseSequence = 0, messageSequence = 0;
export function responseObject(request, output, status = 'completed', usage = true) {
  const text = output.filter(item => item.type === 'message').flatMap(item => item.content).filter(item => item.type === 'output_text').map(item => item.text).join('');
  return { id: `resp_fd005_synthetic_only_${responseSequence++}`, object: 'response', created_at: 1791158400,
    model: request.model || 'fd005-synthetic-model', status, output, output_text: text,
    error: null, incomplete_details: status === 'incomplete' ? { reason: 'max_output_tokens' } : null,
    instructions: null, metadata: {}, parallel_tool_calls: true, temperature: null, top_p: null,
    tool_choice: 'auto', tools: [], store: false, truncation: 'disabled', previous_response_id: null,
    ...(usage ? { usage: { input_tokens: 23, output_tokens: 7, total_tokens: 30,
      input_tokens_details: { cached_tokens: 4 }, output_tokens_details: { reasoning_tokens: 2 } } } : {}) };
}
export const textItem = text => ({ id: `msg_fd005_synthetic_only_${messageSequence++}`, type: 'message', role: 'assistant', status: 'completed',
  content: [{ type: 'output_text', text, annotations: [], logprobs: [] }] });
export const toolItem = (name, args, callId = 'call_fd005_synthetic_only') => ({ id: `fc_${callId}`, type: 'function_call', name, arguments: typeof args === 'string' ? args : JSON.stringify(args), call_id: callId, status: 'completed' });

// Official Responses stream-event shapes from the installed public .d.ts; all text is synthetic.
export function sseResponse(request, output = [textItem('Synthetic complete output.')], options = {}) {
  const final = responseObject(request, output, options.status || 'completed', options.usage !== false);
  let seq = 0;
  const events = [];
  const event = value => events.push({ ...value, sequence_number: seq++ });
  event({ type: 'response.created', response: { ...final, status: 'in_progress', output: [], output_text: '' } });
  event({ type: 'response.in_progress', response: { ...final, status: 'in_progress', output: [], output_text: '' } });
  for (const [index, item] of output.entries()) {
    event({ type: 'response.output_item.added', output_index: index, item: { ...item, status: 'in_progress', ...(item.type === 'message' ? { content: [] } : { arguments: '' }) } });
    if (item.type === 'message') {
      const part = item.content[0];
      const common = { item_id: item.id, output_index: index, content_index: 0 };
      event({ type: 'response.content_part.added', ...common, part: { ...part, text: '' } });
      event({ type: 'response.output_text.delta', ...common, delta: part.text, logprobs: [] });
      event({ type: 'response.output_text.done', ...common, text: part.text, logprobs: [] });
      event({ type: 'response.content_part.done', ...common, part });
    } else {
      event({ type: 'response.function_call_arguments.delta', item_id: item.id, output_index: index, delta: item.arguments });
      event({ type: 'response.function_call_arguments.done', item_id: item.id, output_index: index, name: item.name, arguments: item.arguments });
    }
    event({ type: 'response.output_item.done', output_index: index, item });
  }
  if (!options.omitTerminal) event({ type: options.terminalType || (final.status === 'incomplete' ? 'response.incomplete' : 'response.completed'), response: final });
  const wire = events.map(value => `event: ${value.type}\ndata: ${JSON.stringify(value)}\n\n`).join('');
  if (!request.stream) return new Response(JSON.stringify(final), { status: 200, headers: { 'content-type': 'application/json' } });
  return new Response(new ReadableStream({ start(controller) { controller.enqueue(new TextEncoder().encode(wire)); controller.close(); } }), { status: 200, headers: { 'content-type': 'text/event-stream', 'x-request-id': 'fd005-synthetic-only' } });
}
export const httpModelError = (status = 400) => new Response(JSON.stringify({ error: { message: 'SYNTHETIC_MODEL_FAILURE', type: 'invalid_request_error', code: 'SYNTHETIC_FAILURE' } }), { status, headers: { 'content-type': 'application/json' } });

export async function memoryFetch(operation, handler) {
  assert.equal(activeFetch, undefined, 'Synthetic fetch scopes may not overlap');
  const requests = [];
  activeFetch = async (input, init = {}) => {
    const url = new URL(typeof input === 'string' || input instanceof URL ? input : input.url);
    assert.equal(url.origin, 'https://fd005.invalid', 'Unexpected endpoint: network remains disabled');
    assert.equal(url.pathname, '/v1/responses', 'Only the declared synthetic Responses endpoint is allowed');
    assert.equal(init.method, 'POST');
    const body = JSON.parse(typeof init.body === 'string' ? init.body : '{}');
    const headers = new Headers(init.headers);
    assert.equal(headers.get('authorization'), 'Bearer SYNTHETIC_API_KEY_ONLY');
    const request = { body, signal: init.signal, url: url.href, method: init.method };
    requests.push(request);
    return handler(body, init.signal, requests.length - 1, requests);
  };
  try { return { value: await operation(requests), requests }; }
  finally {
    activeFetch = undefined;
    appendFileSync(path.join(resultRoot, 'api-request-metadata.jsonl'), JSON.stringify({ syntheticTestOnly: true,
      count: requests.length, requests: requests.map(({ body, signal }) => ({ model: body.model, store: body.store,
        truncation: body.truncation, parallelToolCalls: body.parallel_tool_calls, stream: body.stream,
        inputBytes: Buffer.byteLength(JSON.stringify(body.input ?? [])), inputDigest: digest(JSON.stringify(body.input ?? [])),
        toolNames: (body.tools || []).map(tool => tool.name), signalAborted: signal?.aborted === true })) }) + '\n');
  }
}

export function memoryClient(overrides = {}) {
  const calls = [];
  const client = { calls };
  for (const method of ['start', 'heartbeat', 'fail', 'complete']) client[method] = async (...args) => { calls.push({ method, args }); return overrides[method]?.(...args); };
  return client;
}
export function actualConfig(extra = {}) {
  return { apiKey: 'SYNTHETIC_API_KEY_ONLY', baseURL: 'https://fd005.invalid/v1', model: 'fd005-synthetic-model', reasoningEffort: 'low',
    maxTurns: 8, apiTimeoutMs: 1000, taskTimeoutMs: 4000, toolTimeoutMs: 500, maxOutputChars: 2048, allowShell: false,
    maxInputBytes: 200000, compactHistory: false, ...extra };
}
export async function apiFixture(id, role = 'coder', options = {}) {
  const fixture = await sandbox(id);
  const example = rolesByName.get(role);
  assert.ok(example, 'Role must come from the public examples');
  const profile = profileApi.getProfile(role, options.taskType || example.taskType);
  assert.equal(profile.readOnly, example.readOnly);
  const skillsRoot = path.join(fixture.directory, 'synthetic-skills');
  for (const name of new Set([...(profile.protocolSkills || []), profile.primarySkill].filter(Boolean))) {
    const directory = path.join(skillsRoot, name);
    await fs.mkdir(path.join(directory, 'references'), { recursive: true });
    await fs.mkdir(path.join(directory, 'agents'));
    await fs.writeFile(path.join(directory, 'SKILL.md'), `# SYNTHETIC_FULL_SKILL_MAIN_${name}\nRequired synthetic rules and acceptance.`);
    await fs.writeFile(path.join(directory, 'references', 'fixture.md'), `SYNTHETIC_REFERENCE_${name}`);
    await fs.writeFile(path.join(directory, 'agents', 'fixture.md'), `SYNTHETIC_AGENT_${name}`);
  }
  const task = { ...examples.sharedTaskFields, taskId: `synthetic-task-${id}`, attemptId: `synthetic-attempt-${id}`, attemptNo: 1,
    taskType: example.taskType, subjectType: 'CHANGE', subjectId: 'synthetic-subject', requiredCapabilities: [example.capability], priority: 0,
    skillRef: null, leaseToken: 'SYNTHETIC_LEASE_TEST_ONLY', leaseAcquiredAt: '2026-10-05T00:00:00Z', leaseExpiresAt: '2026-10-05T23:59:59Z',
    instruction: example.instruction, inputs: [], workspace: null, aiGuidance: null, ...options.task };
  const api = new ApiRuntime(actualConfig(options.runtime));
  const adapter = createApiAttemptAdapter({ role, skillsRoot }, api, { agents: installedVersions['@openai/agents'], openai: installedVersions.openai, fixture: 'synthetic-test-only' });
  const client = memoryClient(options.client);
  const config = { role, fallbackWorkspace: fixture.workspace, heartbeatMs: 25, evidenceRoot: path.join(fixture.directory, 'evidence'), taskTimeoutMs: 4000,
    cleanupTimeoutMs: 500, maxInputBytes: 200000, evidenceMaxBytes: 4 * 1024 * 1024, ...options.config };
  return { ...fixture, example, profile, skillsRoot, task, api, adapter, client, config };
}
export const executeFixture = fixture => runtime.runClaimedAttempt(fixture.config, fixture.client, fixture.task, fixture.adapter);
export const callCount = (fixture, method) => fixture.client.calls.filter(item => item.method === method).length;
export const completeCall = fixture => fixture.client.calls.find(item => item.method === 'complete');
export async function assertCompleted(fixture, result) {
  assert.equal(result.rejected, false);
  assert.equal(callCount(fixture, 'complete'), 1); assert.equal(callCount(fixture, 'fail'), 0);
  assert.equal(completeCall(fixture).args[1], `api:${fixture.task.attemptId}`);
  assert.equal(completeCall(fixture).args[2], null, 'Non-Git public WorkspaceResult remains null');
  assert.notEqual(await fixture.api.settled(), false);
  fixture.api.assertSuccessful();
}
export function assertNoComplete(fixture) { assert.equal(callCount(fixture, 'complete'), 0); }
export function announcedTool(request, name, supplied) {
  const definition = request.tools?.find(item => item.name === name);
  assert.ok(definition, `Public tool ${name} must be announced by the real SDK request`);
  const required = definition.parameters?.required || [];
  for (const key of required) assert.ok(Object.hasOwn(supplied, key), `Fixture must supply advertised required tool field ${key}`);
  return toolItem(name, supplied);
}
export const cancelledFetch = started => (request, signal) => {
  started.resolve({ request, signal });
  return new Promise((_, reject) => {
    const abort = () => reject(signal.reason || new DOMException('Synthetic cancel', 'AbortError'));
    if (signal?.aborted) abort(); else signal?.addEventListener('abort', abort, { once: true });
  });
};
