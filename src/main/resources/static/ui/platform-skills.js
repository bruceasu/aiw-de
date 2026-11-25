(() => {
  'use strict';
  const byId=id=>document.getElementById(id);
  const form=byId('skill-edit'),historyPanel=byId('skill-history'),version=byId('skill-versions');
  const status=byId('skill-status'),fields=form.elements;
  let skills=[],versions=[],current=null,request=0;
  async function api(path='',data) {
    const response=await fetch('/api/r1/workflows/skills'+path,{
      method:data===undefined?'GET':'POST',credentials:'same-origin',
      headers:{'Content-Type':'application/json','X-CSRF-Token':document.querySelector('meta[name="csrf-token"]').content},
      body:data===undefined?undefined:JSON.stringify(data)
    });
    const value=await response.json().catch(()=>null);
    if(!response.ok||!value)throw new Error(value?.error||'无法读取或保存 Skill。');
    return value;
  }
  function renderCatalogue() {
    const term=byId('skill-search').value.trim().toLocaleLowerCase(),filter=byId('skill-filter').value;
    const matches=skills.filter(skill=>(!filter||(filter==='enabled')===skill.enabled)
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
      row.insertCell().textContent='v'+skill.version;
      const badge=document.createElement('span');
      badge.className='badge';
      badge.dataset.status=skill.enabled?'ACTIVE':'OFFLINE';
      badge.textContent=skill.enabled?'已启用':'已停用';
      row.insertCell().append(badge);
      row.insertCell().textContent=skill.createdMs?new Date(skill.createdMs).toLocaleString():'—';
    }
    if(!matches.length){
      const cell=list.insertRow().insertCell();
      cell.colSpan=4;cell.className='empty';
      cell.textContent=skills.length?'没有匹配的 Skill。':'暂无 Skill。';
    }
  }
  async function select(ref) {
    const requestId=++request;
    current=skills.find(skill=>skill.ref===ref)||null;
    const showing=Boolean(current)||ref==='new';
    const detail=byId('skill-detail'),desktop=window.aiwDesktopWindow;
    byId('skill-catalogue').hidden=showing&&!desktop;
    detail.hidden=!showing;
    if(!showing&&desktop?.has(detail))desktop.close(detail);
    if(ref&&!showing)status.textContent='未找到该 Skill。';
    byId('skill-heading').textContent=current?.title||'新建 Skill';
    if(current)window.dispatchEvent(new CustomEvent('aiw:desktop-content',{detail:{title:current.title}}));
    fields.ref.value=current?.ref||'';fields.ref.readOnly=Boolean(current);
    fields.title.value=current?.title||'';fields.content.value=current?.content||'';
    fields.enabled.checked=current?.enabled??true;
    historyPanel.hidden=true;versions=[];version.replaceChildren();byId('skill-version-content').textContent='';
    if(showing&&desktop){
      const isOpen=desktop.has(detail);
      const marker=isOpen?null:document.createComment('skill feedback');
      if(marker){status.before(marker);detail.prepend(status);}
      const busy=()=>form.querySelector('button[type="submit"]').disabled;
      desktop.open(detail,{title:current?.title||'新建 Skill',icon:'skills',focus:current?fields.title:fields.ref,
        canClose:()=>!busy(),canMinimize:()=>!busy(),
        onClose:()=>{
          marker.replaceWith(status);
          detail.hidden=true;current=null;++request;
          if(new URLSearchParams(location.search).has('skill'))navigate(null);
        }});
    }
    if(!current)return;
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
    skills=await api();
    renderCatalogue();
    await select(ref);
  }
  function navigate(ref,replace=false){
    window.history[replace?'replaceState':'pushState'](null,'',ref?'/ui/skills?skill='+encodeURIComponent(ref):'/ui/skills');
    window.dispatchEvent(new Event('aiw:desktop-location'));
  }
  const report=error=>{status.textContent=error.message;};
  byId('skill-search').oninput=renderCatalogue;
  byId('skill-filter').onchange=renderCatalogue;
  byId('skill-list').onclick=event=>{
    const link=event.target.closest('a');
    if(!link||event.button!==0||event.ctrlKey||event.metaKey||event.shiftKey||event.altKey)return;
    event.preventDefault();
    const ref=new URL(link.href).searchParams.get('skill');
    navigate(ref);select(ref).catch(report);
  };
  byId('skill-new').onclick=()=>{navigate('new');select('new').catch(report);};
  byId('skill-back').onclick=()=>{navigate(null);select(null).catch(report);};
  window.addEventListener('popstate',()=>select(new URLSearchParams(location.search).get('skill')).catch(report));
  version.onchange=showVersion;
  byId('skill-use-version').onclick=()=>{
    const selected=versions.find(item=>item.version===Number(version.value));
    if(selected){fields.title.value=selected.title;fields.content.value=selected.content;fields.content.focus();}
  };
  byId('skill-reload').onclick=()=>load().catch(report);
  form.onsubmit=async event=>{
    event.preventDefault();
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
