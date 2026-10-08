# FD-009 独立测试报告，r1

<!-- aiw-data: FD-009-test-report-r1.json -->

## 结论

FD 修订 8、摘要 `60a1e95a3683465fc204f25f20ece6feae8f0c31d1f6043e14d508b9474549d5`。Tester Session `fd009-tester-20261007-f817be` 领取实现事件 `FD-009-000008-implementation-ready`。

已准备 `tests/fd009/cli-contract.test.mjs`，覆盖旧包路径不存在和公开启动脚本拒绝缺失、未知 runtime。Planner 授权记录为 `FD-009-test-authorization-r1.md`。授权命令实际执行一次，但 Node 测试运行器在载入测试文件前启动子进程时报 `spawn EPERM`。输出显示文件级 1 fail、0 pass；**没有运行任何行为断言，不能把该失败认定为产品行为失败或通过**。没有重试或改用其他命令。

需求行为场景覆盖率 **0/25（0%）**；业务代码分支覆盖率 **未测量**。25 个场景均未执行，其中 S07、S08 已准备可执行用例但被运行器阻断，其余 23 个未准备独立可运行环境。建议 PM 将本轮视为证据不足；若继续，须记录低于 70% 的明确例外与风险。

## 场景与证据

| ID | 可观察行为 | 状态 |
| --- | --- | --- |
| S01 | 显式选择 codex-sdk 后端并完成 Worker 注册 | 未覆盖 |
| S02 | 显式选择 openai-agents 后端并完成 Worker 注册 | 未覆盖 |
| S03 | requirements、architect、planner、coder、integrator、reviewer、verifier、integration-verifier、repairer 映射到原任务类型 | 未覆盖 |
| S04 | verifier 执行 INTEGRATION_VERIFICATION 时使用专用 Profile | 未覆盖 |
| S05 | 两个后端保持各自 runtime 注册标签和默认 Worker ID | 未覆盖 |
| S06 | 已批准 AI policy 与选择的 runtime 匹配 | 未覆盖 |
| S07 | 角色启动脚本拒绝缺失 runtime | 已准备，执行受阻 |
| S08 | 角色启动脚本拒绝未知 runtime | 已准备，执行受阻 |
| S09 | 统一入口拒绝冲突 runtime 配置且不自动回退 | 未覆盖 |
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

旧包目录不存在、SPI 依赖隔离、文档引用属于纯静态检查，未计入 25 个可观察行为场景；旧包目录检查虽写入测试文件，文件未载入，不能称为通过。

## 命令与风险

- 授权命令：`node --test tests/fd009/cli-contract.test.mjs`
- 工作目录：`D:\03_projects\AI-tools\aiw-dev\.wt\FD-009`
- 实际结果：退出码 1；原始输出含 `Error: spawn EPERM`、`tests 1`、`pass 0`、`fail 1`。这是运行器的文件级失败。
- 本轮未运行 Worker 构建、类型检查、在线 Codex CLI/OpenAI API、模拟 Control Plane 或代码分支覆盖测量。
- 剩余风险：两个后端的启动、权限、attempt 终态、证据、租约及 Control Plane 兼容性仍无独立运行时证据。工作树没有可执行的 `dist/index.js`，当前环境还阻止 Node 测试运行器创建子进程。
