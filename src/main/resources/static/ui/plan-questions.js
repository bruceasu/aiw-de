(() => {
  const root = document.querySelector('[data-workflow-detail]');
  const section = root?.querySelector('[data-question-board]');
  if (!section) return;
  const planId = root.dataset.id;
  const base = `/ui/plan-editor/plans/${encodeURIComponent(planId)}/questions`;
  const status = section.querySelector('[data-question-status]');
  const list = section.querySelector('[data-question-list]');
  const drafts = new Map();
  let board, loading = false, saving = false, reloadRequested = false, fingerprint = '', request, previewRequest;
  const node = (tag, text, className) => {
    const value = document.createElement(tag);
    if (text !== undefined) value.textContent = text;
    if (className) value.className = className;
    return value;
  };
  const announce = (message, tone = '') => { status.textContent = message; status.dataset.tone = tone; };
  const open = name => root.dispatchEvent(new CustomEvent('aiw:open-plan-window', { detail: name }));
  const api = async (url, body, signal) => {
    const response = await fetch(body === undefined ? url : `${url}?_csrf=${encodeURIComponent(root.dataset.csrf)}`, {
      method: body === undefined ? 'GET' : 'POST', cache: 'no-store', signal,
      headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!response.ok) throw new Error((await response.text()).slice(0, 500) || `HTTP ${response.status}`);
    if (response.redirected || !response.headers.get('content-type')?.includes('application/json')) throw new Error('Your session changed. Reload before answering.');
    return response.json();
  };
  const preview = async document => {
    previewRequest?.abort(); const controller = new AbortController(); previewRequest = controller;
    const timeout = setTimeout(() => controller.abort(), 10000);
    open('file-preview');
    root.querySelector('[data-question-preview-path]').textContent = document.file.path;
    root.querySelector('[data-question-preview]').textContent = '';
    root.querySelector('[data-question-preview-status]').textContent = 'Loading source file…';
    try {
      const value = await api(`${base}/files/${encodeURIComponent(document.file.id)}`, undefined, controller.signal);
      if (controller !== previewRequest) return;
      let content = value.content;
      if (!value.truncated && value.path.endsWith('.json')) {
        try { content = JSON.stringify(JSON.parse(content), null, 2); } catch (_) { /* Show original text. */ }
      }
      root.querySelector('[data-question-preview]').textContent = content;
      root.querySelector('[data-question-preview-status]').textContent = value.truncated ? 'Preview truncated. Download the source file for the full content.' : '';
    } catch (error) {
      if (controller === previewRequest) root.querySelector('[data-question-preview-status]').textContent = error.name === 'AbortError' ? 'Source preview timed out. Try again.' : error.message;
    } finally { clearTimeout(timeout); }
  };
  const links = document => {
    const row = node('div', undefined, 'question-source-links');
    const path = node('span', document.file.path, 'mono'); path.dataset.i18nSkip = '';
    row.append(path);
    if (/\.(json|md|txt|log|ya?ml|ts|js|sh|xml|csv)$/i.test(document.file.path)) {
      const button = node('button', 'Preview source', 'btn ghost small-btn'); button.type = 'button';
      button.addEventListener('click', () => preview(document)); row.append(button);
    }
    const download = node('a', 'Download source', 'btn ghost small-btn'); download.href = `/ui/api/task-outputs/files/${encodeURIComponent(document.file.id)}`;
    const task = node('a', 'View source task', 'btn ghost small-btn'); task.href = `/ui/plans/${encodeURIComponent(planId)}/tasks/${encodeURIComponent(document.taskId)}`;
    row.append(download, task);
    return row;
  };
  const render = () => {
    const blocked = section.querySelector('[data-question-blocked]');
    blocked.hidden = !board.blockedReason; blocked.textContent = board.blockedReason || '';
    list.replaceChildren();
    const unanswered = board.questions.filter(question => !question.decision).length;
    const badge = root.querySelector('[data-question-count]'); badge.hidden = !unanswered; badge.textContent = String(unanswered);
    const tool = root.querySelector('[data-plan-window-open="questions"]'); tool.dataset.pendingQuestions = String(unanswered > 0);
    if (!board.questions.length) list.append(node('p', 'No structured questions found. Check the related files and review reports.', 'empty'));
    for (const question of [...board.questions].sort((a, b) => Number(!!a.decision) - Number(!!b.decision))) {
      const card = node('article', undefined, 'question-card'); card.dataset.answered = String(!!question.decision);
      const header = node('div', undefined, 'section-title');
      const heading = node('h3', question.id); heading.dataset.i18nSkip = '';
      header.append(heading, node('span', question.decision ? 'Answer confirmed' : 'Decision needed', `badge ${question.decision ? 'ok' : 'warn'}`));
      card.append(header, node('p', question.question, 'question-text'));
      const context = node('details'); context.append(node('summary', 'Question context'));
      context.append(node('p', question.context, 'question-context'));
      context.append(node('p', `${question.source.taskId} · ${question.source.reviewState}`, 'mono small muted'));
      context.append(node('p', `SHA-256: ${question.source.file.sha256}`, 'mono small muted'));
      if (question.affects.length) context.append(node('p', question.affects.join(', '), 'small muted'));
      card.append(context, links(question.source));
      if (question.decision) {
        card.append(node('p', question.decision.answer, 'question-confirmed-answer'));
        card.append(node('p', `${question.decision.actor} · ${new Date(question.decision.decidedAt).toLocaleString()}`, 'small muted'));
      }
      if (root.dataset.canOperate === 'true') {
        const form = node('form', undefined, 'question-answer-form');
        const label = node('label', 'Your decision', 'field');
        const input = node('textarea'); input.maxLength = 2000; input.rows = 3; input.required = true;
        const draftKey = `${question.key}:${question.source.file.sha256}`;
        input.value = drafts.has(draftKey) ? drafts.get(draftKey) : question.decision?.answer || '';
        input.addEventListener('input', () => drafts.set(draftKey, input.value)); label.append(input);
        const button = node('button', question.decision ? 'Confirm updated answer' : 'Confirm answer', 'btn primary small-btn');
        button.type = 'submit'; button.disabled = !!board.blockedReason || saving;
        const localStatus = node('p'); localStatus.setAttribute('role', 'status'); localStatus.setAttribute('aria-live', 'polite');
        const feedback = (message, tone = '') => { announce(message, tone); localStatus.textContent = message; localStatus.dataset.tone = tone; };
        form.append(label, button, localStatus);
        form.addEventListener('submit', async event => {
          event.preventDefault(); if (saving) return;
          const answer = input.value.trim(); if (!answer) { input.focus(); feedback('Enter an answer before confirming.', 'error'); return; }
          const version = board.planVersion;
          saving = true; section.querySelectorAll('button[type="submit"]').forEach(value => { value.disabled = true; });
          let timer; const labelText = button.textContent; button.textContent = 'Waiting for confirmation…';
          try {
            if (!await window.aiwConfirm('Confirm this answer as a manager decision? It will be supplied to future execution and review tasks. This does not start work.')) return;
            button.textContent = 'Saving decision…'; feedback('Saving decision…');
            const controller = new AbortController(); timer = setTimeout(() => controller.abort(), 15000);
            await api(`${base}/answers`, { version, questionKey: question.key, sourceFileId: question.source.file.id,
              sourceHash: question.source.file.sha256, answer, confirmed: true }, controller.signal);
            drafts.delete(draftKey);
            feedback('Answer confirmed. Use Mission Control to authorize the next repair or review.', 'success');
            root.dispatchEvent(new CustomEvent('aiw:refresh-plan'));
          } catch (error) {
            feedback(error.name === 'AbortError' ? 'Decision request timed out. Refresh and check answer history before submitting again.' : error.message, 'error');
          } finally {
            clearTimeout(timer); saving = false; button.textContent = labelText;
            section.querySelectorAll('button[type="submit"]').forEach(value => { value.disabled = !!board.blockedReason; });
            await load();
          }
        });
        card.append(form);
      }
      list.append(card);
    }
    const files = section.querySelector('[data-question-files]'); files.replaceChildren();
    if (!board.documents.length) files.append(node('p', 'No deliverables registered yet.', 'muted'));
    for (const document of board.documents) {
      const row = node('article', undefined, 'question-file-row'); row.append(node('p', `${document.taskId} · ${document.reviewState}`, 'small muted mono'), links(document)); files.append(row);
    }
    const history = section.querySelector('[data-question-history]'); history.replaceChildren();
    if (!board.history.length) history.append(node('p', 'No decision recorded.', 'muted'));
    for (const answer of board.history) {
      const item = node('article', undefined, 'question-file-row');
      item.append(node('h3', answer.questionId), node('p', answer.questionText), node('p', answer.answer, 'question-confirmed-answer'),
        node('p', `${answer.actor} · ${new Date(answer.decidedAt).toLocaleString()} · v${answer.planVersion}`, 'small muted'));
      const source = node('a', 'Download source', 'btn ghost small-btn'); source.href = `/ui/api/task-outputs/files/${encodeURIComponent(answer.sourceFileId)}`;
      item.append(source); history.append(item);
    }
    for (const warning of board.warnings) list.append(node('p', warning, 'callout warn-callout'));
  };
  const load = async () => {
    if (loading || saving) { reloadRequested = true; return; }
    reloadRequested = false; loading = true; const controller = new AbortController(); request = controller;
    const timeout = setTimeout(() => controller.abort(), 10000);
    try { board = await api(base, undefined, controller.signal); render(); }
    catch (error) { announce(error.name === 'AbortError' ? 'Question request timed out. Refresh to try again.' : error.message, 'error'); }
    finally { clearTimeout(timeout); loading = false; if (reloadRequested && !saving) void load(); }
  };
  section.querySelector('[data-questions-refresh]').addEventListener('click', load);
  section.querySelector('[data-questions-mission]')?.addEventListener('click', () => open('mission'));
  root.querySelector('[data-plan-window-open="questions"]').addEventListener('click', load);
  root.addEventListener('aiw:plan-snapshot', event => {
    const next = `${event.detail.version}:${event.detail.tasks}`;
    if (next !== fingerprint) { fingerprint = next; void load(); }
  });
  window.addEventListener('pagehide', () => { request?.abort(); previewRequest?.abort(); });
  window.addEventListener('pageshow', event => { if (event.persisted) void load(); });
  void load();
})();
