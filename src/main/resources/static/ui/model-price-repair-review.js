(() => {
  const host=document.getElementById('model-price-repair-review');
  if(!host)return;
  const csrf=document.querySelector('meta[name="csrf-token"]')?.content||'';
  const words={
    en:{title:'Collector repair review',caption:'Review the exact submitted code and the latest official page fetched for its URL before approving.',loading:'Loading review material…',empty:'No collector candidate is waiting for review.',failed:'Review material is unavailable. Refresh the page and try again.',candidate:'Candidate collector source',checks:'Submitted check source',page:'Latest official page snapshot',summary:'Worker summary',source:'Official source URL',observed:'Fetched at',candidateHash:'Candidate SHA-256',pageHash:'Page SHA-256',previous:'Previous decision or run',approveCheck:'I reviewed this exact code and the latest official page fetched for its URL, and approve one validation run. There is no OS sandbox: the code runs as the Plane process account and may access that account’s files and network. If validation succeeds, it installs this collector for future scheduled collections and publishes its observation.',approve:'Approve validation run',reject:'Reject candidate',submitting:'Saving decision…',approved:'Approval recorded. This candidate may validate once against the page snapshot reviewed; success installs it for future scheduled collections.',rejected:'Candidate rejected. No candidate code was run.',stale:'The candidate or latest page changed. Review the refreshed material before deciding.',decisionFailed:'Could not save the decision. Refresh and review the current material.'},
    zh:{title:'采集器修复审阅',caption:'批准前先审阅候选源码，以及 Plane 刚从候选提供的官方 URL 获取的最新页面。',loading:'正在读取审阅材料…',empty:'当前没有等待审阅的采集器候选。',failed:'暂时无法读取审阅材料。请刷新页面后重试。',candidate:'候选采集器源码',checks:'Worker 提交的检查源码',page:'最新官方页面快照',summary:'Worker 说明',source:'官方页面 URL',observed:'Plane 获取时间',candidateHash:'候选 SHA-256',pageHash:'页面 SHA-256',previous:'上次决定或运行结果',approveCheck:'我已审阅这份源码和 Plane 刚获取的最新页面，并批准本次验证运行一次。此执行没有 OS 沙箱：代码将以 Plane 进程账户权限运行，可访问该账户有权访问的文件和网络。验证通过后会安装该采集器供后续定时采集使用，并发布本次观察结果。',approve:'批准本次验证运行',reject:'拒绝候选',submitting:'正在保存决定…',approved:'批准已记录。候选只会针对审阅时看到的页面验证一次；成功后会安装并用于后续定时采集。',rejected:'已拒绝候选。没有运行候选代码。',stale:'候选或最新页面已变化。请审阅刷新后的材料再决定。',decisionFailed:'未能保存决定。请刷新并审阅当前材料。'},
    ja:{title:'収集プログラムの修復レビュー',caption:'承認前に、提出されたコードと、その URL から Plane が取得した最新の公式ページを確認してください。',loading:'レビュー資料を読み込み中…',empty:'レビュー待ちの候補はありません。',failed:'レビュー資料を取得できません。ページを更新してください。',candidate:'候補の収集プログラム',checks:'提出された確認用コード',page:'最新の公式ページ',summary:'Worker の説明',source:'公式ページ URL',observed:'Plane の取得時刻',candidateHash:'候補 SHA-256',pageHash:'ページ SHA-256',previous:'前回の判断または実行結果',approveCheck:'このコードと Plane が取得した最新ページを確認し、検証のための 1 回の実行を承認します。OS サンドボックスはありません。コードは Plane プロセスのアカウント権限で動作し、そのアカウントがアクセスできるファイルとネットワークを使用できます。検証成功後、この収集プログラムは今後の定期収集に使われ、観測結果が公開されます。',approve:'検証実行を承認',reject:'候補を却下',submitting:'判断を保存中…',approved:'承認を記録しました。候補はレビュー時に確認したページで 1 回だけ検証され、成功後は今後の定期収集に使われます。',rejected:'候補を却下しました。候補コードは実行されていません。',stale:'候補または最新ページが変更されました。更新後の資料を確認してから判断してください。',decisionFailed:'判断を保存できませんでした。更新して現在の資料を確認してください。'}
  };
  let generation=0;
  const locale=()=>window.aiwI18n?.locale||'en';
  const text=(tag,value,cls)=>{const element=document.createElement(tag);element.textContent=value;if(cls)element.className=cls;return element;};
  const wordsNow=()=>words[locale()]||words.en;
  function block(label,value,open){
    const details=document.createElement('details');details.open=open;
    const summary=text('summary',label);const content=text('pre',value||'');
    details.append(summary,content);return details;
  }
  function renderItem(item,w){
    const card=document.createElement('article');card.className='card';
    card.append(text('h3',item.provider),text('p',w.summary+': '+item.summary,'help'));
    card.append(text('p',w.source+': '+item.sourceUrl,'muted'));
    card.append(text('p',w.observed+': '+new Date(item.pageObservedMs).toLocaleString(locale())));
    card.append(text('p',w.candidateHash+': '+item.candidateSha256,'muted'));
    card.append(text('p',w.pageHash+': '+item.pageSha256,'muted'));
    if(item.previousFailure)card.append(text('p',w.previous+': '+item.previousFailure,'callout warn-callout'));
    card.append(block(w.candidate,item.candidateSource,true),block(w.checks,item.checksSource,false),block(w.page,item.pageContent,true));
    const label=document.createElement('label');const check=document.createElement('input');check.type='checkbox';
    const approveText=document.createElement('span');approveText.textContent=w.approveCheck;label.append(check,approveText);
    const approve=document.createElement('button');approve.type='button';approve.className='btn primary';approve.textContent=w.approve;approve.disabled=true;
    const reject=document.createElement('button');reject.type='button';reject.className='btn ghost';reject.textContent=w.reject;
    const actions=document.createElement('div');actions.className='observations-actions';actions.append(approve,reject);
    const feedback=text('p','', 'muted');
    check.addEventListener('change',()=>{approve.disabled=!check.checked;});
    approve.addEventListener('click',()=>submit(item,'APPROVE',approve,reject,check,feedback,w));
    reject.addEventListener('click',()=>submit(item,'REJECT',approve,reject,check,feedback,w));
    card.append(label,actions,feedback);return card;
  }
  async function submit(item,decision,approve,reject,check,feedback,w){
    approve.disabled=true;reject.disabled=true;feedback.textContent=w.submitting;
    try{
      const response=await fetch('/api/r1/authority/execution-configurations/price-repair-decision',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-CSRF-Token':csrf},body:JSON.stringify({materialId:item.materialId,candidateSha256:item.candidateSha256,pageSha256:item.pageSha256,decision})});
      if(!response.ok){const message=response.status===409?w.stale:w.decisionFailed;feedback.textContent=message;document.getElementById('model-price-review-result').textContent=message;await load();return;}
      feedback.textContent=decision==='APPROVE'?w.approved:w.rejected;
      document.getElementById('model-price-review-result').textContent=feedback.textContent;
      document.getElementById('model-usage-refresh')?.click();
    }catch(error){feedback.textContent=w.decisionFailed;approve.disabled=!check.checked;reject.disabled=false;}
  }
  async function load(){
    const current=++generation;const w=wordsNow();
    document.getElementById('model-price-review-title').textContent=w.title;
    document.getElementById('model-price-review-caption').textContent=w.caption;
    host.setAttribute('aria-busy','true');host.replaceChildren(text('p',w.loading,'empty'));
    try{
      const response=await fetch('/api/r1/authority/execution-configurations/state?kind=model-price-repair-review',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-CSRF-Token':csrf},body:'{}'});
      if(!response.ok)throw new Error('unavailable');const data=await response.json();
      if(data.protocol!=='aiw.model-price-repair-review.v1'||!Array.isArray(data.candidates))throw new Error('invalid');
      if(current!==generation)return;host.replaceChildren();
      if(!data.candidates.length)host.append(text('p',w.empty,'empty'));
      else data.candidates.forEach(item=>host.append(renderItem(item,w)));
    }catch(error){if(current===generation)host.replaceChildren(text('p',w.failed,'empty'));}
    finally{if(current===generation)host.setAttribute('aria-busy','false');}
  }
  window.addEventListener('aiw:locale-changed',load);
  window.addEventListener('pagehide',()=>{generation++;host.replaceChildren();});
  document.getElementById('model-usage-refresh')?.addEventListener('click',load);
  load();
})();
