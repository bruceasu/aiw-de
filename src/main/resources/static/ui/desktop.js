(() => {
  const shell = document.querySelector('.desktop-shell');
  if (!shell) return;
  const strip = shell.querySelector('[data-desktop-tabs]');
  const menu = shell.querySelector('[data-desktop-menu]');
  const routes = new Map([...menu.querySelectorAll('.desktop-menu-items > a')].map(link => [link.pathname, link]));
  const area = shell.dataset.desktopArea;
  const storageKey = 'aiw.desktop.pages.v1.' + (shell.dataset.desktopUser || '');
  const identifier = /^[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$/;
  const translate = value => window.aiwI18n?.t(value) || value;

  function page(input) {
    const url = new URL(input, location.origin);
    if (url.origin !== location.origin) return null;
    let path = url.pathname;
    let section = path;
    let key = path;
    const search = new URLSearchParams();
    if (path === '/ui/tasks' && identifier.test(url.searchParams.get('execution') || '')) {
      path += '/' + url.searchParams.get('execution');
    }
    if (path === '/ui/tasks' || path.startsWith('/ui/tasks/')) {
      section = '/ui/tasks';
      key = path;
      if (path !== section && !identifier.test(path.slice(section.length + 1))) return null;
      for (const name of ['q', 'state', 'view', 'page']) {
        const value = url.searchParams.get(name);
        if (value) search.set(name, value.slice(0, 160));
      }
    } else if (path === '/ui/workflows') {
      for (const name of ['definition', 'execution']) {
        const value = url.searchParams.get(name);
        if (identifier.test(value || '')) {
          search.set(name, value);
          key += '?' + search;
          break;
        }
      }
    } else if (path === '/ui/skills' || path === '/ui/goals') {
      const name = path === '/ui/skills' ? 'skill' : 'goal';
      const value = url.searchParams.get(name);
      if (identifier.test(value || '')) {
        search.set(name, value);
        key += '?' + search;
      }
    }
    if (!routes.has(section)) return null;
    return { key, section, url: path + (search.size ? '?' + search : '') };
  }

  function readPages() {
    try {
      const saved = JSON.parse(sessionStorage.getItem(storageKey) || '[]');
      if (!Array.isArray(saved)) return [];
      const valid = saved.slice(-12).filter(entry => typeof entry?.url === 'string');
      return valid.flatMap(entry => {
        try {
          const route = page(entry.url);
          return route ? [{ ...route, title: typeof entry.title === 'string' ? entry.title.slice(0, 160) : '',
            scroll: Number.isFinite(entry.scroll) ? entry.scroll : 0 }] : [];
        } catch (_) { return []; }
      });
    } catch (_) { return []; }
  }

  let pages = readPages();
  let active = null;
  let closing = false;
  let restoring = 0;
  const save = () => {
    try { sessionStorage.setItem(storageKey, JSON.stringify(pages)); }
    catch (_) { /* Current page links still work when storage is disabled. */ }
  };

  function remember(route, title) {
    const existing = pages.find(entry => entry.key === route.key);
    if (existing) Object.assign(existing, route, title ? { title } : {});
    else {
      if (pages.length === 12) pages.splice(pages.findIndex(entry=>entry.key!==active),1);
      pages.push({ ...route, title: title || '', scroll: 0 });
    }
  }

  function highlight(input) {
    const route = page(input);
    if (!route) return;
    remember(route);
    active = route.key;
    save();
    render();
  }

  function render() {
    const tabs = pages.map(entry => {
      const tab = document.createElement('div');
      tab.className = 'desktop-tab';
      const link = document.createElement('a');
      link.href = entry.url;
      const isNewTask = entry.key === '/ui/tasks/new';
      const name = entry.title || (isNewTask ? translate('New task') :
        entry.key === entry.section ? routes.get(entry.section).textContent.trim() : entry.key.split(/[/?=]/).at(-1));
      link.textContent = name;
      link.title = name;
      link.dataset.i18nSkip = '';
      link.dataset.consoleIcon = routes.get(entry.section).dataset.consoleIcon;
      if (entry.key === active) link.setAttribute('aria-current', 'page');
      const close = document.createElement('button');
      close.type = 'button';
      close.textContent = '×';
      close.setAttribute('aria-label', translate('Close page') + ' · ' + name);
      close.addEventListener('click', () => {
        const index = pages.indexOf(entry);
        pages.splice(index, 1);
        save();
        if (entry.key === active) {
          closing = true;
          location.assign((pages[index - 1] || pages[index])?.url || '/ui');
        } else {
          render();
          strip.children[Math.min(index, pages.length - 1)]?.querySelector('button')?.focus();
        }
      });
      tab.append(link, close);
      return tab;
    });
    const offset = strip.scrollLeft;
    strip.replaceChildren(...tabs);
    strip.hidden = !tabs.length;
    strip.scrollLeft = offset;
    const current = strip.querySelector('[aria-current="page"]')?.parentElement;
    if (current) {
      const tab = current.getBoundingClientRect();
      const bar = strip.getBoundingClientRect();
      if (tab.left < bar.left) strip.scrollLeft += tab.left - bar.left;
      else if (tab.right > bar.right) strip.scrollLeft += tab.right - bar.right;
    }
  }

  const currentPage = page(location.href);
  const areaLink = menu.querySelector('.active');
  if (areaLink) areaLink.setAttribute('aria-current', 'page');
  if (currentPage && areaLink?.pathname === currentPage.section) {
    restoring = pages.find(entry => entry.key === currentPage.key)?.scroll || 0;
    highlight(location.href);
    if (area !== 'tasks' || document.body.dataset.wfTaskPage === 'new') {
      requestAnimationFrame(() => {
        if (restoring) window.scrollTo(0, restoring);
        restoring = 0;
      });
    }
  } else render();

  document.addEventListener('click', event => {
    if (menu.open && !menu.contains(event.target)) menu.open = false;
    const link = event.target.closest('.desktop-dock a,.desktop-menu-items a');
    if (!link || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    const saved = pages.find(entry => entry.key === link.pathname);
    if (saved && saved.url !== link.getAttribute('href')) {
      event.preventDefault();
      location.assign(saved.url);
    }
  });
  menu.addEventListener('keydown', event => {
    if (event.key === 'Escape' && menu.open) {
      menu.open = false;
      menu.querySelector('summary').focus();
    }
  });
  window.addEventListener('aiw:locale-changed', render);
  window.addEventListener('aiw:desktop-location', () => highlight(location.href));
  window.addEventListener('aiw:desktop-record', event => {
    if(typeof event.detail?.href!=='string')return;
    const route = page(event.detail.href);
    if (!route) return;
    remember(route, String(event.detail.title || '').slice(0,160));
    save();
    render();
  });
  window.addEventListener('aiw:desktop-content', event => {
    const entry = pages.find(item => item.key === active);
    if (entry && event.detail?.title && event.detail.title !== entry.title) {
      entry.title = String(event.detail.title).slice(0, 160);
      save();
      render();
    }
    if (restoring) {
      window.scrollTo(0, restoring);
      restoring = 0;
    }
  });
  window.addEventListener('pagehide', () => {
    if (closing) return;
    const entry = pages.find(item => item.key === active);
    if (entry) {
      entry.scroll = window.scrollY;
      save();
    }
  });
  window.addEventListener('pageshow', event => {
    if (!event.persisted) return;
    pages = readPages();
    closing = false;
    if (active) highlight(location.href);
    else render();
  });
  document.querySelector('form[action$="/ui/logout"]')?.addEventListener('submit', () => {
    closing = true;
    pages = [];
    save();
  });
})();
