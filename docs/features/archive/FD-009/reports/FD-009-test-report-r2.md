# FD-009 独立测试报告，r2

<!-- aiw-data: FD-009-test-report-r2.json -->

## 结论

本轮测试 FD 修订 12（摘要 `c91d54d5fe9bf52fe8c8a0d85fd2da5f1b99a5f4030c2c7d093d91589d40cc82`）。Tester Session `fd009-tester-r2-20261007-95171d` 领取事件 `FD-009-000012-implementation-ready`。

在 Planner 的两个独立授权下，直接调用公开 PowerShell 角色启动脚本，各执行一次环境变量与脚本参数的反向冲突。两次均产生准确的 `conflicting runtime` 诊断且未出现 Node 启动输出。按“明确拒绝冲突配置”这一可观察行为，两项通过。**工具报告的退出码两次均为 1；Worker 修复报告预期 2，本轮不能确认实际脚本退出码为 2，也没有独立进程跟踪证明 Node 未启动。** 这项差异需 Reviewer 判定是否影响接受。

本轮可观察需求场景覆盖 **2/26（7.69%）**；业务代码分支覆盖率 **未测量**。第 1 轮 Node 测试运行器在载入用例前报 `spawn EPERM`，本轮未重跑该命令。其余 24 个场景未执行，包括缺失/未知 runtime 拒绝、两后端注册与 attempt 生命周期。若 PM 接受本轮，仍需对低于 70% 的覆盖作明确例外并承担剩余风险。

## 场景与证据

| ID | 可观察行为 | 本轮状态 |
| --- | --- | --- |
| S01 | 显式选择 codex-sdk 后端并完成 Worker 注册 | 未覆盖 |
| S02 | 显式选择 openai-agents 后端并完成 Worker 注册 | 未覆盖 |
| S03 | requirements、architect、planner、coder、integrator、reviewer、verifier、integration-verifier、repairer 映射到原任务类型 | 未覆盖 |
| S04 | verifier 执行 INTEGRATION_VERIFICATION 时使用专用 Profile | 未覆盖 |
| S05 | 两个后端保持各自 runtime 注册标签和默认 Worker ID | 未覆盖 |
| S06 | 已批准 AI policy 与选择的 runtime 匹配 | 未覆盖 |
| S07 | 角色启动脚本拒绝缺失 runtime | 未覆盖 |
| S08 | 角色启动脚本拒绝未知 runtime | 未覆盖 |
| S09A | 环境 AIW_AGENT_RUNTIME=codex-sdk 与脚本参数 openai-agents 冲突时明确拒绝且不进入 Node Worker | 通过：冲突诊断，退出码见下文 |
| S09B | 环境 AIW_AGENT_RUNTIME=openai-agents 与脚本参数 codex-sdk 冲突时明确拒绝且不进入 Node Worker | 通过：冲突诊断，退出码见下文 |
| S10 | Agents 缺失 OPENAI_API_KEY 时在注册或领取前拒绝 | 未覆盖 |
| S11 | Codex 缺失有效 launcher 时在注册或领取前拒绝 | 未覆盖 |
| S12 | Codex 只读角色强制只读 sandbox 并执行审批约束 | 未覆盖 |
| S13 | Agents 只读角色无法使用写入或 shell 工具 | 未覆盖 |
| S14 | Agents 可写角色工具范围及 shell 开关符合授权 | 未覆盖 |
| S15 | 两个后端保持模型选择、预算和工具超时约束 | 未覆盖 |
| S16 | 缺少可验证能力时拒绝运行且不伪造 resume、approval 或 usage | 未覆盖 |
| S17 | 成功执行生成兼容 attempt 结果与证据 | 未覆盖 |
| S18 | 后端错误生成兼容失败类别和证据 | 未覆盖 |
| S19 | 无效终态生成兼容失败结果 | 未覆盖 |
| S20 | attempt 超时触发取消、清理和兼容失败结果 | 未覆盖 |
| S21 | 显式取消触发终止与兼容证据 | 未覆盖 |
| S22 | lease 丢失或终止不确定时 drain 并保留工作区 | 未覆盖 |
| S23 | 资源终态不确定时停止领取且不误报完成 | 未覆盖 |
| S24 | Pull、lease、attempt 与 Control Plane 协议保持原有行为 | 未覆盖 |
| S25 | 角色报告和代码任务 workspaceResult 符合公开契约 | 未覆盖 |

旧包目录不存在、SPI/SDK 隔离和文档引用属于静态核查，未计入上述 26 个可观察场景。第 1 轮准备的 `tests/fd009/cli-contract.test.mjs` 仍保留，但未在本轮运行。

## 命令与风险

- 授权记录：`FD-009-test-authorization-r2.md`、`FD-009-test-authorization-r3.md`，均绑定本轮事件、FD 摘要及 Tester Session。
- 命令 1：`$env:AIW_AGENT_RUNTIME='codex-sdk'; & .\workers\process-typescript\scripts\run-role.ps1 requirements openai-agents`。工作目录为 `D:\03_projects\AI-tools\aiw-dev\.wt\FD-009`；输出 `conflicting runtime: AIW_AGENT_RUNTIME=codex-sdk, argument=openai-agents`；工具退出码 1。
- 命令 2：`$env:AIW_AGENT_RUNTIME='openai-agents'; & .\workers\process-typescript\scripts\run-role.ps1 requirements codex-sdk`。同一工作目录；输出 `conflicting runtime: AIW_AGENT_RUNTIME=openai-agents, argument=codex-sdk`；工具退出码 1。
- 未运行测试运行器、构建、类型检查、在线 Codex CLI/OpenAI API、模拟 Control Plane 或分支覆盖测量。
- 剩余风险：本轮仅证实冲突诊断；退出码 2 和 Node 未启动没有独立证据。两个后端的权限、注册、attempt 终态、证据、租约与协议兼容性仍缺运行时验证。
