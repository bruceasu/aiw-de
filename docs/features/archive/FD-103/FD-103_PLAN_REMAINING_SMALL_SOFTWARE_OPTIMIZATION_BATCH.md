# FD-103: Plan remaining Small Software optimization batches

**Status:** Complete
**Revision:** 2
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

37 项清单中的剩余优化尚无完整实施安排。用户要求先建立全部 FD 草案，再逐个以 fd-workflow auto 完成，遵守 Small Software。

## Options and decision

按真实关联范围合为 18 个后续 FD，不机械地为每个原编号创建重复任务。先在本规划 worktree 内提交设计草案；轮到实施时才为对应 FD 创建独立 worktree。

## Solution

FD-104—121 覆盖清单全部剩余范围，按依赖逐项实施。草案记录范围、验收、依赖和验证边界；进入实施前核对实际代码，补充最小改法。未确认的自主权限与交互选择不自动变为已授权需求。

FUNCTION-INVENTORY 增加范围与 FD 对照；生命周期以各 FD 和工具记录为准，不在清单新增另一套状态机。最先处理旧独立图页面，其后继续处理无依赖的优化，依赖任务完成后再推进对应项目。

## Scope

仅规划和文档，不实施后续 FD 的代码，不把生成草案视为完成优化。保留原 37 项编号及现有完成记录。

## Work items

- [x] 1.1 读取剩余清单与已确认规则，按关联范围分组。
- [x] 1.2 分配 FD-104—121，写入简短草案和依赖。
- [x] 1.3 更新清单对照，静态自查后本地交付，随后开始 FD-104 auto。

## Acceptance

全部剩余原编号均有后续 FD 承接，无完成状态误报；每个草案有可观察的验收条件及明确范围，不引入未确认的新平台。实施逐个进行，不同时打开 18 个工作副本。

## Verification

本 FD 仅文档，Self 自查；不需编译或独立审查。只检查范围覆盖、依赖、文档格式和草案与交付的区别。未执行测试、服务、迁移、安装、真实接口或部署。

已核对：18 个草案覆盖原 37 项全部剩余范围，依赖指向本次实际分配的 FD；清单仅增加对照，原完成记录不变。`git diff --check` 通过。后续 FD 均为 Planned，未将设计草案计为完成优化。

## Sources

- docs/FUNCTION-INVENTORY.md。
- 用户：继续全部剩余优化，可先生成全部 FD，再逐个 fd-workflow auto；重申 Small Software。

**Completed:** 2026-11-13
