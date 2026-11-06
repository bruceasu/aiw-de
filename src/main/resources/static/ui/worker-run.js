'use strict';
(() => {
  const $=id=>document.getElementById(id), csrf=document.querySelector('meta[name="csrf-token"]').content;
  const owner=document.querySelector('meta[name="configuration-owner"]').content;
  const labels={RUNNING:'执行中',PAUSING:'等待安全边界',PAUSED:'已暂停',STOPPING:'正在停止',FINALIZING:'正在收尾',SETTLED:'尝试已结束',BROKEN:'通道冲突，需核对'};
  const statuses={ACCEPTED:'已受理',RECEIVED:'Worker 已收到',APPLIED:'已生效',REJECTED:'已被停止请求取代',UNKNOWN:'结果未知'};
  const node=(tag,text,cls)=>{const n=document.createElement(tag);n.textContent=text;if(cls)n.className=cls;return n;};
  let state=null,selection='',cursor=0,events=[],busy=false,pending=null,hidden=false;
  const selected=()=>({taskId:$('wb-task').value,attemptId:$('wb-attempt').value});
  async function request(action,body) {
    const response=await fetch('/api/r1/authority/execution-runs/'+action,{method:'POST',credentials:'same-origin',
      headers:{'Content-Type':'application/json','X-CSRF-Token':csrf},body:JSON.stringify(body)});
    if(response.redirected||!response.headers.get('content-type')?.includes('application/json'))throw new Error('连接未确认，请重新登录后核对原收据。');
    if(!response.ok){const error=new Error(response.status===403||response.status===401?'请确认当前身份和操作权限。':response.status===409?'当前尝试尚未开启控制通道，或运行状态已经变化。':'暂时无法读取执行通道。');error.status=response.status;throw error;}
    return response.json();
  }
  function controls() {
    const live=state&&!state.stale&&state.attemptState==='RUNNING'&&state.capabilities.pauseBoundary==='TOOL'&&!busy&&!pending;
    $('wr-pause').disabled=!live||state.phase!=='RUNNING';$('wr-resume').disabled=!live||state.phase!=='PAUSED';
    $('wr-cancel').disabled=!live||['STOPPING','SETTLED','BROKEN'].includes(state.phase);
    $('wr-retry').hidden=!pending;$('wr-retry').disabled=busy||!pending?.checked;
  }
  function render() {
    const host=$('wr-state');host.replaceChildren();
    if(!state){host.append(node('p','所选尝试尚无已打开的运行通道。原有执行记录和停止流程仍可使用。','help'));$('wr-context').replaceChildren();$('wr-receipts').replaceChildren();controls();return;}
    host.append(node('strong',labels[state.phase]||state.phase),node('p',(state.stale?'联系已过期；不能确认实时控制。':'最近联系：'+new Date(state.lastContact).toLocaleTimeString())+' · 指令版本 '+state.instructionRevision,'help'));
    const cap=state.capabilities;host.append(node('p','工具执行：'+(cap.toolExecutor==='HOST'?'Worker':'后端')+' · 暂停粒度：单个宿主动作结束后 · 上下文：Worker 提交投影','help'));
    const context=$('wr-context');context.replaceChildren();
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
    if(hidden||busy||!owner)return;busy=true;controls();const choice=selected(),key=choice.taskId+'|'+choice.attemptId;
    if(key!==selection){selection=key;state=null;cursor=0;events=[];}
    try {
      if(pending){const receipt=await request('receipt',{requestId:pending.body.requestId});if(receipt.found===false)pending.checked=true;else pending=null;}
      if(!choice.taskId||!choice.attemptId){state=null;return;}
      const result=await request('read',{...choice,afterSequence:cursor});
      if(selected().taskId+'|'+selected().attemptId!==key)return;
      if(result.protocol!=='aiw.worker.loop.v1'||result.taskId!==choice.taskId||result.attemptId!==choice.attemptId)throw new Error('执行通道身份发生变化，请刷新核对。');
      state=result;for(const event of result.events){if(event.sequence_no<=cursor)continue;cursor=event.sequence_no;events.push(event);}events=events.slice(-128);
      $('wr-feedback').textContent=pending?'原提交未找到收据。可以重试同一请求；不要另建控制请求。':'';
    } catch(error){state=null;$('wr-feedback').textContent=error.message;}
    finally{busy=false;render();}
  }
  async function submit(body) {
    if(busy)return;busy=true;controls();
    try{await request('command',body);pending=null;$('wr-feedback').textContent='请求已受理，请按回执确认实际是否生效。';}
    catch(error){if(!error.status||error.status>=500)pending={body,checked:false};$('wr-feedback').textContent=error.message;}
    finally{busy=false;await refresh();}
  }
  for(const [id,action] of [['wr-pause','PAUSE'],['wr-resume','RESUME'],['wr-cancel','CANCEL']])$(id).addEventListener('click',()=>{
    if(!state||busy||pending)return;
    submit({requestId:'run-'+crypto.randomUUID(),taskId:state.taskId,attemptId:state.attemptId,instructionRevision:state.instructionRevision,expectedRunRevision:state.revision,action});
  });
  $('wr-retry').addEventListener('click',()=>{if(pending?.checked&&!busy)submit(pending.body);});
  const timer=setInterval(refresh,2000);
  window.addEventListener('pagehide',()=>{hidden=true;state=null;events=[];clearInterval(timer);render();});
  window.addEventListener('pageshow',event=>{if(event.persisted){hidden=false;location.reload();}});
  refresh();
})();
