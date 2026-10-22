'use strict';
const status = document.getElementById('question-status');
const plan = document.getElementById('question-plan');
const canOperate=document.querySelector('meta[name="question-can-operate"]').content==='true';
const canApprove=document.querySelector('meta[name="question-can-approve"]').content==='true';
document.querySelectorAll('[data-control]').forEach(button=>{button.disabled=!canOperate;});
document.querySelectorAll('#question-binding button,#question-unbind').forEach(button=>{button.disabled=!(canOperate||canApprove);});
document.querySelector('#question-budget button').disabled=!canApprove;
let revision = null;
async function action(route, body) {
  const response = await fetch('/api/r1/authority/questions/' + route, {method:'POST', headers:{'Content-Type':'application/json','X-CSRF-Token':document.querySelector('meta[name="csrf-token"]').content}, body:JSON.stringify(body)});
  const data = await response.json();
  if (!response.ok) throw new Error(data.code || '操作未确认，请刷新当前状态。');
  return data;
}
function run(work) { Promise.resolve().then(work).catch(error => {status.textContent = error.message;}); }
async function refresh() {
  if (!plan.value) return;
  const data = await action('progress', {planId:plan.value});
  revision = data.policy.revision;
  document.getElementById('question-progress').textContent = JSON.stringify({policyState:data.policy.revoked?'已取消':data.policy.paused?'已暂停':'可准入',budgets:data.budgets,tasks:data.tasks,stopSemantics:data.stopSemantics}, null, 2);
  const list = document.getElementById('question-list'); list.replaceChildren();
  for (const q of data.questions) {
    const card = document.createElement('article'); card.className = 'card section';
    const title = document.createElement('h2'); title.textContent = q.question;
    const details = document.createElement('pre'); details.textContent = JSON.stringify({questionId:q.questionId,source:q.source,state:q.state,context:q.context,options:q.options,answerRequirements:q.answerRequirements,authorization:q.authorization,originalAnswer:q.answer,successorTaskId:q.successorTaskId,blockedReason:q.blockedReason},null,2);
    card.append(title,details);
    if (['OPEN','ANSWERED'].includes(q.state) && (q.kind==='AUTHORIZATION'?canApprove:canOperate)) {
      const form = document.createElement('form'); const answer = document.createElement('textarea'); answer.required = true; answer.maxLength = 8000; answer.placeholder = q.kind==='AUTHORIZATION'?'APPROVE 或 REJECT':'原始回答';
      const reason = document.createElement('input'); reason.placeholder = '显式覆盖秘书回答时填写原因';
      const submit = document.createElement('button'); submit.textContent = '提交此版本的回答';
      form.append(answer,reason,submit);form.addEventListener('submit', event => {event.preventDefault();run(async()=>{await action('reply',{questionId:q.questionId,requestVersion:q.requestVersion,rawAnswer:answer.value,messageId:crypto.randomUUID(),expectedAnswerRevision:q.answerRevision,overrideReason:reason.value||null});status.textContent='回答已记录；推进仍需当前控制、预算及停止确认允许。';await refresh();});});card.append(form);
    }
    if(q.state==='OPEN'&&(q.kind==='AUTHORIZATION'?canApprove:canOperate)) {const send=document.createElement('button');send.textContent='发送此问题到绑定私聊';send.addEventListener('click',()=>run(async()=>{await action('telegram',{planId:plan.value,operation:'send',binding:null,questionId:q.questionId});await refresh();}));card.append(send);}
    list.append(card);
  }
  document.getElementById('question-telegram').textContent=JSON.stringify(await action('telegram',{planId:plan.value,operation:'read',binding:null,questionId:null}),null,2);
}
document.getElementById('question-refresh').addEventListener('click',()=>run(refresh));plan.addEventListener('change',()=>run(refresh));
document.querySelectorAll('[data-control]').forEach(button=>button.addEventListener('click',()=>run(async()=>{await action('control',{planId:plan.value,revision,action:button.dataset.control});await refresh();})));
document.getElementById('question-binding').addEventListener('submit',event=>{event.preventDefault();run(async()=>{await action('telegram',{planId:plan.value,operation:'bind',binding:Object.fromEntries(new FormData(event.target)),questionId:null});await refresh();});});
document.getElementById('question-unbind').addEventListener('click',()=>run(async()=>{await action('telegram',{planId:plan.value,operation:'unbind',binding:null,questionId:null});await refresh();}));
document.getElementById('question-budget').addEventListener('submit',event=>{event.preventDefault();run(async()=>{const f=Object.fromEntries(new FormData(event.target));await action('budget',{planId:plan.value,policyRevision:revision,question:f.question,authorization:{requestId:crypto.randomUUID(),dimension:f.dimension,loopId:f.dimension==='LOOP_BACKJUMPS'?f.loopId:null,amount:Number(f.amount),cumulativeCeiling:Number(f.cumulativeCeiling)}});await refresh();});});
run(refresh);
