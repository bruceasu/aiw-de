"use strict";
const result = document.getElementById("authority-result");
const identities = new Map([...document.querySelectorAll("[data-authority-identity]")].map(node => {
  let readScope = null;
  try { const scope = JSON.parse(node.dataset.readScope); if (Array.isArray(scope)) readScope = scope; } catch (_) { /* Keep unknown scope distinct from an empty scope. */ }
  const identity = { workerId: node.dataset.worker, ownerId: node.dataset.owner, generation: Number(node.dataset.generation), workerBoot: node.dataset.boot || null, enabled: node.dataset.enabled === "true", readScope };
  return [identity.workerId, identity];
}));
const workerLabel = identity => identity.workerId.length > 20 ? `${identity.workerId.slice(0, 8)}…${identity.workerId.slice(-4)}` : identity.workerId;

function startupCommand(identity) {
  const platform = window.location.origin;
  if (document.getElementById("authority-startup-shell").value === "powershell") {
    const quote = value => "'" + value.replace(/'/g, "''") + "'";
    return `& '.\\workers\\personal-secretary\\start.ps1' -OwnerId ${quote(identity.ownerId)} -WorkerId ${quote(identity.workerId)} -Platform ${quote(platform)}`;
  }
  const quote = value => "'" + value.replace(/'/g, "'\"'\"'") + "'";
  return `bash './workers/personal-secretary/start.sh' --owner-id ${quote(identity.ownerId)} --worker-id ${quote(identity.workerId)} --platform ${quote(platform)}`;
}
function updateStartupCommand() {
  const identity = identities.get(document.getElementById("authority-startup-worker").value);
  document.getElementById("authority-startup-command").value = identity ? startupCommand(identity) : "";
  document.getElementById("authority-copy-startup").disabled = !identity;
}
function syncWorkerSelection(select) {
  const identity = identities.get(select.value);
  const current = select.form;
  const owner = current?.elements.namedItem("ownerId");
  if (owner) owner.value = identity?.ownerId || "";
  if (current?.id === "control-worker") {
    current.elements.namedItem("generation").value = identity?.generation || "";
    current.elements.namedItem("workerBoot").value = identity?.workerBoot || "";
  }
  if (select.id === "authority-rotation-worker" && document.getElementById("authority-enrollment-mode").value === "rotate") {
    current.elements.namedItem("readScope").value = identity?.readScope?.join("\n") || "";
    if (identity && identity.readScope === null) feedback("无法读取这位秘书的原读取范围，请刷新后核对，再轮换凭证。", "error");
  }
  if (select.id === "authority-startup-worker") updateStartupCommand();
}
function updateEnrollmentMode() {
  const rotating = document.getElementById("authority-enrollment-mode").value === "rotate";
  const selector = document.getElementById("authority-rotation-worker");
  document.getElementById("authority-rotation-worker-field").hidden = !rotating;
  selector.disabled = !rotating;
  selector.required = rotating;
  document.getElementById("authority-enrollment-submit").textContent = rotating ? "轮换所选秘书的凭证" : "登记新秘书";
  if (rotating) syncWorkerSelection(selector);
  else document.getElementById("enroll-worker").elements.namedItem("readScope").value = "";
}
function renderIdentities(preferred = "") {
  for (const select of document.querySelectorAll("[data-authority-worker-select]")) {
    const previous = select.value;
    select.replaceChildren(new Option(identities.size ? "请选择秘书" : "请先登记秘书", ""));
    for (const identity of identities.values()) {
      const option = new Option(workerLabel(identity), identity.workerId);
      option.title = identity.workerId;
      select.append(option);
    }
    select.value = identities.has(previous) ? previous : identities.has(preferred) ? preferred : identities.size === 1 ? identities.keys().next().value : "";
    syncWorkerSelection(select);
  }
  const workerCount = document.querySelector("[data-authority-worker-count]");
  if (workerCount) workerCount.textContent = String(identities.size);
  if (identities.size) {
    const owners = [...new Set([...identities.values()].map(identity => identity.ownerId))];
    const ownerCount = document.querySelector("[data-authority-owner-count]");
    if (ownerCount) ownerCount.textContent = String(owners.length);
    const ownerList = document.querySelector(".authority-owner-list");
    ownerList.replaceChildren(...owners.map(owner => { const code = document.createElement("code"); code.textContent = owner; return code; }));
  }
  document.getElementById("authority-startup").hidden = !identities.size;
  const host = document.getElementById("authority-worker-list");
  host.replaceChildren();
  if (!identities.size) {
    host.className = "authority-empty";
    host.textContent = "尚未登记秘书。选择“登记新秘书”并提交，系统会生成唯一标识并关联当前账号。";
    return;
  }
  host.className = "table-wrap";
  const table = document.createElement("table");
  table.className = "authority-table";
  table.setAttribute("aria-label", "已登记的个人秘书");
  const heading = table.createTHead().insertRow();
  for (const label of ["秘书", "平台状态", "进程会话", "连接详情"]) {
    const cell = document.createElement("th"); cell.scope = "col"; cell.textContent = label; heading.append(cell);
  }
  const body = table.createTBody();
  for (const identity of identities.values()) {
    const row = body.insertRow();
    row.insertCell().textContent = workerLabel(identity);
    const badge = document.createElement("span");
    badge.className = "authority-state " + (identity.enabled ? "authority-state--ok" : "authority-state--muted");
    badge.textContent = identity.enabled ? "已启用" : "未启用";
    row.insertCell().append(badge);
    row.insertCell().textContent = identity.workerBoot ? "已建立会话" : "等待启动";
    const details = document.createElement("details");
    const summary = document.createElement("summary"); summary.textContent = "查看标识与范围";
    const content = document.createElement("pre"); content.textContent = JSON.stringify(identity, null, 2);
    details.append(summary, content); row.insertCell().append(details);
  }
  host.append(table);
}

function feedback(message, tone = "success", needsLogin = false) {
  result.hidden = false;
  result.dataset.tone = tone;
  result.textContent = message;
  if (needsLogin) {
    const link = document.createElement("a");
    link.href = "/ui/login?next=%2Fui%2Fauthority";
    link.textContent = "重新登录";
    result.append(link);
  }
}

// Switch presentation only. No tab change submits a form or requests new authority.
const navigation = document.querySelector("[data-authority-navigation]");
const tabs = [...document.querySelectorAll("[data-authority-tab]")];
const panels = [...document.querySelectorAll("[data-authority-panel]")];
function activatePanel(hash, updateUrl = false) {
  const active = tabs.find(tab => `#${tab.dataset.authorityTab}` === hash) || tabs[0];
  const target = active.dataset.authorityTab;
  for (const tab of tabs) {
    const selected = tab === active;
    tab.setAttribute("aria-selected", String(selected));
    tab.tabIndex = selected ? 0 : -1;
  }
  for (const panel of panels) panel.hidden = panel.id !== target;
  if (updateUrl) {
    const url = new URL(window.location.href);
    url.hash = target;
    window.history.replaceState(null, "", url);
  }
  document.querySelector("[data-authority-refresh]").href = window.location.pathname + window.location.search + `#${target}`;
}
navigation.setAttribute("role", "tablist");
tabs.forEach((tab, index) => {
  tab.setAttribute("role", "tab");
  tab.setAttribute("aria-controls", tab.dataset.authorityTab);
  const panel = document.getElementById(tab.dataset.authorityTab);
  panel.setAttribute("role", "tabpanel");
  panel.setAttribute("aria-labelledby", tab.id);
  panel.tabIndex = 0;
  tab.addEventListener("click", () => activatePanel(`#${tab.dataset.authorityTab}`, true));
  tab.addEventListener("keydown", event => {
    let next;
    if (event.key === "ArrowRight") next = (index + 1) % tabs.length;
    if (event.key === "ArrowLeft") next = (index + tabs.length - 1) % tabs.length;
    if (event.key === "Home") next = 0;
    if (event.key === "End") next = tabs.length - 1;
    if (next === undefined) return;
    event.preventDefault();
    tabs[next].focus();
    activatePanel(`#${tabs[next].dataset.authorityTab}`, true);
  });
});
activatePanel(window.location.hash);
window.addEventListener("hashchange", () => activatePanel(window.location.hash));

const stateLabels = {
  ENABLED: "已启用", REVOKED: "已撤销", DISABLED: "已禁用", EXPIRED: "已到期",
  ADMITTED: "已准入", IN_PROGRESS: "进行中", UNKNOWN: "结果未知", REJECTED: "已拒绝",
  SUCCEEDED: "已成功", FAILED: "已失败", NOT_STARTED: "未开始"
};
const actionLabels = {"A01": "追加修复轮次", "A02": "设置执行或评审模型", "A03": "重试指定失败任务", "A04": "恢复独立评审", "A05": "确认计划", "A06": "跳转计划步骤", "A07": "停止计划", "A08": "撤回计划", "A09": "人工完成计划", "A10": "取消计划", "A11_APPROVE": "批准审批", "A11_REJECT": "拒绝审批", "A12_CREATE": "创建发布计划", "A12_EXECUTE": "执行一次发布", "A13": "调整运行并发", "A14": "排空普通 Worker", "A15": "离线普通 Worker", "A16": "归档计划", "A17_SCHEDULE": "排期删除", "A17_UNDO": "撤销排期删除", "A18_BUDGET": "批准具体额度追加", "A19_ANSWER": "回答工作问题"};
actionLabels.A18_BUDGET = "限定预算追加";
actionLabels.A19_ANSWER = "回答工作问题";
const dimensionLabels = { TIME_MS: "时间（毫秒）", TASKS: "任务数量", COST_MICROS: "费用（微单位）", LOOP_BACKJUMPS: "循环回跳次数" };
const recordTables = {
  grants: { title: "授权记录", empty: "尚无授权记录。先确认动作、精确目标和有效期，再授予最小能力。", columns: [["id", "授权 ID"], ["workerId", "秘书", "worker"], ["rule.action", "动作", "action"], ["rule.targetId", "精确目标"], ["state", "记录状态", "state"], ["expiresMs", "到期时间", "date"]] },
  targets: { title: "目标控制", empty: "尚无已登记的控制目标。", columns: [["target_key", "目标键"], ["generation", "当前世代"], ["paused", "已暂停", "boolean"], ["restriction_revision", "禁止规则版本"]] },
  drafts: { title: "当前登录人的草稿", empty: "当前登录人暂无可供确认的 Plan 草稿。", columns: [["planId", "Plan ID"], ["version", "当前版本"], ["specDigest", "内容摘要"]] },
  policies: { title: "执行政策", empty: "尚无执行政策。确认执行范围后可申请受控执行；预算按当前模式处理。", columns: [["plan_id", "Plan ID"], ["revision", "政策版本"], ["generation", "当前世代"], ["paused", "已暂停", "boolean"], ["revoked", "已撤销", "boolean"]] },
  budgets: { title: "执行用量与配置记录", empty: "尚无执行用量记录。", columns: [["plan_id", "Plan ID"], ["dimension", "预算维度", "dimension"], ["loop_id", "循环 ID"], ["limit_value", "配置参考值", "limit"], ["used_value", "已使用"], ["reserved_value", "已预留"], ["unknown_count", "未知项数"]] },
  receipts: { title: "执行回执", empty: "尚无执行回执。发起操作并不代表已执行成功。", columns: [["request_id", "请求 ID"], ["state", "回执状态", "state"], ["updated_ms", "更新时间", "date"]] },
  effects: { title: "受控操作记录", empty: "尚无受控操作记录。", columns: [["operation_id", "操作 ID"], ["task_id", "任务"], ["attempt_id", "原尝试"], ["tool_id", "工具"], ["state", "记录状态", "state"]] },
  meters: { title: "执行消耗计量", empty: "尚无消耗计量记录。", columns: [["task_id", "任务"], ["attempt_id", "原尝试"], ["elapsed_ms", "执行毫秒数"], ["cost_micros", "费用微单位"], ["settled", "已结清", "boolean"], ["usage_unknown", "消耗未知", "boolean"], ["time_unknown", "时间未知", "boolean"]] },
  deliveries: { title: "正式交付与来源", empty: "尚无正式交付记录。", columns: [["task_id", "任务"], ["attempt_id", "原尝试"], ["digest", "交付摘要"], ["input_digest", "固定输入摘要"], ["config_digest", "配置摘要"]] }
};
const recordValue = (row, path) => path.split(".").reduce((value, key) => value?.[key], row);
function recordCell(row, column) {
  const [path, , format] = column;
  const value = recordValue(row, path);
  const cell = document.createElement("td");
  if (value === null || value === undefined || value === "") {
    cell.textContent = format === "limit" ? "未配置" : "—";
  } else if (format === "state") {
    const badge = document.createElement("span");
    badge.className = "authority-state";
    if (["ENABLED", "SUCCEEDED"].includes(value)) badge.classList.add("authority-state--ok");
    if (["UNKNOWN", "IN_PROGRESS"].includes(value)) badge.classList.add("authority-state--warn");
    if (["FAILED", "REJECTED"].includes(value)) badge.classList.add("authority-state--bad");
    if (["REVOKED", "DISABLED", "EXPIRED"].includes(value)) badge.classList.add("authority-state--muted");
    badge.textContent = stateLabels[value] || String(value);
    badge.title = String(value);
    cell.append(badge);
  } else if (format === "date") {
    const date = new Date(Number(value));
    cell.textContent = Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString("zh-CN", { hour12: false });
  } else if (format === "boolean") {
    cell.textContent = value === true || value === 1 ? "是" : "否";
  } else if (format === "action" || format === "dimension") {
    cell.textContent = (format === "action" ? actionLabels : dimensionLabels)[value] || String(value);
    cell.title = String(value);
  } else if (format === "worker") {
    cell.textContent = workerLabel(identities.get(value) || { workerId: String(value) });
    cell.title = String(value);
  } else {
    cell.textContent = typeof value === "object" ? JSON.stringify(value) : String(value);
    if (path !== "dimension") cell.className = "mono";
  }
  return cell;
}
for (const host of document.querySelectorAll("[data-authority-record-table]")) {
  const key = host.dataset.authorityRecordTable;
  const schema = recordTables[key];
  const source = document.getElementById(`authority-records-${key}`);
  let rows;
  try {
    rows = JSON.parse(source.textContent);
    if (!Array.isArray(rows)) throw new Error("Expected record array");
  } catch (_) {
    host.className = "authority-empty";
    host.textContent = "记录无法解析，请展开下方完整记录查看。";
    continue;
  }
  const counter = document.querySelector(`[data-authority-count="${key}"]`);
  if (counter) counter.textContent = String(rows.length);
  if (!rows.length) {
    host.className = "authority-empty";
    host.textContent = schema.empty;
    continue;
  }
  const table = document.createElement("table");
  table.className = "authority-table";
  table.setAttribute("aria-label", schema.title);
  const heading = table.createTHead().insertRow();
  for (const [, label] of schema.columns) {
    const cell = document.createElement("th");
    cell.scope = "col";
    cell.textContent = label;
    heading.append(cell);
  }
  const body = table.createTBody();
  for (const row of rows) {
    const tr = body.insertRow();
    for (const column of schema.columns) tr.append(recordCell(row, column));
  }
  host.className = "table-wrap";
  host.append(table);
  source.textContent = JSON.stringify(rows, null, 2);
}

async function post(path, body = {}) {
  const response = await fetch("/api/r1/authority" + path, { method: "POST", credentials: "same-origin", headers: { "Content-Type": "application/json", "X-CSRF-Token": document.querySelector('meta[name="csrf-token"]').content }, body: JSON.stringify(body) });
  if (response.status === 401) {
    const error = new Error("登录已失效，请重新登录后再提交。");
    error.needsLogin = true;
    throw error;
  }
  if (!response.ok) {
    let problem;
    try { problem = await response.json(); } catch (_) { /* The server may return an HTML error page. */ }
    const fallback = response.status === 422 ? "输入或请求格式不符合要求（422）。" : response.status === 403 ? "当前身份没有执行此操作的权限（403）。" : response.status === 409 ? "当前状态或版本已变化，请刷新后核对（409）。" : "操作未完成（" + response.status + "）。请核对当前状态。";
    const genericMessages = {
      "Invalid or unavailable action input": path === "/workers" ? "登记输入或请求格式不符合要求（422）。Worker ID、凭证或读取范围未通过校验；若使用生成的凭证且读取范围留空，请确认服务端已加载最新版本。" : fallback,
      "Current owner, identity and action authority required": "当前登录身份、所有者或操作权限不匹配（403）。请核对身份绑定。",
      "Reload authoritative state or query the original request receipt": "状态或版本已变化（409）。请刷新状态或查看原请求回执。"
    };
    const message = typeof problem?.message === "string" ? genericMessages[problem.message] || problem.message : fallback;
    const error = new Error(message);
    if (typeof problem?.field === "string") error.field = problem.field;
    throw error;
  }
  const payload = await response.json();
  feedback("操作已记录。请刷新当前状态，核对实际结果。");
  return payload;
}
function form(id, submit) {
  const node = document.getElementById(id);
  if (!node) return;
  node.addEventListener("submit", async event => {
    event.preventDefault();
    const current = event.currentTarget;
    if (current.dataset.submitting === "true") return;
    const fields = Object.fromEntries(new FormData(current));
    const button = current.querySelector('button[type="submit"], button:not([type])');
    current.dataset.submitting = "true";
    current.setAttribute("aria-busy", "true");
    if (button) button.disabled = true;
    try { await submit(fields); }
    catch (error) {
      feedback(error.message, "error", error.needsLogin);
      const field = error.field && current.elements.namedItem(error.field);
      if (field && typeof field.focus === "function") {
        field.setAttribute("aria-invalid", "true");
        field.addEventListener("input", () => field.removeAttribute("aria-invalid"), { once: true });
        field.focus();
      }
    }
    finally {
      delete current.dataset.submitting;
      current.removeAttribute("aria-busy");
      if (button) button.disabled = false;
    }
  });
}
async function hash(value) { return Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value))), byte => byte.toString(16).padStart(2, "0")).join(""); }
document.querySelectorAll("[data-authority-worker-select]").forEach(select => select.addEventListener("change", () => syncWorkerSelection(select)));
document.getElementById("bind-owner").addEventListener("click", async event => {
  const button = event.currentTarget;
  button.disabled = true;
  try {
    const identity = await post("/owners/bind");
    const code = document.createElement("code"); code.textContent = identity.ownerId;
    document.querySelector(".authority-owner-list").replaceChildren(code);
    document.querySelector("[data-authority-owner-count]").textContent = "1";
    feedback("当前账号已关联。身份标识由系统保管，无需手动填写；此操作未登记或启用秘书。");
  } catch (error) { feedback(error.message, "error", error.needsLogin); }
  finally { button.disabled = false; }
});
document.getElementById("authority-enrollment-mode").addEventListener("change", updateEnrollmentMode);
document.getElementById("authority-startup-shell").addEventListener("change", updateStartupCommand);
document.getElementById("authority-copy-startup").addEventListener("click", async () => {
  const input = document.getElementById("authority-startup-command");
  if (!input.value) return;
  try { await navigator.clipboard.writeText(input.value); feedback("启动命令已复制。请在项目根目录运行，脚本会单独提示输入已登记的凭证。"); }
  catch (_) { input.focus(); input.select(); feedback("无法访问剪贴板，已选中启动命令，请手动复制。", "error"); }
});
const credentialInput = document.getElementById("personal-credential");
const copyCredential = document.getElementById("copy-personal-credential");
credentialInput.addEventListener("input", () => { copyCredential.disabled = !credentialInput.value; });
copyCredential.addEventListener("click", async () => {
  if (!credentialInput.reportValidity()) return;
  try { await navigator.clipboard.writeText(credentialInput.value); feedback("当前凭证已复制，未重新生成或变更登记。"); }
  catch (_) {
    credentialInput.type = "text"; credentialInput.focus(); credentialInput.select();
    credentialInput.addEventListener("blur", () => { credentialInput.type = "password"; }, { once: true });
    feedback("无法访问剪贴板，已选中当前凭证，请手动复制。", "error");
  }
});
renderIdentities();
updateEnrollmentMode();
document.getElementById("generate-credential").addEventListener("click", async event => {
  const button = event.currentTarget;
  const input = document.getElementById("personal-credential");
  button.disabled = true;
  input.readOnly = true;
  try {
    const bytes = crypto.getRandomValues(new Uint8Array(32));
    input.type = "password";
    input.value = btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
    input.dispatchEvent(new Event("input", { bubbles: true }));
    try {
      await navigator.clipboard.writeText(input.value);
      feedback("凭证已生成、填入并复制到剪贴板。请保存供秘书启动使用，再提交登记或轮换。");
    } catch (_) {
      input.type = "text";
      input.focus();
      input.select();
      feedback("凭证已生成并填入，但无法访问剪贴板。已显示并选中，请手动复制保存，再提交登记或轮换。", "error");
      input.addEventListener("blur", () => { input.type = "password"; }, { once: true });
    }
  } catch (_) {
    feedback("无法生成安全随机凭证，请使用支持加密随机数的浏览器。", "error");
  } finally {
    input.readOnly = false;
    button.disabled = false;
  }
});
form("enroll-worker", async value => {
  const readScope = value.readScope.split(/\r?\n/).map(row => row.trim()).filter(Boolean);
  const invalid = (field, message) => Object.assign(new Error(message), { field });
  if (!/^[A-Za-z0-9_-]{43,128}$/.test(value.credential)) throw invalid("credential", "独立凭证必须为 43–128 位英文字母、数字、下划线或短横线。请使用“生成并复制”重新生成。");
  if (readScope.length > 128) throw invalid("readScope", "读取范围最多包含 128 个目标，请减少目标数量。");
  if (readScope.some(target => target.length > 1024)) throw invalid("readScope", "读取范围每个目标不得超过 1024 个字符。");
  const registering = value.mode === "new";
  if (!registering && value.mode !== "rotate") throw new Error("请选择登记新秘书或轮换已有秘书。");
  if (!registering && !identities.has(value.workerId)) throw invalid("workerId", "请先选择已有秘书。");
  if (!registering && identities.get(value.workerId).readScope === null) throw invalid("workerId", "原读取范围未知，请刷新后核对再轮换凭证。");
  const identity = await post(registering ? "/workers/new" : "/workers/" + encodeURIComponent(value.workerId) + "/credential", { credential: value.credential, readScope });
  identities.set(identity.workerId, { ...identity, workerBoot: null, enabled: false });
  renderIdentities(identity.workerId);
  document.getElementById("authority-startup-worker").value = identity.workerId;
  updateStartupCommand();
  const notice = document.getElementById("authority-registration-result");
  notice.hidden = false;
  notice.textContent = (registering ? "秘书已登记：" : "凭证已轮换：") + workerLabel(identity) + "。当前保持未启用，可在下方复制启动命令。";
  feedback(registering ? "登记成功，身份标识已由系统生成。请复制启动命令，无需填写 Owner ID 或 Worker ID。" : "凭证已轮换，原身份标识保持不变。请使用当前凭证启动并重新明确启用。");
});
form("control-worker", value => {
  const identity = identities.get(value.workerId);
  if (!identity) throw new Error("请选择已登记的秘书。");
  if (value.operation === "enable" && !identity.workerBoot) throw new Error("请先复制命令启动这位秘书建立进程会话，再刷新状态并明确启用。");
  return post("/workers/" + encodeURIComponent(value.workerId) + "/" + value.operation, { generation: Number(value.generation), workerBoot: value.workerBoot || null });
});
form("revoke-grant", value => post("/grants/" + encodeURIComponent(value.id) + "/revoke"));
form("target-control", async value => post("/targets/" + await hash(value.target) + "/" + value.operation, { target: value.target, generation: Number(value.generation) }));
form("revoke-restriction", value => post("/restrictions/" + encodeURIComponent(value.id) + "/revoke"));
form("supplement-budget", async value => post("/budgets/" + await hash(value.target + "\n" + value.action) + "/supplement", { target: value.target, action: value.action, amount: Number(value.amount) }));
form("manage-plan", value => post("/managed-plans", { ...value, version: Number(value.version), expiresAt: new Date(value.expiresAt).getTime() }));
form("reconcile-effect", value => post("/effects/" + encodeURIComponent(value.taskId) + "/reconcile", { attempt: Number(value.attempt), conclusion: value.conclusion, evidenceRef: value.evidenceRef }));
form("execution-policy", value => post("/execution-policies", JSON.parse(value.policy)));
form("execution-control", value => post("/execution-policies/" + encodeURIComponent(value.planId) + "/" + value.action, { revision: Number(value.revision) }));
form("execution-bind", value => post("/execution-contexts/" + encodeURIComponent(value.taskId) + "/bind", JSON.parse(value.context)));
form("execution-increment", value => post("/execution-budgets/increment", { ...value, amount: Number(value.amount), cumulativeCeiling: Number(value.cumulativeCeiling), loopId: value.loopId || null }));
form("execution-reconcile", value => post("/execution-effects/reconcile", value));
form("execution-stopped", value => post("/execution-tasks/" + encodeURIComponent(value.taskId) + "/stopped", { attemptId: value.attemptId, evidenceRef: value.evidenceRef, evidenceDigest: value.evidenceDigest, allProcessesQuiescent: value.allProcessesQuiescent === "on" }));
form("execution-meter", value => post("/execution-meters/reconcile", { ...value, actualElapsedMs: value.actualElapsedMs === "" ? null : Number(value.actualElapsedMs), actualCostMicros: value.actualCostMicros === "" ? null : Number(value.actualCostMicros) }));
