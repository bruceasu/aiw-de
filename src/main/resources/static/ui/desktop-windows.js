(() => {
  const tray = document.querySelector('[data-desktop-windows]');
  if (!tray) return;
  const windows = new WeakMap();
  const smallScreen = matchMedia('(max-width: 760px)');
  let nextId = 0;
  const translate = text => window.aiwI18n?.t(text) || text;

  function button(label, symbol, action) {
    const control = document.createElement('button');
    control.type = 'button';
    control.textContent = symbol;
    control.setAttribute('aria-label', translate(label));
    control.title = translate(label);
    control.addEventListener('click', action);
    return control;
  }

  function close(content) {
    const entry = windows.get(content);
    if (!entry) return;
    if (entry.dialog.open) entry.dialog.close();
    entry.restore?.remove();
    entry.anchor.replaceWith(content);
    entry.dialog.remove();
    entry.lifecycle.abort();
    windows.delete(content);
    tray.hidden = !tray.childElementCount;
    entry.options.onClose?.();
    if (entry.trigger?.isConnected) entry.trigger.focus({ preventScroll: true });
  }

  function show(entry) {
    entry.restore?.remove();
    tray.hidden = !tray.childElementCount;
    if (!entry.dialog.open) entry.dialog.showModal();
    (entry.options.focus || entry.title).focus({ preventScroll: true });
  }

  function open(content, options) {
    const current = windows.get(content);
    if (current) {
      if (options.title) {
        current.title.textContent = options.title;
        current.title.toggleAttribute('data-i18n-skip', Boolean(options.recordTitle));
      }
      show(current);
      return;
    }
    const dialog = document.createElement('dialog');
    dialog.className = 'workspace-confirm desktop-window';
    const header = document.createElement('header');
    header.className = 'desktop-window-bar';
    const title = document.createElement('h2');
    title.id = 'desktop-window-' + (++nextId);
    title.tabIndex = -1;
    title.textContent = options.title;
    title.toggleAttribute('data-i18n-skip', Boolean(options.recordTitle));
    if (options.icon) title.dataset.consoleIcon = options.icon;
    dialog.setAttribute('aria-labelledby', title.id);
    const actions = document.createElement('div');
    actions.className = 'desktop-window-actions';
    const body = document.createElement('div');
    body.className = 'desktop-window-content';
    const anchor = document.createComment('desktop window');
    const host = options.container || content.parentElement;
    const entry = { dialog, title, options, anchor, trigger: document.activeElement, restore: null, lifecycle: new AbortController() };
    windows.set(content, entry);
    let closing = false;
    function labelRestore() {
      if (!entry.restore) return;
      entry.restore.textContent = title.textContent;
      entry.restore.setAttribute('aria-label', translate('Restore window') + ' · ' + title.textContent);
      entry.restore.title = translate('Restore window');
    }
    const requestClose = async () => {
      if(closing||options.canClose?.()===false)return;
      closing=true;
      try { if(!options.beforeClose||await options.beforeClose())close(content); }
      finally { closing=false; }
    };
    const minimize = button('Minimize window', '−', () => {
      if (closing || options.canMinimize?.() === false) return;
      dialog.close();
      entry.restore = button('Restore window', title.textContent, () => show(entry));
      entry.restore.className = 'btn ghost small-btn';
      entry.restore.dataset.i18nSkip = '';
      labelRestore();
      tray.append(entry.restore);
      tray.hidden = false;
      entry.restore.focus();
    });
    const maximize = button('Maximize window', '□', () => {
      const expanded = dialog.dataset.maximized !== 'true';
      dialog.dataset.maximized = String(expanded);
      const label = translate(expanded ? 'Restore window size' : 'Maximize window');
      maximize.setAttribute('aria-label', label);
      maximize.title = label;
      maximize.textContent = expanded ? '▣' : '□';
    });
    maximize.className = 'desktop-window-maximize';
    actions.append(minimize,maximize,button('Close window','×',requestClose));
    header.append(title,actions);
    dialog.append(header,body);
    content.before(anchor);
    body.append(content);
    host.append(dialog);
    dialog.addEventListener('cancel', event => { event.preventDefault(); requestClose(); });
    window.addEventListener('aiw:locale-changed', labelRestore, { signal:entry.lifecycle.signal });

    let drag = null;
    function position(x,y) {
      const bounds = dialog.getBoundingClientRect();
      dialog.style.setProperty('--window-left', Math.max(0,Math.min(x,innerWidth-bounds.width))+'px');
      dialog.style.setProperty('--window-top', Math.max(0,Math.min(y,innerHeight-bounds.height))+'px');
      dialog.dataset.positioned = 'true';
    }
    header.addEventListener('pointerdown', event => {
      if (event.button !== 0 || event.target.closest('button') || smallScreen.matches || dialog.dataset.maximized === 'true') return;
      const bounds = dialog.getBoundingClientRect();
      drag = { id: event.pointerId, x: event.clientX-bounds.left, y: event.clientY-bounds.top };
      header.setPointerCapture(event.pointerId);
    });
    header.addEventListener('pointermove', event => {
      if (drag?.id === event.pointerId) position(event.clientX-drag.x,event.clientY-drag.y);
    });
    header.addEventListener('lostpointercapture', () => { drag = null; });
    title.addEventListener('keydown', event => {
      const offset = { ArrowLeft:[-20,0], ArrowRight:[20,0], ArrowUp:[0,-20], ArrowDown:[0,20] }[event.key];
      if (!event.altKey || !offset || smallScreen.matches || dialog.dataset.maximized === 'true') return;
      event.preventDefault();
      const bounds = dialog.getBoundingClientRect();
      position(bounds.left+offset[0],bounds.top+offset[1]);
    });
    window.addEventListener('resize', () => {
      if (dialog.open && dialog.dataset.positioned === 'true' && dialog.dataset.maximized !== 'true' && !smallScreen.matches) {
        position(parseFloat(dialog.style.getPropertyValue('--window-left')),parseFloat(dialog.style.getPropertyValue('--window-top')));
      }
    }, { signal: entry.lifecycle.signal });
    show(entry);
  }

  window.aiwDesktopWindow = Object.freeze({ open, close, has: content => windows.has(content) });
})();
