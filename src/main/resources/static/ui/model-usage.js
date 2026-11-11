'use strict';
(() => {
  const $=id=>document.getElementById(id), owner=document.querySelector('meta[name="configuration-owner"]').content;
  const csrf=document.querySelector('meta[name="csrf-token"]').content;
  const params=new URLSearchParams(window.location.search),filter={planId:params.get('planId')||null,taskId:params.get('taskId')||null};
  const copy={
    en:{title:'Model requests',refresh:'Refresh',caption:'Latest 100 recorded requests. Estimates are not provider invoices; missing data is unknown.',loading:'Loading…',empty:'No recorded requests. This does not mean no calls were made.',failed:'Could not load model usage.',request:'Task and time',model:'Provider and model',tokens:'Tokens',cost:'Estimated cost',input:'Input',cached:'Cached input',output:'Output',unknown:'Unknown',subscription:'ChatGPT subscription · no API bill',unsupported:'Pricing not supported for this request',pending:'Waiting for price or complete usage',started:'Outcome not yet confirmed',summary:'Context summary',task:'Task',plan:'Plan',worker:'Worker'},
    zh:{title:'模型调用记录',refresh:'刷新',caption:'最近 100 次已记录的调用。费用是估算，非服务商账单；缺失信息显示为未知。',loading:'正在读取…',empty:'暂无已记录的调用；这不代表没有发生调用。',failed:'无法读取模型用量。',request:'任务与时间',model:'服务商与模型',tokens:'Token 用量',cost:'估算费用',input:'输入',cached:'其中缓存',output:'输出',unknown:'未知',subscription:'ChatGPT 订阅 · 不计算 API 账单',unsupported:'此请求的计价类型尚不支持',pending:'等待适用价格或完整用量',started:'结果尚未确认',summary:'上下文摘要',task:'任务',plan:'计划',worker:'Worker'},
    ja:{title:'モデル呼び出し',refresh:'更新',caption:'記録された直近100件。費用は推定であり、提供元の請求額ではありません。不明な値は不明と表示します。',loading:'読み込み中…',empty:'記録された呼び出しはありません。呼び出しがなかったことを意味しません。',failed:'モデル使用量を読み込めません。',request:'タスクと時刻',model:'提供元とモデル',tokens:'トークン',cost:'推定費用',input:'入力',cached:'キャッシュ入力',output:'出力',unknown:'不明',subscription:'ChatGPT サブスクリプション・API請求額なし',unsupported:'このリクエストの料金計算は未対応です',pending:'適用価格または完全な使用量を待機中',started:'結果未確認',summary:'コンテキスト要約',task:'タスク',plan:'計画',worker:'Worker'}
  };
  const words=()=>copy[window.aiwI18n?.locale]||copy.en;
  const summaryWords={
    en:{heading:'Recorded totals for this selection',calls:'Requests',known:'Known',missing:'requests missing this value',estimated:'Known estimates',unpriced:'requests without an estimate',coverage:'Totals include only recorded calls and may be incomplete.'},
    zh:{heading:'当前范围的已记录汇总',calls:'调用次数',known:'已知',missing:'次调用缺少该项',estimated:'已知估算',unpriced:'次调用没有估算',coverage:'汇总仅包含已记录的调用，可能不完整。'},
    ja:{heading:'この範囲の記録済み合計',calls:'呼び出し数',known:'既知',missing:'件でこの値が不明',estimated:'既知の推定額',unpriced:'件に推定額がありません',coverage:'合計は記録済みの呼び出しのみを含み、不完全な場合があります。'}
  };
  const sourceWords={
    en:{title:'Price source',pending:'Not yet observed',running:'Refreshing',observed:'Observed; some pricing conditions remain unknown',published:' · {count} rules published',failed:'Refresh failed; previous observation retained',repair:'Repeated source change; repair needed',unavailable:'Status unavailable',last:'Last observation'},
    zh:{title:'价格来源',pending:'尚未采集',running:'正在更新',observed:'已观察；部分计价条件仍未知',published:' · 已发布 {count} 条价格规则',failed:'更新失败，保留上次观察',repair:'来源结构持续变化，需要修复',unavailable:'无法读取状态',last:'上次观察'},
    ja:{title:'価格情報源',pending:'未取得',running:'更新中',observed:'観測済み・一部の料金条件は不明',published:' · {count} 件の料金ルールを公開済み',failed:'更新失敗・前回の観測を保持',repair:'情報源の構造変更が続いており、修復が必要です',unavailable:'状態を取得できません',last:'前回の観測'}
  };
  const node=(tag,text,cls)=>{const n=document.createElement(tag);if(text!=null)n.textContent=text;if(cls)n.className=cls;return n;};
  const repairWords={
    en:{WAITING_DISPATCH:'Repair awaiting dispatch',WAITING_WORKER:'Waiting for a compatible Worker',REPAIRING:'Collector repair in progress',AWAITING_VERIFICATION:'Candidate and page are ready for administrator review',VERIFYING:'Running once against the approved page snapshot',INSTALLING:'Installing the administrator-approved collector',INSTALLED:'Administrator-approved collector installed',VERIFICATION_FAILED:'Candidate run failed; explicit approval is required to retry',INCOMPLETE:'Repair incomplete',STOPPED:'Repair stopped',ATTEMPT_LIMIT:'Repair attempt limit reached',STALE:'Repair inputs superseded'},
    zh:{WAITING_DISPATCH:'修复任务待派发',WAITING_WORKER:'等待兼容的 Worker',REPAIRING:'正在修复采集程序',AWAITING_VERIFICATION:'候选源码和页面快照待管理员审阅',VERIFYING:'正在按已批准的页面快照运行一次',INSTALLING:'正在安装管理员批准的采集程序',INSTALLED:'管理员批准的采集程序已安装',VERIFICATION_FAILED:'候选运行失败；重试前须重新明确批准',INCOMPLETE:'修复尚未完成',STOPPED:'修复已停止',ATTEMPT_LIMIT:'已达到修复尝试次数上限',STALE:'修复输入已失效'},
    ja:{WAITING_DISPATCH:'修復タスクの登録待ち',WAITING_WORKER:'対応する Worker を待機中',REPAIRING:'収集プログラムを修復中',AWAITING_VERIFICATION:'候補コードとページの管理者レビュー待ち',VERIFYING:'承認済みページで 1 回実行中',INSTALLING:'管理者が承認した収集プログラムをインストール中',INSTALLED:'管理者承認済みの収集プログラムをインストール済み',VERIFICATION_FAILED:'候補の実行に失敗・再試行には再承認が必要',INCOMPLETE:'修復未完了',STOPPED:'修復停止済み',ATTEMPT_LIMIT:'修復試行回数の上限に到達',STALE:'修復入力は失効済み'}
  };
  const reviewStageWords={
    en:{AWAITING_ADMIN:'Candidate and exact page snapshot await your review',APPROVED_BY_ADMIN:'Approved; waiting for its one-time run',REJECTED_BY_ADMIN:'Rejected by an administrator',REVIEW_AGAIN:'Previous run failed; review and explicitly approve before another run'},
    zh:{AWAITING_ADMIN:'候选源码和对应页面快照待你审阅',APPROVED_BY_ADMIN:'已批准，等待本次一次性运行',REJECTED_BY_ADMIN:'管理员已拒绝此候选',REVIEW_AGAIN:'上次候选运行失败；如需重跑，请重新审阅并明确批准'},
    ja:{AWAITING_ADMIN:'候補コードと対象ページの確認待ち',APPROVED_BY_ADMIN:'承認済み・今回限りの実行待ち',REJECTED_BY_ADMIN:'管理者が候補を却下',REVIEW_AGAIN:'前回の実行に失敗・再実行には確認と再承認が必要'}
  };
  const number=value=>value==null?words().unknown:Number.isSafeInteger(value)?value.toLocaleString(window.aiwI18n?.locale||'en'):words().unknown;
  const amount=(value,currency)=>Number.isSafeInteger(value)?(value/1000000).toLocaleString(window.aiwI18n?.locale||'en',{maximumFractionDigits:6})+' '+currency:null;
  let snapshot=null, generation=0, busy=false, sources=null, sourceError=false, sourceGeneration=0;
  function renderSources(){
    const w=sourceWords[window.aiwI18n?.locale]||sourceWords.en,host=$('model-price-status');
    if(sourceError){host.textContent=w.title+' · '+w.unavailable;return;}
    if(!sources||!sources.length){host.textContent=w.title+' · '+w.pending;return;}
    host.textContent=sources.map(row=>{
      const locale=window.aiwI18n?.locale||'en';
      const repair=(repairWords[locale]||repairWords.en)[row.repair_stage]
        ||(reviewStageWords[locale]||reviewStageWords.en)[row.repair_stage];
      const repairStatus=['VERIFYING','INSTALLING','INSTALLED','VERIFICATION_FAILED','AWAITING_ADMIN','APPROVED_BY_ADMIN','REJECTED_BY_ADMIN','REVIEW_AGAIN'].includes(row.repair_stage);
      const status=row.repair_needed||repairStatus?(repair||w.repair):({PENDING:w.pending,RUNNING:w.running,OBSERVED_INCOMPLETE:w.observed,FAILED:w.failed}[row.status]||w.unavailable);
      const last=row.last_success_ms?' · '+w.last+': '+new Date(row.last_success_ms).toLocaleString(window.aiwI18n?.locale||'en'):'';
      const published=Number.isSafeInteger(row.published_rule_count)&&row.published_rule_count>0?w.published.replace('{count}',number(row.published_rule_count)):'';
      return row.provider+' · '+status+published+last;
    }).join(' | ');
  }
  async function loadSources(){
    const current=++sourceGeneration;
    try{
      const response=await fetch('/api/r1/authority/execution-configurations/state?kind=model-price-status',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-CSRF-Token':csrf},body:'{}'});
      if(!response.ok)throw new Error('unavailable');const data=await response.json();
      if(data.protocol!=='aiw.model-price-status.v1'||!Array.isArray(data.sources))throw new Error('invalid');
      if(current===sourceGeneration){sources=data.sources;sourceError=false;renderSources();}
    }catch(error){if(current===sourceGeneration){sourceError=true;renderSources();}}
  }
  function render(){
    const t=words(),host=$('model-usage-records');$('model-usage-title').textContent=t.title;
    $('model-usage-caption').textContent=t.caption+(filter.planId?' · '+t.plan+': '+filter.planId:'')+(filter.taskId?' · '+t.task+': '+filter.taskId:'');$('model-usage-refresh').textContent=t.refresh;
    host.replaceChildren();if(!snapshot){host.append(node('p',busy?t.loading:t.empty,'empty'));return;}
    if(!snapshot.calls.length){host.append(node('p',t.empty,'empty'));return;}
    const s=summaryWords[window.aiwI18n?.locale]||summaryWords.en, totals=snapshot.totals;
    const overview=node('div',null,'observations-detail');overview.append(node('strong',s.heading),node('p',s.coverage,'muted'),node('p',s.calls+': '+number(totals.calls)));
    for(const [name,key,missing] of [[t.input,'known_input','missing_input'],[t.cached,'known_cached','missing_cached'],[t.output,'known_output','missing_output']])
      overview.append(node('p',name+' · '+s.known+': '+number(totals[key])+' ('+number(totals[missing])+' '+s.missing+')'));
    for(const currency of snapshot.currencies)overview.append(node('p',s.estimated+': '+(amount(currency.known_cost_micros,currency.currency)||t.unknown)));
    overview.append(node('p',number(totals.without_estimate)+' '+s.unpriced));host.append(overview);
    const wrap=node('div',null,'observations-table-wrap'),table=node('table',null,'observations-table'),head=node('thead'),hr=node('tr');
    for(const label of [t.request,t.model,t.tokens,t.cost]){const th=node('th',label);th.scope='col';hr.append(th);}head.append(hr);table.append(head);
    const body=node('tbody');for(const row of snapshot.calls){
      const tr=node('tr'),task=node('td'),model=node('td'),tokens=node('td'),cost=node('td');
      task.append(node('strong',row.task_id),node('small',new Date(row.started_ms).toLocaleString(window.aiwI18n?.locale||'en')),
        node('small',t.worker+': '+row.worker_id),node('small',row.purpose==='history-summary'?t.summary:t.task));
      if(row.plan_id)task.append(node('small',t.plan+': '+row.plan_id));
      model.append(node('strong',row.model),node('small',row.provider+' · '+row.protocol));
      tokens.append(node('strong',t.input+' '+number(row.input_tokens)),node('small',t.cached+' '+number(row.cached_tokens)),node('small',t.output+' '+number(row.output_tokens)));
      const estimate=amount(row.cost_micros,row.currency);
      const text=row.connection_kind==='CHATGPT'?t.subscription:row.price_scope!=='TEXT_ONLY'?t.unsupported:estimate||t.pending;
      cost.append(node('strong',text));if(row.state!=='COMPLETED')cost.append(node('small',t.started));
      if(row.snapshot_id)cost.append(node('small',row.snapshot_id+' / '+row.rule_id));
      tr.append(task,model,tokens,cost);body.append(tr);
    }
    table.append(body);wrap.append(table);host.append(wrap);
  }
  async function refresh(){
    if(busy||!owner)return;busy=true;const current=++generation;render();loadSources();
    try{
      const response=await fetch('/api/r1/authority/execution-configurations/state?kind=model-usage',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-CSRF-Token':csrf},body:JSON.stringify(filter)});
      if(!response.ok)throw new Error(words().failed);const data=await response.json();
      if(current!==generation)return;if(data.protocol!=='aiw.model-usage.v1'||data.ownerId!==owner)throw new Error(words().failed);
      snapshot=data;
    }catch(error){if(current===generation){snapshot=null;$('model-usage-records').replaceChildren(node('p',words().failed,'empty'));}}
    finally{if(current===generation){busy=false;if(snapshot)render();}}
  }
  $('model-usage-refresh').addEventListener('click',refresh);
  window.addEventListener('aiw:locale-changed',()=>{render();renderSources();});
  window.addEventListener('pagehide',()=>{++generation;++sourceGeneration;snapshot=null;sources=null;$('model-usage-records').replaceChildren();});
  window.addEventListener('pageshow',event=>{if(event.persisted){busy=false;refresh();}});
  render();renderSources();refresh();
})();
