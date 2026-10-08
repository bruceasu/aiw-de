# FD-009 独立审查报告，r1

<!-- aiw-data: FD-009-review-r1.json -->

- **Reviewer 会话：** `fd009-reviewer-20261007-8e72ba`
- **来源事件：** `FD-009-000010-test-accepted`
- **审查范围：** `master` (`ccf2603`) 至 `feature/FD-009` (`e5af38a6cc75d3d45038308635c38867969b5833`)
- **结论：** 请求修改。

## 发现

1. **高：冲突的 runtime 选择被启动脚本静默覆盖。** [run-role.sh](../../../workers/process-typescript/scripts/run-role.sh) 第 11 行和 [run-role.ps1](../../../workers/process-typescript/scripts/run-role.ps1) 第 11 行均无条件把参数写入 `AIW_AGENT_RUNTIME`。当环境已有 `AIW_AGENT_RUNTIME=codex-sdk`、脚本参数为 `openai-agents`（或反向）时，脚本会改用参数值，统一入口只看改写后的环境值，无法发现冲突。FD 第 28、58 行明确要求“选值冲突时在注册前失败”，且避免部署时身份或 policy 静默变化。应在脚本写入前拒绝非空且不同的既有值，并保持两个平台行为一致；补充对应场景的测试设计或静态证据。此发现由代码路径静态推导，未执行启动脚本。

## 已核对证据

- Worker 的 `AgentRuntime` SPI 只依赖共享 attempt 类型，Codex/Agents SDK 调用集中在各自适配器；`runtime-core` 仍负责 Pull、lease 和 attempt。旧 API 包已从分支删除，统一入口保留既有 runtime 标签和分后端默认 Worker ID。旧包的技能目录在统一包中仍存在。
- Worker 只运行一次无产物的 TypeScript 语法编译，并明确没有类型或依赖验证；本审查没有把它当作后端运行成功证据。
- Tester 授权记录与执行命令均为 `node --test tests/fd009/cli-contract.test.mjs`，工作目录、FD 修订 8、摘要 `60a1e95a3683465fc204f25f20ece6feae8f0c31d1f6043e14d508b9474549d5`、Tester 会话 `fd009-tester-20261007-f817be` 一致。授权依据是聚焦离线低风险测试，无需人工批准。Node 运行器在载入测试前因 `spawn EPERM` 失败，0 个行为断言执行，0 个产品行为测试失败。
- Tester 将 25 个行为场景分别列出，S07/S08 标为“已准备但受阻”，没有把静态目录检查或部分场景算作覆盖。需求覆盖率 0/25、分支覆盖率未测量；PM 在修订 9 的版本化决策中对此作了明确例外。本审查尊重该例外，但它不豁免上述未满足的冲突拒绝要求。

## 命令与剩余风险

实际执行只读的 `aiw fd show/resume`、`git status`、`git diff --stat master...HEAD`、限定源码的 `git diff master...HEAD`、`git diff --check master...HEAD`、`git rev-parse`、`git log -1`、`rg`、`Get-Content`；为领取事件执行 `aiw fd claim FD-009 FD-009-000010-test-accepted --session fd009-reviewer-20261007-8e72ba`。未运行测试、类型检查、构建、网络或真实后端。`git diff --check` 未报告空白问题。

两个后端的注册、权限、执行、取消、attempt 终态及 Control Plane 兼容性仍无独立行为证据。修复本发现后仍需如实保留这些残余风险，不应将 0% 覆盖描述为验证通过。
