(() => {
  'use strict';
  const byId = id => document.getElementById(id);
  const search = byId('tool-search'), form = byId('tool-edit');
  const list = byId('tool-results'), status = byId('tool-status');
  const prev = byId('tools-prev'), next = byId('tools-next');
  const csrf = document.querySelector('meta[name="csrf-token"]').content;
  let filters = new FormData(search), pages = [''], after = null, loadVersion = 0;
  let current = null, editorVersion = 0;

  async function api(path, body) {
    const options = { credentials: 'same-origin', cache: 'no-store' };
    if (body !== undefined) {
      Object.assign(options, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrf },
        body: JSON.stringify(body) });
    }
    const response = await fetch('/api/r1/tools' + path, options);
    const value = await response.json().catch(() => null);
    if (!response.ok || !value) throw new Error(value?.error || '工具目录请求失败。');
    return value;
  }

  function cell(row, value) {
    const cell = row.insertCell();
    cell.textContent = value;
    return cell;
  }

  async function load(cursors = pages) {
    const revision = ++loadVersion;
    prev.disabled = next.disabled = true;
    const parameters = new URLSearchParams(filters);
    if (!parameters.get('kind')) parameters.delete('kind');
    if (cursors.at(-1)) parameters.set('after', cursors.at(-1));
    try {
      const page = await api('?' + parameters);
      if (revision !== loadVersion) return;
      pages = cursors;
      after = page.next;
      byId('tools-page').textContent = String(pages.length);
      list.replaceChildren();
      byId('tools-empty').hidden = Boolean(page.items.length);
      for (const tool of page.items) {
        const row = list.insertRow();
        const name = cell(row, ''), title = document.createElement('strong'), identity = document.createElement('small');
        title.dataset.consoleIcon = 'tools';
        title.textContent = tool.title;
        identity.textContent = tool.id + ' · v' + tool.revision;
        name.append(title, identity);
        cell(row, tool.kind);
        cell(row, [tool.httpMethod, tool.operation].filter(Boolean).join(' '));
        const badge = document.createElement('span');
        badge.className = 'badge';
        badge.dataset.status = tool.enabled ? 'ACTIVE' : 'OFFLINE';
        badge.textContent = tool.enabled ? '已启用' : '已停用';
        cell(row, '').append(badge);
        const details = document.createElement('button');
        details.type = 'button';
        details.className = 'btn ghost small-btn';
        details.textContent = '查看详情';
        details.onclick = () => {
          byId('tool-detail-title').textContent = tool.title;
          byId('tool-detail-description').textContent = tool.description || '';
          byId('tool-detail-schema').textContent = JSON.stringify(tool.inputSchema, null, 2);
          const view=byId('tool-detail');view.hidden=false;
          window.aiwDesktopWindow?.open(view,{title:tool.title,icon:'tools',onClose:()=>{view.hidden=true;}});
        };
        cell(row, '').append(details);
        if (form) {
          const edit = document.createElement('button');
          edit.type = 'button';
          edit.className = 'btn ghost';
          edit.textContent = '编辑';
          edit.onclick = async () => {
            const request = ++editorVersion;
            try {
              const entry = await api('/' + encodeURIComponent(tool.id));
              if (request === editorVersion) showEditor(entry);
            } catch (error) { if (request === editorVersion) status.textContent = error.message; }
          };
          cell(row, '').append(edit);
        }
      }
    } catch (error) {
      if (revision === loadVersion) status.textContent = error.message;
    } finally {
      if (revision === loadVersion) {
        prev.disabled = pages.length <= 1;
        next.disabled = !after;
      }
    }
  }

  function showEditor(entry) {
    const fields = form.elements, tool = entry?.tool;
    current = entry;
    fields.id.value = tool?.id || '';
    fields.id.readOnly = Boolean(tool);
    fields.title.value = tool?.title || '';
    fields.description.value = tool?.description || '';
    fields.kind.value = tool?.kind || 'MCP';
    fields.endpoint.value = entry?.endpoint || '';
    fields.operation.value = tool?.operation || '';
    fields.httpMethod.value = tool?.httpMethod || 'GET';
    fields.inputSchema.value = JSON.stringify(tool?.inputSchema || { type: 'object' }, null, 2);
    fields.enabled.checked = tool?.enabled ?? false;
    fields.kind.onchange();
    byId('tool-editor-title').textContent = tool ? '编辑 ' + tool.title : '登记工具';
    const editor=byId('tool-editor'),focus=tool?fields.title:fields.id;
    editor.hidden=false;
    byId('tool-editor-status').textContent='';
    const canClose=()=>!form.querySelector('button[type="submit"]').disabled;
    window.aiwDesktopWindow?.open(editor,{title:byId('tool-editor-title').textContent,icon:'tools',focus,canClose,canMinimize:canClose,onClose:()=>{editor.hidden=true;}});
    focus.focus();
  }

  search.onsubmit = event => {
    event.preventDefault();
    filters = new FormData(search);
    status.textContent = '';
    load(['']);
  };
  prev.onclick = () => load(pages.slice(0, -1));
  next.onclick = () => { if (after) load([...pages, after]); };
  if (form) {
    form.elements.kind.onchange = () => {
      const http = form.elements.kind.value === 'HTTP';
      byId('tool-http-method').hidden = !http;
      byId('tool-operation-label').textContent = http ? 'HTTP 操作标识' : 'MCP 工具名称';
    };
    byId('new-tool').onclick = () => { ++editorVersion; showEditor(null); };
    byId('tool-cancel').onclick = () => {
      if(form.querySelector('button[type="submit"]').disabled)return;
      ++editorVersion;
      const editor=byId('tool-editor');
      window.aiwDesktopWindow?.close(editor);
      editor.hidden=true;
    };
    form.onsubmit = async event => {
      event.preventDefault();
      const fields = form.elements, revision = editorVersion, button = event.submitter;
      button.disabled = true;
      try {
        let inputSchema;
        try { inputSchema = JSON.parse(fields.inputSchema.value); }
        catch { throw new Error('请输入有效的 JSON 入参结构。'); }
        const entry = await api('', {
          id: fields.id.value, expectedRevision: current?.tool.revision || 0, kind: fields.kind.value,
          title: fields.title.value, description: fields.description.value, endpoint: fields.endpoint.value,
          operation: fields.operation.value, httpMethod: fields.kind.value === 'HTTP' ? fields.httpMethod.value : null,
          inputSchema, enabled: fields.enabled.checked
        });
        status.textContent = '已保存 ' + entry.tool.id + ' v' + entry.tool.revision;
        if (revision === editorVersion) {
          const editor=byId('tool-editor');
          window.aiwDesktopWindow?.close(editor);
          editor.hidden=true;
        }
        await load(['']);
      } catch (error) { byId('tool-editor-status').textContent = error.message; }
      finally { button.disabled = false; }
    };
  }
  load();
})();
