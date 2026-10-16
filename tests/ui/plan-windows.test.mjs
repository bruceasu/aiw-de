import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';

const source = readFileSync(new URL('../../src/main/resources/static/ui/plan-windows.js', import.meta.url), 'utf8');
function page() {
  const windowEvents = new Map();
  class Node {
    constructor() { this.dataset = {}; this.style = { setProperty(name, value) { this[name] = value; } }; this.listeners = new Map(); this.attributes = {}; this.hidden = false; this.value = ''; }
    addEventListener(name, listener) { this.listeners.set(name, listener); }
    emit(name, values = {}) { const event = { target: this, currentTarget: this, preventDefault() {}, ...values }; return this.listeners.get(name)?.(event); }
    setAttribute(name, value) { this.attributes[name] = value; }
    focus() { this.focused = true; }
    closest(selector) { return selector.includes('button') && this.button || selector.includes('input') && this.input ? this : null; }
    setPointerCapture(id) { this.capture = id; }
    hasPointerCapture(id) { return this.capture === id; }
    releasePointerCapture() { this.capture = null; }
    getBoundingClientRect() { return { left: parseFloat(this.style.left) || 0, top: parseFloat(this.style.top) || 0,
      width: this.dataset.maximized === 'true' ? 1184 : 700, height: this.dataset.maximized === 'true' ? 784 : 600 }; }
  }
  const panel = new Node(), bar = new Node(), hide = new Node(), maximize = new Node(), opener = new Node(), input = new Node();
  panel.content = input; panel.hidden = true; panel.dataset.planWindow = 'mission'; opener.dataset.planWindowOpen = 'mission';
  hide.button = maximize.button = opener.button = true;
  panel.querySelector = selector => ({ '[data-plan-window-drag]': bar, '[data-plan-window-hide]': hide, '[data-plan-window-maximize]': maximize }[selector]);
  const root = new Node();
  root.querySelectorAll = selector => selector === '[data-plan-window]' ? [panel] : [opener];
  const viewport = { innerWidth: 1200, innerHeight: 800, addEventListener: (name, listener) => windowEvents.set(name, listener) };
  runInNewContext(source, { document: { querySelector: () => root }, window: viewport });
  return { root, panel, bar, hide, maximize, opener, input, windowEvents, viewport };
}

test('toolbar opens and hides the same window without resetting its contents', () => {
  const state = page(); state.input.value = 'Unsubmitted repair scope';
  state.opener.emit('click');
  assert.equal(state.panel.hidden, false);
  assert.equal(state.opener.attributes['aria-expanded'], 'true');
  state.hide.emit('click');
  assert.equal(state.panel.hidden, true);
  assert.equal(state.opener.focused, true);
  state.opener.emit('click');
  assert.equal(state.panel.content, state.input);
  assert.equal(state.input.value, 'Unsubmitted repair scope');
  state.panel.emit('keydown', { key: 'Escape' });
  assert.equal(state.panel.hidden, true);
});

test('dragging stays inside the viewport and pauses refresh only during the drag', () => {
  const state = page(); state.opener.emit('click');
  state.bar.emit('pointerdown', { button: 0, pointerId: 7, clientX: 300, clientY: 100 });
  assert.equal(state.root.dataset.windowDragging, 'true');
  state.bar.emit('pointermove', { pointerId: 7, clientX: 9999, clientY: 9999 });
  assert.equal(state.panel.style.left, '492px');
  assert.equal(state.panel.style.top, '192px');
  state.bar.emit('pointercancel');
  assert.equal(state.root.dataset.windowDragging, undefined);
  assert.equal(state.bar.capture, null);
  state.bar.emit('keydown', { key: 'ArrowLeft' });
  assert.equal(state.panel.style.left, '482px');
});

test('maximize and restore preserve position; custom open never hides an open window', () => {
  const state = page(); state.opener.emit('click');
  const position = state.panel.style.left;
  state.maximize.emit('click');
  assert.equal(state.panel.dataset.maximized, 'true');
  assert.equal(state.maximize.attributes['aria-pressed'], 'true');
  state.bar.emit('pointerdown', { button: 0, pointerId: 7, clientX: 300, clientY: 100 });
  assert.equal(state.root.dataset.windowDragging, undefined);
  state.maximize.emit('click');
  assert.equal(state.panel.style.left, position);
  assert.equal(state.panel.dataset.maximized, 'false');
  state.root.emit('aiw:open-plan-window', { detail: 'mission' });
  assert.equal(state.panel.hidden, false);
  state.viewport.innerWidth = 750; state.windowEvents.get('resize')();
  assert.ok(parseFloat(state.panel.style.left) <= 42);
});
