# FD-120: Complete professional wish clarification attachments and localized identity

**Status:** Complete
**Revision:** 5
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 01. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

专业成员携带所需 Skills 与系统知识参与澄清；附件可直接作为原始需求；确认后生成统一计划。中文使用已确认的卡通鳖形象方向，英日正常愿望角色，不新增吉祥物运行框架。

Dependencies: FD-107, FD-113, FD-116（已交付）。修改现有 GoalDiscussionService、GoalTaskPolicy、TeamMembers 和 goals 页面；不新增调度器、表或服务抽象。讨论发送可选专业成员，profile 与知识正文／摘要随 goal_request.frozen_json 冻结；既有 task Skill 绑定补充成员 Skills。TeamMembers 的实际任务身份和接单状态读取同一冻结记录。模型仍必须符合讨论后端与成员允许范围，不能默默忽略成员模型限制。附件可在消息为空时独立作为原始需求输入。中文采用卡通鳖，英日采用愿望向导，均使用现有页面样式和代码内简洁图形。

风险边界：新增只读讨论成员选择与被冻结 JSON 字段，涉及现有任务身份投影和暂停接单，需独立审查；仍使用既有讨论 REPORT 协议，不扩展工具／文件权限，不改 Owner、授权、数据库结构、重试或执行确认。成员参考资料过大、缺失或模型不匹配时明确拒绝此次发送。原始附件和历史不删除。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

## Work items

- [x] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [x] 1.2 实施已确认行为，删除因此不再需要的代码。
- [x] 1.3 编译受影响源码、自查差异并提交独立审查；最终结论由 Reviewer 记录。

## Acceptance

可从文字或可选附件开始；系统事实有来源；缺失信息明确询问；原需求不丢失；确认前不自动执行生成的计划。

## Verification

2026-11-14 独立聚焦复核通过：`codex-review-fd120-20261114` 领取 `FD-120-000004-implementation-ready`，核对 `3d7dd197` 的讨论冻结选型、真实 start 与范围外拒绝，原权限／租约／停止检查保留，首轮 P1 已关闭。保留一次失败历史；报告：[reviews/review-recheck-20261114.md](reviews/review-recheck-20261114.md)。Reviewer 未执行测试、编译、脚本、浏览器、API、服务或网络，修复后编译证据不等于运行验收。

2026-11-14 独立静态审查：`codex-review-fd120-20261114` 领取 `FD-120-000002-review-requested`，审查 `f260436d`，结论 Changes requested。一项 P1：有全局 EXTERNAL_WORK AI 策略时，选成员的工厂讨论按 factory 版本领取，却在实际 start 前被全局 catalog 版本／ID 成员校验拒绝。报告：[reviews/review-20261114.md](reviews/review-20261114.md)。其余本轮权限、冻结、Skill 绑定和只读讨论链未另列阻塞。Reviewer 未运行测试、编译、验证脚本、浏览器、API、服务或网络，不将已有编译证据称为运行验收通过。

已实现：现有 session/CSRF 下的参与成员读取和发送可选 memberId；冻结成员、模型范围、知识材料及真实 Skills，未新增表。成员任务身份读取冻结版本，接单读取当前成员状态；未完成讨论列入成员当前工作与接单负载，不能直接解散，成员卡片直达原许愿记录。未选专业成员时沿用已有需求讨论 Worker，专业选择明确展示将发送其资料。附件空消息走原提取／图片校验，不新增转换器或自动文件写入。已有确认快照→工作流草稿的流程保持不变，模型建议不能覆盖原始需求或自动启动业务工作。图形为页面内简洁 SVG，未引入吉祥物框架或资源依赖。

实际检查：

- 设置 JDK25，`MAVEN_OPTS=-Xmx384m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC`；`mvn -q -o -s D:/03_projects/AI-tools/aiw-de/.ai/fd/FD-081/offline-maven-settings.xml -Dmaven.repo.local=D:/.m2/repository -DskipTests test-compile` exit 0，仅 Jansi/Unsafe 警告。新增的成员冻结／模型拒绝及冻结身份／当前状态测试仅编译，未执行。
- `node --check src/main/resources/static/ui/goals.js`、`team-members.js`、`i18n.js` 均 exit 0；`git diff --check` exit 0，仅已有 CRLF 规范化提示。
- 静态追踪：actor 限定成员读取→现有 goal_request 冻结→任务 Skill 绑定→真实成员身份／接单状态→讨论报告，保留原附件、报告与用户确认契约。
- 未运行测试、浏览器、服务、SDK、模型、迁移或外部 API；未安装依赖。实际 UI、专业讨论和任务暂停交互仍待运行验收。
- 首轮 P1 已修正：TaskExecutionService 对 goal-discussion 由正式 GoalTaskPolicy.select 校验已冻结的后端版本、成员允许模型和实际选择，并绑定真实 AiSelection；不再先拿另一套全局 catalogue 版本拒绝工厂讨论。其它任务的 AuthorityTaskPolicy.selection 保留。NativeQuestions 仅在其实际适用的 flexible-plan 分支计算选型，避免无关讨论先套用全局模型上下文。新增 DiscussionMemberStartTest 测试源码，覆盖工厂版本正确启动与范围外模型拒绝；修正后同一离线 `-DskipTests test-compile` exit 0，未执行测试。

## Sources

- docs/FUNCTION-INVENTORY.md, 01.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。

**Completed:** 2026-11-14
