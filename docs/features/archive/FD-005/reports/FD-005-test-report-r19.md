# FD-005 独立测试报告 r19

<!-- aiw-data: FD-005-test-report-r19.json -->

## 结论

- 来源事件：`FD-005-000019-implementation-ready`；FD 修订 19，SHA-256 `8b50c27f32a1b75e3eef572f5f96eddfa57b5cd367ddb1af7000920df8b3f84d`；Tester session `fd005-tester-20261005-58dba13b`。
- 唯一授权的离线命令退出码 0；15 个合成行为用例执行，15 通过、0 失败、0 跳过。33 个本轮适用 API Worker 行为场景中，15 个有执行证据，18 个未覆盖；需求场景覆盖率 **15/33 = 45.45%**。业务代码分支覆盖率**未测量**：命令没有分支插桩，测试使用模型替身，不能由用例数推算代码覆盖。
- r13/r18 修复目标已由 `CLEANUP-locked-worktree` 触达：真实本地 Git worktree 在执行中被 `git worktree lock` 锁定，真实 `WorkspaceSession.cleanup()` 删除失败。可观察结果是工作树保留、`workspace-cleanup: preserved`、无 `resource-terminal: settled`、无 `ready-to-report`、无 `/complete`，并有一次 `/fail`。测试最终只清理自己临时目录内的 worktree。
- 本轮证据只覆盖 API Worker 的生产 `createApiAttemptAdapter` 与公共 attempt 的组合路径。模型返回使用合成替身，没有启动 `index.ts` 进程、Agents SDK 的真实模型/工具循环、Control Plane 服务或付费 API。下表未覆盖项不能视为通过；建议 PM 按低于 70% 的实际覆盖率和剩余风险作显式决定。

## 场景与证据

以下每行是一个独立可观察场景。`P` 为通过，`U` 为未覆盖；P 的原始断言与输出都在 `tests/fd005/r19/worker.test.mjs` 和 `tests/fd005/evidence-r19/node-test-output.txt`。

| ID | 可观察行为 | 状态 | 用例或未覆盖原因 |
|---|---|---|---|
| API-01 | requirements 完成正确任务 | P | `ROLE-requirements` |
| API-02 | architect 完成正确任务 | P | `ROLE-architect` |
| API-03 | planner 完成正确任务 | P | `ROLE-planner` |
| API-04 | coder 完成正确任务 | P | `ROLE-coder` |
| API-05 | integrator 完成正确任务 | P | `ROLE-integrator` |
| API-06 | reviewer 完成正确任务 | P | `ROLE-reviewer` |
| API-07 | verifier 完成正确任务 | P | `ROLE-verifier` |
| API-08 | integration-verifier 完成正确任务 | P | `ROLE-integration-verifier` |
| API-09 | repairer 完成正确任务 | P | `ROLE-repairer` |
| API-10 | reviewer 错配任务不能完成 | P | `ROLE-reviewer: mismatched task` |
| API-11 | 审查报告缺失 verdict 不能完成 | P | `REPORT-reviewer: absent verdict` |
| API-12 | 只读工作区变脏不能完成 | P | `READONLY-reviewer` |
| API-13 | 未解决工具失败不能被完成文本掩盖 | P | `TOOL-error`，工具状态由合成运行时注入 |
| API-14 | 完成响应未知时不重发 complete 或改发 fail | P | `REPORT-unknown` |
| API-15 | Git 工作树删除失败不能上报成功 | P | `CLEANUP-locked-worktree`，真实本地 Git 删除失败 |
| API-16 | 保留角色在生产注册前拒绝 | U | 未启动 `index.ts` 进程 |
| API-17 | Reviewer 写工具被拒绝 | U | 未调用真实工具循环 |
| API-18 | Coder 只能在指定工作区编辑 | U | 未调用真实文件工具 |
| API-19 | verifier 集成别名与能力路由 | U | 未测试生产注册/领取路径 |
| API-20 | 独立读取和不同文件有界并行 | U | 未调用真实 ToolHost |
| API-21 | 同文件编辑与 shell 冲突排队 | U | 未调用真实 ToolHost 或 shell |
| API-22 | 取消排队及失败后后续操作 | U | 未构造实际工具资源竞争 |
| API-23 | 模型/工具超时及取消后的资源终态 | U | 未运行 Agents SDK 或工具子进程 |
| API-24 | 租约丢失时抑制完成上报 | U | 未驱动真实任务心跳竞争 |
| API-25 | drain 与第二次信号的强制终止 | U | 未启动 `index.ts` 进程 |
| API-26 | push 期间租约丢失与远端对账 | U | 本轮本地 Git 夹具不设远端 |
| API-27 | 多字节、截断、搜索部分完成的工具输出 | U | 未运行真实文件工具 |
| API-28 | 被长度截断的工具参数不得执行 | U | 未运行真实模型流/工具解析 |
| API-29 | 多区域编辑唯一性、重叠及 BOM/CRLF | U | 未运行真实 `edit_many` |
| API-30 | 历史预算和可关闭的压缩 | U | 模型历史由替身代替 |
| API-31 | 证据关键保存故障与全会话 G2 隐私 | U | 本轮仅检查清理故障的元数据；未认证所有落盘通道 |
| API-32 | 插件错误、版本、超时与权限不扩张 | U | 未配置或调用插件 |
| API-33 | 旧 Profile/manifest 与模型路径兼容 | U | 未跑兼容输入矩阵 |

纯静态或用户排除的范围：Codex TypeScript、Go、Python、原生 launcher、公共核心独立入口、真实 Control Plane/模型端点、跨运行时 G2、升级和回滚操作均不进入本轮分母。它们仍是 FD 实现或发布审查要求，不能从本轮 15 个通过用例外推行为结果。

## 命令与风险

- 授权：[FD-005-test-authorization-r19.md](FD-005-test-authorization-r19.md) 与同名 JSON；授权绑定本次实现事件、FD 修订与摘要、Tester session、精确命令和四个测试文件摘要。执行前核对四个摘要一致。
- 工作目录：`D:\03_projects\AI-tools\aiw-dev`。实际唯一测试命令：`node tests/fd005/r19/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r19.json`。退出码 0，Node 报告 15/15 通过，耗时 1841.4925 ms。
- 原始证据：`tests/fd005/evidence-r19/node-test-output.txt` 和 `tests/fd005/evidence-r19/execution.json`。运行时仅创建 OS 临时目录内的本地 Git 仓库/worktree，属主路径核对后清理；原始证据使用 `wx` 写入，不覆盖旧 r5/r8/r11 记录。未联网、下载依赖、启动其他 Worker 或真实端点。
- 剩余风险：未覆盖的 18 个 API 行为场景、未测分支覆盖、合成模型替身与真实 Agents SDK 差异、未启动生产 `index.ts`、真实 CP/远端 Git/G2 平台认证缺口。虽然 r18 删除失败场景通过，完整 FD 验收仍不能据此视为完成。
