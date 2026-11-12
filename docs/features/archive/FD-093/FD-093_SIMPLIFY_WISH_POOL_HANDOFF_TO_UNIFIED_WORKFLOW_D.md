# FD-093: Simplify Wish Pool handoff to unified workflow drafts

**Status:** Complete
**Revision:** 5
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

许愿池仍需要保存编辑草稿、配置软件角色、请求分析、应用推荐、准备旧 Plan，无法直接进入统一工作流。

## Options and decision

使用普通只读文本任务整理已确认需求，结果直接保存为统一工作流草稿，由用户检查后再启动。不引入独立推荐实体、新模型循环或数据库表。

## Solution

需求快照记录普通整理任务和最终工作流 ID，复用已有 editor_json 作为引用存储。用户只操作“生成工作流草稿／查看草稿”，不再维护角色、分析和应用动作。任务结果必须通过统一 Spec 校验，保留原需求，生成草稿不执行其业务节点。旧执行仍可查看，其他尚未迁移的旧调用不在本次删除。

## Scope

覆盖许愿池快照到工作流草稿的 UI/API、生成结果校验、执行页面直达和国际化。附件／知识读取仍沿用已有明确选择机制，不自动向模型发送未选附件；本次仅使用已确认快照中的需求正文与来源元数据。美术形象和完整知识库接入后续处理。

## Work items

- [x] 1.1 通过普通 Worker 任务整理工作流草稿，并保存去重引用。
- [x] 1.2 用户进入统一工作流编辑器，确认后才执行业务。
- [x] 1.3 移除许愿池角色／分析／应用推荐操作并更新中英日文案。

## Acceptance

重复点击复用原整理任务；无 Worker 时显示实际等待，不假报草稿完成。仅真实 COMPLETED/PASS 的最后文本结果可导入，模型 JSON 无效时保留原结果并报告原因。不因读取状态重新提交任务。正文不会被模型改写，未选私有附件不自动发送。

## Verification

- 本轮仅离线编译 Java/JS 和独立静态审查，不调用模型、服务或网络，不运行测试。
- 2026-11-13：JDK25 下离线 Maven compile 通过；node --check 编译 goals.js、workflows.js、console-translations.js 通过。未安装依赖、执行测试、启动服务、迁移或调用真实模型。
- 生成仅发送已确认正文、标题、缺项和 ready 状态，不发送快照中的附件/知识元数据或私有原文。输入超出当前整理任务上限时明确拒绝，不截断需求。模型草稿保留完整原需求，不采纳模型改写的目标。
- 工作流定义可通过自身 ID 直接读取，不依赖最近 100 项列表；生成任务完成后只导入草稿，业务启动仍需用户操作。独立静态审查结果为 changes-requested；报告 [review-20261113.md](reviews/review-20261113.md)，覆盖 43edac8 对 3c44755，来源事件 FD-093-000002-review-requested，Reviewer codex-review-fd093-20261113。发现：草稿导入失败后 importAttempted/signature 阻断现有 Refresh 恢复，后续请求失败前清除来源且轮询清掉错误提示。

- 首轮独立审查发现前端导入失败后无法重试；已修复：显式 Refresh 重置导入状态并复用同一幂等读取，真正打开编辑器后才清来源；失败后停止导入自动轮询并保留错误。切换到其他执行不再携带原愿望导入意图。JS 编译通过，聚焦静态复核已通过。

- 2026-11-13：独立静态复核通过（verification-passed），首轮 P2 已解决，无剩余阻塞。报告 [review-20261113-recheck.md](reviews/review-20261113-recheck.md)，覆盖修复提交 4767242，来源事件 FD-093-000004-implementation-ready，Reviewer codex-review-fd093-20261113。未运行测试、服务、浏览器、网络或模型，不宣称运行验收通过。

## Sources

- docs/FUNCTION-INVENTORY.md 第 01、02、03、06 项；FD-091、FD-092。

**Completed:** 2026-11-12
