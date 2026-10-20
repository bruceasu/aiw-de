(() => {
  "use strict";
  const $ = id => document.getElementById(`delegation-${id}`);
  const catalog = {
    A01: ["Continue one full execution and independent review", "scope", "modelGrantId"],
    A02: ["Choose approved models for execution or review", "profiles", "catalogVersion", "role"],
    A03: ["Retry a failed task", "taskId", "failureCategory", "sideEffectsConfirmed", "minimumIntervalSeconds"],
    A04: ["Review the existing result again", "warningConfirmed"], A05: ["Confirm and start a Plan"],
    A06: ["Jump to a Plan step", "targetStep"], A07: ["Request a safe stop"], A08: ["Withdraw remaining delegation"],
    A09: ["Record manual Plan completion"], A10: ["Cancel a Plan using its existing rollback rules"],
    A11_APPROVE: ["Approve an independent engineering result"], A11_REJECT: ["Reject an independent engineering result"],
    A12_CREATE: ["Create a reviewed document publication Plan", "reviewTaskId", "targetPath"],
    A12_EXECUTE: ["Execute a fixed document publication Plan", "fileId", "reviewTaskId", "targetPath", "sourceHash"],
    A13: ["Adjust GLOBAL Fleet concurrency", "maxParallelism", "minimum", "maximum", "allowUnlimited"],
    A14: ["Drain an ordinary Worker"], A15: ["Mark an ordinary Worker offline after leases finish"],
    A16: ["Archive a finished Plan"], A17_SCHEDULE: ["Schedule delayed deletion of an archived Plan"], A17_UNDO: ["Undo delayed deletion within its retention window"]
  };
  const names = { scope: "Exact correction scope", modelGrantId: "Required A02 grant ID, optional", profiles: "Allowed approved profile IDs, comma separated", catalogVersion: "Confirmed model catalog version", role: "Affected role: EXECUTION, REVIEW or BOTH", taskId: "Exact failed task ID, or CURRENT", failureCategory: "Allowed failure category", sideEffectsConfirmed: "Allow possible duplicate side effects: true", minimumIntervalSeconds: "Minimum retry interval in seconds, optional", warningConfirmed: "Confirm review-only recovery: true", targetStep: "Destination step, starting at 0", reviewTaskId: "Dependent PASS review task ID", targetPath: "Exact project document path", fileId: "Exact output file ID", sourceHash: "Exact source SHA-256", maxParallelism: "Desired concurrency; 0 means unlimited", minimum: "Minimum permitted concurrency", maximum: "Maximum permitted concurrency", allowUnlimited: "Allow unlimited concurrency explicitly: true or false", reason: "Reason" };
  const query = new URLSearchParams(location.search);
  let data, selected = null, templateSource = null, dirty = true, draftId = null, busy = false;
  const split = value => value.split(",").map(x => x.trim()).filter(Boolean);
  function node(tag, text, cls) { const item = document.createElement(tag); if (text !== undefined) item.textContent = text; if (cls) item.className = cls; return item; }
  function button(text, action) { const item = node("button", text, "btn ghost small-btn"); item.type = "button"; item.onclick = () => run(action); return item; }
  async function api(path = "", body) {
    const csrf = document.querySelector('meta[name="csrf-token"]').content;
    const response = await fetch(`/api/r1/delegation${path}${body === undefined ? "" : `?_csrf=${encodeURIComponent(csrf)}`}`, {
      method: body === undefined ? "GET" : "POST", headers: body === undefined ? {} : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    let result; try { result = await response.json(); } catch { result = {}; }
    if (!response.ok) throw new Error(result.error || `Request failed (${response.status})`);
    return result;
  }
  async function run(action) {
    if (busy) return; busy = true; $("status").textContent = "Working…";
    try { await action(); $("status").textContent = "Updated. Review the current saved facts below."; }
    catch (error) { $("status").textContent = error.message; }
    finally { busy = false; }
  }
  function parameters(values = {}) {
    $("parameters").replaceChildren();
    for (const key of ["reason", ...catalog[$("action").value].slice(1)]) {
      const label = node("label", names[key] || key, "field"); const input = node(key === "reason" || key === "scope" ? "textarea" : "input");
      input.dataset.parameter = key; input.value = values[key] || ""; input.maxLength = key === "reason" ? 2000 : 4000; label.append(input); $("parameters").append(label);
    }
    $("step").disabled = !["A01", "A02", "A03", "A04"].includes($("action").value);
    $("action-help").textContent = catalog[$("action").value][0];
  }
  function rule() {
    const values = {};
    document.querySelectorAll("[data-parameter]").forEach(input => { if (input.value.trim()) values[input.dataset.parameter] = input.value.trim(); });
    return { action: $("action").value, targetId: $("target").value.trim(), step: $("step").disabled || !$("step").value ? null : Number($("step").value), effect: $("effect").value,
      states: split($("states").value), minimumRounds: Number($("rounds").value), maximumUses: Number($("limit").value), parameters: values, supersedes: split($("supersedes").value) };
  }
  function changed() { dirty = true; $("confirmed").checked = false; $("preview").textContent = JSON.stringify(rule(), null, 2); }
  function fill(value, saved = false) {
    const r = value.rule || {}; $("action").value = catalog[r.action] ? r.action : "A01"; parameters(r.parameters || {});
    $("worker").value = value.workerId || query.get("workerId") || ""; $("target").value = r.targetId || query.get("planId") || "";
    $("step").value = r.step ?? ""; $("effect").value = r.effect || "ALLOW"; $("states").value = (r.states || []).join(", ");
    $("rounds").value = r.minimumRounds ?? 0; $("limit").value = r.maximumUses ?? ""; $("supersedes").value = (r.supersedes || []).join(", ");
    $("text").value = value.originalText || "";
    if (value.expiresMs > 0) { const date = new Date(value.expiresMs); $("expiry").value = new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16); }
    else $("expiry").value = "";
    selected = saved ? value : null; changed(); dirty = !saved;
    templateSource = value.templateId ? { id: value.templateId, revision: value.templateRevision } : null;
    $("selected").textContent = saved ? `${value.id} · revision ${value.revision} · ${value.state}${value.templateId ? ` · template ${value.templateId}:${value.templateRevision}` : ""}` : "New unconfirmed rule";
    $("history-detail").textContent = "";
  }
  function table(container, headings, rows) {
    const wrap = node("div", undefined, "table-wrap"), grid = node("table"), head = node("tr"); headings.forEach(text => head.append(node("th", text)));
    const thead = node("thead"); thead.append(head); grid.append(thead); const body = node("tbody");
    for (const values of rows) { const row = node("tr"); for (const value of values) { const cell = node("td"); cell.append(value instanceof Node ? value : document.createTextNode(String(value ?? ""))); row.append(cell); } body.append(row); }
    grid.append(body); wrap.append(grid); container.replaceChildren(wrap); if (!rows.length) container.append(node("p", "No records.", "help"));
  }
  async function reload() {
    data = await api();
    const oldWorker = $("worker").value; $("worker").replaceChildren(node("option", "Choose an enrolled Worker")); $("worker").firstChild.value = "";
    data.identities.forEach(identity => { const option = node("option", identity.workerId); option.value = identity.workerId; $("worker").append(option); }); $("worker").value = oldWorker || selected?.workerId || query.get("workerId") || "";
    table($("identities"), ["Worker", "Process", "Delegation", "Human control"], data.identities.map(identity => {
      const controls = node("div", undefined, "form-actions");
      if (data.administrator) controls.append(button(identity.enabled ? "Disable delegation" : "Explicitly enable delegation", async () => {
        await api(`/identities/${encodeURIComponent(identity.workerId)}/enabled`, { generation: identity.generation, enabled: !identity.enabled, confirmed: true }); await reload();
      }));
      return [identity.workerId, `${identity.processState} · ${identity.live ? "live heartbeat" : "not currently live"}`, identity.enabled ? "Human enabled" : "Disabled", controls];
    }));
    if ($("legacy")) table($("legacy"), ["Request", "Started", "Human recovery"], data.legacyRequests.map(request => [
      `${request.method} ${request.path}`, new Date(request.startedMs).toLocaleString(), request.recoverable ? button("Recover after verifying the original process stopped", async () => {
        await api(`/legacy-requests/${request.id}/recover`, { confirmedStopped: $("recovery-confirmed").checked, reason: $("recovery-reason").value }); await reload();
      }) : "This server is still processing the request; wait for completion"
    ]));
    $("grants").replaceChildren();
    const visible = data.grants.filter(view => !query.get("workerId") || view.grant.workerId === query.get("workerId"))
      .filter(view => !query.get("planId") || view.grant.rule.targetId === query.get("planId"));
    for (const view of visible) {
      const grant = view.grant, card = node("div", undefined, "card"); card.append(node("strong", `${grant.rule.action} · ${grant.rule.targetId}`), node("p", `${grant.workerId} · ${grant.state} · revision ${grant.revision}`), node("p", view.availability, "help"), button("Inspect and edit", () => fill(grant, true))); $("grants").append(card);
    }
    if (!visible.length) $("grants").append(node("p", "No matching rules. Copying or describing a rule does not enable it.", "help"));
    table($("budgets"), ["Shared target / action", "Total", "Used, including reservations", "Reserved", "Remaining"], data.budgets.map(b => [ `${b.target} / ${b.action}`, b.limit, b.used, b.reserved, Math.max(0, b.limit - b.used) ]));
    table($("requests"), ["Request / action", "State", "Evidence and reason", "Human control"], data.requests.map(request => {
      const controls = node("div", undefined, "form-actions");
      if (request.state === "HUMAN") controls.append(button("Explicitly return for a fresh decision", async () => { await api(`/requests/${request.id}/return`, {}); await reload(); }));
      else if (request.state === "PENDING") controls.append(button("Take over this request", async () => { await api(`/requests/${request.id}/human`, {}); await reload(); }));
      const details = node("details"), text = node("pre", request.snapshot); details.append(node("summary", request.detail), text);
      return [`${request.id} · ${request.grantId}:${request.grantRevision}`, request.state, details, controls];
    }));
    table($("audit"), ["Time", "Actor", "Event / request", "Details"], data.audit.map(row => [new Date(row.created_ms).toLocaleString(), row.actor, `${row.event} · ${row.request_id || row.grant_id || ""}`, row.detail]));
    const templates = await api("/templates");
    table($("templates"), ["Template", "Origin", "Actions"], templates.map(template => {
      const controls = node("div", undefined, "form-actions");
      controls.append(button("Use as a new draft", () => { fill({ rule: { ...template.rule, targetId: "" }, originalText: template.originalText, templateId: template.id, templateRevision: template.revision }); $("worker").value = ""; $("target").value = ""; changed(); }),
        button("Edit template fields", () => { fill({ rule: template.rule, originalText: template.originalText, templateId: template.id, templateRevision: template.revision }); $("selected").textContent = `Editing template ${template.id}:${template.revision}; active grants stay unchanged`; }),
        button("Delete template", async () => { await api(`/templates/${template.id}/delete`, { revision: template.revision }); await reload(); }));
      return [`${template.id}:${template.revision} · ${template.rule.action}`, `${template.sourceGrant || ""}:${template.sourceRevision || ""}`, controls];
    }));
  }
  for (const [id, description] of Object.entries(catalog)) { const option = node("option", `${id} · ${description[0]}`); option.value = id; $("action").append(option); }
  $("action").onchange = () => { parameters(); $("states").value = $("action").value === "A13" ? "GLOBAL" : ["A14", "A15"].includes($("action").value) ? "ACTIVE" : $("action").value.startsWith("A11") ? "PENDING" : $("action").value === "A12_CREATE" ? "COMPLETED" : "CONFIRMED"; changed(); };
  $("rule-form").addEventListener("input", event => { if (event.target.id !== "delegation-confirmed") changed(); }); $("text").addEventListener("input", changed);
  $("rule-form").onsubmit = event => { event.preventDefault(); run(async () => {
    const expiresMs = $("expiry").value ? new Date($("expiry").value).getTime() : 0;
    const saved = await api("/grants", { id: selected?.id || null, revision: selected?.revision ?? null, workerId: $("worker").value, originalText: $("text").value, rule: rule(), expiresMs, templateId: templateSource?.id || null, templateRevision: templateSource?.revision ?? null });
    fill(saved, true); await reload();
  }); };
  $("enable").onclick = () => run(async () => { if (!selected || dirty || !$("confirmed").checked) throw new Error("Save, inspect and explicitly confirm the exact current revision first."); const saved = await api(`/grants/${selected.id}/enable`, { revision: selected.revision, confirmed: true }); fill(saved, true); await reload(); });
  $("revoke").onclick = () => run(async () => { if (!selected) throw new Error("Select a saved rule."); const saved = await api(`/grants/${selected.id}/revoke`, { revision: selected.revision }); fill(saved, true); await reload(); });
  $("history").onclick = () => run(async () => { if (!selected) throw new Error("Select a saved rule."); $("history-detail").textContent = JSON.stringify(await api(`/grants/${selected.id}/history`), null, 2); });
  $("supplement").onclick = () => run(async () => { if (!selected) throw new Error("Select the rule whose shared budget you want to supplement."); await api(`/grants/${selected.id}/supplement`, { amount: Number($("increment").value) }); await reload(); });
  $("draft").onclick = () => run(async () => { const result = await api("/drafts", { text: $("text").value }); draftId = result.id; $("missing").textContent = `${result.id} is queued. It will remain queued until a secretary Worker is available.`; });
  $("draft-result").onclick = () => run(async () => { if (!draftId) throw new Error("Request a secretary draft first."); const result = await api(`/drafts/${draftId}`); if (result.suggestion?.rule) { fill({ rule: result.suggestion.rule, expiresMs: result.suggestion.expiresMs, originalText: result.originalText }); $("missing").textContent = `Unconfirmed suggestion. Missing: ${(result.suggestion.missing || []).join("; ") || "Review all fields before saving"}`; } else $("missing").textContent = `${result.state} · ${(result.missing || []).join("; ")}`; });
  $("refresh").onclick = () => run(reload); $("new").onclick = () => fill({});
  $("template-save").onclick = () => run(async () => { if (!selected || dirty) throw new Error("Select and save the source rule first."); await api(`/grants/${selected.id}/template`, { revision: selected.revision }); await reload(); });
  $("template-edit").onclick = () => run(async () => { if (!templateSource) throw new Error("Choose a template to edit first."); const updated = await api(`/templates/${templateSource.id}/edit`, { revision: templateSource.revision, originalText: $("text").value, rule: rule() }); templateSource = { id: updated.id, revision: updated.revision }; await reload(); });
  if ($("generate")) $("generate").onclick = () => { const bytes = crypto.getRandomValues(new Uint8Array(32)); $("key").value = btoa(String.fromCharCode(...bytes)).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", ""); };
  if ($("enroll")) $("enroll").onclick = () => run(async () => { await api("/identities", { workerId: $("enroll-worker").value.trim(), key: $("key").value }); $("key").value = ""; await reload(); });
  $("zone").textContent = `Expiry uses your displayed local zone: ${Intl.DateTimeFormat().resolvedOptions().timeZone}. No expiry means the rule cannot be enabled.`;
  fill({}); run(reload);
})();
