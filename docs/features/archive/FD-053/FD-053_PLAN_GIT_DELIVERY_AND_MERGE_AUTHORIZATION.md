# FD-053: Plan Git delivery and merge authorization

**Status:** Complete
**Revision:** 4
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

计划已完成编码和审查，但结果只在任务分支，用户误以为原工作目录已经更新。完成后必须实际合并，或明确申请合并授权。

## Options and decision

用户明确决定：本地合并提交无需单独授权，属于交办任务的正常交付。新计划在确认时记录原仓库和分支，执行与审查通过后自动排队本地合并；可快进时快进，分叉但无冲突时创建本地合并提交。使用独立交付状态区分执行完成和交付完成，保留原计划执行状态及历史报告。

## Solution

已完成且有实际 Git 改动的计划，从已接受任务回执生成交付记录。列表和详情优先显示交付状态。完成事件仅为新完成计划排队，定时处理器只读取 READY 交付，不扫描历史完成计划。后台重新核对记录的仓库、分支、结果和当前 HEAD，先持久化操作意图，再运行 Git。分叉时先用 merge-tree 检查冲突。拒绝冲突、重叠的本地改动、暂存内容、分支变化、未注册路径和未知结果；不 stash、reset、推送或调用仓库 hooks/filters/自定义 merge driver。保留无关的本地未暂存修改。历史结果和受阻交付可由计划所有者直接点击本地合并，不再弹出额外授权对话框。

新增交付回执及原目标快照两张表，不改写既有尝试或代理授权表。已有完成计划也从实际接受的输出显示待交付，但不会自动修改其仓库；已在外部合并的结果可记录交付回执，不重复执行 Git。进程中断或结果未知时阻止自动重试。Git 与数据库不构成同一事务，必须显式保留 MERGING/UNKNOWN 状态，不能假装回滚外部写入。

## Scope

本机已登记的 Git 工作区、原生及正式 Factory 计划、Control Plane 列表和详情、自动本地交付及审计。多仓库各自记录结果。暂不支持远端推送、冲突自动解决、跨机器文件操作。

## Work items

- [x] 1.1 从权威结果生成交付状态和本地合并入口。
- [x] 1.2 实现原目标记录、新完成事件排队、安全本地合并和持久化回执。
- [x] 1.3 移除额外合并授权对话框，更新国际化、文档并新增定向测试。
- [x] 1.4 交付本次 Sudoku 提交，保留已有工作区改动。

## Acceptance

- 编码及审查完成、尚无交付回执时显示待合并，不显示全部交付完成。
- 已确认的新计划完成后自动尝试本地交付；本地合并提交无需额外授权。历史计划不自动排队。
- 请求绑定当前计划版本、仓库、结果提交、目标分支和 HEAD；自动交付还必须匹配计划确认时记录的原目标。
- 成功后记录前后提交和操作者；重复请求不重复合并；未知结果不自动重跑。
- 原工作区无关修改保持原样，冲突和权限失败保留可恢复证据。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- Independent：涉及 Git 原工作区写入、权限及新的持久化交付记录。独立 Reviewer `reviewer-fd053-20261027-static` 已完成一次合并实现静态审查，结论通过；本轮发现均已修复。报告：[独立审查](reviews/20261027-independent-static-review.md)，来源事件 `FD-053-000003-review-requested`。报告保留测试快照、最后固定参数仅静态/编译验证、生产 runner 未动态验证及尚待备份重启的边界。
- Sudoku 已交付至原仓库 master：合并提交 f9962dc584739f4e87fa47bca483c785fda14b8b，父提交为 c8eded097ed05bcdcabb7f4d41f1861b90f7e738 和 00b7bddcc1047f2dd9f0fdaef6cdc0be8cbc2e44。相对原 master 仅 sudoku2.htm 增加 47 行，sudoku.htm 与原有本地改动、.aiw/ 保留。未推送远端。
- 已执行离线 `mvn -o -DskipTests=true -Dmaven.test.skip=true compile`，315 个 Java 源文件编译通过；两份 UI JavaScript 的 `node --check` 和 scoped `git diff --check` 通过。
- 人工恢复接口要求人类所有者会话；后台交付仅消费平台完成事件生成的 READY 记录。禁止调用方指定内部自动模式。
- 最新源代码离线编译通过。`mvn -o -Dtest=WorkflowGitDeliveryServiceTest,WorkflowReviewedCompletionTest test`：22 项通过（新增交付测试 10 项、既有完成状态测试 12 项），失败/错误/跳过均为 0。交付测试使用临时 Git 仓库及 SQLite 数据库，没有调用模型或修改真实任务仓库。尚未独立审查、应用 V21 或重启服务。用户已授权定向测试、独立静态审查，通过后备份数据库并重启加载。
- 同一次独立审查中修复 ignored 本地文件覆盖风险、清除 branch mergeOptions 并补齐失败提示翻译；相关修复后复跑同一组测试 23 项通过（交付 11 + 完成状态 12）。测试 Git runner 用隔离配置执行真实 Git，不覆盖生产 ProcessBuilder 的全部行为。随后追加固定 `--strategy=ort` 和 `--no-verify-signatures`，这两个固定参数尚未运行测试，只做静态审查及最终编译检查。未扩展至全库测试或完整浏览器验收。

## Sources

- 用户在本会话确认：完成后应该合并或者请求授权合并。
- 用户后续明确：本地合并提交无须申请授权；已经授权定向测试、独立审查，通过后备份数据库并重启。

## Activation evidence

- 独立审查真实通过事件：`FD-053-000004-verification-passed`，来源 `FD-053-000003-review-requested`，报告见 `reviews/20261027-independent-static-review.md`。最后两个固定 Git 参数的最终离线 compile-only 成功。
- 无 CLAIMED/RUNNING 任务时备份 SQLite 至 `D:\temp\data\aidev-r3.before-FD053-20261027-070524.db`，4,079,616 bytes，备份 `PRAGMA integrity_check` 为 `ok`。
- 停止已识别的 Control Plane Java 进程，使用现有环境离线启动（跳过测试）；启动器 PID 20428。登录页 HTTP 200，Flyway V21 success=1；历史完成任务没有自动生成 READY/MERGING 交付。
- 原生数独计划 `FP-bdad1ef7-eac5-47c3-8d85-eee36eebcf45` 的 preview 确认结果已合并，目标 master、HEAD `f9962dc584739f4e87fa47bca483c785fda14b8b`。通过正式人类 API `/git-delivery/merge` 记录 DELIVERED，未再次执行 Git merge；计划 state/displayState 均 COMPLETED，详情页和列表 HTTP 200，目标列表行显示 COMPLETED。
- 首次列表诊断按固定长度截取 HTML，误读相邻计划并触发断言；改为精确匹配原计划的 `<tr>` 后只读复核通过，没有重复提交合并。运行回执在本机临时目录 `aiw-goal-execution/fd053-activation-receipt.json`。
- 未做完整浏览器验收、生产进程中断实验、全库测试或远端推送；未提交 aiw-de 开发仓库中的修改。
