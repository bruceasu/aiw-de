# Python Worker 自动化循环：实施状态与配置

设计和剩余工作以 [FD-079](../../docs/features/FD-079/FD-079_OBSERVABLE_AND_CONTROLLABLE_PYTHON_WORKER_AUTOMA.md) 为准。本页记录已接入的 Python 能力，不代表整个 FD、发布或运行验证已经完成。

## 已有实现

- 模型请求前记录 `runtime-observed`、`context-projected` 和 `context-component`，包含 Worker 提交材料的字节数、摘要、变化与实际可见范围。
- 冻结配置、任务、上游与技能入口记录为材料库存；库存不保证每个材料都出现在后续每个请求中。
- host-action v2 支持严格 JSON、最多 16 项串行工具批次和最多两次协议修正；相同错误无新观察时提前停止。实际动作仍经原 Broker 授权，部分失败后剩余动作明确未派发。
- 候选报告校验回调复用 `process_worker._completion_report`，合法 `REPAIR_REQUIRED` 不改成 `PASS`。API 可在输入窗口封闭前有限修正；文本 v2 在返回 finish 前校验。
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

目前仍有待实现的完成政策，例如合法无需改动结果的统一证据校验、Codex 动态工具路径的候选续行；不要将局部开关视作所有后端语义已对齐。

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

## 尚未交付

非 Git 问答恢复和各后端完整能力匹配仍在 FD-079 工作项中。材料依赖与阻塞恢复还需对齐 FD-078。

单元测试、真实模型、进程、平台联调和性能收益的执行状态以 FD Verification 为准，不能据本页推定已验证。

## 受控任务的运行通道

`AIW_WORKER_LOOP_CONTROL=true` 显式开启 `aiw.worker.loop.v1`，启动时要求已验证的受控适配器，并登记 `worker-loop-v1`；Codex 还要求 host-action v2。当前控制只支持已有执行策略和 Broker 的 Python API，以及 Codex host-action v2。普通原生任务保留原执行路径和 NONE 暂停能力；要求 worker-loop-v1 却缺少受控上下文的任务明确拒绝，不把普通任务自动改为受控任务。Codex 路径继续使用已有账户，不要求新增 API Key。

CP 附加 V28 三张运行元数据表，沿用 Worker token、精确租约、Owner、Operator 和 CSRF。启用前先 drain，升级服务和 Worker，并重新准备冻结配置；不热切换在途任务。该开关及源码摘要进入冻结资源策略，不自动创建执行策略或放宽权限。

通道每秒独立轮询，单请求超时两秒，五秒失联后暂停新宿主动作派发；已有动作可以结束，原硬期限与心跳继续。软暂停默认等待活动动作结束；资源未知时不确认 APPLIED。取消进入既有 requestStop/stop-acknowledgement，只有平台停止确认后显示已生效。输入窗口与运行封闭在同一事务比较，完成前必须封闭；暂停与取消优先于完成。

工作台按选定尝试显示阶段、联系时刻、上下文组成和命令收据。元数据只传封闭白名单，源码、完整 prompt、工具输出及凭据不上传。重复事件按连续序号确认，同序异内容关闭会话。控制请求用原 requestId 核对，结果未知时先查收据，再明确重试原请求。

已通过六项本地 Python 控制规则测试和七项临时 SQLite/Mock Java 测试；这不证明真实模型、浏览器、PostgreSQL、多实例或网络故障联调通过。新通道默认关闭，尚未完成整项 FD 的独立审查与发布。

## 文本读取与只读并行

`AIW_SKILL_TEXT_PROTOCOL=v1` 增加 `read_skill_text`，直接返回 UTF-8 文本；原 `read_skill_file` 的 base64 二进制合同保留。两者使用字节偏移和最多 8192 字节的分页。文本偏移必须是 UTF-8 边界，按返回的 nextOffset 继续；不可解码的数据明确要求二进制工具。摘要仍对应整个冻结文件，不执行其中脚本。

`AIW_HOST_READ_PARALLEL=v1` 允许 host-action v2 的全只读独立批次并行，最多四个实际读取者，结果保持原调用顺序。只读类型为 read、grep、ls 和两种技能读取；混合批次串行，写入/Git/命令/提问互斥并等待读取结束。发生失败后未启动的调用标记 BATCH_NOT_DISPATCHED，已经启动的调用等待实际结果；不撤销或重做已有动作。默认 off，开关和源码进入冻结摘要。

v2 的可写任务允许合法“无需修改”：finish result 提供 noChange.reason 和 noChange.evidence，引用宿主返回的成功读取 callId；不能引用未发生、失败或写入动作。合法 REPAIR_REQUIRED 可以保持无改动，不为了通过校验制造写入。这只是输出和证据一致性校验，仍需既有业务验收。
