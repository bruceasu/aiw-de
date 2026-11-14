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
      const response = await fetch(`/api/r1/plan-editor${path}${body === undefined ? '' : `?_csrf=${encodeURIComponent(csrf)}`}`, {
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
  const reviewNames={SELF:'Self-check',MILESTONE:'Stage milestone review',INDEPENDENT:'Independent step review'};
  const reviewMode=step=>step.reviewMode||'INDEPENDENT';
  const reviewRisk=step=>step.reviewRisk||'UNKNOWN';
  const graph = (host, steps) => {
    host.replaceChildren();
    for (const [index, step] of steps.entries()) {
      const row = el('div', undefined, 'workflow-node');
      line(row, 'strong', `${index + 1}. ${step.key || '(unnamed)'}`);
      line(row, 'span', step.dependsOn?.length ? ` ← ${step.dependsOn.join(', ')}` : ' · start', 'muted');
      line(row,'span',' · '+reviewNames[reviewMode(step)],'muted');
      host.appendChild(row);
    }
  };
  const skillNames = value => value.split(',').map(x => x.trim()).filter(Boolean);
  const stepCopy = step => ({ ...step, reviewMode:reviewMode(step),reviewRisk:reviewRisk(step),requiredSkills: [...(step.requiredSkills || [])],
    preferredSkills: [...(step.preferredSkills || [])], dependsOn: [...(step.dependsOn || [])] });
  const blank = () => ({ key: '', requiredSkills: ['requirement-analysis'], preferredSkills: [],
    skillRef: 'software-requirements-analyst', instruction: '', repositoryRef: null, baseRevision: null, dependsOn: [],reviewMode:'INDEPENDENT',reviewRisk:'UNKNOWN' });

  if (detail) {
    const id = detail.dataset.id;
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
    let executionSignature = '';
    const renderConfiguredExecution = history => {
      const section = detail.querySelector('[data-configured-execution]');
      if (!section) return;
      section.hidden = !history || (!history.tasks.length && !history.preparations.length);
      if (section.hidden) return;
      const body = section.querySelector('[data-configured-history]');
      const signature = JSON.stringify(history);
      // Keep expanded records stable while the user reads; refresh them on the next collapsed poll.
      if (signature === executionSignature || body.querySelector('details[open]')) return;
      executionSignature = signature;
      body.replaceChildren();
      const field = (host, label, value) => {
        line(host, 'dt', label);
        line(host, 'dd', value || 'Not recorded', 'mono');
      };
      const report = (host, text) => {
        if (!text) return;
        try {
          const data = JSON.parse(text);
          if (!data || typeof data !== 'object') throw new Error('Plain report');
          if (data.outcome) line(host, 'p', data.outcome, 'badge');
          line(host, 'p', data.summary || text);
          for (const [key, label] of [['checksRun', 'Checks performed'], ['checksNotRun', 'Checks not performed'], ['blockers', 'Blockers']]) {
            if (Array.isArray(data[key]) && data[key].length) {
              line(host, 'h4', label);
              const list = line(host, 'ul');
              data[key].forEach(item => line(list, 'li', item));
            }
          }
        } catch (_) { line(host, 'pre', text, 'code task-report'); }
      };
      line(body, 'h3', 'Factory preparation history');
      for (const preparation of history.preparations) {
        const record = line(body, 'details', undefined, 'form-card top-gap');
        line(record, 'summary', `${preparation.time ? new Date(preparation.time).toLocaleString() : ''} · ${preparation.state} · ${preparation.errorCode || preparation.repository}`);
        const fields = line(record, 'dl', undefined, 'kv');
        field(fields, 'Preparation', preparation.id);
        field(fields, 'Task', preparation.taskId);
        field(fields, 'Worker role', preparation.role);
        field(fields, 'Generation', preparation.generation);
        (preparation.missing || []).forEach(reason => line(record, 'p', reason, 'callout warn-callout'));
        line(record, 'pre', preparation.instruction, 'code task-report');
      }
      line(body, 'h3', 'Actual Worker execution');
      for (const task of history.tasks) {
        const record = line(body, 'details', undefined, 'form-card top-gap');
        line(record, 'summary', `${new Date(task.createdAt).toLocaleString()} · ${task.state} · ${task.id}`);
        line(record, 'pre', task.instruction, 'code task-report');
        for (const attempt of task.attempts) {
          const attemptCard = line(record, 'article', undefined, 'top-gap');
          const fields = line(attemptCard, 'dl', undefined, 'kv');
          for (const [label, value] of [['Attempt', attempt.number], ['Worker', attempt.worker], ['State', attempt.state],
            ['Started', attempt.startedAt], ['Finished', attempt.finishedAt], ['Failure code', attempt.failureCode],
            ['Recorded verdict', attempt.verdict], ['Base revision', attempt.baseRevision], ['Result revision', attempt.resultRevision],
            ['Changed files', attempt.changedFiles], ['Result branch', attempt.branch], ['Result workspace', attempt.workspace]]) field(fields, label, value);
          report(attemptCard, attempt.report);
          const operations = history.operations.filter(operation => operation.attemptId === attempt.id);
          if (operations.length) {
            const wrap = line(attemptCard, 'div', undefined, 'table-wrap');
            const table = line(wrap, 'table');
            const head = line(line(table, 'thead'), 'tr');
            ['Started', 'Finished', 'Tool', 'Resource', 'State', 'Evidence reference'].forEach(label => line(head, 'th', label));
            const rows = line(table, 'tbody');
            for (const operation of operations) {
              const row = line(rows, 'tr');
              [operation.startedAt, operation.finishedAt, operation.tool, operation.resource, operation.state, operation.evidenceRef]
                .forEach(value => line(row, 'td', value || 'Not recorded'));
            }
          } else line(attemptCard, 'p', 'No tool operations recorded.', 'muted');
        }
      }
      if (history.operationsTruncated) line(body, 'p', 'Only the latest 500 tool operations are shown.', 'callout');
    };
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
    const renderPlanReviews = data => {
      const host = detail.querySelector('[data-plan-review-list]');
      if (!host) return;
      const reviews = data.steps.filter(row => row.task.id.value.includes('~review-'))
        .sort((a, b) => (Date.parse(b.task.createdAt) || 0) - (Date.parse(a.task.createdAt) || 0));
      host.replaceChildren();
      if (reviews.length) {
        for (const row of reviews) {
          const card = el('article', undefined, 'form-card top-gap');
          line(card, 'h3', row.stepKey ? `Review · ${row.stepKey}` : 'Independent review');
          card.appendChild(taskRecord(row));
          host.appendChild(card);
        }
        return;
      }
      const executions = data.steps.filter(row => !row.task.id.value.includes('~review-'));
      if (!executions.length) {
        line(host, 'p', 'No execution or independent review task has been recorded yet.', 'muted');
        return;
      }
      line(host, 'p', 'No independent review task has been recorded for these executions. Open a task to inspect the Worker report and deliverables.', 'callout');
      for (const row of executions) {
        const card = el('article', undefined, 'form-card top-gap');
        line(card, 'h3', row.stepKey ? `Review target · ${row.stepKey}` : 'Review target');
        line(card, 'span', 'Independent review not recorded', 'badge');
        card.appendChild(taskRecord(row));
        host.appendChild(card);
      }
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
        detail.querySelector('[data-detail-id]').textContent = id;
        renderPlanReviews(data);
        const configured = data.configuredExecution;
        const hasConfigured = !!configured && (configured.tasks.length > 0 || configured.preparations.length > 0);
        const formalFactory = !!data.factoryPlan;
        const configuredOnly = formalFactory || hasConfigured && !data.steps.length;
        const startReview = detail.querySelector('[data-factory-review-start]');
        const previousReview = data.steps.find(row => row.task.id.value === data.factoryReview?.reviewTaskId);
        const canRequestReview = !data.factoryReview || ['WAITING', 'CHANGES_REQUIRED'].includes(data.factoryReview.state)
          || data.factoryReview.state === 'PENDING' && ['FAILED', 'CANCELLED'].includes(previousReview?.task.state);
        if (startReview) startReview.hidden = !formalFactory || !['CONFIRMED', 'COMPLETED'].includes(plan.state)
          || !canRequestReview
          || !data.steps.some(row => row.stepKey === 'execute' && row.task.state === 'COMPLETED' && row.verdict === 'PASS');
        const legacyDraft = !formalFactory && configuredOnly;
        renderConfiguredExecution(configured);
        const originalState = detail.querySelector('[data-original-graph-state]');
        if (originalState) originalState.textContent = data.factoryPlan?.originalDraft.state || plan.state;
        const originalReason = detail.querySelector('[data-original-graph-reason]');
        if (originalReason) originalReason.textContent = data.factoryPlan?.originalDraft.reason || plan.reason || 'No decision recorded';
        const draftSteps = detail.querySelector('[data-original-draft-steps]');
        if (draftSteps && draftSteps.dataset.executionMode !== String(legacyDraft)) {
          draftSteps.open = !legacyDraft;
          draftSteps.dataset.executionMode = String(legacyDraft);
        }
        const draftHeading = detail.querySelector('[data-draft-steps-title]');
        if (draftHeading) draftHeading.textContent = formalFactory ? 'Confirmed execution plan' : legacyDraft ? 'Original draft steps (not executed)' : 'Selected plan';
        const reviewPolicy = detail.querySelector('[data-native-review-policy]');
        if (reviewPolicy) reviewPolicy.hidden = formalFactory;
        const originalNote = detail.querySelector('[data-original-draft-superseded]');
        if (originalNote) originalNote.hidden = !formalFactory;
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
        const failedTasks = data.steps.filter(row => row.task.state === 'FAILED');
        const exhausted = !activeWork && latestReviews.some(row => row
          && Number(row.task.id.value.match(/~review-(\d+)/)?.[1]) >= 3
          && row.task.state === 'COMPLETED' && row.verdict === 'REPAIR_REQUIRED')
          || failedTasks.length > 0;
        const pendingControl = data.controls.find(control => control.status === 'PENDING_STOP');
        const attention = detail.querySelector('[data-plan-attention]');

        detail.querySelector('[data-detail-state]').textContent = data.gitDelivery && !['NOT_REQUIRED', 'DELIVERED'].includes(data.gitDelivery.state)
          ? data.gitDelivery.state : data.humanOutcome?.status === 'APPLIED'
          ? data.humanOutcome.outcome === 'COMPLETE' ? 'COMPLETED_BY_HUMAN' : 'CANCELLED'
          : data.humanOutcome?.status === 'PENDING' ? 'WAITING_FOR_WORKER_STOP'
          : pendingGrant ? 'WAITING_FOR_RECOVERY_DECISION'
          : configuredOnly ? data.displayState || configured.state
          : plan.state === 'CONFIRMED'
          ? pendingControl ? 'WAITING_FOR_WORKER_STOP' : finalReview?.verdict === 'PASS' ? 'COMPLETED' : exhausted ? 'NEEDS_ATTENTION' : 'IN_PROGRESS'
          : plan.state;
        detail.querySelector('[data-detail-state]').dataset.status = detail.querySelector('[data-detail-state]').textContent;
        const deliveryPanel = detail.querySelector('[data-git-delivery]');
        if (deliveryPanel) {
          deliveryPanel.hidden = !data.gitDelivery?.items.length;
          const items = deliveryPanel.querySelector('[data-git-delivery-items]');
          items.replaceChildren();
          for (const item of data.gitDelivery?.items || []) {
            const row = line(items, 'article', undefined, 'form-card top-gap');
            line(row, 'strong', item.repositoryRef).setAttribute('data-i18n-skip', '');
            line(row, 'p', item.resultRevision, 'mono').setAttribute('data-i18n-skip', '');
            line(row, 'p', item.files.join(', '), 'small').setAttribute('data-i18n-skip', '');
            line(row, 'span', item.state, 'badge');
            line(row, 'p', item.message, 'help');
            if (['AWAITING_MERGE', 'BLOCKED'].includes(item.state) && detail.dataset.canOperate === 'true') {
              const button = line(row, 'button', 'Merge locally', 'btn primary');
              button.type = 'button';
              button.addEventListener('click', async () => {
                const feedback = deliveryPanel.querySelector('[data-git-delivery-status]');
                button.disabled = true;
                try {
                  const preview = await api(`/plans/${encodeURIComponent(id)}/git-delivery/preview?repositoryRef=${encodeURIComponent(item.repositoryRef)}`);
                  if (preview.blockedReason) { feedback.textContent = preview.blockedReason; return; }
                  await api(`/plans/${encodeURIComponent(id)}/git-delivery/merge`, {
                    planVersion: preview.planVersion, repositoryRef: preview.repositoryRef,
                    repositoryPath: preview.repositoryPath,
                    resultRevision: preview.resultRevision, targetBranch: preview.targetBranch,
                    beforeRevision: preview.beforeRevision, requestId: crypto.randomUUID()
                  });
                  feedback.textContent = '';
                  await load();
                } catch (error) { feedback.textContent = error.message; }
                finally { button.disabled = false; }
              });
            }
          }
        }
        const needsAttention = ['NEEDS_ATTENTION', 'DELEGATED_NEEDS_ATTENTION'].includes(detail.querySelector('[data-detail-state]').textContent);
        detail.dataset.planAttention = String(needsAttention);
        if (attention) attention.hidden = !needsAttention;
        const missionAlert = detail.querySelector('#plan-mission-alert');
        if (missionAlert) missionAlert.hidden = !needsAttention;
        detail.querySelector('[data-detail-source]').textContent = plan.spec.source;
        detail.querySelector('[data-detail-version]').textContent = plan.version;
        detail.querySelector('[data-detail-created-by]').textContent = plan.createdBy;
        detail.querySelector('[data-detail-audit]').textContent = configuredOnly
          ? 'Execution status follows the recorded Worker result.'
          : `${plan.updatedBy} · ${plan.reason || 'No decision recorded'}`;
        detail.querySelector('[data-detail-time]').textContent = `${plan.createdAt} / ${plan.updatedAt}`;
        graph(detail.querySelector('[data-detail-graph]'), plan.spec.steps);
        const rows = detail.querySelector('[data-detail-steps]');
        const reportStates = new Map([...rows.querySelectorAll('[data-task-report]')].map(report =>
          [report.dataset.taskReport, { open: report.open, scrollTop: report.querySelector('pre').scrollTop }]));
        rows.replaceChildren();
        plan.spec.steps.forEach((step, index) => {
          const actual = data.steps.filter(row => row.stepKey === step.key || row.task.id.value.startsWith(`${id}~${step.key}~`))
            .sort((a, b) => (Date.parse(b.task.createdAt) || 0) - (Date.parse(a.task.createdAt) || 0)
              || b.task.id.value.localeCompare(a.task.id.value));
          const tr = el('tr');
          [ `${step.key} / ${step.skillRef}`, `${step.instruction}\nModel: ${step.model || 'Worker choice'} · Effort: ${step.effort || 'Worker choice'}\nAcceptance: ${reviewNames[reviewMode(step)]} · Risk: ${reviewRisk(step)}`,  step.dependsOn.join(', ') || 'Listed order',
            plan.spec.location || 'Plan temporary directory'
          ].forEach(value => line(tr, 'td', value));
          const resultCell = el('td');
          const skipped = data.controls.some(control => control.status === 'APPLIED' && control.action === 'JUMP'
            && index >= control.fromStep && index < control.targetStep);
          if (!actual.length) line(resultCell, 'div', skipped ? 'Skipped by manager decision' : 'Waiting for acceptance under the confirmed policy');
          if(reviewMode(step)==='SELF'&&!actual.some(row=>row.task.id.value.includes('~review-'))&&actual.some(row=>row.task.state==='COMPLETED'&&row.verdict==='PASS'))
            line(resultCell,'p','Self-check passed; no independent review was required by this policy.','help');
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
        const confirm = detail.querySelector('[data-confirm]'); if (confirm) confirm.hidden = plan.state !== 'DRAFT' || hasConfigured;
        const draftToolbar = detail.querySelector('[data-draft-toolbar]');
        if (draftToolbar) draftToolbar.hidden = plan.state !== 'DRAFT' || hasConfigured;
        const editDraft = detail.querySelector('[data-edit-draft]');
        if (editDraft) editDraft.href = '/ui/workflows';
        const withdraw = detail.querySelector('[data-withdraw]'); if (withdraw) withdraw.hidden = formalFactory || plan.state !== 'CONFIRMED';
        const jump = detail.querySelector('[data-jump]'); if (jump) jump.hidden = formalFactory || plan.state !== 'CONFIRMED' || !!pendingControl || !target?.options.length;
        const stop = detail.querySelector('[data-stop-plan]'); if (stop) stop.hidden = formalFactory || plan.state !== 'CONFIRMED' || !!pendingControl || finalReview?.verdict === 'PASS';
        const controlReason = detail.querySelector('[data-plan-decision-reason]'); if (controlReason) controlReason.hidden = formalFactory;
        const factoryStop = detail.querySelector('[data-factory-stop]');
        if (factoryStop) { factoryStop.hidden = !formalFactory || plan.state !== 'CONFIRMED'; factoryStop.href = `/ui/questions?planId=${encodeURIComponent(id)}&tab=controls`; }
        const factoryStopNote = detail.querySelector('[data-factory-stop-note]'); if (factoryStopNote) factoryStopNote.hidden = !formalFactory || plan.state !== 'CONFIRMED';
        const complete = detail.querySelector('[data-complete-plan]'); if (complete) complete.hidden = formalFactory || plan.state !== 'CONFIRMED' || !!pendingControl || !!data.humanOutcome || finalReview?.verdict === 'PASS';
        const cancel = detail.querySelector('[data-cancel-plan]'); if (cancel) cancel.hidden = formalFactory || !['CONFIRMED', 'STOPPED'].includes(plan.state) || !!pendingControl || !!data.humanOutcome;
        const resolve = detail.querySelector('[data-resolve-stop]');
        if (resolve) resolve.hidden = !pendingControl || data.steps.some(row =>
          row.task.id.value === pendingControl.taskId && ['CLAIMED', 'RUNNING'].includes(row.task.state));
        const resume = detail.querySelector('[data-resume-review]');
        if (resume) resume.hidden = !reviewRecovery?.allowed || !!pendingGrant;
        const managerPhase = detail.querySelector('[data-manager-phase]');
        if (managerPhase) {
          managerPhase.textContent = detail.querySelector('[data-detail-state]').textContent;
          managerPhase.dataset.status = data.gitDelivery && !['NOT_REQUIRED', 'DELIVERED'].includes(data.gitDelivery.state)
            ? data.gitDelivery.state : configuredOnly ? data.displayState || configured.state
            : failedTasks.length ? 'FAILED' : exhausted ? 'WAITING_APPROVAL' : activeWork ? 'RUNNING' : plan.state;
        }
        const guidance = detail.querySelector('[data-manager-guidance]');
        const failures = detail.querySelector('[data-failed-task-recovery]');
        if (failures) {
          failures.hidden = !failedTasks.length;
          const items = failures.querySelector('[data-failed-task-links]'); items.replaceChildren();
          for (const row of failedTasks) {
            const link = line(items, 'a', 'Open failed task diagnostics', 'btn ghost small-btn');
            link.href = `/ui/plans/${encodeURIComponent(id)}/tasks/${encodeURIComponent(row.task.id.value)}`;
          }
        }
        const preparation = detail.querySelector('[data-worker-preparation]');
        if (preparation) {
          const rows = data.workerPreparation || [];
          preparation.hidden = rows.length === 0;
          const items = preparation.querySelector('[data-worker-preparation-items]');
          items.replaceChildren();
          for (const row of rows) {
            const item = line(items, 'div');
            line(item, 'span', row.taskId, 'mono').setAttribute('data-i18n-skip', '');
            line(item, 'p', row.message);
          }
        }
        if (guidance) guidance.textContent = data.gitDelivery?.state === 'AWAITING_MERGE'
          ? 'Execution passed; local delivery is pending.' : formalFactory
          ? 'This confirmed plan is executed by AgentFactory. Follow its task results below.'
          : hasConfigured && !data.steps.length
          ? 'Follow the factory execution history below. The original graph has not been started.'
          : pendingGrant
          ? 'A decision Worker is assessing the fixed repair scope.'
          : failedTasks.length ? 'A task failed. Open its diagnostics, fix the cause, then retry that task.'
          : exhausted ? 'Review needs changes. Authorize a bounded repair or request another review.'
          : activeWork ? 'Workers are active. Follow task progress before choosing another action.'
          : plan.state === 'DRAFT' ? 'Review your selected steps, then confirm the draft to start.'
          : 'Choose your next move. Every decision stays in the mission history.';
        const reviewCard = detail.querySelector('[data-review-recovery]');
        if (reviewCard) reviewCard.hidden = !reviewRecovery?.allowed || !!pendingGrant;
        const draftCard = detail.querySelector('[data-draft-action]');
        if (draftCard) draftCard.hidden = plan.state !== 'DRAFT' || hasConfigured;
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
    detail.querySelector('[data-copy]')?.addEventListener('click', () => { location.href = '/ui/workflows'; });
    detail.querySelector('[data-confirm]')?.addEventListener('click', async () => {
      if(!plan)return;
      const selected=structuredClone(plan);
      const policy=selected.spec.steps.map(step=>`${step.key}: ${reviewNames[reviewMode(step)]} (${reviewRisk(step)})`).join('\n');
      if(!await window.aiwConfirm(`Confirm ${selected.spec.steps.length} selected steps at version ${selected.version}? This creates Worker tasks and fixes these review requirements:\n${policy}`))return;
      if(!plan||plan.id!==selected.id||plan.version!==selected.version){actionStatus.textContent='The Plan changed. Refresh and confirm its current review policy.';return;}
      try { await api(`/plans/${encodeURIComponent(id)}/confirm`, { version: selected.version });
        actionStatus.textContent = 'Execution confirmed. Worker tasks have been created.'; await load();
      } catch (error) {
        actionStatus.textContent = `Confirmation failed: ${error.message}`;
        detail.querySelector('[data-draft-start-status]').textContent = actionStatus.textContent;
      }
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
    detail.querySelector('[data-factory-review-start]')?.addEventListener('click', async event => {
      const button = event.currentTarget;
      if (!await window.aiwConfirm('Request an independent review of the recorded result?')) return;
      const status = detail.querySelector('[data-factory-review-status]');
      button.disabled = true;
      try {
        await api(`/plans/${encodeURIComponent(id)}/factory-review`, { version: plan.version });
        status.textContent = 'Independent review queued.';
        await load();
      } catch (error) { status.textContent = `Review request failed: ${error.message}`; }
      finally { button.disabled = false; }
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
