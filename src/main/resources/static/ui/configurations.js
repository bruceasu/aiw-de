'use strict';
(() => {
  const $ = id => document.getElementById(id);
  const owner = document.querySelector('meta[name="configuration-owner"]').content;
  const methodNames = { 'goal-discussion':'目标梳理', requirements:'需求分析', architect:'方案设计', planner:'实施计划', coder:'代码实现', integrator:'代码集成', reviewer:'代码审查', 'stage-reviewer':'阶段审查', verifier:'测试验证', 'integration-verifier':'集成验证', repairer:'问题修复' };
  const outputNames = { REPORT:'分析报告', VERDICT:'审查结论', WORKSPACE:'代码与工作区成果' };
  const problems = {
    INVALID_CONFIGURATION:['配置需要修正','检查工作方法、资料引用和任务填写内容。'],
    UNSUPPORTED_RUNTIME:['模型配置尚不支持','选择当前批准且受支持的模型；系统不会替你切换。'],
    PREPARATION_FAILED:['暂时无法完成准备','保留当前请求，刷新记录核对结果后再处理。'],
    NO_COMPATIBLE_WORKER:['缺少兼容 Worker','到 Worker 页面检查后端、仓库和所需工具能力。'],
    AUTHORIZATION_REQUIRED:['执行范围需要确认','到授权页面确认计划、工具、仓库范围或恢复人工暂停。'],
    BUDGET_CONFIGURATION_REQUIRED:['当前严格模式要求预算','按授权页提示补齐预算；默认仅记录模式不要求此项。'],
    SOURCE_CHANGED:['原依据已发生变化','刷新记录核对原请求；更换输入或执行范围前，明确更新选择项后再准备新版本。']
  };
  let snapshot=null, options=null, origin=null, sent=null, uncertain=null;
  let busy=false, initialized=false, refreshSequence=0, policyRevision=null;
  const sources=new Map();
  const unique=prefix=>prefix+'-'+crypto.randomUUID();
  const clone=value=>structuredClone(value);
  function fingerprint(value) {
    const stable=item=>Array.isArray(item)?item.map(stable):item&&typeof item==='object'?Object.fromEntries(Object.keys(item).sort().map(key=>[key,stable(item[key])])):item;
    return JSON.stringify(stable(value));
  }
  function business(intent) {
    const value=clone(intent);
    for(const key of ['intentId','intentVersion','expectedGeneration']) delete value[key];
    value.dependencies.sort(); return fingerprint(value);
  }
  function node(tag,text,className) {
    const element=document.createElement(tag);
    if(text!==undefined) element.textContent=text;
    if(className) element.className=className;
    return element;
  }
  function feedback(text,tone='info') {
    $('configuration-result').textContent=text; $('configuration-result').dataset.tone=tone; $('configuration-result').hidden=!text;
  }
  async function request(path,value) {
    let response,data;
    try {
      response=await fetch('/api/r1/authority/execution-configurations/'+path,{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-Token':document.querySelector('meta[name="csrf-token"]').content},body:JSON.stringify(value)});
      data=await response.json();
    } catch(_) {
      const error=new Error('连接中断或响应无法读取。请先刷新记录，核对原请求结果。'); error.uncertain=path!=='state'; throw error;
    }
    if(!response.ok) {
      const error=new Error(response.status===401||response.status===403?'当前登录或操作权限不可用，请重新登录并确认身份。':(problems[data.code]||['操作未完成','请刷新记录并检查填写内容。']).join('：'));
      error.uncertain=response.status>=500&&path!=='state'; throw error;
    }
    return data;
  }
  function showTab(name,focus=false) {
    for(const tab of document.querySelectorAll('[data-configuration-tab]')) {
      const selected=tab.dataset.configurationTab===name; tab.setAttribute('aria-selected',String(selected)); tab.tabIndex=selected?0:-1;
      if(selected&&focus) tab.focus();
    }
    for(const panel of document.querySelectorAll('[data-configuration-panel]')) panel.hidden=panel.dataset.configurationPanel!==name;
  }
  const tabs=[...document.querySelectorAll('[data-configuration-tab]')];
  tabs.forEach((tab,index)=>{
    tab.addEventListener('click',()=>showTab(tab.dataset.configurationTab));
    tab.addEventListener('keydown',event=>{
      const next=event.key==='ArrowRight'?(index+1)%tabs.length:event.key==='ArrowLeft'?(index+tabs.length-1)%tabs.length:event.key==='Home'?0:event.key==='End'?tabs.length-1:-1;
      if(next>=0) { event.preventDefault(); showTab(tabs[next].dataset.configurationTab,true); }
    });
  });
  function methodLabel(name) {
    const method=snapshot?.methods.find(item=>item.role===name||item.method.name===name);
    return methodNames[method?.role||name]||name;
  }
  function planChoice() { return options?.plans.find(item=>item.id===$('prepare-plan').value); }
  function setOptions(select,choices,selected,placeholder) {
    select.replaceChildren(new Option(placeholder,''));
    for(const choice of choices) select.add(new Option(choice.label,choice.value));
    if(selected&&!choices.some(choice=>choice.value===selected)) {
      const retained=new Option('原选择（当前不可用）',selected); retained.dataset.unavailable='true'; select.add(retained);
    }
    select.value=selected||'';
  }
  const savedKey=ref=>'saved:'+ref.id+':'+ref.revision+':'+ref.digest;
  function populateSources(selected=$('prepare-method').value) {
    const repository=$('prepare-repository').value;
    for(const item of snapshot.methods) sources.set('default:'+item.role,{defaultRole:item.role,configRef:null,candidate:null});
    for(const item of options.saved) sources.set(savedKey(item.reference),{defaultRole:null,configRef:clone(item.reference),candidate:null,repositoryRef:item.repositoryRef,methodName:item.methodName});
    const choices=[...sources.entries()].filter(([,source])=>!source.repositoryRef||source.repositoryRef===repository||source===origin?.source)
      .map(([key,source])=>({value:key,label:source.defaultRole?methodLabel(source.defaultRole):methodLabel(source.methodName||source.candidate?.method.name||'原工作方法')+' · 已保存方法 '+(source.configRef?.id||'待保存').slice(-8)}));
    setOptions($('prepare-method'),choices,selected,'请选择工作方法'); updateMethodHelp();
  }
  function updateMethodHelp() {
    const source=sources.get($('prepare-method').value);
    const kind=source?.defaultRole?snapshot?.methods.find(item=>item.role===source.defaultRole)?.method.resultKind:source?.configRef?.resultKind||source?.candidate?.method.resultKind;
    $('prepare-method-help').textContent=kind?'预期产出：'+outputNames[kind]+'。具体工具仍需执行范围许可。':'选择这项任务需要的专业工作。';
  }
  function dependencies(selected=[]) {
    const host=$('prepare-dependencies'); host.replaceChildren();
    const available=options.upstream.filter(item=>item.repositoryRef===$('prepare-repository').value);
    for(const item of available) {
      const label=node('label'),input=document.createElement('input'); input.type='checkbox'; input.value=item.id; input.checked=selected.includes(item.id);
      label.append(input,node('span',item.title)); host.append(label);
    }
    for(const id of selected.filter(id=>!available.some(item=>item.id===id))) {
      const label=node('label'),input=document.createElement('input'); input.type='checkbox'; input.value=id; input.checked=true;
      label.append(input,node('span','原上游当前不在可选范围，请取消或修正：'+id)); host.append(label);
    }
    if(!host.childElementCount) host.append(node('p','没有可选的同仓库已完成任务，可以不选。','help'));
  }
  function repositoryChanged({baseline,selectedDependencies=[]}={}) {
    const repository=planChoice()?.repositories.find(item=>item.ref===$('prepare-repository').value);
    $('prepare-baselines').replaceChildren(...(repository?.baselines||[]).map(value=>new Option(value,value)));
    $('prepare-baseline').value=baseline??(repository?.baselines.length===1?repository.baselines[0]:'');
    dependencies(selectedDependencies); populateSources();
  }
  function planChanged({repository,baseline,selectedDependencies=[],revision}={}) {
    const plan=planChoice(); policyRevision=revision??plan?.policyRevision??null;
    const selected=repository??(plan?.repositories.length===1?plan.repositories[0].ref:'');
    setOptions($('prepare-repository'),(plan?.repositories||[]).map(item=>({value:item.ref,label:item.ref})),selected,'请选择工作仓库');
    repositoryChanged({baseline,selectedDependencies});
    if(plan&&(plan.paused||plan.revoked||plan.expiresAt<=Date.now())) feedback('该计划的执行范围当前暂停、撤销或过期，请先到授权页处理。','warn');
  }
  function populateChoices() {
    const selectedPlan=$('prepare-plan').value,selectedModel=$('prepare-model').value;
    setOptions($('prepare-plan'),options.plans.map(item=>({value:item.id,label:item.title+(item.paused||item.revoked||item.expiresAt<=Date.now()?' · 当前不可执行':'')})),selectedPlan,'请选择已确认执行范围的计划');
    setOptions($('prepare-model'),options.models.map(item=>({value:item.id,label:item.profile.model+' · '+item.profile.effort+' · '+item.profile.runtime})),selectedModel,'请选择已批准模型');
    populateSources();
  }
  function syncBusy() {
    $('preparation-fields').disabled=busy||Boolean(uncertain)||!initialized||!owner;
    $('prepare-submit').disabled=busy||!initialized||!owner||Boolean(uncertain&&!uncertain.checked);
    $('prepare-submit').textContent=busy?'正在检查准备条件…':uncertain?'重试同一个原请求':'检查并准备任务';
    $('new-preparation').disabled=busy||Boolean(uncertain); $('reload-preparation-options').disabled=busy||Boolean(uncertain);
  }
  function stateLabel(state) { return ({READY:'任务已创建',BLOCKED:'需要补齐条件',PREPARING:'正在准备',NEEDS_CONFIGURATION:'等待配置'})[state]||'状态待确认'; }
  function renderOutcome(preparation,intent) {
    const host=$('preparation-outcome'); host.replaceChildren(node('span',stateLabel(preparation.state),'pill '+(preparation.state==='READY'?'ok':'warn')));
    host.append(node('h3',preparation.state==='READY'?'已创建任务，等待 Worker 处理':(problems[preparation.errorCode]||['请检查准备条件'])[0]));
    host.append(node('p',preparation.state==='READY'?'准备与任务创建已经提交。这不表示执行、验证或交付已完成。':(problems[preparation.errorCode]||['','请按当前记录补齐配置。'])[1]));
    if(preparation.taskId) host.append(node('p','任务编号：'+preparation.taskId,'mono'));
    const link=node('a',preparation.state==='READY'?'查看关联计划与进度':preparation.errorCode==='NO_COMPATIBLE_WORKER'?'查看 Worker':'查看执行范围');
    link.href=preparation.state==='READY'?options.plans.find(item=>item.id===intent.planId)?.url||'/ui/plans':preparation.errorCode==='NO_COMPATIBLE_WORKER'?'/ui/workers':'/ui/authority'; host.append(link);
    if(preparation.missing?.length) {
      const details=node('details'); details.append(node('summary','查看服务器提供的具体缺口'));
      const list=node('ul'); for(const missing of preparation.missing) list.append(node('li',missing)); details.append(list); host.append(details);
    }
  }
  function renderRecords() {
    const host=$('configuration-records'); host.replaceChildren();
    for(const item of [...snapshot.preparations].reverse()) {
      const intent=options.drafts.find(draft=>draft.intent.intentId===item.intentId&&draft.intent.intentVersion===item.intentVersion)?.intent; if(!intent) continue;
      const card=node('article',undefined,'card configuration-record'),text=node('div');
      text.append(node('span',stateLabel(item.state),'pill '+(item.state==='READY'?'ok':'warn')),node('h3',intent.instruction.split('\n')[0].slice(0,100)));
      text.append(node('p',(options.plans.find(plan=>plan.id===intent.planId)?.title||intent.planId)+' · '+methodLabel(intent.defaultRole||intent.candidate?.method.name||'已保存方法')+' · 第 '+item.intentVersion+' 版'));
      if(item.errorCode) text.append(node('p',(problems[item.errorCode]||['需要核对准备条件'])[0]));
      const button=node('button',item.state==='READY'?'查看或修改':'载入继续','btn ghost'); button.type='button'; button.disabled=busy||Boolean(uncertain); button.addEventListener('click',()=>loadDraft(intent,item));
      card.append(text,button); host.append(card);
    }
    if(!host.childElementCount) host.append(node('p','还没有准备记录。从“准备任务”开始，结果会保存在这里。','empty'));
  }
  function loadDraft(intent,preparation) {
    if(busy||uncertain) return;
    const fixed=clone(intent),source={defaultRole:fixed.defaultRole,configRef:fixed.configRef,candidate:fixed.candidate,repositoryRef:fixed.repositoryRef};
    const key='original:'+fixed.intentId+':'+fixed.intentVersion; sources.set(key,source);
    origin={intent:fixed,preparation:clone(preparation),source}; sent=null;
    populateChoices(); $('prepare-plan').value=fixed.planId;
    if(!$('prepare-plan').value) { $('prepare-plan').add(new Option('原计划（当前不可用）',fixed.planId)); $('prepare-plan').value=fixed.planId; }
    planChanged({repository:fixed.repositoryRef,baseline:fixed.baseRevision,selectedDependencies:fixed.dependencies,revision:fixed.policyRevision});
    populateSources(key); $('prepare-instruction').value=fixed.instruction;
    if(![...$('prepare-model').options].some(option=>option.value===fixed.aiProfileId)) $('prepare-model').add(new Option('原模型（当前不可用）',fixed.aiProfileId));
    $('prepare-model').value=fixed.aiProfileId;
    $('prepare-draft-note').textContent='已载入原记录。相同内容使用原请求重试；修改内容后建立下一版本。更新选择项前会保留原政策和引用。';
    if(planChoice()?.policyRevision!==fixed.policyRevision) feedback('原执行范围版本已变化，当前仍保留原输入。需要更换范围时，请明确点击“更新选择项”。','warn');
    renderOutcome(preparation,fixed); showTab('prepare'); syncBusy();
  }
  async function refresh() {
    const sequence=++refreshSequence,data=await request('state',{}); if(sequence!==refreshSequence) return false;
    if(!data.uiOptions) throw new Error('平台仍提供旧版页面数据，请更新并重启平台后强制刷新页面。');
    if(data.uiOptions.ownerId!==owner) throw new Error('当前登录身份已改变，请重新打开页面，避免混用原草稿。');
    snapshot=data; options=data.uiOptions;
    if(origin) {
      const current=data.preparations.find(item=>item.intentId===origin.intent.intentId&&item.intentVersion===origin.intent.intentVersion);
      if(current) origin.preparation=clone(current);
    }
    if(!origin&&sent&&!uncertain) {
      const current=data.preparations.find(item=>item.intentId===sent.intentId&&item.intentVersion===sent.intentVersion);
      const original=options.drafts.find(item=>item.intent.intentId===sent.intentId&&item.intent.intentVersion===sent.intentVersion)?.intent;
      if(current&&original&&business(original)===business(sent)) origin={intent:clone(original),preparation:clone(current)};
    }
    $('configuration-plan-count').textContent=options.plans.length; $('configuration-model-count').textContent=options.models.length; $('configuration-worker-count').textContent=data.workers.length;
    if(!initialized) { populateChoices(); initialized=true; }
    if(uncertain) {
      const found=data.preparations.find(item=>item.intentId===uncertain.intent.intentId&&item.intentVersion===uncertain.intent.intentVersion);
      if(found) { const fixed=uncertain.intent; uncertain=null; origin={intent:clone(fixed),preparation:clone(found)}; renderOutcome(found,fixed); }
      else { uncertain.checked=true; feedback('暂未查到原请求。可明确重试同一个原请求，系统不会自动换标识或重复准备。','warn'); }
    }
    renderRecords(); syncBusy(); document.dispatchEvent(new CustomEvent('configuration-state-updated',{detail:data})); return true;
  }
  function makeIntent() {
    const plan=planChoice(),source=sources.get($('prepare-method').value),profile=options.models.find(item=>item.id===$('prepare-model').value);
    if(!plan||!source||!profile) throw new Error('请明确选择当前可用的计划、工作方法和已批准模型。');
    const repository=$('prepare-repository').value;
    if(!plan.repositories.some(item=>item.ref===repository)||source.repositoryRef&&source.repositoryRef!==repository) throw new Error('原仓库或工作方法已不在当前范围，请明确重新选择。');
    const dependencyIds=[...$('prepare-dependencies').querySelectorAll('input:checked')].map(input=>input.value);
    if(dependencyIds.some(id=>!options.upstream.some(item=>item.id===id&&item.repositoryRef===repository))) throw new Error('原上游已不在当前账号的同仓库可选范围，请取消或重新选择。');
    const candidate={intentId:origin?.intent.intentId||sent?.intentId||unique('task'),intentVersion:1,expectedGeneration:0,planId:plan.id,policyRevision,repositoryRef:repository,baseRevision:$('prepare-baseline').value.trim(),instruction:$('prepare-instruction').value,dependencies:dependencyIds,runtime:profile.profile.runtime,aiProfileId:profile.id,defaultRole:source.defaultRole,configRef:clone(source.configRef),candidate:clone(source.candidate)};
    if(origin&&business(candidate)===business(origin.intent)) return {...clone(origin.intent),expectedGeneration:origin.preparation.generation};
    else if(origin) candidate.intentVersion=Math.max(...snapshot.preparations.filter(item=>item.intentId===candidate.intentId).map(item=>item.intentVersion),origin.intent.intentVersion)+1;
    return candidate;
  }
  $('prepare-configuration').addEventListener('submit',async event=>{
    event.preventDefault(); if(busy) return;
    try {
      if(uncertain&&!uncertain.checked) throw new Error('请先刷新记录，核对原请求。');
      const intent=clone(uncertain?.intent||makeIntent());
      if(origin?.preparation.state==='READY'&&business(intent)===business(origin.intent)) { renderOutcome(origin.preparation,origin.intent); feedback('这个原请求已创建任务，不会再次创建。'); return; }
      busy=true; sent=clone(intent); syncBusy(); renderRecords(); feedback('正在核对配置、执行范围和兼容 Worker…');
      try {
        const preparation=await request('prepare',intent);
        uncertain=null; origin={intent:clone(intent),preparation:clone(preparation)};
        renderOutcome(preparation,intent); feedback(preparation.state==='READY'?'任务已创建。实际执行进度请查看关联计划。':'准备记录已保存，请按结果补齐条件。',preparation.state==='READY'?'info':'warn');
        try { await refresh(); } catch(_) { feedback('准备结果已返回并保留，但记录刷新失败。请稍后刷新；不要另建同一个任务。','warn'); }
      } catch(error) { if(error.uncertain) uncertain={intent:clone(intent),checked:false}; throw error; }
    } catch(error) { feedback(error.message,'error'); }
    finally { busy=false; syncBusy(); if(snapshot) renderRecords(); }
  });
  $('prepare-plan').addEventListener('change',()=>planChanged()); $('prepare-repository').addEventListener('change',()=>repositoryChanged()); $('prepare-method').addEventListener('change',updateMethodHelp);
  $('new-preparation').addEventListener('click',()=>{
    if(busy||uncertain) return;
    origin=null; sent=null; policyRevision=null; $('prepare-configuration').reset();
    if(initialized) { populateChoices(); planChanged(); }
    $('prepare-draft-note').textContent='正在填写另一个新任务，原记录保持不变。'; $('preparation-outcome').replaceChildren(node('p','提交后显示这个新任务的准备结果。','muted'));
  });
  $('reload-preparation-options').addEventListener('click',async()=>{
    if(busy||uncertain) return;
    try {
      await refresh(); const repository=$('prepare-repository').value,baseline=$('prepare-baseline').value,selectedDependencies=[...$('prepare-dependencies').querySelectorAll('input:checked')].map(input=>input.value);
      populateChoices(); planChanged({repository,baseline,selectedDependencies}); feedback('已明确更新到当前选择项。若执行范围或输入变化，再次准备将建立新版本。');
    } catch(error) { feedback(error.message,'error'); }
  });
  $('refresh-configurations').addEventListener('click',async()=>{ try { await refresh(); if(!uncertain) feedback('记录已刷新，当前草稿和原引用保持不变。'); } catch(error) { feedback(error.message,'error'); } });
  function selectSaved(reference,repositoryRef,methodName) {
    if(busy||uncertain) throw new Error('当前任务正在准备或等待核对，请先处理原请求；已保存方法会保留。');
    const key=savedKey(reference); sources.set(key,{defaultRole:null,configRef:clone(reference),candidate:null,repositoryRef,methodName}); populateSources(key); showTab('prepare');
    feedback('已选用保存的方法。请核对同一仓库、任务内容和模型，再明确准备。');
  }
  window.ConfigurationUI=Object.freeze({get snapshot(){return snapshot;},get options(){return options;},get taskBusy(){return busy||Boolean(uncertain);},request,refresh,feedback,showTab,unique,fingerprint,methodLabel,outputNames,node,selectSaved});
  if(owner) refresh().catch(error=>feedback(error.message,'error')); else syncBusy();
})();
