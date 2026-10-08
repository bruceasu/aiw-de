# FD-005 独立测试报告 r39

<!-- aiw-data: FD-005-test-report-r39.json -->

## 结论

独立 Tester `fd005-tester-20261005-r39-f5c82a` 领取 `FD-005-000039-implementation-ready`，对应 FD 修订 39、SHA-256 `2dc44b6b895a1c1aeece81a568c214c94f9c78b96486d25b4b32abf920e9e770`。Planner 授权的唯一离线命令执行一次，约 1.0 秒，**exit 1**，未超时；没有重跑，也没有改写 r30、r33、r36 的历史失败证据。

本轮 API-16 用例已启动并到达断言。`release-readiness` 与 `orchestrator` 两个子进程都以 exit 1 结束，且均被夹具归类为 `module-or-syntax-load-error`，没有得到保留角色检查的证据；1 个已执行行为测试失败，0 个通过。诊断只保存允许字段，没有原始 stderr。两个角色的具体 Node 错误码与安全模块 basename 均为 `null`，因此目前不能区分 TypeScript 加载器、依赖导入或入口本身的语法问题；顶层 `ERR_ASSERTION` 只代表测试断言失败，**不是角色子进程的根因**。不能把非零退出码当作注册前角色拒绝。

33 个适用 API Worker 场景中，历史完整通过仍为 **23/33（69.70%）**。API-16 未完整覆盖，另有 9 项未完整覆盖。业务代码分支覆盖率**未测量**，无原始分支计数。建议 PM 退回本轮测试验收。

## 场景与证据

| 场景 | 本轮状态 | 累计状态与证据 |
|---|---|---|
| API-16：生产入口在注册前拒绝两个保留角色 | **失败**：用例启动并到达断言；两角色均在模块或语法加载阶段退出 | 未完整覆盖；r30、r33、r36 的失败保留，本轮诊断见 [diagnostic.json](../../../tests/fd005/evidence-r39/diagnostic.json) |
| API-01 至 API-15、API-17 至 API-18、API-22、API-24、API-27 至 API-29、API-33 | 本轮未运行 | 23 项历史完整通过，见 [r36 报告](FD-005-test-report-r36.md)及同名 JSON 的逐项证据 |
| API-19 至 API-21、API-23、API-25 至 API-26、API-30 至 API-32 | 本轮未运行 | 9 项历史未完整覆盖；部分证据不计作完整通过 |

同名 JSON 的 `scenarios` 逐项列出 33 项：23 项 `passed`、API-16 为 `blocked`、其他 9 项为 `uncovered`。本轮执行了 1 个行为用例，失败 1 个，未运行 32 个场景的新用例。

## 命令、授权与剩余风险

- 唯一实际执行命令：`node tests/fd005/r39/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r39.json`；工作目录 `D:\03_projects\AI-tools\aiw-dev`；exit 1，约 1.0 秒，未超时。
- 授权：[r39 Markdown](FD-005-test-authorization-r39.md) 与同名 JSON，绑定事件、FD 修订和摘要、Tester session、五个调用文件 SHA-256。
- 原始执行摘要：[execution.json](../../../tests/fd005/evidence-r39/execution.json)；受控诊断：[diagnostic.json](../../../tests/fd005/evidence-r39/diagnostic.json)。原始 stdout/stderr、绝对路径、token、环境值均未落盘。OS 临时目录经路径核验后删除。
- 测试范围仅 API Worker 的生产源码入口；公共核心只作为 API Worker 依赖路径参与。没有运行其他 Worker、launcher、公共核心独立入口、真实模型或 Control Plane、远端 Git、平台 G2、覆盖率工具、构建、编译或网络命令。
- 剩余风险：API-16 注册前拒绝未获证明；完整场景覆盖低于 70%，分支未测；真实服务、远端 Git、平台 G2 与非 API Worker 不在本轮认证范围。若继续定位加载错误，应创建新夹具并重新授权，保留 r39 失败事实。
