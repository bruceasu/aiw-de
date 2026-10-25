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
    "Task details · AIW:DE": "任务详情 · AIW:DE",
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
    'Sign in': '登录', 'Authentication is enabled for the browser console.': '控制台已启用登录验证。', 'Username': '用户名', 'Password': '密码', 'Forbidden · AIW:DE': '禁止访问 · AIW:DE', '403 · Access denied': '403 · 拒绝访问', 'Return to Dashboard': '返回控制台首页', 'Not Found · AIW:DE': '未找到 · AIW:DE', 'Resource not found': '未找到该资源', 'Return to Plans': '返回计划列表',
    'Fleet Admission': 'Worker 集群准入', 'Max parallelism': '最大并行数', 'Active leases': '活动租约', 'Available slots': '可用槽位', 'Overcommitted': '超额分配', 'Saturated': '已满载', 'Max Parallelism': '最大并行数', 'Reason': '原因', 'Update Fleet Limit': '更新集群限制', 'Accounting Diagnostics': '资源核算诊断', 'Worker lease delta': 'Worker 租约差值', 'Claimable capacity': '可领取容量', 'Current parallelism': '当前并行数', 'Fleet overcommitted': '集群超额分配', 'Accounting is consistent.': '核算一致。', 'Accounting drift detected. Investigate before changing authority.': '检测到核算偏差。请先调查，再更改调度权限。',
    'Machine workforce': 'Worker 列表', 'No workers registered.': '尚未注册 Worker。', 'Active work': '正在执行的工作', 'No active leases in this snapshot.': '此快照中没有活动租约。', 'Worker': 'Worker', 'Task / attempt': '任务 / 尝试次数', 'Plan or subject': '计划或主题', 'Lease expires': '租约到期时间', 'READY work': '待领取任务', 'No READY tasks in this snapshot.': '此快照中没有待领取任务。', 'Worker match': '匹配的 Worker', 'Current conditions': '当前条件', 'Worker operations': 'Worker 操作', 'Drain': '停止接收新任务', 'Offline': '设为离线', 'Wait for active leases': '等待活动租约结束', 'Refresh now': '立即刷新', 'Last heartbeat': '最近心跳',
    'Register Workspace root': '注册工作空间根目录', 'Workspace ID': '工作空间 ID', 'Workspace root directory': '工作空间根目录', 'Register Workspace': '注册工作空间', 'Registered Workspaces': '已注册的工作空间', 'No Workspace has been registered.': '尚未注册工作空间。', 'View and manage': '查看和管理', 'All Workspaces': '返回工作空间列表', 'Workspace details': '工作空间详情', 'Workspace root and registered repositories': '工作空间根目录与已注册仓库', 'No registered repositories.': '没有已注册的仓库。', 'Add repository': '添加仓库', 'Edit Workspace': '编辑工作空间', 'Save name': '保存名称', 'Root directory': '根目录', 'Save root': '保存根目录', 'Disable Workspace': '停用工作空间', 'Delete registration': '删除注册信息', 'Confirm deletion': '确认删除',
    'System · AIW:DE': '系统 · AIW:DE', 'Control Plane, scheduling authority and accounting diagnostics': 'Control Plane、调度权限与资源核算诊断', 'Fleet Scheduling Audit': '集群调度审计', 'Latest 50 changes': '最近 50 条变更', 'No runtime Fleet scheduling changes recorded.': '尚无运行时集群调度变更记录。', 'Web Console Boundary': 'Web Console 边界', 'Refresh in 8s': '8 秒后刷新',
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
  const japaneseTranslations = {
    "Personalization": "個人設定",
    "Your console, your way": "自分に合ったコンソールに",
    "Changes apply immediately and are saved in this browser.": "変更はすぐに反映され、このブラウザーに保存されます。",
    "Appearance": "外観",
    "One setting for all floating windows. Default: 61.8%.": "すべてのフローティングウィンドウに共通の設定です。既定値：61.8%。",
    "Display language": "表示言語",
    "Date and time": "日付と時刻",
    "Show sidebar time": "サイドバーに時刻を表示",
    "Show sidebar date and time zone": "サイドバーに日付とタイムゾーンを表示",
    "Motion": "アニメーション",
    "Enable animations": "アニメーションを有効にする",
    "System reduced motion settings always take precedence.": "システムの「視差効果を減らす」設定が優先されます。",
    "Your system currently reduces motion. Animations remain disabled.": "システムで視差効果が制限されているため、アニメーションは無効のままです。",
    "Restore defaults": "既定値に戻す",
    "Done": "完了",
    "Preferences saved in this browser.": "設定をこのブラウザーに保存しました。",
    "Applied for this page. Browser storage is unavailable.": "このページに反映しました。ブラウザーのストレージが利用できないため保存はできません。",
    "Opacity": "不透明度",
    "Window background opacity": "ウィンドウ背景の不透明度",
    "Lower opacity makes the window more transparent": "値を下げるとウィンドウが透明になります",
    "Questions and files": "確認事項とファイル",
    "Read the source, answer each pending question, then confirm. Confirmed answers are supplied to future tasks; saving does not start work.": "元資料を確認し、未解決の各質問に回答して確定してください。確定した回答は以後のタスクに渡されます。保存しても作業は開始されません。",
    "Refresh questions": "質問を更新",
    "Related files": "関連ファイル",
    "Answer history": "回答履歴",
    "Source file preview": "元ファイルのプレビュー",
    "Loading source file…": "元ファイルを読み込み中…",
    "Preview source": "元資料をプレビュー",
    "Download source": "元ファイルをダウンロード",
    "View source task": "元タスクを表示",
    "Preview truncated. Download the source file for the full content.": "プレビューを省略しました。全文は元ファイルをダウンロードしてください。",
    "Source preview timed out. Try again.": "元資料のプレビューがタイムアウトしました。再試行してください。",
    "No structured questions found. Check the related files and review reports.": "構造化された質問は見つかりませんでした。関連ファイルとレビュー報告を確認してください。",
    "Answer confirmed": "回答確定済み",
    "Decision needed": "判断待ち",
    "Question context": "質問の背景",
    "Your decision": "判断内容",
    "Confirm answer": "回答を確定",
    "Confirm updated answer": "変更した回答を確定",
    "Enter an answer before confirming.": "確定する前に回答を入力してください。",
    "Confirm this answer as a manager decision? It will be supplied to future execution and review tasks. This does not start work.": "この回答を管理者の判断として確定しますか？以後の実行・レビュータスクに渡されます。この操作で作業は開始されません。",
    "Saving decision…": "判断を保存中…",
    "Answer confirmed. Use Mission Control to authorize the next repair or review.": "回答を確定しました。ミッション管理で次の修正やレビューを許可してください。",
    "Decision request timed out. Refresh and check answer history before submitting again.": "判断の送信がタイムアウトしました。再送信する前に更新し、回答履歴を確認してください。",
    "Question request timed out. Refresh to try again.": "質問の取得がタイムアウトしました。更新して再試行してください。",
    "Your session changed. Reload before answering.": "セッションが変更されました。回答する前にページを再読み込みしてください。",
    "This plan is not open for decisions.": "このプランは現在、判断の変更を受け付けていません。",
    "Resolve the pending plan control before answering.": "先に保留中のプラン制御操作を解決してください。",
    "Wait for pending or running tasks to finish before confirming answers.": "待機中または実行中のタスクが終了してから回答を確定してください。",
    "Revoke or finish the pending repair authorization before changing answers.": "回答を変更する前に、保留中の修正許可を取り消すか、その修正を完了してください。",
    "Decision history": "判断履歴",
    "Plan tools": "プランツール",
    "Move window": "ウィンドウを移動",
    "Maximize or restore window": "ウィンドウを最大化・元に戻す",
    "Hide window": "ウィンドウを非表示",
    "Needs attention": "対応が必要",
    "This plan needs a decision before work can continue.": "作業を続けるには、このプランに新たな判断が必要です。",
    "Open Mission control": "ミッション管理を開く",
    "No decision recorded.": "判断はまだ記録されていません。",
    "No repair authorization recorded.": "修正許可はまだ記録されていません。",
    "Mission control": "ミッション管理",
    "Choose your next move. Every decision stays in the mission history.": "次の操作を選択してください。すべての判断がミッション履歴に記録されます。",
    "Continue the mission": "ミッションを続行",
    "Give the Worker a fixed objective, then send it back for an independent review.": "Worker に明確な修正目標を与え、完了後に独立レビューを依頼します。",
    "1 repair + 1 review": "修正1回＋レビュー1回",
    "Valid for 1 hour": "有効期間：1時間",
    "Repair decision reason": "修正を許可する理由",
    "Why should this repair proceed?": "この修正を進める理由を入力してください",
    "An assigned AI may decide only within this scope. It cannot expand the scope or renew the grant.": "指定した AI はこの範囲内でのみ判断できます。範囲の拡大や許可の更新はできません。",
    "Check the current result": "現在の成果を確認",
    "Send the current work to an independent reviewer. This action does not start another coding round.": "現在の成果を独立レビュアーに渡します。この操作で新たなコーディングは開始されません。",
    "Review decision reason": "再レビューの理由",
    "What changed since the last review?": "前回のレビューから何が変わりましたか？",
    "Request independent review": "独立レビューを依頼",
    "Ready for launch": "開始準備完了",
    "Confirm the selected steps to create Worker tasks and begin the mission.": "選択した手順を確定すると Worker タスクが作成され、ミッションが始まります。",
    "Other mission controls": "その他のミッション操作",
    "Start a new plan from this mission.": "このミッションから新しいプランを作成します。",
    "A decision Worker is assessing the fixed repair scope.": "判断担当の Worker が固定された修正範囲を評価しています。",
    "Review needs changes. Authorize a bounded repair or request another review.": "レビューで修正が必要と判定されました。範囲を限定して修正を許可するか、再レビューを依頼してください。",
    "Workers are active. Follow task progress before choosing another action.": "Worker が稼働中です。別の操作を選ぶ前にタスクの進捗を確認してください。",
    "Review your selected steps, then confirm the draft to start.": "選択した手順を確認し、草案を確定して開始してください。",
    "Repair is unavailable. Refresh the plan to check its current state.": "修正を利用できません。プランを更新して現在の状態を確認してください。",
    "Enter a repair decision reason.": "修正を許可する理由を入力してください。",
    "Enter a fixed repair scope.": "修正範囲を明確に入力してください。",
    "Waiting for confirmation…": "確認を待っています…",
    "Submitting authorization…": "許可を送信中…",
    "Repair authorization cancelled.": "修正許可を取り消しました。",
    "Authorization was not used because the plan changed or the grant expired. Check authorization history.": "プランの変更または有効期限切れにより許可は使用されませんでした。許可履歴を確認してください。",
    "Authorization request timed out. Refresh and check authorization history before submitting again.": "許可の送信がタイムアウトしました。再送信する前に更新し、許可履歴を確認してください。",
    "Model (optional)": "モデル（任意）",
    "Reasoning effort (optional)": "推論の深さ（任意）",
    "Leave empty to let the Worker decide. Invalid or unavailable settings fall back to the Worker configuration.": "空欄なら Worker が選択します。無効または利用できない設定は Worker の設定に戻ります。",
    "Worker choice": "Worker に任せる",
    "Long-running task. Check Worker progress; elapsed time alone does not stop execution.": "長時間実行中のタスクです。Worker の進捗を確認してください。経過時間だけで実行が停止することはありません。",
    "Task deliverables": "タスクの成果物",
    "Reports and attachments are saved in the persistent Workspace. Project documents require a passed source review, then a publication plan and confirmation.": "報告と添付ファイルは永続ワークスペースに保存されます。プロジェクト文書にするには元成果物のレビュー合格後、公開プランの作成と確認が必要です。",
    "Loading deliverables…": "成果物を読み込み中…",
    "No deliverables registered yet.": "成果物はまだ登録されていません。",
    "Deliverable request failed. Check your permissions and reload the page.": "成果物を取得できませんでした。権限を確認し、ページを再読み込みしてください。",
    "Deliverable request failed.": "成果物を取得できませんでした。",
    "Independent review passed": "独立レビュー合格",
    "Awaiting a passed independent review": "独立レビューの合格待ち",
    "Unfinished candidate": "未完成の候補",
    "Review report": "レビュー報告",
    "Output registered": "成果物登録済み",
    "Execution claimed": "実行担当確定",
    "Execution running": "実行中",
    "Execution completed": "実行完了",
    "Execution failed": "実行失敗",
    "Execution expired": "実行期限切れ",
    "bytes": "バイト",
    "Passed source review task ID": "合格した元成果物レビューのタスク ID",
    "Project document path (for example, docs/requirements.md)": "プロジェクト文書のパス（例：docs/requirements.md）",
    "Publication reason": "公開理由",
    "Create document publication draft": "文書公開の草案を作成",
    "Save as a project document": "プロジェクト文書として保存",
    "View report": "報告を表示",
    "This task failed. Fix the cause, then retry this task to continue the plan.": "このタスクは失敗しました。原因を修正し、このタスクを再試行してプランを続行してください。",
    "After fixing the cause, enter a reason and retry the failed task below. Earlier accepted outputs and dependencies remain unchanged.": "原因を修正したら理由を入力し、下の失敗タスクを再試行してください。受け入れ済みの前段成果物と依存関係は変わりません。",
    "Retrying keeps the same plan and task IDs and adds a new execution attempt. Check local changes left by the failed attempt before confirming. No earlier step is restarted.": "再試行ではプラン ID とタスク ID を維持し、新しい実行記録を追加します。確認前に失敗した実行が残したローカル変更を調べてください。前段の手順は再実行されません。",
    "Retry reason": "再試行の理由",
    "Describe what was fixed before retrying": "再試行前に修正した内容を入力してください",
    "Retry failed task": "失敗タスクを再試行",
    "Enter a retry reason.": "再試行の理由を入力してください。",
    "Submitting retry…": "再試行を送信中…",
    "Retry only this failed task? Earlier accepted outputs remain. Check local changes first; this starts another Worker execution.": "この失敗タスクだけを再試行しますか？受け入れ済みの前段成果物は維持されます。先にローカル変更を確認してください。この操作で Worker が再度実行します。",
    "Retry failed. Reload the page and check the current task state.": "再試行の送信に失敗しました。ページを再読み込みし、現在のタスク状態を確認してください。",
    "Your session changed. Reload before retrying.": "セッションが変更されました。再試行する前にページを再読み込みしてください。",
    "Plan or task changed. Reload before retrying.": "プランまたはタスクが変更されました。再試行する前にページを再読み込みしてください。",
    "Retry reason must contain 1 to 2000 characters.": "再試行の理由は1～2000文字で入力してください。",
    "Confirm the retry warning before continuing.": "続行する前に再試行の注意事項を確認してください。",
    "This plan has ended or is archived. Its tasks cannot be retried.": "このプランは終了済み、またはアーカイブ済みです。タスクは再試行できません。",
    "Resolve the pending stop request before retrying.": "再試行する前に保留中の停止要求を解決してください。",
    "Only a failed task can be retried.": "再試行できるのは失敗したタスクだけです。",
    "Only the current failed task can be retried. This plan has already advanced.": "再試行できるのは現在の失敗タスクだけです。プランはすでに先へ進んでいます。",
    "Another task is pending or running. Wait before retrying.": "別のタスクが待機中または実行中です。終了までお待ちください。",
    "This plan has already passed its final review.": "このプランは最終レビューに合格済みです。",
    "To change the goal or selected steps, copy the plan into a new draft.": "目標や選択した手順を変更するには、プランを新しい草案にコピーしてください。",
    "Copy to change the plan": "プランをコピーして変更",
    "Manual retries (latest 50)": "手動再試行履歴（最新50件）",
    "Actor": "実行者",
    "Sandbox namespace creation failed. Check Linux user-namespace support and the host or container sandbox configuration before retrying.": "サンドボックスの名前空間を作成できませんでした。再試行する前に Linux のユーザー名前空間対応とホスト・コンテナーのサンドボックス設定を確認してください。",
    "A required command was not found. Check that the tool is installed and available in the Worker process PATH.": "必要なコマンドが見つかりません。ツールがインストール済みで Worker プロセスの PATH から利用できるか確認してください。",
    "The command was denied access. Check filesystem permissions and whether the role is using a read-only workspace.": "コマンドのアクセスが拒否されました。ファイル権限と、その役割が読み取り専用ワークスペースを使用しているかを確認してください。",
    "The command referenced a missing path. Check the registered Workspace and the paths used by the tool.": "コマンドが参照するパスが存在しません。登録済みワークスペースとツールの参照先を確認してください。",
    "A command could not reach its network target. Check the target address from the Worker environment.": "コマンドがネットワーク接続先に到達できませんでした。Worker の環境から接続先アドレスを確認してください。",
    "New Codex failures log the tool type, call ID, exit code, diagnostic code and evidence directory. Only known error patterns are classified; unrecognized failures remain UNRESOLVED_TOOL_FAILURE. Raw command output is not retained.": "新しい Codex の失敗記録にはツール種別、呼び出し ID、終了コード、診断コード、証拠ディレクトリが含まれます。既知のエラーパターンだけを分類し、未分類は UNRESOLVED_TOOL_FAILURE のままです。コマンドの生出力は保存しません。",
    "Task details": "タスク詳細",
    "Task details · AIW:DE": "タスク詳細 · AIW:DE",
    "Execution history, failures and recovery": "実行履歴・失敗と復旧",
    "Back to plan": "プランに戻る",
    "Task overview": "タスク概要",
    "Task ID": "タスク ID",
    "Skill": "スキル",
    "Required capabilities": "必須能力",
    "Workspace": "ワークスペース",
    "Updated": "更新日時",
    "Started": "開始日時",
    "Finished": "終了日時",
    "Not recorded": "記録なし",
    "Failure category": "失敗の分類",
    "Error code / termination reason": "エラーコード／終了理由",
    "Instruction revision": "指示のリビジョン",
    "Result revision": "成果のリビジョン",
    "Attempt": "実行試行",
    "Attempt ID": "実行試行 ID",
    "Verdict": "判定",
    "Recorded report": "記録された報告",
    "Task instruction": "タスクの指示",
    "Execution history": "実行履歴",
    "Execution": "実行",
    "Independent review": "独立レビュー",
    "View task details": "タスク詳細を表示",
    "Updates every 5 seconds": "5秒ごとに更新",
    "View all records": "すべての記録を表示",
    "Newest records first": "新しい記録から表示",
    "Review again after correction": "修正後に再レビュー",
    "Repair authorization history": "修正許可履歴",
    "Authorize one repair round": "修正1回を許可",
    "Fixed repair scope": "固定した修正範囲",
    "Who decides whether to proceed?": "続行を判断する担当者",
    "Human approval now": "人が今すぐ承認",
    "Authorize continued repair": "修正の続行を許可",
    "State allowed changes, accepted requirement decisions, and permitted checks.": "許可する変更範囲、確定済みの要件判断、実施可能なチェックを入力してください。",
    "Use the decision reason above. One repair execution and one independent review; authorization expires in one hour. An AI may use only this fixed scope and cannot renew the grant.": "上の判断理由を使用します。修正1回と独立レビュー1回のみを許可し、1時間で失効します。AI はこの固定範囲内だけで実行でき、許可を更新できません。",
    "Enter a decision reason and a fixed repair scope.": "判断理由と固定する修正範囲を入力してください。",
    "Authorize one repair round within the entered scope? An assigned AI cannot change the scope or add rounds.": "入力した範囲内で修正1回を許可しますか？指定した AI は範囲の変更や回数の追加はできません。",
    "One-round repair authorization recorded.": "修正1回分の許可を記録しました。",
    "Decision report": "判断報告",
    "Revoke authorization": "許可を取り消す",
    "Revoke this unused repair authorization? No new repair will be started.": "この未使用の修正許可を取り消しますか？新たな修正は開始されません。",
    "WAITING_FOR_RECOVERY_DECISION": "復旧許可の判断待ち",
    "CONSUMED": "使用済み",
    "DENIED": "拒否済み",
    "STALE": "失効",
    "Review the current implementation again with the accepted upstream outputs? Prior attempts remain. Coding is not rerun.": "受け入れ済みの前段成果物を使って現在の実装を再レビューしますか？過去の試行は残り、コーディングは再実行されません。",
    "New independent review queued. Prior attempts remain unchanged.": "新しい独立レビューをキューに追加しました。過去の試行は変わりません。",
    "Close": "閉じる",
    "Status refresh failed. Previous data remains visible.": "状態の更新に失敗しました。前回のデータを表示しています。",
    "Status request timed out or was interrupted. The next update will retry.": "状態の取得がタイムアウトしたか、中断されました。次の更新で再試行します。",
    "Status updates paused while editing, confirming or selecting text.": "編集中、確認中、またはテキスト選択中は状態の更新を一時停止します。",
    "This plan needs attention. Open task details to inspect execution history and recovery steps.": "このプランには対応が必要です。タスク詳細で実行履歴と復旧手順を確認してください。",
    "What to do after failure": "失敗後の対応",
    "This task failed. Later steps wait until the failure is resolved and work is delegated again.": "このタスクは失敗しました。失敗を解決して作業を再委派するまで、後続の手順は待機します。",
    "Check the latest attempt below for its Worker, failure category and error code.": "下の最新の実行試行で、担当 Worker、失敗分類、エラーコードを確認してください。",
    "Inspect that Worker's logs and attempt evidence. Fix the failed command, tool, permissions or runtime configuration before trying again.": "その Worker のログと実行の証拠を調べてください。失敗したコマンド、ツール、権限、実行環境の設定を修正してから再試行してください。",
    "Check Fleet for the execution role and an independent workflow-review Worker. Start the roles required by later steps as well.": "Worker 一覧で実行担当の役割と独立した workflow-review Worker を確認してください。後続の手順で必要な役割も起動してください。",
    "Copy this plan into a new draft, review its steps and Workspace, save it, then confirm the new draft. Copying alone does not start work.": "このプランを新しい草案にコピーし、手順とワークスペースを確認して保存し、その草案を確定してください。コピーだけでは作業は始まりません。",
    "The copy starts from the first selected step and can repeat completed work. Review local files and side effects before confirming. The original failure remains recorded.": "コピーしたプランは最初の選択手順から開始し、完了済みの作業を繰り返す場合があります。確認前にローカルファイルと副作用を調べてください。元の失敗記録は残ります。",
    "Copy plan to recover": "プランをコピーして復旧",
    "No Worker has claimed this task yet. Check Fleet matching and task prerequisites.": "まだ Worker がこのタスクを取得していません。Worker の適合状況とタスクの前提条件を確認してください。",
    "This historical attempt has no stored error code. Check its Worker logs; the page cannot reconstruct missing diagnostics.": "この過去の試行にはエラーコードが保存されていません。Worker のログを確認してください。ページから失われた診断情報は復元できません。",
    "Tool or output validation failed. Check the failed command and its output in Worker-side diagnostics. Restarting a Worker alone does not retry this task.": "ツールまたは出力の検証に失敗しました。Worker 側の診断で失敗したコマンドと出力を確認してください。Worker の再起動だけではこのタスクは再試行されません。",
    "No completion report was recorded for this attempt.": "この試行には完了報告が記録されていません。",
    "Where to find diagnostics": "診断情報の確認場所",
    "Run these commands in the TypeScript Worker directory on the machine running the Worker. Replace <role> with requirements, coder or stage-reviewer as appropriate.": "Worker を実行している端末の TypeScript Worker ディレクトリで実行してください。<role> は用途に応じ requirements、coder、stage-reviewer に置き換えてください。",
    "Attempt evidence is stored outside the working directory, under AIW_WORKER_EVIDENCE_ROOT or the default .aidev-attempt-evidence directory. Match owner.json by Attempt ID. Historical records may not include command output.": "実行の証拠は作業ディレクトリ外の AIW_WORKER_EVIDENCE_ROOT、または既定の .aidev-attempt-evidence に保存されます。owner.json の実行試行 ID と照合してください。過去の記録にはコマンド出力がない場合があります。",
    "Confirm action": "操作の確認",
    "Confirm": "確認",
    "Stop this plan early? Completed results remain; unfinished work will be stopped or cancelled.": "このプランを早期停止しますか？完了済みの成果は残り、未完了の作業は停止またはキャンセルされます。",
    "Complete by human decision? Unfinished Worker tasks will be stopped or cancelled.": "人の判断で完了にしますか？未完了の Worker タスクは停止またはキャンセルされます。",
    "Cancel this plan and delete its verified local isolated Git branches? Stop active Workers first.": "このプランをキャンセルし、検証済みのローカル分離 Git ブランチを削除しますか？先に稼働中の Worker を停止してください。",
    "Schedule database deletion in 30 days?": "30日後のデータベース削除を予約しますか？",
    "Mark this worker offline?": "この Worker をオフラインにしますか？",
    "Language": "言語",
    "English": "English",
    "简体中文": "简体中文",
    "New Plan": "新しいプラン",
    "Plans": "プラン",
    "Workspaces": "ワークスペース",
    "Fleet": "Worker 一覧",
    "System": "システム",
    "Authority remains in the Control Plane.": "実行権限はコントロールプレーンが保持します。",
    "Motion: on": "アニメーション：オン",
    "Motion: off": "アニメーション：オフ",
    "Sign out": "ログアウト",
    "Plan": "プラン",
    "Saved selection, actual tasks and recorded audit": "保存した選択内容・実際のタスク・監査記録",
    "All plans": "すべてのプラン",
    "State": "状態",
    "Source": "出所",
    "Version": "バージョン",
    "Created by": "作成者",
    "Updated by / reason": "更新者／理由",
    "Time": "日時",
    "Source records who proposed the graph, not approval. A draft has no execution tasks.": "出所は構成の提案者を示すもので、承認ではありません。草案には実行タスクはありません。",
    "Selected plan": "選択したプラン",
    "Step / skill": "手順／スキル",
    "Instruction": "指示",
    "Declared dependencies": "宣言された依存関係",
    "Context": "コンテキスト",
    "Execution and independent review": "実行と独立レビュー",
    "A step advances only after an independent PASS review. After three correction attempts, NEEDS_ATTENTION means the plan requires a new manager decision. Task states and reports appear only when recorded.": "独立レビューで PASS になった手順だけが次へ進みます。修正試行3回後の NEEDS_ATTENTION は、新しい管理者判断が必要な状態です。タスク状態と報告は記録されたものだけを表示します。",
    "Manager decisions": "管理者の判断",
    "Manager actions": "管理者の操作",
    "Draft": "草案",
    "Copy into editor": "エディターにコピー",
    "Confirm selected draft version": "選択した草案バージョンを確定",
    "Withdrawal reason": "取り下げる理由",
    "Withdraw inactive work": "未稼働の作業を取り下げる",
    "Step control": "手順の制御",
    "Jump to step": "指定した手順へ移動",
    "Target step": "移動先の手順",
    "Decision reason": "判断理由",
    "Reason required": "理由が必要です",
    "Skip to step": "手順をスキップ",
    "Plan control": "プランの制御",
    "Stop plan early": "プランを早期停止",
    "Complete by human decision": "人の判断で完了",
    "Cancel and discard local Git branches": "キャンセルしてローカル Git ブランチを破棄",
    "Resolve stale stop request": "古い停止要求を解決",
    "Active Worker tasks receive a stop request first. Human completion waits for Worker acknowledgement. Cancellation needs all Workers to finish or stop; it deletes only verified local isolated branches. Published results or direct shared work require manual reversal.": "稼働中の Worker タスクには、まず停止要求を送ります。人の判断による完了は Worker の応答を待ちます。キャンセルにはすべての Worker の終了・停止が必要で、検証済みのローカル分離ブランチだけを削除します。公開済み成果や直接共有した作業の取り消しは手動で行ってください。",
    "Archive and retention": "アーカイブと保存期間",
    "Archive plan": "プランをアーカイブ",
    "Database deletion scheduled after": "データベースの削除予定日時：",
    ". Workspaces and Git results remain.": "。ワークスペースと Git の成果は残ります。",
    "Delete in 30 days": "30日後に削除",
    "Undo deletion": "削除予約を取り消す",
    "Current plans": "現在のプラン",
    "Archived plans": "アーカイブ済みプラン",
    "Create plan": "プランを作成",
    "A draft stores selected steps. Confirm it to create Worker tasks. Completed work remains available after a plan is stopped or archived.": "草案に選択した手順を保存します。確定すると Worker タスクが作成されます。停止やアーカイブ後も完了した成果は参照できます。",
    "Goal": "目標",
    "Actions": "操作",
    "Created": "作成日時",
    "Archive": "アーカイブ",
    "Delete after": "削除予定",
    "No archived plans.": "アーカイブ済みプランはありません。",
    "No current plans.": "現在のプランはありません。",
    "Build, preview, save, then confirm your selected steps": "手順を作成・プレビュー・保存してから確定します",
    "Plan Editor": "プランエディター",
    "Plan help": "プランのヘルプ",
    "Capability guide": "能力ガイド",
    "View Workers →": "Worker を表示 →",
    "Loading Worker capabilities…": "Worker の能力を読み込み中…",
    "Start with a template": "テンプレートから開始",
    "Choose template": "テンプレートを選択",
    "Blank plan": "空のプラン",
    "Use selection": "選択内容を使用",
    "Goal and recommendation": "目標と推奨手順",
    "Ask AI to recommend steps": "AI に手順の提案を依頼",
    "Check existing analysis": "既存の分析を確認",
    "Withdraw queued analysis": "待機中の分析を取り下げる",
    "Apply suggestion to editor": "提案をエディターに適用",
    "Selected steps": "選択した手順",
    "Add step": "手順を追加",
    "Review before saving": "保存前に確認",
    "Step": "手順",
    "Worker role": "Worker の役割",
    "Depends on": "依存先",
    "Workspace / revision": "ワークスペース／リビジョン",
    "Fleet snapshot": "Worker 一覧のスナップショット",
    "Save new draft": "新しい草案を保存",
    "Cancel": "キャンセル",
    "Workspace reference": "ワークスペース参照",
    "Filter Workspaces by name or reference": "名前または参照でワークスペースを絞り込み",
    "No Workspace selected": "ワークスペースが未選択です",
    "No matching Workspaces": "一致するワークスペースがありません",
    "Manage Workspaces": "ワークスペースを管理",
    "Step key": "手順キー",
    "Required technical skills (comma separated)": "必須の技術スキル（カンマ区切り）",
    "Preferred skills (comma separated)": "優先スキル（カンマ区切り）",
    "Base revision (optional full Git commit)": "基準リビジョン（完全な Git コミット、任意）",
    "Declared earlier dependencies (comma separated)": "先行する依存手順（カンマ区切り）",
    "Instruction and acceptance criteria": "指示と受け入れ条件",
    "Move up": "上へ移動",
    "Move down": "下へ移動",
    "Delete step": "手順を削除",
    "Enter a goal before saving.": "保存する前に目標を入力してください。",
    "Choose 1–32 steps.": "手順を1～32個選択してください。",
    "No additional technical capabilities in this snapshot.": "このスナップショットに追加の技術能力はありません。",
    "Step roles": "手順の役割",
    "Technical capabilities declared by active Workers": "稼働中の Worker が宣言した技術能力",
    "Sign in": "ログイン",
    "Authentication is enabled for the browser console.": "ブラウザーコンソールの認証は有効です。",
    "Username": "ユーザー名",
    "Password": "パスワード",
    "Forbidden · AIW:DE": "アクセス禁止 · AIW:DE",
    "403 · Access denied": "403 · アクセスが拒否されました",
    "Return to Dashboard": "ダッシュボードに戻る",
    "Not Found · AIW:DE": "見つかりません · AIW:DE",
    "Resource not found": "リソースが見つかりません",
    "Return to Plans": "プランに戻る",
    "Fleet Admission": "Worker の受け入れ制御",
    "Max parallelism": "最大並列数",
    "Active leases": "有効なリース",
    "Available slots": "利用可能な実行枠",
    "Overcommitted": "容量超過",
    "Saturated": "満杯",
    "Max Parallelism": "最大並列数",
    "Reason": "理由",
    "Update Fleet Limit": "並列実行の上限を更新",
    "Accounting Diagnostics": "計数の診断",
    "Worker lease delta": "Worker リース数の差分",
    "Claimable capacity": "取得可能な実行枠",
    "Current parallelism": "現在の並列数",
    "Fleet overcommitted": "Worker 全体の容量超過",
    "Accounting is consistent.": "計数に整合性があります。",
    "Accounting drift detected. Investigate before changing authority.": "計数の不一致を検出しました。権限を変更する前に調査してください。",
    "Machine workforce": "Worker の稼働状況",
    "No workers registered.": "登録済みの Worker はありません。",
    "Active work": "稼働中の作業",
    "No active leases in this snapshot.": "このスナップショットに有効なリースはありません。",
    "Worker": "Worker",
    "Task / attempt": "タスク／実行試行",
    "Plan or subject": "プランまたは対象",
    "Lease expires": "リースの期限",
    "READY work": "READY の作業",
    "No READY tasks in this snapshot.": "このスナップショットに READY のタスクはありません。",
    "Worker match": "Worker の適合状況",
    "Current conditions": "現在の条件",
    "Worker operations": "Worker の操作",
    "Drain": "新規取得を停止",
    "Offline": "オフライン",
    "Wait for active leases": "有効なリースの終了を待つ",
    "Refresh now": "今すぐ更新",
    "Last heartbeat": "最終ハートビート",
    "Register Workspace root": "ワークスペースのルートを登録",
    "Workspace ID": "ワークスペース ID",
    "Workspace root directory": "ワークスペースのルートディレクトリ",
    "Register Workspace": "ワークスペースを登録",
    "Registered Workspaces": "登録済みのワークスペース",
    "No Workspace has been registered.": "ワークスペースは登録されていません。",
    "View and manage": "表示・管理",
    "All Workspaces": "すべてのワークスペース",
    "Workspace details": "ワークスペース詳細",
    "Workspace root and registered repositories": "ワークスペースのルートと登録済みリポジトリ",
    "No registered repositories.": "登録済みリポジトリはありません。",
    "Add repository": "リポジトリを追加",
    "Edit Workspace": "ワークスペースを編集",
    "Save name": "名前を保存",
    "Root directory": "ルートディレクトリ",
    "Save root": "ルートを保存",
    "Disable Workspace": "ワークスペースを無効化",
    "Delete registration": "登録を解除",
    "Confirm deletion": "削除を確認",
    "System · AIW:DE": "システム · AIW:DE",
    "Control Plane, scheduling authority and accounting diagnostics": "コントロールプレーン・スケジューリング権限・計数診断",
    "Fleet Scheduling Audit": "Worker スケジューリングの監査",
    "Latest 50 changes": "最新50件の変更",
    "No runtime Fleet scheduling changes recorded.": "実行時の Worker スケジューリング変更は記録されていません。",
    "Web Console Boundary": "Web Console の責任範囲",
    "Refresh in 8s": "8秒後に更新",
    "A role describes the work performed; a technical capability describes the expertise or toolchain actually available. The Worker’s implementation language does not grant a task capability. These names are suggested labels, not a list of installed tools.": "役割は担当する作業、技術能力は実際に利用できる専門知識やツール群を表します。Worker の実装言語だけでタスクの能力が付与されることはありません。これらは推奨ラベルであり、インストール済みツールの一覧ではありません。",
    "Typical roles when qualified": "能力がある場合に担当できる主な役割",
    "Use a role as required. Require a technical capability only when the step cannot be completed without it; otherwise prefer it. A requirements step need not require the later implementation language. Preferred skills give available specialists a 30-second claim window; required capabilities cannot be bypassed. Workers cannot run git push. Code handoff uses local result revisions in a shared repository.": "役割は必要に応じて指定してください。その技術能力がなければ完了できない手順だけで必須とし、それ以外は優先とします。要件分析の手順で後続実装の言語を必須にする必要はありません。優先スキルに合う専門 Worker には30秒間の優先取得期間がありますが、必須能力は省略できません。Worker は git push を実行できません。コードの引き継ぎには共有リポジトリのローカル成果リビジョンを使います。",
    "Each output also needs an independent workflow-review Worker. The live declaration snapshot below contains at most 100 Workers and does not verify installed tools.": "各成果物には独立した workflow-review Worker も必要です。下の宣言スナップショットは最大100個の Worker を含み、ツールのインストール状態は検証しません。",
    "Core development covers requirements, architecture, planning, and coding. Each output gets an independent stage review. This template does not run the code reviewer, verifier, integrator, integration verifier, or repair Worker. A failed stage review retries the same role, up to three attempts. Select a Workspace for the code step before saving. A Git revision is optional; leave it empty to work directly in the directory.": "基本開発は要件・アーキテクチャ・計画・コーディングを対象とし、各成果物に独立した段階レビューを行います。このテンプレートではコードレビュアー、検証担当、統合担当、統合検証担当、修正 Worker は実行しません。段階レビューに失敗すると同じ役割を最大3回まで試行します。保存前にコーディング手順のワークスペースを選択してください。Git リビジョンは任意で、空欄ならそのディレクトリで直接作業します。",
    "The next step is created only after the previous step passes independent review. Earlier accepted outputs are supplied as context. Code steps require a Workspace. Leave the revision empty for direct directory work, or supply a full immutable Git commit for an isolated Git worktree.": "前の手順が独立レビューに合格してから次の手順を作成します。受け入れ済みの前段成果物はコンテキストに含まれます。コーディング手順にはワークスペースが必要です。リビジョンが空ならディレクトリで直接作業し、完全で不変の Git コミットを指定すれば分離した Git worktree を使います。",
    "Fleet match is a 100-worker snapshot. It does not guarantee that a Worker can claim or complete a step.": "Worker の適合状況は100個までのスナップショットです。Worker が手順を取得・完了できることを保証しません。",
    "When no child repositories are registered, first-level directories are available as implicit repositories. If the root has no subdirectories, the root itself is the target.": "子リポジトリが未登録の場合、ルート直下のディレクトリを暗黙のリポジトリとして使います。子ディレクトリもなければルート自体を対象とします。",
    "A local link keeps its source unchanged unless you select this option. Git initialization adds metadata to the linked source.": "この選択肢を選ばない限り、ローカルリンクは元ディレクトリを変更しません。Git の初期化ではリンク先にメタデータを追加します。",
    "Workers with no heartbeat for more than 90 seconds appear offline and are excluded from available capacity and matching. Historical records and existing leases are retained.": "90秒を超えてハートビートがない Worker はオフラインと表示し、利用可能な容量と適合判定から除外します。過去の記録と既存のリースは残ります。",
    "Ordered by lease expiry, then task ID. Plan links are shown for delegated work. Occupancy is shown separately from Worker management status.": "リース期限、タスク ID の順に表示します。委派された作業にはプランへのリンクを表示します。使用中の枠と Worker の管理状態は別に表示します。",
    "Drain stops new claims and lets existing leases finish. The server checks current state on submission.": "新規取得を停止し、既存のリースの終了を待ちます。送信時にサーバーが現在の状態を確認します。",
    "Refresh paused in background": "バックグラウンドでは更新を一時停止",
    "Updating snapshot…": "スナップショットを更新中…",
    "Snapshot unavailable": "スナップショットを利用できません",
    "Are you sure?": "続行してもよろしいですか？",
    "Working…": "処理中…",
    "Update failed; previous snapshot retained": "更新に失敗したため、前回のスナップショットを保持しました",
    "Update timed out or paused": "更新がタイムアウトしたか、一時停止されました",
    "Runtime unavailable": "実行環境を利用できません",
    "Draining · no new claims; active leases continue": "新規取得を停止中・既存のリースは継続",
    "Executing": "実行中",
    "Idle": "待機中",
    "Unknown": "不明",
    "Goals": "目標",
    "秘书平台授权（高级）": "秘書のプラットフォーム権限（詳細）",
    "专业 Worker 任务配置（高级）": "専門 Worker のタスク設定（詳細）",
    "工作问题与人工控制": "作業上の問題と手動制御",
    "未登录": "未ログイン",
    "日本語": "日本語"
  };
  // Chinese source strings used by the secretary and the shared companion UI.
  const workspaceCatalog = {
  "我的秘书": [
    "My secretary",
    "マイ秘書"
  ],
  "我的秘书 · AIW:DE": [
    "My secretary · AIW:DE",
    "マイ秘書 · AIW:DE"
  ],
  "个人工作空间": [
    "Personal workspace",
    "個人ワークスペース"
  ],
  "工作总览": [
    "Overview",
    "作業の概要"
  ],
  "工作与安排": [
    "Work and schedules",
    "作業とスケジュール"
  ],
  "私人对话": [
    "Private chat",
    "プライベートチャット"
  ],
  "代理策略": [
    "Delegation policies",
    "代理ポリシー"
  ],
  "私人代理策略": [
    "Private delegation policies",
    "個人代理ポリシー"
  ],
  "平台协作": [
    "Platform collaboration",
    "プラットフォーム連携"
  ],
  "私人资料": [
    "Private records",
    "個人資料"
  ],
  "状态与备份": [
    "Status and backup",
    "状態とバックアップ"
  ],
  "待你确认": [
    "Needs your confirmation",
    "確認待ち"
  ],
  "设置": [
    "Settings",
    "設定"
  ],
  "首次设置": [
    "Initial setup",
    "初期設定"
  ],
  "显示高级功能": [
    "Show advanced features",
    "詳細機能を表示"
  ],
  "隐藏高级功能": [
    "Hide advanced features",
    "詳細機能を非表示"
  ],
  "已连接个人服务": [
    "Connected to personal service",
    "個人サービスに接続済み"
  ],
  "个人秘书": [
    "Personal secretary",
    "個人秘書"
  ],
  "日常范围内直接处理。": [
    "Handle everyday work directly.",
    "日常の作業を直接処理します。"
  ],
  "越界或不可恢复删除时再确认。": [
    "Ask before out-of-scope actions or permanent deletion.",
    "範囲外の操作や復元できない削除の前に確認します。"
  ],
  "读取状态中": [
    "Reading status",
    "状態を読み込み中"
  ],
  "刷新": [
    "Refresh",
    "更新"
  ],
  "继续工作": [
    "Resume work",
    "作業を再開"
  ],
  "暂停秘书": [
    "Pause secretary",
    "秘書を一時停止"
  ],
  "你的私人工作台": [
    "Your personal workbench",
    "あなたの作業スペース"
  ],
  "从一件具体的工作开始": [
    "Start with one task",
    "ひとつの作業から始めましょう"
  ],
  "记录想法、讨论细节，再确认安排与需要提交的平台内容。": [
    "Capture ideas, discuss details, then confirm schedules and platform submissions.",
    "アイデアを記録し、詳細を相談してから予定や送信内容を確認します。"
  ],
  "＋ 新建私人工作": [
    "＋ New private task",
    "＋ 個人作業を作成"
  ],
  "新建私人工作": [
    "New private task",
    "個人作業を作成"
  ],
  "私人工作": [
    "Private tasks",
    "個人作業"
  ],
  "本地工作记录": [
    "Local work records",
    "ローカルの作業記録"
  ],
  "已保存的对话轮次": [
    "Saved exchanges",
    "保存した会話"
  ],
  "已保存的策略记录": [
    "Saved policies",
    "保存したポリシー"
  ],
  "待处理请求": [
    "Pending requests",
    "処理待ちのリクエスト"
  ],
  "尚未终结的请求": [
    "Unresolved requests",
    "未完了のリクエスト"
  ],
  "工作一览": [
    "Task journal",
    "作業ログ"
  ],
  "选择工作后继续安排或对话。": [
    "Select a task to schedule it or continue the conversation.",
    "作業を選んで予定を設定したり会話を続けたりできます。"
  ],
  "查看全部 →": [
    "View all →",
    "すべて表示 →"
  ],
  "请求回执": [
    "Request receipts",
    "リクエストの受領記録"
  ],
  "只展示已记录的实际状态。": [
    "Only recorded status is shown.",
    "記録済みの状態のみを表示します。"
  ],
  "查看详情 →": [
    "View details →",
    "詳細を見る →"
  ],
  "本地控制": [
    "Local control",
    "ローカル制御"
  ],
  "服务重启后动作申请保持暂停。平台身份、能力与本地执行需分别明确启用。": [
    "Action requests remain paused after service restart. Enable platform identity, capabilities and local execution separately.",
    "サービス再起動後も操作要求は停止状態を維持します。識別情報、権限、ローカル実行は個別に有効化してください。"
  ],
  "尚未读取状态": [
    "Status not loaded",
    "状態未取得"
  ],
  "工作标题": [
    "Task title",
    "作業タイトル"
  ],
  "这次想完成什么？": [
    "What would you like to accomplish?",
    "何を完了したいですか？"
  ],
  "工作说明": [
    "Task description",
    "作業の説明"
  ],
  "结果文件位置（可选）": [
    "Output file path (optional)",
    "出力ファイルの場所（任意）"
  ],
  "关联专业平台 Plan（高级，可选）": [
    "Linked platform Plan (advanced, optional)",
    "連携するプラットフォーム Plan（詳細・任意）"
  ],
  "保存私人工作": [
    "Save private task",
    "個人作業を保存"
  ],
  "01 · 记录工作": [
    "01 · Record a task",
    "01 · 作業を記録"
  ],
  "02 · 安排执行": [
    "02 · Schedule execution",
    "02 · 実行を設定"
  ],
  "安排这项工作": [
    "Schedule this task",
    "この作業の実行を設定"
  ],
  "默认马上执行，无需填写时间；选择定时执行时，按当前浏览器时区处理。": [
    "Run now by default. Scheduled times use your browser's time zone.",
    "既定ではすぐに実行します。時刻指定にはブラウザーのタイムゾーンを使用します。"
  ],
  "选择工作": [
    "Select a task",
    "作業を選択"
  ],
  "当前版本（自动带入）": [
    "Current version (automatic)",
    "現在のバージョン（自動入力）"
  ],
  "执行方式": [
    "Execution mode",
    "実行方法"
  ],
  "马上执行": [
    "Run now",
    "今すぐ実行"
  ],
  "定时执行": [
    "Run at a scheduled time",
    "時刻を指定して実行"
  ],
  "安排时间": [
    "Scheduled time",
    "実行予定時刻"
  ],
  "我的工作": [
    "My tasks",
    "自分の作業"
  ],
  "内容、私人版本及安排状态以最近一次读取为准。": [
    "Content, private versions and schedules reflect the latest snapshot.",
    "内容、個人バージョン、予定は最終取得時の状態です。"
  ],
  "直接说出需要帮助的事，秘书会在日常范围内处理。": [
    "Tell the secretary what you need. Everyday requests are handled directly.",
    "手伝ってほしいことを伝えてください。日常の範囲で直接処理します。"
  ],
  "请先创建私人工作": [
    "Create a private task first",
    "先に個人作業を作成してください"
  ],
  "所选工作的私人对话": [
    "Private conversation for the selected task",
    "選択した作業の個人会話"
  ],
  "消息": [
    "Message",
    "メッセージ"
  ],
  "围绕这项工作，写下你的问题或想法…": [
    "Write a question or idea about this task…",
    "この作業について質問やアイデアを入力…"
  ],
  "发送给秘书": [
    "Send to secretary",
    "秘書に送信"
  ],
  "属于你的工作空间": [
    "Your own workspace",
    "あなた専用のワークスペース"
  ],
  "把想法、工作与安排": [
    "Bring ideas, work and schedules",
    "アイデア、作業、予定を"
  ],
  "放在一起。": [
    "together.",
    "ひとつに。"
  ],
  "私人对话、资料和策略保存在当前个人服务中。与平台协作时，由你选择并确认提交的内容。": [
    "Private chats, records and policies stay in your personal service. You choose and confirm what to share with the platform.",
    "会話、資料、ポリシーは個人サービスに保存されます。連携先に送る内容は自分で選び、確認します。"
  ],
  "工作安排": [
    "Work scheduling",
    "作業の予定"
  ],
  "明确授权": [
    "Explicit authorization",
    "明示的な許可"
  ],
  "连接个人服务": [
    "Connect personal service",
    "個人サービスに接続"
  ],
  "欢迎回来": [
    "Welcome back",
    "おかえりなさい"
  ],
  "本机连接会自动进入工作空间。其他设备请从服务终端获取一次性配对码。": [
    "Local connections open the workspace automatically. Other devices need a one-time pairing code from the service terminal.",
    "ローカル接続では自動で入れます。別の端末ではサービスのターミナルからワンタイムコードを取得してください。"
  ],
  "一次性配对码": [
    "One-time pairing code",
    "ワンタイムペアリングコード"
  ],
  "登录我的服务": [
    "Sign in to my service",
    "個人サービスにログイン"
  ],
  "本机无需配对码；若本机仍停在此页，请重启更新后的秘书服务并刷新页面。": [
    "Local access needs no pairing code. If this page remains, restart the updated service and refresh.",
    "ローカル接続ではコードは不要です。この画面が続く場合は更新後のサービスを再起動し、ページを更新してください。"
  ],
  "界面风格": [
    "Appearance style",
    "画面スタイル"
  ],
  "自动": [
    "Auto",
    "自動"
  ],
  "时间设置": [
    "Schedule settings",
    "切替時刻"
  ],
  "选择今天的心情": [
    "Choose today's mood",
    "今日の気分を選ぶ"
  ],
  "两套风格共享功能与数据。白天专注，晚上换一种氛围。": [
    "Both styles share features and data. Focus by day, change the mood at night.",
    "機能とデータは共通です。昼は集中し、夜は雰囲気を変えましょう。"
  ],
  "切换方式": [
    "Switching mode",
    "切替方法"
  ],
  "按时间自动切换": [
    "Switch automatically by time",
    "時刻で自動切替"
  ],
  "保持 HUD": [
    "Keep HUD",
    "HUD を維持"
  ],
  "保持 RPG": [
    "Keep RPG",
    "RPG を維持"
  ],
  "HUD · 专注控制台": [
    "HUD · Focus console",
    "HUD · 集中コンソール"
  ],
  "青色线框 / 几何面板": [
    "Cyan outlines / geometric panels",
    "シアンの輪郭 / 幾何学パネル"
  ],
  "RPG · 秘书伙伴": [
    "RPG · Companion",
    "RPG · パートナー"
  ],
  "暖金装饰 / 任务日志": [
    "Warm gold / task journal",
    "暖かい金色 / 作業ログ"
  ],
  "白天开始 · HUD": [
    "Day starts · HUD",
    "昼の開始 · HUD"
  ],
  "夜间开始 · RPG": [
    "Night starts · RPG",
    "夜の開始 · RPG"
  ],
  "本机时区": [
    "Local time zone",
    "端末のタイムゾーン"
  ],
  "使用本机时区：": [
    "Local time zone: ",
    "端末のタイムゾーン："
  ],
  "。手动选择后将保持该风格，选择“自动”恢复定时切换。": [
    ". A manual choice stays active. Choose Auto to resume scheduled switching.",
    "。手動選択したスタイルを維持します。「自動」で時刻による切替を再開します。"
  ],
  "取消": [
    "Cancel",
    "キャンセル"
  ],
  "保存外观": [
    "Save appearance",
    "外観を保存"
  ],
  "当前环境无法保存偏好，本次窗口内仍可切换。": [
    "Preferences cannot be saved here. Switching still works in this window.",
    "設定を保存できません。このウィンドウ内では切替可能です。"
  ],
  "外观已应用，但当前环境无法保存偏好。": [
    "Appearance applied, but preferences could not be saved.",
    "外観を適用しましたが、設定は保存できませんでした。"
  ],
  "请填写不同的白天、夜间开始时间。": [
    "Choose different day and night start times.",
    "昼と夜の開始時刻を別々に設定してください。"
  ],
  "按本机时间切换：白天 HUD，夜间 RPG": [
    "Use local time: HUD by day, RPG at night",
    "端末の時刻を使用：昼は HUD、夜は RPG"
  ],
  "切换到 HUD 风格": [
    "Switch to HUD style",
    "HUD スタイルに切替"
  ],
  "切换到 RPG 风格": [
    "Switch to RPG style",
    "RPG スタイルに切替"
  ],
  "自动 · HUD": [
    "Auto · HUD",
    "自動 · HUD"
  ],
  "自动 · RPG": [
    "Auto · RPG",
    "自動 · RPG"
  ],
  "卡通冒险伙伴": [
    "Cartoon adventure companion",
    "アニメ風の冒険パートナー"
  ],
  "智能助理": [
    "AI assistant",
    "AI アシスタント"
  ],
  "你的冒险伙伴": [
    "Your adventure companion",
    "あなたの冒険パートナー"
  ],
  "你的智能助理": [
    "Your AI assistant",
    "あなたの AI アシスタント"
  ],
  "把今天的想法，写成下一页故事。": [
    "Turn today's ideas into the next chapter.",
    "今日のアイデアを、次の物語へ。"
  ],
  "聚焦当前目标，让每一步清晰可见。": [
    "Focus on your goal. See every step clearly.",
    "目標に集中し、一歩ずつ見通しよく。"
  ],
  "工作伙伴": [
    "Work companion",
    "作業パートナー"
  ],
  "当前工作空间": [
    "Current workspace",
    "現在のワークスペース"
  ],
  "与秘书对话": [
    "Chat with secretary",
    "秘書と会話"
  ],
  "查看工作日志": [
    "Open task journal",
    "作業ログを開く"
  ],
  "查看 Plans": [
    "View Plans",
    "Plan を表示"
  ],
  "查看 Fleet": [
    "View Fleet",
    "Worker 一覧を表示"
  ],
  "每一步进展，都来自真实工作。": [
    "Every step reflects real work.",
    "進捗は実際の作業に基づきます。"
  ],
  "等待个人服务": [
    "Waiting for personal service",
    "個人サービスを待機中"
  ],
  "查看页面中的已记录状态": [
    "See the recorded status on this page",
    "このページの記録済み状態を確認"
  ],
  "PERSONAL COMPANION": [
    "PERSONAL COMPANION",
    "パーソナルパートナー"
  ],
  "MISSION COMPANION": [
    "MISSION COMPANION",
    "ミッションパートナー"
  ],
  "YOUR WORK, IN FOCUS": [
    "YOUR WORK, IN FOCUS",
    "目の前の作業に集中"
  ],
  "工作记录详情": [
    "Task record details",
    "作業記録の詳細"
  ],
  "阅读期间内容保持不变。": [
    "Content stays unchanged while you read.",
    "閲覧中の内容は固定されます。"
  ],
  "更新详情": [
    "Update details",
    "詳細を更新"
  ],
  "关闭": [
    "Close",
    "閉じる"
  ],
  "显示当前记录，阅读期间保持不变；可随时更新详情。": [
    "Showing a fixed snapshot. Update details when ready.",
    "取得時の記録を表示中です。必要に応じて詳細を更新できます。"
  ],
  "工作已移除，当前显示的是打开时的记录。": [
    "The task was removed. This is the snapshot from when you opened it.",
    "作業は削除されました。開いた時点の記録を表示しています。"
  ],
  "有新记录，点击“更新详情”查看；当前内容保持不变。": [
    "New records are available. Select Update details to load them.",
    "新しい記録があります。「詳細を更新」で読み込めます。"
  ],
  "失败原因": [
    "Failure reason",
    "失敗の理由"
  ],
  "最新处理结果": [
    "Latest result",
    "最新の処理結果"
  ],
  "未填写说明": [
    "No description",
    "説明なし"
  ],
  "指定输出位置": [
    "Requested output path",
    "指定の出力先"
  ],
  "失败的对话": [
    "Failed conversations",
    "失敗した会話"
  ],
  "未记录具体原因": [
    "No detailed reason recorded",
    "詳細な理由は未記録"
  ],
  "执行记录": [
    "Execution records",
    "実行記録"
  ],
  "暂无本地日常处理步骤；平台任务请通过卡片中的平台入口查看。": [
    "No local steps recorded. Open the platform link on the task card for platform tasks.",
    "ローカル処理の記録はありません。プラットフォーム作業はカードのリンクから確認してください。"
  ],
  "已保存文件": [
    "Saved files",
    "保存済みファイル"
  ],
  "原始数据（排查时使用）": [
    "Raw data (diagnostics)",
    "元データ（調査用）"
  ],
  "尚未安排": [
    "Not scheduled",
    "未設定"
  ],
  "时间不可用": [
    "Time unavailable",
    "時刻を取得できません"
  ],
  "未命名工作": [
    "Untitled task",
    "無題の作業"
  ],
  "报告更新于": [
    "Report updated at",
    "レポート更新日時"
  ],
  "继续对话": [
    "Continue chat",
    "会話を続ける"
  ],
  "安排执行": [
    "Schedule execution",
    "実行を設定"
  ],
  "已核对结果，重新执行": [
    "Results checked; run again",
    "結果を確認済み・再実行"
  ],
  "现在处理": [
    "Run now",
    "今すぐ処理"
  ],
  "已交给秘书处理，结果会自动更新。": [
    "Sent to the secretary. Results will update automatically.",
    "秘書に渡しました。結果は自動で更新されます。"
  ],
  "移除工作": [
    "Remove task",
    "作業を削除"
  ],
  "已移到设置中的“已删除工作”，可以恢复。": [
    "Moved to Deleted tasks in Settings. You can restore it there.",
    "設定の「削除済み作業」に移動しました。復元できます。"
  ],
  "查看平台 Plan": [
    "View platform Plan",
    "プラットフォームの Plan を表示"
  ],
  "查看工作详情": [
    "View task details",
    "作業の詳細を表示"
  ],
  "安排定时执行": [
    "Schedule execution",
    "時刻指定で実行"
  ],
  "还没有私人工作": [
    "No private tasks yet",
    "個人作業はまだありません"
  ],
  "先记下工作标题和想法，再开始讨论、马上执行或安排定时。": [
    "Record a title and idea, then discuss it, run it or schedule it.",
    "タイトルとアイデアを記録してから、相談、実行、時刻指定ができます。"
  ],
  "日常对话（无需先建工作）": [
    "Everyday chat (no task needed)",
    "日常の会話（作業作成は不要）"
  ],
  "请选择工作": [
    "Select a task",
    "作業を選択"
  ],
  "请先创建工作": [
    "Create a task first",
    "先に作業を作成してください"
  ],
  "直接开始对话": [
    "Start chatting",
    "会話を始める"
  ],
  "输入需要帮助的事，无需先创建工作。": [
    "Tell us what you need. No task needs to be created first.",
    "手伝ってほしいことを入力してください。作業の作成は不要です。"
  ],
  "从一个问题开始": [
    "Start with a question",
    "質問から始める"
  ],
  "写下问题或想法，秘书会在日常范围内直接处理。": [
    "Share a question or idea. The secretary handles everyday requests directly.",
    "質問やアイデアを入力してください。日常の範囲で直接処理します。"
  ],
  "你": [
    "You",
    "あなた"
  ],
  "个人模型": [
    "Personal model",
    "個人モデル"
  ],
  "暂无待处理请求": [
    "No pending requests",
    "処理待ちのリクエストはありません"
  ],
  "当前私人状态中没有尚未终结的请求。": [
    "No unresolved requests in the current private snapshot.",
    "現在の個人状態に未完了のリクエストはありません。"
  ],
  "先连接模型": [
    "Connect a model first",
    "先にモデルを接続"
  ],
  "秘书可用": [
    "Secretary ready",
    "秘書は利用可能"
  ],
  "秘书已暂停": [
    "Secretary paused",
    "秘書は一時停止中"
  ],
  "首次连接模型，之后直接使用": [
    "Connect a model once, then start working",
    "最初にモデルを接続すれば、そのまま利用できます"
  ],
  "查看你的工作、安排与待处理请求": [
    "View your tasks, schedules and pending requests",
    "作業、予定、処理待ちリクエストを確認"
  ],
  "查看工作进度与处理结果": [
    "View task progress and results",
    "作業の進捗と結果を確認"
  ],
  "记录私人工作，马上执行或设定时间": [
    "Record private tasks, run now or schedule them",
    "個人作業を記録し、今すぐ実行または時刻を指定"
  ],
  "围绕具体工作讨论，结果保存在本机": [
    "Discuss a task. Results stay on this device",
    "作業について相談し、結果を端末に保存"
  ],
  "管理已确认的私人策略及精确授权": [
    "Manage confirmed private policies and exact grants",
    "確認済みの個人ポリシーと許可を管理"
  ],
  "预览并确认需要提交给平台的内容": [
    "Preview and confirm platform submissions",
    "プラットフォームに送信する内容をプレビューして確認"
  ],
  "保存草稿、模板与待办": [
    "Save drafts, templates and to-dos",
    "下書き、テンプレート、ToDo を保存"
  ],
  "核对原请求与回执，保留私人记录": [
    "Check original requests and receipts; keep private records",
    "元のリクエストと受領記録を確認し、個人記録を保持"
  ],
  "处理恢复待确认、已错过的工作，以及需要你允许的具体操作": [
    "Review recovery, missed tasks and actions needing your permission",
    "復旧確認、実行時刻を過ぎた作業、許可が必要な操作を確認"
  ],
  "模型连接": [
    "Model connection",
    "モデル接続"
  ],
  "Codex app-server（本机，支持 Windows）": [
    "Codex app-server (local, Windows supported)",
    "Codex app-server（ローカル・Windows 対応）"
  ],
  "OpenAI Agents SDK（Responses API）": [
    "OpenAI Agents SDK (Responses API)",
    "OpenAI Agents SDK（Responses API）"
  ],
  "兼容 Chat Completions 的模型服务（原有方式）": [
    "Chat Completions compatible service (legacy)",
    "Chat Completions 互換サービス（従来）"
  ],
  "自定义模型程序（高级）": [
    "Custom model command (advanced)",
    "カスタムモデルプログラム（詳細）"
  ],
  "Codex 程序路径（高级，可选）": [
    "Codex executable path (advanced, optional)",
    "Codex 実行ファイル（詳細・任意）"
  ],
  "程序路径": [
    "Executable path",
    "実行ファイル"
  ],
  "默认从 PATH 查找 codex，无需填写": [
    "Find codex in PATH by default; leave empty",
    "既定では PATH から codex を検索します。入力不要です"
  ],
  "推理强度": [
    "Reasoning effort",
    "推論の強度"
  ],
  "使用默认值": [
    "Use default",
    "既定値を使用"
  ],
  "服务地址": [
    "Service URL",
    "サービス URL"
  ],
  "模型名称": [
    "Model name",
    "モデル名"
  ],
  "填写服务提供的模型名称": [
    "Enter the model name from your provider",
    "提供元のモデル名を入力"
  ],
  "模型密钥": [
    "API key",
    "API キー"
  ],
  "本机模型可留空；已保存时留空表示保持原值": [
    "Leave empty for local models, or to keep a saved key",
    "ローカルモデルでは空欄可。保存済みのキーも空欄で維持します"
  ],
  "常用资料目录（可选，每行一个）": [
    "Common folders (optional, one per line)",
    "よく使うフォルダー（任意・1行に1つ）"
  ],
  "留空也可以；工作中明确指定的文件位置已在该次授权内。": [
    "Optional. File paths explicitly requested for a task are already included in that task's permission.",
    "任意です。作業で明示したファイルの場所は、その作業の許可に含まれます。"
  ],
  "自定义程序参数（高级）": [
    "Custom command arguments (advanced)",
    "カスタムプログラム引数（詳細）"
  ],
  "程序 argv JSON": [
    "Command argv JSON",
    "プログラムの argv JSON"
  ],
  "保存并开始使用": [
    "Save and start",
    "保存して利用開始"
  ],
  "检查模型连接": [
    "Check model connection",
    "モデル接続を確認"
  ],
  "已删除的工作": [
    "Deleted tasks",
    "削除済みの作業"
  ],
  "私人备份": [
    "Private backup",
    "個人バックアップ"
  ],
  "下载私人备份": [
    "Download private backup",
    "個人バックアップをダウンロード"
  ],
  "取消安排或撤销私人策略": [
    "Cancel schedules or revoke policies",
    "予定の取消・ポリシーの撤回"
  ],
  "类型": [
    "Type",
    "種類"
  ],
  "选择记录": [
    "Select a record",
    "記録を選択"
  ],
  "请选择记录": [
    "Select a record",
    "記録を選択"
  ],
  "当前版本": [
    "Current version",
    "現在のバージョン"
  ],
  "确认本地取消或撤销": [
    "Confirm local cancellation or revocation",
    "ローカルの取消・撤回を確認"
  ],
  "查看完整私人状态与身份标识": [
    "View full private state and identity",
    "個人状態と識別情報の全体を表示"
  ],
  "待处理请求与回执": [
    "Pending requests and receipts",
    "処理待ちのリクエストと受領記録"
  ],
  "核对原请求与已记录的真实状态，保留私人记录和历史。": [
    "Check original requests and recorded state; preserve private records and history.",
    "元のリクエストと記録済みの状態を確認し、個人記録と履歴を保持します。"
  ],
  "连接桌面秘书": [
    "Connect desktop secretary",
    "デスクトップ秘書に接続"
  ],
  "本服务地址：": [
    "Service URL: ",
    "サービス URL："
  ],
  "生成桌面配对码": [
    "Generate desktop pairing code",
    "デスクトップ用コードを生成"
  ],
  "复制配对码": [
    "Copy pairing code",
    "ペアリングコードをコピー"
  ],
  "显示配对码": [
    "Show pairing code",
    "ペアリングコードを表示"
  ],
  "隐藏配对码": [
    "Hide pairing code",
    "ペアリングコードを非表示"
  ],
  "配对码已过期，请生成新码。": [
    "Pairing code expired. Generate a new one.",
    "コードの期限が切れました。新しく生成してください。"
  ],
  "配对码已复制。": [
    "Pairing code copied.",
    "コードをコピーしました。"
  ],
  "浏览器无法复制，请显示配对码后手动复制。": [
    "Copy unavailable. Reveal the pairing code and copy it manually.",
    "コピーできません。コードを表示して手動でコピーしてください。"
  ],
  "正在请求新配对码，请稍候……": [
    "Requesting a new pairing code…",
    "新しいコードを取得中…"
  ],
  "临时配对码已生成，请复制到桌面界面。现有会话继续有效。": [
    "Pairing code generated. Copy it to the desktop app. Existing sessions remain valid.",
    "コードを生成しました。デスクトップにコピーしてください。既存のセッションは有効です。"
  ],
  "草稿": [
    "Draft",
    "下書き"
  ],
  "模板": [
    "Template",
    "テンプレート"
  ],
  "待办": [
    "To-do",
    "ToDo"
  ],
  "资料列表": [
    "Records",
    "資料一覧"
  ],
  "新建资料": [
    "New record",
    "資料を作成"
  ],
  "编辑私人资料": [
    "Edit private record",
    "個人資料を編集"
  ],
  "资料标识": [
    "Record ID",
    "資料 ID"
  ],
  "当前版本（新建为 0）": [
    "Current version (0 for new)",
    "現在のバージョン（新規は 0）"
  ],
  "私人内容（JSON 对象）": [
    "Private content (JSON object)",
    "個人内容（JSON オブジェクト）"
  ],
  "保存私人记录": [
    "Save private record",
    "個人記録を保存"
  ],
  "已保存的策略": [
    "Saved policies",
    "保存済みポリシー"
  ],
  "新建策略": [
    "New policy",
    "ポリシーを作成"
  ],
  "策略内容与授权": [
    "Policy and permission",
    "ポリシーと許可"
  ],
  "策略标识": [
    "Policy ID",
    "ポリシー ID"
  ],
  "已确认的平台授权 ID": [
    "Confirmed platform grant ID",
    "確認済みプラットフォーム許可 ID"
  ],
  "私人策略原文": [
    "Original private policy",
    "個人ポリシーの原文"
  ],
  "私人策略到期时间（可选）": [
    "Private policy expiry (optional)",
    "個人ポリシーの有効期限（任意）"
  ],
  "可选禁止投影（最小结构化范围 JSON，不含原文）": [
    "Optional denial projection (minimal scope JSON, no original text)",
    "任意の拒否投影（最小範囲の JSON・原文なし）"
  ],
  "我确认此私人策略及其关联的精确平台能力": [
    "I confirm this policy and its exact platform capability",
    "このポリシーと紐づく厳密な権限を確認します"
  ],
  "保存已确认策略": [
    "Save confirmed policy",
    "確認済みポリシーを保存"
  ],
  "申请固定动作": [
    "Request a fixed action",
    "固定操作を要求"
  ],
  "已明确授予的固定能力 ID": [
    "Explicitly granted capability ID",
    "明示的に許可された権限 ID"
  ],
  "提交当前固定动作候选": [
    "Submit the fixed action candidate",
    "固定操作の候補を送信"
  ],
  "还没有代理策略": [
    "No delegation policies yet",
    "代理ポリシーはまだありません"
  ],
  "编辑策略": [
    "Edit policy",
    "ポリシーを編集"
  ],
  "还没有这类资料": [
    "No records of this type",
    "この種類の資料はありません"
  ],
  "编辑资料": [
    "Edit record",
    "資料を編集"
  ],
  "删除草稿": [
    "Delete draft",
    "下書きを削除"
  ],
  "没有需要确认的操作": [
    "No actions need confirmation",
    "確認が必要な操作はありません"
  ],
  "没有已移除的工作": [
    "No removed tasks",
    "削除済みの作業はありません"
  ],
  "移除后的工作可在这里恢复。": [
    "Restore removed tasks here.",
    "削除した作業はここで復元できます。"
  ],
  "恢复工作": [
    "Restore task",
    "作業を復元"
  ],
  "工作已恢复。": [
    "Task restored.",
    "作業を復元しました。"
  ],
  "现在执行": [
    "Run now",
    "今すぐ実行"
  ],
  "重新定时": [
    "Reschedule",
    "時刻を再設定"
  ],
  "取消这项工作": [
    "Cancel this task",
    "この作業を取り消す"
  ],
  "已取消这项工作。": [
    "Task cancelled.",
    "作業を取り消しました。"
  ],
  "允许这一次": [
    "Allow once",
    "今回のみ許可"
  ],
  "已允许这一次操作。": [
    "Action allowed once.",
    "今回の操作を許可しました。"
  ],
  "拒绝": [
    "Deny",
    "拒否"
  ],
  "已拒绝。": [
    "Denied.",
    "拒否しました。"
  ],
  "读取文件": [
    "Read file",
    "ファイルを読み込み"
  ],
  "写入文件": [
    "Write file",
    "ファイルを書き込み"
  ],
  "不可恢复删除": [
    "Permanent deletion",
    "復元できない削除"
  ],
  "查看将写入的完整内容": [
    "Preview full file content",
    "書き込む内容の全体を表示"
  ],
  "已安排": [
    "Scheduled",
    "予定済み"
  ],
  "正在处理": [
    "Running",
    "処理中"
  ],
  "已移除": [
    "Removed",
    "削除済み"
  ],
  "等待你确认": [
    "Awaiting your confirmation",
    "あなたの確認待ち"
  ],
  "等待处理": [
    "Pending",
    "処理待ち"
  ],
  "启动待确认": [
    "Start needs confirmation",
    "開始確認待ち"
  ],
  "待人工处理": [
    "Needs human attention",
    "人による対応待ち"
  ],
  "已确认": [
    "Confirmed",
    "確認済み"
  ],
  "已暂停": [
    "Paused",
    "一時停止"
  ],
  "已取消": [
    "Cancelled",
    "取消済み"
  ],
  "已撤销": [
    "Revoked",
    "撤回済み"
  ],
  "待发送": [
    "Queued",
    "送信待ち"
  ],
  "发送中，待核对": [
    "Sending; check receipt",
    "送信中・受領記録を確認"
  ],
  "已记录": [
    "Recorded",
    "記録済み"
  ],
  "未知结果": [
    "Unknown result",
    "結果不明"
  ],
  "已成功": [
    "Succeeded",
    "成功"
  ],
  "已拒绝": [
    "Denied",
    "拒否済み"
  ],
  "已失败": [
    "Failed",
    "失敗"
  ],
  "待启动": [
    "Pending start",
    "開始待ち"
  ],
  "未记录": [
    "Not recorded",
    "未記録"
  ],
  "未记录状态": [
    "No state recorded",
    "状態未記録"
  ],
  "状态": [
    "Status",
    "状態"
  ],
  "版本": [
    "Version",
    "バージョン"
  ],
  "更新时间": [
    "Updated at",
    "更新日時"
  ],
  "创建时间": [
    "Created at",
    "作成日時"
  ],
  "结果": [
    "Result",
    "結果"
  ],
  "原因": [
    "Reason",
    "理由"
  ],
  "摘要": [
    "Summary",
    "概要"
  ],
  "文件": [
    "File",
    "ファイル"
  ],
  "大小": [
    "Size",
    "サイズ"
  ],
  "字节数": [
    "Bytes",
    "バイト数"
  ],
  "步骤": [
    "Step",
    "手順"
  ],
  "动作": [
    "Action",
    "操作"
  ],
  "是否成功": [
    "Succeeded",
    "成功したか"
  ],
  "是否完成": [
    "Completed",
    "完了したか"
  ],
  "是否写入": [
    "Written",
    "書き込み済みか"
  ],
  "说明": [
    "Description",
    "説明"
  ],
  "来源": [
    "Source",
    "参照元"
  ],
  "来源地址": [
    "Source URL",
    "参照元 URL"
  ],
  "参考来源": [
    "References",
    "参考資料"
  ],
  "已写入文件": [
    "Written files",
    "書き込み済みファイル"
  ],
  "查询结果": [
    "Search results",
    "検索結果"
  ],
  "查询内容": [
    "Query",
    "検索内容"
  ],
  "文件位置": [
    "File path",
    "ファイルの場所"
  ],
  "输出位置": [
    "Output path",
    "出力先"
  ],
  "内容": [
    "Content",
    "内容"
  ],
  "是": [
    "Yes",
    "はい"
  ],
  "否": [
    "No",
    "いいえ"
  ],
  "无": [
    "None",
    "なし"
  ],
  "生成回复": [
    "Generate reply",
    "返信を生成"
  ],
  "处理步骤": [
    "Processing steps",
    "処理手順"
  ],
  "查询公开信息": [
    "Search public information",
    "公開情報を検索"
  ],
  "读取网页": [
    "Read web page",
    "Web ページを読み込み"
  ],
  "列出文件": [
    "List files",
    "ファイル一覧"
  ],
  "删除文件": [
    "Delete file",
    "ファイルを削除"
  ],
  "提交专业任务": [
    "Submit specialist task",
    "専門タスクを送信"
  ],
  "获取任务": [
    "Get task",
    "タスクを取得"
  ],
  "保存结果": [
    "Save result",
    "結果を保存"
  ],
  "创建目录": [
    "Create folder",
    "フォルダーを作成"
  ],
  "工作已记录，状态已更新。": [
    "Task recorded; status updated.",
    "作業を記録し、状態を更新しました。"
  ],
  "收到，秘书正在处理。回复会自动显示。": [
    "Received. The secretary is working; the reply will appear automatically.",
    "受け付けました。処理後に返信が自動表示されます。"
  ],
  "设置已保存，可以直接聊天和安排工作。": [
    "Settings saved. You can chat and schedule work now.",
    "設定を保存しました。会話や作業の予定を設定できます。"
  ],
  "秘书已继续工作，待处理安排会自动推进。": [
    "Secretary resumed. Pending schedules will proceed automatically.",
    "秘書を再開しました。処理待ちの予定を自動で進めます。"
  ],
  "秘书已暂停；已在途操作不会停止，结果仍可记录。": [
    "Secretary paused. In-flight actions continue and results can still be recorded.",
    "秘書を停止しました。進行中の操作は続行し、結果は記録されます。"
  ],
  "正在检查模型连接，请稍候……": [
    "Checking model connection…",
    "モデル接続を確認中…"
  ],
  "模型连接检查未完成": [
    "Model connection check incomplete",
    "モデル接続の確認は未完了"
  ],
  "首次使用请打开“设置”连接模型。保存后可直接聊天、查询和安排工作。": [
    "Open Settings to connect a model. Then chat, search and schedule tasks.",
    "設定でモデルを接続してください。保存後は会話、検索、予定設定ができます。"
  ],
  "密钥已保存；留空保持原值": [
    "Key saved; leave empty to keep it",
    "キーは保存済みです。空欄で維持します"
  ],
  "本机无认证模型可留空": [
    "Leave empty for local models without authentication",
    "認証不要のローカルモデルでは空欄可"
  ]
};
  Object.assign(workspaceCatalog, {
  "告诉秘书做什么，以及结果放在哪里。可以马上执行，也可按需设定时间。日常计划和执行会自动处理；失败或需要你确认时，会说明原因和下一步。": [
    "Tell the secretary what to do and where to save the result. Run now or schedule it. Failures and permission requests include a reason and next steps.",
    "作業内容と結果の保存先を伝えてください。すぐ実行するか、時刻を指定できます。失敗や確認が必要な場合は理由と次の手順を表示します。"
  ],
  "策略文字不会创建平台权限；每条策略都关联你已明确确认的固定授权。": [
    "Policy text grants no platform permissions. Each policy links to an explicitly confirmed grant.",
    "ポリシーの文章だけで権限は付与されません。各ポリシーは確認済みの許可に紐づきます。"
  ],
  "从记录中选择编辑，版本会自动带入。": [
    "Select a record to edit; its version is filled automatically.",
    "記録を選んで編集すると、バージョンが自動入力されます。"
  ],
  "禁止投影提交后，平台目标保持暂停，需在平台明确交回。": [
    "After a denial projection is submitted, the platform goal stays paused until explicitly returned on the platform.",
    "拒否投影の送信後、プラットフォームで明示的に返却するまで目標は停止状態を維持します。"
  ],
  "当前版本（新策略为 0）": [
    "Current version (0 for a new policy)",
    "現在のバージョン（新規ポリシーは 0）"
  ],
  "仅提交已经明确授予的固定能力候选。": [
    "Submit only candidates for explicitly granted fixed capabilities.",
    "明示的に許可済みの固定権限の候補のみ送信します。"
  ],
  "连接已授权的专业平台（高级）": [
    "Connect an authorized specialist platform (advanced)",
    "許可済みの専門プラットフォームに接続（詳細）"
  ],
  "与平台协作": [
    "Collaborate with the platform",
    "プラットフォームと連携"
  ],
  "先在本机预览精确投影，再由你明确确认提交。私人对话、策略和资料留在此服务。": [
    "Preview the exact projection locally, then explicitly confirm submission. Private chats, policies and records stay here.",
    "正確な投影をローカルでプレビューし、明示的に送信を確認します。会話、ポリシー、資料はここに保持します。"
  ],
  "平台工作问题": [
    "Platform work questions",
    "プラットフォームの作業に関する質問"
  ],
  "读取问题 → 预览回答 → 确认排队": [
    "Read questions → Preview answer → Confirm queue",
    "質問を取得 → 回答をプレビュー → 送信待ちに追加"
  ],
  "01 · 读取": [
    "01 · Read",
    "01 · 取得"
  ],
  "读取正式问题": [
    "Read formal questions",
    "正式な質問を取得"
  ],
  "已授予读取范围的计划 ID": [
    "Plan ID with a read grant",
    "読み取り権限のある Plan ID"
  ],
  "读取平台问题": [
    "Read platform questions",
    "プラットフォームの質問を取得"
  ],
  "查看读取到的正式问题": [
    "View retrieved formal questions",
    "取得した正式な質問を表示"
  ],
  "尚未读取问题。": [
    "Questions not loaded.",
    "質問は未取得です。"
  ],
  "02 · 选择与预览": [
    "02 · Select and preview",
    "02 · 選択とプレビュー"
  ],
  "预览本次回答": [
    "Preview this answer",
    "今回の回答をプレビュー"
  ],
  "问题 ID": [
    "Question ID",
    "質問 ID"
  ],
  "问题版本": [
    "Question version",
    "質問のバージョン"
  ],
  "你明确选择提交的目标": [
    "Goal you explicitly choose to submit",
    "送信対象として明示的に選択した目標"
  ],
  "处理本问题必需的上下文": [
    "Context needed for this question",
    "この質問の処理に必要なコンテキスト"
  ],
  "原始回答": [
    "Original answer",
    "回答の原文"
  ],
  "在本机预览本次投影": [
    "Preview this projection locally",
    "今回の投影をローカルでプレビュー"
  ],
  "即将提交的精确内容": [
    "Exact content to submit",
    "送信する正確な内容"
  ],
  "先选择内容并生成本地预览。": [
    "Select content and generate a local preview first.",
    "先に内容を選択し、ローカルプレビューを生成してください。"
  ],
  "03 · 明确确认": [
    "03 · Explicit confirmation",
    "03 · 明示的な確認"
  ],
  "确认回答投影": [
    "Confirm answer projection",
    "回答の投影を確認"
  ],
  "仅提交上面明确选择的内容。普通回答不授予执行权限。": [
    "Submit only the selected content above. An ordinary answer grants no execution permission.",
    "上で選択した内容のみ送信します。通常の回答では実行権限は付与されません。"
  ],
  "明确授予的 A19 或 A18 能力 ID": [
    "Explicitly granted A19 or A18 capability ID",
    "明示的に許可された A19 または A18 権限 ID"
  ],
  "确认并提交上述回答": [
    "Confirm and submit this answer",
    "上記の回答を確認して送信"
  ],
  "选定软件目标": [
    "Selected software goal",
    "選択したソフトウェア目標"
  ],
  "选择目标 → 本地预览 → 确认提案": [
    "Select goal → Local preview → Confirm proposal",
    "目標を選択 → ローカルプレビュー → 提案を確認"
  ],
  "01 · 本地预览": [
    "01 · Local preview",
    "01 · ローカルプレビュー"
  ],
  "准备选定目标": [
    "Prepare selected goal",
    "選択した目標を準備"
  ],
  "只提交你选择的字段，不会自动启动工作。": [
    "Only selected fields are submitted. Work does not start automatically.",
    "選択した項目のみ送信します。作業は自動で開始しません。"
  ],
  "选定目标": [
    "Selected goal",
    "選択した目標"
  ],
  "必要上下文": [
    "Required context",
    "必要なコンテキスト"
  ],
  "验收（未明确可留空，后续澄清）": [
    "Acceptance criteria (optional; clarify later)",
    "受け入れ条件（未確定なら空欄・後で確認）"
  ],
  "本地预览选定投影": [
    "Preview selected projection locally",
    "選択した投影をローカルでプレビュー"
  ],
  "先填写目标并生成本地预览。": [
    "Enter a goal and generate a local preview first.",
    "先に目標を入力し、ローカルプレビューを生成してください。"
  ],
  "02 · 核对原文与摘要": [
    "02 · Check source and digest",
    "02 · 原文とダイジェストを確認"
  ],
  "确认提交提案": [
    "Confirm proposal submission",
    "提案の送信を確認"
  ],
  "原请求 ID": [
    "Original request ID",
    "元のリクエスト ID"
  ],
  "确认原文": [
    "Confirmed source text",
    "確認する原文"
  ],
  "确认摘要": [
    "Confirmed digest",
    "確認するダイジェスト"
  ],
  "确认原文与摘要，提交目标提案": [
    "Confirm source and digest; submit goal proposal",
    "原文とダイジェストを確認し、目標の提案を送信"
  ],
  "03 · 查看原回执": [
    "03 · Check original receipt",
    "03 · 元の受領記録を確認"
  ],
  "查询提案回执": [
    "Check proposal receipt",
    "提案の受領記録を確認"
  ],
  "仅查询原回执": [
    "Check original receipt only",
    "元の受領記録のみ確認"
  ],
  "UNKNOWN 或重启留下的 SENDING 状态只查询原 ID；即使返回 404，也不会自动重发。平台当前人类采纳后才形成草稿。": [
    "For UNKNOWN or SENDING after restart, query only the original ID. Even a 404 does not trigger automatic resubmission. A draft is created only after human acceptance on the platform.",
    "再起動後の UNKNOWN や SENDING は元の ID のみ照会します。404 でも自動再送しません。プラットフォームで人が採用した後に下書きが作成されます。"
  ],
  "保存草稿、可复用模板和待办；这些内容保留在当前个人服务中。": [
    "Save drafts, reusable templates and to-dos in this personal service.",
    "下書き、再利用できるテンプレート、ToDo をこの個人サービスに保存します。"
  ],
  "资料类型": [
    "Record type",
    "資料の種類"
  ],
  "列表中选择已有记录时自动带入版本；新建记录从版本 0 开始。": [
    "Selecting a record fills its version automatically. New records start at version 0.",
    "既存の記録を選ぶとバージョンが自動入力されます。新規は 0 から始まります。"
  ],
  "停机前未开始的马上执行任务、错过时间的定时任务会在这里请你决定执行、改期或取消。执行中断的工作请先核对已有结果。超范围或不可恢复删除的允许仍只适用于这一次。": [
    "For tasks missed during downtime, choose to run, reschedule or cancel here. Check existing results before restarting interrupted work. Out-of-scope actions and permanent deletions require one-time approval.",
    "停止中に実行できなかった作業は、ここで実行、再設定、取消を選べます。中断した作業は既存の結果を先に確認してください。範囲外の操作や復元できない削除の許可は今回のみ有効です。"
  ],
  "连接一次模型，之后自动恢复。平台身份、Plan 和 Worker 不属于日常必填项。": [
    "Connect a model once; settings restore automatically. Platform identity, Plan and Worker are not required for daily use.",
    "一度モデルを接続すると設定を自動復元します。日常利用にプラットフォームの識別情報、Plan、Worker は不要です。"
  ],
  "默认从 PATH 启动 Codex，沿用本机配置和已有登录，无需填写路径、模型、服务地址或密钥。Agents SDK 使用 Responses API，需先安装对应 Python 依赖并填写模型与密钥；服务地址留空使用 OpenAI。": [
    "Codex uses PATH, local settings and your existing sign-in. No path, model, URL or key is required. Agents SDK uses the Responses API; install its Python dependencies and enter a model and key. An empty URL uses OpenAI.",
    "Codex は PATH、端末の設定、既存のログインを使用します。パス、モデル、URL、キーは入力不要です。Agents SDK は Responses API を使用します。Python の依存を入れ、モデルとキーを入力してください。URL が空欄なら OpenAI を使用します。"
  ],
  "默认允许聊天、公开信息查询、资料整理和定时处理。文件写入限于你指定的位置或资料目录；覆盖前留存备份。代码、命令、付款和发布不在默认工具范围内。密钥留在本机，不进入私人备份包。模型可能按用量计费，每项工作最多 12 次模型请求，不会自动购买或充值。": [
    "Chat, public searches, organizing records and scheduled work are enabled by default. Writes are limited to requested paths or allowed folders; existing files are backed up before overwriting. Code, shell commands, payments and publishing are outside the default tools. Keys stay local and are excluded from backups. Model usage may be billed; each task allows up to 12 requests, with no automatic purchases or top-ups.",
    "会話、公開情報の検索、資料整理、時刻指定の処理が既定で利用できます。書き込みは指定先または許可フォルダーに限定し、上書き前にバックアップします。コード、コマンド、支払い、公開は既定のツール対象外です。キーは端末に保持し、バックアップに含めません。モデルは従量課金の場合があります。作業ごとに最大12回の要求で、自動購入やチャージはしません。"
  ],
  "普通移除可以恢复，不需要重复确认。本机工具会在下一个操作前停止；已经提交的平台专业任务仍会执行，需要停止时请在平台操作。": [
    "Normal removal is reversible and needs no repeated confirmation. Local tools stop before the next action. Submitted platform tasks continue; stop them on the platform if needed.",
    "通常の削除は復元可能で、繰り返し確認は不要です。ローカルツールは次の操作前に停止します。送信済みのプラットフォーム作業は続行するため、必要ならプラットフォームで停止してください。"
  ],
  "UNKNOWN 保留原请求供核对，不代表操作成功，也不会自动重放。": [
    "UNKNOWN keeps the original request for checking. It does not mean success and is not automatically replayed.",
    "UNKNOWN は照合のため元のリクエストを保持します。成功を意味せず、自動再実行もしません。"
  ],
  "包含私人记录、待发送请求与回执副本；不包含平台凭证或登录会话。": [
    "Includes private records, queued requests and receipt copies. Excludes platform credentials and sign-in sessions.",
    "個人記録、送信待ちリクエスト、受領記録のコピーを含みます。資格情報やログインセッションは含みません。"
  ],
  "停止本地新请求，保留已发送请求的对账。已在平台生效或运行的动作不回滚；停止平台工作需在平台接管或停止 Plan。": [
    "Stop new local requests and keep sent requests for reconciliation. Active platform actions are not rolled back. Take over or stop the Plan on the platform to halt that work.",
    "新しいローカル要求を停止し、送信済み要求は照合のため保持します。プラットフォームで有効または実行中の操作は戻しません。停止にはプラットフォームで引き継ぐか Plan を停止してください。"
  ],
  "私人策略": [
    "Private policy",
    "個人ポリシー"
  ],
  "在秘书目录运行 start-desktop.ps1，在桌面窗口中填写本服务地址和新配对码。两种界面共用私人记录。": [
    "Run start-desktop.ps1 from the secretary folder. Local desktop access connects automatically. Both interfaces share private records.",
    "秘書フォルダーで start-desktop.ps1 を実行します。ローカルのデスクトップ接続は自動です。両方の画面で個人記録を共有します。"
  ],
  "。新码五分钟有效、只能使用一次；生成新码会替换上一枚尚未使用的配对码。": [
    ". Codes expire in five minutes and can be used once. A new code replaces the previous unused code.",
    "。コードは5分間有効で、一度のみ使用できます。新しいコードは未使用の古いコードを置き換えます。"
  ],
  "此处填写个人服务配对码，桌面端不需要平台凭证。": [
    "Use the personal service pairing code, not platform credentials.",
    "個人サービスのペアリングコードを使用してください。プラットフォームの資格情報は不要です。"
  ],
  "配对组件尚未就绪。若点击无反应，请强制刷新页面；仍无效时请更新并重启个人服务。": [
    "Pairing is not ready. Hard-refresh the page. If it still fails, update and restart the personal service.",
    "ペアリングの準備ができていません。強制再読み込みをしてください。改善しない場合はサービスを更新して再起動してください。"
  ],
  "尚未读取状态。": [
    "Status not loaded.",
    "状態未取得です。"
  ],
  "此私人工作空间需要启用 JavaScript 后才能配对、查看状态和提交操作。": [
    "Enable JavaScript to pair, view status and submit actions.",
    "ペアリング、状態確認、操作の送信には JavaScript を有効にしてください。"
  ],
  "私人工作空间分区": [
    "Private workspace sections",
    "個人ワークスペースの区分"
  ],
  "例如 C:/temp/weather.md": [
    "For example, C:/temp/weather.md",
    "例：C:/temp/weather.md"
  ],
  "例如 daily-review": [
    "For example, daily-review",
    "例：daily-review"
  ],
  "例如 weekly-notes": [
    "For example, weekly-notes",
    "例：weekly-notes"
  ],
  "https://你的模型服务/v1": [
    "https://your-model-service/v1",
    "https://your-model-service/v1"
  ],
  "已完成": [
    "Completed",
    "完了"
  ],
  "向伙伴打招呼": [
    "Greet your companion",
    "パートナーに挨拶"
  ],
  "人物素材加载失败，请重启更新后的服务并刷新页面。": [
    "Character asset failed to load. Restart the updated service and refresh.",
    "人物の素材を読み込めません。更新済みサービスを再起動してページを更新してください。"
  ],
  "搜索资料": [
    "Search resources",
    "資料を検索"
  ],
  "搜索公开资料": [
    "Search public resources",
    "公開資料を検索"
  ],
  "读取资料": [
    "Read records",
    "資料を取得"
  ],
  "保存资料": [
    "Save records",
    "資料を保存"
  ],
  "操作": [
    "Operation",
    "操作"
  ],
  "原因代码": [
    "Reason code",
    "理由コード"
  ],
  "标题": [
    "Title",
    "タイトル"
  ],
  "结果位置": [
    "Result path",
    "結果の場所"
  ],
  "处理结果": [
    "Processing result",
    "処理結果"
  ],
  "名称": [
    "Name",
    "名前"
  ],
  "工作标识": [
    "Task ID",
    "作業 ID"
  ],
  "平台计划": [
    "Platform Plan",
    "プラットフォームの Plan"
  ],
  "平台任务": [
    "Platform task",
    "プラットフォームのタスク"
  ],
  "原请求标识": [
    "Original request ID",
    "元のリクエスト ID"
  ],
  "时区": [
    "Time zone",
    "タイムゾーン"
  ],
  "输出": [
    "Output",
    "出力"
  ],
  "工具": [
    "Tool",
    "ツール"
  ],
  "参数": [
    "Arguments",
    "引数"
  ],
  "摘录": [
    "Excerpt",
    "抜粋"
  ],
  "是否存在": [
    "Exists",
    "存在するか"
  ],
  "安排与结果位置": [
    "Schedule and output path",
    "予定と出力先"
  ],
  "未知": [
    "Unknown",
    "不明"
  ]
});
  Object.assign(workspaceCatalog, {
  "请先登录个人服务并刷新状态，再生成配对码。": [
    "Sign in and refresh personal service status before generating a code.",
    "個人サービスにログインして状態を更新してからコードを生成してください。"
  ],
  "当前运行的秘书服务尚不支持再次配对。请更新并重启个人服务，再重新登录后生成配对码。": [
    "This service version does not support new pairing codes. Update, restart and sign in again.",
    "このバージョンではコードを再生成できません。更新して再起動し、再ログインしてください。"
  ],
  "当前会话校验信息不完整，请重新读取私人状态。": [
    "Session validation is incomplete. Reload private status.",
    "セッションの検証情報が不完全です。個人状態を再取得してください。"
  ],
  "服务返回的配对信息不完整，请核对服务版本后重新生成。": [
    "Pairing information is incomplete. Check the service version and generate a new code.",
    "ペアリング情報が不完全です。サービスのバージョンを確認して再生成してください。"
  ],
  "等待配对响应超时，结果尚未确认。请检查秘书服务；本次不会自动重试。": [
    "Pairing timed out; the result is unconfirmed. Check the service. No automatic retry.",
    "ペアリングがタイムアウトし、結果は未確認です。サービスを確認してください。自動再試行しません。"
  ],
  "配对请求结果未确认，请检查个人服务。不会自动重复请求。": [
    "Pairing result is unconfirmed. Check the personal service. No automatic resubmission.",
    "ペアリング結果は未確認です。個人サービスを確認してください。自動再送はしません。"
  ],
  "记录私人工作，马上执行或按需定时": [
    "Record private tasks; run now or schedule",
    "個人作業を記録し、今すぐ実行または時刻指定"
  ],
  "围绕具体工作，与个人模型讨论": [
    "Discuss a task with your personal model",
    "個人モデルと具体的な作業を相談"
  ],
  "管理已确认的私人策略及关联授权": [
    "Manage confirmed private policies and linked grants",
    "確認済み個人ポリシーと関連する許可を管理"
  ],
  "整理草稿、模板与待办": [
    "Organize drafts, templates and to-dos",
    "下書き、テンプレート、ToDo を整理"
  ],
  "核对原请求回执，备份私人记录": [
    "Check original receipts; back up private records",
    "元の受領記録を確認し、個人記録をバックアップ"
  ],
  "跟进中": [
    "Following up",
    "フォロー中"
  ],
  "需重新确认": [
    "Needs reconfirmation",
    "再確認が必要"
  ],
  "待处理": [
    "Pending",
    "処理待ち"
  ],
  "对账中": [
    "Reconciling",
    "照合中"
  ],
  "已受理": [
    "Accepted",
    "受付済み"
  ],
  "处理中": [
    "Processing",
    "処理中"
  ],
  "结果待核对": [
    "Result needs checking",
    "結果の確認待ち"
  ],
  "服务返回的内容无法读取。请先刷新状态并核对原请求回执。": [
    "Cannot read the service response. Refresh status and check the original receipt.",
    "サービスの応答を読み取れません。状態を更新し、元の受領記録を確認してください。"
  ],
  "无法连接个人服务。请检查服务是否仍在运行；已提交的操作请先核对原回执。": [
    "Cannot connect to the personal service. Check that it is running. Check receipts for submitted actions.",
    "個人サービスに接続できません。起動状態を確認してください。送信済みの操作は先に元の受領記録を確認してください。"
  ],
  "操作未确认，请刷新状态并核对原请求回执。": [
    "Action unconfirmed. Refresh status and check the original receipt.",
    "操作は未確認です。状態を更新し、元の受領記録を確認してください。"
  ],
  "配对码无效、已使用或已过期。请核对本服务启动终端提供的配对码；失效时需重新启动个人服务。": [
    "Pairing code is invalid, used or expired. Check the code in the service terminal; restart the service if needed.",
    "コードが無効、使用済み、または期限切れです。起動ターミナルのコードを確認し、必要ならサービスを再起動してください。"
  ],
  "个人会话已失效，请使用本服务启动终端提供的配对码登录。配对码失效时需重新启动个人服务。": [
    "Session expired. Sign in with a code from the service terminal. Restart the service if that code expired.",
    "セッションの期限が切れました。サービスのターミナルのコードでログインしてください。コードも無効なら再起動が必要です。"
  ],
  "当前个人会话或校验令牌无效，请先刷新状态；若会话已过期，需重新配对登录。": [
    "Session or validation token is invalid. Refresh status; pair again if the session expired.",
    "セッションまたは検証トークンが無効です。状態を更新し、期限切れなら再ペアリングしてください。"
  ],
  "对话失败。请检查个人服务是否已配置 AIW_PERSONAL_MODEL_COMMAND，以及模型程序能否正常运行；更新并重启服务后可显示具体原因。": [
    "Chat failed. Check AIW_PERSONAL_MODEL_COMMAND and the model command. Update and restart the service for detailed errors.",
    "会話に失敗しました。AIW_PERSONAL_MODEL_COMMAND とモデルプログラムを確認してください。更新して再起動すると詳細な理由を表示できます。"
  ],
  "这次操作没有完成，当前运行的服务可能仍是旧版本。请重启个人服务并刷新页面；新版本会说明具体原因和处理方法。": [
    "Action incomplete. The service may be outdated. Restart it and refresh for the detailed reason and next steps.",
    "操作は未完了です。古いバージョンの可能性があります。再起動してページを更新すると理由と対処を確認できます。"
  ],
  "查看记录详情": [
    "View record details",
    "記録の詳細を表示"
  ],
  "这项工作已不在当前列表，请刷新后核对。": [
    "This task is no longer listed. Refresh to check.",
    "この作業は現在の一覧にありません。更新して確認してください。"
  ],
  "尚无本地日常处理步骤；平台工作请通过卡片中的平台入口查看。": [
    "No local processing steps recorded. Use the platform link on the card for platform work.",
    "ローカルの処理手順は未記録です。プラットフォームの作業はカード内のリンクから確認してください。"
  ],
  "日常对话，无需先建工作": [
    "Everyday chat; no task needed",
    "日常の会話・作業作成は不要"
  ],
  "保存已确认的策略，并关联平台已授予的精确能力。": [
    "Save confirmed policies and link exact capabilities granted by the platform.",
    "確認済みポリシーを保存し、プラットフォームの厳密な許可に紐づけます。"
  ],
  "到期": [
    "Expires",
    "有効期限"
  ],
  "在下方保存一条私人记录，之后可从这里选择编辑。": [
    "Save a private record below, then select it here to edit.",
    "下で個人記録を保存すると、ここから選んで編集できます。"
  ],
  "资料正在保存，请等待完成后再删除。": [
    "Record is being saved. Wait before deleting it.",
    "資料を保存中です。完了してから削除してください。"
  ],
  "草稿已删除，历史版本仍保留在私人备份中。": [
    "Draft deleted. Historical versions remain in the private backup.",
    "下書きを削除しました。過去のバージョンは個人バックアップに保持されます。"
  ],
  "删除已返回成功，但状态读取失败，请刷新核对。": [
    "Deletion succeeded, but status could not be loaded. Refresh to check.",
    "削除は成功しましたが、状態を取得できません。更新して確認してください。"
  ],
  "已终结的历史记录可在私人备份中核对。": [
    "Check completed historical records in the private backup.",
    "完了済みの履歴は個人バックアップで確認できます。"
  ],
  "私人服务记录的待处理请求": [
    "Pending requests recorded by the personal service",
    "個人サービスに記録された処理待ちリクエスト"
  ],
  "实际状态": [
    "Recorded state",
    "実際の状態"
  ],
  "回执详情": [
    "Receipt details",
    "受領記録の詳細"
  ],
  "查看原回执": [
    "View original receipt",
    "元の受領記録を表示"
  ],
  "状态读取于": [
    "Status read at",
    "状態取得時刻"
  ],
  "操作已返回结果，请刷新列表查看最新状态。": [
    "Action returned a result. Refresh the list for current status.",
    "操作の結果が返りました。一覧を更新して最新状態を確認してください。"
  ],
  "页面已更新，但运行中的个人服务仍是旧版本。请重启个人服务并刷新页面，新版本会显示具体错误原因。": [
    "Page updated, but the service is still an older version. Restart it and refresh for detailed errors.",
    "ページは更新済みですが、サービスは旧バージョンです。再起動してページを更新すると詳細なエラーを確認できます。"
  ],
  "正常在线排队无需反复确认；跨停机恢复或错过时间的工作会在这里提示。": [
    "Normal queued work needs no repeated confirmation. Downtime recovery and missed tasks appear here.",
    "通常の待機作業は繰り返し確認不要です。停止後の復旧や時刻を過ぎた作業はここに表示します。"
  ],
  "已重新提交执行。": [
    "Resubmitted for execution.",
    "再実行を要求しました。"
  ],
  "内容已变化，请重新生成本地预览。": [
    "Content changed. Generate a new local preview.",
    "内容が変わりました。ローカルプレビューを再生成してください。"
  ],
  "的 JSON 格式有误，请检查引号、逗号和括号。": [
    " has invalid JSON. Check quotes, commas and brackets.",
    " の JSON 形式が不正です。引用符、カンマ、括弧を確認してください。"
  ],
  "禁止范围": [
    "Denied scope",
    "拒否する範囲"
  ],
  "私人内容": [
    "Private content",
    "個人内容"
  ],
  "模型程序参数": [
    "Model command arguments",
    "モデルプログラムの引数"
  ],
  "个人服务已接受配对。": [
    "Personal service accepted the pairing.",
    "個人サービスがペアリングを受け付けました。"
  ],
  "操作已记录，状态已更新。": [
    "Action recorded; status updated.",
    "操作を記録し、状態を更新しました。"
  ],
  "操作已返回结果。": [
    "Action returned a result.",
    "操作の結果が返りました。"
  ],
  "已提交马上执行，进度和结果会自动更新；秘书暂停时需先点击“继续工作”。": [
    "Submitted to run now. Progress and results update automatically. If paused, select Resume work.",
    "即時実行を要求しました。進捗と結果は自動更新されます。停止中なら「作業を再開」を選んでください。"
  ],
  "已收到，秘书正在处理，回复会自动显示。": [
    "Received. The secretary is working; the reply will appear automatically.",
    "受け付けました。秘書が処理中です。返信は自動表示されます。"
  ],
  "设置已保存，可以直接聊天或安排工作。": [
    "Settings saved. You can chat or schedule work.",
    "設定を保存しました。会話や作業予定を設定できます。"
  ],
  "本地预览已生成，请核对精确内容后确认提交。": [
    "Local preview generated. Check the exact content before submitting.",
    "ローカルプレビューを生成しました。正確な内容を確認してから送信してください。"
  ],
  "目标提案：": [
    "Goal proposal: ",
    "目標の提案："
  ],
  "待核对": [
    "Needs checking",
    "確認待ち"
  ],
  "平台采纳前不会创建任务。": [
    "No tasks are created until the platform accepts the proposal.",
    "プラットフォームで採用されるまでタスクは作成されません。"
  ],
  "服务已返回结果，但状态读取失败。请先刷新状态、核对原回执，避免重复提交。": [
    "The service returned a result, but status could not be read. Refresh and check the original receipt before resubmitting.",
    "結果は返りましたが状態を取得できません。重複送信を避けるため、状態を更新し元の受領記録を確認してください。"
  ],
  "秘书已继续工作，安排会自动处理。": [
    "Secretary resumed. Schedules will be processed automatically.",
    "秘書を再開しました。予定は自動で処理されます。"
  ],
  "秘书已暂停，后续工具操作会停止；已完成操作保留。": [
    "Secretary paused. Further tool actions stop; completed actions are kept.",
    "秘書を一時停止しました。後続のツール操作は停止し、完了済みの操作は保持します。"
  ],
  "状态读取失败，请刷新查看秘书状态。": [
    "Status could not be loaded. Refresh to check the secretary.",
    "状態を取得できません。更新して秘書の状態を確認してください。"
  ],
  "模型连接正常。": [
    "Model connection is working.",
    "モデル接続は正常です。"
  ],
  "已连接当前已授权的专业平台；日常助理设置不受影响。": [
    "Connected to the authorized platform. Everyday assistant settings are unchanged.",
    "許可済みの専門プラットフォームに接続しました。日常アシスタントの設定には影響しません。"
  ],
  "已读取最新私人状态。": [
    "Latest private status loaded.",
    "最新の個人状態を取得しました。"
  ],
  "更新失败，保留当前详情。": [
    "Update failed. Current details are kept.",
    "更新に失敗しました。現在の詳細を保持します。"
  ],
  "个人会话已失效": [
    "Personal session expired",
    "個人セッションの期限切れ"
  ]
});
  Object.assign(workspaceCatalog, {
    '展开伙伴': ['Expand companion', 'パートナーを展開'],
    '收起伙伴': ['Collapse companion', 'パートナーを縮小'],
    '秘书头像': ['Secretary avatar', '秘書のアバター'],
    '秘书': ['Secretary', '秘書'],
    '移除后的工作可以恢复，也可以永久删除；永久删除时可选择保留已保存的记忆。删除工作不会删除结果文件或停止平台任务。': ['Removed work can be restored or permanently deleted. You can keep saved memories. Deleting work does not delete output files or stop platform tasks.', '削除済みの作業は復元または完全に削除できます。保存済みの記憶を残すこともできます。成果物ファイルの削除やプラットフォームのタスクの停止は行いません。'],
    '移除后的工作可恢复，也可永久删除。': ['Removed work can be restored or permanently deleted.', '削除済みの作業は復元または完全に削除できます。'],
    '保留已保存的记忆': ['Keep saved memories', '保存済みの記憶を残す'],
    '同时删除派生记忆': ['Also delete derived memories', '関連する記憶も削除'],
    '记忆处理': ['Memories', '記憶の扱い'],
    '永久删除工作': ['Permanently delete work', '作業を完全に削除'],
    '永久删除这项工作、对话及其派生记忆？结果文件、已有备份和平台回执会保留。': ['Permanently delete this work, its conversation and derived memories? Output files, existing backups and platform receipts will remain.', 'この作業、会話、関連する記憶を完全に削除しますか？成果物ファイル、既存のバックアップ、プラットフォームの処理記録は残ります。'],
    '永久删除这项工作及其对话，但保留已保存的记忆？结果文件、已有备份和平台回执会保留。': ['Permanently delete this work and its conversation but keep saved memories? Output files, existing backups and platform receipts will remain.', '保存済みの記憶を残して、この作業と会話を完全に削除しますか？成果物ファイル、既存のバックアップ、プラットフォームの処理記録は残ります。'],
    '工作已永久删除。': ['Work permanently deleted.', '作業を完全に削除しました。'],
    '工作已保存，并已选入安排列表。': ['Work saved and selected for scheduling.', '作業を保存し、実行予定の対象に選択しました。'],
    '删除已完成，状态读取失败，请刷新已删除列表核对。': ['Deletion completed, but status could not be refreshed. Refresh the trash to confirm.', '削除は完了しましたが、状態を更新できませんでした。削除済み一覧を更新して確認してください。'],
    '已保存记忆': ['Saved memories', '保存済みの記憶'],
    '保留记忆只保留已有条目，删除时不会自动生成新记忆。': ['Keeping memories preserves existing entries. Deletion does not generate new memories.', '残せるのは保存済みの記憶です。削除時に新しい記憶は生成しません。'],
    '直接说出需要帮助的事，秘书会判断是否建立工作。': ['Tell your secretary what you need. It will decide whether to create work.', '必要なことを話すと、秘書が作業の作成を判断します。'],
    '会话': ['Conversation', '会話'],
    '新对话': ['New conversation', '新しい会話'],
    '当前会话的私人对话': ['Private messages in this conversation', '現在の会話のプライベートメッセージ'],
    '写下问题或需要帮助的事…': ['Ask a question or describe what you need…', '質問や依頼を入力…'],
    '会话摘要与记忆': ['Conversation summary and memories', '会話の要約と記憶'],
    '早期消息默认收起，原文保留；摘要和相关记忆用于后续对话。': ['Earlier messages are collapsed and kept. Summaries and relevant memories provide context for later conversations.', '過去のメッセージは折りたたんで保存します。要約と関連する記憶は以後の会話に利用します。'],
    '更新会话摘要': ['Update conversation summary', '会話の要約を更新'],
    '删除对话时如何处理记忆': ['Memory handling when deleting a conversation', '会話を削除するときの記憶の扱い'],
    '删除对话，保留记忆': ['Delete conversation, keep memories', '会話を削除し記憶を残す'],
    '删除对话及其派生记忆': ['Delete conversation and its memories', '会話とそこから得た記憶を削除'],
    '删除对话': ['Delete conversation', '会話を削除'],
    '查看长期记忆': ['View long-term memories', '長期記憶を表示'],
    '正在生成摘要，原始消息仍然保留。': ['Creating a summary. Original messages are kept.', '要約を作成中です。元のメッセージは保持されます。'],
    '摘要已保存，可供后续对话参考。': ['Summary saved for future conversations.', '要約を保存しました。以後の会話で参照できます。'],
    '还没有会话摘要。长对话会自动整理，也可手动更新。': ['No summary yet. Long conversations are summarized automatically, or you can update manually.', '要約はまだありません。長い会話は自動で要約され、手動でも更新できます。'],
    '还没有长期记忆': ['No long-term memories yet', '長期記憶はまだありません'],
    '摘要只提取稳定偏好和已确认的长期事实。': ['Only stable preferences and confirmed long-term facts become memories.', '安定した好みと確認済みの長期的な事実だけを記憶します。'],
    '删除记忆': ['Delete memory', '記憶を削除'],
    '删除这条长期记忆？': ['Delete this long-term memory?', 'この長期記憶を削除しますか？'],
    '记忆已删除。': ['Memory deleted.', '記憶を削除しました。'],
    '早期消息已收起，原文仍然保留。': ['Earlier messages are collapsed. Original messages are kept.', '過去のメッセージは折りたたまれています。原文は保持されます。'],
    '正在加载早期消息…': ['Loading earlier messages…', '過去のメッセージを読み込み中…'],
    '重试加载消息': ['Retry loading messages', 'メッセージの読み込みを再試行'],
    '显示更早消息': ['Show earlier messages', '以前のメッセージを表示'],
    '收起早期消息': ['Collapse earlier messages', '過去のメッセージを折りたたむ'],
    '摘要已开始生成，完成后会自动显示。': ['Summary started. It will appear when ready.', '要約の作成を開始しました。完了すると自動で表示されます。'],
    '永久删除这段对话及其派生记忆？关联工作和已有备份会保留。': ['Permanently delete this conversation and its memories? Linked work and existing backups will remain.', 'この会話とそこから得た記憶を完全に削除しますか？関連する作業と既存のバックアップは残ります。'],
    '永久删除这段对话，但保留长期记忆？关联工作和已有备份会保留。': ['Permanently delete this conversation but keep memories? Linked work and existing backups will remain.', '長期記憶を残してこの会話を完全に削除しますか？関連する作業と既存のバックアップは残ります。'],
    '对话已删除。': ['Conversation deleted.', '会話を削除しました。'],
    '自动摘要未完成，原始消息仍然保留；可稍后重新生成摘要。': ['Automatic summary failed. Original messages are kept; you can retry later.', '自動要約が完了しませんでした。元のメッセージは保持され、後で再試行できます。'],
  });
  Object.assign(translations, { 'Appearance and language': '外观与语言' });
  Object.assign(japaneseTranslations, { 'Appearance and language': '外観と言語' });
  Object.assign(workspaceCatalog, {
    '专业平台连接与代理': ['Platform connection and delegation', 'プラットフォーム接続と代理権限'],
    '秘书连接与代理设置': ['Secretary connection and delegation', '秘書の接続と代理設定'],
    '在这里连接本机秘书，再确认它可以代理哪个计划、哪些动作以及有效期。': ['Connect your local secretary, then confirm its plans, actions and expiry.', 'ローカル秘書を接続し、代理する計画、操作、有効期限を確認します。'],
    '首次连接由向导处理身份和凭证。选择可代理的计划、动作、有效期和次数后，秘书会在范围内推进；超出范围时向你申请。': ['The guide handles identity and credentials. Choose plans, actions, expiry and usage limits. Your secretary asks when more permission is needed.', '接続ガイドがIDと認証情報を設定します。計画、操作、期限、回数を指定すると、秘書が範囲内で進め、追加の権限が必要な場合は確認します。'],
    '正在读取连接状态…': ['Reading connection status…', '接続状態を読み込み中…'],
    '连接一位新秘书': ['Connect a new secretary', '新しい秘書を接続'],
    '秘书': ['Secretary', '秘書'],
    '连接本机秘书': ['Connect local secretary', 'ローカル秘書を接続'],
    '打开秘书完成连接': ['Open secretary to connect', '秘書を開いて接続'],
    '检查连接状态': ['Check connection', '接続状態を確認'],
    '连接选项与备用连接码': ['Connection options and fallback code', '接続オプションと予備コード'],
    '本机秘书地址': ['Local secretary address', 'ローカル秘書のアドレス'],
    '备用连接码': ['Fallback connection code', '予備の接続コード'],
    '复制连接码': ['Copy connection code', '接続コードをコピー'],
    '页面会直接发送连接信息，无需复制启动命令或填写内部身份。浏览器拦截新窗口时，可复制连接码到秘书设置。': ['Connection details are sent directly. If the browser blocks the window, paste the connection code into secretary settings.', '接続情報は直接送信されます。ブラウザーが新しいウィンドウをブロックした場合は、秘書の設定に接続コードを貼り付けてください。'],
    '确认当前秘书进程，保留原授权': ['Confirm this secretary process, keep existing grants', '現在の秘書プロセスを確認し、既存の権限を維持'],
    '代理范围': ['Delegation scope', '代理範囲'],
    '核对代理范围': ['Review delegation scope', '代理範囲を確認'],
    '返回秘书继续工作': ['Return to secretary', '秘書に戻って続ける'],
    '先允许创建计划草稿，执行范围稍后确认': ['Allow plan drafts first; confirm execution later', 'まず計画案の作成を許可し、実行範囲は後で確認'],
    '查看所选计划内容': ['Review selected plan', '選択した計画を確認'],
    '查看完整计划': ['View full plan', '計画全体を表示'],
    '允许秘书代理的动作': ['Actions the secretary may perform', '秘書に代理を許可する操作'],
    '确认并推进计划': ['Confirm and advance plans', '計画を確定して進める'],
    '重试失败任务': ['Retry failed tasks', '失敗したタスクを再試行'],
    '回答工作问题': ['Answer task questions', '作業の質問に回答'],
    '批准范围内的审批': ['Approve within scope', '範囲内の申請を承認'],
    '拒绝范围内的审批': ['Reject within scope', '範囲内の申請を却下'],
    '停止计划': ['Stop plans', '計画を停止'],
    '授权有效期': ['Permission expiry', '権限の有効期限'],
    '每种动作累计最多执行次数': ['Maximum total uses per action', '操作ごとの累計上限回数'],
    '范围只适用于所选计划及其任务，不包含其他计划。新计划草稿不代表已授权修改仓库或执行命令；需要时秘书会向你申请。': ['Applies to this plan and its tasks. Draft creation does not grant repository edits or commands; your secretary requests those when needed.', '選択した計画とそのタスクに適用します。計画案の作成だけではリポジトリの変更やコマンド実行は許可されず、必要な場合に秘書が申請します。'],
    '核对并确认代理设置': ['Review and confirm delegation', '代理設定を確認して確定'],
    '配置此计划的执行范围': ['Configure execution scope', 'この計画の実行範囲を設定'],
    '尚未连接专业平台': ['Platform is not connected', 'プラットフォーム未接続'],
    '平台连接与进程授权已确认': ['Connection and process authorization confirmed', '接続とプロセス権限を確認済み'],
    '已连接，等待确认代理范围': ['Connected; delegation needs confirmation', '接続済み、代理範囲の確認待ち'],
    '平台地址': ['Platform address', 'プラットフォームのアドレス'],
    '打开连接与授权向导': ['Open connection and permission guide', '接続と権限のガイドを開く'],
    '检查连接与授权状态': ['Check connection and permissions', '接続と権限を確認'],
    '继续配置代理范围': ['Continue delegation setup', '代理範囲の設定を続ける'],
    '平台连接信息': ['Platform connection details', 'プラットフォーム接続情報'],
    '向导会自动填入；也可以粘贴备用连接码': ['Filled by the guide, or paste a fallback code', 'ガイドで自動入力、または予備コードを貼り付け'],
    '确认连接此平台': ['Confirm platform connection', 'この接続を確認'],
    '待确认代理范围': ['Delegation needs confirmation', '代理範囲の確認待ち'],
    '等待连接': ['Waiting for connection', '接続待ち'],
    '已建立连接，请核对下面的代理范围。': ['Connected. Review delegation below.', '接続しました。下の代理範囲を確認してください。'],
    '先连接本机秘书，再确认代理范围。': ['Connect your local secretary, then confirm delegation.', 'ローカル秘書を接続し、代理範囲を確認してください。'],
    '已准备连接信息。请在打开的秘书页面确认连接，然后回到这里检查状态。': ['Connection details are ready. Confirm in the secretary page, then check status here.', '接続情報を準備しました。開いた秘書画面で接続を確認し、ここで状態を確認してください。'],
    '代理设置已保存。返回秘书后，等待授权的原任务会继续。': ['Delegation saved. The waiting task can continue in your secretary.', '代理設定を保存しました。秘書に戻ると、権限待ちの元のタスクを続行できます。'],
    '代理设置已保存；此计划仍需确认仓库和工具执行范围。': ['Delegation saved; repository and tool execution scope still needs confirmation.', '代理設定を保存しました。リポジトリとツールの実行範囲は別途確認が必要です。'],
    '确认允许秘书在以下范围内代理？': ['Allow your secretary to act within this scope?', '次の範囲で秘書の代理を許可しますか？'],
    '创建计划草稿，具体执行另行确认': ['Create plan drafts; confirm execution separately', '計画案を作成し、実行は別途確認'],
    '请填写有效的授权期限和累计次数。': ['Enter a valid expiry and usage limit.', '有効な期限と累計回数を入力してください。'],
    '核对原申请后重试原设置': ['Review and retry the original request', '元の申請を確認して再試行'],
    '原申请已保留，请先检查当前状态。': ['The original request is saved. Check current status first.', '元の申請を保持しています。まず現在の状態を確認してください。'],
    '当前秘书进程已确认，可以返回秘书继续工作。': ['This process is confirmed. Return to your secretary.', '現在のプロセスを確認しました。秘書に戻って続行できます。'],
    '确认当前秘书进程继续使用已有代理范围？不会新增授权或重置累计次数。': ['Let this process use existing delegation? No grants or usage limits will be reset.', '現在のプロセスで既存の代理権限を使いますか？権限の追加や累計回数のリセットは行いません。'],
    '确认只重试原来的代理设置申请？': ['Retry only the original delegation request?', '元の代理設定申請のみ再試行しますか？'],
    '重新连接会轮换这位秘书的连接凭证。确认重新连接到当前电脑？': ['Reconnecting rotates this secretary credential. Reconnect to this computer?', '再接続すると秘書の認証情報が更新されます。このPCに再接続しますか？'],
    '连接码已复制，请粘贴到秘书设置，勿分享给其他人。': ['Code copied. Paste it in secretary settings and keep it private.', 'コピーしました。秘書の設定に貼り付け、他の人には共有しないでください。'],
    '无法复制，请在备用连接码输入框中手动复制。': ['Copy failed. Copy manually from the fallback code field.', 'コピーできません。予備コード欄から手動でコピーしてください。'],
    '登记结果未知，请先刷新并选择原秘书核对，不要重复登记。': ['Registration is uncertain. Refresh and check the original secretary before registering again.', '登録結果が不明です。再登録の前に更新して元の秘書を確認してください。'],
    '无法读取原权限范围，请刷新后再连接。': ['Cannot read existing scope. Refresh before reconnecting.', '既存の範囲を読み取れません。更新してから接続してください。'],
    '无法保留原申请，请使用可保存会话数据的浏览器后重试。': ['Cannot save the request. Use a browser with session storage enabled.', '申請を保存できません。セッション保存が有効なブラウザーを使用してください。'],
    '无法读取原设置申请，请先核对当前授权记录。': ['Cannot read the original request. Review current permission records.', '元の申請を読み取れません。現在の権限記録を確認してください。'],
    '当前身份与原设置申请不同，请切回原秘书核对。': ['Identity differs from the original request. Select the original secretary.', '元の申請とIDが異なります。元の秘書に切り替えて確認してください。'],
    '已读取当前授权记录。原申请结果仍需核对，请使用下方原申请按钮，不能换新申请重复授权。': ['Records loaded. Review the original request using the button below; do not create a duplicate grant.', '権限記録を読み込みました。下のボタンで元の申請を確認し、新規申請による重複付与は避けてください。'],
    '操作回执无法读取，请先检查当前状态。': ['Cannot read the receipt. Check current status.', '結果を読み取れません。現在の状態を確認してください。'],
    '操作未获确认，请检查登录和当前状态。': ['Operation unconfirmed. Check login and current status.', '操作を確認できません。ログインと現在の状態を確認してください。'],
    '请选择本机秘书的完整地址，例如 http://127.0.0.1:8766。': ['Enter the local secretary address, such as http://127.0.0.1:8766.', 'ローカル秘書のアドレス（例：http://127.0.0.1:8766）を入力してください。'],
    '本机地址无效，已保留默认地址。': ['Invalid local address. Keeping the default.', 'ローカルアドレスが無効なため、既定値を使用します。'],
    '秘书申请确认或授权': ['Secretary requests confirmation or permission', '秘書から確認・権限の申請'],
    '原任务需要核对': ['Original task needs review', '元のタスクの確認が必要'],
    '工作需要你补充信息': ['Task needs your input', '作業に追加情報が必要'],
    '查看并回答工作问题': ['View and answer task questions', '作業の質問を確認して回答'],
    '调整秘书的答复代理范围': ['Adjust delegated answer scope', '秘書の代理回答範囲を変更'],
    '原任务和上下文已保留，完成授权后会自动继续，不会重复提交已受理的任务。': ['The task and context are saved. After permission is confirmed it resumes without resubmitting accepted tasks.', '元のタスクと文脈を保持しています。権限確認後に再開し、受付済みのタスクは重複送信しません。'],
    '确认代理范围': ['Confirm delegation scope', '代理範囲を確認'],
    '连接专业平台': ['Connect platform', 'プラットフォームを接続'],
    '已完成授权，检查并继续': ['Permission granted; check and continue', '権限を付与済み、確認して続行'],
    '重新检查授权': ['Check permissions again', '権限を再確認'],
    '已确认，正在继续原任务。': ['Confirmed. Continuing the original task.', '確認しました。元のタスクを続行します。'],
    '关闭核对提示': ['Dismiss review notice', '確認通知を閉じる'],
    '拒绝这项申请': ['Decline this request', 'この申請を拒否'],
    '核对提示已关闭。': ['Review notice dismissed.', '確認通知を閉じました。'],
    '已拒绝，原任务不会继续。': ['Declined. The task will not continue.', '拒否しました。元のタスクは続行しません。']
  });
  Object.assign(workspaceCatalog, { '所选对象': ['Selected target', '選択した対象'] });
  Object.assign(workspaceCatalog, {
    '文件执行范围': ['File execution scope', 'ファイルの実行範囲'],
    '同时确认此计划的仓库与文件执行范围': ['Confirm repository and file scope for this plan', 'この計画のリポジトリとファイル範囲も確認'],
    '保留此计划已有的有效代理权限和累计次数': ['Keep this plan’s active grants and usage history', 'この計画の有効な代理権限と累計使用回数を維持'],
    '项目仓库': ['Project repository', 'プロジェクトのリポジトリ'],
    '请选择项目仓库': ['Choose a project repository', 'プロジェクトのリポジトリを選択'],
    '允许访问的文件或子目录': ['Allowed file or subdirectory', 'アクセスを許可するファイルまたはサブディレクトリ'],
    '例如 sudoku.htm；. 表示整个仓库': ['For example sudoku.htm; . means the whole repository', '例：sudoku.htm。「.」はリポジトリ全体'],
    '允许修改上述文件': ['Allow changes to these files', '上記ファイルの変更を許可'],
    '仅允许读取上述文件': ['Read access only', '上記ファイルの読み取りのみ許可'],
    '允许本地 Git 操作与结果提交': ['Allow local Git operations and result commits', 'ローカルGit操作と成果のコミットを許可'],
    '不允许 Git 操作': ['Git operations are not allowed', 'Git操作は許可しない'],
    '固定 Git 基线': ['Fixed Git baseline', '固定したGit基準コミット'],
    '管理项目仓库': ['Manage project repositories', 'プロジェクトのリポジトリを管理'],
    '确认代理与文件执行范围': ['Confirm delegation and file scope', '代理権限とファイル実行範囲を確認'],
    '配置计划与文件范围': ['Configure plan and file scope', '計画とファイル範囲を設定'],
    '选择计划后可同时确认文件执行范围。': ['Choose a plan to confirm its file scope.', '計画を選択するとファイル実行範囲も確認できます。'],
    '此计划缺少文件执行范围，保存时会一并确认。': ['This plan has no file execution scope; confirm it when saving.', 'この計画のファイル実行範囲は未設定です。保存時に一緒に確認します。'],
    '此计划已有执行范围。需要补充基线或修改范围时，勾选后重新核对。': ['A scope already exists. Select the checkbox to add a baseline or change the scope.', '既存の実行範囲があります。基準コミットの追加や範囲の変更はチェックして再確認してください。'],
    '此计划的执行已暂停或撤销，请先在执行控制中核对。': ['Execution is paused or revoked. Review execution controls first.', 'この計画の実行は一時停止または取り消されています。先に実行制御を確認してください。'],
    '尚无已登记仓库，请先在项目仓库页面添加项目；不会自行创建 Git 仓库。': ['No repository is registered. Add the project on the repository page; Git is not initialized automatically.', '登録済みのリポジトリがありません。プロジェクトを追加してください。Gitリポジトリは自動作成しません。'],
    '请选择项目仓库和允许访问的文件范围。': ['Choose a repository and allowed file scope.', 'リポジトリとアクセス可能なファイル範囲を選択してください。'],
    '基线会从所选仓库自动读取，并在确认框中展示完整提交号。文件范围相对于该仓库；不会自动初始化 Git、创建首次提交或授权任意命令。': ['The baseline is read from the repository and its full commit is shown for confirmation. File paths are relative to the repository. Git initialization, initial commits and arbitrary commands are not included.', '選択したリポジトリから基準コミットを読み取り、確認画面に完全なコミットIDを表示します。ファイル範囲はリポジトリからの相対パスです。Gitの初期化、初回コミット、任意コマンドは含みません。'],
    '不包含任意命令执行；更新将替换此计划原有的执行范围。': ['Arbitrary commands are excluded. Saving replaces this plan’s previous execution scope.', '任意コマンドの実行は含みません。更新するとこの計画の既存実行範囲を置き換えます。']
  });
  Object.assign(workspaceCatalog, { '专业任务从已提交内容开始，不包含未跟踪文件。所选文件存在未提交修改时，需要先保存为 Git 提交。': ['Professional tasks start from committed content and exclude untracked files. Commit changes to the selected files before continuing.', '専門タスクはコミット済みの内容から開始し、未追跡ファイルは含みません。対象ファイルの変更は先にGitコミットとして保存してください。'] });
  Object.assign(workspaceCatalog, { '后台处理发生异常，已停止本地平台操作；这不表示平台撤销了进程授权。': ['Background processing failed and local platform actions were stopped. This does not mean the platform revoked this process authorization.', 'バックグラウンド処理でエラーが発生し、ローカルの操作を停止しました。プラットフォームのプロセス権限が取り消されたことを意味するものではありません。'] });
  Object.assign(workspaceCatalog, {
    '代理设置和文件执行范围已保存。请返回秘书检查并继续。': ['Delegation settings and file execution scope have been saved. Return to the secretary to check and continue.', '代理設定とファイル実行範囲を保存しました。秘書に戻り、確認して続行してください。'],
    '前往保存授权': ['Go to save permissions', '権限の保存へ移動'],
    '正在准备或保存授权，请稍候。': ['Preparing or saving permissions. Please wait.', '権限を準備または保存しています。お待ちください。'],
    '正在读取授权数据；加载失败的原因会显示在这里。': ['Loading permission data. Loading errors appear here.', '権限情報を読み込み中です。読み込みエラーはここに表示します。'],
    '原授权申请的结果尚未确认，请先核对或重试原申请。': ['The original request is unconfirmed. Check or retry that request first.', '元の権限申請の結果は未確認です。先に確認するか、元の申請を再試行してください。'],
    '请先完成秘书连接，再保存授权。': ['Connect the secretary before saving permissions.', '秘書を接続してから権限を保存してください。'],
    '请选择已连接的秘书，建立当前进程会话后再保存。': ['Choose a connected secretary and establish its process session before saving.', '接続済みの秘書を選び、現在のプロセスを接続してから保存してください。'],
    '将同时确认所选计划的代理权限、仓库、文件范围和 Git 基线。': ['Confirm delegation, repository, file scope and Git baseline together.', '選択した計画の代理権限、リポジトリ、ファイル範囲、Git基準をまとめて確認します。'],
    '本次只确认所选代理设置；文件执行范围未勾选。': ['Only delegation will be confirmed; file execution scope is not selected.', '今回の確認は代理設定のみです。ファイル実行範囲は選択されていません。']
  });
  Object.assign(workspaceCatalog, {
    '秘书连接与授权': ['Secretary connection and permissions', '秘書の接続と権限'],
    '秘书连接与授权 · AIW:DE': ['Secretary connection and permissions · AIW:DE', '秘書の接続と権限 · AIW:DE'],
    '连接秘书，选择计划并确认文件执行范围': ['Connect the secretary, choose a plan and confirm file execution scope', '秘書を接続し、計画とファイル実行範囲を確認'],
    '秘书连接与授权设置': ['Secretary connection and permission settings', '秘書の接続と権限の設定'],
    '打开平台高级管理': ['Open advanced platform management', 'プラットフォームの詳細管理を開く'],
    '连接秘书、选择计划和确认文件范围已移到独立页面。': ['Secretary connection, plan selection and file scope confirmation now have a separate page.', '秘書の接続、計画選択、ファイル範囲の確認は専用ページに移動しました。'],
    '打开秘书连接与授权': ['Open secretary connection and permissions', '秘書の接続と権限を開く']
  });
  Object.assign(workspaceCatalog, {
    'Agent 工厂设置': ['Agent Factory settings', 'Agent Factory 設定'],
    'Agent 工厂设置 · AIW:DE': ['Agent Factory settings · AIW:DE', 'Agent Factory 設定 · AIW:DE'],
    '统一选择模型，自动准备 Worker': ['Choose a default model and prepare workers automatically', '共通モデルを選び、Workerを自動準備'],
    '先复用兼容的在线 Worker，缺少时再启动本机 Worker。外部 Worker 仍可通过原协议接入。': ['Reuse a compatible online worker first, then start a local worker if needed. External workers can still use the existing protocol.', '互換性のあるオンラインWorkerを優先し、必要な場合にローカルWorkerを起動します。外部Workerも既存プロトコルで接続できます。'],
    '模型运行时': ['Model runtime', 'モデルランタイム'],
    '默认模型': ['Default model', '既定モデル'],
    '填写已可用的模型名称': ['Enter an available model name', '利用可能なモデル名を入力'],
    '推理强度': ['Reasoning effort', '推論強度'],
    '缺少兼容 Worker 时自动启动本机 Worker': ['Start a local worker when no compatible worker is available', '互換Workerがない場合はローカルWorkerを自動起動'],
    '本机运行环境（高级）': ['Local runtime environment (advanced)', 'ローカル実行環境（詳細）'],
    'Python 程序': ['Python executable', 'Python 実行ファイル'],
    'python 或已安装程序的完整路径': ['python or the full path to an installed executable', 'python またはインストール済み実行ファイルのフルパス'],
    'Codex 程序（可选）': ['Codex executable (optional)', 'Codex 実行ファイル（任意）'],
    '默认从 PATH 查找': ['Use PATH by default', '既定では PATH から検索'],
    '独立 Codex 环境（可选）': ['Separate Codex environment (optional)', '独立したCodex環境（任意）'],
    '默认复用已有 Codex 登录，无需另建目录': ['Reuse the existing Codex login by default; no separate directory is needed', '既定では既存のCodexログインを使用します。別ディレクトリは不要です'],
    '本机 Worker 使用已安装的 Python。Codex 默认复用已有登录，由受控主机工具执行文件操作；只有需要独立账号环境时才指定目录。OpenAI API 使用平台进程已有的 API 凭证。工厂不复制凭证或自动安装依赖。': ['Local workers use installed Python. Codex reuses the existing login and performs file actions through guarded host tools. Specify a directory only for a separate account environment. OpenAI API uses the platform process credentials. The factory does not copy credentials or install dependencies.', 'ローカルWorkerは既存のPythonを使用します。Codexは既存のログインと制御されたホストツールを使用します。別アカウント環境が必要な場合のみディレクトリを指定してください。OpenAI APIはプラットフォームの認証情報を使用します。認証情報の複製や依存関係の自動インストールは行いません。'],
    '留空使用平台已有的 Worker 运行环境': ['Leave blank to use the platform worker environment', '空欄の場合は既存のWorker実行環境を使用'],
    '本机 Worker 使用已安装的 Python 和运行时依赖。Codex 专业执行沿用受控运行环境的登录和权限配置；OpenAI API 使用平台进程已有的 API 凭证。工厂不复制秘书凭证或自动安装依赖。': ['Local workers use installed Python and runtime dependencies. Codex execution uses the guarded environment login and permissions. OpenAI API uses credentials already available to the platform process. The factory does not copy secretary credentials or install dependencies.', 'ローカルWorkerは既存のPythonと依存関係を使用します。Codexは制御対象環境のログインと権限を、OpenAI APIはプラットフォーム環境の認証情報を使用します。秘書の認証情報の複製や依存関係の自動インストールは行いません。'],
    '这里只设置默认模型和启动方式。计划、文件、命令及预算权限仍由原授权决定。': ['These settings choose the default model and launch mode. Existing grants still control plans, files, commands and budgets.', 'ここでは既定モデルと起動方式を設定します。計画、ファイル、コマンド、予算の権限は既存の許可に従います。'],
    '保存工厂设置': ['Save factory settings', 'Factory設定を保存'],
    '本机 Worker 状态': ['Local worker status', 'ローカルWorkerの状態'],
    '刷新状态': ['Refresh status', '状態を更新'],
    '接入外部 Worker': ['Connect external workers', '外部Workerを接続'],
    '外部 Worker 使用注册、心跳、领取任务和提交结果协议，无需由工厂启动。执行工厂任务时，还需声明匹配的运行时、仓库与配置执行能力。': ['External workers register, send heartbeats, claim tasks and submit results without being launched by the factory. Factory tasks also require matching runtime, repository and configuration execution capabilities.', '外部Workerは登録、ハートビート、タスク取得、結果提出のプロトコルを使用し、Factoryによる起動は不要です。Factoryのタスクには、対応するランタイム、リポジトリ、構成実行能力の宣言も必要です。'],
    '查看 Worker': ['View workers', 'Workerを表示'],
    '工厂设置未保存，请检查登录和管理员权限。': ['Factory settings were not saved. Check your login and administrator role.', 'Factory設定を保存できません。ログインと管理者権限を確認してください。'],
    '工厂设置已载入。': ['Factory settings loaded.', 'Factory設定を読み込みました。'],
    '请选择默认模型并保存一次，后续任务由工厂自动准备 Worker。': ['Choose and save a default model once. The factory prepares workers for later tasks.', '既定モデルを一度選んで保存してください。以降のタスクのWorkerはFactoryが準備します。'],
    '尚无平台启动的本机 Worker。有任务需要时才启动。': ['No local worker has been launched by the platform. Workers start when a task needs them.', 'プラットフォームが起動したローカルWorkerはありません。タスクで必要になった時に起動します。'],
    '保存默认模型和本机 Worker 自动启动设置？': ['Save the default model and automatic local worker settings?', '既定モデルとローカルWorkerの自動起動設定を保存しますか？'],
    '工厂设置已保存，等待授权的任务会自动重新检查。': ['Factory settings saved. Tasks waiting for authorization will check again automatically.', 'Factory設定を保存しました。権限待ちのタスクは自動で再確認します。']
  });
  Object.assign(translations, {
  "DELEGATED_RUNNING": "委派工作执行中",
  "DELEGATED_WAITING": "委派工作等待中",
  "DELEGATED_COMPLETED": "委派工作已完成",
  "DELEGATED_NEEDS_ATTENTION": "委派工作需要处理",
  "Original graph state": "原步骤图状态",
  "Delegated execution history": "委派执行记录",
  "Factory preparation history": "工厂准备记录",
  "Actual Worker execution": "Worker 实际执行",
  "Preparation": "准备请求",
  "Generation": "准备代次",
  "Recorded verdict": "原始记录判定",
  "Base revision": "基线提交",
  "Result branch": "结果分支",
  "Result workspace": "结果工作区",
  "Evidence reference": "证据位置",
  "Checks performed": "已执行的检查",
  "Checks not performed": "未执行的检查",
  "Blockers": "阻塞原因",
  "No tool operations recorded.": "没有已记录的工具操作。",
  "Only the latest 500 tool operations are shown.": "仅显示最近 500 条工具操作。",
  "Factory tasks execute independently of the original graph. Completed Worker execution does not imply independent review or delivery to the original directory.": "工厂任务独立于原步骤图执行。Worker 完成不代表已通过独立审核，也不代表结果已同步到原目录。",
  "Newest records first. Expanded records stay stable while you read; collapse them to receive updates. Tool records show audited operations, not private model reasoning.": "最新记录在前。展开阅读时保持内容稳定，收起后更新。工具记录展示已审计操作，不包含模型的私有推理。",
  "Follow the factory execution history below. The original graph has not been started.": "请查看工厂执行记录。原步骤图未启动。",
  "Source records who proposed the graph, not approval. Draft graph tasks and delegated factory tasks are tracked separately.": "来源记录步骤图的提出者，不代表批准。草案步骤与委派工厂任务分别记录。"
});
  Object.assign(japaneseTranslations, {
  "DELEGATED_RUNNING": "委任作業を実行中",
  "DELEGATED_WAITING": "委任作業の待機中",
  "DELEGATED_COMPLETED": "委任作業は完了",
  "DELEGATED_NEEDS_ATTENTION": "委任作業に対応が必要",
  "Original graph state": "元のグラフの状態",
  "Delegated execution history": "委任実行の履歴",
  "Factory preparation history": "Factory準備履歴",
  "Actual Worker execution": "Workerの実行履歴",
  "Preparation": "準備リクエスト",
  "Generation": "準備世代",
  "Recorded verdict": "記録された判定",
  "Base revision": "基準コミット",
  "Result branch": "結果ブランチ",
  "Result workspace": "結果ワークスペース",
  "Evidence reference": "証跡参照",
  "Checks performed": "実施した確認",
  "Checks not performed": "未実施の確認",
  "Blockers": "阻害要因",
  "No tool operations recorded.": "記録されたツール操作はありません。",
  "Only the latest 500 tool operations are shown.": "最新の500件のツール操作のみ表示します。",
  "Factory tasks execute independently of the original graph. Completed Worker execution does not imply independent review or delivery to the original directory.": "Factoryタスクは元のグラフと別に実行されます。Workerの完了は独立レビューや元のディレクトリへの反映を意味しません。",
  "Newest records first. Expanded records stay stable while you read; collapse them to receive updates. Tool records show audited operations, not private model reasoning.": "新しい記録が先頭です。展開中は内容を保持し、閉じると更新します。監査済み操作を表示し、モデルの非公開推論は含みません。",
  "Follow the factory execution history below. The original graph has not been started.": "Factory実行履歴を確認してください。元のグラフは未開始です。",
  "Source records who proposed the graph, not approval. Draft graph tasks and delegated factory tasks are tracked separately.": "出典は提案者を示し、承認を意味しません。草案とFactory委任タスクは別々に記録されます。"
});
  const attrNames = ['placeholder', 'aria-label', 'title', 'alt', 'data-confirm'];
  const originals = new WeakMap();
  const rendered = new WeakMap();
  const attrOriginals = new WeakMap();
  const localeKey = 'aiw-locale';
  const normalizeLocale = value => ['en', 'zh', 'ja'].includes(value) ? value : 'en';
  let locale = 'en';
  try { locale = normalizeLocale(window.aiwPreferences?.get().language || localStorage.getItem(localeKey)
    || (document.getElementById('personal-app') ? navigator.language.split('-')[0] : 'en')); } catch (_) { /* Use English for this page. */ }

  const consoleLookup = new Map();
  for (const [en, zh, ja] of window.aiwConsoleCatalog || []) {
    const entry = { en, zh, ja };
    for (const source of [en, zh, ja]) consoleLookup.set(source, entry);
  }
  const translateText = value => {
    if (consoleLookup.has(value)) return consoleLookup.get(value)[locale];
    if (value.endsWith(' · AIW:DE')) return translateText(value.slice(0, -9)) + ' · AIW:DE';
    if (locale !== 'zh') {
      for (const [pattern, en, ja] of window.aiwConsolePatterns || []) {
        if (pattern.test(value)) return value.replace(pattern, locale === 'ja' ? ja : en);
      }
      // Translate known UI field prefixes, preserving task IDs, names and
      // other supplied values verbatim. Raw records remain excluded below.
      const prefix = value.match(/^([^：]+：)([\s\S]+)$/);
      if (prefix && consoleLookup.has(prefix[1])) return consoleLookup.get(prefix[1])[locale] + prefix[2];
    }
    if (Object.hasOwn(workspaceCatalog, value)) return locale === 'zh' ? value : workspaceCatalog[value][locale === 'ja' ? 1 : 0];
    // Translate changing counts and timestamps without translating user content.
    const dynamic = [
      [/^版本 (\d+)$/, ['Version $1', 'バージョン $1']],
      [/^待你确认（(\d+)）$/, ['Needs confirmation ($1)', '確認待ち（$1）']],
      [/^报告更新于 (.+)$/, ['Report updated at $1', 'レポート更新日時 $1']],
      [/^状态读取于 (.+)$/, ['Status read at $1', '状態取得時刻 $1']],
      [/^你 · (.+)$/, ['You · $1', 'あなた · $1']],
      [/^个人模型 · (.+)$/, ['Personal model · $1', '個人モデル · $1']],
      [/^配对请求未确认（(\d+)），请核对个人服务状态。$/, ['Pairing request unconfirmed ($1). Check the personal service.', 'ペアリング要求は未確認です（$1）。個人サービスを確認してください。']],
      [/^操作未确认（(\d+)），请刷新状态并核对原请求回执。$/, ['Action unconfirmed ($1). Refresh status and check the original receipt.', '操作は未確認です（$1）。状態を更新し、元の受領記録を確認してください。']],
      [/^(.+)的 JSON 格式有误，请检查引号、逗号和括号。$/, ['$1 has invalid JSON. Check quotes, commas and brackets.', '$1 の JSON 形式が不正です。引用符、カンマ、括弧を確認してください。']],
      [/^使用本机时区：(.+)。手动选择后将保持该风格，选择“自动”恢复定时切换。$/, ['Local time zone: $1. A manual choice stays active; choose Auto to resume scheduled switching.', '端末のタイムゾーン：$1。手動選択を維持します。「自動」で時刻による切替を再開します。']],
    ];
    if (locale !== 'zh') for (const [pattern, options] of dynamic) if (pattern.test(value)) return value.replace(pattern, options[locale === 'ja' ? 1 : 0]);
    if (locale === 'en') return value;
    const dictionary = locale === 'ja' ? japaneseTranslations : translations;
    if (Object.hasOwn(dictionary, value)) return dictionary[value];
    if (locale === 'ja') {
      return value
        .replace(/^Confirm (\d+) selected steps at version (\d+)\? This creates Worker tasks\.$/, 'バージョン $2 の選択した $1 手順を確定しますか？Worker タスクが作成されます。')
        .replace(/^Skip ahead to step (\d+)\? Existing unfinished work will be stopped or cancelled\.$/, '手順 $1 に進みますか？既存の未完了の作業は停止またはキャンセルされます。')
        .replace(/^Step (\d+)$/, '手順 $1')
        .replace(/^Refresh in (\d+)s$/, '$1秒後に更新')
        .replace(/^Showing (\d+) of (\d+) registered workers · detail limit 100$/, '登録 Worker $2 件中 $1 件を表示 · 詳細上限 100')
        .replace(/^Showing (\d+) of (\d+) active leases · first 100 in this snapshot$/, '有効リース $2 件中 $1 件を表示 · このスナップショットの先頭100件')
        .replace(/^Showing (\d+) of (\d+) READY tasks · first 100 in this snapshot$/, 'READY タスク $2 件中 $1 件を表示 · このスナップショットの先頭100件');
    }
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
    if (node.parentElement?.closest('[data-i18n-skip],script,style,textarea,pre,code')) return;
    if (node.nodeType === Node.TEXT_NODE) {
      if (!originals.has(node) || (rendered.has(node) && rendered.get(node) !== node.nodeValue)) originals.set(node, node.nodeValue);
      const original = originals.get(node);
      const leading = original.match(/^\s*/)?.[0] || '';
      const trailing = original.match(/\s*$/)?.[0] || '';
      const core = original.slice(leading.length, original.length - trailing.length || undefined);
      const translated = translateText(core);
      rendered.set(node, leading + translated + trailing);
      if (node.nodeValue !== leading + translated + trailing) node.nodeValue = leading + translated + trailing;
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return;
    if (node.matches('[data-i18n-skip],script,style,textarea,pre,code')) return;
    for (const name of attrNames) {
      if (!node.hasAttribute(name)) continue;
      let saved = attrOriginals.get(node);
      if (!saved) { saved = {}; attrOriginals.set(node, saved); }
      if (!(name in saved) || node.getAttribute(name) !== saved[name].rendered) saved[name] = { original: node.getAttribute(name) };
      const original = saved[name].original;
      const translated = translateText(original);
      saved[name].rendered = translated;
      if (node.getAttribute(name) !== translated) node.setAttribute(name, translated);
    }
    for (const child of node.childNodes) visit(child);
  };
  const apply = () => {
    const title = document.querySelector('title');
    if (title && !attrOriginals.has(title)) attrOriginals.set(title, { text: title.textContent });
    if (title && !('text' in attrOriginals.get(title))) attrOriginals.get(title).text = title.textContent;
    document.documentElement.lang = locale === 'zh' ? 'zh-CN' : locale;
    visit(document.documentElement);
    const selector = document.querySelector('#console-locale');
    if (selector) selector.value = locale;
    if (title) {
      const saved = attrOriginals.get(title);
      title.textContent = translateText(saved.text);
    }
  };
  const notify = () => window.dispatchEvent(new CustomEvent('aiw:locale-changed', { detail: { locale } }));
  const setLocale = value => {
    const next = normalizeLocale(value);
    if (window.aiwPreferences) { window.aiwPreferences.update({ language: next }); return; }
    locale = next;
    try { localStorage.setItem(localeKey, locale); } catch (_) { /* Keep this page's language. */ }
    apply(); notify();
  };
  let initialized = false;
  window.aiwI18n = Object.freeze({ get locale() { return locale; }, get ready() { return initialized; }, t: translateText, setLocale });
  document.addEventListener('change', event => {
    if (event.target.matches('#console-locale') && !window.aiwPreferences) setLocale(event.target.value);
  });
  window.addEventListener('aiw:preferences-changed', event => { locale = normalizeLocale(event.detail?.language); apply(); notify(); });
  window.addEventListener('storage', event => {
    if (event.key === localeKey && !window.aiwPreferences) { locale = normalizeLocale(event.newValue); apply(); notify(); }
  });
  apply();
  new MutationObserver(records => records.forEach(record => {
    if (record.type === 'childList') record.addedNodes.forEach(visit);
    else if (record.type === 'characterData') visit(record.target);
    else if (record.type === 'attributes') visit(record.target);
  })).observe(document.documentElement, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: attrNames });
  initialized = true;
  window.dispatchEvent(new Event('aiw:i18n-ready'));
})();
