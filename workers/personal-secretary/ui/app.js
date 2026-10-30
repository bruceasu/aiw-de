"use strict";
let csrf = "";
const status = document.getElementById("status");
async function refresh() {
  const response = await fetch("/api/state", { credentials: "same-origin", cache: "no-store" });
  if (!response.ok) throw new Error("请使用本服务控制台的一次性配对码登录。");
  const state = await response.json(); csrf = state.csrf; delete state.csrf;
  document.getElementById("state").textContent = JSON.stringify(state, null, 2);
}
async function post(route, data) {
  const response = await fetch("/api/" + route, { method: "POST", credentials: "same-origin", headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf }, body: JSON.stringify(data) });
  const result = await response.json();
  if (!response.ok) throw new Error(result.error || "操作未确认，请刷新状态并核对原请求回执。");
  status.textContent = "私人操作已记录。"; await refresh(); return result;
}
document.querySelectorAll("form[data-route]").forEach(form => form.addEventListener("submit", async event => {
  event.preventDefault(); const data = Object.fromEntries(new FormData(form)); const route = form.dataset.route;
  try {
    if ("revision" in data) data.revision = Number(data.revision);
    if ("expiresAt" in data) { if (data.expiresAt) data.expiresAt = new Date(data.expiresAt).getTime(); else delete data.expiresAt; }
    if ("dueMs" in data) { data.dueMs = new Date(data.dueMs).getTime(); data.timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone; }
    if (route === "policy") { data.confirmed = data.confirmed === "on"; if (data.deny.trim()) data.deny = JSON.parse(data.deny); else delete data.deny; }
    if (route === "record") data.data = JSON.parse(data.data);
    await post(route, data);
  } catch (error) { status.textContent = error.message; }
}));
document.getElementById("enable").addEventListener("click", () => post("enable", { enabled: true }).catch(error => status.textContent = error.message));
document.getElementById("pause").addEventListener("click", () => post("enable", { enabled: false }).catch(error => status.textContent = error.message));
document.getElementById("refresh").addEventListener("click", () => refresh().catch(error => status.textContent = error.message));
refresh().catch(() => {});
