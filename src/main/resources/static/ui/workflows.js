(() => {
  'use strict';
  const $ = s => document.querySelector(s), $$ = s => [...document.querySelectorAll(s)];
  const words = {
    'Library':['工作区','ライブラリ'], 'Simple task':['简单任务','単一タスク'], 'New workflow':['新建工作流','新規ワークフロー'],
    'After-sales example':['售后示例','アフターサービス例'], 'Saved workflows':['已保存工作流','保存済みワークフロー'],
    'Executions':['执行记录','実行履歴'], 'Refresh':['刷新','更新'], 'Workflow definition':['工作流定义','ワークフロー定義'],
    'Workflows':['工作流','ワークフロー'], 'Describe a simple task or run a reusable workflow.':['直接处理简单任务，或运行可复用的工作流。','単一タスクを依頼するか、ワークフローを実行します。'],
    'Text tasks use the same executor as workflow nodes. No Git repository is required.':['文本任务与工作流节点共用执行能力，无须 Git 仓库。','テキストタスクとワークフローは同じ実行機能を使います。Git リポジトリは不要です。'],
    'What should be done?':['需要做什么？','何を行いますか？'], 'Working location (optional)':['工作位置（可选）','作業場所（任意）'],
    'Run task':['执行任务','タスクを実行'], 'Name':['名称','名前'], 'Goal and completion conditions':['目标与完成条件','目標と完了条件'],
    'Edit steps':['编辑步骤','ステップを編集'], 'Add step':['添加步骤','ステップを追加'], 'Save workflow':['保存工作流','保存'],
    'Input and reference material':['输入与参考资料','入力と参考資料'], 'Start execution':['启动执行','実行を開始'],
    'Starting freezes this definition for this execution. Later edits do not change existing executions.':['每次执行保存独立的流程副本，后续编辑不影响已有执行。','実行時の定義を保存します。後の編集は既存の実行に影響しません。'],
    'Pause':['暂停','一時停止'], 'Continue':['继续','再開'], 'Retry this step':['重试本步骤','このステップを再試行'], 'Stop execution':['停止本次执行','実行を停止'],
    'Your answer':['你的回答','回答'], 'Answer and continue':['回答并继续','回答して続行'],
    'Worker execution details':['Worker 执行详情','Worker の実行詳細'], 'Execution timeline':['执行时间线','実行タイムライン'],
    'Task':['执行任务','タスク'], 'LLM decision':['LLM 判断','LLM 判断'], 'Ask user':['询问用户','ユーザーに質問'], 'Done':['结束','終了'],
    'Title':['步骤名称','ステップ名'], 'Type':['步骤类型','種類'], 'Instruction':['说明与完成条件','指示と完了条件'], 'Next step':['后续步骤','次のステップ'],
    'Choice':['选项','選択肢'], 'Add choice':['添加选项','選択肢を追加'], 'Remove':['移除','削除'],
    'No executions yet':['暂无执行记录','実行履歴はありません'], 'No saved workflows':['暂无已保存工作流','保存済みワークフローはありません'],
    'Workflow saved':['工作流已保存','保存しました'], 'Save changes before starting':['请先保存修改，再启动执行','変更を保存してから開始してください'],
    'Stop this execution? Existing results will be retained.':['停止本次执行？已有结果将保留。','この実行を停止しますか？既存の結果は保持されます。'],
    'Waiting for a compatible Worker':['等待兼容 Worker 接单','対応する Worker を待機中'], 'Waiting for user input':['等待用户补充','ユーザーの回答待ち'],
    'Request outcome is unknown. Check execution history before submitting again.':['请求结果尚未确认，请先检查执行记录，不要重复提交。','リクエストの結果が不明です。再送信する前に実行履歴を確認してください。'],
    'RUNNING':['执行中','実行中'], 'WAITING_USER':['待你回答','回答待ち'], 'PAUSED':['已暂停','一時停止中'], 'STOPPING':['等待 Worker 停止确认','Worker の停止確認待ち'],
    'STOPPED':['已停止','停止済み'], 'COMPLETED':['已完成','完了'], 'FAILED':['需要处理','対応が必要'], 'READY':['等待接单','待機中'],
    'CLAIMED':['已领取','取得済み'], 'WAITING_INPUT':['等待 Worker 问题答复','Worker の質問への回答待ち'], 'STARTED':['开始','開始'],
    'DISPATCHED':['已派发','割り当て済み'], 'QUESTION':['需要补充','質問'], 'ANSWER':['用户回答','ユーザーの回答'],
    'BRANCH':['分支判断','分岐判断'], 'RESULT':['步骤结果','ステップ結果'], 'STOP_REQUESTED':['已请求停止','停止要求済み'], 'RESUMED':['已继续','再開済み'],
    'LIMIT':['达到执行限制','実行上限に到達'], 'RETRY':['用户请求重试','ユーザーによる再試行'],
  };
  const t = value => {
    const locale = window.aiwI18n?.locale || document.documentElement.lang;
    return words[value]?.[locale?.startsWith('zh') ? 0 : locale?.startsWith('ja') ? 1 : -1] || value;
  };
  const status = message => { $('[data-wf-status]').textContent = message; };
  let definitions=[], editing=null, selectedRun=null, currentView=null, savedSpec='', busy=false, signature='';
  const newRequest = () => crypto.randomUUID();
  let taskRequest=newRequest(), startRequest=newRequest();
  async function api(path, data) {
    const response = await fetch('/api/r1/workflows'+path, {method:data===undefined?'GET':'POST', credentials:'same-origin',
      headers:{'Content-Type':'application/json','X-CSRF-Token':$('meta[name="csrf-token"]').content}, body:data===undefined?undefined:JSON.stringify(data)});
    if(!response.ok) {const detail=await response.json().catch(()=>({}));throw new Error(detail.error || `${response.status} ${response.statusText}`);}
    return response.json();
  }
  async function act(work) {
    if(busy)return;busy=true;status('');
    const buttons=$$('button');buttons.forEach(b=>b.disabled=true);
    try {await work();} catch(error) {status(error instanceof TypeError?t('Request outcome is unknown. Check execution history before submitting again.'):error.message);}
    finally {busy=false;buttons.forEach(b=>{if(b.isConnected)b.disabled=false;});if(currentView)controlButtons(currentView.run);}
  }
  function element(tag,text,className) {const e=document.createElement(tag);if(text!==undefined)e.textContent=text;if(className)e.className=className;return e;}
  function button(label,callback) {const b=element('button',t(label),'btn ghost');b.type='button';b.onclick=callback;return b;}
  function translate() {
    $$('[data-wf-label]').forEach(e=>e.textContent=t(e.dataset.wfLabel));
    $$('[data-wf-new], [data-wf-sample], [data-wf-refresh], [data-wf-add-node], [data-wf-save], [data-wf-start], [data-wf-control]').forEach(e=>{
      e.dataset.wfOriginal ||= e.textContent;e.textContent=t(e.dataset.wfOriginal);
    });
  }
  function panel(name) {['task','editor','run'].forEach(p=>$(`[data-wf-${p}]`).hidden=p!==name);}
  function singleSpec() {return {title:t('New workflow'),goal:'',entry:'task',nodes:[
    {key:'task',title:t('Task'),kind:'TASK',instruction:'',next:'end',branches:{}},
    {key:'end',title:t('Done'),kind:'END',instruction:'',next:null,branches:{}}]};}
  function showEditor(definition) {
    selectedRun=null;editing=structuredClone(definition || {id:null,version:0,spec:singleSpec()});
    $('[data-wf-title]').value=editing.spec.title;$('[data-wf-goal]').value=editing.spec.goal;
    $('[data-wf-input]').value='';startRequest=newRequest();savedSpec=definition?JSON.stringify(editing.spec):'';
    panel('editor');renderNodes();graph($('[data-wf-definition-graph]'),editing.spec);status('');
  }
  function field(label,input) {const wrapper=element('label',undefined,'field');wrapper.append(element('span',t(label)),input);return wrapper;}
  function input(value,change,max=8000) {const e=element('input');e.value=value||'';e.maxLength=max;e.oninput=()=>change(e.value);return e;}
  function target(value,change) {
    const select=element('select');select.append(new Option('—',''));
    editing.spec.nodes.forEach(n=>select.append(new Option(n.title,n.key)));select.value=value||'';
    select.onchange=()=>{change(select.value||null);redraw();};return select;
  }
  const redraw=()=>graph($('[data-wf-definition-graph]'),editing.spec);
  function renderNodes() {
    const list=$('[data-wf-nodes]');list.replaceChildren();
    for(const node of editing.spec.nodes) {
      const section=element('section',undefined,'wf-node-editor');section.id='wf-node-'+node.key;
      const row=element('div',undefined,'wf-node-fields'),kind=element('select');
      for(const [value,label] of [['TASK','Task'],['DECISION','LLM decision'],['ASK','Ask user'],['END','Done']])kind.append(new Option(t(label),value));
      kind.value=node.kind;kind.onchange=()=>{node.kind=kind.value;node.branches={};node.next=node.kind==='END'||node.kind==='DECISION'?null:'end';if(node.kind==='DECISION')node.branches={'yes':'end','no':'end'};renderNodes();redraw();};
      row.append(field('Title',input(node.title,v=>{node.title=v;redraw();},160)),field('Type',kind));section.append(row);
      if(node.kind!=='END') {
        const instruction=element('textarea');instruction.value=node.instruction;instruction.rows=3;instruction.maxLength=8000;instruction.oninput=()=>node.instruction=instruction.value;
        section.append(field('Instruction',instruction));
        if(node.kind==='DECISION'||node.kind==='ASK') {
          for(const [choice,next] of Object.entries(node.branches)) {
            const branch=element('div',undefined,'wf-node-fields');let currentChoice=choice;
            branch.append(field('Choice',input(choice,v=>{const destination=node.branches[currentChoice];delete node.branches[currentChoice];node.branches[v]=destination;currentChoice=v;redraw();},120)),field('Next step',target(next,v=>node.branches[currentChoice]=v)));
            branch.append(button('Remove',()=>{delete node.branches[currentChoice];renderNodes();redraw();}));section.append(branch);
          }
          section.append(button('Add choice',()=>{let key='option'+(Object.keys(node.branches).length+1);while(key in node.branches)key+='x';node.branches[key]='end';node.next=null;renderNodes();redraw();}));
        }
        if(node.kind==='TASK'||node.kind==='ASK'&&!Object.keys(node.branches).length)section.append(field('Next step',target(node.next,v=>node.next=v)));
      }
      section.append(button('Remove',()=>{editing.spec.nodes=editing.spec.nodes.filter(n=>n!==node);if(editing.spec.entry===node.key)editing.spec.entry=editing.spec.nodes[0]?.key;renderNodes();redraw();}));list.append(section);
    }
  }
  function graph(container,spec,run) {
    container.replaceChildren();if(!spec?.nodes.length)return;
    const ns='http://www.w3.org/2000/svg',svg=document.createElementNS(ns,'svg');
    const make=(tag,attrs)=>{const e=document.createElementNS(ns,tag);Object.entries(attrs).forEach(([k,v])=>e.setAttribute(k,v));return e;};
    const nodes=spec.nodes,positions=new Map(nodes.map((n,i)=>[n.key,{x:80+(i%2)*310,y:30+Math.floor(i/2)*135}]));
    svg.setAttribute('viewBox',`0 0 760 ${Math.ceil(nodes.length/2)*135+20}`);svg.setAttribute('width','760');svg.setAttribute('height',Math.ceil(nodes.length/2)*135+20);
    svg.setAttribute('role','img');svg.setAttribute('aria-label',spec.title);
    const marker=make('marker',{id:container.hasAttribute('data-wf-run-graph')?'wf-arrow-run':'wf-arrow-definition',viewBox:'0 0 10 10',refX:9,refY:5,markerWidth:6,markerHeight:6,orient:'auto'});
    marker.append(make('path',{d:'M 0 0 L 10 5 L 0 10 z'}));const defs=make('defs',{});defs.append(marker);svg.append(defs);
    const taken=new Set();let previous=null;
    for(const event of run?.events||[]){if(previous&&previous!==event.node)taken.add(previous+'\n'+event.node);previous=event.node;}
    for(const n of nodes) {
      const from=positions.get(n.key),edges=n.next?[[t('Next step'),n.next]]:Object.entries(n.branches);
      edges.forEach(([choice,key],i)=>{const to=positions.get(key);if(!to)return;
        const path=make('path',{d:`M ${from.x+110} ${from.y+60} C ${from.x+110+35*i} ${from.y+105}, ${to.x+110-35*i} ${to.y-45}, ${to.x+110} ${to.y}`,'marker-end':`url(#${marker.id})`,class:taken.has(n.key+'\n'+key)?'wf-taken':''});svg.append(path);
        if(n.branches&&Object.keys(n.branches).length){const label=make('text',{x:(from.x+to.x)/2+120,y:(from.y+to.y)/2+82+i*15});label.textContent=choice;svg.append(label);}
      });
    }
    const visited=new Set(run?.events.filter(e=>['RESULT','ANSWER','COMPLETED'].includes(e.type)).map(e=>e.node)||[]);
    for(const n of nodes) {
      const p=positions.get(n.key),group=make('g',{class:run?.current===n.key?'wf-current':visited.has(n.key)?'wf-visited':''});
      group.append(make('rect',{x:p.x,y:p.y,width:220,height:60,rx:n.kind==='DECISION'?22:8}));
      const kind=make('text',{x:p.x+12,y:p.y+21});kind.textContent=t({TASK:'Task',DECISION:'LLM decision',ASK:'Ask user',END:'Done'}[n.kind]);
      const title=make('text',{x:p.x+12,y:p.y+44});title.textContent=n.title.length>22?n.title.slice(0,21)+'…':n.title;
      const tooltip=make('title',{});tooltip.textContent=n.title+'\n'+n.instruction;group.append(kind,title,tooltip);
      if(!run){group.setAttribute('tabindex','0');group.style.cursor='pointer';const open=()=>{$('[data-wf-editor] details').open=true;document.getElementById('wf-node-'+n.key)?.scrollIntoView({block:'center',behavior:'smooth'});};group.onclick=open;group.onkeydown=e=>{if(e.key==='Enter')open();};}
      svg.append(group);
    }
    container.append(svg);
  }
  async function library() {
    const data=await api('');definitions=data.definitions;
    const list=$('[data-wf-definitions]');list.replaceChildren();
    if(!definitions.length)list.append(element('p',t('No saved workflows'),'help'));
    definitions.forEach(d=>{const b=button(d.spec.title,()=>showEditor(d));b.classList.add('wf-item');list.append(b);});
    const runs=$('[data-wf-executions]');runs.replaceChildren();
    if(!data.executions.length)runs.append(element('p',t('No executions yet'),'help'));
    data.executions.forEach(r=>{const b=button(t(r.state)+' · '+new Date(r.updated_ms).toLocaleString(),()=>act(()=>openRun(r.id)));b.classList.add('wf-item');runs.append(b);});
  }
  async function openRun(id) {selectedRun=id;signature='';panel('run');await refreshRun();}
  function controlButtons(run) {
    const allowed={pause:run.state==='RUNNING',resume:run.state==='PAUSED',retry:run.state==='FAILED',stop:!['COMPLETED','STOPPED','STOPPING'].includes(run.state)};
    $$('[data-wf-control]').forEach(b=>{b.hidden=!allowed[b.dataset.wfControl];b.disabled=busy;});
  }
  async function refreshRun() {
    if(!selectedRun)return;
    const id=selectedRun,data=await api('/executions/'+encodeURIComponent(id));if(id!==selectedRun)return;
    currentView=data;const run=data.run,stamp=JSON.stringify(data);if(stamp===signature)return;signature=stamp;
    $('[data-wf-run-title]').textContent=run.spec.title;
    $('[data-wf-run-state]').textContent=t(run.state);
    const node=run.spec.nodes.find(n=>n.key===run.current),task=data.task;
    $('[data-wf-run-current]').textContent=(node?.title||'')+(task?' · '+t(task.state):'');
    graph($('[data-wf-run-graph]'),run.spec,run);controlButtons(run);
    const answer=$('[data-wf-answer]');answer.hidden=run.state!=='WAITING_USER';$('[data-wf-question]').textContent=node?.instruction||'';
    const options=$('[data-wf-answer-options]');options.replaceChildren();
    if(!answer.hidden)Object.keys(node.branches).forEach(choice=>options.append(button(choice,()=>act(()=>control('answer',choice)))));
    $('[data-wf-answer-form]').hidden=!!Object.keys(node?.branches||{}).length;
    $('[data-wf-preparation]').textContent=data.preparation.map(p=>p.message).join('\n') || (task?.state==='READY'?t('Waiting for a compatible Worker'):'');
    const detail=$('[data-wf-worker-detail]');detail.hidden=!task;detail.href=task?'/ui/plans/'+encodeURIComponent(run.id)+'/tasks/'+encodeURIComponent(task.id.value):'#';
    const timeline=$('[data-wf-timeline]');timeline.replaceChildren();
    for(const event of run.events) {
      const section=element('details',undefined,'wf-event');section.open=['RESULT','FAILED','QUESTION'].includes(event.type);
      section.append(element('summary',new Date(event.time).toLocaleTimeString()+' · '+(run.spec.nodes.find(n=>n.key===event.node)?.title||'')+' · '+t(event.type)),element('pre',event.content));timeline.append(section);
    }
    if(data.report&&!run.events.some(e=>e.type==='RESULT'&&e.content===data.report)){const section=element('details',undefined,'wf-event');section.append(element('summary',t('RESULT')),element('pre',data.report));timeline.append(section);}
  }
  async function control(action,answer) {
    if(action==='stop'&&!window.confirm(t('Stop this execution? Existing results will be retained.')))return;
    await api('/executions/'+encodeURIComponent(selectedRun)+'/control',{version:currentView.run.version,action,answer});signature='';await refreshRun();await library();
  }
  function example() {
    const n=(key,title,kind,instruction,next=null,branches={})=>({key,title,kind,instruction,next,branches});
    showEditor({id:null,version:0,spec:{title:'客户售后申请',goal:'根据提供的售后规则和客户资料，给出有依据的处理建议与回复草稿。只生成文字，不实际换货、退款或发送消息。',entry:'check',nodes:[
      n('check','核对必要资料','DECISION','检查是否有购买记录、签收日期、故障说明。涉及实物损坏还需要照片或明确的照片描述。缺少资料时说明具体缺项。',null,{'资料齐全':'eligibility','需要补充':'details'}),
      n('details','补充申请资料','ASK','请根据上一条判断列出的缺项补充资料。','check'),
      n('eligibility','判断售后处理方式','DECISION','根据参考规则、处理日期和签收日期判断。疑似运输损伤时先让人类判断，不擅自认定责任。',null,{'建议换货':'draft','人工决定':'human'}),
      n('human','确认特殊申请','ASK','该申请需要人工决定，请核对上一步说明后选择。',null,{'同意生成建议':'draft','结束处理':'end'}),
      n('draft','生成处理建议','TASK','整理适用规则、事实依据、处理建议及客户回复草稿。明确尚未实际办理或发送。','end'),
      n('end','处理结束','END','')
    ]}});
    savedSpec='';$('[data-wf-input]').value='模拟售后规则 v1：签收后30天内产品故障可建议换货，超期转人工决定；需购买记录、签收日期、故障说明；实物损坏需照片。疑似运输损伤须核实包装和签收情况。本流程不实际退款、换货或发送消息。\n\n处理日期：2026-11-12。\n购买记录：已提供，订单 DEMO-001。\n签收日期：2026-11-09。\n客户说明：通电后无法启动，希望换货。\n\n以上全部是模拟资料。';
  }
  $('[data-wf-task-form]').onsubmit=e=>{e.preventDefault();act(async()=>{const form=new FormData(e.target),run=await api('/tasks',{requestId:taskRequest,instruction:form.get('instruction'),location:form.get('location')||null});taskRequest=newRequest();await library();await openRun(run.id);});};
  $$('[data-wf-new]').forEach(b=>b.onclick=()=>{if(b.dataset.wfNew==='task'){selectedRun=null;panel('task');}else showEditor();});
  $('[data-wf-sample]').onclick=example;
  $('[data-wf-refresh]').onclick=()=>act(async()=>{await library();await refreshRun();});
  $('[data-wf-add-node]').onclick=()=>{const key='step'+newRequest().replaceAll('-','').slice(0,8);editing.spec.nodes.splice(-1,0,{key,title:t('Task'),kind:'TASK',instruction:'',next:'end',branches:{}});renderNodes();redraw();};
  $('[data-wf-save]').onclick=()=>act(async()=>{editing.spec.title=$('[data-wf-title]').value;editing.spec.goal=$('[data-wf-goal]').value;editing=await api('/definitions',editing);savedSpec=JSON.stringify(editing.spec);startRequest=newRequest();await library();status(t('Workflow saved'));});
  $('[data-wf-start]').onclick=()=>act(async()=>{
    editing.spec.title=$('[data-wf-title]').value;editing.spec.goal=$('[data-wf-goal]').value;
    if(!editing.id||savedSpec!==JSON.stringify(editing.spec))throw new Error(t('Save changes before starting'));
    const run=await api('/start',{requestId:startRequest,definitionId:editing.id,version:editing.version,input:$('[data-wf-input]').value,location:$('[data-wf-location]').value||null});
    startRequest=newRequest();await library();await openRun(run.id);
  });
  $$('[data-wf-control]').forEach(b=>b.onclick=()=>act(()=>control(b.dataset.wfControl)));
  $('[data-wf-answer-form]').onsubmit=e=>{e.preventDefault();act(()=>control('answer',new FormData(e.target).get('answer')));};
  window.addEventListener('aiw:locale-changed',()=>{translate();signature='';if(editing){renderNodes();redraw();}act(async()=>{await library();await refreshRun();});});
  translate();panel('task');act(library);
  setInterval(()=>{if(selectedRun&&!busy&&!document.hidden)act(refreshRun);},4000);
})();
