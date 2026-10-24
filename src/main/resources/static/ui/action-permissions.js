'use strict';
(() => {
  const $=id=>document.getElementById(id),form=$('issue-action-permission');if(!form)return;
  const scopes={ALL:'当前账号全部适用目标',PLAN:'指定计划',TASK:'指定任务',WORKER:'指定普通 Worker（同时确认归属）'};
  const states={ENABLED:'有效',EXHAUSTED:'次数已用尽',EXPIRED:'已到期',REVOKED:'已撤销',REPLACED:'已被新授权期替代',ROLE_CHANGED:'当前角色已变化'};
  let data=null,ready=false,reading=false,busy=false,pending=null,confirmation=null,recent=null,sequence=0,initialized=false;
  const fields=$('permission-fields'),worker=()=>form.elements.namedItem('workerId').value;
  const action=()=>data?.catalogue.find(a=>a.action===$('permission-action').value);
  const safe=value=>Number.isSafeInteger(value)&&value>=0;
  const node=(tag,text,className)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(className)n.className=className;return n;};
  const key=()=>data?.ownerId?'aiw.action-permission.pending.'+data.ownerId:null;
  function message(text,tone='info'){$('permission-feedback').textContent=text;$('permission-feedback').dataset.tone=tone;$('permission-feedback').hidden=!text;}
  function persist(){try{if(key()){if(pending)sessionStorage.setItem(key(),JSON.stringify(pending));else sessionStorage.removeItem(key());}}catch(_){/* In-memory originals remain authoritative for this open page. */}}
  function restore(){try{const raw=key()&&sessionStorage.getItem(key());if(raw){const saved=JSON.parse(raw);if(saved.ownerId===data.ownerId&&['issue','revoke'].includes(saved.kind)){pending=saved;pending.checked=false;}}}catch(_){message('无法读取本页保留的原操作，请先核对现有授权记录。','warn');}}
  function canWrite(){return ready&&!reading&&!busy&&!pending;}
  function sync(){
    fields.disabled=!canWrite()||!data?.ownerId;
    $('permission-refresh').disabled=busy||reading;
    $('permission-submit').disabled=!canWrite()||!action()?.allowed;
    $('permission-new-period').disabled=!canWrite();
    $('permission-pending').hidden=!pending;
    if(pending)$('permission-pending-note').textContent=(pending.kind==='issue'?'创建授权期':'撤销原授权')+'的结果尚未确认。保留原标识与范围，请先刷新核对；未找到记录不代表失败。'+(pending.checked?'已读取记录，可明确重试同一个原操作。':'');
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
    const maximum=Number($('permission-maximum').value),expiry=new Date($('permission-expiry').value).getTime(),a=action(),kind=$('permission-scope-kind').value;
    if(!data?.ownerId||!worker()||!a?.allowed)throw new Error('请明确选择秘书、功能分组及当前角色可授予的动作。');
    if(!Number.isSafeInteger(maximum)||maximum<1||maximum>2147483647||!Number.isSafeInteger(expiry)||expiry<=Date.now())throw new Error('请设置未来的到期时间和有效的正整数累计次数。');
    if(!a.scopes.includes(kind))throw new Error('原范围已不适用于此动作，请重新核对选择。');
    const id=kind==='ALL'?null:$('permission-scope-id').value;
    if(kind!=='ALL'&&!choices(kind).some(item=>item.id===id))throw new Error('请选择当前可用的范围；原范围不可用时不会改为全部目标。');
    const boundOwner=form.elements.namedItem('ownerId').value;
    if(boundOwner&&boundOwner!==data.ownerId)throw new Error('登录身份已变化，请重新打开页面后核对秘书。');
    return {ownerId:data.ownerId,workerId:worker(),action:a.action,scopeKind:kind,scopeId:id,maximumUses:maximum,expiresAt:expiry};
  }
  function choices(kind){return kind==='PLAN'?data?.plans||[]:kind==='TASK'?data?.tasks||[]:kind==='WORKER'?(data?.workers||[]).map(w=>({id:w.id,title:w.id+' · '+w.runtime_type+(w.owner_id?'':' · 尚未确认归属')})):[];}
  function populateActions(keep=false){
    const selected=keep?$('permission-action').value:'';$('permission-action').replaceChildren(new Option('请选择一个动作',''));
    for(const a of data.catalogue.filter(a=>a.group===$('permission-group').value)){const option=new Option(a.label+(a.allowed?'':'（当前角色不可授予）'),a.action);option.disabled=!a.allowed;$('permission-action').add(option);}
    $('permission-action').value=selected;populateScope(keep);
  }
  function populateScope(keep=false){
    const previous=keep?$('permission-scope-kind').value:'ALL',a=action();$('permission-scope-kind').replaceChildren();
    for(const kind of ['ALL','PLAN','TASK','WORKER'].filter(kind=>!a?kind==='ALL':a.scopes.includes(kind)))$('permission-scope-kind').add(new Option(scopes[kind],kind));
    if([...$('permission-scope-kind').options].some(o=>o.value===previous))$('permission-scope-kind').value=previous;
    else if(keep&&previous){$('permission-scope-kind').add(new Option('原范围（当前不可用）',previous));$('permission-scope-kind').value=previous;}
    populateScopeId(keep);
  }
  function populateScopeId(keep=false){
    const kind=$('permission-scope-kind').value,selected=keep?$('permission-scope-id').value:'';
    $('permission-scope-field').hidden=kind==='ALL';$('permission-scope-id').required=kind!=='ALL';$('permission-scope-label').textContent=scopes[kind]||'原范围';
    $('permission-scope-id').replaceChildren(new Option('请选择范围',''));
    for(const item of choices(kind))$('permission-scope-id').add(new Option(item.title===item.id?item.id:item.title+' · '+item.id,item.id));
    if(selected&&!choices(kind).some(item=>item.id===selected))$('permission-scope-id').add(new Option('原选择（当前不可用）',selected));
    $('permission-scope-id').value=selected;
    $('permission-scope-note').textContent=action()?.action==='A13'?'此动作影响平台全局运行并发；确认授权时会核对并确认其归属，不能操作已属于其它账号的全局对象。':kind==='WORKER'?'明确选择这个普通 Worker 并确认归属；不能接管其它账号的 Worker。':kind==='TASK'?'仅列出最近 100 个可选任务。该动作必须能核对精确任务来源，不会扩展到同计划的其它任务。':['A14','A15'].includes(action()?.action)?'不指定时仅适用于已经确认归属的普通 Worker。首次使用可选择一个普通 Worker，在授权确认时明确其归属。':'不指定范围时，覆盖当前账号现有及以后新增的全部适用事务/任务；多个目标共用累计次数。';
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
          card.append(badge,node('h4',a?.label||'原动作授权'),node('p','秘书：'+p.workerId),node('p',scopeLabel(p)));
          const metrics=node('div',undefined,'permission-metrics');for(const [value,label] of [[safe(p.usedUses)&&safe(p.maximumUses)?p.usedUses+' / '+p.maximumUses:'待核对','已受理 / 总次数'],[safe(p.remaining)?String(p.remaining):'待核对','剩余次数']]){const item=node('div');item.append(node('strong',value),node('span',label));metrics.append(item);}card.append(metrics,node('p','到期：'+new Date(p.expiresAt).toLocaleString('zh-CN',{hour12:false})));
          if(p.state==='ENABLED'){const revoke=node('button','撤销这条授权','btn ghost');revoke.type='button';revoke.dataset.revokePermission=p.id;revoke.addEventListener('click',()=>{if(canWrite())ask({kind:'revoke',id:p.id,ownerId:data.ownerId},'撤销动作授权','停止这条授权的新操作和后续准入，保留历史次数和原回执；在途按现有停止规则处理。',(a?.label||p.action)+' · '+p.workerId+'\n'+scopeLabel(p));});card.append(revoke);}
          const technical=node('details');technical.append(node('summary','查看授权引用'),node('p',p.id));card.append(technical);cards.append(card);
        }host.append(cards);
      }
      if(!rows.length)host.append(node('p',historical?'尚无历史授权期。':'尚无当前业务动作授权。先在上方选择一种动作。','authority-empty'));
    }
  }
  function samePermission(p,body){return ['ownerId','workerId','action','scopeKind','scopeId','maximumUses','expiresAt'].every(field=>p[field]===body[field]);}
  async function load(){
    const token=++sequence;reading=true;ready=false;sync();
    try{const current=await call('/action-permissions/state');if(token!==sequence)return;data=current;
      if(!initialized){$('permission-group').replaceChildren(new Option('请选择功能分组',''));for(const group of [...new Set(data.catalogue.map(a=>a.group))])$('permission-group').add(new Option(group,group));initialized=true;restore();}
      else populateActions(true);
      if(pending){pending.checked=true;const found=pending.kind==='issue'?data.permissions.find(p=>p.creationRequestId===pending.body.requestId):data.permissions.find(p=>p.id===pending.id&&p.state!=='ENABLED');
        if(found&&(pending.kind!=='issue'||samePermission(found,pending.body))){recent=pending.kind==='issue'?JSON.stringify(Object.fromEntries(Object.entries(pending.body).filter(([k])=>!['requestId','expectedPriorId'].includes(k)))):recent;pending=null;persist();message('已查到原操作的正式记录，请以当前授权效力与剩余次数为准。');}
      }
      ready=true;renderRecords();persist();
    }catch(error){ready=false;message(error.message,'error');}
    finally{if(token===sequence){reading=false;sync();}}
  }
  function ask(op,title,note,target){if(!canWrite())return;confirmation=op;$('permission-confirm-title').textContent=title;$('permission-confirm-note').textContent=note;$('permission-confirm-target').textContent=target;$('permission-confirm').showModal();}
  async function perform(op){
    if(busy||reading||!ready)return;busy=true;pending={...op,checked:false};persist();sync();
    try{const result=await call(op.kind==='issue'?'/action-permissions':'/action-permissions/'+encodeURIComponent(op.id)+'/revoke',op.kind==='issue'?op.body:{},true);
      pending=null;persist();if(op.kind==='issue'){recent=JSON.stringify(Object.fromEntries(Object.entries(op.body).filter(([k])=>!['requestId','expectedPriorId'].includes(k))));if(samePermission(result,op.body))message('原授权期已登记，请核对当前效力和剩余次数；不会因重试重新计数。');}
      else message('撤销操作已返回，正在读取当前授权效力。');
      await load();if(!ready)message('操作已返回成功，但列表刷新失败。请先刷新核对，不要重建授权期。','warn');
    }catch(error){if(!error.uncertain){pending=null;persist();ready=false;}message(error.message,'error');}
    finally{busy=false;renderRecords();sync();}
  }
  form.addEventListener('submit',event=>{event.preventDefault();if(!canWrite()||!form.reportValidity())return;
    try{const values=selectedFields(),fingerprint=JSON.stringify(values);if(recent===fingerprint){message('此内容已经登记。若需要新的授权期，请明确点击“另建一个授权期”后再核对。','warn');return;}
      const prior=data.permissions.find(p=>p.workerId===values.workerId&&p.action===values.action&&p.state==='ENABLED');
      const body={requestId:crypto.randomUUID(),...values,expectedPriorId:prior?.id||null};
      ask({kind:'issue',body,ownerId:data.ownerId},'确认授予一种动作','仅授予选中的动作，所有适用目标共用次数。创建新授权期将替代这位秘书同动作的旧授权；新期从零计数，历史保留，旧在途按现有撤销规则处理。'+(values.scopeKind==='WORKER'?'同时为本账号确认所选普通 Worker 的归属。':''),action().label+' · '+worker()+'\n'+scopeLabel(body)+'\n累计 '+values.maximumUses+' 次 · 至 '+new Date(values.expiresAt).toLocaleString('zh-CN',{hour12:false}));
    }catch(error){message(error.message,'error');}
  });
  $('permission-confirm-dismiss').addEventListener('click',()=>$('permission-confirm').close());
  $('permission-confirm').addEventListener('close',()=>{confirmation=null;});
  $('permission-confirm-submit').addEventListener('click',()=>{const op=confirmation;$('permission-confirm').close();confirmation=null;if(op&&canWrite())perform(op);});
  $('permission-group').addEventListener('change',()=>{populateActions();sync();});$('permission-action').addEventListener('change',()=>{populateScope();sync();});$('permission-scope-kind').addEventListener('change',()=>populateScopeId());
  $('permission-refresh').addEventListener('click',load);$('permission-retry').addEventListener('click',()=>{if(pending?.checked&&ready)perform(pending);});
  $('permission-new-period').addEventListener('click',()=>{if(!canWrite())return;recent=null;message('将创建一个新的授权期。请重新核对动作、范围、次数和到期时间；原记录仍保留。','warn');});
  const expiry=new Date(Date.now()+7*86400000);expiry.setMinutes(expiry.getMinutes()-expiry.getTimezoneOffset());$('permission-expiry').value=expiry.toISOString().slice(0,16);
  load();
})();
