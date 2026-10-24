"use strict";

// Independent adapter: this card also works with the pre-redesign personal UI.
(() => {
  const element = id => document.getElementById(id);
  const generate = element("create-desktop-pairing");
  if (!generate) return;
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
    if (response.status === 404) throw new Error("服务尚不支持再次配对，请确认已更新并重新启动个人服务。");
    throw new Error(`配对请求未确认（${response.status}），请核对个人服务状态。`);
  };
  element("desktop-service-origin").textContent = location.origin;
  generate.addEventListener("click", async () => {
    generate.disabled = true;
    clear();
    try {
      const stateResponse = await fetch("/api/state", { credentials: "same-origin", cache: "no-store" });
      check(stateResponse);
      const state = await stateResponse.json();
      if (typeof state.csrf !== "string" || !state.csrf) throw new Error("当前会话校验信息不完整，请重新读取私人状态。");
      const response = await fetch("/api/pairing", {
        method: "POST", credentials: "same-origin", cache: "no-store",
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
      timer = setTimeout(clear, result.expiresIn * 1000);
      feedback("临时配对码已生成，请在另一个界面使用。现有会话继续有效。");
    } catch (error) {
      feedback(error instanceof TypeError || error instanceof SyntaxError ? "配对请求结果未确认，请检查个人服务。不会自动重复请求。" : error.message, "error");
    } finally { generate.disabled = false; }
  });
  show.addEventListener("click", () => {
    if (Date.now() >= expires) { clear(); return; }
    input.type = input.type === "password" ? "text" : "password";
    show.textContent = input.type === "password" ? "显示配对码" : "隐藏配对码";
  });
  element("copy-desktop-pairing").addEventListener("click", async () => {
    if (Date.now() >= expires) { clear(); feedback("配对码已过期，请生成新码。", "warn"); return; }
    try { await navigator.clipboard.writeText(input.value); feedback("配对码已复制。"); }
    catch (_) { feedback("浏览器无法复制，请显示配对码后手动复制。", "warn"); }
  });
  window.addEventListener("pagehide", clear);
})();
