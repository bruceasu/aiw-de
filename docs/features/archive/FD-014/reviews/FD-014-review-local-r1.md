# FD-014 本地范围独立审查 r1

<!-- aiw-data: FD-014-review-local-r1.json -->

## 结论与缺陷

**Changes requested。** Reviewer 会话 `fd014-reviewer-linux-20261013-c8d052`，已独立领取 `FD-014-000020-implementation-ready`。审查 FD revision 20；提交 `dff2b237d942b4442b44c977d0ab78bf9d15a94f`，差异基线 `813a40107e3364921c33a5cd7d43401d02b94688`。

1. **R1 / 高：未确认终止时仍释放资源。** `app_server_runtime.go:309` 在 fatalDone 关闭时直接返回；decode/事件队列溢出可触发 fatal，而受管进程和 tool/turn 仍可能运行。`consumeTurnEvent` 的事件解析错误也直接返回而不终止活动 turn。`main.go:350` 在 stopRequested 为 true 时没有把这类错误判为 uncertain，随后可能 cleanup 工作区并确认停止。即使 uncertain 为 true，`main.go:342` 的 defer 仍释放工作区锁，executeTask 返回后 slotLoop 继续领取；保留目录与写 unknown evidence 不等于保留其占用。这违反仍在本地范围内的 Acceptance 3 和 CODEX-RUNTIME 最小契约 5。**修复：** 对已发送活动 turn 的 transport/非终态事件失败统一确认停止（matching terminal 或成功终止整个 App Server）；无法确认则返回明确资源未知状态，保留工作区锁并退出或隔离该 slot，不 cleanup/stop-ack。shared server Close 成功可作为所有相关活动停止的证据；Close 失败不可假设安全。只修复本地 App Server 接入产生的相关资源路径，不增加 Docker 或生产范围。
2. **R2 / 高：App Server 忽略 Worker 网络策略。** `main.go:913` 读取 `AIW_CODEX_NETWORK_ACCESS`（默认 false），Exec 明确传递 `sandbox_workspace_write.network_access`；App Server 启动命令及 thread/start 都没有使用 cfg.NetworkAccess。原生已准备认证目录的 config 若启用网络，则 Worker 配置 false 不能覆盖；true 也不能可靠启用。这违反 Acceptance 2 的既有权限契约和 README 共用配置。**修复：** 在 App Server 启动使用原生 `--config sandbox_workspace_write.network_access=<bool>` 或固定协议支持的等效 thread 配置，确保 true/false 均由 Worker 明确传递。

## 其余审查与证据界限

实际阅读了认证配置、环境过滤、login status/account/read、版本/握手、thread/turn 路由、取消与关闭、attempt/lease/workspace 调用链及代码差异。显式认证模式默认 inherit 兼容，准备已有认证上下文，不复制/登录/注销凭据；捕获登录状态只在内存使用，类型检查返回固定错误。没有发现需要变更 CP API、租约或持久化结构的额外范围。

G11/G12 明确允许本轮跳过真实认证及新增 Linux 运行验证；本次没有要求重复这些检查。Worker 报告编译通过与未运行项区分清楚，Windows 历史证据不能替代 Linux。FD-015 的 Deferred 记录承接 Docker/生产准入/新增 Linux 证据，移交不视为生产通过。上面两项是静态可定位的实现行为缺陷，用户免运行验证不免除这些行为要求。

## 实际命令、跳过与风险

执行 cat/sed/rg/nl 定向只读源码与报告读取、git diff/stat、git rev-parse，aiw fd/claim/emit help 与精确事件 claim；一处预期缓存 schema 路径不存在，未下载或重复探测。随后仅写审查文档并进行一次 git diff --check，提交文档与管理事件。未执行测试、编译、格式化、网络、登录/turn、Docker 或生产检查。没有查看 optional fd-test 报告。

运行验证仍未覆盖真实双认证及 Linux 生命周期；生产禁存与服务端留存未认证，仍归 FD-015。修复后应由独立 Reviewer 再审具体修改；不因 waived 检查要求新增运行验证。
