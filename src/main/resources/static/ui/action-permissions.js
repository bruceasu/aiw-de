'use strict';
(() => {
  const $=id=>document.getElementById(id),form=$('issue-action-permission');if(!form)return;
  const scopes={ALL:'当前账号全部适用目标',PLAN:'指定计划',TASK:'指定任务',WORKER:'指定普通 Worker（同时确认归属）'};
  const states={ENABLED:'有效',EXHAUSTED:'次数已用尽',EXPIRED:'已到期',REVOKED:'已撤销',REPLACED:'已被新授权期替代',ROLE_CHANGED:'当前角色已变化'};
  let data=null,ready=false,reading=false,busy=false,pending=null,confirmation=null,recent=null,sequence=0;
  const fields=$('permission-fields'),agent=()=>form.elements.namedItem('agentId').value;
  const selectedActions=()=>[...document.querySelectorAll('[data-permission-action]:checked')].map(input=>data?.catalogue.find(a=>a.action===input.value)).filter(Boolean);
  const commonScopes=()=>{const selected=selectedActions();return selected.length?selected.map(a=>a.scopes).reduce((all,scopes)=>all.filter(scope=>scopes.includes(scope)),['ALL','PLAN','TASK','WORKER']):[];};
  const safe=value=>Number.isSafeInteger(value)&&value>=0;
  const node=(tag,text,className)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(className)n.className=className;return n;};
  const key=()=>data?.ownerId?'aiw.action-permission.pending.'+data.ownerId:null;
  const fingerprint=items=>JSON.stringify(items.map(({requestId,expectedPriorId,...value})=>value).sort((a,b)=>a.action.localeCompare(b.action)));
  function message(text,tone='info'){$('permission-feedback').textContent=text;$('permission-feedback').dataset.tone=tone;$('permission-feedback').hidden=!text;}
  function persist(){try{if(key()){if(pending)sessionStorage.setItem(key(),JSON.stringify(pending));else sessionStorage.removeItem(key());}}catch(_){/* In-memory originals remain authoritative for this open page. */}}
  function restore(){try{const raw=key()&&sessionStorage.getItem(key());if(raw){const saved=JSON.parse(raw),validBatch=saved.kind!=='batch'||Array.isArray(saved.body?.permissions)&&saved.body.permissions.length>0&&saved.body.permissions.length<=32;if(saved.ownerId===data.ownerId&&validBatch&&['issue','batch','revoke'].includes(saved.kind)){pending=saved;pending.checked=false;}}}catch(_){message('无法读取本页保留的原操作，请先核对现有授权记录。','warn');}}
  function canWrite(){return ready&&!reading&&!busy&&!pending;}
  function sync(){
    fields.disabled=!canWrite()||!data?.ownerId;
    $('permission-refresh').disabled=busy||reading;
    const selected=selectedActions();
    $('permission-submit').disabled=!canWrite()||!selected.length||selected.some(a=>!a.allowed)||!commonScopes().includes($('permission-scope-kind').value);
    $('permission-new-period').disabled=!canWrite();
    $('permission-clear-selection').disabled=!canWrite()||!selected.length;
    $('permission-pending').hidden=!pending;
    if(pending){const subject=pending.kind==='batch'?`创建 ${pending.body.permissions.length} 项授权批次`:pending.kind==='issue'?'创建授权期':'撤销原授权';$('permission-pending-note').textContent=subject+'的结果尚未确认。保留原标识与范围，请先刷新核对；未找到记录不代表失败。'+(pending.checked?'已读取记录，可明确重试同一个原操作。':'');}
    $('permission-retry').textContent=pending?.kind==='batch'?'重试同一批授权':'重试同一个原请求';
    $('permission-retry').disabled=!pending?.checked||!ready||busy||reading;
    $('permission-confirm-submit').disabled=!ready||busy||reading||Boolean(pending);
    document.querySelectorAll('[data-revoke-permission]').forEach(button=>{button.disabled=!canWrite();});
  }
  async function call(path,body={},write=false){
    let response,result;
    try{response=await fetch('/api/r1/authority'+path,{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-Token':document.querySelector('meta[name="csrf-token"]').content},body:JSON.stringify(body)});}
    catch(_){const error=new Error(write?'连接中断，操作结果未知。请先刷新核对原操作。':'授权状态未能读取，请稍后刷新。');error.uncertain=write;throw error;}
    try{result=await response.json();}catch(_){const error=new Error(response.status===401?'请登录后重新读取授权。':response.status===403?'此入口未获准，请核对登录身份，并确认平台已更新重启。':'响应无法读取，请先刷新核对。');error.uncertain=write&&(response.ok||response.status>=500);throw error;}
    if(!response.ok){const error=new Error(result.message||'授权操作未确认，请先刷新状态。');error.uncertain=write&&response.status>=500;throw error;}return result;
  }
  function selectedFields(){
    const maximum=Number($('permission-maximum').value),expiry=new Date($('permission-expiry').value).getTime(),selected=selectedActions(),kind=$('permission-scope-kind').value;
    if(!data?.ownerId||!agent()||!selected.length||selected.some(a=>!a.allowed))throw new Error('请选择 Agent 和至少一项当前身份可以授予的操作。');
    if(!Number.isSafeInteger(maximum)||maximum<1||maximum>2147483647||!Number.isSafeInteger(expiry)||expiry<=Date.now())throw new Error('请设置未来的到期时间和有效的正整数累计次数。');
    if(!commonScopes().includes(kind))throw new Error('所选操作不支持当前范围，请重新核对操作和范围。');
    const id=kind==='ALL'?null:$('permission-scope-id').value;
    if(kind!=='ALL'&&!choices(kind).some(item=>item.id===id))throw new Error('请选择当前可用的范围；原范围不可用时不会改为全部目标。');
    const boundOwner=form.elements.namedItem('ownerId').value;
    if(boundOwner&&boundOwner!==data.ownerId)throw new Error('登录身份已变化，请重新打开页面后核对Agent。');
    return {ownerId:data.ownerId,agentId:agent(),scopeKind:kind,scopeId:id,maximumUses:maximum,expiresAt:expiry};
  }
  function choices(kind){return kind==='PLAN'?data?.plans||[]:kind==='TASK'?data?.tasks||[]:kind==='WORKER'?(data?.workers||[]).map(w=>({id:w.id,title:w.id+' · '+w.runtime_type+(w.owner_id?'':' · 尚未确认归属')})):[];}
  function renderActionGroups(keep=false){
    const host=$('permission-action-groups'),selected=new Set(keep?selectedActions().map(a=>a.action):[]);host.replaceChildren();
    for(const group of [...new Set(data.catalogue.map(a=>a.group))]){
      const section=node('fieldset',undefined,'permission-action-group');section.append(node('legend',group));
      for(const a of data.catalogue.filter(item=>item.group===group)){
        const label=node('label',undefined,'permission-action-option'+(a.allowed?'':' is-disabled')),input=document.createElement('input');
        input.type='checkbox';input.value=a.action;input.dataset.permissionAction='true';input.disabled=!a.allowed;input.checked=a.allowed&&selected.has(a.action);
        input.addEventListener('change',()=>{populateScope(true);updateSelection();sync();});
        label.append(input,node('span',a.label),node('code',a.action));section.append(label);
      }
      host.append(section);
    }
    populateScope(keep);updateSelection();
  }
  function updateSelection(){const selected=selectedActions();$('permission-selection-count').textContent=selected.length?`已选 ${selected.length} 项；每项会建立独立授权`:'尚未选择操作';}
  function populateScope(keep=false){
    const previous=keep?$('permission-scope-kind').value:'',available=commonScopes();$('permission-scope-kind').replaceChildren(new Option(selectedActions().length?'请选择适用范围':'请先选择操作',''));
    for(const kind of ['ALL','PLAN','TASK','WORKER'].filter(kind=>available.includes(kind)))$('permission-scope-kind').add(new Option(scopes[kind],kind));
    if(available.includes(previous))$('permission-scope-kind').value=previous;
    populateScopeId(keep);
  }
  function populateScopeId(keep=false){
    const kind=$('permission-scope-kind').value,selected=keep?$('permission-scope-id').value:'';
    const actions=selectedActions();$('permission-scope-field').hidden=!kind||kind==='ALL';$('permission-scope-id').required=Boolean(kind&&kind!=='ALL');$('permission-scope-label').textContent=scopes[kind]||'选择具体对象';
    $('permission-scope-id').replaceChildren(new Option('请选择范围',''));
    for(const item of choices(kind))$('permission-scope-id').add(new Option(item.title===item.id?item.id:item.title+' · '+item.id,item.id));
    if(selected&&!choices(kind).some(item=>item.id===selected))$('permission-scope-id').add(new Option('原选择（当前不可用）',selected));
    $('permission-scope-id').value=selected;
    $('permission-scope-note').textContent=!actions.length?'先选择操作，再选择这些操作共同支持的范围。':!commonScopes().length?'所选操作没有共同适用范围，请减少选择后再授权。':actions.some(a=>a.action==='A13')?'包含平台全局操作；授权时会核对全局对象归属，不能操作属于其他账号的对象。':kind==='WORKER'?'将操作限定到所选普通 Worker；系统会核对归属，不能接管其他账号的 Worker。':kind==='TASK'?'只作用于所选任务，不会扩展到同计划的其他任务。':kind==='ALL'?'覆盖每项操作各自适用的全部目标，包括以后新增的目标。每项操作的次数单独计算。':kind==='PLAN'?'只作用于所选计划。每项操作的次数单独计算。':'所选操作的适用范围不一致，请重新选择。';
  }
  function scopeLabel(p){
    if(p.action==='A13')return '平台全局运行并发';
    if(p.scopeKind==='ALL')return ['A14','A15'].includes(p.action)?'当前账号已确认归属的全部普通 Worker':'当前账号现有及后续全部适用目标';
    const item=choices(p.scopeKind).find(item=>item.id===p.scopeId);return (scopes[p.scopeKind]||'指定范围')+'：'+(item?.title||'原指定对象（当前不可选）');
  }
  function renderRecords(){
    if(!data)return;
    for(const [id,historical] of [['permission-records',false],['permission-history',true]]){
      const host=$(id);host.replaceChildren();const rows=data.permissions.filter(p=>(p.state!=='ENABLED')===historical);
      for(const group of [...new Set(rows.map(p=>data.catalogue.find(a=>a.action===p.action)?.group||'其它动作'))]){
        host.append(node('h3',group,'permission-list-label'));const cards=node('div',undefined,'permission-group-records');
        for(const p of rows.filter(p=>(data.catalogue.find(a=>a.action===p.action)?.group||'其它动作')===group)){
          const card=node('article',undefined,'permission-record'),a=data.catalogue.find(a=>a.action===p.action),badge=node('span',states[p.effectiveState]||'状态待核对','pill '+(p.effectiveState==='ENABLED'?'ok':'neutral'));
          card.append(badge,node('h4',a?.label||'原动作授权'),node('p','Agent：'+p.agentId),node('p',p.defaultPermission?'当前账号下的全部计划和任务 · 随 Agent 启用':scopeLabel(p)));
          if(!p.defaultPermission){const metrics=node('div',undefined,'permission-metrics');for(const [value,label] of [[safe(p.usedUses)&&safe(p.maximumUses)?p.usedUses+' / '+p.maximumUses:'待核对','已受理 / 总次数'],[safe(p.remaining)?String(p.remaining):'待核对','剩余次数']]){const item=node('div');item.append(node('strong',value),node('span',label));metrics.append(item);}card.append(metrics,node('p','到期：'+new Date(p.expiresAt).toLocaleString('zh-CN',{hour12:false})));}
          if(p.state==='ENABLED'&&!p.defaultPermission){const revoke=node('button','撤销这条授权','btn ghost');revoke.type='button';revoke.dataset.revokePermission=p.id;revoke.addEventListener('click',()=>{if(canWrite())ask({kind:'revoke',id:p.id,ownerId:data.ownerId},'撤销动作授权','停止这条授权的新操作和后续准入，保留历史次数和原回执；在途按现有停止规则处理。',(a?.label||p.action)+' · '+p.agentId+'\n'+scopeLabel(p));});card.append(revoke);}
          const technical=node('details');technical.append(node('summary','查看授权引用'),node('p',p.id));card.append(technical);cards.append(card);
        }host.append(cards);
      }
      if(!rows.length)host.append(node('p',historical?'尚无历史授权期。':'尚无当前业务动作授权。先在上方选择一种动作。','authority-empty'));
    }
  }
  function samePermission(p,body){return ['ownerId','agentId','action','scopeKind','scopeId','maximumUses','expiresAt'].every(field=>p[field]===body[field]);}
  async function load(){
    const token=++sequence;reading=true;ready=false;sync();
    try{const current=await call('/action-permissions/state');if(token!==sequence)return;data=current;
      restore();renderActionGroups(true);
      if(pending){pending.checked=true;
        if(pending.kind==='batch'){
          const items=pending.body.permissions,matched=items.filter(item=>{const found=data.permissions.find(p=>p.creationRequestId===item.requestId);return found&&samePermission(found,item);});
          if(matched.length===items.length){recent=fingerprint(items);pending=null;persist();message(`已查到整批 ${items.length} 项授权记录；请核对各项当前效力和剩余次数。`);}
          else message(matched.length?`已查到 ${matched.length}/${items.length} 项。整批重试会复用已登记记录，只补齐原批次中未登记的项。`:'暂未查到原批次记录；请确认重试同一批请求，不要重新创建新请求。','warn');
        }else{
          const found=pending.kind==='issue'?data.permissions.find(p=>p.creationRequestId===pending.body.requestId):data.permissions.find(p=>p.id===pending.id&&p.state!=='ENABLED');
          if(found&&(pending.kind!=='issue'||samePermission(found,pending.body))){recent=pending.kind==='issue'?JSON.stringify(Object.fromEntries(Object.entries(pending.body).filter(([k])=>!['requestId','expectedPriorId'].includes(k)))):recent;pending=null;persist();message('已查到原操作的正式记录，请以当前授权效力与剩余次数为准。');}
        }
      }
      ready=true;renderRecords();persist();
    }catch(error){ready=false;message(error.message,'error');}
    finally{if(token===sequence){reading=false;sync();}}
  }
  function ask(op,title,note,target){if(!canWrite())return;confirmation=op;$('permission-confirm-title').textContent=title;$('permission-confirm-note').textContent=note;$('permission-confirm-target').textContent=target;$('permission-confirm').showModal();}
  async function perform(op){
    if(busy||reading||!ready)return;busy=true;pending={...op,checked:false};persist();sync();
    try{const path=op.kind==='batch'?'/action-permissions/batch':op.kind==='issue'?'/action-permissions':'/action-permissions/'+encodeURIComponent(op.id)+'/revoke';
      const result=await call(path,op.kind==='batch'||op.kind==='issue'?op.body:{},true);
      if(op.kind==='batch'&&(!Array.isArray(result)||result.length!==op.body.permissions.length||result.some((permission,index)=>!samePermission(permission,op.body.permissions[index])))){const error=new Error('服务器已响应，但批次结果与原请求不一致；请刷新核对原批次。');error.uncertain=true;throw error;}
      pending=null;persist();if(op.kind==='batch'){recent=fingerprint(op.body.permissions);message(`已创建 ${op.body.permissions.length} 项独立授权；请核对各项当前效力和剩余次数。`);}
      else if(op.kind==='issue'){recent=JSON.stringify(Object.fromEntries(Object.entries(op.body).filter(([k])=>!['requestId','expectedPriorId'].includes(k))));if(samePermission(result,op.body))message('原授权期已登记，请核对当前效力和剩余次数；不会因重试重新计数。');}
      else message('撤销操作已返回，正在读取当前授权效力。');
      await load();if(!ready)message('操作已返回成功，但列表刷新失败。请先刷新核对，不要重建授权期。','warn');
    }catch(error){if(!error.uncertain){pending=null;persist();ready=false;}message(error.message,'error');}
    finally{busy=false;renderRecords();sync();}
  }
  form.addEventListener('submit',event=>{event.preventDefault();if(!canWrite()||!form.reportValidity())return;
    try{const values=selectedFields(),selected=selectedActions();
      const permissions=selected.map(action=>{const prior=data.permissions.find(p=>p.agentId===values.agentId&&p.action===action.action&&p.state==='ENABLED');return {requestId:crypto.randomUUID(),...values,action:action.action,expectedPriorId:prior?.id||null};});
      const body={permissions},batchFingerprint=fingerprint(permissions);if(recent===batchFingerprint){message('这批授权已经登记。若确实需要新的授权期，请明确点击“重新创建相同授权”后再核对。','warn');return;}
      const summary=permissions.map((permission,index)=>`${index+1}. ${data.catalogue.find(a=>a.action===permission.action)?.label||permission.action} · ${scopeLabel(permission)} · 最多 ${permission.maximumUses} 次`).join('\n');
      ask({kind:'batch',body,ownerId:data.ownerId},`确认创建 ${permissions.length} 项独立授权`,`每项操作会各自建立授权、次数上限和审计记录。若某项与现有授权冲突，整批都会失败；同动作旧授权会按原规则替代，其他动作不受影响。`,`${agent()}\n${summary}\n到期：${new Date(values.expiresAt).toLocaleString('zh-CN',{hour12:false})}`);
    }catch(error){message(error.message,'error');}
  });
  $('permission-confirm-dismiss').addEventListener('click',()=>$('permission-confirm').close());
  $('permission-confirm').addEventListener('close',()=>{confirmation=null;});
  $('permission-confirm-submit').addEventListener('click',()=>{const op=confirmation;$('permission-confirm').close();confirmation=null;if(op&&canWrite())perform(op);});
  $('permission-clear-selection').addEventListener('click',()=>{document.querySelectorAll('[data-permission-action]').forEach(input=>input.checked=false);populateScope(false);updateSelection();sync();});
  $('permission-scope-kind').addEventListener('change',()=>{populateScopeId();sync();});
  $('permission-refresh').addEventListener('click',load);$('permission-retry').addEventListener('click',()=>{if(pending?.checked&&ready)perform(pending);});
  $('permission-new-period').addEventListener('click',()=>{if(!canWrite())return;recent=null;message('将创建一个新的授权期。请重新核对动作、范围、次数和到期时间；原记录仍保留。','warn');});
  const expiry=new Date(Date.now()+7*86400000);expiry.setMinutes(expiry.getMinutes()-expiry.getTimezoneOffset());$('permission-expiry').value=expiry.toISOString().slice(0,16);
  load();
})();
