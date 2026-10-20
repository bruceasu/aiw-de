(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const csrf = document.querySelector('meta[name="csrf-token"]').content;
  let current = null, data = null, dirty = false, selectedSources = new Set(), busy = false;
  const editorDirty = new Set();
  const status = message => { $('goal-status').textContent = message; };
  const node = (tag, text, className) => { const n = document.createElement(tag); if (text != null) n.textContent = text; if (className) n.className = className; return n; };
  async function api(path, value, options = {}) {
    const post = value !== undefined;
    const response = await fetch(`/api/r1/goals${path}${post ? (path.includes('?') ? '&' : '?') + '_csrf=' + encodeURIComponent(csrf) : ''}`, {
      method: post ? 'POST' : 'GET', headers: post ? {'Content-Type':'application/json'} : {},
      ...(post ? {body:JSON.stringify(value)} : {}), ...options
    });
    const type = response.headers.get('content-type') || '';
    if (!type.includes('application/json')) throw new Error('Console session expired or request was rejected. Reload the page.');
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || `Request failed (${response.status})`);
    return result;
  }
  async function run(action) {
    if (busy) return;
    busy = true;
    try { await action(); } catch (e) { status(e.message); } finally { busy = false; }
  }
  function clean() {
    if (dirty || editorDirty.size) throw new Error('Save your goal and plan editing changes before continuing, or reload the saved version.');
  }
  async function list() {
    const rows = await api(''); $('goal-list').replaceChildren();
    rows.forEach(row => {
      const b = node('button', `${row.deleted ? '[Deleted] ' : ''}${row.title}`, 'btn ghost goal-list-item');
      b.onclick = () => run(async () => { clean(); await load(row.id); }); $('goal-list').append(b);
    });
  }
  async function load(id) {
    data = await api(`/${encodeURIComponent(id)}`); current = data.draft; dirty = false; editorDirty.clear();
    $('goal-detail').hidden = false;
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
  for (const action of ['delete','restore']) $('goal-'+action).onclick = () => run(async () => {
    clean(); if (action === 'delete' && !window.confirm('Delete this goal? You can restore it. Old pending replies will be discarded.')) return;
    await api(`/${current.id}/${action}`, {revision:current.revision}); await load(current.id); status(`Goal ${action === 'delete' ? 'deleted' : 'restored'}.`);
  });
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
