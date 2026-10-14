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
  fetch(`/ui/api/task-outputs/tasks/${encodeURIComponent(section.dataset.taskId)}`).then(json).then(bundles => {
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
        link.href = `/ui/api/task-outputs/files/${encodeURIComponent(file.id)}`;
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
              const url = new URL(`/ui/api/task-outputs/files/${encodeURIComponent(file.id)}/publication-plans`, location.origin);
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
