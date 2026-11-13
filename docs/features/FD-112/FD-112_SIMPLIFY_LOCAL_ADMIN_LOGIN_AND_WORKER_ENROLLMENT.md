# FD-112: Simplify local admin login and Worker enrollment

**Status:** Planned
**Revision:** 1
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 23, 24. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

本机限定模式自动管理员，开放远程时必须登录；统一首期管理员角色。本机 Worker 自动登记，外部使用管理员接入凭据，重连保留身份，撤销阻止连接和接单。

Dependencies: none. 本文件为设计草案。进入实施前核对附近代码，再补充最小改法和实际受影响的契约；不得扩大已确认范围。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

## Work items

- [ ] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [ ] 1.2 实施已确认行为，删除因此不再需要的代码。
- [ ] 1.3 编译受影响源码、自查差异并完成适用的审查。

## Acceptance

本机与远程信任模式明确；不能通过来源地址绕过远程登录；身份不因进程重启重复创建；撤销不暗示远端进程已停止。

## Verification

目前仅有草案，未实施、未通过运行验收。先做编译和有界静态检查；额外测试、安装、服务、迁移及真实 API 需另行授权。根据实际差异复核审查策略。

## Sources

- docs/FUNCTION-INVENTORY.md, 23, 24.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。
