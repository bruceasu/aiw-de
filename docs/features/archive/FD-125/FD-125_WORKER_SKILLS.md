# FD-125: Worker 自主调整 Skills 并保留版本与修改依据

**Status:** Complete
**Revision:** 13
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Dual

## Problem

成员 Skills 目前只能使用人工设置的固定文本。Worker 执行中发现稳定、可复用的方法改进时，无法调整自己的 Skill，也没有版本或依据可供人检查和回退。

## Options and decision

扩展 FD-124 的同一个最终回合元数据块，允许 Worker 在确有改进时返回一个 Skill 更新；不增加模型调用。按成员和已配置的 Skill 保存不可变修订，包括完整内容、摘要、来源任务、Plan 和修改理由。无需引入第二套 Skill 仓库或可执行插件。

## Solution

Worker 只可调整其已配置的 Skill 方法文本。成员首次配置或使用时，将当时的内置文本固定为不可变的版本 0。服务器核验更新引用属于任务冻结成员的 Skills；每次有效更新形成新版本，记录来源任务和修改理由，并成为该成员之后派发任务的版本。Plan 任务和许愿池讨论都使用该成员的最新 Skill；已领取任务继续使用已冻结版本。Agent 工厂成员详情可检查版本和差异，并恢复先前版本（恢复也产生新版本）。

Skill 内容不能授予工具、扩大文件范围或修改权限策略；工作流结构变更仍须人工确认。缺少、无效或过长的 Skill 更新只跳过该更新，不影响任务完成或独立经验保存。Skill 更新与 FD-124 的共享经验摘要复用同一元数据块和模型回合。

## Scope

只做成员已配置 Skill 文本的自动修订、不可变历史、来源依据、查看差异与恢复。复用 FD-124 的完成元数据和执行时 Skill 快照；不实现可执行插件、权限调整或自动工作流重构。

## Work items

- [x] 1.1 扩展 FD-124 完成元数据块为可选 Skill 修订，并校验任务成员和 Skill 引用。
- [x] 1.2 追加保存不可变成员 Skill 版本及来源、理由；后续任务固定最新有效版本。
- [x] 1.3 在 Agent 工厂成员详情展示版本与差异，并支持生成新版本的恢复操作。
- [x] 1.4 编译并自查修订隔离、执行快照和权限边界。

## Acceptance

成员可根据完成工作改进自己已配置的 Skill；每个版本均可追溯到来源任务和修改理由，旧任务使用的版本不变。未来任务固定最新版本。Worker 可查看差异并恢复旧版；恢复不删除历史。无有效修改时不产生新版本。Skill 修改不会改变工具或文件权限，也不会自动改工作流结构；Skill 更新与共享经验只使用原有最终模型回合。

## Verification

- Review policy is Independent because this change adds persistent skill history,
  completion data and a member-scoped restore API.
- `git diff --check` passed.
- Java compile passed: offline Maven `-DskipTests compile` using JDK 25 and the
  FD-081 offline settings/local repository. The first PowerShell invocation
  split the repository property and stopped before compilation; the corrected,
  quoted invocation compiled 367 Java sources successfully.
- Python syntax compilation passed:
  `python -m py_compile workers/process-python/process_worker.py workers/process-python/skill_files.py`.
- Tests, migration execution and browser interaction were not run. The
  migration and end-to-end UI behavior remain unverified at runtime.
- Independent review requested changes; report: `docs/features/FD-125/reviews/review-20261114-r1.md`.
- Findings: built-in baseline version 0 is not persisted, Goal discussion tasks bypass member Skill revisions, and the experience metadata example may be stored literally.
- Repairs: persist immutable baseline version 0 when a Skill is first configured, used or opened; apply and record revisions for owner-verified Wish Pool discussions; use `NONE` as the experience example and explicitly reject placeholder copying.
- After those fixes, `git diff --check`, offline Maven `-DskipTests compile` (367 Java sources), and Python `py_compile` for `process_worker.py` and `skill_files.py` passed.
- The review findings are addressed; a focused independent recheck is pending. Tests, migration execution and browser interaction remain unrun.
- The first review request was rejected because compile/self-check and review
  shared one unfinished Work Item. Review is a separate delivery gate, not an
  implementation Work Item; all implementation and self-check items are now
  complete.
- Independent re-review of R1-R3 passed; report:
  `docs/features/FD-125/reviews/review-20261115-r2.md`. The Reviewer relied on
  the implementation report's compile evidence and did not rerun compilation
  or tests. Migration and runtime UI behavior remain unverified.

## Sources

- FD-124：自动保存并复用已完成任务的共享经验。
- `docs/features/archive/FD-113/FD-113_GIVE_MEMBERS_SCOPED_SETTINGS_AND_SOURCE_BACKED_E.md`：现有成员资料与显式来源经验实现。

**Completed:** 2026-11-14
