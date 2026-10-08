# FD-005 独立测试报告 r27

<!-- aiw-data: FD-005-test-report-r27.json -->

## 结论

- 来源事件 `FD-005-000027-implementation-ready`；修订 27，FD 摘要 `dbc2bc71b3bb6d1fda4bd3fdfde2915fb68ae91a1e70baac6670f9dd31b139c7`；独立 Tester session `fd005-tester-20261005-bdc1bff5`。
- 唯一获批离线命令运行一次，**10 个本轮用例全部通过，exit 0**。其中 API-27 多字节读取返回 1791 字符、`complete=false`、`truncated=true`；API-29 正常批量编辑的工具输出 `complete=true`，重复匹配和重叠计划均未写入；API-28 截断的原始 JSON 参数报 `RESPONSES_TOOL_ARGUMENTS_INVALID` 且未写入。
- r19 的 15 项历史通过和 r23 的三个完整新增场景 API-17、18、24 继续保留。r23 的 API-27/29 两项失败仍是历史执行事实；本轮合法输出预算为 4096，API-29 的完整行为通过，支持其先前失败由非法夹具预算造成的静态推断，但不改写旧证据。API-27 的读取子行为通过，**搜索部分完成仍未测**，所以该场景仍未覆盖。
- 严格按完整场景统计，新增 API-28、29 两项，综合 **20/33＝60.61%**。API-16、19、23、27、33 的局部通过不计入覆盖。业务代码分支覆盖率未测量，无原始分支计数。**尚未达到 70%**，不建议 PM 接受为完整测试验收；若按流程继续，需补测或由 PM 记录明确例外与剩余风险。

## 场景与证据

| 场景 | 状态 | 本轮与历史证据或缺口 |
|---|---|---|
| API-01～09 | 历史通过 | r19 九角色 API 适配执行，本轮未重跑。 |
| API-10～15 | 历史通过 | r19 错配、报告、只读、工具失败、未知完成、Git 清理故障，本轮未重跑。 |
| API-16 | 未覆盖，局部通过 | r27 适配器工厂拒绝保留角色；生产 `index.ts` 注册前路径未执行。 |
| API-17～18 | 历史通过，复核通过 | r23/r27 分别观察只读角色写入拒绝和工作区越界拒绝。 |
| API-19 | 未覆盖，局部通过 | r27 集成别名 Profile 与报告通过；生产注册能力与领取路由未执行。 |
| API-20～22 | 未覆盖 | 并行、冲突排队及取消队列未执行。 |
| API-23 | 未覆盖，局部通过 | r27 强制取消模型请求通过；工具超时及资源终态未验证。 |
| API-24 | 历史通过，复核通过 | r23/r27 观察失租后不发送 complete/fail。 |
| API-25～26 | 未覆盖 | 生产信号排空、远端 push 对账未执行。 |
| API-27 | 未覆盖，局部通过 | r27 多字节读取被明确标为截断且有界；grep 搜索部分完成未执行。r23 失败保留。 |
| API-28 | 通过 | r27 截断且不可解析的工具原始参数被拒绝，目标文件未变化，无 complete。 |
| API-29 | 通过 | r27 用合法预算复核 BOM/CRLF 多区域编辑、重复匹配拒绝和重叠计划拒绝；r23 失败保留。 |
| API-30～32 | 未覆盖 | 历史压缩、完整 G2 证据边界和插件错误/版本/权限矩阵未执行。 |
| API-33 | 未覆盖，局部通过 | r27 旧八字段 Profile、报告和默认模型通过；manifest 兼容矩阵未执行。 |

同名 JSON 的 `scenarios` 逐项列出全部 33 个适用场景。公共核心只经 API Worker 公开组合路径间接观察；非 API 运行时不纳入本轮分母。

## 命令与风险

- 授权：[r27 Markdown](FD-005-test-authorization-r27.md) 与同名 JSON；唯一命令：`node tests/fd005/r27/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r27.json`；工作目录 `D:\03_projects\AI-tools\aiw-dev`。授权绑定事件、修订、FD 摘要、session 和四个调用文件 SHA-256。
- 原始结果：[执行摘要](../../../tests/fd005/evidence-r27/execution.json) 与 [Node 输出](../../../tests/fd005/evidence-r27/node-test-output.txt)。输出只记录工具终端码及完整性元数据，不保存工具正文。r19/r23 原始证据未修改。
- 合成 Responses 与内存 Control Plane 在本机执行真实 API Worker 适配器及 Agents SDK；拒绝 socket/DNS/TLS、清除真实 API/Worker token，测试文件仅写独占 OS 临时目录与本轮证据目录。未运行真实 Control Plane、模型端点、远端 Git、其他 Worker 或跨运行时 G2 平台认证。
- 仍有 13 个适用场景未完整覆盖，分支覆盖率不可用；生产注册、完整取消/终态、搜索部分完成、插件矩阵、真实服务与远端副作用均缺行为证明。历史 r23 两项失败不能抹去，本轮新夹具结果仅支持限定的原因推断。
