# FD-090: Implement the confirmed Workers page design

**Status:** Design
**Revision:** 1
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

按用户已确认方案实现 Workers 页面，使管理者能清楚查看 Worker 当前工作及可用操作。用户于 2026-11-11 要求建立独立 FD；FD-088 的用量、价格采集和计费工作继续由 FD-088 管理。

## Options and decision

在现有 Web Console Workers 页面和 Fleet 查询基础上实施；复用已有视觉样式和任务／执行入口，保留服务器授权、CSRF 和真实状态校验。不得把已归档 FD-003 自动当作此次新方案。

%% NEEDS_INPUT: 用户已说明方案在 `/side` 讨论中；当前主对话及可读取的近期任务记录尚未包含该侧边讨论的方案正文。需将 `/side` 中的确认结论带回主对话或保存到仓库文档。取得后在此记录来源并补齐具体验收，相关页面实施暂不开始。

## Solution

实现入口为 `src/main/resources/templates/ui/workers.html` 及其现有查询和交互代码。具体信息布局、筛选、详情展示和操作流程以待补的确认方案为准。加载、空列表、刷新失败、状态竞争及权限不足必须有可理解的反馈；复用现有国际化语言。没有数据的状态显示未知，不把停止请求显示为已经停止。

## Scope

范围为已确认的 Workers 页面方案。新 FD 的创建不代表新授权的工具权限、调度算法、模型运行时、数据库迁移或 Worker 生命周期改造；如方案需要这些变更，应在实施前明确其合同及风险。FD-088 的费用计算和采集器安装不并入本 FD。

## Work items

- [ ] 1.1 定位并记录已确认方案，核对当前页面与查询合同，补齐具体验收。
- [ ] 1.2 按方案实现 Workers 页面布局、状态及已有工作关联入口。
- [ ] 1.3 实现方案约定的交互与操作反馈，核对权限、停止语义及刷新状态竞争。
- [ ] 1.4 完成国际化、邻近文档、静态自查和源码编译；按实际影响重新判定评审策略。

## Acceptance

- 页面布局及用户操作与补齐后的已确认方案一致。
- 遵循现有 Web Console 样式，界面文案和新增控件覆盖现有支持语言。
- 展示真实 Worker、任务和执行状态，保留已有可达入口和服务器端操作校验。
- 失败、未知、等待和已停止状态有明确区别，刷新不覆盖用户正在编辑的内容。
- 方案中的具体布局与操作验收尚待 1.1 补齐，不据此宣称设计已就绪。

## Verification

- 仅完成 FD 建立与初步范围记录，尚未修改页面、运行编译或测试。
- 已静态检查 FD-003 历史设计及现有 Workers 页面位置；尚未核实此次确认方案。
- 默认源码编译和静态检查。额外测试、依赖安装和真实服务调用沿用用户此前的单独授权限制。
- 初始 Review policy 为 Self；如实际改动涉及权限、公共合同、持久化或并发，调整为 Independent。

## Sources

- Issue: none
- 用户 2026-11-11 请求：“创建新的FD：按已确认方案实现 Workers 页面”。
- 当前入口：`src/main/resources/templates/ui/workers.html`。
- 历史参考：`docs/features/archive/FD-003/FD-003_WORKER_FLEET_AND_SCHEDULING_EXPERIENCE.md`，非此次方案的替代依据。
