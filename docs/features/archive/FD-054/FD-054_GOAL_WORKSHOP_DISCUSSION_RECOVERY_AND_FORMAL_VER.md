# FD-054: Goal workshop discussion recovery and formal version feedback

**Status:** Complete
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

目标讨论没有响应，正式版本确认也缺乏可见反馈。API 复现目标 e71f1bc3-84df-4106-b462-2277e00eeeae：讨论因无在线 Worker 返回 409；正式版本实际已有两个，当前请求因未接受缺失信息返回 400。工厂 automaticWorkers=true，但旧讨论路径没有使用自动启动。

## Options and decision

复用工厂现有模型和本地进程管理，按需启动独立 goal-discussion Worker。讨论仅使用现有纯文字模型模式，无仓库绑定、动态执行配置或执行工具。保留外部 Worker 接入及原始版本快照，不自动消除缺失信息或确认执行计划。

## Solution

讨论提交先冻结选定后端配置；没有匹配 Worker 时请求受已有开关和进程上限约束的启动，事务提交后启动，回滚不启动。消息持久化后显示排队状态并有限轮询回复；失败明确呈现。正式版本涉及未接受缺失信息时使用统一确认框；保存后定位版本，不重复创建执行计划。操作提示在对应按钮附近显示，避免页面顶部不可见。

## Scope

GoalDiscussionService、GoalTaskPolicy、ManagedWorkerProvisioner、goals.js/CSS/模板、附近文档。使用原“重做”目标进行 API 验证，仅讨论与保存正式版本，不实施或执行 sudoku2.htm 功能。不改数据库结构、身份凭据或文件授权。

## Work items

- [x] 1.1 接入现有工厂默认模型和受管纯文字讨论 Worker。
- [x] 1.2 修复讨论和正式版本操作反馈及有界回复更新。
- [x] 1.3 完成编译及附近文档更新，准备静态审查和 API 验证。

## Acceptance

- 无在线讨论 Worker、工厂自动启动开启时，新消息可排队并取得真实模型回复。
- 工厂未启用、后端不匹配、启动失败时给出明确原因；不切换后端或授权文件工具。
- 用户可看到当前操作成功/错误；正式版本保存后定位该版本。
- 缺失信息需明确接受，历史正式快照不修改，讨论建议不覆盖正文。
- 原案例正式版本可通过 API 保存、读取，且没有启动编码计划。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- API 原始复现：新目标 revision 4，讨论 POST 返回 409 无在线 Worker；formalize confirmed=false 返回 400 缺失信息未接受；GET 已存在 revision 2/3 两个正式版本。工厂 state 显示 codex-app-server/gpt-6-luna/automaticWorkers=true，在线 Worker 为 0。
- `mvn -o -DskipTests=true -Dmaven.test.skip=true compile` 成功，315 源文件。未运行本次单元测试或全库验证。
- Independent：复用进程管理按需启动角色并调整讨论冻结配置，需核对权限、事务后启动及 UI 未保存内容保护。API 验证待审查后备份并加载修复，再使用原目标发一条纯文字讨论、记录正式版本；不执行编码计划。
- 同一次独立审查修正三项发现：自动轮询不再把旧未发送文本写回，也不重建附件分享选项；工厂配置只在后端匹配时优先，否则保留既有已批准模型/外部 Worker 路径；手动启动文档限定为工厂未启用时。修复后最终离线 compile-only 成功，未运行单元或全库测试。两份 JS 在初版实现运行过 node --check；之后小范围修复经静态审查，未声称浏览器验收通过。
- 确认没有 CLAIMED/RUNNING 任务后，将数据库备份至 `D:\temp\data\aidev-r3.before-FD054-20261027-073233.db`，4,112,384 bytes，integrity_check=ok。重启加载器 PID 13836，登录页 HTTP 200，无新增数据库迁移。
- 使用原目标正式 API 发送一次消息（revision 4），请求 `GC-29125edc-2771-439b-a44f-7a8261fea473` 返回 QUEUED。工厂自动创建 `factory-67225fcb6e004613eca90148`，随后 replies 返回 RECEIVED，真实中文模型回复 554 字符已写入；待处理请求为 0，当前目标 revision 6。
- 通过 formalize confirmed=true 明确接受现有缺失项，保存正式版本 `3ba0b3d7-e029-409e-90b2-f4f49335c665`（sourceRevision 5），GET 读取存在，ready=false、planId=null。原 revision 2/3 两份快照保留，没有创建执行计划。
- API 当前目标正文与启动前备份逐字比较一致；目标页、JS、CSS 均 HTTP 200，服务 JS 与最终工作区源码一致，页面包含两处局部状态入口。没有读取或修改 `sudoku2.htm`，没有运行模型编码工作。受管讨论 Worker 留在线以支持后续讨论，由平台负责关闭。
- 运行回执保存在本机临时目录 `aiw-goal-execution/fd054-example-request.json`；完整浏览器点击、输入法和滚动效果未运行验证。实际模型只调用本次讨论，不把 API 资源读取等同于浏览器功能测试。
- 独立审查通过：`reviews/20261027-independent-static-review.md`，Reviewer `codex-fd054-review-20261027`，来源 `FD-054-000002-review-requested`。三项本轮源码发现及一处文档冲突已修复关闭；已静态核对最终差异并读取真实 API 回执，未额外运行测试或模型。

## Sources

- 用户要求使用 API 和“为 sudoku2.htm ‘重做’功能”案例调查并修正两条流程。

**Completed:** 2026-10-26
