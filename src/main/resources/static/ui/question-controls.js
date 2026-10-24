'use strict';
(() => {
  const ui=window.QuestionUI;if(!ui)return;
  const {$,node,readout,flag,safe,number}=ui;
  const taskStates={BLOCKED:'等待前置条件',READY:'等待 Worker 领取',CLAIMED:'Worker 已领取',RUNNING:'正在执行',YIELDING:'正在移交执行',WAITING_INPUT:'等待补充输入',CONTINUED:'已继续处理',COMPLETED:'执行已完成',FAILED:'执行失败',CANCELLED:'已取消'};
  const deliveryStates={READY:'等待发送',SENDING:'正在发送',DELIVERED:'已送达',UNKNOWN:'结果未知 · 请核对原消息',INVALIDATED:'关联已失效'};
  let budgetDraft=null,budgetPlan=null;
  function table(host,headings,rows,empty) {
    host.replaceChildren();if(!rows.length){host.append(node('p',empty,'empty'));return;}
    const wrap=node('div',undefined,'table-wrap'),table=node('table'),head=node('thead'),tr=node('tr'),body=node('tbody');
    headings.forEach(text=>tr.append(node('th',text)));head.append(tr);
    for(const values of rows){const row=node('tr');for(const value of values){const cell=node('td');if(value instanceof HTMLElement)cell.append(value);else cell.textContent=value;row.append(cell);}body.append(row);}
    table.append(head,body);wrap.append(table);host.append(wrap);
  }
  function measured(value){return value==null?'未记录':number(value);}
  function binding(){return ui.telegram?.bindings.find(b=>flag(b.enabled));}
  function update() {
    const data=ui.data,telegram=ui.telegram,allowed=ui.writable(),revoked=flag(data?.policy.revoked),paused=flag(data?.policy.paused),expired=data?.policy.expires_ms!=null&&Number(data.policy.expires_ms)<=Date.now();
    $('question-policy-state').textContent=!data?'尚未读取':revoked?'执行范围已取消':expired?'执行范围已到期':paused?'已人工暂停':'允许按范围准入';
    $('question-policy-note').textContent=!data?'先读取计划，再决定是否需要人工控制。':revoked?'此执行范围已取消，不能直接恢复。在途任务的停止仍须实际确认。':expired?'执行范围已到期，请在授权页核对；不能仅靠恢复按钮延长期限。':paused?'新领取和推进已暂停，在途任务仍可能按原授权收尾。':'当前未人工暂停，实际领取和推进仍取决于执行范围、兼容 Worker 和前置条件。';
    document.querySelectorAll('[data-control]').forEach(button=>{button.disabled=!allowed||!ui.canOperate||revoked||(button.dataset.control==='pause'&&paused)||(button.dataset.control==='resume'&&(!paused||expired));});
    table($('question-tasks'),['任务','执行状态','停止确认'],data?data.tasks.map(t=>[t.id,taskStates[t.state]||'状态待核对',t.stop_acknowledged_at_epoch_ms!=null?'已确认停止':t.stop_requested_at_epoch_ms!=null?'已请求停止 · 等待实际确认':'未请求停止']):[],data?'此计划暂无正式任务记录。':'尚未读取任务。');
    const columns=['记录维度','已使用','已预留',...(ui.enforced?['上限']:[]),'未知项'];
    table($('question-meters'),columns,data?data.budgets.map(b=>[ui.dimensions[b.dimension]||'其它记录',measured(b.used_value),measured(b.reserved_value),...(ui.enforced?[measured(b.limit_value)]:[]),measured(b.unknown_count)]):[],data?'尚无用量记录；缺少记录不代表零费用。':'尚未读取记录。');
    const active=binding(),bindingHost=$('question-binding-state');bindingHost.replaceChildren();
    if(!telegram)bindingHost.append(node('p','通知状态尚未成功读取，绑定与发送操作已暂停。','help'));
    else if(active)bindingHost.append(readout([['绑定状态','已绑定 · 当前账号所有计划共用'],['个人用户',active.user_id],['私聊',active.chat_id]]));
    else bindingHost.append(node('p','当前没有有效私聊绑定。可以在下方明确登记，不影响控制台直接回答。','help'));
    $('question-binding-fields').disabled=!allowed||!telegram||!(ui.canOperate||ui.canApprove);
    $('question-unbind').disabled=!allowed||!active||!(ui.canOperate||ui.canApprove);
    const deliveries=telegram?.deliveries||[];
    table($('question-deliveries'),['对应问题','送达状态','消息关联'],deliveries.map(d=>{
      const q=data?.questions.find(q=>q.questionId===d.question_id),label=q?node('button',q.question.length>70?q.question.slice(0,70)+'…':q.question,'btn ghost'):node('span','其它计划或未列出的问题：'+d.question_id);
      if(q){label.type='button';label.addEventListener('click',()=>ui.openQuestion(q.questionId));}
      return [label,deliveryStates[d.state]||'状态待核对',d.state==='UNKNOWN'?'未知结果不会因过期自动重发':d.state==='INVALIDATED'?'不可用于回答':Number(d.expires_ms)<=Date.now()?'关联已过期':'仅对当前问题版本有效'];
    }),telegram?'暂无送达记录。请在具体问题上明确选择发送。':'尚未读取送达记录。');
    if(deliveries.length>=100)$('question-deliveries').prepend(node('p','仅返回最近 100 条，送达记录不完整；当前暂停新发送，避免遗漏旧未知结果后重复发送。','callout warn-callout'));
    if($('question-budget-fields'))$('question-budget-fields').disabled=!allowed||!ui.canApprove||revoked;
    if(budgetPlan!==$('question-plan').value){budgetPlan=$('question-plan').value;budgetDraft=null;$('question-budget')?.reset();}
  }
  document.querySelectorAll('[data-control]').forEach(button=>button.addEventListener('click',()=>{
    if(button.disabled||!ui.writable()||!ui.data)return;
    const action=button.dataset.control,body={planId:$('question-plan').value,revision:ui.data.policy.revision,action};
    if(action==='cancel')ui.confirm('control','control',body,'取消此计划的执行','停止新的领取和推进，并向在途工作提交停止请求。必须等待实际停止确认；不会自动回滚或重放。',body.planId);
    else ui.write('control','control',body,action==='pause'?'暂停新工作':'恢复推进');
  }));
  $('question-binding').addEventListener('submit',event=>{
    event.preventDefault();if(!ui.writable()||!ui.telegram||!event.target.reportValidity())return;
    const form=new FormData(event.target),body={planId:$('question-plan').value,operation:'bind',binding:{userId:form.get('userId'),privateChatId:form.get('privateChatId')},questionId:null};
    ui.confirm('bind','telegram',body,'保存此账号的私聊绑定','这个绑定影响当前账号的所有计划，旧消息关联和排队送达会失效。保存不会发送问题，也不表示 Bot 已启用。','用户 '+body.binding.userId+' · 私聊 '+body.binding.privateChatId);
  });
  $('question-unbind').addEventListener('click',()=>{
    if(!ui.writable()||!binding())return;
    const body={planId:$('question-plan').value,operation:'unbind',binding:null,questionId:null};
    ui.confirm('unbind','telegram',body,'解除此账号的私聊绑定','当前账号所有计划的旧私聊关联和排队送达都会失效。控制台仍可处理工作问题。','当前账号 · 私聊 '+binding().chat_id);
  });
  $('question-budget')?.addEventListener('submit',event=>{
    event.preventDefault();if(!ui.writable()||!ui.canApprove||!ui.enforced||!event.target.reportValidity())return;
    const form=new FormData(event.target),amount=Number(form.get('amount')),ceiling=Number(form.get('cumulativeCeiling')),dimension=form.get('dimension'),loop=dimension==='LOOP_BACKJUMPS'?form.get('loopId').trim():null;
    if(!safe(amount,1)||!safe(ceiling,1)||dimension==='LOOP_BACKJUMPS'&&!loop){ui.message('增加量和累计上限必须是可精确处理的正整数；循环回跳还需要明确循环标识。','error');return;}
    const fields={planId:$('question-plan').value,policyRevision:ui.data.policy.revision,question:form.get('question'),authorization:{dimension,loopId:loop,amount,cumulativeCeiling:ceiling}},fingerprint=JSON.stringify(fields);
    if(!budgetDraft||budgetDraft.fingerprint!==fingerprint)budgetDraft={fingerprint,body:{...fields,authorization:{...fields.authorization,requestId:crypto.randomUUID()}}};
    ui.write('budget','budget',budgetDraft.body,'登记这份额度申请');
  });
  document.addEventListener('question-ui-state',update);update();
})();