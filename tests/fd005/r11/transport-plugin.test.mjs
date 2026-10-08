import * as runtime from '../../../workers/process-typescript/runtime-core/index.mjs';
import { assert, fs, path, root, behavior, sandbox, outcome, deferred, within } from './harness.mjs';
import { createHash } from 'node:crypto';
const hash = bytes => createHash('sha256').update(bytes).digest('hex');

async function cpFixture(handler, operation) {
  const before = globalThis.fetch, requests = [];
  globalThis.fetch = async (input, init = {}) => {
    const url = new URL(typeof input === 'string' || input instanceof URL ? input : input.url);
    assert.equal(url.origin, 'https://fd005-cp.invalid'); assert.equal(init.method, 'POST');
    assert.equal(new Headers(init.headers).get('authorization'), 'Bearer SYNTHETIC_CP_KEY');
    const record = { pathname: url.pathname, headers: new Headers(init.headers), body: init.body ? JSON.parse(init.body) : null, signal: init.signal };
    requests.push(record); return handler(record, requests.length - 1);
  };
  const client = new runtime.ControlPlaneClient({ baseUrl: 'https://fd005-cp.invalid', workerId: 'synthetic worker/one', token: 'SYNTHETIC_CP_KEY', capabilities: ['coding'], maxConcurrency: 2, httpTimeoutMs: 100 }, 'synthetic-test-only');
  try { return { result: await operation(client), requests }; } finally { globalThis.fetch = before; }
}
const response = value => new Response(JSON.stringify(value), { status: 200, headers: { 'content-type': 'application/json' } });
const task = { taskId: 'synthetic task/one', attemptId: 'synthetic-attempt', attemptNo: 1, taskType: 'IMPLEMENTATION', leaseToken: 'SYNTHETIC_LEASE_ONLY', inputs: [], aiGuidance: { version: 'synthetic-guidance-v1' } };
behavior('CP-register', async () => {
  const run = await cpFixture(() => response({ workerId: 'synthetic worker/one', status: 'ACTIVE', activeLeases: 0, maxConcurrency: 2 }), client => client.register());
  assert.equal(run.requests[0].pathname, '/api/worker-protocol/v1/workers/register');
  assert.deepEqual(run.requests[0].body, { workerId: 'synthetic worker/one', runtimeType: 'synthetic-test-only', capabilities: ['coding'], maxConcurrency: 2 });
});
behavior('CP-claim-empty', async () => {
  const run = await cpFixture(() => new Response(null, { status: 204 }), client => client.claim());
  assert.equal(run.result, null); assert.equal(run.requests[0].pathname, '/api/worker-protocol/v1/tasks/claim');
  assert.deepEqual(run.requests[0].body, { workerId: 'synthetic worker/one' });
});
behavior('CP-claim-task', async () => {
  const run = await cpFixture(() => response(task), client => client.claim()); assert.deepEqual(run.result, task);
});
behavior('CP-start-selection', async () => {
  const selection = { id: 'approved-a', reason: 'synthetic approved selection', profile: { model: 'synthetic-model-a' } };
  const run = await cpFixture(() => response({}), client => client.start(task, selection));
  assert.equal(run.requests[0].pathname, '/api/worker-protocol/v1/tasks/synthetic%20task%2Fone/start');
  assert.deepEqual(run.requests[0].body, { workerId: 'synthetic worker/one', leaseToken: task.leaseToken, profileId: selection.id, profileVersion: task.aiGuidance.version, selectionReason: selection.reason });
});
behavior('CP-heartbeat', async () => {
  const run = await cpFixture(() => response({}), client => client.heartbeat(task, 'api:synthetic-attempt'));
  assert.deepEqual(run.requests[0].body, { workerId: 'synthetic worker/one', leaseToken: task.leaseToken, externalSessionId: 'api:synthetic-attempt' });
  assert.ok(run.requests[0].pathname.endsWith('/heartbeat'));
});
behavior('CP-fail', async () => {
  const run = await cpFixture(() => response({}), client => client.fail(task, 'api:synthetic-attempt', 'SYNTHETIC_FAILURE_CODE', 'TOOL_ERROR'));
  assert.deepEqual(run.requests[0].body, { workerId: 'synthetic worker/one', leaseToken: task.leaseToken, externalSessionId: 'api:synthetic-attempt', failureReason: 'SYNTHETIC_FAILURE_CODE', failureCategory: 'TOOL_ERROR' });
});
behavior('CP-complete', async () => {
  const workspace = { resultRevision: 'synthetic-revision', changedFiles: ['bounded.txt'], publicationRef: null }, report = { verdict: 'PASS', summary: 'Synthetic result.' };
  const run = await cpFixture(() => response({ taskId: task.taskId, newlyReadyTaskIds: [], generatedTaskIds: [] }), client => client.complete(task, 'api:synthetic-attempt', workspace, report));
  assert.deepEqual(run.requests[0].body, { workerId: 'synthetic worker/one', leaseToken: task.leaseToken, externalSessionId: 'api:synthetic-attempt', workspaceResult: workspace, report });
});
behavior('CP-error-no-retry', async () => {
  const run = await cpFixture(() => new Response('SYNTHETIC_UNTRUSTED_ERROR_BODY', { status: 409 }), client => outcome(client.complete(task, null, null, null)));
  assert.equal(run.result.rejected, true); assert.ok(run.result.error instanceof runtime.ProtocolError); assert.equal(run.result.error.status, 409);
  assert.ok(!String(run.result.error.message).includes('SYNTHETIC_UNTRUSTED_ERROR_BODY')); assert.equal(run.requests.length, 1);
});
behavior('CP-http-timeout', async () => {
  const run = await cpFixture(record => new Promise((_, reject) => record.signal.addEventListener('abort', () => reject(record.signal.reason), { once: true })), client => outcome(client.claim()));
  assert.equal(run.result.rejected, true); assert.equal(run.requests.length, 1); assert.equal(run.requests[0].signal.aborted, true);
});
behavior('CP-attempt-cancel', async () => {
  const controller = new AbortController(), started = deferred();
  const run = await cpFixture(record => { started.resolve(); return new Promise((_, reject) => record.signal.addEventListener('abort', () => reject(record.signal.reason), { once: true })); }, async client => {
    const pending = outcome(client.start(task, null, controller.signal)); await within(started.promise); controller.abort(new runtime.RuntimeFault('LEASE_LOST')); return within(pending);
  });
  assert.equal(run.result.rejected, true); assert.equal(run.requests.length, 1); assert.equal(run.requests[0].signal.aborted, true);
});
behavior('CP-worker-controls', async () => {
  const run = await cpFixture(() => new Response(null, { status: 204 }), async client => { await client.workerHeartbeat(); await client.drain(); await client.offline(); });
  assert.deepEqual(run.requests.map(item => item.pathname), ['heartbeat', 'drain', 'offline'].map(suffix => `/api/worker-protocol/v1/workers/synthetic%20worker%2Fone/${suffix}`));
  assert.ok(run.requests.every(item => item.body === null));
});

async function pluginFixture(id, mode = 'success', operation = 'skills-list', extra = {}) {
  const fixture = await sandbox(id);
  const pluginDirectory = path.join(fixture.directory, 'pinned-plugin'); await fs.mkdir(pluginDirectory);
  const pluginPath = path.join(pluginDirectory, 'fake-plugin.mjs'); await fs.copyFile(path.join(root, 'tests', 'fd005', 'r11', 'fake-plugin.mjs'), pluginPath);
  await fs.writeFile(path.join(pluginDirectory, 'plugin-state.json'), JSON.stringify({ syntheticTestOnly: true, mode }));
  const catalogPath = path.join(fixture.directory, 'catalog.json');
  const entry = { id: operation, executable: process.execPath, executableSha256: hash(await fs.readFile(process.execPath)), pluginPath,
    pluginSha256: hash(await fs.readFile(pluginPath)), outputSchema: operation === 'github-get-issue' ? 'github.issue.v1' : 'aiw.skills.v1', timeoutMs: 1000, maxOutputBytes: 1024,
    networkAllowed: false, ...(operation === 'github-get-issue' ? { repository: 'synthetic-owner/synthetic-repo' } : {}), ...extra };
  const catalog = { schema: 'aiw.worker.tool-catalog.v1', entries: [entry] };
  await fs.writeFile(catalogPath, JSON.stringify(catalog));
  return { ...fixture, pluginDirectory, pluginPath, catalogPath, catalog, entry, marker: path.join(pluginDirectory, 'plugin-metadata.jsonl') };
}
const query = async fixture => (await runtime.AiwAdapter.load(fixture.catalogPath, fixture.workspace)).query(fixture.entry.id, fixture.entry.id === 'github-get-issue' ? { number: 1 } : {}, new AbortController().signal);
behavior('PLUGIN-skills-list', async () => {
  const fixture = await pluginFixture('skills-list'); const result = await query(fixture);
  assert.equal(result.complete, true); assert.equal(result.data.schema, 'aiw.worker.query.v1'); assert.equal(result.data.operation, 'skills-list'); assert.equal(result.data.pluginDigest, fixture.entry.pluginSha256);
  assert.equal(result.data.result.skills[0].name, 'synthetic-skill');
  const marker = JSON.parse((await fs.readFile(fixture.marker, 'utf8')).trim()); assert.deepEqual(marker.args, ['list', '--json']); assert.equal(marker.cwd, fixture.workspace);
});
behavior('PLUGIN-skills-discover', async () => {
  const fixture = await pluginFixture('skills-discover', 'success', 'skills-discover'); const result = await query(fixture);
  assert.equal(result.complete, true); assert.equal(result.data.result.action, 'discover'); assert.equal(result.data.result.skills[0].status, 'installed');
});
behavior('PLUGIN-partial-issues', async () => {
  const fixture = await pluginFixture('partial-issues', 'partial', 'skills-discover'); const result = await query(fixture);
  assert.equal(result.complete, false); assert.equal(result.data.result.issues.length, 1);
});
behavior('PLUGIN-version', async () => {
  const fixture = await pluginFixture('plugin-identity'); const adapter = await runtime.AiwAdapter.load(fixture.catalogPath, fixture.workspace);
  await fs.appendFile(fixture.pluginPath, '\n// synthetic changed identity'); await assert.rejects(adapter.query('skills-list', {}, new AbortController().signal));
  await assert.rejects(fs.stat(fixture.marker), { code: 'ENOENT' });
  const mismatch = await pluginFixture('executable-identity', 'success', 'skills-list', { executableSha256: '0'.repeat(64) });
  await assert.rejects(runtime.AiwAdapter.load(mismatch.catalogPath, mismatch.workspace));
});
behavior('PLUGIN-timeout', async () => {
  const fixture = await pluginFixture('plugin-timeout', 'timeout', 'skills-list', { timeoutMs: 100 }); await assert.rejects(query(fixture));
});
behavior('PLUGIN-output', async () => {
  for (const mode of ['overflow', 'malformed', 'schema-invalid']) { const fixture = await pluginFixture(`plugin-${mode}`, mode); await assert.rejects(query(fixture)); }
});
behavior('PLUGIN-network', async () => {
  const fixture = await pluginFixture('plugin-network', 'success', 'github-get-issue'); await assert.rejects(query(fixture));
  await assert.rejects(fs.stat(fixture.marker), { code: 'ENOENT' });
});
behavior('PLUGIN-cancel', async () => {
  const fixture = await pluginFixture('plugin-cancel'); const adapter = await runtime.AiwAdapter.load(fixture.catalogPath, fixture.workspace);
  const controller = new AbortController(); controller.abort(new runtime.RuntimeFault('EXPLICIT_TERMINATION'));
  await assert.rejects(adapter.query('skills-list', {}, controller.signal)); await assert.rejects(fs.stat(fixture.marker), { code: 'ENOENT' });
});
behavior('PLUGIN-parameters', async () => {
  const fixture = await pluginFixture('plugin-parameters', 'success', 'github-get-issue'); const adapter = await runtime.AiwAdapter.load(fixture.catalogPath, fixture.workspace);
  for (const number of [0, -1, 1.5, '1']) await assert.rejects(adapter.query('github-get-issue', { number }, new AbortController().signal));
  await assert.rejects(fs.stat(fixture.marker), { code: 'ENOENT' });
});
behavior('PLUGIN-environment', async () => {
  const fixture = await pluginFixture('plugin-environment');
  const prior = Object.fromEntries(['AIW_WORKER_TOKEN','AIW_LEASE_TOKEN','OPENAI_API_KEY','OTEL_EXPORTER_OTLP_ENDPOINT'].map(key => [key, process.env[key]]));
  try {
    for (const key of Object.keys(prior)) process.env[key] = 'SYNTHETIC_SECRET_ONLY'; await query(fixture);
    const marker = JSON.parse((await fs.readFile(fixture.marker, 'utf8')).trim()); assert.equal(marker.leakedEnvironment, false);
  } finally { for (const [key, value] of Object.entries(prior)) { if (value === undefined) delete process.env[key]; else process.env[key] = value; } }
});

