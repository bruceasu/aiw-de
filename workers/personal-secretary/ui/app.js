"use strict";

// Bind before the rest of the workspace UI; only the original asset routes are needed.
(() => {
  const element = id => document.getElementById(id);
  const generate = element("create-desktop-pairing");
  if (!generate || generate.dataset.pairingBound === "true") return;
  generate.dataset.pairingBound = "true";
  let expires = 0;
  let timer;
  const input = element("desktop-pairing-code");
  const show = element("show-desktop-pairing");
  const feedback = (message, tone = "ok") => {
    const status = element("desktop-pairing-status");
    status.textContent = message;
    status.dataset.tone = tone;
    status.hidden = !message;
  };
  const clear = () => {
    clearTimeout(timer);
    expires = 0;
    input.value = "";
    input.type = "password";
    show.textContent = "显示配对码";
    element("desktop-pairing-result").hidden = true;
  };
  const check = response => {
    if (response.ok) return;
    if ([401, 403].includes(response.status)) throw new Error("请先登录个人服务并刷新状态，再生成配对码。");
    if (response.status === 404) throw new Error("当前运行的秘书服务尚不支持再次配对。请更新并重启个人服务，再重新登录后生成配对码。");
    throw new Error(`配对请求未确认（${response.status}），请核对个人服务状态。`);
  };
  element("desktop-service-origin").textContent = location.origin;
  generate.addEventListener("click", async () => {
    generate.disabled = true;
    clear();
    feedback("正在请求新配对码，请稍候……");
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15000);
    try {
      const stateResponse = await fetch("/api/state", { credentials: "same-origin", cache: "no-store", signal: controller.signal });
      check(stateResponse);
      const state = await stateResponse.json();
      if (typeof state.csrf !== "string" || !state.csrf) throw new Error("当前会话校验信息不完整，请重新读取私人状态。");
      const response = await fetch("/api/pairing", {
        method: "POST", credentials: "same-origin", cache: "no-store", signal: controller.signal,
        headers: { "Content-Type": "application/json", "X-CSRF-Token": state.csrf }, body: "{}"
      });
      check(response);
      const result = await response.json();
      if (typeof result.code !== "string" || !result.code || !Number.isInteger(result.expiresIn) || result.expiresIn < 1 || result.expiresIn > 300) {
        throw new Error("服务返回的配对信息不完整，请核对服务版本后重新生成。");
      }
      expires = Date.now() + result.expiresIn * 1000;
      input.value = result.code;
      element("desktop-pairing-result").hidden = false;
      timer = setTimeout(() => { clear(); feedback("配对码已过期，请生成新码。", "warn"); }, result.expiresIn * 1000);
      feedback("临时配对码已生成，请复制到桌面界面。现有会话继续有效。");
    } catch (error) {
      const message = error.name === "AbortError" ? "等待配对响应超时，结果尚未确认。请检查秘书服务；本次不会自动重试。" :
        error instanceof TypeError || error instanceof SyntaxError ? "配对请求结果未确认，请检查个人服务。不会自动重复请求。" : error.message;
      feedback(message, "error");
    } finally { clearTimeout(timeout); generate.disabled = false; }
  });
  show.addEventListener("click", () => {
    if (Date.now() >= expires) { clear(); feedback("配对码已过期，请生成新码。", "warn"); return; }
    input.type = input.type === "password" ? "text" : "password";
    show.textContent = input.type === "password" ? "显示配对码" : "隐藏配对码";
  });
  element("copy-desktop-pairing").addEventListener("click", async () => {
    if (Date.now() >= expires) { clear(); feedback("配对码已过期，请生成新码。", "warn"); return; }
    try { await navigator.clipboard.writeText(input.value); feedback("配对码已复制。"); }
    catch (_) { feedback("浏览器无法复制，请显示配对码后手动复制。", "warn"); }
  });
  window.addEventListener("pagehide", clear);
  feedback("");
})();

let csrf = "";
let snapshot = null;
const byId = id => document.getElementById(id);
const formFor = route => document.querySelector(`form[data-route="${route}"]`);
const rows = key => Array.isArray(snapshot?.[key]) ? snapshot[key] : [];
const viewInfo = {
  overview: ["工作总览", "查看你的工作、安排与待处理请求"],
  work: ["工作与安排", "记录私人工作，确认安排时间"],
  chat: ["私人对话", "围绕具体工作，与个人模型讨论"],
  policies: ["代理策略", "管理已确认的私人策略及关联授权"],
  platform: ["平台协作", "预览并确认需要提交给平台的内容"],
  records: ["私人资料", "整理草稿、模板与待办"],
  state: ["状态与备份", "核对原请求回执，备份私人记录"]
};
const stateLabels = {
  DRAFT: ["草稿", "neutral"], SCHEDULED: ["已安排", "info"],
  START_PENDING: ["启动待确认", "warn"], ACTIVE: ["跟进中", "info"], DONE: ["已完成", "ok"],
  NEEDS_CONFIRMATION: ["需重新确认", "warn"], NEEDS_HUMAN: ["待人工处理", "warn"],
  CONFIRMED: ["已确认", "ok"], PAUSED: ["已暂停", "warn"],
  CANCELLED: ["已取消", "neutral"], REVOKED: ["已撤销", "neutral"],
  PENDING: ["待处理", "info"], SENDING: ["发送中，待核对", "warn"],
  RECONCILING: ["对账中", "warn"], ADMITTED: ["已受理", "info"],
  IN_PROGRESS: ["处理中", "info"], UNKNOWN: ["结果待核对", "warn"],
  SUCCEEDED: ["已成功", "ok"], REJECTED: ["已拒绝", "bad"],
  FAILED: ["已失败", "bad"]
};
const recordKeys = { draft: "drafts", template: "templates", todo: "todos" };
const previewVersions = { "question-preview": 0, "goal-preview": 0 };

function node(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text !== undefined) element.textContent = text;
  return element;
}
function feedback(message, tone = "ok", pairing = false) {
  const target = byId(pairing ? "pairing-status" : "status");
  target.textContent = message;
  target.dataset.tone = tone;
  target.hidden = !message;
}
function errorMessage(error) {
  if (error instanceof SyntaxError) return "服务返回的内容无法读取。请先刷新状态并核对原请求回执。";
  if (error instanceof TypeError) return "无法连接个人服务。请检查服务是否仍在运行；已提交的操作请先核对原回执。";
  return error.message || "操作未确认，请刷新状态并核对原请求回执。";
}
function apiError(response, result, route = "") {
  if (route === "login" && [401, 403].includes(response.status)) return new Error("配对码无效、已使用或已过期。请核对本服务启动终端提供的配对码；失效时需重新启动个人服务。");
  if (response.status === 401) return new Error("个人会话已失效，请使用本服务启动终端提供的配对码登录。配对码失效时需重新启动个人服务。");
  if (response.status === 403) return new Error("当前个人会话或校验令牌无效，请先刷新状态；若会话已过期，需重新配对登录。");
  if (response.status === 422) return new Error("操作未获确认。请检查所选记录、当前版本及平台授权，刷新状态并核对原请求回执。");
  return new Error(result.error || `操作未确认（${response.status}），请刷新状态并核对原请求回执。`);
}
function showSession(authenticated) {
  byId("pairing-view").hidden = authenticated;
  byId("personal-app").hidden = !authenticated;
}
function switchView(name, focus = false) {
  if (!Object.hasOwn(viewInfo, name)) name = "overview";
  document.querySelectorAll("[data-view]").forEach(panel => { panel.hidden = panel.dataset.view !== name; });
  document.querySelectorAll("[data-view-target]").forEach(tab => {
    const selected = tab.dataset.viewTarget === name;
    tab.setAttribute("aria-selected", String(selected));
    tab.tabIndex = selected ? 0 : -1;
    if (selected && focus) tab.focus();
  });
  [byId("page-title").textContent, byId("page-description").textContent] = viewInfo[name];
  history.replaceState(null, "", "#" + name);
}
function badge(value) {
  const [label, tone] = stateLabels[value] || [value || "未记录", "neutral"];
  const element = node("span", "pill " + tone, label);
  element.title = value || "未记录状态";
  return element;
}
function button(label, action) {
  const element = node("button", "text-button", label);
  element.type = "button";
  element.addEventListener("click", action);
  return element;
}
function empty(target, title, description, action) {
  const element = node("div", "empty-state");
  element.append(node("strong", "", title), node("span", "", description));
  if (action) element.append(button(action[0], action[1]));
  target.append(element);
}
function detail(value, label = "查看记录详情") {
  const element = node("details", "raw-details");
  element.append(node("summary", "", label), node("pre", "", JSON.stringify(value, null, 2)));
  return element;
}
function dateLabel(value) {
  if (!value) return "尚未安排";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "时间不可用" : date.toLocaleString("zh-CN", { hour12: false });
}
function localDate(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const part = number => String(number).padStart(2, "0");
  return `${date.getFullYear()}-${part(date.getMonth() + 1)}-${part(date.getDate())}T${part(date.getHours())}:${part(date.getMinutes())}`;
}
function selectOptions(select, records, label, placeholder) {
  const previous = select.value;
  select.replaceChildren(new Option(placeholder, ""));
  for (const record of records) select.add(new Option(label(record), record.id));
  if (records.some(record => record.id === previous)) select.value = previous;
}
function selectWork(workId, destination) {
  const form = formFor(destination === "chat" ? "chat" : "schedule");
  form.elements.workId.value = workId;
  if (destination === "chat") renderChat(); else updateSchedule();
  switchView(destination, true);
}
function workCard(work, compact = false) {
  const item = node("article", "record-item");
  const heading = node("div", "record-heading");
  heading.append(node("h3", "", work.title || "未命名工作"), badge(work.state));
  item.append(heading);
  if (work.body) item.append(node("p", "record-excerpt", work.body.slice(0, compact ? 90 : 220)));
  if (work.report) {
    item.append(node("p", "record-excerpt", work.report.message), node("p", "help", "报告更新于 " + dateLabel(work.report.updatedAt)));
  }
  const meta = node("div", "record-meta");
  meta.append(node("span", "", `版本 ${work.revision}`), node("span", "", dateLabel(work.dueMs)));
  const actions = node("div", "record-actions");
  actions.append(button("继续对话", () => selectWork(work.id, "chat")), button("安排时间", () => selectWork(work.id, "work")));
  if (work.planId && snapshot.platformOrigin) {
    const link = node("a", "text-button", "查看平台 Plan");
    link.href = new URL("/ui/plans/" + encodeURIComponent(work.planId), snapshot.platformOrigin).href;
    link.target = "_blank"; link.rel = "noopener noreferrer";
    actions.append(link);
  }
  item.append(meta, actions);
  if (!compact) item.append(detail(work));
  return item;
}
function updateSchedule() {
  const form = formFor("schedule");
  const work = rows("work").find(item => item.id === form.elements.workId.value);
  form.elements.revision.value = work?.revision ?? "";
}
function renderWorks() {
  const work = [...rows("work")].sort((a, b) => (a.title || "").localeCompare(b.title || "", "zh-CN"));
  for (const id of ["work-list", "overview-work"]) {
    const target = byId(id);
    target.replaceChildren();
    if (!work.length) empty(target, "还没有私人工作", "先记下工作标题和想法，再开始讨论或安排时间。", ["新建私人工作", () => switchView("work", true)]);
    for (const item of id === "overview-work" ? work.slice(0, 5) : work) target.append(workCard(item, id === "overview-work"));
  }
  document.querySelectorAll("[data-work-select]").forEach(select => {
    selectOptions(select, work, item => item.title || "未命名工作", work.length ? "请选择工作" : "请先创建私人工作");
    if (!select.value && work.length === 1) select.value = work[0].id;
  });
  updateSchedule();
  byId("personal-plan-options").replaceChildren(...[...new Set(work.map(item => item.planId).filter(Boolean))].map(id => new Option(id, id)));
}
function renderChat() {
  const target = byId("chat-messages");
  const workId = formFor("chat").elements.workId.value;
  const messages = rows("messages").filter(item => item.workId === workId).sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0));
  target.replaceChildren();
  if (!workId) empty(target, "先选择一项工作", "每项工作都有独立的私人对话记录。");
  else if (!messages.length) empty(target, "从一个问题开始", "写下问题或想法，发送给你配置的个人模型。");
  for (const message of messages) {
    for (const [kind, label, text] of [["user", "你", message.text], ["assistant", "个人模型", message.reply]]) {
      if (text === undefined || text === null || text === "") continue;
      const item = node("article", "chat-message " + kind);
      item.append(node("small", "", label + (message.createdAt ? " · " + dateLabel(message.createdAt) : "")), node("div", "chat-bubble", text));
      target.append(item);
    }
  }
  target.scrollTop = target.scrollHeight;
}
function renderPolicies() {
  const target = byId("policy-list");
  target.replaceChildren();
  if (!rows("policies").length) empty(target, "还没有代理策略", "保存已确认的策略，并关联平台已授予的精确能力。");
  for (const policy of rows("policies")) {
    const item = node("article", "record-item");
    const heading = node("div", "record-heading");
    heading.append(node("h3", "", policy.id), badge(policy.state));
    const meta = node("div", "record-meta", `版本 ${policy.revision}`);
    if (policy.expiresAt) meta.append(node("span", "", "到期 " + dateLabel(policy.expiresAt)));
    const actions = node("div", "record-actions");
    actions.append(button("编辑策略", () => {
      const form = formFor("policy");
      form.reset();
      for (const key of ["id", "revision", "grantId", "originalText"]) form.elements[key].value = policy[key] ?? "";
      form.elements.id.readOnly = true;
      form.elements.expiresAt.value = localDate(policy.expiresAt);
      form.elements.confirmed.checked = false;
      form.elements.id.focus();
    }));
    item.append(heading, node("p", "record-excerpt", (policy.originalText || "").slice(0, 240)), meta, actions, detail(policy));
    target.append(item);
  }
}
function renderRecords() {
  const target = byId("record-list");
  const kind = byId("record-kind").value;
  const records = rows(recordKeys[kind]);
  target.replaceChildren();
  if (!records.length) empty(target, "还没有这类资料", "在下方保存一条私人记录，之后可从这里选择编辑。");
  for (const record of records) {
    const data = Object.fromEntries(Object.entries(record).filter(([key]) => !["id", "revision"].includes(key)));
    const item = node("article", "record-item");
    item.append(node("h3", "", String(data.title || record.id)), node("p", "record-excerpt", String(data.text ?? data.body ?? JSON.stringify(data)).slice(0, 220)), node("div", "record-meta", `版本 ${record.revision}`));
    const actions = node("div", "record-actions");
    actions.append(button("编辑资料", () => {
      const form = formFor("record");
      form.elements.kind.value = kind;
      form.elements.id.value = record.id;
      form.elements.id.readOnly = true;
      form.elements.revision.value = record.revision;
      form.elements.data.value = JSON.stringify(data, null, 2);
      form.elements.data.focus();
    }));
    if (kind === "draft") {
      const remove = button("删除草稿", async () => {
        if (formFor("record").getAttribute("aria-busy") === "true") {
          feedback("资料正在保存，请等待完成后再删除。", "warn");
          return;
        }
        if (!window.confirm(`删除草稿“${String(data.title || record.id)}”？历史版本仍保留在私人备份中。`)) return;
        remove.disabled = true;
        try {
          const { refreshed } = await post("delete-draft", { id: record.id, revision: record.revision });
          const form = formFor("record");
          if (form.elements.kind.value === "draft" && form.elements.id.value === record.id) {
            form.reset();
            form.elements.kind.value = "draft";
            form.elements.id.readOnly = false;
          }
          feedback(refreshed ? "草稿已删除，历史版本仍保留在私人备份中。" : "删除已返回成功，但状态读取失败，请刷新核对。", refreshed ? "ok" : "warn");
        } catch (error) { feedback(errorMessage(error), "error"); }
        finally { remove.disabled = false; }
      });
      remove.className = "btn danger small";
      actions.append(remove);
    }
    item.append(actions, detail(record));
    target.append(item);
  }
}
function renderReceipts() {
  const receipts = rows("receipts");
  const overview = byId("overview-receipts");
  overview.replaceChildren();
  if (!receipts.length) empty(overview, "暂无待处理请求", "当前私人状态中没有尚未终结的请求。");
  for (const receipt of receipts.slice(0, 5)) {
    const item = node("article", "record-item");
    item.append(badge(receipt.state), node("p", "record-excerpt", receipt.request_id));
    overview.append(item);
  }
  const target = byId("receipt-list");
  target.replaceChildren();
  if (!receipts.length) { empty(target, "暂无待处理请求", "已终结的历史记录可在私人备份中核对。"); return; }
  const wrap = node("div", "table-wrap");
  const table = node("table");
  table.append(node("caption", "sr-only", "私人服务记录的待处理请求"));
  const head = node("thead");
  const header = node("tr");
  for (const title of ["原请求 ID", "实际状态", "回执详情"]) {
    const cell = node("th", "", title); cell.scope = "col"; header.append(cell);
  }
  head.append(header);
  const body = node("tbody");
  for (const receipt of receipts) {
    const row = node("tr");
    const stateCell = node("td"); stateCell.append(badge(receipt.state));
    const receiptCell = node("td"); receiptCell.append(detail(receipt.receipt, "查看原回执"));
    row.append(node("td", "request-id", receipt.request_id), stateCell, receiptCell);
    body.append(row);
  }
  table.append(head, body); wrap.append(table); target.append(wrap);
}
function updateCancelRecords() {
  const form = formFor("cancel");
  const records = rows(form.elements.kind.value === "work" ? "work" : "policies");
  selectOptions(form.elements.id, records, item => item.title || item.id, "请选择记录");
  updateCancelRevision();
}
function updateCancelRevision() {
  const form = formFor("cancel");
  const record = rows(form.elements.kind.value === "work" ? "work" : "policies").find(item => item.id === form.elements.id.value);
  form.elements.revision.value = record?.revision ?? "";
}
function render() {
  for (const element of document.querySelectorAll("[data-count]")) element.textContent = rows(element.dataset.count).length;
  byId("worker-label").textContent = snapshot.workerId || "个人秘书";
  byId("worker-label").title = snapshot.workerId || "";
  const enabled = snapshot.enabled === true;
  byId("runtime-state").textContent = enabled ? "本地动作申请已启用" : "本地动作申请已暂停";
  byId("runtime-state").className = "pill " + (enabled ? "ok" : "neutral");
  byId("enable").hidden = enabled;
  byId("pause").hidden = !enabled;
  byId("state").textContent = JSON.stringify(snapshot, null, 2);
  byId("last-updated").textContent = "状态读取于 " + new Date().toLocaleTimeString("zh-CN", { hour12: false });
  renderWorks(); renderChat(); renderPolicies(); renderRecords(); renderReceipts(); updateCancelRecords();
}
async function refresh() {
  const response = await fetch("/api/state", { credentials: "same-origin", cache: "no-store" });
  if (!response.ok) {
    if (response.status === 401) { csrf = ""; snapshot = null; showSession(false); }
    throw apiError(response, {});
  }
  const state = await response.json();
  csrf = state.csrf;
  delete state.csrf;
  snapshot = state;
  render();
  showSession(true);
  byId("pairing-status").hidden = true;
}
async function post(route, data) {
  const response = await fetch("/api/" + route, {
    method: "POST", credentials: "same-origin",
    headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf }, body: JSON.stringify(data)
  });
  const result = await response.json();
  if (!response.ok) throw apiError(response, result, route);
  let refreshed = true;
  try { await refresh(); } catch (_) { refreshed = false; }
  return { result, refreshed };
}
function invalidatePreview(route) {
  previewVersions[route] += 1;
  const question = route === "question-preview";
  const target = formFor(question ? "question-reply" : "goal-submit");
  for (const key of question ? ["projectionJson", "digest"] : ["requestId", "projectionJson", "digest"]) target.elements[key].value = "";
  target.querySelector("[data-projection-submit]").disabled = true;
  byId(question ? "question-projection" : "goal-projection").textContent = "内容已变化，请重新生成本地预览。";
}
function applyResult(route, result, version) {
  if (route === "questions") byId("questions").textContent = JSON.stringify(result, null, 2);
  if (route === "question-preview" || route === "goal-preview") {
    if (version !== previewVersions[route]) return false;
    const question = route === "question-preview";
    const target = formFor(question ? "question-reply" : "goal-submit");
    byId(question ? "question-projection" : "goal-projection").textContent = result.projectionJson;
    for (const key of question ? ["projectionJson", "digest"] : ["requestId", "projectionJson", "digest"]) target.elements[key].value = result[key];
    target.querySelector("[data-projection-submit]").disabled = false;
    if (!question) formFor("goal-receipt").elements.requestId.value = result.requestId;
  }
  return true;
}
function parseInput(value, label) {
  try { return JSON.parse(value); }
  catch (_) { throw new Error(label + "的 JSON 格式有误，请检查引号、逗号和括号。"); }
}
function updateSavedEditor(route, data, refreshed) {
  if (!refreshed || !["policy", "record"].includes(route)) return;
  const form = formFor(route);
  if (form.elements.id.value !== data.id || Number(form.elements.revision.value) !== data.revision) return;
  if (route === "record" && form.elements.kind.value !== data.kind) return;
  const saved = rows(route === "policy" ? "policies" : recordKeys[data.kind]).find(item => item.id === data.id);
  if (saved) {
    form.elements.revision.value = saved.revision;
    form.elements.id.readOnly = true;
    if (route === "policy") form.elements.confirmed.checked = false;
  }
}

document.querySelectorAll("[data-view-target]").forEach(tab => tab.addEventListener("click", () => switchView(tab.dataset.viewTarget)));
document.querySelector(".sidebar-nav").addEventListener("keydown", event => {
  if (!event.target.matches("[data-view-target]")) return;
  const tabs = [...document.querySelectorAll("[data-view-target]")];
  const horizontal = matchMedia("(max-width: 800px)").matches;
  const nextKey = horizontal ? "ArrowRight" : "ArrowDown";
  const previousKey = horizontal ? "ArrowLeft" : "ArrowUp";
  if (![nextKey, previousKey, "Home", "End"].includes(event.key)) return;
  event.preventDefault();
  const index = tabs.indexOf(event.target);
  const next = event.key === "Home" ? 0 : event.key === "End" ? tabs.length - 1 : (index + (event.key === nextKey ? 1 : -1) + tabs.length) % tabs.length;
  switchView(tabs[next].dataset.viewTarget, true);
});
const navMedia = matchMedia("(max-width: 800px)");
const updateNavOrientation = () => document.querySelector(".sidebar-nav").setAttribute("aria-orientation", navMedia.matches ? "horizontal" : "vertical");
navMedia.addEventListener("change", updateNavOrientation);
updateNavOrientation();
document.querySelectorAll("[data-go-view]").forEach(control => control.addEventListener("click", () => switchView(control.dataset.goView, true)));
formFor("schedule").elements.workId.addEventListener("change", updateSchedule);
formFor("chat").elements.workId.addEventListener("change", renderChat);
formFor("cancel").elements.kind.addEventListener("change", updateCancelRecords);
formFor("cancel").elements.id.addEventListener("change", updateCancelRevision);
byId("record-kind").addEventListener("change", renderRecords);
document.querySelectorAll("[data-reset-editor]").forEach(control => control.addEventListener("click", () => {
  const form = formFor(control.dataset.resetEditor);
  form.reset(); form.elements.id.readOnly = false;
  if (control.dataset.resetEditor === "record") form.elements.kind.value = byId("record-kind").value;
  form.elements.id.focus();
}));
for (const route of Object.keys(previewVersions)) formFor(route).addEventListener("input", () => invalidatePreview(route));

document.querySelectorAll("form[data-route]").forEach(form => form.addEventListener("submit", async event => {
  event.preventDefault();
  if (form.getAttribute("aria-busy") === "true") return;
  const route = form.dataset.route;
  const data = Object.fromEntries(new FormData(form));
  const submit = form.querySelector('[type="submit"]');
  if (submit.disabled) return;
  form.setAttribute("aria-busy", "true"); submit.disabled = true;
  const version = previewVersions[route];
  try {
    if ("revision" in data) data.revision = Number(data.revision);
    if ("requestVersion" in data) data.requestVersion = Number(data.requestVersion);
    if ("expiresAt" in data) { if (data.expiresAt) data.expiresAt = new Date(data.expiresAt).getTime(); else delete data.expiresAt; }
    if ("dueMs" in data) { data.dueMs = new Date(data.dueMs).getTime(); data.timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone; }
    if (route === "policy") { data.confirmed = data.confirmed === "on"; if (data.deny.trim()) data.deny = parseInput(data.deny, "禁止范围"); else delete data.deny; }
    if (route === "record") data.data = parseInput(data.data, "私人内容");
    const { result, refreshed } = await post(route, data);
    const current = applyResult(route, result, version);
    updateSavedEditor(route, data, refreshed);
    if (route === "login") form.reset();
    if (route === "chat" && form.elements.text.value === data.text) form.elements.text.value = "";
    let message = route === "login" ? "个人服务已接受配对。" : (refreshed ? "操作已记录，状态已更新。" : "操作已返回结果。");
    if (route === "schedule" && result.report) message = result.report.message;
    if (route === "question-preview" || route === "goal-preview") message = current ? "本地预览已生成，请核对精确内容后确认提交。" : "内容已变化，请重新生成本地预览。";
    if (route === "goal-submit" || route === "goal-receipt") message = "目标提案：" + (stateLabels[result.state]?.[0] || result.state || "待核对") + "。" + (result.note || "平台采纳前不会创建任务。");
    if (!refreshed) message += " 服务已返回结果，但状态读取失败。请先刷新状态、核对原回执，避免重复提交。";
    feedback(message, !refreshed || !current || route === "schedule" && result.state !== "SCHEDULED" ? "warn" : "ok", byId("personal-app").hidden);
  } catch (error) {
    feedback(errorMessage(error), "error", route === "login" || byId("personal-app").hidden);
  } finally {
    form.removeAttribute("aria-busy");
    submit.disabled = submit.hasAttribute("data-projection-submit") && !form.elements.projectionJson.value;
  }
}));

for (const [id, enabled] of [["enable", true], ["pause", false]]) byId(id).addEventListener("click", async () => {
  byId("enable").disabled = true; byId("pause").disabled = true;
  try {
    const { refreshed } = await post("enable", { enabled });
    feedback(refreshed ? (enabled ? "本地动作申请已启用，平台仍按当前身份和授权校验每次申请。" : "本地动作申请已暂停。") : "服务已返回结果，但状态读取失败。请刷新状态后核对启用情况。", refreshed ? "ok" : "warn", byId("personal-app").hidden);
  } catch (error) { feedback(errorMessage(error), "error"); }
  finally { byId("enable").disabled = false; byId("pause").disabled = false; }
});
byId("refresh").addEventListener("click", async () => {
  byId("refresh").disabled = true;
  try { await refresh(); feedback("已读取最新私人状态。"); }
  catch (error) { feedback(errorMessage(error), "error", byId("personal-app").hidden); }
  finally { byId("refresh").disabled = false; }
});
window.addEventListener("hashchange", () => switchView(location.hash.slice(1)));
let pollingState = false;
setInterval(async () => {
  if (!csrf || document.hidden || pollingState) return;
  pollingState = true;
  try { await refresh(); }
  catch (error) { feedback(errorMessage(error), "warn", byId("personal-app").hidden); }
  finally { pollingState = false; }
}, 5000);
switchView(location.hash.slice(1));
refresh().catch(error => {
  if (!error.message.includes("个人会话已失效")) feedback(errorMessage(error), "error", true);
});
