import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { setImmediate } from 'node:timers/promises';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';

const source = readFileSync(new URL('../../src/main/resources/static/ui/workflow-editor.js', import.meta.url), 'utf8');

function page() {
  const windowEvents = new Map();
  const intervals = new Map();
  const timeouts = new Map();
  const requests = [];
  const nodes = new Map();
  let sequence = 0;
  const detail = {
    dataset: { id: 'test-plan' }, contains: () => false,
    querySelector(selector) {
      if (!nodes.has(selector)) nodes.set(selector, { addEventListener() {}, textContent: '' });
      return nodes.get(selector);
    },
  };
  const document = {
    hidden: false, activeElement: null, addEventListener() {},
    querySelector: selector => selector === '[data-workflow-detail]' ? detail : null,
  };
  runInNewContext(source, {
    document, AbortController,
    window: { getSelection: () => ({ isCollapsed: true }), addEventListener: (event, handler) => windowEvents.set(event, handler) },
    setInterval: handler => { const id = ++sequence; intervals.set(id, handler); return id; },
    clearInterval: id => intervals.delete(id),
    setTimeout: handler => { const id = ++sequence; timeouts.set(id, handler); return id; },
    clearTimeout: id => timeouts.delete(id),
    fetch: (url, options) => {
      requests.push({ url, options });
      return new Promise((resolve, reject) => options.signal.addEventListener('abort', () =>
        reject(Object.assign(new Error('aborted'), { name: 'AbortError' })), { once: true }));
    },
  });
  return { windowEvents, intervals, timeouts, requests, nodes, document };
}

test('returning from task details restores exactly one polling timer', async () => {
  const state = page();
  assert.equal(state.intervals.size, 1);
  state.windowEvents.get('pagehide')();
  await setImmediate();
  assert.equal(state.intervals.size, 0);
  state.windowEvents.get('pageshow')({ persisted: true });
  assert.equal(state.intervals.size, 1);
  assert.equal(state.requests.length, 2);
  state.windowEvents.get('pageshow')({ persisted: true });
  assert.equal(state.intervals.size, 1);
  state.windowEvents.get('pagehide')();
  await setImmediate();
});

test('a stalled snapshot request times out and the next poll retries without caching', async () => {
  const state = page();
  [...state.timeouts.values()][0]();
  await setImmediate();
  assert.match(state.nodes.get('[data-detail-error]').textContent, /timed out/);
  [...state.intervals.values()][0]();
  assert.equal(state.requests.length, 2);
  assert.equal(state.requests[1].options.cache, 'no-store');
  state.document.hidden = true;
  [...state.intervals.values()][0]();
  assert.equal(state.requests.length, 2);
  state.windowEvents.get('pagehide')();
  await setImmediate();
});
