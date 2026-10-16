(() => {
  const root = document.querySelector('[data-workflow-detail]');
  if (!root) return;
  const windows = [...root.querySelectorAll('[data-plan-window]')].map(panel => ({
    panel, bar: panel.querySelector('[data-plan-window-drag]'),
    openers: [...root.querySelectorAll('[data-plan-window-open]')].filter(button => button.dataset.planWindowOpen === panel.dataset.planWindow),
    maximized: false, positioned: false, opener: null, drag: null,
  }));
  const order = [...windows];
  const raise = state => {
    order.splice(order.indexOf(state), 1); order.push(state);
    order.forEach((item, index) => { item.panel.style.zIndex = String(40 + index); item.panel.dataset.active = String(item === state); });
  };
  const place = (state, left, top) => {
    const rect = state.panel.getBoundingClientRect();
    const inset = 8;
    state.panel.style.left = `${Math.max(inset, Math.min(left, Math.max(inset, window.innerWidth - rect.width - inset)))}px`;
    state.panel.style.top = `${Math.max(inset, Math.min(top, Math.max(inset, window.innerHeight - rect.height - inset)))}px`;
    state.positioned = true;
  };
  const stopDrag = state => {
    const drag = state.drag; state.drag = null;
    if (drag && state.bar.hasPointerCapture?.(drag.id)) state.bar.releasePointerCapture(drag.id);
    delete root.dataset.windowDragging;
  };
  const hide = state => {
    stopDrag(state); state.panel.hidden = true;
    state.openers.forEach(button => { button.setAttribute('aria-expanded', 'false'); });
    state.opener?.focus();
  };
  const show = (state, opener) => {
    state.opener = opener || state.openers.find(button => !button.closest('[hidden]')) || state.openers[0];
    state.panel.hidden = false;
    state.openers.forEach(button => { button.setAttribute('aria-expanded', 'true'); });
    if (!state.maximized) {
      const rect = state.panel.getBoundingClientRect();
      place(state, state.positioned ? rect.left : (window.innerWidth - rect.width) / 2,
        state.positioned ? rect.top : Math.max(24, (window.innerHeight - rect.height) / 2));
    }
    raise(state); state.panel.focus({ preventScroll: true });
  };
  for (const state of windows) {
    state.openers.forEach(button => button.addEventListener('click', () => {
      if (state.panel.hidden) show(state, button); else hide(state);
    }));
    state.panel.querySelector('[data-plan-window-hide]').addEventListener('click', () => hide(state));
    state.panel.querySelector('[data-plan-window-maximize]').addEventListener('click', event => {
      stopDrag(state); state.maximized = !state.maximized;
      state.panel.dataset.maximized = String(state.maximized);
      event.currentTarget.setAttribute('aria-pressed', String(state.maximized));
      event.currentTarget.textContent = state.maximized ? '❐' : '□';
      if (!state.maximized) {
        const rect = state.panel.getBoundingClientRect(); place(state, rect.left, rect.top);
      }
      raise(state);
    });
    state.panel.addEventListener('pointerdown', () => raise(state));
    state.panel.addEventListener('keydown', event => {
      if (event.key === 'Escape' && !event.defaultPrevented) { event.preventDefault(); hide(state); }
    });
    state.bar.addEventListener('pointerdown', event => {
      if (event.button !== 0 || state.maximized || root.dataset.windowDragging === 'true' || event.target.closest('button, input, label, output')) return;
      event.preventDefault(); state.bar.focus({ preventScroll: true });
      const rect = state.panel.getBoundingClientRect();
      state.drag = { id: event.pointerId, x: event.clientX, y: event.clientY, left: rect.left, top: rect.top };
      root.dataset.windowDragging = 'true';
      state.bar.setPointerCapture(event.pointerId);
    });
    state.bar.addEventListener('pointermove', event => {
      if (!state.drag || state.drag.id !== event.pointerId) return;
      place(state, state.drag.left + event.clientX - state.drag.x, state.drag.top + event.clientY - state.drag.y);
    });
    for (const name of ['pointerup', 'pointercancel', 'lostpointercapture']) state.bar.addEventListener(name, () => stopDrag(state));
    state.bar.addEventListener('keydown', event => {
      if (event.target !== state.bar || state.maximized) return;
      const delta = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[event.key];
      if (!delta) return;
      event.preventDefault(); const rect = state.panel.getBoundingClientRect(), step = event.shiftKey ? 30 : 10;
      place(state, rect.left + delta[0] * step, rect.top + delta[1] * step);
    });
  }
  root.addEventListener('aiw:open-plan-window', event => {
    const state = windows.find(item => item.panel.dataset.planWindow === event.detail);
    if (state) show(state);
  });
  window.addEventListener('resize', () => windows.forEach(state => {
    if (!state.panel.hidden && !state.maximized) { const rect = state.panel.getBoundingClientRect(); place(state, rect.left, rect.top); }
  }));
  window.addEventListener('pagehide', () => windows.forEach(stopDrag));
})();
