import { test } from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(fileURLToPath(new URL('../../../', import.meta.url)));
assert.equal(process.env.FD005_TESTER_SESSION, 'fd005-tester-20261005-r42-a9c4e2');
assert.ok(process.env.FD005_R42_SCRATCH);
console.log('FD005_TEST_STARTED');

function safeModule(output) {
  const match = output.match(/Cannot find (?:module|package) ['"]([^'"\r\n]+)['"]/i);
  if (!match) return { moduleCategory: null, moduleBasename: null };
  const specifier = match[1].replaceAll('\\', '/');
  const basename = specifier.split('/').pop();
  const moduleBasename = ['index.ts', 'api-runtime.ts', 'tools.ts', 'profiles.ts', 'package.json',
    'typescript', 'attempt.mjs', 'workspace.mjs'].includes(basename) ? basename : null;
  const moduleCategory = specifier.includes('/src/') ? 'api-source'
    : specifier.includes('/runtime-core/') ? 'shared-runtime-import'
    : specifier.startsWith('@') || (!specifier.includes('/') && !specifier.startsWith('.')) ? 'package'
    : 'other-module';
  return { moduleCategory, moduleBasename };
}

function classifyStartup(output, role, nodeErrorCode) {
  if (output.includes('FD005_NETWORK_DISABLED')) return 'network-attempt';
  if (new RegExp(`(?:reserved|unsupported|not executable|not supported)[^\\r\\n]{0,80}${role}|${role}[^\\r\\n]{0,80}(?:reserved|unsupported|not executable|not supported)`, 'i').test(output)) return 'reserved-role';
  if (/does not provide an export named|has no exported member|Named export .* not found/i.test(output)) return 'missing-export';
  if (/Cannot find (?:module|package)|ERR_MODULE_NOT_FOUND|MODULE_NOT_FOUND/i.test(output)) return 'missing-module';
  if (/SyntaxError|Unexpected token|ERR_UNKNOWN_FILE_EXTENSION/i.test(output)) return 'syntax-error';
  if (/(?:missing|required|invalid).*(?:AIW_|OPENAI_|config|configuration)/i.test(output)) return 'configuration-error';
  return nodeErrorCode ? 'other-node-error' : 'other-startup-error';
}

async function runRole(role) {
  const env = { ...process.env, AIW_PROCESS_ROLE: role, OPENAI_API_KEY: 'SYNTHETIC_KEY_ONLY',
    AIW_CONTROL_PLANE_URL: 'https://fd005.invalid', OPENAI_AGENTS_DONT_LOG_MODEL_DATA: '1',
    OPENAI_AGENTS_DONT_LOG_TOOL_DATA: '1', OPENAI_AGENTS_DISABLE_TRACING: '1', DEBUG: '' };
  delete env.AIW_WORKER_TOKEN;
  const child = spawn(process.execPath, [
    '--no-warnings', '--import=./tests/fd005/r42/register-loader.mjs',
    '--import=./tests/fd005/r42/entry-guard.mjs',
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
  const nodeErrorCode = output.match(/\b(?:ERR_[A-Z_]+|MODULE_NOT_FOUND)\b/)?.[0] ?? null;
  const { moduleCategory, moduleBasename } = safeModule(output);
  const category = classifyStartup(output, role, nodeErrorCode);
  return { role, exit, timedOut, category, nodeErrorCode, moduleCategory, moduleBasename };
}

test('API-16 both reserved roles fail before registration at API Worker entry', async () => {
  const results = [];
  for (const role of ['release-readiness', 'orchestrator']) results.push(await runRole(role));
  for (const result of results) console.log('FD005_ENTRY_META ' + JSON.stringify(result));
  console.log('FD005_ASSERTIONS_REACHED');
  for (const result of results) {
    assert.equal(result.timedOut, false, `${result.role} timed out`);
    assert.notEqual(result.exit, 0, `${result.role} exited successfully`);
    assert.equal(result.category, 'reserved-role', `${result.role} did not reach the reserved-role guard`);
  }
});
