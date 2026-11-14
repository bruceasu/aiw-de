(() => {
  'use strict';
  const $ = s => document.querySelector(s), $$ = s => [...document.querySelectorAll(s)];
  const words = {
    'INTERRUPTED':['执行中断，结果待核对','実行が中断されました。結果の確認が必要です'],
    'The Worker lease expired. Its process and external effects are not confirmed. Inspect the existing execution and request stop or reconcile it before choosing a retry.':['Worker 租约已过期，进程和外部操作的结果尚未确认。请先查看原执行并请求停止或核对结果，再决定是否重试。','Worker のリースが期限切れです。プロセスと外部操作の結果は未確認です。元の実行を確認し、停止または結果確認後に再試行を判断してください。'],
    'An administrator revoked the original lease. Check existing results before explicitly choosing a retry; no work was automatically dispatched.':['管理员已撤销原租约。请核对已有结果后明确选择是否重试；没有自动重新派发。','管理者が元のリースを取り消しました。既存の結果を確認して再試行を選んでください。自動再割り当ては行われていません。'],
    'RESULT_ACCEPTED':['已确认使用原结果','記録済みの結果を使用'],
    'Use recorded result':['使用已记录的结果','記録済みの結果を使用'],
    'Inspect existing execution':['查看原执行，核对结果与停止状态','元の実行・結果・停止状態を確認'],
    'A lost connection does not mean the Worker stopped. Check the existing execution before retrying.':['连接中断不代表 Worker 已停止。重试前，请核对原执行和已有结果。','接続切断は Worker の停止を意味しません。再試行前に元の実行と結果を確認してください。'],
    'Retry this step? Existing results will be retained; this starts another execution.':['重做本步骤？已有结果会保留，此操作会再次执行。','このステップを再試行しますか？既存の結果は保持され、再び実行されます。'],
    'Use the recorded successful result and continue the workflow?':['使用已记录的成功结果，并继续工作流？','記録済みの成功結果を使用してワークフローを続けますか？'],
    'If suggestion generation is still running, wait for its stop acknowledgement before resuming.':['建议仍在生成时，先等待其停止确认，再继续流程。','提案作成中の場合は、その停止確認後にフローを再開してください。'],
    'Adjust a future step':['调整后续步骤','後続ステップを調整'],
    'Pause following steps first. A read-only Worker suggests a change to one undispatched node. Review it before applying. Resuming or stopping discards the suggestion.':['先暂停后续步骤，再让只读 Worker 为一个尚未派发节点生成建议，审阅确认后才应用。继续或停止流程会放弃建议。','先に後続ステップを一時停止してください。未割り当てノード1件の変更案を読み取り専用 Worker が作成します。確認後に適用してください。再開・停止すると提案を破棄します。'],
    'Future step':['尚未派发的步骤','未割り当てのステップ'],'Requested change':['希望如何调整','変更したい内容'],'Generate suggestion':['生成调整建议','変更案を作成'],
    'Suggestion generation task (usage is recorded separately)':['查看建议生成任务（用量单独记录）','提案作成タスクを表示（使用量は別に記録）'],
    'Current instruction':['原说明','現在の指示'],'Suggested instruction':['建议说明','変更案の指示'],
    'Preview the changed workflow':['预览调整后的流程','変更後のフローをプレビュー'],'Confirm and apply':['确认应用','確認して適用'],'Discard suggestion':['放弃建议','提案を破棄'],
    'Pause following steps before requesting a change':['请先暂停后续步骤，再请求调整。','変更依頼の前に後続ステップを一時停止してください。'],
    'Only an undispatched node can change':['只能调整尚未派发或提问的节点。','未割り当て・未質問のノードのみ変更できます。'],
    'Discard the current suggestion before requesting another':['请先放弃当前建议，再生成新建议。','新しい提案の前に現在の提案を破棄してください。'],
    'Execution changed; request a new suggestion':['执行状态已变化，请放弃旧建议后重新生成。','実行が変わりました。提案を破棄して再作成してください。'],
    'The suggestion is invalid; nothing was changed':['建议格式或分支无效，未修改流程。请查看生成任务的结果。','提案の形式または分岐が無効です。フローは変更されていません。作成タスクの結果を確認してください。'],
    'A current valid suggestion is required':['需要当前版本的有效建议。','現在のバージョンに対応する有効な提案が必要です。'],
    'STALE':['建议已过期','期限切れの提案'],'INVALID':['建议无效','無効な提案'],
    'AMENDMENT_REQUESTED':['已请求调整建议','変更案を依頼済み'],'AMENDMENT_APPLIED':['已确认并应用调整','変更を確認・適用済み'],'AMENDMENT_DISCARDED':['已放弃调整建议','提案を破棄済み'],
    'Task sources':['任务资料','タスク資料'],
    'Source type':['资料类型','資料の種類'],
    'Choose UTF-8 text files':['请选择 UTF-8 文本文件','UTF-8 テキストファイルを選択してください'],
    'Reference knowledge':['参考知识','参考知識'],
    'Method / custom Skill':['工作方法／自定义 Skill','作業方法／カスタム Skill'],
    'Add text files':['添加文本文件','テキストファイルを追加'],
    'Selected text is saved with this definition and sent to the assigned Worker. The model reads relevant pages on demand. Up to 8 files, 128 KiB each, 512 KiB total.':['所选文本随定义保存并提供给执行 Worker；模型按需读取相关页。最多 8 个文件，每个 128 KiB，总计 512 KiB。','選択したテキストは定義とともに保存され、担当 Worker に渡されます。モデルは必要なページを読みます。最大8件、各128 KiB、合計512 KiB。'],
    'Select up to eight text sources totaling 512 KiB':['最多选择 8 份文本资料，总计不超过 512 KiB。','テキスト資料は最大8件、合計512 KiBまでです。'],
    'A text source must contain 1 to 128 KiB':['每份资料须为非空文本，不超过 128 KiB。','資料は空でないテキストで、128 KiB以内にしてください。'],
    'Source digest differs from content':['资料内容与摘要不一致，请重新加载。','資料の内容とダイジェストが一致しません。再読み込みしてください。'],
    'Team member':['团队成员','チームメンバー'],
    'Automatically choose member':['自动选择成员','メンバーを自動選択'],
    'General execution (no team member)':['通用执行（不使用团队成员）','汎用実行（メンバーなし）'],
    'Required Skills (optional, up to 8)':['所需 Skills（可选，最多 8 项）','必要な Skills（任意、最大8件）'],
    'WAITING_MEMBER':['等待合适成员','適切なメンバーを待機中','Waiting for a suitable member'],
    'MEMBER_ASSIGNED':['已选择成员','メンバー選択済み','Member selected'],
    'No team members exist. Create one in Agent factory.':['尚未创建团队成员，请到 Agent 工厂创建。','メンバーがいません。Agent ファクトリーで作成してください。'],
    'No member has all required Skills. Update the requirements or member settings.':['当前没有成员拥有全部所需 Skills，请调整条件或成员设定。','必要な Skills をすべて持つメンバーがいません。条件または設定を変更してください。'],
    'Matching members have stopped accepting work. Resume a matching member.':['匹配的成员均已停止接单，请恢复成员。','条件に合うメンバーは受付停止中です。受付を再開してください。'],
    'Assignment resumes automatically when a matching member is available.':['有合适成员后会自动继续；可暂停或停止本次执行。','適切なメンバーが利用可能になると自動で続行します。一時停止・停止も可能です。'],
    'Selected member lacks required Skills':['所选成员缺少所需 Skills，请调整条件或成员设定。','選択したメンバーに必要な Skills がありません。'],
    'Select up to eight registered platform Skills':['请选择最多 8 项已注册的内置 Skills。','登録済みの Skills を最大8件まで選択してください。'],
    'Team member (optional)':['团队成员（可选）','チームメンバー（任意）'],'No assigned member':['不指定成员','メンバーを指定しない'],'Member unavailable':['成员未加载或已解散','未表示または解散済みのメンバー'],
    'Member stopped accepting work':['成员已停止接单','メンバーは受付を停止しています'],
    'Selected member is not accepting new work':['所选成员已停止接单，请恢复该成员或明确重新选择。','選択したメンバーは受付を停止しています。受付を再開するか、明示的に選び直してください。'],
    'Assigned member snapshot':['本次执行的成员设定','この実行のメンバー設定'],'Resume the selected member to claim this work.':['该工作正在等待成员恢复接单。','この作業の取得にはメンバーの受付再開が必要です。'],'Current work continues.':['当前工作会继续。','現在の作業は継続します。'],
    'Member catalog unavailable; existing assignments are preserved.':['暂时无法读取成员目录；已有成员选择会保留，不会自动改派。','メンバー一覧を取得できません。既存の割り当てを保持し、自動変更しません。'],
    'Recorded usage':['已记录用量','記録済み使用量'], 'Model calls':['模型调用次数','モデル呼び出し数'],
    'Input tokens':['输入 Token','入力トークン'], 'Cached input tokens':['其中缓存 Token','キャッシュ入力トークン'], 'Output tokens':['输出 Token','出力トークン'],
    'Not recorded':['未记录','未記録'], 'Recorded estimates':['已知估算费用','既知の推定費用'], 'Missing estimates':['尚无估算的调用（含订阅或价格缺失）','推定額のない呼び出し（サブスクリプション等を含む）'],
    'calls missing this value':['次调用未记录该值','件でこの値が未記録'], 'View recorded calls':['查看调用记录','呼び出し記録を表示'],
    'No calls recorded; this does not mean no calls occurred.':['暂无调用记录；不代表没有发生调用。','呼び出しは未記録です。呼び出しがなかったことを意味しません。'],
    'Recorded calls include retries and summaries. Calls are not rounds. Cached input is part of input. Costs are estimates, not invoices.':['包含已记录的重试和摘要调用。调用次数不等于轮数，缓存已包含在输入中。费用是估算，并非实际账单。','記録済みの再試行・要約呼び出しを含みます。呼び出し数はラウンド数ではなく、キャッシュ入力は入力に含まれます。費用は推定です。'],
    'Describe the work and follow its execution.':['说清要做的事，直接查看执行进展。','依頼を伝え、実行状況を確認します。'],
    'Edit a reusable workflow and follow each execution.':['编辑可复用的工作流，查看每次执行的进展。','再利用できるワークフローを編集し、各実行の状況を確認します。'],
    'Pausing prevents following steps from starting. Current Worker work may continue. Stopping waits for Worker confirmation.':['暂停只阻止后续步骤启动，当前 Worker 工作可能继续。停止执行需要等待 Worker 确认。','一時停止は後続ステップの開始を止めます。現在の Worker 作業は続く場合があります。実行停止は Worker の確認を待ちます。'],
    'SETTLED':['本次执行已结束','実行終了'], 'FINALIZING':['正在确认结果','結果を確定中'],
    'PAUSING':['等待暂停确认','一時停止確認待ち'], 'BROKEN':['执行通道异常','実行チャネル異常'],
    'Worker activity':['Worker 实时活动','Worker の実行状況'],
    'Recent model and tool metadata. This is not the complete model conversation.':['展示最近的模型与工具活动概况，并非完整模型对话原文。','最近のモデル・ツール活動の概要です。モデルとの会話全文ではありません。'],
    'Current task instructions':['当前任务内容','現在のタスク内容'], 'Recent activity (up to 64 events)':['最近活动（最多 64 条）','最近の活動（最大64件）'],
    'No Worker activity recorded yet':['尚未记录 Worker 活动','Worker の活動はまだ記録されていません'],
    'Last contact':['最后联系','最終通信'], 'Connection delayed; execution state is not confirmed':['连接更新延迟，不能据此确认执行已停止','通信更新が遅れています。実行停止を意味しません'],
    'Model not recorded':['模型未记录','モデル未記録'], 'model-request':['模型请求','モデル要求'], 'model-terminal':['模型响应终态','モデル応答完了状態'],
    'tool-proposed':['工具调用已校验','ツール呼び出し検証済み'], 'runtime-event':['运行活动','実行イベント'], 'ai-settings':['模型设置','モデル設定'],
    'native-turn-started':['Worker 开始处理','Worker が処理を開始'], 'execution-quiescent':['执行资源已静止','実行リソース停止確認'],
    'Workflow draft is not ready yet':['工作流草稿尚未就绪','ワークフロー案はまだ準備中です'],
    'Add context':['补充内容','情報を追加'], 'Additional content':['补充资料或方向','追加情報・方針'],
    'When to use':['生效方式','適用方法'], 'Add to context':['加入处理上下文','コンテキストに追加'],
    'Use next turn':['下一轮生效','次のターンで適用'], 'Send context':['发送补充','追加情報を送信'],
    'New content is used at the next model call. It cannot change an in-flight request or grant new permissions.':['内容最早在下一次模型调用生效，不会修改在途请求或增加权限。','追加情報は次のモデル呼び出しから適用されます。進行中のリクエストや権限は変更しません。'],
    'QUEUED':['已收到，等待生效','受信済み・適用待ち'], 'RESERVED':['等待模型调用回执','モデル応答の確認待ち'],
    'APPLIED':['已用于模型调用','モデル呼び出しに適用済み'], 'CONTEXT':['已加入后续上下文','後続コンテキストに追加済み'],
    'UNKNOWN':['是否生效尚未确认','適用結果が不明'], 'Input received':['补充内容已收到','追加情報を受信しました'],
    'INPUT_RECEIVED':['收到补充','追加情報を受信'], 'INPUT_CONTEXT':['加入上下文','コンテキストに追加'],
    'INPUT_RESERVED':['准备使用补充','追加情報を使用準備'], 'INPUT_APPLIED':['补充已生效','追加情報を適用'], 'INPUT_UNKNOWN':['补充生效结果未确认','追加情報の適用結果が不明'],
    'This version handles read-only text tasks and decisions. It does not modify files, issue refunds or send messages.':['当前版本处理只读文本任务与判断，不修改文件、不实际退款或发送消息。','この版は読み取り専用のテキスト処理と判断に対応します。ファイル変更、返金、メッセージ送信は行いません。'],
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
    'Pause following steps':['暂停后续步骤','後続ステップを一時停止'], 'Resume workflow':['继续工作流','ワークフローを再開'], 'Retry this step':['重试本步骤','このステップを再試行'], 'Stop execution':['停止本次执行','実行を停止'],
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
    'RUNNING':['执行中','実行中'], 'WAITING_USER':['待你回答','回答待ち'], 'PAUSED':['后续步骤已暂停','後続ステップは一時停止中'], 'STOPPING':['等待 Worker 停止确认','Worker の停止確認待ち'],
    'STOPPED':['已停止','停止済み'], 'COMPLETED':['已完成','完了'], 'FAILED':['需要处理','対応が必要'], 'READY':['等待接单','待機中'],
    'CLAIMED':['已领取','取得済み'], 'WAITING_INPUT':['等待 Worker 问题答复','Worker の質問への回答待ち'], 'STARTED':['开始','開始'],
    'DISPATCHED':['已派发','割り当て済み'], 'QUESTION':['需要补充','質問'], 'ANSWER':['用户回答','ユーザーの回答'],
    'BRANCH':['分支判断','分岐判断'], 'RESULT':['步骤结果','ステップ結果'], 'STOP_REQUESTED':['已请求停止','停止要求済み'], 'RESUMED':['已继续','再開済み'],
    'LIMIT':['达到执行限制','実行上限に到達'], 'RETRY':['用户请求重试','ユーザーによる再試行'],
  };
  const t = value => {
    const locale = window.aiwI18n?.locale || document.documentElement.lang;
    return words[value]?.[locale?.startsWith('zh') ? 0 : locale?.startsWith('ja') ? 1 : 2] || value;
  };
  const status = message => { $('[data-wf-status]').textContent = message; };
  let definitions=[], teamSkills=[], teamMembers=[], taskSources=[], editing=null, selectedRun=null, currentView=null, savedSpec='', busy=false, signature='';
  const newRequest = () => crypto.randomUUID();
  let taskRequest=newRequest(), startRequest=newRequest(), inputRequest=newRequest(), amendmentRequest=newRequest();
  const route=new URLSearchParams(location.search);
  const pageMode=document.body.dataset.wfMode==='tasks'?'tasks':'workflows';
  let preparingGoal=route.get('goalVersion'), importAttempted=false;
  async function api(path, data) {
    const response = await fetch('/api/r1/workflows'+path, {method:data===undefined?'GET':'POST', credentials:'same-origin',
      headers:{'Content-Type':'application/json','X-CSRF-Token':$('meta[name="csrf-token"]').content}, body:data===undefined?undefined:JSON.stringify(data)});
    if(!response.ok) {const detail=await response.json().catch(()=>({}));throw new Error(detail.error || `${response.status} ${response.statusText}`);}
    return response.json();
  }
  async function act(work) {
    if(busy)return;busy=true;status('');
    const buttons=$$('button');buttons.forEach(b=>b.disabled=true);
    try {await work();} catch(error) {status(error instanceof TypeError?t('Request outcome is unknown. Check execution history before submitting again.'):t(error.message));}
    finally {busy=false;buttons.forEach(b=>{if(b.isConnected)b.disabled=false;});if(currentView)controlButtons(currentView.run);}
  }
  function element(tag,text,className) {const e=document.createElement(tag);if(text!==undefined)e.textContent=text;if(className)e.className=className;return e;}
  function button(label,callback) {const b=element('button',t(label),'btn ghost');b.type='button';b.onclick=callback;return b;}
  function translate() {
    document.title=t(pageMode==='tasks'?'Simple task':'Workflows');
    $$('[data-wf-label]').forEach(e=>e.textContent=t(e.dataset.wfLabel));
    $$('[data-wf-new], [data-wf-sample], [data-wf-refresh], [data-wf-add-node], [data-wf-save], [data-wf-start], [data-wf-control]').forEach(e=>{
      e.dataset.wfOriginal ||= e.textContent;e.textContent=t(e.dataset.wfOriginal);
    });
  }
  function panel(name) {['task','editor','run'].forEach(p=>$(`[data-wf-${p}]`).hidden=p!==name);}
  function singleSpec() {return {title:t('New workflow'),goal:'',entry:'task',nodes:[
    {key:'task',title:t('Task'),kind:'TASK',instruction:'',next:'end',branches:{},assignment:'AUTO',requiredSkills:[]},
    {key:'end',title:t('Done'),kind:'END',instruction:'',next:null,branches:{}}]};}
  function showEditor(definition) {
    selectedRun=null;editing=structuredClone(definition || {id:null,version:0,spec:singleSpec()});
    editing.spec.sources ||= [];
    renderSources($('[data-wf-definition-sources]'),editing.spec.sources,values=>editing.spec.sources=values);
    for(const n of editing.spec.nodes)if(['TASK','DECISION'].includes(n.kind))n.assignment ||= n.memberId?'MEMBER':definition?.id?'GENERAL':'AUTO';
    $('[data-wf-title]').value=editing.spec.title;$('[data-wf-goal]').value=editing.spec.goal;
    $('[data-wf-input]').value='';startRequest=newRequest();savedSpec=definition?JSON.stringify(editing.spec):'';
    panel('editor');renderNodes();graph($('[data-wf-definition-graph]'),editing.spec);status('');
  }
  function field(label,input) {const wrapper=element('label',undefined,'field');wrapper.append(element('span',t(label)),input);return wrapper;}
  function renderSources(host,values,change){
    host.replaceChildren(element('h3',t('Task sources')),element('p',t('Selected text is saved with this definition and sent to the assigned Worker. The model reads relevant pages on demand. Up to 8 files, 128 KiB each, 512 KiB total.'),'help'));
    const kind=element('select');kind.append(new Option(t('Reference knowledge'),'knowledge'),new Option(t('Method / custom Skill'),'skill'));
    const file=element('input');file.type='file';file.multiple=true;file.accept='.txt,.md,.markdown,.json,.csv';
    file.onchange=()=>act(async()=>{
      const additions=[],sourceKind=kind.value,selectedFiles=[...file.files];
      for(const source of selectedFiles){
        if(source.size>131072)throw new Error('A text source must contain 1 to 128 KiB');
        let content;try{content=new TextDecoder('utf-8',{fatal:true}).decode(await source.arrayBuffer());}catch(_){throw new Error('Choose UTF-8 text files');}
        if(!content.trim()||content.includes('\0')||new TextEncoder().encode(content).length>131072)throw new Error('A text source must contain 1 to 128 KiB');
        additions.push({kind:sourceKind,title:source.name,content});
      }
      const next=[...values,...additions];
      if(next.length>8||next.reduce((size,item)=>size+new TextEncoder().encode(item.content).length,0)>524288)throw new Error('Select up to eight text sources totaling 512 KiB');
      change(next);renderSources(host,next,change);
    });
    host.append(field('Source type',kind),field('Add text files',file));
    values.forEach((source,index)=>{const row=element('div',undefined,'wf-actions');row.append(element('span',source.title+' · '+t(source.kind==='skill'?'Method / custom Skill':'Reference knowledge')),button('Remove',()=>{const next=values.filter((_,i)=>i!==index);change(next);renderSources(host,next,change);}));host.append(row);});
  }
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
      kind.value=node.kind;kind.onchange=()=>{node.kind=kind.value;if(!['TASK','DECISION'].includes(node.kind)){node.memberId=null;node.assignment=null;node.requiredSkills=[];}else node.assignment ||= 'AUTO';node.branches={};node.next=node.kind==='END'||node.kind==='DECISION'?null:'end';if(node.kind==='DECISION')node.branches={'yes':'end','no':'end'};renderNodes();redraw();};
      row.append(field('Title',input(node.title,v=>{node.title=v;redraw();},160)),field('Type',kind));section.append(row);
      if(node.kind!=='END') {
        const instruction=element('textarea');instruction.value=node.instruction;instruction.rows=3;instruction.maxLength=8000;instruction.oninput=()=>node.instruction=instruction.value;
        section.append(field('Instruction',instruction));
        if(['TASK','DECISION'].includes(node.kind)){const select=element('select');select.dataset.i18nSkip='';memberOptions(select,node.memberId||node.assignment||'GENERAL');select.onchange=()=>Object.assign(node,assignment(select.value));section.append(field('Team member',select));const required=element('select');required.multiple=true;required.size=4;required.dataset.i18nSkip='';skillOptions(required,node.requiredSkills||[]);required.onchange=()=>node.requiredSkills=[...required.selectedOptions].map(o=>o.value);section.append(field('Required Skills (optional, up to 8)',required));}
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
    const data=await api('?kind='+pageMode);definitions=data.definitions;
    const list=$('[data-wf-definitions]');list.replaceChildren();
    if(!definitions.length)list.append(element('p',t('No saved workflows'),'help'));
    definitions.forEach(d=>{const b=button('',()=>showEditor(d));b.textContent=d.spec.title;b.dataset.i18nSkip='';b.classList.add('wf-item');list.append(b);});
    const runs=$('[data-wf-executions]');runs.replaceChildren();
    if(!data.executions.length)runs.append(element('p',t('No executions yet'),'help'));
    data.executions.forEach(r=>{const b=button('',()=>act(()=>openRun(r.id)));b.textContent=r.title||r.id;b.dataset.i18nSkip='';b.classList.add('wf-item');b.append(element('small',t(r.state)+' · '+new Date(r.updated_ms).toLocaleString()));runs.append(b);});
    loadMembers();
  }
  let teamLoading=false;
  async function loadMembers(){
    if(teamLoading)return;teamLoading=true;
    try{
      const response=await fetch('/api/r1/team-members',{credentials:'same-origin'});if(!response.ok||response.redirected)throw new Error();
      const team=await response.json();if(!Array.isArray(team.members))throw new Error();teamMembers=team.members;teamSkills=team.skills||[];
      memberOptions($('[data-wf-member]'),$('[data-wf-member]').value);skillOptions($('[data-wf-skills]'),[...$('[data-wf-skills]').selectedOptions].map(o=>o.value));if(editing&&!$('[data-wf-editor]').hidden)renderNodes();
      $('[data-wf-member-status]').textContent='';
    }catch(_){$('[data-wf-member-status]').textContent=t('Member catalog unavailable; existing assignments are preserved.');}
    finally{teamLoading=false;}
  }
  function memberOptions(select,value){
    select.replaceChildren(new Option(t('Automatically choose member'),'AUTO'),new Option(t('General execution (no team member)'),'GENERAL'));
    for(const member of teamMembers){const option=new Option(member.name+(member.state!=='ACTIVE'?' · '+t('Member stopped accepting work'):''),member.id);option.disabled=member.state!=='ACTIVE';select.add(option);}
    if(value&&![...select.options].some(o=>o.value===value))select.add(new Option(t('Member unavailable')+' · '+value,value));
    select.value=value||'AUTO';
  }
  function assignment(value){return {assignment:['AUTO','GENERAL'].includes(value)?value:'MEMBER',memberId:['AUTO','GENERAL'].includes(value)?null:value};}
  function skillOptions(select,values){select.replaceChildren();for(const skill of new Set([...teamSkills,...values]))select.add(new Option(skill,skill,false,values.includes(skill)));}
  async function openRun(id) {if(id!==route.get('execution'))preparingGoal=null;selectedRun=id;signature='';panel('run');await refreshRun();}
  function controlButtons(run,task) {
    const allowed={pause:['RUNNING','WAITING_MEMBER'].includes(run.state),resume:run.state==='PAUSED',retry:run.state==='FAILED',
      'accept-result':run.state==='FAILED'&&task?.state==='COMPLETED',stop:!['COMPLETED','STOPPED','STOPPING'].includes(run.state)};
    $$('[data-wf-control]').forEach(b=>{b.hidden=!allowed[b.dataset.wfControl];b.disabled=busy;});
  }
  function renderActivity(data) {
    const activity=data.activity||{}, events=activity.events||[], connection=$('[data-wf-connection]');
    const progress=events.filter(e=>e.event_type==='progress-stalled').at(-1);
    let progressCode='';try{progressCode=JSON.parse(progress?.payload_json||'{}').code;}catch(_){/* Keep ordinary activity available. */}
    const progressMessage=progressCode==='MODEL_NO_PROGRESS'
      ?'Repeated reads produced no new result after a reminder. The Worker stopped further model calls.'
      :progressCode==='REPEATED_READ_RESULT'?'Repeated reads returned the same result. The Worker will ask the model to reconsider its next step.':'';
    $('[data-wf-progress]').textContent=progressMessage?(window.aiwI18n?.t(progressMessage)||progressMessage):'';
    if(!activity.attemptId)connection.textContent=t('No Worker activity recorded yet');
    else connection.textContent=t(activity.phase)+' · '+t('Last contact')+': '+new Date(activity.lastContact).toLocaleString(window.aiwI18n?.locale||undefined)
      +(activity.stale&&activity.phase!=='SETTLED'?' · '+t('Connection delayed; execution state is not confirmed'):'');
    let model=null,effort=null;
    for(const event of events){try{const payload=JSON.parse(event.payload_json);if(payload.model)model=payload.model;if(payload.effort)effort=payload.effort;}catch(_){/* Preserve other recorded activity. */}}
    $('[data-wf-model]').textContent=model?(model+(effort?' · '+effort:'')):t('Model not recorded');
    $('[data-wf-instructions]').textContent=data.task?.instruction||data.run.spec.goal;
    const list=$('[data-wf-activity-events]');
    if(list.dataset.attempt!==activity.attemptId){list.replaceChildren();list.dataset.attempt=activity.attemptId||'';}
    const valid=new Set(events.map(e=>String(e.sequence_no)));
    [...list.children].forEach(e=>{if(!valid.has(e.dataset.sequence))e.remove();});
    for(const event of events){
      if([...list.children].some(e=>e.dataset.sequence===String(event.sequence_no)))continue;
      const entry=element('details',undefined,'wf-event');entry.dataset.sequence=String(event.sequence_no);
      entry.append(element('summary',new Date(event.occurred_ms).toLocaleTimeString()+' · '+t(event.event_type)),element('pre',event.payload_json));list.append(entry);
    }
  }
  function renderUsage(data) {
    const usage=data.usage,totals=usage?.totals,host=$('[data-wf-usage]');
    $('[data-wf-usage-link]').href='/ui/configurations?view=observations&planId='+encodeURIComponent(data.run.id);
    const stamp=JSON.stringify([data.run.id,totals,usage?.currencies]);if(host.dataset.stamp===stamp)return;host.dataset.stamp=stamp;
    host.replaceChildren();
    const number=value=>Number.isSafeInteger(value)?value.toLocaleString(window.aiwI18n?.locale||undefined):t('Not recorded');
    if(!totals){host.append(element('p',t('Not recorded')));return;}
    host.append(element('p',t('Model calls')+': '+number(totals.calls)));
    if(totals.calls===0)host.append(element('p',t('No calls recorded; this does not mean no calls occurred.'),'help'));
    for(const [label,key,missing] of [['Input tokens','known_input','missing_input'],['Cached input tokens','known_cached','missing_cached'],['Output tokens','known_output','missing_output']])
      host.append(element('p',t(label)+': '+number(totals[key])+(totals[missing]>0?' · '+number(totals[missing])+' '+t('calls missing this value'):'')));
    for(const currency of usage.currencies||[]){
      const amount=Number.isSafeInteger(currency.known_cost_micros)?(currency.known_cost_micros/1000000).toLocaleString(window.aiwI18n?.locale||undefined,{maximumFractionDigits:6}):t('Not recorded');
      host.append(element('p',t('Recorded estimates')+': '+amount+' '+currency.currency));
    }
    if(!usage.currencies?.length)host.append(element('p',t('Recorded estimates')+': '+t('Not recorded')));
    host.append(element('p',t('Missing estimates')+': '+number(totals.without_estimate)));
  }
  function renderAmendment(data){
    const run=data.run,proposal=data.proposal,host=$('[data-wf-amendment]'),result=$('[data-wf-amendment-result]'),form=$('[data-wf-amendment-form]'),select=$('[data-wf-amendment-node]');
    host.hidden=['COMPLETED','STOPPED'].includes(run.state)&&!proposal;
    const stamp=JSON.stringify([run.id,run.version,proposal]);if(host.dataset.stamp===stamp)return;
    const changedRun=host.dataset.run!==run.id;host.dataset.run=run.id;host.dataset.stamp=stamp;
    if(changedRun){form.reset();amendmentRequest=newRequest();}
    const previous=select.value;select.replaceChildren();
    run.spec.nodes.filter(n=>n.kind!=='END'&&!(run.visits?.[n.key])&&!run.events.some(e=>e.node===n.key&&['DISPATCHED','QUESTION'].includes(e.type))).forEach(n=>select.add(new Option(n.title,n.key)));
    if([...select.options].some(o=>o.value===previous))select.value=previous;
    form.hidden=run.state!=='PAUSED'||!!proposal||!select.options.length;result.replaceChildren();
    if(!proposal)return;
    result.append(element('p',t(proposal.status),'help'));
    if(proposal.status==='STOPPING')result.append(element('p',t('If suggestion generation is still running, wait for its stop acknowledgement before resuming.'),'help'));
    const link=element('a',t('Suggestion generation task (usage is recorded separately)'));link.href='/ui/tasks?execution='+encodeURIComponent(proposal.helperRun);result.append(link);
    if(proposal.error)result.append(element('p',t(proposal.error),'help'));
    if(proposal.candidate){
      result.append(element('h4',t('Current instruction')),element('pre',proposal.before.instruction),element('h4',t('Suggested instruction')),element('pre',proposal.candidate.instruction));
      const preview=element('details'),canvas=element('div',undefined,'wf-graph');preview.append(element('summary',t('Preview the changed workflow')),canvas);result.append(preview);
      graph(canvas,{...run.spec,nodes:run.spec.nodes.map(n=>n.key===proposal.candidate.key?proposal.candidate:n)});
      if(run.state==='PAUSED'&&proposal.status==='READY')result.append(button('Confirm and apply',()=>act(()=>decideAmendment(true))));
    }
    result.append(button('Discard suggestion',()=>act(()=>decideAmendment(false))));
  }
  async function decideAmendment(apply){
    await api('/executions/'+encodeURIComponent(selectedRun)+'/amendments/decision',{version:currentView.run.version,proposalId:currentView.proposal.id,apply});
    amendmentRequest=newRequest();signature='';await refreshRun();await library();
  }
  async function refreshRun() {
    if(!selectedRun)return;
    const id=selectedRun,data=await api('/executions/'+encodeURIComponent(id));if(id!==selectedRun)return;
    currentView=data;renderActivity(data);renderUsage(data);renderAmendment(data);
    const member=data.member||{};$('[data-wf-assigned-member]').textContent=member.id?t('Assigned member snapshot')+': '+member.name+' · v'+member.revision+(member.state==='DRAINING'?' · '+t(['READY','BLOCKED'].includes(data.task?.state)?'Resume the selected member to claim this work.':'Current work continues.'):''):'';
    const run=data.run,taskState=data.task?{id:data.task.id,state:data.task.state,instruction:data.task.instruction}:null,stamp=JSON.stringify([run,taskState,data.report,data.preparation]);if(stamp===signature)return;signature=stamp;
    $('[data-wf-run-title]').textContent=run.spec.title;
    $('[data-wf-run-state]').textContent=t(run.state);
    const node=run.spec.nodes.find(n=>n.key===run.current),task=data.task;
    $('[data-wf-run-current]').textContent=(node?.title||'')+(task?' · '+t(task.state):'');
    graph($('[data-wf-run-graph]'),run.spec,run);controlButtons(run,task);
    $('[data-wf-interrupted]').hidden=run.state!=='FAILED'||!run.events.some(e=>e.type==='INTERRUPTED');
    const inspect=$('[data-wf-inspect]');inspect.hidden=!task||!data.activity?.attemptId;
    if(!inspect.hidden)inspect.href='/ui/configurations?'+new URLSearchParams({view:'workbench',source:task.requiredCapabilities.includes('execution-guard-v1')?'guarded':'native',plan:run.id,task:task.id.value||task.id,attempt:data.activity.attemptId})+'#worker-run';
    $('[data-wf-supplement]').hidden=!['RUNNING','PAUSED','WAITING_USER','WAITING_MEMBER'].includes(run.state);
    const inputReceipts=$('[data-wf-input-receipts]');inputReceipts.replaceChildren();
    for(const input of run.inputs||[]) {
      const receipt=element('details',undefined,'wf-event');
      receipt.append(element('summary',new Date(input.receivedAt).toLocaleTimeString()+' · '+t(input.state)),element('pre',input.text));
      inputReceipts.append(receipt);
    }
    const answer=$('[data-wf-answer]');answer.hidden=run.state!=='WAITING_USER';$('[data-wf-question]').textContent=node?.instruction||'';
    const options=$('[data-wf-answer-options]');options.replaceChildren();
    if(!answer.hidden)Object.keys(node.branches).forEach(choice=>options.append(button(choice,()=>act(()=>control('answer',choice)))));
    $('[data-wf-answer-form]').hidden=!!Object.keys(node?.branches||{}).length;
    $('[data-wf-preparation]').textContent=(run.memberWait&&['WAITING_MEMBER','PAUSED'].includes(run.state)?t(run.memberWait)+(node?.requiredSkills?.length?' ('+node.requiredSkills.join(', ')+')':'')+(run.state==='WAITING_MEMBER'?' '+t('Assignment resumes automatically when a matching member is available.'):''):'') || data.preparation.map(p=>p.message).join('\n') || (task?.state==='READY'?t('Waiting for a compatible Worker'):'');
    const detail=$('[data-wf-worker-detail]'), activityTask=task?.id.value||data.activity?.taskId;
    detail.hidden=!activityTask;detail.href=activityTask?'/ui/plans/'+encodeURIComponent(run.id)+'/tasks/'+encodeURIComponent(activityTask):'#';
    const timeline=$('[data-wf-timeline]');timeline.replaceChildren();
    for(const event of run.events) {
      const section=element('details',undefined,'wf-event');section.open=['RESULT','FAILED','QUESTION','INTERRUPTED'].includes(event.type);
      section.append(element('summary',new Date(event.time).toLocaleTimeString()+' · '+(run.spec.nodes.find(n=>n.key===event.node)?.title||'')+' · '+t(event.type)),element('pre',event.type==='INTERRUPTED'?t(event.content):event.content));timeline.append(section);
    }
    if(data.report&&!run.events.some(e=>e.type==='RESULT'&&e.content===data.report)){const section=element('details',undefined,'wf-event');section.append(element('summary',t('RESULT')),element('pre',data.report));timeline.append(section);}
    if(run.state==='COMPLETED'&&preparingGoal&&!importAttempted) {
      importAttempted=true;
      const response=await fetch('/api/r1/goals/versions/'+encodeURIComponent(preparingGoal)+'/workflow/read',{
        method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-CSRF-Token':$('meta[name="csrf-token"]').content},body:'{}'});
      const result=await response.json();if(!response.ok)throw new Error(result.error||response.statusText);
      if(!result.workflowId)throw new Error(t('Workflow draft is not ready yet'));
      await library();const definition=await api('/definitions/'+encodeURIComponent(result.workflowId));
      showEditor(definition);preparingGoal=null;
      history.replaceState(null,'','/ui/workflows?definition='+encodeURIComponent(result.workflowId));
    }
  }
  async function control(action,answer) {
    if(action==='stop'&&!window.confirm(t('Stop this execution? Existing results will be retained.')))return;
    if(action==='retry'&&!window.confirm(t('Retry this step? Existing results will be retained; this starts another execution.')))return;
    if(action==='accept-result'&&!window.confirm(t('Use the recorded successful result and continue the workflow?')))return;
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
  $('[data-wf-task-form]').onsubmit=e=>{e.preventDefault();act(async()=>{const form=new FormData(e.target),run=await api('/tasks',{requestId:taskRequest,instruction:form.get('instruction'),location:form.get('location')||null,...assignment($('[data-wf-member]').value),requiredSkills:[...$('[data-wf-skills]').selectedOptions].map(o=>o.value),sources:taskSources});taskRequest=newRequest();await library();await openRun(run.id);});};
  $$('[data-wf-new]').forEach(b=>b.onclick=()=>{if(b.dataset.wfNew==='task'){selectedRun=null;panel('task');}else showEditor();});
  $('[data-wf-sample]').onclick=example;
  $('[data-wf-refresh]').onclick=()=>act(async()=>{signature='';importAttempted=false;await refreshRun();await library();});
  $('[data-wf-add-node]').onclick=()=>{const key='step'+newRequest().replaceAll('-','').slice(0,8);editing.spec.nodes.splice(-1,0,{key,title:t('Task'),kind:'TASK',instruction:'',next:'end',branches:{},assignment:'AUTO',requiredSkills:[]});renderNodes();redraw();};
  $('[data-wf-save]').onclick=()=>act(async()=>{editing.spec.title=$('[data-wf-title]').value;editing.spec.goal=$('[data-wf-goal]').value;editing=await api('/definitions',editing);savedSpec=JSON.stringify(editing.spec);startRequest=newRequest();await library();status(t('Workflow saved'));});
  $('[data-wf-start]').onclick=()=>act(async()=>{
    editing.spec.title=$('[data-wf-title]').value;editing.spec.goal=$('[data-wf-goal]').value;
    if(!editing.id||savedSpec!==JSON.stringify(editing.spec))throw new Error(t('Save changes before starting'));
    const run=await api('/start',{requestId:startRequest,definitionId:editing.id,version:editing.version,input:$('[data-wf-input]').value,location:$('[data-wf-location]').value||null});
    startRequest=newRequest();await library();await openRun(run.id);
  });
  $$('[data-wf-control]').forEach(b=>b.onclick=()=>act(()=>control(b.dataset.wfControl)));
  $('[data-wf-answer-form]').onsubmit=e=>{e.preventDefault();act(()=>control('answer',new FormData(e.target).get('answer')));};
  $('[data-wf-supplement-form]').onsubmit=e=>{e.preventDefault();act(async()=>{
    const form=new FormData(e.target);
    await api('/executions/'+encodeURIComponent(selectedRun)+'/inputs',{requestId:inputRequest,text:form.get('text'),mode:form.get('mode')});
    inputRequest=newRequest();e.target.reset();signature='';await refreshRun();status(t('Input received'));
  });};
  $('[data-wf-amendment-form]').onsubmit=e=>{e.preventDefault();act(async()=>{
    const form=new FormData(e.target);
    await api('/executions/'+encodeURIComponent(selectedRun)+'/amendments',{version:currentView.run.version,requestId:amendmentRequest,node:form.get('node'),change:form.get('change')});
    signature='';await refreshRun();await library();
  });};
  window.addEventListener('aiw:locale-changed',()=>{translate();renderSources($('[data-wf-task-sources]'),taskSources,values=>taskSources=values);if(editing)renderSources($('[data-wf-definition-sources]'),editing.spec.sources||[],values=>editing.spec.sources=values);signature='';$('[data-wf-usage]').dataset.stamp='';$('[data-wf-amendment]').dataset.stamp='';if(editing){renderNodes();redraw();}act(async()=>{await library();await refreshRun();});});
  translate();renderSources($('[data-wf-task-sources]'),taskSources,values=>taskSources=values);if(pageMode==='tasks')panel('task');else showEditor();act(async()=>{
    await library();
    if(route.get('execution'))await openRun(route.get('execution'));
    else if(route.get('definition'))showEditor(await api('/definitions/'+encodeURIComponent(route.get('definition'))));
  });
  setInterval(()=>{if(selectedRun&&!busy&&!document.hidden&&!(preparingGoal&&importAttempted))act(refreshRun);},4000);
})();
