# Worker 公共执行契约

来源：FD-005 / ISSUE-001。本文记录跨运行时的目标契约与源码基线，尚不代表全部运行时已经实现或验证。

## 角色、操作和报告

| 配置角色 | 有效 TaskType | 注册能力 | 仓库编辑 | 完成报告 |
|---|---|---|---|---|
| requirements | REQUIREMENT_ANALYSIS | requirement-analysis | 禁止 | 非空 summary，PASS |
| architect | SOLUTION_ARCHITECTURE | solution-architecture | 禁止 | 非空 summary，PASS |
| planner | IMPLEMENTATION_PLANNING | implementation-planning | 禁止 | 非空 summary，PASS；保留原 manifest |
| coder | IMPLEMENTATION | coding | 指定工作区内 | 保持现有 workspaceResult，report 可空 |
| integrator | INTEGRATION | integration | 指定工作区内 | 保持现有 workspaceResult，report 可空 |
| reviewer | CODE_REVIEW | code-review | 禁止 | 末行 AIW_VERDICT: PASS 或 REPAIR_REQUIRED |
| verifier | VERIFICATION | verification | 禁止 | 同上 |
| integration-verifier | INTEGRATION_VERIFICATION | integration-verification | 禁止 | 同上 |
| repairer | REPAIR | repair | 指定工作区内 | 保持现有 workspaceResult，report 可空 |

`verifier` 对 INTEGRATION_VERIFICATION 使用 integration-verifier 的有效 Profile，并声明对应注册能力。Release Readiness 与 Orchestrator 的 Profile 保留，缺少现有可执行 TaskType，不允许注册为执行角色。

操作授权与资源协调分别处理。只读角色不能编辑；API Worker 不向其开放 bash，Codex 内部命令受其 read-only sandbox 及主机政策约束，不能伪称 SDK 内部没有 shell。可写角色可以取得文件编辑能力，命令、网络和外部写入仍需要具体政策。角色名、插件输出、模型文本及上游报告不能自行授予权限。

Profile 继续使用现有八个字段，逐一验证而不增加新必需字段。冻结 protocolSkills、primarySkill 和参考材料的内容契约保持完整，变更加载政策先形成设计决定。

## 单次 attempt

1. 校验角色、任务类型和模型指导，调用 start，维持独占租约。
2. 准备工作区、固定输入版本与 Skill 来源，建立整体期限和执行元数据。
3. 执行有界模型与工具，记录调用关联、完整性、取消及副作用。
4. 等待活动工具结束，校验报告及只读工作区，准备结果并按政策发布。
5. 检查所有权并上报 complete。终态不确定时记录未知，由控制平面恢复。
6. 异常时停止新增副作用，等待活动操作终止，在仍拥有有效租约时按原契约上报 fail，然后进行有界清理。

准备、模型、工具、报告和 Git 发布共享整体期限；清理使用单独期限。drain 停止领取并完成在途工作，不等同于中断。取消不等于撤销已经发生的文件或远端修改。

### 控制平面源码基线

TaskExecutionService 的 complete/fail 要求 RUNNING 且租约未过期、属于调用 Worker；complete 的任务更新使用 completeOwned，失败使用 failOwned。现有 complete/fail 不提供已完成请求再次返回成功的幂等接口。响应丢失属于结果未知，Worker 不自行重跑整个任务。

现有 failureCategory 仅有 UNKNOWN、RATE_LIMIT、MODEL_ERROR、OUTPUT_INVALID、TOOL_ERROR、CONFIGURATION。内部取消、权限、证据和资源错误映射到这些值，不新增公开枚举。TS RuntimeFault 的 TIMEOUT 只用于内部取消与工具终态，向控制平面上报时映射为 TOOL_ERROR。

代码任务保持 WorkspaceResult 的 resultRevision、changedFiles、publicationRef。审查/验证必须给出 report；前三个语义角色必须给出 PASS report。发布与报告校验必须遵循这一既有契约。

## 并行与工具结果

- 独立读取和不同文件操作有界并行，同一文件的读改写全过程排队。
- shell 无可靠文件声明时，与工作区写入协调；Git 收尾必须等待活动工具结束。
- 队列等待可以取消；已经执行的 I/O 没有终止前不释放资源。普通已结束的失败不阻塞后续操作；子进程或 I/O 终止未确认时保留租约，拒绝该协调器的后续操作，主机停止领取并保留工作区待人工对账。
- 路径策略拒绝工作区外路径、Git 内部路径、符号链接及危险硬链接写入；路径检查不等于操作系统沙箱。
- 工具调用与结果按 ID 关联，完成顺序可以不同。嵌套调用经过同一授权、取消与输出边界。
- 参数或响应长度截断造成的调用不完整必须拒绝执行，不因为剩余 JSON 能解析就认为完整。
- UTF-8 行/字节上限包含提示标记。完整结构返回或明确拒绝；搜索部分完成、截断和结果未知分别标记。
- 多区域编辑在原始内容上校验唯一匹配和重叠，再一次写入；格式保留，冲突不产生部分计划修改。

## 上下文与证据

任务约束、角色规则、验收和来源引用必须显式保留。初始输入及历史都设预算；精确 usage、估算与未知区分。预算不足时明确失败，不悄悄删除必需冻结材料。

历史压缩可关闭；启用前核实 SDK 合法历史、模型窗口和预算。摘要是派生材料，不成为人工批准；不拆散工具调用与结果，保留最近细节和可回查来源。

G1：首期接受取消或租约丢失竞争窗口内可能已完成的远端发布，必须记录与对账。发布结果无法确认时标记未知，不承诺自动回滚。

G2：必存执行元数据，访问沿用主机文件权限。新增 attempt 日志保存 30 天，在 Git 工作区外，容量有界；关键证据保存失败不回报成功。用户已明确 G2 覆盖全部 Worker 会话，包括 SDK/CLI 自身及派生线程；完整提示词、源码和会话正文不能因来自既有 SDK 而落盘。既有明确业务 report 和 FD 工件沿用其字段及保留契约，不能增添完整聊天转储。

默认不存完整提示词、源码或隐藏推理；密钥、leaseToken 不进入常规日志或插件环境。关键元数据包括输入版本、调用关联、终态、取消、发布和结果校验。允许字段、拒绝规则与 Runner 前置要求见 [公共输入契约](process-typescript/runtime-core/PUBLIC_CONTRACT.md)。会话准入必须在正文送入之前生效；能力未知时拒绝注册/领取。Codex 的禁存入口源码已经实现，其他落盘通道及兼容 Gate 见 [完整设计](../docs/worker-codex-no-store-design.md)；真实平台运行效果尚未认证。

## 运行时能力基线

| 运行时 | 当前源码与基线 | 验证边界 |
|---|---|---|
| API TypeScript | Agents 0.3.9 / OpenAI 6.49.0；完整终态保护、公共工具与九角色适配；parallelToolCalls=true | 独立离线验收进行中，真实部署端点未运行 |
| Codex TypeScript | SDK 0.155.1；公共生命周期与上下文接入已安装 native CLI | 内部工具细节明确 unknown；真实平台缺少禁存认证时拒绝准入 |
| Go | `AgentRuntime` SPI 显式选择 `codex-cli` 或 `openai-api`；共享期限、证据、workspace 与 Git 生命周期 | OpenAI tool loop 与各 API/CLI 部署行为仍需运行验证 |
| Python | SDK 0.157.0 wheel 静态核对；公开 launch_args_override 与 ephemeral/read 回执入口已接入 | 未安装或启动真实 SDK；禁存平台能力未知时拒绝准入 |

这个基线不是端点支持声明。不可观察的能力明确记录 unknown/unsupported，不静默将并行要求改为全局串行。

旧 workers/codex-* 保持参考示例定位，生产对齐覆盖四个 process Worker；demo-deployer 的权限、发布和回滚独立设计。TypeScript Worker 使用包内 runtime-core ESM，Go/Python 按此行为契约适配；Codex 禁存共用固定原生启动适配器，未经验证的平台/版本/配置在注册前拒绝。

## 输入与拒绝样例

- requirements + REQUIREMENT_ANALYSIS：只读，非空语义报告。
- coder + IMPLEMENTATION：可在指定工作区编辑；查询工具可并行。
- reviewer + IMPLEMENTATION：任务类型不匹配，模型启动前拒绝。
- reviewer + 文件写入：操作未授权，不能产生写入。
- verifier + INTEGRATION_VERIFICATION：解析为 integration-verifier 的 Profile 和报告契约。
- release-readiness 或 orchestrator：保留配置材料，但拒绝执行注册。
- 任意角色 + 过期 lease：不接受 complete/fail；已有副作用进入对账记录。
- 任意角色 + 关键证据不可保存：不能回报成功，不能由模型文本替代证据。

## 验证状态

静态读取了 API Worker 的 Profile、工具、上下文与生命周期，已安装 SDK 源码、Codex SDK 声明和 TaskExecutionService。首轮源码的一次编译检查通过；首轮独立离线测试 138 个用例执行，78 通过、60 失败。第二轮 261 个用例执行，256 个原始 pass-case、5 个失败；完整场景通过 255/285=89.47%，全 FD 分支尚未测量，两轮均已被 PM 退回。后续源码修复尚待新的独立授权验证，历史编译结果不代表新源码通过。以上样例仍是验收输入，完整 FD 尚未验收，真实端点或模型未调用。SDK 细节见 [SDK 能力基线](SDK_CAPABILITIES.md)，当前证据以 FD 报告与角色交接收据为准。
