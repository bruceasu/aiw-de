'use strict';
(() => {
  const $=id=>document.getElementById(id),owner=document.querySelector('meta[name="configuration-owner"]').content;
  const csrf=document.querySelector('meta[name="csrf-token"]').content,initial=new URLSearchParams(location.search);
  const labels={RUNNING:'执行中',CLAIMED:'已领取',COMPLETED:'已完成',SUCCEEDED:'成功',FAILED:'失败',EXPIRED:'已过期',CANCELLED:'已撤回',WAITING_INPUT:'等待人工',UNKNOWN:'结果未知',STARTED:'进行中',REJECTED:'未获准',QUEUED:'等待领取',RESERVED:'已领取，等待模型结果确认',DELIVERED:'已递交模型',FINALIZING:'正在收尾'};
  const label=value=>labels[value]||value||'未记录';
  const node=(tag,text,cls)=>{const n=document.createElement(tag);if(text!=null)n.textContent=text;if(cls)n.className=cls;return n;};
  let state=null,epoch=0,loading=false,busy=false,pending=null,task=initial.get('task')||null,attempt=null;
  const compare=new Set(),reasons=new Map();
  function feedback(text,tone='info'){const n=$('wb-feedback');n.textContent=text;n.dataset.tone=tone;n.hidden=!text;}
  function entry(){return state?.attempts.find(e=>e.attemptId===state.attemptId);}
  function session(){return state?.sessions.find(s=>s.attempt_id===state.attemptId);}
  function canInput(){const e=entry(),s=session(),b=state?.basis;return e&&s&&s.phase==='RUNNING'&&e.taskState==='RUNNING'&&e.attemptState==='RUNNING'&&b.live&&!b.stopRequested&&b.instructionRevision===b.currentInstructionRevision;}
  function sync(){
    const locked=loading||busy||Boolean(pending);$('wb-task').disabled=locked||!state?.attempts.length;$('wb-attempt').disabled=locked||!entry();
    $('wb-refresh').disabled=loading||busy;$('wb-input-fields').disabled=locked||!canInput();$('wb-branch-fields').disabled=locked||!entry();
    $('wb-branch-result').disabled=!entry()?.resultRevision;if(!entry()?.resultRevision)$('wb-branch-result').checked=false;
    $('wb-retry').disabled=loading||busy||!pending?.checked;$('wb-pending').hidden=!pending;
    for(const b of document.querySelectorAll('[data-wb-action]'))b.disabled=locked;
  }
  async function request(path,body,mutation=false){
    let response,data;
    try{response=await fetch('/api/r1/authority/execution-configurations/'+path,{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-CSRF-Token':csrf},body:JSON.stringify(body)});if(response.redirected||!response.headers.get('content-type')?.includes('application/json'))throw new Error();data=await response.json();}
    catch(_){const e=new Error('连接或响应未确认，请先刷新核对原记录。');e.uncertain=mutation;throw e;}
    if(!response.ok){const e=new Error(response.status===401||response.status===403?'请重新登录并确认当前操作身份。':response.status===409?'任务或原请求状态已变化，请刷新核对；已领取的输入不能撤回。':'暂时无法完成请求（'+response.status+'）。请检查填写内容与当前任务。');e.uncertain=mutation&&response.status>=500;throw e;}
    return data;
  }
  function command(action,extra={}){
    if(!entry()||!state.basis)throw new Error('请先选择一个有记录的执行尝试。');
    return {requestId:'work-'+crypto.randomUUID(),action,taskId:state.taskId,attemptId:state.attemptId,instructionRevision:state.basis.instructionRevision,
      mode:null,text:null,messageId:null,parentId:null,title:null,resultRevision:null,...extra};
  }
  async function submit(body){
    if(busy||pending||loading)return;busy=true;sync();
    try{await request('candidates?kind=workbench',body,true);feedback('请求已记录，请按收据确认实际处理状态。');await refresh(true);}
    catch(e){if(e.uncertain){pending={body,checked:false};$('wb-pending-text').textContent='提交结果未知。先刷新核对原收据，再按需要重试同一个请求；不会另换标识重复提交。';}feedback(e.message,'error');}
    finally{busy=false;sync();}
  }
  async function reconcile(){
    if(!pending)return;const captured=pending,result=await request('state?kind=workbench-receipt',{requestId:captured.body.requestId});
    if(pending!==captured)return;
    if(result.found){pending=null;feedback('已核对原请求有记录，没有重复提交。');}
    else{captured.checked=true;$('wb-pending-text').textContent='暂未找到原收据。可显式重试同一个原请求，服务器将按原标识核对。';}
  }
  function options(select,items,value){select.replaceChildren();for(const [id,text] of items)select.add(new Option(text,id));select.value=value||'';}
  function render(){
    const tasks=new Map();for(const e of state.attempts)if(!tasks.has(e.taskId))tasks.set(e.taskId,e.title);
    options($('wb-task'),state.taskId?[...tasks]:[['','请选择已有记录的任务'],...tasks],state.taskId);
    options($('wb-attempt'),state.attempts.filter(e=>e.taskId===state.taskId).map(e=>[e.attemptId,'第 '+e.attemptNo+' 次 · '+label(e.attemptState)]),state.attemptId);
    const summary=$('wb-summary');summary.replaceChildren();const e=entry(),s=session();
    if(!e){summary.append(node('p',state.notice||'当前账号没有可展示的计量记录。无记录不代表没有执行。','muted'));}
    else{
      const active=state.operations.filter(o=>o.state==='STARTED');let phase=label(e.attemptState);
      if(e.attemptState==='RUNNING')phase=state.basis.stopRequested?'已请求停止，等待确认':s?.phase==='FINALIZING'?'正在收尾':active.some(o=>o.tool_id==='MODEL')?'模型处理中':active.length?'工具处理中':'执行中，等待下一步记录';
      for(const [title,lines] of [[phase,['任务：'+label(e.taskState),'输入版本：'+state.basis.instructionRevision]],['实际模型',[e.model||'未记录',e.runtime||'后端未记录']],[s?'工作台已开启':'沿用原任务控制',[s?'执行中输入：Python API':'本次尝试未开启执行中输入',e.methodName||e.primarySkill||'工作方法未记录']]]){
        const block=node('div');block.append(node('strong',title));for(const line of lines)block.append(node('small',line));summary.append(block);
      }
      const detail=node('details'),facts=node('p',e.taskId+' / '+e.attemptId,'help');detail.append(node('summary','内部引用'),facts);summary.append(detail);
    }
    $('wb-updated').textContent='读取时间：'+new Date(state.observedAt).toLocaleString(window.aiwI18n?.locale || 'en')+' · 阶段是当时记录，未刷新时不能代表当前状态。';
    $('wb-input-availability').textContent=canInput()?'这次尝试已开启输入，可选择处理时机。':s?'本次尝试正在停止、收尾或已结束，不能再添加输入。':'本次尝试没有开启实时输入能力。可使用原任务问答、指令修订与停止流程。';
    renderOperations();renderMessages();renderBranches();sync();
  }
  function renderOperations(){
    const host=$('wb-operations');host.replaceChildren();
    for(const op of state.operations){const card=node('article',null,'workbench-record');card.append(node('h3',op.tool_id+' · '+label(op.state)),node('p',op.resource,'muted'));
      const detail=node('details');detail.append(node('summary','查看记录依据'),node('p','操作：'+op.operation_id),node('p','结果摘要：'+(op.result_digest||'未记录')),node('p','证据位置：'+(op.evidence_ref||'未记录')),node('p','开始：'+new Date(op.started_ms).toLocaleString(window.aiwI18n?.locale || 'en')));card.append(detail);host.append(card);}
    if(!host.childElementCount)host.append(node('p','此尝试暂无工具操作记录。','empty'));
  }
  function renderMessages(){
    const host=$('wb-messages');host.replaceChildren();
    for(const message of state.messages){const batch=state.batches.find(b=>b.batch_id===message.batch_id),e=entry();
      const unknown=message.state==='RESERVED'&&(batch?.operation_state==='UNKNOWN'||!['RUNNING','CLAIMED'].includes(e?.attemptState));
      const card=node('article',null,'workbench-record');card.append(node('h3',(message.mode==='STEER'?'当前补充':'后续排队')+' · '+(unknown?'递交结果待核对':label(message.state))),node('pre',message.body));
      if(message.state==='QUEUED'&&!['RUNNING','CLAIMED'].includes(e?.attemptState))card.append(node('p','原尝试已结束，此项没有执行；不会自动转移或重新派发。','help'));
      if(message.state==='QUEUED'){const cancel=node('button','撤回未领取内容','btn ghost');cancel.type='button';cancel.dataset.wbAction='true';cancel.addEventListener('click',()=>submit(command('CANCEL',{messageId:message.id})));card.append(cancel);}host.append(card);}
    if(!host.childElementCount)host.append(node('p','尚无补充或排队内容。','empty'));
  }
  function renderBranches(){
    const host=$('wb-branches'),parent=$('wb-parent'),selected=parent.value;host.replaceChildren();
    options(parent,[['','独立方案'],...state.branches.map(b=>[b.id,b.title])],state.branches.some(b=>b.id===selected)?selected:'');
    const byId=new Map(state.branches.map(b=>[b.id,b]));
    for(const branch of state.branches){const card=node('article',null,'workbench-record'),pick=node('label',null,'configuration-command'),check=node('input');check.type='checkbox';check.checked=compare.has(branch.id);check.addEventListener('change',()=>{if(check.checked)compare.add(branch.id);else compare.delete(branch.id);});pick.append(check,node('strong',branch.title));card.append(pick);
      if(branch.parent_id)card.append(node('p','继续自：'+(byId.get(branch.parent_id)?.title||'原方案'),'muted'));
      card.append(node('pre',branch.body),node('p','结果版本：'+(branch.result_revision||'未关联代码结果'),'help'));
      const choice=state.choices.find(c=>c.node_id===branch.id);if(choice)card.append(node('p',(choice.decision==='SELECT'?'已记录采用：':'已记录放弃：')+choice.reason,'help'));
      const provenance=node('details');provenance.append(node('summary','来源与选择记录'),node('p','原尝试：'+branch.attempt_id+' · 指令版本 '+branch.instruction_revision),node('p','记录人：'+branch.actor+' · '+new Date(branch.created_ms).toLocaleString(window.aiwI18n?.locale || 'en')));
      const history=node('div');provenance.append(history);let loaded=false;
      provenance.addEventListener('toggle',async()=>{if(!provenance.open||loaded)return;const generation=epoch,sourceTask=state.taskId;history.replaceChildren(node('p','正在读取选择记录…'));try{const result=await request('state?kind=branch-history',{taskId:sourceTask,nodeId:branch.id});if(generation!==epoch||state?.taskId!==sourceTask)return;if(result.ownerId!==owner||result.protocol!=='aiw.worker.workbench.v1'||result.nodeId!==branch.id)throw new Error('记录身份已变化，请重新打开页面。');history.replaceChildren();for(const item of result.history)history.append(node('p',(item.decision==='SELECT'?'采用：':'放弃：')+item.reason+' · '+item.actor+' · '+new Date(item.created_ms).toLocaleString(window.aiwI18n?.locale || 'en')));if(!result.history.length)history.append(node('p','尚无选择记录。'));loaded=true;}catch(e){history.replaceChildren(node('p',e.message));}});card.append(provenance);
      const reason=node('input');reason.maxLength=2000;reason.placeholder='采用或放弃的原因';reason.value=reasons.get(branch.id)||'';reason.addEventListener('input',()=>reasons.set(branch.id,reason.value));
      const field=node('label','选择原因','field');field.append(reason);card.append(field);const actions=node('div',null,'configuration-form-actions');
      for(const [action,text] of [['SELECT','记录采用'],['DISCARD','记录放弃']]){const button=node('button',text,'btn ghost');button.type='button';button.dataset.wbAction='true';button.addEventListener('click',()=>{if(!reason.value.trim()){feedback('请填写选择原因。','warn');return;}submit(command(action,{messageId:branch.id,text:reason.value}));});actions.append(button);}
      const use=node('button','带入补充草稿','btn ghost');use.type='button';use.dataset.wbAction='true';use.addEventListener('click',()=>{$('wb-input-text').value='请在当前任务范围内继续探索方案「'+branch.title+'」：\n'+branch.body;tab('inputs');feedback('方案已带入草稿，请核对并按需要缩短到 4000 字以内，再明确提交。');});actions.append(use);card.append(actions);host.append(card);}
    if(!host.childElementCount)host.append(node('p','尚未记录探索方案。','empty'));
  }
  async function refresh(internal=false){
    if(loading||busy&&!internal)return;const generation=++epoch;loading=true;sync();
    for(const id of ['wb-summary','wb-operations','wb-messages','wb-branches','wb-comparison'])$(id).replaceChildren();$('wb-comparison').hidden=true;
    try{
      if(!owner)throw new Error('请先到个人授权页绑定当前身份。');await reconcile();
      const snapshot=await request('state?kind=workbench',{taskId:task,attemptId:attempt});if(generation!==epoch)return;
      if(snapshot.protocol!=='aiw.worker.workbench.v1'||snapshot.ownerId!==owner||document.querySelector('meta[name="configuration-owner"]').content!==owner)throw new Error('当前身份或数据版本已变化，请重新打开页面。');
      state=snapshot;task=state.taskId||task;attempt=state.attemptId;render();
    }catch(e){if(generation===epoch){state=null;feedback(e.message,'error');}}
    finally{if(generation===epoch){loading=false;sync();}}
  }
  function tab(name){
    for(const key of ['progress','inputs','branches']){const button=$('wb-tab-'+key);button.setAttribute('aria-selected',String(key===name));button.tabIndex=key===name?0:-1;$('wb-panel-'+key).hidden=key!==name;}
  }
  for(const name of ['progress','inputs','branches'])$('wb-tab-'+name).addEventListener('click',()=>tab(name));
  const tabs=[...document.querySelectorAll('[role=tab]')];for(const button of tabs)button.addEventListener('keydown',event=>{let index=tabs.indexOf(button);if(event.key==='ArrowRight')index=(index+1)%tabs.length;else if(event.key==='ArrowLeft')index=(index+tabs.length-1)%tabs.length;else if(event.key==='Home')index=0;else if(event.key==='End')index=tabs.length-1;else return;event.preventDefault();tabs[index].click();tabs[index].focus();});
  $('wb-refresh').addEventListener('click',()=>refresh());
  $('wb-task').addEventListener('change',()=>{task=$('wb-task').value;attempt=null;compare.clear();reasons.clear();$('wb-input-text').value='';$('wb-branch-text').value='';$('wb-branch-title').value='';refresh();});
  $('wb-attempt').addEventListener('change',()=>{attempt=$('wb-attempt').value;refresh();});
  $('wb-input-form').addEventListener('submit',event=>{event.preventDefault();const text=$('wb-input-text').value;if(!canInput()||!text.trim()||text.length>4000){feedback('请核对当前尝试，并将补充内容限制在 4000 字以内。','warn');return;}submit(command('SEND',{mode:$('wb-mode').value,text}));});
  $('wb-branch-form').addEventListener('submit',event=>{event.preventDefault();submit(command('BRANCH',{title:$('wb-branch-title').value,text:$('wb-branch-text').value,parentId:$('wb-parent').value||null,resultRevision:$('wb-branch-result').checked?entry().resultRevision:null}));});
  $('wb-retry').addEventListener('click',async()=>{if(!pending?.checked||busy||loading)return;const captured=pending;busy=true;sync();try{await request('candidates?kind=workbench',captured.body,true);if(pending===captured)pending=null;feedback('原请求已确认。');await refresh(true);}catch(e){feedback(e.message,'error');}finally{busy=false;sync();}});
  $('wb-compare').addEventListener('click',()=>{const chosen=state?.branches.filter(b=>compare.has(b.id))||[];if(chosen.length<2||chosen.length>3){feedback('请选择两至三个方案比较。','warn');return;}const host=$('wb-comparison');host.replaceChildren(node('h2','方案比较'));const grid=node('div',null,'workbench-comparison');for(const branch of chosen){const card=node('article',null,'workbench-record');card.append(node('h3',branch.title),node('pre',branch.body),node('p','结果版本：'+(branch.result_revision||'未关联'),'help'));grid.append(card);}host.append(grid);host.hidden=false;});
  window.addEventListener('pagehide',()=>{epoch++;state=null;for(const id of ['wb-summary','wb-operations','wb-messages','wb-branches','wb-comparison','wb-task','wb-attempt'])$(id).replaceChildren();for(const id of ['wb-input-text','wb-branch-title','wb-branch-text'])$(id).value='';compare.clear();reasons.clear();});
  window.addEventListener('pageshow',event=>{if(event.persisted){loading=false;busy=false;refresh();}});refresh();
})();
