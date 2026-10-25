/* Shared confirmation UI for the console and secretary. Created on first use. */
(() => {
  "use strict";
  let dialog, pending, previousFocus;
  const t = value => window.aiwI18n?.t(value) || value;
  function create() {
    dialog = document.createElement("dialog");
    dialog.className = "workspace-confirm console-confirm";
    dialog.setAttribute("aria-labelledby", "aiw-confirm-title");
    dialog.setAttribute("aria-describedby", "aiw-confirm-message");
    dialog.innerHTML = '<div class="workspace-confirm__top"><span data-heading></span></div>'
      + '<div class="workspace-confirm__body"><h2 id="aiw-confirm-title"></h2><p id="aiw-confirm-message"></p></div>'
      + '<div class="workspace-confirm__target" hidden><span data-target-label></span><strong data-target data-i18n-skip></strong></div>'
      + '<div class="workspace-confirm__footer"><button class="btn ghost" type="button" data-cancel></button><button class="btn primary" type="button" data-accept></button></div>';
    document.body.append(dialog);
    dialog.querySelector("[data-cancel]").addEventListener("click", () => dialog.close("cancel"));
    dialog.querySelector("[data-accept]").addEventListener("click", () => dialog.close("confirm"));
    dialog.addEventListener("click", event => {
      const rect = dialog.getBoundingClientRect();
      if (event.target === dialog && (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom)) dialog.close("cancel");
    });
    dialog.addEventListener("close", () => {
      const resolve = pending; pending = null;
      if (previousFocus?.isConnected) previousFocus.focus();
      resolve?.(dialog.returnValue === "confirm");
    });
  }
  window.aiwConfirm = (message, { danger = false, title = "Confirm action", target = "", acceptLabel = "Confirm" } = {}) => {
    if (pending || !document.body) return Promise.resolve(false);
    if (!dialog) create();
    if (typeof dialog.showModal !== "function") return Promise.resolve(false);
    dialog.querySelector("[data-heading]").textContent = t("Confirm action");
    dialog.querySelector("#aiw-confirm-title").textContent = t(title);
    dialog.querySelector("#aiw-confirm-message").textContent = t(message);
    dialog.querySelector("[data-cancel]").textContent = t("Cancel");
    const accept = dialog.querySelector("[data-accept]");
    accept.textContent = t(acceptLabel); accept.className = danger ? "btn danger" : "btn primary";
    dialog.classList.toggle("console-confirm--danger", danger);
    dialog.querySelector(".workspace-confirm__target").hidden = !target;
    dialog.querySelector("[data-target-label]").textContent = t("所选对象");
    dialog.querySelector("[data-target]").textContent = target;
    dialog.returnValue = ""; previousFocus = document.activeElement;
    return new Promise(resolve => {
      pending = resolve;
      try { dialog.showModal(); dialog.querySelector("[data-cancel]").focus(); }
      catch (_) { pending = null; resolve(false); }
    });
  };
})();
/* Shared HUD/RPG presentation. No business state, identity or credentials. */
(() => {
  "use strict";
  if (window.AiwAppearance) return;
  const key = "aiw.appearance.v1";
  const defaults = { mode: "auto", dayStart: "08:00", nightStart: "19:00" };
  const source = document.currentScript.src;
  const portraits = Object.fromEntries(["hud", "rpg"].map(theme => [theme, new URL("appearance-" + theme + ".png", source).href]));
  const expressions = Object.fromEntries(["hud", "rpg"].map(theme => [theme, new URL("appearance-" + theme + "-wink.png", source).href]));
  const fallbackPortrait = new URL("appearance-secretary.png", source).href;
  const avatars = Object.fromEntries(["hud", "rpg"].map(theme => [theme, new URL("appearance-avatar-" + theme + ".png", source).href]));
  const failedImages = new Set();
  const expressionCache = new Map();
  const text = value => window.aiwI18n?.t(value) || value;
  const motion = () => !window.matchMedia("(prefers-reduced-motion: reduce)").matches && document.documentElement.dataset.motion !== "off";
  let expressionUntil = 0;
  let summary = null;
  function setAvatar(image, theme) {
    const url = avatars[theme];
    image.alt = text("秘书头像");
    image.onerror = () => {
      image.classList.add("secretary-avatar-fallback");
      image.onerror = null;
      image.src = portraits[theme];
    };
    image.onload = () => { if (image.src === url) image.classList.remove("secretary-avatar-fallback"); };
    if (image.dataset.avatarTheme !== theme) {
      image.dataset.avatarTheme = theme;
      image.classList.remove("secretary-avatar-fallback");
      image.src = url;
    }
  }
  function setPortrait(image, theme) {
    let url = motion() && Date.now() < expressionUntil ? expressions[theme] : portraits[theme];
    if (failedImages.has(url)) url = portraits[theme];
    if (failedImages.has(url)) url = fallbackPortrait;
    image.onerror = () => {
      failedImages.add(image.src);
      image.parentElement?.classList.add("companion-image-error");
      image.title = text("人物素材加载失败，请重启更新后的服务并刷新页面。");
      if (image.src !== fallbackPortrait) setPortrait(image, theme);
    };
    image.onload = () => {
      if (image.src !== fallbackPortrait) { image.parentElement?.classList.remove("companion-image-error"); image.removeAttribute("title"); }
    };
    if (image.src !== url) image.src = url;
    if (motion() && !expressionCache.has(theme)) {
      const cached = new Image(); cached.src = expressions[theme]; expressionCache.set(theme, cached);
    }
    image.alt = text(theme === "rpg" ? "卡通冒险伙伴" : "智能助理");
  }
  function setSummary(value) {
    summary = value;
    const stage = document.querySelector(".companion-stage");
    if (!stage) return;
    stage.dataset.activity = value?.running ? "busy" : "idle";
    const status = stage.querySelector(".companion-status");
    if (status) {
      status.dataset.state = value?.state || "unknown";
      const label = text(value?.status || "等待个人服务");
      status.title = label; status.setAttribute("aria-label", label);
    }
    for (const counter of stage.querySelectorAll("[data-companion-count]")) {
      const name = counter.dataset.companionCount;
      counter.hidden = !value;
      counter.querySelector("strong").textContent = value ? new Intl.NumberFormat(window.aiwI18n?.locale).format(value[name] || 0) : "—";
      const label = text({ running: "正在处理", waiting: "待你确认", done: "已完成" }[name]);
      counter.title = label; counter.setAttribute("aria-label", label + ": " + (value?.[name] || 0));
    }
  }
  const time = value => typeof value === "string" && /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value);
  function valid(value) {
    return value && ["auto", "hud", "rpg"].includes(value.mode) && time(value.dayStart)
      && time(value.nightStart) && value.dayStart !== value.nightStart;
  }
  function clean(value) { return { mode: value.mode, dayStart: value.dayStart, nightStart: value.nightStart }; }
  let preference = { ...defaults };
  let persistenceAvailable = true;
  try { const saved = JSON.parse(localStorage.getItem(key)); if (valid(saved)) preference = clean(saved); }
  catch (_) { persistenceAvailable = false; }
  const minutes = value => Number(value.slice(0, 2)) * 60 + Number(value.slice(3));
  function effective(now = new Date()) {
    if (preference.mode !== "auto") return preference.mode;
    const current = now.getHours() * 60 + now.getMinutes();
    const day = minutes(preference.dayStart), night = minutes(preference.nightStart);
    const daytime = day < night ? current >= day && current < night : current >= day || current < night;
    return daytime ? "hud" : "rpg";
  }
  function getState() { return { preference: { ...preference }, effective: effective(), persistenceAvailable }; }
  function apply() {
    const value = effective();
    const changed = document.documentElement.dataset.appearance !== value;
    document.documentElement.dataset.appearance = value;
    document.documentElement.dataset.appearanceMode = preference.mode;
    document.querySelectorAll("img[data-secretary-avatar]").forEach(image => setAvatar(image, value));
    document.querySelectorAll("img[data-appearance-portrait]").forEach(image => {
      setPortrait(image, value);
    });
    document.querySelectorAll("[data-companion-name]").forEach(label => { label.textContent = value === "rpg" ? "你的冒险伙伴" : "你的智能助理"; });
    document.querySelectorAll("[data-companion-caption]").forEach(label => { label.textContent = value === "rpg" ? "把今天的想法，写成下一页故事。" : "聚焦当前目标，让每一步清晰可见。"; });
    document.querySelectorAll("button[data-appearance-mode]").forEach(button => {
      button.setAttribute("aria-pressed", String(button.dataset.appearanceMode === preference.mode));
    });
    const label = document.getElementById("appearance-current");
    if (label) label.textContent = (preference.mode === "auto" ? "自动 · " : "") + value.toUpperCase();
    if (changed) window.dispatchEvent(new CustomEvent("aiw:appearance", { detail: getState() }));
  }
  function setPreference(value) {
    const next = { ...preference, ...value };
    if (!valid(next)) throw new Error("请填写不同的白天、夜间开始时间。");
    preference = clean(next);
    try { localStorage.setItem(key, JSON.stringify(preference)); persistenceAvailable = true; }
    catch (_) { persistenceAvailable = false; }
    apply();
    return getState();
  }
  window.AiwAppearance = Object.freeze({ getState, setPreference, setSummary });
  apply();
  function element(tag, className, text) {
    const result = document.createElement(tag);
    if (className) result.className = className;
    if (text) result.textContent = text;
    return result;
  }
  // Fixed local paths keep icons consistent across fonts, browsers and Qt.
  function icon(name) {
    const paths = {
      overview: "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z",
      work: "M7 3h10l3 3v15H4V3z M8 9h8 M8 13h8 M8 17h5",
      chat: "M5 4h14l2 2v10l-2 2h-8l-6 3v-3H3V6z M7 11h.1 M12 11h.1 M17 11h.1",
      policies: "M12 2l8 4v6c0 5-8 10-8 10S4 17 4 12V6z M8 12l3 3 5-6",
      platform: "M3 4h7v6H3z M14 14h7v6h-7z M17 14V7h-4 M7 10v7h4",
      records: "M3 6h7l2 2h9v12H3z M3 6V3h7l2 3",
      state: "M3 12h4l3-8 4 16 3-8h4",
      confirmations: "M21 12a9 9 0 1 1-9-9 M8 11l4 4 9-11",
      setup: "M9 3h6l1 4 4 1v8l-4 1-1 4H9l-1-4-4-1V8l4-1z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0",
      refresh: "M20 8a8 8 0 1 0 0 8 M20 3v5h-5",
      play: "M8 4l12 8-12 8z", pause: "M8 4v16 M16 4v16",
      waiting: "M12 3l10 18H2z M12 9v5 M12 17h.1",
      expand: "M4 9V4h5 M15 4h5v5 M20 15v5h-5 M9 20H4v-5",
      collapse: "M9 4v5H4 M20 9h-5V4 M15 20v-5h5 M4 15h5v5",
      emblem: "M12 2l10 10-10 10L2 12z M12 6l4 6-4 6-4-6z",
    };
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("viewBox", "0 0 24 24"); svg.setAttribute("class", "aiw-icon");
    svg.setAttribute("aria-hidden", "true"); svg.setAttribute("focusable", "false");
    const path = document.createElementNS(svg.namespaceURI, "path");
    path.setAttribute("d", paths[name] || paths.emblem); svg.append(path);
    return svg;
  }
  function mountIcons() {
    for (const button of document.querySelectorAll("#personal-app [data-view-target]")) {
      let slot = button.querySelector(".nav-icon");
      if (!slot) { slot = element("span", "nav-icon"); button.prepend(slot); }
      slot.setAttribute("aria-hidden", "true"); slot.replaceChildren(icon(button.dataset.viewTarget));
    }
    for (const [id, name] of [["refresh", "refresh"], ["enable", "play"], ["pause", "pause"]]) {
      document.querySelector("#personal-app #" + id)?.prepend(icon(name));
    }
    const brand = document.querySelector("#personal-app .brand-mark");
    if (brand) { brand.replaceChildren(icon("emblem")); brand.setAttribute("aria-label", "AIW"); }
  }
  function mount() {
    mountIcons();
    const controls = element("div", "appearance-controls");
    controls.setAttribute("role", "group"); controls.setAttribute("aria-label", "界面风格");
    for (const [value, label] of [["hud", "HUD"], ["rpg", "RPG"], ["auto", "自动"]]) {
      const button = element("button", "appearance-mode", label);
      button.type = "button"; button.dataset.appearanceMode = value;
      button.title = value === "auto" ? "按本机时间切换：白天 HUD，夜间 RPG" : "切换到 " + label + " 风格";
      button.addEventListener("click", () => setPreference({ mode: value })); controls.append(button);
    }
    const settings = element("button", "appearance-mode appearance-settings", "时间设置"); settings.type = "button";
    controls.append(settings);
    if (document.getElementById("personal-app") && !document.getElementById("console-locale")) {
      const language = element("select", "appearance-language"); language.id = "console-locale";
      language.setAttribute("aria-label", "Language"); language.dataset.i18nSkip = "";
      for (const [value, label] of [["en", "English"], ["zh", "简体中文"], ["ja", "日本語"]]) language.add(new Option(label, value));
      language.value = window.aiwI18n?.locale || "en"; controls.append(language);
    }
    const host = document.querySelector("#personal-app [data-appearance-controls]") || document.querySelector(".topbar-actions") || document.querySelector(".topbar");
    if (host) host.append(controls); else { controls.classList.add("appearance-floating"); document.body.append(controls); }
    const dialog = element("dialog", "appearance-dialog");
    dialog.setAttribute("aria-labelledby", "appearance-title");
    const title = element("h2", "", "选择今天的心情"); title.id = "appearance-title";
    dialog.append(title, element("p", "appearance-description", "两套风格共享功能与数据。白天专注，晚上换一种氛围。"));
    const previews = element("div", "appearance-previews");
    const form = element("form", "appearance-form");
    const modeLabel = element("label", "", "切换方式"), select = element("select");
    for (const [value, label] of [["auto", "按时间自动切换"], ["hud", "保持 HUD"], ["rpg", "保持 RPG"]]) select.add(new Option(label, value));
    modeLabel.append(select); select.name = "mode"; form.append(modeLabel);
    for (const [value, label, description] of [["hud", "HUD · 专注控制台", "青色线框 / 几何面板"], ["rpg", "RPG · 秘书伙伴", "暖金装饰 / 任务日志"]]) {
      const tile = element("button", "appearance-preview appearance-preview-" + value); tile.type = "button";
      const image = element("img"); image.src = avatars[value]; image.alt = "";
      tile.append(image, element("strong", "", label), element("span", "", description));
      tile.addEventListener("click", () => { select.value = value; updateFields(); }); previews.append(tile);
    }
    dialog.append(previews);
    const times = element("div", "appearance-times");
    for (const [name, label] of [["dayStart", "白天开始 · HUD"], ["nightStart", "夜间开始 · RPG"]]) {
      const field = element("label", "", label), input = element("input"); input.type = "time"; input.name = name; input.required = true;
      field.append(input); times.append(field);
    }
    const zone = Intl.DateTimeFormat().resolvedOptions().timeZone || "本机时区";
    const notice = element("p", "appearance-description", "使用本机时区：" + zone + "。手动选择后将保持该风格，选择“自动”恢复定时切换。");
    const error = element("p", "appearance-error"); error.setAttribute("role", "status");
    const actions = element("div", "appearance-actions");
    const close = element("button", "btn ghost", "取消"), save = element("button", "btn primary", "保存外观");
    close.type = "button"; save.type = "submit";
    close.addEventListener("click", () => dialog.close()); actions.append(close, save);
    form.append(times, notice, error, actions); dialog.append(form); document.body.append(dialog);
    function updateFields() {
      for (const input of times.querySelectorAll("input")) input.disabled = select.value !== "auto";
      previews.querySelectorAll("button").forEach((button, index) => button.setAttribute("aria-pressed", String(select.value === (index ? "rpg" : "hud"))));
    }
    select.addEventListener("change", updateFields);
    settings.addEventListener("click", () => {
      for (const [name, value] of Object.entries(preference)) form.elements[name].value = value;
      error.textContent = persistenceAvailable ? "" : "当前环境无法保存偏好，本次窗口内仍可切换。";
      updateFields(); dialog.showModal();
    });
    form.addEventListener("submit", event => {
      event.preventDefault();
      try {
        setPreference({ mode: select.value, dayStart: form.elements.dayStart.value, nightStart: form.elements.nightStart.value });
        if (!persistenceAvailable) { error.textContent = "外观已应用，但当前环境无法保存偏好。"; return; }
        dialog.close();
      } catch (problem) { error.textContent = problem.message; }
    });
    mountAvatars();
    const brand = document.querySelector(".sidebar .brand");
    if (brand) {
      const mode = element("span", "appearance-current"); mode.id = "appearance-current"; brand.append(mode);
    }
    apply();
  }
  function mountAvatars() {
    document.documentElement.dataset.workspaceAvatar = "on";
    const brand = document.querySelector(".sidebar .brand .brand-mark");
    if (brand) {
      const avatar = element("img", "secretary-avatar"); avatar.dataset.secretaryAvatar = "";
      setAvatar(avatar, effective());
      brand.classList.add("secretary-brand-avatar");
      brand.removeAttribute("aria-label"); brand.replaceChildren(avatar);
    }
    // Replies are inserted asynchronously. Bind only their new avatar nodes;
    // do not inspect or rewrite private message text.
    const thread = document.getElementById("chat-messages");
    if (thread) new MutationObserver(records => {
      for (const record of records) for (const node of record.addedNodes) {
        if (node.nodeType !== Node.ELEMENT_NODE) continue;
        if (node.matches("img[data-secretary-avatar]")) setAvatar(node, effective());
        node.querySelectorAll("img[data-secretary-avatar]").forEach(image => setAvatar(image, effective()));
      }
    }).observe(thread, { childList: true, subtree: true });
  }
  function mountCompanion() {
    const personal = Boolean(document.getElementById("personal-app"));
    const workspace = document.querySelector(personal ? ".workspace-content" : ".main > .content");
    if (!workspace) return;
    let layout = workspace;
    if (!personal) {
      layout = element("div", "appearance-layout appearance-console");
      workspace.before(layout); layout.append(workspace);
    }
    layout.classList.add("appearance-layout");
    const stage = element("aside", "companion-stage"); stage.setAttribute("aria-label", "工作伙伴");
    stage.dataset.size = "compact";
    const art = element("div", "companion-art");
    const orbit = element("div", "companion-orbit"); orbit.setAttribute("aria-hidden", "true");
    const image = element("img", "companion-character"); image.dataset.appearancePortrait = "";
    setPortrait(image, effective());
    art.append(orbit, image);
    art.tabIndex = 0; art.setAttribute("role", "button"); art.setAttribute("aria-label", "向伙伴打招呼");
    const greet = () => {
      if (!motion()) return;
      expressionUntil = Date.now() + 1200; apply();
      art.classList.remove("companion-greeting"); requestAnimationFrame(() => art.classList.add("companion-greeting"));
      setTimeout(() => { art.classList.remove("companion-greeting"); apply(); }, 1300);
    };
    art.addEventListener("click", greet);
    art.addEventListener("keydown", event => { if (["Enter", " "].includes(event.key)) { event.preventDefault(); greet(); } });
    const size = element("button", "companion-size"); size.type = "button"; size.append(icon("expand"));
    size.title = "展开伙伴"; size.setAttribute("aria-label", "展开伙伴"); size.setAttribute("aria-expanded", "false");
    size.addEventListener("click", () => {
      const expanded = stage.dataset.size !== "expanded";
      stage.dataset.size = expanded ? "expanded" : "compact";
      size.replaceChildren(icon(expanded ? "collapse" : "expand"));
      size.title = text(expanded ? "收起伙伴" : "展开伙伴"); size.setAttribute("aria-label", size.title);
      size.setAttribute("aria-expanded", String(expanded));
    });
    const status = element("span", "companion-status"); status.setAttribute("role", "status"); status.textContent = "●";
    const counters = element("div", "companion-counters");
    if (personal) for (const [key, name] of [["running", "play"], ["waiting", "waiting"], ["done", "confirmations"]]) {
      const counter = element("span", "companion-count"); counter.dataset.companionCount = key; counter.hidden = true;
      counter.setAttribute("role", "img"); counter.tabIndex = 0;
      const symbol = element("span"); symbol.setAttribute("aria-hidden", "true"); symbol.append(icon(name));
      counter.append(symbol, element("strong", "", "—")); counters.append(counter);
    }
    art.append(status, counters);
    const actions = element("div", "companion-actions");
    const destinations = personal ? [["与秘书对话", "chat"], ["查看工作日志", "work"]] : [["查看 Plans", "/ui/plans"], ["查看 Fleet", "/ui/workers"]];
    for (const [index, [label, target]] of destinations.entries()) {
      const action = element(personal ? "button" : "a", "btn " + (index ? "ghost" : "primary"));
      action.append(icon(index ? "work" : "chat"));
      action.title = label; action.setAttribute("aria-label", label);
      if (personal) {
        action.type = "button";
        action.addEventListener("click", () => document.querySelector('[data-view-target="' + target + '"]')?.click());
      } else action.href = target;
      actions.append(action);
    }
    stage.append(art, actions, size);
    layout.prepend(stage);
    if (!personal) status.hidden = true;
    setSummary(summary);
    setInterval(() => {
      if (document.hidden || !motion() || !art.getClientRects().length) return;
      expressionUntil = Date.now() + 220; apply(); setTimeout(apply, 260);
    }, 8500);
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", mount, { once: true }); else mount();
  setInterval(() => { if (!document.hidden) apply(); }, 30000);
  document.addEventListener("visibilitychange", apply);
  window.addEventListener("focus", apply);
  window.addEventListener("aiw:locale-changed", () => { apply(); setSummary(summary); });
  window.addEventListener("storage", event => {
    if (event.key !== key) return;
    try { const value = JSON.parse(event.newValue); preference = valid(value) ? clean(value) : { ...defaults }; apply(); } catch (_) { /* Ignore corrupt data. */ }
  });
})();
