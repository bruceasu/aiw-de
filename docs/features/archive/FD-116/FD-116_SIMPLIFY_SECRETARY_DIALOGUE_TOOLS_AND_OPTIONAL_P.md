# FD-116: Simplify secretary dialogue tools and optional Plane delegation

**Status:** Complete
**Revision:** 5
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 29, 31, 35. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

对话直接问答和简单操作；独立需求澄清复用许愿池，不强制提交 Plane。需要团队时明确委派、最少提交内容并跟踪；文件操作按需授权，缺信息询问，不只给笼统失败。

Dependencies: FD-108, FD-109, FD-112，均已合入。沿实际秘书及统一执行器实施，不扩大已确认权限。

2026-11-14 定向核对：实际秘书实现位于根目录 personal-secretary；workers/personal-secretary 是另一套旧实现，本 FD 不向其重复加功能。已有 goal-discussion Skill、独立 SDK 对话和文件确认，不应再造一套澄清循环。

已发现两个待修入口：daily_assistant 在最终答复时仍按“实现／修复／refactor”等关键词强制要求专业 Worker 结果，会错误拒绝本机已授权的小工作；delegate_task 的工具参数仍强制旧专业 Plan、repositoryRef、baseRevision，尚未接入新的统一简单任务／工作流。后续应复用现有正式授权与操作回执，先消除强制委派，再接入最少必要的统一执行请求；未连接 Plane 时保留本地对话与工具。不能通过模拟人类登录或静默扩大代理范围绕过授权。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

最小委派方案：秘书只提交当前明确交接的 instruction、可选 memberId 与 requiredSkills，创建统一执行器的单步骤文字任务；不发送整段历史、私库或模型凭据，不再创建旧专业 Plan 草稿或要求仓库／Git 基线。文件仍可由秘书本地已有工具按需确认处理；此委派入口不授予远端文件或命令权限。

服务端沿现有机器认证、当前进程 boot 和 A05 计划确认权限；复用 confirmFactoryPlan 消费同一个正式授权记录，不另造权限表。本机已有默认管理权限直接生效，远程仍需已有正式授权。新增受认证的 task-delegations POST／按 requestId GET，重用 workflow_execution 的请求去重，不新增任务或回执表。Owner 绑定用已消费的精确 A05 记录作为证据，保持原人类入口不变，不模拟登录。风险集中在认证、授权消费、请求去重与 Owner 绑定，须独立审查。

## Work items

- [x] 1.1 核对实际秘书 SDK／Host／daily_assistant、现有澄清 Skill、授权和统一执行器，记录具体缺口与最小改法。
- [x] 1.2a 删除按“实现／修复／重构”等关键词拒绝本地完成的旧判断；SDK 与备用循环都明确本地能力优先、需求讨论不联系 Plane。原文件回执和已提交专业任务结果校验保留。
- [x] 1.2b 团队委派改为统一单步骤文字任务，复用 A05 消费和 Owner 绑定；请求按当前秘书身份去重，回执未知只查询，不回退旧专业接口；原执行有租约或仍在停止时阻止重复提交。
- [x] 1.3 完成源码自查、定向编译并提交独立静态审查；测试仍需单独授权，不把用例源码当作已执行。

## Acceptance

简单提问不擅自创建文件；秘书可不连 Plane 独立工作；委派可直达执行；同机管理与远程连接按已确认边界处理。

## Verification

2026-11-14 独立聚焦复核通过：`codex-review-fd116-20261114` 领取 `FD-116-000004-implementation-ready`，核对修复 `11ea2eb` 的真实客户端路径闭集、直接调用及回归用例源码，首轮唯一 P1 已关闭，保留一次失败历史。报告：[reviews/review-recheck-20261114.md](reviews/review-recheck-20261114.md)。修复后的编译证据已记录如下；Reviewer 未执行测试、编译、脚本、SDK、服务或网络，本结论不宣称运行验收通过。

- 首轮独立审查仅一项 P1：真实 PlatformClient 路径闭集漏加 task-delegations，HTTP 前即拒绝。已只允许新 options／提交与规范 UUID 回执路径；其余未知路径仍拒绝。新增真实 PlatformClient 校验、模拟传输的回归源码，核对 GET／POST／GET 及越界路径不发请求；未执行。原 SDK unknown POST 收敛链经 Reviewer 静态核对未另列问题。修复后 scripts/compile-secretary-runtime.ps1 与新增用例内存 compile 均返回 0，Java 源码未改，不重复 Java 编译；待聚焦复核。

2026-11-14 独立静态审查：`codex-review-fd116-20261114` 已领取 `FD-116-000002-review-requested`，审查 `306560a..d398ce0`，结论 Changes requested。发现一项 P1：真实 `PlatformClient.request` 未允许新的 task-delegations 三种路径，options、提交和回执查询均会在 HTTP 前被拒绝。报告：[reviews/review-20261114.md](reviews/review-20261114.md)。正常业务 UNKNOWN 到 SDK 工具回执及正式 GET 收敛链已静态核对，未另列问题。未运行测试、编译、脚本、服务或网络；以下 Worker 编译证据保持原意，不作为运行验收。

- 最终源码编译：JDK25，`MAVEN_OPTS=-Xmx384m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC`，离线 Maven 使用 FD-081 settings 与 `D:/.m2/repository`，`-DskipTests test-compile` 返回 0。`scripts/compile-secretary-runtime.ps1`、新增 `test_task_delegation.py` 标准库 compile 和 `node --check personal-secretary/ui/i18n.js` 返回 0。最后补充精确消费 grant_id 的关联校验后，生产与测试源码再编译返回 0；未运行任何测试、SDK、服务、迁移、安装或真实 API。首次实现及最后权限修正分开记录，不把中间编译冒充最终证据。

本地执行与统一文字委派均已实施。新增离线用例源码：本地代码示例无需 Plane、要求保存时仍核对文件回执；委派不包含未选择历史／仓库参数、未知 POST 只读查询、结束却缺结果不算成功、原失败执行仍有租约时阻止重复委派；Java 覆盖旧 boot 拒绝、精确 A05 消费后才绑定 Owner、同一 UUID 在不同秘书间不能串读。未运行测试。

2026-11-14 运行 `scripts/compile-secretary-runtime.ps1` 返回 0，秘书 Python（含本次回归源码）、页面 JavaScript 与脚本语法编译通过；未导入 SDK、执行测试、启动服务或访问网络。定向 `git diff --check` 通过。该证据仅覆盖本地执行部分，不代表委派改造已完成或整个 FD 已通过独立审查。

新增协议无新表、依赖或自动重试。去重键使用限定当前秘书的确定性 UUID，兼容既有权限消费表的 VARCHAR(36)，不冒用人类上下文。拒绝或未知结果保留原请求；普通状态跟踪只查询，不重发 POST。文件仍由秘书本地工具处理，此入口不授予远端文件／命令权限。

36 字符去重键、租约投影、真实受理／未知回执区分及精确 grant_id 消费证据均已纳入最终源码。待独立 Reviewer 核对，不将编译当作运行验收；实际本机／远程认证、正式问题续接、成员调度和委派回执仍待运行验证。

## Sources

- docs/FUNCTION-INVENTORY.md, 29, 31, 35.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。

**Completed:** 2026-11-14
