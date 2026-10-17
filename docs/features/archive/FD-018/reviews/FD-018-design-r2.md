# FD-018 独立设计复核（第 2 轮）

<!-- aiw-data: FD-018-design-r2.json -->

结论：**ready**，建议 Planner 进行设计交接。原 D1–D4 均已在设计层面补全；本次未发现仍需阻止设计交接的实质遗漏。这是对设计可实施性的建议，不是实现通过、运行通过或代替 Planner 发出 design-ready 事件。

- 审阅对象：`docs/features/FD-018_AI.md` Revision 2 当前内容，重点为新增“设计审阅 D1–D4 的落实”与 Verification。
- 来源事件：`FD-018-000002-design-requested`；本报告是同一设计请求的有界复核，没有新建事件。
- 前序报告：`FD-018-design-r1.md` / `FD-018-design-r1.json`，保留其历史结论，不覆盖。

## D1：设计缺口已解决

固定 id=1 的迁移初始化、settings 写锁与 expectedRevision CAS、settings→context/task 和原领取 admission→worker→task 顺序均已明确；关闭与领取由 task 状态 CAS 裁决，双数据库的锁机制已说明。

## D2：设计缺口已解决

用户明确 planner 为现有 AI Profile；planner/model/effort 精确筛选已批准组合，不产生新组合；冻结完整候选内容及 catalog version，guidance/start/claim 使用同一快照；既有 wire guidance 兼容并保守限定三语言 runtime/provider/fast/effort。

## D3：设计缺口已解决

推荐专用过期 CAS、task FAILED、attempt EXPIRED、一次容量/并发释放、审计和 stopRequested 例外均已明确；迟到调用不能形成可应用结果；健康调度等待与墙钟上界已区分，普通任务继续旧重排路径。

## D4：设计缺口已解决

actor-owned context 生命周期与 URL/近期上下文恢复、contextRevision 并发裁决、requestKey 内容幂等、刷新 goal/version、一次绑定 Plan 与旧任务兼容均已明确；未提交编辑不承诺恢复的边界可检验。

## 测试与实现交接关注点

FD 已给出聚焦 TDD 的 target Red/Green、离线命令、固定 JDK/Maven 路径和一次独立验收预算。测试执行者仍应从验收规则独立推导场景，尤其检查并发 CAS 的胜负、冻结 profile 在配置变化后保持、过期恢复一次释放及恢复后的 stale 判定；不能只以默认启用的单条断言代表全部验收通过。

实现评审需检视 D1–D4 的实际调用链，特别是：事务实际生效而非 self-invocation；readiness/claim/guidance/start 不重新解释快照；过期候选查询不受旧 stop 过滤而漏掉推荐；迟到输出即使留下审计材料也不能成为可应用结果。这些是已确定设计的验证关注点，没有新增业务决策或独立阻断项。

## 事实、假设与未验证范围

事实：planner 已由用户明确为现有 AI Profile；当前 FD 已记录四项落实与具体验证预算。此次仅通过一条只读命令读取当前 FD 和首轮 JSON，并写本报告及同名 sidecar；未修改 FD 或首轮报告。

假设：实施保持当前描述的数据库事务边界与既有 Worker guidance 协议；如果代码调查推翻这些前提，应返回 Planner 修订，而非自行弱化验收。

未验证：数据库实际并发锁行为、三个语言 Worker 的实际运行、浏览器交互、PostgreSQL 行为、模型调用和测试命令可运行性。本次没有执行实现、编译、测试、网络、Git 或工作流事件。ready 仅适用于本次所读设计。

