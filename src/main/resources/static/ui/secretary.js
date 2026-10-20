(() => {
  'use strict';
  const el = id => document.getElementById(`secretary-${id}`);
  let current = null;
  let pendingMessage = null;
  let busy = false;
  async function api(path = '', data) {
    const token = document.querySelector('meta[name="csrf-token"]').content;
    const response = await fetch(`/api/r1/secretary${path}${data === undefined ? '' : `?_csrf=${encodeURIComponent(token)}`}`, {
      method: data === undefined ? 'GET' : 'POST', credentials: 'same-origin',
      headers: data === undefined ? {} : {'Content-Type': 'application/json'},
      body: data === undefined ? undefined : JSON.stringify(data)
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || `Request failed (${response.status})`);
    return result;
  }
  function node(tag, text, className) {
    const n = document.createElement(tag); n.textContent = text;
    if (className) n.className = className;
    return n;
  }
  async function list() {
    const rows = await api(); el('list').replaceChildren();
    for (const w of rows) {
      const button = node('button', `${w.content.title} · ${w.state}`, 'btn ghost');
      button.addEventListener('click', () => run(() => load(w.id))); el('list').append(button);
    }
  }
  function render(data) {
    current = data; const w = data.work; const c = w.content;
    el('detail').hidden = false; el('title').value = c.title; el('body').value = c.body;
    el('kind').value = c.kind; el('plan').value = w.planId || '';
    el('state').textContent = w.state; el('fact').textContent = `${w.id} · ${c.detail}`;
    el('zone').value = c.zone || Intl.DateTimeFormat().resolvedOptions().timeZone;
    el('due').value = data.dueAt || ''; el('confirmed').checked = false; el('retry-confirmed').checked = false;
    el('preview').textContent = `${c.title}\n${c.body}\n\n${data.plan ? JSON.stringify(data.plan.spec, null, 2) : 'Local reminder; no professional action.'}`;
    el('messages').replaceChildren();
    for (const m of data.messages) {
      el('messages').append(node('p', `You: ${m.userText}`), node('p', m.reply === null ? 'Secretary: waiting for a reply' : `Secretary (saved work revision ${m.sourceRevision}): ${m.reply}`));
    }
    el('tasks').replaceChildren(); el('attention').hidden = !data.attention;
    for (const t of data.tasks) {
      const row = node('details', ''); row.append(node('summary', `${t.id} · ${t.state}${t.interrupted ? ' · interrupted: outcome needs verification' : ''}`));
      row.append(node('pre', t.report || 'No completion report yet.')); el('tasks').append(row);
    }
    const editable = ['DRAFT', 'SCHEDULED', 'NEEDS_CONFIRMATION', 'CANCELLED'].includes(w.state);
    for (const id of ['title', 'body', 'kind', 'plan', 'save']) el(id).disabled = !editable;
    el('confirm').disabled = !['DRAFT', 'SCHEDULED', 'NEEDS_CONFIRMATION'].includes(w.state);
  }
  async function load(id) { render(await api(`/${encodeURIComponent(id)}`)); }
  async function run(action) {
    if (busy) return; busy = true; el('status').textContent = 'Saving or loading…';
    try { await action(); el('status').textContent = 'Saved state loaded.'; }
    catch (error) { el('status').textContent = error.message; }
    finally { busy = false; }
  }
  const path = action => `/${encodeURIComponent(current.work.id)}/${action}`;
  el('new').onclick = () => run(async () => { const w = await api('', {}); await list(); await load(w.id); });
  el('reload').onclick = () => run(() => load(current.work.id));
  el('save').onclick = () => run(async () => {
    await api(path('edit'), {revision: current.work.revision, title: el('title').value, body: el('body').value, kind: el('kind').value, planId: el('plan').value});
    await load(current.work.id); await list();
  });
  el('confirm').onclick = () => run(async () => {
    if (el('body').value !== current.work.content.body || el('title').value !== current.work.content.title || el('kind').value !== current.work.content.kind || el('plan').value !== (current.work.planId || '')) throw new Error('Save your edits and review the saved content before confirming.');
    await api(path('confirm'), {revision: current.work.revision, dueAt: el('due').value, zone: el('zone').value, confirmed: el('confirmed').checked, planVersion: current.plan?.version ?? -1, planHash: current.currentPlanHash});
    await load(current.work.id); await list();
  });
  el('cancel').onclick = () => run(async () => { await api(path('cancel'), {revision: current.work.revision}); await load(current.work.id); await list(); });
  el('send').onclick = () => run(async () => {
    const text = el('message').value;
    if (!pendingMessage || pendingMessage.text !== text || pendingMessage.id !== current.work.id) pendingMessage = {id: current.work.id, key: crypto.randomUUID(), text, revision: current.work.revision};
    await api(path('messages'), {key: pendingMessage.key, text, revision: pendingMessage.revision});
    pendingMessage = null; el('message').value = ''; await load(current.work.id);
  });
  el('replies').onclick = () => run(async () => { render(await api(path('replies'), {})); await list(); });
  el('retry').onclick = () => run(async () => { await api(path('retry-coordination'), {revision: current.work.revision, confirmed: el('retry-confirmed').checked}); await load(current.work.id); });
  run(list);
})();
