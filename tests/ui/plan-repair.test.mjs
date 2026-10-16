import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { setImmediate } from 'node:timers/promises';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';

const source = readFileSync(new URL('../../src/main/resources/static/ui/workflow-editor.js', import.meta.url), 'utf8');

async function page({ confirm = async () => true, post = async () => ({ status: 'CONSUMED' }), allowed = true } = {}) {
  const nodes = new Map(), timers = new Map(), requests = [];
  let sequence = 0, confirmations = 0;
  let document;
  class Node {
    constructor(tag = 'div') { this.tag = tag; this.children = []; this.dataset = {}; this.listeners = new Map(); this.value = ''; this.hidden = false; this.textContent = ''; }
    appendChild(node) { this.children.push(node); return node; }
    append(...nodes) { this.children.push(...nodes); }
    replaceChildren(...nodes) { this.children = [...nodes]; }
    querySelectorAll() { return []; }
    querySelector(selector) {
      if (!nodes.has(selector)) nodes.set(selector, new Node());
      return nodes.get(selector);
    }
    addEventListener(name, handler) { this.listeners.set(name, handler); }
    setAttribute(name, value) { this[name] = value; }
    removeAttribute(name) { delete this[name]; }
    focus() { document.activeElement = this; }
    scrollIntoView() {}
    contains() { return false; }
    matches() { return false; }
    get options() { return this.children.filter(child => child.tag === 'option'); }
  }
  const detail = new Node(); detail.dataset = { id: 'FP-test', csrf: 'csrf', canOperate: 'true' };
  document = { hidden: false, activeElement: null, addEventListener() {},
    querySelector: selector => selector === '[data-workflow-detail]' ? detail : null,
    createElement: tag => new Node(tag), createTextNode: text => Object.assign(new Node('#text'), { textContent: text }) };
  const snapshot = {
    plan: { id: 'FP-test', state: 'CONFIRMED', version: 7, spec: { goal: 'Greeting', source: 'MANAGER',
      steps: [{ key: 'code', skillRef: 'executor', instruction: 'Implement', dependsOn: [] }] } },
    steps: [{ task: { id: { value: 'FP-test~code~review-3' }, state: 'COMPLETED', createdAt: '2026-10-16T00:00:00Z' }, verdict: 'REPAIR_REQUIRED' }],
    controls: [], repairAuthorizations: [], decisionWorkers: [],
    reviewRecovery: { allowed, blockedReason: allowed ? null : 'Plan changed; refresh required.', planVersion: 7, taskVersion: 5, taskId: 'FP-test~code~review-3' },
  };
  runInNewContext(source, {
    document, AbortController,
    window: { getSelection: () => ({ isCollapsed: true }), addEventListener() {}, aiwConfirm: async () => { confirmations++; return confirm(); } },
    setInterval: () => ++sequence, clearInterval() {},
    setTimeout: (callback, ms) => { const id = ++sequence; timers.set(id, { callback, ms }); return id; },
    clearTimeout: id => timers.delete(id),
    fetch: async (url, options) => {
      requests.push({ url, options });
      const body = options.method === 'POST' ? await post(url, options) : snapshot;
      return { ok: true, json: async () => body };
    },
  });
  await setImmediate();
  assert.equal(nodes.get('[data-detail-error]').textContent, '', 'the real detail renderer must finish before clicking');
  const node = selector => detail.querySelector(selector);
  const click = () => node('[data-authorize-repair]').listeners.get('click')();
  return { node, click, timers, requests, confirmations: () => confirmations };
}

test('repair validation appears next to the button and focuses the missing field', async () => {
  const state = await page();
  state.node('[data-repair-scope]').value = 'Fix the reviewer findings.';
  await state.click();
  assert.equal(state.node('[data-repair-status]').hidden, false);
  assert.equal(state.node('[data-repair-status]').textContent, 'Enter a repair decision reason.');
  assert.equal(state.node('[data-repair-reason]')['aria-invalid'], 'true');
  assert.equal(state.requests.filter(request => request.options.method === 'POST').length, 0);
  assert.equal(state.confirmations(), 0);
});

test('one confirmation submits the fixed scope once and displays success', async () => {
  let approve;
  const state = await page({ confirm: () => new Promise(resolve => { approve = resolve; }) });
  state.node('[data-repair-reason]').value = 'Address the independent review.';
  state.node('[data-repair-scope]').value = 'Fix the named defects only.';
  const pending = state.click();
  assert.equal(state.node('[data-authorize-repair]').disabled, true);
  await state.click();
  assert.equal(state.confirmations(), 1);
  approve(true); await pending;
  const writes = state.requests.filter(request => request.options.method === 'POST');
  assert.equal(writes.length, 1);
  assert.deepEqual(JSON.parse(writes[0].options.body), { planVersion: 7, taskId: 'FP-test~code~review-3', taskVersion: 5,
    reason: 'Address the independent review.', scope: 'Fix the named defects only.', delegateWorkerId: null, warningConfirmed: true });
  assert.equal(state.node('[data-action-status]').dataset.tone, 'success');
  assert.equal(state.node('[data-authorize-repair]').disabled, false);
});

test('cancel, stale state and request timeout produce feedback without an automatic resubmission', async () => {
  const cancelled = await page({ confirm: async () => false });
  cancelled.node('[data-repair-reason]').value = 'Fix findings'; cancelled.node('[data-repair-scope]').value = 'Named bugs';
  await cancelled.click();
  assert.match(cancelled.node('[data-repair-status]').textContent, /cancelled/);
  assert.equal(cancelled.requests.filter(request => request.options.method === 'POST').length, 0);
  const stale = await page({ allowed: false }); await stale.click();
  assert.match(stale.node('[data-action-status]').textContent, /Plan changed/);
  const timed = await page({ post: (_url, options) => new Promise((_resolve, reject) => options.signal.addEventListener('abort', () => reject(Object.assign(new Error(), { name: 'AbortError' })))) });
  timed.node('[data-repair-reason]').value = 'Fix findings'; timed.node('[data-repair-scope]').value = 'Named bugs';
  const pending = timed.click(); await setImmediate();
  [...timed.timers.values()].find(timer => timer.ms === 15000).callback(); await pending;
  assert.match(timed.node('[data-repair-status]').textContent, /check authorization history/);
  assert.equal(timed.requests.filter(request => request.options.method === 'POST').length, 1);
  assert.equal(timed.node('[data-repair-scope]').value, 'Named bugs');
  assert.equal(timed.node('[data-authorize-repair]').disabled, false);
});
