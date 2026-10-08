# FD-005 独立测试报告 r30

<!-- aiw-data: FD-005-test-report-r30.json -->

## 结论

独立 Tester session `fd005-tester-20261005-e8931791` 领取 `FD-005-000030-implementation-ready`，对应 FD 修订 30、摘要 `422bebacd953340895e93a6d254266eecf58b8ed26d77c8dc5d8202fea359e17`。Planner 仅授权一条离线命令；实际执行一次，耗时约 2.7 秒，**exit 1，17 项中 15 项通过、2 项失败**。不重跑、不改写原始证据。

按 33 个适用 API Worker 可观察场景严格统计，历史累计 20 项、本轮新增 API-22 和 API-27 两项完整通过，累计 **22/33（66.67%）**，仍低于 70%。业务代码分支覆盖率**未测量**，无原始分支计数。建议 PM 退回；执行失败不能作为通过验收。

## 场景与证据

| 场景 | 本轮结果与覆盖判断 |
|---|---|
| API-01～15 | 历史 r19 完整通过；本轮未重跑。 |
| API-16 | **失败，未完整覆盖**。既有 adapter 工厂拒绝保留角色通过；本轮生产 `index.ts` 子进程 exit 1、无超时、无网络 guard 命中，但测试没有在错误输出中识别角色名。它可能在更早的加载/配置阶段失败，不能据此证明生产注册前拒绝。原始测试没有保存该子进程 stderr，原因未判定。 |
| API-17～18 | 历史 r23/r27 通过；本轮复核通过。 |
| API-19 | 历史别名 Profile/报告与本轮复核通过；注册能力及领取路由未执行，仍未完整覆盖。 |
| API-20 | 同轮两个独立 `read` 返回、call ID 和有界结果通过；缺少并发时序及上限证据，**仅部分**。 |
| API-21 | 同路径 `write` 后 `edit` 顺序与结果通过；Shell 冲突排队未测试，**仅部分**。 |
| API-22 | 排队写入在占用工作区的受控 Shell 运行期间取消，目标原值保留、无 `complete`；另一次普通工具失败后合法写入仍执行，整个 attempt 未误报完成。**完整通过**。 |
| API-23 | 历史模型取消复核通过；工具超时及所有资源终态未验证，仍未完整覆盖。 |
| API-24 | 历史租约丢失抑制上报复核通过。 |
| API-25～26 | drain/强制终止及远端 push 对账未执行。 |
| API-27 | r27 多字节读取截断通过；本轮 `grep` 返回 `complete=false`、`truncated=true`、`data.scanComplete=false`，原始信封 3560 字符且未超过 4096 上限。**完整通过**。 |
| API-28～29 | 历史 r27 通过；本轮复核通过。r23 的两项失败仍保留为历史事实。 |
| API-30 | 历史压缩与关闭配置未完整执行。 |
| API-31 | 九角色合成 API 会话的指定 `evidenceRoot` 未发现合成源码/密钥/租约标记，关键证据目录故障阻止成功上报；真实 SDK/CLI 持久化、所有落盘通道及平台 G2 未认证，**仅部分**。 |
| API-32 | 插件版本、输出、超时和权限矩阵未执行。 |
| API-33 | **失败，未完整覆盖**。旧八字段 Profile、报告 verdict 和模型路径复核通过；新增 planner manifest 测试中 `complete.args[2]` 为 `null`，与预期原样文本不符。尚不能区分断言位置与实际兼容缺陷。 |

同名 JSON 的 `scenarios` 逐项列出全部 33 项，其中 22 项为 `passed`；执行失败的 API-16/33 为 `blocked`，部分和未执行项均不计入通过数。

## 命令与风险

- 唯一实际测试命令：`node tests/fd005/r30/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r30.json`，工作目录 `D:\03_projects\AI-tools\aiw-dev`。
- 授权：[r30 Markdown](FD-005-test-authorization-r30.md) 与同名 JSON；绑定事件、修订、FD 摘要、Tester session 和五份调用文件 SHA-256。
- 原始证据：[执行摘要](../../../tests/fd005/evidence-r30/execution.json) 与 [Node 原始输出](../../../tests/fd005/evidence-r30/node-test-output.txt)。该输出保留两项失败堆栈及 17 项结果。
- 合成 Responses、合成 Control Plane 与独占 OS 临时工作区；runner 禁止真实 socket/DNS/TLS、移除真实 API/Worker token。固定 Shell 仅在临时工作区写标记并等待 600 毫秒。未运行其他 Worker、launcher、真实模型/服务、远端 Git、生产部署或跨运行时验证。
- 剩余风险：2 项失败、11 项未完整覆盖、分支未测；API-16 子进程原始错误未保存，API-33 参数位置需核对公开调用契约；API-31 仅说明本轮合成 API 会话的有限证据边界，不能外推平台 G2。
