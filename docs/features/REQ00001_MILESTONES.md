# REQ00001 的 FD 里程碑映射

来源：[原需求计划](../issues/ISSUE-00002/issue-plan.md)。2026-10-04 用户先批准 M1 交接，随后授权其余里程碑先生成 FD。此文件仅记录范围与来源，不作为 FD 状态的第二权威；状态以各 FD 及 CLI 回执为准。

| 里程碑 | FD | 交付归属 | 本次记录 |
|---|---|---|---|
| M1 | [FD-001](FD-001_MISSION_CONTROL_OVERVIEW_AND_SHARED_VISUAL_FOUND.md) | Overview、共享战术视觉、首页/Fleet 机器身份基础、首页查询/刷新 | 已设计就绪，Worker 未领取 |
| M2 | [FD-002](FD-002_RUN_MISSION_DETAIL_AND_TIMELINE.md) | Run 需求/状态、真实依赖、task/attempt、失败/恢复、时间线 | 设计草案，历史范围与数据契约待定 |
| M3 | [FD-003](FD-003_WORKER_FLEET_AND_SCHEDULING_EXPERIENCE.md) | Fleet 工作关联、READY 详情、准入限制与调整/排空体验 | 设计草案，健康范围与关联设计待定 |
| M4 | [FD-004](FD-004_APPROVAL_AND_ENGINEERING_EVIDENCE.md) | 待处理/历史审批、结果版本与已存工程证据 | 制品与历史范围已确定；精确版本证据契约已设计，待实施 |

FD-001 拥有共享组件，其他 FD 复用而不重复交付；FD-002 拥有 Run 执行阅读，FD-003 拥有 Fleet/调度关联，FD-004 拥有审批证据。既有 URL 在后续阶段完成前仍可作为详情入口。

当前 AIW CLI 的 `fd new --issue` 限制一个需求正式绑定一个 FD。保留 FD-001 的正式来源关联，FD-002–004 通过 Sources 及本表明确追溯，未修改 CLI、制造子 Issue 或篡改批准/交接元数据。

M4 仅展示已存结果，审批历史为有界最近列表；外部制品全文、下载和完整历史分页均不在 FD-004 范围内。各 FD 的实施和完成状态以 FD 文件及 CLI 回执为准；M1 完成不代表原需求或 M4 完成。
