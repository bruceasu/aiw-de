# Python Worker 自动化循环：实施状态与配置

设计和剩余工作以 [FD-079](../../docs/features/FD-079/FD-079_OBSERVABLE_AND_CONTROLLABLE_PYTHON_WORKER_AUTOMA.md) 为准。本页记录已接入的 Python 能力，不代表整个 FD、发布或运行验证已经完成。

## 已有实现

- 模型请求前记录 `runtime-observed`、`context-projected` 和 `context-component`，包含 Worker 提交材料的字节数、摘要、变化与实际可见范围。
- 冻结配置、任务、上游与技能入口记录为材料库存；库存不保证每个材料都出现在后续每个请求中。
- host-action v2 支持严格 JSON、最多 16 项串行工具批次和最多两次协议修正；相同错误无新观察时提前停止。实际动作仍经原 Broker 授权，部分失败后剩余动作明确未派发。
- 候选报告校验回调复用 `process_worker._completion_report`，合法 `REPAIR_REQUIRED` 不改成 `PASS`。API 可在输入窗口封闭前有限修正；文本 v2 在返回 finish 前校验。
- 文本 v2 可在一个 attempt 内复用同一临时只读、无原生工具的 Codex 会话，后续回合仅追加新观察。不存在自动重连或跨 attempt 续接。

## 默认值与显式启用

默认保持 `AIW_HOST_ACTION_PROTOCOL=v1`、`AIW_CODEX_HOST_SESSION_REUSE=false`、`AIW_COMPLETION_CORRECTIONS=0`。观测只写本地元数据，不改变模型或权限配置。

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

CP 实时观测/控制侧通道、Web 按钮与真实回执、软暂停/继续、完成竞态原子封闭、非 Git 问答恢复、只读工具并行和各后端完整能力匹配仍在 FD-079 工作项中。材料依赖与阻塞恢复还需对齐 FD-078。

新行为目前只有编译和静态证据；单元测试、真实模型、进程、平台联调和性能收益的执行状态以 FD Verification 为准，不能据本页推定已验证。
