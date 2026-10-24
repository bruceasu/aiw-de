# FD-047: 秘书任务支持马上执行与可选定时

**Status:** Design
**Revision:** 2
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户希望给秘书安排任务可马上执行，无需每次设定时间。现有 /api/run-work 已按工作 ID、revision 调用日常安排器设为当前毫秒；列表有“现在处理”，但主要安排表单只支持 required datetime-local，导致用户认为所有任务都必须定时。

## Options and decision

选择在同一安排表单提供默认“马上执行”和可选“定时执行”，复用 /api/run-work 与 /api/schedule。无需新增 API、时间哨兵、调度器或后端行为。纯界面接入，不需独立设计审阅；独立实现审阅仍必需。

## Solution

执行方式默认马上执行，时间输入隐藏、禁用且非必填；选择定时执行后显示时间并恢复必填，按原浏览器时区提交。马上执行只向原 run-work 传 id/revision，定时执行按原 schedule 传 workId/revision/dueMs/timeZone。模式字段不传入 API，卡片入口改为“安排执行”。提交防重、版本校验、暂停及专业任务防重均沿用原逻辑。马上执行只表示已交给现有队列，不伪称完成。

## Scope

仅秘书 Web 的 ui/index.html、ui/app.js、README 及本 FD 证据，沿用当前主工作区 develop 范围提交。无后端/权限/持久化/时间语义变化。不扩展桌面 UI。风险为隐藏时间仍触发校验、路由字段混用或提示误称完成，静态追踪防止。无测试、浏览器、HTTP、模型、重启、部署授权。

## Work items

- [ ] 1.1 实现执行方式选择及时间输入状态。小/低，无依赖，证据为模板和同步函数。
- [ ] 1.2 分流现有马上/定时接口并更新反馈。小/中，依赖 1.1，证据为提交请求、校验与防重路径。
- [ ] 1.3 更新说明、静态检查和 JS 语法编译及独立审阅。小/低，依赖 1.2。

## TODO

实施 1.1–1.3。

## Acceptance

1. 安排表单默认马上执行，空时间不阻止提交；定时模式才显示并要求时间（1.1）。
2. 马上执行向 /api/run-work 传真实工作 ID/revision，不生成浏览器“当前时间”；定时执行保留原时间及时区协议（1.2）。
3. 切换模式不丢失工作选择及已填写时间；保留提交防重、原后端版本/暂停/专业任务校验，结果不伪称执行完成（1.1–1.2）。
4. 周边文案明确两种方式，静态/编译结果及未验证项如实记录（1.3）。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.

## Sources

- Issue: none
- 用户本轮请求；workers/personal-secretary/ui/{index.html,app.js}；main.py /api/run-work、/api/schedule；runtime.schedule；daily_assistant.schedule/tick；README。
- Session: secretary-now-20261024-873d19；设计来源 FD-047-000002-design-requested。
