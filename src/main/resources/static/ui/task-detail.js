(() => {
  const opener = document.querySelector('[data-task-diagnostic-open]')
    || document.querySelector('a[href^="/api/r1/plan-editor/plans/"][href*="/tasks/"]');
  if (!opener) return;
  const el = (tag, text, className = '') => {
    const node = document.createElement(tag); if (text !== undefined) node.textContent = text; node.className = className; return node;
  };
  const dialog = el('dialog', undefined, 'workspace-confirm console-confirm workflow-history-dialog');
  dialog.setAttribute('aria-labelledby', 'task-diagnostic-title');
  const top = el('div', 'Failure diagnosis', 'workspace-confirm__top');
  const body = el('div', undefined, 'workspace-confirm__body');
  const heading = el('h2', 'Failure diagnosis'); heading.id = 'task-diagnostic-title';
  const status = el('p'); status.setAttribute('role', 'status'); status.setAttribute('aria-live', 'polite');
  const content = el('div'); body.append(heading, status, content);
  const footer = el('div', undefined, 'workspace-confirm__footer');
  const copy = el('button', 'Copy diagnostic data', 'btn ghost'); copy.type = 'button'; copy.disabled = true;
  const close = el('button', 'Close', 'btn primary'); close.type = 'button'; close.autofocus = true;
  footer.append(copy, close); dialog.append(top, body, footer); document.body.append(dialog);
  let request = null, raw = '';
  close.addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', event => { if (event.target === dialog) dialog.close(); });
  dialog.addEventListener('close', () => { request?.abort(); raw = ''; copy.disabled = true; opener.focus(); });
  copy.addEventListener('click', async () => {
    const current = request;
    try { await navigator.clipboard.writeText(raw); if (dialog.open && request === current) status.textContent = 'Diagnostic data copied.'; }
    catch (_) { if (dialog.open && request === current) status.textContent = 'Copy failed. Expand the raw data to select and copy it.'; }
  });
  opener.addEventListener('click', async event => {
    event.preventDefault(); if (dialog.open) return;
    content.replaceChildren(); raw = ''; copy.disabled = true; status.textContent = 'Loading diagnosis…';
    dialog.showModal();
    const current = new AbortController(); request = current;
    try {
      const response = await fetch(opener.href, { signal: current.signal, cache: 'no-store' });
      if (!response.ok || response.redirected || !response.headers.get('content-type')?.includes('application/json'))
        throw new Error('Diagnosis could not be loaded. Check your session and permissions, then reopen it.');
      const data = await response.json();
      if (current.signal.aborted || !dialog.open || request !== current) return;
      if (!data.task || !Array.isArray(data.attempts)) throw new Error('Diagnostic data is incomplete.');
      const attempt = data.attempts[0];
      const section = (title, text) => { content.append(el('h3', title), el('p', text)); };
      section('Failure reason', attempt?.diagnostic?.summary || 'No specific failure reason was recorded.');
      const roles = { 'software-requirements-analyst': 'Requirement analysis', 'solution-architect': 'Solution design',
        'implementation-planner': 'Implementation planning', 'coding-work-package-executor': 'Code implementation',
        'workflow-stage-reviewer': 'Independent stage review' };
      section('Affected step', roles[data.task.skillRef] || data.task.skillRef || data.task.type);
      section('Recovery advice', attempt?.diagnostic?.nextAction || 'Check the current task state before retrying.');
      const technical = el('details'); technical.append(el('summary', 'Technical details'));
      const fields = el('dl', undefined, 'kv');
      for (const [name, value] of [['Task ID', data.task.id?.value], ['State', data.task.state], ['Worker', attempt?.workerId],
        ['Attempt ID', attempt?.id], ['Failure category', attempt?.failureCategory], ['Error code / termination reason', attempt?.failureCode],
        ['Workspace', data.task.repositoryRef], ['Git baseline', data.task.baseRevision]]) {
        const item = el('dd', value ?? 'Not recorded', 'mono'); item.dataset.i18nSkip = ''; fields.append(el('dt', name), item);
      }
      technical.append(fields);
      const source = el('details'); source.append(el('summary', 'Raw diagnostic data'));
      const json = el('pre', JSON.stringify(data, null, 2), 'task-report'); json.dataset.i18nSkip = ''; source.append(json);
      technical.append(source); content.append(technical);
      raw = JSON.stringify(data, null, 2); copy.disabled = false; status.textContent = '';
    } catch (error) { if (!current.signal.aborted && dialog.open && request === current) status.textContent = error.message; }
  });
})();

(() => {
  const notice = document.querySelector('[data-attempt-duration-warning]');
  if (!notice) return;
  const update = () => {
    const longRunning = [...document.querySelectorAll('[data-attempt-duration]')].some(attempt => {
      const started = Date.parse(attempt.dataset.startedAt);
      const warningMs = Number(attempt.dataset.warningMs);
      return ['CLAIMED', 'RUNNING'].includes(attempt.dataset.state) && Number.isFinite(started)
        && Number.isSafeInteger(warningMs) && warningMs > 0 && Date.now() - started >= warningMs;
    });
    notice.hidden = !longRunning;
    notice.classList.toggle('warn', longRunning);
  };
  update();
  const timer = setInterval(update, 1000);
  window.addEventListener('pagehide', () => clearInterval(timer), { once: true });
})();

(() => {
  const form = document.querySelector('[data-task-retry]');
  if (!form) return;
  const status = document.querySelector('[data-retry-status]');
  const errorText = document.querySelector('[data-retry-error]');
  const button = form.querySelector('button[type="submit"]');
  let pending = false;
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (pending) return;
    const reason = form.querySelector('[name="reason"]').value.trim();
    if (!reason) { status.textContent = 'Enter a retry reason.'; return; }
    pending = true;
    errorText.textContent = '';
    try {
      const confirmed = await window.aiwConfirm('Retry only this failed task? Earlier accepted outputs remain. Check local changes first; this starts another Worker execution.');
      if (!confirmed) return;
      button.disabled = true;
      status.textContent = 'Submitting retry…';
      const url = new URL(form.dataset.url, location.origin);
      url.searchParams.set('_csrf', form.dataset.csrf);
      const response = await fetch(url, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ planVersion: Number(form.dataset.planVersion), taskVersion: Number(form.dataset.taskVersion),
          reason, warningConfirmed: true })
      });
      if (!response.ok) throw new Error((await response.text()).slice(0, 500));
      if (response.redirected || !response.headers.get('content-type')?.includes('application/json')) {
        throw new Error('Your session changed. Reload before retrying.');
      }
      await response.json();
      location.reload();
    } catch (error) {
      status.textContent = 'Retry failed. Reload the page and check the current task state.';
      errorText.textContent = error.message;
    } finally { pending = false; button.disabled = false; }
  });
})();

(() => {
  const section = document.querySelector('[data-task-outputs]');
  if (!section) return;
  const list = section.querySelector('[data-output-list]');
  const status = section.querySelector('[data-output-status]');
  const element = (tag, text, className = '') => {
    const node = document.createElement(tag); node.textContent = text; node.className = className; return node;
  };
  const json = async response => {
    if (response.redirected || !response.headers.get('content-type')?.includes('application/json')) {
      throw new Error('Deliverable request failed. Check your permissions and reload the page.');
    }
    const body = await response.json();
    if (!response.ok) throw new Error(String(body.message || 'Deliverable request failed.').slice(0, 500));
    return body;
  };
  fetch(`/api/r1/task-outputs/tasks/${encodeURIComponent(section.dataset.taskId)}`).then(json).then(bundles => {
    list.replaceChildren();
    if (!bundles.length) { list.append(element('p', 'No deliverables registered yet.', 'muted')); return; }
    for (const bundle of bundles) {
      const article = element('article', '', 'form-card top-gap');
      const reviewLabels = { PASS: 'Independent review passed', PENDING: 'Awaiting a passed independent review', UNFINISHED: 'Unfinished candidate', NOT_REQUIRED: 'Review report' };
      const executionLabels = { REGISTERED: 'Output registered', CLAIMED: 'Execution claimed', RUNNING: 'Execution running',
        COMPLETED: 'Execution completed', FAILED: 'Execution failed', EXPIRED: 'Execution expired' };
      const heading = element('h3', '');
      heading.append(element('span', new Date(bundle.createdAtEpochMs).toLocaleString()), ' · ',
        element('span', executionLabels[bundle.executionState] || bundle.executionState), ' · ',
        element('span', reviewLabels[bundle.reviewState] || bundle.reviewState));
      article.append(heading);
      const directory = element('p', bundle.directory, 'small muted mono');
      directory.dataset.i18nSkip = ''; article.append(directory);
      for (const file of bundle.files) {
        const row = element('div', '', 'top-gap');
        const link = element('a', '');
        const path = element('span', file.path); path.dataset.i18nSkip = '';
        link.append(path, ` (${file.bytes} `, element('span', 'bytes'), ')');
        link.href = `/api/r1/task-outputs/files/${encodeURIComponent(file.id)}`;
        row.append(link);
        if (section.dataset.canOperate === 'true' && bundle.executionState === 'COMPLETED' && bundle.reviewState === 'PASS'
            && ['REPORT', 'DOCUMENT'].includes(file.kind) && file.path.endsWith('.md')) {
          const form = document.createElement('form'); form.className = 'form-card top-gap';
          const fields = [
            ['reviewTaskId', 'Passed source review task ID', /~work-\d+$/.test(section.dataset.taskId) ? section.dataset.taskId.replace(/~work-(\d+)$/, '~review-$1') : ''],
            ['targetPath', 'Project document path (for example, docs/requirements.md)', ''],
            ['reason', 'Publication reason', '']
          ];
          for (const [name, label, value] of fields) {
            const field = element('label', label, 'field');
            const input = document.createElement('input'); input.name = name; input.required = true;
            input.maxLength = name === 'reason' ? 2000 : 480; input.value = value; field.append(input); form.append(field);
          }
          const button = element('button', 'Create document publication draft', 'btn ghost small-btn'); button.type = 'submit'; form.append(button);
          form.addEventListener('submit', async event => {
            event.preventDefault(); button.disabled = true; status.textContent = '';
            try {
              const data = Object.fromEntries(new FormData(form));
              const url = new URL(`/api/r1/task-outputs/files/${encodeURIComponent(file.id)}/publication-plans`, location.origin);
              url.searchParams.set('_csrf', section.dataset.csrf);
              const plan = await json(await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) }));
              location.assign(`/ui/plans/${encodeURIComponent(plan.id)}`);
            } catch (error) { status.textContent = error.message; button.disabled = false; }
          });
          const details = document.createElement('details'); details.className = 'top-gap';
          details.append(element('summary', 'Save as a project document'), form); row.append(details);
        }
        article.append(row);
      }
      list.append(article);
    }
  }).catch(error => { list.replaceChildren(element('p', error.message, 'muted')); });
})();
