# FD-145: 秘书独立澄清并保存选定的需求草稿

**Status:** Complete
**Revision:** 2
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

秘书可以在本地澄清需求。用户需要从对话直接留下草稿，并准确引用讨论使用过的资料。

## Options and decision

继续使用私人对话、goal-discussion Skill 和私人草稿。用所选回复创建本机草稿。提交许愿池仍由用户通过既有选材、预览和确认入口发起。

## Solution

秘书回复下方提供「保存这条回复为私人草稿」。只保存该条回复与来源消息 ID。用来源消息形成固定记录 ID，重复点击和两个页面并发保存均无法覆盖已保存的草稿。Skill 指导助手整理独立草稿，引用实际读取的知识文档 ID 及版本，列出尚待核实的问题。

## Work items

- [x] 1.1 在现有私人资料增加所选回复的保存入口与反馈。
- [x] 1.2 更新讨论 Skill、国际化及委派边界核对。

## Acceptance

保存可在只连接私人服务时完成。保存的数据来自所选单条回复。外发只走用户显式选择的投影。提交回执未知时核对原请求。

## Verification

两份 JS 语法检查和静态差异检查通过。已有委派定向用例 5 条通过。另静态核对 `/api/record` 的版本写入、知识工具的文档版本与所选目标的原请求回执查询。浏览器保存和实际模型引用行为待运行。

## Sources

- [改进清单](../../../AGENT-IMPROVEMENT-BACKLOG.md)

**Completed:** 2026-11-21
