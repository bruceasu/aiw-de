/* Shared HUD/RPG presentation. No business state, identity or credentials. */
(() => {
  "use strict";
  if (window.AiwAppearance) return;
  const key = "aiw.appearance.v1";
  const defaults = { mode: "auto", dayStart: "08:00", nightStart: "19:00" };
  const source = document.currentScript.src;
  const portraits = Object.fromEntries(["hud", "rpg"].map(theme => [theme, new URL("appearance-" + theme + ".png", source).href]));
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
    document.querySelectorAll("img[data-appearance-portrait]").forEach(image => {
      if (image.src !== portraits[value]) image.src = portraits[value];
      image.alt = value === "rpg" ? "卡通冒险伙伴" : "智能助理";
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
  window.AiwAppearance = Object.freeze({ getState, setPreference });
  apply();
  function element(tag, className, text) {
    const result = document.createElement(tag);
    if (className) result.className = className;
    if (text) result.textContent = text;
    return result;
  }
  function mount() {
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
    const host = document.querySelector(".topbar-actions") || document.querySelector(".topbar");
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
      const image = element("img"); image.src = portraits[value]; image.alt = "";
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
    mountCompanion();
    const brand = document.querySelector(".sidebar .brand");
    if (brand) {
      const mode = element("span", "appearance-current"); mode.id = "appearance-current"; brand.append(mode);
    }
    apply();
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
    const mast = element("div", "companion-mast");
    mast.append(element("span", "companion-emblem", "✦"), element("span", "", personal ? "PERSONAL COMPANION" : "MISSION COMPANION"));
    const art = element("div", "companion-art");
    const orbit = element("div", "companion-orbit"); orbit.setAttribute("aria-hidden", "true");
    const image = element("img", "companion-character"); image.dataset.appearancePortrait = ""; image.src = portraits[effective()]; image.alt = "工作伙伴";
    art.append(orbit, image);
    const copy = element("div", "companion-copy");
    const name = element("h2"); name.dataset.companionName = "";
    const caption = element("p"); caption.dataset.companionCaption = "";
    copy.append(element("span", "companion-kicker", "YOUR WORK, IN FOCUS"), name, caption);
    const context = element("div", "companion-context");
    const heading = element("strong"), status = element("span");
    context.append(element("small", "", "当前工作空间"), heading, status);
    const actions = element("div", "companion-actions");
    const destinations = personal ? [["与秘书对话", "chat"], ["查看工作日志", "work"]] : [["查看 Plans", "/ui/plans"], ["查看 Fleet", "/ui/workers"]];
    for (const [label, target] of destinations) {
      const action = element(personal ? "button" : "a", "btn " + (actions.children.length ? "ghost" : "primary"), label);
      if (personal) {
        action.type = "button";
        action.addEventListener("click", () => document.querySelector('[data-view-target="' + target + '"]')?.click());
      } else action.href = target;
      actions.append(action);
    }
    stage.append(mast, art, copy, context, actions, element("p", "companion-footnote", "每一步进展，都来自真实工作。"));
    layout.prepend(stage);
    const sync = () => {
      heading.textContent = document.querySelector(".topbar h1")?.textContent || document.title;
      status.textContent = personal ? document.getElementById("runtime-state")?.textContent || "等待个人服务" : "查看页面中的已记录状态";
    };
    sync();
    const observer = new MutationObserver(sync);
    for (const target of document.querySelectorAll(".topbar h1, #runtime-state")) observer.observe(target, { childList: true, subtree: true, characterData: true });
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", mount, { once: true }); else mount();
  setInterval(() => { if (!document.hidden) apply(); }, 30000);
  document.addEventListener("visibilitychange", apply);
  window.addEventListener("focus", apply);
  window.addEventListener("storage", event => {
    if (event.key !== key) return;
    try { const value = JSON.parse(event.newValue); preference = valid(value) ? clean(value) : { ...defaults }; apply(); } catch (_) { /* Ignore corrupt data. */ }
  });
})();
