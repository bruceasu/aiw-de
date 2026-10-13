# FD-016 第一次独立审查：请求修改

<!-- aiw-data: FD-016-review-local-r1.json -->

Reviewer session：`fd016-reviewer-20261013-a9e28b61`；已领取来源 `FD-016-000012-implementation-ready`。基准 master `3e13f49`，审查 HEAD `21afd675474a0f929adb32d99dc7d62d0c98a0e1`；实际检查三语言 diff、新 Codex adapter/transport/process、共享 attempt/workspace 与 Worker Dual 报告。

- **P1 R1：turn/start 提升管理员配置的只读 sandbox**。位置 `workers/process-typescript/src/app-server-adapter.ts:71`。写角色允许 AIW_CODEX_SANDBOX=read-only；thread/start 传入并验证 readOnly，但 turn/start 只按 profile.readOnly 分支，写角色被覆盖为 workspaceWrite。Exec 则保留 settings.sandbox，两个模式权限不一致。 修复：由同一有效 sandbox 生成 thread/start 与 turn/start 权限；显式只读配置不得被写角色提升。 对应 Acceptance 5, 7, 11, 12。

- **P1 R2：Windows taskkill 失败被父进程退出掩盖**。位置 `workers/process-typescript/src/codex-process.ts:95`。terminate 在 taskkill 非零且原父进程已经 close 时继续执行；treeGone 在 Windows 只检查 exited 后直接返回 true。无法杀死或核对父进程留下的子操作时，Exec 仍报告已结算，允许清理工作区或 stop acknowledgement。 修复：taskkill 非零/不可核对不得因父进程已 close 而成为成功；保留未知进程树状态并阻止清理/确认。若平台无法提供所需能力，应明确拒绝或保持未知，而非宣传终止已确认。 对应 Acceptance 7, 11, 12。

- **P2 R3：未消费 authoritative agentMessage 完成正文**。位置 `workers/process-typescript/src/app-server-adapter.ts:51`。item/completed 只记工具终态，忽略 agentMessage.text。若最终正文由 item/completed 给出、turn/completed.items 为空，当前逻辑只能依赖此前 delta；无 delta 时有效完成被拒绝，只有 commentary delta 时可能将 commentary 当作 REPORT/WORKSPACE 的 PASS summary。固定 schema 的 AgentMessage 具有完整 text，而 Turn.items 允许空数组，不能以 delta 串代替权威最终结果。 修复：按 item id/phase 消费并校验完成的 agentMessage 正文，优先最终完成消息；对最终文本执行同一输出预算，不把 commentary 或缺失最终结果当成功。 对应 Acceptance 5, 6, 11, 12。

配置九字段与结果种类、旧任务拒绝、角色能力、技能冻结、只读 Git 收尾及上游 revision 校验已有静态实现依据；计划确认由控制平面负责，没有新增自主编排。package/lock 与发布定义保持不携带 TypeScript Codex 包，三入口显式选择，无自动回退。上述三项使当前结果、权限与未知终态要求尚未全部满足，因此不能通过。

本次只运行只读文件/差异检查、`aiw fd show`、读取真实 event、`aiw fd claim FD-016 FD-016-000012-implementation-ready --session fd016-reviewer-20261013-a9e28b61` 和 `aiw fd emit --help`。在用户授权范围内，以 Python urllib 只读查询 OpenAI 固定 `rust-v0.162.1` 的 ThreadStartResponse、ThreadReadParams、TurnStartParams、TurnCompletedNotification、ThreadTokenUsageUpdatedNotification、ThreadUnsubscribeParams/Response schema，检查嵌入 UserInput/ThreadItem/SandboxPolicy。最初独立 ThreadItem.json、UserInput.json 返回 404，改读上述已存在 schema 的嵌入定义；未重复请求错误路径。

固定协议来源：[TurnStartParams](https://raw.githubusercontent.com/openai/codex/rust-v0.162.1/codex-rs/app-server-protocol/schema/json/v2/TurnStartParams.json)、[TurnCompletedNotification](https://raw.githubusercontent.com/openai/codex/rust-v0.162.1/codex-rs/app-server-protocol/schema/json/v2/TurnCompletedNotification.json)。这些是允许的协议形状证据，不是真实服务/模型运行结果。Worker 三次 compile-only 的成功按其报告保留；Reviewer 未复跑编译，未执行测试、类型检查、真实 CLI/模型、发行构建或部署，未评估可选 fd-test。

生产禁存、认证及平台认证已由最新用户决定移交 FD-015，不作为本次新增阻塞条件。真实并发、SDK 类型与端点、取消和系统路径仍未运行验证。App Server 目前仅删除本地 inbox、未显式释放服务端 thread；持续处理大量任务时的线程资源边界也需留意，不把本地 unsubscribe 等同于服务端清理。本轮三个可操作问题返回 Worker 修复，再独立审查；本次为第 1 次 changes-requested。
