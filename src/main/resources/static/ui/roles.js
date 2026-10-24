'use strict';
(() => {
  const $=id=>document.getElementById(id),clone=value=>structuredClone(value);
  const names={'goal-discussion':'目标梳理',requirements:'需求分析',architect:'方案设计',planner:'实施计划',coder:'代码实现',integrator:'代码集成',reviewer:'代码审查','stage-reviewer':'阶段审查',verifier:'测试验证','integration-verifier':'集成验证',repairer:'问题修复',research:'资料研究','domain-modeling':'领域建模','codebase-design':'代码设计','improve-codebase-architecture':'架构改进','diagnosing-bugs':'故障诊断','resolving-merge-conflicts':'冲突修复',teach:'讲解与教学',handoff:'工作交接','edit-article':'文章编辑',prototype:'原型制作','delegation-planning':'委派判断'};
  const outputs={REPORT:'分析报告',VERDICT:'审查结论',WORKSPACE:'代码与工作区成果'};
  const reasons={INCOMPATIBLE_OUTPUT:'所选专业技能与最终产出不兼容。请保持模板原产出，或更换为适合的技能。',SKILL_VERSION_CONFLICT:'同一个技能的步骤引用了不同版本，请统一版本。',ROLE_LIMIT_EXCEEDED:'角色最多八步、六种技能和八份知识；也请缩短过长的说明。',STEP_SKILLS_INVALID:'每步请选择一至六种技能；工程协议和已生成角色不能用作步骤技能。',ROLE_TOOLS_INVALID:'工具需求与产出不一致，请重新选择产出并预览。',ROLE_ASSET_MISMATCH:'角色定义与保存配置不一致，请核对原版本。',NOT_COMPOSED_ROLE:'这份配置不是可复制的组合角色。'};
  let state=null,draft=null,epoch=0,refreshEpoch=0,preview=null,previewBusy=false,saveBusy=false,copyBusy=false,uncertain=null;
  const key=a=>a.ref+':'+a.version+':'+a.digest;
  const uid=prefix=>prefix+'-'+crypto.randomUUID();
  function node(tag,text,className){const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(className)n.className=className;return n;}
  function feedback(text,tone='info'){$('role-feedback').textContent=text;$('role-feedback').dataset.tone=tone;$('role-feedback').hidden=!text;}
  function signature(value){const sorted=x=>Array.isArray(x)?x.map(sorted):x&&typeof x==='object'?Object.fromEntries(Object.keys(x).sort().map(k=>[k,sorted(x[k])])):x;const d=clone(value);d.toolRequirements.sort();return JSON.stringify(sorted(d));}
  function useUrl(ref){const q=new URLSearchParams({method:ref.id,revision:String(ref.revision)});return '/ui/configurations?'+q;}
  async function request(path,body){
    let response,data;
    try{response=await fetch('/api/r1/authority/execution-configurations/'+path,{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-Token':document.querySelector('meta[name="csrf-token"]').content},body:JSON.stringify(body)});data=await response.json();}
    catch(_){const e=new Error('连接中断或响应无法读取，请刷新并核对原记录。');e.uncertain=path.startsWith('candidates');throw e;}
    if(!response.ok){const e=new Error(response.status===401||response.status===403?'请登录并确认当前操作身份。':reasons[data.reason]||(data.code==='SOURCE_CHANGED'?'原版本内容已经变化，请核对记录；不要另换标识重试。':'请检查必填内容、技能版本、专业技能的输入与产出约束，以及仓库范围。'));e.uncertain=response.status>=500&&path.startsWith('candidates');throw e;}
    return data;
  }
  function sync(){
    const locked=!state||saveBusy||copyBusy||Boolean(uncertain);
    $('role-fields').disabled=locked;$('role-new').disabled=locked;$('role-preview').disabled=locked||previewBusy;
    $('role-preview').textContent=previewBusy?'正在检查组合…':'预览组合角色';
    $('role-save').disabled=saveBusy||copyBusy||previewBusy||(!uncertain&&(!preview||Boolean(preview.reference)))||Boolean(uncertain&&!uncertain.checked);
    $('role-save').textContent=saveBusy?'正在保存…':uncertain?'重试保存原角色':'保存角色';
    $('role-use').hidden=!preview?.reference||Boolean(uncertain);if(preview?.reference)$('role-use').href=useUrl(preview.reference);
    for(const b of document.querySelectorAll('[data-role-copy]'))b.disabled=locked||previewBusy;
  }
  function invalidate(){epoch++;if(!uncertain){preview=null;$('role-preview-result').replaceChildren(node('p','内容已变化，请重新预览。','muted'));}sync();}
  function repositories(){return [...new Set(state.uiOptions.plans.flatMap(p=>p.repositories.map(r=>r.ref)))].sort();}
  function options(select,items,value,empty){select.replaceChildren(new Option(empty,''));for(const i of items)select.add(new Option(i.label,i.value));if(value&&!items.some(i=>i.value===value))select.add(new Option('原选择（当前不可用）',value));select.value=value||'';}
  function fresh(){
    const protocol=state.uiOptions.assets.find(a=>a.kind==='skill'&&a.ref==='engineering-artifact-protocol'&&!a.repositoryRef);
    if(!protocol)throw new Error('当前目录缺少工程成果协议，请检查平台资源。');
    return {protocol:'aiw.role.composition.v1',id:uid('role'),revision:1,repositoryRef:'',category:'product-engineering',name:'',goal:'',delivery:'',resultKind:'REPORT',protocolSkill:reference(protocol),steps:[newStep()],knowledge:[],toolRequirements:['MODEL','READ','SEARCH','GIT','QUESTION']};
  }
  function reference(a){return {ref:a.ref,version:a.version,digest:a.digest};}
  function newStep(){return {key:uid('step'),title:'',skills:[],instruction:'',whenCondition:'',completion:''};}
  function assetLabel(a){const m=state.methods.find(m=>m.method.primarySkill===a.ref);return names[a.ref]||(m?names[m.role]+' · 专业技能':a.title||a.ref);}
  function available(kind){return state.uiOptions.assets.filter(a=>a.kind===kind&&(!a.repositoryRef||a.repositoryRef===draft.repositoryRef)&&a.ref!=='engineering-artifact-protocol'&&!/^role-[0-9a-f]{64}$/.test(a.ref));}
  function checks(host,kind,chosen,change){
    const assets=available(kind);host.replaceChildren();
    for(const a of assets){const row=node('label'),input=document.createElement('input');input.type='checkbox';input.checked=chosen.some(r=>key(r)===key(a));input.addEventListener('change',()=>{change(input.checked?[...chosen,reference(a)]:chosen.filter(r=>key(r)!==key(a)));invalidate();renderStepsAndKnowledge();});row.append(input,node('span',assetLabel(a)+' · '+a.version));host.append(row);}
    for(const a of chosen.filter(r=>!assets.some(a=>key(a)===key(r)))){const row=node('label'),input=document.createElement('input');input.type='checkbox';input.checked=true;input.addEventListener('change',()=>{change(chosen.filter(r=>key(r)!==key(a)));invalidate();renderStepsAndKnowledge();});row.append(input,node('span','原引用当前不可用，请取消或重新选择：'+a.ref));host.append(row);}
    if(!host.childElementCount)host.append(node('p',draft.repositoryRef?'没有可选资料；可先在任务配置页登记。':'先选择使用仓库。','help'));
  }
  function stepField(step,property,label,rows,max){const field=node('label',undefined,'field configuration-full');field.append(node('span',label));const input=document.createElement(rows?'textarea':'input');if(rows)input.rows=rows;input.maxLength=max;input.value=step[property];input.required=property!=='whenCondition';input.addEventListener('input',()=>{step[property]=input.value;invalidate();});field.append(input);return field;}
  function renderStepsAndKnowledge(){
    const host=$('role-steps');host.replaceChildren();
    draft.steps.forEach((step,index)=>{
      const card=node('section',undefined,'role-step'),head=node('div',undefined,'role-heading'),actions=node('div',undefined,'role-step-actions');head.append(node('h3','步骤 '+(index+1)));
      for(const [label,disabled,act] of [['上移',index===0,()=>{[draft.steps[index-1],draft.steps[index]]=[draft.steps[index],draft.steps[index-1]];}],['下移',index===draft.steps.length-1,()=>{[draft.steps[index+1],draft.steps[index]]=[draft.steps[index],draft.steps[index+1]];}],['删除',draft.steps.length===1,()=>draft.steps.splice(index,1)]]){const b=node('button',label,'btn ghost');b.type='button';b.disabled=disabled;b.addEventListener('click',()=>{act();invalidate();renderStepsAndKnowledge();});actions.append(b);}
      head.append(actions);const fields=node('div',undefined,'form-grid');fields.append(stepField(step,'title','步骤名称',0,120));
      const skillField=node('div',undefined,'configuration-full'),skillList=node('div',undefined,'configuration-checks');skillField.append(node('strong','本步使用的技能'),skillList);checks(skillList,'skill',step.skills,value=>step.skills=value);fields.append(skillField,stepField(step,'instruction','本步做什么',2,2000),stepField(step,'whenCondition','什么情况下执行（留空表示始终执行）',2,500),stepField(step,'completion','怎样算完成',2,1000));card.append(head,fields);host.append(card);
    });
    checks($('role-knowledge'),'knowledge',draft.knowledge,value=>draft.knowledge=value);$('role-add-step').disabled=draft.steps.length>=8;
  }
  function renderDraft(){
    for(const [id,field] of [['role-name','name'],['role-goal','goal'],['role-delivery','delivery'],['role-output','resultKind']])$(id).value=draft[field];
    options($('role-repository'),repositories().map(r=>({value:r,label:r})),draft.repositoryRef,'请选择仓库');
    options($('role-category'),state.categories.filter(c=>c.softwareWorker).map(c=>({value:c.id,label:c.chinese})),draft.category,'请选择分类');
    $('role-command').checked=draft.toolRequirements.includes('COMMAND');renderStepsAndKnowledge();sync();
  }
  function input(){
    const d=clone(draft);d.name=d.name.trim();
    if(!repositories().includes(d.repositoryRef))throw new Error('请选择当前账号已确认范围中的仓库。');
    const selected=new Map();for(const step of d.steps)for(const ref of step.skills){const actual=available('skill').find(a=>key(a)===key(ref));if(!actual)throw new Error('步骤中有不可用技能，请重新选择。');const old=selected.get(ref.ref);if(old&&key(old)!==key(ref))throw new Error(reasons.SKILL_VERSION_CONFLICT);selected.set(ref.ref,ref);}
    if(d.steps.some(s=>!s.skills.length))throw new Error('每个步骤至少选择一种技能。');
    if(selected.size>6||d.knowledge.length>8)throw new Error(reasons.ROLE_LIMIT_EXCEEDED);
    if(d.knowledge.some(r=>!available('knowledge').some(a=>key(a)===key(r))))throw new Error('存在当前仓库不可用的参考资料，请重新选择。');
    d.toolRequirements=['READ','SEARCH','GIT','MODEL','QUESTION'];if(d.resultKind==='WORKSPACE')d.toolRequirements.push('WRITE');if($('role-command').checked)d.toolRequirements.push('COMMAND');return d;
  }
  function summary(p){
    const d=p.definition,host=$('role-preview-result');host.replaceChildren(node('span',p.reference?'已保存，可选用':'待你核对','pill '+(p.reference?'ok':'info')),node('h3',d.name));
    const toolNames={MODEL:'模型',READ:'读取',SEARCH:'搜索',GIT:'Git',QUESTION:'问答',WRITE:'写入',COMMAND:'运行命令'};
    const detail=node('dl',undefined,'configuration-readout');for(const [label,value]of [['职责',d.goal],['仓库',d.repositoryRef],['最终产出',outputs[d.resultKind]],['整体交付',d.delivery],['参考知识',d.knowledge.map(a=>assetLabel(state.uiOptions.assets.find(x=>key(x)===key(a))||a)+' · '+a.version).join('、')||'未选用'],['工具需求',d.toolRequirements.map(t=>toolNames[t]||t).join('、')]])detail.append(node('dt',label),node('dd',value));host.append(detail);
    const list=node('ol');for(const step of d.steps){const li=node('li');li.append(node('strong',step.title),node('p',step.skills.map(a=>assetLabel(state.uiOptions.assets.find(x=>key(x)===key(a))||a)).join('、')+'；'+(step.whenCondition||'始终执行')),node('p',step.instruction),node('p','完成：'+step.completion));list.append(li);}host.append(list,node('p','具体任务、模型和执行范围在准备任务时核对。','help'));sync();
  }
  function renderSaved(){
    const host=$('role-saved');host.replaceChildren();for(const item of state.roles||[]){const card=node('article',undefined,'configuration-record'),text=node('div'),actions=node('div',undefined,'configuration-form-actions');text.append(node('h3',item.name),node('p',item.repositoryRef+' · 版本 '+item.reference.revision+' · '+outputs[item.reference.resultKind]));const use=node('a','用于准备任务','btn ghost');use.href=useUrl(item.reference);const copy=node('button','复制并编辑','btn ghost');copy.type='button';copy.dataset.roleCopy='true';copy.addEventListener('click',()=>copyRole(item.reference));actions.append(use,copy);card.append(text,actions);host.append(card);}if(!host.childElementCount)host.append(node('p','还没有组合角色。默认模板仍可直接用于任务准备。','empty'));sync();
  }
  async function copyRole(ref){if(copyBusy||saveBusy||previewBusy||uncertain)return;copyBusy=true;sync();try{const d=await request('state?kind=role',ref);draft={...d,id:uid('role'),revision:1,name:d.name.slice(0,116)+' 副本'};invalidate();renderDraft();feedback('已复制为新角色草稿；原版本及任务保持不变。');}catch(e){feedback(e.message,'error');}finally{copyBusy=false;sync();}}
  async function refresh(){
    const generation=++refreshEpoch,next=await request('state',{});if(generation!==refreshEpoch)return;
    if(!next.uiOptions||next.uiOptions.ownerId!==document.querySelector('meta[name="configuration-owner"]').content)throw new Error('当前身份或页面数据已经变化，请重新打开页面，避免混用原草稿。');
    state=next;if(!draft)draft=fresh();options($('role-template'),state.methods.map(m=>({value:m.role,label:names[m.role]||m.role})),$('role-template').value,'选择一个模板');renderDraft();renderSaved();
    if(uncertain){const pending=uncertain,found=(state.roles||[]).find(r=>r.reference.id===pending.proposal.definition.id&&r.reference.revision===pending.proposal.definition.revision);if(found){const actual=await request('state?kind=role',found.reference);if(uncertain!==pending)return;if(signature(actual)!==signature(pending.proposal.definition))throw new Error('原标识已有不同内容，不能重试覆盖。请核对已保存版本。');preview={...pending.proposal,reference:found.reference};uncertain=null;summary(preview);feedback('已核对原角色保存成功，未重复创建。');}else{pending.checked=true;feedback('当前未找到原版本。可显式重试保存同一个原角色。','warn');}}sync();
  }
  for(const [id,field] of [['role-name','name'],['role-goal','goal'],['role-delivery','delivery'],['role-output','resultKind'],['role-category','category']])$(id).addEventListener('input',()=>{draft[field]=$(id).value;invalidate();});
  $('role-repository').addEventListener('change',()=>{draft.repositoryRef=$('role-repository').value;invalidate();renderStepsAndKnowledge();});$('role-command').addEventListener('change',()=>{draft.toolRequirements=draft.toolRequirements.filter(t=>t!=='COMMAND');if($('role-command').checked)draft.toolRequirements.push('COMMAND');invalidate();});
  $('role-new').addEventListener('click',()=>{draft=fresh();invalidate();renderDraft();feedback('正在编辑一个空白角色；标识由系统维护。');});
  $('role-add-step').addEventListener('click',()=>{if(draft.steps.length>=8)return;draft.steps.push(newStep());invalidate();renderStepsAndKnowledge();});
  $('role-import').addEventListener('click',()=>{
    try{
    const base=state.methods.find(m=>m.role===$('role-template').value);if(!base){feedback('先选择一个默认模板。','warn');return;}
    const repository=draft.repositoryRef,method=base.method,next=fresh();next.repositoryRef=repository;next.name=names[base.role]||base.role;next.category=base.category;next.goal=method.instruction;next.resultKind=method.resultKind;next.delivery='按'+outputs[method.resultKind]+'交付本次任务的正式结果，保留专业技能要求，说明实际检查、依据及剩余缺口。';
    const refs=[...new Set([...method.protocolSkills,method.primarySkill])].filter(r=>r!=='engineering-artifact-protocol').map(r=>{const a=state.uiOptions.assets.find(a=>a.kind==='skill'&&a.ref===r&&!a.repositoryRef);if(!a)throw new Error('模板技能缺失，请刷新目录。');return reference(a);});
    next.steps=[{key:uid('step'),title:next.name,skills:refs,instruction:method.instruction,whenCondition:'',completion:'满足专业技能及本次任务的交付要求；不完整时报告明确缺口。'}];next.toolRequirements=base.role.includes('verifier')?['MODEL','GIT','COMMAND']:['MODEL','GIT'];draft=next;invalidate();renderDraft();feedback('已导入专业模板。其输入、来源和产出约束仍保留，可继续组合共享技能。');
    }catch(e){feedback(e.message,'error');}
  });
  $('role-form').addEventListener('submit',async event=>{
    event.preventDefault();if(previewBusy||saveBusy||uncertain)return;
    try{const definition=input(),stamp=++epoch,sign=signature(definition);previewBusy=true;preview=null;sync();const result=await request('propose?kind=role',definition);if(stamp!==epoch||signature(input())!==sign)return;preview={definition,sign,definitionDigest:result.definitionDigest,reference:result.reused||null};summary(preview);feedback(preview.reference?'找到相同原版本，可直接选用。':'角色组合已检查，请核对流程和整体交付后保存。');}catch(e){feedback(e.message,'error');}finally{previewBusy=false;sync();}
  });
  $('role-save').addEventListener('click',async()=>{
    if(saveBusy||previewBusy||copyBusy)return;
    try{if(uncertain&&!uncertain.checked)throw new Error('请先刷新记录核对原保存结果。');if(!uncertain&&(!preview||signature(input())!==preview.sign))throw new Error('预览已失效，请重新预览。');const captured=clone(uncertain?uncertain.proposal:preview);saveBusy=true;sync();try{const ref=await request('candidates?kind=role',captured.definition);preview={...captured,reference:ref};uncertain=null;summary(preview);feedback('角色已保存。可以选用于任务准备，尚未执行任务。');try{await refresh();}catch(_){feedback('角色已保存，原引用已保留；记录刷新失败，可稍后再刷新。','warn');}}catch(e){if(e.uncertain)uncertain={proposal:captured,checked:false};throw e;}}catch(e){feedback(e.message,'error');}finally{saveBusy=false;sync();}
  });
  $('role-refresh').addEventListener('click',async()=>{if(saveBusy||copyBusy)return;try{await refresh();}catch(e){feedback(e.message,'error');}});
  if(document.querySelector('meta[name="configuration-owner"]').content)refresh().catch(e=>feedback(e.message,'error'));else sync();
})();
