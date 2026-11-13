# FD-105: Retire duplicate software workflow and recommendation paths

**Status:** Planned
**Revision:** 1
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 02, 04, 08, 13, 15. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

软件开发作为普通工作流应用；退役旧专用工作台和隐式流转，清理不再被调用的推荐服务。保留执行结果与必要工具和 Skills，默认不破坏历史记录。

Dependencies: FD-104. 本文件为设计草案。进入实施前核对附近代码，再补充最小改法和实际受影响的契约；不得扩大已确认范围。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

## Work items

- [ ] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [ ] 1.2 实施已确认行为，删除因此不再需要的代码。
- [ ] 1.3 编译受影响源码、自查差异并完成适用的审查。

## Acceptance

只保留一套日常创建与控制入口；旧推荐与隐式评审不再驱动新工作；历史执行仍可查看；无调用方的代码删除。

## Verification

目前仅有草案，未实施、未通过运行验收。先做编译和有界静态检查；额外测试、安装、服务、迁移及真实 API 需另行授权。根据实际差异复核审查策略。

## Sources

- docs/FUNCTION-INVENTORY.md, 02, 04, 08, 13, 15.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。
