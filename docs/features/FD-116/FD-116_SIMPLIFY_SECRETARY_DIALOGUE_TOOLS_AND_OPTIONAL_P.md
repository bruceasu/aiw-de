# FD-116: Simplify secretary dialogue tools and optional Plane delegation

**Status:** Planned
**Revision:** 1
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 29, 31, 35. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

对话直接问答和简单操作；独立需求澄清复用许愿池，不强制提交 Plane。需要团队时明确委派、最少提交内容并跟踪；文件操作按需授权，缺信息询问，不只给笼统失败。

Dependencies: FD-108, FD-109, FD-112. 本文件为设计草案。进入实施前核对附近代码，再补充最小改法和实际受影响的契约；不得扩大已确认范围。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

## Work items

- [ ] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [ ] 1.2 实施已确认行为，删除因此不再需要的代码。
- [ ] 1.3 编译受影响源码、自查差异并完成适用的审查。

## Acceptance

简单提问不擅自创建文件；秘书可不连 Plane 独立工作；委派可直达执行；同机管理与远程连接按已确认边界处理。

## Verification

目前仅有草案，未实施、未通过运行验收。先做编译和有界静态检查；额外测试、安装、服务、迁移及真实 API 需另行授权。根据实际差异复核审查策略。

## Sources

- docs/FUNCTION-INVENTORY.md, 29, 31, 35.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。
