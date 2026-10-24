# FD-032: 执行预算与审计对账改为仅记录

**Status:** Open
**Revision:** 4
**Priority:** Medium
**Evidence policy:** Dual

## Problem

系统仍处于打通运行流程的阶段，用户明确要求暂不以预算、审计或对账限制运行，只记录相关数据。当前动作累计额度、执行预算完整性/余额、未知计量会阻断准备、领取或推进；授权页又要求先填写全部预算，增加了试运行成本。

## Options and decision

- 删除计量及审计：丢失定位故障所需数据，不符合“记录相关数据”。
- 填写巨大预算或自动虚构对账结论：仍有配置门槛，并会把未知数据写成已知，不采用。
- 默认仅记录，保留显式严格模式：预算配置可缺省，继续记录任务数、用量、实际时间、可计算费用、未知状态和审计；这些数据不再限制新工作。采用此方案，后续可通过部署配置恢复预算约束。

## Solution

在现有权限边界和事务内增加统一 ExecutionAccounting Bean，配置为 aidev.execution.accounting-mode（环境变量 AIW_EXECUTION_ACCOUNTING_MODE），默认 record-only，严格 enforce 模式只供后续显式配置；未知配置值拒绝启动，避免拼写错误改变模式。动作累计额度与执行账本使用同一模式；准备、领取、回跳和推进不得绕过此模式另查预算。Policy.budget 可省略并规范化为空预算；每次预留可为零，严格模式仍要求完整预算、定价和正预留。无预算可记录零预留，缺定价时费用保持未知，绝不把未知费用冒充零；确认从未启动的 Attempt 仍记录已知零执行成本。账本保留已有表、标识、计数和人工核对入口，不迁移或清空历史。

ExecutionGuard 的创建/固定输入/领取/推进和 AgentFactory 准备共用 Ledger 的模式感知检查；仅记录时允许冻结政策与当前定价不同，仍以原 Attempt 的冻结定价记账。GraphRunner 的回跳预检、整计划 unknown_count 静止判断以及预算问题生成也接入同一模式，不在仅记录时生成必须回答的预算追加问题。

审计与对账是观察数据，不新增“通过审计才可运行”的条件；未知计量和其它任务的待核对历史不阻止正常新工作。动作和托管协调的 Plan 级阻塞共用一个查询：仅记录时纯历史 UNKNOWN 不阻止无关的新任务，但工作任务 CLAIMED/RUNNING、现存 Lease、活跃 execution_attempt 仍阻止并发推进；严格模式还保留旧的 UNKNOWN/IN_PROGRESS 核对条件。原请求幂等、同一任务/Attempt 的未知外部效果防重复、真实在途并发资源、身份/CSRF/工具和动作授权、人工暂停/撤销仍有效，不把仅记录解释为授予新能力或自动重试。既有失联恢复、停止/静止核验和未知操作结论不自动改变。

授权页保留工具及仓库范围确认，将预算和审计说明改为当前模式及记录用途；预算输入和历史核对成为可选维护，不要求用户为开始运行先追加预算或补齐定价。外部协议和表结构不变；已有预算继续展示，恢复严格模式仍要求完整预算/定价。

## Scope

仅修改平台执行/动作预算的准入条件、必要的未知历史阻塞范围、授权页文案及有关稳定规格/部署说明。保留工作区中其它个人秘书和模型配置任务的未提交改动。沿用本会话已选择的主工作区处理方式，仅提交本 FD 的文件/差异；不覆盖其它任务，不推送或发布。

不取消登录、Owner/Worker 绑定、精确动作/工具授权、Plan 托管、人工暂停/撤权、幂等和重复执行保护，不取消命令沙箱及单命令资源限制，不改变 Fleet/Worker 并发容量，不执行数据库迁移、服务重启、测试或真实模型调用。

## Work items

- [x] 1.1 确定统一模式及兼容规则，完成独立静态设计审阅。规模小、难度中，无依赖；证据为本设计与 FD-032-design-review-20261024-b4c92a1f 双证据；D032-01/02/03 均已采纳到 Solution，实现 Reviewer 必须逐项核对。原 changes-needed 结论保留，不改写为通过。
- [x] 1.2 动作累计额度仅计数，保留授权及重复请求保护。规模小、难度中，依赖 1.1；证据为 ExecutionAccounting、application.yaml 与 AuthorityBudgets 的统一模式及原子计数，授权/幂等路径未跳过。
- [x] 1.3 执行准备、任务计数、Attempt 预留/结算及回跳支持缺省预算并持续记录。规模小、难度中，依赖 1.1；证据为 ExecutionLedger、GuardContract、ExecutionGuard、AgentFactory、GraphRunner 的模式感知路径，费用未知不虚构、冻结定价不追溯改写。
- [ ] 1.4 待核对历史只记录，保留同一原任务/Attempt 的防重复与真实在途保护。规模小、难度中，依赖 1.2、1.3；证据为准入/推进路径及审阅报告。
- [ ] 1.5 更新授权页、稳定规格与部署说明，完成窄范围编译、独立实现审阅及范围内本地交付归档。规模小、难度中，依赖前述条目；未运行的检查保持未运行。

## TODO

- 已采纳独立设计审阅的图预算旁路、实际在途区分、预算缺省/价格兼容三项意见，开始落实平台与页面变更；不得把其它工作区改动纳入本次证据。

## Acceptance

1. 默认未填预算、定价，预算为零/已超额或有未知消耗时，不因这些数据拒绝新工作的准备、领取、计数、回跳或正常推进（1.1、1.3）。
2. 动作仍必须匹配当前授权、状态、固定参数和版本，但不会因 maximumUses 或累计预算耗尽而拒绝；累计次数与审计仍记录（1.2）。
3. 有定价记录实际费用，无定价保留未知；每个任务/Attempt/回跳仍按原唯一标识计数，不因重放重复计数，不删除已有历史（1.3）。
4. 不要求先处理其它历史审计/计量/对账记录才能开始新工作；同一未知原请求不自动重放、不得在运行中的原任务上创建并发执行（1.4）。
5. 显式恢复 enforce 模式后原预算完整性、余额和未知计量约束重新生效；没有登录、授权或已人工暂停的请求继续被拒绝（1.1–1.4）。
6. 页面和部署说明准确说明默认仅记录、预算可选、原任务重复执行保护与尚未运行验收范围，不声称已完成服务升级（1.5）。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- 仅执行一次平台 Maven 离线 compile；不下载依赖、不打包，不运行测试、数据库、服务、浏览器或模型。独立设计审阅和独立实现审阅仅静态读取。
- 独立设计审阅：../reviews/FD-032-design-review-20261024-b4c92a1f.md 与同名 JSON。Planner 已将三项收敛要求写入 Solution 和验收；尚未声称实现或运行通过。

## Sources

- Issue: none
- 用户指令（2026-10-24）：当前系统还没有正常运作，暂时不做预算、审计和对账等限制，只需要记录相关数据。
- Source event: FD-032-000002-design-requested；Planner/Worker Session: secretary-desktop-20261024-a6529740。
- src/main/java/me/asu/aidev/authority/AuthorityBudgets.java、ActionAdmissionService.java；execution/guard/；execution/config/AgentFactory.java；templates/ui/authority.html；docs/EXECUTION-GUARD.md。
