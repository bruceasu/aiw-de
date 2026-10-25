/* Human setup UI. No credential in a URL, localStorage, logs or saved request journal. */
(() => {
  "use strict";
  const $ = id => document.getElementById(id);
  if (!$("secretary-setup")) return;
  const labels = { A05: "确认并推进计划", A03: "重试失败任务", A19_ANSWER: "回答工作问题", A11_APPROVE: "批准范围内的审批", A11_REJECT: "拒绝范围内的审批", A07: "停止计划" };
  const parameters = new URLSearchParams(location.search);
  let data = null, permissions = [], busy = false, uncertain = false, registrationUnknown = false, ready = false;
  let popup = null, proposal = null, linkedOrigin = null, preferred = parameters.get("secretarySetup") || "";
  let journal = null, journalKey = null;
  let saveError = null, saveResult = null;
  let executionPlanKey = null;
  let needsRepair = parameters.get("connectionNeedsRepair") === "1";
  const t = value => window.aiwI18n?.t(value) || value;
  // Also upgrade an already cached template without restarting the platform.
  if (!$("secretary-profile-actions")) {
    const footer = document.createElement("div"); footer.id = "secretary-profile-actions"; footer.className = "authority-full secretary-setup-actions";
    const hint = document.createElement("p"); hint.id = "setup-save-hint"; hint.className = "help"; hint.setAttribute("role", "status");
    const save = $("setup-authorize"); save.setAttribute("aria-describedby", hint.id); save.replaceWith(footer); footer.append(hint, save);
  }
  if (!$("setup-save-shortcut")) {
    const shortcut = document.createElement("button"); shortcut.id = "setup-save-shortcut"; shortcut.type = "button"; shortcut.className = "btn ghost"; shortcut.textContent = t("前往保存授权");
    $("secretary-setup").querySelector(".section-heading").append(shortcut);
  }
  const show = (value, error = false, saved = false) => { saveError = error ? value : null; saveResult = saved ? value : null; $("setup-status").textContent = value; if ($("setup-save-hint")) $("setup-save-hint").textContent = value; };
  $("setup-save-shortcut")?.addEventListener("click", () => {
    ($("secretary-profile-actions") || $("setup-authorize")).scrollIntoView({ block: "center", behavior: "smooth" });
    if (!$("setup-authorize").disabled) $("setup-authorize").focus({ preventScroll: true });
  });
  const selectedWorker = () => data?.workers.find(item => item.worker_id === $("setup-worker").value);
  const selectedPlan = () => data?.plans.find(item => item.id === $("setup-plan").value);
  function localOrigin(value) {
    const url = new URL(value);
    if (url.protocol !== "http:" || !["127.0.0.1", "localhost", "[::1]"].includes(url.hostname) || url.username || url.password || url.pathname !== "/" || url.search || url.hash) throw new Error(t("请选择本机秘书的完整地址，例如 http://127.0.0.1:8766。"));
    return url.origin;
  }
  try { if (parameters.get("secretaryOrigin")) $("setup-local-origin").value = localOrigin(parameters.get("secretaryOrigin")); } catch (_) { show(t("本机地址无效，已保留默认地址。")); }
  async function call(path, body = {}) {
    const response = await fetch("/api/r1/authority" + path, { method: "POST", credentials: "same-origin", headers: { "Content-Type": "application/json", "X-CSRF-Token": document.querySelector('meta[name="csrf-token"]').content }, body: JSON.stringify(body) });
    let result; try { result = await response.json(); } catch (_) { throw new Error(t("操作回执无法读取，请先检查当前状态。")); }
    if (!response.ok) { const error = new Error(result.message || t("操作未获确认，请检查登录和当前状态。")); error.rejected = response.status >= 400 && response.status < 500; throw error; }
    return result;
  }
  function update() {
    const worker = selectedWorker(), plan = selectedPlan();
    // Saving advances the policy revision. Keep the user's choices for the same plan.
    const planKey = plan?.id || "";
    if (executionPlanKey !== planKey) {
      executionPlanKey = planKey;
      const repositories = data?.repositories || [];
      $("setup-repository").replaceChildren(new Option(t("请选择项目仓库"), ""));
      for (const repository of repositories) { const option = new Option(repository.workspace + " · " + repository.path, repository.ref); option.dataset.i18nSkip = ""; $("setup-repository").add(option); }
      const normalize = value => value.replace(/\\/g, "/").replace(/\/$/, "").toLowerCase();
      const matches = repositories.filter(item => normalize(plan?.description || "").includes(normalize(item.path)));
      const rules = plan?.execution?.rules || [];
      const prior = rules.find(rule => rule.tool === "READ");
      if (prior && repositories.some(item => item.ref === prior.repositoryRef)) $("setup-repository").value = prior.repositoryRef;
      else if (matches.length === 1) $("setup-repository").value = matches[0].ref;
      else if (repositories.length === 1) $("setup-repository").value = repositories[0].ref;
      const names = [...new Set((plan?.description || "").match(/[\w-]+\.(?:html?|css|js|ts|py|java|json|md)\b/gi) || [])];
      $("setup-path").value = prior?.pathPrefix || (names.length === 1 ? names[0] : ".");
      $("setup-with-files").checked = !!plan && (!plan.execution?.revision || !rules.some(rule => rule.baseRevision));
      $("setup-write").checked = prior ? rules.some(rule => rule.tool === "WRITE") : true;
      $("setup-git").checked = prior ? rules.some(rule => rule.tool === "GIT") : true;
    }
    $("setup-files").disabled = busy || !plan;
    for (const id of ["setup-repository", "setup-path", "setup-write", "setup-git"]) $(id).disabled = busy || !plan || !$("setup-with-files").checked;
    $("setup-authorize").textContent = t("确认代理与文件执行范围");
    $("setup-files-status").textContent = t(!plan ? "选择计划后可同时确认文件执行范围。" : !(data?.repositories || []).length ? "尚无已登记仓库，请先在项目仓库页面添加项目；不会自行创建 Git 仓库。" : plan.execution?.paused || plan.execution?.revoked ? "此计划的执行已暂停或撤销，请先在执行控制中核对。" : plan.execution?.revision ? "此计划已有执行范围。需要补充基线或修改范围时，勾选后重新核对。" : "此计划缺少文件执行范围，保存时会一并确认。" );
    for (const input of $("setup-actions").querySelectorAll("input")) input.disabled = busy || !plan || !data?.actions.includes(input.value);
    $("setup-authorize").disabled = !ready || busy || !worker?.worker_boot || uncertain || needsRepair;
    if ($("setup-save-hint")) {
      $("setup-save-hint").textContent = saveError || saveResult || t(busy ? "正在准备或保存授权，请稍候。" : !ready ? "正在读取授权数据；加载失败的原因会显示在这里。" : uncertain ? "原授权申请的结果尚未确认，请先核对或重试原申请。" : needsRepair ? "请先完成秘书连接，再保存授权。" : !worker?.worker_boot ? "请选择已连接的秘书，建立当前进程会话后再保存。" : !plan ? "选择计划后可同时确认文件执行范围。" : $("setup-with-files").checked ? "将同时确认所选计划的代理权限、仓库、文件范围和 Git 基线。" : "本次只确认所选代理设置；文件执行范围未勾选。" );
      $("setup-save-hint").classList.toggle("secretary-save-error", !!saveError);
    }
    $("setup-enable-current").disabled = busy || !worker?.worker_boot || worker.enabled || uncertain || needsRepair;
    $("setup-connect").disabled = busy || registrationUnknown;
    $("setup-refresh").disabled = busy;
    const preview = $("setup-plan-preview"); preview.replaceChildren();
    if (plan) {
      const goal = document.createElement("p"); goal.textContent = plan.description; goal.dataset.i18nSkip = ""; preview.append(goal);
      for (const step of plan.steps || []) { const item = document.createElement("p"); item.textContent = step.instruction || step.prompt || step.id || ""; item.dataset.i18nSkip = ""; preview.append(item); }
      const detail = document.createElement("a"); detail.href = "/ui/plans/" + encodeURIComponent(plan.id); detail.textContent = t("查看完整计划"); detail.target = "_blank"; detail.rel = "noopener"; preview.append(detail);
    }
  }
  async function read() {
    saveResult = null; ready = false; update();
    const state = await call("/secretary-setup/state");
    const previous = $("setup-worker").value || preferred;
    data = state;
    $("setup-worker").replaceChildren(new Option(t("连接一位新秘书"), "new"));
    data.workers.forEach((worker, index) => {
      const option = new Option(t("秘书") + " " + (index + 1) + " · " + t(worker.enabled ? "已启用" : worker.worker_boot ? "待确认代理范围" : "等待连接"), worker.worker_id);
      option.dataset.i18nSkip = ""; $("setup-worker").add(option);
    });
    if (data.workers.some(item => item.worker_id === preferred)) $("setup-worker").value = preferred;
    else if (data.workers.some(item => item.worker_id === previous)) $("setup-worker").value = previous;
    else if (data.workers.length === 1 && !preferred) $("setup-worker").value = data.workers[0].worker_id;
    const plan = $("setup-plan").value || parameters.get("planId");
    $("setup-plan").replaceChildren(new Option(t("先允许创建计划草稿，执行范围稍后确认"), ""));
    for (const item of data.plans) { const option = new Option(item.title || item.id, item.id); option.dataset.i18nSkip = ""; $("setup-plan").add(option); }
    if (data.plans.some(item => item.id === plan)) $("setup-plan").value = plan;
      const permissionState = await call("/action-permissions/state"); permissions = permissionState.permissions;
    const nextKey = permissionState.ownerId ? "aiw.secretary.setup.pending." + permissionState.ownerId : null;
    if (journalKey !== nextKey) {
      journalKey = nextKey;
      try { journal = journalKey ? JSON.parse(sessionStorage.getItem(journalKey) || "null") : null; }
      catch (_) { uncertain = true; show(t("无法读取原设置申请，请先核对当前授权记录。")); update(); return; }
    }
    if (journal) {
      const worker = selectedWorker();
      if (worker?.owner_id !== journal.ownerId || worker?.worker_id !== journal.body.workerId) {
        uncertain = true; show(t("当前身份与原设置申请不同，请切回原秘书核对。"));
      } else {
        const actions = journal.body.actions;
        // The existing permission protocol records immutable creation requests.
        // With no action grants, the exact enabled process is sufficient proof.
        if (!actions.length && worker.enabled && worker.worker_boot === journal.body.workerBoot) {
          journal = null; sessionStorage.removeItem(journalKey); uncertain = false; show(t("当前秘书进程已确认，可以返回秘书继续工作。"));
        } else { uncertain = true; show(t("已读取当前授权记录。原申请结果仍需核对，请使用下方原申请按钮，不能换新申请重复授权。")); }
      }
    } else show(t(selectedWorker()?.worker_boot ? "已建立连接，请核对下面的代理范围。" : "先连接本机秘书，再确认代理范围。"));
    retry.hidden = !journal; ready = true;
    update();
  }
  function encode(value) {
    const bytes = new TextEncoder().encode(JSON.stringify(value));
    return "aiw-secretary-v1." + btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }
  $("setup-connect").addEventListener("click", async () => {
    if (busy || registrationUnknown) return;
    let origin; try { origin = localOrigin($("setup-local-origin").value); } catch (error) { show(error.message); return; }
    const worker = selectedWorker();
    if (worker?.worker_boot && !needsRepair) {
      linkedOrigin = origin;
      popup = parameters.has("secretaryOrigin") && window.opener ? window.opener : window.open(origin + "/#setup", "_blank");
      busy = true; update();
      try { await read(); show(t("已建立连接，请核对下面的代理范围。")); }
      catch (error) { show(error.message, true); }
      finally { busy = false; update(); }
      return;
    }
    if (worker && !await window.aiwConfirm("重新连接会轮换这位秘书的连接凭证。确认重新连接到当前电脑？", { danger: true })) return;
    if (busy || registrationUnknown) return;
    busy = true; update();
    linkedOrigin = origin;
    // Open during the user gesture. No secret is placed in this URL.
    popup = parameters.has("secretaryOrigin") && window.opener ? window.opener : window.open(origin + "/#setup", "_blank");
    const credential = btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(48)))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
    try {
      const readScope = worker ? (typeof worker.read_scope === "string" ? JSON.parse(worker.read_scope) : worker.read_scope) : [];
      if (!Array.isArray(readScope)) throw new Error(t("无法读取原权限范围，请刷新后再连接。"));
      registrationUnknown = true;
      const identity = await call(worker ? "/workers/" + encodeURIComponent(worker.worker_id) + "/credential" : "/workers/new", { credential, readScope });
      registrationUnknown = false;
      proposal = { platform: location.origin, ownerId: identity.ownerId, workerId: identity.workerId, credential };
      preferred = identity.workerId;
      $("setup-code").value = encode(proposal); $("setup-copy-code").disabled = false;
      if (popup) popup.postMessage({ type: "aiw:secretary:connect", code: $("setup-code").value }, origin);
      show(t("已准备连接信息。请在打开的秘书页面确认连接，然后回到这里检查状态。"));
      await read();
    } catch (error) { if (error.rejected) registrationUnknown = false; show(error.message + (registrationUnknown ? " " + t("登记结果未知，请先刷新并选择原秘书核对，不要重复登记。") : "")); }
    finally { busy = false; update(); }
  });
  window.addEventListener("message", event => {
    if (!popup || !linkedOrigin || event.source !== popup || event.origin !== linkedOrigin) return;
    if (event.data?.type === "aiw:secretary:ready" && proposal) popup.postMessage({ type: "aiw:secretary:connect", code: encode(proposal) }, event.origin);
    if (event.data?.type === "aiw:secretary:connected" && proposal && event.data.workerId === proposal.workerId && event.data.ownerId === proposal.ownerId) {
      $("setup-code").value = ""; $("setup-copy-code").disabled = true; proposal = null;
      needsRepair = false;
      read().catch(error => show(error.message));
    }
  });
  $("setup-copy-code").addEventListener("click", async () => { try { await navigator.clipboard.writeText($("setup-code").value); show(t("连接码已复制，请粘贴到秘书设置，勿分享给其他人。")); } catch (_) { show(t("无法复制，请在备用连接码输入框中手动复制。")); } });
  $("setup-refresh").addEventListener("click", () => read().catch(error => show(error.message)));
  $("setup-worker").addEventListener("change", () => { saveResult = null; saveError = null; preferred = $("setup-worker").value; if (selectedWorker()) registrationUnknown = false; update(); });
  const clearSaveFeedback = () => { saveResult = null; saveError = null; update(); };
  $("secretary-profile").addEventListener("input", clearSaveFeedback);
  $("secretary-profile").addEventListener("change", clearSaveFeedback);
  $("setup-plan").addEventListener("change", update);
  $("setup-with-files").addEventListener("change", update);
  const expiry = new Date(Date.now() + 30 * 86400000); expiry.setMinutes(expiry.getMinutes() - expiry.getTimezoneOffset()); $("setup-expiry").value = expiry.toISOString().slice(0, 16);
  const retry = document.createElement("button"); retry.type = "button"; retry.className = "btn ghost"; retry.textContent = "核对原申请后重试原设置"; retry.hidden = !journal; $("secretary-profile").append(retry);
  async function submit(body) {
    busy = true; update();
    try {
      const result = await call("/secretary-setup/authorize", body);
      journal = null; uncertain = false; sessionStorage.removeItem(journalKey); retry.hidden = true;
      $("setup-execution").hidden = !result.executionScopeRequired;
      if (result.executionScopeRequired) $("setup-execution").href = "/ui/authority#authority-execution";
      await read(); show(t(result.executionScopeRequired ? "代理设置已保存；此计划仍需确认仓库和工具执行范围。" : body.execution ? "代理设置和文件执行范围已保存。请返回秘书检查并继续。" : "代理设置已保存。返回秘书后，等待授权的原任务会继续。"), false, true);
      const receiver = popup || (parameters.has("secretaryOrigin") ? window.opener : null);
      if (receiver) receiver.postMessage({ type: "aiw:secretary:authorized" }, localOrigin($("setup-local-origin").value));
    } catch (error) {
      if (error.rejected) { journal = null; uncertain = false; if (journalKey) sessionStorage.removeItem(journalKey); retry.hidden = true; show(error.message, true); }
      else { uncertain = true; retry.hidden = false; show(error.message + " " + t("原申请已保留，请先检查当前状态。"), true); }
    }
    finally { busy = false; update(); }
  }
  $("secretary-profile").addEventListener("submit", async event => {
    event.preventDefault(); if (!ready || busy || uncertain) return;
    saveError = null; saveResult = null;
    const worker = selectedWorker(), plan = selectedPlan(); if (!worker?.worker_boot) return;
    const actions = [...$("setup-actions").querySelectorAll("input:checked:not(:disabled)")].map(input => input.value);
    const maximumUses = Number($("setup-maximum").value), expiresAt = new Date($("setup-expiry").value).getTime();
    if (!Number.isSafeInteger(maximumUses) || maximumUses < 1 || maximumUses > 10000 || !Number.isSafeInteger(expiresAt) || expiresAt <= Date.now()) { show(t("请填写有效的授权期限和累计次数。")); return; }
    const expectedPriorIds = Object.fromEntries(actions.map(action => [action, permissions.find(item => item.workerId === worker.worker_id && item.action === action && item.state === "ENABLED")?.id || null]));
    const keepExistingPermissions = !!plan && actions.length > 0 && $("setup-keep-permissions").checked && actions.every(action => permissions.some(item => item.id === expectedPriorIds[action] && item.scopeKind === "PLAN" && item.scopeId === plan.id && item.effectiveState === "ENABLED" && item.remaining > 0));
    let execution = null, executionSummary = "";
    if (plan && $("setup-with-files").checked) {
      const request = { planId: plan.id, repositoryRef: $("setup-repository").value, pathPrefix: $("setup-path").value.trim(), writeAllowed: $("setup-write").checked, gitAllowed: $("setup-git").checked };
      if (!request.repositoryRef || !request.pathPrefix) { show(t("请选择项目仓库和允许访问的文件范围。")); return; }
      busy = true; update();
      try {
        const preview = await call("/secretary-setup/execution-preview", request);
        execution = { repositoryRef: preview.repositoryRef, pathPrefix: preview.pathPrefix, baseRevision: preview.baseRevision, expectedPolicyRevision: preview.expectedPolicyRevision, writeAllowed: preview.writeAllowed, gitAllowed: preview.gitAllowed };
        executionSummary = "\n\n" + t("文件执行范围") + "\n" + preview.repositoryPath + "\n" + preview.pathPrefix + "\n" + t("固定 Git 基线") + ": " + preview.baseRevision + "\n" + t(preview.writeAllowed ? "允许修改上述文件" : "仅允许读取上述文件") + "\n" + t(preview.gitAllowed ? "允许本地 Git 操作与结果提交" : "不允许 Git 操作") + "\n" + t("不包含任意命令执行；更新将替换此计划原有的执行范围。") + "\n" + t("授权有效期") + ": " + $("setup-expiry").value;
      } catch (error) { show(error.message, true); return; }
      finally { busy = false; update(); }
    }
    const permissionSummary = keepExistingPermissions ? t("保留此计划已有的有效代理权限和累计次数") : t("每种动作累计最多执行次数") + ": " + maximumUses + "\n" + $("setup-expiry").value;
    if (!await window.aiwConfirm(t("确认允许秘书在以下范围内代理？") + "\n\n" + actions.map(action => t(labels[action])).join("、") + "\n" + permissionSummary + executionSummary, { target: plan?.title || t("创建计划草稿，具体执行另行确认") })) return;
    if (busy || uncertain) return;
    const body = { requestId: crypto.randomUUID(), workerId: worker.worker_id, workerBoot: worker.worker_boot, generation: worker.generation, planId: plan?.id || null,
      planVersion: plan?.version ?? null, specDigest: plan?.specDigest || null, actions, expiresAt, maximumUses, expectedPriorIds, execution, keepExistingPermissions };
    journal = { ownerId: worker.owner_id, body };
    try { if (!journalKey) throw new Error(); sessionStorage.setItem(journalKey, JSON.stringify(journal)); } catch (_) { show(t("无法保留原申请，请使用可保存会话数据的浏览器后重试。")); journal = null; return; }
    await submit(body);
  });
  $("setup-enable-current").addEventListener("click", async () => {
    const worker = selectedWorker();
    if (busy || uncertain || needsRepair || !worker?.worker_boot || !journalKey) return;
    if (!await window.aiwConfirm("确认当前秘书进程继续使用已有代理范围？不会新增授权或重置累计次数。")) return;
    if (busy || uncertain || needsRepair) return;
    const body = { requestId: crypto.randomUUID(), workerId: worker.worker_id, workerBoot: worker.worker_boot, generation: worker.generation,
      planId: null, planVersion: null, specDigest: null, actions: [], expiresAt: Date.now() + 86400000, maximumUses: 1, expectedPriorIds: {} };
    journal = { ownerId: worker.owner_id, body };
    try { sessionStorage.setItem(journalKey, JSON.stringify(journal)); } catch (_) { journal = null; show(t("无法保留原申请，请使用可保存会话数据的浏览器后重试。")); return; }
    await submit(body);
  });
  retry.addEventListener("click", async () => { if (busy || !journal || selectedWorker()?.owner_id !== journal.ownerId || selectedWorker()?.worker_id !== journal.body.workerId) return; const original = journal; if (await window.aiwConfirm("确认只重试原来的代理设置申请？") && !busy && journal === original) await submit(original.body); });
  $("setup-execution").addEventListener("click", () => { document.querySelector('[data-authority-tab="authority-execution"]')?.click(); document.getElementById("authority-execution")?.scrollIntoView({ block: "start" }); });
  read().catch(error => show(error.message, true));
})();
