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
let stateRequestVersion = 0;
const byId = id => document.getElementById(id);
const formFor = route => document.querySelector(`form[data-route="${route}"]`);
const rows = key => Array.isArray(snapshot?.[key]) ? snapshot[key].filter(row => key !== "work" || row.state !== "TRASHED" && !row.conversationOnly) : [];
let settingsLoaded = false;
let knowledgeSettingsDirty = false;
let platformSetupLoaded = false;
let platformWindow = null;
let platformSender = null;
let platformInitialChecked = false;
let platformNeedsRepair = false;
const viewInfo = {
  setup: ["设置", "首次连接模型，之后直接使用"],
  confirmations: ["待你确认", "处理恢复待确认、已错过的工作，以及需要你允许的具体操作"],
  overview: ["工作总览", "查看你的工作、安排与待处理请求"],
  work: ["工作与安排", "记录私人工作，马上执行或按需定时"],
  chat: ["私人对话", "直接说出需要帮助的事，秘书会判断是否建立工作。"],
  policies: ["代理策略", "管理已确认的私人策略及关联授权"],
  platform: ["平台协作", "预览并确认需要提交给平台的内容"],
  records: ["私人资料", "整理草稿、模板与待办"],
  state: ["状态与备份", "核对原请求回执，备份私人记录"]
};
const stateLabels = {
  DRAFT: ["草稿", "neutral"], SCHEDULED: ["已安排", "info"],
  DAILY_RUNNING: ["正在处理", "info"], WAITING_CONFIRMATION: ["待你确认", "warn"], TRASHED: ["已移除", "neutral"],
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
  if (typeof result.message === "string" && result.code) return new Error(result.message.slice(0, 1000));
  if (route === "login" && [401, 403].includes(response.status)) return new Error("配对码无效、已使用或已过期。请核对本服务启动终端提供的配对码；失效时需重新启动个人服务。");
  if (response.status === 401) return new Error("个人会话已失效，请使用本服务启动终端提供的配对码登录。配对码失效时需重新启动个人服务。");
  if (response.status === 403) return new Error("当前个人会话或校验令牌无效，请先刷新状态；若会话已过期，需重新配对登录。");
  if (response.status === 422 && typeof result.code === "string" && result.code.startsWith("CHAT_") && typeof result.message === "string") return new Error(result.message.slice(0, 500));
  if (response.status === 422 && route === "chat") return new Error("对话失败。请检查个人服务是否已配置 AIW_PERSONAL_MODEL_COMMAND，以及模型程序能否正常运行；更新并重启服务后可显示具体原因。");
  if (response.status === 422) return new Error("这次操作没有完成，当前运行的服务可能仍是旧版本。请重启个人服务并刷新页面；新版本会说明具体原因和处理方法。");
  return new Error(result.error || `操作未确认（${response.status}），请刷新状态并核对原请求回执。`);
}
function showSession(authenticated) {
  if (!authenticated && byId("work-detail").open) byId("work-detail").close();
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
    else if (selected) requestAnimationFrame(() => revealNavTab(tab));
  });
  [byId("page-title").textContent, byId("page-description").textContent] = viewInfo[name];
  history.replaceState(null, "", "#" + name);
}
function revealNavTab(tab) {
  if (!matchMedia("(max-width: 800px)").matches) return;
  const behavior = matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth";
  tab.scrollIntoView({ behavior, block: "nearest", inline: "nearest" });
  requestAnimationFrame(updateNavOverflow);
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
let displayedWork = null;
const detailLabels = {
  action: "操作", text: "内容", message: "说明", error: "失败原因", code: "原因代码", completed: "是否完成",
  path: "文件位置", outputPath: "结果位置", title: "标题", url: "来源地址", query: "查询内容",
  content: "内容", result: "处理结果", state: "状态", status: "状态", name: "名称", description: "说明",
  source: "来源", sources: "参考来源", written: "已写入文件", steps: "处理步骤", workId: "工作标识",
  planId: "平台计划", taskId: "平台任务", requestId: "原请求标识", revision: "版本", kind: "类型",
  createdAt: "创建时间", updatedAt: "更新时间", dueMs: "安排时间", timeZone: "时区",
  reason: "原因", summary: "摘要", output: "输出", files: "文件", size: "大小", bytes: "字节数",
  tool: "工具", arguments: "参数", success: "是否成功", excerpt: "摘录", exists: "是否存在", result: "处理结果"
};
const actionLabels = {
  finish: "生成回复", search: "搜索资料", web_search: "搜索公开资料", fetch: "读取网页", read_url: "读取网页",
  read_file: "读取文件", write_file: "写入文件", list_files: "列出文件", delete_file: "删除文件",
  delegate_task: "提交专业任务", read: "读取资料", write: "保存资料", mkdir: "创建目录"
};
function readableValue(value, key = "") {
  if (value === null || value === undefined || value === "") return node("span", "muted", "未记录");
  if (Array.isArray(value)) {
    if (!value.length) return node("span", "muted", "无");
    const list = node("ol", "detail-list");
    for (const entry of value) { const item = node("li"); item.append(readableValue(entry)); list.append(item); }
    return list;
  }
  if (typeof value === "object") {
    const list = node("dl", "detail-fields");
    for (const [field, entry] of Object.entries(value)) {
      const content = node("dd"); content.append(readableValue(entry, field));
      list.append(node("dt", "", detailLabels[field] || field), content);
    }
    return list;
  }
  if (["state", "status"].includes(key)) return badge(String(value));
  const text = typeof value === "boolean" ? (value ? "是" : "否") :
    ["createdAt", "updatedAt", "dueMs"].includes(key) ? dateLabel(value) :
    key === "action" ? actionLabels[value] || String(value) : String(value);
  return (typeof value === "boolean" || ["state", "status", "action"].includes(key) ? node : rawNode)("p", "detail-text", text);
}
function workDetailSection(title, value, className = "") {
  const section = node("section", "work-detail-section " + className);
  section.append(node("h3", "", title), readableValue(value));
  return section;
}
function showWorkDetails(id) {
  const work = rows("work").find(item => item.id === id);
  if (!work) { feedback("这项工作已不在当前列表，请刷新后核对。", "warn"); return; }
  const jobs = rows("decisions").filter(item => item.kind === "DAILY_PLAN" && item.workId === id);
  const failures = rows("messages").filter(item => item.workId === id && item.state === "FAILED");
  displayedWork = { id, signature: JSON.stringify({ work, jobs, failures }) };
  const content = byId("work-detail-content");
  content.replaceChildren();
  byId("work-detail-title").textContent = work.title || "工作记录详情";
  byId("work-detail-title").toggleAttribute("data-i18n-skip", Boolean(work.title));
  const summary = node("div", "record-meta");
  summary.append(badge(work.state), node("span", "", "版本 " + work.revision));
  content.append(summary);
  if (work.report) content.append(workDetailSection(work.state === "FAILED" ? "失败原因" : "最新处理结果",
    work.report, work.state === "FAILED" ? "detail-failure" : ""));
  content.append(workDetailSection("工作说明", work.body || null));
  content.append(workDetailSection("安排与结果位置", { dueMs: work.dueMs, timeZone: work.timeZone, outputPath: work.outputPath }));
  if (failures.length) content.append(workDetailSection("失败的对话", failures.map(item => ({
    createdAt: item.createdAt, text: item.text, error: item.reply || "未记录具体原因"
  })), "detail-failure"));
  if (!jobs.length) content.append(workDetailSection("执行记录", "尚无本地日常处理步骤；平台工作请通过卡片中的平台入口查看。"));
  for (const [index, job] of [...jobs].reverse().entries()) {
    const section = workDetailSection("秘书处理记录 " + (jobs.length - index), {
      ...(job.state === "DONE" ? {} : { state: job.state }),
      ...(job.message ? { message: job.message } : {}), ...(job.result ? { result: job.result } : {})
    }, job.state === "FAILED" ? "detail-failure" : "");
    if (job.state === "DONE") section.append(node("p", "muted", "本轮回复已结束，不代表工作已完成。"));
    if (job.steps?.length) section.append(workDetailSection("处理步骤", job.steps));
    if (job.written?.length) section.append(workDetailSection("已保存文件", job.written));
    if (job.sources?.length) section.append(workDetailSection("参考来源", job.sources));
    content.append(section);
  }
  const raw = detail(work, "原始数据（排查时使用）");
  content.append(raw);
  byId("work-detail-note").textContent = "显示当前记录，阅读期间保持不变；可随时更新详情。";
  content.scrollTop = 0;
  if (!byId("work-detail").open) byId("work-detail").showModal();
}
function updateWorkDetailNotice() {
  if (!displayedWork || !byId("work-detail").open) return;
  const work = rows("work").find(item => item.id === displayedWork.id);
  const jobs = rows("decisions").filter(item => item.kind === "DAILY_PLAN" && item.workId === displayedWork.id);
  const failures = rows("messages").filter(item => item.workId === displayedWork.id && item.state === "FAILED");
  const text = !work ? "工作已移除，当前显示的是打开时的记录。" :
    displayedWork.signature !== JSON.stringify({ work, jobs, failures }) ? "有新记录，点击“更新详情”查看；当前内容保持不变。" :
    "显示当前记录，阅读期间保持不变；可随时更新详情。";
  if (byId("work-detail-note").textContent !== text) byId("work-detail-note").textContent = text;
}
function dateLabel(value) {
  if (!value) return "尚未安排";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "时间不可用" : date.toLocaleString(window.aiwI18n?.locale || "en", { hour12: false });
}
function rawNode(tag, className, text) {
  const result = node(tag, className, text); result.dataset.i18nSkip = ""; return result;
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
  const options = [{ value: "", text: window.aiwI18n?.t(placeholder) || placeholder },
    ...records.map(record => ({ value: record.id, text: label(record) }))];
  // Keep the native select and unchanged options alive during polling. Replacing
  // every option can leave an open WebEngine popup displaying the old list.
  options.forEach((entry, index) => {
    let option = select.options[index];
    if (!option) { option = new Option(); select.add(option); }
    option.dataset.i18nSkip = "";
    if (option.value !== entry.value) option.value = entry.value;
    if (option.textContent !== entry.text) option.textContent = entry.text;
  });
  while (select.options.length > options.length) select.remove(select.options.length - 1);
  const selected = records.some(record => record.id === previous) ? previous : "";
  if (select.value !== selected) select.value = selected;
}
function showSavedWork(work) {
  if (!snapshot || typeof work?.id !== "string" || !Number.isInteger(work.revision)) return;
  const existing = (snapshot.work || []).find(row => row.id === work.id);
  if (existing && existing.revision > work.revision) return;
  // The save receipt is authoritative. Invalidate reads started before it,
  // then show the saved item without waiting for another full-state request.
  stateRequestVersion += 1;
  snapshot.work = [...(snapshot.work || []).filter(row => row.id !== work.id), work];
  renderWorks();
  const select = formFor("schedule").elements.workId;
  select.value = work.id;
  updateSchedule();
  for (const count of document.querySelectorAll('[data-count="work"]')) {
    count.textContent = rows("work").length;
  }
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
  heading.append(work.title ? rawNode("h3", "", work.title) : node("h3", "", "未命名工作"), badge(work.state));
  item.append(heading);
  if (work.body) item.append(rawNode("p", "record-excerpt", work.body.slice(0, compact ? 90 : 220)));
  if (work.report) {
    const report = node("div", work.state === "FAILED" ? "work-report detail-failure" : "work-report");
    if (work.state === "FAILED") report.append(node("strong", "", "失败原因"));
    report.append(node("p", "record-excerpt", work.report.message), node("p", "help", "报告更新于 " + dateLabel(work.report.updatedAt)));
    item.append(report);
  }
  const meta = node("div", "record-meta");
  meta.append(node("span", "", `版本 ${work.revision}`), node("span", "", dateLabel(work.dueMs)));
  const actions = node("div", "record-actions");
  actions.append(button("继续对话", () => selectWork(work.id, "chat")), button("安排执行", () => selectWork(work.id, "work")));
  if (!["DAILY_RUNNING", "CANCELLED"].includes(work.state)) actions.append(button(work.report?.code === "INTERRUPTED" ? "已核对结果，重新执行" : "现在处理", () => assistantOperation("run-work", { id: work.id, revision: work.revision }, "已交给秘书处理，结果会自动更新。")));
  actions.append(button("移除工作", () => assistantOperation("work-trash", { id: work.id, revision: work.revision }, "已移到设置中的“已删除工作”，可以恢复。")));
  const planId = work.professionalPlanId || work.professionalDraftPlanId || work.planId;
  if (planId && snapshot.platformOrigin) {
    const link = node("a", "text-button", "查看平台 Plan");
    link.href = new URL("/ui/plans/" + encodeURIComponent(planId), snapshot.platformOrigin).href;
    link.target = "_blank"; link.rel = "noopener noreferrer";
    actions.append(link);
  }
  item.append(meta, actions);
  item.append(button("查看工作详情", () => showWorkDetails(work.id)));
  return item;
}
function updateSchedule() {
  const form = formFor("schedule");
  const work = rows("work").find(item => item.id === form.elements.workId.value);
  form.elements.revision.value = work?.revision ?? "";
}
function updateExecutionMode() {
  const form = formFor("schedule");
  const timed = form.elements.executionMode.value === "scheduled";
  byId("schedule-time-field").hidden = !timed;
  form.elements.dueMs.disabled = !timed;
  form.elements.dueMs.required = timed;
  form.querySelector('[type="submit"]').textContent = timed ? "安排定时执行" : "马上执行";
}
function renderWorks() {
  const work = [...rows("work")].sort((a, b) => (a.title || "").localeCompare(b.title || "", window.aiwI18n?.locale || "en"));
  for (const id of ["work-list", "overview-work"]) {
    const target = byId(id);
    target.replaceChildren();
    if (!work.length) empty(target, "还没有私人工作", "先记下工作标题和想法，再开始讨论、马上执行或安排定时。", ["新建私人工作", () => switchView("work", true)]);
    for (const item of id === "overview-work" ? work.slice(0, 5) : work) target.append(workCard(item, id === "overview-work"));
  }
  document.querySelectorAll("[data-work-select]").forEach(select => {
    const chatting = select === formFor("chat").elements.workId;
    const conversations = snapshot.conversations || work;
    const choices = chatting ? [...conversations, ...work.filter(item => !conversations.some(session => session.id === item.id))] : work;
    selectOptions(select, choices, item => item.title || "未命名工作", chatting ? "新对话" : work.length ? "请选择工作" : "请先创建工作");
    if (!chatting && !select.value && work.length === 1) select.value = work[0].id;
  });
  updateSchedule();
  byId("personal-plan-options").replaceChildren(...[...new Set(work.map(item => item.planId).filter(Boolean))].map(id => new Option(id, id)));
}
function markdownInline(target, text, depth = 0) {
  if (depth > 4) { target.append(document.createTextNode(text)); return; }
  const pattern = /`([^`\n]+)`|\*\*([^*\n]+)\*\*|__([^_\n]+)__|\*([^*\n]+)\*|\[([^\]\n]+)\]\(([^\s)]+)\)/g;
  let offset = 0;
  for (const match of text.matchAll(pattern)) {
    target.append(document.createTextNode(text.slice(offset, match.index)));
    if (match[1] !== undefined) target.append(node("code", "", match[1]));
    else if (match[5] !== undefined) {
      let url;
      try { url = new URL(match[6]); } catch (_) { /* Keep unsupported links as text. */ }
      if (url && ["https:", "http:"].includes(url.protocol) && !url.username && !url.password) {
        const link = node("a", "", match[5]);
        link.href = url.href; link.target = "_blank"; link.rel = "noopener noreferrer";
        target.append(link);
      } else target.append(document.createTextNode(match[0]));
    } else {
      const formatted = node(match[4] !== undefined ? "em" : "strong");
      markdownInline(formatted, match[2] ?? match[3] ?? match[4], depth + 1);
      target.append(formatted);
    }
    offset = match.index + match[0].length;
  }
  target.append(document.createTextNode(text.slice(offset)));
}
function markdownContent(text) {
  // Build DOM with text nodes only: raw HTML is never interpreted, images never fetched.
  const root = node("div", "markdown-content");
  const lines = String(text).replace(/\r\n?/g, "\n").split("\n");
  const cells = line => line.trim().replace(/^\||\|$/g, "").split("|").map(cell => cell.trim());
  const separator = line => line?.includes("|") && cells(line).every(cell => /^:?-{3,}:?$/.test(cell));
  const block = line => /^(?:\s*$|\s*`{3,}|\s*~{3,}|#{1,6}\s|\s*[-+*]\s|\s*\d+[.)]\s|>\s?)/.test(line);
  for (let i = 0; i < lines.length;) {
    const line = lines[i];
    if (!line.trim()) { i++; continue; }
    const fence = /^\s*(`{3,}|~{3,})([^\s]*)\s*$/.exec(line);
    if (fence) {
      const code = []; i++;
      const closing = new RegExp("^\\s*" + fence[1][0] + "{" + fence[1].length + ",}\\s*$");
      while (i < lines.length && !closing.test(lines[i])) code.push(lines[i++]);
      if (i < lines.length) i++;
      const pre = node("pre"); pre.append(node("code", "", code.join("\n"))); root.append(pre); continue;
    }
    const heading = /^(#{1,6})\s+(.+?)(?:\s+#+)?$/.exec(line);
    if (heading) { const title = node("h" + Math.min(heading[1].length + 1, 6)); markdownInline(title, heading[2]); root.append(title); i++; continue; }
    if (/^\s*(?:-{3,}|\*{3,}|_{3,})\s*$/.test(line)) { root.append(node("hr")); i++; continue; }
    if (line.includes("|") && separator(lines[i + 1])) {
      const table = node("table"), head = node("thead"), header = node("tr"), body = node("tbody");
      const columns = cells(line);
      for (const value of columns) { const cell = node("th"); cell.scope = "col"; markdownInline(cell, value); header.append(cell); }
      head.append(header); i += 2;
      while (i < lines.length && lines[i].trim() && lines[i].includes("|")) {
        const row = node("tr"), values = cells(lines[i++]);
        for (let index = 0; index < columns.length; index++) { const cell = node("td"); markdownInline(cell, values[index] || ""); row.append(cell); }
        body.append(row);
      }
      table.append(head, body); const wrapper = node("div", "table-wrap"); wrapper.append(table); root.append(wrapper); continue;
    }
    const listItem = /^\s*([-+*]|\d+[.)])\s+(.+)$/.exec(line);
    if (listItem) {
      const ordered = /^\d/.test(listItem[1]), list = node(ordered ? "ol" : "ul");
      if (ordered) list.start = Number.parseInt(listItem[1], 10);
      while (i < lines.length) {
        const entry = /^\s*([-+*]|\d+[.)])\s+(.+)$/.exec(lines[i]);
        if (!entry || /^\d/.test(entry[1]) !== ordered) break;
        const item = node("li"); markdownInline(item, entry[2]); list.append(item); i++;
      }
      root.append(list); continue;
    }
    if (/^>\s?/.test(line)) {
      const quote = node("blockquote"), values = [];
      while (i < lines.length && /^>\s?/.test(lines[i])) values.push(lines[i++].replace(/^>\s?/, ""));
      markdownInline(quote, values.join("\n")); root.append(quote); continue;
    }
    const paragraph = node("p"), values = [line]; i++;
    while (i < lines.length && !block(lines[i]) && !separator(lines[i + 1])) values.push(lines[i++]);
    markdownInline(paragraph, values.join("\n")); root.append(paragraph);
  }
  return root;
}
let chatRenderKey = "";
let trashRenderKey = "";
let renderedChatWork = null;
const chatPages = new Map();
const chatVisible = new Map();
const compareMessages = (a, b) => (a.createdAt || 0) - (b.createdAt || 0) || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0);
function mergeMessages(previous, incoming) {
  const items = new Map(previous.map(item => [item.id, item]));
  for (const item of incoming) items.set(item.id, item);
  return [...items.values()].sort(compareMessages);
}
async function loadChatPage(workId, older = false) {
  const page = chatPages.get(workId);
  if (!page || page.loading) return;
  const thread = byId("chat-messages");
  const height = thread.scrollHeight, top = thread.scrollTop;
  page.loading = true; page.error = false;
  renderChat();
  try {
    const response = await fetch("/api/conversation/page", {
      method: "POST", credentials: "same-origin", cache: "no-store",
      headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf },
      body: JSON.stringify({ id: workId, before: older ? page.before : null })
    });
    const result = await response.json();
    if (!response.ok) throw apiError(response, result);
    if (chatPages.get(workId) !== page) return;
    page.messages = mergeMessages(page.messages, result.messages);
    page.before = result.before; page.hasMore = result.hasMore; page.loaded = true;
    if (older) chatVisible.set(workId, (chatVisible.get(workId) || 10) + 20);
  } catch (error) {
    page.error = true;
    feedback(errorMessage(error), "error");
  } finally {
    page.loading = false;
    if (chatPages.get(workId) === page && formFor("chat").elements.workId.value === workId) {
      renderChat();
      if (older) thread.scrollTop = top + thread.scrollHeight - height;
    }
  }
}
function renderChatContext(workId) {
  const conversation = (snapshot.conversations || []).find(item => item.id === workId);
  const summary = byId("chat-summary");
  summary.textContent = conversation?.summary || "";
  byId("chat-summary-state").textContent = conversation?.summaryState === "RUNNING" ? "正在生成摘要，原始消息仍然保留。"
    : conversation?.summaryError || (conversation?.summary ? "摘要已保存，可供后续对话参考。" : "还没有会话摘要。长对话会自动整理，也可手动更新。");
  byId("chat-summarize").disabled = !conversation || conversation.summaryState === "RUNNING";
  byId("chat-delete").disabled = !conversation;
  const panel = byId("chat-memory-panel");
  if (panel.open) renderMemories();
}
let memoryRenderKey = "";
function renderMemories() {
  const memories = rows("memories");
  const key = JSON.stringify({ memories, locale: window.aiwI18n?.locale });
  if (key === memoryRenderKey) return;
  memoryRenderKey = key;
  const target = byId("chat-memories"); target.replaceChildren();
  if (!memories.length) empty(target, "还没有长期记忆", "摘要只提取稳定偏好和已确认的长期事实。");
  for (const memory of memories) {
    const item = node("article", "record-item");
    const text = node("p", "", memory.text); text.dataset.i18nSkip = "";
    item.append(text, button("删除记忆", async () => {
      if (!await window.aiwConfirm("删除这条长期记忆？", { danger: true, target: memory.text })) return;
      await assistantOperation("memory/delete", { id: memory.id, revision: memory.revision }, "记忆已删除。");
    }));
    target.append(item);
  }
}
function renderChat() {
  const target = byId("chat-messages");
  const workId = formFor("chat").elements.workId.value;
  renderChatContext(workId);
  if (workId && !chatPages.has(workId)) chatPages.set(workId, { messages: [], hasMore: false, loaded: false, loading: false, error: false, before: null });
  const page = chatPages.get(workId);
  if (page) page.messages = mergeMessages(page.messages, rows("messages").filter(item => item.workId === workId).slice(-10));
  const visible = chatVisible.get(workId) || 10;
  if (page && page.messages.length > visible + 20) {
    page.messages = page.messages.slice(-(visible + 20));
    page.before = [page.messages[0].createdAt || 0, page.messages[0].id];
    page.hasMore = true;
  }
  const messages = page ? page.messages.slice(-visible) : [];
  const key = JSON.stringify({ workId, messages, visible, more: page?.hasMore, loaded: page?.loaded, loading: page?.loading, error: page?.error, locale: window.aiwI18n?.locale });
  if (key === chatRenderKey) return;
  const follow = renderedChatWork !== workId || target.scrollHeight - target.scrollTop - target.clientHeight < 48;
  const previousScroll = target.scrollTop;
  chatRenderKey = key; renderedChatWork = workId;
  target.replaceChildren();
  if (!workId) empty(target, "直接开始对话", "输入需要帮助的事，无需先创建工作。");
  else if (!messages.length) empty(target, "从一个问题开始", "写下问题或想法，秘书会在日常范围内直接处理。");
  if (page && (page.hasMore || page.messages.length > visible || page.error)) {
    const history = node("div", "chat-history-controls");
    history.append(node("span", "help", "早期消息已收起，原文仍然保留。"));
    const show = button(page.loading ? "正在加载早期消息…" : page.error ? "重试加载消息" : "显示更早消息", () => {
      if (page.error || !page.loaded) { loadChatPage(workId, page.loaded); return; }
      if (page.messages.length >= visible + 20 || !page.hasMore) {
        const height = target.scrollHeight, top = target.scrollTop;
        chatVisible.set(workId, visible + 20); renderChat();
        target.scrollTop = top + target.scrollHeight - height;
      } else loadChatPage(workId, true);
    });
    show.disabled = page.loading;
    history.append(show);
    if (visible > 10) history.append(button("收起早期消息", () => { chatVisible.set(workId, 10); renderedChatWork = null; renderChat(); }));
    target.append(history);
  } else if (page && visible > 10) target.append(button("收起早期消息", () => { chatVisible.set(workId, 10); renderedChatWork = null; renderChat(); }));
  for (const message of messages) {
    for (const [kind, label, text] of [["user", "你", message.text], ["assistant", "秘书", message.reply]]) {
      if (text === undefined || text === null || text === "") continue;
      const item = node("article", "chat-message " + kind);
      if (kind === "assistant") {
        const avatar = node("img", "secretary-avatar chat-secretary-avatar");
        avatar.dataset.secretaryAvatar = ""; avatar.alt = "秘书头像";
        item.append(avatar);
      }
      const bubble = node("div", "chat-bubble");
      bubble.dataset.i18nSkip = "";
      if (kind === "assistant") bubble.append(markdownContent(text)); else bubble.textContent = text;
      item.append(node("small", "", label + (message.createdAt ? " · " + dateLabel(message.createdAt) : "")), bubble);
      target.append(item);
    }
  }
  target.scrollTop = follow ? target.scrollHeight : previousScroll;
  if (page && !page.loaded && !page.loading && !page.error) loadChatPage(workId);
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
  renderAssistant();
  renderSecretaryModels();
  renderSecretaryRuns();
  if (snapshot.conversations) for (const id of chatPages.keys()) {
    if (!snapshot.conversations.some(item => item.id === id)) chatPages.delete(id);
  }
  for (const element of document.querySelectorAll("[data-count]")) element.textContent = element.dataset.count === "messages" ? snapshot.messageCount ?? rows("messages").length : rows(element.dataset.count).length;
  byId("worker-label").textContent = snapshot.workerId || "个人秘书";
  byId("worker-label").title = snapshot.workerId || "";
  const enabled = snapshot.enabled === true;
  byId("runtime-state").textContent = snapshot.modelConfigured === false ? "先连接模型" : enabled ? "秘书可用" : "秘书已暂停";
  byId("runtime-state").className = "pill " + (enabled ? "ok" : "neutral");
  byId("enable").hidden = enabled;
  byId("pause").hidden = !enabled;
  byId("state").textContent = JSON.stringify(snapshot, null, 2);
  byId("last-updated").textContent = "状态读取于 " + new Date().toLocaleTimeString(window.aiwI18n?.locale || "en", { hour12: false });
  window.AiwAppearance?.setSummary({
    status: snapshot.modelConfigured === false ? "先连接模型" : enabled ? "秘书可用" : "秘书已暂停",
    state: snapshot.modelConfigured === false ? "unknown" : enabled ? "ready" : "paused",
    running: rows("work").filter(work => ["DAILY_RUNNING", "ACTIVE", "START_PENDING"].includes(work.state)).length,
    waiting: rows("decisions").filter(item => ["DAILY_CONFIRMATION", "PLATFORM_AUTHORIZATION", "PLATFORM_QUESTION"].includes(item.kind) && ["WAITING", "INTERRUPTED"].includes(item.state)).length
      + rows("work").filter(work => ["NEEDS_CONFIRMATION", "NEEDS_HUMAN"].includes(work.state)
        && ["RECOVERY_CONFIRMATION", "LEGACY_SCHEDULE_CONFIRMATION", "MISSED_TIME", "INTERRUPTED"].includes(work.report?.code)).length,
    done: rows("work").filter(work => work.state === "DONE").length,
  });
  renderWorks(); renderChat(); renderPolicies(); renderRecords(); renderReceipts(); updateCancelRecords();
}
async function refresh() {
  const version = ++stateRequestVersion;
  const response = await fetch("/api/state", { credentials: "same-origin", cache: "no-store" });
  if (version !== stateRequestVersion) return false;
  if (!response.ok) {
    if (response.status === 401) { csrf = ""; snapshot = null; showSession(false); }
    throw apiError(response, {});
  }
  const state = await response.json();
  if (version !== stateRequestVersion) return false;
  csrf = state.csrf;
  delete state.csrf;
  snapshot = state;
  if (byId("work-detail").open) updateWorkDetailNotice(); else render();
  showSession(true);
  byId("pairing-status").hidden = true;
  return true;
}
async function post(route, data, refreshState = true) {
  const response = await fetch("/api/" + route, {
    method: "POST", credentials: "same-origin",
    headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf }, body: JSON.stringify(data)
  });
  const result = await response.json();
  if (!response.ok) { const error = apiError(response, result, route); error.httpStatus = response.status; throw error; }
  if (route === "work") showSavedWork(result);
  let refreshed = true;
  if (refreshState) try { refreshed = await refresh() !== false; } catch (_) { refreshed = false; }
  return { result, refreshed };
}

async function assistantOperation(route, data, message) {
  try {
    const { result, refreshed } = await post(route, data);
    feedback(refreshed ? result.note || result.report?.message || message : "操作已返回结果，请刷新列表查看最新状态。", refreshed ? "ok" : "warn");
  } catch (error) { feedback(errorMessage(error), "error"); }
}

let modelLoginBusy = false;
let modelAuthorizationUrl = "";
const secretaryRunDrafts = new Map();
const secretaryRunDetails = new Map();
const secretaryControlRequests = new Map();

function renderSecretaryModels() {
  const form = formFor("settings");
  if (!form || !snapshot?.assistant) return;
  const sdk = form.elements.provider.value === "openai-agents";
  const mode = form.elements.authMode.value;
  const models = snapshot.assistant.models || {};
  const select = form.elements.connectionId;
  const selected = select.value || select.dataset.savedId || "";
  const signature = JSON.stringify([mode, models.connections]);
  if (select.dataset.signature !== signature) {
    select.replaceChildren(new Option("请选择连接，或创建新连接", ""));
    for (const connection of models.connections || []) {
      if (connection.kind !== mode) continue;
      const status = connection.status === "READY" ? connection.kind === "api-key" ? "密钥已保存，模型待检查" : "已授权，模型待检查"
        : ({ REAUTH_REQUIRED: "需要重新登录", REFRESHING: "刷新结果待确认", DISCONNECTED: "本机已停用" })[connection.status] || connection.status;
      const option = new Option(`${connection.label || connection.kind} · ${connection.account || connection.id} · ${status}`, connection.id);
      select.add(option);
    }
    select.value = selected;
    select.dataset.signature = signature;
  }
  for (const element of document.querySelectorAll("[data-sdk-auth]")) element.hidden = !sdk;
  form.elements.authMode.disabled = !sdk;
  select.disabled = !sdk;
  form.elements.key.disabled = form.elements.provider.value === "codex-app-server" || sdk && mode !== "api-key";
  form.elements.endpoint.disabled = form.elements.provider.value === "codex-app-server" || sdk && mode === "chatgpt";
  const loginActive = ["WAITING", "EXCHANGING"].includes(models.login?.state);
  byId("model-login").disabled = modelLoginBusy || !sdk || mode !== "chatgpt" || !models.persistentOAuth || !snapshot.localOAuth || loginActive;
  byId("model-login-cancel").disabled = !loginActive || modelLoginBusy;
  byId("model-disconnect").disabled = !select.value || modelLoginBusy;
  let message = "秘书使用本机独立连接，Control Plane 不需要在线。";
  if (models.error) message = models.error;
  else if (sdk && !mode) message = "请明确选择使用 ChatGPT 套餐登录或 API Key，再选择连接和模型。";
  else if (sdk && mode === "chatgpt" && !models.persistentOAuth) message = "此系统暂不支持受保护的 OAuth 保存，请使用 API Key 或本机 Codex 备用方式。";
  else if (sdk && mode === "chatgpt" && !snapshot.localOAuth) message = "请在秘书主机打开本机界面登录；当前远程浏览器不能接收秘书的回环回调。";
  else if (models.login?.message && mode === "chatgpt") message = models.login.message;
  else if (sdk && mode === "chatgpt" && !select.value) message = "还没有选择 ChatGPT 连接。先登录，再选择返回的本机连接；登录不代表已选择模型。";
  else if (sdk && mode === "api-key" && !select.value) message = snapshot.assistant.hasKey ? "当前沿用此前保存的本机密钥；可重新填写密钥后保存，迁入本机连接管理。" : "选择已保存的 API Key 连接，或填写 API Key 后保存以创建连接。";
  else if (!sdk && form.elements.provider.value === "codex-app-server") message = "明确使用本机 Codex 备用通道；沿用本机登录，不自动切换到其它账户或付费方式。";
  byId("model-connection-status").textContent = message;
  if (sdk && snapshot.assistant.credentialSource === "environment" && !select.value) byId("model-connection-status").textContent = "当前沿用此前明确配置的 OPENAI_API_KEY 启动环境来源；可填写密钥或选择连接后迁入本机连接管理。";
  if (loginActive && modelAuthorizationUrl) {
    const link = node("a", "", "打开 ChatGPT 登录页"); link.href = modelAuthorizationUrl; link.target = "_blank"; link.rel = "noopener noreferrer";
    byId("model-connection-status").append(document.createTextNode(" "), link);
  }
}

function renderSecretaryRuns() {
  let panel = byId("secretary-runs");
  if (!panel) {
    panel = node("details", "card"); panel.id = "secretary-runs";
    panel.append(node("summary", "", "运行过程与控制"));
    byId("view-chat").append(panel);
  }
  if (document.activeElement?.tagName === "TEXTAREA" && panel.contains(document.activeElement)) return;
  while (panel.childNodes.length > 1) panel.lastChild.remove();
  const runs = snapshot?.runs || [];
  if (!runs.length) { panel.append(node("p", "help", "开始处理请求后，这里显示真实运行与工具记录。")); return; }
  const labels = { QUEUED: "等待处理", RUNNING: "正在处理", PAUSE_REQUESTED: "暂停待生效", PAUSED: "已暂停", WAITING_USER: "等待确认或专业任务结果", WAITING_AUTH: "等待登录", RECONCILING: "结果待核对", SUCCEEDED: "已完成", FAILED: "未完成", CANCELLED: "已取消" };
  for (const run of runs.slice(0, 12)) {
    const section = node("section", "record-card");
    const work = (snapshot.work || []).find(row => row.id === run.workId);
    section.append(node("h3", "", work?.title || "私人会话"), node("p", "help", `${labels[run.state] || run.state} · 模型 ${run.modelCalls || 0} 次 · 工具 ${run.toolCalls || 0} 次`));
    section.append(node("p", "help", run.usageKnown ? `执行回合用量：输入 ${run.inputTokens}，输出 ${run.outputTokens} tokens（不含单独的会话摘要）` : "执行回合用量尚未完整获知；不按零费用计算。"));
    if (run.note) section.append(node("p", "help", run.note));
    if (run.hasUnknownEffect) section.append(node("p", "help", "仍有未知副作用；取消后续操作不表示原操作未执行，原始记录继续保留。"));
    for (const pending of run.queuedControls || []) section.append(node("p", "help", `${pending.control === "follow-up" ? "稍后处理" : "待应用控制"} · ${pending.state} · ${pending.text || pending.control} ${pending.note || ""}`));
    for (const operation of (snapshot.decisions || []).filter(item => item.kind === "FILE_OPERATION" && item.runId === run.id)) {
      section.append(node("p", "help", `${operation.resultStatus === "DRAFT_READY" ? "修订副本（原文件保留）" : "新文件"} · ${operation.state} · ${operation.path}`));
      if (operation.state === "SUCCEEDED" && operation.resultStatus === "DRAFT_READY") section.append(button("查看修订差异", async () => {
        try {
          const { result } = await post("file/diff", { id: operation.id }, false);
          secretaryRunDetails.set(run.id, result.diff + (result.truncated ? "\n（差异显示已截断）" : ""));
          renderSecretaryRuns();
        } catch (error) { feedback(errorMessage(error), "error"); }
      }));
    }
    const actions = node("div", "record-actions");
    const send = async (control, text = "") => {
      const current = (snapshot.runs || []).find(item => item.id === run.id);
      if (!current) return;
      const key = JSON.stringify([run.id, control, text]);
      let requestId = secretaryControlRequests.get(key);
      if (!requestId) { requestId = crypto.randomUUID(); secretaryControlRequests.set(key, requestId); }
      try {
        const { result } = await post("run/control", { runId: run.id, revision: current.revision, control, text, requestId });
        secretaryControlRequests.delete(key);
        secretaryRunDrafts.delete(run.id);
        feedback(result.note || "控制已记录，将在安全边界处理。");
      } catch (error) {
        if (error.httpStatus >= 400 && error.httpStatus < 500) secretaryControlRequests.delete(key);
        feedback(errorMessage(error), "error");
      }
    };
    if (run.state === "RUNNING") actions.append(button("暂停", () => send("pause")));
    if (run.state === "PAUSED") actions.append(button("继续原运行", () => send("resume")));
    if (["RUNNING", "PAUSED", "PAUSE_REQUESTED", "WAITING_USER", "WAITING_AUTH", "RECONCILING"].includes(run.state)) actions.append(button("取消后续操作", () => send("cancel")));
    if (run.state === "RECONCILING" || run.state === "CANCELLED" && run.hasUnknownEffect) actions.append(button("核对原操作结果", async () => {
      const current = (snapshot.runs || []).find(item => item.id === run.id);
      try { const { result } = await post("run/reconcile", { runId: run.id, revision: current.revision }); feedback(result.note); }
      catch (error) { feedback(errorMessage(error), "error"); }
    }));
    actions.append(button("查看执行事件", async () => {
      try {
        const { result } = await post("run/events", { runId: run.id }, false);
        secretaryRunDetails.set(run.id, result.map(event => `${event.sequence}. ${event.type} ${event.tool || event.control || ""} ${event.status || event.state || ""}`).join("\n"));
        renderSecretaryRuns();
      } catch (error) { feedback(errorMessage(error), "error"); }
    }));
    section.append(actions);
    if (["RUNNING", "PAUSED", "PAUSE_REQUESTED", "WAITING_USER"].includes(run.state)) {
      const text = node("textarea", ""); text.rows = 2; text.maxLength = 4000;
      text.placeholder = "补充当前工作的要求，或安排完成后再做的事";
      text.value = secretaryRunDrafts.get(run.id) || "";
      text.addEventListener("input", () => secretaryRunDrafts.set(run.id, text.value));
      section.append(text);
      const commands = node("div", "record-actions");
      if (run.state !== "WAITING_USER") commands.append(button("调整当前工作", () => send("steer", text.value)));
      commands.append(button("完成后再处理", () => send("follow-up", text.value)));
      section.append(commands, node("p", "help", "调整在安全边界生效。暂停或取消不会撤销已完成或已受理的操作。"));
    }
    if (secretaryRunDetails.has(run.id)) section.append(node("pre", "", secretaryRunDetails.get(run.id)));
    panel.append(section);
  }
}

function renderAssistant() {
  const settings = snapshot.assistant;
  if (!settings) {
    feedback("页面已更新，但运行中的个人服务仍是旧版本。请重启个人服务并刷新页面，新版本会显示具体错误原因。", "warn");
    return;
  }
  if (!settingsLoaded) {
    const form = formFor("settings");
    form.elements.provider.value = settings.provider;
    form.elements.authMode.value = settings.authMode || "";
    form.elements.connectionId.dataset.savedId = settings.connectionId || "";
    form.elements.endpoint.value = settings.endpoint;
    form.elements.model.value = settings.model;
    form.elements.executable.value = settings.executable || "";
    form.elements.effort.value = settings.effort || "";
    form.elements.roots.value = settings.roots.join("\n");
    if (form.elements.knowledgeBaseUrl) {
      form.elements.knowledgeBaseUrl.value = settings.knowledge?.baseUrl || "";
      form.elements.knowledgeEnabled.checked = !!settings.knowledge?.enabled;
      form.elements.knowledgeToken.placeholder = settings.knowledge?.hasCredential ? "凭证已保存；留空保持原值" : "粘贴独立知识库 Bearer token";
      const knowledgeAvailable = settings.knowledge?.available !== false;
      for (const name of ["knowledgeBaseUrl", "knowledgeToken", "knowledgeEnabled"]) form.elements[name].disabled = !knowledgeAvailable;
      const knowledgeStatus = byId("knowledge-availability");
      if (knowledgeStatus) {
        knowledgeStatus.hidden = knowledgeAvailable;
        knowledgeStatus.textContent = knowledgeAvailable ? "" : "知识库功能包未安装；秘书其余功能可正常使用。";
      }
      knowledgeSettingsDirty = false;
      for (const name of ["knowledgeBaseUrl", "knowledgeToken", "knowledgeEnabled"]) {
        if (name === "knowledgeEnabled") form.elements[name].onchange = () => { knowledgeSettingsDirty = true; };
        else form.elements[name].oninput = () => { knowledgeSettingsDirty = true; };
      }
    }
    form.elements.key.placeholder = settings.hasKey ? "密钥已保存；留空保持原值" : "本机无认证模型可留空";
    settingsLoaded = true;
    if (!settings.configured) {
      feedback("首次使用请打开“设置”连接模型。保存后可直接聊天、查询和安排工作。", "warn");
      switchView("setup");
    }
  }
  const target = byId("confirmation-list");
  const connection = snapshot.platformConnection;
  if (connection) {
    if (!platformSetupLoaded) { byId("platform-origin").value = connection.platform; platformSetupLoaded = true; }
    updatePlatformSetupMode();
    byId("platform-connection-status").textContent = !connection.connected
      ? connection.profile === "LOCAL" ? "本机 Plane 尚未连接；确认 Plane 正在此设备运行后可自动连接。" : "尚未连接 Control Plane"
      : connection.profile === "LOCAL" && connection.enabled ? "本机 Plane 已自动登记并授权计划与任务管理。"
      : connection.approvalState === "PENDING" ? "Agent 已配对，等待在 Control Plane 一次确认身份、初始授权并启用当前进程"
      : connection.approvalState === "APPROVED" && connection.enabled ? "Control Plane 已批准 Agent 并启用当前进程"
      : connection.approvalState === "APPROVED" ? "Agent 已批准；当前进程尚未启用，请在 Control Plane 启用"
      : "已连接 Control Plane，请在 Agent 页面检查配对状态";
      if (snapshot.backgroundError) byId("platform-connection-status").textContent = window.aiwI18n?.t("后台处理发生异常，已停止本地平台操作；这不表示平台撤销了进程授权。") || "后台处理发生异常，已停止本地平台操作；这不表示平台撤销了进程授权。";
    byId("platform-next").hidden = !connection.connected;
    if (connection.connected) byId("platform-next").href = connection.setupUrl;
    if (connection.profile === "LOCAL") byId("platform-next").hidden = true;
    byId("platform-rotate-certificate").hidden = !connection.connected || connection.profile !== "REMOTE";
    if (connection.connected && !platformInitialChecked) {
      platformInitialChecked = true;
      byId("platform-wizard").disabled = true;
      post("platform/check", {}).catch(() => { platformNeedsRepair = true; }).finally(() => { byId("platform-wizard").disabled = false; });
    }
  }
  target.replaceChildren();
  const pending = rows("decisions").filter(row => row.kind === "DAILY_CONFIRMATION" && row.state === "WAITING");
  const recovery = rows("work").filter(work => ["NEEDS_CONFIRMATION", "NEEDS_HUMAN"].includes(work.state)
    && ["RECOVERY_CONFIRMATION", "LEGACY_SCHEDULE_CONFIRMATION", "MISSED_TIME", "INTERRUPTED"].includes(work.report?.code));
  const authorizations = rows("decisions").filter(row => row.kind === "PLATFORM_AUTHORIZATION" && ["WAITING", "INTERRUPTED"].includes(row.state));
  const platformQuestions = rows("decisions").filter(row => row.kind === "PLATFORM_QUESTION" && row.state === "WAITING" && (snapshot.work || []).some(work => work.id === row.workId && !["TRASHED", "CANCELLED"].includes(work.state)));
  const count = pending.length + recovery.length + authorizations.length + platformQuestions.length;
  byId("tab-confirmations").textContent = count ? `待你确认（${count}）` : "待你确认";
  if (!count) empty(target, "没有需要确认的操作", "正常在线排队无需反复确认；跨停机恢复或错过时间的工作会在这里提示。");
  for (const question of platformQuestions) {
    const item = node("article", "record-item"); item.append(node("h3", "", "工作需要你补充信息"), rawNode("p", "", question.message));
    if (connection?.connected) {
      const link = node("a", "btn primary", "查看并回答工作问题");
      const url = new URL("/ui/questions", connection.platform); url.searchParams.set("planId", question.planId);
      link.href = url.href; link.target = "_blank"; link.rel = "noopener";
      const scope = node("a", "btn ghost", "调整秘书的答复代理范围");
      const setup = new URL(connection.setupUrl); setup.searchParams.set("planId", question.planId);
      scope.href = setup.href; scope.target = "_blank"; scope.rel = "noopener"; item.append(link, scope);
    }
    target.append(item);
  }
  for (const request of authorizations) {
    const work = (snapshot.work || []).find(item => item.id === request.workId);
    const item = node("article", "record-item");
    item.append(node("h3", "", request.state === "INTERRUPTED" ? "原任务需要核对" : "秘书申请确认或授权"), rawNode("p", "", work?.title || ""), node("p", "", request.message));
    if (request.state === "WAITING") item.append(node("p", "help", "原任务和上下文已保留，完成授权后会自动继续，不会重复提交已受理的任务。"));
    const controls = node("div", "record-actions");
    if (request.reason === "PROFESSIONAL_MODEL_REQUIRED" && connection?.connected) {
      const factory = node("a", "btn primary", "Agent 工厂设置");
      const url = new URL("/ui/agent-factory", connection.platform);
      for (const key of ["model", "effort"]) if (snapshot.assistant?.[key]) url.searchParams.set(key, snapshot.assistant[key]);
      if (["codex-app-server", "openai-agents"].includes(snapshot.assistant?.provider)) url.searchParams.set("runtime", snapshot.assistant.provider);
      factory.href = url.href; factory.target = "_blank"; factory.rel = "noopener"; controls.append(factory);
    }
    if (request.state === "INTERRUPTED") controls.append(button("查看工作记录", () => showWorkDetails(request.workId)));
    else if (connection?.connected) {
      const link = node("a", "btn primary", request.planId ? "配置计划与文件范围" : "确认代理范围");
      const url = new URL(connection.setupUrl); if (request.planId) url.searchParams.set("planId", request.planId);
      link.href = url.href; link.target = "_blank"; link.rel = "noopener"; controls.append(link);
    } else controls.append(button("连接专业平台", () => { switchView("setup", true); byId("platform-setup-card").scrollIntoView({ block: "start" }); }));
    if (request.state === "WAITING") controls.append(button("重新检查授权", () => assistantOperation("authorization/decide", { id: request.id, revision: request.revision, accepted: true }, "已确认，正在继续原任务。")));
    controls.append(button(request.state === "INTERRUPTED" ? "关闭核对提示" : "拒绝这项申请", () => assistantOperation("authorization/decide", { id: request.id, revision: request.revision, accepted: false }, request.state === "INTERRUPTED" ? "核对提示已关闭。" : "已拒绝，原任务不会继续。")));
    item.append(controls); target.append(item);
  }
  for (const work of recovery) {
    const item = node("article", "record-item");
    item.append(rawNode("h3", "", work.title), node("p", "", work.report.message));
    const controls = node("div", "record-actions");
    controls.append(button(work.report.code === "INTERRUPTED" ? "已核对结果，重新执行" : "现在执行",
      () => assistantOperation("run-work", { id: work.id, revision: work.revision }, "已重新提交执行。")),
      button("重新定时", () => {
        formFor("schedule").elements.executionMode.value = "scheduled";
        updateExecutionMode(); selectWork(work.id, "work");
      }), button("取消这项工作", () => assistantOperation("cancel", { kind: "work", id: work.id, revision: work.revision }, "已取消这项工作。")));
    item.append(controls); target.append(item);
  }
  for (const decision of pending) {
    const item = node("article", "record-item");
    item.append(node("h3", "", decision.message));
    const action = decision.action;
    const names = { read_file: "读取文件", write_file: "写入文件", delete_file: "不可恢复删除" };
    item.append(node("p", "", (names[action.action] || action.action) + "：" + (action.path || "")));
    if (typeof action.text === "string") {
      const preview = node("details", "raw-details");
      preview.append(node("summary", "", "查看将写入的完整内容"), node("pre", "", action.text));
      item.append(preview);
    }
    const controls = node("div", "record-actions");
    controls.append(button("允许这一次", () => assistantOperation("assistant/confirm", { id: decision.id, revision: decision.revision, accepted: true }, "已允许这一次操作。")),
      button("拒绝", () => assistantOperation("assistant/confirm", { id: decision.id, revision: decision.revision, accepted: false }, "已拒绝。")));
    item.append(controls); target.append(item);
  }
  const trash = byId("trashed-work");
  const deleted = (snapshot.work || []).filter(work => work.state === "TRASHED");
  const key = JSON.stringify({ deleted, memories: rows("memories").map(row => [row.id, row.sourceWorkId]), locale: window.aiwI18n?.locale });
  if (key === trashRenderKey) return;
  trashRenderKey = key;
  const modes = new Map([...trash.querySelectorAll("[data-trash-memory]")].map(select => [select.dataset.trashMemory, select.value]));
  trash.replaceChildren();
  if (!deleted.length) empty(trash, "没有已移除的工作", "移除后的工作可恢复，也可永久删除。");
  for (const work of deleted) {
    const item = node("article", "record-item");
    const controls = node("div", "record-actions");
    const memoryField = node("label", "field"), mode = node("select");
    mode.dataset.trashMemory = work.id;
    mode.add(new Option("保留已保存的记忆", "keep"));
    mode.add(new Option("同时删除派生记忆", "all"));
    mode.value = modes.get(work.id) || "keep";
    memoryField.append(node("span", "", "记忆处理"), mode);
    const remove = button("永久删除工作", async () => {
      const deleteMemories = mode.value === "all";
      const question = deleteMemories ? "永久删除这项工作、对话及其派生记忆？结果文件、已有备份和平台回执会保留。"
        : "永久删除这项工作及其对话，但保留已保存的记忆？结果文件、已有备份和平台回执会保留。";
      if (!await window.aiwConfirm(question, { danger: true, target: work.title })) return;
      remove.disabled = true;
      try {
        const { refreshed } = await post("work-delete", { id: work.id, revision: work.revision, deleteMemories });
        chatPages.delete(work.id); chatVisible.delete(work.id);
        feedback(refreshed ? "工作已永久删除。" : "删除已完成，状态读取失败，请刷新已删除列表核对。", refreshed ? "ok" : "warn");
      } catch (error) { feedback(errorMessage(error), "error"); }
      finally { remove.disabled = false; }
    });
    controls.append(button("恢复工作", () => assistantOperation("work-restore", { id: work.id, revision: work.revision }, "工作已恢复。")), memoryField, remove);
    const count = rows("memories").filter(memory => memory.sourceWorkId === work.id).length;
    const memoryCount = node("p", "help"); memoryCount.append(node("span", "", "已保存记忆"), document.createTextNode(": " + count));
    item.append(rawNode("h3", "", work.title), memoryCount, node("p", "help", "保留记忆只保留已有条目，删除时不会自动生成新记忆。"), controls);
    trash.append(item);
  }
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
const sidebarNav = document.querySelector(".sidebar-nav");
const navRegion = document.querySelector(".nav-scroll-region");
const updateNavOrientation = () => sidebarNav.setAttribute("aria-orientation", navMedia.matches ? "horizontal" : "vertical");
const updateNavOverflow = () => {
  const scrollable = navMedia.matches && sidebarNav.scrollWidth > sidebarNav.clientWidth + 1;
  navRegion.classList.toggle("has-overflow-start", scrollable && sidebarNav.scrollLeft > 1);
  navRegion.classList.toggle("has-overflow-end", scrollable && sidebarNav.scrollLeft + sidebarNav.clientWidth < sidebarNav.scrollWidth - 1);
};
sidebarNav.addEventListener("scroll", updateNavOverflow, { passive: true });
sidebarNav.addEventListener("focusin", event => {
  const tab = event.target.closest("[data-view-target]");
  if (tab) revealNavTab(tab);
});
sidebarNav.addEventListener("click", () => {
  const selected = sidebarNav.querySelector('[aria-selected="true"]');
  if (selected) revealNavTab(selected);
});
new MutationObserver(updateNavOverflow).observe(sidebarNav, {
  attributes: true, attributeFilter: ["hidden"], childList: true, characterData: true, subtree: true
});
window.addEventListener("resize", updateNavOverflow, { passive: true });
navMedia.addEventListener("change", () => {
  updateNavOrientation();
  updateNavOverflow();
  const selected = sidebarNav.querySelector('[aria-selected="true"]');
  if (selected) revealNavTab(selected);
});
updateNavOrientation();
requestAnimationFrame(updateNavOverflow);
document.querySelectorAll("[data-go-view]").forEach(control => control.addEventListener("click", () => switchView(control.dataset.goView, true)));
formFor("schedule").elements.workId.addEventListener("change", updateSchedule);
formFor("schedule").elements.executionMode.addEventListener("change", updateExecutionMode);
updateExecutionMode();
formFor("chat").elements.workId.addEventListener("change", renderChat);
byId("chat-new").addEventListener("click", () => {
  formFor("chat").elements.workId.value = "";
  renderedChatWork = null; renderChat(); formFor("chat").elements.text.focus();
});
function isLocalPlatformOrigin(value) {
  try {
    const origin = new URL(value);
    return origin.protocol === "http:" && !origin.username && !origin.password && !origin.search && !origin.hash
      && origin.pathname === "/" && ["127.0.0.1", "localhost", "[::1]"].includes(origin.hostname);
  } catch (_) { return false; }
}
function updatePlatformSetupMode() {
  const local = isLocalPlatformOrigin(byId("platform-origin").value);
  byId("platform-code-field").hidden = local;
  byId("platform-code").required = !local;
  if (local) byId("platform-code").value = "";
  byId("platform-wizard").hidden = local;
  byId("platform-connect-submit").textContent = local ? "自动连接本机 Plane" : "连接远程 Plane";
  byId("platform-connect-proposal").textContent = local
    ? "Plane 需配置管理员账号与管理员角色；秘书无需再次登录。登记仅发送到明确的本机 loopback 地址。"
    : "远程连接需要 HTTPS、mTLS 客户端证书和一次性配对邀请。";
}
byId("platform-origin").addEventListener("input", updatePlatformSetupMode);
updatePlatformSetupMode();
byId("platform-wizard").addEventListener("click", () => {
  try {
    const origin = new URL(byId("platform-origin").value);
    if (origin.username || origin.password || origin.search || origin.hash || origin.pathname !== "/" || !["http:", "https:"].includes(origin.protocol)) throw new Error("请输入完整的平台地址。");
    if (origin.protocol === "http:" && !["127.0.0.1", "localhost", "[::1]"].includes(origin.hostname)) throw new Error("远程平台请使用 HTTPS，HTTP 仅支持本机地址。");
    if (origin.protocol !== "https:") throw new Error("远程配对页面需要 HTTPS。");
    const url = new URL("/ui/agents", origin.origin);
    platformWindow = window.open(url.href, "_blank");
    if (!platformWindow) feedback("浏览器拦截了向导窗口，请允许打开后再试。", "warn");
  } catch (error) { feedback(errorMessage(error), "error"); }
});
byId("platform-check").addEventListener("click", () => assistantOperation("platform/check", {}, "已检查连接状态。"));
window.addEventListener("message", event => {
  if (event.source !== platformWindow && event.source !== window.opener) return;
  if (!event.source) return;
  if (event.data?.type === "aiw:agent:connect") {
    const code = event.data.code;
    try {
      if (typeof code !== "string" || code.length > 4096 || !code.startsWith("aiw-agent-v1.")) return;
      const raw = code.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
      const value = JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(raw), char => char.charCodeAt(0))));
      if (new URL(value.platform).origin !== event.origin) return;
      platformSender = { source: event.source, origin: event.origin };
      byId("platform-code").value = code; byId("platform-origin").value = event.origin; platformSetupLoaded = true;
      byId("platform-connect-proposal").textContent = event.origin;
      switchView("setup", true); byId("platform-setup-card").scrollIntoView({ block: "start" });
      feedback("已收到平台连接请求，请核对平台地址，再点击确认连接。", "ok");
    } catch (_) { feedback("平台连接信息无法读取，请在向导重新发送。", "error"); }
  } else if (event.data?.type === "aiw:agent:authorized" && event.origin === snapshot?.platformConnection?.platform) {
    assistantOperation("platform/check", {}, "代理设置已确认，等待中的任务将继续。");
  }
});
if (window.opener) window.opener.postMessage({ type: "aiw:agent:ready" }, "*");
byId("platform-connect-form").addEventListener("submit", async event => {
  event.preventDefault();
  const form = event.currentTarget, control = form.querySelector('[type="submit"]');
  if (control.disabled) return;
  control.disabled = true;
  try {
    const origin = new URL(byId("platform-origin").value);
    if (origin.username || origin.password || origin.search || origin.hash || origin.pathname !== "/") throw new Error("请输入完整的 Control Plane 地址。");
    const local = isLocalPlatformOrigin(origin.href);
    if (!local && origin.protocol !== "https:") throw new Error("远程 Control Plane 必须使用 HTTPS。");
    const { result } = await post(local ? "platform/connect-local" : "platform/connect", local
      ? { platform: origin.origin } : { code: byId("platform-code").value });
    platformNeedsRepair = false;
    byId("platform-code").value = "";
    if (platformSender) platformSender.source.postMessage({ type: "aiw:agent:connected", ownerId: result.ownerId, agentId: result.agentId }, platformSender.origin);
    byId("platform-next").href = result.nextUrl;
    byId("platform-next").hidden = local;
    const message = local ? "本机秘书已自动登记到默认管理员，并获得计划与任务管理权限。"
      : result.approvalState === "PENDING"
        ? "Agent 已配对但仍处于禁用状态。请在 Control Plane 确认身份、选择是否设置初始授权，并启用当前进程。"
        : result.approvalState === "APPROVED"
          ? "Agent 已连接。若当前进程尚未启用，请在 Control Plane 明确启用。"
          : "Agent 已连接，请在 Control Plane 的 Agent 页面检查配对状态。";
    feedback(message, local || result.approvalState === "APPROVED" ? "ok" : "warn");
  } catch (error) { feedback(errorMessage(error), "error"); }
  finally { control.disabled = false; }
});
byId("chat-memory-panel").addEventListener("toggle", event => { if (event.currentTarget.open) renderMemories(); });
byId("chat-summarize").addEventListener("click", async event => {
  const id = formFor("chat").elements.workId.value;
  const session = (snapshot.conversations || []).find(item => item.id === id);
  if (!session) return;
  event.currentTarget.disabled = true;
  await assistantOperation("conversation/summary", { id, revision: session.revision }, "摘要已开始生成，完成后会自动显示。");
  renderChatContext(formFor("chat").elements.workId.value);
});
byId("chat-delete").addEventListener("click", async event => {
  const control = event.currentTarget;
  const id = formFor("chat").elements.workId.value;
  const session = (snapshot.conversations || []).find(item => item.id === id);
  if (!session) return;
  const deleteMemories = byId("chat-delete-mode").value === "all";
  const question = deleteMemories ? "永久删除这段对话及其派生记忆？关联工作和已有备份会保留。" : "永久删除这段对话，但保留长期记忆？关联工作和已有备份会保留。";
  if (!await window.aiwConfirm(question, { danger: true, target: session.title || "" })) return;
  control.disabled = true;
  try {
    await post("conversation/delete", { id, revision: session.revision, deleteMemories });
    chatPages.delete(id); chatVisible.delete(id);
    formFor("chat").elements.workId.value = "";
    renderedChatWork = null; renderChat();
    feedback("对话已删除。", "ok");
  } catch (error) { feedback(errorMessage(error), "error"); }
  finally { renderChatContext(formFor("chat").elements.workId.value); }
});
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

// Keep only IDs and request digests across reloads, never private chat text.
async function chatSubmission(data) {
  const bytes = new TextEncoder().encode(JSON.stringify({ workId: data.workId || "", text: data.text }));
  const digest = Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)), b => b.toString(16).padStart(2, "0")).join("");
  const key = "secretary-chat-submit:" + digest;
  const id = sessionStorage.getItem(key) || crypto.randomUUID();
  sessionStorage.setItem(key, id);
  data.requestId = id;
  return key;
}

document.querySelectorAll("form[data-route]").forEach(form => form.addEventListener("submit", async event => {
  event.preventDefault();
  if (form.getAttribute("aria-busy") === "true") return;
  const route = form.dataset.route;
  const data = Object.fromEntries(new FormData(form));
  const immediate = route === "schedule" && data.executionMode === "now";
  const submit = form.querySelector('[type="submit"]');
  if (submit.disabled) return;
  form.setAttribute("aria-busy", "true"); submit.disabled = true;
  const version = previewVersions[route];
  let chatSubmissionKey;
  try {
    if (route === "chat") chatSubmissionKey = await chatSubmission(data);
    let requestRoute = route;
    if (route === "schedule") {
      if (immediate) {
        requestRoute = "run-work";
        data.id = data.workId;
        delete data.workId;
        delete data.dueMs;
      }
      delete data.executionMode;
    }
    if ("revision" in data) data.revision = Number(data.revision);
    if ("requestVersion" in data) data.requestVersion = Number(data.requestVersion);
    if ("expiresAt" in data) { if (data.expiresAt) data.expiresAt = new Date(data.expiresAt).getTime(); else delete data.expiresAt; }
    if ("dueMs" in data) { data.dueMs = new Date(data.dueMs).getTime(); data.timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone; }
    if (route === "policy") { data.confirmed = data.confirmed === "on"; if (data.deny.trim()) data.deny = parseInput(data.deny, "禁止范围"); else delete data.deny; }
    if (route === "record") data.data = parseInput(data.data, "私人内容");
    if (route === "settings") {
      data.roots = data.roots.split(/\r?\n/).map(value => value.trim()).filter(Boolean);
      if (knowledgeSettingsDirty) data.knowledgeEnabled = !!form.elements.knowledgeEnabled?.checked;
      else { delete data.knowledgeEnabled; delete data.knowledgeBaseUrl; delete data.knowledgeToken; }
      if (data.command.trim()) data.command = parseInput(data.command, "模型程序参数"); else delete data.command;
    }
    const { result, refreshed } = await post(requestRoute, data);
    if (chatSubmissionKey && result.state !== "UNKNOWN") sessionStorage.removeItem(chatSubmissionKey);
    if (route === "settings") {
      settingsLoaded = false;
      knowledgeSettingsDirty = false;
      form.elements.key.value = "";
      if (form.elements.knowledgeToken) form.elements.knowledgeToken.value = "";
      renderAssistant();
    }
    const current = applyResult(route, result, version);
    updateSavedEditor(route, data, refreshed);
    if (route === "login") form.reset();
    if (route === "chat" && result.state !== "UNKNOWN" && form.elements.text.value === data.text) form.elements.text.value = "";
    if (route === "chat" && result.state !== "UNKNOWN" && result.workId) { form.elements.workId.value = result.workId; renderChat(); }
    let message = route === "login" ? "个人服务已接受配对。" : (refreshed ? "操作已记录，状态已更新。" : "操作已返回结果。");
    if (route === "schedule" && result.report) message = result.report.message;
    if (immediate && result.state === "SCHEDULED") message = "已提交马上执行，进度和结果会自动更新；秘书暂停时需先点击“继续工作”。";
    if (route === "chat") message = result.state === "UNKNOWN" ? result.reply : "请求已记录，回复会自动显示；重发的原请求未重复排队。";
    if (route === "work") message = "工作已保存，并已选入安排列表。";
    if (route === "settings") message = "设置已保存，可以直接聊天或安排工作。";
    if (route === "question-preview" || route === "goal-preview") message = current ? "本地预览已生成，请核对精确内容后确认提交。" : "内容已变化，请重新生成本地预览。";
    if (route === "goal-submit" || route === "goal-receipt") message = "目标提案：" + (stateLabels[result.state]?.[0] || result.state || "待核对") + "。" + (result.note || "平台采纳前不会创建任务。");
    if (!refreshed) message += " 服务已返回结果，但状态读取失败。请先刷新状态、核对原回执，避免重复提交。";
    feedback(message, !refreshed || !current || route === "schedule" && result.state !== "SCHEDULED" ? "warn" : "ok", byId("personal-app").hidden);
  } catch (error) {
    if (chatSubmissionKey && error.httpStatus >= 400 && error.httpStatus < 500) sessionStorage.removeItem(chatSubmissionKey);
    if (chatSubmissionKey && !error.httpStatus) {
      feedback("消息提交结果尚未确认。可在此页面重发相同内容，系统将使用原提交标识核对，避免重复执行。" + errorMessage(error), "warn");
      return;
    }
    feedback(errorMessage(error), "error", route === "login" || byId("personal-app").hidden);
  } finally {
    form.removeAttribute("aria-busy");
    submit.disabled = submit.hasAttribute("data-projection-submit") && !form.elements.projectionJson.value;
  }
}));

for (const [id, enabled] of [["enable", true], ["pause", false]]) byId(id).addEventListener("click", async () => {
  byId("enable").disabled = true; byId("pause").disabled = true;
  try {
    const { refreshed } = await post("assistant/control", { paused: !enabled });
    feedback(refreshed ? (enabled ? "秘书已继续工作，安排会自动处理。" : "秘书已暂停，后续工具操作会停止；已完成操作保留。") : "状态读取失败，请刷新查看秘书状态。", refreshed ? "ok" : "warn", byId("personal-app").hidden);
  } catch (error) { feedback(errorMessage(error), "error"); }
  finally { byId("enable").disabled = false; byId("pause").disabled = false; }
});
byId("show-advanced").addEventListener("click", event => {
  const show = event.currentTarget.getAttribute("aria-expanded") !== "true";
  event.currentTarget.setAttribute("aria-expanded", String(show));
  for (const element of document.querySelectorAll("[data-advanced]")) element.hidden = !show;
  event.currentTarget.textContent = show ? "隐藏高级功能" : "显示高级功能";
});
byId("check-model").addEventListener("click", async event => {
  const button = event.currentTarget;
  button.disabled = true;
  feedback("正在检查模型连接，请稍候……");
  try { await assistantOperation("settings/check", {}, "模型连接正常。"); }
  finally { button.disabled = false; }
});
byId("check-model").textContent = "检查已保存的模型配置";
for (const name of ["provider", "authMode"]) formFor("settings").elements[name].addEventListener("change", () => {
  const select = formFor("settings").elements.connectionId;
  select.value = ""; select.dataset.savedId = ""; delete select.dataset.signature;
  renderSecretaryModels();
});
formFor("settings").elements.connectionId.addEventListener("change", event => {
  event.target.dataset.savedId = event.target.value;
  renderSecretaryModels();
});
byId("model-login").addEventListener("click", async () => {
  if (modelLoginBusy) return;
  modelLoginBusy = true;
  const popup = window.open("about:blank", "_blank");
  if (popup) popup.opener = null;
  renderSecretaryModels();
  try {
    const { result } = await post("model/login", {});
    const url = new URL(result.authorizationUrl);
    if (url.origin !== "https://auth.openai.com" || url.pathname !== "/api/accounts/authorize") throw new Error("登录地址来源无效。");
    modelAuthorizationUrl = url.href;
    if (popup) popup.location.replace(url.href);
    else {
      const link = node("a", "", "打开 ChatGPT 登录页"); link.href = url.href; link.target = "_blank"; link.rel = "noopener noreferrer";
      byId("model-connection-status").append(document.createTextNode(" 浏览器拦截了窗口，请点击："), link);
    }
    feedback(result.note);
  } catch (error) { if (popup) popup.close(); feedback(errorMessage(error), "error"); }
  finally { modelLoginBusy = false; renderSecretaryModels(); }
});
byId("model-login-cancel").addEventListener("click", () => assistantOperation("model/cancel", {}, "原登录已取消。"));
byId("model-disconnect").addEventListener("click", async () => {
  const identity = formFor("settings").elements.connectionId.value;
  const connection = snapshot.assistant.models.connections.find(item => item.id === identity);
  if (!connection) return;
  await assistantOperation("model/disconnect", { id: identity, version: connection.version }, "本机连接已停用；需要时请重新登录。远端授权撤销尚未确认。");
});
byId("model-import").addEventListener("click", async () => {
  try {
    const registration = JSON.parse(byId("model-import-metadata").value);
    const { result } = await post("model/import", { registration });
    byId("model-import-metadata").value = "";
    feedback(result.note);
  } catch (error) { feedback(errorMessage(error), "error"); }
});
byId("enable-platform").addEventListener("click", () => assistantOperation("enable", { enabled: true }, "已连接当前已授权的专业平台；日常助理设置不受影响。"));
byId("refresh").addEventListener("click", async () => {
  byId("refresh").disabled = true;
  try { await refresh(); feedback("已读取最新私人状态。"); }
  catch (error) { feedback(errorMessage(error), "error", byId("personal-app").hidden); }
  finally { byId("refresh").disabled = false; }
});
byId("close-work-detail").addEventListener("click", () => byId("work-detail").close());
byId("work-detail").addEventListener("close", () => {
  displayedWork = null;
  byId("work-detail-content").replaceChildren();
  if (snapshot) render();
});
byId("update-work-detail").addEventListener("click", async () => {
  const id = displayedWork?.id;
  if (!id) return;
  byId("update-work-detail").disabled = true;
  try { await refresh(); if (byId("work-detail").open && displayedWork?.id === id) showWorkDetails(id); }
  catch (error) { byId("work-detail-note").textContent = "更新失败，保留当前详情。" + errorMessage(error); }
  finally { byId("update-work-detail").disabled = false; }
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

window.addEventListener("aiw:locale-changed", () => { if (snapshot) render(); });

// Report a missing/failed language runtime separately: ordinary refresh feedback
// must not hide this error, and a select must not pretend a switch succeeded.
function updateLocaleAvailability() {
  const selector = byId("console-locale"), status = byId("locale-status");
  if (!selector || !status) return;
  const ready = window.aiwI18n?.ready === true;
  selector.disabled = !ready;
  status.hidden = ready;
  status.textContent = ready ? "" : window.aiwI18n
    ? "语言组件初始化失败。Language initialization failed. 言語の初期化に失敗しました。"
    : "语言脚本未加载，请重启个人服务。Language script missing; restart the personal service. 言語スクリプト未読込：個人サービスを再起動してください。";
  status.dataset.i18nSkip = "";
}
window.addEventListener("aiw:i18n-ready", updateLocaleAvailability);
updateLocaleAvailability();
