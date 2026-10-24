'use strict';
(() => {
  const $=id=>document.getElementById(id),flag=value=>value===true||value===1||value==='true'||value==='1';
  const canOperate=document.querySelector('meta[name="question-can-operate"]').content==='true';
  const canApprove=document.querySelector('meta[name="question-can-approve"]').content==='true';
  const enforced=document.querySelector('meta[name="question-accounting-enforced"]').content==='true';
  const safe=(value,min=0)=>Number.isSafeInteger(value)&&value>=min,copy=value=>structuredClone(value);
  const states={OPEN:'待回答',ANSWERED:'已回答 · 等待推进',APPLIED:'回答已生效',SUPERSEDED:'已失效'};
  const dimensions={TASKS:'任务数（次）',TIME_MS:'执行时间（毫秒）',COST_MICROS:'费用（微单位）',LOOP_BACKJUMPS:'循环回跳（次）'};
  const drafts=new Map();
  let data=null,telegram=null,ready=false,reading=false,busy=false,pending=null,sequence=0,selected=null,confirmation=null;
  function node(tag,text,className) { const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(className)n.className=className;return n; }
  function message(text,tone='info') { $('question-status').textContent=text;$('question-status').dataset.tone=tone;$('question-status').hidden=!text; }
  function readout(values) { const dl=node('dl',undefined,'question-readout');for(const [key,value] of values){dl.append(node('dt',key),node('dd',value??'未记录'));}return dl; }
  function number(value) { return safe(value)?String(value):'超出页面可精确显示范围'; }
  function showTab(name,focus=false) {
    document.querySelectorAll('[data-question-tab]').forEach(tab=>{const active=tab.dataset.questionTab===name;tab.setAttribute('aria-selected',String(active));tab.tabIndex=active?0:-1;if(active&&focus)tab.focus();});
    document.querySelectorAll('[data-question-panel]').forEach(panel=>{panel.hidden=panel.dataset.questionPanel!==name;});
  }
  const tabs=[...document.querySelectorAll('[data-question-tab]')];
  tabs.forEach((tab,index)=>{tab.addEventListener('click',()=>showTab(tab.dataset.questionTab));tab.addEventListener('keydown',event=>{
    const next=event.key==='ArrowRight'?(index+1)%tabs.length:event.key==='ArrowLeft'?(index+tabs.length-1)%tabs.length:event.key==='Home'?0:event.key==='End'?tabs.length-1:-1;
    if(next>=0){event.preventDefault();showTab(tabs[next].dataset.questionTab,true);}
  });});
  async function request(route,body,write=false) {
    let response,result;
    try { response=await fetch('/api/r1/authority/questions/'+route,{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-Token':document.querySelector('meta[name="csrf-token"]').content},body:JSON.stringify(body)});result=await response.json(); }
    catch(_){const error=new Error(write?'连接中断或响应无法读取，操作结果未知。请先刷新原记录核对。':'状态未能读取，请稍后重试。');error.uncertain=write;throw error;}
    if(!response.ok){const error=new Error(response.status===401||response.status===403?'当前身份或角色无权执行此操作，请重新登录并核对权限。':response.status===409?'问题、回答或计划状态已变化，请刷新并重新核对。':response.status===422?'填写内容未被接纳，请检查回答、范围及数字格式。':'服务暂时无法确认结果，请刷新原记录核对。');error.uncertain=write&&response.status>=500;throw error;}
    return result;
  }
  const writable=()=>ready&&!reading&&!busy&&!pending;
  function sync() {
    $('question-plan').disabled=busy||Boolean(pending)||Boolean(confirmation);
    $('question-refresh').disabled=busy||!$('question-plan').value;
    $('question-read-state').textContent=reading?'正在读取…':ready?'已读取当前计划':$('question-plan').value?'状态尚未确认，操作已暂停':'选择计划后读取';
    document.querySelectorAll('[data-answer-fields]').forEach(fields=>{fields.disabled=!writable()||fields.dataset.stale==='true';});
    document.querySelectorAll('[data-send-question]').forEach(button=>{const q=data?.questions.find(item=>item.questionId===button.dataset.sendQuestion);button.disabled=!writable()||!q||Boolean(sendBlock(q));});
    document.querySelectorAll('[data-adopt-question]').forEach(button=>{button.disabled=!writable();});
    $('question-uncertain').hidden=!pending;
    if(pending){$('question-uncertain-text').textContent=pending.label+'的结果尚未确认。当前计划已锁定，请刷新并核对正式记录；未查到记录不代表操作失败。'+(pending.checked?'已读取记录，请核对后决定是否继续。':'');}
    $('question-check').disabled=busy||reading||!pending;
    $('question-retry').hidden=!pending||!['reply','budget'].includes(pending.kind);
    $('question-retry').disabled=busy||reading||!ready||!pending?.checked;
    $('question-release').disabled=busy||reading||!ready||!pending?.checked;
    document.dispatchEvent(new Event('question-ui-state'));
  }
  function sendBlock(q) {
    if(!telegram)return '通知状态尚未读取，先到通知分区核对。';
    if(telegram.deliveries.length>=100)return '送达记录不完整，无法安全判断是否已有消息，暂不能新发送。';
    if(!telegram.bindings.some(b=>flag(b.enabled)))return '尚未绑定有效私聊，可在 Telegram 通知分区设置。';
    const existing=telegram.deliveries.filter(d=>d.question_id===q.questionId);
    if(existing.some(d=>['READY','SENDING','UNKNOWN'].includes(d.state)||d.state==='DELIVERED'&&Number(d.expires_ms)>Date.now()))return '已有待发送、发送中、结果未知或仍有效的送达，请先核对原消息。';
    return '';
  }
  function renderSummary() {
    $('question-open-count').textContent=data?data.questions.filter(q=>q.state==='OPEN').length:'—';
    $('question-waiting-count').textContent=data?data.questions.filter(q=>q.state==='ANSWERED').length:'—';
    $('question-task-count').textContent=data?data.tasks.length:'—';
  }
  function renderInbox() {
    const host=$('question-list'),detail=$('question-detail');host.replaceChildren();
    if(!data){host.append(node('p',$('question-plan').value?'当前计划尚未成功读取。':'先选择一个计划，再查看需要处理的问题。','empty'));detail.replaceChildren(node('p','在问题列表中选择一项，阅读详情并回答。','empty'));return;}
    const filter=$('question-filter').value,search=$('question-search').value.trim().toLowerCase();
    const questions=data.questions.filter(q=>(filter==='all'||filter==='open'&&q.state==='OPEN'||filter==='waiting'&&q.state==='ANSWERED'||filter==='history'&&['APPLIED','SUPERSEDED'].includes(q.state))&&[q.question,q.source.taskId,q.questionId].join(' ').toLowerCase().includes(search));
    if(!questions.some(q=>q.questionId===selected))selected=questions[0]?.questionId||null;
    for(const q of questions){const button=node('button',undefined,'question-item');button.type='button';button.setAttribute('aria-current',String(q.questionId===selected));button.append(node('span',states[q.state]||'状态待核对','pill '+(q.state==='OPEN'?'warn':'neutral')),node('strong',q.question.length>110?q.question.slice(0,110)+'…':q.question),node('small',(q.kind==='AUTHORIZATION'?'授权申请':'工作问题')+(q.source.taskId?' · '+q.source.taskId:'')));button.addEventListener('click',()=>{selected=q.questionId;renderInbox();sync();});host.append(button);}
    if(!questions.length)host.append(node('p',search?'没有匹配的问题。':'此分类暂无问题。可切换范围查看已回答或处理记录。','empty'));
    const q=questions.find(item=>item.questionId===selected);if(q)renderQuestion(q);else detail.replaceChildren(node('p','当前没有选中的问题。','empty'));
  }
  function paragraph(host,title,text) { if(!text)return;host.append(node('h3',title),node('p',text,'question-text')); }
  function renderQuestion(q) {
    const host=$('question-detail');host.replaceChildren(node('span',states[q.state]||'状态待核对','pill '+(q.state==='OPEN'?'warn':'neutral')),node('h2',q.question));
    if(q.kind==='AUTHORIZATION'&&q.authorization){const a=q.authorization;host.append(readout([['申请维度',dimensions[a.dimension]||'不支持的维度'],['增加量',number(a.amount)],['累计上限',number(a.cumulativeCeiling)],...(a.loopId?[['循环',a.loopId]]:[])]));host.append(node('p','批准只针对这份具体申请，不授予其它动作权限。','help'));}
    else {paragraph(host,'必要上下文',q.context);paragraph(host,'回答要求',q.answerRequirements);}
    if(q.answer){paragraph(host,'已记录的原始回答',q.kind==='AUTHORIZATION'?({APPROVE:'批准此申请',REJECT:'拒绝此申请'})[q.answer.rawAnswer]||q.answer.rawAnswer:q.answer.rawAnswer);host.append(node('p',(q.answer.delegated?'秘书提交':'人类提交')+' · '+(({CONSOLE:'控制台',TELEGRAM:'Telegram 私聊',PERSONAL:'个人秘书'})[q.answer.channel]||q.answer.channel||'来源未记录')+' · '+q.answer.actor,'help'));}
    if(q.blockedReason){const reasons={SOURCE_CHANGED:'问题的原始依据已变化，请核对来源。',WAITING_GRAPH_CONTROL_OR_PREREQUISITES:'回答已记录，正在等待图计划控制或前置条件。',WAITING_CONTROL_BUDGET_OR_QUIESCENCE:'回答已记录，正在等待人工控制、严格模式额度或实际停止确认。',CANCEL_REQUESTED:'计划已请求取消，此问题不再接收新回答。'};paragraph(host,'为什么还没有继续',reasons[q.blockedReason]||'当前仍有前置条件未满足，请核对任务与人工控制。');}
    if(q.successorTaskId)paragraph(host,'继续执行的任务',q.successorTaskId);
    const source=node('details',undefined,'question-disclosure');source.append(node('summary','查看问题来源与版本'),readout([['问题编号',q.questionId],['来源',q.source.kind==='WORKFLOW_GRAPH_EXECUTION'?'图计划执行':'计划任务'],['源任务',q.source.taskId||q.source.executionId||'计划范围申请'],['问题版本',number(q.requestVersion)],['回答版本',number(q.answerRevision)]]));host.append(source);
    const permission=q.kind==='AUTHORIZATION'?canApprove:canOperate;
    const editable=['OPEN','ANSWERED'].includes(q.state)&&(!q.answer||q.answer.delegated);
    if(editable&&permission)renderAnswer(host,q);
    else host.append(node('p',!permission?'当前角色可查看此问题，提交'+(q.kind==='AUTHORIZATION'?'授权决定需要审批角色。':'回答需要操作角色。'):q.state==='SUPERSEDED'?'此问题已失效，请查看当前工作记录。':q.state==='APPLIED'?'回答已经生效，不能被晚到消息改写。':'原始人类回答已记录，当前不能改写。','help'));
    if(q.state==='OPEN'&&permission){const controls=node('div',undefined,'question-actions'),send=node('button','发送到绑定私聊','btn ghost');send.type='button';send.dataset.sendQuestion=q.questionId;send.addEventListener('click',()=>{if(!writable()||sendBlock(q))return;write('send','telegram',{planId:$('question-plan').value,operation:'send',binding:null,questionId:q.questionId},'发送此问题到私聊');});const settings=node('button','通知设置','btn ghost');settings.type='button';settings.addEventListener('click',()=>showTab('notifications'));controls.append(send,settings);host.append(controls);const reason=sendBlock(q);if(reason)host.append(node('p',reason,'help'));}
  }
  function draftFor(q) { const key=$('question-plan').value+':'+q.questionId;if(!drafts.has(key))drafts.set(key,{question:copy(q),raw:'',reason:'',sent:null});return drafts.get(key); }
  function renderAnswer(host,q) {
    const draft=draftFor(q),fixed=draft.question,stale=fixed.requestVersion!==q.requestVersion||fixed.answerRevision!==q.answerRevision;
    const precise=safe(fixed.requestVersion,1)&&safe(fixed.answerRevision)&&(!q.authorization||safe(q.authorization.amount,1)&&safe(q.authorization.cumulativeCeiling,1));
    host.append(node('h3',q.answer?'明确覆盖秘书原答':'你的回答'));
    if(stale){host.append(node('p','问题或回答版本已变化。原草稿保留，请先重新核对当前问题。','help'));const adopt=node('button','我已核对，采用当前问题版本','btn ghost');adopt.type='button';adopt.dataset.adoptQuestion=q.questionId;adopt.addEventListener('click',()=>{if(!writable())return;draft.question=copy(q);draft.sent=null;renderInbox();sync();});host.append(adopt);}
    if(!precise)host.append(node('p','版本或申请数值超出页面可精确处理范围，当前不可提交。','help'));
    const form=node('form'),fields=node('fieldset');fields.dataset.answerFields='true';fields.dataset.stale=String(stale||!precise);
    if(q.kind!=='AUTHORIZATION'){
      if(q.options?.length){const choices=node('div',undefined,'question-actions');for(const option of q.options){const button=node('button',option,'btn ghost');button.type='button';button.addEventListener('click',()=>{draft.raw=option;textarea.value=option;textarea.focus();});choices.append(button);}fields.append(choices);}
      const label=node('label','填写原始回答','field'),textarea=node('textarea');textarea.required=true;textarea.maxLength=8000;textarea.rows=5;textarea.value=draft.raw;textarea.placeholder='直接写你的决定、补充信息或具体要求。';textarea.addEventListener('input',()=>{draft.raw=textarea.value;});label.append(textarea);fields.append(label);
    }
    if(q.answer){const label=node('label','覆盖原因（必填）','field'),reason=node('textarea');reason.required=true;reason.maxLength=2000;reason.rows=2;reason.value=draft.reason;reason.addEventListener('input',()=>{draft.reason=reason.value;});label.append(reason);fields.append(label);}
    const submit=raw=>{
      if(!writable()||stale||!precise||!form.reportValidity())return;
      const content={questionId:fixed.questionId,requestVersion:fixed.requestVersion,rawAnswer:raw,expectedAnswerRevision:fixed.answerRevision,overrideReason:q.answer?draft.reason:null};
      const fingerprint=JSON.stringify(content);if(!draft.sent||draft.sent.fingerprint!==fingerprint)draft.sent={fingerprint,body:{...content,messageId:crypto.randomUUID()}};
      write('reply','reply',draft.sent.body,q.answer?'覆盖秘书回答':'提交此问题的回答');
    };
    const actions=node('div',undefined,'question-actions');
    if(q.kind==='AUTHORIZATION'){for(const [raw,text] of [['APPROVE','批准此申请'],['REJECT','拒绝此申请']]){const button=node('button',text,'btn '+(raw==='APPROVE'?'primary':'ghost'));button.type='button';button.addEventListener('click',()=>submit(raw));actions.append(button);}}
    else {const button=node('button',q.answer?'确认覆盖并提交':'提交回答','btn primary');button.type='submit';actions.append(button);form.addEventListener('submit',event=>{event.preventDefault();submit(draft.raw);});}
    fields.append(actions);form.append(fields);host.append(form,node('p','回答只保存这项问题的决定；实际继续工作仍受当前执行范围和停止状态约束。','help'));
  }
  function reconcile() {
    if(!pending)return;
    pending.checked=['bind','unbind','send'].includes(pending.kind)?Boolean(telegram):true;
    let found;
    if(pending.kind==='reply')found=data.questions.find(q=>q.questionId===pending.body.questionId&&q.answer?.messageId===pending.body.messageId&&q.answer.rawAnswer===pending.body.rawAnswer);
    if(pending.kind==='budget')found=data.questions.find(q=>q.authorization?.requestId===pending.body.authorization.requestId&&q.authorization.amount===pending.body.authorization.amount&&q.authorization.cumulativeCeiling===pending.body.authorization.cumulativeCeiling&&q.authorization.dimension===pending.body.authorization.dimension&&q.authorization.loopId===pending.body.authorization.loopId);
    if(found){pending=null;message('已查到原请求的正式记录。不会重新生成请求或重复提交。');}
  }
  async function refresh() {
    const planId=$('question-plan').value,token=++sequence;if(!planId)return false;
    reading=true;ready=false;sync();
    const results=await Promise.allSettled([request('progress',{planId}),request('telegram',{planId,operation:'read',binding:null,questionId:null})]);
    if(token!==sequence||planId!==$('question-plan').value)return false;
    reading=false;
    if(results[0].status==='rejected'){telegram=null;sync();throw results[0].reason;}
    data=results[0].value;telegram=results[1].status==='fulfilled'?results[1].value:null;
    ready=safe(data.policy.revision,1);
    if(ready)reconcile();else message('计划版本超出页面可精确处理范围，当前只可查看。','error');
    render();if(!telegram)message('问题状态已读取，但通知状态读取失败。通知操作已暂停，可稍后刷新。','warn');return true;
  }
  function render() {renderSummary();renderInbox();sync();}
  async function perform(op) {
    if(busy||reading||!ready||op.planId!==$('question-plan').value)return;
    busy=true;sync();
    try {
      const result=await request(op.route,op.body,true);pending=null;
      if(op.kind==='reply'&&result.questionId){data.questions=data.questions.map(q=>q.questionId===result.questionId?result:q);drafts.delete(op.planId+':'+result.questionId);}
      message(op.label+'已记录。'+(op.kind==='control'?'停止和推进以实际任务状态为准。':''));
      try {await refresh();if(!ready)message(op.label+'已记录，但当前状态尚未确认，请刷新核对。','warn');}
      catch(_){message(op.label+'已记录，但后续刷新失败。请刷新核对，不要重复提交。','warn');}
    } catch(error) {if(error.uncertain)pending={...op,checked:false};else ready=false;message(error.message,'error');}
    finally {busy=false;render();}
  }
  function write(kind,route,body,label) {if(!writable())return;return perform({kind,route,body:copy(body),label,planId:$('question-plan').value});}
  function confirm(kind,route,body,title,description,target) {
    if(!writable())return;
    confirmation={kind,route,body:copy(body),label:title,planId:$('question-plan').value};
    $('question-confirm-title').textContent=title;$('question-confirm-description').textContent=description;$('question-confirm-target').textContent=target;
    $('question-confirm-dialog').showModal();sync();
  }
  $('question-confirm-dismiss').addEventListener('click',()=>$('question-confirm-dialog').close());
  $('question-confirm-dialog').addEventListener('close',()=>{confirmation=null;sync();});
  $('question-confirm-submit').addEventListener('click',()=>{const op=confirmation;$('question-confirm-dialog').close();if(op&&writable())perform(op);});
  function readCurrent() {return refresh().catch(error=>message(error.message,'error'));}
  $('question-refresh').addEventListener('click',readCurrent);$('question-check').addEventListener('click',readCurrent);
  $('question-retry').addEventListener('click',()=>{if(pending?.checked&&['reply','budget'].includes(pending.kind))perform(pending);});
  $('question-release').addEventListener('click',()=>{if(!pending?.checked||!ready||busy||reading)return;pending=null;message('已结束此次人工核对。请按当前正式状态继续，不会自动重放原操作。','warn');render();});
  $('question-plan').addEventListener('change',()=>{++sequence;data=null;telegram=null;ready=false;reading=false;selected=null;message('');render();if($('question-plan').value)readCurrent();});
  $('question-filter').addEventListener('change',()=>{renderInbox();sync();});$('question-search').addEventListener('input',()=>{renderInbox();sync();});
  function openQuestion(id) {if(!data?.questions.some(q=>q.questionId===id))return;selected=id;$('question-filter').value='all';$('question-search').value='';showTab('inbox');renderInbox();sync();}
  window.QuestionUI=Object.freeze({$,node,flag,safe,number,readout,message,dimensions,canOperate,canApprove,enforced,writable,write,confirm,showTab,openQuestion,get data(){return data;},get telegram(){return telegram;},get pending(){return pending;},get busy(){return busy;},get reading(){return reading;}});
  render();
})();
