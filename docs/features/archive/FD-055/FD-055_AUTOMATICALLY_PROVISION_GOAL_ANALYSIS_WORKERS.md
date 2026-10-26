# FD-055: Automatically provision goal analysis Workers

**Status:** Complete
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

目标的正式版本已保存，但“同时分析步骤”返回没有在线 analysis Worker。原案例正式版本 35988632-f11a-4daa-b91c-b1336951e699 的 analyze API 返回 409，recommendationId 仍为 null。此前 FD-054 只处理讨论角色，本轮补齐 planner 自动启动。

## Options and decision

复用工厂已经配置的默认模型及进程管理。只读 availability 不启动进程；实际分析提交在无匹配在线 Worker 时按需启动 planner，启动沿用事务提交后执行及回滚清理。显式指定的已批准配置和外部 Worker 保留。

## Solution

WorkflowRecommendationService 默认解析工厂模型，显式约束继续筛选已批准配置。ManagedWorkerProvisioner 增加 planner 入口，禁用动态执行和 guard，使用只读角色。Python 对 recommendation 任务拒绝 workspace assignment、使用独立临时目录并开启纯文字模型模式，关闭原生工具。UI 明确区分正式版本已保存与分析提交失败。

## Scope

分析请求接入、模型冻结、Worker 启动、只读分析隔离及提示翻译。使用已有原案例版本分析并读取步骤建议，不自动创建或确认执行计划，不修改数独文件，不改变已有正式版本正文。

## Work items

- [x] 1.1 接入工厂模型和按需 planner 启动，保留外部 Worker/显式配置。
- [x] 1.2 隔离纯文字分析环境并修正 UI 提示和文档。
- [x] 1.3 离线编译及静态检查，准备限定 API 复验。

## Acceptance

- 原正式版本不需重新保存，analyze 可排队并最终取得 READY_TO_APPLY。
- 工厂未启用或配置不匹配时清晰失败，不静默切换显式模型约束。
- availability 无启动副作用；新进程只在实际提交的事务成功后启动。
- 分析不读写项目文件，不产生执行计划；原正式版本及目标正文保持不变。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- API 已复现 analyze 返回 409；原正式版本 sourceRevision 14/editorRevision 0/recommendationId null。
- 离线 `mvn -o -DskipTests=true -Dmaven.test.skip=true compile` 成功，315 源。未运行单元或全库测试。
- Independent：涉及新受管角色启动和模型配置冻结；审查无阻断后再备份重启并验证原版本。完整浏览器验证不在本轮。
- node --check 两份 JS、Python ast.parse 和 git diff --check 通过；没有运行单元、全库或完整浏览器测试。
- 独立静态审查无阻断后，确认没有 CLAIMED/RUNNING 工作，备份 `D:\temp\data\aidev-r3.before-FD055-20261027-080351.db`（4,423,680 bytes，integrity_check=ok），重启本机平台，launcher PID 4692，登录页 HTTP 200。
- 对原正式版本 `35988632-f11a-4daa-b91c-b1336951e699` 重新提交一次 analyze，无须新建目标或正式版本；创建 `WR-cfb6301a-e1c5-481e-be7a-9c0551a8a80e`，工厂自动启动 `factory-2728fcf864cf9b5fab7b0090`。API 观察 RUNNING -> READY_TO_APPLY，得到 plan/code 两步，模型输出通过后端校验。
- 在 editorRevision 0 未发生已保存编辑变更的条件下，通过 apply API 写入同一版本的编辑草稿，editorRevision 1、步骤数 2、planId null。原正式快照与目标正文逐字不变，没有创建或启动执行计划，没有修改数独文件。正式目标页 JS HTTP 200，服务源码与工作区一致。
- 真实运行回执在本机临时目录 `aiw-goal-execution/fd055-analysis-receipt.json`。受管 planner 保持在线供后续分析，由平台进程生命周期管理。剩余边界：availability 只报告可配置启动，不预判进程上限和历史 slot 故障，实际请求仍会明确拒绝；不把 API 成功描述为完整浏览器验收。
- 独立审查通过：`reviews/20261027-independent-static-review.md`，Reviewer `codex-fd055-review-20261027`，来源 `FD-055-000002-review-requested`。核对新增分析路径的模型冻结、事务后启动、纯文字隔离及原版本真实 API 回执，无阻断发现；Reviewer 未运行测试、模型或 API。

## Sources

- 用户贴出正式版本保存后分析 Worker 不在线的错误，延续已授权 API 调查修复。

**Completed:** 2026-10-26
