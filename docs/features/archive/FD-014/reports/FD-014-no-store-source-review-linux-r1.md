# FD-014 固定版本禁存源码核对与 Linux 准入设计（r1）

<!-- aiw-data: FD-014-no-store-source-review-linux-r1.json -->

- **角色：** PM，补充设计与准入证据核对；不是 Worker 完成报告或 Reviewer 结果。
- **来源事件：** `FD-014-000012-work-requested`，仍为 pending；未领取。
- **核对日期：** 2026-10-13。
- **固定基线：** OpenAI 官方 `rust-v0.162.1`；annotated tag 对象 `7a078f6bb4ce0dc2a81408b334ce52979d92c603`，指向源码提交 `092d3acd6bec3e3a14bdc7e7a2810ab628ab759d`。
- **结论：** 已核定会话持久化之外的独立通道。普通主机上的 ephemeral thread 不能据此取得禁存准入；需要部署隔离方案、实际平台与认证方式的证据及运行验证。生产候选目标没有降低。

## 固定源码证据

| 核对项 | 已确认事实 | 准入影响 |
|---|---|---|
| App Server 全局初始化 | 启动阶段调用 `init_sqlite_state_db_with_fresh_start_on_corruption`，再把 `state_db` 接入 `log_db::start` 及 `log_db::default_filter`。[固定 lib.rs](https://github.com/openai/codex/blob/092d3acd6bec3e3a14bdc7e7a2810ab628ab759d/codex-rs/app-server/src/lib.rs#L661) | thread/start 的 ephemeral 属性不是这个进程级初始化的开关 |
| SQLite 初始化 | `try_init` 把 codex_home、sqlite 和 provider 传入初始化逻辑；`RolloutConfigView` 没有 ephemeral 字段。[固定 state_db.rs](https://github.com/openai/codex/blob/092d3acd6bec3e3a14bdc7e7a2810ab628ab759d/codex-rs/rollout/src/state_db.rs#L60) | 不能从 thread 的 ephemeral 回执推导全局数据库没有写入 |
| SQLite 日志层 | 独立 filter 使用默认 TRACE，并排除或降低部分目标；日志项包含 message 和格式化事件/跨度字段，随后写入数据库。[固定 log_db.rs](https://github.com/openai/codex/blob/092d3acd6bec3e3a14bdc7e7a2810ab628ab759d/codex-rs/state/src/log_db.rs#L61) | `RUST_LOG` 的 stderr filter 不能替代日志库准入。此处确认了日志通道，未证明某次运行实际保存了完整提示词，也未证明所有事件都经过正文脱敏 |
| 已移除的 sqlite 开关 | `Feature::Sqlite` 对应 `key: "sqlite"`、`Stage::Removed`、默认 true。[固定 features/lib.rs](https://github.com/openai/codex/blob/092d3acd6bec3e3a14bdc7e7a2810ab628ab759d/codex-rs/features/src/lib.rs#L1224) | 不把 `features.sqlite=false` 当作这个版本的有效关闭机制 |
| 内存 thread store | `ThreadStoreConfig::InMemory` 的源码注释限定为 test/debug；对应配置标记 experimental/do not use。[固定 config/mod.rs](https://github.com/openai/codex/blob/092d3acd6bec3e3a14bdc7e7a2810ab628ab759d/codex-rs/core/src/config/mod.rs#L600) | 不借这个配置推导生产支持或全局 SQLite/日志关闭 |
| Memory 与 OTEL | memory 有独立 generate/use 配置；OTEL 有独立日志、trace、metrics exporter 和 prompt/response 日志配置。[固定 types.rs](https://github.com/openai/codex/blob/092d3acd6bec3e3a14bdc7e7a2810ab628ab759d/codex-rs/config/src/types.rs#L620) | 每项都需要固定配置、来源摘要及实际生效证据 |

源码仅在系统临时目录读取，没有安装、编译或启动官方 CLI。部分最初候选路径在该 tag 中不存在，改由该提交的 Git tree 定位真实模块；没有借用浮动 main 的同名文件。

## 官方文档与证据边界

[配置参考](https://learn.chatgpt.com/docs/config-file/config-reference)分别列出 `history.persistence`、`feedback.enabled` 和 `sqlite_home`；它们不能相互替代。该页面是当前文档，固定版本实现仍以上面的提交为准。

[App Server 文档](https://learn.chatgpt.com/docs/app-server)说明 ephemeral fork 的内存语义及 thread 属性；该协议语义不是主机全部日志、缓存和派生会话的禁存认证。

[OpenAI API 数据控制](https://developers.openai.com/api/docs/guides/your-data)区分应用状态、滥用监控留存及 Zero Data Retention。文档中的 API 政策不能自动套用于未知账号/端点，也不能自动当作 ChatGPT 登录认证路径的承诺。本地 ephemeral 或请求 store:false 不证明供应商零留存。

## 建议的 Linux 部署准入边界

用户已接受保留固定官方 CLI，并把生产候选限定为经过验证的 Linux 内存隔离部署；限制已确认，隔离机制尚未实施。

1. **写入前约束。** Codex 自有状态、SQLite/WAL、诊断、缓存和临时通道只能写入受管内存区域；普通磁盘临时目录和结束后删除不能作为禁存机制。先拒绝未知平台，不默认降级到普通主机。
2. **主机条件。** 需要证明匿名内存及内存文件系统不会进入磁盘 swap、core dump 或持久化诊断；不在本 FD 自动改动主机 swap、挂载或系统诊断设置。仅给 tmpfs 加 noswap 不能单独证明进程匿名内存不会被交换。
3. **文件系统边界。** 已批准的 attempt 工作区继续允许任务授权的源码及工程工件写入；它不能成为聊天恢复或 CLI 状态落盘目录。认证文件沿用批准机制，不复制密钥；认证刷新、配置层、工具/MCP/hook 的写入要求须逐项核定。
4. **配置与能力。** 固定 history、memory、feedback、analytics、OTEL 等配置及来源；尚未核验的插件、hooks、MCP 和派生持久化能力不进入生产候选声明。不能仅把 stderr 丢弃后宣称内部数据库无正文。
5. **身份与证据绑定。** 部署清单绑定 Linux 平台、CLI/适配器摘要、配置来源、主机策略、实际认证/服务端数据政策及验证工件；不是一个人工设置的“已验证”布尔开关。主机政策证据和应用端检查是不同证据。
6. **生命周期与回滚。** 经授权的运行验证覆盖多 slot、成功、工具/模型失败、取消、断连、租约丢失、父进程终止、重启及显式 Exec 回滚。过程写入监测与结束扫描均须覆盖；Exec 回滚组合也必须满足相同禁存政策。

这个方案需要部署级隔离和运行证据；当前没有创建内存挂载、隔离容器、系统配置或新的 CLI 包。用户已明确要求同时支持 OpenAI API Key 与 ChatGPT/Codex 登录的显式切换；无需在设计阶段二选一。两种认证分别验证准入与服务端政策。原 1.6 拆分为 1.6.1–1.6.6，运行证据仍是最终完成标准。

## 本次实际命令与结果

- `GOTOOLCHAIN=local GOPROXY=https://proxy.golang.org GOSUMDB=sum.golang.org go mod download github.com/BurntSushi/toml@v1.6.0`：通过，下载已有固定依赖；没有升级 go.mod/go.sum。
- `GOTOOLCHAIN=local GOPROXY=off GOSUMDB=off go build -o /dev/null .`：授权重试一次，通过，输出到 `/dev/null`，不保留分发产物。
- 联网读取 OpenAI 官方 GitHub tag、目标 Git tree 和选定源码，以及官方配置/App Server/API 数据文档；下载内容位于系统临时目录。一次候选 `config/src/types/otel.rs` 请求为 HTTP 404，实际定义已在已下载的 `config/src/types.rs` 找到，未重复请求失败路径。
- 未执行测试、真实 turn、文件写入监测、主机配置变更、部署、Reviewer、合并或归档。编译通过只证明本地源码可编译。

## 下一步

部署限制与双认证方向已确认，继续核定具体隔离启动机制、主机证据、凭据边界、准入实现及运行命令。若无法提供满足禁存的主机与服务端条件，保持 1.6 未完成及 Worker 交接待处理，不标记生产候选就绪。

## 双认证边界核对

[官方认证文档](https://learn.chatgpt.com/docs/auth)明确支持 API Key 和 ChatGPT 登录，并说明它们采用不同组织/工作区的数据政策。文档还指出，不匹配 forced_login_method 时可能注销已有登录。因此本设计不通过改写共享登录缓存或触发注销来实现切换；应使用经过准入的独立 runtime 认证上下文并验证有效类型。身份与凭据的存储/刷新机制需要纳入隔离启动契约，不能把正文禁存与认证缓存混为一谈。
