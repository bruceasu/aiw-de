(() => {
  const root = document.querySelector('.mission-page');
  if (!root) return;
  const category = document.getElementById('mission-category');
  const search = document.getElementById('mission-search');
  const count = document.querySelector('[data-timeline-count]');
  const graphToggle = document.querySelector('[data-graph-toggle]');
  let graphHidden = false;
  const graphScroll = { left: 0, top: 0 };
  const rememberGraphScroll = viewport => {
    if (!graphHidden && viewport?.isConnected) {
      graphScroll.left = viewport.scrollLeft;
      graphScroll.top = viewport.scrollTop;
    }
  };
  // Controls remain outside refresh regions; their values survive snapshot replacement.
  const applyFilters = () => {
    const timeline = document.getElementById('mission-timeline');
    if (!timeline || !category || !search) return;
    const entries = [...timeline.querySelectorAll('[data-timeline-entry]')];
    const selected = category.value;
    // Keep a selected category even when no entries in the new window have it.
    const categories = [...new Set(entries.map(entry => entry.dataset.category))];
    if (selected && !categories.includes(selected)) categories.push(selected);
    const options = ['', ...categories.sort()];
    if (JSON.stringify([...category.options].map(option => option.value)) !== JSON.stringify(options)) {
      category.replaceChildren(...options.map(value => new Option(value || 'All categories', value)));
      category.value = selected;
    }
    const needle = search.value.trim().toLowerCase();
    let visible = 0;
    entries.forEach(entry => {
      entry.hidden = (!!selected && selected !== entry.dataset.category)
        || (!!needle && !entry.textContent.toLowerCase().includes(needle));
      if (!entry.hidden) visible++;
    });
    timeline.querySelectorAll('.mission-timeline-day').forEach(day => {
      day.hidden = ![...day.querySelectorAll('[data-timeline-entry]')].some(entry => !entry.hidden);
    });
    const empty = timeline.querySelector('[data-timeline-empty]');
    if (empty) empty.hidden = visible > 0 || entries.length === 0;
    if (count) count.textContent = `${visible} / ${entries.length} returned entries match`;
  };
  const applyGraph = () => {
    root.querySelectorAll('.mission-graph-panel').forEach(panel => { panel.hidden = graphHidden; });
    const viewport = root.querySelector('#mission-tasks .mission-graph-viewport');
    if (!graphHidden && viewport) {
      viewport.scrollLeft = graphScroll.left;
      viewport.scrollTop = graphScroll.top;
    }
    if (graphToggle) {
      graphToggle.setAttribute('aria-pressed', String(graphHidden));
      graphToggle.textContent = graphHidden ? 'Show graph' : 'Hide graph';
    }
  };
  document.querySelector('[data-timeline-controls]')?.removeAttribute('hidden');
  graphToggle?.removeAttribute('hidden');
  category?.addEventListener('change', applyFilters);
  search?.addEventListener('input', applyFilters);
  document.querySelector('[data-timeline-reset]')?.addEventListener('click', () => {
    category.value = '';
    search.value = '';
    applyFilters();
  });
  root.addEventListener('scroll', event => {
    if (event.target?.matches?.('.mission-graph-viewport')) rememberGraphScroll(event.target);
  }, true);
  graphToggle?.addEventListener('click', () => {
    rememberGraphScroll(root.querySelector('#mission-tasks .mission-graph-viewport'));
    graphHidden = !graphHidden;
    applyGraph();
  });
  const content = root.querySelector('.content');
  if (content) new MutationObserver(() => {
    // Only direct child replacements are observed, avoiding loops from filter DOM updates.
    // Detached or hidden viewports have no layout; restore the last visible position.
    applyFilters();
    applyGraph();
  }).observe(content, { childList: true });
  applyFilters();
  applyGraph();
})();
