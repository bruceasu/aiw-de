# FD-108: Unify task files workspace safety and handoffs

**Status:** Complete
**Revision:** 7
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 05, 14, 25, 26. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

将已有宿主文件与命令工具接入统一任务；工作区默认边界、明确指定授权、可恢复删除、危险确认、停止优先共用原规则。Worker 准备 Git/worktree 和 .ai 交接；不同目录可并发，相同目录无隔离则串行。

Dependencies: FD-107、FD-112（均已交付）。

现状：统一节点全部使用只读 general-work。已有 GuardBroker 文件工具、人工确认、执行过程与停止协议；PlanWorkspace 已负责 Worker 侧单仓库 worktree／目录串行以及 .ai 交接。不能仅把 general-work 改成可写，否则原文本任务会得到未选择的文件能力。

最小实施顺序：

1. TASK 节点增加显式“工作区任务”选项；普通文本与判断节点继续只读。简单任务复用同一选项。工作区仍是 location，不增加仓库登记或 Control Plane Git 操作。
2. 工作区执行复用现有 GuardBroker 和当前 Owner 的任务范围策略，不新建 agent loop、审批表或执行日志。使用新的一般文件工作方法，不借用“软件开发”角色。一个执行内混合节点时，文件节点可写、其余节点仍只读。
3. 普通创建／读取／有限修改在工作区内自主执行。单文件删除先保存到 .ai/trash 再移除，失败不得删除；不可恢复删除、大范围覆盖及危险命令走既有一次确认。原已配置命令和宿主隔离能力保留，不把任意 shell 误当作受工作区路径约束的文件工具。
4. 无 Git 的目录允许完成：正式交接记录文件路径和结果，不要求虚构 commit。Git 结果仍使用原接口；文件交接使用已有 manifest.deliverables，不上传修改后的文件正文。上游 .ai 报告与工作目录可继续被后续节点读取。
5. 自定义 location 即该任务明确选择的工作目录；显式附件按已选内容提供，不能从模型提到的绝对路径推导整个外部目录权限。额外主机路径不被自动扩大授权。不支持的越界操作应解释限制并等待用户明确调整工作目录或资料。

需要一并调整的真实耦合：ExecutionGuard.validateResult 当前强制 Git result 非空；文件删除直接 unlink；文件动作的风险判定曾读取参数中不存在的 action 字段，实际 SDK 调用绕过了该判定。修正为从封闭工具参数结构识别操作。createWorkflowExecution 需给该执行的任务补齐 guard 能力与上下文。

已核实：guard.confirm 仅触发既有完成回执排空，不会自行执行另一个工作流，直接复用，不新增包装接口。PlanLocations 原本就是明确发起的清理，不在本轮改成自动删除。删除未接入执行的 tool_risk 原型及其草稿测试，实际规则仅保留在宿主执行入口。

常规覆盖的首期可见规则：精确旧摘要匹配、旧／新内容各不超过 64 KiB，单次执行不超过 20 个既有目标；更大或更多覆盖保留原一次确认。新文件不据此限制。单文件删除先原子移动至共享 Plan 位置下 .ai/trash，返回恢复 ID；restore 仅恢复原相对路径且不能覆盖已存在目标。目录递归删除不提供。恢复资料及输出清单留在 .ai，不上传正文。

另两个已确认的启动耦合一并解除：general-work 是工作方法名，实际基础 Skill 是 engineering-artifact-protocol，Worker 同时识别方法名与实际 Skill，避免 ROLE_SKILL_DENIED；guarded Worker 的问题交接存储改用已有共享存储标识，不再要求启动时登记一个 Git 仓库路径。具体位置仍由冻结的 handoff 校验。已认证管理员首次启动任务时复用 grants.bind 自动取得自己的 Owner，无须再到授权页手动创建；多重 Owner 的异常仍不猜测。

工厂分别区分只读／工作区角色与 native／guarded 进程配置，防止复用一个缺少所需协议的旧进程。工作区任务要求 workspace-files-v1 与 worker-loop-v1，工厂显式开启现有回合控制和观察，不为一般文件任务加载软件专用配置。

生命周期：临时目录至少保留到整个执行结束，失败保留现场；存在文件交付时不可在用户取得结果前自动清除。沿用原位置记录，避免新增工作空间注册表。当前阶段先完成文件执行与交接，再核对原命令适配器的实际能力；不承诺没有宿主隔离时任意命令都能安全执行。

验证：按用户最新指示只做静态修改／审查，内存恢复后再编译；不测试、不安装、不调用真实 API。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

## Work items

- [x] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [x] 1.2 接入显式文件任务、原 GuardBroker、可恢复删除及 .ai 上下游交接；解除 Git 前置和方法名／Skill 不匹配。
- [x] 1.3 统一任务接入已有受控命令配置；未确认的 Windows 新执行路径不扩大到本 FD。
- [x] 1.4 完成源码自查并提交独立审查；编译按用户要求待内存恢复后补。

## Acceptance

简单任务可交付实际文件；越界未授权被阻止；同一授权不重复询问；删除可恢复且留记录；临时目录保留至执行结束，失败保留现场。

## Verification

- 最终独立聚焦复核 `71102c1`（来源 `FD-108-000006-implementation-ready`）：静态代码 Pass，第二轮两项 P1 均关闭，累计两轮失败历史保留。实际 Profile 文件按已加载对象/映射定位；共享 handoff 能力覆盖两种 Broker runtime，资料能力仅 SDK 保留。详见 [最终复核报告](reviews/review-recheck2-20261114.md)。未运行测试、编译或脚本；**最终编译仍未补齐，不据此归档交付。**

- 第二轮聚焦修复：实际 Profile 文件使用既有 PROFILES／ROLE_FILES 的已加载对象定位，不根据业务 name 猜文件名；固定方法证明能力禁止通过普通 capability 环境配置伪造。handoff-store 在所有实际支持 Broker 位置型问答的 runtime 登记，资料能力仍仅 SDK 登记。第二次 Changes requested 历史保留，待最后一次聚焦复核；仍未编译或运行测试。

- 独立聚焦复核 `4a4db9d`（来源 `FD-108-000004-implementation-ready`）：Changes requested，2 项 P1——新增 Profile 摘要按 name 猜文件路径导致既有 guarded 角色启动失败；guarded Codex 未登记位置型问答所需 handoff-store 能力。SDK 资料能力及位置检查点主链修复已静态核对，首轮历史保留；详见 [复核报告](reviews/review-recheck-20261114.md)。未运行测试、编译或脚本；最终编译仍待补齐，不能据此归档。

- 第一轮审查三项修复：业务摘要继续冻结在 configurationDigest；新增 worker-profile 引用冻结实际 TOML 内容摘要（统一换行），领取核对 Worker 真实加载的方法文件与原 Skill 摘要。旧配置路径保留原 fixed-profile 校验。
- guarded openai-agents 登记实际支持的 task-materials-v1 和共享 handoff-store；备用 runtime 不冒充资料能力。
- 正式问题的无 Git 检查点复用位置／不可变 handoff／树摘要。服务端核对当前 Attempt 已登记的 handoff，续接保留原授权；Worker 在目录锁内验证 manifest 和最新检查点树。旧 Git 检查点仍验证原提交。新增普通目录问答及现场被修改的回归用例源码，未执行。
- 用户延期编译仍有效。本次只做源码修正和静态检查，未运行编译、测试、模型、网络或服务。

- 独立静态审查（`48e16be → 01ca86c`，来源 `FD-108-000002-review-requested`）：Changes requested，3 项 P1——固定 profile 与业务摘要不匹配阻止领取、guarded SDK 未登记资料能力、无 Git 正式提问/续接仍依赖提交检查点。详见 [审查报告](reviews/review-20261114.md)。未运行测试、编译或运行时验证；最终编译仍待补齐，不能据此归档。

文件部分已修改，未编译、未运行。新增离线文件工具测试源码，覆盖无 Git 写入／回收／恢复、真实 SDK 参数结构触发大范围覆盖确认、精确旧摘要；新增管理员自动绑定用例源码，调整工厂调度夹具。尚未执行。

任务与可复用定义增加可选的 commands 配置，沿用 GuardContract.Command 的精确参数、工作目录及资源限制；仅工作区节点可声明。策略／重放摘要／后续调整均冻结命令内容。需要命令的节点要求已验证的 Linux／WSL 适配器；工厂不会反复启动不具备命令能力的进程，直接报告需要连接相应 Worker。只读节点不会暴露写入或执行工具。修正文件节点仍沿用“不要创建文件”的旧提示。

Windows 新命令执行设计仍待用户回答；未获得扩大宿主权限的确认，暂不实现。这是未确认的扩展，不作为本 FD 原有“接入已有文件和命令工具”的完成前置。不伪造 Windows 能力，也不将继续处理视为逐条确认执行方案已获批准。

用户已要求“先继续静态整理，稍后再编译”。不运行编译、测试、SDK 诊断、服务、安装或真实 API；不能将源码存在视为运行通过。待全部实现后做独立审查，最终编译补齐后才归档。

## Sources

- docs/FUNCTION-INVENTORY.md, 05, 14, 25, 26.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。
