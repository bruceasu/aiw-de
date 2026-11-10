"use strict";
const feedback = document.getElementById("agent-feedback");
const csrf = document.querySelector('meta[name="csrf-token"]').content;
function show(message, error = false) {
  feedback.hidden = false;
  feedback.dataset.tone = error ? "error" : "success";
  feedback.textContent = message;
}
async function post(path, body = {}) {
  const response = await fetch("/api/r1/authority" + path, { method: "POST", credentials: "same-origin",
    headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf }, body: JSON.stringify(body) });
  const value = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(value.message || `请求失败 (${response.status})`);
  return value;
}
async function get(path) {
  const response = await fetch("/api/r1/authority" + path, { credentials: "same-origin" });
  const value = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(value.message || `读取失败 (${response.status})`);
  return value;
}
const profile = document.getElementById("agent-profile");
const origin = document.getElementById("agent-origin");
function setDefaultOrigin() {
  if (profile.value === "REMOTE") origin.value = window.location.origin;
  else origin.value = `http://127.0.0.1:${window.location.port || "8080"}`;
}
profile.addEventListener("change", setDefaultOrigin);
setDefaultOrigin();
document.getElementById("agent-invitation").addEventListener("submit", async event => {
  event.preventDefault();
  const button = event.currentTarget.querySelector('[type="submit"]');
  button.disabled = true;
  try {
    const value = await post("/agents/invitations", { profile: profile.value, serverOrigin: origin.value.trim() });
    document.getElementById("agent-pairing-code").textContent = value.connectionCode;
    document.getElementById("agent-pairing-result").hidden = false;
    document.getElementById("agent-copy-code").disabled = false;
    show("配对码已生成，5 分钟后失效。请仅交给目标 Agent 客户端；Agent 连接成功后，点击“刷新待确认 Agent”，再确认身份并启用当前进程。");
  } catch (error) { show(error.message, true); }
  finally { button.disabled = false; }
});
document.getElementById("agent-copy-code").addEventListener("click", async () => {
  const code = document.getElementById("agent-pairing-code");
  const text = code.textContent.trim();
  if (!text) return;
  try { await navigator.clipboard.writeText(text); show("配对码已复制。"); }
  catch (_) {
    const range = document.createRange();
    range.selectNodeContents(code);
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
    show("无法访问剪贴板，配对码已选中，请手动复制。", true);
  }
});
document.getElementById("agent-refresh-pending").addEventListener("click", () => window.location.reload());
document.querySelectorAll("[data-approve-agent]").forEach(form => form.addEventListener("submit", async event => {
  event.preventDefault();
  const button = form.querySelector('[type="submit"]');
  const value = Object.fromEntries(new FormData(form));
  button.disabled = true;
  try {
    await post(`/agents/${encodeURIComponent(form.closest("[data-agent-id]").dataset.agentId)}/approve-pairing`, {
      generation: Number(value.generation), agentBoot: value.agentBoot
    });
    show("秘书身份已确认，当前进程已启用；具体业务动作仍需单独授权。重启后仍需重新启用。");
    window.location.reload();
  } catch (error) { show(error.message, true); button.disabled = false; }
}));
async function loadRotations(card) {
  const agentId = card.dataset.agentId;
  const host = card.querySelector("[data-agent-rotations]");
  try {
    const rows = await get(`/agents/${encodeURIComponent(agentId)}/certificate-rotations`);
    const pending = Array.isArray(rows) ? rows.filter(row => row.state === "PENDING") : [];
    host.replaceChildren();
    if (!pending.length) { host.textContent = "没有待批准的证书轮换。"; return; }
    for (const rotation of pending) {
      const item = document.createElement("div");
      const fingerprint = document.createElement("code"); fingerprint.textContent = rotation.client_cert_sha256;
      const approve = document.createElement("button"); approve.className = "btn primary"; approve.type = "button"; approve.textContent = "批准此指纹";
      const reject = document.createElement("button"); reject.className = "btn ghost"; reject.type = "button"; reject.textContent = "拒绝";
      for (const [button, action] of [[approve, "approve"], [reject, "reject"]]) button.addEventListener("click", async () => {
        button.disabled = true;
        try {
          await post(`/agents/${encodeURIComponent(agentId)}/certificate-rotations/${encodeURIComponent(rotation.id)}/${action}`);
          show(action === "approve" ? "证书指纹已批准；旧进程会话已失效。" : "证书轮换已拒绝。");
          await loadRotations(card);
        } catch (error) { show(error.message, true); button.disabled = false; }
      });
      item.append(document.createTextNode("待批准 SHA-256："), fingerprint, document.createTextNode(" "), approve, document.createTextNode(" "), reject);
      host.append(item);
    }
  } catch (error) { host.textContent = error.message; }
}
document.querySelectorAll('[data-agent-id][data-agent-active="true"][data-agent-remote="true"]').forEach(loadRotations);
document.querySelectorAll("[data-revoke-agent]").forEach(button => button.addEventListener("click", async () => {
  const card = button.closest("[data-agent-id]");
  const agentId = card?.dataset.agentId;
  if (!agentId) return;
  if (typeof window.aiwConfirm !== "function") { show("确认弹窗尚未就绪，请刷新页面后重试。", true); return; }
  if (!await window.aiwConfirm("当前会话将失效，身份及授权历史会保留；重新连接必须重新配对。", {
    title: "撤销 Agent 凭据", target: agentId, acceptLabel: "确认撤销", danger: true
  })) return;
  button.disabled = true;
  try {
    await post(`/agents/${encodeURIComponent(agentId)}/credential/revoke`);
    show("Agent 凭据已撤销，旧会话和旧凭据已失效。");
    window.location.reload();
  } catch (error) { show(error.message, true); button.disabled = false; }
}));
