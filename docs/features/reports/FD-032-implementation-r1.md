# FD-032 实施记录 r1

<!-- aiw-data: FD-032-implementation-r1.json -->

已完成全部源码与文档条目，提交独立静态实现审阅；不代表已重启服务或验证真实执行。

- Worker Session：`secretary-desktop-20261024-a6529740`，来源事件 `FD-032-000004-design-ready`。
- 实现提交：`7f5f211efdae4fb632e9cb32270b7b79a24a9bdc`；比较基线 `16193b5`（本 FD 设计提交）。实现分片为 `1689485`、`8d1eb6c`、`e784f67`、`7f5f211`。
- 沿用当前主工作区，逐次核对空暂存区及明确文件列表后提交；未创建 worktree，未把其它个人秘书/模型配置/启动脚本改动纳入提交。

## 行为与边界

1. `ExecutionAccounting` 是唯一部署模式 Bean；`AIW_EXECUTION_ACCOUNTING_MODE` 默认 `record-only`，可显式 `enforce`，非法值拒绝启动。请求不能改变模式。
2. `AuthorityBudgets.consume` 仅在严格模式应用 maximumUses/累计额度谓词，原子递增与原请求去重、审计顺序保留。固定动作、授权、角色、DENY、版本、到期及暂停均保持独立校验。
3. `Policy.budget` 可缺省，预留允许零；严格模式的 configured 要求完整预算/定价和正预留。准备、创建、绑定、领取、推进、回跳及 GraphRunner 的直接判断均按同一模式。仅记录时不产生预算追加问题，不因历史 unknown_count 阻止整计划完成。
4. 仍落任务、Attempt、用量、预留和回跳账。价格使用原 Attempt 冻结快照；缺价的已启动 Attempt 费用 null，已知从未启动的执行费用为零。不重算或删除历史。
5. 跨任务纯历史 UNKNOWN 仅记录，动作与协调复用 `blocksNewExecution`。实际 Task/Attempt 的 CLAIMED/RUNNING、现存 Lease、同一原任务的 UNKNOWN、未知工具操作和静止核验继续阻止重复或并发替代执行。旧失联恢复不改造，不自动 stopped、重放或释放真实占用。
6. 授权页显示实际模式、预算可选及用量记录，默认隐藏累计额度/追加表单；`form` 绑定容忍被服务端条件移除的表单。工具/仓库授权仍显式确认。两份稳定规格与执行部署说明同步。

独立设计报告的 D032-01（图预算旁路）、D032-02（历史未知/真实在途）、D032-03（缺省/价格/严格恢复）均按明确决策实施；原 changes-needed 设计报告保持原状，交由实现 Reviewer 核对。

## 实际检查

- 范围内 `git diff --check 16193b5 -- ...` 通过；静态读取实际差异、预算调用链、表结构、原未知效果和 Lease 恢复路径及邻近页面/共享样式。
- `node --check src/main/resources/static/ui/authority.js` 通过，无应用执行。
- 最初 `mvn -o -Dmaven.test.skip=true compile` 因 PATH 找不到 mvn 未启动。随后临时设置 `JAVA_HOME=C:/green/jdk-25.0.1+8`，调用 `C:/green/apache-maven-3.9.9/bin/mvn.cmd -o -Dmaven.test.skip=true compile` 成功，编译 291 个生产源，JDK 25，2026-10-24 10:55 JST。原 Jansi/Unsafe/Maven 参数及 GraphPlans unchecked 警告保留。没有测试、依赖下载或可分发包。
- 仓库 `compile-personal-secretary.ps1` 还会检查其它 Python/TypeScript 项目，本 FD 仅改平台，故使用窄平台 compile，不扩张到其它未提交源码。
- 已只读核对用户提供的 `C:/green/bin/setup-jdk25.ps1`：它指向相同 JDK 25、Maven 3.9.4，并运行版本显示；本次实际成功用 Maven 3.9.9，未执行脚本或重复检查。
- 早期不存在的 java 子目录、Windows 通配符和一处文档整行 patch 未匹配，均按实际文件/精确片段修正；这些不是应用运行结果，没有改写他人改动。

## 未运行与风险

未运行测试、Spring/数据库/模板渲染、浏览器、真实预算/会话/对账流程、模式切换、模型调用、打包、发布或服务重启。仅记录不设累计成本上限，这是用户要求；它不授予任何新动作或工具权限。恢复 enforce 后缺省配置、历史未知及冻结价格不兼容可能再次阻止新工作，文档已明确。SQL 和模式分支目前为编译及静态证据，不是运行验收。

编译入口反馈已解决，见同目录 `FD-032-blocker-compiler-path-20261024-a8f47e19.md`；没有尚待人类决定的实施问题。下一步为独立实现 Reviewer。
