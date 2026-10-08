import { createHash } from 'node:crypto';
import { spawn } from 'node:child_process';
import { assert, fs, path, sandbox, blocked, within, runRoot, resultRoot } from './harness.mjs';

export const hash = value => createHash('sha256').update(value).digest('hex');
const privacyOverrides = {
  'history.persistence': 'none', 'otel.log_user_prompt': false, 'otel.exporter': 'none',
  'otel.trace_exporter': 'none', 'otel.metrics_exporter': 'none', 'analytics.enabled': false,
  'feedback.enabled': false, 'memories.generate_memories': false, 'memories.use_memories': false,
};
const controls = ['rollout', 'history', 'diagnostics', 'trace', 'memory', 'state-cache', 'derived-sessions', 'hooks-plugins', 'credentials', 'termination'];
export async function syntheticPolicy(id) {
  const fixture = await sandbox(id);
  const home = path.join(fixture.directory, 'synthetic-home');
  const codexHome = path.join(home, '.codex');
  await fs.mkdir(codexHome, { recursive: true });
  // Do not read/hash existing real ancestor configuration or credentials.
  for (let current = path.dirname(fixture.workspace); ; current = path.dirname(current)) {
    const possibleConfig = path.join(current, '.codex', 'config.toml');
    const exists = await fs.lstat(possibleConfig).then(() => true, error => { if (error.code === 'ENOENT') return false; throw error; });
    if (exists && !current.startsWith(runRoot + path.sep)) blocked('真实工程祖先 .codex/config.toml 存在；合成 fixture 不读取或认证该文件。');
    if (path.dirname(current) === current) break;
  }
  const native = process.env.FD005_NATIVE;
  const launcher = process.env.FD005_LAUNCHER;
  assert.ok(native && launcher, 'Temporary synthetic native and launcher must be prepared by the authorized entrypoint');
  const configurationSources = [
    { path: path.join(codexHome, 'config.toml'), sha256: 'absent' },
    { path: path.join(fixture.workspace, '.codex', 'config.toml'), sha256: 'absent' },
  ];
  const rawPath = path.join(fixture.directory, 'synthetic-test-only-raw.json');
  await fs.writeFile(rawPath, JSON.stringify({ syntheticTestOnly: true, target: 'fake-native.go', note: 'Not Codex deployment evidence. No actual channel has been certified.' }));
  const proof = {
    schema: 'aiw.worker.session-validation.v1', status: 'verified', syntheticTestOnly: true,
    scope: 'all-worker-sessions', platform: process.platform, runtime: 'codex-go', sdkVersion: 'cli',
    nativeVersion: 'fd005-synthetic-test-only', nativeSha256: hash(await fs.readFile(native)), launcherSha256: hash(await fs.readFile(launcher)),
    configurationSha256: hash(JSON.stringify(privacyOverrides)),
    configurationSourcesSha256: hash(JSON.stringify(configurationSources.map(item => [item.path, item.sha256]))),
    controls: [...controls], rawEvidence: [{ path: rawPath, sha256: hash(await fs.readFile(rawPath)) }],
  };
  const proofPath = path.join(fixture.directory, 'synthetic-test-only-proof.json');
  const policyPath = path.join(fixture.directory, 'synthetic-test-only-policy.json');
  const policy = {
    schema: 'aiw.worker.session-policy.v1', mode: 'metadata-only', scope: 'all-worker-sessions', syntheticTestOnly: true,
    platform: process.platform, runtime: 'codex-go', sdkVersion: 'cli',
    native: { path: native, sha256: proof.nativeSha256, version: proof.nativeVersion },
    launcher: { path: launcher, sha256: proof.launcherSha256 },
    configurationSources, evidence: { path: proofPath, sha256: '' },
  };
  const marker = path.join(fixture.directory, 'native-metadata-only.jsonl');
  const environment = {
    ...process.env, HOME: home, USERPROFILE: home, CODEX_HOME: codexHome,
    AIW_CODEX_SESSION_POLICY: policyPath, AIW_CODEX_SESSION_ROOTS: JSON.stringify([fixture.workspace]),
    FD005_NATIVE_MARKER: marker,
  };
  const result = { ...fixture, home, codexHome, policy, proof, policyPath, proofPath, rawPath, marker, environment, native, launcher };
  await savePolicy(result);
  return result;
}
export async function savePolicy(fixture) {
  await fs.writeFile(fixture.proofPath, JSON.stringify(fixture.proof));
  fixture.policy.evidence.sha256 = hash(await fs.readFile(fixture.proofPath));
  await fs.writeFile(fixture.policyPath, JSON.stringify(fixture.policy));
  fixture.environment.AIW_CODEX_SESSION_POLICY = fixture.policyPath;
  fixture.environment.AIW_CODEX_SESSION_POLICY_SHA256 = hash(await fs.readFile(fixture.policyPath));
}
export async function inEnvironment(environment, operation) {
  const originals = new Map(Object.keys(environment).map(key => [key, process.env[key]]));
  try { Object.assign(process.env, environment); return await operation(); }
  finally { for (const [key, value] of originals) { if (value === undefined) delete process.env[key]; else process.env[key] = value; } }
}
export function startLauncher(fixture, args, extra = {}) {
  const child = spawn(fixture.launcher, args, { cwd: fixture.workspace, env: { ...fixture.environment, ...extra }, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  let stdout = '', stderr = '', bytes = 0;
  const replies = [], waiting = [];
  let lineBuffer = '';
  const add = chunk => {
    stdout += chunk; lineBuffer += chunk; bytes += Buffer.byteLength(chunk);
    if (bytes > 1048576) child.kill();
    for (;;) {
      const end = lineBuffer.indexOf('\n'); if (end < 0) break;
      const line = lineBuffer.slice(0, end); lineBuffer = lineBuffer.slice(end + 1);
      let value; try { value = JSON.parse(line); } catch { continue; }
      replies.push(value);
      for (let i = waiting.length - 1; i >= 0; i--) if (waiting[i].predicate(value)) { waiting[i].resolve(value); waiting.splice(i, 1); }
    }
  };
  child.stdout.on('data', chunk => add(chunk.toString('utf8')));
  child.stderr.on('data', chunk => { stderr += chunk.toString('utf8'); if (stderr.length > 65536) child.kill(); });
  child.stdin.on('error', () => {});
  const closed = new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('close', (exitCode, signal) => resolve({ exitCode, signal, stdout, stderr, replies }));
  });
  const timer = setTimeout(() => child.kill(), 3000);
  closed.finally(() => clearTimeout(timer)).catch(() => {});
  return {
    child, closed,
    send: value => child.stdin.write(JSON.stringify(value) + '\n'),
    reply: predicate => {
      const existing = replies.find(predicate); if (existing) return Promise.resolve(existing);
      return within(new Promise(resolve => waiting.push({ predicate, resolve })), 1500);
    },
    stop: async () => { child.stdin.end(); const result = await within(closed, 4000); await fs.appendFile(path.join(resultRoot, 'launcher-transcripts.jsonl'), JSON.stringify({ syntheticTestOnly: true, args, exitCode: result.exitCode, signal: result.signal, stdout: result.stdout, stderr: result.stderr }) + '\n'); return result; },
  };
}
export async function launcherCommand(fixture, args, input = '', extra = {}) {
  const process = startLauncher(fixture, args, extra);
  process.child.stdin.end(input);
  return process.stop();
}


