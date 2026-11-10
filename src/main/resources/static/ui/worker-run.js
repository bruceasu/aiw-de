'use strict';
(() => {
  const $=id=>document.getElementById(id), csrf=document.querySelector('meta[name="csrf-token"]').content;
  const owner=document.querySelector('meta[name="configuration-owner"]').content;
  const labels={RUNNING:'执行中',PAUSING:'等待安全边界',PAUSED:'已暂停',YIELDING:'正在保留交接并等待输入',STOPPING:'正在停止',FINALIZING:'正在收尾',SETTLED:'尝试已结束',BROKEN:'通道冲突，需核对'};
  const statuses={ACCEPTED:'已受理',RECEIVED:'Worker 已收到',APPLIED:'已生效',REJECTED:'已被停止请求取代',UNKNOWN:'结果未知'};
  const node=(tag,text,cls)=>{const n=document.createElement(tag);n.textContent=text;if(cls)n.className=cls;return n;};
  let state=null,selection='',cursor=0,events=[],busy=false,pending=null,hidden=false;
  let plans=[],attempts=[],catalogPlan=null,catalogAt=0;
  const native=()=>$('wr-source').value==='native';
  const selected=()=>native()?(attempts.find(a=>a.attempt_id===$('wr-attempt').value)||{}):({taskId:$('wb-task').value,attemptId:$('wb-attempt').value});
  const choice=()=>{const s=selected();return {taskId:s.taskId||s.task_id||'',attemptId:s.attemptId||s.attempt_id||''};};
  const aligned=()=>{const current=choice();return state&&state.taskId===current.taskId&&state.attemptId===current.attemptId;};
  function options(id,items,value,label,empty) {
    const host=$(id),previous=host.value;host.replaceChildren(new Option(empty,''));
    for(const item of items)host.add(new Option(label(item),value(item)));
    if([...host.options].some(o=>o.value===previous))host.value=previous;
  }
  async function catalog() {
    const planId=$('wr-plan').value;
    if(catalogPlan===planId&&Date.now()-catalogAt<10000)return;
    const result=await request('read',{taskId:null,attemptId:null,afterSequence:0,planId:planId||null});
    plans=result.plans;attempts=result.nativeTasks;
    options('wr-plan',plans,p=>p.id,p=>p.title+' · '+p.id,'请选择计划');
    options('wr-attempt',attempts,a=>a.attempt_id,a=>a.task_id+' · '+a.state+' · '+a.attempt_id,'请选择执行尝试');
    catalogPlan=planId;catalogAt=Date.now();
  }
  async function request(action,body) {
    const response=await fetch('/api/r1/authority/execution-runs/'+action,{method:'POST',credentials:'same-origin',
      headers:{'Content-Type':'application/json','X-CSRF-Token':csrf},body:JSON.stringify(body)});
    if(response.redirected||!response.headers.get('content-type')?.includes('application/json'))throw new Error('连接未确认，请重新登录后核对原收据。');
    if(!response.ok){const error=new Error(response.status===403||response.status===401?'请确认当前身份和操作权限。':response.status===409?'当前尝试尚未开启控制通道，或运行状态已经变化。':'暂时无法读取执行通道。');error.status=response.status;throw error;}
    return response.json();
  }
  function controls() {
    const live=aligned()&&!state.stale&&state.attemptState==='RUNNING'&&['TOOL','TURN'].includes(state.capabilities.pauseBoundary)&&!busy&&!pending;
    $('wr-pause').disabled=!live||state.phase!=='RUNNING';$('wr-resume').disabled=!live||state.phase!=='PAUSED';
    const cancellable=aligned()&&state.attemptState==='RUNNING'&&!busy&&!pending;
    $('wr-cancel').disabled=!cancellable||['STOPPING','SETTLED'].includes(state.phase);
    $('wr-retry').hidden=!pending;$('wr-retry').disabled=busy||!pending?.checked;
    for(const id of ['wr-source','wr-plan','wr-attempt'])$(id).disabled=busy||Boolean(pending);
    document.querySelectorAll('[data-native-run]').forEach(n=>n.hidden=!native());
    const plan=plans.find(p=>p.id===$('wr-plan').value);
    $('wr-bind').disabled=busy||Boolean(pending)||!plan||plan.bound||!['DRAFT','CONFIRMED'].includes(plan.state);
    $('wr-binding').textContent=!plan?'选择计划后，由计划创建者在执行前明确启用。':plan.bound?'当前计划范围已固定。修改目标或范围请创建新计划；此绑定不增加工具权限。':'启用后固定当前计划范围。必须在任务开始执行前操作；修改范围请创建新计划。';
    $('wr-questions').href='/ui/questions'+(plan?'?planId='+encodeURIComponent(plan.id):'');
  }
  function render() {
    const host=$('wr-state');host.replaceChildren();
    if(!state){host.append(node('p','所选尝试尚无已打开的运行通道。原有执行记录和停止流程仍可使用。','help'));$('wr-context').replaceChildren();$('wr-receipts').replaceChildren();controls();return;}
    host.append(node('strong',labels[state.phase]||state.phase),node('p',(state.stale?'联系已过期；不能确认实时控制。':'最近联系：'+new Date(state.lastContact).toLocaleTimeString())+' · 指令版本 '+state.instructionRevision,'help'));
    const cap=state.capabilities;host.append(node('p','工具执行：'+(cap.toolExecutor==='HOST'?'Worker':'后端')+' · 暂停粒度：'+(cap.pauseBoundary==='TURN'?'后端回合结束并确认停止活动后':'单个宿主动作结束后')+' · 上下文：Worker 提交投影','help'));
    const context=$('wr-context');context.replaceChildren();
    for(const event of events.filter(e=>e.event_type==='progress-stalled').slice(-3)) {
      const notice=node('p',window.aiwI18n?.t('Repeated reads returned the same result. The Worker will ask the model to reconsider its next step.') || 'Repeated reads returned the same result. The Worker will ask the model to reconsider its next step.','help');
      notice.append(document.createTextNode(' · '+new Date(event.occurred_ms).toLocaleTimeString(window.aiwI18n?.locale || 'en')));context.append(notice);
    }
    for(const event of events.filter(e=>e.event_type==='progress-review').slice(-3)) {
      const notice=node('p',window.aiwI18n?.t('No change was observed. The Worker will ask the model to recheck the delivery conditions.') || 'No change was observed. The Worker will ask the model to recheck the delivery conditions.','help');
      notice.append(document.createTextNode(' · '+new Date(event.occurred_ms).toLocaleTimeString(window.aiwI18n?.locale || 'en')));context.append(notice);
    }
    for(const event of events.filter(e=>e.event_type==='context-component').slice(-12)) {
      const item=JSON.parse(event.payload_json);context.append(node('p',(item.sourceKind||'输入组成')+'：'+item.bytes+' UTF-8 字节 · '+(item.changed?'内容有变化':'与上次相同')+' · '+item.digest.slice(0,12),'help'));
    }
    if(!context.childElementCount)context.append(node('p',state.eventHistoryAvailable?'暂无本次通道的上下文组成记录。':'历史元数据已到期清理，控制审计与任务结果保留。','help'));
    const receipts=$('wr-receipts');receipts.replaceChildren();
    for(const command of state.commands.slice(-12))receipts.append(node('p',({PAUSE:'暂停',RESUME:'继续',CANCEL:'取消'})[command.action]+' · '+(statuses[command.status]||command.status)+' · '+command.request_id,'help'));
    if(!receipts.childElementCount)receipts.append(node('p','暂无控制请求。','help'));
    controls();
  }
  async function refresh() {
    if(hidden||busy||!owner)return;busy=true;controls();
    try {
      if(native()||pending?.route==='bind')await catalog();
      if(pending){
        if(pending.route==='bind'){if(plans.find(p=>p.id===pending.body.planId)?.bound)pending=null;else pending.checked=true;}
        else {const receipt=await request('receipt',{requestId:pending.body.requestId});if(receipt.found===false)pending.checked=true;else pending=null;}
      }
      const current=choice(),key=current.taskId+'|'+current.attemptId;
      if(key!==selection){selection=key;state=null;cursor=0;events=[];}
      if(!current.taskId||!current.attemptId){state=null;return;}
      const result=await request('read',{...current,afterSequence:cursor});
      if(choice().taskId+'|'+choice().attemptId!==key)return;
      if(result.protocol!=='aiw.worker.loop.v1'||result.taskId!==current.taskId||result.attemptId!==current.attemptId)throw new Error('执行通道身份发生变化，请刷新核对。');
      state=result;for(const event of result.events){if(event.sequence_no<=cursor)continue;cursor=event.sequence_no;events.push(event);}events=events.slice(-128);
      $('wr-feedback').textContent=pending?'原提交未找到收据。可以重试同一请求；不要另建控制请求。':'';
    } catch(error){state=null;$('wr-feedback').textContent=error.message;}
    finally{busy=false;render();}
  }
  async function submit(body,route='command') {
    if(busy)return;busy=true;controls();
    if(route==='command'&&(!aligned()||body.taskId!==state.taskId||body.attemptId!==state.attemptId)){
      busy=false;state=null;$('wr-feedback').textContent='执行选择已变化。请切回原任务和尝试并刷新核对，再提交控制请求。';render();return;
    }
    try{await request(route,body);pending=null;$('wr-feedback').textContent=route==='bind'?'计划已启用控制和正式问答。':'请求已受理，请按回执确认实际是否生效。';}
    catch(error){if(!error.status||error.status>=500)pending={body,route,checked:false};$('wr-feedback').textContent=error.message;}
    finally{catalogAt=0;busy=false;await refresh();}
  }
  for(const [id,action] of [['wr-pause','PAUSE'],['wr-resume','RESUME'],['wr-cancel','CANCEL']])$(id).addEventListener('click',()=>{
    if(!aligned()||busy||pending)return;
    submit({requestId:'run-'+crypto.randomUUID(),taskId:state.taskId,attemptId:state.attemptId,instructionRevision:state.instructionRevision,expectedRunRevision:state.revision,action});
  });
  $('wr-retry').addEventListener('click',()=>{if(pending?.checked&&!busy)submit(pending.body,pending.route);});
  $('wr-bind').addEventListener('click',()=>{const plan=plans.find(p=>p.id===$('wr-plan').value);if(plan&&!$('wr-bind').disabled)submit({requestId:'bind-'+crypto.randomUUID(),planId:plan.id,expectedPlanVersion:plan.version},'bind');});
  for(const id of ['wr-source','wr-plan','wr-attempt','wb-task','wb-attempt'])$(id).addEventListener('change',()=>{
    if(id.startsWith('wb-')&&native())return;
    state=null;catalogAt=0;render();refresh();
  });
  const timer=setInterval(refresh,2000);
  window.addEventListener('pagehide',()=>{hidden=true;state=null;events=[];clearInterval(timer);render();});
  window.addEventListener('pageshow',event=>{if(event.persisted){hidden=false;location.reload();}});
  refresh();
})();
