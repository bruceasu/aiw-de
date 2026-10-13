# FD-014 Linux 生产候选准入方案与阻塞

<!-- aiw-data: FD-014-blocker-linux-admission-r1.json -->

- **FD / 阶段 / 角色：** FD-014 / Auto 接续、生产候选准入 / PM。
- **来源事件：** `FD-014-000012-work-requested`，pending Worker；本会话没有领取它。
- **日期与状态：** 2026-10-13；未解决。生产候选目标保留，未降低验收要求。

## 已完成的接续工作

用户已确认原 Worker 会话结束。Linux 工作树已登记为 `feature/FD-014`，父分支为 `master`，路径为 `/workspace/ai-tools/aiw-dev/.wt/FD-014`；共享 `.ai` 符号链接指向主工作区。原 Windows 实施提交对象不在本机仓库中，不能宣称恢复了原提交历史。

PM 从工作树 FD Revision 11 执行受管 `recover-worker`，将原事件取消并创建 Revision 12 的待处理交接；未手改收据。迁移后的源码、文档及历史报告保存在本地提交 `c2507f7`。该提交是迁移检查点，不是新实施完成或 Reviewer 通过。

Reviewer 结果事件数量为 0；未创建 Reviewer、发送 implementation-ready、交付或归档。历史报告中的运行结果来自 Windows，不能自动作为 Linux 的平台验证证据。

## 准入方案草案

本方案遵守 `docs/worker-codex-no-store-design.md` 及 `workers/WORKER_RUNTIME_CONTRACT.md` 的既有禁存规则。以下是待核定、实施和验证的控制方案，不是已具备的能力；CLI 参数和配置键需核对固定版本官方源码后才能选用。

| 准入部分 | 拟采用的控制 | 必需证据及失败处理 |
|---|---|---|
| 固定部署组合 | 绑定 Linux 平台、CLI `0.162.1` 原生文件摘要、Go 适配器及配置来源摘要 | 来源核对和验证工件须匹配实际部署；版本字符串本身不构成认证 |
| 启动前检查 | 沿用 metadata-only 政策；在注册、领取及送入正文之前核验能力和证据 | 缺失、未知、被替换或冲突即拒绝；不能用操作者勾选代替运行证据 |
| 全部本地正文通道 | 分别核对 rollout/history、trace/OTEL、memory/SQLite、缓存和 shell snapshot、错误日志、hooks/MCP/插件、派生线程与压缩 | 必须逐通道确定控制和覆盖证据；只设置 ephemeral 或事后删除文件均不满足要求 |
| 主机与服务边界 | 列出主机诊断、持久化介质和实际账号/端点的数据政策责任 | 本地 flag 不能证明供应商政策；缺少必要部署依据时保持未准入，不能自行修改系统设置 |
| Linux 生命周期 | 验证成功、取消、断连、租约丢失、父进程终止和重启；观察 thread/attempt 关联和资源终态 | 运行检查需单独授权；过程监测与结束后扫描结合，不记录生产正文或密钥 |
| 显式 Exec 回滚 | drain/停机后显式改为 codex-cli；回滚组合也必须符合相同禁存政策 | 不允许借回滚恢复有持久化风险的旧组合；仍须补齐实际重启及回滚运行证据 |

原 1.6 保持未完成。完成上述方案不是自动完成 1.6；还需要真实部署证据、实现和独立 Reviewer 验证。没有新增或改动配置、权限或启动契约。

## 实际命令与结果

- `aiw fd recover-worker FD-014 --expected-event FD-014-000010-work-requested --expected-session fd014-worker-20261013-7c2e91 --reason <用户确认原会话结束及 Linux 接续理由>`：成功，创建 pending Worker 事件 `FD-014-000012-work-requested`。
- 路径限定的 `git add`、`git commit -m "FD-014: preserve migrated implementation on Linux"`：成功，提交 `c2507f7`；未纳入 Windows Zone.Identifier 文件或共享 `.ai` 链接。
- `GOTOOLCHAIN=local GOPROXY=off GOSUMDB=off go build -o /dev/null .`（Go Worker 目录）：失败，缺少已有依赖 `github.com/BurntSushi/toml v1.6.0`；模块查找被离线策略阻止。没有下载依赖，没有保留分发产物。
- 未运行测试、真实 turn、运行验证、跨语言构建、网络查询、部署或推送；未复跑未经修复的失败命令。

## 当前需要的授权

1. 联网读取 OpenAI 官方固定 tag `rust-v0.162.1` 的文档/源码，以核定控制项。该授权仅解决研究来源，不证明禁存已通过。
2. 下载项目已有的 `github.com/BurntSushi/toml v1.6.0`，随后重试一次相同 Go 编译命令；不升级依赖。

后续运行验证应依据具体方案和命令另行授权。现有固定 CLI 版本、源码编译和历史真实 turn 都不能替代禁存准入证据。

## 既有 blocker 与复用改进

Linux 路径和旧会话接续问题已解决；原候选准入 blocker 仍未解决。本记录承接 `FD-014-blocker-resume-20261013-linux-6e91b4` 与 `FD-014-blocker-candidate-admission-20261013-0828-a54d3e`。

技能引用的 blocker 专用模板仍缺失，本记录沿用现有双工件格式。模板补齐及跨平台工作树预检值得建立后续工具改进范围，本 FD 未扩大到相关工具修改。

## 授权与设计方向更新（2026-10-13）

- 用户 confirm 已授权固定官方来源核对、现有 toml v1.6.0 下载及一次 Linux 编译重试；两条 Go 命令均已通过。原网络授权与依赖缺口已解决。
- 固定源码核对见 FD-014-no-store-source-review-linux-r1.md/.json：App Server 有独立 SQLite/日志通道，sqlite 特性开关已移除，不把 ephemeral 或 RUST_LOG=off 当作全通道证明。
- 用户已接受 Linux 内存隔离部署限制，并澄清应同时支持 API Key 与 ChatGPT/Codex 登录显式切换，无需设计阶段二选一。两种认证分别准入。原 1.6 未完成，已明确取消并由 1.6.1–1.6.6 承接范围。
- 使用受管 refresh-worker 将设计变更绑定到 FD-014-000014-work-requested（pending），没有领取或篡改收据。
- 本记录保持未解决，因为实际隔离启动与主机/服务端证据尚不完整；新平台设计 Gate 由 FD-014-blocker-linux-isolation-r1.md/.json 跟踪。

## Docker 与本地阶段决定（2026-10-13）

- 用户决定后续采用 Docker，当前不处理 Docker，在本地继续。隔离策略不再待选择；没有运行 Docker 或获得生产禁存证据。
- 本地 ready 子项 1.6.4.1 已由 Worker fd014-worker-linux-20261013-a83c26 在 FD-014-000016-work-requested 下实现；默认 inherit，显式 api-key/chatgpt 使用准备好的独立 CODEX_HOME，不自动登录/注销/复制凭据。新增源码通过一次离线 Go 编译。
- 原生产准入缺口仍未解决，当前按用户指示暂缓，不阻塞已授权的独立本地实现。新增登录/切换运行检查未执行。后续状态见 FD-014-blocker-local-stage-r1.md/.json；本记录不再要求用户选择认证或隔离工具。

## 最终范围处置（2026-10-13）

用户明确将 FD-014 收敛为本地 Go Runtime 与双认证支持。此工作流 Gate 已由范围调整解除；Docker、生产准入和新增 Linux 生命周期证据移交 FD-015，真实认证切换本轮跳过。此前未完成证据仍未运行、未通过。后续对修订范围进行独立审查、本地合并与归档；不重复索取已解决的认证或部署决策。
