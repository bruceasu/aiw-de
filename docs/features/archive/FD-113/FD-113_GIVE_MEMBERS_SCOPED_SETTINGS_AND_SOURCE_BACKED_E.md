# FD-113: Give members scoped settings and source-backed experience

**Status:** Complete
**Revision:** 6
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 09, 10, 27. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

成员绑定相关知识、模型与工具设定；从实际执行提出有来源的经验建议，用户查看、修改、删除或确认后采用。解散前可提取资料，不让经验自动扩大权限。

Dependencies: FD-107、FD-110（已交付）。直接扩展 TeamMembers 的现有保存、分配快照、导出和成员编辑表单；不增加成长服务、自动总结流水线或另一套工具权限。

- V48 给成员表增加一个 `settings_json` 字段，保存模型／强度偏好、明确允许的已注册方案、显式文本资料和有来源的经验。无凭据字段；不移动成员身份或进程。迁移只编写，不运行。
- 模型和强度通过既有 WorkflowAiPreferences 交给 Worker；任务明确值优先。存在全局已批准模型策略时，成员只能收窄，允许方案保存具体值而非仅可变名称；默认固定值不扩成自动切换范围。没有已注册方案时，不凭空提供可切换范围。
- 成员设定随既有 worker_team_assignment 的 Profile 一次冻结。引用资料通过 FD-107 的 referenceMaterials 按需读取，人工备注与确认经验也作为资料，不整段注入指令。已派发及已冻结分配不读取成员当前的新设定。
- 任务与成员合计沿用 8 项／512 KiB 的同一预算；成员最多 6 个知识文本，各 128 KiB，另给备注和确认经验各一项。显式选人启动前检查；自动选人派发前检查，超限记录失败且不创建任务。实际含资料的任务明确要求 task-materials-v1。
- 按需资料需要支持 task-materials-v1 的 SDK Worker；不向不支持该协议的备用运行时伪装资料可用，也不静默扩大内联上下文。
- 经验先复用实际 Worker 结果：用户选择成员参与过的执行，读取其所属节点最新 RESULT／FAILED 记录作为草稿，修改并保存后才作为经验采用。最多 8 条，每条 2000 字，保存时重新验证来源归属；不会新增一次模型调用或自动提升经验为通用规则。
- 设定、参考正文和确认经验可在解散前后导出；已解散成员继续只读。修改／移除经验只影响未来分配，不抹掉历史执行。个人私库不自动进入成员资料；上传和保存是显式提供参考资料的操作。
- 工具仍由已确认的任务策略决定；成员 Skills／模型／参考资料不授予文件或工具权限。当前通用成员仍是只读文字任务；文件工作入口归 FD-108，不用提示词假装已开放工具。
- 独立审查修复：成员只收窄目录，不另造 catalog version；统一在 AuthorityTaskPolicy 的 guidance／实际 selection 中实施，领取、启动和原生问答续接使用同一范围，再沿用全局目录选型与原问答固定模型校验。
- 同机 ChatGPT 的任务模型绑定也使用冻结的任务／成员模型偏好；没有目录策略时不再只把执行进程基准模型写入凭据约束。账户、连接版本、Worker 身份与租约校验保持原路径，实际目录选型仍优先于模型默认值。
- 编辑表单保留旧方案完整值并标出失效，编辑备注不会清空或换绑模型范围；必须明确重新选择当前方案后保存。经验来源同时要求实际 worker_team_task 关联与 COMPLETED／FAILED 任务，未来仅预分配节点不能作为来源。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

## Work items

- [x] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [x] 1.2 实施已确认行为，删除因此不再需要的代码。
- [x] 1.3 编译受影响源码、自查差异并提交独立审查；结论单独记录。

## Acceptance

同一成员设定可理解且实际生效；历史执行保持冻结；经验可追溯和纠正；私人内容不擅自共享；导出不含凭据。

## Verification

- 独立聚焦复核 `a2ccc49`（来源 `FD-113-000005-implementation-ready`）：Pass，首轮 2 项 P1、1 项 P2 均关闭。已核对统一 guidance/实际启动与续接、旧完整方案保留、真实结束任务来源及 OAuth 默认模型绑定的直接影响；详见 [复核报告](reviews/review-recheck-20261114.md)。Reviewer 未运行测试、编译、迁移或运行时验证，新增用例仅静态阅读，原证据限制保持。

- 独立静态审查（`a143840 → f0cb3c3`，来源 `FD-113-000003-review-requested`）：Changes requested，2 项 P1（成员 guidance 与真实启动/续接版本校验不一致；旧模型范围在 UI 编辑时静默清空/改绑）、1 项 P2（经验来源未核对实际派发关联）。详见 [审查报告](reviews/review-20261114.md)。Reviewer 未运行测试、编译、迁移或运行时验证。

初版及修复后均通过 `node --check src/main/resources/static/ui/team-members.js`、JDK 25 低内存离线 Maven `-DskipTests test-compile`；自查 `git diff --check` 通过。审查修复新增源码用例覆盖实际启动拒绝越界方案、目录版本保持一致，以及内存 SQLite 中“真实已完成任务／仅预分配节点／未完成任务”的来源差异。待限定复核。按用户限制未运行测试、迁移、服务、浏览器或实际模型／目录请求；编译和静态审查不等于运行验收。

## Sources

- docs/FUNCTION-INVENTORY.md, 09, 10, 27.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。

**Completed:** 2026-11-14
