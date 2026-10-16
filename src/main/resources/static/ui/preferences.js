(() => {
  const key = 'aiw-preferences';
  const defaults = { windowOpacity: 61.8, language: 'en', showTime: true, showDate: true, animations: true };
  const normalize = value => ({
    windowOpacity: typeof value?.windowOpacity === 'number' && Number.isFinite(value.windowOpacity)
      && value.windowOpacity >= 55 && value.windowOpacity <= 100 ? Math.round(value.windowOpacity * 10) / 10 : defaults.windowOpacity,
    language: value?.language === 'zh' ? 'zh' : 'en',
    showTime: typeof value?.showTime === 'boolean' ? value.showTime : defaults.showTime,
    showDate: typeof value?.showDate === 'boolean' ? value.showDate : defaults.showDate,
    animations: typeof value?.animations === 'boolean' ? value.animations : defaults.animations,
  });
  let settings = { ...defaults };
  try {
    const saved = localStorage.getItem(key);
    settings = saved ? normalize(JSON.parse(saved)) : normalize({
      language: localStorage.getItem('aiw-locale'), animations: localStorage.getItem('aiw-motion') !== 'off',
    });
  } catch (_) { /* Start with defaults; in-page controls still work. */ }
  const dialog = document.querySelector('[data-preferences-dialog]');
  const status = dialog?.querySelector('[data-preferences-status]');
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)');
  const apply = () => {
    document.documentElement.style.setProperty('--plan-window-alpha', (settings.windowOpacity / 100).toFixed(3));
    document.documentElement.dataset.motion = settings.animations && !reducedMotion?.matches ? 'on' : 'off';
    const opacity = dialog?.querySelector('[data-preference-opacity]');
    if (opacity) opacity.value = String(settings.windowOpacity);
    const output = dialog?.querySelector('[data-preference-opacity-value]');
    if (output) output.textContent = `${settings.windowOpacity.toFixed(1)}%`;
    const language = dialog?.querySelector('#console-locale');
    if (language) language.value = settings.language;
    for (const property of ['showTime', 'showDate', 'animations']) {
      const input = dialog?.querySelector(`[data-preference="${property}"]`);
      if (input) input.checked = settings[property];
    }
    const note = dialog?.querySelector('[data-system-motion-note]');
    if (note) note.hidden = !reducedMotion?.matches;
    window.dispatchEvent(new CustomEvent('aiw:preferences-changed', { detail: { ...settings } }));
  };
  const update = changes => {
    settings = normalize({ ...settings, ...changes });
    let saved = true;
    try { localStorage.setItem(key, JSON.stringify(settings)); } catch (_) { saved = false; }
    apply();
    if (status) status.textContent = saved ? 'Preferences saved in this browser.' : 'Applied for this page. Browser storage is unavailable.';
  };
  window.aiwPreferences = { get: () => ({ ...settings }), update, reset: () => update(defaults) };
  let opener = null;
  document.querySelector('[data-preferences-open]')?.addEventListener('click', event => {
    if (!dialog || dialog.open) return;
    opener = event.currentTarget;
    if (status) status.textContent = '';
    dialog.showModal();
  });
  dialog?.querySelector('[data-preferences-close]')?.addEventListener('click', () => dialog.close());
  dialog?.querySelector('[data-preferences-reset]')?.addEventListener('click', () => update(defaults));
  dialog?.addEventListener('close', () => opener?.focus());
  dialog?.addEventListener('click', event => { if (event.target === dialog) dialog.close(); });
  dialog?.querySelector('[data-preference-opacity]')?.addEventListener('input', event => update({ windowOpacity: Number(event.target.value) }));
  dialog?.querySelector('#console-locale')?.addEventListener('change', event => update({ language: event.target.value }));
  for (const property of ['showTime', 'showDate', 'animations']) {
    dialog?.querySelector(`[data-preference="${property}"]`)?.addEventListener('change', event => update({ [property]: event.target.checked }));
  }
  reducedMotion?.addEventListener?.('change', apply);
  window.addEventListener('storage', event => {
    if (event.key !== key && event.key !== null) return;
    try { settings = event.newValue ? normalize(JSON.parse(event.newValue)) : { ...defaults }; apply(); }
    catch (_) { /* Ignore malformed changes from another tab. */ }
  });
  apply();
})();
