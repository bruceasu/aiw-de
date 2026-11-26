(() => {
  'use strict';
  const $ = s => document.querySelector(s), $$ = s => [...document.querySelectorAll(s)];
  const words = {
    'Acceptance criteria (optional)':['验收条件（可选）'], 'Required checks':['必需检查'],
    'Required checks need a compatible Python OpenAI Worker.':['必需检查需要兼容的 Python OpenAI Worker。'],
    'Only select commands that verify the result. Select fewer commands if each requires separate approval.':['只选择核验产物的命令。若每条都需要单独审批，可合并为一次检查。'],
    'Plane schedules':['Plane 定时安排'], 'Arrange text work':['安排文字工作'],
    'Schedule':['定时安排'], 'Save arrangement':['保存安排'], 'No arrangements yet':['尚无定时安排'],
    'First time (browser time zone)':['首次时间（浏览器时区）'], 'Repeat':['重复方式'],
    'Once':['仅一次'], 'Every day':['每天'], 'Every week':['每周'], 'Time zone':['时区'],
    'Pause arrangement':['暂停自动启动'], 'Checked; resume arrangement':['已核对，恢复安排'],
    'Run once now':['现在执行一次'], 'Skip occurrence':['跳过本次'], 'Reschedule':['改期'], 'Disable arrangement':['停用安排'],
    'View execution':['查看本次执行'], 'ACTIVE':['自动安排中'], 'NEEDS_CONFIRMATION':['等待你决定'], 'TRIGGERED':['本次已启动'],
    'Captured work':['已保存的工作内容'], 'New arrangement with captured work':['使用原内容新建安排'],
    'Pausing an arrangement leaves its current execution running. Use the execution page to stop that work.':['暂停或停用安排不会停止当前执行；请在执行页面停止该项工作。'],
    'The selected content is frozen. Later edits do not change this arrangement. Missed or overlapping work waits for your decision.':['安排保存当前内容快照，后续编辑不会自动替换。错过时间或上次尚未结束时，等待你决定。'],
    'Schedules currently support text workflows only':['当前定时安排只支持文字工作流，不授予文件或命令权限。'],
    'Schedule work must have an instruction':['请先填写工作内容和完成条件。'],
    'Run this occurrence now? Check the prior result first.':['现在执行一次？请先核对原结果及停止状态。'],
    'Check the original execution and its stop acknowledgement first':['原执行尚未确认结束，请先核对结果和停止回执。'],
    'Choose a future first time':['请选择将来的执行时间。'],
    'Schedules use temporary locations; attach selected text sources':['定时文字任务使用临时目录，请把所需资料明确加入任务资料。'],
    'Arrangement saved; the current content is frozen':['安排已保存，使用当前内容快照。'],
    'Schedule changed; reload':['安排已变化，请刷新后再操作。'],
    'Schedule request changed; inspect the original arrangement':['请求内容已变化，请先查看原安排。'],
    'Current schedule Owner required':['需要当前安排所属账号的操作权限。'],
    'User checked the prior result and resumed automatic starts':['你已核对原结果并恢复自动安排。'],
    'Choose run once, skip or reschedule; missed work was not started':['错过的工作尚未启动；请选择执行一次、跳过或改期。'],
    'User rescheduled after checking the prior result':['你已核对原结果并修改时间。'],
    'Missed time; choose run once, skip or reschedule':['已错过时间；请选择执行一次、跳过或改期。'],
    'Check the prior execution before starting another occurrence':['请先核对上次执行，再决定是否启动下一次。'],
    'Execution started; inspect its actual result':['本次执行已启动，请查看实际结果。'],
    'Automatic starts paused; current execution continues':['已暂停后续自动启动，当前执行继续。'],
    'Arrangement disabled; current execution is unchanged':['安排已停用，当前执行不受影响。'],
    'User skipped the missed occurrence':['你已跳过本次安排。'],
    'Allow autonomous project commands':['允许自主运行项目命令'],
    'Use only with a trusted Worker account. Commands have the same file and network access as the Worker account.':['请仅使用受信任的 Worker 账号。命令具有该账号的文件和网络访问权限。'],
    'Extra command profiles (optional, advanced)':['额外命令配置（可选，高级）'],
    'Most tasks should leave this empty. It describes fixed command profiles for a connected Linux or WSL Worker; it does not grant command permission. Each command still needs separate authorization.':['大多数任务留空即可。这里只描述连接的 Linux／WSL Worker 可用的固定命令，不会授予命令权限；每次执行仍须单独获准。'],
    'Most workflows should leave this empty. It describes fixed command profiles for a connected Linux or WSL Worker; it does not grant command permission. Each command still needs separate authorization.':['大多数工作流留空即可。这里只描述连接的 Linux／WSL Worker 可用的固定命令，不会授予命令权限；每次执行仍须单独获准。'],
    'Use the task instructions above for natural-language requests. This box only lists fixed command IDs and arguments; unlisted commands cannot be selected by the Worker.':['自然语言请求请写在上方任务说明中。此处仅列出固定命令 ID 和参数，Worker 不能选择列表外的命令。'],
    'Command profiles (JSON)':['命令配置（JSON）'],
    'Commands must be a JSON array.':['命令配置必须是 JSON 数组。'],
    'This task requires a connected Linux/WSL Worker with the configured command adapter; the local factory does not enable commands automatically.':['此任务需要已接入的 Linux／WSL 受控命令 Worker；工厂不会自动开启本机命令能力。'],
    'Work on files in this location':['在此工作目录处理文件'],
    'Leave the working location empty to use a temporary workspace.':['工作位置留空时，系统会分配临时工作目录。'],
    'Workspace task':['工作区任务'],
    'Without this option, the Worker only returns text. Other host paths remain blocked. Commands require separate permissions.':['不勾选时仅返回文本。不会因此授权其他主机路径；命令需要单独权限。'],
    'INTERRUPTED':['执行中断，结果待核对'],
    'Review and confirm Worker stopped':['核对并确认 Worker 已停止'],
    'Resolve interrupted execution':['核对中断并恢复'],
    'Operation to check':['待核对操作'],
    'Verified outcome':['核实结果'],
    'Operation succeeded':['操作已成功'],
    'Operation failed':['操作已失败'],
    'Operation did not start':['操作未开始'],
    'Evidence reference':['证据引用'],
    'Evidence SHA-256':['证据 SHA-256'],
    'Record verified outcome':['保存核实结果'],
    'I verified that the Worker and its processes have stopped.':['我已核实 Worker 及其启动的进程均已停止。'],
    'Confirm stopped':['确认已停止'],
    'Close':['关闭'],
    'Check the original workspace and record what happened before releasing the task.':['请先检查原工作区，记录待核对操作的实际结果。'],
    'Local Worker exit evidence found. Verify the processes have stopped before confirming.':['已找到本机 Worker 的退出记录。请核实进程已停止后确认。'],
    'Verify the original Worker has stopped and supply the evidence from its device.':['请在原 Worker 所在设备核实进程已停止，并填写那里的证据。'],
    'Approval permission is required to confirm a stop.':['需要审批权限才能确认停止。'],
    'No current execution is awaiting stop confirmation.':['当前没有等待确认停止的执行，请刷新。'],
    'The execution changed. Reload before confirming.':['执行状态已变化，请刷新后再确认。'],
    'Stop confirmed. Retry this step when ready.':['已确认停止。核对已有结果后可重做本步骤。'],
    'The operation could not be confirmed. Reload its current state before submitting again.':['未能确认操作回执。请刷新状态后再提交。'],
    'The model provider rejected the credentials or access. Check the connection in Agent Factory.':['模型服务拒绝了凭证或访问权限。请检查 Agent 工厂中的模型连接。'],
    'The model provider did not find the requested model or endpoint. Check the configuration and retry.':['模型服务找不到所选模型或接口。请检查配置后重试。'],
    'The model provider rejected the request. Check the model and reasoning settings.':['模型服务拒绝了请求。请检查模型和推理设置。'],
    'The model provider reported a rate or quota limit. Check the quota before retrying.':['模型服务报告限流或配额不足。请核对配额后重试。'],
    'workspace-lock':['工作区锁', 'Workspace lock'],
    'Waiting for the workspace to become available':['等待工作区可用'],
    'Preparing workspace':['准备工作区'],
    'Preparing task context':['整理任务上下文'],
    'Running model and tools':['运行模型和工具'],
    'Writing final report':['整理最终报告'],
    'DRAFT_RESULT':['补充前的结果草稿'],
    'Delivering task result':['交付任务结果'],
    'The workspace is locked and the Worker could not identify its owner. Check for an existing execution before retrying.':['工作区已锁定，Worker 无法识别持有者。重试前请检查是否有其他执行仍在使用该目录。'],
    'The Worker lease expired. Its process and external effects are not confirmed. Inspect the existing execution and request stop or reconcile it before choosing a retry.':['Worker 租约已过期，进程和外部操作的结果尚未确认。请先查看原执行并请求停止或核对结果，再决定是否重试。'],
    'An administrator revoked the original lease. Check existing results before explicitly choosing a retry; no work was automatically dispatched.':['管理员已撤销原租约。请核对已有结果后明确选择是否重试；没有自动重新派发。'],
    'RESULT_ACCEPTED':['已确认使用原结果'],
    'Use recorded result':['使用已记录的结果'],
    'Inspect existing execution':['查看原执行，核对结果与停止状态'],
    'A lost connection does not mean the Worker stopped. Check the existing execution before retrying.':['连接中断不代表 Worker 已停止。重试前，请核对原执行和已有结果。'],
    'Retry this step? Existing results will be retained; this starts another execution.':['重做本步骤？已有结果会保留，此操作会再次执行。'],
    'Use the recorded successful result and continue the workflow?':['使用已记录的成功结果，并继续工作流？'],
    'If suggestion generation is still running, wait for its stop acknowledgement before resuming.':['建议仍在生成时，先等待其停止确认，再继续流程。'],
    'Adjust future steps':['调整后续步骤'],
    'Pause the workflow first. A read-only Worker can suggest content or structural changes. Review the full diff and graph before applying. Resuming or stopping discards the suggestion.':['先暂停工作流，再由只读 Worker 提议修改步骤内容或流程结构。请检查完整差异和流程图后再应用。继续或停止会放弃建议。'],
    'Suggestion type':['建议类型'], 'Change existing steps':['调整现有步骤内容'],'Restructure future steps':['调整后续步骤结构'],
    'Diagram order affects display only; routes control execution order. Added steps use automatic assignment and have no file or command tools.':['图中排列只影响显示，执行顺序由路线决定。新增步骤由系统自动分配，不具有文件或命令工具。'],
    'Pause following steps before requesting a structure change':['请求结构调整前，请先暂停工作流。'],
    'The workflow has no remaining steps to restructure':['当前工作流没有可调整的后续步骤。'],
    'Workflow graph and context are too large for a structural suggestion':['流程图和上下文过长，暂时无法生成结构建议。'],
    'Future steps':['尚未派发的步骤（可多选）'],'Requested change':['希望如何调整'],'Generate suggestion':['生成调整建议'],
    'Added step':['新增步骤'],'Removed step':['删除步骤'],'Route change':['路线调整'],
    'Current route':['原路线'],'Proposed route':['建议路线'],'Display order':['图中顺序'],'Current order':['原顺序'],'Proposed order':['建议顺序'],
    'Suggestion generation task (usage is recorded separately)':['查看建议生成任务（用量单独记录）'],
    'Current step':['原步骤'],'Suggested step':['建议步骤'],
    'Preview the changed workflow':['预览调整后的流程'],'Confirm and apply':['确认应用'],'Discard suggestion':['放弃建议'],
    'Pause following steps before requesting a change':['请先暂停后续步骤，再请求调整。'],
    'Only an undispatched node can change':['只能调整尚未派发或提问的节点。'],
    'Discard the current suggestion before requesting another':['请先放弃当前建议，再生成新建议。'],
    'Execution changed; request a new suggestion':['执行状态已变化，请放弃旧建议后重新生成。'],
    'Select one to eight distinct pending nodes':['请选择 1 到 8 个不同的尚未派发节点。'],
    'Workflow context is too large for a coordinated suggestion; select fewer nodes or request one change at a time':['当前工作流上下文过长，无法生成协调建议；请减少所选节点，或逐项请求调整。'],
    'The suggestion is invalid; nothing was changed':['建议格式或分支无效，未修改流程。请查看生成任务的结果。'],
    'A current valid suggestion is required':['需要当前版本的有效建议。'],
    'STALE':['建议已过期'],'INVALID':['建议无效'],
    'AMENDMENT_REQUESTED':['已请求调整建议'],'AMENDMENT_APPLIED':['已确认并应用调整'],'AMENDMENT_DISCARDED':['已放弃调整建议'],
    'Task sources':['任务资料'],
    'Source type':['资料类型'],
    'Choose UTF-8 text files':['请选择 UTF-8 文本文件'],
    'Reference knowledge':['参考知识'],
    'Method / custom Skill':['工作方法／自定义 Skill'],
    'Add text files':['添加文本文件'],
    'Selected text is saved with this definition and sent to the assigned Worker. The model reads relevant pages on demand. Up to 8 files, 128 KiB each, 512 KiB total.':['所选文本随定义保存并提供给执行 Worker；模型按需读取相关页。最多 8 个文件，每个 128 KiB，总计 512 KiB。'],
    'Select up to eight text sources totaling 512 KiB':['最多选择 8 份文本资料，总计不超过 512 KiB。'],
    'A text source must contain 1 to 128 KiB':['每份资料须为非空文本，不超过 128 KiB。'],
    'Source digest differs from content':['资料内容与摘要不一致，请重新加载。'],
    'Team member':['团队成员'],
    'Automatically choose member':['自动选择成员'],
    'General execution (no team member)':['使用通用 Worker（不指定成员）'],
    'Automatic assignment uses a general Worker when no team members are configured.':['未配置团队成员时，自动分配会使用通用 Worker。'],
    'GENERAL_ASSIGNED':['已转交通用 Worker','Assigned to a general Worker'],
    'No team members are configured. The task will use a general Worker.':['未配置团队成员，本任务将交给通用 Worker。'],
    'The selected team member has stopped accepting work. Resume that member to continue.':['所选成员已停止接单，恢复后可继续。'],
    'CANCELLED':['已取消','Cancelled'],'BLOCKED':['等待处理','Blocked'],
    'Required Skills (optional, up to 8)':['所需 Skills（可选，最多 8 项）'],
    'WAITING_MEMBER':['等待合适成员', 'Waiting for a suitable member'],
    'MEMBER_ASSIGNED':['已选择成员', 'Member selected'],
    'No team members exist. Create one in Agent factory.':['尚未创建团队成员，请到 Agent 工厂创建。'],
    'No member has all required Skills. Update the requirements or member settings.':['当前没有成员拥有全部所需 Skills，请调整条件或成员设定。'],
    'Matching members have stopped accepting work. Resume a matching member.':['匹配的成员均已停止接单，请恢复成员。'],
    'Assignment resumes automatically when a matching member is available.':['有合适成员后会自动继续；可暂停或停止本次执行。'],
    'Selected member lacks required Skills':['所选成员缺少所需 Skills，请调整条件或成员设定。'],
    'Select up to eight registered platform Skills':['请选择最多 8 项已注册的内置 Skills。'],
    'Team member (optional)':['团队成员（可选）'],'No assigned member':['不指定成员'],'Member unavailable':['成员未加载或已解散'],
    'Member stopped accepting work':['成员已停止接单'],
    'Selected member is not accepting new work':['所选成员已停止接单，请恢复该成员或明确重新选择。'],
    'Assigned member snapshot':['本次执行的成员设定'],'Resume the selected member to claim this work.':['该工作正在等待成员恢复接单。'],'Current work continues.':['当前工作会继续。'],
    'Member catalog unavailable; existing assignments are preserved.':['暂时无法读取成员目录；已有成员选择会保留，不会自动改派。'],
    'Recorded usage':['已记录用量'], 'Model calls':['模型调用次数'],
    'Input tokens':['输入 Token'], 'Cached input tokens':['其中缓存 Token'], 'Output tokens':['输出 Token'],
    'Not recorded':['未记录'], 'Recorded estimates':['已知估算费用'], 'Missing estimates':['尚无估算的调用（含订阅或价格缺失）'],
    'calls missing this value':['次调用未记录该值'], 'View recorded calls':['查看调用记录'],
    'No calls recorded; this does not mean no calls occurred.':['暂无调用记录；不代表没有发生调用。'],
    'Recorded calls include retries and summaries. Calls are not rounds. Cached input is part of input. Costs are estimates, not invoices.':['包含已记录的重试和摘要调用。调用次数不等于轮数，缓存已包含在输入中。费用是估算，并非实际账单。'],
    'Describe the work and follow its execution.':['说清要做的事，直接查看执行进展。'],
    'Edit a reusable workflow and follow each execution.':['编辑可复用的工作流，查看每次执行的进展。'],
    'Pausing prevents following steps from starting. Current Worker work may continue. Stopping waits for Worker confirmation.':['暂停只阻止后续步骤启动，当前 Worker 工作可能继续。停止执行需要等待 Worker 确认。'],
    'SETTLED':['本次执行已结束'], 'FINALIZING':['正在确认结果'],
    'PAUSING':['等待暂停确认'], 'BROKEN':['执行通道异常'],
    'Worker activity':['Worker 实时活动'],
    'Recent model and tool metadata. This is not the complete model conversation.':['展示最近的模型与工具活动概况，并非完整模型对话原文。'],
    'Current task instructions':['当前任务内容'], 'Recent activity (up to 64 events)':['最近活动（最多 64 条）'],
    'No Worker activity recorded yet':['尚未记录 Worker 活动'],
    'Last contact':['最后联系'], 'Connection delayed; execution state is not confirmed':['连接更新延迟，不能据此确认执行已停止'],
    'Model not recorded':['模型未记录'], 'model-request':['模型请求'], 'model-terminal':['模型响应终态'],
    'tool-proposed':['工具调用已校验'], 'runtime-event':['运行活动'], 'ai-settings':['模型设置'],
    'native-turn-started':['Worker 开始处理'], 'execution-quiescent':['执行资源已静止'],
    'Workflow draft is not ready yet':['工作流草稿尚未就绪'],
    'Add context':['补充内容'], 'Additional content':['补充资料或方向'],
    'When to use':['生效方式'], 'Add to context':['加入处理上下文'],
    'Use next turn':['下一轮生效'], 'Send context':['发送补充'],
    'New content is used at the next model call. It cannot change an in-flight request or grant new permissions.':['内容最早在下一次模型调用生效，不会修改在途请求或增加权限。'],
    'QUEUED':['已收到，等待生效'], 'RESERVED':['等待模型调用回执'],
    'APPLIED':['已用于模型调用'], 'CONTEXT':['已加入后续上下文'],
    'UNKNOWN':['是否生效尚未确认'], 'Input received':['补充内容已收到'],
    'INPUT_RECEIVED':['收到补充'], 'INPUT_CONTEXT':['加入上下文'],
    'INPUT_RESERVED':['准备使用补充'], 'INPUT_APPLIED':['补充已生效'], 'INPUT_UNKNOWN':['补充生效结果未确认'],
    'This version handles read-only text tasks and decisions. It does not modify files, issue refunds or send messages.':['当前版本处理只读文本任务与判断，不修改文件、不实际退款或发送消息。'],
    'Library':['工作区'], 'Tasks':['任务'], 'New workflow':['新建工作流'],
    'Task history':['任务历史'], 'Search tasks':['检索任务'], 'View':['视图'], 'Status':['状态'],
    'New task':['新建任务'], 'Back to task list':['返回任务列表'], 'Back to workflow list':['返回工作流列表'], 'Working location':['工作目录'],
    'Updated':['更新时间'], 'Actions':['操作'], 'Archived':['已归档'],
    'Only finished tasks with no active work can be managed from task history':['任务仍有未结束的工作或租约，暂时无法归档或删除。'],
    'A task must have exactly one execution node and no exits':['任务须包含一个执行节点，且不能设置出口。'],
    'Required command checks are available for tasks created from the Tasks page':['从「任务」页面新建的任务支持必需命令检查。'],
    'Add a step':['添加步骤'], 'Workflow diagram':['业务流程图'], 'Step activity':['步骤记录'],
    'Select a step to edit it. Drag an output dot to connect steps.':['点击步骤编辑属性，拖动输出端连接步骤。'],
    'Agent task':['Agent 任务'], 'Field rule':['字段规则'], 'Field name':['字段名'], 'Expected value':['预期值'],
    'Rule input':['规则数据'], 'Run input JSON':['本次运行的 JSON 输入'], 'Latest Agent result':['最近的 Agent 正式结果（完整 JSON，最多 4,000 字符）'],
    'Match':['匹配'], 'Otherwise':['其他情况'], 'Set as entry step':['设为入口步骤'], 'Entry step':['入口步骤'],
    'No recorded activity for this step.':['此步骤暂无运行记录。'], 'RULE_RESULT':['规则判定'],
    'Rule steps need a JSON object as input.':['字段规则读取 JSON 对象，例如 {"status":"approved"}。'],
    'This workflow needs JSON input for each run':['此工作流每次运行都需要提供 JSON 输入。'],
    'Current tasks':['当前任务'], 'Archived tasks':['已归档'], 'Pending deletion':['待删除'], 'All states':['全部状态'],
    'Previous page':['上一页'], 'Next page':['下一页'],
    'No tasks match these filters.':['没有符合筛选条件的任务。'],
    'Archive':['归档'], 'Delete':['删除'], 'Cancel deletion':['撤销删除'],
    'Task archived.':['任务已归档。'], 'Deletion scheduled.':['已安排删除。'], 'Deletion cancelled.':['已撤销删除。'],
    'Delete after':['计划删除时间'],
    'Delete task?':['删除任务？'],
    'This task will move to Pending deletion. You can cancel within 30 days. After that it disappears from the task list. Audit, usage and delivery records are retained.':['任务将移入「待删除」。30 天内可以撤销，到期后会从任务列表移除，后台保留审计、模型用量和交付记录。'],
    'Creating task…':['正在创建任务…'],
    'Task created.':['任务已创建。'],
    'Task created, but its details could not be loaded.':['任务已创建，但暂时无法加载详情。'],
    'After-sales example':['售后示例'], 'Saved workflows':['已保存工作流'],
    'Decisions requested by the manager':['经理请你决定'],
    'Manager diagnosis':['经理诊断'],
    'Runtime profile':['运行配置'], 'Factory default':['工厂默认'],
    'Manager selects':['由经理选择'], 'Candidate runtime profiles':['候选运行配置'],
    'Shared read-only tools':['共享只读工具'], 'Revoke tool grant':['撤销工具授权'],
    'Reconcile tool result':['核对工具操作'], 'Evidence reviewed':['已核对的证据'],
    'No shared tools are enabled for execution.':['当前没有可执行的共享工具。'],
    'Current Skill version':['当前 Skill 版本'],
    'Waiting for the manager to select a runtime profile.':['等待经理选择运行配置。'],
    'Select at least one candidate runtime profile.':['请至少选择一个候选运行配置。'],
    'WAITING_PROFILE':['等待运行配置'], 'PROFILE_SELECTED':['已选择运行配置'],
    'MANAGER_MODEL_FAILED':['经理选型失败'], 'PROFILE_UNAVAILABLE':['运行配置不可用'],
    'Ask manager to choose profile':['请经理选择运行配置'],
    'Pinned Skills':['固定 Skills'], 'Granted tools':['任务授权工具'],
    'Preparing a diagnosis':['正在准备诊断'],
    'Review recovery options':['查看恢复方案'],
    'Open the failed task':['打开失败任务'],
    'Implement and verify':['实现与验证'],
    'Prepare a JSON record':['生成 JSON 记录'],
    'Verify the delivered record':['验证交付记录'],
    'Decide how to handle the findings':['决定如何处理验收问题'],
    'Accepted':['验收通过'], 'Closed without acceptance':['结束，未通过验收'],
    'Revise the result':['修改后重新验证'], 'Close without acceptance':['结束本次工作'],
    'Convert the supplied record to one JSON object with three exact keys: title, language, source. Preserve the values exactly.':['将输入转换成一个 JSON 对象，键必须恰为 title、language、source，值须与输入完全相同。'],
    'Deliver the JSON object in the formal result, with no surrounding prose. Apply any supplied correction.':['在正式结果中只交付 JSON 对象。若收到明确的修正要求，请按要求处理。'],
    'Read the verified upstream report from the latest preparation step. Check the exact JSON keys and every value against the input. If the report or required data is missing, choose revise. State the missing or incorrect field in the reason. Use accept only when all checks pass.':['读取最近一次生成步骤的已校验交接报告。逐字段核对 JSON 键和值是否与原输入相符。报告或必要资料缺失时选 revise，说明缺失或错误的字段。所有检查通过才选 accept。'],
    'Read the checks in the execution timeline. Supply a correction if you want to generate and verify a fresh result, or close this run without acceptance.':['查看执行时间线中的验收说明。可补充修正要求后重新生成和验证，也可结束本次工作。'],
    'Executions':['执行记录'], 'Refresh':['刷新'], 'Workflow definition':['工作流定义'],
    'Workflows':['工作流'], 'Create a task or run a reusable workflow.':['创建任务，或运行可复用的工作流。'],
    'Text tasks use the same executor as workflow nodes. No Git repository is required.':['文本任务与工作流节点共用执行能力，无须 Git 仓库。'],
    'What should be done?':['需要做什么？'], 'Working location (optional)':['工作位置（可选）'],
    'Run task':['执行任务'], 'Name':['名称'], 'Goal and completion conditions':['目标与完成条件'],
    'Edit steps':['编辑步骤'], 'Add step':['添加步骤'], 'Save workflow':['保存工作流'],
    'Input and reference material':['输入与参考资料'], 'Start execution':['启动执行'],
    'Starting freezes this definition for this execution. Later edits do not change existing executions.':['每次执行保存独立的流程副本，后续编辑不影响已有执行。'],
    'Pause following steps':['暂停后续步骤'], 'Resume workflow':['继续工作流'], 'Retry this step':['重试本步骤'], 'Stop execution':['停止本次执行'],
    'Your answer':['你的回答'], 'Answer and continue':['回答并继续'],
    'Worker execution details':['Worker 执行详情'], 'Execution timeline':['执行时间线'],
    'Task':['执行任务'], 'LLM decision':['LLM 判断'], 'Ask user':['询问用户'], 'Done':['结束'],
    'Title':['步骤名称'], 'Type':['步骤类型'], 'Instruction':['说明与完成条件'], 'Next step':['后续步骤'],
    'Choice':['选项'], 'Add choice':['添加选项'], 'Remove':['移除'],
    'No executions yet':['暂无执行记录'], 'No saved workflows':['暂无已保存工作流'],
    'Workflow saved':['工作流已保存'], 'Save changes before starting':['请先保存修改，再启动执行'],
    'Stop this execution? Existing results will be retained.':['停止本次执行？已有结果将保留。'],
    'Waiting for a compatible Worker':['等待兼容 Worker 接单'], 'Waiting for user input':['等待用户补充'],
    'Request outcome is unknown. Check execution history before submitting again.':['请求结果尚未确认，请先检查执行记录，不要重复提交。'],
    'RUNNING':['执行中'], 'WAITING_USER':['待你回答'], 'PAUSED':['后续步骤已暂停'], 'STOPPING':['等待 Worker 停止确认'],
    'STOPPED':['已停止'], 'COMPLETED':['已完成'], 'FAILED':['需要处理'], 'READY':['等待接单'],
    'CLAIMED':['已领取'], 'WAITING_INPUT':['等待 Worker 问题答复'], 'STARTED':['开始'],
    'DISPATCHED':['已派发'], 'QUESTION':['需要补充'], 'ANSWER':['用户回答'],
    'BRANCH':['分支判断'], 'RESULT':['步骤结果'], 'STOP_REQUESTED':['已请求停止'], 'RESUMED':['已继续'],
    'LIMIT':['达到执行限制'], 'RETRY':['用户请求重试'],
    'Required Skills':['必需 Skills'],
    'Task method':['本次任务方法'],
    'Choose any number of installed Skills this task must use. Leave empty for automatic selection.':['可多选本任务必须使用的已安装 Skill；留空表示由 Worker 自行选择。'],
    'Only catalogued Skills appear here. Write one-task instructions under Task sources; external downloads are not enabled.':['这里只显示平台目录中的 Skill。单次任务说明可直接写在“任务资料”中；目前没有开放外部下载。'],
    'Choose any number of installed Skills this step must use. Leave empty for automatic selection.':['可多选本步骤必须使用的已安装 Skill；留空表示由 Worker 自行选择。'],
    'Write a task-specific method or reference':['直接写入本次工作的方法或资料'],
    'Add written source':['添加文字资料'],
    'Written method':['手写方法'], 'Written reference':['手写参考资料'],
    'This text is attached to this task or saved workflow. It is not installed as a shared Skill.':['这段文字只附加到本任务或已保存的工作流中，不会安装成共享 Skill。'],
    'Choose a step to delete. The final Done step cannot be removed.':['请选择要删除的步骤。最后的“完成”步骤不能删除。'],
    'Drag a step onto the diagram to add it. Drag an output dot to another step to connect them. Double-click a step to edit it, or select it and use Edit selected step.':['将步骤拖到图中可添加；拖动右侧圆点到另一节点可连线；双击节点编辑，也可选中节点后点“编辑所选步骤”。'],
    'Delete selected step':['删除所选步骤'], 'Edit selected step':['编辑所选步骤'], 'Edit step':['编辑步骤'],
    'Step settings':['步骤设置'], 'Done':['完成'],
    'The selected step cannot be deleted.':['所选步骤不能删除。'],
    'Deleting this decision sends incoming routes to Done. Continue?':['删除此判断步骤后，指向它的连线会改为指向“完成”。是否继续？'],
    'Connect a step to itself is not allowed.':['不能将步骤连接到自身。'],
    'Connected steps. Save the workflow to keep this change.':['已连接步骤。保存工作流后保留此修改。'],
    'Step added. Save the workflow to keep this change.':['已添加步骤。保存工作流后保留此修改。'],
    'Saved workflows are reusable. Every start creates a separate run from a frozen copy; later edits do not change existing runs.':['已保存的工作流可重复使用。每次启动都会基于固定副本创建独立执行，后续编辑不会改变已启动的执行。'],
    'Open a saved workflow to edit it, make a separate copy, or schedule it. Starting it creates its own execution.':['打开已保存的工作流，可以编辑、复制为新工作流或设置定时安排。每次启动都会创建独立执行。'],
    'Information for this run':['本次执行的补充信息'],
    'Enter facts or case details that change each time you run this workflow. Shared documents and methods belong under Task sources above.':['填写每次运行会变化的事实或个案信息。所有执行共用的文档和方法请放在上方“任务资料”中。'],
    'Attach notes or methods to this task or saved workflow. They are sent with each run. A written method here is not installed as a shared Skill. Up to 8 sources, 128 KiB each, 512 KiB total.':['把说明或方法附加到本任务或已保存的工作流，每次执行都会发送。这里写的方法不会安装成共享 Skill。最多 8 项，每项 128 KiB，总计 512 KiB。'],
    'Write some text first.':['请先写入内容。'],
    'No installed Skills are available. You can write a task-specific method in Task sources.':['当前没有已安装 Skill。可以在“任务资料”中直接写入本次方法。'],
    'Delete step':['删除步骤'],
    'Make a copy':['复制为新工作流'],
  };
  const t = value => {
    const locale = window.aiwI18n?.locale || document.documentElement.lang;
    return words[value]?.[locale?.startsWith('zh') ? 0 : 1] || window.aiwI18n?.t(value) || value;
  };
  const status = message => {
    const modalStatus = $('.desktop-window[open] [data-wf-window-status]');
    (modalStatus || $('[data-wf-status]')).textContent = message;
  };
  let definitions=[], teamSkills=[], teamMembers=[], factoryProfiles=[], taskSources=[], taskSkills=[], editing=null, selectedRun=null, currentView=null, savedSpec='', busy=false, signature='', selectedNodeKey=null;
  let sharedTools=[], taskSharedTools=[];
  let toolDecision=null;
  function newRequest() {
    if(typeof crypto.randomUUID==='function')return crypto.randomUUID();
    const bytes=crypto.getRandomValues(new Uint8Array(16));
    bytes[6]=(bytes[6]&0x0f)|0x40;
    bytes[8]=(bytes[8]&0x3f)|0x80;
    const hex=Array.from(bytes,byte=>byte.toString(16).padStart(2,'0')).join('');
    return [hex.slice(0,8),hex.slice(8,12),hex.slice(12,16),hex.slice(16,20),hex.slice(20)].join('-');
  }
  let scheduleSpec=null,scheduleRequest=newRequest();
  let taskRequest=newRequest(), startRequest=newRequest(), inputRequest=newRequest(), amendmentRequest=newRequest();
  const route=new URLSearchParams(location.search);
  const pageMode=document.body.dataset.wfMode==='tasks'?'tasks':'workflows';
  const taskPage=document.body.dataset.wfTaskPage||'list';
  const taskExecution=document.body.dataset.wfTaskExecution||route.get('execution');
  let preparingGoal=route.get('goalVersion'), importAttempted=false;
  async function api(path, data, prefix='/api/r1/workflows') {
    const response = await fetch(prefix+path, {method:data===undefined?'GET':'POST', credentials:'same-origin',cache:'no-store',
      headers:{'Content-Type':'application/json','X-CSRF-Token':$('meta[name="csrf-token"]').content}, body:data===undefined?undefined:JSON.stringify(data)});
    if(!response.ok) {const detail=await response.json().catch(()=>({}));throw new Error(detail.error || `${response.status} ${response.statusText}`);}
    return response.json();
  }
  function commands(control){
    try{const result=JSON.parse(control.value.trim()||'[]');if(!Array.isArray(result))throw Error();return result;}
    catch(_){throw Error(t('Commands must be a JSON array.'));}
  }
  const taskForm=$('[data-wf-task-form]');
  function renderTaskFileOptions() {
    const fileTask=taskForm.elements.workspaceTools.checked;
    taskForm.elements.autonomousShell.disabled=!fileTask;
    taskForm.elements.commands.disabled=!fileTask;
    taskForm.querySelector('[data-wf-aiw-commands]').disabled=!fileTask;
    $('[data-wf-schedule-task]').hidden=fileTask;
    const container=$('[data-wf-check-options]');
    const selected=new Set([...container.querySelectorAll('input:checked')].map(input=>input.value));
    let profiles=[];
    try { if(fileTask)profiles=commands(taskForm.elements.commands); } catch (_) { /* Editing incomplete JSON. Submit will show the error. */ }
    const ids=[...new Set(profiles.map(profile=>profile?.id).filter(id=>typeof id==='string'&&id.trim()))];
    const visible=ids.length>0;
    $('[data-wf-required-checks]').hidden=!visible;
    container.replaceChildren();
    if(!visible)return;
    container.append(element('p',t('Only select commands that verify the result. Select fewer commands if each requires separate approval.'),'help'));
    for(const id of ids) {
      const label=element('label',undefined,'field'), checkbox=element('input');
      checkbox.type='checkbox';checkbox.name='requiredCheckIds';checkbox.value=id;checkbox.checked=selected.has(id);
      const title=element('span',id);title.dataset.i18nSkip='';label.append(checkbox,title);container.append(label);
    }
  }
  taskForm.elements.commands.addEventListener('input',renderTaskFileOptions);
  function addAiwCommand(id,argv) {
    const profiles=commands(taskForm.elements.commands).filter(profile=>profile?.id!==id);
    profiles.push({id,argv,cwd:'.',permissionProfile:'aiw-project-read',timeoutMs:15000,
      outputBytes:65536,memoryBytes:268435456,processLimit:8});
    taskForm.elements.commands.value=JSON.stringify(profiles,null,2);
    if(teamSkills.includes('aiw-project-read')&&!taskSkills.includes('aiw-project-read')){
      taskSkills.push('aiw-project-read');
      renderSkillPicker($('[data-wf-skills]'),taskSkills,values=>taskSkills=values);
    }
    renderTaskFileOptions();
  }
  $('[data-wf-aiw-skills]').onclick=()=>act(async()=>{
    addAiwCommand('aiw-skill-list',['aiw','pkg','skill','list','--json']);
  });
  $('[data-wf-aiw-issue]').onclick=()=>act(async()=>{
    const field=taskForm.elements.aiwIssueId;
    const issue=field.value.trim().toUpperCase();
    if(!/^ISSUE-[0-9]{5,15}$/.test(issue)){field.focus();throw new Error(t('Enter an AIW Issue ID such as ISSUE-00001.'));}
    addAiwCommand('aiw-issue-show',['aiw','issue','show',issue,'--json']);
  });
  taskForm.elements.workspaceTools.addEventListener('change',renderTaskFileOptions);
  window.addEventListener('aiw:locale-changed',renderTaskFileOptions);
  renderTaskFileOptions();
  async function act(work) {
    if(busy)return;busy=true;status('');
    const buttons=$$('button:not(:disabled)');buttons.forEach(b=>b.disabled=true);
    try {await work();} catch(error) {status(error instanceof TypeError?t('Request outcome is unknown. Check execution history before submitting again.'):t(error.message));}
    finally {busy=false;buttons.forEach(b=>{if(b.isConnected)b.disabled=false;});if(currentView)controlButtons(currentView);}
  }
  function element(tag,text,className) {const e=document.createElement(tag);if(text!==undefined)e.textContent=text;if(className)e.className=className;return e;}
  function taskTitle(title) {
    return title==='Plane task caretaker'||title==='Manager diagnosis'?t('Manager diagnosis'):title;
  }
  function workWindow(section,title,icon,onClose,focus) {
    if(!section.querySelector('[data-wf-window-status]')){
      const message=element('p',undefined,'help');
      message.dataset.wfWindowStatus='';message.setAttribute('role','status');
      section.prepend(message);
    }
    const recordTitle=section.matches('[data-wf-run]')||section.matches('[data-wf-editor]')&&Boolean(editing?.id);
    window.aiwDesktopWindow.open(section,{title,recordTitle,icon,onClose,focus,container:$('.main>.content'),canClose:()=>!busy,canMinimize:()=>!busy});
  }
  function showNewTask() {
    const section=$('[data-wf-task]');
    if(window.aiwDesktopWindow.has(section)){
      workWindow(section,t('New task'),'tasks');
      return;
    }
    section.prepend($('[data-wf-member-status]'));
    section.append($('[data-wf-schedule-editor]'));
    section.hidden=false;
    workWindow(section,t('New task'),'tasks',()=>{
      section.hidden=true;
      if(taskPage==='new')location.assign(taskListUrl());
    },taskForm.elements.instruction);
    status('');
    loadMembers();
    loadProfiles().catch(error=>status(t(error.message)));
    if(document.body.dataset.wfAdmin==='true')loadSharedTools().catch(error=>status(t(error.message)));
  }
  function openEditorWindow(definition) {
    const section=$('[data-wf-editor]'),desktop=window.aiwDesktopWindow;
    if(desktop?.has(section)){
      if((definition?.id||null)===(editing?.id||null)){workWindow(section,'','');return;}
      if(!window.confirm(t('Discard unsaved edits?')))return;
      desktop.close(section);
    }
    showEditor(definition);
    if(!desktop)return;
    $('.wf-library').hidden=false;
    workWindow(section,definition?.spec.title||t('New workflow'),'workflows',()=>{
      editing=null;panel('');
      if(new URLSearchParams(location.search).has('definition')){
        history.replaceState(null,'','/ui/workflows');
        window.dispatchEvent(new Event('aiw:desktop-location'));
      }
    },$('[data-wf-title]'));
  }
  function button(label,callback) {const b=element('button',t(label),'btn ghost');b.type='button';b.onclick=callback;return b;}
  function translate() {
    document.title=t(pageMode==='tasks'?'Tasks':'Workflows');
    $$('[data-wf-label]').forEach(e=>e.textContent=t(e.dataset.wfLabel));
    $$('[data-wf-new], [data-wf-sample], [data-wf-refresh], [data-wf-save], [data-wf-start], [data-wf-control]').forEach(e=>{
      e.dataset.wfOriginal ||= e.textContent;e.textContent=t(e.dataset.wfOriginal);
    });
  }
  function panel(name) {
    ['task','editor','run'].forEach(p=>$(`[data-wf-${p}]`).hidden=p!==name);
    if(pageMode==='workflows'){
      $('.wf-library').hidden=Boolean(name);
      $('.wf-content').hidden=!name;
      $('[data-wf-workflow-back]').hidden=!name;
    }
  }
  const workflowKinds={RULE:'Field rule',ASK:'Ask user',TASK:'Agent task',DECISION:'LLM decision',END:'Done'};
  const ruleDefaults=()=>({source:'INPUT',field:'status',expected:'approved'});
  const branchLabel=(node,choice)=>node.kind==='RULE'?t(choice==='match'?'Match':'Otherwise'):choice;
  function singleSpec() {return {title:t('New workflow'),goal:'',entry:'rule',nodes:[
    {key:'rule',title:t('Field rule'),kind:'RULE',instruction:'',next:null,branches:{match:'end',otherwise:'end'},rule:ruleDefaults()},
    {key:'end',title:t('Done'),kind:'END',instruction:'',next:null,branches:{}}]};}
  function showEditor(definition) {
    selectedRun=null;editing=structuredClone(definition || {id:null,version:0,spec:singleSpec()});
    selectedNodeKey=editing.spec.nodes[0]?.key||null;
    editing.spec.sources ||= [];
    $('[data-wf-commands]').value=JSON.stringify(editing.spec.commands||[],null,2);
    $('[data-wf-shell]').checked=Boolean(editing.spec.autonomousShell);
    renderSources($('[data-wf-definition-sources]'),editing.spec.sources,values=>editing.spec.sources=values);
    for(const node of editing.spec.nodes) {
      node.branches ||= {};
      if(['TASK','DECISION'].includes(node.kind))node.assignment ||= node.memberId?'MEMBER':definition?.id?'GENERAL':'AUTO';
    }
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
    const choices=new Map();
    for(const value of new Set([...teamSkills,...values])){
      const ref=value.split('@')[0];
      if(!choices.has(ref))choices.set(ref,[]);
      if(ref!==value)choices.get(ref).push(value);
    }
    for(const [skill,versions] of choices){
      const selected=values.find(value=>value.split('@')[0]===skill);
      const label=element('label',undefined,'wf-skill-option'),checkbox=element('input');
      checkbox.type='checkbox';checkbox.checked=Boolean(selected);checkbox.disabled=!checkbox.checked&&values.length>=8;
      checkbox.onchange=()=>{const next=checkbox.checked?[...values,skill]:values.filter(value=>value!==selected);change(next);renderSkillPicker(host,next,change);};
      label.append(checkbox,element('span',skill));host.append(label);
      if(selected&&versions.length){
        const select=element('select');
        select.setAttribute('aria-label',skill+' · '+t('Current Skill version'));
        select.add(new Option(t('Current Skill version'),skill));
        for(const version of versions)select.add(new Option('v'+version.split('@')[1],version));
        select.value=selected;
        select.onchange=()=>{
          const next=values.map(value=>value===selected?select.value:value);
          change(next);renderSkillPicker(host,next,change);
        };
        label.after(select);
      }
    }
    if(!host.children.length)host.append(element('p',t('No installed Skills are available. You can write a task-specific method in Task sources.'),'help'));
  }
  function renderSharedTools(host,values,change) {
    host.replaceChildren();
    const choices=new Map(sharedTools.map(item=>[item.id,item]));
    for(const ref of values)if(!choices.has(ref.id))choices.set(ref.id,ref);
    if(!choices.size)host.append(element('p',t('No shared tools are enabled for execution.'),'help'));
    for(const item of choices.values()){
      const chosen=values.find(ref=>ref.id===item.id), ref=chosen||{id:item.id,revision:item.revision};
      const label=element('label',undefined,'wf-skill-option'),box=element('input');
      box.type='checkbox';box.checked=Boolean(chosen);box.disabled=!chosen&&values.length>=8;
      box.onchange=()=>{
        const updated=box.checked?[...values,ref]:values.filter(value=>value.id!==ref.id);
        change(updated);renderSharedTools(host,updated,change);
      };
      label.append(box,element('span',(item.title||ref.id)+' · v'+ref.revision));host.append(label);
    }
  }
  async function loadSharedTools(){
    sharedTools=await api('/executable',undefined,'/api/r1/tools');
    const simple=$('[data-wf-shared-tool-picker]');
    if(simple)renderSharedTools(simple,taskSharedTools,values=>taskSharedTools=values);
    if(editing&&!$('[data-wf-editor]').hidden)renderNodes();
  }
  function confirmToolDecision(label,item,action){
    const dialog=$('[data-wf-tool-confirm]'),form=$('[data-wf-tool-confirm-form]');
    $('[data-wf-tool-confirm-title]').textContent=t(label);
    $('[data-wf-tool-confirm-item]').textContent=item;
    form.elements.reason.value='';
    form.elements.reason.required=action==='reconcile';
    $('[data-wf-tool-confirm-evidence]').hidden=action!=='reconcile';
    dialog.showModal();
  }
  $('[data-wf-tool-confirm-cancel]').onclick=()=>$('[data-wf-tool-confirm]').close();
  $('[data-wf-tool-confirm-form]').onsubmit=event=>{
    event.preventDefault();
    const chosen=toolDecision;
    if(!chosen)return;
    const reason=event.target.elements.reason.value.trim();
    act(async()=>{
      const body=chosen.action==='revoke'?chosen.ref:{operationId:chosen.operationId,reason};
      await api('/tasks/'+encodeURIComponent(chosen.taskId)+'/'+chosen.action,body,'/api/r1/tools');
      $('[data-wf-tool-confirm]').close();toolDecision=null;signature='';await refreshRun();
    });
  };
  function removeNode(key){
    const node=editing.spec.nodes.find(item=>item.key===key);if(!node||node.kind==='END'){status(t('The selected step cannot be deleted.'));return;}
    const branching=Object.keys(node.branches||{}).length>0,hasIncoming=editing.spec.nodes.some(item=>item.next===key||Object.values(item.branches||{}).includes(key));
    if(branching&&hasIncoming&&!window.confirm(t('Deleting this decision sends incoming routes to Done. Continue?')))return;
    const bypass=!branching?node.next||'end':'end';
    editing.spec.nodes=editing.spec.nodes.filter(item=>item.key!==key);
    for(const item of editing.spec.nodes){if(item.next===key)item.next=bypass;for(const choice of Object.keys(item.branches||{}))if(item.branches[choice]===key)item.branches[choice]=bypass;}
    if(editing.spec.entry===key)editing.spec.entry=editing.spec.nodes[0]?.key||'end';
    selectedNodeKey=editing.spec.nodes.find(item=>item.kind!=='END')?.key||'end';renderNodes();redraw();
  }
  function addNode(kind='TASK'){
    const source=editing.spec.nodes.find(item=>item.key===selectedNodeKey&&item.kind!=='END')||[...editing.spec.nodes].reverse().find(item=>item.kind!=='END');
    const endIndex=editing.spec.nodes.findIndex(item=>item.kind==='END'),key='step'+newRequest().replaceAll('-','').slice(0,8);
    const node={key,title:t(workflowKinds[kind]),kind,instruction:'',next:'end',branches:{}};
    if(['TASK','DECISION'].includes(kind)){node.assignment='AUTO';node.requiredSkills=[];}
    if(kind==='RULE'){node.rule=ruleDefaults();node.branches={match:'end',otherwise:'end'};node.next=null;}
    if(kind==='DECISION'){node.branches={yes:'end',no:'end'};node.next=null;}
    editing.spec.nodes.splice(endIndex<0?editing.spec.nodes.length:endIndex,0,node);
    if(source){
      if(source.kind==='RULE'){source.branches.match=key;}
      else if(source.kind==='TASK'||source.kind==='ASK'&&!Object.keys(source.branches).length){
        if(node.next)node.next=source.next||'end';
        else for(const branch of Object.keys(node.branches))node.branches[branch]=source.next||'end';
        source.next=key;
      }
      else {source.branches||={};if(!Object.keys(source.branches).length&&source.next)source.branches.Continue=source.next;let choice='Option '+(Object.keys(source.branches).length+1);while(choice in source.branches)choice+=' ';source.branches[choice]=key;source.next=null;}
    }
    selectedNodeKey=key;renderNodes();redraw();status(t('Step added. Save the workflow to keep this change.'));
  }
  function connectNodes(sourceKey,targetKey){
    if(sourceKey===targetKey){status(t('Connect a step to itself is not allowed.'));return;}
    const source=editing.spec.nodes.find(item=>item.key===sourceKey),target=editing.spec.nodes.find(item=>item.key===targetKey);
    if(!source||!target||source.kind==='END')return;
    if(source.kind==='RULE'){source.branches.match=targetKey;}
    else if(source.kind==='TASK'){source.next=targetKey;source.branches={};}
    else {source.branches||={};if(!Object.keys(source.branches).length&&source.next)source.branches.Continue=source.next;let choice='Option '+(Object.keys(source.branches).length+1);while(choice in source.branches)choice+=' ';source.branches[choice]=targetKey;source.next=null;}
    selectedNodeKey=sourceKey;renderNodes();redraw();status(t('Connected steps. Save the workflow to keep this change.'));
  }
  function renderNodes() {
    const list=$('[data-wf-nodes]');list.replaceChildren();
    for(const node of editing.spec.nodes) {
      if(node.key!==selectedNodeKey)continue;
      const section=element('section',undefined,'wf-node-editor');section.id='wf-node-'+node.key;
      const row=element('div',undefined,'wf-node-fields'),kind=element('select');
      for(const [value,label] of Object.entries(workflowKinds))kind.append(new Option(t(label),value));
      kind.value=node.kind;
      kind.disabled=node.kind==='END';
      kind.onchange=()=>{
        node.kind=kind.value;
        if(node.kind!=='TASK')node.workspaceTools=false;
        if(!['TASK','DECISION'].includes(node.kind)){node.memberId=null;node.assignment=null;node.requiredSkills=[];delete node.runtimeProfile;delete node.runtimeProfileCandidates;delete node.sharedTools;}
        else node.assignment ||= 'AUTO';
        delete node.rule;
        node.branches={};node.next='end';
        if(node.kind==='RULE'){node.rule=ruleDefaults();node.branches={match:'end',otherwise:'end'};node.next=null;}
        if(node.kind==='DECISION'){node.branches={yes:'end',no:'end'};node.next=null;}
        if(node.kind==='END'){node.instruction='';node.next=null;}
        renderNodes();redraw();
      };
      row.append(field('Title',input(node.title,v=>{node.title=v;redraw();},160)),field('Type',kind));section.append(row);
      if(node.key===editing.spec.entry)section.append(element('p',t('Entry step'),'help'));
      else if(node.kind!=='END')section.append(button('Set as entry step',()=>{editing.spec.entry=node.key;renderNodes();redraw();}));
      if(node.kind==='RULE'){
        const source=element('select');
        source.append(new Option(t('Run input JSON'),'INPUT'),new Option(t('Latest Agent result'),'TASK_RESULT'));
        source.value=node.rule.source;source.onchange=()=>node.rule.source=source.value;
        section.append(field('Rule input',source),field('Field name',input(node.rule.field,v=>node.rule.field=v,64)),
          field('Expected value',input(node.rule.expected,v=>node.rule.expected=v,1000)),
          field('Match',target(node.branches.match,v=>{node.branches.match=v;redraw();})),
          field('Otherwise',target(node.branches.otherwise,v=>{node.branches.otherwise=v;redraw();})));
      } else if(node.kind!=='END') {
        const instruction=element('textarea');instruction.value=node.instruction;instruction.rows=3;instruction.maxLength=8000;instruction.oninput=()=>node.instruction=instruction.value;
        section.append(field('Instruction',instruction));
        if(['TASK','DECISION'].includes(node.kind)){
          const profile=element('select');
          profileOptions(profile,node.runtimeProfile||'');
          profile.add(new Option(t('Manager selects'),'@manager'));
          if(node.runtimeProfileCandidates)profile.value='@manager';
          profile.onchange=()=>{
            delete node.runtimeProfileCandidates;
            delete node.runtimeProfile;
            if(profile.value==='@manager')node.runtimeProfileCandidates=[];
            else if(profile.value)node.runtimeProfile=profile.value;
            renderNodes();
          };
          section.append(field('Runtime profile',profile));
          if(document.body.dataset.wfAdmin==='true'){
            const picker=element('div',undefined,'wf-skill-picker');
            renderSharedTools(picker,node.sharedTools||[],values=>{if(values.length)node.sharedTools=values;else delete node.sharedTools;});
            section.append(field('Shared read-only tools',picker));
          }
          if(node.runtimeProfileCandidates){
            profile.setCustomValidity(node.runtimeProfileCandidates.length?'':t('Select at least one candidate runtime profile.'));
            const picker=element('div',undefined,'wf-skill-picker');
            const available=new Map(factoryProfiles.map(item=>[item.id,(item.displayName||item.id)+' · '+item.model]));
            for(const id of node.runtimeProfileCandidates)if(!available.has(id))available.set(id,id);
            for(const [id,label] of available){
              const box=element('input');box.type='checkbox';box.checked=node.runtimeProfileCandidates.includes(id);
              box.onchange=()=>{
                if(box.checked&&node.runtimeProfileCandidates.length<16)node.runtimeProfileCandidates.push(id);
                else if(!box.checked)node.runtimeProfileCandidates=node.runtimeProfileCandidates.filter(value=>value!==id);
                else box.checked=false;
                profile.setCustomValidity(node.runtimeProfileCandidates.length?'':t('Select at least one candidate runtime profile.'));
              };
              const option=element('label');option.append(box,document.createTextNode(label));picker.append(option);
            }
            section.append(field('Candidate runtime profiles',picker));
          }
        }
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
      if(node.kind!=='END')section.append(button('Delete step',()=>removeNode(node.key)));
      list.append(section);
    }
  }
  function graph(container,spec,run) {
    container.replaceChildren();if(!spec?.nodes.length)return;
    const editable=container.hasAttribute('data-wf-definition-graph');
    const inspectable=!!run&&container.hasAttribute('data-wf-run-graph');
    const ns='http://www.w3.org/2000/svg',svg=document.createElementNS(ns,'svg');
    const make=(tag,attrs)=>{const e=document.createElementNS(ns,tag);Object.entries(attrs).forEach(([k,v])=>e.setAttribute(k,v));return e;};
    const nodes=spec.nodes,positions=new Map(nodes.map((n,i)=>[n.key,{x:80+(i%2)*310,y:30+Math.floor(i/2)*135}]));
    svg.setAttribute('viewBox',`0 0 760 ${Math.ceil(nodes.length/2)*135+20}`);svg.setAttribute('width','760');svg.setAttribute('height',Math.ceil(nodes.length/2)*135+20);
    svg.setAttribute('role',editable||inspectable?'group':'img');svg.setAttribute('aria-label',spec.title);
    const marker=make('marker',{id:container.hasAttribute('data-wf-run-graph')?'wf-arrow-run':'wf-arrow-definition',viewBox:'0 0 10 10',refX:9,refY:5,markerWidth:6,markerHeight:6,orient:'auto'});
    marker.append(make('path',{d:'M 0 0 L 10 5 L 0 10 z'}));const defs=make('defs',{});defs.append(marker);svg.append(defs);
    const taken=new Set();let previous=null;
    for(const event of run?.events||[]){if(previous&&previous!==event.node)taken.add(previous+'\n'+event.node);previous=event.node;}
    for(const n of nodes) {
      const from=positions.get(n.key),edges=n.next?[[t('Next step'),n.next]]:Object.entries(n.branches||{});
      edges.forEach(([choice,key],i)=>{const to=positions.get(key);if(!to)return;
        const path=make('path',{d:`M ${from.x+220} ${from.y+30} C ${from.x+270+30*i} ${from.y+30}, ${to.x-50-30*i} ${to.y+30}, ${to.x} ${to.y+30}`,'marker-end':`url(#${marker.id})`,class:taken.has(n.key+'\n'+key)?'wf-taken':''});svg.append(path);
        if(n.branches&&Object.keys(n.branches).length){const label=make('text',{x:(from.x+to.x)/2+120,y:(from.y+to.y)/2+82+i*15});label.textContent=branchLabel(n,choice);svg.append(label);}
      });
    }
    const visited=new Set(run?.events.filter(e=>['RESULT','ANSWER','COMPLETED','RULE_RESULT'].includes(e.type)).map(e=>e.node)||[]);
    for(const n of nodes) {
      const p=positions.get(n.key),group=make('g',{class:run?.current===n.key?(run.state==='FAILED'?'wf-failed':'wf-current'):visited.has(n.key)?'wf-visited':editable&&selectedNodeKey===n.key?'wf-selected':''});
      if(editable)group.dataset.nodeKey=n.key;
      group.append(make('rect',{x:p.x,y:p.y,width:220,height:60,rx:['DECISION','RULE'].includes(n.kind)?22:8}));
      const kind=make('text',{x:p.x+12,y:p.y+21});kind.textContent=t(n.workspaceTools?'Workspace task':workflowKinds[n.kind]);
      const title=make('text',{x:p.x+12,y:p.y+44});title.textContent=n.title.length>22?n.title.slice(0,21)+'…':n.title;
      const tooltip=make('title',{});tooltip.textContent=n.title+'\n'+n.instruction;group.append(kind,title,tooltip);
      if(editable||inspectable) {
        group.setAttribute('tabindex','0');group.setAttribute('role','button');group.setAttribute('aria-label',n.title);group.style.cursor='pointer';
      }
      if(editable){
        const inputPort=make('circle',{cx:p.x,cy:p.y+30,r:6,class:'wf-port wf-input-port'}),outputPort=make('circle',{cx:p.x+220,cy:p.y+30,r:7,class:'wf-port wf-output-port'});
        group.append(inputPort);if(n.kind!=='END')group.append(outputPort);
        group.onclick=()=>{selectedNodeKey=n.key;renderNodes();redraw();};
        group.ondblclick=()=>openNodeEditor(n.key);
        group.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();openNodeEditor(n.key);}};
        outputPort.onpointerdown=e=>{e.preventDefault();e.stopPropagation();svg.dataset.connectSource=n.key;svg.dataset.connectX=String(e.clientX);svg.dataset.connectY=String(e.clientY);};
      } else if(inspectable) {
        const select=()=>{selectedRunStep=n.key;renderRunStep(run,n.key);};
        group.onclick=select;
        group.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();select();}};
      }
      svg.append(group);
    }
    if(editable){
      svg.onpointerup=e=>{
        const source=svg.dataset.connectSource;if(!source)return;delete svg.dataset.connectSource;
        if(Math.hypot(e.clientX-Number(svg.dataset.connectX),e.clientY-Number(svg.dataset.connectY))<8)return;
        const target=document.elementFromPoint(e.clientX,e.clientY)?.closest('[data-node-key]')?.dataset.nodeKey;
        if(target)connectNodes(source,target);
      };
      container.ondragover=e=>{if(Array.from(e.dataTransfer.types).includes('text/plain'))e.preventDefault();};
      container.ondrop=e=>{e.preventDefault();const kind=e.dataTransfer.getData('text/plain');if(['RULE','TASK','DECISION','ASK'].includes(kind))addNode(kind);};
    }
    container.append(svg);
  }
  function openNodeEditor(key){selectedNodeKey=key;renderNodes();redraw();$('[data-wf-nodes] input')?.focus();}
  let caretakerStamp='';
  async function refreshCaretaker() {
    const rows=await api('/caretaker-alerts'), stamp=JSON.stringify(rows);
    if(stamp===caretakerStamp)return;
    caretakerStamp=stamp;
    $('[data-wf-caretaker]').hidden=!rows.length;
    const list=$('[data-wf-caretaker-list]');
    list.replaceChildren();
    for(const row of rows){
      const item=element('div',undefined,'wf-event');
      const title=element('strong',row.title||row.taskId);title.dataset.i18nSkip='';
      const task=element('a',t('Open the failed task'),'btn ghost');
      task.href='/ui/plans/'+encodeURIComponent(row.planId)+'/tasks/'+encodeURIComponent(row.taskId);
      item.append(title,element('p',row.state==='PENDING'?t('Preparing a diagnosis'):t(row.state),'help'),task);
      if(row.executionId)item.append(button('Review recovery options',()=>act(()=>openRun(row.executionId))));
      list.append(item);
    }
  }
  let libraryRequest=0;
  const urlPage=Number(route.get('page'))||1;
  let taskHistoryPage=Number.isInteger(urlPage)&&urlPage>=1&&urlPage<=10000?urlPage:1;
  let taskHistoryRequest=0,taskHistoryTimer,taskHistoryHasMore=false;
  function taskListUrl(path='/ui/tasks') {
    const params=new URLSearchParams();
    if(taskPage==='list'){
      params.set('q',$('[data-wf-history-search]').value.trim());
      params.set('state',$('[data-wf-history-state]').value);
      params.set('view',$('[data-wf-history-view]').value);
      params.set('page',String(taskHistoryPage));
    } else {
      for(const key of ['q','state','view','page'])if(route.has(key))params.set(key,route.get(key));
    }
    for(const [key,value] of [...params]){
      if(!value||key==='view'&&value==='current'||key==='page'&&value==='1')params.delete(key);
    }
    return params.size?path+'?'+params:path;
  }
  function updateTaskListUrl() {
    window.history.replaceState(null,'',taskListUrl());
    $('[data-wf-new-task]').href=taskListUrl('/ui/tasks/new');
    window.dispatchEvent(new Event('aiw:desktop-location'));
  }
  function emptyTable(host,columns,label) {
    const row=element('tr'),cell=element('td',t(label),'empty');
    cell.colSpan=columns;row.append(cell);host.append(row);
  }
  function updatedCell(value) {
    const cell=element('td'),date=new Date(value),time=element('time',date.toLocaleString(window.aiwI18n?.locale||undefined));
    time.dateTime=date.toISOString();cell.append(time);return cell;
  }
  async function refreshTaskHistory() {
    const request=++taskHistoryRequest;
    const view=$('[data-wf-history-view]').value;
    const query=new URLSearchParams({view,state:$('[data-wf-history-state]').value,q:$('[data-wf-history-search]').value,page:taskHistoryPage});
    let result;
    try { result=await api('/tasks/history?'+query); }
    catch(error) { if(request===taskHistoryRequest)throw error;return; }
    if(request!==taskHistoryRequest)return;
    if(!result.tasks.length&&taskHistoryPage>1) {
      taskHistoryPage=1;
      updateTaskListUrl();
      return refreshTaskHistory();
    }
    taskHistoryHasMore=result.hasMore;
    const list=$('[data-wf-executions]');
    list.replaceChildren();
    if(!result.tasks.length)emptyTable(list,5,'No tasks match these filters.');
    for(const row of result.tasks) {
      const title=taskTitle(row.title),entry=element('tr'),name=element('td'),open=element('a',title||row.id);
      open.href=taskListUrl('/ui/tasks/'+encodeURIComponent(row.id));open.dataset.i18nSkip='';
      open.className='record-name';open.dataset.consoleIcon='tasks';
      open.addEventListener('click',event=>{
        if(event.button!==0||event.ctrlKey||event.metaKey||event.shiftKey||event.altKey||!window.aiwDesktopWindow)return;
        event.preventDefault();act(()=>openRun(row.id));
      });
      name.append(open,element('small',row.id,'wf-task-id mono'));
      const state=element('td');
      const badge=element('span',t(row.state),'badge');badge.dataset.status=row.state;state.append(badge);
      if(row.delete_after_epoch_ms)state.append(element('small',t('Delete after')+' '+new Date(row.delete_after_epoch_ms).toLocaleString(),'wf-task-id'));
      const actions=element('td'),buttons=element('div',undefined,'wf-history-actions');
      renderTaskActions(buttons,row.id,title,{manageable:row.manageable,archived:view!=='current',deleteAfter:row.delete_after_epoch_ms});
      if(buttons.childElementCount)actions.append(buttons);
      entry.append(name,state,element('td',row.location||'—'),updatedCell(row.updated_ms),actions);list.append(entry);
    }
    $('[data-wf-history-pagination]').hidden=result.page===1&&!result.hasMore;
    $('[data-wf-history-page]').textContent=String(result.page);
    $('[data-wf-history-prev]').disabled=result.page===1;
    $('[data-wf-history-next]').disabled=!result.hasMore;
    window.dispatchEvent(new Event('aiw:desktop-content'));
  }
  function renderTaskActions(host,id,title,management) {
    host.replaceChildren();
    if(!management?.manageable)return;
    if(!management.archived)host.append(button('Archive',()=>manageTaskHistory(id,'archive')));
    if(document.body.dataset.wfAdmin==='true') {
      if(!management.deleteAfter){
        const remove=button('Delete',()=>manageTaskHistory(id,'schedule-delete',title||id));
        remove.classList.replace('ghost','danger');
        host.append(remove);
      } else if(management.deleteAfter>Date.now()){
        host.append(button('Cancel deletion',()=>manageTaskHistory(id,'undo-delete')));
      }
    }
  }
  async function manageTaskHistory(id,action,target) {
    if(action==='schedule-delete'){
      if(busy)return;
      busy=true;
      try {
        if(!await window.aiwConfirm(t('This task will move to Pending deletion. You can cancel within 30 days. After that it disappears from the task list. Audit, usage and delivery records are retained.'),{
          title:t('Delete task?'),target,acceptLabel:t('Delete'),danger:true
        }))return;
      } finally {busy=false;}
    }
    return act(async()=>{
      await api('/tasks/'+encodeURIComponent(id)+'/history',{action});
      status(t(({archive:'Task archived.','schedule-delete':'Deletion scheduled.','undo-delete':'Deletion cancelled.'})[action]));
      if(taskPage==='list')await refreshTaskHistory();
      if(selectedRun===id){signature='';await refreshRun();}
    });
  }
  async function library() {
    const request=++libraryRequest;
    if(pageMode==='tasks') {
      if(taskPage!=='list')return;
      const [,schedules]=await Promise.all([refreshTaskHistory(),api('/schedules')]);
      if(request!==libraryRequest)return;
      renderSchedules(schedules);
      return;
    }
    const [data,schedules]=await Promise.all([api('?kind='+pageMode),api('/schedules')]);
    if(request!==libraryRequest)return;
    definitions=data.definitions;
    const list=$('[data-wf-definitions]');list.replaceChildren();
    if(!definitions.length)emptyTable(list,2,'No saved workflows');
    for(const definition of definitions){
      const row=element('tr'),name=element('td'),actions=element('td'),controls=element('div',undefined,'wf-history-actions');
      const link=element('a',definition.spec.title);
      link.href='/ui/workflows?definition='+encodeURIComponent(definition.id);link.dataset.i18nSkip='';
      link.className='record-name';link.dataset.consoleIcon='workflows';
      link.addEventListener('click',event=>{
        if(event.button!==0||event.ctrlKey||event.metaKey||event.shiftKey||event.altKey||!window.aiwDesktopWindow)return;
        event.preventDefault();
        openEditorWindow(definition);
      });
      name.append(link);
      controls.append(button('Make a copy',()=>openEditorWindow({id:null,version:0,spec:definition.spec})),
        button('Schedule',()=>act(()=>arrange(definition.spec))));
      actions.append(controls);row.append(name,actions);list.append(row);
    }
    const runs=$('[data-wf-executions]');runs.replaceChildren();
    if(!data.executions.length)emptyTable(runs,3,'No executions yet');
    for(const run of data.executions){
      const row=element('tr'),name=element('td'),state=element('td'),link=element('a',run.title||run.id);
      link.href='/ui/workflows?execution='+encodeURIComponent(run.id);link.dataset.i18nSkip='';
      link.addEventListener('click',event=>{
        if(event.button!==0||event.ctrlKey||event.metaKey||event.shiftKey||event.altKey||!window.aiwDesktopWindow)return;
        event.preventDefault();act(()=>openRun(run.id));
      });
      link.className='record-name';link.dataset.consoleIcon='workflows';
      const badge=element('span',t(run.state),'badge');badge.dataset.status=run.state;name.append(link);state.append(badge);
      row.append(name,state,updatedCell(run.updated_ms));runs.append(row);
    }
    loadMembers();
    renderSchedules(schedules);
  }
  function localDateInput(value){const d=new Date(value);return new Date(value-d.getTimezoneOffset()*60000).toISOString().slice(0,16);}
  function arrange(spec){
    if(spec.nodes.some(n=>n.workspaceTools)||(spec.commands||[]).length)throw new Error('Schedules currently support text workflows only');
    if(spec.nodes.some(n=>n.rule?.source==='INPUT'))throw new Error('This workflow needs JSON input for each run');
    scheduleSpec=structuredClone(spec);scheduleRequest=newRequest();
    const details=$('[data-wf-schedule-editor]');details.hidden=false;details.open=true;
    $('[data-wf-schedule-title]').textContent=spec.title;
    $('[data-wf-schedule-content]').textContent=JSON.stringify(spec,null,2);
    $('[data-wf-schedule-zone]').textContent=t('Time zone')+': '+Intl.DateTimeFormat().resolvedOptions().timeZone;
    $('[data-wf-schedule-form]').elements.firstAt.value=localDateInput(Date.now()+3600000);
    details.scrollIntoView({block:'nearest'});
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
      const team=await response.json();if(!Array.isArray(team.members))throw new Error();teamMembers=team.members;teamSkills=team.workflowSkills||team.skills||[];
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
  function profileOptions(select,current=''){
    select.replaceChildren(new Option(t('Factory default'),''));
    for(const profile of factoryProfiles)select.add(new Option((profile.displayName||profile.id)+' · '+profile.model,profile.id));
    if(current&&!factoryProfiles.some(p=>p.id===current))select.add(new Option(current,current));
    select.value=current;
  }
  async function loadProfiles(){
    factoryProfiles=await api('/runtime-profiles');
    const select=$('[data-wf-profile]');
    profileOptions(select,select.value);
    if(editing&&!$('[data-wf-editor]').hidden)renderNodes();
  }
  $('[data-wf-manager-profile]').onclick=()=>act(async()=>{
    const task=taskForm.elements.instruction.value.trim();
    const result=await api('/profile-recommendation',{task,candidates:factoryProfiles.map(p=>p.id)},'/api/r1/authority/agent-factory');
    $('[data-wf-profile]').value=result.profileId;
    status(result.profileId+' · '+result.reason);
  });
  function assignment(value){return {assignment:['AUTO','GENERAL'].includes(value)?value:'MEMBER',memberId:['AUTO','GENERAL'].includes(value)?null:value};}
  let selectedRunStep=null;
  async function openRun(id) {
    if(pageMode==='tasks'&&(taskPage==='new'||taskPage!=='detail'&&!window.aiwDesktopWindow)){
      window.location.assign(taskListUrl('/ui/tasks/'+encodeURIComponent(id)));
      return;
    }
    const section=$('[data-wf-run]'),editor=$('[data-wf-editor]');
    const onDesktop=window.aiwDesktopWindow;
    if(onDesktop?.has(section)){
      if(selectedRun===id){workWindow(section,'','');return;}
      onDesktop.close(section);
    }
    if(onDesktop?.has(editor))onDesktop.close(editor);
    if(onDesktop?.has($('[data-wf-task]')))onDesktop.close($('[data-wf-task]'));
    if(id!==taskExecution)preparingGoal=null;
    selectedRun=id;selectedRunStep=null;signature='';panel('run');
    await refreshRun();
    if(!onDesktop||selectedRun!==id||section.hidden)return;
    if(pageMode==='workflows')$('.wf-library').hidden=false;
    const title=$('[data-wf-run-title]').textContent||id;
    const href=pageMode==='tasks'?taskListUrl('/ui/tasks/'+encodeURIComponent(id)):'/ui/workflows?execution='+encodeURIComponent(id);
    window.dispatchEvent(new CustomEvent('aiw:desktop-record',{detail:{href,title}}));
    workWindow(section,title,pageMode,()=>{
      selectedRun=null;currentView=null;signature='';panel('');
      if(taskPage==='detail')location.assign(taskListUrl());
      else if(pageMode==='workflows'&&route.has('execution'))location.assign('/ui/workflows');
    });
  }
  function renderRunStep(run,key) {
    const host=$('[data-wf-run-node]'),node=run.spec.nodes.find(item=>item.key===key);
    if(!node)return;
    host.replaceChildren(element('h4',node.title),element('p',t(workflowKinds[node.kind]),'help'));
    const events=run.events.filter(event=>event.node===key);
    if(!events.length)host.append(element('p',t('No recorded activity for this step.'),'help'));
    for(const event of events) {
      const item=element('details',undefined,'wf-event');
      item.open=['RESULT','RULE_RESULT','QUESTION','FAILED'].includes(event.type);
      item.append(element('summary',new Date(event.time).toLocaleTimeString()+' · '+t(event.type)),element('pre',event.content));
      host.append(item);
    }
  }
  function controlButtons({run,task,taskDisplayState,management}) {
    const interrupted=taskDisplayState==='INTERRUPTED';
    const allowed={pause:pageMode!=='tasks'&&!interrupted&&['RUNNING','WAITING_MEMBER'].includes(run.state),
      resume:run.state==='PAUSED'&&!interrupted,retry:run.state==='FAILED'&&!interrupted&&!['CLAIMED','RUNNING','YIELDING'].includes(task?.state),
      'accept-result':run.state==='FAILED'&&task?.state==='COMPLETED',stop:!interrupted&&!['COMPLETED','STOPPED','STOPPING'].includes(run.state)};
    $$('[data-wf-control]').forEach(b=>{b.hidden=management?.archived||!allowed[b.dataset.wfControl];b.disabled=busy;});
  }
  function renderActivity(data) {
    const activity=data.activity||{}, events=activity.events||[], connection=$('[data-wf-connection]');
    const progress=events.filter(e=>e.event_type==='progress-stalled'||e.event_type==='progress-review').at(-1);
    let progressCode='';try{progressCode=JSON.parse(progress?.payload_json||'{}').code;}catch(_){/* Keep ordinary activity available. */}
    const progressMessage=progressCode==='MODEL_NO_PROGRESS'
      ?'Repeated tool results produced no new evidence after a reminder. The Worker stopped further model calls.'
      :progressCode==='READ_PHASE_CHECK'?'The Worker has spent several turns reading. It will review the evidence and choose the next check.'
      :progressCode==='LAST_COMMAND_NOT_PASSED'?'The last command did not pass. The Worker will review its relevance before delivery.'
      :progressCode==='NO_OBSERVED_CHANGE'?'No change was observed. The Worker will ask the model to recheck the delivery conditions.'
      :progressCode==='REPEATED_TOOL_FAILURE'?'A tool returned the same failure repeatedly. The Worker will check the blocker before continuing.'
      :progressCode==='REPEATED_READ_RESULT'?'Repeated reads returned the same result. The Worker will ask the model to reconsider its next step.':'';
    $('[data-wf-progress]').textContent=progressMessage?(window.aiwI18n?.t(progressMessage)||progressMessage):'';
    if(!activity.attemptId)connection.textContent=t('No Worker activity recorded yet');
    else connection.textContent=t(activity.phase)+' · '+t('Last contact')+': '+new Date(activity.lastContact).toLocaleString(window.aiwI18n?.locale||undefined)
      +(activity.stale&&activity.phase!=='SETTLED'?' · '+t('Connection delayed; execution state is not confirmed'):'');
    if(!activity.stale&&activity.phase==='RUNNING') {
      const stages={'preparing-workspace':'Preparing workspace','preparing-context':'Preparing task context',
        'running-model':'Running model and tools','final-report-only':'Writing final report','delivering-result':'Delivering task result'};
      for(const event of [...events].reverse()) {
        if(!['workspace-lock','runtime-event'].includes(event.event_type))continue;
        try {
          const payload=JSON.parse(event.payload_json);
          if(event.event_type==='workspace-lock') {
            if(['pending','waiting'].includes(payload.status))connection.textContent=t('Waiting for the workspace to become available');
            else if(['completed','acquired'].includes(payload.status))connection.textContent=t('Preparing workspace');
            else continue;
          } else {
            if(!Object.hasOwn(stages,payload.runtimeEvent))continue;
            connection.textContent=t(stages[payload.runtimeEvent]);
          }
          break;
        } catch (_) { /* Try the preceding event when this one cannot be read. */ }
      }
    }
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
    run.spec.nodes.filter(n=>!['END','RULE'].includes(n.kind)&&!(run.visits?.[n.key])&&!run.events.some(e=>e.node===n.key&&['DISPATCHED','QUESTION'].includes(e.type))).forEach(n=>select.add(new Option(n.title,n.key)));
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
    currentView=data;renderActivity(data);renderUsage(data);
    if(pageMode==='tasks'||data.run.spec.simpleTask)$('[data-wf-amendment]').hidden=true;else renderAmendment(data);
    const member=data.member||{};$('[data-wf-assigned-member]').textContent=member.id?t('Assigned member snapshot')+': '+member.name+' · v'+member.revision+(member.state==='DRAINING'?' · '+t(['READY','BLOCKED'].includes(data.task?.state)?'Resume the selected member to claim this work.':'Current work continues.'):''):'';
    const run=data.run,taskState=data.task?{id:data.task.id,state:data.task.state,instruction:data.task.instruction}:null,stamp=JSON.stringify([run,taskState,data.taskDisplayState,data.activity?.attemptId,data.activity?.stale,data.report,data.failureCode,data.preparation,data.management,data.taskCapabilities]);if(stamp===signature)return;signature=stamp;
    const title=taskTitle(run.spec.title);
    $('[data-wf-run-title]').textContent=title;
    if(taskPage==='detail'||route.get('execution')===run.id)
      requestAnimationFrame(() => window.dispatchEvent(new CustomEvent('aiw:desktop-content', {detail:{title}})));
    const profile=data.runtimeProfile;
    $('[data-wf-runtime-profile]').textContent=profile?.id?t('Runtime profile')+' · '+profile.id+' v'+profile.version+' · '+profile.model:'';
    const grants=$('[data-wf-shared-tool-grants]');
    grants.replaceChildren();
    const toolTask=run.taskId||run.completedTaskId, toolAdmin=document.body.dataset.wfAdmin==='true';
    for(const ref of data.taskCapabilities?.sharedTools||[]){
      grants.append(element('span',ref.id+' · v'+ref.revision));
      if(toolTask&&toolAdmin&&run.taskId)grants.append(button('Revoke tool grant',()=>{
        toolDecision={action:'revoke',taskId:toolTask,ref};
        confirmToolDecision('Revoke tool grant',ref.id+' · v'+ref.revision,'revoke');
      }));
    }
    for(const operation of data.taskCapabilities?.sharedToolOperations||[]){
      grants.append(element('span',operation.toolId+' · '+t(operation.state)));
      if(toolTask&&toolAdmin&&['UNKNOWN','STARTED'].includes(operation.state)&&['FAILED','STOPPED'].includes(run.state))
        grants.append(button('Reconcile tool result',()=>{
          toolDecision={action:'reconcile',taskId:toolTask,operationId:operation.operationId};
          confirmToolDecision('Reconcile tool result',operation.toolId+' · '+operation.operationId,'reconcile');
        }));
    }
    const capabilities=data.taskCapabilities||{},skills=capabilities.skills||[],tools=[...(capabilities.tools||[]),...(capabilities.commands||[])];
    $('[data-wf-execution-skills]').textContent=skills.length?t('Pinned Skills')+' · '+skills.map(skill=>skill.id+' @'+skill.version).join(' · '):'';
    $('[data-wf-execution-tools]').textContent=tools.length?t('Granted tools')+' · '+tools.join(' · '):'';
    const interrupted=data.taskDisplayState==='INTERRUPTED';
    const failure=$('[data-wf-failure]'),code=data.failureCode;
    failure.hidden=!code||run.state!=='FAILED';
    if(!failure.hidden) {
      const help=['MODEL_API_HTTP_401','MODEL_API_HTTP_403','MODEL_AUTH_REQUIRED'].includes(code)
        ?'The model provider rejected the credentials or access. Check the connection in Agent Factory.'
        :['MODEL_API_HTTP_400','MODEL_API_HTTP_422'].includes(code)?'The model provider rejected the request. Check the model and reasoning settings.'
        :code==='MODEL_API_HTTP_404'?'The model provider did not find the requested model or endpoint. Check the configuration and retry.'
        :['MODEL_API_HTTP_429','MODEL_QUOTA_OR_RATE_LIMIT'].includes(code)?'The model provider reported a rate or quota limit. Check the quota before retrying.'
        :code==='WORKSPACE_LOCK_OWNER_UNKNOWN'?'The workspace is locked and the Worker could not identify its owner. Check for an existing execution before retrying.':'';
      failure.textContent=help?t(help)+' ('+code+')':code;
    }
    $('[data-wf-run-state]').textContent=t(interrupted&&['RUNNING','FAILED'].includes(run.state)?'INTERRUPTED':run.state);
    const node=run.spec.nodes.find(n=>n.key===run.current),task=data.task;
    $('[data-wf-run-current]').textContent=(pageMode==='tasks'||run.spec.simpleTask?run.id:node?.title||'')+(task?' · '+t(data.taskDisplayState||task.state):'');
    const overview=$('[data-wf-task-overview]'),management=$('[data-wf-task-management]');
    overview.hidden=pageMode!=='tasks';
    if(!overview.hidden){
      $('[data-wf-task-instruction]').textContent=run.spec.goal;
      $('[data-wf-task-location]').textContent=run.location?t('Working location')+' · '+run.location:'';
    }
    renderTaskActions(management,run.id,title,pageMode==='tasks'?data.management:null);
    if(pageMode==='tasks'&&data.management?.archived)management.prepend(element('span',t('Archived'),'pill neutral'));
    if(pageMode==='tasks'&&data.management?.deleteAfter)management.append(
      element('span',t('Delete after')+' '+new Date(data.management.deleteAfter).toLocaleString(),'help'));
    management.hidden=!management.childElementCount;
    const workbench=$('[data-wf-run-workbench]');
    workbench.hidden=pageMode==='tasks'||!!run.spec.simpleTask;
    if(!workbench.hidden) {
      graph($('[data-wf-run-graph]'),run.spec,run);
      renderRunStep(run,run.spec.nodes.some(item=>item.key===selectedRunStep)?selectedRunStep:run.current);
    }
    controlButtons(data);
    $('[data-wf-interrupted]').hidden=!interrupted&&(run.state!=='FAILED'||run.events.at(-1)?.type!=='INTERRUPTED');
    $('[data-wf-confirm-stop]').hidden=!task||!(interrupted||run.state==='STOPPING'&&data.activity?.stale);
    const inspect=$('[data-wf-inspect]');inspect.hidden=!task||!data.activity?.attemptId;
    if(!inspect.hidden)inspect.href='/ui/configurations?'+new URLSearchParams({view:'workbench',source:task.requiredCapabilities.includes('execution-guard-v1')?'guarded':'native',plan:run.id,task:task.id.value||task.id,attempt:data.activity.attemptId})+'#worker-run';
    $('[data-wf-supplement]').hidden=interrupted||!['RUNNING','PAUSED','WAITING_USER','WAITING_MEMBER'].includes(run.state);
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
    const profileFailure=run.state==='FAILED'&&['MANAGER_MODEL_FAILED','PROFILE_UNAVAILABLE'].includes(run.events.at(-1)?.type)?run.events.at(-1).content:'';
    $('[data-wf-preparation]').textContent=(run.memberWait&&['WAITING_MEMBER','PAUSED'].includes(run.state)?t(run.memberWait)+(!run.selectingRuntimeProfile&&node?.requiredSkills?.length?' ('+node.requiredSkills.join(', ')+')':''):'') || profileFailure || data.preparation.map(p=>t(p.message)).join('\n') || (task?.state==='READY'?t('Waiting for a compatible Worker'):'');
    const detail=$('[data-wf-worker-detail]'), activityTask=task?.id.value||data.activity?.taskId;
    detail.hidden=!activityTask;detail.href=activityTask?'/ui/plans/'+encodeURIComponent(run.id)+'/tasks/'+encodeURIComponent(activityTask):'#';
    const timeline=$('[data-wf-timeline]');timeline.replaceChildren();
    for(const event of run.events) {
      const section=element('details',undefined,'wf-event');section.open=['RESULT','DRAFT_RESULT','FAILED','QUESTION','INTERRUPTED'].includes(event.type);
      section.append(element('summary',new Date(event.time).toLocaleTimeString()+' · '+(run.spec.nodes.find(n=>n.key===event.node)?.title||'')+' · '+t(event.type)),element('pre',['INTERRUPTED','WAITING_MEMBER','GENERAL_ASSIGNED'].includes(event.type)?t(event.content):event.content));timeline.append(section);
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
      window.dispatchEvent(new Event('aiw:desktop-location'));
    }
  }
  async function control(action,answer) {
    if(action==='stop'&&!window.confirm(t('Stop this execution? Existing results will be retained.')))return;
    if(action==='retry'&&!window.confirm(t('Retry this step? Existing results will be retained; this starts another execution.')))return;
    if(action==='accept-result'&&!window.confirm(t('Use the recorded successful result and continue the workflow?')))return;
    await api('/executions/'+encodeURIComponent(selectedRun)+'/control',{version:currentView.run.version,action,answer});signature='';await refreshRun();await library();
  }
  const recoveryDialog=$('[data-wf-recovery]'),effectForm=$('[data-wf-effect-form]'),stopForm=$('[data-wf-stop-form]');
  let recoveryCandidate=null;
  async function loadRecovery(task,attempt) {
    const path='/execution-tasks/'+encodeURIComponent(task);
    const {candidate,effects}=await api(path+'/recovery',undefined,'/api/r1/authority');
    const currentTask=currentView?.task?.id;
    if(candidate.planId!==selectedRun||candidate.taskId!==task||(currentTask?.value||currentTask)!==task||
        (attempt&&candidate.attemptId!==attempt))throw new Error('The execution changed. Reload before confirming.');
    recoveryCandidate=candidate;
    effectForm.reset();stopForm.reset();
    effectForm.hidden=effects.length===0;stopForm.hidden=effects.length!==0;
    effectForm.elements.operationId.replaceChildren(...effects.map(effect=>{
      const option=new Option((effect.tool||'')+' · '+(effect.resource||''),effect.id);
      option.dataset.evidenceRef=effect.evidenceRef||'';
      return option;
    }));
    effectForm.elements.operationId.onchange();
    const external=!candidate.evidenceDigest;
    $('[data-wf-external-evidence]').hidden=!external;
    stopForm.elements.evidenceRef.required=external;
    stopForm.elements.evidenceDigest.required=external;
    $('[data-wf-recovery-evidence]').textContent=t(effects.length
      ?'Check the original workspace and record what happened before releasing the task.'
      :external?'Verify the original Worker has stopped and supply the evidence from its device.'
      :'Local Worker exit evidence found. Verify the processes have stopped before confirming.');
  }
  async function submitRecovery(form,work) {
    if(!recoveryCandidate||form.dataset.sending)return;
    form.dataset.sending='true';
    const submit=form.querySelector('button');
    submit.disabled=true;
    $('[data-wf-recovery-status]').textContent='';
    try {await work(new FormData(form),recoveryCandidate);}
    catch(error){
      $('[data-wf-recovery-status]').textContent=t(error instanceof TypeError||/^\d{3} /.test(error.message)
        ?'The operation could not be confirmed. Reload its current state before submitting again.':error.message);
    } finally {delete form.dataset.sending;submit.disabled=false;}
  }
  function example() {
    showEditor({id:null,version:0,spec:{title:'售后申请受理',goal:'检查申请是否附有购买凭证，记录人工受理决定。',entry:'receipt',nodes:[
      {key:'receipt',title:'检查购买凭证',kind:'RULE',instruction:'',rule:{source:'INPUT',field:'hasReceipt',expected:'true'},branches:{match:'confirm',otherwise:'missing'}},
      {key:'confirm',title:'确认受理',kind:'ASK',instruction:'申请已附购买凭证。是否受理？',branches:{'受理':'accepted','拒绝':'declined'}},
      {key:'accepted',title:'已受理',kind:'END',instruction:''},
      {key:'declined',title:'未受理',kind:'END',instruction:''},
      {key:'missing',title:'缺购买凭证',kind:'END',instruction:''}
    ]}});
    savedSpec='';$('[data-wf-input]').value=JSON.stringify({order:'DEMO-001',hasReceipt:true},null,2);
  }
  function verificationExample() {
    showEditor({id:null,version:0,spec:{
      title:t('Implement and verify'),
      goal:t('Convert the supplied record to one JSON object with three exact keys: title, language, source. Preserve the values exactly.'),
      entry:'prepare',
      nodes:[
        {key:'prepare',title:t('Prepare a JSON record'),kind:'TASK',next:'verify',
          instruction:t('Deliver the JSON object in the formal result, with no surrounding prose. Apply any supplied correction.')},
        {key:'verify',title:t('Verify the delivered record'),kind:'DECISION',branches:{accept:'accepted',revise:'findings'},
          instruction:t('Read the verified upstream report from the latest preparation step. Check the exact JSON keys and every value against the input. If the report or required data is missing, choose revise. State the missing or incorrect field in the reason. Use accept only when all checks pass.')},
        {key:'findings',title:t('Decide how to handle the findings'),kind:'ASK',branches:{[t('Revise the result')]:'prepare',[t('Close without acceptance')]:'closed'},
          instruction:t('Read the checks in the execution timeline. Supply a correction if you want to generate and verify a fresh result, or close this run without acceptance.')},
        {key:'accepted',title:t('Accepted'),kind:'END',instruction:''},
        {key:'closed',title:t('Closed without acceptance'),kind:'END',instruction:''}
      ]
    }});
    savedSpec='';$('[data-wf-input]').value='title: 明日のニュース\nlanguage: ja\nsource: sample-01';
  }
  taskForm.onsubmit=event=>{
    event.preventDefault();
    act(async()=>{
      const form=new FormData(taskForm);
      const workspaceTools=form.has('workspaceTools');
      status(t('Creating task…'));
      const run=await api('/tasks',{requestId:taskRequest,instruction:form.get('instruction'),acceptance:form.get('acceptance'),requiredCheckIds:form.getAll('requiredCheckIds'),location:form.get('location')||null,workspaceTools,autonomousShell:form.has('autonomousShell'),commands:workspaceTools?commands(taskForm.elements.commands):[],...assignment($('[data-wf-member]').value),runtimeProfile:form.get('runtimeProfile')||null,requiredSkills:taskSkills,sources:taskSources,sharedTools:taskSharedTools});
      taskRequest=newRequest();
      taskForm.elements.autonomousShell.checked=false;
      taskForm.elements.workspaceTools.checked=taskForm.elements.workspaceTools.defaultChecked;
      renderTaskFileOptions();
      status(t('Task created.'));
      library().catch(()=>{});
      try { await openRun(run.id); }
      catch(error) { status(t('Task created, but its details could not be loaded.')+' '+t(error.message)); }
    });
  };
  $$('[data-wf-new]').forEach(b=>b.onclick=()=>openEditorWindow());
  $('[data-wf-sample]').onclick=example;
  $('[data-wf-verify-sample]').onclick=verificationExample;
  $('[data-wf-refresh]').onclick=()=>act(async()=>{signature='';importAttempted=false;await refreshRun();await library();});
  $$('[data-wf-drag-kind]').forEach(control=>{
    let dragEndedAt=0;control.onclick=()=>{if(Date.now()-dragEndedAt>400)addNode(control.dataset.wfDragKind);};
    control.ondragstart=event=>event.dataTransfer.setData('text/plain',control.dataset.wfDragKind);
    control.ondragend=()=>dragEndedAt=Date.now();
  });
  $('[data-wf-save]').onclick=()=>act(async()=>{
    if(editing.spec.nodes.some(node=>node.runtimeProfileCandidates?.length===0))throw new Error(t('Select at least one candidate runtime profile.'));
    editing.spec.title=$('[data-wf-title]').value;editing.spec.goal=$('[data-wf-goal]').value;editing.spec.commands=commands($('[data-wf-commands]'));editing.spec.autonomousShell=$('[data-wf-shell]').checked;editing=await api('/definitions',editing);savedSpec=JSON.stringify(editing.spec);startRequest=newRequest();await library();status(t('Workflow saved'));
  });
  $('[data-wf-start]').onclick=()=>act(async()=>{
    editing.spec.title=$('[data-wf-title]').value;editing.spec.goal=$('[data-wf-goal]').value;editing.spec.commands=commands($('[data-wf-commands]'));editing.spec.autonomousShell=$('[data-wf-shell]').checked;
    if(!editing.id||savedSpec!==JSON.stringify(editing.spec))throw new Error(t('Save changes before starting'));
    const run=await api('/start',{requestId:startRequest,definitionId:editing.id,version:editing.version,input:$('[data-wf-input]').value,location:$('[data-wf-location]').value||null});
    startRequest=newRequest();await library();await openRun(run.id);
  });
  $$('[data-wf-control]').forEach(b=>b.onclick=()=>act(()=>control(b.dataset.wfControl)));
  $('[data-wf-confirm-stop]').onclick=()=>act(async()=>{
    const id=currentView?.task?.id;
    const task=id?.value||id;
    if(!task)return;
    recoveryCandidate=null;
    $('[data-wf-recovery-status]').textContent='';
    await loadRecovery(task,currentView.activity?.attemptId);
    recoveryDialog.showModal();
  });
  $('[data-wf-recovery-close]').onclick=()=>recoveryDialog.close();
  effectForm.elements.operationId.onchange=()=>{
    effectForm.elements.evidenceRef.value=effectForm.elements.operationId.selectedOptions[0]?.dataset.evidenceRef||'';
    effectForm.elements.evidenceDigest.value='';
  };
  effectForm.onsubmit=event=>{
    event.preventDefault();
    submitRecovery(effectForm,async(form,candidate)=>{
      await api('/execution-effects/reconcile',Object.fromEntries(form),'/api/r1/authority');
      await loadRecovery(candidate.taskId,candidate.attemptId);
    });
  };
  stopForm.onsubmit=event=>{
    event.preventDefault();
    submitRecovery(stopForm,async(form,candidate)=>{
      await api('/execution-tasks/'+encodeURIComponent(candidate.taskId)+'/stopped',{
        attemptId:candidate.attemptId,evidenceRef:candidate.evidenceRef||form.get('evidenceRef').trim(),
        evidenceDigest:candidate.evidenceDigest||form.get('evidenceDigest'),allProcessesQuiescent:form.has('quiescent')
      },'/api/r1/authority');
      recoveryCandidate=null;
      recoveryDialog.close();
      signature='';await refreshRun();await library();
      if(currentView?.run.state==='FAILED'){
        status(t('Stop confirmed. Retry this step when ready.'));
        $('[data-wf-control="retry"]').focus();
      }
    });
  };
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
    if(form.has('workspaceTools'))throw new Error('Schedules currently support text workflows only');
    if(String(form.get('location')||'').trim())throw new Error('Schedules use temporary locations; attach selected text sources');
    const acceptance=String(form.get('acceptance')||'').trim();
    arrange({title:instruction.slice(0,80),goal:instruction,entry:'task',simpleTask:true,nodes:[
      {key:'task',title:'Task',kind:'TASK',instruction,acceptance:acceptance||null,next:null,branches:{},...assignment($('[data-wf-member]').value),runtimeProfile:form.get('runtimeProfile')||null,requiredSkills:taskSkills}],sources:taskSources,commands:[]});
  });
  $('[data-wf-schedule-form]').onsubmit=e=>{e.preventDefault();act(async()=>{
    const form=new FormData(e.target);
    await api('/schedules',{requestId:scheduleRequest,spec:scheduleSpec,repeat:form.get('repeat'),zone:Intl.DateTimeFormat().resolvedOptions().timeZone||'UTC',firstAt:new Date(form.get('firstAt')).getTime()});
    $('[data-wf-schedule-editor]').hidden=true;scheduleSpec=null;
    if(pageMode==='tasks'&&taskPage==='new')window.location.assign(taskListUrl());
    else {
      window.aiwDesktopWindow?.close($('[data-wf-task]'));
      await library();
    }
  });};
  window.addEventListener('aiw:locale-changed',()=>{translate();const member=$('[data-wf-member]');memberOptions(member,member.value);renderSources($('[data-wf-task-sources]'),taskSources,values=>taskSources=values);if(editing)renderSources($('[data-wf-definition-sources]'),editing.spec.sources||[],values=>editing.spec.sources=values);signature='';$('[data-wf-usage]').dataset.stamp='';$('[data-wf-amendment]').dataset.stamp='';if(editing){renderNodes();redraw();}act(async()=>{await library();await refreshRun();});});
  if(pageMode==='tasks'&&taskPage==='list') {
    $('[data-wf-new-task]').addEventListener('click',event=>{
      if(event.button!==0||event.ctrlKey||event.metaKey||event.shiftKey||event.altKey||!window.aiwDesktopWindow)return;
      event.preventDefault();
      if(!busy)showNewTask();
    });
    const search=$('[data-wf-history-search]'),view=$('[data-wf-history-view]'),state=$('[data-wf-history-state]');
    search.value=(route.get('q')||'').slice(0,160);
    if([...view.options].some(option=>option.value===route.get('view')))view.value=route.get('view');
    if([...state.options].some(option=>option.value===route.get('state')))state.value=route.get('state');
    updateTaskListUrl();
    const loadHistory=()=>refreshTaskHistory().catch(error=>status(t(error.message)));
    const filterHistory=(delay=0)=>{
      clearTimeout(taskHistoryTimer);
      ++taskHistoryRequest;
      taskHistoryPage=1;
      taskHistoryHasMore=false;
      updateTaskListUrl();
      taskHistoryTimer=setTimeout(loadHistory,delay);
    };
    search.addEventListener('input',()=>filterHistory(250));
    view.addEventListener('change',()=>filterHistory());
    state.addEventListener('change',()=>filterHistory());
    $('[data-wf-history-refresh]').onclick=()=>act(library);
    $('[data-wf-history-prev]').onclick=()=>{clearTimeout(taskHistoryTimer);if(taskHistoryPage>1){taskHistoryPage--;updateTaskListUrl();loadHistory();}};
    $('[data-wf-history-next]').onclick=()=>{clearTimeout(taskHistoryTimer);if(taskHistoryHasMore){taskHistoryPage++;taskHistoryHasMore=false;updateTaskListUrl();loadHistory();}};
  }
  translate();renderSources($('[data-wf-task-sources]'),taskSources,values=>taskSources=values);
  if(pageMode!=='tasks'||taskPage==='new')loadProfiles().catch(error=>status(t(error.message)));
  if(document.body.dataset.wfAdmin==='true'&&(pageMode!=='tasks'||taskPage==='new'))
    loadSharedTools().catch(error=>status(t(error.message)));
  if(pageMode==='tasks') {
    const back=$('[data-wf-back]');if(back)back.href=taskListUrl();
    if(taskPage==='new') {
      window.addEventListener('DOMContentLoaded',()=>{
        if(window.aiwDesktopWindow)showNewTask();
        else {panel('task');$('[data-wf-task]').append($('[data-wf-schedule-editor]'));loadMembers();}
      },{once:true});
    } else if(taskPage==='detail') {
      window.addEventListener('DOMContentLoaded',()=>act(()=>openRun(taskExecution)),{once:true});
    } else {
      panel('');library().catch(error=>{if(!busy&&!selectedRun)status(t(error.message));});
    }
  } else {
    panel('');
    window.addEventListener('DOMContentLoaded',()=>act(async()=>{
      if(route.get('execution'))await openRun(route.get('execution'));
      else if(route.get('definition'))openEditorWindow(await api('/definitions/'+encodeURIComponent(route.get('definition'))));
      await library();
    }),{once:true});
  }
  setInterval(()=>{if(selectedRun&&!busy&&!document.hidden&&!(preparingGoal&&importAttempted))act(refreshRun);},4000);
  if(pageMode!=='tasks'||taskPage==='list'){
    refreshCaretaker().catch(()=>{});
    setInterval(()=>{if(!document.hidden)refreshCaretaker().catch(()=>{});},8000);
  }
})();
