# FD-014 Worker 实施报告（r1）

<!-- aiw-data: FD-014-implementation-r1.json -->

- **FD：** `FD-014`
- **Worker handoff：** `FD-014-000010-work-requested`
- **Worker session：** `fd014-worker-20261013-7c2e91`
- **结果：** 完成 App Server runtime、受管进程、协议/事件、slot 隔离、取消与 lease-loss 路径；本地假服务和一次真实 CLI turn 验证通过。Work Items 1.3、1.4、1.5、1.7 已完成。Work Item 1.6 的生产候选准入仍被禁存语义和完整 restart/rollback 证据 Gate 阻塞，未发送 `implementation-ready`。

## 完成内容

- 固定 Codex CLI `0.162.1` 与官方 tag `rust-v0.162.1` 的 App Server v2 JSON schema；启动前校验版本及 `initialize`/`initialized` 握手。
- 为每个 Go Worker 管理一个 stdio App Server；每个 attempt 使用独立 ephemeral thread；请求/通知按 thread/turn ID 关联；只在 matching `turn/completed` 为 completed 时报告成功。
- 取消会发送 `turn/interrupt`，等待 RPC 回执和匹配的终态；无确认时关闭 stdin 并终止受管进程，Worker 保留不确定 attempt 的 workspace。
- 修复三个运行缺陷：managed command 必须用 `exec.CommandContext`；`interrupted` 是匹配终态确认，不应关闭可复用 App Server；runtime capability evidence 字段需加入证据 allowlist。
- 补充本地假服务测试、Worker lease-loss/stop 集成测试、显式版本拒绝测试和 gated real-turn 测试。部署与迁移建议说明唯一支持版本、手动升级、显式回退到 Exec，以及未量化成本不作收益声明。

## 变更文件与提交

源码与协议实现提交：`e9f15ac`。生命周期/取消修复、假服务与 Worker 集成测试、FD 状态及验证报告提交：`09ad083`。两项提交均位于当前 `feature/FD-014` worktree。

## 检查结果

- 编译：`GOTOOLCHAIN=local GOPROXY=off GOSUMDB=off go build -o NUL .` 通过，不保留分发产物。
- 本地假 App Server/Worker：聚焦 `go test -run '^Test(CodexAppServer(FakeLifecycle|RejectsUnpinnedVersion|DisconnectSignalsFailure|UnconfirmedInterruptStopsManagedProcess)|ExecuteTask(LeaseLossDoesNotReportSuccess|StopRequestIsAcknowledged))$' -count=1` 通过。覆盖并发 thread、session/output 关联、事件、deadline cancellation、interrupt ack/terminal、stop acknowledgement、lease loss、不报告成功、workspace 保留、断连、不确认时进程终止、版本拒绝和关闭。
- 真实 CLI：用户授权后执行 `AIW_RUN_CODEX_APP_SERVER_REAL_TURN=1` 下的 `go test -run '^TestCodexAppServerRealTurn$' -count=1 -v`，本机 Codex CLI `0.162.1` 在临时只读 workspace 完成固定无工具 turn，输出断言通过，stdin EOF 后 App Server 关闭。
- 受限范围：没有运行全量 Go 测试、完整 Worker restart/Exec rollback 验收或部署；没有声称已认证 CLI/主机/服务端全部持久化通道。

## 剩余 Gate

`ephemeral` thread 与单次真实 turn 不能证明所有持久化路径符合仓库禁存契约。完整 Worker restart/Exec rollback 的运行证据也未覆盖。FD 保持 In Progress，生产候选准入及 Reviewer handoff 暂停，等待[候选准入 Gate](FD-014-blocker-candidate-admission-20261013-0828-a54d3e.md)中的范围决定。
