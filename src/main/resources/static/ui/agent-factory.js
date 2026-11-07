(() => {
  "use strict";
  const form = document.getElementById("factory-settings");
  if (!form) return;
  const byId = id => document.getElementById(id);
  const fields = byId("factory-fields"), save = byId("factory-save"), status = byId("factory-status");
  const runtime = form.elements.runtime, mode = byId("factory-auth-mode"), credential = byId("factory-credential");
  const modelChoice = byId("factory-model-choice"), modelInput = form.elements.model, modelStatus = byId("factory-model-status");
  const authStatus = byId("model-auth-status"), cancelLogin = byId("model-auth-cancel");
  const t = text => window.aiwI18n?.t(text) || text;
  const factoryRoot = "/api/r1/authority/agent-factory/", authRoot = "/api/r1/model-connections/";
  const labels = { READY: "认证就绪", IDENTITY_ONLY: "仅身份登录，尚无模型权限", REFRESHING: "正在更新访问凭证", REFRESH_OUTCOME_UNKNOWN: "刷新结果未知，需要重新登录或导入", AUTH_REQUIRED: "需要重新登录", DISCONNECTED: "已断开" };
  const errors = {
    MODEL_CREDENTIAL_VAULT_NOT_CONFIGURED: "Plane 尚未配置凭证加密存储。请配置 AIW_MODEL_CREDENTIAL_KEY 后重启，再登录或导入。",
    MODEL_REFRESH_TOO_EARLY: "尚未到服务端允许的刷新时间。", MODEL_AUTH_SCOPE_DENIED: "当前账户无权执行此操作，或凭证身份校验失败。",
    MODEL_AUTH_INVALID_INPUT: "凭证格式、来源或有效期不符合要求。请重新登录或选择正确的文件。",
    MODEL_TOKEN_ALREADY_IMPORTED_OR_USED: "这组轮换凭证已导入或使用，请选择已有连接，或获取新的凭证后再导入。",
    MODEL_AUTH_REFRESH_OUTCOME_UNKNOWN: "刷新结果未知。ChatGPT 连接请重新登录；Codex 连接请取得新凭据后替换。",
    MODEL_CONNECTION_NOT_READY_FOR_RUNTIME: "连接与执行方式不匹配，或尚未就绪。请重新选择登录方式和连接。",
    MODEL_DIRECTORY_API_KEY_NOT_CONFIGURED: "本机运行环境未配置 OPENAI_API_KEY。可改用 ChatGPT 登录，或配置环境后重新读取目录。",
    MODEL_DIRECTORY_LOGIN_REQUIRED: "Codex 登录尚不可用。请完成本机 Codex 登录，或更新所选 Plane 连接。",
    MODEL_DIRECTORY_CODEX_UNAVAILABLE: "本机找不到 Codex。请核对已保存的程序路径；也可先手动填写模型。",
    MODEL_DIRECTORY_HELPER_NOT_FOUND: "模型目录查询程序缺失，请核对本机 Worker 安装目录。",
    MODEL_DIRECTORY_PROBE_FAILED: "Codex 模型目录读取失败，请检查安装版本、登录状态及已保存的程序路径。",
    MODEL_DIRECTORY_TIMEOUT: "读取 Codex 模型目录超时，可以稍后重试或手动填写。",
    MODEL_DIRECTORY_BUSY: "已有模型目录查询正在执行，请稍后重新读取。",
    MODEL_DIRECTORY_SOURCE_CHANGED: "已保存的运行环境刚刚变化，旧目录结果已丢弃，请重新读取模型目录。",
    MODEL_DIRECTORY_FORMAT_UNSUPPORTED: "目录返回格式暂不支持，请手动填写模型。",
    MODEL_AUTH_REMOTE_REJECTED_401: "目录接口未接受当前凭证，请重新登录、更新凭证或检查 API Key。",
    MODEL_AUTH_REMOTE_REJECTED_403: "当前凭证无权读取模型目录，请检查账户授权或 API Key 权限。",
    MODEL_AUTH_REMOTE_REJECTED_429: "模型目录查询受到限流，请稍后重新读取。"
  };
  let connections = [], vault = null, environment = null, authError = "", saved = null;
  let models = [], manual = false, contextKey = null, readyBefore = false, revision = 0;
  let catalogEpoch = 0, catalogController = null, catalogTimer = null, connectionEpoch = 0;
  let login = null, loginTimer = null, loginPolling = false, loginStarting = false;
  const busy = new Set();
  async function call(root, path, body = {}, signal) {
    const csrf = document.querySelector('meta[name="csrf-token"]').content;
    const response = await fetch(root + path + "?_csrf=" + encodeURIComponent(csrf), { method: "POST", credentials: "same-origin", cache: "no-store", signal,
      headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf }, body: JSON.stringify(body) });
    const result = await response.json().catch(() => null);
    if (result === null || typeof result !== "object") throw new Error(t("接口返回格式异常，请重新登录或刷新页面。"));
    if (!response.ok) throw new Error(t(errors[result.code || result.message] || result.message || "操作未完成，请检查登录状态和权限。"));
    return result;
  }
  const selected = () => connections.find(item => item.id === credential.value);
  const plane = () => ["chatgpt", "codex-plane"].includes(mode.value);
  const kind = () => mode.value === "chatgpt" ? "CHATGPT_RESPONSES" : "CODEX_APP_SERVER";
  const kindName = value => t(value === "CHATGPT_RESPONSES" ? "ChatGPT OAuth（Python Worker）" : "Codex 登录凭证（Codex App Server）");
  function modes(chosen = "") {
    mode.replaceChildren(new Option(t(runtime.value ? "请选择登录方式" : "请先选择执行方式"), ""));
    for (const [value, name] of runtime.value === "codex-app-server"
      ? [["codex-local", "使用 Worker 本机 Codex 登录"], ["codex-plane", "使用 Plane 保存的 Codex 凭证"]]
      : runtime.value === "openai-agents" ? [["chatgpt", "使用 ChatGPT 登录（OAuth）"], ["api-key", "使用 API Key（运行环境）"]]
      : [["codex-local", "使用 Worker 本机 Codex 登录"], ["codex-plane", "使用 Plane 保存的 Codex 凭证"], ["chatgpt", "使用 ChatGPT 登录（OAuth）"], ["api-key", "使用 API Key（运行环境）"]]) mode.add(new Option(t(name), value));
    mode.disabled = !runtime.value;
    mode.value = chosen;
  }
  function showModel(id = "", asManual = false) {
    manual = asManual;modelInput.value = id;
    modelChoice.replaceChildren(new Option(t("请选择模型"), ""));
    for (const item of models) modelChoice.add(new Option(item.name === item.id ? item.id : item.name + " · " + item.id, item.id));
    if (id && !asManual && !models.some(item => item.id === id)) modelChoice.add(new Option(id + t("（当前配置，目录待核对）"), id));
    modelChoice.add(new Option(t("手动填写模型名称…"), "__manual__"));
    modelChoice.value = asManual ? "__manual__" : id;
    byId("factory-manual-model").hidden = !asManual;modelInput.required = asManual;
    efforts();
  }
  function efforts() {
    const item = models.find(value => value.id === modelInput.value), current = form.elements.effort.value;
    const allowed = item?.efforts?.length ? item.efforts : runtime.value === "codex-app-server"
      ? ["none", "minimal", "low", "medium", "high", "xhigh"] : ["none", "minimal", "low", "medium", "high"];
    form.elements.effort.replaceChildren(...allowed.map(value => new Option(value, value)));
    form.elements.effort.value = allowed.includes(current) ? current : allowed.includes(item?.defaultEffort) ? item.defaultEffort : allowed.includes("medium") ? "medium" : allowed[0];
    byId("factory-effort-help").textContent = t(item?.efforts?.length ? "推理强度来自所选模型的目录信息。" : "目录未提供此模型的推理强度信息；这里显示执行方式的通用选项。实际支持情况以模型响应为准。");
  }
  function sourceReady() { return Boolean(runtime.value && mode.value && (!plane() || selected()?.state === "READY")); }
  function synchronize({ requestedRef, preserveModel = false, auto = true } = {}) {
    const previous = requestedRef === undefined ? credential.value : requestedRef;
    const usePlane = plane(), codex = runtime.value === "codex-app-server";
    byId("factory-plane-settings").hidden = !usePlane;credential.disabled = !usePlane;credential.required = usePlane;
    credential.replaceChildren(new Option(t("请选择已保存的连接"), ""));
    for (const item of connections.filter(value => usePlane && value.kind === kind())) credential.add(new Option(item.label + " · " + t(labels[item.state] || item.state), item.id));
    credential.value = previous || "";
    const connection = selected();form.elements.credentialVersion.value = connection?.version ?? "";
    credential.setCustomValidity(usePlane && connection && connection.state !== "READY" ? t("此连接尚未就绪，请先在下方连接卡片中恢复。") : "");
    byId("factory-chatgpt-create").hidden = mode.value !== "chatgpt";byId("factory-codex-create").hidden = mode.value !== "codex-plane";
    byId("factory-new-summary").textContent = t(mode.value === "chatgpt" ? "登录新的 ChatGPT 账户" : "导入新的 Codex 登录凭证");
    byId("model-auth-login").disabled = vault !== true || login !== null || loginStarting;
    byId("model-auth-import").disabled = vault !== true;
    byId("factory-local-settings").hidden = mode.value !== "codex-local";
    byId("factory-local-settings").textContent = t("复用 Worker 本机的 Codex 登录。模型目录查询使用 Plane 本机的相同程序路径；外部 Worker 的模型可用性可能不同。");
    byId("factory-api-settings").hidden = mode.value !== "api-key";
    byId("factory-api-settings").textContent = t(environment === null ? "无法读取本机 API Key 配置状态，可重新读取连接状态后重试。" : environment.apiKeyConfigured
      ? (environment.customEndpoint ? "本机运行环境已配置 API Key 和自定义 API 地址。密钥不会显示在页面中；Python Worker 使用 OpenAI Agents SDK 执行。" : "本机运行环境已配置 OPENAI_API_KEY。密钥不会显示在页面中；Python Worker 使用 OpenAI Agents SDK 执行。")
      : "本机尚未配置 OPENAI_API_KEY。请在运行环境中配置，或改用 ChatGPT 登录。外部 Worker 使用自己的环境时，可手动填写模型。");
    byId("factory-codex-executable").hidden = !codex;form.elements.executable.disabled = !codex;
    byId("factory-codex-home").hidden = mode.value !== "codex-local";form.elements.guardHome.disabled = mode.value !== "codex-local";
    byId("factory-auth-help").textContent = t(!mode.value ? "先选择执行方式，再选择对应的登录方式。" : usePlane
      ? (vault === false ? errors.MODEL_CREDENTIAL_VAULT_NOT_CONFIGURED : authError || (mode.value === "chatgpt" ? "选择已有 ChatGPT 连接，或展开下方区域登录新账户。无需 Codex 或 API Key。" : "选择已有 Codex 连接，或展开下方区域导入新连接。仍需本机安装 Codex。"))
      : mode.value === "api-key" ? "API Key 来自 Worker 运行环境，本页不录入或保存 API Key。" : "此方式使用 Worker 自己的 Codex 登录，不从 Plane 领取凭证。");
    const next = [runtime.value, mode.value, credential.value, form.elements.credentialVersion.value].join("|");
    const changed = contextKey !== next, ready = sourceReady();
    if (changed) {
      revision++;contextKey = next;catalogEpoch++;catalogController?.abort();clearTimeout(catalogTimer);models = [];
      showModel(preserveModel ? modelInput.value : "", preserveModel && manual);
      modelStatus.textContent = t(ready ? "正在准备读取当前登录来源的模型目录…" : "请先完成执行与登录设置；也可以手动填写模型。");
    }
    byId("factory-model-refresh").disabled = !ready;
    if (ready && auto && (changed || !readyBefore)) catalogTimer = setTimeout(() => loadModels(false), 250);
    readyBefore = ready;renderConnections();
  }
  async function loadModels(force) {
    if (!sourceReady()) return;
    const epoch = ++catalogEpoch, expected = contextKey;catalogController?.abort();catalogController = new AbortController();
    modelStatus.textContent = t("正在读取当前登录来源的模型目录…");
    const chosen = selected();
    try {
      const result = await call(authRoot, "models", { runtime: runtime.value, mode: mode.value, credentialRef: plane() ? chosen.id : null,
        credentialVersion: plane() ? chosen.version : null, force }, catalogController.signal);
      if (epoch !== catalogEpoch || expected !== contextKey) return;
      models = result.models;const id = modelInput.value;
      showModel(id, manual || Boolean(id && !models.some(item => item.id === id)));
      const notice = result.source === "CODEX_RPC" ? "Codex 返回的目录可能来自内置或缓存信息，不代表逐个模型已验证账户权限。"
        : result.source === "OPENAI_API_ACCOUNT" ? "这是 API Key 账户的模型目录，可能包含其他用途的模型；尚未逐项验证 Responses 支持。"
        : "这是所选 ChatGPT 账户的可见模型目录，实际推理和额度尚未验证。";
      modelStatus.textContent = t("已读取模型目录：") + models.length + t(" 个。") + t(notice) + (result.cached ? t("（目录缓存，60 秒内有效）") : "");
    } catch (error) {
      if (error.name === "AbortError" || epoch !== catalogEpoch || expected !== contextKey) return;
      modelStatus.textContent = error.message + t(" 可以手动填写模型名称。");
    }
  }
  function button(text, action, disabled = false) {
    const element = document.createElement("button");element.type = "button";element.className = "btn ghost";element.textContent = t(text);element.disabled = disabled;
    element.addEventListener("click", action);return element;
  }
  function renderConnections() {
    const list = byId("model-auth-connections");list.replaceChildren();
    if (!connections.length) { list.textContent = t("尚无 Plane 保存的登录连接。请先在上方选择登录方式，再新增连接。");return; }
    for (const item of connections) {
      const row = document.createElement("article");row.className = "card";
      const title = document.createElement("h3");title.textContent = item.label;
      const description = document.createElement("p");description.textContent = kindName(item.kind) + " · " + t(labels[item.state] || item.state);
      const actions = document.createElement("div");actions.className = "actions";
      const active = busy.has(item.id), matches = runtime.value === (item.kind === "CHATGPT_RESPONSES" ? "openai-agents" : "codex-app-server");
      actions.append(button("用于当前工厂", () => {
        mode.value = item.kind === "CHATGPT_RESPONSES" ? "chatgpt" : "codex-plane";synchronize({ requestedRef: item.id });
        status.textContent = t("已选择连接，保存工厂设置后生效。");
      }, !matches || item.state !== "READY" || active));
      if (item.kind === "CHATGPT_RESPONSES") actions.append(button("重新登录此连接", () => runAction(item.id, () => beginLogin(item)), login !== null || loginStarting || vault !== true || active));
      else actions.append(button("用新文件替换此连接凭据", () => chooseReplacement(item), vault !== true || active));
      actions.append(button("刷新此连接的访问凭证", () => runAction(item.id, async () => {
        await call(authRoot, "refresh", { id: item.id, version: item.version });await reloadConnections();authStatus.textContent = item.label + t("：访问凭证已更新。");
      }), vault !== true || active || item.state !== "READY"));
      actions.append(button("断开此连接", () => runAction(item.id, async () => {
        if (!await window.aiwConfirm(t("断开此连接并清除 Plane 中的令牌？后续模型请求会被拒绝。") + "\n" + item.label)) return;
        const result = await call(authRoot, "disconnect", { id: item.id, version: item.version });await reloadConnections();
        authStatus.textContent = item.label + t(result.remoteRevocationConfirmed ? "：已断开，并收到远端撤销确认。" : "：已在本机断开，远端撤销未确认；可在 ChatGPT 设置中断开应用。");
      }), active || item.state === "DISCONNECTED"));
      row.append(title, description, actions);list.append(row);
    }
  }
  async function runAction(key, action) {
    if (busy.has(key)) return;busy.add(key);renderConnections();
    try { await action(); }catch (error) { authStatus.textContent = error.message; }
    finally { busy.delete(key);renderConnections(); }
  }
  async function reloadConnections() {
    const epoch = ++connectionEpoch;
    const results = await Promise.allSettled([call(authRoot, "state"), call(authRoot, "catalog-context")]);
    if (epoch !== connectionEpoch) return;
    if (results[1].status === "fulfilled") environment = results[1].value;
    if (results[0].status === "rejected") { authError = results[0].reason.message;authStatus.textContent = authError;synchronize({ preserveModel: true });return; }
    const state = results[0].value;connections = state.connections;vault = state.vaultConfigured;authError = "";
    authStatus.textContent = t(vault ? "已保存连接如下。操作目标以每张连接卡片的名称为准。" : errors.MODEL_CREDENTIAL_VAULT_NOT_CONFIGURED);
    synchronize({ preserveModel: true });
  }
  async function importFile(file, target = null) {
    const at = revision;
    if (!file || file.size > 98304) throw new Error(t("请选择不超过 96 KiB 的凭据 JSON 文件。"));
    const label = target?.label || byId("model-auth-label").value.trim();
    if (!label) throw new Error(t("请先填写新连接名称。"));
    const content = await file.text();let parsed;
    try { parsed = JSON.parse(content); }catch (_error) { throw new Error(t("请选择有效的凭据 JSON 文件。")); }
    const expected = target?.kind || kind();
    if ((parsed?.tokens ? "CODEX_APP_SERVER" : "CHATGPT_RESPONSES") !== expected) throw new Error(t("文件类型与此操作的连接类型不匹配，请选择正确的凭据文件。"));
    if (!await window.aiwConfirm(t(target ? "仅替换此连接的凭据？账户与客户端必须保持一致。" : "将所选凭据导入为新连接？已有连接保持原状。") + "\n" + label)) return;
    const value = await call(authRoot, "import", { connectionId: target?.id ?? null, expectedVersion: target?.version ?? null, label, content });
    if (!target) byId("model-auth-file").value = "";
    await reloadConnections();
    if (!target && at === revision && runtime.value === (value.kind === "CHATGPT_RESPONSES" ? "openai-agents" : "codex-app-server")) synchronize({ requestedRef: value.id });
    authStatus.textContent = label + t(target ? "：凭据已替换。" : "：新连接已创建，请选择连接并保存工厂设置。");
  }
  function chooseReplacement(target) {
    const picker = document.createElement("input");picker.type = "file";picker.accept = "application/json,.json";
    picker.addEventListener("change", () => runAction(target.id, () => importFile(picker.files[0], target)), { once: true });picker.click();
  }
  function endPolling() { clearInterval(loginTimer);loginTimer = null;loginPolling = false; }
  async function beginLogin(target = null) {
    if (login || loginStarting) throw new Error(t("已有登录正在进行，请先完成或取消。"));
    const label = target?.label || byId("model-auth-label").value.trim();
    if (!label) throw new Error(t("请先填写新连接名称。"));
    const browser = window.open("about:blank", "_blank");if (!browser) throw new Error(t("请允许本页打开登录窗口。"));browser.opener = null;
    const activateAt = revision;
    loginStarting = true;synchronize({ preserveModel: true, auto: false });
    try {
      const value = await call(authRoot, "login", { connectionId: target?.id ?? null, expectedVersion: target?.version ?? null, label });
      login = { id: value.loginId, activateAt, newConnection: !target };browser.location = value.authorizationUrl;cancelLogin.hidden = false;synchronize({ preserveModel: true, auto: false });
      authStatus.textContent = label + t("：请在浏览器中完成 ChatGPT 登录。");
      loginTimer = setInterval(async () => {
        if (loginPolling || !login) return;loginPolling = true;const current = login;
        try {
          const receipt = await call(authRoot, "logins/" + encodeURIComponent(current.id) + "/status");
          if (login !== current || ["PENDING", "PROCESSING"].includes(receipt.status)) return;
          endPolling();login = null;cancelLogin.hidden = true;
          if (receipt.status !== "COMPLETED") throw new Error(t("登录未完成：") + receipt.status);
          const activate = current.newConnection && current.activateAt === revision && mode.value === "chatgpt";
          await reloadConnections();if (activate && current.activateAt === revision && mode.value === "chatgpt") synchronize({ requestedRef: receipt.connectionId });
          authStatus.textContent = label + t("：登录完成。保存工厂设置后才会用于新 Worker。");
        } catch (error) { if (login === current) endPolling();authStatus.textContent = error.message; }
        finally { loginPolling = false;if (login === null) synchronize({ preserveModel: true, auto: false }); }
      }, 2000);
    } catch (error) { browser.close();throw error; }
    finally { loginStarting = false;synchronize({ preserveModel: true, auto: false }); }
  }
  function renderWorkers(workers) {
    const target = byId("factory-workers");target.replaceChildren();
    if (!workers.length) target.textContent = t("尚无平台启动的本机 Worker。有任务需要时才启动。");
    for (const worker of workers) {
      const row = document.createElement("p");row.dataset.i18nSkip = "";
      row.textContent = worker.workerId + " · " + worker.state + (worker.message ? " · " + worker.message : ""); target.append(row);
    }
  }
  function settingsInput() {
    return { runtime: runtime.value, model: modelInput.value.trim(), effort: form.elements.effort.value,
      automaticWorkers: form.elements.automaticWorkers.checked, python: form.elements.python.value,
      executable: runtime.value === "codex-app-server" ? form.elements.executable.value : "", guardHome: mode.value === "codex-local" ? form.elements.guardHome.value : "",
      credentialRef: plane() ? credential.value || null : null, credentialVersion: plane() ? Number(form.elements.credentialVersion.value) || null : null };
  }
  runtime.addEventListener("change", () => { modes();synchronize(); });
  mode.addEventListener("change", () => synchronize());credential.addEventListener("change", () => synchronize());
  modelChoice.addEventListener("change", () => { revision++;showModel(modelChoice.value === "__manual__" ? modelInput.value : modelChoice.value, modelChoice.value === "__manual__"); });
  modelInput.addEventListener("input", () => { revision++;efforts(); });
  for (const name of ["effort", "python", "executable", "guardHome", "automaticWorkers"]) form.elements[name].addEventListener("change", () => { revision++; });
  byId("factory-model-refresh").addEventListener("click", () => loadModels(true));
  byId("factory-model-manual").addEventListener("click", () => { revision++;showModel(modelInput.value, true);modelInput.focus(); });
  byId("model-auth-login").addEventListener("click", () => runAction("new-login", () => beginLogin()));
  byId("model-auth-import").addEventListener("click", () => runAction("new-import", () => importFile(byId("model-auth-file").files[0])));
  byId("model-auth-reload").addEventListener("click", () => runAction("reload", reloadConnections));
  cancelLogin.addEventListener("click", () => runAction("cancel", async () => {
    if (!login) return;await call(authRoot, "logins/" + encodeURIComponent(login.id) + "/cancel");endPolling();login = null;cancelLogin.hidden = true;
    synchronize({ preserveModel: true, auto: false });authStatus.textContent = t("当前登录已取消，已有连接保持原状。");
  }));
  byId("factory-refresh").addEventListener("click", async () => {
    try { const result = await call(factoryRoot, "state");renderWorkers(result.managedWorkers || []); }catch (error) { status.textContent = error.message; }
  });
  form.addEventListener("submit", async event => {
    event.preventDefault(); if (save.disabled || !form.reportValidity()) return;
    const input = settingsInput(), snapshot = JSON.stringify(input), chosen = selected();save.disabled = true;fields.disabled = true;
    try {
      const detail = runtime.selectedOptions[0].textContent + "\n" + mode.selectedOptions[0].textContent + (chosen && plane() ? " · " + chosen.label : "") + "\n" + input.model + " / " + input.effort;
      if (!await window.aiwConfirm(t("保存以下工厂设置？") + "\n" + detail)) return;
      if (JSON.stringify(settingsInput()) !== snapshot) throw new Error(t("设置在确认期间发生变化，请重新核对后保存。"));
      const result = await call(factoryRoot, "settings", input);saved = result.settings || input;renderWorkers(result.managedWorkers || []);
      const stillConfirmed = JSON.stringify(settingsInput()) === snapshot;
      contextKey = null;synchronize({ preserveModel: true });
      status.textContent = t(stillConfirmed ? "工厂设置已保存，正在按已保存的运行环境重新读取模型目录。" : "已保存刚才确认的设置；页面选择随后发生变化，当前选择尚未保存。");
    }
    catch (error) { status.textContent = error.message; }
    finally { save.disabled = false; fields.disabled = false; }
  });
  // Local choices must not depend on successful credential or catalog API calls.
  modes();fields.disabled = false;
  (async () => {
    const startedRevision = revision;
    const results = await Promise.allSettled([call(factoryRoot, "state"), call(authRoot, "state"), call(authRoot, "catalog-context")]);
    byId("model-auth-reload").disabled = false;
    if (results[1].status === "fulfilled") { connections = results[1].value.connections;vault = results[1].value.vaultConfigured; }
    else authError = results[1].reason.message;
    if (results[2].status === "fulfilled") environment = results[2].value;
    if (results[0].status === "rejected") { status.textContent = results[0].reason.message;authStatus.textContent = authError;synchronize({ preserveModel: true });return; }
    const result = results[0].value; saved = result.settings || null;const params = new URLSearchParams(location.search);
    if (revision !== startedRevision) {
      save.disabled = false;synchronize({ preserveModel: true });renderWorkers(result.managedWorkers || []);
      status.textContent = t("已读取工厂设置，并保留你正在编辑的页面选择。保存后才会生效。");return;
    }
    for (const name of ["runtime", "model", "effort", "python", "executable", "guardHome"]) {
      const value = saved?.[name] ?? (["runtime", "model", "effort"].includes(name) ? params.get(name) : null);
      if (name === "effort" && value === "xhigh" && runtime.value === "codex-app-server") form.elements.effort.add(new Option("xhigh", "xhigh"));
      if (value != null) form.elements[name].value = value;
    }
    form.elements.automaticWorkers.checked = saved?.automaticWorkers ?? true;
    modes(saved?.runtime ? saved.credentialRef ? (saved.runtime === "codex-app-server" ? "codex-plane" : "chatgpt") : (saved.runtime === "codex-app-server" ? "codex-local" : "api-key") : "");
    fields.disabled = false;save.disabled = false;showModel(modelInput.value);synchronize({ requestedRef: saved?.credentialRef || "", preserveModel: true });
    renderWorkers(result.managedWorkers || []);status.textContent = t(saved ? "工厂设置已载入。" : "请按顺序选择执行方式、登录方式和模型，再保存设置。");
    authStatus.textContent = authError || t(vault ? "已保存连接如下。新增与更新操作分别处理，不会隐式覆盖其他连接。" : errors.MODEL_CREDENTIAL_VAULT_NOT_CONFIGURED);
  })().catch(error => { status.textContent = error.message; });
})();
