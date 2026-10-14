# 公共运行时输入契约

本文件与 `index.d.mts` 定义调用入口；行为要求见 [Worker 公共执行契约](../../WORKER_RUNTIME_CONTRACT.md)。调用者必须将正文与元数据分开，不能通过额外字段扩展常规日志。

## EvidenceRecorder

任务交付物由独立的 outputs 协议保存在持久工作区 `.aiw`，不受本节证据的 30 天清理影响。
领取响应的可选 `outputStore` 声明能力和固定输入版本；Worker 在工作区清理及 complete 前
收集、登记报告和声明附件。旧服务器没有该字段时保持原完成协议。
`complete` 仅发送既有 verdict/summary，不发送本地 outputFiles 声明。
详细协议与文档入库过程见 [任务产出](../../../docs/current-zh/TASK-OUTPUTS.md)。

`.aiw` 是运行时管理的保留目录，根目录只读快照和 Git 自动暂存排除它；模型不得修改它。
文档入库额外比较目标文件 SHA-256 与来源，并校验目标及其父目录以外的项目文件未改变。

`EvidenceRecorder.open(root, identity, { maxBytes?, forbiddenRoots? })` 在工作区外创建专属日志。默认总容量 4 MiB，包括 owner、事件及终态标记；终态后的拥有目录保留 30 天。字段在目录创建前校验，未知字段拒绝，不静默过滤后宣称保存成功。

新建子目录使用 `<UTC datetime>_<UUID>`，例如 `2026-10-14T06-30-00.123Z_550e8400-e29b-41d4-a716-446655440000`，日期时间精确到毫秒，名称可按创建时间排序且适用于 Windows 路径。旧 UUID 目录保持原名；保留清理仍依据 owner/terminal 标记及终态时间，不解析目录名。

身份推荐包含 `taskId`、`attemptId`、`attemptNo`、`role`、`runtime`、`versions`；公共 attempt 入口使用控制平面领取的值。通用 Recorder 允许只提交其中所需字段；`attemptNo` 是控制平面已有的序号，不代替 `attemptId`。

允许的字段如下；`record(event, details)` 使用同一清单。

| 用途 | 字段 |
|---|---|
| 身份与事件 | taskId, attemptId, attemptNo, role, runtime, versions, event, seq, time |
| 状态 | status, code, category, phase, reason, complete, truncated, mode, sideEffect, reportStatus, verdict, capability, unknown |
| 调用与来源 | callId, parentCallId, tool, operation, sourceId, sourceIds, path, digest |
| 计数与预算 | bytes, count, inputBytes, outputBytes, inputTokens, outputTokens, cachedTokens, usageKnown, usageSource, durationMs, maxBytes, maxTurns, deadlineMs, warningMs, summaryCalls, retentionDays |
| 模型与工作区 | model, effort, resultRevision, publicationRef, baseRevision, workspaceKey, readOnly, processTree, revision |

普通值为 null、布尔、有限数值、长度不超过 4096 的字符串，或不超过 256 项、每项长度不超过 4096 的字符串数组。`versions` 另外接受版本名到短字符串的对象，版本值长度小于 128；不用于传任意嵌套内容。undefined 被省略。

禁止提示词、源码、原始错误正文、密钥、leaseToken 等字段。未知字段返回 `EVIDENCE_FIELD_FORBIDDEN`，不合规值返回 `EVIDENCE_VALUE_INVALID`；记录或关键保存失败后 Recorder 保持不健康，`assertHealthy()` 及后续成功校验不能绕过该状态。调用者应提交固定错误码或内容摘要。

## AttemptRunner

构造参数为 `timeoutMs`（默认0，关闭硬期限）、`warningMs`（默认900000，仅提醒）、
`onWarning(durationMs)`、`parentSignal`、`settleTimeoutMs`（默认10000）及 `recorder`。
硬期限为非负整数，提醒和清理期限为正整数；硬期限及提醒期限不得超过2147483647ms。
提醒不会取消 signal，会记录一次 `attempt-duration-warning`（durationMs、warningMs及固定码）。
Worker 中 `AIW_WORKER_ATTEMPT_TIMEOUT_MS` 决定提醒，`AIW_WORKER_ATTEMPT_HARD_TIMEOUT_MS` 决定硬期限。
支持在构造时提供 Recorder，或在第一次 `step` 前赋给 `attempt.recorder`，便于先确定 attempt 专属目录。

`initializeEvidence(root, identity, options?)` 只初始化 EvidenceRecorder，其 I/O 也登记为活动操作并受整体期限和宽限约束。`step(phase, operation)` 必须有健康的关键证据入口；未绑定 Recorder 时以 `ATTEMPT_EVIDENCE_REQUIRED` 拒绝，回调不执行。阶段日志与回调共同登记、计时，在记录前及回调启动前检查取消。回调通过 `attempt.signal` 观察取消，Runner 等待活动 Promise 的实际终态；宽限到期后返回资源不确定，不能把后台 Promise 当作已终止。

`cancel(reason)` 中止 signal，后续阶段拒绝执行。整体 deadline 和 tool-timeout 在内部 RuntimeFault 中保留 TIMEOUT 类别；`failureCategory` 向控制平面映射为既有 TOOL_ERROR，不增加 CP 枚举。其他取消沿用 TOOL_ERROR。`checkSignal` 保留公共 RuntimeFault 原因，不记录外部异常正文。`settled(timeoutMs?)` 等待已登记活动，期限必须是正整数，默认使用构造时的宽限，到期返回 false 并设置 `resourceUncertain`。`dispose()` 只移除提醒和硬期限计时器，不能替代取消、等待资源终态或 WorkspaceSession 清理。

## ResourceCoordinator

`forWorkspace(cwd)` 按真实路径共享协调器；`run(request, signal, operation)` 覆盖活动操作的整个 Promise。普通已结束的成功或失败释放租约，取消排队只移除未启动的请求。

若操作返回 PROCESS_TREE_UNCERTAIN 或 RESOURCE_TERMINATION_UNCERTAIN，调用 Promise 的拒绝不代表子进程终止。协调器保留该活动租约及自身引用，拒绝排队请求和后续操作，`settled()` 不回报已结束。它不提供自动解锁入口；主机停止领取，操作人员确认遗留进程终止并对账后再重启。工作区删除也取得排他租约并遵守独立清理期限；未知终止时保留工作区，成功请求不能绕过这个状态。

`runCommand` 取消后等待原进程关闭和进程树终止结果；最多等待 5000ms。无法确认时抛出 PROCESS_TREE_UNCERTAIN 并解除自身管道等待，不能把这次返回当作释放资源或删除工作区的依据。Windows 的 taskkill 辅助进程也有同样的有界等待；该策略不证明逃逸子进程已经结束，真实平台能力仍由准入验证约束。

## FD-016 Profile 结果接入

adapter.profile 固定启动角色并校验 EXTERNAL_WORK 与精确 primarySkill skillRef；共享 attempt 在模型与工作区业务动作前完成校验和可信技能冻结。adapter.report 接受 profile.resultKind（REPORT/VERDICT/WORKSPACE），不接受旧 TaskType 分派。写角色必须有固定 baseRevision 工作区，审查基准等于输入结果 revision；只读收尾不 stage/commit/push。

## 公共 attempt 与 slot

`runClaimedAttempt(cfg, client, task, adapter)` 返回 `{ status, sessionId, workspaceResult?, evidence?, resourceUncertain }`。`resourceUncertain` 为明确布尔值，覆盖 finally 的最终判断；调用者不能用 failed/completed 字符串代替资源状态。`adapter.settled(timeoutMs?)` 必须说明活动 I/O 是否已结束，false 或拒绝按未知处理，未提供钩子仍依赖 Runner 登记的整个执行 Promise。

元数据初始化、准备/只读基线、模型、报告验证、发布与成功上报只在显式配置硬期限时受整体期限约束。取消后的操作宽限和随后独立清理窗口分别有界；catch/finally 的保存、失败上报、资源等待及清理共用一个 cleanupTimeoutMs 窗口。窗口耗尽后不启动新的清理 I/O；未能确认的资源和工作区保留。未确认资源时不调用 adapter.clear，也不把后台 Promise 当作结束。

`runWorkerSlot({ cfg, client, createAdapter, shouldStop, onOutcome?, onUncertain?, onError? })` 是三个 TS Runtime 共用的公开领取循环。cfg 需要 pollMs，并沿用公共 attempt 配置；client 另提供 claim()。每个领取任务使用 createAdapter(task)。正常 drain 由 shouldStop() 停止后续领取，已领取任务仍可结束；forceSignal 中止时不启动刚收到的任务，由控制平面恢复租约。

资源未知结果让 slot 永久退役，不再调用 claim；即使观察回调或 onUncertain 抛错也不能恢复该 slot。onUncertain 调用本身受 cleanupTimeoutMs 约束。TS/API 主机回调设置停止、强制取消与 drain，防止其他 slot 再启动新 attempt；只保留现有协议终态，不在 Worker 自行重跑。

### 终态元数据组合

| 事件/标记 | 成功完成 | 上报响应未知或资源未知 |
|---|---|---|
| model-terminal | completed；API 的 usageKnown=true、usageSource=provider-response 表示实际报告数值，Codex 使用 exec-json/app-server-notification；缺失 usage 时 false/unknown，不补零值 | error 对应固定错误码；不保存原始模型异常 |
| resource-terminal | 成功请求前保存 status=settled；正常成功不追加 PROCESS_TREE_UNCERTAIN | status=unknown、code=PROCESS_TREE_UNCERTAIN 表示未确认终态；关键 I/O 故障无法保存时仍返回 resourceUncertain=true 并停止领取 |
| terminal.json / attempt-terminal | ready-to-report 是成功请求前已保存必需结果与清理证据的标记，本身不代表 CP 接受 | 无 ready-to-report 时只有已确认资源结束才形成 failed/lease-lost 等终态标记；未知活动日志不作为完成证明 |
| report-posted | reportStatus=accepted 表示 complete 请求已获得正常响应 | attempt-failed 的 status=report-unknown、reportStatus=unknown；不再 fail 或重发 complete |
| 公共返回 | status=completed、resourceUncertain=false；正常完成不人为中止已结束请求的 signal | status=failed/lease-lost/report-unknown 与独立资源布尔值；不能以 unknown usage 代替 unknown resource，或反过来 |

成功前 adapter.clear 只在活动资源确认结束后调用。Git 工作树删除失败会先记录 `workspace-cleanup: preserved`，然后向 attempt 抛出原错误；成功上报前的清理失败不会生成 `resource-terminal: settled`、`ready-to-report` 或 `complete`，且不会在 finally 中重复尝试该次失败的删除。关键保存失败也不得生成未经确认的成功上报；CP 已接受后发现结果保存故障按 report-unknown 对账。

## 输出与工具

`boundedText` 的 `maxBytes` 为大于等于 128 的整数，`maxLines` 为大于等于 3 的整数，默认分别为 24000 和 1000；预算包含截断提示。低于此范围返回 `OUTPUT_BUDGET_INVALID`。UTF-8 截断分别返回 text、complete、truncated 与原始字节/行数。

`toolOutput` 的预算覆盖整个 JSON 信封。结构不能在预算内完整表达时明确拒绝；不能截断已序列化的 JSON。ToolHost 的定义参数 `validate(args)` 返回布尔，不承担转换参数的接口职责。历史工具失败保留在证据中，不能依赖相同命令/参数重跑来判定任务完成。`assertSuccessful()` 检查证据健康及取消状态，普通工具错误不永久阻止完成；致命错误仍取消 attempt。完成还须经过报告、工作区、产出、资源终态及租约校验；这不等于独立业务验收或测试已通过。
