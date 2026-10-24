'use strict';
(() => {
  const $=id=>document.getElementById(id);
  const owner=document.querySelector('meta[name="configuration-owner"]').content;
  const csrf=document.querySelector('meta[name="csrf-token"]').content;
  const names={SUCCEEDED:'已完成',FAILED:'失败',RUNNING:'执行中',PENDING:'等待中',LEASED:'已领取',CANCELLED:'已取消',ABANDONED:'已放弃',TIMED_OUT:'已超时',BLOCKED:'已阻塞',PASS:'通过',FAIL:'未通过',UNKNOWN:'未知'};
  const label=value=>value==null||value===''?'未记录':names[value]||value;
  const number=value=>value==null?'未知':Number.isSafeInteger(value)?value.toLocaleString('zh-CN'):'超出页面精确显示范围';
  let epoch=0,busy=false;
  function node(tag,text,className) {
    const item=document.createElement(tag);if(text!=null)item.textContent=text;if(className)item.className=className;return item;
  }
  function field(list,name,value) { list.append(node('dt',name),node('dd',value==null||value===''?'未记录':String(value))); }
  function flag(cell,text) { cell.append(node('span',text,'observations-flag')); }
  function detail(entry) {
    const box=node('details',null,'observations-detail');box.append(node('summary','配置与交付依据'));
    const facts=node('dl',null,'configuration-readout');
    field(facts,'事务',entry.planId);field(facts,'任务',entry.taskId);field(facts,'尝试',entry.attemptId);
    field(facts,'固定配置',entry.configuration?entry.configuration.id+' / v'+entry.configuration.revision+' / '+entry.configuration.digest:null);
    field(facts,'模型配置',entry.profileId?entry.profileId+' / '+(entry.profileVersion||'版本未记录'):null);
    field(facts,'运行后端',entry.runtime);field(facts,'主技能',entry.primarySkill);
    field(facts,'失败分类',entry.failureCategory);field(facts,'结果版本',entry.resultRevision);
    field(facts,'发布引用',entry.publicationRef);field(facts,'正式交付摘要',entry.deliveryDigest);
    field(facts,'未运行检查数',entry.checksNotRunCount==null?'未提交正式交付':number(entry.checksNotRunCount));
    field(facts,'未决项数',entry.unresolvedCount==null?'未提交正式交付':number(entry.unresolvedCount));
    field(facts,'同结果再次读取',number(entry.repeatedReads)+' 次（不代表浪费）');box.append(facts);
    if(entry.deliveryDigest) {
      box.append(node('p','正式来源 '+number(entry.sourceCount)+' 项；本页列出 '+entry.sources.length+' 项。','muted'));
      const list=node('ul',null,'observations-sources');
      for(const source of entry.sources)list.append(node('li',[source.kind,source.id,source.revision,source.sha256].join(' / ')));
      box.append(list);
    }
    return box;
  }
  function render(snapshot) {
    const target=$('observations-records');target.replaceChildren();
    $('observations-updated').textContent='读取时间：'+new Date(snapshot.observedAt).toLocaleString('zh-CN')+' · '+snapshot.entries.length+' 条（最多 '+snapshot.limit+' 条）';
    if(!snapshot.entries.length) { target.append(node('p','当前账号暂无可展示的计量记录。此结果不代表没有执行。','empty'));return; }
    const wrap=node('div',null,'observations-table-wrap'),table=node('table',null,'observations-table');
    table.append(node('caption','每行对应一次尝试；展开可查看固定配置和交付来源。'));
    const head=node('thead'),heading=node('tr');
    for(const title of ['任务与方法','状态与上报结论','实际模型','累计 token','耗时与费用']) {
      const cell=node('th',title);cell.scope='col';heading.append(cell);
    }
    head.append(heading);table.append(head);const body=node('tbody');
    for(const entry of snapshot.entries) {
      const row=node('tr'),task=node('td'),state=node('td'),model=node('td'),tokens=node('td'),usage=node('td');
      const link=node('a',entry.title);link.href='/ui/tasks/'+encodeURIComponent(entry.taskId);task.append(link);
      task.append(node('small',entry.methodName?'工作方法：'+entry.methodName:'主技能：'+label(entry.primarySkill)+'（无固定方法名称）'));
      task.append(detail(entry));
      state.append(node('strong','尝试：'+label(entry.attemptState)),node('small','任务：'+label(entry.taskState)),node('small','上报结论：'+label(entry.reportedVerdict)));
      state.append(node('small','第 '+number(entry.attemptNo)+' 次尝试'));
      model.append(node('strong',label(entry.model)),node('small',label(entry.runtime)));
      tokens.append(node('strong','输入 '+number(entry.inputTokens)),node('small','其中缓存 '+number(entry.cachedTokens)),node('small','输出 '+number(entry.outputTokens)));
      if(entry.usageUnknown)flag(tokens,'用量标记：未知');
      else if(entry.startedAt==null)flag(tokens,'尚未开始计量');
      usage.append(node('strong',entry.elapsedMs==null?'耗时未知':number(entry.elapsedMs)+' 毫秒'));
      usage.append(node('small',entry.costMicros==null?'费用未知':number(entry.costMicros)+' 微单位'+(entry.priceUnit?' / '+entry.priceUnit:'（计价单位未记录）')));
      usage.append(node('small',entry.settled?'已结算记录':'尚未结算'));
      if(entry.timeUnknown)flag(usage,'耗时标记：未知（可能已有校正值）');
      row.append(task,state,model,tokens,usage);body.append(row);
    }
    table.append(body);wrap.append(table);target.append(wrap);
  }
  async function refresh() {
    if(busy)return;
    const sequence=++epoch;busy=true;$('observations-refresh').disabled=true;
    $('observations-feedback').hidden=true;$('observations-records').replaceChildren(node('p','正在读取已有记录…','empty'));
    $('observations-records').setAttribute('aria-busy','true');
    try {
      if(!owner)throw new Error('请先到个人授权页绑定当前登录身份。');
      const response=await fetch('/api/r1/authority/execution-configurations/state?kind=observations',{
        method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-CSRF-Token':csrf},body:'{}'
      });
      if(!response.ok)throw new Error(response.status===401?'登录已失效，请重新登录后打开此页。':response.status===403?'当前身份无权查看，请检查登录与操作角色。':'暂时无法读取记录（'+response.status+'），请稍后手动刷新。');
      const snapshot=await response.json();
      if(sequence!==epoch)return;
      if(snapshot.protocol!=='aiw.execution.observations.v1'||snapshot.ownerId!==owner||document.querySelector('meta[name="configuration-owner"]').content!==owner)throw new Error('记录所属身份或版本已变化，请重新打开页面。');
      render(snapshot);
    } catch(error) {
      if(sequence!==epoch)return;
      $('observations-records').replaceChildren(node('p','记录未载入。','empty'));
      $('observations-updated').textContent='当前没有有效快照';
      const feedback=$('observations-feedback');feedback.textContent=error.message;feedback.dataset.tone='error';feedback.hidden=false;
    } finally {
      if(sequence===epoch){busy=false;$('observations-refresh').disabled=false;$('observations-records').setAttribute('aria-busy','false');}
    }
  }
  // Invalidate pending requests and clear private rows when leaving or restoring a cached document.
  window.addEventListener('pagehide',()=>{++epoch;$('observations-records').replaceChildren();});
  window.addEventListener('pageshow',event=>{if(event.persisted){busy=false;refresh();}});
  $('observations-refresh').addEventListener('click',refresh);refresh();
})();
