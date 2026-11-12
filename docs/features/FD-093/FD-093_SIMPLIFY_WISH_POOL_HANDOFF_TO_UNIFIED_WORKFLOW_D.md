# FD-093: Simplify Wish Pool handoff to unified workflow drafts

**Status:** In Progress
**Revision:** 1
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
- 工作流定义可通过自身 ID 直接读取，不依赖最近 100 项列表；生成任务完成后只导入草稿，业务启动仍需用户操作。独立静态审查待进行。

## Sources

- docs/FUNCTION-INVENTORY.md 第 01、02、03、06 项；FD-091、FD-092。
