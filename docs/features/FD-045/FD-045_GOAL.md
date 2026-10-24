# FD-045: Goal 删除结果提示与状态核对修复

**Status:** Open
**Revision:** 3
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户删除 Goal 后看到“未确认”，误以为需要寻找第二个确认入口。静态证据：goals.js 将网络失败、响应异常和 HTTP 拒绝全部包装成“请求未确认”；Controller 收到删除请求即按 actor/revision 同步保存，没有待审批状态。实际这次请求失败的运行原因尚未取得。

## Options and decision

不新增审批流程。选择区分明确拒绝与结果未知，提供对原目标的只读核对入口，而非仅让用户刷新最近 100 条列表。沿用后端协议，无需独立设计审阅；最终独立实现审阅必需。

## Solution

保留提交前确认弹窗。HTTP 4xx 明确说明请求被拒绝并展示原因；网络、5xx 和无效成功响应说明结果暂不可知，明确无需第二次确认。常驻提示及“核对删除/恢复结果”按钮按原 ID GET 读取当前状态；不会重放写入，读失败可再次核对。保留编辑及原有 revision、CSRF、归属和恢复语义。

## Scope

仅 goals.js、goals.html、附近用户说明及本 FD 证据。在用户已选择的主工作区 develop 进行范围提交。不改变权限、后端、数据库或删除语义。主要风险为写请求响应丢失；只读核对可显示当前状态但不能证明具体是哪次请求造成。无运行时复现、测试、服务重启或部署授权。

## Work items

- [x] 1.1 区分拒绝与结果未知，保留错误详情和登录入口。小/中，无依赖，证据为 API 错误处理及删除调用链。
- [x] 1.2 增加原目标只读核对入口与清晰结果提示。小/中，依赖 1.1，证据为模板、GET 路径和编辑保护。
- [x] 1.3 更新使用说明，完成一次静态检查和 JS 编译检查、Worker 报告及独立实现审阅。小/低，依赖 1.2。

## TODO

1.1–1.3 已实施，静态差异检查及 JS 语法编译检查通过；等待独立实现审阅。实际运行错误原因未复现。

## Acceptance

1. 删除前仍须确认；成功直接提示删除完成，无后续审批入口（1.1）。
2. 4xx 展示拒绝与错误原因；其它不确定响应不宣称已提交成功或待人工确认（1.1）。
3. 结果未知时显示原目标名称与只读核对按钮；按原 ID 查询而非依赖列表前 100 条，读取失败保留入口（1.2）。
4. 核对显示实际已删除或未删除状态，不重复写入、不清空未保存编辑；原登录、归属、CSRF、revision 协议不变（1.1–1.2）。
5. 使用说明解释无需二次确认，证据区分静态结论与未运行项（1.3）。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.

## Sources

- Issue: none
- 用户本轮反馈；FD-036；goals.js、goals.html、GoalController.delete/restore、GoalDiscussionService.deleted、GoalRepository.get。
- Planner/Worker Session: goal-delete-20261024-682ecf；设计来源 FD-045-000002-design-requested。
