(() => {
  "use strict";
  const status = document.getElementById("model-auth-status"), select = document.getElementById("factory-credential");
  if (!status || !select) return;
  const form = document.getElementById("factory-settings"), label = document.getElementById("model-auth-label");
  const cancel = document.getElementById("model-auth-cancel"), list = document.getElementById("model-auth-connections");
  const t = text => window.aiwI18n?.t(text) || text;
  let connections = [], desired = "", login = null, polling = null, busy = false;
  const names = { READY: "认证就绪（实际额度尚未验证）", IDENTITY_ONLY: "已登录，尚未获得模型权限", REFRESHING: "正在刷新", REFRESH_OUTCOME_UNKNOWN: "刷新结果未知，需要重新登录", AUTH_REQUIRED: "需要重新登录", DISCONNECTED: "已在本机断开" };
  const errors = { MODEL_CREDENTIAL_VAULT_NOT_CONFIGURED: "请先为 Plane 配置 AIW_MODEL_CREDENTIAL_KEY，再登录或导入。密钥配置方法见凭证管理说明。", MODEL_REFRESH_TOO_EARLY: "尚未到服务端允许的刷新时间。", MODEL_TOKEN_ALREADY_IMPORTED_OR_USED: "这组凭证已导入或已被使用，请使用当前连接或重新登录。", MODEL_AUTH_SCOPE_DENIED: "当前账户无权操作此连接，或凭证身份校验未通过。", MODEL_AUTH_INVALID_INPUT: "凭证格式、有效期或来源不符合要求。请使用正常登录入口。", MODEL_AUTH_REFRESH_OUTCOME_UNKNOWN: "刷新结果未知。为避免重复使用轮换令牌，请重新登录。" };
  async function call(path, body = {}) {
    const csrf = document.querySelector('meta[name="csrf-token"]').content;
    const response = await fetch("/api/r1/model-connections/" + path + "?_csrf=" + encodeURIComponent(csrf), {
      method: "POST", credentials: "same-origin", cache: "no-store", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
    });
    const value = await response.json();
    if (!response.ok) throw new Error(errors[value.code] || "凭证操作未完成：" + (value.code || response.status));
    return value;
  }
  function selected() { return connections.find(item => item.id === select.value); }
  function version() { form.elements.credentialVersion.value = selected()?.version ?? ""; }
  function render(state) {
    connections = state.connections;
    const previous = desired || select.value;
    select.replaceChildren(new Option(t("Worker 本机凭证（Codex 登录或 API Key）"), ""));
    list.replaceChildren();
    for (const item of connections) {
      const text = item.label + " · " + t(item.kind === "CHATGPT_RESPONSES" ? "ChatGPT OAuth（用于 Python Worker）" : "Codex 登录凭证（用于 Codex App Server）") + " · " + (names[item.state] || item.state);
      select.add(new Option(text, item.id));
      const row = document.createElement("p"); row.textContent = text; list.append(row);
    }
    if (connections.some(item => item.id === previous)) select.value = previous;
    desired = ""; version();
    status.textContent = state.vaultConfigured ? "凭证存储已配置。请选择已有连接、登录或显式导入。" : errors.MODEL_CREDENTIAL_VAULT_NOT_CONFIGURED;
  }
  async function reload() { render(await call("state")); }
  async function action(run) {
    if (busy) return; busy = true;
    try { await run(); }
    catch (error) { status.textContent = error.message; }
    finally { busy = false; }
  }
  select.addEventListener("change", () => { desired = ""; version(); });
  window.addEventListener("aiw-factory-loaded", event => { desired = event.detail.credentialRef || ""; reload().catch(error => { status.textContent = error.message; }); });
  document.getElementById("model-auth-login").addEventListener("click", () => action(async () => {
    if (login) throw new Error("已有登录正在进行，请先完成或取消。");
    const current = selected();
    if (current?.kind === "CODEX_APP_SERVER") throw new Error("所选连接是 Codex 登录凭证，只能通过导入新的 Codex 凭据文件更新。若要创建独立 ChatGPT OAuth 连接，请先取消选择此 Plane 连接，再点击 Continue with ChatGPT。");
    // Open synchronously so the explicit user gesture survives the API round trip.
    const browser = window.open("about:blank", "_blank");
    if (!browser) throw new Error("浏览器阻止了登录窗口，请允许本页弹出登录窗口。");
    browser.opener = null;
    try {
      const value = await call("login", { connectionId: current?.id ?? null, expectedVersion: current?.version ?? null, label: label.value });
      login = value.loginId; browser.location = value.authorizationUrl; cancel.hidden = false;
      status.textContent = "请在登录窗口完成授权。凭证将由本机 Plane 保存。";
      polling = setInterval(async () => {
        const id = login; if (!id) return;
        try {
          const state = await call("logins/" + encodeURIComponent(id) + "/status");
          if (login !== id || ["PENDING", "PROCESSING"].includes(state.status)) return;
          clearInterval(polling); login = null; cancel.hidden = true;
          if (state.status !== "COMPLETED") throw new Error("登录未完成：" + state.status + "。可以重新发起登录。");
          desired = state.connectionId; await reload();
          status.textContent = "登录完成。请将模型执行方式设为“Python Worker（直接调用 OpenAI Responses）”，选择此 ChatGPT OAuth 连接及账户可用的模型，然后保存工厂设置。";
        } catch (error) { status.textContent = error.message; }
      }, 2000);
    } catch (error) { browser.close(); throw error; }
  }));
  cancel.addEventListener("click", () => action(async () => {
    if (!login) return; await call("logins/" + encodeURIComponent(login) + "/cancel");
    login = null; clearInterval(polling); cancel.hidden = true; status.textContent = "登录已取消，原连接保持原状。";
  }));
  document.getElementById("model-auth-import").addEventListener("click", () => action(async () => {
    const file = document.getElementById("model-auth-file").files[0];
    if (!file || file.size > 98304) throw new Error("请选择不超过 96 KiB 的凭据 JSON 文件。");
    if (!await window.aiwConfirm("将所选文件中的凭证导入本机 Plane？")) return;
    const current = selected();
    const value = await call("import", { connectionId: current?.id ?? null, expectedVersion: current?.version ?? null, label: label.value, content: await file.text() });
    desired = value.id; document.getElementById("model-auth-file").value = ""; await reload();
    status.textContent = value.kind === "CHATGPT_RESPONSES"
      ? "凭证已导入。请选择“Python Worker（直接调用 OpenAI Responses）”及此 ChatGPT OAuth 连接，然后保存工厂设置。"
      : "凭证已导入。请选择“Codex App Server（通过本机 Codex）”及此 Codex 登录凭证连接，然后保存工厂设置。";
  }));
  document.getElementById("model-auth-refresh").addEventListener("click", () => action(async () => {
    const current = selected(); if (!current) throw new Error("请先选择 Plane 管理的连接。");
    await call("refresh", { id: current.id, version: current.version }); await reload(); status.textContent = "所选凭证已更新。";
  }));
  document.getElementById("model-auth-disconnect").addEventListener("click", () => action(async () => {
    const current = selected(); if (!current) throw new Error("请先选择 Plane 管理的连接。");
    if (!await window.aiwConfirm("断开所选连接并清除 Plane 中的令牌？正在使用该连接的后续模型请求会被拒绝。")) return;
    const result = await call("disconnect", { id: current.id, version: current.version }); await reload();
    status.textContent = result.remoteRevocationConfirmed ? "已在本机断开，并收到远端撤销确认。" : "已在本机断开。远端撤销尚未确认；可在 ChatGPT 设置中断开该应用。";
  }));
  reload().catch(error => { status.textContent = error.message; });
})();
