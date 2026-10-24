# FD-048: 秘书任务过期与跨停机执行确认

**Status:** Open
**Revision:** 3
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户确认统一恢复规则：过期定时不补跑；马上执行任务跨停机仍未开始时询问执行或取消；执行中断先核对结果；同进程正常排队不反复确认。当前 daily.tick 对任何已到 dueMs 的 SCHEDULED 都执行，且没有持久区分马上与定时。旧协调器已有一分钟执行窗口，不改其既有语义。

## Options and decision

在私人 work JSON 中追加 executionMode=IMMEDIATE/TIMED。安排 API 保持原协议：run-work 显式传入 IMMEDIATE，runtime.schedule 使用 TIMED 默认值。服务构造时将尚未开始的 IMMEDIATE 转 NEEDS_CONFIRMATION；TIMED 若错过 dueMs+60000 则转 NEEDS_CONFIRMATION/MISSED_TIME；未知模式旧 SCHEDULED 保守转确认，不猜原意。未来 TIMED 仍可正常到点处理。选择复用已有 run-work/schedule/cancel，不新增公共 API 或迁移。该设计涉及持久化恢复语义，须一次独立设计审阅；最终实现审阅另行进行。

## Solution

tick 先判定执行方式与过期，再考虑暂停及启动。IMMEDIATE 在同进程排队不限时；不通过刷新、启用、恢复自动确认。服务启动的 DAILY_RUNNING 保持 NEEDS_HUMAN/INTERRUPTED 且原运行 decision 变 UNKNOWN，不重放。Web“待你确认”同时显示恢复/错过/中断工作：现在执行、重新定时、取消；中断按钮明确“已核对结果，重新执行”。桌面工作摘要显示报告并提供明确重试/取消入口。取消及重新安排沿用 revision 和原专业任务/忙碌校验。

## Scope

workers/personal-secretary 的 daily_assistant.py、main.py、Web app.js/index.html、desktop.py、README 及本 FD 证据。主工作区 develop 范围提交。仅追加 JSON 字段，无 SQL schema 迁移；旧字段缺失转确认，历史完成/取消/移除不变。恢复规则会改变未开始旧安排行为，属用户授权范围。无自动重试、依赖、权限、平台动作协议、停机/重启/模型运行。对运行结果不确定的专业任务保留原 pending 保护。

## Work items

- [x] 1.1 保存模式并实现启动/到期状态分流。小/中，无依赖，证据为 JSON 更新、构造及 tick 各分支。
- [x] 1.2 Web 恢复确认列表及桌面报告/执行/取消入口。小/中，依赖 1.1，证据为原 API 调用及可见用户选择。
- [x] 1.3 同步文档、静态检查与最小 Python/JS 编译，提交 Worker 证据并独立审阅。小/低，依赖 1.2。

## TODO

独立设计审阅 r1 ready，1.1–1.3 已实施；静态差异检查、三份 Python 内存编译与 JS 语法编译通过。等待独立实现审阅。

## Acceptance

1. 未来 TIMED 正常等待，定时任务在 dueMs+60000 后标记错过并不启动，暂停跨窗口也不补跑（1.1）。
2. IMMEDIATE 同进程正常排队不超时；服务重启后仍 SCHEDULED 的马上任务只进确认，不自行启动（1.1）。
3. 旧无模式的未开始 daily 安排先确认；完成、取消、移除不复活；运行中断仍要求核对且不重放（1.1）。
4. Web 可看到恢复/过期/中断说明，并明确选择现在执行、改期或取消；桌面显示报告，提供核对后重试及取消入口（1.2）。
5. 重新执行/改期采用当前 revision 与原服务端专业任务/忙碌保护，取消复用 cancel，不绕过暂停或权限（1.1–1.2）。
6. 文档说明一分钟容差及旧数据兼容；静态/编译和未运行项如实报告（1.3）。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.

## Sources

- Issue: none
- 用户本轮授权及前两轮规则确认；daily_assistant.__init__/schedule/tick/run，main /api/run-work 与 /api/cancel，runtime.schedule，store.put/cancel_record，Web/桌面现有安排入口。
- Session: secretary-recovery-20261024-f46715；设计来源 FD-048-000002-design-requested。

- 设计证据：docs/features/reviews/FD-048-design-r1.md 及 JSON；独立审阅 ready，无阻塞。
