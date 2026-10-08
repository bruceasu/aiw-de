# FD-005 独立测试报告 r36

<!-- aiw-data: FD-005-test-report-r36.json -->

## 结论

独立 Tester `fd005-tester-20261005-r36-a4d7c9` 领取 `FD-005-000036-implementation-ready`。对应 FD 修订 36、SHA-256 `c91c8ebc64fedf695d33612b045483f5ece5e1b0c075f962f7472ad3d27518c1`。Planner 仅授权本报告所列的一条离线命令；该命令执行一次，约 1.0 秒，**exit 1**，未超时。脱敏输出没有 API-16 元数据或 TAP 摘要，故本轮没有可确认的行为断言结果，**不能把进程失败解释为角色拒绝**。没有重跑或改写历史证据。

本轮新夹具准备了两个保留角色 `release-readiness`、`orchestrator` 的生产源码入口用例，先分别运行再断言，但获批命令在可观察行为输出前失败。原始子进程 stderr 只在测试进程内处理且未保存；运行器只保留允许名单中的脱敏元数据行和 TAP 计数。本轮脱敏结果为空，因此无法确定失败发生在测试加载器、用例初始化或更早阶段，也无法记录具体 Node 错误码与失败模块。该限制是诊断缺口，不是生产代码缺陷证据。

33 个适用 API Worker 场景中，历史完整通过仍为 **23/33（69.70%）**。API-16 本轮未取得新证据，其他 9 项仍未完整覆盖。业务代码分支覆盖率**未测量**，没有原始分支计数。建议 PM 退回本轮测试验收。

## 场景与证据

| 场景 | 本轮状态 | 累计状态与证据 |
|---|---|---|
| API-16：生产入口在注册前拒绝两个保留角色 | **未执行到可确认行为断言** | r30、r33 的失败仍是历史事实；r36 命令 exit 1，脱敏输出为空，仍未完整覆盖 |
| API-01 至 API-15、API-17 至 API-18、API-22、API-24、API-27 至 API-29、API-33 | 本轮未运行 | 23 项历史完整通过，详见 [r33 报告](FD-005-test-report-r33.md)及其逐项证据 |
| API-19 至 API-21、API-23、API-25 至 API-26、API-30 至 API-32 | 本轮未运行 | 9 项历史未完整覆盖；部分静态或执行证据不计作完整通过 |

同名 JSON 的 `scenarios` 逐项列出 33 项，`passed` 为 23 项。API-16 标为 `blocked`，其他未完整覆盖项标为 `uncovered`；本轮执行用例的行为结果不可确认，因此没有新增通过或失败的行为断言计数。

## 命令、授权与剩余风险

- 唯一实际执行命令：`node tests/fd005/r36/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r36.json`；工作目录 `D:\03_projects\AI-tools\aiw-dev`；exit 1，约 1.0 秒，未超时。
- 授权：[r36 Markdown](FD-005-test-authorization-r36.md) 与同名 JSON，绑定事件、FD 修订与摘要、Tester session、五个调用文件 SHA-256。
- 原始执行摘要：[execution.json](../../../tests/fd005/evidence-r36/execution.json)。[脱敏输出](../../../tests/fd005/evidence-r36/node-test-output.txt) 仅有空行。原始 stderr、绝对路径、token、环境值均未写入证据；这也使本轮错误阶段无法反查。OS 临时目录按运行器路径核验后删除。
- 测试范围仅 API Worker 的生产源码入口。没有运行其他 Worker、launcher、公共核心独立入口、真实模型或 Control Plane、远端 Git、平台 G2、覆盖率工具、构建、编译或网络命令。
- 剩余风险：API-16 仍未得到生产入口行为证明；完整场景覆盖低于 70%，分支未测；真实服务、远端 Git、平台 G2 与非 API Worker 均未由本轮认证。下轮若继续诊断，应新建夹具并重新授权，保留允许名单内的启动前错误码与模块类别，不能重用或改写本轮证据。
