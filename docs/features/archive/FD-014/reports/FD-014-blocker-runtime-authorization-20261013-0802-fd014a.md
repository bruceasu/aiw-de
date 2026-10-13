# FD-014 App Server 运行验证授权 Gate

<!-- aiw-data: FD-014-blocker-runtime-authorization-20261013-0802-fd014a.json -->

- **FD / 阶段 / 角色：** FD-014 / Worker 实施 / PM、Worker 主代理。
- **来源事件：** `FD-014-000010-work-requested`，旧交接已恢复并由新 Worker 领取。
- **观察时间：** 2026-10-13 08:02（Asia/Tokyo）；于 08:28 更新解决记录。
- **现象：** 用户已回复“使用最新的版本”，据此查阅 OpenAI 官方来源并固定 Codex CLI `0.162.1`、tag `rust-v0.162.1` 及 App Server v2 schema。Go App Server runtime、版本校验、每 Worker 进程管理、thread/turn 映射、取消和关闭路径已实现；相关 Go 源码通过 compile-only 检查。FD Work Items 1.3–1.7 仍未完成。
- **已获授权及结果：** 用户回复“confirm”，授权前述两类验证。聚焦假服务/Worker 测试覆盖并发 thread/session 关联、deadline cancellation、interrupt acknowledgement、matching terminal、stop request、lease loss、断连、无确认时进程终止、版本拒绝和 workspace 资源终态；最终测试组通过。真实 CLI `0.162.1` 在临时只读 workspace 完成一次固定 smoke turn，验证通过并在 stdin EOF 后关闭。
- **修复记录：** 验证发现 App Server `exec.Cmd.Cancel` 需要 `exec.CommandContext`；`interrupted` 终态原被错误识别为未确认取消；Worker runtime capability metadata 不在 evidence allowlist。均已修复并在验证后重跑相关测试。
- **仍未证明：** 这些检查不认证所有 CLI、主机和服务端持久化通道，不构成禁存准入，也未覆盖完整 Worker restart/Exec rollback；对应问题由新的候选准入 blocker 跟踪。
- **状态：** 已解决（2026-10-13 08:28 Asia/Tokyo）。本 Gate 的授权已兑现；FD 仍因候选准入问题保持 In Progress。
- **下一步：** 处理独立的候选准入 blocker；在该项决策和证据完成前，不标记生产候选或归档 Complete。
- **可复用改进：** `docs/templates` 当前仅有通用模板；fd-workflow 引用的 blocker pair 专用模板缺失，已按现存 blocker pair 结构记录本 Gate。
