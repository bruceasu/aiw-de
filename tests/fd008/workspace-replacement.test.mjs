import assert from 'node:assert/strict';
import test from 'node:test';

// The operator supplies an isolated server and a READY task. The test never
// starts a service or creates repository data in an existing workspace.
const required = [
  'FD008_ISOLATED_BASE_URL',
  'FD008_RUN_ID',
  'FD008_READY_TASK_ID',
  'FD008_TARGET_REF',
];

function fixture() {
  const missing = required.filter(name => !process.env[name]);
  assert.deepEqual(missing, [], `Missing isolated fixture values: ${missing.join(', ')}`);
  assert.equal(process.env.FD008_ALLOW_MUTATION, '1', 'Explicit isolated mutation flag required');
  const base = new URL(process.env.FD008_ISOLATED_BASE_URL);
  assert.equal(base.protocol, 'http:');
  assert.ok(['127.0.0.1', 'localhost'].includes(base.hostname), 'Loopback service required');
  assert.equal(base.pathname, '/');
  return {
    base: base.origin,
    runId: process.env.FD008_RUN_ID,
    taskId: process.env.FD008_READY_TASK_ID,
    targetRef: process.env.FD008_TARGET_REF,
  };
}

function taskRoute({ runId, taskId }) {
  return `/ui/runs/${encodeURIComponent(runId)}/tasks/${encodeURIComponent(taskId)}/workspace-replacement`;
}

async function page(base, runId) {
  const response = await fetch(`${base}/ui/runs/${encodeURIComponent(runId)}`, {
    redirect: 'manual',
    signal: AbortSignal.timeout(5000),
  });
  assert.equal(response.status, 200, 'Isolated Run detail must be reachable without login');
  return response.text();
}

function formForTask(html, route) {
  const escaped = route.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = html.match(new RegExp(`<form[^>]*action=["']${escaped}["'][\\s\\S]*?<\\/form>`, 'i'));
  assert.ok(match, `Replacement form missing for ${route}`);
  return match[0];
}

test('READY task exposes a registered-target replacement form', async () => {
  const f = fixture();
  const html = await page(f.base, f.runId);
  const form = formForTask(html, taskRoute(f));
  for (const name of ['expectedVersion', 'repositoryRef', 'mode', 'revision', 'reason']) {
    assert.match(form, new RegExp(`name=["']${name}["']`), `${name} is required`);
  }
  assert.ok(form.includes(`value="${f.targetRef}"`) || form.includes(`value='${f.targetRef}'`),
    'Registered target must be selectable');
});

test('READY task is cancelled and replacement appears in Run history', async () => {
  const f = fixture();
  const route = taskRoute(f);
  const before = formForTask(await page(f.base, f.runId), route);
  const version = before.match(/name=["']expectedVersion["'][^>]*value=["']([^"']+)["']/i)?.[1];
  assert.ok(version, 'Expected task version must be present');
  const body = new URLSearchParams({
    expectedVersion: version,
    repositoryRef: f.targetRef,
    mode: 'DIRECT',
    revision: '',
    reason: 'FD-008 isolated black-box replacement check',
  });
  const response = await fetch(`${f.base}${route}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
    redirect: 'manual',
    signal: AbortSignal.timeout(5000),
  });
  assert.ok([302, 303].includes(response.status), `Expected success redirect, got ${response.status}`);
  const after = await page(f.base, f.runId);
  assert.match(after, /COMPLETED|已替换/i, 'Replacement history should show completion');
  assert.match(after, /CANCELLED/, 'Source task should be cancelled');
  assert.match(after, /FD-008 isolated black-box replacement check/);
});
