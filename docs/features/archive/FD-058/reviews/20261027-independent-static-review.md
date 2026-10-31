# FD-058 独立静态审查

- Reviewer：`codex-fd058-review-20261027`。
- 来源事件：`FD-058-000002-review-requested`，已 claim。
- 比较基线：`7a7b92f88b0b5874981d73c915e1d812a9a42980` 上本 FD 增量；排除已有 FD-057 人工注销及更早交付。
- 最终结论：**通过**。本轮两项供给可用性发现已修复关闭；原计划真实执行、阶段独立审查、Git 提交及本地交付证据已读取。此次合并由宿主在既有授权内完成，不能表述为全自动 Git 合并。

## 发现

1. **P2：默认上限与新增角色池不相容，且供给拒绝被吞掉。** 默认 max-workers=4，但完整原生 requirements/architect/planner/coder 与独立 stage-reviewer 共五种角色；在线空闲进程按用户要求保留时，第五种角色会持续被拒绝。分析/讨论也占进程名额。`supply` 未检查 requestNative 的 Result(false,message)，运行视图只留 READY，无法看见真实阻塞原因。应为默认支持的角色集合提供合理默认容量，仍尊重用户显式上限，并记录/展示具体拒绝原因。
2. **P2：最旧五条长期 READY 可使后续计划无法供给。** SQL 每次固定 ORDER BY 最旧任务 LIMIT 5；如果它们需要不具备的技术能力、环境启动失败或其他长期阻塞，后续 READY 永远不会被遍历。应采用有界公平轮转/游标，或等价机制，避免稳定的旧阻塞任务饿死新计划。

## 已核对

- 本轮修复：READY 按任务 ID 有界游标轮转，尾部空扫描时回到起点，不重复只看最旧五条；失败/启动结果按原计划暴露在详情 API 和独立 UI 卡片中，保留具体准备原因。未捕获的异常使用固定说明，不导出原始异常敏感数据。
- 容量压力修复：仍尊重配置上限，存活失败进程也计入容量；仅达到上限时，对当前 JVM 持有且无活动容量/任务/尝试、无同技能和相符仓库 READY 的进程 CAS 为 DRAINING，提交后精确停止，退出后才移除槽位。不改成周期性五分钟杀空闲进程。此例外已明确写入本 FD。
- 排空与 claim 的同 Worker 行状态更新相容，DRAINING 阻止新容量预留；停止持有 Provisioner 同步锁，退出前不释放同 ID 槽位。事务回滚不启动停止回调并恢复 stopping 标志。

- Draft retarget 仅原所有者的人类会话、非代理、DRAFT/版本匹配、无原生执行任务、未归档且非托管计划；通过原控制台会话/CSRF 和 operator 路由。仓库需已登记，基线需完整 SHA；保留目标、步骤、技能、指令和依赖，仅统一仓库/基线。CAS 与同 Authority 锁下的确认路径相容。
- 供给 SQL 只读 CONFIRMED flexible-plan READY，排除归档；不批量确认历史 DRAFT，也不进入 subject configured-plan 的工厂执行路径。
- 在线匹配包含 runtime、全部必需技能与 repo，允许忙碌在线 Worker 排队复用；原生 coder 与 guarded coder 分离兼容键，动态 guard/config capability 不隐式授给原生进程。
- 原生工作仍由既有 Git 工作树、只读/可写角色、独立 stage review、结果提交及交付路径负责；本次新增进程供给不代表跳过这些阶段。

## 证据与限制

- 实际仅静态读取 git 差异、相关源码/测试/FD、CLI 状态并 claim；未测试、构建、调用 API、模型、重启服务或修改产品源码。
- 已读取 WorkflowWorkerProvisioningTest：缺失角色、复用忙碌匹配角色、仓库不匹配、自动开关，修复后增加准备失败可见及扫描轮转。FactoryWorkerRegistryTest 增加排空与容量/任务/尝试/匹配 READY 保护。执行者报告最终三组二十七项通过，compile-only 319 源成功；本 Reviewer 未重复运行。
- retarget 负面鉴权/CAS、跨容量压力的真实进程回收、PostgreSQL 并发和完整浏览器未运行验收。相关路径有本轮静态或定向测试支持，但不将未运行场景标作通过。

## 原计划实际运行证据复核

- 已读取 `fd058-final-receipt.json` 及 FD Verification：同一原 Plan `FP-893522e1-c598-449a-95b6-cd230c3b5a7b` 经 retarget 的 v0→v1、confirm 的 v2，最终 COMPLETED/v3；plan work/review 与 code work/review 四个原任务均 COMPLETED/PASS，没有另建 Plan 或手改数据库状态。
- 自动供给规划、阶段审查与编码 Worker 的运行结果表明本例角色链已接通；执行者记录的最终独立模型审查已通过。Worker 声明语法/历史行为模拟及 diff 检查通过、未运行真实浏览器；本 Reviewer 未重复模型审查或测试。
- 真实提交 `6e80f479a5feb5e1b7fd671871e5827f0a645db5` 仅 `sudoku2.htm`，原基线为 `f9962dc584739f4e87fa47bca483c785fda14b8b`。回执显示原仓库 master HEAD 为结果提交、status 为空、gitDelivery=DELIVERED。
- 自动 Git 交付被既有全局 LFS filter 保护阻止；本 FD 未放宽保护。宿主核对干净基线与文件属性后按已有本地合并授权快进，再通过 preview/merge API 记录 alreadyMerged。该人工交付事实已在 FD 和附近文档说明，不把自动 Worker 成功夸大为自动合并成功。
- 备份与服务加载、原计划 API 和交付操作均由执行者实施；Reviewer 只静态核对回执，没有额外 API、模型或 Git 合并。
