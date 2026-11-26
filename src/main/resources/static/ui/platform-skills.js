(() => {
  'use strict';
  const byId=id=>document.getElementById(id);
  const form=byId('skill-edit'),historyPanel=byId('skill-history'),version=byId('skill-versions');
  const status=byId('skill-status'),fields=form.elements;
  const reviewButton=byId('skill-manager-review'),reviewResult=byId('skill-manager-result');
  const t=value=>window.aiwI18n?.t(value)||value;
  let skills=[],versions=[],current=null,request=0;
  let proposedContent=null;
  async function api(path='',data) {
    const response=await fetch('/api/r1/workflows/skills'+path,{
      method:data===undefined?'GET':'POST',credentials:'same-origin',
      headers:{'Content-Type':'application/json','X-CSRF-Token':document.querySelector('meta[name="csrf-token"]').content},
      body:data===undefined?undefined:JSON.stringify(data)
    });
    const value=await response.json().catch(()=>null);
    if(!response.ok||!value)throw new Error(t(value?.error||'无法读取或保存 Skill。'));
    return value;
  }
  function renderCatalogue() {
    const term=byId('skill-search').value.trim().toLocaleLowerCase(),filter=byId('skill-filter').value;
    const matches=skills.filter(skill=>(!filter||(filter==='builtin'?skill.builtin:(filter==='enabled')===skill.enabled))
      && [skill.title,skill.ref].some(value=>value.toLocaleLowerCase().includes(term)));
    const list=byId('skill-list');
    list.replaceChildren();
    for(const skill of matches){
      const row=list.insertRow(),name=row.insertCell(),open=document.createElement('a'),identity=document.createElement('small');
      open.href='/ui/skills?skill='+encodeURIComponent(skill.ref);
      open.textContent=skill.title;
      open.className='record-name';
      open.dataset.consoleIcon='skills';
      open.dataset.i18nSkip='';
      identity.textContent=skill.ref;
      name.append(open,identity);
      row.insertCell().textContent=skill.builtin?skill.version:'v'+skill.version;
      const badge=document.createElement('span');
      badge.className='badge';
      badge.dataset.status=skill.enabled?'ACTIVE':'OFFLINE';
      badge.textContent=t(skill.builtin?'Built-in':skill.enabled?'已启用':'已停用');
      row.insertCell().append(badge);
      row.insertCell().textContent=skill.createdMs?new Date(skill.createdMs).toLocaleString(window.aiwI18n?.locale||undefined):'—';
    }
    if(!matches.length){
      const cell=list.insertRow().insertCell();
      cell.colSpan=4;cell.className='empty';
      cell.textContent=skills.length?'没有匹配的 Skill。':'暂无 Skill。';
    }
  }
  async function select(ref) {
    const requestId=++request;
    clearReview();
    current=skills.find(skill=>skill.ref===ref)||null;
    const showing=Boolean(current)||ref==='new';
    const detail=byId('skill-detail'),desktop=window.aiwDesktopWindow;
    byId('skill-catalogue').hidden=showing&&!desktop;
    detail.hidden=!showing;
    if(!showing&&desktop?.has(detail))desktop.close(detail);
    if(ref&&!showing)status.textContent='未找到该 Skill。';
    byId('skill-heading').textContent=current?.title||'新建 Skill';
    byId('skill-heading').toggleAttribute('data-i18n-skip',Boolean(current));
    if(current)window.dispatchEvent(new CustomEvent('aiw:desktop-content',{detail:{title:current.title}}));
    fields.ref.value=current?.ref||'';fields.ref.readOnly=Boolean(current);
    fields.title.value=current?.title||'';fields.content.value=current?.content||'';
    fields.enabled.checked=current?.enabled??true;
    const builtin=Boolean(current?.builtin);
    fields.title.readOnly=builtin;fields.content.readOnly=builtin;
    byId('skill-enabled-field').hidden=builtin;
    byId('skill-edit-actions').hidden=builtin;
    byId('skill-builtin-actions').hidden=!current?.copyable;
    historyPanel.hidden=true;versions=[];version.replaceChildren();byId('skill-version-content').textContent='';
    if(showing&&desktop){
      const isOpen=desktop.has(detail);
      const marker=isOpen?null:document.createComment('skill feedback');
      if(marker){status.before(marker);detail.prepend(status);}
      const busy=()=>form.querySelector('button[type="submit"]').disabled;
      desktop.open(detail,{title:current?.title||'新建 Skill',recordTitle:Boolean(current),icon:'skills',focus:current?fields.title:fields.ref,
        canClose:()=>!busy(),canMinimize:()=>!busy(),
        onClose:()=>{
          marker.replaceWith(status);
          detail.hidden=true;current=null;++request;
          if(new URLSearchParams(location.search).has('skill'))navigate(null);
        }});
    }
    if(!current||builtin)return;
    const result=await api('/'+encodeURIComponent(current.ref)+'/versions');
    if(requestId!==request)return;
    versions=result;
    for(const item of versions)version.add(new Option('v'+item.version+' · '+item.title,item.version));
    historyPanel.hidden=!versions.length;
    showVersion();
  }
  function showVersion(){
    byId('skill-version-content').textContent=versions.find(item=>item.version===Number(version.value))?.content||'';
  }
  async function load(ref=new URLSearchParams(location.search).get('skill')) {
    const [custom,builtin]=await Promise.all([api(),api('/builtin')]);
    skills=[...custom,...builtin.map(skill=>({...skill,title:skill.ref,enabled:true,builtin:true}))];
    renderCatalogue();
    await select(ref);
  }
  function navigate(ref,replace=false){
    window.history[replace?'replaceState':'pushState'](null,'',ref?'/ui/skills?skill='+encodeURIComponent(ref):'/ui/skills');
    window.dispatchEvent(new Event('aiw:desktop-location'));
  }
  const report=error=>{status.textContent=error.message;};
  function clearReview(){
    proposedContent=null;
    reviewResult.hidden=true;
  }
  function draftInput(){
    return {title:fields.title.value.trim(),content:fields.content.value};
  }
  fields.title.addEventListener('input',clearReview);
  fields.content.addEventListener('input',clearReview);
  reviewButton.onclick=async()=>{
    const draft=draftInput();
    if(!draft.title){fields.title.reportValidity();fields.title.focus();return;}
    if(!draft.content.trim()){fields.content.reportValidity();fields.content.focus();return;}
    const selection=request;
    clearReview();
    reviewButton.disabled=true;
    status.textContent=t('The manager is reviewing…');
    try{
      const result=await api('/manager-review',{...draft,language:window.aiwI18n?.locale});
      if(selection!==request)return;
      const latest=draftInput();
      if(latest.title!==draft.title||latest.content!==draft.content){
        status.textContent=t('The draft changed during review. Ask the manager again.');
        return;
      }
      byId('skill-manager-advice').textContent=result.advice;
      byId('skill-manager-draft').textContent=result.content;
      proposedContent=result.content;
      reviewResult.hidden=false;
      status.textContent='';
    }catch(error){if(selection===request)report(error);}
    finally{reviewButton.disabled=false;}
  };
  byId('skill-manager-apply').onclick=()=>{
    if(!proposedContent)return;
    fields.content.value=proposedContent;
    clearReview();
    fields.content.focus();
    status.textContent=t('The proposed draft is in the editor. Save a new version when ready.');
  };
  byId('skill-search').oninput=renderCatalogue;
  byId('skill-filter').onchange=renderCatalogue;
  window.addEventListener('aiw:locale-changed',renderCatalogue);
  byId('skill-list').onclick=event=>{
    const link=event.target.closest('a');
    if(!link||event.button!==0||event.ctrlKey||event.metaKey||event.shiftKey||event.altKey)return;
    event.preventDefault();
    const ref=new URL(link.href).searchParams.get('skill');
    navigate(ref);select(ref).catch(report);
  };
  byId('skill-new').onclick=()=>{navigate('new');select('new').catch(report);};
  byId('skill-copy').onclick=async()=>{
    if(!current?.copyable)return;
    const original=current;
    navigate('new');
    try{
      await select('new');
      fields.ref.value='custom-'+original.ref;
      fields.title.value=original.title;
      fields.content.value=original.content;
      fields.ref.focus();
    }catch(error){report(error);}
  };
  byId('skill-back').onclick=()=>{navigate(null);select(null).catch(report);};
  window.addEventListener('popstate',()=>select(new URLSearchParams(location.search).get('skill')).catch(report));
  version.onchange=showVersion;
  byId('skill-use-version').onclick=()=>{
    const selected=versions.find(item=>item.version===Number(version.value));
    if(selected){clearReview();fields.title.value=selected.title;fields.content.value=selected.content;fields.content.focus();}
  };
  byId('skill-reload').onclick=()=>load().catch(report);
  form.onsubmit=async event=>{
    event.preventDefault();
    if(current?.builtin)return;
    const submit=event.submitter;if(submit.disabled)return;
    submit.disabled=true;
    try {
      const saved=await api('',{ref:fields.ref.value,expectedVersion:current?.version||0,
        enabled:fields.enabled.checked,title:fields.title.value,content:fields.content.value});
      status.textContent='已保存 v'+saved.version;
      navigate(saved.ref,true);
      await load(saved.ref);
    }catch(error){report(error);}
    finally{submit.disabled=false;}
  };
  load().catch(report);
})();
