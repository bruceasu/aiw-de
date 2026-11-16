(() => {
  'use strict';
  const $ = s => document.querySelector(s), $$ = s => [...document.querySelectorAll(s)];
  const words = {
    'Plane schedules':['Plane 定时安排','Plane の実行予定'], 'Arrange text work':['安排文字工作','テキスト作業を予定'],
    'Schedule':['定时安排','実行を予定'], 'Save arrangement':['保存安排','予定を保存'], 'No arrangements yet':['尚无定时安排','予定はありません'],
    'First time (browser time zone)':['首次时间（浏览器时区）','初回日時（ブラウザーのタイムゾーン）'], 'Repeat':['重复方式','繰り返し'],
    'Once':['仅一次','1回のみ'], 'Every day':['每天','毎日'], 'Every week':['每周','毎週'], 'Time zone':['时区','タイムゾーン'],
    'Pause arrangement':['暂停自动启动','自動開始を一時停止'], 'Checked; resume arrangement':['已核对，恢复安排','確認して予定を再開'],
    'Run once now':['现在执行一次','今すぐ1回実行'], 'Skip occurrence':['跳过本次','今回をスキップ'], 'Reschedule':['改期','日時を変更'], 'Disable arrangement':['停用安排','予定を無効化'],
    'View execution':['查看本次执行','実行を表示'], 'ACTIVE':['自动安排中','自動実行予定'], 'NEEDS_CONFIRMATION':['等待你决定','判断待ち'], 'TRIGGERED':['本次已启动','実行を開始済み'],
    'Captured work':['已保存的工作内容','保存した作業内容'], 'New arrangement with captured work':['使用原内容新建安排','保存した内容で予定を新規作成'],
    'Pausing an arrangement leaves its current execution running. Use the execution page to stop that work.':['暂停或停用安排不会停止当前执行；请在执行页面停止该项工作。','予定の一時停止・無効化では実行中の作業は停止しません。実行ページで停止してください。'],
    'The selected content is frozen. Later edits do not change this arrangement. Missed or overlapping work waits for your decision.':['安排保存当前内容快照，后续编辑不会自动替换。错过时间或上次尚未结束时，等待你决定。','現在の内容を保存します。後の編集は反映されません。予定時刻を逃した場合や前回が未完了の場合は判断を待ちます。'],
    'Schedules currently support text workflows only':['当前定时安排只支持文字工作流，不授予文件或命令权限。','現在の予定はテキスト作業のみです。ファイル・コマンド権限は付与しません。'],
    'Schedule work must have an instruction':['请先填写工作内容和完成条件。','作業内容と完了条件を入力してください。'],
    'Run this occurrence now? Check the prior result first.':['现在执行一次？请先核对原结果及停止状态。','今すぐ1回実行しますか？元の結果と停止状態を確認してください。'],
    'Check the original execution and its stop acknowledgement first':['原执行尚未确认结束，请先核对结果和停止回执。','元の実行の結果と停止確認を先に確認してください。'],
    'Choose a future first time':['请选择将来的执行时间。','将来の実行日時を選択してください。'],
    'Schedules use temporary locations; attach selected text sources':['定时文字任务使用临时目录，请把所需资料明确加入任务资料。','予定のテキスト作業は一時フォルダーを使います。必要な資料を明示的に追加してください。'],
    'Arrangement saved; the current content is frozen':['安排已保存，使用当前内容快照。','予定を保存しました。現在の内容を使用します。'],
    'Schedule changed; reload':['安排已变化，请刷新后再操作。','予定が変更されました。再読み込みしてください。'],
    'Schedule request changed; inspect the original arrangement':['请求内容已变化，请先查看原安排。','依頼内容が変わりました。元の予定を確認してください。'],
    'Current schedule Owner required':['需要当前安排所属账号的操作权限。','予定を所有するアカウントの権限が必要です。'],
    'User checked the prior result and resumed automatic starts':['你已核对原结果并恢复自动安排。','元の結果を確認し、自動開始を再開しました。'],
    'Choose run once, skip or reschedule; missed work was not started':['错过的工作尚未启动；请选择执行一次、跳过或改期。','時刻を過ぎた作業は開始していません。1回実行・スキップ・日時変更を選んでください。'],
    'User rescheduled after checking the prior result':['你已核对原结果并修改时间。','元の結果を確認し、日時を変更しました。'],
    'Missed time; choose run once, skip or reschedule':['已错过时间；请选择执行一次、跳过或改期。','予定時刻を逃しました。1回実行・スキップ・日時変更を選んでください。'],
    'Check the prior execution before starting another occurrence':['请先核对上次执行，再决定是否启动下一次。','次の実行を始める前に前回の結果を確認してください。'],
    'Execution started; inspect its actual result':['本次执行已启动，请查看实际结果。','実行を開始しました。実際の結果を確認してください。'],
    'Automatic starts paused; current execution continues':['已暂停后续自动启动，当前执行继续。','自動開始を一時停止しました。現在の実行は継続します。'],
    'Arrangement disabled; current execution is unchanged':['安排已停用，当前执行不受影响。','予定を無効にしました。現在の実行は変わりません。'],
    'User skipped the missed occurrence':['你已跳过本次安排。','今回の予定をスキップしました。'],
    'Extra command profiles (optional, advanced)':['额外命令配置（可选，高级）','追加コマンド設定（任意・詳細）'],
    'Most tasks should leave this empty. It describes fixed command profiles for a connected Linux or WSL Worker; it does not grant command permission. Each command still needs separate authorization.':['大多数任务留空即可。这里只描述连接的 Linux／WSL Worker 可用的固定命令，不会授予命令权限；每次执行仍须单独获准。','ほとんどのタスクでは空欄のままにします。接続された Linux／WSL Worker 用の固定コマンドを記述しますが、コマンド権限は付与しません。実行には別途許可が必要です。'],
    'Most workflows should leave this empty. It describes fixed command profiles for a connected Linux or WSL Worker; it does not grant command permission. Each command still needs separate authorization.':['大多数工作流留空即可。这里只描述连接的 Linux／WSL Worker 可用的固定命令，不会授予命令权限；每次执行仍须单独获准。','ほとんどのワークフローでは空欄のままにします。接続された Linux／WSL Worker 用の固定コマンドを記述しますが、コマンド権限は付与しません。実行には別途許可が必要です。'],
    'Use the task instructions above for natural-language requests. This box only lists fixed command IDs and arguments; unlisted commands cannot be selected by the Worker.':['自然语言请求请写在上方任务说明中。此处仅列出固定命令 ID 和参数，Worker 不能选择列表外的命令。','自然言語の依頼は上の指示欄に記入してください。ここには固定コマンドの ID と引数のみを記述し、一覧にないコマンドは Worker が選択できません。'],
    'Command profiles (JSON)':['命令配置（JSON）','コマンド設定（JSON）'],
    'Commands must be a JSON array.':['命令配置必须是 JSON 数组。','コマンド設定は JSON 配列で入力してください。'],
    'This task requires a connected Linux/WSL Worker with the configured command adapter; the local factory does not enable commands automatically.':['此任务需要已接入的 Linux／WSL 受控命令 Worker；工厂不会自动开启本机命令能力。','このタスクには接続済みの Linux／WSL 制御コマンド Worker が必要です。ファクトリーはローカルのコマンド権限を自動で有効にしません。'],
    'Work on files in this location':['在此工作目录处理文件','この作業場所でファイルを扱う'],
    'Workspace task':['工作区任务','作業場所のタスク'],
    'Without this option, the Worker only returns text. Other host paths remain blocked. Commands require separate permissions.':['不勾选时仅返回文本。不会因此授权其他主机路径；命令需要单独权限。','未選択の場合はテキストのみ返します。他のホストパスは許可されず、コマンドには別の権限が必要です。'],
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
    'Adjust future steps':['调整后续步骤','後続ステップを調整'],
    'Pause the workflow first. A read-only Worker can suggest content or structural changes. Review the full diff and graph before applying. Resuming or stopping discards the suggestion.':['先暂停工作流，再由只读 Worker 提议修改步骤内容或流程结构。请检查完整差异和流程图后再应用。继续或停止会放弃建议。','先にワークフローを一時停止し、読み取り専用 Worker に内容または構造の変更を提案させます。差分とフロー図全体を確認してから適用してください。再開・停止すると提案は破棄されます。'],
    'Suggestion type':['建议类型','提案の種類'], 'Change existing steps':['调整现有步骤内容','既存ステップの内容を変更'],'Restructure future steps':['调整后续步骤结构','後続ステップの構造を変更'],
    'Diagram order affects display only; routes control execution order. Added steps use automatic assignment and have no file or command tools.':['图中排列只影响显示，执行顺序由路线决定。新增步骤由系统自动分配，不具有文件或命令工具。','図の並び順は表示のみで、実行順は経路で決まります。追加ステップは自動割り当てとなり、ファイル・コマンドツールは使えません。'],
    'Pause following steps before requesting a structure change':['请求结构调整前，请先暂停工作流。','構造変更を依頼する前にワークフローを一時停止してください。'],
    'The workflow has no remaining steps to restructure':['当前工作流没有可调整的后续步骤。','変更できる後続ステップがありません。'],
    'Workflow graph and context are too large for a structural suggestion':['流程图和上下文过长，暂时无法生成结构建议。','フロー図とコンテキストが長すぎるため、構造案を作成できません。'],
    'Future steps':['尚未派发的步骤（可多选）','未割り当てのステップ（複数選択可）'],'Requested change':['希望如何调整','変更したい内容'],'Generate suggestion':['生成调整建议','変更案を作成'],
    'Added step':['新增步骤','追加ステップ'],'Removed step':['删除步骤','削除ステップ'],'Route change':['路线调整','経路の変更'],
    'Current route':['原路线','現在の経路'],'Proposed route':['建议路线','提案された経路'],'Display order':['图中顺序','図の表示順'],'Current order':['原顺序','現在の順序'],'Proposed order':['建议顺序','提案された順序'],
    'Suggestion generation task (usage is recorded separately)':['查看建议生成任务（用量单独记录）','提案作成タスクを表示（使用量は別に記録）'],
    'Current step':['原步骤','現在のステップ'],'Suggested step':['建议步骤','変更案のステップ'],
    'Preview the changed workflow':['预览调整后的流程','変更後のフローをプレビュー'],'Confirm and apply':['确认应用','確認して適用'],'Discard suggestion':['放弃建议','提案を破棄'],
    'Pause following steps before requesting a change':['请先暂停后续步骤，再请求调整。','変更依頼の前に後続ステップを一時停止してください。'],
    'Only an undispatched node can change':['只能调整尚未派发或提问的节点。','未割り当て・未質問のノードのみ変更できます。'],
    'Discard the current suggestion before requesting another':['请先放弃当前建议，再生成新建议。','新しい提案の前に現在の提案を破棄してください。'],
    'Execution changed; request a new suggestion':['执行状态已变化，请放弃旧建议后重新生成。','実行が変わりました。提案を破棄して再作成してください。'],
    'Select one to eight distinct pending nodes':['请选择 1 到 8 个不同的尚未派发节点。','未割り当ての異なるノードを1～8件選択してください。'],
    'Workflow context is too large for a coordinated suggestion; select fewer nodes or request one change at a time':['当前工作流上下文过长，无法生成协调建议；请减少所选节点，或逐项请求调整。','ワークフローの文脈が長すぎて一括提案できません。選択ノードを減らすか、一件ずつ依頼してください。'],
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
    'Required Skills':['必需 Skills','必須 Skills'],
    'Task method':['本次任务方法','今回の作業方法'],
    'Choose any number of installed Skills this task must use. Leave empty for automatic selection.':['可多选本任务必须使用的已安装 Skill；留空表示由 Worker 自行选择。','このタスクで必須のインストール済み Skill を複数選べます。空欄なら Worker が選びます。'],
    'Only catalogued Skills appear here. Write one-task instructions under Task sources; external downloads are not enabled.':['这里只显示平台目录中的 Skill。单次任务说明可直接写在“任务资料”中；目前没有开放外部下载。','ここにはプラットフォームのカタログにある Skill のみ表示されます。今回だけの指示は「作業資料」に記述できます。外部ダウンロードは現在無効です。'],
    'Choose any number of installed Skills this step must use. Leave empty for automatic selection.':['可多选本步骤必须使用的已安装 Skill；留空表示由 Worker 自行选择。','このステップで必須のインストール済み Skill を複数選べます。空欄なら Worker が選びます。'],
    'Write a task-specific method or reference':['直接写入本次工作的方法或资料','今回の作業方法・資料を直接入力'],
    'Add written source':['添加文字资料','テキスト資料を追加'],
    'Written method':['手写方法','記述した方法'], 'Written reference':['手写参考资料','記述した参考資料'],
    'This text is attached to this task or saved workflow. It is not installed as a shared Skill.':['这段文字只附加到本任务或已保存的工作流中，不会安装成共享 Skill。','この内容はタスクまたは保存済みワークフローに添付されます。共有 Skill としては登録されません。'],
    'Choose a step to delete. The final Done step cannot be removed.':['请选择要删除的步骤。最后的“完成”步骤不能删除。','削除するステップを選んでください。最後の「完了」ステップは削除できません。'],
    'Drag a step onto the diagram to add it. Drag an output dot to another step to connect them. Double-click a step to edit it, or select it and use Edit selected step.':['将步骤拖到图中可添加；拖动右侧圆点到另一节点可连线；双击节点编辑，也可选中节点后点“编辑所选步骤”。','ステップを図にドラッグして追加します。右側の丸を別のステップへドラッグして接続し、ダブルクリックで編集できます。選択して「選択したステップを編集」も使えます。'],
    'Delete selected step':['删除所选步骤','選択したステップを削除'], 'Edit selected step':['编辑所选步骤','選択したステップを編集'], 'Edit step':['编辑步骤','ステップを編集'],
    'Step settings':['步骤设置','ステップ設定'], 'Done':['完成','完了'],
    'The selected step cannot be deleted.':['所选步骤不能删除。','選択したステップは削除できません。'],
    'Deleting this decision sends incoming routes to Done. Continue?':['删除此判断步骤后，指向它的连线会改为指向“完成”。是否继续？','この判断ステップを削除すると、ここへ向かう経路は「完了」に進みます。続行しますか？'],
    'Connect a step to itself is not allowed.':['不能将步骤连接到自身。','ステップを自分自身に接続できません。'],
    'Connected steps. Save the workflow to keep this change.':['已连接步骤。保存工作流后保留此修改。','ステップを接続しました。ワークフローを保存してください。'],
    'Step added. Save the workflow to keep this change.':['已添加步骤。保存工作流后保留此修改。','ステップを添加しました。ワークフローを保存してください。'],
    'Saved workflows are reusable. Every start creates a separate run from a frozen copy; later edits do not change existing runs.':['已保存的工作流可重复使用。每次启动都会基于固定副本创建独立执行，后续编辑不会改变已启动的执行。','保存済みワークフローは再利用できます。開始ごとに固定コピーから別の実行を作り、後の編集は既存の実行に影響しません。'],
    'Open a saved workflow to edit it, make a separate copy, or schedule it. Starting it creates its own execution.':['打开已保存的工作流，可以编辑、复制为新工作流或设置定时安排。每次启动都会创建独立执行。','保存済みワークフローを開いて編集・複製・予定できます。開始ごとに独立した実行が作成されます。'],
    'Information for this run':['本次执行的补充信息','今回の実行情報'],
    'Enter facts or case details that change each time you run this workflow. Shared documents and methods belong under Task sources above.':['填写每次运行会变化的事实或个案信息。所有执行共用的文档和方法请放在上方“任务资料”中。','実行ごとに変わる事実や案件情報を入力します。共通の文書や方法は上の「作業資料」に追加してください。'],
    'Attach notes or methods to this task or saved workflow. They are sent with each run. A written method here is not installed as a shared Skill. Up to 8 sources, 128 KiB each, 512 KiB total.':['把说明或方法附加到本任务或已保存的工作流，每次执行都会发送。这里写的方法不会安装成共享 Skill。最多 8 项，每项 128 KiB，总计 512 KiB。','メモや方法をタスクまたは保存済みワークフローに添付し、各実行で送信します。ここに書いた方法は共有 Skill として登録されません。最大8件、各128 KiB、合計512 KiBです。'],
    'Write some text first.':['请先写入内容。','先に内容を入力してください。'],
    'No installed Skills are available. You can write a task-specific method in Task sources.':['当前没有已安装 Skill。可以在“任务资料”中直接写入本次方法。','インストール済み Skill がありません。「作業資料」に今回の方法を直接記述できます。'],
    'Delete step':['删除步骤','ステップを削除'],
    'Make a copy':['复制为新工作流','新しいワークフローとして複製'],
  };
  const t = value => {
    const locale = window.aiwI18n?.locale || document.documentElement.lang;
    return words[value]?.[locale?.startsWith('zh') ? 0 : locale?.startsWith('ja') ? 1 : 2] || value;
  };
  const status = message => { $('[data-wf-status]').textContent = message; };
  let definitions=[], teamSkills=[], teamMembers=[], taskSources=[], taskSkills=[], editing=null, selectedRun=null, currentView=null, savedSpec='', busy=false, signature='', selectedNodeKey=null;
  let scheduleSpec=null,scheduleRequest=crypto.randomUUID();
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
  function commands(control){
    try{const result=JSON.parse(control.value.trim()||'[]');if(!Array.isArray(result))throw Error();return result;}
    catch(_){throw Error(t('Commands must be a JSON array.'));}
  }
  async function act(work) {
    if(busy)return;busy=true;status('');
    const buttons=$$('button');buttons.forEach(b=>b.disabled=true);
    try {await work();} catch(error) {status(error instanceof TypeError?t('Request outcome is unknown. Check execution history before submitting again.'):t(error.message));}
    finally {busy=false;buttons.forEach(b=>{if(b.isConnected)b.disabled=false;});if(currentView)controlButtons(currentView.run,currentView.task);}
  }
  function element(tag,text,className) {const e=document.createElement(tag);if(text!==undefined)e.textContent=text;if(className)e.className=className;return e;}
  function button(label,callback) {const b=element('button',t(label),'btn ghost');b.type='button';b.onclick=callback;return b;}
  function translate() {
    document.title=t(pageMode==='tasks'?'Simple task':'Workflows');
    $$('[data-wf-label]').forEach(e=>e.textContent=t(e.dataset.wfLabel));
    $$('[data-wf-new], [data-wf-sample], [data-wf-refresh], [data-wf-save], [data-wf-start], [data-wf-control]').forEach(e=>{
      e.dataset.wfOriginal ||= e.textContent;e.textContent=t(e.dataset.wfOriginal);
    });
  }
  function panel(name) {['task','editor','run'].forEach(p=>$(`[data-wf-${p}]`).hidden=p!==name);}
  function singleSpec() {return {title:t('New workflow'),goal:'',entry:'task',nodes:[
    {key:'task',title:t('Task'),kind:'TASK',instruction:'',next:'end',branches:{},assignment:'AUTO',requiredSkills:[]},
    {key:'end',title:t('Done'),kind:'END',instruction:'',next:null,branches:{}}]};}
  function showEditor(definition) {
    selectedRun=null;editing=structuredClone(definition || {id:null,version:0,spec:singleSpec()});
    selectedNodeKey=editing.spec.nodes[0]?.key||null;
    editing.spec.sources ||= [];
    $('[data-wf-commands]').value=JSON.stringify(editing.spec.commands||[],null,2);
    renderSources($('[data-wf-definition-sources]'),editing.spec.sources,values=>editing.spec.sources=values);
    for(const n of editing.spec.nodes)if(['TASK','DECISION'].includes(n.kind))n.assignment ||= n.memberId?'MEMBER':definition?.id?'GENERAL':'AUTO';
    $('[data-wf-title]').value=editing.spec.title;$('[data-wf-goal]').value=editing.spec.goal;
    $('[data-wf-input]').value='';startRequest=newRequest();savedSpec=definition?JSON.stringify(editing.spec):'';
    panel('editor');renderNodes();graph($('[data-wf-definition-graph]'),editing.spec);status('');
  }
  function field(label,input) {const wrapper=element('label',undefined,'field');wrapper.append(element('span',t(label)),input);return wrapper;}
  function renderSources(host,values,change){
    host.replaceChildren(element('h3',t('Task sources')),element('p',t('Attach notes or methods to this task or saved workflow. They are sent with each run. A written method here is not installed as a shared Skill. Up to 8 sources, 128 KiB each, 512 KiB total.'),'help'));
    const kind=element('select');kind.append(new Option(t('Reference knowledge'),'knowledge'),new Option(t('Task method'),'skill'));
    const written=element('textarea');written.rows=4;written.maxLength=131072;written.placeholder=t('Write a task-specific method or reference');
    const file=element('input');file.type='file';file.multiple=true;file.accept='.txt,.md,.markdown,.json,.csv';
    const addWritten=button('Add written source',()=>{
      const content=written.value.trim(),bytes=new TextEncoder().encode(content).length;if(!content){status(t('Write some text first.'));return;}if(content.includes('\0')||bytes>131072){status(t('A text source must contain 1 to 128 KiB'));return;}
      const sourceKind=kind.value,title=sourceKind==='skill'?'Written method':'Written reference';
      const next=[...values,{kind:sourceKind,title,content}],size=next.reduce((total,item)=>total+new TextEncoder().encode(item.content).length,0);
      if(next.length>8||size>524288){status(t('Select up to eight text sources totaling 512 KiB'));return;}
      change(next);renderSources(host,next,change);status('');
    });
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
    host.append(field('Source type',kind),field('Write a task-specific method or reference',written),addWritten,field('Add text files',file),element('p',t('This text is attached to this task or saved workflow. It is not installed as a shared Skill.'),'help'));
      values.forEach((source,index)=>{const row=element('div',undefined,'wf-actions');row.append(element('span',t(source.title)+' · '+t(source.kind==='skill'?'Task method':'Reference knowledge')),button('Remove',()=>{const next=values.filter((_,i)=>i!==index);change(next);renderSources(host,next,change);}));host.append(row);});
  }
  function input(value,change,max=8000) {const e=element('input');e.value=value||'';e.maxLength=max;e.oninput=()=>change(e.value);return e;}
  function target(value,change) {
    const select=element('select');select.append(new Option('—',''));
    editing.spec.nodes.forEach(n=>select.append(new Option(n.title,n.key)));select.value=value||'';
    select.onchange=()=>{change(select.value||null);redraw();};return select;
  }
  const redraw=()=>graph($('[data-wf-definition-graph]'),editing.spec);
  function renderSkillPicker(host,values,change){
    host.replaceChildren();
    for(const skill of new Set([...teamSkills,...values])){
      const label=element('label',undefined,'wf-skill-option'),checkbox=element('input');
      checkbox.type='checkbox';checkbox.checked=values.includes(skill);checkbox.disabled=!checkbox.checked&&values.length>=8;
      checkbox.onchange=()=>{const next=checkbox.checked?[...values,skill]:values.filter(value=>value!==skill);change(next);renderSkillPicker(host,next,change);};
      label.append(checkbox,element('span',skill));host.append(label);
    }
    if(!host.children.length)host.append(element('p',t('No installed Skills are available. You can write a task-specific method in Task sources.'),'help'));
  }
  function removeNode(key){
    const node=editing.spec.nodes.find(item=>item.key===key);if(!node||node.kind==='END'){status(t('The selected step cannot be deleted.'));return;}
    const branching=Object.keys(node.branches||{}).length>0,hasIncoming=editing.spec.nodes.some(item=>item.next===key||Object.values(item.branches||{}).includes(key));
    if(branching&&hasIncoming&&!window.confirm(t('Deleting this decision sends incoming routes to Done. Continue?')))return;
    const bypass=!branching?node.next||'end':'end';
    editing.spec.nodes=editing.spec.nodes.filter(item=>item.key!==key);
    for(const item of editing.spec.nodes){if(item.next===key)item.next=bypass;for(const choice of Object.keys(item.branches||{}))if(item.branches[choice]===key)item.branches[choice]=bypass;}
    if(editing.spec.entry===key)editing.spec.entry=editing.spec.nodes[0]?.key||'end';
    if($('[data-wf-node-dialog]').open)$('[data-wf-node-dialog]').close();
    selectedNodeKey=editing.spec.nodes.find(item=>item.kind!=='END')?.key||'end';renderNodes();redraw();
  }
  function addNode(kind='TASK'){
    const source=editing.spec.nodes.find(item=>item.key===selectedNodeKey&&item.kind!=='END')||[...editing.spec.nodes].reverse().find(item=>item.kind!=='END');
    const endIndex=editing.spec.nodes.findIndex(item=>item.kind==='END'),key='step'+newRequest().replaceAll('-','').slice(0,8);
    const branch=kind==='DECISION'||kind==='ASK',node={key,title:t(({TASK:'Task',DECISION:'LLM decision',ASK:'Ask user'})[kind]||'Task'),kind,instruction:'',next:branch?null:'end',branches:branch?{'Option 1':'end'}:{},assignment:'AUTO',requiredSkills:[]};
    editing.spec.nodes.splice(endIndex<0?editing.spec.nodes.length:endIndex,0,node);
    if(source){
      if(source.kind==='TASK'){node.next=source.next||'end';source.next=key;}
      else {source.branches||={};if(!Object.keys(source.branches).length&&source.next)source.branches.Continue=source.next;let choice='Option '+(Object.keys(source.branches).length+1);while(choice in source.branches)choice+=' ';source.branches[choice]=key;source.next=null;}
    }
    selectedNodeKey=key;renderNodes();redraw();status(t('Step added. Save the workflow to keep this change.'));
  }
  function connectNodes(sourceKey,targetKey){
    if(sourceKey===targetKey){status(t('Connect a step to itself is not allowed.'));return;}
    const source=editing.spec.nodes.find(item=>item.key===sourceKey),target=editing.spec.nodes.find(item=>item.key===targetKey);
    if(!source||!target||source.kind==='END')return;
    if(source.kind==='TASK'){source.next=targetKey;source.branches={};}
    else {source.branches||={};if(!Object.keys(source.branches).length&&source.next)source.branches.Continue=source.next;let choice='Option '+(Object.keys(source.branches).length+1);while(choice in source.branches)choice+=' ';source.branches[choice]=targetKey;source.next=null;}
    selectedNodeKey=sourceKey;renderNodes();redraw();status(t('Connected steps. Save the workflow to keep this change.'));
  }
  function renderNodes() {
    const list=$('[data-wf-nodes]');list.replaceChildren();
    for(const node of editing.spec.nodes) {
      if(node.key!==selectedNodeKey)continue;
      const section=element('section',undefined,'wf-node-editor');section.id='wf-node-'+node.key;
      const row=element('div',undefined,'wf-node-fields'),kind=element('select');
      for(const [value,label] of [['TASK','Task'],['DECISION','LLM decision'],['ASK','Ask user'],['END','Done']])kind.append(new Option(t(label),value));
      kind.value=node.kind;kind.onchange=()=>{node.kind=kind.value;if(node.kind!=='TASK')node.workspaceTools=false;if(!['TASK','DECISION'].includes(node.kind)){node.memberId=null;node.assignment=null;node.requiredSkills=[];}else node.assignment ||= 'AUTO';node.branches={};node.next=node.kind==='END'||node.kind==='DECISION'?null:'end';if(node.kind==='DECISION')node.branches={'yes':'end','no':'end'};renderNodes();redraw();};
      row.append(field('Title',input(node.title,v=>{node.title=v;redraw();},160)),field('Type',kind));section.append(row);
      if(node.kind!=='END') {
        const instruction=element('textarea');instruction.value=node.instruction;instruction.rows=3;instruction.maxLength=8000;instruction.oninput=()=>node.instruction=instruction.value;
        section.append(field('Instruction',instruction));
        if(node.kind==='TASK'){const files=element('input');files.type='checkbox';files.checked=!!node.workspaceTools;files.onchange=()=>node.workspaceTools=files.checked;section.append(field('Work on files in this location',files));}
        if(['TASK','DECISION'].includes(node.kind)){const select=element('select');select.dataset.i18nSkip='';memberOptions(select,node.memberId||node.assignment||'GENERAL');select.onchange=()=>Object.assign(node,assignment(select.value));section.append(field('Team member',select));const skillField=element('div',undefined,'field');skillField.append(element('span',t('Required Skills')),element('p',t('Choose any number of installed Skills this step must use. Leave empty for automatic selection.'),'help'));const picker=element('div',undefined,'wf-skill-picker');renderSkillPicker(picker,node.requiredSkills||[],values=>node.requiredSkills=values);skillField.append(picker);section.append(skillField);}
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
      section.append(button('Delete step',()=>removeNode(node.key)));list.append(section);
    }
    const dialog=$('[data-wf-node-dialog]');if(dialog.open)dialog.querySelector('[data-wf-label="Edit step"]').textContent=t('Edit step');
    const selected=editing.spec.nodes.find(item=>item.key===selectedNodeKey),deleteButton=$('[data-wf-delete-node]');deleteButton.disabled=!selected||selected.kind==='END';$('[data-wf-open-node]').disabled=!selected;
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
        const path=make('path',{d:`M ${from.x+220} ${from.y+30} C ${from.x+270+30*i} ${from.y+30}, ${to.x-50-30*i} ${to.y+30}, ${to.x} ${to.y+30}`,'marker-end':`url(#${marker.id})`,class:taken.has(n.key+'\n'+key)?'wf-taken':''});svg.append(path);
        if(n.branches&&Object.keys(n.branches).length){const label=make('text',{x:(from.x+to.x)/2+120,y:(from.y+to.y)/2+82+i*15});label.textContent=choice;svg.append(label);}
      });
    }
    const visited=new Set(run?.events.filter(e=>['RESULT','ANSWER','COMPLETED'].includes(e.type)).map(e=>e.node)||[]);
    for(const n of nodes) {
      const p=positions.get(n.key),group=make('g',{class:run?.current===n.key?'wf-current':visited.has(n.key)?'wf-visited':!run&&selectedNodeKey===n.key?'wf-selected':''});
      if(!run)group.dataset.nodeKey=n.key;
      group.append(make('rect',{x:p.x,y:p.y,width:220,height:60,rx:n.kind==='DECISION'?22:8}));
      const kind=make('text',{x:p.x+12,y:p.y+21});kind.textContent=t(n.workspaceTools?'Workspace task':{TASK:'Task',DECISION:'LLM decision',ASK:'Ask user',END:'Done'}[n.kind]);
      const title=make('text',{x:p.x+12,y:p.y+44});title.textContent=n.title.length>22?n.title.slice(0,21)+'…':n.title;
      const tooltip=make('title',{});tooltip.textContent=n.title+'\n'+n.instruction;group.append(kind,title,tooltip);
      if(!run){
        group.setAttribute('tabindex','0');group.style.cursor='pointer';
        const inputPort=make('circle',{cx:p.x,cy:p.y+30,r:6,class:'wf-port wf-input-port'}),outputPort=make('circle',{cx:p.x+220,cy:p.y+30,r:7,class:'wf-port wf-output-port'});
        group.append(inputPort,outputPort);
        group.onclick=()=>{selectedNodeKey=n.key;renderNodes();redraw();};
        group.ondblclick=()=>openNodeEditor(n.key);
        group.onkeydown=e=>{if(e.key==='Enter')openNodeEditor(n.key);};
        outputPort.onpointerdown=e=>{e.preventDefault();e.stopPropagation();svg.dataset.connectSource=n.key;svg.dataset.connectX=String(e.clientX);svg.dataset.connectY=String(e.clientY);};
      }
      svg.append(group);
    }
    if(!run){
      svg.onpointerup=e=>{
        const source=svg.dataset.connectSource;if(!source)return;delete svg.dataset.connectSource;
        if(Math.hypot(e.clientX-Number(svg.dataset.connectX),e.clientY-Number(svg.dataset.connectY))<8)return;
        const target=document.elementFromPoint(e.clientX,e.clientY)?.closest('[data-node-key]')?.dataset.nodeKey;
        if(target)connectNodes(source,target);
      };
      container.ondragover=e=>{if(Array.from(e.dataTransfer.types).includes('text/plain'))e.preventDefault();};
      container.ondrop=e=>{e.preventDefault();const kind=e.dataTransfer.getData('text/plain');if(['TASK','DECISION','ASK'].includes(kind))addNode(kind);};
    }
    container.append(svg);
  }
  function openNodeEditor(key){selectedNodeKey=key;renderNodes();$('[data-wf-node-dialog]').showModal();redraw();}
  async function library() {
    const [data,schedules]=await Promise.all([api('?kind='+pageMode),api('/schedules')]);definitions=data.definitions;
    const list=$('[data-wf-definitions]');list.replaceChildren();
    if(!definitions.length)list.append(element('p',t('No saved workflows'),'help'));
    definitions.forEach(d=>{const b=button('',()=>showEditor(d));b.textContent=d.spec.title;b.dataset.i18nSkip='';b.classList.add('wf-item');const copy=button('Make a copy',()=>showEditor({id:null,version:0,spec:structuredClone(d.spec)}));list.append(b,copy,button('Schedule',()=>act(()=>arrange(d.spec))));});
    const runs=$('[data-wf-executions]');runs.replaceChildren();
    if(!data.executions.length)runs.append(element('p',t('No executions yet'),'help'));
    data.executions.forEach(r=>{const b=button('',()=>act(()=>openRun(r.id)));b.textContent=r.title||r.id;b.dataset.i18nSkip='';b.classList.add('wf-item');b.append(element('small',t(r.state)+' · '+new Date(r.updated_ms).toLocaleString()));runs.append(b);});
    loadMembers();
    renderSchedules(schedules);
  }
  function localDateInput(value){const d=new Date(value);return new Date(value-d.getTimezoneOffset()*60000).toISOString().slice(0,16);}
  function arrange(spec){
    if(spec.nodes.some(n=>n.workspaceTools)||(spec.commands||[]).length)throw new Error('Schedules currently support text workflows only');
    scheduleSpec=structuredClone(spec);scheduleRequest=crypto.randomUUID();
    const details=$('[data-wf-schedule-editor]');details.hidden=false;details.open=true;
    $('[data-wf-schedule-title]').textContent=spec.title;
    $('[data-wf-schedule-content]').textContent=JSON.stringify(spec,null,2);
    $('[data-wf-schedule-zone]').textContent=t('Time zone')+': '+Intl.DateTimeFormat().resolvedOptions().timeZone;
    $('[data-wf-schedule-form]').elements.firstAt.value=localDateInput(Date.now()+3600000);
  }
  function renderSchedules(rows){
    const host=$('[data-wf-schedules]');host.replaceChildren();
    if(!rows.length)host.append(element('p',t('No arrangements yet'),'help'));
    for(const row of rows){
      const entry=element('details',undefined,'wf-event');entry.append(element('summary',row.title+' · '+t(row.state)));
      entry.append(element('p',new Date(row.next_ms).toLocaleString(window.aiwI18n?.locale||undefined,{timeZone:row.zone_id})+' · '+row.zone_id),element('p',t(row.message),'help'));
      if(row.last_execution){const link=element('a',t('View execution')+' · '+t(row.last_state));link.href='/ui/tasks?execution='+encodeURIComponent(row.last_execution);entry.append(link);}
      const controls=element('div',undefined,'wf-actions');
      controls.append(button('New arrangement with captured work',()=>act(async()=>arrange((await api('/schedules/'+encodeURIComponent(row.id))).spec))));
      const send=(action,extra={})=>act(async()=>{if(action==='run-now'&&!window.confirm(t('Run this occurrence now? Check the prior result first.')))return;await api('/schedules/'+encodeURIComponent(row.id)+'/control',{version:row.version,action,...extra});await library();});
      if(row.state==='ACTIVE')controls.append(button('Pause arrangement',()=>send('pause')));
      if(['PAUSED','NEEDS_CONFIRMATION'].includes(row.state))controls.append(button('Checked; resume arrangement',()=>send('resume')));
      if(!['STOPPED','TRIGGERED'].includes(row.state)){
        controls.append(button('Run once now',()=>send('run-now')),button('Skip occurrence',()=>send('skip')),button('Disable arrangement',()=>send('stop')));
        const date=element('input');date.type='datetime-local';date.value=localDateInput(Math.max(Date.now()+3600000,row.next_ms));date.ariaLabel=t('First time (browser time zone)');
        controls.append(date,button('Reschedule',()=>send('reschedule',{firstAt:new Date(date.value).getTime()})));
      }
      entry.append(controls);host.append(entry);
    }
  }
  let teamLoading=false;
  async function loadMembers(){
    if(teamLoading)return;teamLoading=true;
    try{
      const response=await fetch('/api/r1/team-members',{credentials:'same-origin'});if(!response.ok||response.redirected)throw new Error();
      const team=await response.json();if(!Array.isArray(team.members))throw new Error();teamMembers=team.members;teamSkills=team.skills||[];
      memberOptions($('[data-wf-member]'),$('[data-wf-member]').value);renderSkillPicker($('[data-wf-skills]'),taskSkills,values=>taskSkills=values);if(editing&&!$('[data-wf-editor]').hidden)renderNodes();
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
  function syncAmendmentMode(){
    const form=$('[data-wf-amendment-form]'),mode=$('[data-wf-amendment-mode]').value,select=$('[data-wf-amendment-node]');
    $('[data-wf-amendment-nodes]').hidden=mode==='STRUCTURE';select.required=mode==='CONTENT';
    $('[data-wf-amendment-structure-note]').hidden=mode!=='STRUCTURE';
    form.querySelector('button').disabled=mode==='CONTENT'&&!select.options.length;
  }
  function renderAmendment(data){
    const run=data.run,proposal=data.proposal,host=$('[data-wf-amendment]'),result=$('[data-wf-amendment-result]'),form=$('[data-wf-amendment-form]'),select=$('[data-wf-amendment-node]');
    host.hidden=['COMPLETED','STOPPED'].includes(run.state)&&!proposal;
    const stamp=JSON.stringify([run.id,run.version,proposal]);if(host.dataset.stamp===stamp)return;
    const changedRun=host.dataset.run!==run.id;host.dataset.run=run.id;host.dataset.stamp=stamp;
    if(changedRun){form.reset();amendmentRequest=newRequest();}
    const previous=[...select.selectedOptions].map(option=>option.value);select.replaceChildren();
    run.spec.nodes.filter(n=>n.kind!=='END'&&!(run.visits?.[n.key])&&!run.events.some(e=>e.node===n.key&&['DISPATCHED','QUESTION'].includes(e.type))).forEach(n=>select.add(new Option(n.title,n.key)));
    for(const option of select.options)option.selected=previous.includes(option.value);
    form.hidden=run.state!=='PAUSED'||!!proposal;syncAmendmentMode();result.replaceChildren();
    if(!proposal)return;
    result.append(element('p',t(proposal.status),'help'));
    if(proposal.status==='STOPPING')result.append(element('p',t('If suggestion generation is still running, wait for its stop acknowledgement before resuming.'),'help'));
    const link=element('a',t('Suggestion generation task (usage is recorded separately)'));link.href='/ui/tasks?execution='+encodeURIComponent(proposal.helperRun);result.append(link);
    if(proposal.error)result.append(element('p',t(proposal.error),'help'));
    const changes=proposal.changes?.length?proposal.changes:proposal.candidate?[{before:proposal.before,candidate:proposal.candidate}]:[];
    if(proposal.candidateGraph){
      const original=new Map(run.spec.nodes.map(node=>[node.key,node])),candidate=new Map(proposal.candidateGraph.nodes.map(node=>[node.key,node]));
      for(const change of changes){
        const before=change.before,next=change.candidate;
        if(!before)result.append(element('h4',t('Added step')+': '+next.title),element('pre',JSON.stringify({key:next.key,kind:next.kind,title:next.title,instruction:next.instruction,next:next.next,branches:next.branches},null,2)));
        else if(!next)result.append(element('h4',t('Removed step')+': '+before.title),element('pre',JSON.stringify({key:before.key,kind:before.kind,title:before.title},null,2)));
        else result.append(element('h4',t('Route change')+': '+before.title),element('p',t('Current route')),element('pre',JSON.stringify({next:before.next,branches:before.branches},null,2)),element('p',t('Proposed route')),element('pre',JSON.stringify({next:next.next,branches:next.branches},null,2)));
      }
      const oldOrder=run.spec.nodes.map(node=>node.key),newOrder=proposal.candidateGraph.nodes.map(node=>node.key);
      if(JSON.stringify(oldOrder)!==JSON.stringify(newOrder))result.append(element('h4',t('Display order')),element('p',t('Current order')+': '+oldOrder.map(key=>original.get(key)?.title||key).join(' → ')),element('p',t('Proposed order')+': '+newOrder.map(key=>candidate.get(key)?.title||key).join(' → ')));
      const preview=element('details'),canvas=element('div',undefined,'wf-graph');preview.append(element('summary',t('Preview the changed workflow')),canvas);result.append(preview);graph(canvas,{entry:proposal.candidateGraph.entry,nodes:proposal.candidateGraph.nodes});
      if(run.state==='PAUSED'&&proposal.status==='READY')result.append(button('Confirm and apply',()=>act(()=>decideAmendment(true))));
    } else if(changes.length){
      const replacements=new Map();
      for(const change of changes){
        const before=change.before,candidate=change.candidate;replacements.set(candidate.key,candidate);
        result.append(element('h4',t('Current step')+': '+before.title),element('pre',JSON.stringify({title:before.title,instruction:before.instruction,next:before.next,branches:before.branches},null,2)),
          element('h4',t('Suggested step')+': '+candidate.title),element('pre',JSON.stringify({title:candidate.title,instruction:candidate.instruction,next:candidate.next,branches:candidate.branches},null,2)));
      }
      const preview=element('details'),canvas=element('div',undefined,'wf-graph');preview.append(element('summary',t('Preview the changed workflow')),canvas);result.append(preview);
      graph(canvas,{...run.spec,nodes:run.spec.nodes.map(n=>replacements.get(n.key)||n)});
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
  $('[data-wf-task-form]').onsubmit=e=>{e.preventDefault();act(async()=>{const form=new FormData(e.target),run=await api('/tasks',{requestId:taskRequest,instruction:form.get('instruction'),location:form.get('location')||null,workspaceTools:form.has('workspaceTools'),commands:commands(e.target.elements.commands),...assignment($('[data-wf-member]').value),requiredSkills:taskSkills,sources:taskSources});taskRequest=newRequest();await library();await openRun(run.id);});};
  $$('[data-wf-new]').forEach(b=>b.onclick=()=>{if(b.dataset.wfNew==='task'){selectedRun=null;panel('task');}else showEditor();});
  $('[data-wf-sample]').onclick=example;
  $('[data-wf-refresh]').onclick=()=>act(async()=>{signature='';importAttempted=false;await refreshRun();await library();});
  $$('[data-wf-drag-kind]').forEach(control=>{
    let dragEndedAt=0;control.onclick=()=>{if(Date.now()-dragEndedAt>400)addNode(control.dataset.wfDragKind);};
    control.ondragstart=event=>event.dataTransfer.setData('text/plain',control.dataset.wfDragKind);
    control.ondragend=()=>dragEndedAt=Date.now();
  });
  $('[data-wf-open-node]').onclick=()=>selectedNodeKey&&openNodeEditor(selectedNodeKey);
  $('[data-wf-delete-node]').onclick=()=>removeNode(selectedNodeKey);
  $('[data-wf-node-close]').onclick=()=> $('[data-wf-node-dialog]').close();
  $('[data-wf-node-dialog]').addEventListener('click',event=>{if(event.target===$('[data-wf-node-dialog]'))event.target.close();});
  $('[data-wf-save]').onclick=()=>act(async()=>{editing.spec.title=$('[data-wf-title]').value;editing.spec.goal=$('[data-wf-goal]').value;editing.spec.commands=commands($('[data-wf-commands]'));editing=await api('/definitions',editing);savedSpec=JSON.stringify(editing.spec);startRequest=newRequest();await library();status(t('Workflow saved'));});
  $('[data-wf-start]').onclick=()=>act(async()=>{
    editing.spec.title=$('[data-wf-title]').value;editing.spec.goal=$('[data-wf-goal]').value;editing.spec.commands=commands($('[data-wf-commands]'));
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
    const mode=form.get('proposalMode'),nodes=form.getAll('nodes'),request={version:currentView.run.version,requestId:amendmentRequest,mode,change:form.get('change')};
    if(mode==='CONTENT'){request.node=nodes[0];request.nodes=nodes;}
    await api('/executions/'+encodeURIComponent(selectedRun)+'/amendments',request);
    signature='';await refreshRun();await library();
  });};
  $('[data-wf-amendment-mode]').onchange=()=>{amendmentRequest=newRequest();syncAmendmentMode();};
  $('[data-wf-schedule-task]').onclick=()=>act(()=>{
    const form=new FormData($('[data-wf-task-form]')),instruction=String(form.get('instruction')||'').trim();
    if(!instruction)throw new Error('Schedule work must have an instruction');
    if(form.has('workspaceTools')||commands($('[data-wf-task-form]').elements.commands).length)throw new Error('Schedules currently support text workflows only');
    if(String(form.get('location')||'').trim())throw new Error('Schedules use temporary locations; attach selected text sources');
    arrange({title:instruction.slice(0,80),goal:instruction,entry:'task',nodes:[
      {key:'task',title:'Task',kind:'TASK',instruction,next:'end',branches:{},...assignment($('[data-wf-member]').value),requiredSkills:taskSkills},
      {key:'end',title:'Done',kind:'END',instruction:'',next:null,branches:{}}],sources:taskSources,commands:[]});
  });
  $('[data-wf-schedule-form]').onsubmit=e=>{e.preventDefault();act(async()=>{
    const form=new FormData(e.target);
    await api('/schedules',{requestId:scheduleRequest,spec:scheduleSpec,repeat:form.get('repeat'),zone:Intl.DateTimeFormat().resolvedOptions().timeZone||'UTC',firstAt:new Date(form.get('firstAt')).getTime()});
    $('[data-wf-schedule-editor]').hidden=true;scheduleSpec=null;await library();
  });};
  window.addEventListener('aiw:locale-changed',()=>{translate();renderSources($('[data-wf-task-sources]'),taskSources,values=>taskSources=values);if(editing)renderSources($('[data-wf-definition-sources]'),editing.spec.sources||[],values=>editing.spec.sources=values);signature='';$('[data-wf-usage]').dataset.stamp='';$('[data-wf-amendment]').dataset.stamp='';if(editing){renderNodes();redraw();}act(async()=>{await library();await refreshRun();});});
  translate();renderSources($('[data-wf-task-sources]'),taskSources,values=>taskSources=values);if(pageMode==='tasks')panel('task');else showEditor();act(async()=>{
    await library();
    if(route.get('execution'))await openRun(route.get('execution'));
    else if(route.get('definition'))showEditor(await api('/definitions/'+encodeURIComponent(route.get('definition'))));
  });
  setInterval(()=>{if(selectedRun&&!busy&&!document.hidden&&!(preparingGoal&&importAttempted))act(refreshRun);},4000);
})();
