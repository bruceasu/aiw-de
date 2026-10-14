(() => {
  const formValue = form => JSON.stringify([...form.elements].filter(el =>
    el.name && !['hidden', 'submit', 'button'].includes(el.type)).map(el =>
    [el.name, el.value, el.checked, el.type === 'select-multiple' ? [...el.selectedOptions].map(option => option.value) : null]));
  const initialForms = new Map([...document.forms].map(form => [form, formValue(form)]));
  let dirty = false;
  const trackEdits = () => { dirty = [...initialForms].some(([form, value]) => formValue(form) !== value); };
  const editing = () => dirty || !!document.querySelector('dialog[open]') || document.activeElement?.matches('input, textarea, select, [contenteditable="true"]');
  document.addEventListener('input', event => {
    if (event.target.closest('form')) trackEdits();
    const id = event.target.dataset.tableFilter;
    if (!id) return;
    const needle = event.target.value.trim().toLowerCase();
    document.getElementById(id)?.querySelectorAll('tbody tr').forEach(row => {
      row.hidden = !!needle && !row.textContent.toLowerCase().includes(needle);
    });
  });
  document.addEventListener('change', event => { if (event.target.closest('form')) trackEdits(); });
  document.addEventListener('reset', () => setTimeout(trackEdits, 0));
  const confirmation = document.createElement('dialog');
  confirmation.className = 'workspace-confirm console-confirm';
  confirmation.setAttribute('aria-labelledby', 'console-confirm-title');
  confirmation.setAttribute('aria-describedby', 'console-confirm-message');
  confirmation.innerHTML = '<div class="workspace-confirm__top"><span>Confirm action</span></div>'
    + '<div class="workspace-confirm__body"><h2 id="console-confirm-title"></h2><p id="console-confirm-message"></p></div>'
    + '<div class="workspace-confirm__footer"><button class="btn ghost" type="button" data-confirm-cancel>Cancel</button>'
    + '<button class="btn primary" type="button" data-confirm-accept>Confirm</button></div>';
  document.body.appendChild(confirmation);
  let pendingConfirmation = null;
  let confirmationFocus = null;
  window.aiwConfirm = (message, { danger = false, title = 'Confirm action' } = {}) => {
    if (pendingConfirmation || typeof confirmation.showModal !== 'function') return Promise.resolve(false);
    confirmation.querySelector('#console-confirm-title').textContent = title;
    confirmation.querySelector('#console-confirm-message').textContent = message;
    confirmation.classList.toggle('console-confirm--danger', danger);
    confirmation.querySelector('[data-confirm-accept]').className = danger ? 'btn danger' : 'btn primary';
    confirmation.returnValue = '';
    confirmationFocus = document.activeElement;
    return new Promise(resolve => {
      pendingConfirmation = resolve;
      confirmation.showModal();
      confirmation.querySelector('[data-confirm-cancel]').focus();
    });
  };
  confirmation.querySelector('[data-confirm-cancel]').addEventListener('click', () => confirmation.close('cancel'));
  confirmation.querySelector('[data-confirm-accept]').addEventListener('click', () => confirmation.close('confirm'));
  confirmation.addEventListener('click', event => {
    if (event.target === confirmation) confirmation.close('cancel');
  });
  confirmation.addEventListener('close', () => {
    const resolve = pendingConfirmation;
    pendingConfirmation = null;
    if (confirmationFocus?.isConnected) confirmationFocus.focus();
    resolve?.(confirmation.returnValue === 'confirm');
  });
  const confirmedForms = new WeakSet();
  const confirmingForms = new WeakSet();
  document.addEventListener('submit', async event => {
    const button = event.submitter;
    if (event.defaultPrevented || !button?.hasAttribute('data-confirm')) return;
    const form = event.target;
    if (confirmedForms.delete(form)) return;
    event.preventDefault();
    if (confirmingForms.has(form)) return;
    confirmingForms.add(form);
    try {
      if (await window.aiwConfirm(button.dataset.confirm || 'Are you sure?', { danger: button.classList.contains('danger') })) {
        confirmedForms.add(form);
        try { form.requestSubmit(button); } finally { confirmedForms.delete(form); }
      }
    } finally { confirmingForms.delete(form); }
  });
  const deletionDialog = document.querySelector('[data-workspace-delete-dialog]');
  let pendingDeletion = null;
  if (deletionDialog) {
    document.addEventListener('click', event => {
      const summary = event.target.closest('summary');
      const choice = summary?.parentElement;
      if (!choice?.matches('details[data-delete-confirm]') ||
          typeof deletionDialog.showModal !== 'function') return;
      event.preventDefault();
      const repository = choice.dataset.deleteConfirm === 'repository';
      deletionDialog.querySelector('[data-delete-title]').textContent = repository
        ? 'Remove repository registration?' : 'Delete Workspace registration?';
      deletionDialog.querySelector('[data-delete-copy]').textContent = repository
        ? 'The repository registration will be removed. Its directory, local link, Git clone, and files will stay on disk.'
        : 'The Workspace registration will be removed. Files, directories, links, and clones on disk will stay.';
      deletionDialog.querySelector('[data-delete-target-display]').textContent = choice.dataset.deleteTarget || '';
      pendingDeletion = choice.querySelector('form');
      deletionDialog.showModal();
      deletionDialog.querySelector('[data-delete-cancel]').focus();
    });
    deletionDialog.querySelector('[data-delete-cancel]').addEventListener('click', () => deletionDialog.close());
    deletionDialog.addEventListener('close', () => { pendingDeletion = null; });
    deletionDialog.addEventListener('click', event => {
      if (event.target === deletionDialog) deletionDialog.close();
    });
    deletionDialog.querySelector('[data-delete-accept]').addEventListener('click', () => {
      if (!pendingDeletion) return;
      const form = pendingDeletion;
      pendingDeletion = null;
      deletionDialog.close();
      form.requestSubmit();
    });
  }
  document.addEventListener('submit', event => {
    if (event.defaultPrevented) return;
    const button = event.submitter || event.target.querySelector('button[type="submit"]');
    if (button) { button.disabled = true; button.textContent = 'Working…'; }
  });

  const motion = document.querySelector('[data-motion-toggle]');
  let motionOff = false;
  try { motionOff = localStorage.getItem('aiw-motion') === 'off'; } catch (_) { /* Storage can be disabled. */ }
  const applyMotion = () => {
    document.documentElement.dataset.motion = motionOff ? 'off' : 'on';
    if (motion) {
      motion.setAttribute('aria-pressed', String(motionOff));
      motion.textContent = motionOff ? 'Motion: off' : 'Motion: on';
    }
  };
  applyMotion();
  motion?.addEventListener('click', () => {
    motionOff = !motionOff;
    applyMotion();
    try { localStorage.setItem('aiw-motion', motionOff ? 'off' : 'on'); } catch (_) { /* Keep the session preference. */ }
  });

  const root = document.querySelector('[data-auto-refresh]');
  const seconds = Number(root?.dataset.autoRefresh);
  if (!root || !Number.isFinite(seconds) || seconds < 2) return;
  const partial = root.dataset.refreshMode === 'partial';
  const labels = [...document.querySelectorAll('[data-refresh-label]')];
  let next = Date.now() + seconds * 1000;
  let lastSuccess = Date.now();
  let inFlight = false;
  let stopped = false;
  let failure = '';
  let abort = null;
  const label = (message, warning = false) => labels.forEach(el => {
    if (el.textContent !== message) el.textContent = message;
    el.classList.toggle('dirty-refresh', warning);
  });
  const updateLabel = () => {
    const stale = Date.now() - lastSuccess >= 30000 ? ' · Stale: last update over 30s ago' : '';
    if (stopped) return label('Session or page changed · Reload to re-enter' + stale, true);
    if (document.hidden) return label('Refresh paused in background' + stale, !!stale);
    if (editing()) return label('Refresh paused while editing · Restore or reset inputs to resume' + stale, true);
    if (inFlight) return label('Updating snapshot…' + stale, !!stale);
    if (failure) return label(failure + ' · Retry in ' + Math.max(0, Math.ceil((next - Date.now()) / 1000)) + 's' + stale, true);
    label('Refresh in ' + Math.max(0, Math.ceil((next - Date.now()) / 1000)) + 's' + stale, !!stale);
  };
  const detailKey = detail => detail.dataset.detailKey || detail.id || detail.closest('li[data-detail-key]')?.dataset.detailKey || detail.querySelector('summary')?.textContent;
  const refresh = async () => {
    if (inFlight || stopped || document.hidden || editing()) { updateLabel(); return; }
    if (!partial) { window.location.reload(); return; }
    inFlight = true;
    abort = new AbortController();
    const timer = setTimeout(() => abort?.abort(), 10000);
    updateLabel();
    try {
      const response = await fetch(window.location.pathname + window.location.search, {
        credentials: 'same-origin', cache: 'no-store', signal: abort.signal, headers: { Accept: 'text/html' }
      });
      if (response.redirected || response.status === 401 || response.status === 403) { stopped = true; return; }
      if (!response.ok) throw new Error('Snapshot unavailable');
      if (!response.headers.get('content-type')?.includes('text/html')) { stopped = true; return; }
      const page = new DOMParser().parseFromString(await response.text(), 'text/html');
      const incoming = page.querySelector('[data-refresh-mode="partial"]');
      const regions = [...root.querySelectorAll('[data-refresh-region]')];
      if (!incoming || incoming.dataset.consoleRole !== root.dataset.consoleRole || regions.length === 0 ||
          regions.some(region => !page.getElementById(region.id)?.hasAttribute('data-refresh-region'))) {
        stopped = true; return;
      }
      // Visibility and edit state can change while the request is awaiting a response.
      if (document.hidden || editing()) return;
      const scroll = { x: window.scrollX, y: window.scrollY };
      let preserved = false;
      const selection = window.getSelection();
      const selectedRanges = selection && !selection.isCollapsed
        ? Array.from({ length: selection.rangeCount }, (_, index) => selection.getRangeAt(index))
        : [];
      regions.forEach(region => {
        if (region.contains(document.activeElement) || selectedRanges.some(range => range.intersectsNode(region))) {
          preserved = true; return;
        }
        const open = new Set([...region.querySelectorAll('details[open]')].map(detailKey));
        const replacement = page.getElementById(region.id);
        replacement.querySelectorAll('details').forEach(detail => { detail.open = open.has(detailKey(detail)); });
        region.replaceWith(replacement);
      });
      window.scrollTo(scroll.x, scroll.y);
      document.querySelectorAll('[data-snapshot-time]').forEach(time => {
        const fresh = page.querySelector('[data-snapshot-time]');
        if (fresh && !preserved) time.textContent = fresh.textContent;
      });
      // A focused/selected region remains an older snapshot until the next refresh.
      if (!preserved) lastSuccess = Date.now();
      failure = preserved ? 'Focused content preserved; some facts may be older' : '';
    } catch (error) {
      failure = error.name === 'AbortError' ? 'Update timed out or paused' : 'Update failed; previous snapshot retained';
    } finally {
      clearTimeout(timer);
      abort = null;
      inFlight = false;
      next = Date.now() + seconds * 1000;
      updateLabel();
    }
  };
  document.querySelector('[data-refresh-now]')?.addEventListener('click', refresh);
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) abort?.abort();
    else { next = Date.now(); refresh(); }
    updateLabel();
  });
  setInterval(() => {
    if (Date.now() >= next && !inFlight && !stopped && !document.hidden && !editing()) refresh();
    updateLabel();
  }, 1000);
})();
