(() => {
  const editor = document.querySelector('[data-workflow-editor]');
  const detail = document.querySelector('[data-workflow-detail]');
  if (!editor && !detail) return;
  const root = editor || detail;
  const csrf = root.dataset.csrf;
  let pendingWrites = 0;
  const api = async (path, body, options = {}) => {
    if (body !== undefined) pendingWrites++;
    try {
      const response = await fetch(`/ui/plan-editor${path}${body === undefined ? '' : `?_csrf=${encodeURIComponent(csrf)}`}`, {
        method: body === undefined ? 'GET' : 'POST',
        signal: options.signal,
        cache: options.cache,
        headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body)
      });
      if (!response.ok) throw new Error((await response.text()).slice(0, 500) || `HTTP ${response.status}`);
      return await response.json();
    } finally { if (body !== undefined) pendingWrites--; }
  };
  const el = (tag, text, className) => {
    const node = document.createElement(tag);
    if (text !== undefined) node.textContent = String(text);
    if (className) node.className = className;
    return node;
  };
  const line = (parent, tag, text, className) => parent.appendChild(el(tag, text, className));
  const graph = (host, steps) => {
    host.replaceChildren();
    for (const [index, step] of steps.entries()) {
      const row = el('div', undefined, 'workflow-node');
      line(row, 'strong', `${index + 1}. ${step.key || '(unnamed)'}`);
      line(row, 'span', step.dependsOn?.length ? ` ← ${step.dependsOn.join(', ')}` : ' · start', 'muted');
      host.appendChild(row);
    }
  };
  const skillNames = value => value.split(',').map(x => x.trim()).filter(Boolean);
  const stepCopy = step => ({ ...step, requiredSkills: [...(step.requiredSkills || [])],
    preferredSkills: [...(step.preferredSkills || [])], dependsOn: [...(step.dependsOn || [])] });
  const blank = () => ({ key: '', requiredSkills: ['requirement-analysis'], preferredSkills: [],
    skillRef: 'software-requirements-analyst', instruction: '', repositoryRef: null, baseRevision: null, dependsOn: [] });

  if (editor && editor.dataset.enabled === 'true') {
    let catalog;
    let steps = [blank()];
    let source = 'MANAGER';
    let inputVersion = 0;
    let recommendation = null;
    let currentRequest = null;
    let timer = null;
    let context = null;
    const goal = editor.querySelector('[data-goal]');
    const rows = editor.querySelector('[data-steps]');
    const notice = editor.querySelector('[data-validation]');
    const preview = editor.querySelector('[data-preview]');
    const status = editor.querySelector('[data-recommend-status]');
    const history = editor.querySelector('[data-recommend-history]');
    const result = editor.querySelector('[data-recommend-result]');
    const saveStatus = editor.querySelector('[data-save-status]');
    const csrfError = 'If your session expired, reload this page and try again.';
    const workspaceOptions = [...editor.querySelectorAll('[data-workspace-options] option')]
      .map(option => ({ value: option.value, label: option.textContent }));
    const workspaceRefs = new Set(workspaceOptions.map(option => option.value));
    const renderCapabilityGuide = () => {
      const host = editor.querySelector('[data-capability-guide]');
      host.replaceChildren();
      const active = catalog.workers.filter(worker => worker.status === 'ACTIVE');
      const roleNames = new Set([...catalog.roles.map(role => role.capability),
        'workflow-review', 'code-review', 'verification', 'integration-verification']);
      const roles = el('div');
      line(roles, 'h3', 'Step roles');
      for (const role of catalog.roles) {
        const count = active.filter(worker => worker.capabilities.includes(role.capability)).length;
        line(roles, 'p', `${role.capability} · ${role.resultKind} · ${count} active Worker(s)`);
      }
      host.appendChild(roles);
      const counts = new Map();
      for (const worker of active) for (const capability of new Set(worker.capabilities)) {
        if (roleNames.has(capability) || capability.startsWith('repo:') || capability === 'flexible-work-v1') continue;
        counts.set(capability, (counts.get(capability) || 0) + 1);
      }
      const technical = el('div');
      line(technical, 'h3', 'Technical capabilities declared by active Workers');
      if (!counts.size) line(technical, 'p', 'No additional technical capabilities in this snapshot.');
      else {
        const chips = el('div', undefined, 'capabilities');
        for (const [name, count] of [...counts].sort(([a], [b]) => a.localeCompare(b))) {
          line(chips, 'span', `${name} · ${count}`);
        }
        technical.appendChild(chips);
      }
      host.appendChild(technical);
      const reviewers = active.filter(worker => worker.capabilities.includes('workflow-review')).length;
      line(host, 'p', `Independent review: ${reviewers} active workflow-review Worker(s).`);
    };
    const markEdit = () => {
      inputVersion++; recommendation = null; result.hidden = true;
      if (currentRequest) {
        currentRequest.stale = true;
        status.textContent = currentRequest.id
          ? `Previous analysis ${currentRequest.id} is stale. Check or withdraw it below.`
          : 'Previous analysis request is stale. Its task ID will appear below when the request returns.';
      }
      if (timer) { clearInterval(timer); timer = null; }
      editor.querySelector('[data-refresh-recommend]').hidden = true;
      if (catalog) renderPreview();
    };
    const field = (container, labelText, value, update, multiline = false) => {
      const label = el('label', labelText, 'field');
      const input = el(multiline ? 'textarea' : 'input');
      if (!multiline) input.type = 'text';
      input.value = value || '';
      input.addEventListener('input', () => { update(input.value); markEdit(); });
      label.appendChild(input); container.appendChild(label);
      return input;
    };
    const workspaceField = (container, step, index) => {
      const group = el('div', undefined, 'field');
      const label = el('label', 'Workspace reference');
      const select = el('select');
      select.id = `step-${index + 1}-workspace`;
      label.htmlFor = select.id;
      const filter = el('input');
      filter.type = 'search'; filter.placeholder = 'Filter Workspaces by name or reference';
      filter.setAttribute('aria-label', `Filter Workspaces for step ${index + 1}`);
      const options = () => {
        select.replaceChildren();
        const empty = el('option', 'No Workspace selected'); empty.value = ''; select.appendChild(empty);
        if (step.repositoryRef && !workspaceRefs.has(step.repositoryRef)) {
          const unavailable = el('option', `Unavailable: ${step.repositoryRef}`);
          unavailable.value = step.repositoryRef; unavailable.disabled = true; select.appendChild(unavailable);
        }
        const query = filter.value.trim().toLowerCase();
        const matches = workspaceOptions.filter(option => option.label.toLowerCase().includes(query));
        for (const entry of workspaceOptions) {
          if (!matches.includes(entry) && entry.value !== step.repositoryRef) continue;
          const option = el('option', entry.label); option.value = entry.value; select.appendChild(option);
        }
        if (!matches.length) {
          const none = el('option', 'No matching Workspaces'); none.disabled = true; select.appendChild(none);
        }
        select.value = step.repositoryRef || '';
      };
      filter.addEventListener('input', options);
      select.addEventListener('change', () => { step.repositoryRef = select.value || null; markEdit(); options(); });
      group.append(label, filter, select);
      line(group, 'span', workspaceOptions.length
        ? 'Type to filter, then select a registered Workspace target. The current selection stays visible.'
        : 'No enabled Workspace targets. Register one in Workspaces, then reload this page.', 'help');
      const manage = el('a', 'Manage Workspaces'); manage.href = '/ui/workspaces'; group.appendChild(manage);
      container.appendChild(group); options();
    };
    const renderSteps = () => {
      rows.replaceChildren();
      steps.forEach((step, index) => {
        const card = el('section', undefined, 'workflow-step');
        const title = line(card, 'h3', `Step ${index + 1}`);
        title.id = `step-${index + 1}`;
        const grid = el('div', undefined, 'form-grid');
        const keyInput = field(grid, 'Step key', step.key, value => { step.key = value; });
        keyInput.maxLength = 32; keyInput.dataset.stepKey = step.key;
        const roleLabel = el('label', 'Worker role', 'field');
        const select = el('select');
        for (const role of catalog.roles) {
          const option = el('option', `${role.capability} · ${role.skillRef} (${role.resultKind})`);
          option.value = `${role.capability}|${role.skillRef}`;
          select.appendChild(option);
        }
        const role = catalog.roles.find(r => r.skillRef === step.skillRef && step.requiredSkills.includes(r.capability)) || catalog.roles[0];
        select.value = `${role.capability}|${role.skillRef}`;
        select.addEventListener('change', () => {
          const chosen = catalog.roles.find(r => `${r.capability}|${r.skillRef}` === select.value);
          step.requiredSkills = [chosen.capability, ...step.requiredSkills.filter(s => !catalog.roles.some(r => r.capability === s))];
          step.skillRef = chosen.skillRef; markEdit(); renderSteps();
        });
        roleLabel.appendChild(select); grid.appendChild(roleLabel);
        field(grid, 'Required technical skills (comma separated)', step.requiredSkills.filter(s => s !== role.capability).join(', '),
          value => { step.requiredSkills = [role.capability, ...skillNames(value)]; });
        field(grid, 'Preferred skills (comma separated)', step.preferredSkills.join(', '), value => { step.preferredSkills = skillNames(value); });
        const model = field(grid, 'Model (optional)', step.model, value => { step.model = value.trim() || null; });
        model.maxLength = 160;
        const effort = field(grid, 'Reasoning effort (optional)', step.effort, value => { step.effort = value.trim() || null; });
        effort.maxLength = 160;
        line(effort.parentElement, 'span', 'Leave empty to let the Worker decide. Invalid or unavailable settings fall back to the Worker configuration.', 'help');
        workspaceField(grid, step, index);
        const revisionInput = field(grid, 'Base revision (optional full Git commit)', step.baseRevision,
          value => { step.baseRevision = value.trim() || null; });
        line(revisionInput.parentElement, 'span', 'Leave empty to use the Workspace directory directly. Set a full Git commit to use an isolated Git worktree.', 'help');
        field(grid, 'Declared earlier dependencies (comma separated)', step.dependsOn.join(', '), value => { step.dependsOn = skillNames(value); });
        field(grid, 'Instruction and acceptance criteria', step.instruction, value => { step.instruction = value; }, true);
        card.appendChild(grid);
        const controls = el('div', undefined, 'form-actions');
        const button = (name, action, disabled = false) => {
          const b = el('button', name, 'btn ghost small-btn'); b.type = 'button'; b.disabled = disabled;
          b.addEventListener('click', action); controls.appendChild(b);
        };
        button('Move up', () => { [steps[index - 1], steps[index]] = [steps[index], steps[index - 1]]; markEdit(); renderSteps(); }, index === 0);
        button('Move down', () => { [steps[index], steps[index + 1]] = [steps[index + 1], steps[index]]; markEdit(); renderSteps(); }, index === steps.length - 1);
        button('Delete step', () => { steps.splice(index, 1); markEdit(); renderSteps(); });
        card.appendChild(controls); rows.appendChild(card);
      });
      renderPreview();
    };
    const renderPreview = () => {
      preview.replaceChildren(); notice.textContent = '';
      const seen = new Set();
      if (!goal.value.trim()) notice.textContent = 'Enter a goal before saving.';
      else if (!steps.length || steps.length > 32) notice.textContent = 'Choose 1–32 steps.';
      else for (const [index, step] of steps.entries()) {
        const problems = [];
        if (!/^[a-z][a-z0-9._-]{0,31}$/.test(step.key) || seen.has(step.key)) problems.push('unique valid key');
        if (!step.instruction?.trim()) problems.push('instruction');
        if (step.dependsOn.some(key => !seen.has(key))) problems.push('dependencies on earlier keys');
        const role = catalog.roles.find(r => r.skillRef === step.skillRef && step.requiredSkills.includes(r.capability));
        if (!role) problems.push('trusted role and skill');
        if (role?.needsWorkspace && !step.repositoryRef) problems.push('workspace');
        if (step.repositoryRef && !workspaceRefs.has(step.repositoryRef)) problems.push('an available registered Workspace (reload if changed)');
        if (step.baseRevision && !/^(?:[a-f0-9]{40}|[a-f0-9]{64})$/i.test(step.baseRevision)) problems.push('full Git revision');
        if (step.baseRevision && !step.repositoryRef) problems.push('workspace for Git revision');
        if (role?.resultKind === 'VERDICT' && (!step.dependsOn.length || step.baseRevision)) problems.push('upstream dependency and empty revision');
        if (problems.length && !notice.textContent) notice.textContent = `Step ${index + 1} (${step.key || 'unnamed'}): check ${problems.join(', ')}.`;
        seen.add(step.key);
        const tr = el('tr');
        const matches = catalog.workers.filter(worker => worker.status === 'ACTIVE' &&
          step.requiredSkills.every(skill => worker.capabilities.includes(skill)));
        const match = matches.length ? `${matches.length} active Worker(s) in snapshot` :
          catalog.workers.length >= 100 ? 'Unknown: snapshot limit reached' : 'No active match in snapshot';
        const reviewers = catalog.workers.filter(worker => worker.status === 'ACTIVE' &&
          worker.capabilities.includes('workflow-review'));
        const reviewMatch = reviewers.length ? `${reviewers.length} independent reviewer(s)` : 'No independent reviewer';
        [step.key || '?', `${step.requiredSkills.join(', ')} / ${step.skillRef}`, step.dependsOn.join(', ') || '—',
          `${step.repositoryRef || 'No workspace'} / ${step.baseRevision || 'upstream or none'}`, `${match}; ${reviewMatch}`]
          .forEach(text => line(tr, 'td', text));
        preview.appendChild(tr);
      }
      graph(editor.querySelector('[data-graph]'), steps);
      return !notice.textContent;
    };
    goal.addEventListener('input', markEdit);
    editor.querySelector('[data-add-step]').addEventListener('click', () => {
      if (!catalog) return;
      if (steps.length >= 32) { notice.textContent = 'Maximum 32 steps.'; return; }
      steps.push(blank()); markEdit(); renderSteps();
    });
    editor.querySelector('[data-use-template]').addEventListener('click', () => {
      if (!catalog) return;
      const id = editor.querySelector('[data-template]').value;
      const chosen = catalog.templates.find(t => t.id === id);
      steps = chosen ? chosen.plan.steps.map(stepCopy) : [blank()]; source = 'MANAGER';
      markEdit(); renderSteps();
    });
    editor.querySelector('[data-save]').addEventListener('click', async () => {
      if (!catalog) { saveStatus.textContent = 'Editor data is still loading.'; return; }
      if (!renderPreview()) { notice.scrollIntoView({ block: 'center' }); return; }
      const payload = { goal: goal.value, source, steps };
      try {
        saveStatus.textContent = 'Saving draft…';
        if (!context) throw new Error('Editor recovery context is still loading.');
        const plan = await api(`/recommendation-contexts/${encodeURIComponent(context.id)}/draft`, { revision: context.revision, plan: payload });
        location.href = `/ui/plans/${encodeURIComponent(plan.id)}`;
      } catch (error) { saveStatus.textContent = `Draft was not saved: ${error.message}. ${csrfError} Your input is still here.`; }
    });
    const exposeRequest = snapshot => {
      const row = el('div', undefined, 'workflow-request');
      const state = line(row, 'span', `${snapshot.id} · ${snapshot.stale ? 'stale · ' : ''}checking…`);
      const check = el('button', 'Check task', 'btn ghost small-btn'); check.type = 'button';
      check.addEventListener('click', async () => {
        try {
          const reply = await api(`/recommendations/${encodeURIComponent(snapshot.id)}`);
          withdraw.hidden = !['READY', 'BLOCKED'].includes(reply.state);
          state.textContent = `${snapshot.id} · ${snapshot.stale ? 'stale · ' : ''}${reply.state}${reply.error ? ` · ${reply.error}` : ''}`;
          if (!snapshot.stale && snapshot === currentRequest) await poll();
        } catch (error) { state.textContent = `${snapshot.id} · check failed: ${error.message}`; }
      });
      const withdraw = el('button', 'Withdraw if queued', 'btn ghost small-btn'); withdraw.type = 'button';
      withdraw.addEventListener('click', async () => {
        try {
          const reply = await api(`/recommendations/${encodeURIComponent(snapshot.id)}/withdraw`, {});
          state.textContent = `${snapshot.id} · ${reply.state}`;
          withdraw.hidden = true;
        } catch (error) { state.textContent = `${snapshot.id} · cannot withdraw: ${error.message}`; }
      });
      withdraw.hidden = true;
      snapshot.withdraw = withdraw;
      row.append(check, withdraw); history.prepend(row);
      check.click();
    };
    const poll = async () => {
      if (!currentRequest) return;
      const snapshot = currentRequest;
      if (!snapshot.id || snapshot.stale) return;
      try {
        const reply = await api(`/recommendations/${encodeURIComponent(snapshot.id)}`);
        const queued = ['READY', 'BLOCKED'].includes(reply.state);
        if (snapshot.withdraw) snapshot.withdraw.hidden = !queued;
        if (snapshot !== currentRequest || snapshot.stale || snapshot.version !== inputVersion || snapshot.goal !== goal.value) return;
        editor.querySelector('[data-withdraw-recommend]').hidden = !queued;
        if (reply.state === 'READY_TO_APPLY') {
          clearInterval(timer); timer = null; recommendation = reply;
          editor.querySelector('[data-refresh-recommend]').hidden = true;
          editor.querySelector('[data-withdraw-recommend]').hidden = true;
          status.textContent = 'Suggestion ready. Review it before applying.';
          editor.querySelector('[data-recommend-reason]').textContent = reply.reason;
          result.hidden = false;
        } else if (['FAILED', 'CANCELLED', 'INVALID'].includes(reply.state)) {
          clearInterval(timer); timer = null; status.textContent = reply.error || reply.state;
          editor.querySelector('[data-refresh-recommend]').hidden = true;
          editor.querySelector('[data-withdraw-recommend]').hidden = true;
        } else status.textContent = `Analysis ${reply.state.toLowerCase()}; waiting for the planner Worker…`;
      } catch (error) { clearInterval(timer); timer = null; status.textContent = `Recommendation unavailable: ${error.message}`;
        editor.querySelector('[data-refresh-recommend]').hidden = false; }
    };
    editor.querySelector('[data-recommend]').addEventListener('click', async () => {
      if (!goal.value.trim()) { status.textContent = 'Enter a goal first.'; return; }
      if (!context || context.planId) { status.textContent = 'Open a new editor context to request another analysis.'; return; }
      if (goal.value.length > 8000) { status.textContent = 'AI recommendation accepts up to 8000 characters; edit the goal or continue manually.'; return; }
      clearInterval(timer); recommendation = null; result.hidden = true;
      editor.querySelector('[data-refresh-recommend]').hidden = true;
      editor.querySelector('[data-withdraw-recommend]').hidden = true;
      const button = editor.querySelector('[data-recommend]'); button.disabled = true;
      const snapshot = { key: crypto.randomUUID(), version: inputVersion, goal: goal.value, id: null, stale: false };
      currentRequest = snapshot; status.textContent = 'Requesting one bounded analysis task…';
      try {
        const reply = await api('/recommendations', { requestKey: snapshot.key, inputVersion: snapshot.version, goal: snapshot.goal,
          contextId: context.id, contextRevision: context.revision });
        context = (await api(`/recommendation-contexts/${encodeURIComponent(context.id)}`)).context;
        snapshot.id = reply.id;
        snapshot.stale = snapshot.stale || snapshot !== currentRequest || snapshot.version !== inputVersion || snapshot.goal !== goal.value;
        exposeRequest(snapshot);
        if (snapshot.stale) {
          if (snapshot === currentRequest) status.textContent = `Previous analysis ${snapshot.id} is stale. Check or withdraw it below.`;
          return;
        }
        editor.querySelector('[data-withdraw-recommend]').hidden = !['READY', 'BLOCKED'].includes(reply.state);
        timer = setInterval(poll, 5000);
        await poll();
        setTimeout(() => { if (snapshot === currentRequest && timer) {
          clearInterval(timer); timer = null;
          status.textContent = 'Waiting stopped after 10 minutes. The analysis task may still run; check this request again or start a new request.';
          editor.querySelector('[data-refresh-recommend]').hidden = false;
        } }, 600000);
      } catch (error) { status.textContent = `Recommendation could not start: ${error.message}`; }
      finally { button.disabled = false; }
    });
    editor.querySelector('[data-withdraw-recommend]').addEventListener('click', async () => {
      if (!currentRequest?.id) return;
      try { const reply = await api(`/recommendations/${encodeURIComponent(currentRequest.id)}/withdraw`, {});
        status.textContent = reply.error || reply.state; clearInterval(timer); timer = null;
        editor.querySelector('[data-withdraw-recommend]').hidden = true;
        if (currentRequest.withdraw) currentRequest.withdraw.hidden = true;
      } catch (error) { status.textContent = `Cannot withdraw active analysis: ${error.message}`; }
    });
    editor.querySelector('[data-refresh-recommend]').addEventListener('click', poll);
    editor.querySelector('[data-apply-recommend]').addEventListener('click', () => {
      if (!recommendation || recommendation.inputVersion !== inputVersion || recommendation.plan.goal !== goal.value.trim()) {
        status.textContent = 'Suggestion is stale; request again for the current goal.'; return;
      }
      steps = recommendation.plan.steps.map(stepCopy); source = 'AI';
      result.hidden = true; status.textContent = 'Suggestion applied. Add required workspace details, review and save a draft.';
      renderSteps();
    });
    api('/catalog').then(async data => {
      catalog = data;
      renderCapabilityGuide();
      const select = editor.querySelector('[data-template]');
      data.templates.forEach(t => { const option = el('option', `${t.title} — ${t.use}`); option.value = t.id; select.appendChild(option); });
      if (!data.recommendationAvailable) {
        editor.querySelector('[data-recommend]').disabled = true;
        status.textContent = data.recommendationStatus.reason;
      }
      const recent = await api('/recommendation-contexts');
      const recoveryList = editor.querySelector('[data-recovery-contexts]');
      for (const item of recent.filter(item => item.goal)) {
        const link = el('a', `${item.planId || 'Unsaved editor'} · ${item.goal.slice(0, 80)}`, 'btn ghost small-btn');
        link.href = `/ui/plans/new?context=${encodeURIComponent(item.id)}`;
        recoveryList.append(link);
      }
      const contextId = new URLSearchParams(location.search).get('context');
      if (contextId) {
        const recovered = await api(`/recommendation-contexts/${encodeURIComponent(contextId)}`);
        context = recovered.context;
        goal.value = context.goal; inputVersion = context.inputVersion;
        if (recovered.result) {
          const reply = recovered.result;
          const snapshot = { key: reply.requestKey, version: reply.inputVersion, goal: context.goal, id: reply.id, stale: false };
          currentRequest = snapshot; exposeRequest(snapshot);
          timer = setInterval(poll, 5000); await poll();
        }
        if (context.planId) {
          editor.querySelector('[data-recommend]').disabled = true;
          editor.querySelector('[data-save]').textContent = 'Save as a new draft';
          const link = el('a', 'View the saved Plan', 'btn ghost'); link.href = `/ui/plans/${encodeURIComponent(context.planId)}`;
          recoveryList.prepend(link);
          line(recoveryList, 'p', 'This analysis is linked to a saved Plan. Applying and saving creates a new draft; the saved Plan keeps its confirmed selection. To request a new analysis, open a new editor.', 'help');
        }
      } else {
        context = await api('/recommendation-contexts', {});
        const url = new URL(location.href); url.searchParams.set('context', context.id); historyReplace(url);
      }
      const copy = new URLSearchParams(location.search).get('copy');
      if (copy && !context.goal) return api(`/plans/${encodeURIComponent(copy)}`).then(data => {
        goal.value = data.plan.spec.goal; source = data.plan.spec.source;
        steps = data.plan.spec.steps.map(stepCopy); renderSteps();
      });
      renderSteps();
    }).catch(error => { notice.textContent = `Editor data unavailable: ${error.message}`; });
    const historyReplace = url => window.history.replaceState(null, '', url);
  }

  if (detail) {
    const id = detail.dataset.id;
    if (detail.dataset.canOperate === 'true') api(`/plans/${encodeURIComponent(id)}/recommendation-context`).then(context => {
      if (!context.id) return;
      const link = el('a', 'View recommendation analysis', 'btn ghost small-btn');
      link.href = `/ui/plans/new?context=${encodeURIComponent(context.id)}`;
      detail.querySelector('[data-detail-goal]').parentElement.append(link);
    }).catch(() => {});
    let plan;
    let reviewRecovery;
    let repairSubmitting = false;
    const actionStatus = detail.querySelector('[data-action-status]');
    const refreshStatus = detail.querySelector('[data-plan-refresh-status]');
    const historyDialog = detail.querySelector('[data-task-history-dialog]');
    const historyBody = detail.querySelector('[data-task-history-body]');
    let historyOpener = null;
    detail.querySelector('[data-task-history-close]')?.addEventListener('click', () => historyDialog.close());
    historyDialog?.addEventListener('click', event => { if (event.target === historyDialog) historyDialog.close(); });
    historyDialog?.addEventListener('close', () => {
      historyBody.replaceChildren();
      if (historyOpener?.isConnected) historyOpener.focus();
      historyOpener = null;
    });
    const taskRecord = (row, reportStates = new Map()) => {
      const kind = row.task.id.value.includes('~review-') ? 'Independent review' : 'Execution';
      const record = el('div', undefined, 'workflow-task-record');
      line(record, 'div', kind, 'small muted');
      if (row.task.createdAt) {
        const time = line(record, 'time', new Date(row.task.createdAt).toLocaleString(), 'small muted');
        time.dateTime = row.task.createdAt;
      }
      line(record, 'div', row.task.id.value, 'mono');
      line(record, 'span', row.task.state, 'badge');
      if (row.verdict) line(record, 'span', row.verdict, 'badge');
      const link = line(record, 'a', 'View task details', 'btn ghost small-btn');
      link.href = `/ui/plans/${encodeURIComponent(id)}/tasks/${encodeURIComponent(row.task.id.value)}`;
      if (row.report) {
        const report = el('details', undefined, 'workflow-task-report');
        report.dataset.taskReport = row.task.id.value;
        line(report, 'summary', 'View report', 'small muted');
        const content = line(report, 'pre', row.report, 'code task-report');
        const previous = reportStates.get(row.task.id.value);
        report.open = previous?.open || false;
        record.appendChild(report);
        if (previous?.open) requestAnimationFrame(() => { content.scrollTop = previous.scrollTop; });
      }
      return record;
    };
    let loading = false;
    let request = null;
    const load = async () => {
      if (loading) return;
      loading = true;
      request = new AbortController();
      const timeout = setTimeout(() => request?.abort(), 10000);
      try {
        const data = await api(`/plans/${encodeURIComponent(id)}`, undefined, { signal: request.signal, cache: 'no-store' }); plan = data.plan;
        reviewRecovery = data.reviewRecovery;
        const grants = data.repairAuthorizations || [];
        const pendingGrant = grants.find(grant => grant.status === 'PENDING' && grant.planVersion === plan.version
          && Date.parse(grant.expiresAt) > Date.now());
        detail.querySelector('[data-detail-goal]').textContent = plan.spec.goal;
        const latestReviews = plan.spec.steps.map(step => data.steps.filter(row =>
          row.task.id.value.startsWith(`${id}~${step.key}~review-`)).sort((a, b) => {
            const sequence = row => row.task.id.value.match(/~review-(\d+)(?:-resume-(\d+))?$/);
            const left = sequence(a), right = sequence(b);
            return Number(left?.[1] || 0) - Number(right?.[1] || 0)
              || Number(left?.[2] || 0) - Number(right?.[2] || 0);
          }).at(-1));
        const finalReview = latestReviews.at(-1);
        const activeWork = data.steps.some(row => ['READY', 'BLOCKED', 'CLAIMED', 'RUNNING'].includes(row.task.state));
        const exhausted = !activeWork && latestReviews.some(row => row
          && Number(row.task.id.value.match(/~review-(\d+)/)?.[1]) >= 3
          && row.task.state === 'COMPLETED' && row.verdict === 'REPAIR_REQUIRED')
          || data.steps.some(row => row.task.state === 'FAILED');
        const pendingControl = data.controls.find(control => control.status === 'PENDING_STOP');
        const attention = detail.querySelector('[data-plan-attention]');

        detail.querySelector('[data-detail-state]').textContent = data.humanOutcome?.status === 'APPLIED'
          ? data.humanOutcome.outcome === 'COMPLETE' ? 'COMPLETED_BY_HUMAN' : 'CANCELLED'
          : data.humanOutcome?.status === 'PENDING' ? 'WAITING_FOR_WORKER_STOP'
          : pendingGrant ? 'WAITING_FOR_RECOVERY_DECISION'
          : plan.state === 'CONFIRMED'
          ? pendingControl ? 'WAITING_FOR_WORKER_STOP' : finalReview?.verdict === 'PASS' ? 'COMPLETED' : exhausted ? 'NEEDS_ATTENTION' : 'IN_PROGRESS'
          : plan.state;
        const needsAttention = detail.querySelector('[data-detail-state]').textContent === 'NEEDS_ATTENTION';
        detail.dataset.planAttention = String(needsAttention);
        if (attention) attention.hidden = !needsAttention;
        const missionAlert = detail.querySelector('#plan-mission-alert');
        if (missionAlert) missionAlert.hidden = !needsAttention;
        detail.querySelector('[data-detail-source]').textContent = plan.spec.source;
        detail.querySelector('[data-detail-version]').textContent = plan.version;
        detail.querySelector('[data-detail-created-by]').textContent = plan.createdBy;
        detail.querySelector('[data-detail-audit]').textContent = `${plan.updatedBy} · ${plan.reason || 'No decision recorded'}`;
        detail.querySelector('[data-detail-time]').textContent = `${plan.createdAt} / ${plan.updatedAt}`;
        graph(detail.querySelector('[data-detail-graph]'), plan.spec.steps);
        const rows = detail.querySelector('[data-detail-steps]');
        const reportStates = new Map([...rows.querySelectorAll('[data-task-report]')].map(report =>
          [report.dataset.taskReport, { open: report.open, scrollTop: report.querySelector('pre').scrollTop }]));
        rows.replaceChildren();
        plan.spec.steps.forEach((step, index) => {
          const actual = data.steps.filter(row => row.task.id.value.startsWith(`${id}~${step.key}~`))
            .sort((a, b) => (Date.parse(b.task.createdAt) || 0) - (Date.parse(a.task.createdAt) || 0)
              || b.task.id.value.localeCompare(a.task.id.value));
          const tr = el('tr');
          [ `${step.key} / ${step.skillRef}`, `${step.instruction}\nModel: ${step.model || 'Worker choice'} · Effort: ${step.effort || 'Worker choice'}`,  step.dependsOn.join(', ') || 'Listed order',
            `${step.repositoryRef || 'No workspace'} / ${step.baseRevision || 'No fixed revision'}`
          ].forEach(value => line(tr, 'td', value));
          const resultCell = el('td');
          const skipped = data.controls.some(control => control.status === 'APPLIED' && control.action === 'JUMP'
            && index >= control.fromStep && index < control.targetStep);
          if (!actual.length) line(resultCell, 'div', skipped ? 'Skipped by manager decision' : 'Waiting for the previous step to pass review');
          if (actual.length) resultCell.appendChild(taskRecord(actual[0], reportStates));
          if (actual.length > 1) {
            const more = line(resultCell, 'button', 'View all records', 'btn ghost small-btn');
            more.type = 'button';
            more.setAttribute('aria-haspopup', 'dialog');
            more.setAttribute('aria-controls', 'plan-task-history');
            more.addEventListener('click', () => {
              historyOpener = more;
              detail.querySelector('[data-task-history-step]').textContent = step.key;
              historyBody.replaceChildren(...actual.map(row => taskRecord(row)));
              historyDialog.showModal();
            });
          }
          tr.appendChild(resultCell); rows.appendChild(tr);
        });
        const history = detail.querySelector('[data-control-history]'); history.replaceChildren();
        if (!data.controls.length && !data.humanOutcome) line(history, 'p', 'No decision recorded.');
        for (const control of data.controls) line(history, 'p',
          `${control.requestedAt} · ${control.actor} · ${control.action === 'STOP' ? 'Stop early' : `Jump from step ${control.fromStep + 1} to ${control.targetStep + 1}`} · ${control.status} · ${control.reason}`);
        if (data.humanOutcome) line(history, 'p',
          `${data.humanOutcome.decidedAt} · ${data.humanOutcome.actor} · ${data.humanOutcome.outcome} · ${data.humanOutcome.status} · ${data.humanOutcome.reason}`);
        const currentStep = Math.max(-1, ...data.steps.map(row => plan.spec.steps.findIndex(step =>
          row.task.id.value.startsWith(`${id}~${step.key}~`))));
        const target = detail.querySelector('[data-jump-target]');
        if (target) {
          const selectedTarget = target.value;
          target.replaceChildren();
          plan.spec.steps.forEach((step, index) => {
            if (index > currentStep) {
              const option = el('option', `${index + 1}. ${step.key}`); option.value = String(index);
              target.appendChild(option);
            }
          });
          if ([...target.options].some(option => option.value === selectedTarget)) target.value = selectedTarget;
        }
        const confirm = detail.querySelector('[data-confirm]'); if (confirm) confirm.hidden = plan.state !== 'DRAFT';
        const withdraw = detail.querySelector('[data-withdraw]'); if (withdraw) withdraw.hidden = plan.state !== 'CONFIRMED';
        const jump = detail.querySelector('[data-jump]'); if (jump) jump.hidden = plan.state !== 'CONFIRMED' || !!pendingControl || !target?.options.length;
        const stop = detail.querySelector('[data-stop-plan]'); if (stop) stop.hidden = plan.state !== 'CONFIRMED' || !!pendingControl || finalReview?.verdict === 'PASS';
        const complete = detail.querySelector('[data-complete-plan]'); if (complete) complete.hidden = plan.state !== 'CONFIRMED' || !!pendingControl || !!data.humanOutcome || finalReview?.verdict === 'PASS';
        const cancel = detail.querySelector('[data-cancel-plan]'); if (cancel) cancel.hidden = !['CONFIRMED', 'STOPPED'].includes(plan.state) || !!pendingControl || !!data.humanOutcome;
        const resolve = detail.querySelector('[data-resolve-stop]');
        if (resolve) resolve.hidden = !pendingControl || data.steps.some(row =>
          row.task.id.value === pendingControl.taskId && ['CLAIMED', 'RUNNING'].includes(row.task.state));
        const resume = detail.querySelector('[data-resume-review]');
        if (resume) resume.hidden = !reviewRecovery?.allowed || !!pendingGrant;
        const managerPhase = detail.querySelector('[data-manager-phase]');
        if (managerPhase) {
          managerPhase.textContent = detail.querySelector('[data-detail-state]').textContent;
          managerPhase.dataset.status = exhausted ? 'WAITING_APPROVAL' : activeWork ? 'RUNNING' : plan.state;
        }
        const guidance = detail.querySelector('[data-manager-guidance]');
        if (guidance) guidance.textContent = pendingGrant
          ? 'A decision Worker is assessing the fixed repair scope.'
          : exhausted ? 'Review needs changes. Authorize a bounded repair or request another review.'
          : activeWork ? 'Workers are active. Follow task progress before choosing another action.'
          : plan.state === 'DRAFT' ? 'Review your selected steps, then confirm the draft to start.'
          : 'Choose your next move. Every decision stays in the mission history.';
        const reviewCard = detail.querySelector('[data-review-recovery]');
        if (reviewCard) reviewCard.hidden = !reviewRecovery?.allowed || !!pendingGrant;
        const draftCard = detail.querySelector('[data-draft-action]');
        if (draftCard) draftCard.hidden = plan.state !== 'DRAFT';
        const repair = detail.querySelector('[data-repair-authorization]');
        if (repair) repair.hidden = !reviewRecovery?.allowed || !!pendingGrant;
        const delegate = detail.querySelector('[data-repair-delegate]');
        if (delegate) {
          const selected = delegate.value;
          delegate.replaceChildren();
          const human = line(delegate, 'option', 'Human approval now'); human.value = '';
          for (const workerId of data.decisionWorkers || []) {
            const option = line(delegate, 'option', workerId); option.value = workerId;
          }
          if (selected && !(data.decisionWorkers || []).includes(selected)) {
            const unavailable = line(delegate, 'option', selected); unavailable.value = selected; unavailable.disabled = true;
          }
          delegate.value = selected || '';
        }
        const grantHistory = detail.querySelector('[data-repair-authorizations]');
        if (grantHistory) {
          detail.querySelector('[data-repair-authorization-history]').hidden = !grants.length;
          const repairEmpty = detail.querySelector('[data-repair-history-empty]');
          if (repairEmpty) repairEmpty.hidden = !!grants.length;
          grantHistory.replaceChildren();
          for (const grant of grants) {
            const card = line(grantHistory, 'article', undefined, 'form-card top-gap');
            line(card, 'div', grant.id, 'mono');
            const grantStatus = grant.status !== 'PENDING' ? grant.status : Date.parse(grant.expiresAt) <= Date.now()
              ? 'EXPIRED' : grant.planVersion !== plan.version ? 'STALE' : 'PENDING';
            line(card, 'span', grantStatus, 'badge');
            line(card, 'p', `${grant.createdAt} · ${grant.grantedBy} · ${grant.delegateWorkerId || grant.grantedBy}`, 'small muted');
            line(card, 'pre', grant.scope, 'code task-report');
            line(card, 'p', grant.reason, 'small');
            if (grant.repairTaskId) {
              const link = line(card, 'a', 'View task details', 'btn ghost small-btn');
              link.href = `/ui/plans/${encodeURIComponent(id)}/tasks/${encodeURIComponent(grant.repairTaskId)}`;
            }
            if (data.repairDecisionReports?.[grant.id]) {
              const report = line(card, 'details');
              line(report, 'summary', 'Decision report');
              line(report, 'pre', data.repairDecisionReports[grant.id], 'code task-report');
            }
            if (grant.status === 'PENDING' && detail.dataset.canOperate === 'true') {
              const revoke = line(card, 'button', 'Revoke authorization', 'btn ghost small-btn'); revoke.type = 'button';
              revoke.addEventListener('click', async () => {
                if (!await window.aiwConfirm('Revoke this unused repair authorization? No new repair will be started.')) return;
                try { await api(`/plans/${encodeURIComponent(id)}/repair-authorizations/${encodeURIComponent(grant.id)}/revoke`, {}); await load(); }
                catch (error) {
                  actionStatus.textContent = error.message;
                  detail.dispatchEvent(new CustomEvent('aiw:open-plan-window', { detail: 'mission' }));
                }
              });
            }
          }
        }
        detail.querySelector('[data-detail-error]').textContent = '';
        if (typeof CustomEvent === 'function') detail.dispatchEvent(new CustomEvent('aiw:plan-snapshot', {
          detail: { version: plan.version, tasks: data.steps.map(row => `${row.task.id.value}:${row.task.state}:${row.task.version}`).join('|') }
        }));
        if (refreshStatus) refreshStatus.replaceChildren(el('span', 'Updates every 5 seconds'),
          document.createTextNode(' · '), el('time', new Date().toLocaleTimeString()));
      } catch (error) {
        detail.querySelector('[data-detail-error]').textContent = error.name === 'AbortError'
          ? 'Status request timed out or was interrupted. The next update will retry.' : error.message;
        if (refreshStatus) refreshStatus.textContent = 'Status refresh failed. Previous data remains visible.';
      } finally { clearTimeout(timeout); request = null; loading = false; }
    };
    detail.querySelector('[data-copy]')?.addEventListener('click', () => { location.href = `/ui/plans/new?copy=${encodeURIComponent(id)}`; });
    detail.querySelector('[data-confirm]')?.addEventListener('click', async () => {
      if (!plan || !await window.aiwConfirm(`Confirm ${plan.spec.steps.length} selected steps at version ${plan.version}? This creates Worker tasks.`)) return;
      try { await api(`/plans/${encodeURIComponent(id)}/confirm`, { version: plan.version });
        actionStatus.textContent = 'Confirmed selected graph.'; await load();
      } catch (error) { actionStatus.textContent = `Confirmation failed: ${error.message}`; }
    });
    detail.querySelector('[data-withdraw]')?.addEventListener('click', async () => {
      const reason = detail.querySelector('[data-withdraw-reason]').value.trim();
      if (!reason) { actionStatus.textContent = 'Enter a withdrawal reason.'; return; }
      try { await api(`/plans/${encodeURIComponent(id)}/withdraw`, { version: plan.version, reason });
        actionStatus.textContent = 'Inactive work withdrawn; completed results remain recorded.'; await load();
      } catch (error) { actionStatus.textContent = `Withdrawal failed; plan unchanged: ${error.message}`; }
    });
    detail.querySelector('[data-jump]')?.addEventListener('click', async () => {
      const reason = detail.querySelector('[data-control-reason]').value.trim();
      const targetStep = Number(detail.querySelector('[data-jump-target]').value);
      if (!reason) { actionStatus.textContent = 'Enter a decision reason.'; return; }
      if (!await window.aiwConfirm(`Skip ahead to step ${targetStep + 1}? Existing unfinished work will be stopped or cancelled.`, { danger: true })) return;
      try { await api(`/plans/${encodeURIComponent(id)}/jump`, { version: plan.version, targetStep, reason });
        actionStatus.textContent = 'Jump recorded. Waiting for Worker acknowledgement if work was active.'; await load();
      } catch (error) { actionStatus.textContent = `Jump failed: ${error.message}`; }
    });
    detail.querySelector('[data-stop-plan]')?.addEventListener('click', async () => {
      const reason = detail.querySelector('[data-control-reason]').value.trim();
      if (!reason) { actionStatus.textContent = 'Enter a decision reason.'; return; }
      if (!await window.aiwConfirm('Stop this plan early? Completed results remain; unfinished work will be stopped or cancelled.', { danger: true })) return;
      try { await api(`/plans/${encodeURIComponent(id)}/stop`, { version: plan.version, reason });
        actionStatus.textContent = 'Stop recorded. Waiting for Worker acknowledgement if work was active.'; await load();
      } catch (error) { actionStatus.textContent = `Stop failed: ${error.message}`; }
    });
    detail.querySelector('[data-complete-plan]')?.addEventListener('click', async () => {
      const reason = detail.querySelector('[data-control-reason]').value.trim();
      if (!reason) { actionStatus.textContent = 'Enter a decision reason.'; return; }
      if (!await window.aiwConfirm('Complete by human decision? Unfinished Worker tasks will be stopped or cancelled.')) return;
      try { await api(`/plans/${encodeURIComponent(id)}/complete`, { version: plan.version, reason });
        actionStatus.textContent = 'Human completion recorded. Waiting for Worker acknowledgement if work was active.'; await load();
      } catch (error) { actionStatus.textContent = `Completion failed: ${error.message}`; }
    });
    detail.querySelector('[data-cancel-plan]')?.addEventListener('click', async () => {
      const reason = detail.querySelector('[data-control-reason]').value.trim();
      if (!reason) { actionStatus.textContent = 'Enter a decision reason.'; return; }
      if (!await window.aiwConfirm('Cancel this plan and delete its verified local isolated Git branches? Stop active Workers first.', { danger: true })) return;
      try { await api(`/plans/${encodeURIComponent(id)}/cancel`, { version: plan.version, reason });
        actionStatus.textContent = 'Plan cancelled; verified local branches discarded.'; await load();
      } catch (error) { actionStatus.textContent = `Cancellation requires manual intervention: ${error.message}`; }
    });
    detail.querySelector('[data-resolve-stop]')?.addEventListener('click', async () => {
      try { await api(`/plans/${encodeURIComponent(id)}/resolve-stop`, { version: plan.version });
        actionStatus.textContent = 'Stale stop request marked as conflict. Review the tasks, then submit a new decision.'; await load();
      } catch (error) { actionStatus.textContent = `Resolution failed: ${error.message}`; }
    });
    detail.querySelector('[data-plan-refresh]')?.addEventListener('click', load);
    detail.addEventListener?.('aiw:refresh-plan', load);
    detail.querySelector('[data-authorize-repair]')?.addEventListener('click', async () => {
      if (repairSubmitting) return;
      const button = detail.querySelector('[data-authorize-repair]');
      const reasonInput = detail.querySelector('[data-repair-reason]') || detail.querySelector('[data-control-reason]');
      const scopeInput = detail.querySelector('[data-repair-scope]');
      const status = detail.querySelector('[data-repair-status]');
      const feedback = (message, tone = 'info') => {
        actionStatus.textContent = message; actionStatus.dataset.tone = tone;
        if (status) { status.hidden = false; status.textContent = message; status.dataset.tone = tone; }
      };
      if (!reviewRecovery?.allowed) {
        feedback(reviewRecovery?.blockedReason || 'Repair is unavailable. Refresh the plan to check its current state.', 'error');
        actionStatus.scrollIntoView?.({ block: 'nearest' });
        return;
      }
      const reason = reasonInput.value.trim() || detail.querySelector('[data-control-reason]')?.value.trim();
      const scope = scopeInput.value.trim();
      const delegateWorkerId = detail.querySelector('[data-repair-delegate]').value || null;
      reasonInput.removeAttribute('aria-invalid'); scopeInput.removeAttribute('aria-invalid');
      if (!reason || !scope) {
        const missing = !reason ? reasonInput : scopeInput;
        missing.setAttribute('aria-invalid', 'true');
        feedback(!reason ? 'Enter a repair decision reason.' : 'Enter a fixed repair scope.', 'error');
        missing.focus();
        return;
      }
      const snapshot = { ...reviewRecovery };
      repairSubmitting = true; button.disabled = true; button.setAttribute('aria-busy', 'true');
      button.textContent = 'Waiting for confirmation…';
      let timeout;
      try {
        if (!await window.aiwConfirm('Authorize one repair round within the entered scope? An assigned AI cannot change the scope or add rounds.')) {
          feedback('Repair authorization cancelled.');
          return;
        }
        button.textContent = 'Submitting authorization…';
        feedback('Submitting authorization…');
        const controller = new AbortController();
        timeout = setTimeout(() => controller.abort(), 15000);
        const grant = await api(`/plans/${encodeURIComponent(id)}/repair-authorizations`, {
          planVersion: snapshot.planVersion, taskId: snapshot.taskId, taskVersion: snapshot.taskVersion,
          reason, scope, delegateWorkerId, warningConfirmed: true,
        }, { signal: controller.signal });
        clearTimeout(timeout);
        feedback(grant.status === 'STALE' || grant.status === 'EXPIRED'
          ? 'Authorization was not used because the plan changed or the grant expired. Check authorization history.'
          : 'One-round repair authorization recorded.', grant.status === 'STALE' || grant.status === 'EXPIRED' ? 'error' : 'success');
        await load();
        actionStatus.scrollIntoView?.({ block: 'nearest' });
      } catch (error) {
        feedback(error.name === 'AbortError'
          ? 'Authorization request timed out. Refresh and check authorization history before submitting again.'
          : `Repair authorization failed: ${error.message}`, 'error');
      } finally {
        clearTimeout(timeout); repairSubmitting = false;
        button.disabled = false; button.removeAttribute('aria-busy'); button.textContent = 'Authorize continued repair';
      }
    });
    detail.querySelector('[data-resume-review]')?.addEventListener('click', async () => {
      if (!reviewRecovery?.allowed) return;
      const reasonInput = detail.querySelector('[data-review-reason]') || detail.querySelector('[data-control-reason]');
      const reason = reasonInput.value.trim();
      if (!reason) { actionStatus.textContent = 'Enter a decision reason.'; reasonInput.focus(); return; }
      const snapshot = reviewRecovery;
      if (!await window.aiwConfirm('Review the current implementation again with the accepted upstream outputs? Prior attempts remain. Coding is not rerun.')) return;
      try {
        await api(`/plans/${encodeURIComponent(id)}/resume-review`, {
          planVersion: snapshot.planVersion, taskId: snapshot.taskId, taskVersion: snapshot.taskVersion, reason, warningConfirmed: true,
        });
        actionStatus.textContent = 'New independent review queued. Prior attempts remain unchanged.';
        await load();
      } catch (error) { actionStatus.textContent = `Review recovery failed: ${error.message}`; }
    });
    const refresh = () => {
      const selection = window.getSelection();
      const paused = document.hidden || pendingWrites > 0 || detail.dataset.windowDragging === 'true' || document.querySelector('dialog[open]')
        || document.querySelector('[data-plan-window]:not([hidden]) details[open]')
        || detail.contains(document.activeElement) && document.activeElement?.matches('input, textarea, select')
        || selection && !selection.isCollapsed;
      if (paused) {
        if (refreshStatus) refreshStatus.textContent = 'Status updates paused while editing, confirming or selecting text.';
        return;
      }
      void load();
    };
    let timer = null;
    const startRefresh = () => { if (timer === null) timer = setInterval(refresh, 5000); };
    startRefresh();
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
    window.addEventListener('pagehide', () => {
      clearInterval(timer); timer = null;
      request?.abort();
    });
    window.addEventListener('pageshow', event => {
      if (event.persisted) { startRefresh(); refresh(); }
    });
    load();
  }
})();
