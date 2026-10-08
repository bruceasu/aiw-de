import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const repository = path.resolve(fileURLToPath(new URL('../../../', import.meta.url)));
const authorizationPath = 'docs/features/reports/FD-005-test-authorization-r19.json';
const exactCommand = `node tests/fd005/r19/run-offline.mjs --authorization ${authorizationPath}`;
const session = 'fd005-tester-20261005-58dba13b';
assert.equal(process.cwd(), repository);
assert.deepEqual(process.argv.slice(2), ['--authorization', authorizationPath]);
const authorization = JSON.parse(await fs.readFile(path.join(repository, authorizationPath), 'utf8'));
const receipt = JSON.parse(await fs.readFile(path.join(repository, '.ai/fd/FD-005/events/000019-implementation-ready.json'), 'utf8'));
assert.equal(authorization.kind, 'planner-authorization');
assert.equal(authorization.fd_id, 'FD-005');
assert.equal(authorization.source_event, receipt.event_id);
assert.equal(authorization.data.decision, 'approved');
assert.equal(authorization.data.implementation_event, receipt.event_id);
assert.equal(authorization.data.fd_revision, receipt.fd_revision);
assert.equal(authorization.data.fd_digest, receipt.fd_sha256);
assert.equal(authorization.data.tester_session, session);
assert.equal(authorization.data.command, exactCommand);
assert.equal(createHash('sha256').update(await fs.readFile(path.join(repository, receipt.fd_path))).digest('hex'), receipt.fd_sha256);
assert.equal(receipt.session_ref, session);
const evidence = path.join(repository, 'tests/fd005/evidence-r19');
await fs.mkdir(evidence, { recursive: true });
const scratch = await fs.mkdtemp(path.join(os.tmpdir(), 'fd005-r19-'));
let output = '', status;
try {
  const env = { ...process.env, FD005_TESTER_SESSION: session, FD005_R19_SCRATCH: scratch,
    OPENAI_AGENTS_DONT_LOG_MODEL_DATA: '1', OPENAI_AGENTS_DONT_LOG_TOOL_DATA: '1', OPENAI_AGENTS_DISABLE_TRACING: '1',
    GIT_CONFIG_NOSYSTEM: '1', GIT_TERMINAL_PROMPT: '0', GIT_ALLOW_PROTOCOL: 'file' };
  delete env.OPENAI_API_KEY;
  delete env.AIW_WORKER_TOKEN;
  const child = spawn(process.execPath, ['--import=./tests/fd005/r19/register-loader.mjs', '--test', '--test-concurrency=1',
    'tests/fd005/r19/worker.test.mjs'], { cwd: repository, env, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.on('data', value => { output += value.toString('utf8'); });
  child.stderr.on('data', value => { output += value.toString('utf8'); });
  const timer = setTimeout(() => child.kill(), 30000);
  try { status = await new Promise((resolve, reject) => { child.once('error', reject); child.once('close', code => resolve(code)); }); }
  finally { clearTimeout(timer); }
  await fs.writeFile(path.join(evidence, 'node-test-output.txt'), output, { flag: 'wx' });
  await fs.writeFile(path.join(evidence, 'execution.json'), JSON.stringify({ event: receipt.event_id, session,
    command: exactCommand, exit_code: status, test_file: 'tests/fd005/r19/worker.test.mjs',
    raw_output: 'tests/fd005/evidence-r19/node-test-output.txt' }, null, 2), { flag: 'wx' });
} finally {
  const actual = await fs.realpath(scratch);
  const allowed = await fs.realpath(os.tmpdir());
  assert.equal(path.dirname(actual), allowed);
  await fs.rm(actual, { recursive: true, force: true });
}
process.stdout.write(output);
process.exitCode = status;
