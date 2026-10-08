# FD-005 独立测试报告 r23

<!-- aiw-data: FD-005-test-report-r23.json -->

## 结论

- 来源事件：`FD-005-000023-implementation-ready`；FD 修订 23，摘要 `5687310002741b048722a581e2f01409df3d38bf1c536ddec841a43df726caf2`。
- 独立 Tester session：`fd005-tester-20261005-b07656ac`。本轮仅针对 `workers/process-typescript-with-api` 的公开生产适配入口、实际 Agents SDK 和九角色契约。模型 Responses 与 Control Plane 均为离线合成输入。
- 唯一获批命令执行一次，**exit 1；9 个新用例中 7 通过、2 失败**。API-27 与 API-29 在真实 SDK 工具路径得到 `UNRESOLVED_TOOL_FAILURE`，未达到预期完成断言。现有原始输出不足以区分夹具参数问题与生产缺陷；两项保持未覆盖，不能记为实现失败已定位，也不能记为通过。未修改测试后重跑。
- r19 的 15/15 历史通过证据继续有效。本轮完整新增 API-17、18、24 三个代表场景；API-16、19、23、33 的用例只通过局部断言。**综合需求场景覆盖 18/33＝54.55%**；15 个场景仍未完整覆盖。该综合值明确引用 r19 历史证据，不表示本轮运行 18 个用例。业务代码分支覆盖率未测量，无可用原始分支计数。
- 建议 PM 退回测试验收，要求独立分析两个失败夹具与其余覆盖缺口。低于 70% 和分支不可测均未获例外。

## 场景与证据

| 场景 | 结果 | 证据或限制 |
|---|---|---|
| API-01～09 | 历史通过 | r19 九角色实际 API 适配执行；本轮未重跑。 |
| API-10～15 | 历史通过 | r19 错配、报告、只读、工具失败、未知完成、Git 清理故障；本轮未重跑。 |
| API-16 | 局部通过，整体未覆盖 | `assertExecutableRole` 和生产适配器工厂拒绝两个保留角色；未启动 `index.ts` 验证注册前路径。 |
| API-17 | 通过 | Reviewer SDK 请求不提供写工具；伪造写调用被拒，文件保持原样且无完成上报。 |
| API-18 | 通过 | Coder 请求 `../outside.txt` 写入，外部文件保持原样且无完成上报；只覆盖代表性越界路径。 |
| API-19 | 局部通过，整体未覆盖 | verifier 集成任务选用集成 Profile 且报告 PASS；未验证进程注册能力和真实领取路由。 |
| API-20～22 | 未覆盖 | 并行、同文件或 shell 资源冲突、排队取消未执行。 |
| API-23 | 局部通过，整体未覆盖 | 强制取消使内存模型请求 AbortSignal 终止且无完成上报；工具超时和所有资源终态未验证。 |
| API-24 | 通过 | 合成任务心跳在模型活动时失租，Worker 抑制 `/complete` 和 `/fail`。 |
| API-25～26 | 未覆盖 | 生产信号排空和 push/远端对账未执行；离线无远端。 |
| API-27 | 失败用例，未覆盖 | 多字节长行读取路径返回 `UNRESOLVED_TOOL_FAILURE`，测试预期完成未成立；截断与搜索部分完成尚无证明。 |
| API-28 | 未覆盖 | 截断的工具参数调用未执行。 |
| API-29 | 失败用例，未覆盖 | `edit_many` 正常多区域编辑路径返回 `UNRESOLVED_TOOL_FAILURE`；BOM/CRLF、唯一性和重叠未得到有效断言。 |
| API-30～32 | 未覆盖 | 历史压缩、全会话证据隐私及插件边界未执行。 |
| API-33 | 局部通过，整体未覆盖 | 旧八字段 Profile 可载入，旧报告与默认模型路径可运行；manifest 兼容矩阵未执行。 |

JSON `scenarios` 给出全部 33 个场景的逐项机器记录；`passed` 的 15 个历史项均指向 r19，三个本轮完整新增项指向 r23。其余即使局部断言通过也保持 `uncovered`。

## 命令与风险

- 唯一命令：`node tests/fd005/r23/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r23.json`；工作目录 `D:\03_projects\AI-tools\aiw-dev`。授权：[r23 Markdown](FD-005-test-authorization-r23.md) 与同名 JSON，绑定本轮事件、FD 摘要、Tester session 和四个调用文件 SHA-256。
- 原始结果：[执行摘要](../../../tests/fd005/evidence-r23/execution.json)、[Node 原始输出](../../../tests/fd005/evidence-r23/node-test-output.txt)；其中记录 exit 1、9/7/2、未超时。历史原始证据在 `tests/fd005/evidence-r19/`，未改动。
- 测试脚本只在 OS 独占临时目录创建合成工作区，清除真实 API/Worker token，限定内存 Responses URL，并拒绝 socket、DNS、TLS。没有真实 Control Plane、模型、Git 远端或网络调用；没有运行其他 Worker、launcher 或平台入口。
- 本轮没有业务代码分支插桩，不能把用例数推算成分支覆盖率。生产注册、完整资源终态、真实 API/CP、远端发布、平台 G2、跨运行时和升级回滚仍无本轮行为证明。测试失败不触发自动重跑或重写原始证据。
