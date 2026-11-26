(() => {
  'use strict';
  const $=s=>document.querySelector(s),dialog=$('[data-workers-detail]');
  const words={
    'Workers':['Worker 团队'],'Agent factory':['Agent 工厂'],'Refresh':['刷新'],
    'Execution processes':['执行进程'],'Team member':['团队成员'],
    'model-request':['调用模型'],'model-terminal':['模型调用结束'],
    'tool-proposed':['准备使用工具'],'tool-start':['开始使用工具'],'tool-end':['工具操作结束'],
    'read_file':['读取文件'],'apply_patch':['修改文件'],'shell':['执行命令'],
    'Current work, attention needed and connection status':['当前工作、待处理事项与连接状态'],
    'Worker':['Worker'],'Current work':['当前工作'],'Recent activity':['最近活动'],'Actions':['操作'],
    'Status':['状态'],'Source':['来源'],'Search Worker, task or Plan':['搜索 Worker、任务或 Plan'],
    'All statuses':['全部状态'],'All sources':['全部来源'],'Needs attention':['待处理'],
    'Connection issue':['连接异常'],'Working':['执行中'],'Idle':['空闲'],'Marked offline':['已标记离线'],
    'Factory generated':['工厂生成'],'External connection':['外部接入'],'Unknown':['未知'],
    'Scheduling diagnostics':['调度诊断'],'Diagnostics captured at page load':['诊断快照读取于页面加载时'],
    'Tasks not started':['项任务尚未开始'],
    'Reload diagnostics':['重新读取诊断'],'Close':['关闭'],'Select execution':['选择执行'],
    'View':['查看'],'Handle':['处理'],'Not recorded':['未记录'],'Last heartbeat':['最后心跳'],
    'Method':['工作方法／已记录 Skill'],'Model':['已记录模型'],'Plan':['所属 Plan'],
    'Runtime':['实际运行时'],'Provider':['服务商'],'Effort':['思考强度'],'Task':['任务'],
    'Process':['执行过程'],'Connection diagnostics':['连接诊断'],'Recorded usage':['已记录用量'],
    'Round count':['轮数'],'Model calls':['模型调用次数'],'Input tokens':['输入 Token'],
    'Cached tokens':['其中缓存 Token'],'Output tokens':['输出 Token'],'Known estimates':['已知估算费用'],
    'Missing values in calls':['次调用缺少该值'],'Unpriced calls':['尚无估算的调用（含订阅）'],
    'Estimates are not invoices; cached tokens are part of input.':['估算不是账单；缓存 Token 已包含在输入中。'],
    'Management status':['管理状态'],'Concurrency and active leases':['并发上限／活动租约'],
    'Registered at':['注册时间'],
    'Capabilities':['能力标签'],'Internal references':['内部引用'],'Active work shown':['已显示的工作'],
    'Open full workbench':['打开完整工作台'],'Execution controls in workbench':['到工作台控制本次执行'],
    'Open Plan controls':['到所属计划查看／提前停止'],'Open questions and approvals':['处理问题与授权'],
    'No live control channel recorded.':['本次尝试尚未记录实时控制通道。'],
    'Stop accepting new tasks':['停止领取新任务'],'Current tasks continue.':['当前任务会继续。'],
    'Mark offline':['标记离线'],'This does not terminate the remote process.':['这不会终止远端进程。'],
    'A connection issue does not confirm the remote process has stopped.':['连接异常不代表远端进程已经停止。'],
    'Refresh failed; the previous snapshot is retained.':['刷新失败，保留上次快照，请核对连接。'],
    'Loading':['正在读取'],'Updated':['读取时间'],'No matching Workers':['没有符合条件的 Worker'],
    'Loaded Workers':['已加载 Worker'],'Linked work':['已关联工作'],'Overview and filters cover loaded Workers only.':['概览、搜索和筛选仅覆盖已加载的 Worker。'],
    'Other accounts work is hidden.':['其他账号的工作内容不展示。'],
    'Work details require an operator account.':['工作内容需要具有操作权限的账号查看。'],
    'No visible current work':['暂无可见的当前工作'],'More work may exist outside this snapshot.':['快照之外可能还有工作，请结合数量查看。'],
    'Choose an execution':['请选择一个执行'],'The selected execution changed. No command was sent.':['所选执行已变化，未发送任何控制请求。'],
    'Worker is outside the loaded snapshot.':['此 Worker 已不在当前快照中。'],
    'Source is unknown without a formal origin record.':['没有正式来源记录时显示未知，不按 ID 推断外部接入。'],
    'Instruction truncated for this view.':['当前视图已截断较长指令。'],
    'RUNNING':['执行中'],'PAUSED':['已暂停'],'PAUSING':['等待 Worker 暂停确认'],
    'STOPPING':['等待 Worker 停止确认'],'SETTLED':['本次执行已结束'],'BROKEN':['通道异常'],
    'CLAIMED':['已领取'],'WAITING_INPUT':['等待输入'],'DRAINING':['不再领取新任务'],'ACTIVE':['可领取任务'],'OFFLINE':['已标记离线'],
  };
  const t=x=>(window.aiwI18n?.locale==='zh' && words[x]?.[0])||window.aiwI18n?.t(x)||x;
  const text=(tag,value,cls)=>{const n=document.createElement(tag);if(value!=null)n.textContent=value;if(cls)n.className=cls;return n;};
  const date=x=>x?new Date(x).toLocaleString(window.aiwI18n?.locale||undefined):t('Not recorded');
  const number=x=>Number.isSafeInteger(x)?x.toLocaleString(window.aiwI18n?.locale||undefined):t('Not recorded');
  let snapshot=null,busy=false,selected=null,selectedAttempt=null,loadFailed=false;
  const stale=m=>snapshot.observedAt-Date.parse(m.worker.lastHeartbeatAt)>90000;
  const category=m=>m.pendingCount>0?'PENDING':stale(m)?'DISCONNECTED':m.managementStatus==='OFFLINE'?'OFFLINE':m.worker.activeLeases>0||m.visibleWorkCount>0||m.work.length>0?'RUNNING':'IDLE';
  const label={PENDING:'Needs attention',DISCONNECTED:'Connection issue',RUNNING:'Working',IDLE:'Idle',OFFLINE:'Marked offline',FACTORY:'Factory generated',EXTERNAL:'External connection',UNKNOWN:'Unknown'};
  function translate(){document.querySelectorAll('[data-workers-label]').forEach(n=>n.textContent=t(n.dataset.workersLabel));}
  function render(){
    if(!snapshot)return;
    $('[data-workers-ready]').textContent=number(snapshot.readyTasks)+' '+t('Tasks not started');
    const counts=$('[data-workers-counts]');counts.replaceChildren();
    for(const kind of ['RUNNING','IDLE','PENDING','DISCONNECTED']){const card=text('div',null,'card');card.append(text('div',t(label[kind]),'metric-label'),text('div',snapshot.members.filter(m=>category(m)===kind).length,'metric'));counts.append(card);}
    $('[data-workers-scope]').textContent=t('Loaded Workers')+': '+snapshot.members.length+' / '+snapshot.totalWorkers+' · '+t('Linked work')+': '+snapshot.members.reduce((n,m)=>n+m.work.length,0)+' / '+snapshot.totalVisibleWork+' · '+t('Overview and filters cover loaded Workers only.')+' '+t(document.body.dataset.canOperate==='true'?'Other accounts work is hidden.':'Work details require an operator account.');
    const term=$('[data-workers-search]').value.toLocaleLowerCase(),filter=$('[data-workers-status]').value,source=$('[data-workers-source]').value;
    const priority={PENDING:0,DISCONNECTED:1,RUNNING:2,IDLE:3,OFFLINE:4};
    const members=snapshot.members.filter(m=>(!filter||category(m)===filter)&&(!source||m.source===source)&&(!term||[m.mark,m.worker.workerId,m.worker.runtimeType,...m.work.flatMap(w=>[w.title,w.task_id,w.plan_id,w.plan_title,w.skill_ref,w.ai_model,w.member_name])].filter(Boolean).join(' ').toLocaleLowerCase().includes(term)))
      .sort((a,b)=>priority[category(a)]-priority[category(b)]||a.worker.workerId.localeCompare(b.worker.workerId));
    const list=$('[data-workers-list]');list.replaceChildren();
    for(const m of members){
      const tr=text('tr'),name=text('td'),person=text('div',null,'workers-person');person.append(text('span',m.mark,'workers-mark'),text('strong','Worker '+m.mark));name.append(person,text('small',t(label[m.source]||'Unknown')));
      const work=text('td');for(const w of m.work.slice(0,3)){work.append(text('strong',w.title),text('small',(w.plan_title||w.plan_id||t('Not recorded'))+' · '+(w.skill_ref||t('Not recorded'))),text('small',w.ai_model||t('Not recorded')));if(w.member_name)work.append(text('small',t('Team member')+': '+w.member_name));}
      if(!m.work.length)work.append(text('span',t('No visible current work')));
      if(m.visibleWorkCount>3||m.visibleWorkCount>m.work.length)work.append(text('small',t('Active work shown')+': '+m.work.length+' / '+m.visibleWorkCount));
      const events=m.work.flatMap(w=>w.events||[]).sort((a,b)=>b.time-a.time),recent=text('td');recent.append(text('span',events.length?t(events[0].toolName||events[0].runtimeEvent||events[0].type):t('Last heartbeat')),text('small',date(events[0]?.time||m.worker.lastHeartbeatAt)));
      const state=text('td'),badge=text('span',t(label[category(m)]),'badge');
      badge.dataset.status=category(m)==='IDLE'?'ACTIVE':category(m);
      state.append(badge,text('small',t(m.managementStatus)));
      if(stale(m))state.append(text('small',t('A connection issue does not confirm the remote process has stopped.')));
      const actions=text('td'),open=text('button',t(category(m)==='PENDING'?'Handle':'View'),'btn ghost');open.type='button';open.dataset.workerId=m.worker.workerId;open.onclick=()=>openDetail(m.worker.workerId);actions.append(open);tr.append(name,work,recent,state,actions);list.append(tr);
    }
    if(!members.length){const cell=text('td',t('No matching Workers'),'empty');cell.colSpan=5;const row=text('tr');row.append(cell);list.append(row);}
    if(selected)renderDetail();
  }
  function facts(host,rows){const dl=text('dl');for(const [key,value] of rows){dl.append(text('dt',t(key)),text('dd',value==null||value===''?t('Not recorded'):value));}host.append(dl);}
  function link(host,label,url){const a=text('a',t(label),'btn ghost');a.href=url;a.dataset.focus=label;host.append(a);}
  function workerForm(host,m,action,label,help){
    if(document.body.dataset.canOperate!=='true')return;
    const form=text('form');form.method='post';form.action='/ui/workers/'+encodeURIComponent(m.worker.workerId)+'/'+action;
    const csrf=document.createElement('input');csrf.type='hidden';csrf.name='_csrf';csrf.value=$('meta[name="csrf-token"]').content;
    const button=text('button',t(label),'btn ghost');button.type='submit';button.dataset.focus=action;button.disabled=action==='drain'?m.managementStatus!=='ACTIVE':m.managementStatus==='OFFLINE'||m.worker.activeLeases>0;
    form.append(csrf,button,text('p',t(help),'help'));form.onsubmit=()=>window.confirm(t(label)+'? '+t(help));host.append(form);
  }
  function closeDetail(){
    const id=selected;selected=null;dialog.hidden=true;
    ([...document.querySelectorAll('[data-worker-id]')].find(n=>n.dataset.workerId===id)||$('[data-workers-search]')).focus();
  }
  function openDetail(id){
    selected=id;selectedAttempt=null;
    const member=snapshot.members.find(item=>item.worker.workerId===id);
    if(member?.work.length===1)selectedAttempt=member.work[0].attempt_id;
    renderDetail();
    dialog.hidden=false;
    window.aiwDesktopWindow?.open(dialog,{title:$('#workers-detail-title').textContent||id,icon:'workers',onClose:closeDetail});
  }
  function renderDetail(){
    const m=snapshot.members.find(m=>m.worker.workerId===selected),host=$('[data-workers-detail-body]'),notice=$('[data-workers-detail-notice]'),select=$('[data-workers-work]');
    const focused=host.contains(document.activeElement)?document.activeElement.dataset.focus:null;
    $('[id="workers-detail-title"]').textContent=m?'Worker '+m.mark:t('Worker');notice.textContent=loadFailed?t('Refresh failed; the previous snapshot is retained.'):'';
    if(!m){notice.textContent=t('Worker is outside the loaded snapshot.');host.replaceChildren();select.replaceChildren();return;}
    select.replaceChildren(new Option(t('Choose an execution'),''));for(const w of m.work)select.add(new Option(w.title+' · '+w.attempt_no,w.attempt_id));
    const current=m.work.find(w=>w.attempt_id===selectedAttempt);select.value=current?.attempt_id||'';select.disabled=m.work.length===0;
    if(selectedAttempt&&!current)notice.textContent=t('The selected execution changed. No command was sent.');
    const opened=new Set([...host.querySelectorAll('details[open]')].map(n=>n.dataset.section));host.replaceChildren();
    if(current){
      host.append(text('h3',t('Current work')));facts(host,[['Team member',current.member_name],['Task',current.title],['Plan',current.plan_title||current.plan_id],['Status',t(current.phase||current.task_state)]]);
      const instruction=text('details');instruction.dataset.section='instruction';instruction.open=opened.has('instruction');instruction.append(text('summary',t('Task')),text('pre',current.instruction));if(current.instruction_truncated)instruction.append(text('p',t('Instruction truncated for this view.'),'help'));host.append(instruction);
      const links=text('div',null,'workers-links'),params=new URLSearchParams({view:'workbench',source:current.workbench_source,plan:current.plan_id,task:current.task_id,attempt:current.attempt_id});
      link(links,'Open full workbench','/ui/configurations?'+params);
      if(current.phase)link(links,'Execution controls in workbench','/ui/configurations?'+params+'#worker-run');else host.append(text('p',t('No live control channel recorded.'),'help'));
      link(links,'Open Plan controls',current.unified?'/ui/workflows?execution='+encodeURIComponent(current.plan_id):'/ui/plans/'+encodeURIComponent(current.plan_id));
      if(current.pending)link(links,'Open questions and approvals','/ui/questions?planId='+encodeURIComponent(current.plan_id));host.append(links);
      host.append(text('h3',t('Process')));for(const e of current.events||[])host.append(text('p',date(e.time)+' · '+t(e.toolName||e.runtimeEvent||e.type)+(e.status?' · '+t(e.status):'')));
      if(!current.events?.length)host.append(text('p',t('Not recorded'),'help'));
      facts(host,[['Runtime',current.ai_runtime],['Provider',current.ai_provider],['Model',current.ai_model],['Effort',current.ai_effort],['Method',current.skill_ref]]);
      host.append(text('h3',t('Recorded usage')),text('p',t('Estimates are not invoices; cached tokens are part of input.'),'help'));
      const usage=current.usage;facts(host,[['Round count',null],['Model calls',usage?number(usage.calls):null],...['input','cached','output'].map((key,i)=>[['Input tokens','Cached tokens','Output tokens'][i],usage?number(usage[key+'_tokens'])+(usage['missing_'+key]>0?' · '+usage['missing_'+key]+' '+t('Missing values in calls'):''):null])]);
      for(const price of current.prices||[])facts(host,[['Known estimates',Number.isSafeInteger(price.cost_micros)?(price.cost_micros/1000000).toLocaleString(window.aiwI18n?.locale||undefined,{maximumFractionDigits:6})+' '+price.currency:null]]);
      if(!current.prices?.length)facts(host,[['Known estimates',null]]);
      if(usage)facts(host,[['Unpriced calls',number(Math.max(0,usage.calls-(current.prices||[]).reduce((n,p)=>n+p.priced_calls,0)))]]);
    }else{host.append(text('p',t(m.work.length?'Choose an execution':'No visible current work'),'help'));if(m.pendingCount>0)link(host,'Open questions and approvals','/ui/questions');}
    if(m.visibleWorkCount>m.work.length)host.append(text('p',t('More work may exist outside this snapshot.'),'help'));
    const diagnostics=text('details');diagnostics.dataset.section='diagnostics';diagnostics.open=opened.has('diagnostics');diagnostics.append(text('summary',t('Connection diagnostics')));
    facts(diagnostics,[['Internal references',m.worker.workerId],['Management status',t(m.managementStatus)],['Registered at',date(m.registeredAt)],['Last heartbeat',date(m.worker.lastHeartbeatAt)],['Concurrency and active leases',m.worker.maxConcurrency+' / '+m.worker.activeLeases],['Capabilities',m.worker.capabilities.join(', ')],['Source',t(label[m.source]||'Unknown')]]);
    if(m.source==='UNKNOWN')diagnostics.append(text('p',t('Source is unknown without a formal origin record.'),'help'));
    if(current)facts(diagnostics,[['Task',current.task_id],['Internal references',current.attempt_id]]);host.append(diagnostics);
    workerForm(host,m,'drain','Stop accepting new tasks','Current tasks continue.');workerForm(host,m,'offline','Mark offline','This does not terminate the remote process.');
    if(focused)[...host.querySelectorAll('[data-focus]')].find(n=>n.dataset.focus===focused)?.focus();
  }
  async function refresh(){
    if(busy)return;busy=true;$('[data-workers-refresh]').disabled=true;
    try{const response=await fetch('/api/r1/fleet/workers',{credentials:'same-origin'});if(!response.ok||response.redirected||!response.headers.get('content-type')?.includes('application/json'))throw new Error();snapshot=await response.json();loadFailed=false;render();$('[data-workers-feedback]').textContent=t('Updated')+': '+date(snapshot.observedAt);}
    catch(_){loadFailed=true;$('[data-workers-feedback]').textContent=t('Refresh failed; the previous snapshot is retained.');if(selected)renderDetail();}
    finally{busy=false;$('[data-workers-refresh]').disabled=false;}
  }
  $('[data-workers-close]').onclick=()=>{
    if(window.aiwDesktopWindow?.has(dialog))window.aiwDesktopWindow.close(dialog);else closeDetail();
  };
  $('[data-workers-work]').onchange=e=>{selectedAttempt=e.target.value||null;renderDetail();};
  for(const key of ['search','status','source'])$('[data-workers-'+key+']').addEventListener(key==='search'?'input':'change',render);
  $('[data-workers-refresh]').onclick=refresh;window.addEventListener('aiw:locale-changed',()=>{translate();render();});
  translate();$('[data-workers-feedback]').textContent=t('Loading');refresh();
  const timer=setInterval(()=>{if(!document.hidden)refresh();},8000);window.addEventListener('pagehide',()=>clearInterval(timer));
  window.addEventListener('pageshow',event=>{if(event.persisted)location.reload();});
})();
