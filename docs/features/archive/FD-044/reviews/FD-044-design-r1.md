# FD-044 独立设计审阅 r1

<!-- aiw-data: FD-044-design-r1.json -->

结论：**ready，可以进入实施**。初次发现的一项输入状态竞争已由 Planner 在同轮补齐并经聚焦静态复核，当前没有未解决的设计阻断项。这是设计就绪结论，不是实现验收或运行通过。

- Reviewer Session：`fd044-design-20261024-d3e9a016`，与 Planner/Worker `worker-context-20261024-61db8e4a` 不同。
- 真实来源：`FD-044-000002-design-requested`；本会话不领取或发出生命周期事件。
- 原设计提交：`408fc32052da05f7681fe2dedddb81f9e1062f09`，FD Revision 2。
- 聚焦复核后的 FD SHA256：`b7ea598c28d436751863c7620fa0de2b313dfcf73d4d9532569193659df724c8`。对应当前设计正文及 TODO，Planner 尚需按托管流程提交/标记就绪。

## 发现与同轮修正

**D044-01 / P1 / 已在设计中解决：消息被领取后仍可能显示已撤回。**

初版在 FD“受控 Worker 工作台与输入合同”中只区分待递交与 DELIVERED，并允许撤回未递交消息。Worker 已把消息读入内存、模型请求发出或等待终态 ACK 时，人类仍可能撤回该消息，从而出现“页面显示已撤回，内容却已交给模型”的竞争。单独给 enqueue 与 seal 加锁不能覆盖这段窗口。

Planner 已按建议补入 QUEUED → RESERVED 事务领取批次，保存批次 mode、消息序列及 digest；只有 QUEUED 可以撤回。模型 begin 后、请求发出前，批次绑定同一 Attempt 的确切 STARTED MODEL 操作；成功模型终态与该操作 SUCCEEDED 后才可 DELIVERED，不能用任一别的模型结果代替。未知/失败/失联不自动返队或换 ID 重放；DRAIN 原子领取剩余或封口，QUEUED、RESERVED/UNKNOWN 批次及活动/未知操作阻止 seal。聚焦读取当前 FD 第 36、38、42 行及实际 diff，确认该设计缺口已关闭。

同时落实了配套边界：现有 `ExecutionGuard.read` 的 running=false 校验不直接用于新交互 kind；新 open/reserve/link/ACK/drain/seal 必须 `verifyLease(task,lease,true,true)`，核对无 stop request、原 revision 与服务器 Owner。人类命令有 owner+requestId 的原 digest/result 收据，未知响应先核对原结果，只能重试同 ID/内容。

## 静态审阅结论

1. **范围与用户方向**：九项建议映射到明确工作项。保留同 Worker 多技能、平台跨 Worker 编排及仅记录消耗；没有新增 Pi 依赖、固定专家进程、模型路由或预算门禁。Python API 先落实新实时输入/压缩，其他后端真实声明支持差异，未用统一名称掩盖未实现能力。
2. **旧输入与任务合同**：渐进入口保留主技能、工程协议和无有效描述的正文，完整文件/hash/manifest/工具边界不变。新增输入另有 Attempt/revision/批次证据，不覆写原固定 system/task/Skill，也不替代正式换目标修订。FOLLOW_UP 仍在同一 Attempt 与原角色内，原期限及总模型调用数不重新取得。
3. **Owner、状态与恢复**：Owner 从已有执行政策归属取得，人类及机器路由沿现有保护路径明确分支。追加表只为主动开启的新会话记录，不回填旧任务；幂等键与原结果收据处理响应未知。补齐后的领取/撤回、精确 MODEL 绑定与 DRAIN/seal 为实现提供了明确的线性化边界。实际停止、完成、验收仍由原平台事实决定，阶段只是有时间的上报提示。
4. **摘要、工具与计量**：采用已有 TS 方法的安全切点、完整调用/结果配对、原文分页和派生摘要边界；排除隐藏推理，固定依据与未知效果保留。同模型摘要进入受控 MODEL/真实用量并共用原执行上限。完整工具结构与模型可见内容分层，原锁覆盖整段读改写；没有声称跨进程互斥、可逆副作用或自由执行扩展。
5. **能力包与探索**：能力包复用既有不可变 Owner/仓库 knowledge 资产，固定 Skill 引用核对、原工具枚举及角色编译继续约束。导入不等于增加权限。探索是同任务方案记录树、可比较及明确递交，不是假装底层 transcript 或文件恢复；不可变父引用、同 Owner/任务及真实同任务结果版本已限定。
6. **工作项与证据**：设计将接口、状态、适配和 UI 分成有依赖的低/中难度工作项，并保留最终独立实现审阅。无需因为本次设计静态审阅额外执行测试或新增人类批准；后续若实际 SDK/数据库事实与设计不符，应如实返回设计，而不是猜测支持。

## 实际读取与命令

读取根 AGENTS、四份 core、work-management、domain、monorepo、java-spring、code-review 和 fd-workflow 的 planning 引用。仅使用 PowerShell、内联 Python 标准库读取固定文本，检查 FD、ExecutionConfigurations、ComposedRoles、ConfigurationContract、ExecutionGuard 与 Python GuardBroker 的相关段落；读取原设计提交 ID。一次源码输出被截断后，按准确路径补读与发现直接相关的 Lease/操作/合同段落。

Planner 写入修正后，运行 `git diff 408fc32 -- docs/features/FD-044/FD-044_WORKER_CONTEXT_TOOLS_AND_HUMAN_INTERACTION.md` 并只复核变动段落，读取设计文件 SHA256。写入本报告双文件后只做一次报告格式/sidecar/本地链接静态检查，不执行产品方法或自动验证脚本。

本会话没有修改 FD、产品代码、状态、原计划或其他已有改动，没有暂存/提交、claim/emit、再生成代理或开展独立实现审阅。

## 未验证与剩余风险

未执行测试、编译、模型/SDK、Worker、数据库/迁移、HTTP/浏览器、服务、网络、依赖下载、打包、发布或重启。设计及当前源码能支持边界取舍，不能证明真实输入注入、压缩效果、并发竞争、停止竞争、SQLite/PostgreSQL 迁移或 UI 已运行正确。摘要和模型是否遵循新补充仍需要未来实际证据；不把派生摘要或 Worker 自报结论当权威。

接下来由 Planner 保留上述明确状态和来源约束，提交当前设计并正常发出 design-ready；Worker 实现后由另一个独立 Reviewer 审阅实际代码与证据。
