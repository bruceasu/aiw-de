(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const csrf = document.querySelector('meta[name="csrf-token"]').content;
  let current = null, data = null, dirty = false, selectedSources = new Set(), busy = false;
  let goals = [], showDeleted = false;
  let pendingDeletion = null;
  let activeStatus = null, replyTimer = null, replyPolls = 0;
  const text = message => window.aiwI18n?.t(message) || message;
  const status = message => {
    $('goal-status').textContent = text(message);
    if (activeStatus) $(activeStatus).textContent = text(message);
  };
  const node = (tag, text, className) => { const n = document.createElement(tag); if (text != null) n.textContent = text; if (className) n.className = className; return n; };
  function wishGuide() {
    const chinese = window.aiwI18n?.locale === 'zh';
    $('wish-guide-softshell').hidden = !chinese;
    $('wish-guide-spirit').hidden = chinese;
  }
  window.addEventListener('aiw:locale-changed', wishGuide);
  window.addEventListener('aiw:i18n-ready', wishGuide);
  wishGuide();
  async function participants() {
    const selected = $('goal-member').value;
    const available = await api('/participants');
    $('goal-member').replaceChildren(new Option(text('Default requirements guide'), ''));
    for (const member of available.members.filter(m => m.state === 'ACTIVE')) {
      const option = new Option(member.name + (member.skills.length ? ' · ' + member.skills.join(', ') : ''), member.id);
      option.dataset.i18nSkip = '';
      $('goal-member').append(option);
    }
    if ([...$('goal-member').options].some(option => option.value === selected)) $('goal-member').value = selected;
  }
  async function api(path, value, options = {}) {
    const post = value !== undefined;
    const response = await fetch(`/api/r1/goals${path}${post ? (path.includes('?') ? '&' : '?') + '_csrf=' + encodeURIComponent(csrf) : ''}`, {
      method: post ? 'POST' : 'GET', headers: post ? {'Content-Type':'application/json'} : {},
      ...(post ? {body:JSON.stringify(value)} : {}), ...options
    });
    if (response.status === 401 || response.redirected && new URL(response.url).pathname === '/ui/login') {
      const error = new Error('Your console session is unavailable. Copy any unsaved changes before signing in again.');
      error.loginRequired = true;
      error.httpStatus = 401;
      throw error;
    }
    const type = response.headers.get('content-type') || '';
    const failure = message => {
      const error = new Error(message);
      error.httpStatus = response.status;
      return error;
    };
    if (!type.includes('application/json')) throw failure(`服务器返回了无法读取的响应（HTTP ${response.status}），请刷新页面核对登录状态。`);
    let result;
    try { result = await response.json(); }
    catch (_) { throw failure(`服务器响应无法解析（HTTP ${response.status}）。`); }
    if (!response.ok) throw failure(result?.message || result?.error || result?.detail || `请求失败（HTTP ${response.status}）。`);
    return result;
  }
  async function run(action, target = null) {
    if (busy) return;
    busy = true;
    activeStatus = target;
    if (target) status('Processing…');
    try { await action(); } catch (e) {
      status(e.message);
      if (e.loginRequired) {
        const login = node('a', 'Sign in');
        login.href = '/ui/login?next=' + encodeURIComponent(window.location.pathname);
        $('goal-status').append(' ', login);
      }
    } finally { busy = false; activeStatus = null; renderList(); }
  }
  function clean() {
    if (dirty) throw new Error('Save your goal and plan editing changes before continuing, or reload the saved version.');
  }
  async function list() {
    goals = await api(''); renderList();
  }
  function renderList() {
    $('goal-show-active').textContent = `当前目标（${goals.filter(row => !row.deleted).length}）`;
    $('goal-show-deleted').textContent = `已删除（${goals.filter(row => row.deleted).length}）`;
    $('goal-show-active').setAttribute('aria-pressed', String(!showDeleted));
    $('goal-show-deleted').setAttribute('aria-pressed', String(showDeleted));
    $('goal-list').replaceChildren();
    const query = $('goal-list-search').value.trim().toLocaleLowerCase();
    const rows = goals.filter(row => row.deleted === showDeleted && [row.title,row.id].some(value=>value.toLocaleLowerCase().includes(query)));
    rows.forEach(row => {
      const item = $('goal-list').insertRow(), link = node('a',row.title,'record-name');
      link.href='/ui/goals?goal='+encodeURIComponent(row.id);
      link.dataset.consoleIcon='goals';link.dataset.i18nSkip='';
      link.onclick = event => {
        if(event.button!==0||event.ctrlKey||event.metaKey||event.shiftKey||event.altKey)return;
        event.preventDefault();
        run(async () => { clean(); await load(row.id); $('goal-back').focus(); });
      };
      const action = node('button', row.deleted ? '恢复' : '删除', `btn ${row.deleted ? 'ghost' : 'danger'} small-btn goal-list-action`);
      action.type = 'button'; action.setAttribute('aria-label', `${row.deleted ? '恢复' : '删除'}目标：${row.title}`);
      action.onclick = () => run(() => changeDeleted(row, !row.deleted));
      action.disabled = busy;
      item.insertCell().append(link);
      const badge=node('span',text(row.deleted?'Deleted':'Current wishes'),'badge');badge.dataset.status=row.deleted?'OFFLINE':'ACTIVE';
      item.insertCell().append(badge);
      item.insertCell().textContent='v'+row.revision;
      item.insertCell().append(action);
    });
    if (!rows.length) {
      const cell=$('goal-list').insertRow().insertCell();cell.colSpan=4;cell.className='empty';
      cell.textContent=text(query?'No matching wishes':showDeleted?'No wishes are resting in the archive.':'The pool is quiet. Plant your first wish when you are ready.');
    }
  }
  $('goal-list-search').oninput=renderList;
  function updateGoalLocation(id,title) {
    const url=id?'/ui/goals?goal='+encodeURIComponent(id):'/ui/goals';
    if(location.pathname+location.search!==url)history.replaceState(null,'',url);
    window.dispatchEvent(new Event('aiw:desktop-location'));
    if(title)window.dispatchEvent(new CustomEvent('aiw:desktop-content',{detail:{title}}));
  }
  $('goal-back').onclick=()=>run(async()=>{
    clean();
    if($('goal-message').value.trim()&&!await window.aiwConfirm(text('Discard the unsent message?'),{title:text('Back to wish list')}))return;
    clearSelection();
    await list();
    $('goal-list-search').focus();
  });
  function clearSelection() {
    const detail=$('goal-detail');
    if(window.aiwDesktopWindow?.has(detail)){window.aiwDesktopWindow.close(detail);return;}
    if ($('goal-discussion-dialog').open) $('goal-discussion-dialog').close();
    current = null; data = null; dirty = false;
    $('goal-detail').hidden = true; $('goal-catalogue').hidden = false;
    updateGoalLocation();
    $('goal-message').value = '';
  }
  const discussionDialog = $('goal-discussion-dialog');
  const discussionOpener = $('goal-discussion-open');
  const discussionMaximize = $('goal-discussion-maximize');
  function setDiscussionMaximized(maximized) {
    discussionDialog.dataset.maximized = String(maximized);
    discussionMaximize.setAttribute('aria-pressed', String(maximized));
    const label = text(maximized ? 'Restore wish discussion' : 'Maximize wish discussion');
    discussionMaximize.setAttribute('aria-label', label);
    discussionMaximize.title = label;
  }
  discussionOpener.onclick = () => {
    if (!current || current.deleted || discussionDialog.open) return;
    $('goal-discussion-subtitle').textContent = current.title;
    setDiscussionMaximized(false);
    discussionDialog.showModal();
    discussionOpener.setAttribute('aria-expanded', 'true');
    $('goal-message').focus({preventScroll:true});
  };
  $('goal-discussion-close').onclick = () => discussionDialog.close();
  discussionMaximize.onclick = () => {
    setDiscussionMaximized(discussionDialog.dataset.maximized !== 'true');
  };
  discussionDialog.addEventListener('close', () => {
    discussionOpener.setAttribute('aria-expanded', 'false');
    setDiscussionMaximized(false);
  });
  for (const deleted of [false, true]) $(deleted ? 'goal-show-deleted' : 'goal-show-active').onclick = () => run(async () => {
    clean();
    if (current && current.deleted !== deleted && $('goal-message').value.trim() && !await window.aiwConfirm('切换列表将放弃当前未发送的消息，是否继续？', {title:'切换目标列表'})) return;
    showDeleted = deleted;
    if (current && current.deleted !== deleted) clearSelection();
    renderList();
  });
  $('goal-refresh-list').onclick = () => run(async () => {
    clean();
    if (current) await load(current.id); else await list();
    status('目标列表已刷新。');
  });
  async function changeDeleted(draft, deleted) {
    clean();
    if (pendingDeletion) throw new Error('请先使用“核对操作结果”读取上一次删除或恢复的当前状态。无需再次确认或提交。');
    const unsent = current?.id === draft.id && $('goal-message').value.trim() ? '\n当前未发送的消息也将放弃。' : '';
    if (deleted && !await window.aiwConfirm(`删除目标“${draft.title}”？\n可在“已删除”中恢复。旧的待处理讨论回复不再采用，已创建的执行计划继续保留。${unsent}`, {title:'删除目标', danger:true})) return;
    $('goal-deletion-check').hidden = true;
    const controls = [...$('goal-workshop').querySelectorAll('input,textarea,select,button')].map(el => [el, el.disabled]);
    controls.forEach(([el]) => { el.disabled = true; });
    let saved;
    try {
      saved = await api(`/${encodeURIComponent(draft.id)}/${deleted ? 'delete' : 'restore'}`, {revision:draft.revision,confirmed:deleted});
      if (saved?.id !== draft.id || saved.deleted !== deleted || !Number.isSafeInteger(saved.revision)) {
        throw new Error('服务器未返回有效的目标状态。');
      }
    } catch (error) {
      const action = deleted ? '删除' : '恢复';
      if (error.httpStatus >= 400 && error.httpStatus < 500) {
        error.message = `${action}请求被拒绝：${error.message} 无需再次确认；请处理错误后刷新目标状态。`;
      } else {
        pendingDeletion = {id:draft.id, title:draft.title};
        $('goal-deletion-check').hidden = false;
        $('goal-check-deletion').hidden = false;
        $('goal-deletion-state').textContent = `暂时无法判断“${draft.title}”是否已${action}。无需再次确认，请点击“核对操作结果”；核对只读取状态，不会重复提交。`;
        error.message = `${action}结果暂时未知：${error.message} 请使用下方“核对操作结果”。`;
      }
      throw error;
    } finally {
      controls.forEach(([el, disabled]) => { el.disabled = disabled; });
    }
    goals = goals.map(row => row.id === saved.id ? saved : row);
    if (current?.id === saved.id) clearSelection();
    renderList();
    const message = deleted ? '目标已删除，可在“已删除”中恢复；已创建的执行计划保留。' : '目标已恢复，请在“当前目标”中打开。';
    try { await list(); status(message); }
    catch (error) { status(`${message} 列表刷新失败：${error.message}`); }
  }
  $('goal-check-deletion').onclick = () => run(async () => {
    if (!pendingDeletion) return;
    const target = pendingDeletion;
    const result = await api(`/${encodeURIComponent(target.id)}`);
    const draft = result?.draft;
    if (draft?.id !== target.id || typeof draft.deleted !== 'boolean' || !Number.isSafeInteger(draft.revision)) {
      throw new Error('未能读取有效的目标状态，请稍后再次核对；无需重复提交删除或恢复。');
    }
    goals = goals.map(row => row.id === draft.id ? draft : row);
    pendingDeletion = null;
    $('goal-check-deletion').hidden = true;
    $('goal-deletion-state').textContent = `已读取“${target.title}”的当前状态：${draft.deleted ? '已删除，可在“已删除”中恢复。' : '未删除，可在“当前目标”中打开。'}没有待确认的删除审批。${current?.id === draft.id ? ' 当前详情保留原有内容；请先复制未保存的编辑，再重新加载已保存版本。' : ''}`;
    status('目标状态核对完成，未重复提交任何操作。');
  });
  let configurationEpoch = 0;
  async function discussionConfiguration() {
    const epoch = ++configurationEpoch, backend = $('goal-backend').value;
    const output = $('goal-model-configuration');output.textContent = '正在读取实际讨论配置…';
    try {
      const value = await api('/discussion-configuration?backend=' + encodeURIComponent(backend));
      if(epoch !== configurationEpoch || backend !== $('goal-backend').value)return;
      const models = value.profiles.map(item => item.profile.provider + ' / ' + item.profile.model + ' / ' + item.profile.effort);
      output.textContent = (models.length ? '可用讨论配置：' + models.join('；') : '未配置明确模型，将采用匹配外部 Worker 的配置。') + ' · ' + value.protocol + ' · ' + value.auth + ' · ' + value.verification + '。发送时冻结；修改工厂设置只影响后续请求。';
    } catch(error) {if(epoch === configurationEpoch)output.textContent = error.message;}
  }
  $('goal-backend').addEventListener('change', discussionConfiguration);
  async function load(id, preserveEdits = false) {
    clearTimeout(replyTimer);
    const latest = await api(`/${encodeURIComponent(id)}`);
    if (preserveEdits && (dirty || current?.id !== id)) { scheduleReplies(); return; }
    data = latest; current = data.draft; dirty = false;
    showDeleted = current.deleted;
    const detail=$('goal-detail');
    detail.hidden = false;
    $('goal-catalogue').hidden = !window.aiwDesktopWindow;
    updateGoalLocation(current.id,current.title);
    $('goal-title').value = current.title; $('goal-body').value = current.content.body;
    $('goal-missing').value = current.content.missing; $('goal-backend').value = current.content.backend;
    $('goal-discussion-subtitle').textContent = current.title;
    discussionConfiguration();
    selectedSources = new Set(current.content.knowledgeIds);
    $('goal-revision').textContent = `${current.deleted ? 'Deleted · ' : ''}Version ${current.revision}`;
    $('goal-delete').hidden = current.deleted; $('goal-restore').hidden = !current.deleted;
    $('goal-detail').querySelectorAll('input,textarea,select,button').forEach(el => { el.disabled = current.deleted; });
    discussionDialog.querySelectorAll('input,textarea,select,button').forEach(el => { el.disabled = current.deleted; });
    $('goal-restore').disabled = false; $('goal-reload').disabled = false;
    $('goal-back').disabled = false;
    if(window.aiwDesktopWindow?.has(detail)){
      if(!preserveEdits)window.aiwDesktopWindow.open(detail,{title:current.title});
    }else if(window.aiwDesktopWindow){
      const notice=$('goal-status'),marker=document.createComment('wish feedback');
      notice.before(marker);detail.prepend(notice);
      window.aiwDesktopWindow.open(detail,{title:current.title,icon:'goals',canClose:()=>!busy,canMinimize:()=>!busy,
        beforeClose:()=>!dirty&&!$('goal-message').value.trim()||window.aiwConfirm(text('Discard unsaved wish changes?'),{title:text('Back to wish list')}),
        onClose:()=>{marker.replaceWith(notice);clearSelection();renderList();}});
    }
    $('goal-messages').replaceChildren();
    current.content.messages.forEach(m => {
      const box = node('div', null, 'goal-message'); box.append(node('strong', `${m.role}${m.stale ? ' · Older goal context' : ''}`), node('p',m.text));
      $('goal-messages').append(box);
    });
    if (!preserveEdits) {
      $('goal-sources').replaceChildren(); selectedSources.forEach(id => sourceChoice({id, preview:'Selected project source'}));
      renderFiles(); renderVersions();
    }
    await list();
    scheduleReplies();
  }
  function scheduleReplies() {
    clearTimeout(replyTimer);
    if (!current || current.deleted || !data.requests.length || replyPolls >= 40) return;
    const id = current.id;
    replyTimer = setTimeout(async () => {
      if (current?.id !== id) return;
      if (busy || dirty) { scheduleReplies(); return; }
      replyPolls++;
      await run(async () => {
        const results = [];
        for (const request of data.requests) results.push(await api(`/${id}/replies/${request}`, {}));
        if (current?.id !== id) return;
        const terminal = results.some(r => ['FAILED','CANCELLED','INVALID','UNAVAILABLE'].includes(r.state));
        await load(id, true);
        if (terminal) clearTimeout(replyTimer);
        status(results.map(r => `${text(r.state)}: ${text(r.detail)}`).join(' · '));
        if (replyPolls >= 40) status('Automatic reply checks paused. Use Check replies to continue.');
      }, 'goal-discussion-status');
    }, 3000);
  }
  ['goal-title','goal-body','goal-missing','goal-backend'].forEach(id => $(id).addEventListener('input', () => { dirty = true; }));
  $('goal-new').onclick = () => run(async () => { clean(); const draft = await api('', {}); await load(draft.id); status('A new wish is ready. What would you like to make real?'); });
  $('goal-reload').onclick = () => run(async () => {
    if ((dirty) && !await window.aiwConfirm('放弃尚未保存的目标和计划编辑，重新加载已保存版本？', {title:'重新加载目标', danger:true})) return;
    await load(current.id); status('Your saved wish is back in the pool.');
  });
  $('goal-save').onclick = () => run(async () => {
    await api(`/${current.id}/edit`, {revision:current.revision,title:$('goal-title').value,body:$('goal-body').value,
      missing:$('goal-missing').value,backend:$('goal-backend').value,knowledgeIds:[...selectedSources]});
    await load(current.id); status('Your wish is saved. Existing wish snapshots and plans are unchanged.');
  });
  for (const action of ['delete','restore']) $('goal-'+action).onclick = () => run(() => changeDeleted(current, action === 'delete'));
  $('goal-send').onclick = () => run(async () => {
    clean(); const text = $('goal-message').value;
    const attachments = [...document.querySelectorAll('[data-share-file]:checked')].map(n => n.value);
    const reply = await api(`/${current.id}/messages`, {key:crypto.randomUUID(),revision:current.revision,text,attachments,memberId:$('goal-member').value || null});
    $('goal-message').value = ''; replyPolls = 0; await load(current.id); status(reply.detail);
  }, 'goal-discussion-status');
  $('goal-replies').onclick = () => run(async () => {
    clean(); replyPolls = 0; const results = [];
    for (const request of data.requests) results.push(await api(`/${current.id}/replies/${request}`, {}));
    await load(current.id); status(results.map(r => `${r.state}: ${r.detail}`).join(' · ') || 'No pending discussion requests.');
  }, 'goal-discussion-status');
  function sourceChoice(source) {
    const label = node('label', null, 'goal-source'); const check = document.createElement('input'); check.type='checkbox'; check.checked=selectedSources.has(source.id);
    check.onchange=()=>{ if(check.checked) selectedSources.add(source.id); else selectedSources.delete(source.id); dirty=true; };
    label.append(check, document.createTextNode(` ${source.id} · ${source.preview}`)); $('goal-sources').append(label);
  }
  $('goal-search').onclick=()=>run(async()=>{
    const sources=await api(`/sources?query=${encodeURIComponent($('goal-query').value)}`); $('goal-sources').replaceChildren();
    const found=new Set(sources.map(s=>s.id)); selectedSources.forEach(id=>{if(!found.has(id)) sourceChoice({id,preview:'Selected project source'});}); sources.forEach(sourceChoice);
  });
  $('goal-upload').onchange=()=>run(async()=>{
    clean(); const file=$('goal-upload').files[0]; if(!file)return;
    if(file.size>10*1024*1024)throw new Error('Attachment exceeds 10 MiB.');
    await api(`/${current.id}/attachments?revision=${current.revision}&filename=${encodeURIComponent(file.name)}`, {}, {headers:{'Content-Type':'application/octet-stream'},body:file});
    await load(current.id); $('goal-upload').value=''; status('附件已保存在本地。图片可明确勾选发送；其他附件请先转换、核对提取文本，再勾选发送。');
  });
  function renderFiles(){
    $('goal-attachments').replaceChildren(); data.attachments.forEach(a=>{
      const box=node('div',null,'goal-attachment'); const link=node('a',a.filename); link.href=`/api/r1/goals/${current.id}/attachments/${a.id}`;
      const convert=node('button','Convert locally','btn ghost'); convert.onclick=()=>run(async()=>{clean();status('Converting locally…');await api(`/${current.id}/attachments/${a.id}/convert`,{});await load(current.id);status('Conversion status updated.');});
      box.append(link,node('p',`${a.status}: ${a.detail}`));if(a.status!=='IMAGE_READY')box.append(convert);
      if(a.extracted){const detail=node('details');detail.append(node('summary','Review extracted text'),node('pre',a.extracted));box.append(detail);}
      if(a.status==='TEXT_EXTRACTED'){const label=node('label');const check=document.createElement('input');check.type='checkbox';check.value=a.id;check.dataset.shareFile='true';label.append(check,document.createTextNode(' Share this extracted text with the selected backend in my next message'));box.append(label);}
      if(a.status==='IMAGE_READY'){const label=node('label');const check=document.createElement('input');check.type='checkbox';check.value=a.id;check.dataset.shareFile='true';label.append(check,document.createTextNode(' 将这张图片发送给本次所选模型服务（需要支持图片的 API Key SDK 模型；每条最多 4 张、合计 512 KiB）'));box.append(label);}
      $('goal-attachments').append(box);
    });
  }
  $('goal-formalize').onclick=()=>run(async()=>{
    clean();
    let confirmed=$('goal-accept').checked;
    if(current.content.missing.trim() && !confirmed) {
      confirmed=await window.aiwConfirm(`${text('This goal still lists missing information. Save a formal version marked Not ready?')}\n\n${current.content.missing}`, {title:text('Save goal version (does not start execution)')});
      if(!confirmed){status('Formal version was not saved. Resolve the missing information or explicitly accept it.');return;}
    }
    const version=await api(`/${current.id}/formalize`,{revision:current.revision,confirmed});
    let message=text('Goal version saved. Execution has not started.');
    if($('goal-plan-now').checked){const prepared=await api(`/versions/${version.id}/workflow`,{revision:version.editorRevision,restart:false});location.href=workflowLocation(prepared,version.id);return;}
    await load(current.id);status(message);
    const saved=[...$('goal-versions').children].find(el=>el.dataset.versionId===version.id);
    saved?.scrollIntoView({block:'start',behavior:'smooth'});
  }, 'goal-formalize-status');
  function workflowLocation(result, version) {
    if(result.workflowId)return '/ui/workflows?definition='+encodeURIComponent(result.workflowId);
    return '/ui/workflows?execution='+encodeURIComponent(result.executionId)+'&goalVersion='+encodeURIComponent(version);
  }
  function renderVersions(){
    $('goal-versions').replaceChildren();
    data.versions.forEach(v=>{
      const box=node('section',null,'goal-version');box.dataset.versionId=v.id;
      box.append(node('h3',`${v.snapshot.title} · ${text('Goal version')} ${v.sourceRevision}`),
        node('p',text(v.snapshot.ready?'Ready':'Not ready · Missing information accepted')),node('pre',v.snapshot.goal));
      if(v.planId){const link=node('a',text('View execution plan'),'btn primary');link.href='/ui/plans/'+encodeURIComponent(v.planId);box.append(link);}
      else {
        const existing=v.editor.workflowGenerationId;
        const generate=node('button',text(existing?'View workflow draft':'Generate workflow draft'),'btn primary');
        generate.onclick=()=>run(async()=>{clean();const result=await api(`/versions/${v.id}/workflow`,{revision:v.editorRevision,restart:false});location.href=workflowLocation(result,v.id);});
        box.append(node('p',text('A Worker organizes these requirements into a workflow draft. Review it before starting business work.'),'help'),generate);
        if(existing&&!v.editor.workflowId){
          const retry=node('button',text('Regenerate workflow draft'),'btn ghost');
          retry.onclick=()=>run(async()=>{clean();const result=await api(`/versions/${v.id}/workflow`,{revision:v.editorRevision,restart:true});location.href=workflowLocation(result,v.id);});box.append(retry);
        }
      }
      $('goal-versions').append(box);
    });
  }
  run(async () => {
    await list();
    await participants();
    const goal = new URLSearchParams(location.search).get('goal');
    if (goal) await load(goal);
  });
})();
