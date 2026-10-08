import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = path.resolve(fileURLToPath(new URL('../../../', import.meta.url)));
const authorizationPath = 'docs/features/reports/FD-005-test-authorization-r42.json';
const command = `node tests/fd005/r42/run-offline.mjs --authorization ${authorizationPath}`;
const session = 'fd005-tester-20261005-r42-a9c4e2';
assert.equal(process.cwd(), root);
assert.deepEqual(process.argv.slice(2), ['--authorization', authorizationPath]);
const authorization = JSON.parse(await fs.readFile(path.join(root, authorizationPath), 'utf8'));
const receipt = JSON.parse(await fs.readFile(path.join(root, '.ai/fd/FD-005/events/000039-implementation-ready.json'), 'utf8'));
assert.equal(authorization.kind, 'planner-authorization');
assert.equal(authorization.fd_id, 'FD-005');
assert.equal(authorization.source_event, receipt.event_id);
assert.equal(authorization.data.decision, 'approved');
assert.equal(authorization.data.implementation_event, receipt.event_id);
assert.equal(authorization.data.fd_revision, receipt.fd_revision);
assert.equal(authorization.data.fd_digest, receipt.fd_sha256);
assert.equal(authorization.data.tester_session, session);
assert.equal(authorization.data.command, command);
assert.equal(receipt.session_ref, session);
assert.equal(createHash('sha256').update(await fs.readFile(path.join(root, receipt.fd_path))).digest('hex'), receipt.fd_sha256);
for (const [file, digest] of Object.entries(authorization.data.invoked_files_sha256 || {})) {
  assert.ok(file.startsWith('tests/fd005/r42/'));
  assert.equal(createHash('sha256').update(await fs.readFile(path.join(root, file))).digest('hex'), digest);
}

function safeModule(output) {
  const match = output.match(/Cannot find (?:module|package) ['"]([^'"\r\n]+)['"]/i);
  if (!match) return { moduleCategory: null, moduleBasename: null };
  const specifier = match[1].replaceAll('\\', '/');
  const basename = specifier.split('/').pop();
  return {
    moduleCategory: specifier.includes('/src/') ? 'api-source'
      : specifier.includes('/runtime-core/') ? 'shared-runtime-import'
      : specifier.startsWith('@') || (!specifier.includes('/') && !specifier.startsWith('.')) ? 'package' : 'other-module',
    moduleBasename: ['index.ts', 'api-runtime.ts', 'tools.ts', 'profiles.ts', 'package.json',
      'typescript', 'attempt.mjs', 'workspace.mjs'].includes(basename) ? basename : null,
  };
}

function sanitizedDiagnostic(output, exit, timedOut) {
  const lines = output.split(/\r?\n/).map(line => line.replace(/^\s*#\s?/, '').trim());
  const marker = name => lines.some(line => line === name);
  const roles = [];
  for (const line of lines.filter(line => line.startsWith('FD005_ENTRY_META '))) {
    let value;
    try { value = JSON.parse(line.slice('FD005_ENTRY_META '.length)); } catch { continue; }
    if (!['release-readiness', 'orchestrator'].includes(value.role)) continue;
    roles.push({
      role: value.role,
      exit: Number.isInteger(value.exit) ? value.exit : null,
      timedOut: value.timedOut === true,
      category: ['network-attempt', 'reserved-role', 'missing-export', 'missing-module',
        'syntax-error', 'configuration-error', 'other-node-error', 'other-startup-error'].includes(value.category)
        ? value.category : 'invalid-category',
      nodeErrorCode: typeof value.nodeErrorCode === 'string' && /^(?:ERR_[A-Z_]+|MODULE_NOT_FOUND)$/.test(value.nodeErrorCode)
        ? value.nodeErrorCode : null,
      moduleCategory: ['api-source', 'shared-runtime-import', 'package', 'other-module'].includes(value.moduleCategory)
        ? value.moduleCategory : null,
      moduleBasename: ['index.ts', 'api-runtime.ts', 'tools.ts', 'profiles.ts', 'package.json',
        'typescript', 'attempt.mjs', 'workspace.mjs'].includes(value.moduleBasename)
        ? value.moduleBasename : null,
    });
  }
  const nodeErrorCode = output.match(/\b(?:ERR_[A-Z_]+|MODULE_NOT_FOUND)\b/)?.[0] ?? null;
  const { moduleCategory, moduleBasename } = safeModule(output);
  const reporter = {};
  for (const key of ['tests', 'pass', 'fail', 'cancelled', 'skipped']) {
    const match = output.match(new RegExp(`(?:^|\\n)# ${key} (\\d+)(?:\\r?\\n|$)`));
    reporter[key] = match ? Number(match[1]) : null;
  }
  return { testProcessExit: exit, timedOut, testStarted: marker('FD005_TEST_STARTED'),
    assertionsReached: marker('FD005_ASSERTIONS_REACHED'), roles,
    startup: { nodeErrorCode, moduleCategory, moduleBasename }, reporter };
}

const evidence = path.join(root, 'tests/fd005/evidence-r42');
await fs.mkdir(evidence, { recursive: true });
const scratch = await fs.mkdtemp(path.join(os.tmpdir(), 'fd005-r42-'));
let code = null, timedOut = false, diagnostic;
try {
  const env = { ...process.env, FD005_TESTER_SESSION: session, FD005_R42_SCRATCH: scratch,
    OPENAI_AGENTS_DONT_LOG_MODEL_DATA: '1', OPENAI_AGENTS_DONT_LOG_TOOL_DATA: '1', OPENAI_AGENTS_DISABLE_TRACING: '1',
    DEBUG: '', GIT_CONFIG_NOSYSTEM: '1', GIT_TERMINAL_PROMPT: '0', GIT_ALLOW_PROTOCOL: 'file' };
  delete env.OPENAI_API_KEY;
  delete env.AIW_WORKER_TOKEN;
  const child = spawn(process.execPath, ['--import=./tests/fd005/r42/register-loader.mjs', '--test',
    '--test-reporter=tap', '--test-concurrency=1', 'tests/fd005/r42/worker.test.mjs'],
  { cwd: root, env, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  let output = '';
  for (const stream of [child.stdout, child.stderr]) stream.on('data', chunk => {
    output = (output + chunk.toString('utf8')).slice(0, 32000);
  });
  const timer = setTimeout(() => { timedOut = true; child.kill(); }, 45000);
  try { code = await new Promise((resolve, reject) => { child.once('error', reject); child.once('close', resolve); }); }
  finally { clearTimeout(timer); }
  diagnostic = sanitizedDiagnostic(output, code, timedOut);
  await fs.writeFile(path.join(evidence, 'diagnostic.json'), JSON.stringify(diagnostic, null, 2), { flag: 'wx' });
  await fs.writeFile(path.join(evidence, 'execution.json'), JSON.stringify({ event: receipt.event_id, session,
    command, exit_code: code, timed_out: timedOut, diagnostic: 'tests/fd005/evidence-r42/diagnostic.json' }, null, 2), { flag: 'wx' });
} finally {
  const actual = await fs.realpath(scratch);
  assert.equal(path.dirname(actual), await fs.realpath(os.tmpdir()));
  await fs.rm(actual, { recursive: true, force: true });
}
process.stdout.write(JSON.stringify(diagnostic) + '\n');
process.exitCode = timedOut ? 1 : code;
