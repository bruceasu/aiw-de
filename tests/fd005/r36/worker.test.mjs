import { test } from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(fileURLToPath(new URL('../../../', import.meta.url)));
assert.equal(process.env.FD005_TESTER_SESSION, 'fd005-tester-20261005-r36-a4d7c9');
assert.ok(process.env.FD005_R36_SCRATCH);

function safeModule(output) {
  const match = output.match(/Cannot find (?:module|package) ['"]([^'"\r\n]+)['"]/i);
  if (!match) return { moduleCategory: null, moduleBasename: null };
  const specifier = match[1].replaceAll('\\', '/');
  const basename = specifier.split('/').pop();
  const safe = /^[a-zA-Z0-9._@-]{1,80}$/.test(basename) ? basename : null;
  const moduleCategory = specifier.includes('/src/') ? 'api-source'
    : specifier.includes('/runtime-core/') ? 'shared-runtime-import'
    : specifier.startsWith('@') || (!specifier.includes('/') && !specifier.startsWith('.')) ? 'package'
    : 'other-module';
  return { moduleCategory, moduleBasename: safe };
}

async function runRole(role) {
  const env = { ...process.env, AIW_PROCESS_ROLE: role, OPENAI_API_KEY: 'SYNTHETIC_KEY_ONLY',
    AIW_CONTROL_PLANE_URL: 'https://fd005.invalid', OPENAI_AGENTS_DONT_LOG_MODEL_DATA: '1',
    OPENAI_AGENTS_DONT_LOG_TOOL_DATA: '1', OPENAI_AGENTS_DISABLE_TRACING: '1', DEBUG: '' };
  delete env.AIW_WORKER_TOKEN;
  const child = spawn(process.execPath, [
    '--no-warnings', '--import=./tests/fd005/r36/register-loader.mjs',
    '--import=./tests/fd005/r36/entry-guard.mjs',
    'workers/process-typescript-with-api/src/index.ts',
  ], { cwd: root, env, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  let output = '';
  for (const stream of [child.stdout, child.stderr]) stream.on('data', chunk => {
    output = (output + chunk.toString('utf8')).slice(0, 16000);
  });
  let timedOut = false;
  const timer = setTimeout(() => { timedOut = true; child.kill(); }, 5000);
  let exit;
  try { exit = await new Promise((resolve, reject) => { child.once('error', reject); child.once('close', resolve); }); }
  finally { clearTimeout(timer); }
  const code = output.match(/\b(?:ERR_[A-Z_]+|MODULE_NOT_FOUND)\b/)?.[0] ?? null;
  const { moduleCategory, moduleBasename } = safeModule(output);
  const category = output.includes('FD005_NETWORK_DISABLED') ? 'network-attempt'
    : new RegExp(`(?:reserved|unsupported|not executable|not supported)[^\\r\\n]{0,80}${role}|${role}[^\\r\\n]{0,80}(?:reserved|unsupported|not executable|not supported)`, 'i').test(output)
      ? 'reserved-role'
      : code || /SyntaxError|Cannot find (?:module|package)|does not provide an export/.test(output)
        ? 'module-or-syntax-load-error'
        : /(?:missing|required|invalid).*(?:AIW_|OPENAI_|config|configuration)/i.test(output)
          ? 'configuration-error' : 'other-startup-error';
  return { role, exit, timedOut, category, nodeErrorCode: code, moduleCategory, moduleBasename };
}

test('API-16 both reserved roles fail before registration at API Worker entry', async () => {
  const results = [];
  for (const role of ['release-readiness', 'orchestrator']) results.push(await runRole(role));
  for (const result of results) console.log('FD005_ENTRY_META ' + JSON.stringify(result));
  for (const result of results) {
    assert.equal(result.timedOut, false, `${result.role} timed out`);
    assert.notEqual(result.exit, 0, `${result.role} exited successfully`);
    assert.equal(result.category, 'reserved-role', `${result.role} did not reach the reserved-role guard`);
  }
});
