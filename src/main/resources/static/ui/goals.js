(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const csrf = document.querySelector('meta[name="csrf-token"]').content;
  let current = null, data = null, dirty = false, selectedSources = new Set(), busy = false;
  let goals = [], showDeleted = false;
  let pendingDeletion = null;
  const editorDirty = new Set();
  const status = message => { $('goal-status').textContent = message; };
  const node = (tag, text, className) => { const n = document.createElement(tag); if (text != null) n.textContent = text; if (className) n.className = className; return n; };
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
  async function run(action) {
    if (busy) return;
    busy = true;
    try { await action(); } catch (e) {
      status(e.message);
      if (e.loginRequired) {
        const login = node('a', 'Sign in');
        login.href = '/ui/login?next=' + encodeURIComponent(window.location.pathname);
        $('goal-status').append(' ', login);
      }
    } finally { busy = false; renderList(); }
  }
  function clean() {
    if (dirty || editorDirty.size) throw new Error('Save your goal and plan editing changes before continuing, or reload the saved version.');
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
    const rows = goals.filter(row => row.deleted === showDeleted);
    rows.forEach(row => {
      const item = node('div', null, 'goal-list-row');
      const b = node('button', row.title, 'btn ghost goal-list-item'); b.type = 'button';
      b.setAttribute('aria-current', String(current?.id === row.id));
      b.onclick = () => run(async () => { clean(); await load(row.id); });
      const action = node('button', row.deleted ? '恢复' : '删除', `btn ${row.deleted ? 'ghost' : 'danger'} small-btn goal-list-action`);
      action.type = 'button'; action.setAttribute('aria-label', `${row.deleted ? '恢复' : '删除'}目标：${row.title}`);
      action.onclick = () => run(() => changeDeleted(row, !row.deleted));
      b.disabled = busy; action.disabled = busy;
      item.append(b, action); $('goal-list').append(item);
    });
    if (!rows.length) $('goal-list').append(node('p', showDeleted ? '最近的目标中没有已删除记录。' : '最近的目标中没有当前记录。可新建目标，或从“已删除”恢复。', 'goal-list-empty'));
  }
  function clearSelection() {
    current = null; data = null; dirty = false; editorDirty.clear();
    $('goal-detail').hidden = true; $('goal-empty').hidden = false;
    $('goal-message').value = '';
  }
  for (const deleted of [false, true]) $(deleted ? 'goal-show-deleted' : 'goal-show-active').onclick = () => run(async () => {
    clean();
    if (current && current.deleted !== deleted && $('goal-message').value.trim() && !window.confirm('切换列表将放弃当前未发送的消息，是否继续？')) return;
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
    if (deleted && !window.confirm(`删除目标“${draft.title}”？\n可在“已删除”中恢复。旧的待处理讨论回复不再采用，已创建的执行计划继续保留。${unsent}`)) return;
    $('goal-deletion-check').hidden = true;
    const controls = [...$('goal-workshop').querySelectorAll('input,textarea,select,button')].map(el => [el, el.disabled]);
    controls.forEach(([el]) => { el.disabled = true; });
    let saved;
    try {
      saved = await api(`/${encodeURIComponent(draft.id)}/${deleted ? 'delete' : 'restore'}`, {revision:draft.revision});
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
  async function load(id) {
    data = await api(`/${encodeURIComponent(id)}`); current = data.draft; dirty = false; editorDirty.clear();
    showDeleted = current.deleted;
    $('goal-detail').hidden = false; $('goal-empty').hidden = true;
    $('goal-title').value = current.title; $('goal-body').value = current.content.body;
    $('goal-missing').value = current.content.missing; $('goal-backend').value = current.content.backend;
    selectedSources = new Set(current.content.knowledgeIds);
    $('goal-revision').textContent = `${current.deleted ? 'Deleted · ' : ''}Version ${current.revision}`;
    $('goal-delete').hidden = current.deleted; $('goal-restore').hidden = !current.deleted;
    $('goal-detail').querySelectorAll('input,textarea,select,button').forEach(el => { el.disabled = current.deleted; });
    $('goal-restore').disabled = false; $('goal-reload').disabled = false;
    $('goal-messages').replaceChildren();
    current.content.messages.forEach(m => {
      const box = node('div', null, 'goal-message'); box.append(node('strong', `${m.role}${m.stale ? ' · Older goal context' : ''}`), node('p',m.text));
      $('goal-messages').append(box);
    });
    $('goal-sources').replaceChildren(); selectedSources.forEach(id => sourceChoice({id, preview:'Selected project source'}));
    renderFiles(); renderVersions(); await list();
  }
  ['goal-title','goal-body','goal-missing','goal-backend'].forEach(id => $(id).addEventListener('input', () => { dirty = true; }));
  $('goal-new').onclick = () => run(async () => { clean(); const draft = await api('', {}); await load(draft.id); status('Goal created. Start with your intent.'); });
  $('goal-reload').onclick = () => run(async () => {
    if ((dirty || editorDirty.size) && !window.confirm('Discard your unsaved edits and reload?')) return;
    await load(current.id); status('Saved version restored.');
  });
  $('goal-save').onclick = () => run(async () => {
    if (editorDirty.size) throw new Error('Save plan editing changes first.');
    await api(`/${current.id}/edit`, {revision:current.revision,title:$('goal-title').value,body:$('goal-body').value,
      missing:$('goal-missing').value,backend:$('goal-backend').value,knowledgeIds:[...selectedSources]});
    await load(current.id); status('Goal saved. Existing formal versions and plans are unchanged.');
  });
  for (const action of ['delete','restore']) $('goal-'+action).onclick = () => run(() => changeDeleted(current, action === 'delete'));
  $('goal-send').onclick = () => run(async () => {
    clean(); const text = $('goal-message').value;
    const attachments = [...document.querySelectorAll('[data-share-file]:checked')].map(n => n.value);
    const reply = await api(`/${current.id}/messages`, {key:crypto.randomUUID(),revision:current.revision,text,attachments});
    $('goal-message').value = ''; await load(current.id); status(reply.detail);
  });
  $('goal-replies').onclick = () => run(async () => {
    clean(); const results = [];
    for (const request of data.requests) results.push(await api(`/${current.id}/replies/${request}`, {}));
    await load(current.id); status(results.map(r => `${r.state}: ${r.detail}`).join(' · ') || 'No pending discussion requests.');
  });
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
    await load(current.id); $('goal-upload').value=''; status('Attachment saved locally. Convert it, review the result, then explicitly select text to share.');
  });
  function renderFiles(){
    $('goal-attachments').replaceChildren(); data.attachments.forEach(a=>{
      const box=node('div',null,'goal-attachment'); const link=node('a',a.filename); link.href=`/api/r1/goals/${current.id}/attachments/${a.id}`;
      const convert=node('button','Convert locally','btn ghost'); convert.onclick=()=>run(async()=>{clean();status('Converting locally…');await api(`/${current.id}/attachments/${a.id}/convert`,{});await load(current.id);status('Conversion status updated.');});
      box.append(link,node('p',`${a.status}: ${a.detail}`),convert);
      if(a.extracted){const detail=node('details');detail.append(node('summary','Review extracted text'),node('pre',a.extracted));box.append(detail);}
      if(a.status==='TEXT_EXTRACTED'){const label=node('label');const check=document.createElement('input');check.type='checkbox';check.value=a.id;check.dataset.shareFile='true';label.append(check,document.createTextNode(' Share this extracted text with the selected backend in my next message'));box.append(label);}
      $('goal-attachments').append(box);
    });
  }
  $('goal-formalize').onclick=()=>run(async()=>{
    clean();const version=await api(`/${current.id}/formalize`,{revision:current.revision,confirmed:$('goal-accept').checked});
    let message='Formal version saved. It does not start work.';
    if($('goal-plan-now').checked){try{await api(`/versions/${version.id}/analyze`,{revision:version.editorRevision});message+=' AI Steps requested.';}catch(e){message+=` Plan editing draft retained: ${e.message}`;}}
    await load(current.id);status(message);
  });
  const roles=[['requirement-analysis','software-requirements-analyst','Clarify requirements'],['solution-architecture','solution-architect','Design solution'],['implementation-planning','implementation-planner','Plan implementation'],['coding','coding-work-package-executor','Implement'],['integration','coding-work-package-executor','Integrate'],['repair','coding-work-package-executor','Repair']];
  function field(parent,label,value,change,multiline=false){
    const wrapper=node('label',null,'field');wrapper.append(node('span',label));const input=node(multiline?'textarea':'input');input.value=value||'';if(multiline)input.rows=3;input.oninput=()=>change(input.value);wrapper.append(input);parent.append(wrapper);return input;
  }
  function renderVersions(){
    $('goal-versions').replaceChildren();data.versions.forEach(v=>{
      const box=node('section',null,'goal-version');box.append(node('h3',`${v.snapshot.title} · Goal version ${v.sourceRevision}`),node('p',v.snapshot.ready?'Ready':'Not ready · Missing information accepted'),node('pre',v.snapshot.goal));
      if(v.planId){const link=node('a','Review execution plan','btn primary');link.href=`/ui/plans/${encodeURIComponent(v.planId)}`;box.append(link);$('goal-versions').append(box);return;}
      const spec=structuredClone(v.editor);const steps=node('div');
      const changed=()=>editorDirty.add(v.id);
      function renderSteps(){steps.replaceChildren();spec.steps.forEach((s,index)=>{
        const row=node('div',null,'goal-step');row.append(node('h4',`Step ${index+1}`));
        field(row,'Step key',s.key,value=>{s.key=value;changed();});
        const select=node('select');roles.forEach(([cap,skill,title])=>{const o=node('option',title);o.value=cap;select.append(o);});select.value=roles.find(([cap,skill])=>skill===s.skillRef&&s.requiredSkills?.includes(cap))?.[0]||'requirement-analysis';
        select.onchange=()=>{const r=roles.find(r=>r[0]===select.value);s.requiredSkills=[r[0]];s.skillRef=r[1];changed();};const label=node('label',null,'field');label.append(node('span','Role'),select);row.append(label);
        field(row,'Instruction',s.instruction,value=>{s.instruction=value;changed();},true);
        field(row,'Required skills (comma separated)',(s.requiredSkills||[]).join(','),value=>{s.requiredSkills=value.split(',').map(x=>x.trim()).filter(Boolean);changed();});
        field(row,'Depends on step keys (comma separated)',(s.dependsOn||[]).join(','),value=>{s.dependsOn=value.split(',').map(x=>x.trim()).filter(Boolean);changed();});
        field(row,'Workspace target (optional, e.g. workspace/root)',s.repositoryRef,value=>{s.repositoryRef=value.trim()||null;changed();});
        const remove=node('button','Remove step','btn ghost');remove.onclick=()=>{spec.steps.splice(index,1);changed();renderSteps();};row.append(remove);steps.append(row);
      });}
      renderSteps();box.append(steps);const actions=node('div',null,'form-actions');
      function button(title,action){const b=node('button',title,'btn ghost');b.onclick=()=>run(action);actions.append(b);}
      button('Add step',async()=>{if(spec.steps.length>=32)throw new Error('At most 32 steps.');let n=spec.steps.length+1;while(spec.steps.some(s=>s.key===`step${n}`))n++;spec.steps.push({key:`step${n}`,requiredSkills:['requirement-analysis'],preferredSkills:[],skillRef:'software-requirements-analyst',instruction:'',repositoryRef:null,baseRevision:null,dependsOn:[]});changed();renderSteps();});
      button('Save editing draft',async()=>{if(dirty||[...editorDirty].some(id=>id!==v.id))throw new Error('Save other goal or editing changes first.');await api(`/versions/${v.id}/editor`,{revision:v.editorRevision,editor:spec});editorDirty.delete(v.id);await load(current.id);status('Editing draft saved, including incomplete steps.');});
      button('Analyze Steps',async()=>{clean();await api(`/versions/${v.id}/analyze`,{revision:v.editorRevision});await load(current.id);status('AI Steps requested. Your saved editing draft remains available.');});
      button('Check / apply AI Steps',async()=>{clean();const result=await api(`/versions/${v.id}/analysis`);if(result.state!=='READY_TO_APPLY'){status(`${result.state}: ${result.error||'Waiting for analysis; manual editing remains available.'}`);return;}await api(`/versions/${v.id}/apply`,{revision:v.editorRevision});await load(current.id);status('AI Steps applied to the editing draft. Review before preparing execution.');});
      button('Prepare execution plan',async()=>{clean();if(!window.confirm('Hand this version to the operator-visible Plans console? Missing workspaces will be created now and retained. This saves a draft; you must confirm execution separately.'))return;const saved=await api(`/versions/${v.id}/prepare`,{revision:v.editorRevision,confirmed:true});await load(current.id);status(`Execution draft ${saved.planId} saved. Review it before confirming execution.`);});
      box.append(actions);$('goal-versions').append(box);
    });
  }
  run(list);
})();
