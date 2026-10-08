import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash, randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { binding, scenarios } from './cases.mjs';

const repository = path.resolve(fileURLToPath(new URL('../../../', import.meta.url)));
const testsDirectory = path.join(repository, 'tests', 'fd005', 'r11');
const authorizationPath = 'docs/features/reports/FD-005-test-authorization-r11.json';
const exactCommand = `node tests/fd005/r11/run-offline.mjs --authorization ${authorizationPath}`;
assert.equal(process.cwd(), repository, 'Run from the exact authorized repository root');
assert.deepEqual(process.argv.slice(2), ['--authorization', authorizationPath]);
const authorization = JSON.parse(await fs.readFile(path.join(repository, authorizationPath), 'utf8'));
assert.equal(authorization.kind, 'planner-authorization');
assert.equal(authorization.fd_id, binding.fdId);
assert.equal(authorization.source_event, binding.event);
assert.equal(authorization.data.decision, 'approved');
assert.equal(authorization.data.implementation_event, binding.event);
assert.equal(authorization.data.fd_revision, binding.revision);
assert.equal(authorization.data.fd_digest, binding.digest);
assert.equal(authorization.data.tester_session, binding.session);
assert.equal(authorization.data.command, exactCommand);
assert.ok(['repository root', repository, repository.replaceAll('\\', '/')].includes(authorization.data.working_directory));
const fdPath = path.join(repository, 'docs', 'features', 'FD-005_WORKER_PI.md');
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
assert.equal(hash(await fs.readFile(fdPath)), binding.digest, 'FD changed after authorization');
const receiptNumber = binding.event.match(/^FD-005-(\d{6})-implementation-ready$/)?.[1];
assert.ok(receiptNumber, 'New implementation-ready receipt has not been bound; execution forbidden');
const receipt = JSON.parse(await fs.readFile(path.join(repository, '.ai', 'fd', 'FD-005', 'events', `${receiptNumber}-implementation-ready.json`), 'utf8'));
assert.equal(receipt.fd_revision, binding.revision); assert.equal(receipt.fd_sha256, binding.digest);
assert.equal(receipt.target_role, 'tester'); assert.ok(JSON.stringify(receipt).includes(binding.session), 'Tester does not own this receipt');

const entryFiles = ['cases.mjs', 'harness.mjs', 'core.test.mjs', 'session-fixture.mjs', 'session.test.mjs', 'reporter.mjs',
  'fake-native.go', 'run-offline.mjs', 'register-loader.mjs', 'typescript-loader.mjs', 'api-fixture.mjs', 'api.test.mjs',
  'sdk.test.mjs', 'guidance.test.mjs', 'transport-plugin.test.mjs', 'fake-plugin.mjs', 'workspace.test.mjs', 'git-guard.go', 'diagnostics.mjs', 'workspace-fixture.mjs',
  'attempt-fixture.mjs', 'lifecycle.test.mjs', 'slot.test.mjs']
  .map(file => `tests/fd005/r11/${file}`).concat(['tests/fd005/cases.mjs', 'tests/fd005/r8/cases.mjs', 'workers/runtime-contract.examples.json']);
for (const name of ['@openai/agents', '@openai/agents-core', '@openai/agents-openai', 'openai', 'typescript']) entryFiles.push(`workers/process-typescript-with-api/node_modules/${name}/package.json`);
const hashes = Object.fromEntries(await Promise.all(entryFiles.map(async file => [file, hash(await fs.readFile(path.join(repository, file)))])));
assert.deepEqual(hashes, authorization.data.test_file_sha256, 'Test code changed after Planner review');
const runRoot = path.join(testsDirectory, `.tmp-${binding.session}-${randomUUID()}`);
const evidenceRoot = path.join(repository, 'tests', 'fd005', 'evidence-r11');
await fs.mkdir(evidenceRoot, { recursive: true });
// Refuse to overwrite previous executed evidence or reuse its authorization.
await fs.writeFile(path.join(evidenceRoot, 'run-owner.json'), JSON.stringify({ ...binding, exactCommand, syntheticTestOnly: true }), { flag: 'wx' });
await fs.mkdir(runRoot);
await fs.writeFile(path.join(runRoot, 'owner.json'), JSON.stringify({ session: binding.session, syntheticTestOnly: true }));
const resultsDirectory = path.join(evidenceRoot, 'results');
const coverageDirectory = path.join(evidenceRoot, 'v8-raw');
const programsDirectory = path.join(runRoot, 'programs');
for (const directory of [resultsDirectory, path.join(resultsDirectory, 'diagnostics'), coverageDirectory, programsDirectory, path.join(runRoot, 'temp'), path.join(runRoot, 'home'), path.join(runRoot, 'home', '.codex'), path.join(runRoot, 'home', 'xdg'), path.join(runRoot, 'empty-hooks'), path.join(runRoot, 'go-cache'), path.join(runRoot, 'go-mod-cache')]) await fs.mkdir(directory, { recursive: true });
const gitConfigPath = path.join(runRoot, 'home', 'offline-git-config');
await fs.writeFile(gitConfigPath, `[user]\n name = FD005 Synthetic Tester\n email = fd005-synthetic@invalid\n[credential]\n helper =\n[commit]\n gpgSign = false\n[tag]\n gpgSign = false\n[core]\n hooksPath = ${JSON.stringify(path.join(runRoot, 'empty-hooks').replaceAll('\\', '/'))}\n[protocol]\n allow = never\n[protocol "file"]\n allow = always\n`);
const environment = {};
for (const key of ['PATH', 'Path', 'SystemRoot', 'SYSTEMROOT', 'WINDIR', 'COMSPEC', 'PATHEXT']) if (process.env[key]) environment[key] = process.env[key];
Object.assign(environment, {
  HOME: path.join(runRoot, 'home'), USERPROFILE: path.join(runRoot, 'home'), CODEX_HOME: path.join(runRoot, 'home', '.codex'), XDG_CONFIG_HOME: path.join(runRoot, 'home', 'xdg'),
  TEMP: path.join(runRoot, 'temp'), TMP: path.join(runRoot, 'temp'), TMPDIR: path.join(runRoot, 'temp'),
  GOTOOLCHAIN: 'local', GOPROXY: 'off', GOSUMDB: 'off', GOENV: 'off', CGO_ENABLED: '0',
  GOCACHE: path.join(runRoot, 'go-cache'), GOMODCACHE: path.join(runRoot, 'go-mod-cache'),
  GIT_CONFIG_NOSYSTEM: '1', GIT_CONFIG_GLOBAL: gitConfigPath, GIT_TERMINAL_PROMPT: '0', GIT_ALLOW_PROTOCOL: 'file',
  OPENAI_AGENTS_DONT_LOG_MODEL_DATA: '1', OPENAI_AGENTS_DONT_LOG_TOOL_DATA: '1', OPENAI_AGENTS_DISABLE_TRACING: '1', DEBUG: '', OPENAI_LOG: 'off',
  FD005_RUN_ROOT: runRoot, FD005_RESULT_ROOT: resultsDirectory, FD005_TESTER_SESSION: binding.session,
});
const suffix = process.platform === 'win32' ? '.exe' : '';
environment.FD005_NATIVE = path.join(programsDirectory, `fake-native${suffix}`);
environment.FD005_LAUNCHER = path.join(programsDirectory, `launcher${suffix}`);
environment.FD005_GIT_GUARD = path.join(programsDirectory, `git${suffix}`);
// Resolve an already installed Git using filesystem metadata. Never invoke setup, installation, or a network resolver.
async function installedGit() {
  for (const directory of (environment.PATH || environment.Path || '').split(path.delimiter).filter(Boolean)) {
    const candidate = path.join(directory.replace(/^"|"$/g, ''), `git${suffix}`);
    if ((await fs.stat(candidate).catch(() => undefined))?.isFile()) return fs.realpath(candidate);
  }
  throw new Error('Installed local Git is unavailable; no install is authorized');
}
const invokedCommands = [];
const startedAt = new Date().toISOString();
let setupError, exitCode = 1, resourceUncertain = false;
async function command(executable, args, cwd, outputName, timeoutMs, extraEnv = {}) {
  const started = Date.now();
  const child = spawn(executable, args, { cwd, env: { ...environment, ...extraEnv }, windowsHide: true, detached: process.platform !== 'win32', stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '', stderr = '', bytes = 0, timedOut = false, childClosed = false, retainedUnknownTree = false;
  let resolveClosed;
  const closed = new Promise((resolve, reject) => {
    resolveClosed = resolve;
    child.once('error', reject);
    child.once('close', (code, signal) => { childClosed = true; resolve({ code, signal }); });
  });
  const retainUnknownTree = () => {
    if (retainedUnknownTree) return;
    retainedUnknownTree = true;
    resourceUncertain = true;
    // This is the already-owned main PID, not a second or broader tree kill.
    if (child.pid && child.exitCode === null && child.signalCode === null) { try { child.kill(); } catch {} }
    child.stdout.destroy(); child.stderr.destroy(); child.unref();
    resolveClosed({ code: null, signal: 'TERMINATION_UNCONFIRMED' });
  };
  let termination;
  const terminate = () => {
    if (termination) return termination;
    termination = (async () => {
      if (!child.pid || child.exitCode !== null || child.signalCode !== null) return;
      if (process.platform === 'win32') {
        const killer = spawn(path.join(environment.SystemRoot || environment.SYSTEMROOT, 'System32', 'taskkill.exe'), ['/PID', String(child.pid), '/T', '/F'], { env: environment, windowsHide: true, stdio: ['ignore', 'ignore', 'ignore'] });
        const status = await new Promise(resolve => {
          let done = false;
          const finish = code => { if (done) return; done = true; clearTimeout(helperTimer); resolve(code); };
          const helperTimer = setTimeout(() => {
            try { killer.kill(); } catch {}
            killer.unref(); finish(null);
          }, 5000);
          killer.once('error', () => finish(null)); killer.once('close', code => finish(code));
        });
        if (status !== 0) retainUnknownTree();
      } else {
        try { process.kill(-child.pid, 'SIGKILL'); } catch (error) { if (error.code !== 'ESRCH') retainUnknownTree(); }
      }
      if (!childClosed) {
        let timer;
        try { await Promise.race([closed.catch(() => {}), new Promise(resolve => { timer = setTimeout(resolve, 5000); })]); }
        finally { clearTimeout(timer); }
        if (!childClosed) retainUnknownTree();
      }
    })();
    return termination;
  };
  const collect = (stream, value) => {
    bytes += value.length;
    if (bytes > 8 * 1024 * 1024) { void terminate(); return; }
    if (stream === 'stdout') stdout += value.toString('utf8'); else stderr += value.toString('utf8');
  };
  child.stdout.on('data', value => collect('stdout', value)); child.stderr.on('data', value => collect('stderr', value));
  const timer = setTimeout(() => { timedOut = true; void terminate(); }, timeoutMs);
  let result;
  try {
    result = await closed;
  } finally { clearTimeout(timer); }
  if (termination) await termination;
  const record = { executable, args, working_directory: cwd, exit_code: result.code, signal: result.signal, timed_out: timedOut, duration_ms: Date.now() - started, raw_output: `tests/fd005/evidence-r11/${outputName}` };
  invokedCommands.push(record);
  await fs.writeFile(path.join(evidenceRoot, outputName), stdout + (stderr ? '\nSTDERR\n' + stderr : ''));
  if (result.code !== 0 || timedOut) throw Object.assign(new Error(`Focused child command failed: ${outputName}`), { execution: record });
  return stdout;
}
try {
  await command('go', ['build', '-buildvcs=false', '-o', environment.FD005_NATIVE, path.join(testsDirectory, 'fake-native.go')], testsDirectory, 'compile-fake-native.log', 75000);
  await command('go', ['build', '-buildvcs=false', '-o', environment.FD005_LAUNCHER, '.'], path.join(repository, 'workers', 'codex-session-launcher'), 'compile-launcher.log', 45000);
  const realGit = await installedGit();
  const linkFlags = `-X ${JSON.stringify(`main.realGit=${realGit.replaceAll('\\', '/')}`)} -X ${JSON.stringify(`main.ownedRoot=${runRoot.replaceAll('\\', '/')}`)} -X ${JSON.stringify(`main.diagnosticRoot=${path.join(resultsDirectory, 'diagnostics').replaceAll('\\', '/')}`)}`;
  await command('go', ['build', '-buildvcs=false', '-ldflags', linkFlags, '-o', environment.FD005_GIT_GUARD, path.join(testsDirectory, 'git-guard.go')], testsDirectory, 'compile-git-guard.log', 45000);
  const basePath = environment.PATH || environment.Path;
  delete environment.Path; environment.PATH = programsDirectory + path.delimiter + basePath;
  await command(process.execPath, ['--import=./tests/fd005/r11/register-loader.mjs', '--experimental-test-coverage', '--test', '--test-concurrency=1',
    '--test-reporter=./tests/fd005/r11/reporter.mjs', 'tests/fd005/r11/core.test.mjs', 'tests/fd005/r11/session.test.mjs', 'tests/fd005/r11/api.test.mjs',
    'tests/fd005/r11/sdk.test.mjs', 'tests/fd005/r11/guidance.test.mjs', 'tests/fd005/r11/transport-plugin.test.mjs', 'tests/fd005/r11/workspace.test.mjs', 'tests/fd005/r11/lifecycle.test.mjs', 'tests/fd005/r11/slot.test.mjs'],
    repository, 'node-test-events.jsonl', 180000, { NODE_V8_COVERAGE: coverageDirectory });
  exitCode = 0;
} catch (error) { setupError = error.message; }
finally {
  if ((await fs.stat(path.join(resultsDirectory, 'resource-uncertain.jsonl')).catch(() => undefined))?.isFile()) resourceUncertain = true;
  const results = [];
  for (const file of await fs.readdir(resultsDirectory)) if (file.endsWith('.test.mjs.jsonl')) {
    for (const line of (await fs.readFile(path.join(resultsDirectory, file), 'utf8')).split('\n').filter(Boolean)) results.push(JSON.parse(line));
  }
  const actualById = new Map(results.map(result => [result.id, result]));
  const inventory = scenarios.map(scenario => ({ ...scenario, execution: actualById.get(scenario.id) || { outcome: scenario.prepared ? 'prepared-unrun' : 'uncovered' } }));
  const execution = { ...binding, exact_command: exactCommand, authorization: authorizationPath, started_at: startedAt, finished_at: new Date().toISOString(), synthetic_test_only: true,
    test_file_sha256: hashes, invoked_commands: invokedCommands, exit_code: exitCode, setup_error: setupError, resource_uncertain: resourceUncertain,
    applicable_scenarios: inventory.length, prepared_scenarios: scenarios.filter(item => item.prepared).length,
    passed: results.filter(item => item.outcome === 'passed').length, failed: results.filter(item => item.outcome === 'failed').length, blocked: results.filter(item => item.outcome === 'blocked').length,
    scenarios: inventory };
  await fs.writeFile(path.join(evidenceRoot, 'execution.json'), JSON.stringify(execution, null, 2));
  // Windows recursive deletion: resolve and check the owned absolute target first.
  const absoluteTarget = await fs.realpath(runRoot);
  const absoluteAllowed = await fs.realpath(testsDirectory);
  const relative = path.relative(absoluteAllowed, absoluteTarget);
  const owner = JSON.parse(await fs.readFile(path.join(absoluteTarget, 'owner.json'), 'utf8'));
  assert.ok(relative.startsWith(`.tmp-${binding.session}-`) && !relative.includes(path.sep) && !path.isAbsolute(relative));
  assert.equal(owner.session, binding.session); assert.equal(owner.syntheticTestOnly, true);
  if (!resourceUncertain) await fs.rm(absoluteTarget, { recursive: true, force: false });
  else console.log('Owned temporary directory retained because process-tree termination is uncertain.');
  console.log(JSON.stringify({ exitCode, applicable: inventory.length, prepared: execution.prepared_scenarios, passed: execution.passed, failed: execution.failed, blocked: execution.blocked, evidence: 'tests/fd005/evidence-r11/execution.json' }));
}
process.exitCode = exitCode;
