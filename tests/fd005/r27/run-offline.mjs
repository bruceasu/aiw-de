import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = path.resolve(fileURLToPath(new URL('../../../', import.meta.url)));
const authorizationPath = 'docs/features/reports/FD-005-test-authorization-r27.json';
const command = `node tests/fd005/r27/run-offline.mjs --authorization ${authorizationPath}`;
const session = 'fd005-tester-20261005-bdc1bff5';
assert.equal(process.cwd(), root);
assert.deepEqual(process.argv.slice(2), ['--authorization', authorizationPath]);
const authorization = JSON.parse(await fs.readFile(path.join(root, authorizationPath), 'utf8'));
const receipt = JSON.parse(await fs.readFile(path.join(root, '.ai/fd/FD-005/events/000027-implementation-ready.json'), 'utf8'));
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
  assert.ok(file.startsWith('tests/fd005/r27/'));
  assert.equal(createHash('sha256').update(await fs.readFile(path.join(root, file))).digest('hex'), digest);
}
const evidence = path.join(root, 'tests/fd005/evidence-r27');
await fs.mkdir(evidence, { recursive: true });
const scratch = await fs.mkdtemp(path.join(os.tmpdir(), 'fd005-r27-'));
let output = '', code = null, timedOut = false;
try {
  const env = { ...process.env, FD005_TESTER_SESSION: session, FD005_R27_SCRATCH: scratch,
    OPENAI_AGENTS_DONT_LOG_MODEL_DATA: '1', OPENAI_AGENTS_DONT_LOG_TOOL_DATA: '1', OPENAI_AGENTS_DISABLE_TRACING: '1',
    DEBUG: '', GIT_CONFIG_NOSYSTEM: '1', GIT_TERMINAL_PROMPT: '0', GIT_ALLOW_PROTOCOL: 'file' };
  delete env.OPENAI_API_KEY;
  delete env.AIW_WORKER_TOKEN;
  const child = spawn(process.execPath, ['--import=./tests/fd005/r27/register-loader.mjs', '--test', '--test-concurrency=1',
    'tests/fd005/r27/worker.test.mjs'], { cwd: root, env, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.on('data', chunk => { output += chunk.toString('utf8'); });
  child.stderr.on('data', chunk => { output += chunk.toString('utf8'); });
  const timer = setTimeout(() => { timedOut = true; child.kill(); }, 45000);
  try { code = await new Promise((resolve, reject) => { child.once('error', reject); child.once('close', resolve); }); }
  finally { clearTimeout(timer); }
  await fs.writeFile(path.join(evidence, 'node-test-output.txt'), output, { flag: 'wx' });
  await fs.writeFile(path.join(evidence, 'execution.json'), JSON.stringify({ event: receipt.event_id, session,
    command, exit_code: code, timed_out: timedOut, raw_output: 'tests/fd005/evidence-r27/node-test-output.txt' }, null, 2), { flag: 'wx' });
} finally {
  const actual = await fs.realpath(scratch);
  assert.equal(path.dirname(actual), await fs.realpath(os.tmpdir()));
  await fs.rm(actual, { recursive: true, force: true });
}
process.stdout.write(output);
process.exitCode = timedOut ? 1 : code;
