# FD-114: Reconcile interrupted work and make completion checks explicit

**Status:** Complete
**Revision:** 5
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 11, 12, 15. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

连接中断先核对已有操作和结果，不自动重派；明确完成条件、缺信息询问、阶段结果、自检及显式评审节点。复用已有执行记录和停止协议。

最小实现：统一执行的过期租约先登记 INTERRUPTED 事件并转入现有 FAILED 待处理状态，不释放未知进程占用，不自动重派；原 Guard 仍标记未知副作用。领取前核对执行状态及当前任务，阻止停止后的旧 READY 任务领取。管理员明确撤销租约后仍须用户选择重试；已收到的成功结果可明确选择使用。失败沿用原报告与工作台。

影响范围为 WorkflowRuns、现有 TaskExecutionService／TaskClaimService 和工作流页面。保留非统一旧任务的原恢复策略，不新增状态表、调度器或自动评审。风险：失联的真实进程仍可能在运行；保留租约和容量，重试前必须走原停止／核对流程，页面不能将超时描述为已停止。新增控制动作只接受原 Owner 和版本校验。

Dependencies: FD-108, FD-109. 中断核对与完成检查先在统一执行路径完善，不以旧后端退役为前置条件，避免与 FD-105 的实际迁入依赖形成环。进入实施前继续核对附近代码。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

## Work items

- [x] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [x] 1.2 实施中断待核对、领取门禁、显式使用成功结果及重试确认；补充完成条件提示与中日英页面文字。
- [x] 1.3 完成源码自查并提交独立静态审查；编译按用户要求延期，不将该项视为已编译。

## Acceptance

未知副作用不会直接重放；结果与模型声称完成分开；失败说明已完成内容和阻碍；重做由用户决定；普通结束不隐式启动评审。

## Verification

- 独立聚焦复核 `e9f7b6f`（来源 `FD-114-000004-implementation-ready`）：静态代码 Pass，首轮唯一 P2 已关闭；act.finally 与 refreshRun 均向 controlButtons 传一致的 run/task，失败历史保留。详见 [复核报告](reviews/review-recheck-20261114.md)。未运行测试、编译或脚本；**最终编译仍未补齐，不据此归档。**

第一轮独立静态审查提出 1 项 P2：act.finally 更新按钮状态时漏传当前 task，隐藏“使用已记录的结果”。已将该调用与 refreshRun 一样传入 currentView.task，等待聚焦复核。首轮报告保留；没有执行浏览器／测试或编译。

- 独立静态审查（`48e16be → b7ab56a`，来源 `FD-114-000002-review-requested`）：Changes requested，1 项 P2——act.finally 刷新控制按钮漏传 task，使“使用已记录结果”在加载收尾被隐藏。详见 [审查报告](reviews/review-20261114.md)。限定范围未发现其他阻塞；未运行测试、编译或运行时验证。最终编译仍待补齐，不据此归档；后续合并 FD-108 必须保留其工作区提示和 commands 冻结。

源码已实施，未通过运行验收。统一执行在租约过期时使用现有 FAILED／INTERRUPTED，保留真实进程资源；原 Guard 继续登记 UNKNOWN 副作用。领取核对当前执行及任务；管理员撤销租约仍须手动重试，重做时取消旧未领取任务。晚到的正式 PASS 报告可由用户明确使用，继续前仍核对无活动租约和原版本／Owner。页面新增原执行工作台入口、使用结果／重做确认及连接不等于停止的说明。

新增 WorkflowInterruptionTest 源码，覆盖中断幂等、禁止重新领取／旧任务、暂停与停止边界、显式使用晚到成功结果和拒绝未结束结果。未执行。没有新增自动重试、评审 Worker、资源释放或状态表；完成检查为现有模型回合内提示，不伪造独立验收。

本轮仅定向读取源码和静态差异检查。用户已要求先静态整理、稍后编译；未运行 Java／页面编译、测试、浏览器、服务、迁移、安装或真实 API。最终编译未补齐，不得归档合入。静态审查另记，不能代替运行证据。

## Sources

- docs/FUNCTION-INVENTORY.md, 11, 12, 15.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。
