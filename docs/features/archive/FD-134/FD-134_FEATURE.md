# FD-134 在任务详情显示当前工作与恢复入口

**Status:** Complete
**Revision:** 5
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

任务详情显示任务状态、尝试历史及检查回执。要查看 Worker 正在等待工作区还是已经开始向模型请求，用户需要打开工作流或重新在工作台选任务。受控 Python Worker 在准备工作区之后才打开事件通道，实际的等待过程可能只有本机日志可见。

## Solution

在任务详情顶部从最新执行尝试投影当前阶段、最近一项已记录的结果及时间。使用现有运行会话和已允许的事件元数据，沿用同一 Owner 检查。不读取模型内容、工具输出和其他尝试。缺少事件时用已确认的任务状态，连接延迟时明确显示状态未确认，租约到期仍使用原中断入口。

受控 Python OpenAI Worker 提前打开已有运行通道，在准备工作区和等待锁时发出已有事件。生命周期、租约、权限和控制动作继续沿用现有协议。任务页已有的恢复按钮保留，工作台链接使用任务 ID 和实际尝试 ID 跳转。任务页使用原有自动刷新。

## Work items

- [x] 1 在任务详情显示最新尝试的当前阶段、结果和发生时间，按记录提供工作台直达。
- [x] 2 让受控 Python Worker 在工作区准备及等待期间上报已有事件。
- [x] 3 静态核对归属、状态优先级和无事件回退，完成编译。

## Acceptance

- 待领取、等待工作区、模型调用、工具执行、检查与交付可用已有证据区分。
- 暂停、停止请求、人工等待和租约过期优先于此前运行事件。陈旧连接不会显示为已停止，原恢复入口仍能定位相同任务。
- 当前尝试尚无任何事件时不会借用上一次的时间线。无权限的访问者也不会看到原始运行元数据。
- FD-133 的必需检查和执行回执继续在原位置显示。其他运行时有事件则投影，缺少事件时使用可核对的状态。

## Verification

修改涉及受控 Worker 通道开启时机与任务详情的 Owner 归属，需独立复审。`node scripts/compile-worker-runtime.mjs` 和 JDK 25 离线 Maven `compile` 已通过。定向 Maven 用例 `ExecutionRunsTest#taskProgressUsesOnlyTheExactOwnedAttemptAndKeepsStopState` 首次因夹具仍引用已移除的 V28 迁移停在初始化。夹具改用 V1 基线后复跑通过，覆盖等待锁、具体操作失败、缺少尝试、外部 Owner、连接延迟和停止申请优先级。未运行完整单测套件、Chrome 或真实 Worker。

首次独立审查的两项修正已完成（Worker 会话 `fd134-worker-clu2mihlm`，来源 `FD-134-000003-changes-requested`）。获取或回收锁的短操作服从暂停，锁繁忙时在步骤之间释放通道边界。交付、暂停与停止等待在连接延迟时仍显示独立提示。最终 Worker 编译和 JDK 25 离线编译通过。新增的暂停后目录锁和交付阶段失联断言未运行。

独立审查要求修正暂停后仍可能抢占目录锁，以及提交阶段失联时未显示状态待核对的问题。[审查报告](reviews/FD-134-000002-review.md)

两项修正通过独立静态复核，新增断言和真实运行的验证范围保持上述记录。[复核报告](reviews/FD-134-000004-review.md)

## Sources

- [Agent 改进清单](../../../AGENT-IMPROVEMENT-BACKLOG.md)
- [FD-133](../FD-133/index.md)

**Completed:** 2026-11-21
