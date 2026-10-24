# FD-032 独立实现审阅 r1

结论：**verification-passed**。本轮未发现阻断验收的实现缺陷；这是一次独立静态实现审阅，不是服务、数据库、模板或真实执行验收。

<!-- aiw-data: FD-032-implementation-review-r1-20261024-3cf9158a.json -->

- Reviewer Session：`fd032-reviewer-20261024-3cf9158a`，与 Worker `secretary-desktop-20261024-a6529740` 分离。
- 已先检查最新 receipt，再认领确切来源事件 `FD-032-000005-implementation-ready`；认领时 FD Revision 5、Pending Verification。
- 审阅源码提交：`7f5f211efdae4fb632e9cb32270b7b79a24a9bdc`；比较基线 `16193b5`；证据提交 `a1eb81360b76bb62389fbd234046fb402b843157`。
- 审阅全部实际 Java、资源、部署文档及稳定规格差异，并追踪准备、任务创建、领取、开始、结算、续行、图回跳/完成、动作及协调调用链。审阅时范围内源码相对所审提交无未提交差异；其它个人秘书 Python、模型及启动脚本改动完全排除并保留。

## 发现

无阻断发现。未将代码气味、未授权运行项或编译工具路径问题转成新的验收门槛。编译路径反馈的当前状态为 resolved；本轮未执行恢复或重复编译。

## 逐项验收证据

1. **默认无预算也可正常准入。** `ExecutionAccounting.java:11` 与 `application.yaml:36` 将唯一部署模式默认设为 record-only，非法配置拒绝启动。`GuardContract.java:43` 把缺省 budget 规范化为无定价、零预留对象；字段校验仍拒绝负数。`ExecutionLedger.java:20,37,46` 使配置及余额检查按统一模式执行，`ExecutionGuard.java:55,143,171,180,211`、`AgentFactory.java:79`、续行和领取调用链复用这些判断。不存在请求选择模式的字段。
2. **动作仅记录额度，保留真实授权。** `AuthorityBudgets.java:39` 只在 enforce 应用 maximumUses/累计上限，原 SQL 递增及审计仍在调用者事务中。`ActionAdmissionService.java:102` 在消费前查询原 requestId；身份、固定参数、当前角色/授权、DENY、状态、版本、暂停与世代校验没有被模式分支跳过。
3. **计量未知不伪装为零，原标识仍防重复记账。** `ExecutionLedger.java:54` 按原 Attempt 唯一键预留并保存原 policy 的 price_json；`:81` 从该快照结算，缺价的已启动 Attempt 写 null，确认从未启动的 Attempt 写已知零。未知维度仍增加 unknown_count、保留预留。`ExecutionGuard.java:61`、Ledger 的 reserve/settle/backjump 及原表唯一约束保持任务、Attempt、transition 的幂等；现有计数不清零、不重算旧费用。V12 的可空限额/费用及非负零预留结构支持该数据流。
4. **历史与在途分别处理。** `ActionAdmissionService.java:88` 和 `CoordinationService.java:66` 共用 Plan 检查：record-only 不以纯历史 UNKNOWN 阻止无关新工作，实际 Task CLAIMED/RUNNING、现存 Lease 或活跃 Attempt 仍阻止并发推进。同原任务 UNKNOWN 拒绝、`ManagedPlanAdmission.checkReplacement`、Guard unknownTask/uncertain、原请求查询及受控失联保留 Lease/资源的路径未放开。只读 SoftwareDelivery 仍可显示计量 UNKNOWN，不参与执行准入或图完成门槛。
5. **图分支和严格恢复完整。** `GraphRunner.java:219,258,261,269,271` 的直接回跳判断、最终 unknown_count 门槛及预算问题生成已接入统一模式。record-only 不生成必须回答的预算追加问题；真实任务终态、Lease、活跃 Attempt、未知外部操作与静止核验保留。显式 enforce 恢复完整配置、正预留、原/当前价格兼容、余额、未知计量及历史动作核对；预算/政策更新不重置历史。人工暂停、撤销、到期和所有权保持独立校验。
6. **页面和文档一致。** `AuthorityPageController.java:37` 提供实际部署模式；`authority.html:14,50,54,62,67,71` 按模式显示状态、隐藏额度/追加表单，并保留传输兼容的默认 maximumUses。`authority.js:279` 容忍服务端移除的表单节点，其余绑定不因此中断；Thymeleaf 条件与三元表达式按现有模板写法使用。两份稳定规格和部署说明明确预算可选、未知数据保留、严格模式恢复及未重启/未运行验收边界。

设计意见 D032-01、D032-02、D032-03 的上述落实已分别核对。原设计 changes-needed 报告保留原状，不改写为通过。

## 实际命令与证据边界

- 实际执行：`Get-Content -Encoding UTF8`、`Select-Object`、`Select-String`、`rg/rg --files` 的定向静态读取；`git status --short`、`git diff`、`git rev-parse HEAD` 的只读范围核对；`aiw fd --help`、`aiw fd show FD-032`、确切 `aiw fd claim`、`aiw fd emit --help`。随后仅用 apply_patch 写本双报告和 FD TODO/Verification，再以本报告发出 verification-passed。
- 读取命令中曾使用错误的 Reviewer 角色/既有报告文件名、PowerShell 不支持的花括号目录列表及未展开的路径通配符；均依据实际文件路径完成静态读取，没有把这些入口失败解释为产品失败。
- 已检查 Worker 双证据和 resolved 编译反馈：所审源码提交使用 JDK 25、Maven 3.9.9 的 `-o -Dmaven.test.skip=true compile` 报告通过（291 个生产源），`node --check src/main/resources/static/ui/authority.js` 报告通过。本 Reviewer 没有重复执行，不能据此声称运行通过。
- 未执行：测试、编译、Spring/数据库、模板渲染、浏览器、真实身份/账本流、模式切换、模型、网络、依赖下载、打包、安装、重启、发布、下级代理。未修改实现源码、提交、合并或归档。

## 剩余风险与交接

SQL 在实际 SQLite/PostgreSQL 数据上的行为、Thymeleaf 实际渲染和 record-only/enforce 切换尚无运行证据；本结论以源码、原事务和既有表约束的静态追踪为限。record-only 不设累计成本上限，符合用户当前决定；恢复 enforce 后缺省政策、历史未知项和已消耗额度可能重新使新工作等待。当前服务不会因源码修改自动升级。

本轮为实施周期第 1 次独立实现审阅，之前失败次数为 0，本轮通过。交 PM 完成有范围的本地证据提交及正常归档，不授权发布或覆盖其它任务。
