import assert from 'node:assert/strict';
import { test } from 'node:test';
import fs from 'node:fs/promises';
import { appendFileSync } from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { binding, byId } from './cases.mjs';

export { assert, fs, path };
export const root = path.resolve(fileURLToPath(new URL('../../', import.meta.url)));
export const runRoot = process.env.FD005_RUN_ROOT;
export const resultRoot = process.env.FD005_RESULT_ROOT;
if (!runRoot || !resultRoot || process.env.FD005_TESTER_SESSION !== binding.session) {
  throw new Error('Run only through the Planner-authorized FD-005 entrypoint.');
}
// No test may open a real Control Plane or model endpoint.
globalThis.fetch = async () => { throw new Error('FD005_NETWORK_DISABLED'); };

class Unavailable extends Error {}
export const blocked = reason => { throw new Unavailable(reason); };
export const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
export const deferred = () => {
  let resolve, reject;
  const promise = new Promise((a, b) => { resolve = a; reject = b; });
  return { promise, resolve, reject };
};
export async function within(promise, ms = 2000) {
  let timer;
  try {
    return await Promise.race([promise, new Promise((_, reject) => {
      timer = setTimeout(() => reject(new Error('Synthetic test deadline exceeded')), ms);
    })]);
  } finally { clearTimeout(timer); }
}
export async function sandbox(id) {
  const directory = path.join(runRoot, 'cases', `${id}-${randomUUID()}`);
  await fs.mkdir(directory, { recursive: true });
  const workspace = path.join(directory, 'workspace');
  await fs.mkdir(workspace);
  return { directory, workspace };
}
export async function fileTree(directory) {
  const files = [];
  for (const item of await fs.readdir(directory, { withFileTypes: true })) {
    const name = path.join(directory, item.name);
    if (item.isDirectory()) files.push(...await fileTree(name));
    else if (item.isFile()) files.push(name);
  }
  return files;
}
export async function directoryText(directory) {
  return (await Promise.all((await fileTree(directory)).map(file => fs.readFile(file, 'utf8')))).join('\n');
}
export async function outcome(promise) {
  try { return { rejected: false, value: await promise }; }
  catch (error) { return { rejected: true, error }; }
}
export function assertDenied(result) {
  if (result.rejected) return;
  const value = JSON.parse(result.value);
  assert.ok(value.status && !['success', 'ok', 'completed'].includes(value.status), 'Tool must report a denied/error outcome');
}
export function behavior(id, operation) {
  const scenario = byId.get(id);
  assert.ok(scenario?.prepared, `Undeclared or unprepared scenario ${id}`);
  test(`${id} ${scenario.behavior}`, { timeout: 10000 }, async context => {
    const started = Date.now();
    let record;
    try {
      await operation(context);
      record = { id, outcome: 'passed', duration_ms: Date.now() - started };
    } catch (error) {
      record = { id, outcome: error instanceof Unavailable ? 'blocked' : 'failed', duration_ms: Date.now() - started,
        error: String(error.message).slice(0, 2000) };
      if (error instanceof Unavailable) context.skip(error.message);
      else throw error;
    } finally {
      if (record) appendFileSync(path.join(resultRoot, `${path.basename(process.argv[1])}.jsonl`), JSON.stringify(record) + '\n', { encoding: 'utf8' });
    }
  });
}
