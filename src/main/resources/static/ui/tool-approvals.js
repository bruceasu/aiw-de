'use strict';
(() => {
  const $=id=>document.getElementById(id),csrf=document.querySelector('meta[name="csrf-token"]').content;
  const canApprove=document.querySelector('meta[name="tool-can-approve"]')?.content==='true';
  const states={PENDING:'待审批',APPROVED:'已批准，尚未执行',REJECTED:'已拒绝',EXPIRED:'已过期',INVALIDATED:'已失效',CONSUMED:'授权已使用，执行结果请查操作记录'};
  const actions={REPLACE_FILE:'整体替换文件',DELETE_FILE:'删除单个文件',EXECUTE_COMMAND:'执行固定命令'};
  let rows=null,loadedPlan='',busy=false,confirmation=null,pending=null,hidden=false;
  const node=(tag,text,cls)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(cls)n.className=cls;return n;};
  const message=text=>{$('ta-status').textContent=text;};
  const current=()=>rows!==null&&loadedPlan===$('ta-plan').value;
  const valid=row=>row&&row.state==='PENDING'&&Number.isSafeInteger(row.revision)&&row.revision>0&&
    Number.isSafeInteger(row.binding?.expiresAt)&&row.binding.expiresAt>Date.now()&&Boolean(actions[row.binding.action])&&row.quiescent===true;
  function sync() {
    $('ta-plan').disabled=busy||Boolean(confirmation)||Boolean(pending);
    $('ta-filter').disabled=busy||Boolean(confirmation)||Boolean(pending);
    $('ta-refresh').disabled=busy||!$('ta-plan').value||Boolean(confirmation);
    $('ta-confirm').hidden=!confirmation;$('ta-uncertain').hidden=!pending;
    $('ta-dismiss').disabled=busy;$('ta-confirm-check').disabled=busy;
    $('ta-submit').disabled=busy||!confirmation||!valid(confirmation.row)||!$('ta-confirm-check').checked;
    $('ta-retry').disabled=busy||!pending?.checked||!valid(rows?.find(row=>row.id===pending?.body.approvalId));
    for(const button of document.querySelectorAll('[data-tool-approval]')) {
      const row=rows?.find(r=>r.id===button.dataset.toolApproval);
      button.disabled=!canApprove||!current()||busy||Boolean(confirmation)||Boolean(pending)||!valid(row);
    }
  }
  async function request(action,body,write=false) {
    let response,result;
    try {
      response=await fetch('/api/r1/authority/execution-tool-approvals/'+action,{method:'POST',credentials:'same-origin',
        headers:{'Content-Type':'application/json','X-CSRF-Token':csrf},body:JSON.stringify(body)});
      if(response.redirected||!response.headers.get('content-type')?.includes('application/json'))throw new Error();
      result=await response.json();
    } catch(_) {const error=new Error(write?'提交结果未知，请刷新原记录核对。':'无法读取记录，请重新登录或稍后刷新。');error.uncertain=write;throw error;}
    if(!response.ok) {
      const error=new Error(response.status===401||response.status===403?'当前身份无权处理此计划，请核对登录账号、Owner 和审批角色。':
        response.status===409?'审批版本或状态已变化，请刷新并重新核对。':response.status===422?'请求未被接受，请刷新并核对原记录。':'服务暂时无法确认结果，请刷新原记录。');
      error.uncertain=write&&response.status>=500;throw error;
    }
    return result;
  }
  function render() {
    const host=$('ta-records');host.replaceChildren();
    if(!current()){sync();return;}
    const visible=rows.filter(r=>$('ta-filter').value==='all'||r.state==='PENDING');
    for(const row of visible) {
      const binding=row.binding,operation=binding.operation,card=node('article',undefined,'workbench-record');
      card.append(node('h3',(actions[binding.action]||'未知操作')+' · '+(states[row.state]||'未知状态')),
        node('p',(binding.action==='EXECUTE_COMMAND'?'命令编号：':'目标路径：')+operation.resource),node('p','所属任务：'+row.taskId),
        node('p','有效期至：'+new Date(binding.expiresAt).toLocaleString(),'help'));
      if(row.successorTaskId)card.append(node('p','续接任务：'+row.successorTaskId,'help'));
      if(row.state==='PENDING'&&binding.expiresAt<=Date.now())card.append(node('p','有效期已过，请刷新核对平台状态。','help'));
      if(row.state==='PENDING'&&!row.quiescent)card.append(node('p','Worker 尚未确认模型和工具已停止；确认停止前不能作出决定。','help'));
      const details=node('details');details.append(node('summary','核对固定操作与版本'));
      for(const [label,value] of [['审批编号',row.id],['操作编号',operation.operationId],['来源尝试',operation.lease.attemptId],
        ['仓库引用',operation.repositoryRef],['授权修订',operation.expectedGrantRevision],[binding.action==='EXECUTE_COMMAND'?'命令摘要':'目标文件版本',binding.targetVersion],
        ['工作区摘要',binding.workspaceDigest],['参数摘要',operation.parameterDigest],['审批内容摘要',row.bindingDigest],['审批版本',row.revision]])
        details.append(node('p',label+'：'+value));
      if(binding.action==='EXECUTE_COMMAND'&&binding.command) {
        details.append(node('p','固定 argv：'+JSON.stringify(binding.command.argv)),
          node('p','工作目录：'+binding.command.cwd),node('p','权限配置：'+binding.command.permissionProfile),
          node('p','资源限制：超时 '+binding.command.timeoutMs+' ms；输出 '+binding.command.outputBytes+' 字节；内存 '+binding.command.memoryBytes+' 字节；进程 '+binding.command.processLimit));
      } else details.append(node('p','文件正文未上传；摘要用于识别固定版本，不能代替对具体改动的核对。','help'));
      card.append(details);
      if(row.state==='PENDING') {
        const controls=node('div',undefined,'workbench-actions section');
        for(const approve of [true,false]) {
          const button=node('button',approve?'批准此操作…':'拒绝此操作…','btn ghost');button.type='button';button.dataset.toolApproval=row.id;
          button.addEventListener('click',()=>confirm(row,approve));controls.append(button);
        }
        card.append(controls);
      }
      host.append(card);
    }
    if(!visible.length)host.append(node('p',rows.length?'当前没有待审批操作。可切换为全部记录查看历史。':'此计划尚无工具审批申请。Worker 在遇到需要确认的文件操作时会自动提交申请。','empty'));
    sync();
  }
  async function refresh() {
    if(busy||confirmation||hidden||!$('ta-plan').value)return;
    busy=true;sync();const plan=$('ta-plan').value;
    try {
      const result=await request('read',{planId:plan});
      if(!Array.isArray(result))throw new Error('审批记录格式无法确认，请刷新核对。');
      rows=result;loadedPlan=plan;
      if(pending) {
        const row=rows.find(r=>r.id===pending.body.approvalId);
        pending.checked=Boolean(valid(row)&&row.revision===pending.body.expectedRevision&&row.bindingDigest===pending.body.bindingDigest);
        if(row&&!pending.checked){pending=null;message('原记录已变化。请查看当前状态；没有自动重发决定。');}
        else message(pending.checked?'原记录仍为同一版本待审批，可明确重试原决定。':'尚未确认原记录，不能重试。请保留本页并核对服务状态。');
      } else message('已读取计划 '+plan+' 的审批记录，时间：'+new Date().toLocaleTimeString()+'。');
    } catch(error){rows=null;loadedPlan='';message(error.message);}
    finally {busy=false;render();}
  }
  function confirm(row,approve) {
    if(!canApprove||!current()||busy||pending||confirmation||!valid(row))return;
    confirmation={row:structuredClone(row),approve};$('ta-confirm-check').checked=false;
    $('ta-confirm-title').textContent=approve?'确认批准这项操作':'确认拒绝这项操作';
    const command=row.binding.command;
    $('ta-confirm-target').textContent=(actions[row.binding.action]||'操作')+'：'+row.binding.operation.resource+
      (row.binding.action==='EXECUTE_COMMAND'&&command?'；argv '+JSON.stringify(command.argv)+'；cwd '+command.cwd:'')+
      '；审批编号 '+row.id+'；版本 '+row.revision+'。';
    $('ta-submit').textContent=approve?'确认批准':'确认拒绝';sync();$('ta-confirm-check').focus();
  }
  async function submit(body) {
    if(busy||!canApprove)return;busy=true;confirmation=null;sync();
    let outcome='决定已保存；执行是否开始请查操作记录。';
    try {
      const result=await request('decide',body,true);pending=null;
      outcome=body.approve&&result.successorTaskId?'批准已保存并创建续接任务。Worker 将在取得执行资源后校验并执行这项固定操作。':'拒绝已保存；这项操作不会执行。';
    }
    catch(error){if(error.uncertain)pending={body:structuredClone(body),checked:false};outcome=error.message;}
    finally {rows=null;loadedPlan='';busy=false;render();}
    // Read back even after a lost response. Never replay a decision automatically.
    await refresh();
    if(!pending)message(outcome+' '+$('ta-status').textContent);
  }
  $('ta-submit').addEventListener('click',()=>{
    if(!confirmation||$('ta-submit').disabled)return;
    const {row,approve}=confirmation;
    submit({approvalId:row.id,expectedRevision:row.revision,bindingDigest:row.bindingDigest,approve});
  });
  $('ta-dismiss').addEventListener('click',()=>{if(!busy){confirmation=null;sync();}});
  $('ta-confirm-check').addEventListener('change',sync);
  $('ta-retry').addEventListener('click',()=>{if(pending?.checked&&!busy)submit(pending.body);});
  $('ta-refresh').addEventListener('click',refresh);
  $('ta-filter').addEventListener('change',render);
  $('ta-plan').addEventListener('change',()=>{rows=null;loadedPlan='';render();if($('ta-plan').value)refresh();else message('选择计划后读取审批记录。');});
  const initial=new URLSearchParams(location.search).get('planId');
  if(initial&&[...$('ta-plan').options].some(option=>option.value===initial))$('ta-plan').value=initial;
  const timer=setInterval(sync,1000);
  window.addEventListener('pagehide',()=>{hidden=true;clearInterval(timer);rows=null;confirmation=null;sync();});
  sync();if($('ta-plan').value)refresh();
})();
