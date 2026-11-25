(() => {
  'use strict';
  const byId=id=>document.getElementById(id);
  const form=byId('skill-edit'),choice=byId('skill-choice'),history=byId('skill-history'),version=byId('skill-versions');
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
  async function select() {
    const requestId=++request;
    current=skills.find(skill=>skill.ref===choice.value)||null;
    fields.ref.value=current?.ref||'';fields.ref.readOnly=Boolean(current);
    fields.title.value=current?.title||'';fields.content.value=current?.content||'';
    fields.enabled.checked=current?.enabled??true;
    history.hidden=true;versions=[];version.replaceChildren();byId('skill-version-content').textContent='';
    if(!current)return;
    const result=await api('/'+encodeURIComponent(current.ref)+'/versions');
    if(requestId!==request)return;
    versions=result;
    for(const item of versions)version.add(new Option('v'+item.version+' · '+item.title,item.version));
    history.hidden=!versions.length;
    showVersion();
  }
  function showVersion(){
    byId('skill-version-content').textContent=versions.find(item=>item.version===Number(version.value))?.content||'';
  }
  async function load(ref='') {
    skills=await api();
    choice.replaceChildren(new Option('新建 Skill',''));
    for(const skill of skills)choice.add(new Option(skill.title+' · v'+skill.version+(skill.enabled?'':' · 已停用'),skill.ref));
    choice.value=ref;
    await select();
  }
  const report=error=>{status.textContent=error.message;};
  choice.onchange=()=>select().catch(report);
  version.onchange=showVersion;
  byId('skill-use-version').onclick=()=>{
    const selected=versions.find(item=>item.version===Number(version.value));
    if(selected){fields.title.value=selected.title;fields.content.value=selected.content;fields.content.focus();}
  };
  byId('skill-reload').onclick=()=>load(choice.value).catch(report);
  form.onsubmit=async event=>{
    event.preventDefault();
    const submit=event.submitter;if(submit.disabled)return;
    submit.disabled=true;
    try {
      const saved=await api('',{ref:fields.ref.value,expectedVersion:current?.version||0,
        enabled:fields.enabled.checked,title:fields.title.value,content:fields.content.value});
      status.textContent='已保存 v'+saved.version;
      await load(saved.ref);
    }catch(error){report(error);}
    finally{submit.disabled=false;}
  };
  load().catch(report);
})();
