# FD-014 Worker 协议与交接 Gate

<!-- aiw-data: FD-014-blocker-worker-protocol-20261012-b71f6d.json -->

- **FD / 阶段 / 角色：** FD-014 / Worker 实施 / PM、Worker 主代理。
- **来源事件：** `FD-014-000005-design-ready`，由会话 `fd014-worker-20261012-a19d7c` 领取。
- **观察时间：** 2026-10-12 17:11（Asia/Tokyo）。
- **现象：** 已完成并分别提交 Work Items 1.1、1.2。仓库没有 Go App Server stdio 协议实现或固定版本夹具；本地研究只说明 Python SDK 有 ephemeral thread 入口，且未运行认证。与此同时，`aiw fd set-status ... In Progress` 返回强制状态变更并提示 receipts unchanged/may be stale；随后 `aiw fd resume FD-014` 报告当时 FD revision 8 与 Worker 交接 revision 5 不同；记录 Gate 后 FD 当前为 revision 9，要求手动 reconcile。当前 latest Worker 事件为 dispatched，绑定本会话，不是 pending。
- **已确认原因：** App Server 的握手、请求/终态、事件 framing、取消确认、ephemeral 隔离和进程关闭语义尚未以固定 CLI/protocol 版本确立，不能从 Exec JSONL 或 Python SDK wrapper 推断。另一次强制状态操作未更新已领取收据，导致该交接与当前 FD 修订不一致；不能用 `refresh-worker` 替换已 dispatched 的事件。
- **已尝试恢复：** 静态检查 Go runtime SPI、Exec/OpenAI 实现、跨运行时契约、SDK 能力研究和禁存设计；完成基线与跨模式契约文档。核对 `aiw fd show/resume` 和状态操作审计，确认具体事件、会话和版本差异。没有进行网络查阅、启动 Codex/App Server、运行测试或尝试覆盖收据。此会话尚未停止，因此尚未执行只能在 PM 确认 Worker 会话停止后使用的 `recover-worker`。
- **协议来源 Gate 的解决记录（2026-10-13 07:53 Asia/Tokyo）：** 用户回复“使用最新的版本”。本次仅查阅 OpenAI 官方文档和官方仓库，确认最新稳定 CLI 为 `0.162.1`（GitHub tag `rust-v0.162.1`）；以该 tag 的 App Server v2 JSON Schema 固定协议基线，并将官方 App Server 文档作为 stdio 握手/turn 语义补充来源。FD 已记录此决策并移除对应 `%% NEEDS_INPUT`。官方依据包括 [最新稳定发布](https://github.com/openai/codex/releases/tag/rust-v0.162.1)、[ThreadStartParams v2 schema](https://github.com/openai/codex/blob/rust-v0.162.1/codex-rs/app-server-protocol/schema/json/v2/ThreadStartParams.json)、[TurnInterruptParams v2 schema](https://github.com/openai/codex/blob/rust-v0.162.1/codex-rs/app-server-protocol/schema/json/v2/TurnInterruptParams.json)、[TurnCompletedNotification v2 schema](https://github.com/openai/codex/blob/rust-v0.162.1/codex-rs/app-server-protocol/schema/json/v2/TurnCompletedNotification.json) 和 [OpenAI Developers App Server 文档](https://developers.openai.com/siwc/token-sharing-open-source/codex-app-server)。
- **恢复结果及后续状态：** 旧 Worker 会话确认停止后，PM 已使用原事件 `FD-014-000005-design-ready` 和 session `fd014-worker-20261012-a19d7c` 执行 managed recovery，生成并由当前 Worker session `fd014-worker-20261013-7c2e91` 领取 `FD-014-000010-work-requested`。协议来源/版本 Gate 已解决；随后完成 Work Items 1.3、1.4、1.5、1.7 并运行获准检查。Work Item 1.6 仍受禁存准入及完整 restart/rollback 证据 Gate 阻塞，当前 FD 仍为 In Progress。
- **需要的人类决定：** 协议查阅已获用户指示并完成。生产候选所需的离线假 App Server/进程生命周期检查及真实 Codex 运行证据仍需单独授权；本次“使用最新版本”不代表该项授权。编译检查仅在协议确定并完成 Go 源码后执行。
- **下一步与风险：** 当前会话确认先前 Worker 会话已结束；以 `FD-014-000005-design-ready` 和 `fd014-worker-20261012-a19d7c` 执行 `aiw fd recover-worker`，随后由新 Worker 会话领取新事件。按固定版本实现；未经授权的运行验证不得标记通过。
- **可复用改进：** `aiw fd set-status` 应拒绝或安全处理已领取的 stale event，避免仅改 FD 状态却留下不可匹配的 dispatched receipt；修复技能引用的缺失 blocker 模板。
