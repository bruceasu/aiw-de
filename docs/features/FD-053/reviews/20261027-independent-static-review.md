# FD-053 独立静态审查

- 结论：通过。本轮发现的问题已在同一次审查中修复，当前无未解决的阻断项。
- Reviewer：`reviewer-fd053-20261027-static`。
- 来源事件：`FD-053-000003-review-requested`，已实际 claim。
- FD：Revision 3；审查基线为开发仓库 HEAD `4bb30379e2365224040edfaeee2c77fe0028b103`，对象是本 FD 的未提交实现及新文件，不包含其他历史修改。
- 最新人类决定：本地合并提交属于交办任务的正常交付，无须额外申请授权。

## 本轮发现及修复

1. **P1，已修复：ignored 本地文件可能被覆盖。** `WorkflowGitDeliveryService.preview` 原先使用 `ls-files --others --exclude-standard`，无法发现被忽略的本地文件；Git 默认允许覆盖这类文件。现在预检包含 ignored 文件，真实合并增加 `--no-overwrite-ignore`。新增 `ignoredLocalFileIsNeverOverwrittenByDelivery` 使用临时 Git 仓库验证原文件、HEAD 均保留。
2. **P1，已修复：仓库配置可能改变真实合并行为。** 清除本次目标的 `branch.<target>.mergeOptions`，固定 `--strategy=ort`，禁用签名校验 `--no-verify-signatures`，避免策略和签名配置引入外部程序或偏离预检。原有 hooks、过滤器、自定义 merge driver、提交签名及自动维护限制保留。最后两个固定参数经过本 Reviewer 静态核对及宿主离线编译，未声称已运行对应回归。
3. **P2，已修复：失败提示缺少翻译。** 原目标缺失、目标变化、冲突和停止自动重试等固定提示已加入中文及日文 catalog。

## 实际检查

- 追踪原生与 Factory 确认、完成调用链：确认事务记录本机仓库和分支；完成事务创建交付行；轮询仅消费 `READY`，不扫描历史完成计划。原生同仓库编码步骤会传播先前已接受的结果提交。
- 检查人工入口：需要当前人类所有者，调用方不能设置内部 automatic 模式。后台权限来自新完成事件形成的内部队列。
- 检查操作意图与 Git 边界：SQL 持久化 `MERGING` 后再写 Git；快进或预检无冲突的本地合并；成功记录前后提交；不推送、不 stash、不 reset。冲突为 `BLOCKED`，未知为 `UNKNOWN`，中断遗留 `MERGING`，均不自动重跑。
- 检查注册根目录、完整提交标识、计划版本、仓库路径、目标分支及 HEAD 快照，跟踪文件差异前后摘要、暂存/未跟踪文件保护，以及归档阻止规则。
- 检查 V21 两张新增表、状态约束与唯一请求标识；UI 区分执行完成和交付完成，历史/受阻交付直接点击本地合并，无额外授权框，保留用户数据的翻译跳过标记。
- 阅读真实测试源码及 Surefire XML：宿主执行 `mvn -o -Dtest=WorkflowGitDeliveryServiceTest,WorkflowReviewedCompletionTest test`，修复后为 23 项通过（11 + 12），失败、错误、跳过均为 0。测试使用真实临时 Git 与 SQLite，覆盖快进、分叉合并、冲突、原目标缺失/变化、历史不自动执行、未知不重试、幂等、委派拒绝、暂存和本地修改保护。
- 宿主报告最终离线 compile-only 成功；本 Reviewer 未运行测试、构建、服务诊断或模型调用。

## 命令与证据边界

Reviewer 实际执行：定向 `Get-Content` / `rg` / `git diff` / `git rev-parse HEAD` / `Get-FileHash`，读取 Surefire XML，`aiw fd claim`，查看 `aiw fd emit --help`，写本报告及 FD Verification，随后提交真实 Reviewer 结果事件。早期一次技能共享文件路径读取错误已改为 `fd-review/skills/` 后成功读取，未扩大运行验证。

没有运行完整浏览器、生产 Git ProcessBuilder、真实进程中断或并发外部 Git 操作的验收。临时仓库测试 override Git runner 隔离全局配置；不能据其宣称生产 runner 的超时、环境清理及输出上限已动态验证。最后增加的 ort/禁签名两个固定参数不在 23 项测试的编译快照内，静态依据和最终编译足以支持本次有限审查结论。Git 与 SQL 不能原子提交；外部工具并发改变工作区仍需人工核对未知结果。V21 和服务重启由宿主在通过后备份并执行，本报告不声称已经部署。

## 冻结实现摘要（SHA-256）

```text
16d0f028f157979d6dd864a9e72b513657e2b5f062c7e32b0b2440c834860246  WorkflowGitDeliveryService.java
f49e25ad037bed5961522db7b238254b6782c69a4490a601d3827b284ae3aaee  V21__plan_git_delivery.sql
a085cd5afe670c68d0f3f2e2c536cf7742f88b81dab35981d7076f92f6b34fc7  WorkflowEditorService.java
538def4efb47e81cdb69ed2bdeb0cd0bafd10d61d1e25ffaeff491b6ed218ef4  WorkflowPlanArchiveService.java
3f033b9fff700d64aeddc2d412b635c868893cc73dee3f6d65f2d551a21da92e  WorkflowEditorController.java
679a9815f09e5cbcf238333f35c1bebfe2eee4b60dd2a03c2c169d1df56759de  WorkflowPlanService.java
c108ad354f0fe162e738f6715c26ebd1e4bad6e1322a1f56c2eb5ca764f689b2  FactoryPlanLifecycle.java
bc216490652781e65e860964bd0cc776309d808c43fc114f2c4540b338f0d855  workflow-editor.js
e4cf8ea070f72609a8eefbb7a78cc0659f342a8deac5b3966d0f8799fdfdc2f3  console-translations.js
8457cb9f97e9935506ed06bdf65e2f44eb9af485b0f59a6e84a5d5244c0a8d59  plan-detail.html
209b803cadbf428b3393ea1428234c01d3544f6db39b6e2d289d7cdd66b2e752  WorkflowGitDeliveryServiceTest.java
```
