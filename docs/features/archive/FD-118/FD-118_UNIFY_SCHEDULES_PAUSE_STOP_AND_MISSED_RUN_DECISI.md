# FD-118: Unify schedules pause stop and missed-run decisions

**Status:** Complete
**Revision:** 5
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 32, 33. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

立即、定时、周期工作复用现有业务安排；Spring 管调度，操作系统管启动守护。区分停止本次、暂停自动处理、关闭界面；按已确认规则处理错过和中断。

Dependencies: FD-116, FD-114. 本文件为设计草案。进入实施前核对附近代码，再补充最小改法和实际受影响的契约；不得扩大已确认范围。

2026-11-14 已核对附近实现：秘书 daily_assistant 已有 IMMEDIATE／TIMED、错过执行分钟或停机未开始时请用户确认，以及暂停状态下不开始新工作的控制。它独立于 Plane，保留单次轻量安排，不为此依赖 Java 或新增第三方时区库。Plane 当前有价格采集等专用 @Scheduled，但没有统一业务周期安排；A17_SCHEDULE 是文件删除保留期动作，不应用于工作流调度。

下一步最小范围：Plane 增加每天／每周和指定时间的业务安排，使用原统一执行器创建独立执行记录；持久化时区和下次时间，以安排与原触发时间去重。需要明确记录后台启动所依据的原 Owner／安排许可，不伪造当前人类会话。暂停安排不停止当前执行，旧执行未结束或结果不明时不启动重复工作；停机错过或中断显示待处理，由用户明确决定。秘书沿用本地单次安排，界面明确本地／Plane 的负责人及停止范围，不把关闭界面显示为任务已停止。

实施决定：新建一张 workflow_schedule 表；一个 Spring Controller 同时提供本人安排的创建／控制／查看与定期扫描，复用原事务管理和 WorkflowRuns。保存工作内容快照，首期只允许文字节点，不扩大文件／命令授权；时区使用 Java 自带 ZoneId，无新依赖。触发使用安排 ID＋原定时间形成确定性 UUID，按数据库事务去重；每次触发前核对原 Owner 当前 A05 权限。错过分钟、上次仍未结束或上一轮失败时转入待确认，不补跑、不重叠。用户可执行一次、跳过、改期、暂停／继续或停用；当前执行通过已有统一页面控制。

风险是新增持久化迁移、后台启动权限与时间／并发语义。迁移只新增表，不改历史任务；本轮不执行迁移或真实调度。后台使用安排记录作为明确授权来源，不设置或冒充当前人类会话。夏令时采用 Java 时区规则，一天／一周只形成一个原定时刻；测试仅写源码及编译，需独立静态审查。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

## Work items

- [x] 1.1 核对现有本地安排、暂停／中断和 Plane 调度入口，记录最小范围与权限／时间语义。
- [x] 1.2 实施单表安排、Spring 到期扫描、原执行器及明确 Owner 绑定、时间／状态去重和控制页面；秘书暂停仅影响未来自动启动，保留当前处理、聊天及既有回执查询。
- [x] 1.3 完成源码自查与编译，并提交独立静态审查；测试源码未执行，不将迁移或运行验收视为通过。

## Acceptance

不重复创建相同执行；暂停不被定时器重新启动；错过或中断先展示已有结果与决定；关闭界面不冒充停止任务。

## Verification

2026-11-14 独立聚焦复核通过：`codex-review-fd118-20261114` 领取 `FD-118-000004-implementation-ready`，核对 `0d12c4e` 中暂停时原平台动作／回答只读回执收敛、自动恢复与问答暂停门禁及顶层 planId/taskId 映射。首轮 P2 已关闭，保留一次失败历史；报告：[reviews/review-recheck-20261114.md](reviews/review-recheck-20261114.md)。Reviewer 未运行测试、编译、验证脚本、真实 API、服务或网络；Worker 编译证据不等于运行验收。

首轮独立静态审查仅一项 P2：AuthorizationRequests.tick 的全局暂停门禁仍挡住已提交平台动作的只读回执，影响收尾。已将既有只读 poll 放在暂停门禁外，保留自动授权恢复及自动代答的动态暂停检查；新增实际 poll＋模拟 receipt／线程调度的源码用例，断言暂停时原消息可收敛而不会恢复授权或代答。正式问答读取统一委派回执顶层 taskId，不再从旧 preparation 字段取任务。未执行测试；修复后 scripts/compile-secretary-runtime.ps1 与新用例内存 compile 返回 0，Java 未改不重复编译。

2026-11-14 独立静态审查：`codex-review-fd118-20261114` 领取 `FD-118-000002-review-requested`，审查 `e7375ca..e672ca6`，结论 Changes requested。一项 P2：`AuthorizationRequests.tick` 全局暂停时仍直接返回，阻断已提交 platform_action／platform_answer 的唯一只读回执轮询及原工作收敛。报告：[reviews/review-20261114.md](reviews/review-20261114.md)。安排权限、事务、去重及前次未结束检查未另列阻塞。Reviewer 未运行任何测试、编译、脚本、迁移、服务或网络；以下编译证据不代表运行验收。

已实施。新增 V51 仅创建安排表，未执行迁移。保存当前 Spec／Owner／时区与时间，确定性 UUID 复用原 workflow_execution 去重；后台从已保存安排读取权限来源、检查当前 A05 与到期时间，不伪造人类上下文。原定时间进入 STARTED 上下文。到期、版本及状态变更在原事务管理器内处理；错过分钟、前次有租约／未结束或失败未核对时不重派。用户确认只针对已经观察到且已结束的失败，不提前批准之后才发生的失败。

页面沿现有卡片／字段／按钮提供指定时间、每天、每周、暂停／恢复、执行一次、跳过、改期、停用和原执行链接。完整快照按需读取，可以明确选用原内容创建新安排；列表不返回全部参考正文。普通安排只允许文字任务，不扩大文件／命令权限。时区使用 Java ZoneId；停机不会逐项向操作系统注册任务。

秘书全局暂停原来阻止聊天、当前回合和状态查询，已删除这些错误门禁；只在本地到期自动启动及自动授权恢复入口保留全局暂停。每次运行自己的 PAUSED／CANCELLED、未知副作用、授权、模型绑定和文件确认检查保留。手动确认／继续和排队的用户后续消息仍可处理，全局设置变化写入活动运行时间线。关闭 Qt 界面只隐藏到托盘的行为已静态核对，未改成停止服务。

新增 Java 回归源码覆盖错过安排不启动、停用不停止当前执行、保留租约时禁止人工重复启动、时区／周计划／夏令时；Python 新源码覆盖暂停后聊天、当前处理继续、单次暂停仍生效、本地到期不启动及已委派回执继续查询。未执行。

最终 JDK25 离线 `mvn -q -o -s D:/03_projects/AI-tools/aiw-de/.ai/fd/FD-081/offline-maven-settings.xml -Dmaven.repo.local=D:/.m2/repository -DskipTests test-compile` 返回 0；`node --check src/main/resources/static/ui/workflows.js`、`scripts/compile-secretary-runtime.ps1`、新 Python 用例标准库 compile 及秘书 i18n.js 语法检查均返回 0。`git diff --check` 无错误。未运行测试、浏览器、服务、迁移、模型、网络或安装；实际调度、数据库并发、时区环境及界面操作待运行验收。

## Sources

- docs/FUNCTION-INVENTORY.md, 32, 33.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。

**Completed:** 2026-11-14
