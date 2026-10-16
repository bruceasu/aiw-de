(() => {
  const translations = {
    'Personalization': '个性化偏好',
    'Your console, your way': '按你的习惯设置控制台',
    'Changes apply immediately and are saved in this browser.': '修改立即生效，并保存在当前浏览器。',
    'Appearance': '外观',
    'One setting for all floating windows. Default: 61.8%.': '所有浮动窗口统一设置，默认不透明度为 61.8%。',
    'Display language': '界面语言',
    'Date and time': '日期与时间',
    'Show sidebar time': '显示侧栏时间',
    'Show sidebar date and time zone': '显示侧栏日期与时区',
    'Motion': '动画效果',
    'Enable animations': '启用动画',
    'System reduced motion settings always take precedence.': '优先遵循系统的“减少动态效果”设置。',
    'Your system currently reduces motion. Animations remain disabled.': '系统当前启用了减少动态效果，动画会保持关闭。',
    'Restore defaults': '恢复默认设置',
    'Done': '完成',
    'Preferences saved in this browser.': '偏好设置已保存在当前浏览器。',
    'Applied for this page. Browser storage is unavailable.': '已应用到当前页面；浏览器存储不可用，无法持久保存。',
    'Opacity': '不透明度',
    'Window background opacity': '窗口背景不透明度',
    'Lower opacity makes the window more transparent': '数值越低，窗口越透明',
    'Questions and files': '待决问题与文件',
    'Read the source, answer each pending question, then confirm. Confirmed answers are supplied to future tasks; saving does not start work.': '查看来源，填写并确认待决问题。已确认的回答会传给后续任务；保存不会启动执行。',
    'Refresh questions': '刷新问题',
    'Related files': '相关文件',
    'Answer history': '回答历史',
    'Source file preview': '来源文件预览',
    'Loading source file…': '正在读取来源文件…',
    'Preview source': '预览来源',
    'Download source': '下载来源文件',
    'View source task': '查看来源任务',
    'Preview truncated. Download the source file for the full content.': '预览已截断，完整内容请下载来源文件。',
    'Source preview timed out. Try again.': '来源预览超时，请重试。',
    'No structured questions found. Check the related files and review reports.': '未发现结构化问题，请查看相关文件和评审报告。',
    'Answer confirmed': '回答已确认',
    'Decision needed': '等待决策',
    'Question context': '问题上下文',
    'Your decision': '你的决策结论',
    'Confirm answer': '确认回答',
    'Confirm updated answer': '确认修改后的回答',
    'Enter an answer before confirming.': '请先填写回答再确认。',
    'Confirm this answer as a manager decision? It will be supplied to future execution and review tasks. This does not start work.': '确认将此回答记录为管理决策吗？它会传给后续执行和评审任务；本次确认不会启动执行。',
    'Saving decision…': '正在保存决策…',
    'Answer confirmed. Use Mission Control to authorize the next repair or review.': '回答已确认。请在任务指挥台授权下一轮修复或发起评审。',
    'Decision request timed out. Refresh and check answer history before submitting again.': '决策请求超时，请先刷新并核对回答历史，再决定是否重新提交。',
    'Question request timed out. Refresh to try again.': '问题请求超时，请刷新重试。',
    'Your session changed. Reload before answering.': '会话已变化，请重新加载页面后再回答。',
    'This plan is not open for decisions.': '此计划当前不接受决策修改。',
    'Resolve the pending plan control before answering.': '请先处理待完成的计划控制操作，再确认回答。',
    'Wait for pending or running tasks to finish before confirming answers.': '请等待排队或执行中的任务结束，再确认回答。',
    'Revoke or finish the pending repair authorization before changing answers.': '请先撤销或完成待处理的修复授权，再修改回答。',
    'Decision history': '决策历史',
    'Plan tools': '计划工具栏',
    'Move window': '移动窗口',
    'Maximize or restore window': '最大化或还原窗口',
    'Hide window': '隐藏窗口',
    'Needs attention': '需要处理',
    'This plan needs a decision before work can continue.': '计划需要新的决策，才能继续执行。',
    'Open Mission control': '打开任务指挥台',
    'No decision recorded.': '暂无决策记录。',
    'No repair authorization recorded.': '暂无修复授权记录。',
    'Mission control': '任务指挥台',
    'Choose your next move. Every decision stays in the mission history.': '选择下一步行动，每次决策都会留在任务历史中。',
    'Continue the mission': '继续推进任务',
    'Give the Worker a fixed objective, then send it back for an independent review.': '给 Worker 明确的修复目标，完成后再交由独立评审。',
    '1 repair + 1 review': '1 轮修复 + 1 次评审',
    'Valid for 1 hour': '授权有效期 1 小时',
    'Repair decision reason': '修复授权原因',
    'Why should this repair proceed?': '为什么需要继续这一轮修复？',
    'An assigned AI may decide only within this scope. It cannot expand the scope or renew the grant.': '指定 AI 只能在此范围内决策，不能扩大范围或续期授权。',
    'Check the current result': '核验当前成果',
    'Send the current work to an independent reviewer. This action does not start another coding round.': '将当前成果交给独立评审，此操作不会启动新一轮编码。',
    'Review decision reason': '重新评审原因',
    'What changed since the last review?': '相比上次评审，做了哪些补正？',
    'Request independent review': '发起独立评审',
    'Ready for launch': '准备启动',
    'Confirm the selected steps to create Worker tasks and begin the mission.': '确认所选步骤，创建 Worker 任务并开始执行。',
    'Other mission controls': '其他任务操作',
    'Start a new plan from this mission.': '以当前任务为基础创建新计划。',
    'A decision Worker is assessing the fixed repair scope.': '决策 Worker 正在评估已固定的修复范围。',
    'Review needs changes. Authorize a bounded repair or request another review.': '评审要求补正，可授权一轮限定范围的修复，或发起重新评审。',
    'Workers are active. Follow task progress before choosing another action.': 'Worker 正在执行，请结合任务进展选择后续操作。',
    'Review your selected steps, then confirm the draft to start.': '检查所选步骤，确认草稿后开始执行。',
    'Repair is unavailable. Refresh the plan to check its current state.': '当前不能授权修复，请刷新计划核对状态。',
    'Enter a repair decision reason.': '请填写修复授权原因。',
    'Enter a fixed repair scope.': '请填写固定修复范围。',
    'Waiting for confirmation…': '等待确认…',
    'Submitting authorization…': '正在提交授权…',
    'Repair authorization cancelled.': '已取消本次授权，未提交修复。',
    'Authorization was not used because the plan changed or the grant expired. Check authorization history.': '计划已变更或授权已过期，本次授权未被使用，请核对授权历史。',
    'Authorization request timed out. Refresh and check authorization history before submitting again.': '授权请求超时，请先刷新并核对授权历史，再决定是否重新提交。',
    'Model (optional)': '模型（可选）',
    'Reasoning effort (optional)': '推理强度（可选）',
    'Leave empty to let the Worker decide. Invalid or unavailable settings fall back to the Worker configuration.': '留空时由 Worker 自行决定；配置错误或不可用时，回退到 Worker 自己的配置。',
    'Worker choice': '由 Worker 决定',
    'Long-running task. Check Worker progress; elapsed time alone does not stop execution.': '任务执行时间较长，请检查 Worker 进展；仅因耗时较长不会终止执行。',
    'Task deliverables': '任务交付物',
    'Reports and attachments are saved in the persistent Workspace. Project documents require a passed source review, then a publication plan and confirmation.': '报告与附件保存在持久工作区。正式文档须有通过的来源评审，再创建入库计划并确认执行。',
    'Loading deliverables…': '正在读取交付物…',
    'No deliverables registered yet.': '暂未登记交付物。',
    'Deliverable request failed. Check your permissions and reload the page.': '交付物请求失败，请检查权限并刷新页面。',
    'Deliverable request failed.': '交付物请求失败。',
    'Independent review passed': '独立评审通过',
    'Awaiting a passed independent review': '尚无通过的独立评审',
    'Unfinished candidate': '未完成候选',
    'Review report': '评审报告',
    'Output registered': '产出已登记',
    'Execution claimed': '执行已领取',
    'Execution running': '执行中',
    'Execution completed': '执行已完成',
    'Execution failed': '执行失败',
    'Execution expired': '执行已过期',
    'bytes': '字节',
    'Passed source review task ID': '通过的来源评审任务 ID',
    'Project document path (for example, docs/requirements.md)': '项目文档路径（例如 docs/requirements.md）',
    'Publication reason': '入库原因',
    'Create document publication draft': '创建文档入库草稿',
    'Save as a project document': '保存为项目文档',
    'View report': '查看报告',
    "This task failed. Fix the cause, then retry this task to continue the plan.": "任务失败导致计划暂停推进。修复原因后，可重试当前任务来继续计划。",
    "After fixing the cause, enter a reason and retry the failed task below. Earlier accepted outputs and dependencies remain unchanged.": "修复原因后，在下方填写原因并重试失败任务。已验收的前置产出和依赖关系会保留。",
    "Retrying keeps the same plan and task IDs and adds a new execution attempt. Check local changes left by the failed attempt before confirming. No earlier step is restarted.": "重试保留原计划和任务 ID，并增加一次执行记录。确认前请检查失败执行遗留的本地改动；此前步骤不会重新执行。",
    "Retry reason": "重试原因",
    "Describe what was fixed before retrying": "请说明重试前修复了什么问题",
    "Retry failed task": "重试失败任务",
    "Enter a retry reason.": "请填写重试原因。",
    "Submitting retry…": "正在提交重试…",
    "Retry only this failed task? Earlier accepted outputs remain. Check local changes first; this starts another Worker execution.": "确定只重试当前失败任务吗？已验收的前置产出会保留。请先检查本地改动；确认后将启动新一轮 Worker 执行。",
    "Retry failed. Reload the page and check the current task state.": "重试提交失败。请刷新页面并检查当前任务状态。",
    "Your session changed. Reload before retrying.": "会话已变化。请刷新页面后再重试。",
    "Plan or task changed. Reload before retrying.": "计划或任务已变化。请刷新页面后再重试。",
    "Retry reason must contain 1 to 2000 characters.": "重试原因须为 1 至 2000 个字符。",
    "Confirm the retry warning before continuing.": "请确认重试提示后再继续。",
    "This plan has ended or is archived. Its tasks cannot be retried.": "计划已结束或归档，不能重试其中的任务。",
    "Resolve the pending stop request before retrying.": "请先处理待确认的停止请求，再重试。",
    "Only a failed task can be retried.": "只有失败任务可以重试。",
    "Only the current failed task can be retried. This plan has already advanced.": "只能重试当前失败任务；计划已经推进，不能回退重试旧任务。",
    "Another task is pending or running. Wait before retrying.": "还有其他任务在等待或执行，请先等待。",
    "This plan has already passed its final review.": "计划已经通过最终审核。",
    "To change the goal or selected steps, copy the plan into a new draft.": "如果需要修改目标或所选步骤，请将计划复制为新草稿。",
    "Copy to change the plan": "复制以修改计划",
    "Manual retries (latest 50)": "人工重试记录（最近 50 条）",
    "Actor": "操作人",
    "Sandbox namespace creation failed. Check Linux user-namespace support and the host or container sandbox configuration before retrying.": "沙箱无法创建 Linux namespace。请先检查宿主机或容器的用户命名空间支持及沙箱配置，再重新执行。",
    "A required command was not found. Check that the tool is installed and available in the Worker process PATH.": "找不到所需命令。请检查工具是否已安装，以及 Worker 进程的 PATH 是否能找到它。",
    "The command was denied access. Check filesystem permissions and whether the role is using a read-only workspace.": "命令访问被拒绝。请检查文件权限，以及当前角色是否使用只读工作空间。",
    "The command referenced a missing path. Check the registered Workspace and the paths used by the tool.": "命令引用的路径不存在。请检查已注册的工作空间和工具使用的路径。",
    "A command could not reach its network target. Check the target address from the Worker environment.": "命令无法连接网络目标。请从 Worker 所在环境检查目标地址。",
    "New Codex failures log the tool type, call ID, exit code, diagnostic code and evidence directory. Only known error patterns are classified; unrecognized failures remain UNRESOLVED_TOOL_FAILURE. Raw command output is not retained.": "新的 Codex 失败记录会包含工具类型、调用 ID、退出码、诊断码及证据目录。仅对已识别的错误模式分类；无法识别时仍显示 UNRESOLVED_TOOL_FAILURE。不会保存原始命令输出。",
    "Task details": "任务详情",
    "Task details · AI Dev Manager": "任务详情 · AI Dev Manager",
    "Execution history, failures and recovery": "执行历史、失败诊断与恢复",
    "Back to plan": "返回计划",
    "Task overview": "任务概况",
    "Task ID": "任务 ID",
    "Skill": "技能",
    "Required capabilities": "必需能力",
    "Workspace": "工作空间",
    "Updated": "更新时间",
    "Started": "开始时间",
    "Finished": "结束时间",
    "Not recorded": "未记录",
    "Failure category": "失败分类",
    "Error code / termination reason": "错误码 / 终止原因",
    "Instruction revision": "指令版本",
    "Result revision": "结果版本",
    "Attempt": "执行尝试",
    "Attempt ID": "执行 ID",
    "Verdict": "审核判定",
    "Recorded report": "已记录的报告",
    "Task instruction": "任务指令",
    "Execution history": "执行历史",
    "Execution": "执行",
    "Independent review": "独立审核",
    "View task details": "查看任务详情",
    "Updates every 5 seconds": "每 5 秒更新状态",
    "View all records": "查看全部记录",
    "Newest records first": "最新记录在最上方",
    "Review again after correction": "补正后重新评审",
    "Repair authorization history": "修复授权历史",
    "Authorize one repair round": "授权一轮修复",
    "Fixed repair scope": "固定修复范围",
    "Who decides whether to proceed?": "由谁决定是否执行？",
    "Human approval now": "人工直接批准",
    "Authorize continued repair": "授权继续修复",
    "State allowed changes, accepted requirement decisions, and permitted checks.": "填写允许的修改范围、已确认的需求决策及允许执行的检查。",
    "Use the decision reason above. One repair execution and one independent review; authorization expires in one hour. An AI may use only this fixed scope and cannot renew the grant.": "使用上方的决策原因。仅授权一次修复和一次独立评审，一小时后到期。AI 只能使用此固定范围，不能续批。",
    "Enter a decision reason and a fixed repair scope.": "请填写决策原因和固定修复范围。",
    "Authorize one repair round within the entered scope? An assigned AI cannot change the scope or add rounds.": "确认在填写的范围内授权一轮修复？指定的 AI 不能修改范围或增加轮数。",
    "One-round repair authorization recorded.": "已记录一轮修复授权。",
    "Decision report": "决策报告",
    "Revoke authorization": "撤销授权",
    "Revoke this unused repair authorization? No new repair will be started.": "撤销这份尚未使用的修复授权？撤销后不会触发新的修复。",
    "WAITING_FOR_RECOVERY_DECISION": "等待修复授权决策",
    "CONSUMED": "已使用",
    "DENIED": "已拒绝",
    "STALE": "已失效",
    "Review the current implementation again with the accepted upstream outputs? Prior attempts remain. Coding is not rerun.": "携带已接受的前置产出，重新独立评审当前实现？旧记录保留，不会重新执行编码。",
    "New independent review queued. Prior attempts remain unchanged.": "已创建新的独立评审任务，旧执行记录保持不变。",
    "Close": "关闭",
    "Status refresh failed. Previous data remains visible.": "状态更新失败，保留上次显示的数据。",
    "Status request timed out or was interrupted. The next update will retry.": "状态请求超时或已中断，将在下次更新时重试。",
    "Status updates paused while editing, confirming or selecting text.": "正在编辑、确认操作或选择文本，已暂停状态更新。",
    "This plan needs attention. Open task details to inspect execution history and recovery steps.": "计划需要处理。请打开任务详情，查看执行历史与恢复指引。",
    "What to do after failure": "失败后怎么办",
    "This task failed. Later steps wait until the failure is resolved and work is delegated again.": "任务已失败。需要先解决问题并重新委派工作，后续步骤才能继续。",
    "Check the latest attempt below for its Worker, failure category and error code.": "查看下方最近一次执行的 Worker、失败分类和错误码。",
    "Inspect that Worker's logs and attempt evidence. Fix the failed command, tool, permissions or runtime configuration before trying again.": "检查该 Worker 的日志与执行证据。修复失败的命令、工具、权限或运行时配置后再尝试。",
    "Check Fleet for the execution role and an independent workflow-review Worker. Start the roles required by later steps as well.": "到 Worker 集群页面确认执行角色和独立 workflow-review Worker 可用，并启动后续步骤所需的角色。",
    "Copy this plan into a new draft, review its steps and Workspace, save it, then confirm the new draft. Copying alone does not start work.": "将计划复制为新草稿，检查步骤和工作空间，保存后再确认新草稿。仅复制不会开始执行。",
    "The copy starts from the first selected step and can repeat completed work. Review local files and side effects before confirming. The original failure remains recorded.": "新计划从第一个所选步骤开始，可能重复已完成的工作。确认前请检查本地文件和已有改动。原失败记录会保留。",
    "Copy plan to recover": "复制计划重新执行",
    "No Worker has claimed this task yet. Check Fleet matching and task prerequisites.": "尚无 Worker 领取此任务。请检查集群能力匹配和任务前置条件。",
    "This historical attempt has no stored error code. Check its Worker logs; the page cannot reconstruct missing diagnostics.": "这次历史执行未保存错误码。请查看 Worker 日志；页面无法还原缺失的诊断信息。",
    "Tool or output validation failed. Check the failed command and its output in Worker-side diagnostics. Restarting a Worker alone does not retry this task.": "工具或输出校验失败。请在 Worker 侧诊断记录中检查失败的命令及输出。仅重启 Worker 不会重试此任务。",
    "No completion report was recorded for this attempt.": "这次执行没有记录完成报告。",
    "Where to find diagnostics": "在哪里查看诊断",
    "Run these commands in the TypeScript Worker directory on the machine running the Worker. Replace <role> with requirements, coder or stage-reviewer as appropriate.": "在运行 Worker 的机器上，进入 TypeScript Worker 目录执行以下命令。将 <role> 替换为对应的 requirements、coder 或 stage-reviewer 等角色。",
    "Attempt evidence is stored outside the working directory, under AIW_WORKER_EVIDENCE_ROOT or the default .aidev-attempt-evidence directory. Match owner.json by Attempt ID. Historical records may not include command output.": "执行证据存放在工作目录之外，位于 AIW_WORKER_EVIDENCE_ROOT 指定位置或默认的 .aidev-attempt-evidence 目录。使用执行 ID 匹配 owner.json；历史记录可能没有保存命令输出。",
    'Confirm action': '确认操作', 'Confirm': '确认',
    'Stop this plan early? Completed results remain; unfinished work will be stopped or cancelled.': '确定提前停止计划吗？已完成的结果会保留；未完成的工作将停止或取消。',
    'Complete by human decision? Unfinished Worker tasks will be stopped or cancelled.': '确定由管理员标记完成吗？未完成的 Worker 任务将停止或取消。',
    'Cancel this plan and delete its verified local isolated Git branches? Stop active Workers first.': '确定取消计划并删除已验证的本地隔离 Git 分支吗？请先停止正在执行的 Worker。',
    'Schedule database deletion in 30 days?': '确定安排在 30 天后删除数据库记录吗？',
    'Mark this worker offline?': '确定将此 Worker 设为离线吗？',
    'Language': '语言', 'English': 'English', '简体中文': '简体中文',
    'New Plan': '新建计划', 'Plans': '计划', 'Workspaces': '工作空间', 'Fleet': 'Worker 集群', 'System': '系统',
    'Authority remains in the Control Plane.': '控制权限仍由 Control Plane 管理。', 'Motion: on': '动画：开', 'Motion: off': '动画：关', 'Sign out': '退出登录',
    'Plan': '计划', 'Saved selection, actual tasks and recorded audit': '已保存的步骤、实际任务和审计记录',
    'All plans': '返回计划列表', 'State': '状态', 'Source': '来源', 'Version': '版本', 'Created by': '创建人', 'Updated by / reason': '更新人 / 原因', 'Time': '时间',
    'Source records who proposed the graph, not approval. A draft has no execution tasks.': '来源记录的是计划图的提议者，不代表已获批准。草稿不会创建执行任务。',
    'Selected plan': '已选计划', 'Step / skill': '步骤 / 技能', 'Instruction': '指令', 'Declared dependencies': '声明的依赖', 'Context': '上下文', 'Execution and independent review': '执行与独立审核',
    'A step advances only after an independent PASS review. After three correction attempts, NEEDS_ATTENTION means the plan requires a new manager decision. Task states and reports appear only when recorded.': '每个步骤只有通过独立审核（PASS）后才会继续。经过三次修正后若仍为 NEEDS_ATTENTION，计划需要管理员重新决策。任务状态和报告仅在记录后显示。',
    'Manager decisions': '决策历史', 'Manager actions': '管理员操作', 'Draft': '草稿', 'Copy into editor': '复制到编辑器', 'Confirm selected draft version': '确认当前草稿版本',
    'Withdrawal reason': '撤回原因', 'Withdraw inactive work': '撤回未执行的工作', 'Step control': '步骤控制', 'Jump to step': '跳转到步骤', 'Target step': '目标步骤', 'Decision reason': '决策原因', 'Reason required': '请填写原因', 'Skip to step': '跳过并继续',
    'Plan control': '计划控制', 'Stop plan early': '提前停止计划', 'Complete by human decision': '由管理员标记完成', 'Cancel and discard local Git branches': '取消计划并删除本地 Git 分支', 'Resolve stale stop request': '处理过期的停止请求',
    'Active Worker tasks receive a stop request first. Human completion waits for Worker acknowledgement. Cancellation needs all Workers to finish or stop; it deletes only verified local isolated branches. Published results or direct shared work require manual reversal.': '停止计划时会先通知正在运行的 Worker。由管理员标记完成需要等待 Worker 确认。取消计划前，所有 Worker 都必须完成或停止；系统只会删除已验证的本地隔离分支。已发布的结果或直接写入共享目录的内容需要人工撤回。',
    'Archive and retention': '归档与保留', 'Archive plan': '归档计划', 'Database deletion scheduled after': '数据库计划删除时间：', '. Workspaces and Git results remain.': '。工作空间和 Git 结果会保留。', 'Delete in 30 days': '安排 30 天后删除', 'Undo deletion': '取消删除安排',
    'Current plans': '当前计划', 'Archived plans': '已归档计划', 'Create plan': '创建计划', 'A draft stores selected steps. Confirm it to create Worker tasks. Completed work remains available after a plan is stopped or archived.': '草稿会保存所选步骤。确认后才会创建 Worker 任务。计划停止或归档后，已完成的工作仍可查看。',
    'Goal': '目标', 'Actions': '操作', 'Created': '创建时间', 'Archive': '归档', 'Delete after': '计划删除时间', 'No archived plans.': '没有已归档的计划。', 'No current plans.': '没有当前计划。',
    'New Plan': '新建计划', 'Build, preview, save, then confirm your selected steps': '创建并预览步骤，保存后再确认执行', 'Plan Editor': '计划编辑器',
    'Plan help': '计划帮助', 'Capability guide': '能力说明', 'View Workers →': '查看 Worker →', 'Loading Worker capabilities…': '正在加载 Worker 能力…',
    'Start with a template': '从模板开始', 'Choose template': '选择模板', 'Blank plan': '空白计划', 'Use selection': '应用所选模板', 'Goal and recommendation': '目标与建议', 'Goal': '目标',
    'Ask AI to recommend steps': '让 AI 推荐步骤', 'Check existing analysis': '查看已有分析', 'Withdraw queued analysis': '撤回排队中的分析', 'Apply suggestion to editor': '将建议应用到编辑器',
    'Selected steps': '已选步骤', 'Add step': '添加步骤', 'Review before saving': '保存前检查', 'Step': '步骤', 'Worker role': 'Worker 职责', 'Depends on': '依赖步骤', 'Workspace / revision': '工作空间 / 版本', 'Fleet snapshot': 'Worker 集群快照', 'Save new draft': '保存为新草稿', 'Cancel': '取消',
    'Workspace reference': '工作空间引用', 'Filter Workspaces by name or reference': '按名称或引用筛选工作空间', 'No Workspace selected': '未选择工作空间', 'No matching Workspaces': '没有匹配的工作空间', 'Manage Workspaces': '管理工作空间',
    'Step key': '步骤标识', 'Required technical skills (comma separated)': '必需的技术能力（用逗号分隔）', 'Preferred skills (comma separated)': '优先技能（用逗号分隔）', 'Base revision (optional full Git commit)': '基准版本（可选的完整 Git 提交号）', 'Declared earlier dependencies (comma separated)': '依赖的前置步骤（用逗号分隔）', 'Instruction and acceptance criteria': '指令与验收标准', 'Move up': '上移', 'Move down': '下移', 'Delete step': '删除步骤',
    'Enter a goal before saving.': '保存前请填写目标。', 'Choose 1–32 steps.': '步骤数量需为 1–32。', 'No additional technical capabilities in this snapshot.': '此快照中没有其他技术能力。', 'Step roles': '步骤职责', 'Technical capabilities declared by active Workers': '活跃 Worker 声明的技术能力',
    'Sign in': '登录', 'Authentication is enabled for the browser console.': '控制台已启用登录验证。', 'Username': '用户名', 'Password': '密码', 'Forbidden · AI Dev Manager': '禁止访问 · AI Dev Manager', '403 · Access denied': '403 · 拒绝访问', 'Return to Dashboard': '返回控制台首页', 'Not Found · AI Dev Manager': '未找到 · AI Dev Manager', 'Resource not found': '未找到该资源', 'Return to Plans': '返回计划列表',
    'Fleet Admission': 'Worker 集群准入', 'Max parallelism': '最大并行数', 'Active leases': '活动租约', 'Available slots': '可用槽位', 'Overcommitted': '超额分配', 'Saturated': '已满载', 'Max Parallelism': '最大并行数', 'Reason': '原因', 'Update Fleet Limit': '更新集群限制', 'Accounting Diagnostics': '资源核算诊断', 'Worker lease delta': 'Worker 租约差值', 'Claimable capacity': '可领取容量', 'Current parallelism': '当前并行数', 'Fleet overcommitted': '集群超额分配', 'Accounting is consistent.': '核算一致。', 'Accounting drift detected. Investigate before changing authority.': '检测到核算偏差。请先调查，再更改调度权限。',
    'Machine workforce': 'Worker 列表', 'No workers registered.': '尚未注册 Worker。', 'Active work': '正在执行的工作', 'No active leases in this snapshot.': '此快照中没有活动租约。', 'Worker': 'Worker', 'Task / attempt': '任务 / 尝试次数', 'Plan or subject': '计划或主题', 'Lease expires': '租约到期时间', 'READY work': '待领取任务', 'No READY tasks in this snapshot.': '此快照中没有待领取任务。', 'Worker match': '匹配的 Worker', 'Current conditions': '当前条件', 'Worker operations': 'Worker 操作', 'Drain': '停止接收新任务', 'Offline': '设为离线', 'Wait for active leases': '等待活动租约结束', 'Refresh now': '立即刷新', 'Last heartbeat': '最近心跳',
    'Register Workspace root': '注册工作空间根目录', 'Workspace ID': '工作空间 ID', 'Workspace root directory': '工作空间根目录', 'Register Workspace': '注册工作空间', 'Registered Workspaces': '已注册的工作空间', 'No Workspace has been registered.': '尚未注册工作空间。', 'View and manage': '查看和管理', 'All Workspaces': '返回工作空间列表', 'Workspace details': '工作空间详情', 'Workspace root and registered repositories': '工作空间根目录与已注册仓库', 'No registered repositories.': '没有已注册的仓库。', 'Add repository': '添加仓库', 'Edit Workspace': '编辑工作空间', 'Save name': '保存名称', 'Root directory': '根目录', 'Save root': '保存根目录', 'Disable Workspace': '停用工作空间', 'Delete registration': '删除注册信息', 'Confirm deletion': '确认删除',
    'System · AI Dev Manager': '系统 · AI Dev Manager', 'Control Plane, scheduling authority and accounting diagnostics': 'Control Plane、调度权限与资源核算诊断', 'Fleet Scheduling Audit': '集群调度审计', 'Latest 50 changes': '最近 50 条变更', 'No runtime Fleet scheduling changes recorded.': '尚无运行时集群调度变更记录。', 'Web Console Boundary': 'Web Console 边界', 'Refresh in 8s': '8 秒后刷新',
    'A role describes the work performed; a technical capability describes the expertise or toolchain actually available. The Worker’s implementation language does not grant a task capability. These names are suggested labels, not a list of installed tools.': '职责描述要完成的工作；技术能力描述实际可用的专业知识或工具链。Worker 使用的实现语言不会自动赋予任务能力。这些名称只是建议标签，并不代表已安装的工具。',
    'Typical roles when qualified': '具备相应能力时可承担的职责', 'Use a role as required. Require a technical capability only when the step cannot be completed without it; otherwise prefer it. A requirements step need not require the later implementation language. Preferred skills give available specialists a 30-second claim window; required capabilities cannot be bypassed. Workers cannot run git push. Code handoff uses local result revisions in a shared repository.': '按需指定职责。只有步骤确实离不开某项技术能力时才将其设为必需，否则设为优先能力即可。需求分析步骤不必要求后续实现所用的语言。优先技能会给匹配的专家 Worker 30 秒领取任务；必需能力不可绕过。Worker 不能执行 git push。代码交接通过共享仓库中的本地结果版本完成。',
    'Each output also needs an independent workflow-review Worker. The live declaration snapshot below contains at most 100 Workers and does not verify installed tools.': '每个产出还需要由独立的 workflow-review Worker 审核。下方实时声明快照最多包含 100 个 Worker，且不会验证工具是否实际安装。',
    'Core development covers requirements, architecture, planning, and coding. Each output gets an independent stage review. This template does not run the code reviewer, verifier, integrator, integration verifier, or repair Worker. A failed stage review retries the same role, up to three attempts. Select a Workspace for the code step before saving. A Git revision is optional; leave it empty to work directly in the directory.': '核心开发包含需求、架构、计划和编码。每项产出都会经过独立阶段审核。此模板不会运行代码审查员、验证员、集成人员、集成验证员或修复 Worker。阶段审核未通过时会由相同职责重试，最多三次。保存前请为编码步骤选择工作空间。Git 版本号为可选项；留空则直接在目录中工作。',
    'The next step is created only after the previous step passes independent review. Earlier accepted outputs are supplied as context. Code steps require a Workspace. Leave the revision empty for direct directory work, or supply a full immutable Git commit for an isolated Git worktree.': '只有上一步通过独立审核后才会创建下一步。之前已验收的产出会作为上下文提供。编码步骤必须指定工作空间。版本号留空表示直接在目录中工作；也可以填写完整且不可变的 Git 提交号，以使用隔离的 Git worktree。',
    'Fleet match is a 100-worker snapshot. It does not guarantee that a Worker can claim or complete a step.': 'Worker 匹配信息来自最多 100 个 Worker 的快照，不保证 Worker 一定能领取或完成步骤。',
    'When no child repositories are registered, first-level directories are available as implicit repositories. If the root has no subdirectories, the root itself is the target.': '未注册子仓库时，根目录下的一级目录会作为隐式仓库。如果根目录没有子目录，则根目录本身就是目标。',
    'A local link keeps its source unchanged unless you select this option. Git initialization adds metadata to the linked source.': '除非选择此项，否则本地链接不会修改源目录。初始化 Git 会向链接的源目录添加元数据。',
    'Workers with no heartbeat for more than 90 seconds appear offline and are excluded from available capacity and matching. Historical records and existing leases are retained.': '超过 90 秒无心跳的 Worker 显示为离线，不再计入可用容量和能力匹配。历史记录及已有租约仍保留。',
    'Ordered by lease expiry, then task ID. Plan links are shown for delegated work. Occupancy is shown separately from Worker management status.': '按租约到期时间、任务 ID 排序。委派任务会显示计划链接。资源占用情况与 Worker 管理状态分开展示。',
    'Drain stops new claims and lets existing leases finish. The server checks current state on submission.': '停止接收新任务并等待现有租约完成。提交操作时，服务器会重新检查当前状态。',
    'Refresh paused in background': '页面在后台，已暂停刷新', 'Updating snapshot…': '正在更新快照…', 'Snapshot unavailable': '快照暂不可用',
    'Are you sure?': '确定要继续吗？', 'Working…': '处理中…', 'Update failed; previous snapshot retained': '更新失败，保留了上一份快照', 'Update timed out or paused': '更新超时或已暂停',
    'Runtime unavailable': '运行环境不可用', 'Draining · no new claims; active leases continue': '正在停止接收新任务；现有租约继续执行', 'Executing': '执行中', 'Idle': '空闲', 'Unknown': '未知'
  };
  const attrNames = ['placeholder', 'aria-label', 'title', 'data-confirm'];
  const originals = new WeakMap();
  const attrOriginals = new WeakMap();
  const localeKey = 'aiw-locale';
  let locale = 'en';
  try { locale = (window.aiwPreferences?.get().language || localStorage.getItem(localeKey)) === 'zh' ? 'zh' : 'en'; } catch (_) { /* Use English for this page. */ }

  const translateText = value => {
    if (locale !== 'zh') return value;
    if (translations[value]) return translations[value];
    if (/^Confirm \d+ selected steps at version \d+\? This creates Worker tasks\.$/.test(value)) {
      return value.replace(/^Confirm (\d+) selected steps at version (\d+)\? This creates Worker tasks\.$/, '确定执行版本 $2 的 $1 个所选步骤吗？确认后将创建 Worker 任务。');
    }
    if (/^Skip ahead to step \d+\? Existing unfinished work will be stopped or cancelled\.$/.test(value)) {
      return value.replace(/^Skip ahead to step (\d+)\? Existing unfinished work will be stopped or cancelled\.$/, '确定跳转到第 $1 步吗？现有未完成的工作将停止或取消。');
    }
    let result = value.replace(/^Step (\d+)$/, '第$1步').replace(/^Refresh in (\d+)s$/, '$1 秒后刷新');
    result = result.replace(/^Showing (\d+) of (\d+) registered workers · detail limit 100$/, '显示 $1 / $2 个已注册 Worker · 详情上限 100');
    result = result.replace(/^Showing (\d+) of (\d+) active leases · first 100 in this snapshot$/, '显示 $1 / $2 个活动租约 · 此快照最多 100 条');
    result = result.replace(/^Showing (\d+) of (\d+) READY tasks · first 100 in this snapshot$/, '显示 $1 / $2 个待领取任务 · 此快照最多 100 条');
    return result;
  };
  const visit = node => {
    if (node.parentElement?.closest('[data-i18n-skip]')) return;
    if (node.nodeType === Node.TEXT_NODE) {
      if (!originals.has(node)) originals.set(node, node.nodeValue);
      const original = originals.get(node);
      const leading = original.match(/^\s*/)?.[0] || '';
      const trailing = original.match(/\s*$/)?.[0] || '';
      const core = original.slice(leading.length, original.length - trailing.length || undefined);
      const translated = translateText(core);
      if (node.nodeValue !== leading + translated + trailing) node.nodeValue = leading + translated + trailing;
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return;
    if (node.hasAttribute('data-i18n-skip')) return;
    for (const name of attrNames) {
      if (!node.hasAttribute(name)) continue;
      let saved = attrOriginals.get(node);
      if (!saved) { saved = {}; attrOriginals.set(node, saved); }
      if (!(name in saved)) saved[name] = node.getAttribute(name);
      const original = saved[name];
      const translated = translateText(original);
      if (node.getAttribute(name) !== translated) node.setAttribute(name, translated);
    }
    for (const child of node.childNodes) visit(child);
  };
  const apply = () => {
    document.documentElement.lang = locale === 'zh' ? 'zh-CN' : 'en';
    visit(document.documentElement);
    const selector = document.querySelector('#console-locale');
    if (selector) selector.value = locale;
    const title = document.querySelector('title');
    if (title && !attrOriginals.has(title)) attrOriginals.set(title, { text: title.textContent });
    if (title) {
      const saved = attrOriginals.get(title);
      if (!('text' in saved)) saved.text = title.textContent;
      title.textContent = translateText(saved.text);
    }
  };
  const selector = document.querySelector('#console-locale');
  if (!window.aiwPreferences) selector?.addEventListener('change', () => {
    locale = selector.value === 'zh' ? 'zh' : 'en';
    try { localStorage.setItem(localeKey, locale); } catch (_) { /* Keep this page's language. */ }
    apply();
  });
  window.addEventListener('aiw:preferences-changed', event => { locale = event.detail.language; apply(); });
  apply();
  new MutationObserver(records => records.forEach(record => {
    if (record.type === 'childList') record.addedNodes.forEach(visit);
    else if (record.type === 'characterData') visit(record.target);
    else if (record.type === 'attributes') visit(record.target);
  })).observe(document.documentElement, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: attrNames });
})();
