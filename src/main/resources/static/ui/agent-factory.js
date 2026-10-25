(() => {
  "use strict";
  const form = document.getElementById("factory-settings");
  if (!form) return;
  const fields = document.getElementById("factory-fields"), save = document.getElementById("factory-save"), status = document.getElementById("factory-status");
  const t = text => window.aiwI18n?.t(text) || text;
  async function call(path, body) {
    const response = await fetch("/api/r1/authority/agent-factory/" + path, { method: "POST", credentials: "same-origin",
      headers: { "Content-Type": "application/json", "X-CSRF-Token": document.querySelector('meta[name="csrf-token"]').content }, body: JSON.stringify(body) });
    const result = await response.json();
    if (!response.ok) throw new Error(result.message || t("工厂设置未保存，请检查登录和管理员权限。"));
    return result;
  }
  function render(result) {
    const settings = result.settings || {};
    const params = new URLSearchParams(location.search);
    for (const name of ["runtime", "model", "effort", "python", "executable", "guardHome"]) {
      const value = settings[name] ?? (name === "runtime" || name === "model" || name === "effort" ? params.get(name) : null);
      if (value != null) form.elements[name].value = value;
    }
    form.elements.automaticWorkers.checked = settings.automaticWorkers ?? true;
    status.textContent = t(settings.model ? "工厂设置已载入。" : "请选择默认模型并保存一次，后续任务由工厂自动准备 Worker。");
    fields.disabled = false; save.disabled = false;
    renderWorkers(result.managedWorkers || []);
  }
  function renderWorkers(workers) {
    const target = document.getElementById("factory-workers"); target.replaceChildren();
    if (!workers.length) target.textContent = t("尚无平台启动的本机 Worker。有任务需要时才启动。");
    for (const worker of workers) {
      const row = document.createElement("p"); row.dataset.i18nSkip = "";
      row.textContent = worker.workerId + " · " + worker.state + (worker.message ? " · " + worker.message : ""); target.append(row);
    }
  }
  document.getElementById("factory-refresh").addEventListener("click", async () => {
    try { const result = await call("state", {}); renderWorkers(result.managedWorkers || []); }
    catch (error) { status.textContent = error.message; }
  });
  form.addEventListener("submit", async event => {
    event.preventDefault(); if (save.disabled || !form.reportValidity()) return;
    const input = Object.fromEntries(new FormData(form)); input.automaticWorkers = form.elements.automaticWorkers.checked;
    save.disabled = true; fields.disabled = true;
    try {
      if (!await window.aiwConfirm(t("保存默认模型和本机 Worker 自动启动设置？") + "\n" + input.runtime + " / " + input.model)) return;
      await call("settings", input); status.textContent = t("工厂设置已保存，等待授权的任务会自动重新检查。");
    }
    catch (error) { status.textContent = error.message; }
    finally { save.disabled = false; fields.disabled = false; }
  });
  call("state", {}).then(render).catch(error => { status.textContent = error.message; });
})();
