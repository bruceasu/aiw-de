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
    document.getElementById("agent-pairing-code").value = value.connectionCode;
    document.getElementById("agent-copy-code").disabled = false;
    show("配对码已生成，5 分钟后失效。请仅交给目标 Agent 客户端；Agent 建立受限会话后，在此页一次确认精确授权。");
  } catch (error) { show(error.message, true); }
  finally { button.disabled = false; }
});
document.getElementById("agent-copy-code").addEventListener("click", async () => {
  const code = document.getElementById("agent-pairing-code");
  if (!code.value) return;
  try { await navigator.clipboard.writeText(code.value); show("配对码已复制。"); }
  catch (_) { code.focus(); code.select(); show("无法访问剪贴板，配对码已选中，请手动复制。", true); }
});
document.querySelectorAll("[data-approve-agent]").forEach(form => form.addEventListener("submit", async event => {
  event.preventDefault();
  const button = form.querySelector('[type="submit"]');
  const value = Object.fromEntries(new FormData(form));
  let parameters;
  try { parameters = JSON.parse(value.parameters || "{}"); }
  catch (_) { show("动作参数必须是有效 JSON。", true); return; }
  if (!parameters || typeof parameters !== "object" || Array.isArray(parameters)) {
    show("动作参数必须是 JSON 对象。", true); return;
  }
  const expiresAt = new Date(value.expiresAt).getTime();
  if (!Number.isFinite(expiresAt)) { show("请选择有效的 Grant 到期时间。", true); return; }
  const scope = {
    action: value.action,
    targetId: value.targetId.trim(),
    step: value.step === "" ? null : Number(value.step),
    effect: "ALLOW",
    states: value.states.split(",").map(state => state.trim()).filter(Boolean),
    minimumRounds: Number(value.minimumRounds),
    maximumUses: Number(value.maximumUses),
    parameters
  };
  button.disabled = true;
  try {
    const result = await post(`/agents/${encodeURIComponent(form.closest("[data-agent-id]").dataset.agentId)}/approve-pairing`, {
      generation: Number(value.generation), agentBoot: value.agentBoot, scope, expiresAt
    });
    show(`Agent 已注册；Grant ${result.grantId} 已批准，当前进程已启用。重启后仍需重新启用。`);
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
document.querySelectorAll('[data-agent-id][data-agent-active="true"]').forEach(loadRotations);
document.querySelectorAll("[data-revoke-agent]").forEach(button => button.addEventListener("click", async () => {
  const card = button.closest("[data-agent-id]");
  const agentId = card?.dataset.agentId;
  if (!agentId || !window.confirm(`撤销 Agent ${agentId} 的凭据？当前会话将失效，身份及授权历史会保留；重新连接必须重新配对。`)) return;
  button.disabled = true;
  try {
    await post(`/agents/${encodeURIComponent(agentId)}/credential/revoke`);
    show("Agent 凭据已撤销，旧会话和旧凭据已失效。");
    window.location.reload();
  } catch (error) { show(error.message, true); button.disabled = false; }
}));
