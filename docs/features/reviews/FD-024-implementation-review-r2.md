# FD-024 第 2 轮独立实现审核

<!-- aiw-data: FD-024-implementation-review-r2.json -->

结论：**verification-passed**。第 1 轮的 R1-F1、R1-F2、R1-F3 均已解决；本轮修复及相邻调用链未发现新的阻断缺陷。此结论支持既定范围的本地交付，不是运行时、真实迁移或上线结论。

Reviewer 独立身份：`reviewer-fd024-r2-20261021-90c42`，不同于 Worker `01a15e5f-d79d-73e0-bb3b-480e76aa474c`。先确认最新 pending receipt，再 claim `FD-024-000009-implementation-ready`。本轮是实现审核 2/3；修复比较 `b25b487..d8470cd98461f3559bc82a39c254eb1aff547b23`，完整 FD 原始基线 `c182720`。保留原审核及修复报告，不改写第 1 轮结论。

## 原发现的源码复核

- **R1-F1 resolved**：V10 直接从 V9 `delegation_security` 的单例行继承 `legacy_auth_required`，不再固定写 FALSE。新 `AuthorityBoundary.humanAuthRequired` 与 Console 拦截器读取这份持久事实；既有 UI 的 auth-disabled ADMIN 回退受永久锁存拦截。V8/V9 原迁移保持原样，没有恢复旧执行模式。
- **R1-F2 resolved**：`AuthorityGrantService.permitted` 共用 `targetCurrent` 核验 owner 与暂停；直接和间接发布写入单次许可时再次核验独立 PUBLICATION 目标，并保存 targetKey、owner、generation、restrictionRevision。`AuthorityTaskPolicy.live` 在 claim/actual start 对照这些值，同时保留原主 PLAN 目标、授权版本、身份与 DENY 检查。已核对 V10 新增列及 INSERT 的十列十参数、唯一写入位置与读取位置，未发现列/参数不一致。
- **R1-F3 resolved**：普通人工技术重试在修改 Plan/Task 前调用 `beforeHumanTaskReplacement → checkReplacement`，在已经取得的权限首锁及原事务内拒绝 UNKNOWN/IN_PROGRESS。人工新建及重用任务许可均走 `replaceHumanTask`，再次检查后保存旧 task+attempt 快照（含原 request 关联），再建立清空旧授权/身份/request 的 HUMAN 许可。专用 reconcile 仍要求精确 attempt、明确结论和证据；历史消耗与未知占用没有新增自动退款路径。

## 完整 FD 的验收与证据

本轮结合第 1 轮已审完整实现、原 Worker 报告 `reports/FD-024-implementation.md` 与本轮 `reports/FD-024-implementation-r2.md`，检查修复是否改变既定验收边界。确认的静态依据如下：

| 验收 | 依据及结论 |
| --- | --- |
| AC1–AC2 | 独立 Python 进程、owner/Worker 绑定私库、私有记录/版本与模型/调度；平台 authority 仅保存执行与权限事实。既有私人历史封存，不声称已清理或迁移。 |
| AC3、AC9 | 独立机器身份、人工 session/CSRF/当前角色、只读 owner/scope、独立个人 UI session/Host/Origin；本轮保留并修复永久认证锁存。 |
| AC4 | 稳定实例+owner+requestId 去重、原事务回执/预算、私有原信封 outbox；UNKNOWN 不自动重放，人工重试漏洞已由共同替换关口修正。 |
| AC5 | 权限首锁、身份/目标/禁止世代和启动复查；PUBLICATION 附带边界不再被主 PLAN 能力旁路。 |
| AC6–AC7 | 20 闭集动作、独立累计账本、历史参与隔离、模型实际 start、固定发布 attempt；本轮未改变预算键或通过替换 Worker 重置消耗。 |
| AC8 | 真实专业结果照实记录，托管后续推进需新准入；人工接管与证据对账仍可独立于个人进程操作。 |
| AC10 | 只读离线迁移规划、明确映射、未验证/混合记录封存、ARCHIVE_ONLY 与执行入口拒绝；实际迁移/切换仍需未来独立授权。 |

最新用户决定是完整实现、取消旧执行模式兼容、迁移仅实现工具且不实际迁移或切换。G1 已据此作为未来执行 Gate 处理，不把缺少真实源库/owner 清单重新当作工具实现的未答问题。运行测试未获授权，不作为新增验收条件；没有把静态证据说成运行通过。

Worker 的修复报告记录 2026-10-21 23:14:47 JST compile-only 通过，范围与原脚本一致；Reviewer 阅读该报告及实际修复，没有重跑编译或测试。SQL/Spring、浏览器、并发与外部效果故障窗口仍未经运行验证，是明确保留的证据限制。

实际执行：PowerShell `Get-Content`、`Select-Object`、`rg`，`git status/log/diff`，最新 receipt 的 `aiw fd show/claim`，双文件和 FD 编辑、一次只读证据/diff 检查，以及 scoped Git 提交和 `verification-passed` 交接。未运行测试、编译、SQL、服务、HTTP、模型、网络、实际迁移/切换、格式化或额外代理；未修改生产代码，未 merge/archive/push。

FD Revision 9 / Pending Verification 的 CLI 受控未提交变化与 Verification 一并提交。本报告不执行本地交付或归档，由 PM 按正常门禁继续。
