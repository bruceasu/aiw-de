# Python Worker 自动化循环：实施状态与配置

设计与证据见已归档的 [FD-079](../../docs/features/archive/FD-079/FD-079_OBSERVABLE_AND_CONTROLLABLE_PYTHON_WORKER_AUTOMA.md)。本地实现和整项独立审查已完成；本页记录已接入的 Python 能力，真实环境与发布验证范围另行说明。

## 已有实现

- 模型请求前记录 `runtime-observed`、`context-projected` 和 `context-component`，包含 Worker 提交材料的字节数、摘要、变化与实际可见范围。
- 冻结配置、任务、上游与技能入口记录为材料库存；库存不保证每个材料都出现在后续每个请求中。
- host-action v2 支持严格 JSON、最多 16 项串行工具批次和最多两次协议修正；相同错误无新观察时提前停止。实际动作仍经原 Broker 授权，部分失败后剩余动作明确未派发。
- 候选报告校验回调复用 `process_worker._completion_report`，合法 `REPAIR_REQUIRED` 不改成 `PASS`。API 在输入窗口封闭前校验，Codex dynamicTools 在返回候选前校验；`AIW_COMPLETION_CORRECTIONS=1/2` 允许在原 attempt 内有限修正，默认 0 仍校验并直接保留具体错误。文本 v2 在返回 finish 前校验。
- 文本 v2 可在一个 attempt 内复用同一临时只读、无原生工具的 Codex 会话，后续回合仅追加新观察。不存在自动重连或跨 attempt 续接。

## 默认值与显式启用

默认保持 `AIW_HOST_ACTION_PROTOCOL=v1`、`AIW_CODEX_HOST_SESSION_REUSE=false`、`AIW_COMPLETION_CORRECTIONS=0`、`AIW_WORKER_LOOP_CONTROL=false`。观测默认只写本地元数据，不改变模型或权限配置。

已有有效执行策略、Broker 与 `execution-guard-v1` 任务的 Codex 文本路径，可以显式配置：

```dotenv
AIW_CODEX_GUARD_MODE=host-actions
AIW_HOST_ACTION_PROTOCOL=v2
AIW_CODEX_HOST_SESSION_REUSE=true
```

这些开关不把原生工厂任务自动变成受控任务，也不补建缺失的执行策略、授权或目录上下文。会话复用要求 v2，其他值明确拒绝。配置与源文件进入既有冻结适配器/资源摘要，应先 drain 再更新 Worker，不热切换在途任务。

API 路径可以独立设置 `AIW_COMPLETION_CORRECTIONS=1` 或 `2`；默认 `0` 保留原行为。修正不增加原调用上限或期限；租约、权限、不确定副作用错误不当作报告格式错误纠正。

动态工具修正复用原线程，只追加校验反馈，不重放整项任务；每轮仍经原 Broker 的 MODEL 准入和期限约束。相同错误无新工具观察时提前停止，缺输入的正式 question 优先于格式纠正。后端累计 usage 按可确认的相邻差值记录，缺少基线保持未知。原生 Codex 回合结束后验证报告；它不声明同线程格式修正或逐工具控制能力。

2026-11-07 收尾定向验证：Python 八组 58 项通过，Java 四组 21 项通过，包含动态候选校验、上下文绑定和整个 Plan 停止优先级。仅使用 mock、临时目录和测试 SQLite；这不代表真实 app-server、API、浏览器、升级迁移或性能基准通过。整项独立审查结果以 FD 记录为准。

独立审查发现并修复控制目标切换、续行来源标记、本地上下文读取的暂停边界三项问题后，同样八组 Python 61 项、四组 Java 22 项在一次修复重跑中全部通过。控制提交会再核对当前任务/尝试；本机数据读取也计入受控活动，异步等待不会阻塞事件循环或遗留后台准入。

## 如何理解观测记录

使用现有 `AIW_WORKER_EVIDENCE_ROOT` 管理的 attempt 专属 `events.jsonl`。完整记录位于源码与工作区之外，继续受既有容量和保留规则限制。

| 字段 | 含义 |
| --- | --- |
| `visibility=WORKER_PROJECTION` | 仅代表 Worker 可见的提交，不代表完整提供方内部 payload。 |
| `requestScope=REQUEST` | API 包装能观察的请求；摘要请求也计入。 |
| `requestScope=BACKEND_TURN` | Codex 后端回合，不等于其内部模型请求数。 |
| `changed` | 同运行时、用途、组件的摘要与上次不同，不是缓存命中率。 |
| `usageKnown=false` | 此条投影没有真实 token 归因，不能补零或用字节代替。 |
| 材料库存 `complete=false` | 库存已记录，但未逐请求确认该材料的实际占比。 |
| `pauseBoundary=NONE` | 当前没有对外提供新的软暂停保证。 |

复用会话的后端累积历史不可完全观测；投影只记录本次提交增量，不能把较小增量当作模型全部上下文或实际费用。

不保存完整 prompt、源码、工具原文或凭据到这些元数据事件，不向 CP 上传修改文件。上下文读取和工具结果仍由既有授权消费者处理。

本地投影另记录原指令版本、配置摘要、配置的模型/effort、显式工具 schema 摘要，以及冻结材料库存摘要；同一运行时的任务/配置绑定不能在 attempt 内改变。未提供的模型、配置或后端工具 schema 为未知，库存摘要也不证明逐来源实际注入字节数。CP 的运行记录保留原版本/配置，侧通道继续只接受既有元数据白名单。

## 验证边界

原生 Codex 的 location 问答与恢复实现见下节。各后端按上面声明的实际能力工作；真实平台联调、升级迁移与性能收益尚未验证，OAuth 仍只有设计评估。

单元测试、真实模型、进程、平台联调和性能收益的执行状态以 FD Verification 为准，不能据本页推定已验证。

## 受控任务的运行通道

`AIW_WORKER_LOOP_CONTROL=true` 显式开启 `aiw.worker.loop.v1`，启动时要求已验证的受控适配器，并登记 `worker-loop-v1`；Codex 还要求 host-action v2。当前控制只支持已有执行策略和 Broker 的 Python API，以及 Codex host-action v2。普通原生任务保留原执行路径和 NONE 暂停能力；要求 worker-loop-v1 却缺少受控上下文的任务明确拒绝，不把普通任务自动改为受控任务。Codex 路径继续使用已有账户，不要求新增 API Key。

CP 附加 V28 三张运行元数据表，沿用 Worker token、精确租约、Owner、Operator 和 CSRF。启用前先 drain，升级服务和 Worker，并重新准备冻结配置；不热切换在途任务。该开关及源码摘要进入冻结资源策略，不自动创建执行策略或放宽权限。

通道每秒独立轮询，单请求超时两秒，五秒失联后暂停新宿主动作派发；已有动作可以结束，原硬期限与心跳继续。软暂停默认等待活动动作结束；资源未知时不确认 APPLIED。取消进入既有 requestStop/stop-acknowledgement，只有平台停止确认后显示已生效。输入窗口与运行封闭在同一事务比较，完成前必须封闭；暂停与取消优先于完成。

工作台按选定尝试显示阶段、联系时刻、上下文组成和命令收据。元数据只传封闭白名单，源码、完整 prompt、工具输出及凭据不上传。重复事件按连续序号确认，同序异内容关闭会话。控制请求用原 requestId 核对，结果未知时先查收据，再明确重试原请求。

控制通道默认关闭；初期通过六项本地 Python 控制规则测试和七项临时 SQLite/Mock Java 测试，最终定向结果见前文。整项本地实现已通过独立审查；真实模型、浏览器、PostgreSQL、多实例、网络故障联调和发布仍未验证。

## 默认原生 Codex 工厂计划

此路径保留 Codex app-server 的内部循环与工具执行。更新后的非 guard Python Codex Worker 自动登记 `worker-loop-turn-v1`、`questions-v2`、本机交接存储和适配器配置摘要能力；登记不自动启用某个 Plan。计划创建者应在任务执行前打开 Worker 工作台，选择“原生工厂计划”，明确点击“为此计划启用控制和问答”。这只绑定当前 Owner 与计划范围，不补建执行策略或工具授权。已有运行任务不能事后绑定；绑定后修改目标或范围应新建 Plan。

原生通道的工具执行者是后端，暂停边界是完整后端回合及本机收尾结束并确认静止，不能暂停 app-server 内部任意模型请求或工具中点，也不提供执行中补充输入。租约心跳和原期限继续计时。资源或停止结果未知时保留尝试、容量与现场，由现有停止证据入口核对，不能强制过期后直接重放。未绑定的原生任务继续原执行路径。

明确缺少输入的报告由 Worker 转为正式 `aiw.questions.v2` 问题，保留 `.ai/handoffs/.../manifest.json` 和报告，Git 为可选证据。只有本机静止、平台接受交接且确认 yield 后，任务才进入 `WAITING_INPUT` 并释放执行容量。合法的普通 `REPAIR_REQUIRED` 不自动变成提问，缺输入报告不能同时声明 PASS。

在“工作问题”页确认的原始回答可以创建唯一 successor；没有确认答案就不会以普通重试代替回答。续行保留原目标、冻结技能、配置/模型政策和交接存储能力。Worker 在执行模型前验证源任务/尝试、确认答案版本/摘要、manifest 摘要和工作树摘要。工作树在等待期间改变或原配置不可用时关闭自动续行，不自动换模型、目录或账户。`.ai` 和工作文件仍不上传 CP。

CP 的原生 availability 明确返回 `checkpointRequired`，其值来自正式问题的 successor 绑定。`questions-v2` 只表示能力；首次任务即使要求该能力，也不因此要求历史 checkpoint。真实续行缺少 checkpoint、首次任务收到意外 checkpoint 或缺少来源声明时明确拒绝。

默认存储亲和性按主机名计算；共享存储可显式设 `AIW_HANDOFF_STORE_ID`，仅用于标识同一真实存储，不创建共享目录。等待回答时不要清理交接或 worktree。原生 checkpoint 的目录快照沿用 50,000 项/128 MiB 上限，排除 `.git` 和 `.ai`；超过上限不能声明可恢复，应保留现场并核对。配置/适配器更新须先 drain，不复用相同 Worker ID 热替换正在执行的代码。

本节初期在用户授权后通过原生与控制 Python 17 项、Java 临时 SQLite/Mock 20 项，Java 首轮的无效测试数据已修复并仅重跑一次；最终 61/22 项及独立审查结果见前文和 FD 记录。未运行真实模型、浏览器、服务或正式数据库迁移。独立 ChatGPT OAuth 仍是设计评估，不是本节已实现的认证入口。

回滚旧 CP 前必须消除它无法识别的原生绑定执行状态：绑定 Plan 不再可领取，活动/暂停尝试已核对结束，待恢复原生问题已在当前版本完成或明确终止。仅关闭 `AIW_WORKER_LOOP_CONTROL` 不会撤销原生 Owner 绑定；不要在旧 CP 下继续此类 Plan。保留 V28/V29 元数据和本机交接，不通过删表或清目录回滚。

## 文本读取与只读并行

`AIW_SKILL_TEXT_PROTOCOL=v1` 增加 `read_skill_text`，直接返回 UTF-8 文本；原 `read_skill_file` 的 base64 二进制合同保留。两者使用字节偏移和最多 8192 字节的分页。文本偏移必须是 UTF-8 边界，按返回的 nextOffset 继续；不可解码的数据明确要求二进制工具。摘要仍对应整个冻结文件，不执行其中脚本。

`AIW_HOST_READ_PARALLEL=v1` 允许 host-action v2 的全只读独立批次并行，最多四个实际读取者，结果保持原调用顺序。只读类型为 read、grep、ls 和两种技能读取；混合批次串行，写入/Git/命令/提问互斥并等待读取结束。发生失败后未启动的调用标记 BATCH_NOT_DISPATCHED，已经启动的调用等待实际结果；不撤销或重做已有动作。默认 off，开关和源码进入冻结摘要。

v2 的可写任务允许合法“无需修改”：finish result 提供 noChange.reason 和 noChange.evidence，引用宿主返回的成功读取 callId；不能引用未发生、失败或写入动作。合法 REPAIR_REQUIRED 可以保持无改动，不为了通过校验制造写入。这只是输出和证据一致性校验，仍需既有业务验收。
