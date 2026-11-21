# FD-144: 在正式完成后沉淀 Worker 经验与 Skill 修订

**Status:** Complete
**Revision:** 2
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

受控任务可能已经收到 Worker 报告，仍需等待结算允许推进。此时的经验不应进入共享知识。并行任务也可能从同一旧 Skill 版本提出修改。

## Options and decision

复用已有完成回执、按尝试推进与成员版本记录。一条经验从原结果追到任务。已有历史版本继续供人查看与还原。

## Solution

非受控任务随完成事务沉淀经验。受控任务等待现有许可推进及最新尝试检查通过，在同一事务保存经验，然后分发后续节点。修订只接受来源回执为 PASS、该任务确实使用了成员 Skill，且冻结版本和摘要与当前版本一致的结果。成员归档或已移除 Skill 时放弃修订。

## Work items

- [x] 1.1 绑定成长记录与现有完成、结算和最新尝试。
- [x] 1.2 以任务使用的版本为 Skill 修订依据，保留已有历史及回退操作。

## Acceptance

等待结算、已被替换的尝试和失败结果不会供应自动成长。变更保留结果任务、原因和版本。较旧的 Skill 建议不会覆盖从此后产生的修改或恢复版本。

## Verification

离线 Maven compile 与 `GuardedGrowthTest` 通过。用 mock 验证等待及被替换结果不写经验，当前完成只在推进时沉淀且早于后续派发。对 Skill 版本检查与原有历史保留做静态核对。未访问实际 Worker 或模型。

## Sources

- [改进清单](../../../AGENT-IMPROVEMENT-BACKLOG.md)
- [成员 Skill 历史](../FD-125/index.md)

**Completed:** 2026-11-21
