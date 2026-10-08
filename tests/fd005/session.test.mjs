import * as runtime from '../../workers/process-typescript/runtime-core/index.mjs';
import { assert, fs, path, behavior, outcome } from './harness.mjs';
import { syntheticPolicy, savePolicy, inEnvironment, launcherCommand, startLauncher, hash } from './session-fixture.mjs';

const admission = fixture => inEnvironment(fixture.environment, () => runtime.loadSessionPolicy({ runtime: 'codex-go', sdkVersion: 'cli', nativePath: fixture.native, roots: [fixture.workspace], manifestPath: fixture.policyPath }));
async function negativePolicy(id, modify) {
  const fixture = await syntheticPolicy(id);
  await admission(fixture); // Establish valid fake-only baseline before changing one binding.
  await modify(fixture);
  await assert.rejects(admission(fixture));
}
behavior('SESSION-missing', async () => {
  const fixture = await syntheticPolicy('missing');
  await fs.unlink(fixture.policyPath);
  await assert.rejects(admission(fixture));
});
behavior('SESSION-unverified', () => negativePolicy('unverified', async fixture => { fixture.proof.status = 'unverified'; await savePolicy(fixture); }));
behavior('SESSION-sdk-mismatch', () => negativePolicy('sdk-mismatch', async fixture => { fixture.policy.sdkVersion = 'unsupported-sdk'; await savePolicy(fixture); }));
behavior('SESSION-native-digest', () => negativePolicy('native-digest', async fixture => { fixture.policy.native.sha256 = '0'.repeat(64); await savePolicy(fixture); }));
behavior('SESSION-launcher-digest', () => negativePolicy('launcher-digest', async fixture => { fixture.policy.launcher.sha256 = '0'.repeat(64); await savePolicy(fixture); }));
behavior('SESSION-proof-digest', () => negativePolicy('proof-digest', async fixture => { await fs.appendFile(fixture.proofPath, ' '); }));
behavior('SESSION-control-missing', () => negativePolicy('control-missing', async fixture => { fixture.proof.controls = fixture.proof.controls.filter(item => item !== 'derived-sessions'); await savePolicy(fixture); }));
behavior('SESSION-config-change', () => negativePolicy('config-change', async fixture => { await fs.writeFile(path.join(fixture.codexHome, 'config.toml'), '# synthetic changed configuration'); }));
behavior('SESSION-workspace-file', () => negativePolicy('workspace-file', async fixture => {
  const original = fixture.policyPath; fixture.policyPath = path.join(fixture.workspace, 'policy.json');
  await fs.copyFile(original, fixture.policyPath); fixture.environment.AIW_CODEX_SESSION_POLICY = fixture.policyPath;
  fixture.environment.AIW_CODEX_SESSION_POLICY_SHA256 = hash(await fs.readFile(fixture.policyPath));
}));
behavior('SESSION-raw-evidence', () => negativePolicy('raw-evidence', async fixture => { await fs.appendFile(fixture.rawPath, ' '); }));
behavior('SESSION-fake-admission', async () => {
  const fixture = await syntheticPolicy('fake-admission');
  const accepted = await admission(fixture);
  assert.equal(accepted.nativePath, fixture.native); assert.equal(accepted.launcherPath, fixture.launcher);
  await inEnvironment(fixture.environment, () => accepted.recheck());
  assert.equal(fixture.policy.syntheticTestOnly, true); assert.equal(fixture.proof.syntheticTestOnly, true);
});
behavior('LAUNCHER-preflight', async () => {
  const fixture = await syntheticPolicy('preflight');
  const result = await launcherCommand(fixture, ['--preflight']);
  assert.equal(result.exitCode, 0);
  await assert.rejects(fs.stat(fixture.marker), { code: 'ENOENT' });
});
behavior('LAUNCHER-exec', async () => {
  const fixture = await syntheticPolicy('exec');
  const prompt = 'SYNTHETIC_STDIN_PROMPT_中文';
  const result = await launcherCommand(fixture, ['exec', '--json', '--model', 'synthetic-model', '--sandbox', 'read-only'], prompt);
  assert.equal(result.exitCode, 0);
  const native = JSON.parse(result.stdout.trim());
  assert.equal(native.syntheticTestOnly, true); assert.ok(native.args.includes('--ephemeral'));
  assert.ok(native.args.includes('synthetic-model') && native.args.includes('read-only'));
  assert.equal(native.stdinSha256, hash(prompt)); assert.equal(native.stdinBytes, Buffer.byteLength(prompt));
  assert.ok(!native.args.some(argument => argument.includes(prompt)));
});
async function rejectedArgs(id, args) {
  const fixture = await syntheticPolicy(id);
  assert.equal((await launcherCommand(fixture, ['--preflight'])).exitCode, 0);
  const result = await launcherCommand(fixture, args, 'SYNTHETIC_PROMPT_MUST_NOT_SEND');
  assert.notEqual(result.exitCode, 0);
  await assert.rejects(fs.stat(fixture.marker), { code: 'ENOENT' });
}
behavior('LAUNCHER-resume', () => rejectedArgs('resume', ['exec', 'resume', '--last']));
behavior('LAUNCHER-save-output', () => rejectedArgs('save-output', ['exec', '--output-last-message', 'forbidden.txt']));
behavior('LAUNCHER-prompt-argv', () => rejectedArgs('prompt-argv', ['exec', 'SYNTHETIC_BODY_ARGV']));
behavior('LAUNCHER-conflict', () => rejectedArgs('privacy-conflict', ['exec', '--config', 'history.persistence="save-all"']));
behavior('LAUNCHER-environment', async () => {
  const fixture = await syntheticPolicy('environment');
  const result = await launcherCommand(fixture, ['exec', '--json'], 'synthetic', { AIW_WORKER_TOKEN: 'SYNTHETIC_CP_TOKEN', OPENAI_API_KEY: 'SYNTHETIC_MODEL_TOKEN', DEBUG: 'synthetic-debug', OTEL_EXPORTER_OTLP_ENDPOINT: 'synthetic-exporter' });
  assert.equal(result.exitCode, 0); const native = JSON.parse(result.stdout.trim());
  assert.equal(native.leakedEnvironment, false); assert.equal(native.rustLog, 'off'); assert.equal(native.modelAuthAvailable, true);
});
behavior('LAUNCHER-stderr', async () => {
  const fixture = await syntheticPolicy('stderr');
  const result = await launcherCommand(fixture, ['exec', '--json'], 'synthetic');
  assert.equal(result.exitCode, 0);
  assert.ok(!result.stderr.includes('SYNTHETIC_STDERR_BODY_NEVER_LOG'));
  assert.ok(!result.stdout.includes('SYNTHETIC_STDERR_BODY_NEVER_LOG'));
});

async function initialize(fixture, extra = {}) {
  const server = startLauncher(fixture, ['app-server'], extra);
  server.send({ id: 1, method: 'initialize', params: { clientInfo: { name: 'fd005-synthetic-only', version: '1' } } });
  await server.reply(value => value.id === 1);
  server.send({ method: 'initialized', params: {} });
  return server;
}
behavior('RPC-ephemeral', async () => {
  const fixture = await syntheticPolicy('rpc-ephemeral');
  const server = await initialize(fixture);
  server.send({ id: 2, method: 'thread/start', params: { cwd: fixture.workspace, ephemeral: false } });
  const result = await server.stop();
  assert.notEqual(result.exitCode, 0); assert.ok(!result.replies.some(value => value.id === 2 && value.result));
});
behavior('RPC-valid-turn', async () => {
  const fixture = await syntheticPolicy('rpc-valid-turn');
  const server = await initialize(fixture);
  try {
    server.send({ id: 2, method: 'thread/start', params: { cwd: fixture.workspace, ephemeral: true } });
    const start = await server.reply(value => value.id === 2);
    assert.equal(start.result.thread.ephemeral, true); assert.equal(start.result.thread.path, null);
    server.send({ id: 3, method: 'thread/read', params: { threadId: 'synthetic-thread', includeTurns: false } });
    const read = await server.reply(value => value.id === 3);
    assert.equal(read.result.thread.ephemeral, true); assert.equal(read.result.thread.path, null);
    server.send({ id: 4, method: 'turn/start', params: { threadId: 'synthetic-thread', input: [{ type: 'text', text: 'SYNTHETIC_MEMORY_ONLY_PROMPT' }] } });
    assert.equal((await server.reply(value => value.id === 4)).result.turn.status, 'completed');
  } finally { await server.stop(); }
});
behavior('RPC-receipt', async () => {
  const fixture = await syntheticPolicy('rpc-receipt');
  const server = await initialize(fixture, { FD005_RPC_BAD_RECEIPT: 'true' });
  server.send({ id: 2, method: 'thread/start', params: { cwd: fixture.workspace, ephemeral: true } });
  await outcome(server.reply(value => value.id === 2));
  const result = await server.stop();
  assert.notEqual(result.exitCode, 0); assert.ok(!result.replies.some(value => value.id === 2 && value.result?.thread?.ephemeral === false));
});
behavior('RPC-read-receipt', async () => {
  const fixture = await syntheticPolicy('rpc-read-receipt');
  const server = await initialize(fixture, { FD005_RPC_BAD_READ: 'true' });
  server.send({ id: 2, method: 'thread/start', params: { cwd: fixture.workspace, ephemeral: true } });
  await server.reply(value => value.id === 2);
  server.send({ id: 3, method: 'thread/read', params: { threadId: 'synthetic-thread', includeTurns: false } });
  await outcome(server.reply(value => value.id === 3));
  const result = await server.stop();
  assert.notEqual(result.exitCode, 0); assert.ok(!result.replies.some(value => value.id === 3 && value.result?.thread?.path));
});
behavior('RPC-turn-order', async () => {
  const fixture = await syntheticPolicy('rpc-turn-order');
  const server = await initialize(fixture);
  server.send({ id: 3, method: 'turn/start', params: { threadId: 'unverified-thread', input: [{ type: 'text', text: 'SYNTHETIC_PROMPT_MUST_NOT_SEND' }] } });
  const result = await server.stop(); assert.notEqual(result.exitCode, 0);
  const metadata = await fs.readFile(fixture.marker, 'utf8'); assert.ok(!metadata.includes('turn/start'));
});
behavior('RPC-resume', async () => {
  const fixture = await syntheticPolicy('rpc-resume');
  const server = await initialize(fixture);
  server.send({ id: 3, method: 'thread/resume', params: { threadId: 'old-disk-session' } });
  const result = await server.stop(); assert.notEqual(result.exitCode, 0);
  const metadata = await fs.readFile(fixture.marker, 'utf8'); assert.ok(!metadata.includes('thread/resume'));
});
behavior('RPC-disk-fork', async () => {
  const fixture = await syntheticPolicy('rpc-disk-fork');
  const server = await initialize(fixture);
  server.send({ id: 3, method: 'thread/fork', params: { threadId: 'old-disk-session', ephemeral: false } });
  const result = await server.stop(); assert.notEqual(result.exitCode, 0);
  const metadata = await fs.readFile(fixture.marker, 'utf8'); assert.ok(!metadata.includes('thread/fork'));
});
behavior('RPC-approval', async () => {
  const fixture = await syntheticPolicy('rpc-approval');
  const server = await initialize(fixture, { FD005_RPC_EMIT_APPROVAL: 'true' });
  server.send({ id: 2, method: 'thread/start', params: { cwd: fixture.workspace, ephemeral: true } });
  await server.reply(value => value.id === 2);
  await server.reply(value => value.id === 9001 && value.method);
  server.send({ id: 9001, result: { decision: 'accept' } });
  const result = await server.stop(); assert.notEqual(result.exitCode, 0);
  const metadata = await fs.readFile(fixture.marker, 'utf8'); assert.ok(!metadata.includes('clientReplyForwarded'));
});
