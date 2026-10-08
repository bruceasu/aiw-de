# FD-005 独立测试报告，r8

<!-- aiw-data: FD-005-test-report-r8.json -->

## 结论

建议 **fail，交回 PM 决策**。获批离线命令实际运行一次：**261 个行为用例，256 通过、5 失败**，Node exit 1。完整独立行为覆盖 **255/285 = 89.47%**；`scenarios` 为 **255 passed、5 blocked、25 uncovered**。其中 24 项未运行；`REPORT-manifest` 的 pass-case 只验证报告字段，完整行为仍 partial/uncovered，不计入完整覆盖。

本报告绑定的是来源交接的 **FD revision 9**；文件名 r8 对应本轮 Worker authored revision 8 的交接报告。未修改首轮失败历史，未读产品实现方法体建立预期，未改产品代码或已授权测试代码，未重跑。

| 绑定项 | 实值 |
| --- | --- |
| FD | `FD-005`，`docs/features/FD-005_WORKER_PI.md` |
| 来源事件 | `FD-005-000009-implementation-ready` |
| tested_fd_revision | 9 |
| tested_fd_digest | `6d51664531db4418fe20c5f05dbd0ddf797ce64f8689e48d096221970330627b` |
| Tester session | `fd005-tester-20261005-7865d383` |
| Planner 授权 | [FD-005-test-authorization-r8.md](FD-005-test-authorization-r8.md)／[同名 JSON](FD-005-test-authorization-r8.json) |
| 测试入口 SHA-256 | `aedf7391c0d21e33670138e2ce4e9214184fb355101657e6288ca1eb635204c8` |
| 开始／结束（UTC） | 2026-10-04T23:23:00.535Z／2026-10-04T23:23:30.869Z |
| 命令耗时／最大授权时限 | 30.334 秒／375 秒 |
| 机器数据 | [FD-005-test-report-r8.json](FD-005-test-report-r8.json) |

### 统计口径

| 指标 | 本轮实际值 |
| --- | --- |
| 原分母／新增独立场景／全适用分母 | 222／63／285 |
| prepared／executed | 261／261 |
| 原始 pass-case／failed case | 256／5 |
| 完整 covered／blocked／uncovered 场景 | 255／5／25 |
| 未执行行为 | 24 |
| 部分执行 | 4：两项摘要失败、Git 收尾失败、REPORT-manifest pass-case |
| 明确到达目标断言的失败 | 1：SDK-tool-parameters 的成功上报拒绝断言 |
| 夹具分类前置不足 | 1：TOOL-failure-then-text |

原始 failed case 五项全部保留；scenarios 的 blocked 表示独立行为未完整通过，并不把 raw failed 改成 skipped 或 passed。两个摘要用例在 complete 调用数断言处失败，不能据此判定摘要隐私目标已运行或具体根因。普通 JavaScript Error 的工具夹具未建立可修正错误前提，未知或致命错误允许终止；该夹具问题与实际目标断言失败分别记录。

### 实际执行范围

| Suite | executed | pass-case | failed | 观察边界 |
| --- | --- | --- | --- | --- |
| [api.test.mjs](../../../tests/fd005/r8/api.test.mjs) | 60 | 60 | 0 | 真实 API 适配/角色/报告/取消；内存 SSE 与假 CP |
| [core.test.mjs](../../../tests/fd005/r8/core.test.mjs) | 121 | 120 | 1 | public runtime-core；受控工具/模型/资源/证据 |
| [guidance.test.mjs](../../../tests/fd005/r8/guidance.test.mjs) | 11 | 11 | 0 | 公开 guidance 与批准候选选择 |
| [sdk.test.mjs](../../../tests/fd005/r8/sdk.test.mjs) | 16 | 13 | 3 | 实际 Agents SDK 工具/上下文/输出入口 |
| [session.test.mjs](../../../tests/fd005/r8/session.test.mjs) | 27 | 27 | 0 | 实际 launcher + 假 native/RPC，合成准入 |
| [transport-plugin.test.mjs](../../../tests/fd005/r8/transport-plugin.test.mjs) | 21 | 21 | 0 | 实际 CP 客户端内存 fetch + 固定假插件 |
| [workspace.test.mjs](../../../tests/fd005/r8/workspace.test.mjs) | 5 | 4 | 1 | public WorkspaceSession + 隔离本地 Git |

实际 API 生产入口通过公开 `createApiAttemptAdapter`、`ApiRuntime` 和现有安装的 Agents SDK 调用。测试加载器仅在内存转译必要的实际 TypeScript 模块；实现方法体没有供 Tester 检查或用来构造预期。响应来自受控内存 fetch 的官方形状 Responses SSE，没有 socket、真实 CP、模型服务或 Worker 主循环。合成批准模型名与 API key 只供内存夹具，不能称为外部模型端到端通过。

Agents/Agents core/Agents OpenAI 版本为 **0.3.9**，OpenAI 为 **6.49.0**，使用已有依赖，没有安装。实际 launcher 连接的是假 native/RPC；policy/proof 明确 `syntheticTestOnly`、绑定假程序与专属临时路径。它们只证明适配和准入契约，不能认证真实 Codex、用户认证缓存或平台 G2 禁存。

## 场景与证据

### 五个失败的阶段与证据边界

### `TOOL-failure-then-text`

- 原始结果：failed；Node testNumber 180；ATTEMPT_CANCELLED。
- 到达阶段：夹具分类前置不足；第二工具回调未进入。
- 观察与结论：第一个工具夹具抛出未标记可修正类别的普通 JavaScript Error('SYNTHETIC_FAILURE')；第二个无关工具在回调前收到 ATTEMPT_CANCELLED，assertSuccessful 未到达。公开契约允许未知或致命工具错误终止，普通 Error 不能独立证明已声明的可修正业务错误。本轮 raw case 仍失败，不能据此认定失败被完成文本掩盖，也不把允许取消改写为该目标行为通过。
- 不可用证据：夹具未以公开错误类别建立可修正前提；本轮不改码、不重跑。
- 证据：[逐用例结果](../../../tests/fd005/evidence-r8/results/core.test.mjs.jsonl)、[原始 Node 事件](../../../tests/fd005/evidence-r8/node-test-events.jsonl)；用例 tests/fd005/r8/core.test.mjs#TOOL-failure-then-text。

### `SDK-summary-privacy`

- 原始结果：failed；Node testNumber 205；实际断言：Expected values to be strictly equal: 0 !== 1 。
- 到达阶段：实际 SDK 普通请求已发生；完成上报前置断言失败。
- 观察与结论：assertCompleted 期望 complete 调用数 1，实际 0。原始元数据顺序 scope 70（0-based，JSONL 第 71 行）只有三个普通流式请求，未观察到摘要请求；summaryCalls、批准摘要模型及继承期限的目标断言未到达。没有保存 client.fail 与临时 attempt 终态日志，不能依据 complete=0 推出具体内部原因或摘要隐私泄漏。
- 不可用证据：受控临时根安全清理后，未持久化的 client.fail/attempt 终态日志不可恢复；无补测授权。
- 证据：[逐用例结果](../../../tests/fd005/evidence-r8/results/sdk.test.mjs.jsonl)、[原始 Node 事件](../../../tests/fd005/evidence-r8/node-test-events.jsonl)；用例 tests/fd005/r8/sdk.test.mjs#SDK-summary-privacy。

### `API-store-false`

- 原始结果：failed；Node testNumber 206；实际断言：Expected values to be strictly equal: 0 !== 1 。
- 到达阶段：正常请求的参数已观察；摘要请求/完成上报前置断言失败。
- 观察与结论：assertCompleted 期望 complete 调用数 1，实际 0。原始元数据顺序 scope 71（0-based，JSONL 第 72 行）只有三个普通流式请求，其 store=false、truncation=disabled、批准模型与 parallel_tool_calls 参数已观察；没有摘要请求，无法完整验证正常与摘要两类请求以及 tracing/logging 的全部目标。不能据此宣称摘要泄漏或推断具体根因。
- 不可用证据：client.fail 和临时 attempt 终态日志未持久化；metadata 的 signalAborted 是 scope 结束快照，不能证明请求发起时取消或超时。无补测授权。
- 证据：[逐用例结果](../../../tests/fd005/evidence-r8/results/sdk.test.mjs.jsonl)、[原始 Node 事件](../../../tests/fd005/evidence-r8/node-test-events.jsonl)；用例 tests/fd005/r8/sdk.test.mjs#API-store-false。

### `SDK-tool-parameters`

- 原始结果：failed；Node testNumber 208；实际断言：Expected values to be strictly equal: 1 !== 0 。
- 到达阶段：目标成功上报拒绝断言失败；后续文件副作用断言未执行。
- 观察与结论：合成实际 SDK function_call 的 write 参数含公开 strict schema 未允许的 undeclared 字段。assertNoComplete 实际 complete 调用数 1，期望 0，目标拒绝成功上报断言失败。随后的 readFile 原内容断言未执行；临时根已清理，不能声称已观察到文件内容被修改。失败证据只支持非法参数未阻止本次成功上报，不独立证明文件副作用或实现根因。
- 不可用证据：文件内容后置断言被前一个失败中止；临时文件已按安全规则清理，本轮无补测授权。
- 证据：[逐用例结果](../../../tests/fd005/evidence-r8/results/sdk.test.mjs.jsonl)、[原始 Node 事件](../../../tests/fd005/evidence-r8/node-test-events.jsonl)；用例 tests/fd005/r8/sdk.test.mjs#SDK-tool-parameters。

### `WORKSPACE-git-finalize`

- 原始结果：failed；Node testNumber 260；PROCESS_EXIT_FAILED。
- 到达阶段：工作区已物化；finalize 返回 PROCESS_EXIT_FAILED。
- 观察与结论：materialize/beforeExecution 已进入，受控本地 Git finalize 返回 PROCESS_EXIT_FAILED；resultRevision、changedFiles、publicationRef 和远端核对的目标断言均未到达。原始日志保留异常栈，但没有持久化实际 Git argv/stderr、提交与 bare remote 状态，不能区分受控 guard/配置问题与产品根因，也不能声称发布无写入、已回滚或发布成功。
- 不可用证据：实际 Git stderr/argv 与提交/远端状态未持久化，拥有的临时根已清理；无补测授权。
- 证据：[逐用例结果](../../../tests/fd005/evidence-r8/results/workspace.test.mjs.jsonl)、[原始 Node 事件](../../../tests/fd005/evidence-r8/node-test-events.jsonl)；用例 tests/fd005/r8/workspace.test.mjs#WORKSPACE-git-finalize。

### 部分通过项与原历史

`REPORT-manifest` 原始用例通过，只验证 adapter 的 report 字段；完整规划 manifest 和线上审批消费未执行。Git finalize 失败后，WorkspaceResult/发布核对断言也未完成。因此保留 **actual_outcome=passed，status=uncovered，verification_scope=partial**，不把 report 字段通过推广为完整兼容验收。

首轮 [r5 双报告](FD-005-test-report-r5.md) 与 [evidence-r5](../../../tests/fd005/evidence-r5/execution.json) 原样保留：138 executed、78 pass-case、60 failed，78/222 = 35.14%，tested revision 6。原八个调用文件及原 cases 的摘要保持不变；本轮仅新增 r8 文件与证据，不覆盖失败记录。

### 行为覆盖与业务分支覆盖

| 覆盖域 | 实际统计 | 限定 |
| --- | --- | --- |
| 全适用独立行为 | **255/285 = 89.47%** | 包含全部原 222 场景与新增 63；未测真实跨语言/NS/部署项仍在分母 |
| runtime-core 的 15 个 JavaScript 文件 | 分支 **629/777 = 80.95%**；行 1256/1317；函数 170/191 | 仅该公共核心的受控离线执行 |
| 实际 API 的 8 个内存转译文件 | 分支 **94/116 = 81.03%**；行 290/417；函数 35/44 | 是转译后 JavaScript 插桩；无 source map，不能冒称原 TypeScript 行号映射 |
| 原始 Node 总计（含测试 helpers） | 分支 883/1110 = 79.55%；行 2331/2542；函数 295/340 | 原始总计，不是全 FD 业务代码分支覆盖率 |
| 全 FD 业务分支 | **未完整测量** | Go launcher/guard 未插桩；真实 Go/Python/Codex TS Worker、主循环、真实平台与部署流程未执行 |

原始统计在 [Node `test:coverage` 事件](../../../tests/fd005/evidence-r8/node-test-events.jsonl) 与 [execution 元数据](../../../tests/fd005/evidence-r8/execution.json)，23 个原始 V8 文件在 `tests/fd005/evidence-r8/v8-raw/`。同名 JSON 保存分域文件与计数。不将 helpers 的覆盖率、合成假运行时覆盖率或编译成功推成全 FD 验收。

### 逐场景证据索引

- **E0**：[execution.json](../../../tests/fd005/evidence-r8/execution.json)，每项独立 ID 的 prepared 与实际 outcome。
- **E1**：以下逐 suite 结果 JSONL；ID 对应的原始行为结果。
- **E2**：[node-test-events.jsonl](../../../tests/fd005/evidence-r8/node-test-events.jsonl)，原始测试事件、异常栈与覆盖率。
- 请求/版本/生产模块/launcher 元数据：[api-request-metadata.jsonl](../../../tests/fd005/evidence-r8/results/api-request-metadata.jsonl)、[sdk-version-metadata.jsonl](../../../tests/fd005/evidence-r8/results/sdk-version-metadata.jsonl)、[production-module-sha256.jsonl](../../../tests/fd005/evidence-r8/results/production-module-sha256.jsonl)、[launcher-transcripts.jsonl](../../../tests/fd005/evidence-r8/results/launcher-transcripts.jsonl)。

| Suite | E1 |
| --- | --- |
| `api.test.mjs` | [逐用例原始结果](../../../tests/fd005/evidence-r8/results/api.test.mjs.jsonl) |
| `core.test.mjs` | [逐用例原始结果](../../../tests/fd005/evidence-r8/results/core.test.mjs.jsonl) |
| `guidance.test.mjs` | [逐用例原始结果](../../../tests/fd005/evidence-r8/results/guidance.test.mjs.jsonl) |
| `sdk.test.mjs` | [逐用例原始结果](../../../tests/fd005/evidence-r8/results/sdk.test.mjs.jsonl) |
| `session.test.mjs` | [逐用例原始结果](../../../tests/fd005/evidence-r8/results/session.test.mjs.jsonl) |
| `transport-plugin.test.mjs` | [逐用例原始结果](../../../tests/fd005/evidence-r8/results/transport-plugin.test.mjs.jsonl) |
| `workspace.test.mjs` | [逐用例原始结果](../../../tests/fd005/evidence-r8/results/workspace.test.mjs.jsonl) |

元数据共 74 个请求 scope；两项摘要失败按顺序对应 0-based scope 70/71（JSONL 行 71/72），各仅三个普通流式请求，没有摘要请求。`signalAborted` 是 scope 结束时的快照，且元数据没有唯一 scopeId；不能据它证明请求发起时超时/取消。临时 attempt 终态日志、client.fail、Git 实际 argv/stderr/提交与远端状态未持久化，安全清理后不可恢复；本轮无补测授权。

### 285 个独立可观察场景

表中用例后的 `#ID` 表示独立测试注册 ID；并非源码文件的 HTML 锚点。通过项仅证明其所属 suite 的公开接口与受控合成输入范围；所属 suite 的限制见“实际执行范围”。JSON 中每项保存完整 test_cases/raw_evidence 与未覆盖理由，作为机器统计依据。

| ID | 独立验收行为 | FD 验收项 | status | 原始执行 | 用例 | 原始证据 | 未覆盖原因或范围 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `ROLE-requirements-tool-success` | requirements 的已授权读取有合法完整结果 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-requirements-tool-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-requirements-tool-failure` | requirements 的工具失败不能被后续成功文本掩盖 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-requirements-tool-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-requirements-access` | requirements 的写入被拒绝且文件不变 | 1 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-requirements-access` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-requirements-cancel` | requirements 的取消后新工具不执行 | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-requirements-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-requirements-success` | requirements 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-requirements-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-requirements-failure` | requirements 的实际 API Worker 模型/工具失败形成既有 failureCategory | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-requirements-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-requirements-capability-reject` | requirements 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-requirements-capability-reject` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-requirements-report` | requirements 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-requirements-report` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-requirements-cancel` | requirements 的实际 API Worker 取消后无过期 complete | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-requirements-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-architect-tool-success` | architect 的已授权读取有合法完整结果 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-architect-tool-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-architect-tool-failure` | architect 的工具失败不能被后续成功文本掩盖 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-architect-tool-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-architect-access` | architect 的写入被拒绝且文件不变 | 1 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-architect-access` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-architect-cancel` | architect 的取消后新工具不执行 | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-architect-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-architect-success` | architect 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-architect-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-architect-failure` | architect 的实际 API Worker 模型/工具失败形成既有 failureCategory | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-architect-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-architect-capability-reject` | architect 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-architect-capability-reject` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-architect-report` | architect 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-architect-report` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-architect-cancel` | architect 的实际 API Worker 取消后无过期 complete | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-architect-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-planner-tool-success` | planner 的已授权读取有合法完整结果 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-planner-tool-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-planner-tool-failure` | planner 的工具失败不能被后续成功文本掩盖 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-planner-tool-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-planner-access` | planner 的写入被拒绝且文件不变 | 1 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-planner-access` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-planner-cancel` | planner 的取消后新工具不执行 | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-planner-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-planner-success` | planner 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-planner-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-planner-failure` | planner 的实际 API Worker 模型/工具失败形成既有 failureCategory | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-planner-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-planner-capability-reject` | planner 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-planner-capability-reject` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-planner-report` | planner 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-planner-report` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-planner-cancel` | planner 的实际 API Worker 取消后无过期 complete | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-planner-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-coder-tool-success` | coder 的已授权读取有合法完整结果 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-coder-tool-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-coder-tool-failure` | coder 的工具失败不能被后续成功文本掩盖 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-coder-tool-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-coder-access` | coder 的编辑只修改指定工作区内文件 | 1 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-coder-access` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-coder-cancel` | coder 的取消后新工具不执行 | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-coder-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-coder-success` | coder 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-coder-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-coder-failure` | coder 的实际 API Worker 模型/工具失败形成既有 failureCategory | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-coder-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-coder-capability-reject` | coder 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-coder-capability-reject` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-coder-report` | coder 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-coder-report` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-coder-cancel` | coder 的实际 API Worker 取消后无过期 complete | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-coder-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-integrator-tool-success` | integrator 的已授权读取有合法完整结果 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-integrator-tool-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-integrator-tool-failure` | integrator 的工具失败不能被后续成功文本掩盖 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-integrator-tool-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-integrator-access` | integrator 的编辑只修改指定工作区内文件 | 1 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-integrator-access` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-integrator-cancel` | integrator 的取消后新工具不执行 | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-integrator-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-integrator-success` | integrator 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-integrator-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-integrator-failure` | integrator 的实际 API Worker 模型/工具失败形成既有 failureCategory | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-integrator-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-integrator-capability-reject` | integrator 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-integrator-capability-reject` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-integrator-report` | integrator 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-integrator-report` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-integrator-cancel` | integrator 的实际 API Worker 取消后无过期 complete | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-integrator-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-reviewer-tool-success` | reviewer 的已授权读取有合法完整结果 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-reviewer-tool-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-reviewer-tool-failure` | reviewer 的工具失败不能被后续成功文本掩盖 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-reviewer-tool-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-reviewer-access` | reviewer 的写入被拒绝且文件不变 | 1 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-reviewer-access` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-reviewer-cancel` | reviewer 的取消后新工具不执行 | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-reviewer-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-reviewer-success` | reviewer 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-reviewer-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-reviewer-failure` | reviewer 的实际 API Worker 模型/工具失败形成既有 failureCategory | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-reviewer-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-reviewer-capability-reject` | reviewer 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-reviewer-capability-reject` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-reviewer-report` | reviewer 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-reviewer-report` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-reviewer-cancel` | reviewer 的实际 API Worker 取消后无过期 complete | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-reviewer-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-verifier-tool-success` | verifier 的已授权读取有合法完整结果 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-verifier-tool-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-verifier-tool-failure` | verifier 的工具失败不能被后续成功文本掩盖 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-verifier-tool-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-verifier-access` | verifier 的写入被拒绝且文件不变 | 1 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-verifier-access` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-verifier-cancel` | verifier 的取消后新工具不执行 | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-verifier-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-verifier-success` | verifier 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-verifier-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-verifier-failure` | verifier 的实际 API Worker 模型/工具失败形成既有 failureCategory | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-verifier-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-verifier-capability-reject` | verifier 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-verifier-capability-reject` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-verifier-report` | verifier 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-verifier-report` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-verifier-cancel` | verifier 的实际 API Worker 取消后无过期 complete | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-verifier-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-integration-verifier-tool-success` | integration-verifier 的已授权读取有合法完整结果 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-integration-verifier-tool-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-integration-verifier-tool-failure` | integration-verifier 的工具失败不能被后续成功文本掩盖 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-integration-verifier-tool-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-integration-verifier-access` | integration-verifier 的写入被拒绝且文件不变 | 1 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-integration-verifier-access` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-integration-verifier-cancel` | integration-verifier 的取消后新工具不执行 | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-integration-verifier-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-integration-verifier-success` | integration-verifier 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-integration-verifier-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-integration-verifier-failure` | integration-verifier 的实际 API Worker 模型/工具失败形成既有 failureCategory | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-integration-verifier-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-integration-verifier-capability-reject` | integration-verifier 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-integration-verifier-capability-reject` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-integration-verifier-report` | integration-verifier 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-integration-verifier-report` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-integration-verifier-cancel` | integration-verifier 的实际 API Worker 取消后无过期 complete | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-integration-verifier-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-repairer-tool-success` | repairer 的已授权读取有合法完整结果 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-repairer-tool-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-repairer-tool-failure` | repairer 的工具失败不能被后续成功文本掩盖 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-repairer-tool-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-repairer-access` | repairer 的编辑只修改指定工作区内文件 | 1 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-repairer-access` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-repairer-cancel` | repairer 的取消后新工具不执行 | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ROLE-repairer-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-repairer-success` | repairer 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-repairer-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-repairer-failure` | repairer 的实际 API Worker 模型/工具失败形成既有 failureCategory | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-repairer-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-repairer-capability-reject` | repairer 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-repairer-capability-reject` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-repairer-report` | repairer 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | 1,8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-repairer-report` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-repairer-cancel` | repairer 的实际 API Worker 取消后无过期 complete | 1,3 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-repairer-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PATH-outside` | 工作区外路径被拒绝 | 1 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#PATH-outside` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PATH-git` | Git 内部路径被拒绝 | 1 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#PATH-git` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PATH-link` | 链接路径不能越过工作区策略 | 1 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#PATH-link` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PATH-hardlink` | 危险硬链接写入被拒绝 | 1 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#PATH-hardlink` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RESOURCE-independent` | 独立文件操作可以并行 | 2 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#RESOURCE-independent` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RESOURCE-readers` | 多个读取可以并行 | 2 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#RESOURCE-readers` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RESOURCE-same-file` | 同文件修改按读改写全过程串行 | 2 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#RESOURCE-same-file` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RESOURCE-read-write` | 同文件读取与修改保持一致性 | 2 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#RESOURCE-read-write` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RESOURCE-shell` | 未声明范围的 shell 与工作区修改互斥 | 2 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#RESOURCE-shell` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RESOURCE-limit` | 活动操作数量不超过并发上限 | 2 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#RESOURCE-limit` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RESOURCE-cancel-wait` | 排队等待取消后不启动操作 | 2,3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#RESOURCE-cancel-wait` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RESOURCE-active-cancel` | 活动 I/O 未结束时取消不能提前释放资源 | 2,3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#RESOURCE-active-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RESOURCE-failure-release` | 失败后下一操作取得资源 | 2 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#RESOURCE-failure-release` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `TOOL-parameters` | 非法参数在副作用前失败 | 2,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#TOOL-parameters` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `TOOL-shell-denied` | 缺少 shell 授权时不执行命令 | 1,9 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#TOOL-shell-denied` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `TOOL-nested` | 嵌套工具调用经过相同边界并保留关联 ID | 2,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#TOOL-nested` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `TOOL-partial` | 未完成搜索的模型可见结果明确为不完整 | 5 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#TOOL-partial` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `TOOL-output-bound` | 工具结果 envelope 与截断提示一同受字节上限约束 | 5 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#TOOL-output-bound` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `TOOL-timeout-active` | 操作超时后仍等待活动 I/O 终态 | 2,3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#TOOL-timeout-active` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `OUTPUT-short` | 未超限文本完整返回 | 5 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#OUTPUT-short` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `OUTPUT-chinese` | 中文和 emoji 预算按 UTF-8 字节计算 | 5 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#OUTPUT-chinese` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `OUTPUT-long-line` | 长单行截断不产生无效 Unicode | 5 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#OUTPUT-long-line` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `OUTPUT-lines` | 行数上限包含截断提示 | 5 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#OUTPUT-lines` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `JSON-complete` | 合法完整 JSON 原样解析 | 5 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#JSON-complete` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `JSON-too-large` | 超限 JSON 明确拒绝，不返回半个结构 | 5 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#JSON-too-large` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `JSON-invalid` | 非法或不符合 schema 的 JSON 明确拒绝 | 5,9 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#JSON-invalid` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EDIT-multiple` | 多区域计划一次生成全部修改 | 6 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EDIT-multiple` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EDIT-missing` | 缺少匹配时拒绝且不写任何部分修改 | 6 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EDIT-missing` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EDIT-duplicate-match` | 重复匹配时拒绝且原文件不变 | 6 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EDIT-duplicate-match` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EDIT-overlap` | 重叠编辑在写入前拒绝 | 6 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EDIT-overlap` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EDIT-duplicate-plan` | 重复编辑区域在写入前拒绝 | 6 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EDIT-duplicate-plan` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EDIT-format` | 修改保留 BOM 与 CRLF | 6 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EDIT-format` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EDIT-baseline` | 基线摘要变化时拒绝原子写入 | 6 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EDIT-baseline` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EDIT-diff-bound` | 编辑结果 diff 受字节预算约束 | 6 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EDIT-diff-bound` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EDIT-cancel` | 写入前取消不会修改文件 | 3,6 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EDIT-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `CONTEXT-sources` | 输入来源具有路径、版本摘要与字节记录 | 7,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#CONTEXT-sources` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `CONTEXT-required` | 必需规则、验收和冻结材料均保留 | 7 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#CONTEXT-required` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `CONTEXT-overflow` | 首轮超限明确失败而非删除必需输入 | 7 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#CONTEXT-overflow` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `CONTEXT-skills` | 原 Skill 主文、references 与 agents 展开兼容 | 7,10 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#CONTEXT-skills` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `HISTORY-off` | 关闭历史压缩时不调用摘要模型且原输入保留 | 7 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#HISTORY-off` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `HISTORY-budget` | 启用摘要必须具有显式预算 | 7 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#HISTORY-budget` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `HISTORY-derived` | 摘要是派生输入且原始来源可回查 | 7 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#HISTORY-derived` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `HISTORY-pairs` | 压缩不拆散工具调用和结果 | 7 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#HISTORY-pairs` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `HISTORY-cancel` | 摘要取消后无新增摘要和无限重试 | 3,7 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#HISTORY-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `HISTORY-failure` | 摘要失败不进行无界重试 | 7 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#HISTORY-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `HISTORY-max-two` | 同一历史压缩器最多生成两次摘要 | 7 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#HISTORY-max-two` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EVIDENCE-outside` | 关键证据目录位于 Git 工作区之外 | 8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EVIDENCE-outside` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EVIDENCE-forbidden-root` | 证据根在工作区内时拒绝 | 8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EVIDENCE-forbidden-root` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EVIDENCE-correlation` | 工具与输入/结果版本可以关联追踪 | 8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EVIDENCE-correlation` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EVIDENCE-redaction` | 正文、密钥和 leaseToken 不进入元数据 | 8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EVIDENCE-redaction` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EVIDENCE-capacity` | 关键元数据超限后健康校验失败 | 8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EVIDENCE-capacity` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EVIDENCE-write-failure` | 关键写入失败后健康校验失败 | 8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EVIDENCE-write-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EVIDENCE-terminal-expiry` | 拥有且已终态的日志在30天后可清理 | 8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EVIDENCE-terminal-expiry` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EVIDENCE-active-retain` | 活动日志即使很旧也不自动删除 | 8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EVIDENCE-active-retain` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EVIDENCE-foreign-retain` | 不带所有权标记的目录不自动删除 | 8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EVIDENCE-foreign-retain` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ATTEMPT-cancel-prepare` | 准备阶段取消后不执行下一阶段 | 3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ATTEMPT-cancel-prepare` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ATTEMPT-cancel-model` | 模型阶段取消后不启动工具或发布 | 3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ATTEMPT-cancel-model` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ATTEMPT-cancel-tool` | 工具阶段取消后不启动发布 | 3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ATTEMPT-cancel-tool` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ATTEMPT-cancel-publish` | 发布阶段取消后不回报有效完成 | 3,4 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ATTEMPT-cancel-publish` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ATTEMPT-timeout` | 整体期限覆盖执行阶段并传出取消 | 3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ATTEMPT-timeout` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ATTEMPT-cleanup-wait` | 独立清理预算不能把活动资源写成已结束 | 3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ATTEMPT-cleanup-wait` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `MODEL-completed` | 已完成且完整的模型响应可接受 | 5 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#MODEL-completed` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `MODEL-incomplete` | 长度截断的模型响应在工具执行前拒绝 | 5 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#MODEL-incomplete` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `MODEL-unknown` | 未知响应终态明确失败 | 5,10 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#MODEL-unknown` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `MODEL-invalid-call` | 不完整工具参数即使部分 JSON 可解析也不得执行 | 5 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#MODEL-invalid-call` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `MODEL-truncated-call` | 残余参数 JSON 可解析的长度截断响应仍不得执行工具 | 5 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#MODEL-truncated-call` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `MODEL-stream-incomplete` | 流结束没有完整终态时拒绝 | 5 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#MODEL-stream-incomplete` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `MODEL-usage-unknown` | 缺少实际 usage 不能当作精确零值 | 7,10 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#MODEL-usage-unknown` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PROCESS-success` | 离线子进程退出、输出和完整性可观察 | 3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#PROCESS-success` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PROCESS-overflow` | 子进程结构化输出超限明确失败 | 5 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#PROCESS-overflow` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PROCESS-cancel` | 子进程取消返回终态并停止输出 | 3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#PROCESS-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ENV-minimum` | 插件/工具子进程不取得 CP、模型密钥与 leaseToken | 8,9 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#ENV-minimum` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PLUGIN-disabled` | 没有 catalog 时插件默认关闭 | 9 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#PLUGIN-disabled` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PLUGIN-malformed` | 非法 catalog 格式被拒绝 | 9 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#PLUGIN-malformed` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PLUGIN-missing` | 不存在的插件入口明确失败 | 9 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#PLUGIN-missing` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SESSION-missing` | 缺少禁存清单时在会话前拒绝 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#SESSION-missing` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SESSION-unverified` | 未验证证据不能使会话准入 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#SESSION-unverified` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SESSION-sdk-mismatch` | SDK 版本绑定不一致时拒绝 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#SESSION-sdk-mismatch` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SESSION-native-digest` | native 摘要不符时拒绝 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#SESSION-native-digest` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SESSION-launcher-digest` | launcher 摘要不符时拒绝 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#SESSION-launcher-digest` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SESSION-proof-digest` | 证据摘要不符时拒绝 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#SESSION-proof-digest` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SESSION-control-missing` | 十项通道证据缺失时拒绝 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#SESSION-control-missing` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SESSION-config-change` | 固定配置源发生变化后拒绝 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#SESSION-config-change` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SESSION-workspace-file` | 任务可写根中的清单不能授予准入 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#SESSION-workspace-file` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SESSION-raw-evidence` | rawEvidence 摘要不符时拒绝 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#SESSION-raw-evidence` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SESSION-fake-admission` | 只绑定假 native 的合成政策可以验证准入适配 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#SESSION-fake-admission` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `LAUNCHER-preflight` | preflight 不启动假 native 或发送提示词 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#LAUNCHER-preflight` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `LAUNCHER-exec` | 首次 exec 强制 ephemeral、stdin 与原模型参数 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#LAUNCHER-exec` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `LAUNCHER-resume` | 通过准入后仍拒绝 resume | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#LAUNCHER-resume` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `LAUNCHER-save-output` | 通过准入后仍拒绝输出文件参数 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#LAUNCHER-save-output` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `LAUNCHER-prompt-argv` | 通过准入后仍拒绝正文 argv | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#LAUNCHER-prompt-argv` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `LAUNCHER-conflict` | 通过准入后仍拒绝隐私配置冲突 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#LAUNCHER-conflict` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `LAUNCHER-environment` | 假 native 不取得适配器/CP/调试环境 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#LAUNCHER-environment` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `LAUNCHER-stderr` | 假 native 的原始 stderr 正文不向主机泄漏 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#LAUNCHER-stderr` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RPC-ephemeral` | thread/start 必须显式 ephemeral true | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#RPC-ephemeral` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RPC-valid-turn` | 已核验 ephemeral 线程可接收合成正文 turn | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#RPC-valid-turn` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RPC-receipt` | 非 ephemeral 或有磁盘 path 的回执在正文前被拒绝 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#RPC-receipt` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RPC-read-receipt` | 公开 thread/read 回执的磁盘 path 冲突在正文前被拒绝 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#RPC-read-receipt` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RPC-turn-order` | turn 只允许已核验的 ephemeral 线程 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#RPC-turn-order` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RPC-resume` | 持久化 resume/fork RPC 被拒绝 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#RPC-resume` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RPC-disk-fork` | 持久化 fork RPC 被拒绝 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#RPC-disk-fork` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RPC-approval` | SDK 默认 accept 审批回复不能取得宿主授权 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/session.test.mjs#RPC-approval` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-preserved-reject` | release-readiness 与 orchestrator 在注册前拒绝 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#ROLE-preserved-reject` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `ROLE-verifier-alias` | verifier 的集成任务使用既有 integration-verifier Profile 与能力 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#ROLE-verifier-alias` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `REPORT-semantic-empty` | 前三个语义角色的空报告拒绝 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#REPORT-semantic-empty` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `REPORT-verdict-missing` | 审查/验证缺少末行 verdict 时拒绝 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#REPORT-verdict-missing` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `REPORT-verdict-final` | verdict 后有正文或 verdict 非法时拒绝 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#REPORT-verdict-final` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `REPORT-readonly-dirty` | 只读工作区变脏时不回报成功 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#REPORT-readonly-dirty` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `REPORT-manifest` | 规划 manifest 和代码 workspaceResult 的既有契约保持兼容 | 10 | uncovered／未覆盖 | pass-case／partial | `tests/fd005/r8/api.test.mjs#REPORT-manifest` | E0 + E1（undefined） + E2 | 用例通过只证明实际 adapter 的 report 字段。完整规划 manifest 和线上审批消费未执行；Git finalize 用例失败，WorkspaceResult/发布目标未完成，因此该独立行为仍为 partial/uncovered。 |
| `LEASE-lost` | 真实角色适配的租约丢失停止新增副作用并拒绝过期结果 | 3,4 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#LEASE-lost` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PUBLISH-completed-race` | 发布已经完成的租约竞争记录引用、版本和对账状态 | 4 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未执行实际 CP 租约竞争与已完成发布的对账；本地 Git 收尾用例失败且不含线上竞争，无法证明该行为。 |
| `PUBLISH-unknown-race` | 发布结果未知的租约竞争不声称回滚或无写入 | 4 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未执行实际 CP 租约竞争、不可判定发布与恢复对账；没有部署服务或受控远端故障资源，不声称回滚或无写入。 |
| `COMPLETE-response-lost` | CP complete 响应丢失不重跑整个业务 attempt | 3,8 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#COMPLETE-response-lost` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `DRAIN-inflight` | drain 停止领取且允许在途 attempt 结束 | 3,11 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未启动实际 Worker 主循环与 drain 运维入口；仅 public attempt/SDK 取消测试不能证明停止领取并等待在途任务。 |
| `DRAIN-force` | 第二次终止取消在途任务并有资源终态 | 3,11 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未运行实际 Worker 两次终止流程及在途 slot；拥有测试进程安全关闭不等于 Worker 强制终止资源状态通过。 |
| `PLUGIN-version` | 插件/解释器版本或摘要不一致时拒绝 | 9 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#PLUGIN-version` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PLUGIN-timeout` | 实际固定查询插件超时明确失败 | 9 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#PLUGIN-timeout` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PLUGIN-output` | 插件超限/非法输出明确失败 | 9 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#PLUGIN-output` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PLUGIN-network` | 未有网络授权的 GitHub 查询在外部请求前拒绝 | 9 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#PLUGIN-network` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-store-false` | API 实际 SDK 正常与摘要请求均 store false 且 tracing/logging 关闭 | 8 | blocked／阻断 | failed case／partial | `tests/fd005/r8/sdk.test.mjs#API-store-false` | E0 + E1（undefined） + E2 | assertCompleted 期望 complete 调用数 1，实际 0。原始元数据顺序 scope 71（0-based，JSONL 第 72 行）只有三个普通流式请求，其 store=false、truncation=disabled、批准模型与 parallel_tool_calls 参数已观察；没有摘要请求，无法完整验证正常与摘要两类请求以及 tracing/logging 的全部目标。不能据此宣称摘要泄漏或推断具体根因。 |
| `API-parallel-setting` | API 实际 SDK 保持并行工具设置与关联 | 2,10 | passed／通过 | pass-case | `tests/fd005/r8/sdk.test.mjs#API-parallel-setting` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `CODEX-TS-bridge` | Codex TS 的公开 SDK 入口保留模型政策并覆盖公共生命周期 | 3,7,10 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未运行固定 Codex TS SDK 或真实 native 会话；假 native 的 launcher 契约测试不能证明真实 SDK 模型政策与生命周期。 |
| `GO-admission` | Go Worker 无准入证据时在注册/领取前拒绝 | 8,10 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未启动真实 Go Worker 的注册/领取入口；合成 launcher/策略拒绝测试不能代替 Go Worker 准入。无额外真实运行授权。 |
| `PYTHON-admission` | Python Worker 无准入证据时在注册/领取前拒绝 | 8,10 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未启动真实 Python Worker 的注册/领取入口；合成策略 fixture 不能代替 Python Worker 准入。无额外真实运行授权。 |
| `GO-lifecycle` | Go Worker 的期限、取消、证据、工作区和报告端到端对齐 | 3,8,10 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未运行真实 Go Worker 端到端期限、取消、证据、工作区与报告；本轮临时 Go 编译只生成 launcher/假 native/Git guard。 |
| `PYTHON-lifecycle` | Python Worker 的期限、取消、证据、工作区和报告端到端对齐 | 3,8,10 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未运行真实 Python Worker 端到端期限、取消、证据、工作区与报告；没有 Python 真实 SDK/模型/服务会话授权。 |
| `PYTHON-sdk-receipt` | 真实固定 Python SDK 在送正文前核对公开 thread/read 回执 | 8,10 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未运行固定真实 Python SDK 的 thread/read 回执入口；假 native RPC 回执仅证明合成 launcher 适配，不覆盖 Python SDK。 |
| `NS-roles-slots-attempts` | 九角色、两个 slot、多 attempt 和 CP 重试全部经过实际禁存入口 | 8 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未进行实际 Codex/Go/Python 会话九角色、两个 slot、多 attempt 和 CP 重试的全过程禁存监测；合成政策/假程序不提供主机认证。 |
| `NS-normal-no-body` | 实际正常会话在全过程监测中无正文落盘 | 8 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未执行真实平台正常会话的全过程文件/导出监测；内存 API SSE 和假 native 无法证明真实主机正文禁存。 |
| `NS-tool-failure-no-body` | 实际工具失败会话在全过程监测中无正文落盘 | 8 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未执行真实平台工具失败会话的全过程禁存监测；本轮合成失败测试不能认证平台无正文落盘。 |
| `NS-model-failure-no-body` | 实际模型失败会话在全过程监测中无正文落盘 | 8 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未执行真实平台模型失败会话的全过程禁存监测；内存 Responses 故障不覆盖真实模型与主机渠道。 |
| `NS-timeout-no-body` | 实际超时会话在全过程监测中无正文落盘 | 8 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未执行真实平台超时会话的全过程禁存监测；合成超时与自有进程清理不能认证主机无正文写入。 |
| `NS-cancel-no-body` | 实际显式取消会话在全过程监测中无正文落盘 | 8 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未执行真实平台显式取消会话的全过程禁存监测；本轮实际 SDK 使用合成内存响应而非真实平台会话。 |
| `NS-lease-no-body` | 实际租约丢失会话在全过程监测中无正文落盘 | 8 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未执行真实平台租约丢失会话的全过程禁存监测；内存 CP 租约夹具不覆盖真实平台写入渠道。 |
| `NS-termination-tree` | 实际父进程强杀和逃逸子进程不突破禁存/资源终态 | 3,8 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未执行真实父进程强杀与逃逸子进程禁存监测；本轮只检查拥有测试树结束，未认证平台全进程树及正文禁存。 |
| `NS-other-channels` | 实际 memory/state-cache/hooks/plugins/trace/诊断均无正文写入或导出 | 8 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未监测真实平台 memory/state-cache/hooks/plugins/trace/诊断等全渠道；本轮假插件/受控环境不能证明主机所有渠道。 |
| `NS-derived` | 实际子 Agent、fork 和压缩继承禁存或在创建前拒绝 | 8 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未创建真实子 Agent、fork 与真实平台压缩会话；合成历史压缩不能证明实际派生会话继承禁存或创建前拒绝。 |
| `NS-host-auth` | 实际认证缓存与主机介质符合部署 G2 政策 | 8 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未读取真实用户认证、会话或主机介质，未获得部署 G2 主机认证；syntheticTestOnly proof 只绑定假程序与专属临时路径。 |
| `UPGRADE-order` | 升级先 drain，并按只读至可写角色分批接入 | 11 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未执行部署升级与分批角色接入；没有运维资源授权，不能以静态操作文档或 public API 用例替代升级验收。 |
| `ROLLBACK-policy` | 旧包/配置回退仍满足 G2，未完成 attempt 与远端副作用分别恢复对账 | 8,11 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未执行旧包/配置回退、真实 G2 重新准入和未完成 attempt/远端副作用恢复对账；没有部署或真实平台认证授权。 |
| `ROLLBACK-old-directory` | 回滚和历史清理不自动删除其他用户共享目录 | 8,11 | uncovered／未覆盖 | not run | 未准备／未运行 | 无执行证据 | 未执行实际回滚/历史清理命令；专属测试临时根的安全删除只证明本轮清理范围，不覆盖共享用户目录回滚行为。 |
| `EVIDENCE-identity-attempt` | 公开 attemptNo 与版本身份字段可持久化并关联 | 8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EVIDENCE-identity-attempt` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EVIDENCE-identity-forbidden` | 身份元数据的正文或未知字段在有效记录前拒绝 | 8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EVIDENCE-identity-forbidden` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `EVIDENCE-value-invalid` | 非法数值、长字符串和嵌套对象拒绝并使记录器不健康 | 8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#EVIDENCE-value-invalid` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RUNNER-recorder-required` | 缺少健康 recorder 时 step 不启动回调 | 3,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#RUNNER-recorder-required` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RUNNER-unhealthy-recorder` | 关键证据故障在阶段回调前拒绝 | 3,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#RUNNER-unhealthy-recorder` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `RUNNER-dispose-not-wait` | dispose 不代替活动操作终态等待 | 3 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#RUNNER-dispose-not-wait` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `OUTPUT-invalid-budget` | 小于公开下限或非整数输出预算明确拒绝 | 5 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#OUTPUT-invalid-budget` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `TOOL-validator-false` | 布尔参数校验 false 在操作前拒绝 | 2,8 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#TOOL-validator-false` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `TOOL-failure-then-text` | 未解决失败不能被无关工具成功或完成文本消除 | 8 | blocked／阻断 | failed case | `tests/fd005/r8/core.test.mjs#TOOL-failure-then-text` | E0 + E1（undefined） + E2 | 第一个工具夹具抛出未标记可修正类别的普通 JavaScript Error('SYNTHETIC_FAILURE')；第二个无关工具在回调前收到 ATTEMPT_CANCELLED，assertSuccessful 未到达。公开契约允许未知或致命工具错误终止，普通 Error 不能独立证明已声明的可修正业务错误。本轮 raw case 仍失败，不能据此认定失败被完成文本掩盖，也不把允许取消改写为该目标行为通过。 |
| `HISTORY-impossible-budget` | 不可压缩规则和近期材料超预算时明确失败 | 7 | passed／通过 | pass-case | `tests/fd005/r8/core.test.mjs#HISTORY-impossible-budget` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SDK-text-stream` | 实际 API Runtime 和已安装 Agents/OpenAI SDK 消费合法 Responses SSE 文本终态 | 5,10 | passed／通过 | pass-case | `tests/fd005/r8/sdk.test.mjs#SDK-text-stream` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SDK-incomplete-no-tool` | 实际 SDK 的 incomplete 响应不得调度残余可解析工具参数 | 2,5 | passed／通过 | pass-case | `tests/fd005/r8/sdk.test.mjs#SDK-incomplete-no-tool` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SDK-missing-terminal` | 实际 SDK 流没有终态时明确失败 | 5 | passed／通过 | pass-case | `tests/fd005/r8/sdk.test.mjs#SDK-missing-terminal` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SDK-unknown-terminal` | 实际 SDK 未识别终态不能表示完成 | 5,10 | passed／通过 | pass-case | `tests/fd005/r8/sdk.test.mjs#SDK-unknown-terminal` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SDK-model-error` | 实际 SDK 合成 HTTP 模型错误映射现有 failureCategory且无自动重试 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/sdk.test.mjs#SDK-model-error` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SDK-tool-failure` | 实际 SDK 工具失败后最终文本不使 attempt 成功 | 8 | passed／通过 | pass-case | `tests/fd005/r8/sdk.test.mjs#SDK-tool-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SDK-readonly-write` | 实际 SDK 的只读 Profile 拒绝写工具而原文件不变 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/sdk.test.mjs#SDK-readonly-write` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SDK-cancel-fetch` | 实际 API fetch 等待时整体取消触发 signal且无 complete | 3 | passed／通过 | pass-case | `tests/fd005/r8/sdk.test.mjs#SDK-cancel-fetch` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SDK-tool-correlation` | 实际 SDK 双工具调用的结果按唯一 call ID 匹配来源内容 | 2,8 | passed／通过 | pass-case | `tests/fd005/r8/sdk.test.mjs#SDK-tool-correlation` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SDK-usage-exact` | 实际 SDK 已知 usage 与未知 usage 元数据分开记录 | 7,10 | passed／通过 | pass-case | `tests/fd005/r8/sdk.test.mjs#SDK-usage-exact` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SDK-request-privacy` | 实际 SDK 正常请求 store=false、truncation=disabled且无 trace 请求 | 8 | passed／通过 | pass-case | `tests/fd005/r8/sdk.test.mjs#SDK-request-privacy` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SDK-summary-privacy` | 实际 SDK 历史概要使用批准模型并继承禁存与期限 | 7,8 | blocked／阻断 | failed case／partial | `tests/fd005/r8/sdk.test.mjs#SDK-summary-privacy` | E0 + E1（undefined） + E2 | assertCompleted 期望 complete 调用数 1，实际 0。原始元数据顺序 scope 70（0-based，JSONL 第 71 行）只有三个普通流式请求，未观察到摘要请求；summaryCalls、批准摘要模型及继承期限的目标断言未到达。没有保存 client.fail 与临时 attempt 终态日志，不能依据 complete=0 推出具体内部原因或摘要隐私泄漏。 |
| `SDK-history-off` | 实际 API 默认关闭历史压缩且不产生概要请求 | 7 | passed／通过 | pass-case | `tests/fd005/r8/sdk.test.mjs#SDK-history-off` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `SDK-tool-parameters` | 实际 SDK 工具 schema 非法参数不执行副作用 | 2,8 | blocked／阻断 | failed case | `tests/fd005/r8/sdk.test.mjs#SDK-tool-parameters` | E0 + E1（undefined） + E2 | 合成实际 SDK function_call 的 write 参数含公开 strict schema 未允许的 undeclared 字段。assertNoComplete 实际 complete 调用数 1，期望 0，目标拒绝成功上报断言失败。随后的 readFile 原内容断言未执行；临时根已清理，不能声称已观察到文件内容被修改。失败证据只支持非法参数未阻止本次成功上报，不独立证明文件副作用或实现根因。 |
| `API-profile-legacy` | 实际 Profile loader 接受公开八字段旧 TOML且保留只读规则 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-profile-legacy` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-skill-complete` | 实际 API adapter 提示词包含公开 Skill主文、references、agents与来源摘要 | 7,10 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-skill-complete` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-model-selection` | 实际 API adapter 保留公共 aiGuidance 模型/effort批准边界 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/guidance.test.mjs#API-model-selection` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-start-failure` | 控制平面 start 失败前不执行模型或业务副作用 | 3,8 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-start-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-heartbeat-failure` | 合成租约 heartbeat失效终止执行且无有效complete | 3,4 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-heartbeat-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-evidence-failure` | attempt关键证据根不可写时不执行成功上报 | 8 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-evidence-failure` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `API-operation-wait` | attempt等待实际adapter活动I/O终态后才清理工作区 | 2,3 | passed／通过 | pass-case | `tests/fd005/r8/api.test.mjs#API-operation-wait` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `WORKSPACE-fallback` | 公开非Git WorkspaceSession返回受控fallback且结果为null | 3,10 | passed／通过 | pass-case | `tests/fd005/r8/workspace.test.mjs#WORKSPACE-fallback` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `WORKSPACE-readonly-baseline` | 公开WorkspaceSession只读快照识别外部修改 | 1,8 | passed／通过 | pass-case | `tests/fd005/r8/workspace.test.mjs#WORKSPACE-readonly-baseline` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `WORKSPACE-git-materialize` | 公开Git工作区按准确基线物化且身份分离 | 3,10 | passed／通过 | pass-case | `tests/fd005/r8/workspace.test.mjs#WORKSPACE-git-materialize` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `WORKSPACE-git-finalize` | 本地Git收尾产生准确resultRevision与changedFiles且证据不入提交 | 4,8,10 | blocked／阻断 | failed case／partial | `tests/fd005/r8/workspace.test.mjs#WORKSPACE-git-finalize` | E0 + E1（undefined） + E2 | materialize/beforeExecution 已进入，受控本地 Git finalize 返回 PROCESS_EXIT_FAILED；resultRevision、changedFiles、publicationRef 和远端核对的目标断言均未到达。原始日志保留异常栈，但没有持久化实际 Git argv/stderr、提交与 bare remote 状态，不能区分受控 guard/配置问题与产品根因，也不能声称发布无写入、已回滚或发布成功。 |
| `WORKSPACE-base-reject` | 不存在的基线在模型启动前失败 | 3,10 | passed／通过 | pass-case | `tests/fd005/r8/workspace.test.mjs#WORKSPACE-base-reject` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `CP-register` | 实际公共客户端register发送既有Worker身份、能力和并发字段 | 10 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#CP-register` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `CP-claim-empty` | 实际公共客户端claim的204表示没有任务 | 10 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#CP-claim-empty` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `CP-claim-task` | 实际公共客户端claim保留完整既有任务响应 | 10 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#CP-claim-task` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `CP-start-selection` | 实际公共客户端start保留租约与批准profile版本且编码ID路径 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#CP-start-selection` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `CP-heartbeat` | 实际公共客户端heartbeat关联租约与externalSessionId | 3,10 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#CP-heartbeat` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `CP-fail` | 实际公共客户端fail发送既有failureCategory而无新增枚举 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#CP-fail` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `CP-complete` | 实际公共客户端complete保留workspaceResult与report既有结构 | 8,10 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#CP-complete` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `CP-error-no-retry` | 实际公共客户端拒绝非成功状态且不自动重试complete | 3,8,10 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#CP-error-no-retry` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `CP-http-timeout` | 实际公共客户端HTTP期限取消内存响应等待 | 3 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#CP-http-timeout` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `CP-attempt-cancel` | 实际公共客户端start同时受attempt取消控制 | 3 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#CP-attempt-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `CP-worker-controls` | 实际公共客户端worker心跳、drain和offline保留无正文路径 | 10,11 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#CP-worker-controls` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PLUGIN-skills-list` | 实际查询适配调用固定list argv并返回带pluginDigest的数据 | 9 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#PLUGIN-skills-list` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PLUGIN-skills-discover` | 实际查询适配调用固定discover并校验name/status结构 | 9 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#PLUGIN-skills-discover` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PLUGIN-partial-issues` | 插件issues非空明确标记结果不完整 | 5,9 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#PLUGIN-partial-issues` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PLUGIN-cancel` | 取消后固定插件查询不得启动子进程 | 3,9 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#PLUGIN-cancel` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PLUGIN-parameters` | 固定GitHub查询number非法时在插件启动前拒绝 | 9 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#PLUGIN-parameters` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `PLUGIN-environment` | 实际固定插件仅取得必要环境而不取得CP、模型或leaseToken | 8,9 | passed／通过 | pass-case | `tests/fd005/r8/transport-plugin.test.mjs#PLUGIN-environment` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `MODELSELECT-escalate-model` | 实际API指导在MODEL_ERROR后前进一个批准profile | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/guidance.test.mjs#MODELSELECT-escalate-model` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `MODELSELECT-escalate-output` | 实际API指导在OUTPUT_INVALID后前进一个批准profile | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/guidance.test.mjs#MODELSELECT-escalate-output` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `MODELSELECT-hold-tool` | 实际API指导在TOOL_ERROR后保持先前批准profile | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/guidance.test.mjs#MODELSELECT-hold-tool` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `MODELSELECT-cap-end` | 实际API指导已到末项时不越界或循环 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/guidance.test.mjs#MODELSELECT-cap-end` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `MODELSELECT-filter` | 实际API指导过滤不兼容项后保持批准顺序 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/guidance.test.mjs#MODELSELECT-filter` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `MODELSELECT-deny-fast` | 实际API拒绝唯一fast=true候选且无模型请求 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/guidance.test.mjs#MODELSELECT-deny-fast` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `MODELSELECT-deny-provider` | 实际API拒绝唯一不兼容provider且无模型请求 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/guidance.test.mjs#MODELSELECT-deny-provider` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `MODELSELECT-deny-runtime` | 实际API拒绝唯一不兼容runtime且无模型请求 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/guidance.test.mjs#MODELSELECT-deny-runtime` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `MODELSELECT-deny-effort` | 实际API拒绝唯一不支持effort且无模型请求 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/guidance.test.mjs#MODELSELECT-deny-effort` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |
| `MODELSELECT-deny-empty` | 实际API拒绝空批准路径且无模型请求 | 1,10 | passed／通过 | pass-case | `tests/fd005/r8/guidance.test.mjs#MODELSELECT-deny-empty` | E0 + E1（undefined） + E2 | 按本轮公开接口与合成输入的断言范围完成；观察边界见所属 suite。 |

### 静态要求排除

- 模块命名、职责组织、复制源码来源和 MIT 许可属于静态审查，不计入运行行为分母。
- 旧 codex-* 参考示例定位与 demo-deployer 独立维护范围属于静态维护说明。
- 发布携带规则由 Reviewer 静态检查；本轮没有最终发布包构建，不能声称发布物行为通过。

测试預期来自 FD、公开接口声明、Profile/协议样例、工具与 guidance 契约。Worker 报告只供范围/编译交接背景，不能代替独立结果。授权摘要共 25 项（18 个本轮调用文件、原分母 cases、公共样例与 5 个已安装依赖 package.json）在同名 JSON 和原始 execution 中逐项保存。

## 命令与风险

### 获批命令实际运行一次

```text
node tests/fd005/r8/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r8.json
```

cwd：`D:/03_projects/AI-tools/aiw-dev`。对应 [Planner 授权 Markdown](FD-005-test-authorization-r8.md)，入口摘要与 25 项依赖摘要先经 gate 核对。最初尚未使用的授权在最后 ldflags 参数修正后已由 Planner 更新；测试只在最终有效授权下执行一次，没有改码后沿用或再跑。

授权上限为 375 秒。三个 Go 编译分别 exit 0（4522、825、615 ms），Node 测试 exit 1（24364 ms），都未超时；准确子命令 argv/cwd 保存在 [execution.json](../../../tests/fd005/evidence-r8/execution.json) 和同名报告 JSON。三个成功编译的原始日志分别为 [fake-native](../../../tests/fd005/evidence-r8/compile-fake-native.log)、[launcher](../../../tests/fd005/evidence-r8/compile-launcher.log)、[git-guard](../../../tests/fd005/evidence-r8/compile-git-guard.log)，成功时为空日志。执行结果中的 `Focused child command failed: node-test-events.jsonl` 是 Node 非零退出的通用标签，不是 Go 编译或准备失败。

### 写入、关闭与清理

临时编译的三个程序、Go 缓存、合成 HOME/USERPROFILE/CODEX_HOME、fixture、隔离 Git 仓库和本地 bare remote 只位于该次拥有的 `tests/fd005/r8/.tmp-fd005-tester-20261005-7865d383-UUID` 根。Go 环境为 local toolchain、GOPROXY/GOSUMDB off、GOENV off、CGO off；Git 使用合成身份、隔离配置，仅允许 file 协议，无 hooks/签名/远端网络。API 源文件内存转译不写最终发布物。

原始 `resource_uncertain=false`；拥有的子进程已确认结束，临时根通过解析绝对路径、owner 和专属前缀校验后清理。taskkill helper 的 5 秒上限本轮未触发。持久写入仅测试证据目录和本双报告；清理没有涉及其外目录。该安全清理状态只能描述本轮拥有的测试树，不证明真实平台进程逃逸、正文禁存或远端发布可回滚。

### 未运行与剩余风险

- 本轮没有重跑、补测、安装、网络、真实模型、真实 Codex/Go/Python SDK 服务会话、真实 CP、用户认证/会话读取、最终发布物构建、lint/typecheck/formatter 或额外验证脚本。
- 24 个未执行行为全部在场景表中给出独立原因，包含真实跨语言准入与生命周期、固定 Python SDK 回执、九角色/slot/attempt 的实际禁存全过程监测、主机认证、drain、租约发布竞态、升级与回滚。
- `SDK-tool-parameters` 的成功上报拒绝断言失败需处理；文件写入后置断言未执行，不推断文件实际变化。工具普通 Error、摘要 complete=0 和 Git PROCESS_EXIT_FAILED 的阶段与不足分别记录，不从缺失证据推断产品根因。
- 本轮元数据不能独立核对全部真实 usage 来源及完整终态“完成/上报/资源”一致性；后续独立阶段应依据公开契约直接核对对应字段，并持久化合成 attempt 与受控 Git 诊断。此为剩余证据边界，不改变本轮原始结果。
- 全 FD 分支及真实平台 G2 未获完整证据，合成准入 policy/proof 不充当 verified 主机证书。升级/回滚、发布竞态与线上 manifest 消费也未验收。

报告准备仅做已保存模板/结果的静态读取；PowerShell 一次读取批次因主机页面文件限制未启动，cmd Node 一次读取因引号参数解析失败，修正只读参数后读取成功。没有据此启动 SDK 或额外测试。报告与原始证据齐备后提交 `test-report-ready`，后续处理由 PM 决策；任何新用例或补测需要新授权。

