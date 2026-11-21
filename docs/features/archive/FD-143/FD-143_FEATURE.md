# FD-143: 按当前工作召回共享经验

**Status:** Complete
**Revision:** 2
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

只取最近几条共享经验会让不相干的内容挤掉较早的相关结论。

## Options and decision

使用现有 SQLite 和成员快照。以声明工作位置、Skill 和主题关键词排序，显式添加的参考资料继续随任务发送。匹配范围是同一操作者最近 128 条共享经验。

## Solution

同位置优先，同 Skill 次之，关键词用于补充匹配。同分保留较新结果。分数为零的经验不发送。工作流在分发时记录至多八条经验的任务 ID，同节点领取、重试和问答续接使用同一选择。许愿讨论在提交时选取相关经验，已有冻结讨论快照保存其内容。

## Scope

现有 owner 范围内的共享经验。已确认的成员资料继续可用。引用含来源任务、Plan、冻结成员配置版本及材料摘要。

## Work items

- [x] 1.1 排序、过滤并固定本节点的召回结果。
- [x] 1.2 保留来源版本，验证操作者隔离和显式资料。

## Acceptance

较旧的同项目或同 Skill 经验可以排在较新的次要命中前。查询、读取都约束操作者。此前选定的参考资料不受排序影响。

## Verification

离线 Maven compile 和 `TeamMemberSettingsTest` 的两条定向用例通过，覆盖项目、技能、关键词、跨操作者过滤及来源版本。未使用实际线上模型检验召回后的答复表现。

## Sources

- [改进清单](../../../AGENT-IMPROVEMENT-BACKLOG.md)

**Completed:** 2026-11-21
