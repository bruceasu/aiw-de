'use strict';
(() => {
  const UI=window.ConfigurationUI,$=id=>document.getElementById(id),clone=value=>structuredClone(value);
  const composeRole=UI.node('a','组合新的角色：选择技能与流程','btn ghost');
  composeRole.href='/ui/configurations?view=roles';
  $('panel-methods').querySelector('.configuration-section-heading').append(composeRole);
  let initialized=false,epoch=0,previewBusy=false,saveBusy=false,assetBusy=false,proposal=null,uncertainSave=null,pendingAsset=null;
  const refKey=asset=>asset.ref+':'+asset.version+':'+asset.digest;
  const selected=id=>[...$(id).querySelectorAll('input:checked')].map(input=>input.value);
  function base() { return UI.snapshot?.methods.find(item=>item.role===$('method-role').value); }
  function label(asset) { return asset.ref==='engineering-artifact-protocol'?'工程成果协议':UI.methodLabel(asset.title||asset.ref); }
  function repositoryChoices(select) {
    const current=select.value,refs=[...new Set(UI.options.plans.flatMap(plan=>plan.repositories.map(item=>item.ref)))].sort();
    select.replaceChildren(new Option('请选择已确认范围的仓库',''));
    for(const ref of refs) select.add(new Option(ref,ref));
    if(current&&!refs.includes(current)) select.add(new Option('原仓库（当前不可用）',current));
    select.value=current;
  }
  function listAssets(hostId,kind,selectedKeys) {
    const host=$(hostId),repository=$('method-repository').value;
    const required=new Set(base()?[...base().method.protocolSkills,base().method.primarySkill]:[]);
    const assets=UI.options.assets.filter(asset=>asset.kind===kind&&(!asset.repositoryRef||asset.repositoryRef===repository)&&!(kind==='skill'&&(required.has(asset.ref)||/^role-[0-9a-f]{64}$/.test(asset.ref))));
    host.replaceChildren();
    for(const asset of assets) {
      const row=UI.node('label'),input=document.createElement('input'); input.type='checkbox'; input.value=refKey(asset); input.checked=selectedKeys.includes(input.value);
      row.append(input,UI.node('span',label(asset)+' · 版本 '+asset.version)); host.append(row);
    }
    for(const key of selectedKeys.filter(key=>!assets.some(asset=>refKey(asset)===key))) {
      const stillInBase=kind==='skill'&&UI.options.assets.some(asset=>refKey(asset)===key&&required.has(asset.ref)); if(stillInBase) continue;
      const row=UI.node('label'),input=document.createElement('input'); input.type='checkbox'; input.value=key; input.checked=true;
      row.append(input,UI.node('span','原引用当前不可选，请取消或重新选择')); host.append(row);
    }
    if(!host.childElementCount) host.append(UI.node('p',repository?'没有额外可选内容，可以不选；需要时在下方登记资料。':'先选择仓库，再查看可引用内容。','help'));
  }
  function renderAssets() {
    listAssets('method-skills','skill',selected('method-skills')); listAssets('method-knowledge','knowledge',selected('method-knowledge'));
  }
  function builderValue() {
    return {role:$('method-role').value,repository:$('method-repository').value,skills:selected('method-skills'),knowledge:selected('method-knowledge'),instruction:$('method-instruction').value,command:$('method-command').checked};
  }
  function builderFingerprint() { return UI.fingerprint(builderValue()); }
  function sync() {
    $('method-fields').disabled=!initialized||saveBusy||Boolean(uncertainSave);
    $('method-preview').disabled=!initialized||previewBusy||saveBusy||Boolean(uncertainSave);
    $('method-preview').textContent=previewBusy?'正在装配预览…':'2. 预览工作方法';
    $('method-save').disabled=saveBusy||previewBusy||(!proposal?.candidate&&!uncertainSave)||Boolean(uncertainSave&&!uncertainSave.checked);
    $('method-save').textContent=saveBusy?'正在保存…':uncertainSave?'重试保存同一个原方法':'保存此方法';
    $('method-use').disabled=!proposal?.reference||saveBusy||Boolean(uncertainSave);
    $('asset-fields').disabled=!initialized||assetBusy||Boolean(pendingAsset?.uncertain);
    $('asset-submit').disabled=!initialized||assetBusy||Boolean(pendingAsset?.uncertain&&!pendingAsset.checked);
    $('asset-submit').textContent=assetBusy?'正在登记…':pendingAsset?.uncertain?'重试登记原资料':'登记资料';
  }
  function invalidate() {
    epoch++; if(uncertainSave) return;
    proposal=null; $('method-preview-result').replaceChildren(UI.node('p','输入已变化。请重新预览后再保存。','muted')); sync();
  }
  function defaultTools(role,method) {
    const tools=['MODEL','READ','SEARCH','GIT','QUESTION']; if(!method.readOnly) tools.push('WRITE');
    if(['verifier','integration-verifier'].includes(role)||$('method-command').checked) tools.push('COMMAND'); return tools;
  }
  function assembly() {
    const method=base(),values=builderValue();
    if(!method||!UI.options.plans.some(plan=>plan.repositories.some(repository=>repository.ref===values.repository))) throw new Error('请选择当前可用的基础方法和仓库。');
    if(values.skills.length>6||values.knowledge.length>8) throw new Error('额外 Skill 最多六项，知识最多八项。请缩小本次选用内容。');
    const refs=(keys,kind)=>keys.map(key=>{
      const asset=UI.options.assets.find(item=>item.kind===kind&&refKey(item)===key&&(!item.repositoryRef||item.repositoryRef===values.repository));
      if(!asset) throw new Error('原引用已不在当前仓库的可选范围，请取消或重新选择。');
      return {ref:asset.ref,version:asset.version,digest:asset.digest};
    });
    return {id:UI.unique('method'),revision:1,repositoryRef:values.repository,category:method.category,defaultRole:method.role,
      additionalSkills:refs(values.skills,'skill'),knowledge:refs(values.knowledge,'knowledge'),toolRequirements:defaultTools(method.role,method.method),methodInstruction:values.instruction.trim()||null};
  }
  function previewSummary(current) {
    const host=$('method-preview-result'),candidate=current.candidate,reference=current.reference;
    host.replaceChildren(UI.node('span',reference?'可直接选用':'待你确认','pill '+(reference?'ok':'info')));
    host.append(UI.node('h3',reference?'找到可复用的工作方法':'工作方法预览'));
    const detail=UI.node('dl',undefined,'configuration-readout');
    const row=(name,value)=>detail.append(UI.node('dt',name),UI.node('dd',value));
    row('基础方法',current.methodName); row('使用仓库',current.repository); row('预期产出',UI.outputNames[candidate?.method.resultKind||reference?.resultKind]||'请核对');
    row('Skill',(candidate?.skills||reference?.skills||[]).map(ref=>label(UI.options.assets.find(asset=>refKey(asset)===refKey(ref))||ref)+' · '+ref.version).join('\n'));
    row('参考知识',(candidate?.knowledge||reference?.knowledge||[]).map(ref=>label(UI.options.assets.find(asset=>refKey(asset)===refKey(ref))||ref)+' · '+ref.version).join('\n')||'未选用');
    row('工具需求',(candidate?.toolRequirements||reference?.toolRequirements||[]).map(tool=>({MODEL:'模型',READ:'读取',SEARCH:'搜索',GIT:'Git',QUESTION:'提出问题',WRITE:'修改文件',COMMAND:'运行命令'})[tool]||tool).join('、'));
    row('补充说明',current.guidance||'沿用基础方法'); host.append(detail,UI.node('p','保存仅固定这份方法与内容引用。执行仍需当前工具、仓库和模型许可。','help')); sync();
  }
  $('method-fields').addEventListener('input',invalidate);
  $('method-role').addEventListener('change',()=>{ renderAssets(); invalidate(); });
  $('method-repository').addEventListener('change',()=>{ renderAssets(); invalidate(); });
  $('propose-configuration').addEventListener('submit',async event=>{
    event.preventDefault(); if(previewBusy||saveBusy||uncertainSave) return;
    try {
      const input=assembly(),sequence=++epoch,signature=builderFingerprint(); previewBusy=true; proposal=null; sync();
      $('method-preview-result').replaceChildren(UI.node('p','正在检查已保存方法与选定引用…','muted'));
      const result=await UI.request('propose',input);
      if(sequence!==epoch||signature!==builderFingerprint()) return;
      proposal={candidate:result.candidate?clone(result.candidate):null,reference:result.reused?clone(result.reused):null,signature,repository:input.repositoryRef,methodName:UI.methodLabel(input.defaultRole),guidance:input.methodInstruction};
      previewSummary(proposal); UI.feedback(proposal.reference?'找到相同方法，可以直接选用。':'预览已生成，请核对摘要后明确保存。');
    } catch(error) { UI.feedback(error.message,'error'); }
    finally { previewBusy=false; sync(); }
  });
  $('method-save').addEventListener('click',async()=>{
    if(saveBusy||previewBusy) return;
    try {
      if(uncertainSave&&!uncertainSave.checked) throw new Error('请先刷新记录，核对原方法是否已经保存。');
      if(!uncertainSave&&(!proposal?.candidate||proposal.signature!==builderFingerprint())) throw new Error('预览已失效，请重新预览。');
      const captured=uncertainSave?clone(uncertainSave.proposal):clone(proposal); saveBusy=true; sync();
      try {
        const reference=await UI.request('candidates',captured.candidate);
        uncertainSave=null; proposal={...captured,candidate:null,reference:clone(reference)}; previewSummary(proposal); UI.feedback('方法已保存，可明确选用于任务准备。');
        try { await UI.refresh(); } catch(_) { UI.feedback('方法已保存，返回的引用已保留；列表刷新失败，可稍后刷新。','warn'); }
      } catch(error) { if(error.uncertain) uncertainSave={proposal:captured,checked:false}; throw error; }
    } catch(error) { UI.feedback(error.message,'error'); }
    finally { saveBusy=false; sync(); }
  });
  $('method-use').addEventListener('click',()=>{
    if(!proposal?.reference||uncertainSave) return;
    try { UI.selectSaved(proposal.reference,proposal.repository,proposal.methodName); } catch(error) { UI.feedback(error.message,'error'); }
  });
  function renderSaved() {
    const host=$('saved-methods'); host.replaceChildren();
    for(const item of UI.options.saved) {
      const card=UI.node('article',undefined,'configuration-record'),text=UI.node('div');
      text.append(UI.node('h3',UI.methodLabel(item.methodName)),UI.node('p',item.repositoryRef+' · 版本 '+item.reference.revision+' · '+UI.outputNames[item.reference.resultKind]));
      const use=UI.node('button','用于准备任务','btn ghost'); use.type='button';
      use.addEventListener('click',()=>{ try { UI.selectSaved(item.reference,item.repositoryRef,item.methodName); } catch(error) { UI.feedback(error.message,'error'); } }); card.append(text,use); host.append(card);
    }
    if(!host.childElementCount) host.append(UI.node('p','还没有自定义方法。可以直接使用默认方法，或在上方预览后保存。','empty'));
  }
  function acceptState() {
    if(!UI.snapshot) return;
    if(!initialized) {
      $('method-role').replaceChildren(new Option('请选择基础方法',''),...UI.snapshot.methods.map(item=>new Option(UI.methodLabel(item.role),item.role)));
      initialized=true;
    }
    renderSaved();
    if(!saveBusy&&!uncertainSave) { repositoryChoices($('method-repository')); renderAssets(); }
    if(!assetBusy&&!pendingAsset?.uncertain) repositoryChoices($('asset-repository'));
    if(uncertainSave) {
      const candidate=uncertainSave.proposal.candidate;
      const found=UI.options.saved.find(item=>item.reference.id===candidate.id&&item.reference.revision===candidate.revision&&item.repositoryRef===candidate.repositoryRef);
      if(found) { proposal={...uncertainSave.proposal,candidate:null,reference:clone(found.reference)}; uncertainSave=null; previewSummary(proposal); UI.feedback('已查到原方法，保存成功，未重复创建。'); }
      else uncertainSave.checked=true;
    }
    if(pendingAsset?.uncertain) {
      const fixed=pendingAsset.body;
      const found=UI.options.assets.some(asset=>asset.kind===fixed.kind&&asset.ref===fixed.ref&&asset.version===fixed.version&&asset.digest===fixed.digest&&asset.repositoryRef===fixed.repositoryRef);
      if(found) { pendingAsset=null; $('register-configuration-asset').reset(); $('asset-status').textContent='已查到原资料，登记成功。请选择仓库后再勾选资料。'; repositoryChoices($('asset-repository')); renderAssets(); }
      else pendingAsset.checked=true;
    }
    sync();
  }
  $('register-configuration-asset').addEventListener('submit',async event=>{
    event.preventDefault(); if(assetBusy) return;
    try {
      if(pendingAsset?.uncertain&&!pendingAsset.checked) throw new Error('请先刷新记录，核对原资料。');
      assetBusy=true; sync();
      let body=pendingAsset?.uncertain?clone(pendingAsset.body):null;
      if(!body) {
        const repository=$('asset-repository').value;
        if(!UI.options.plans.some(plan=>plan.repositories.some(item=>item.ref===repository))) throw new Error('请选择当前账号已确认执行范围中的仓库。');
        const content='# '+$('asset-name').value.trim()+'\n\n'+$('asset-content').value;
        const digest=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(content))),value=>value.toString(16).padStart(2,'0')).join('');
        body={kind:$('asset-kind').value,ref:UI.unique('asset'),version:'1',digest,ownerId:document.querySelector('meta[name="configuration-owner"]').content,repositoryRef:repository,content};
      }
      try {
        await UI.request('assets',body); pendingAsset=null;
        $('asset-status').textContent='资料已登记。可在本仓库的方法表单中勾选；没有自动加入任何任务。'; $('register-configuration-asset').reset();
        try { await UI.refresh(); } catch(_) { UI.feedback('资料登记已成功，但列表刷新失败，请稍后刷新。','warn'); }
      } catch(error) { if(error.uncertain) pendingAsset={body:clone(body),uncertain:true,checked:false}; throw error; }
    } catch(error) { UI.feedback(error.message,'error'); }
    finally { assetBusy=false; if(UI.options) repositoryChoices($('asset-repository')); sync(); }
  });
  document.addEventListener('configuration-state-updated',acceptState);
  if(UI.snapshot) acceptState(); else sync();
})();
