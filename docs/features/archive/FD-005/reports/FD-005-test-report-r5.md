# FD-005 独立测试报告，第 5 轮

<!-- aiw-data: FD-005-test-report-r5.json -->

来源事件：`FD-005-000006-implementation-ready`；FD revision：`6`；digest：`f29cb07ce849cbbc92c64095bcac4e37ce873ef160d3d3ff6fcf819b38ee5015`。
Tester session：`fd005-tester-20261005-7865d383`，独立于 Worker/Reviewer。测试时段：2026-10-04T22:39:38.925Z 至 2026-10-04T22:39:54.149Z。

## 结论

本轮只执行 Planner 批准的离线命令一次，exit 1。222 个适用场景中，138 个 Node 用例实际执行，78 个通过、60 个失败；84 个未运行。需求场景覆盖率为 **78/222=35.14%**。建议 PM 拒绝本轮测试结果，交回 Worker 修复观察到的边界并补齐公开输入/前置契约。

60 个失败包含51个证据夹具前置失败、7个预算/生命周期部分执行和2个假 native/RPC目标断言失败。这些失败均保留在原始计数；前置失败不代表已执行目标行为，也不能当成51个独立产品缺陷。未读实现源码、未依据 Worker 自报结果设预期，未修改已授权用例或重跑。

公开核心的 Node V8 分支计数为 **278/393=70.74%**；行计数 768/1310。这仅覆盖 runtime-core 的引擎计数。**完整 FD 的业务分支覆盖率未测量**：Go launcher和实际四个 Worker/SDK未做同等分支插桩或端到端运行。Node包含测试helper的总分支比例73.22%不能作为业务覆盖率。

## 失败、夹具边界与原始证据

1. **假 native 环境剥离目标失败（LAUNCHER-environment）。** 绑定假程序的政策通过准入，native 实际报告 `leakedEnvironment=true`，表明扫描到 AIW_/OTEL_/DEBUG 类环境键。它同时报告 `modelAuthAvailable=true` 与 `rustLog=off`。夹具没有采集键名或值，不能据此声称真实凭据泄漏；现有禁存文档要求的剥离仍未通过。
2. **公开 read 回执拒绝目标失败（RPC-read-receipt）。** 先取得 `ephemeral=true/path=null` 的假线程；后续 `thread/read` 返回 `ephemeral=false` 与 `path=SYNTHETIC_FORBIDDEN_SESSION_PATH`，该回执被转发且launcher exit 0。拒绝非法 read 回执的目标断言失败。
3. **51个证据前置失败。** 原始失败栈指向 `EvidenceRecorder.open` 返回 `EVIDENCE_FIELD_FORBIDDEN`。九角色工具、ToolHost、部分证据与Model用例均未进入目标断言。公开声明使用通用 `Metadata`，没有给出完整的允许identity字段；需明确公开输入契约后判断夹具或接口修正。
4. **预算与Runner部分执行。** `OUTPUT-chinese` 的UTF-8字节断言已执行，但 `maxBytes=96` 被 `OUTPUT_BUDGET_INVALID` 拒绝，不能解释为Unicode截断算法错误。六个Runner用例的阶段回调/Recorder前置没有可确认的成功证据；取消和超时终态断言失败，清理用例在回调未进入时达到本地观察期限，不能据此声称真实模型/发布阶段取消已完成验证。

原始证据：[执行摘要](../../../tests/fd005/evidence-r5/execution.json)、[Node原始事件与失败栈](../../../tests/fd005/evidence-r5/node-test-events.jsonl)、[核心逐例结果](../../../tests/fd005/evidence-r5/results/core.test.mjs.jsonl)、[会话逐例结果](../../../tests/fd005/evidence-r5/results/session.test.mjs.jsonl)、[假launcher原始转录](../../../tests/fd005/evidence-r5/results/launcher-transcripts.jsonl)、[V8原始目录](../../../tests/fd005/evidence-r5/v8-raw/)。

## 场景与证据

分母覆盖FD的适用行为，包含API每个角色的成功、失败、能力拒绝、报告和取消、跨语言、真实禁存、插件及升级/回滚缺口。`prepared` 表示用例已编写但目标被前置阻塞；`partial` 表示只进入部分检查；`executed` 可为通过或失败；`uncovered` 表示尚未执行。JSON的60个失败仍计入 `failed_behavior_tests`。

通过行只证明其所列公开核心或合成适配行为。真实角色入口、SDK模型调用、平台过程写入监测、派生会话及供应商/主机政策没有被假政策认证。每个实际用例的原始记录按ID可在上述结果文件检索。

| ID | 独立可观察行为 | 状态 | 用例 | 实际结果或未覆盖原因 |
|---|---|---|---|---|
| ROLE-requirements-tool-success | requirements 的已授权读取有合法完整结果 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-requirements-tool-success | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-requirements-tool-failure | requirements 的工具失败不能被后续成功文本掩盖 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-requirements-tool-failure | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-requirements-access | requirements 的写入被拒绝且文件不变 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-requirements-access | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-requirements-cancel | requirements 的取消后新工具不执行 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-requirements-cancel | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| API-requirements-success | requirements 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-requirements-failure | requirements 的实际 API Worker 模型/工具失败形成既有 failureCategory | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-requirements-capability-reject | requirements 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-requirements-report | requirements 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-requirements-cancel | requirements 的实际 API Worker 取消后无过期 complete | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| ROLE-architect-tool-success | architect 的已授权读取有合法完整结果 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-architect-tool-success | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-architect-tool-failure | architect 的工具失败不能被后续成功文本掩盖 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-architect-tool-failure | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-architect-access | architect 的写入被拒绝且文件不变 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-architect-access | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-architect-cancel | architect 的取消后新工具不执行 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-architect-cancel | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| API-architect-success | architect 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-architect-failure | architect 的实际 API Worker 模型/工具失败形成既有 failureCategory | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-architect-capability-reject | architect 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-architect-report | architect 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-architect-cancel | architect 的实际 API Worker 取消后无过期 complete | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| ROLE-planner-tool-success | planner 的已授权读取有合法完整结果 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-planner-tool-success | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-planner-tool-failure | planner 的工具失败不能被后续成功文本掩盖 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-planner-tool-failure | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-planner-access | planner 的写入被拒绝且文件不变 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-planner-access | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-planner-cancel | planner 的取消后新工具不执行 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-planner-cancel | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| API-planner-success | planner 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-planner-failure | planner 的实际 API Worker 模型/工具失败形成既有 failureCategory | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-planner-capability-reject | planner 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-planner-report | planner 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-planner-cancel | planner 的实际 API Worker 取消后无过期 complete | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| ROLE-coder-tool-success | coder 的已授权读取有合法完整结果 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-coder-tool-success | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-coder-tool-failure | coder 的工具失败不能被后续成功文本掩盖 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-coder-tool-failure | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-coder-access | coder 的编辑只修改指定工作区内文件 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-coder-access | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-coder-cancel | coder 的取消后新工具不执行 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-coder-cancel | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| API-coder-success | coder 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-coder-failure | coder 的实际 API Worker 模型/工具失败形成既有 failureCategory | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-coder-capability-reject | coder 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-coder-report | coder 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-coder-cancel | coder 的实际 API Worker 取消后无过期 complete | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| ROLE-integrator-tool-success | integrator 的已授权读取有合法完整结果 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-integrator-tool-success | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-integrator-tool-failure | integrator 的工具失败不能被后续成功文本掩盖 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-integrator-tool-failure | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-integrator-access | integrator 的编辑只修改指定工作区内文件 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-integrator-access | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-integrator-cancel | integrator 的取消后新工具不执行 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-integrator-cancel | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| API-integrator-success | integrator 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-integrator-failure | integrator 的实际 API Worker 模型/工具失败形成既有 failureCategory | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-integrator-capability-reject | integrator 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-integrator-report | integrator 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-integrator-cancel | integrator 的实际 API Worker 取消后无过期 complete | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| ROLE-reviewer-tool-success | reviewer 的已授权读取有合法完整结果 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-reviewer-tool-success | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-reviewer-tool-failure | reviewer 的工具失败不能被后续成功文本掩盖 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-reviewer-tool-failure | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-reviewer-access | reviewer 的写入被拒绝且文件不变 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-reviewer-access | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-reviewer-cancel | reviewer 的取消后新工具不执行 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-reviewer-cancel | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| API-reviewer-success | reviewer 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-reviewer-failure | reviewer 的实际 API Worker 模型/工具失败形成既有 failureCategory | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-reviewer-capability-reject | reviewer 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-reviewer-report | reviewer 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-reviewer-cancel | reviewer 的实际 API Worker 取消后无过期 complete | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| ROLE-verifier-tool-success | verifier 的已授权读取有合法完整结果 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-verifier-tool-success | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-verifier-tool-failure | verifier 的工具失败不能被后续成功文本掩盖 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-verifier-tool-failure | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-verifier-access | verifier 的写入被拒绝且文件不变 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-verifier-access | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-verifier-cancel | verifier 的取消后新工具不执行 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-verifier-cancel | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| API-verifier-success | verifier 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-verifier-failure | verifier 的实际 API Worker 模型/工具失败形成既有 failureCategory | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-verifier-capability-reject | verifier 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-verifier-report | verifier 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-verifier-cancel | verifier 的实际 API Worker 取消后无过期 complete | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| ROLE-integration-verifier-tool-success | integration-verifier 的已授权读取有合法完整结果 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-integration-verifier-tool-success | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-integration-verifier-tool-failure | integration-verifier 的工具失败不能被后续成功文本掩盖 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-integration-verifier-tool-failure | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-integration-verifier-access | integration-verifier 的写入被拒绝且文件不变 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-integration-verifier-access | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-integration-verifier-cancel | integration-verifier 的取消后新工具不执行 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-integration-verifier-cancel | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| API-integration-verifier-success | integration-verifier 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-integration-verifier-failure | integration-verifier 的实际 API Worker 模型/工具失败形成既有 failureCategory | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-integration-verifier-capability-reject | integration-verifier 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-integration-verifier-report | integration-verifier 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-integration-verifier-cancel | integration-verifier 的实际 API Worker 取消后无过期 complete | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| ROLE-repairer-tool-success | repairer 的已授权读取有合法完整结果 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-repairer-tool-success | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-repairer-tool-failure | repairer 的工具失败不能被后续成功文本掩盖 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-repairer-tool-failure | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-repairer-access | repairer 的编辑只修改指定工作区内文件 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-repairer-access | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| ROLE-repairer-cancel | repairer 的取消后新工具不执行 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#ROLE-repairer-cancel | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| API-repairer-success | repairer 的实际 API Worker 成功 attempt 使用正确角色与既有终态 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-repairer-failure | repairer 的实际 API Worker 模型/工具失败形成既有 failureCategory | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-repairer-capability-reject | repairer 的实际 API Worker 在不兼容任务类型/能力时于模型前拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-repairer-report | repairer 的实际 API Worker 报告保留当前 role/report/workspaceResult 契约 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-repairer-cancel | repairer 的实际 API Worker 取消后无过期 complete | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| PATH-outside | 工作区外路径被拒绝 | passed：已执行 | tests/fd005/core.test.mjs#PATH-outside | 通过；仅覆盖所列公开/合成行为 |
| PATH-git | Git 内部路径被拒绝 | passed：已执行 | tests/fd005/core.test.mjs#PATH-git | 通过；仅覆盖所列公开/合成行为 |
| PATH-link | 链接路径不能越过工作区策略 | passed：已执行 | tests/fd005/core.test.mjs#PATH-link | 通过；仅覆盖所列公开/合成行为 |
| PATH-hardlink | 危险硬链接写入被拒绝 | passed：已执行 | tests/fd005/core.test.mjs#PATH-hardlink | 通过；仅覆盖所列公开/合成行为 |
| RESOURCE-independent | 独立文件操作可以并行 | passed：已执行 | tests/fd005/core.test.mjs#RESOURCE-independent | 通过；仅覆盖所列公开/合成行为 |
| RESOURCE-readers | 多个读取可以并行 | passed：已执行 | tests/fd005/core.test.mjs#RESOURCE-readers | 通过；仅覆盖所列公开/合成行为 |
| RESOURCE-same-file | 同文件修改按读改写全过程串行 | passed：已执行 | tests/fd005/core.test.mjs#RESOURCE-same-file | 通过；仅覆盖所列公开/合成行为 |
| RESOURCE-read-write | 同文件读取与修改保持一致性 | passed：已执行 | tests/fd005/core.test.mjs#RESOURCE-read-write | 通过；仅覆盖所列公开/合成行为 |
| RESOURCE-shell | 未声明范围的 shell 与工作区修改互斥 | passed：已执行 | tests/fd005/core.test.mjs#RESOURCE-shell | 通过；仅覆盖所列公开/合成行为 |
| RESOURCE-limit | 活动操作数量不超过并发上限 | passed：已执行 | tests/fd005/core.test.mjs#RESOURCE-limit | 通过；仅覆盖所列公开/合成行为 |
| RESOURCE-cancel-wait | 排队等待取消后不启动操作 | passed：已执行 | tests/fd005/core.test.mjs#RESOURCE-cancel-wait | 通过；仅覆盖所列公开/合成行为 |
| RESOURCE-active-cancel | 活动 I/O 未结束时取消不能提前释放资源 | passed：已执行 | tests/fd005/core.test.mjs#RESOURCE-active-cancel | 通过；仅覆盖所列公开/合成行为 |
| RESOURCE-failure-release | 失败后下一操作取得资源 | passed：已执行 | tests/fd005/core.test.mjs#RESOURCE-failure-release | 通过；仅覆盖所列公开/合成行为 |
| TOOL-parameters | 非法参数在副作用前失败 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#TOOL-parameters | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| TOOL-shell-denied | 缺少 shell 授权时不执行命令 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#TOOL-shell-denied | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| TOOL-nested | 嵌套工具调用经过相同边界并保留关联 ID | blocked：前置夹具失败 | tests/fd005/core.test.mjs#TOOL-nested | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| TOOL-partial | 未完成搜索的模型可见结果明确为不完整 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#TOOL-partial | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| TOOL-output-bound | 工具结果 envelope 与截断提示一同受字节上限约束 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#TOOL-output-bound | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| TOOL-timeout-active | 操作超时后仍等待活动 I/O 终态 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#TOOL-timeout-active | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| OUTPUT-short | 未超限文本完整返回 | passed：已执行 | tests/fd005/core.test.mjs#OUTPUT-short | 通过；仅覆盖所列公开/合成行为 |
| OUTPUT-chinese | 中文和 emoji 预算按 UTF-8 字节计算 | blocked：部分执行 | tests/fd005/core.test.mjs#OUTPUT-chinese | utf8Bytes('中文🧪')=10 的断言已执行；maxBytes=96 的 boundedText 返回 OUTPUT_BUDGET_INVALID，未验证截断分支。该函数预算有效域缺少明确公开声明，可能需要调整夹具，不能判为 Unicode 算法错误。 |
| OUTPUT-long-line | 长单行截断不产生无效 Unicode | passed：已执行 | tests/fd005/core.test.mjs#OUTPUT-long-line | 通过；仅覆盖所列公开/合成行为 |
| OUTPUT-lines | 行数上限包含截断提示 | passed：已执行 | tests/fd005/core.test.mjs#OUTPUT-lines | 通过；仅覆盖所列公开/合成行为 |
| JSON-complete | 合法完整 JSON 原样解析 | passed：已执行 | tests/fd005/core.test.mjs#JSON-complete | 通过；仅覆盖所列公开/合成行为 |
| JSON-too-large | 超限 JSON 明确拒绝，不返回半个结构 | passed：已执行 | tests/fd005/core.test.mjs#JSON-too-large | 通过；仅覆盖所列公开/合成行为 |
| JSON-invalid | 非法或不符合 schema 的 JSON 明确拒绝 | passed：已执行 | tests/fd005/core.test.mjs#JSON-invalid | 通过；仅覆盖所列公开/合成行为 |
| EDIT-multiple | 多区域计划一次生成全部修改 | passed：已执行 | tests/fd005/core.test.mjs#EDIT-multiple | 通过；仅覆盖所列公开/合成行为 |
| EDIT-missing | 缺少匹配时拒绝且不写任何部分修改 | passed：已执行 | tests/fd005/core.test.mjs#EDIT-missing | 通过；仅覆盖所列公开/合成行为 |
| EDIT-duplicate-match | 重复匹配时拒绝且原文件不变 | passed：已执行 | tests/fd005/core.test.mjs#EDIT-duplicate-match | 通过；仅覆盖所列公开/合成行为 |
| EDIT-overlap | 重叠编辑在写入前拒绝 | passed：已执行 | tests/fd005/core.test.mjs#EDIT-overlap | 通过；仅覆盖所列公开/合成行为 |
| EDIT-duplicate-plan | 重复编辑区域在写入前拒绝 | passed：已执行 | tests/fd005/core.test.mjs#EDIT-duplicate-plan | 通过；仅覆盖所列公开/合成行为 |
| EDIT-format | 修改保留 BOM 与 CRLF | passed：已执行 | tests/fd005/core.test.mjs#EDIT-format | 通过；仅覆盖所列公开/合成行为 |
| EDIT-baseline | 基线摘要变化时拒绝原子写入 | passed：已执行 | tests/fd005/core.test.mjs#EDIT-baseline | 通过；仅覆盖所列公开/合成行为 |
| EDIT-diff-bound | 编辑结果 diff 受字节预算约束 | passed：已执行 | tests/fd005/core.test.mjs#EDIT-diff-bound | 通过；仅覆盖所列公开/合成行为 |
| EDIT-cancel | 写入前取消不会修改文件 | passed：已执行 | tests/fd005/core.test.mjs#EDIT-cancel | 通过；仅覆盖所列公开/合成行为 |
| CONTEXT-sources | 输入来源具有路径、版本摘要与字节记录 | passed：已执行 | tests/fd005/core.test.mjs#CONTEXT-sources | 通过；仅覆盖所列公开/合成行为 |
| CONTEXT-required | 必需规则、验收和冻结材料均保留 | passed：已执行 | tests/fd005/core.test.mjs#CONTEXT-required | 通过；仅覆盖所列公开/合成行为 |
| CONTEXT-overflow | 首轮超限明确失败而非删除必需输入 | passed：已执行 | tests/fd005/core.test.mjs#CONTEXT-overflow | 通过；仅覆盖所列公开/合成行为 |
| CONTEXT-skills | 原 Skill 主文、references 与 agents 展开兼容 | passed：已执行 | tests/fd005/core.test.mjs#CONTEXT-skills | 通过；仅覆盖所列公开/合成行为 |
| HISTORY-off | 关闭历史压缩时不调用摘要模型且原输入保留 | passed：已执行 | tests/fd005/core.test.mjs#HISTORY-off | 通过；仅覆盖所列公开/合成行为 |
| HISTORY-budget | 启用摘要必须具有显式预算 | passed：已执行 | tests/fd005/core.test.mjs#HISTORY-budget | 通过；仅覆盖所列公开/合成行为 |
| HISTORY-derived | 摘要是派生输入且原始来源可回查 | passed：已执行 | tests/fd005/core.test.mjs#HISTORY-derived | 通过；仅覆盖所列公开/合成行为 |
| HISTORY-pairs | 压缩不拆散工具调用和结果 | passed：已执行 | tests/fd005/core.test.mjs#HISTORY-pairs | 通过；仅覆盖所列公开/合成行为 |
| HISTORY-cancel | 摘要取消后无新增摘要和无限重试 | passed：已执行 | tests/fd005/core.test.mjs#HISTORY-cancel | 通过；仅覆盖所列公开/合成行为 |
| HISTORY-failure | 摘要失败不进行无界重试 | passed：已执行 | tests/fd005/core.test.mjs#HISTORY-failure | 通过；仅覆盖所列公开/合成行为 |
| HISTORY-max-two | 同一历史压缩器最多生成两次摘要 | passed：已执行 | tests/fd005/core.test.mjs#HISTORY-max-two | 通过；仅覆盖所列公开/合成行为 |
| EVIDENCE-outside | 关键证据目录位于 Git 工作区之外 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#EVIDENCE-outside | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| EVIDENCE-forbidden-root | 证据根在工作区内时拒绝 | passed：已执行 | tests/fd005/core.test.mjs#EVIDENCE-forbidden-root | 通过；仅覆盖所列公开/合成行为 |
| EVIDENCE-correlation | 工具与输入/结果版本可以关联追踪 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#EVIDENCE-correlation | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| EVIDENCE-redaction | 正文、密钥和 leaseToken 不进入元数据 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#EVIDENCE-redaction | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| EVIDENCE-capacity | 关键元数据超限后健康校验失败 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#EVIDENCE-capacity | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| EVIDENCE-write-failure | 关键写入失败后健康校验失败 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#EVIDENCE-write-failure | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| EVIDENCE-terminal-expiry | 拥有且已终态的日志在30天后可清理 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#EVIDENCE-terminal-expiry | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| EVIDENCE-active-retain | 活动日志即使很旧也不自动删除 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#EVIDENCE-active-retain | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| EVIDENCE-foreign-retain | 不带所有权标记的目录不自动删除 | passed：已执行 | tests/fd005/core.test.mjs#EVIDENCE-foreign-retain | 通过；仅覆盖所列公开/合成行为 |
| ATTEMPT-cancel-prepare | 准备阶段取消后不执行下一阶段 | blocked：部分执行 | tests/fd005/core.test.mjs#ATTEMPT-cancel-prepare | 已构造 AttemptRunner 并调用公开 step；取消/超时终态断言失败，或回调未进入导致本地观察期限届满。关键 Recorder 前置/step 语义尚不明确，没有证明对应阶段的实际取消或活动 I/O 行为。 |
| ATTEMPT-cancel-model | 模型阶段取消后不启动工具或发布 | blocked：部分执行 | tests/fd005/core.test.mjs#ATTEMPT-cancel-model | 已构造 AttemptRunner 并调用公开 step；取消/超时终态断言失败，或回调未进入导致本地观察期限届满。关键 Recorder 前置/step 语义尚不明确，没有证明对应阶段的实际取消或活动 I/O 行为。 |
| ATTEMPT-cancel-tool | 工具阶段取消后不启动发布 | blocked：部分执行 | tests/fd005/core.test.mjs#ATTEMPT-cancel-tool | 已构造 AttemptRunner 并调用公开 step；取消/超时终态断言失败，或回调未进入导致本地观察期限届满。关键 Recorder 前置/step 语义尚不明确，没有证明对应阶段的实际取消或活动 I/O 行为。 |
| ATTEMPT-cancel-publish | 发布阶段取消后不回报有效完成 | blocked：部分执行 | tests/fd005/core.test.mjs#ATTEMPT-cancel-publish | 已构造 AttemptRunner 并调用公开 step；取消/超时终态断言失败，或回调未进入导致本地观察期限届满。关键 Recorder 前置/step 语义尚不明确，没有证明对应阶段的实际取消或活动 I/O 行为。 |
| ATTEMPT-timeout | 整体期限覆盖执行阶段并传出取消 | blocked：部分执行 | tests/fd005/core.test.mjs#ATTEMPT-timeout | 已构造 AttemptRunner 并调用公开 step；取消/超时终态断言失败，或回调未进入导致本地观察期限届满。关键 Recorder 前置/step 语义尚不明确，没有证明对应阶段的实际取消或活动 I/O 行为。 |
| ATTEMPT-cleanup-wait | 独立清理预算不能把活动资源写成已结束 | blocked：部分执行 | tests/fd005/core.test.mjs#ATTEMPT-cleanup-wait | 已构造 AttemptRunner 并调用公开 step；取消/超时终态断言失败，或回调未进入导致本地观察期限届满。关键 Recorder 前置/step 语义尚不明确，没有证明对应阶段的实际取消或活动 I/O 行为。 |
| MODEL-completed | 已完成且完整的模型响应可接受 | passed：已执行 | tests/fd005/core.test.mjs#MODEL-completed | 通过；仅覆盖所列公开/合成行为 |
| MODEL-incomplete | 长度截断的模型响应在工具执行前拒绝 | passed：已执行 | tests/fd005/core.test.mjs#MODEL-incomplete | 通过；仅覆盖所列公开/合成行为 |
| MODEL-unknown | 未知响应终态明确失败 | passed：已执行 | tests/fd005/core.test.mjs#MODEL-unknown | 通过；仅覆盖所列公开/合成行为 |
| MODEL-invalid-call | 不完整工具参数即使部分 JSON 可解析也不得执行 | passed：已执行 | tests/fd005/core.test.mjs#MODEL-invalid-call | 通过；仅覆盖所列公开/合成行为 |
| MODEL-truncated-call | 残余参数 JSON 可解析的长度截断响应仍不得执行工具 | passed：已执行 | tests/fd005/core.test.mjs#MODEL-truncated-call | 通过；仅覆盖所列公开/合成行为 |
| MODEL-stream-incomplete | 流结束没有完整终态时拒绝 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#MODEL-stream-incomplete | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| MODEL-usage-unknown | 缺少实际 usage 不能当作精确零值 | blocked：前置夹具失败 | tests/fd005/core.test.mjs#MODEL-usage-unknown | 已运行 Node 用例；EvidenceRecorder.open 的前置夹具返回 EVIDENCE_FIELD_FORBIDDEN，目标工具/元数据/模型断言未到达。公开 Metadata 声明未列明允许 identity 字段，需核对公开契约后修正夹具或接口；不据此判定目标业务已执行或通过。 |
| PROCESS-success | 离线子进程退出、输出和完整性可观察 | passed：已执行 | tests/fd005/core.test.mjs#PROCESS-success | 通过；仅覆盖所列公开/合成行为 |
| PROCESS-overflow | 子进程结构化输出超限明确失败 | passed：已执行 | tests/fd005/core.test.mjs#PROCESS-overflow | 通过；仅覆盖所列公开/合成行为 |
| PROCESS-cancel | 子进程取消返回终态并停止输出 | passed：已执行 | tests/fd005/core.test.mjs#PROCESS-cancel | 通过；仅覆盖所列公开/合成行为 |
| ENV-minimum | 插件/工具子进程不取得 CP、模型密钥与 leaseToken | passed：已执行 | tests/fd005/core.test.mjs#ENV-minimum | 通过；仅覆盖所列公开/合成行为 |
| PLUGIN-disabled | 没有 catalog 时插件默认关闭 | passed：已执行 | tests/fd005/core.test.mjs#PLUGIN-disabled | 通过；仅覆盖所列公开/合成行为 |
| PLUGIN-malformed | 非法 catalog 格式被拒绝 | passed：已执行 | tests/fd005/core.test.mjs#PLUGIN-malformed | 通过；仅覆盖所列公开/合成行为 |
| PLUGIN-missing | 不存在的插件入口明确失败 | passed：已执行 | tests/fd005/core.test.mjs#PLUGIN-missing | 通过；仅覆盖所列公开/合成行为 |
| SESSION-missing | 缺少禁存清单时在会话前拒绝 | passed：已执行 | tests/fd005/session.test.mjs#SESSION-missing | 通过；仅覆盖所列公开/合成行为 |
| SESSION-unverified | 未验证证据不能使会话准入 | passed：已执行 | tests/fd005/session.test.mjs#SESSION-unverified | 通过；仅覆盖所列公开/合成行为 |
| SESSION-sdk-mismatch | SDK 版本绑定不一致时拒绝 | passed：已执行 | tests/fd005/session.test.mjs#SESSION-sdk-mismatch | 通过；仅覆盖所列公开/合成行为 |
| SESSION-native-digest | native 摘要不符时拒绝 | passed：已执行 | tests/fd005/session.test.mjs#SESSION-native-digest | 通过；仅覆盖所列公开/合成行为 |
| SESSION-launcher-digest | launcher 摘要不符时拒绝 | passed：已执行 | tests/fd005/session.test.mjs#SESSION-launcher-digest | 通过；仅覆盖所列公开/合成行为 |
| SESSION-proof-digest | 证据摘要不符时拒绝 | passed：已执行 | tests/fd005/session.test.mjs#SESSION-proof-digest | 通过；仅覆盖所列公开/合成行为 |
| SESSION-control-missing | 十项通道证据缺失时拒绝 | passed：已执行 | tests/fd005/session.test.mjs#SESSION-control-missing | 通过；仅覆盖所列公开/合成行为 |
| SESSION-config-change | 固定配置源发生变化后拒绝 | passed：已执行 | tests/fd005/session.test.mjs#SESSION-config-change | 通过；仅覆盖所列公开/合成行为 |
| SESSION-workspace-file | 任务可写根中的清单不能授予准入 | passed：已执行 | tests/fd005/session.test.mjs#SESSION-workspace-file | 通过；仅覆盖所列公开/合成行为 |
| SESSION-raw-evidence | rawEvidence 摘要不符时拒绝 | passed：已执行 | tests/fd005/session.test.mjs#SESSION-raw-evidence | 通过；仅覆盖所列公开/合成行为 |
| SESSION-fake-admission | 只绑定假 native 的合成政策可以验证准入适配 | passed：已执行 | tests/fd005/session.test.mjs#SESSION-fake-admission | 通过；仅覆盖所列公开/合成行为 |
| LAUNCHER-preflight | preflight 不启动假 native 或发送提示词 | passed：已执行 | tests/fd005/session.test.mjs#LAUNCHER-preflight | 通过；仅覆盖所列公开/合成行为 |
| LAUNCHER-exec | 首次 exec 强制 ephemeral、stdin 与原模型参数 | passed：已执行 | tests/fd005/session.test.mjs#LAUNCHER-exec | 通过；仅覆盖所列公开/合成行为 |
| LAUNCHER-resume | 通过准入后仍拒绝 resume | passed：已执行 | tests/fd005/session.test.mjs#LAUNCHER-resume | 通过；仅覆盖所列公开/合成行为 |
| LAUNCHER-save-output | 通过准入后仍拒绝输出文件参数 | passed：已执行 | tests/fd005/session.test.mjs#LAUNCHER-save-output | 通过；仅覆盖所列公开/合成行为 |
| LAUNCHER-prompt-argv | 通过准入后仍拒绝正文 argv | passed：已执行 | tests/fd005/session.test.mjs#LAUNCHER-prompt-argv | 通过；仅覆盖所列公开/合成行为 |
| LAUNCHER-conflict | 通过准入后仍拒绝隐私配置冲突 | passed：已执行 | tests/fd005/session.test.mjs#LAUNCHER-conflict | 通过；仅覆盖所列公开/合成行为 |
| LAUNCHER-environment | 假 native 不取得适配器/CP/调试环境 | blocked：目标断言失败 | tests/fd005/session.test.mjs#LAUNCHER-environment | 假 native 实际启动并报告 leakedEnvironment=true（扫描 AIW_/OTEL_/DEBUG 类键）；stdout 同时确认 modelAuthAvailable=true、RUST_LOG=off。未采集泄漏键名/值，不能断言真实凭据泄漏；禁存文档要求的剥离目标断言失败。 |
| LAUNCHER-stderr | 假 native 的原始 stderr 正文不向主机泄漏 | passed：已执行 | tests/fd005/session.test.mjs#LAUNCHER-stderr | 通过；仅覆盖所列公开/合成行为 |
| RPC-ephemeral | thread/start 必须显式 ephemeral true | passed：已执行 | tests/fd005/session.test.mjs#RPC-ephemeral | 通过；仅覆盖所列公开/合成行为 |
| RPC-valid-turn | 已核验 ephemeral 线程可接收合成正文 turn | passed：已执行 | tests/fd005/session.test.mjs#RPC-valid-turn | 通过；仅覆盖所列公开/合成行为 |
| RPC-receipt | 非 ephemeral 或有磁盘 path 的回执在正文前被拒绝 | passed：已执行 | tests/fd005/session.test.mjs#RPC-receipt | 通过；仅覆盖所列公开/合成行为 |
| RPC-read-receipt | 公开 thread/read 回执的磁盘 path 冲突在正文前被拒绝 | blocked：目标断言失败 | tests/fd005/session.test.mjs#RPC-read-receipt | 先成功创建 ephemeral/path=null 的合成线程；thread/read 的 ephemeral=false 且 path=SYNTHETIC_FORBIDDEN_SESSION_PATH 回执被转发，launcher exit 0。拒绝非法回执的目标断言失败。 |
| RPC-turn-order | turn 只允许已核验的 ephemeral 线程 | passed：已执行 | tests/fd005/session.test.mjs#RPC-turn-order | 通过；仅覆盖所列公开/合成行为 |
| RPC-resume | 持久化 resume/fork RPC 被拒绝 | passed：已执行 | tests/fd005/session.test.mjs#RPC-resume | 通过；仅覆盖所列公开/合成行为 |
| RPC-disk-fork | 持久化 fork RPC 被拒绝 | passed：已执行 | tests/fd005/session.test.mjs#RPC-disk-fork | 通过；仅覆盖所列公开/合成行为 |
| RPC-approval | SDK 默认 accept 审批回复不能取得宿主授权 | passed：已执行 | tests/fd005/session.test.mjs#RPC-approval | 通过；仅覆盖所列公开/合成行为 |
| ROLE-preserved-reject | release-readiness 与 orchestrator 在注册前拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 没有执行实际 Worker/控制平面/工作区发布入口；相关生命周期、报告、租约、drain 或兼容行为未有目标断言证据。 |
| ROLE-verifier-alias | verifier 的集成任务使用既有 integration-verifier Profile 与能力 | uncovered：未运行 | 尚未准备可运行用例 | 没有执行实际 Worker/控制平面/工作区发布入口；相关生命周期、报告、租约、drain 或兼容行为未有目标断言证据。 |
| REPORT-semantic-empty | 前三个语义角色的空报告拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 没有执行实际 Worker/控制平面/工作区发布入口；相关生命周期、报告、租约、drain 或兼容行为未有目标断言证据。 |
| REPORT-verdict-missing | 审查/验证缺少末行 verdict 时拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 没有执行实际 Worker/控制平面/工作区发布入口；相关生命周期、报告、租约、drain 或兼容行为未有目标断言证据。 |
| REPORT-verdict-final | verdict 后有正文或 verdict 非法时拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 没有执行实际 Worker/控制平面/工作区发布入口；相关生命周期、报告、租约、drain 或兼容行为未有目标断言证据。 |
| REPORT-readonly-dirty | 只读工作区变脏时不回报成功 | uncovered：未运行 | 尚未准备可运行用例 | 没有执行实际 Worker/控制平面/工作区发布入口；相关生命周期、报告、租约、drain 或兼容行为未有目标断言证据。 |
| REPORT-manifest | 规划 manifest 和代码 workspaceResult 的既有契约保持兼容 | uncovered：未运行 | 尚未准备可运行用例 | 没有执行实际 Worker/控制平面/工作区发布入口；相关生命周期、报告、租约、drain 或兼容行为未有目标断言证据。 |
| LEASE-lost | 真实角色适配的租约丢失停止新增副作用并拒绝过期结果 | uncovered：未运行 | 尚未准备可运行用例 | 没有执行实际 Worker/控制平面/工作区发布入口；相关生命周期、报告、租约、drain 或兼容行为未有目标断言证据。 |
| PUBLISH-completed-race | 发布已经完成的租约竞争记录引用、版本和对账状态 | uncovered：未运行 | 尚未准备可运行用例 | 没有执行实际 Worker/控制平面/工作区发布入口；相关生命周期、报告、租约、drain 或兼容行为未有目标断言证据。 |
| PUBLISH-unknown-race | 发布结果未知的租约竞争不声称回滚或无写入 | uncovered：未运行 | 尚未准备可运行用例 | 没有执行实际 Worker/控制平面/工作区发布入口；相关生命周期、报告、租约、drain 或兼容行为未有目标断言证据。 |
| COMPLETE-response-lost | CP complete 响应丢失不重跑整个业务 attempt | uncovered：未运行 | 尚未准备可运行用例 | 没有执行实际 Worker/控制平面/工作区发布入口；相关生命周期、报告、租约、drain 或兼容行为未有目标断言证据。 |
| DRAIN-inflight | drain 停止领取且允许在途 attempt 结束 | uncovered：未运行 | 尚未准备可运行用例 | 没有执行实际 Worker/控制平面/工作区发布入口；相关生命周期、报告、租约、drain 或兼容行为未有目标断言证据。 |
| DRAIN-force | 第二次终止取消在途任务并有资源终态 | uncovered：未运行 | 尚未准备可运行用例 | 没有执行实际 Worker/控制平面/工作区发布入口；相关生命周期、报告、租约、drain 或兼容行为未有目标断言证据。 |
| PLUGIN-version | 插件/解释器版本或摘要不一致时拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 固定 catalog 只提供空模板；本轮未构造实际插件查询或授予网络/外部操作。身份、超时、输出与网络拒绝的目标场景没有执行。 |
| PLUGIN-timeout | 实际固定查询插件超时明确失败 | uncovered：未运行 | 尚未准备可运行用例 | 固定 catalog 只提供空模板；本轮未构造实际插件查询或授予网络/外部操作。身份、超时、输出与网络拒绝的目标场景没有执行。 |
| PLUGIN-output | 插件超限/非法输出明确失败 | uncovered：未运行 | 尚未准备可运行用例 | 固定 catalog 只提供空模板；本轮未构造实际插件查询或授予网络/外部操作。身份、超时、输出与网络拒绝的目标场景没有执行。 |
| PLUGIN-network | 未有网络授权的 GitHub 查询在外部请求前拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 固定 catalog 只提供空模板；本轮未构造实际插件查询或授予网络/外部操作。身份、超时、输出与网络拒绝的目标场景没有执行。 |
| API-store-false | API 实际 SDK 正常与摘要请求均 store false 且 tracing/logging 关闭 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| API-parallel-setting | API 实际 SDK 保持并行工具设置与关联 | uncovered：未运行 | 尚未准备可运行用例 | 未启动实际 API Worker/Agents SDK 角色入口；本轮公开核心合成用例不能证明实际角色路由、报告和任务协议。 |
| CODEX-TS-bridge | Codex TS 的公开 SDK 入口保留模型政策并覆盖公共生命周期 | uncovered：未运行 | 尚未准备可运行用例 | 仅编译和执行了独立假 native/launcher；没有执行对应 Worker/真实 SDK，缺少受控端到端入口及单独授权。 |
| GO-admission | Go Worker 无准入证据时在注册/领取前拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 仅编译和执行了独立假 native/launcher；没有执行对应 Worker/真实 SDK，缺少受控端到端入口及单独授权。 |
| PYTHON-admission | Python Worker 无准入证据时在注册/领取前拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 仅编译和执行了独立假 native/launcher；没有执行对应 Worker/真实 SDK，缺少受控端到端入口及单独授权。 |
| GO-lifecycle | Go Worker 的期限、取消、证据、工作区和报告端到端对齐 | uncovered：未运行 | 尚未准备可运行用例 | 仅编译和执行了独立假 native/launcher；没有执行对应 Worker/真实 SDK，缺少受控端到端入口及单独授权。 |
| PYTHON-lifecycle | Python Worker 的期限、取消、证据、工作区和报告端到端对齐 | uncovered：未运行 | 尚未准备可运行用例 | 仅编译和执行了独立假 native/launcher；没有执行对应 Worker/真实 SDK，缺少受控端到端入口及单独授权。 |
| PYTHON-sdk-receipt | 真实固定 Python SDK 在送正文前核对公开 thread/read 回执 | uncovered：未运行 | 尚未准备可运行用例 | 仅编译和执行了独立假 native/launcher；没有执行对应 Worker/真实 SDK，缺少受控端到端入口及单独授权。 |
| NS-roles-slots-attempts | 九角色、两个 slot、多 attempt 和 CP 重试全部经过实际禁存入口 | uncovered：未运行 | 尚未准备可运行用例 | 无真实 Codex/SDK/平台全通道或运维过程证据；模型、网络、部署和主机控制未授权；合成政策不构成认证。 |
| NS-normal-no-body | 实际正常会话在全过程监测中无正文落盘 | uncovered：未运行 | 尚未准备可运行用例 | 无真实 Codex/SDK/平台全通道或运维过程证据；模型、网络、部署和主机控制未授权；合成政策不构成认证。 |
| NS-tool-failure-no-body | 实际工具失败会话在全过程监测中无正文落盘 | uncovered：未运行 | 尚未准备可运行用例 | 无真实 Codex/SDK/平台全通道或运维过程证据；模型、网络、部署和主机控制未授权；合成政策不构成认证。 |
| NS-model-failure-no-body | 实际模型失败会话在全过程监测中无正文落盘 | uncovered：未运行 | 尚未准备可运行用例 | 无真实 Codex/SDK/平台全通道或运维过程证据；模型、网络、部署和主机控制未授权；合成政策不构成认证。 |
| NS-timeout-no-body | 实际超时会话在全过程监测中无正文落盘 | uncovered：未运行 | 尚未准备可运行用例 | 无真实 Codex/SDK/平台全通道或运维过程证据；模型、网络、部署和主机控制未授权；合成政策不构成认证。 |
| NS-cancel-no-body | 实际显式取消会话在全过程监测中无正文落盘 | uncovered：未运行 | 尚未准备可运行用例 | 无真实 Codex/SDK/平台全通道或运维过程证据；模型、网络、部署和主机控制未授权；合成政策不构成认证。 |
| NS-lease-no-body | 实际租约丢失会话在全过程监测中无正文落盘 | uncovered：未运行 | 尚未准备可运行用例 | 无真实 Codex/SDK/平台全通道或运维过程证据；模型、网络、部署和主机控制未授权；合成政策不构成认证。 |
| NS-termination-tree | 实际父进程强杀和逃逸子进程不突破禁存/资源终态 | uncovered：未运行 | 尚未准备可运行用例 | 无真实 Codex/SDK/平台全通道或运维过程证据；模型、网络、部署和主机控制未授权；合成政策不构成认证。 |
| NS-other-channels | 实际 memory/state-cache/hooks/plugins/trace/诊断均无正文写入或导出 | uncovered：未运行 | 尚未准备可运行用例 | 无真实 Codex/SDK/平台全通道或运维过程证据；模型、网络、部署和主机控制未授权；合成政策不构成认证。 |
| NS-derived | 实际子 Agent、fork 和压缩继承禁存或在创建前拒绝 | uncovered：未运行 | 尚未准备可运行用例 | 无真实 Codex/SDK/平台全通道或运维过程证据；模型、网络、部署和主机控制未授权；合成政策不构成认证。 |
| NS-host-auth | 实际认证缓存与主机介质符合部署 G2 政策 | uncovered：未运行 | 尚未准备可运行用例 | 无真实 Codex/SDK/平台全通道或运维过程证据；模型、网络、部署和主机控制未授权；合成政策不构成认证。 |
| UPGRADE-order | 升级先 drain，并按只读至可写角色分批接入 | uncovered：未运行 | 尚未准备可运行用例 | 无真实 Codex/SDK/平台全通道或运维过程证据；模型、网络、部署和主机控制未授权；合成政策不构成认证。 |
| ROLLBACK-policy | 旧包/配置回退仍满足 G2，未完成 attempt 与远端副作用分别恢复对账 | uncovered：未运行 | 尚未准备可运行用例 | 无真实 Codex/SDK/平台全通道或运维过程证据；模型、网络、部署和主机控制未授权；合成政策不构成认证。 |
| ROLLBACK-old-directory | 回滚和历史清理不自动删除其他用户共享目录 | uncovered：未运行 | 尚未准备可运行用例 | 无真实 Codex/SDK/平台全通道或运维过程证据；模型、网络、部署和主机控制未授权；合成政策不构成认证。 |

## 业务子集覆盖率

下表来自Node `test:coverage` 原始事件，剔除 tests/fd005 下的代码；计数采用Node/V8自身定义，没有从测试通过数反推代码分支。未运行的Go/Python或实际Worker代码不被该子集覆盖。

| runtime-core文件 | V8分支覆盖数/总数 | 行覆盖数/总数 |
|---|---|---|
| aiw-adapter.mjs | 11/23 | 34/69 |
| attempt.mjs | 6/8 | 46/170 |
| context.mjs | 54/85 | 155/167 |
| edit.mjs | 31/38 | 69/69 |
| errors.mjs | 5/5 | 17/26 |
| evidence.mjs | 18/38 | 79/119 |
| index.mjs | 1/1 | 14/14 |
| model-guard.mjs | 8/14 | 18/91 |
| output.mjs | 26/29 | 56/74 |
| process.mjs | 21/36 | 74/96 |
| resources.mjs | 39/49 | 94/105 |
| session-policy.mjs | 55/63 | 74/74 |
| settings.mjs | 1/1 | 4/17 |
| tool-host.mjs | 1/2 | 15/85 |
| workspace.mjs | 1/1 | 19/134 |

完整FD业务分支无法给出可信百分比，因此JSON `branch_coverage` 保持 `not measured`，单独保留 `scoped_core_branch_coverage` 和原始计数。不能用此子集豁免真实全会话G2要求。

## 命令与授权

[Planner批准记录](FD-005-test-authorization-r5.md)及同名JSON绑定精确事件、FD版本/摘要、Tester session、唯一命令和八个文件SHA-256。工作目录为 `D:/03_projects/AI-tools/aiw-dev`。实际唯一顶层命令：

```text
node tests/fd005/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r5.json
```

实际调用链如下，均属于该一条授权命令；没有第二次验证执行。两个Go编译输出只在专属临时目录，GOPROXY/GOSUMDB off、GOTOOLCHAIN local、GOENV off、CGO关闭，缓存/用户配置全部隔离；未下载或安装依赖。Node子命令exit 1决定本轮失败。

| 实际子命令 | 工作目录 | exit | 耗时 | 原始输出 |
|---|---|---|---|---|
| go build -o D:\03_projects\AI-tools\aiw-dev\tests\fd005\.tmp-fd005-tester-20261005-7865d383-8640da40-5afc-40bf-806c-0c31014921a4\programs\fake-native.exe D:\03_projects\AI-tools\aiw-dev\tests\fd005\fake-native.go | D:\03_projects\AI-tools\aiw-dev\tests\fd005 | 0 | 7146 ms | [原始输出](../../../tests/fd005/evidence-r5/compile-fake-native.log) |
| go build -o D:\03_projects\AI-tools\aiw-dev\tests\fd005\.tmp-fd005-tester-20261005-7865d383-8640da40-5afc-40bf-806c-0c31014921a4\programs\launcher.exe . | D:\03_projects\AI-tools\aiw-dev\workers\codex-session-launcher | 0 | 906 ms | [原始输出](../../../tests/fd005/evidence-r5/compile-launcher.log) |
| C:\green\nodejs\node.exe --experimental-test-coverage --test --test-concurrency=1 --test-reporter=./tests/fd005/reporter.mjs tests/fd005/core.test.mjs tests/fd005/session.test.mjs | D:\03_projects\AI-tools\aiw-dev | 1 | 7159 ms | [原始输出](../../../tests/fd005/evidence-r5/node-test-events.jsonl) |

命令报告 `resource_uncertain=false`；入口已在解析绝对路径、专属前缀和owner校验后清理本次临时根。未触发245秒预算或进程树未知保留分支。没有留下临时native/launcher发布产物；保留的只有测试代码和原始证据。

交接CLI此前分别拒绝报告的附加布尔字段和命令/授权条目格式。已删除非模板顶层布尔字段，并按当前CLI约定把命令与授权写成一一对应的字符串数组，Dual授权引用指向Markdown。此修正只涉及报告格式，实际测试仍仅执行一次，原始证据和结论未变。

## 静态依据与未运行项目

已读取FD、独立测试/授权模板、公共执行契约、SDK能力基线、运维说明、兼容样例、公开ESM声明和禁存数据格式。四个process运行时的九份Profile之taskType/capability/readOnly均静态核对；这不代表真实运行时角色路由通过。Worker报告只用于范围和既有编译证据，没有把其内容当作测试预期或结果。

- 模块命名、职责组织、复制源码来源和 MIT 许可属于静态审查，不计入运行行为分母。
- 旧 codex-* 参考示例定位与 demo-deployer 独立维护范围属于静态维护说明。
- 发布携带规则由 Reviewer 静态检查；本轮没有最终发布包构建，不能声称发布物行为通过。

本轮未运行真实SDK/模型/端点/控制平面/插件服务、网络、依赖安装、Go/Python实际Worker端到端、实际Codex全通道监测、类型检查、lint/formatter或最终发布构建。未执行重跑、自动review、主机配置改动或用户目录清理。

## 剩余风险与后续边界

只存在绑定假程序且显著标为synthetic-test-only的政策、证明和输入；它们验证适配契约，不认证真实Codex、版本、平台、派生会话或磁盘/导出通道。完整FD还有84个未覆盖场景，以及本轮51个前置阻塞和7个部分执行场景。实际部署缺少禁存证据时必须保持拒绝准入。

PM应据此拒绝本轮结果。Worker修复观察到的拒绝边界或明确公开Metadata/Runner/输出预算契约后，Tester才能依据新公共契约纠正有据的夹具，提出新版本命令和绑定授权，再独立测试；本轮授权不能复用。
