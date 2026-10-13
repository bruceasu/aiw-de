(() => {
  const editor = document.querySelector('[data-workflow-editor]');
  const detail = document.querySelector('[data-workflow-detail]');
  if (!editor && !detail) return;
  const root = editor || detail;
  const csrf = root.dataset.csrf;
  const api = async (path, body) => {
    const response = await fetch(`/ui/plan-editor${path}${body === undefined ? '' : `?_csrf=${encodeURIComponent(csrf)}`}`, {
      method: body === undefined ? 'GET' : 'POST',
      headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (!response.ok) throw new Error((await response.text()).slice(0, 500) || `HTTP ${response.status}`);
    return response.json();
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
        const plan = await api('/drafts', payload);
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
          state.textContent = `${snapshot.id} · ${snapshot.stale ? 'stale · ' : ''}${reply.state}${reply.error ? ` · ${reply.error}` : ''}`;
          if (!snapshot.stale && snapshot === currentRequest) await poll();
        } catch (error) { state.textContent = `${snapshot.id} · check failed: ${error.message}`; }
      });
      const withdraw = el('button', 'Withdraw if queued', 'btn ghost small-btn'); withdraw.type = 'button';
      withdraw.addEventListener('click', async () => {
        try {
          const reply = await api(`/recommendations/${encodeURIComponent(snapshot.id)}/withdraw`, {});
          state.textContent = `${snapshot.id} · ${reply.state}`;
        } catch (error) { state.textContent = `${snapshot.id} · cannot withdraw: ${error.message}`; }
      });
      row.append(check, withdraw); history.prepend(row);
    };
    const poll = async () => {
      if (!currentRequest) return;
      const snapshot = currentRequest;
      if (!snapshot.id || snapshot.stale) return;
      try {
        const reply = await api(`/recommendations/${encodeURIComponent(snapshot.id)}`);
        if (snapshot !== currentRequest || snapshot.stale || snapshot.version !== inputVersion || snapshot.goal !== goal.value) return;
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
      if (goal.value.length > 8000) { status.textContent = 'AI recommendation accepts up to 8000 characters; edit the goal or continue manually.'; return; }
      clearInterval(timer); recommendation = null; result.hidden = true;
      editor.querySelector('[data-refresh-recommend]').hidden = true;
      editor.querySelector('[data-withdraw-recommend]').hidden = true;
      const button = editor.querySelector('[data-recommend]'); button.disabled = true;
      const snapshot = { key: crypto.randomUUID(), version: inputVersion, goal: goal.value, id: null, stale: false };
      currentRequest = snapshot; status.textContent = 'Requesting one bounded analysis task…';
      try {
        const reply = await api('/recommendations', { requestKey: snapshot.key, inputVersion: snapshot.version, goal: snapshot.goal });
        snapshot.id = reply.id;
        snapshot.stale = snapshot.stale || snapshot !== currentRequest || snapshot.version !== inputVersion || snapshot.goal !== goal.value;
        exposeRequest(snapshot);
        if (snapshot.stale) {
          if (snapshot === currentRequest) status.textContent = `Previous analysis ${snapshot.id} is stale. Check or withdraw it below.`;
          return;
        }
        editor.querySelector('[data-withdraw-recommend]').hidden = false;
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
    api('/catalog').then(data => {
      catalog = data;
      renderCapabilityGuide();
      const select = editor.querySelector('[data-template]');
      data.templates.forEach(t => { const option = el('option', `${t.title} — ${t.use}`); option.value = t.id; select.appendChild(option); });
      if (!data.recommendationAvailable) {
        editor.querySelector('[data-recommend]').disabled = true;
        status.textContent = 'AI recommendation is not configured. Templates and manual editing are available.';
      }
      const copy = new URLSearchParams(location.search).get('copy');
      if (copy) return api(`/plans/${encodeURIComponent(copy)}`).then(data => {
        goal.value = data.plan.spec.goal; source = data.plan.spec.source;
        steps = data.plan.spec.steps.map(stepCopy); renderSteps();
      });
      renderSteps();
    }).catch(error => { notice.textContent = `Editor data unavailable: ${error.message}`; });
  }

  if (detail) {
    const id = detail.dataset.id;
    let plan;
    const actionStatus = detail.querySelector('[data-action-status]');
    const load = async () => {
      try {
        const data = await api(`/plans/${encodeURIComponent(id)}`); plan = data.plan;
        detail.querySelector('[data-detail-goal]').textContent = plan.spec.goal;
        const finalStep = plan.spec.steps.at(-1);
        const finalReview = data.steps.filter(row => row.task.id.value.startsWith(`${id}~${finalStep.key}~review-`))
          .sort((a, b) => a.task.id.value.localeCompare(b.task.id.value)).at(-1);
        const exhausted = data.steps.some(row => (row.task.id.value.endsWith('~review-3') &&
          row.task.state === 'COMPLETED' && row.verdict === 'REPAIR_REQUIRED') || row.task.state === 'FAILED');
        const pendingControl = data.controls.find(control => control.status === 'PENDING_STOP');
        detail.querySelector('[data-detail-state]').textContent = data.humanOutcome?.status === 'APPLIED'
          ? data.humanOutcome.outcome === 'COMPLETE' ? 'COMPLETED_BY_HUMAN' : 'CANCELLED'
          : data.humanOutcome?.status === 'PENDING' ? 'WAITING_FOR_WORKER_STOP'
          : plan.state === 'CONFIRMED'
          ? pendingControl ? 'WAITING_FOR_WORKER_STOP' : finalReview?.verdict === 'PASS' ? 'COMPLETED' : exhausted ? 'NEEDS_ATTENTION' : 'IN_PROGRESS'
          : plan.state;
        detail.querySelector('[data-detail-source]').textContent = plan.spec.source;
        detail.querySelector('[data-detail-version]').textContent = plan.version;
        detail.querySelector('[data-detail-created-by]').textContent = plan.createdBy;
        detail.querySelector('[data-detail-audit]').textContent = `${plan.updatedBy} · ${plan.reason || 'No decision recorded'}`;
        detail.querySelector('[data-detail-time]').textContent = `${plan.createdAt} / ${plan.updatedAt}`;
        graph(detail.querySelector('[data-detail-graph]'), plan.spec.steps);
        const rows = detail.querySelector('[data-detail-steps]'); rows.replaceChildren();
        plan.spec.steps.forEach((step, index) => {
          const actual = data.steps.filter(row => row.task.id.value.startsWith(`${id}~${step.key}~`))
            .sort((a, b) => a.task.id.value.localeCompare(b.task.id.value));
          const tr = el('tr');
          [ `${step.key} / ${step.skillRef}`, step.instruction, step.dependsOn.join(', ') || 'Listed order',
            `${step.repositoryRef || 'No workspace'} / ${step.baseRevision || 'No fixed revision'}`
          ].forEach(value => line(tr, 'td', value));
          const resultCell = el('td');
          const skipped = data.controls.some(control => control.status === 'APPLIED' && control.action === 'JUMP'
            && index >= control.fromStep && index < control.targetStep);
          if (!actual.length) line(resultCell, 'div', skipped ? 'Skipped by manager decision' : 'Waiting for the previous step to pass review');
          for (const row of actual) {
            const kind = row.task.id.value.includes('~review-') ? 'Independent review' : 'Execution';
            line(resultCell, 'div', `${kind} · ${row.task.id.value} · ${row.task.state}${row.verdict ? ` · ${row.verdict}` : ''}`);
            if (row.report) line(resultCell, 'p', row.report, 'small muted');
          }
          tr.appendChild(resultCell); rows.appendChild(tr);
        });
        const history = detail.querySelector('[data-control-history]'); history.replaceChildren();
        if (!data.controls.length) line(history, 'p', 'No step override recorded.');
        for (const control of data.controls) line(history, 'p',
          `${control.requestedAt} · ${control.actor} · ${control.action === 'STOP' ? 'Stop early' : `Jump from step ${control.fromStep + 1} to ${control.targetStep + 1}`} · ${control.status} · ${control.reason}`);
        if (data.humanOutcome) line(history, 'p',
          `${data.humanOutcome.decidedAt} · ${data.humanOutcome.actor} · ${data.humanOutcome.outcome} · ${data.humanOutcome.status} · ${data.humanOutcome.reason}`);
        const currentStep = Math.max(-1, ...data.steps.map(row => plan.spec.steps.findIndex(step =>
          row.task.id.value.startsWith(`${id}~${step.key}~`))));
        const target = detail.querySelector('[data-jump-target]');
        if (target) {
          target.replaceChildren();
          plan.spec.steps.forEach((step, index) => {
            if (index > currentStep) {
              const option = el('option', `${index + 1}. ${step.key}`); option.value = String(index);
              target.appendChild(option);
            }
          });
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
      } catch (error) { detail.querySelector('[data-detail-error]').textContent = error.message; }
    };
    detail.querySelector('[data-copy]')?.addEventListener('click', () => { location.href = `/ui/plans/new?copy=${encodeURIComponent(id)}`; });
    detail.querySelector('[data-confirm]')?.addEventListener('click', async () => {
      if (!plan || !window.confirm(`Confirm ${plan.spec.steps.length} selected steps at version ${plan.version}? This creates Worker tasks.`)) return;
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
      if (!window.confirm(`Skip ahead to step ${targetStep + 1}? Existing unfinished work will be stopped or cancelled.`)) return;
      try { await api(`/plans/${encodeURIComponent(id)}/jump`, { version: plan.version, targetStep, reason });
        actionStatus.textContent = 'Jump recorded. Waiting for Worker acknowledgement if work was active.'; await load();
      } catch (error) { actionStatus.textContent = `Jump failed: ${error.message}`; }
    });
    detail.querySelector('[data-stop-plan]')?.addEventListener('click', async () => {
      const reason = detail.querySelector('[data-control-reason]').value.trim();
      if (!reason) { actionStatus.textContent = 'Enter a decision reason.'; return; }
      if (!window.confirm('Stop this plan early? Completed results remain; unfinished work will be stopped or cancelled.')) return;
      try { await api(`/plans/${encodeURIComponent(id)}/stop`, { version: plan.version, reason });
        actionStatus.textContent = 'Stop recorded. Waiting for Worker acknowledgement if work was active.'; await load();
      } catch (error) { actionStatus.textContent = `Stop failed: ${error.message}`; }
    });
    detail.querySelector('[data-complete-plan]')?.addEventListener('click', async () => {
      const reason = detail.querySelector('[data-control-reason]').value.trim();
      if (!reason) { actionStatus.textContent = 'Enter a decision reason.'; return; }
      if (!window.confirm('Complete by human decision? Unfinished Worker tasks will be stopped or cancelled.')) return;
      try { await api(`/plans/${encodeURIComponent(id)}/complete`, { version: plan.version, reason });
        actionStatus.textContent = 'Human completion recorded. Waiting for Worker acknowledgement if work was active.'; await load();
      } catch (error) { actionStatus.textContent = `Completion failed: ${error.message}`; }
    });
    detail.querySelector('[data-cancel-plan]')?.addEventListener('click', async () => {
      const reason = detail.querySelector('[data-control-reason]').value.trim();
      if (!reason) { actionStatus.textContent = 'Enter a decision reason.'; return; }
      if (!window.confirm('Cancel this plan and delete its verified local isolated Git branches? Stop active Workers first.')) return;
      try { await api(`/plans/${encodeURIComponent(id)}/cancel`, { version: plan.version, reason });
        actionStatus.textContent = 'Plan cancelled; verified local branches discarded.'; await load();
      } catch (error) { actionStatus.textContent = `Cancellation requires manual intervention: ${error.message}`; }
    });
    detail.querySelector('[data-resolve-stop]')?.addEventListener('click', async () => {
      try { await api(`/plans/${encodeURIComponent(id)}/resolve-stop`, { version: plan.version });
        actionStatus.textContent = 'Stale stop request marked as conflict. Review the tasks, then submit a new decision.'; await load();
      } catch (error) { actionStatus.textContent = `Resolution failed: ${error.message}`; }
    });
    load();
  }
})();
