"use strict";
const result = document.getElementById("authority-result");
async function post(path, body = {}) {
  const response = await fetch("/api/r1/authority" + path, { method: "POST", credentials: "same-origin", headers: { "Content-Type": "application/json", "X-CSRF-Token": document.querySelector('meta[name="csrf-token"]').content }, body: JSON.stringify(body) });
  if (!response.ok) throw new Error("操作未获准（" + response.status + "）。请刷新状态并检查当前角色、版本和能力。");
  result.textContent = "操作已记录。请刷新页面查看当前状态。";
  return response.json();
}
function form(id, submit) { document.getElementById(id).addEventListener("submit", async event => { event.preventDefault(); const fields = Object.fromEntries(new FormData(event.currentTarget)); try { await submit(fields); } catch (error) { result.textContent = error.message; } }); }
async function hash(value) { return Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value))), byte => byte.toString(16).padStart(2, "0")).join(""); }
document.getElementById("bind-owner").addEventListener("click", async () => { try { const value = await post("/owners/bind"); result.textContent = "Owner ID：" + value.ownerId; } catch (error) { result.textContent = error.message; } });
const budgetAction = document.createElement("option");budgetAction.value = "A18_BUDGET";budgetAction.textContent = "限定预算追加（维度、增量、累计上限）";document.querySelector('#issue-grant select[name="action"]').append(budgetAction);
form("enroll-worker", value => post("/workers", { ...value, readScope: value.readScope.split(/\r?\n/).map(row => row.trim()).filter(Boolean) }));
form("control-worker", value => post("/workers/" + encodeURIComponent(value.workerId) + "/" + value.operation, { generation: Number(value.generation), workerBoot: value.workerBoot || null }));
form("issue-grant", value => post("/grants", { ownerId: value.ownerId, workerId: value.workerId, expiresAt: new Date(value.expiresAt).getTime(), scope: { action: value.action, targetId: value.targetId, step: value.step === "" ? null : Number(value.step), effect: "ALLOW", states: value.states.split(",").map(row => row.trim()), minimumRounds: Number(value.minimumRounds), maximumUses: Number(value.maximumUses), parameters: JSON.parse(value.parameters) } }));
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
