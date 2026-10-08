# FD-005 实施报告 r5

<!-- aiw-data: FD-005-implementation-r5.json -->

日期：2026-10-05。Worker session：fd005-worker-20261004-2916aea6；来源事件：FD-005-000005-design-ready；FD revision：5。

当前代码基点：0783b1a5be642d52c4d0f62c3884a6b7ea7c5fd2（FD-002 提交），加本报告列明路径的工作区快照；部分公共核心已在当前基点中，Reviewer 须按完整 FD 范围读取源码及历史，而非只审查最后未提交的 G2 片段。

## 结论与范围

十二个工作项的源码、公共契约、兼容样例、来源和运维说明已落实，一次 compile-only 通过；提交独立 Tester。此报告不是行为验收、部署批准或真实 Codex 禁存认证。

范围是 process-typescript-with-api、process-typescript、process-go、process-python，以及 runtime-core 与固定原生 codex-session-launcher，覆盖九个既有可执行角色。控制平面继续管理任务、租约、模型政策、重试与验收；保留 Profile、manifest、report/verdict 和六种 failureCategory。旧 codex-* 为参考示例，demo-deployer 不取得额外部署权限。

## 实施映射

| 工作项 | 交付与静态依据 |
|---|---|
| 1.1 | WORKER_RUNTIME_CONTRACT.md 与 runtime-contract.examples.json：九角色、verifier 别名、只读/报告/旧八字段 Profile，拒绝非执行角色 |
| 1.2 | SDK_CAPABILITIES.md：实际 Agents 0.3.9/OpenAI 6.49.0、Codex TS 0.155.1、Python 固定 wheel 0.157.0 的公开接口和能力缺口；版本启动核对 |
| 2.1 | attempt/workspace/process：整体期限覆盖准备至发布、独立清理、租约丢失、资源未知保留、非幂等 CP 上报与 G1 发布对账 |
| 2.2 | tool-host/resources：参数与权限、IS/IX/S/X、文件读改写一致性、有界并行/队列、取消、委托、真实完成后释放 |
| 2.3 | output/edit：UTF-8 完整 envelope 预算、截断范围与 JSON 拒绝、严格多区域计划、BOM/CRLF、基线核对与有界 diff |
| 2.4 | evidence/attempt/model-guard/session-policy：关键白名单元数据、4 MiB/30 天、工具错误与报告分开校验、全会话禁存准入 |
| 3.1 | context/instructions：完整冻结 Skill 及 references/agents、材料摘要、必需内容与首轮字节预算，精确/未知 usage 分开 |
| 3.2 | context/model-guard：默认关闭、有显式预算、同模型与期限、最多两次、工具调用成对、来源仅在有界内存保留 |
| 4.1 | API index/api-runtime/tools：九角色共用同一生命周期和 ToolHost；并行 true；完整 Responses 终态在工具调度之前校验 |
| 4.2 | aiw-adapter/aiw-tools：默认关闭，固定解释器/插件摘要的 Skills 与经授权 GitHub 查询，schema/超时/容量/最小环境 |
| 5.1 | Codex TS：公共 ESM 第二消费者、保留 installed native resolver 和 SDK 循环/模型政策；固定 launcher 公开接入及发布携带 |
| 5.2 | Go/Python：同契约期限/证据/Git/心跳、整体工作区排他、未知能力明确化；固定 launcher/ephemeral 接入及升级回滚文档 |

pi 仅提供机制与算法参考，不复制整个 Agent。runtime-core/SOURCE-PROVENANCE.md 记录八个实际文件摘要及无 Git 元数据事实，THIRD-PARTY-NOTICES.md 保留 MIT 许可。

## G2 的新增实现与兼容影响

用户明确全会话 G2，并批准固定原生启动适配器与仅下载 Python 固定 wheel。禁存入口不修改 SDK 私有字段或 node_modules：TS 用 codexPathOverride；Go 提示词改 stdin；Python 用公开 launch_args_override、thread_start(ephemeral=True) 与 Thread.read 回执。

三个 Codex Worker 启动均要求工作区外的 AIW_CODEX_SESSION_POLICY。清单绑定平台/SDK/native/launcher/配置来源/原始验证证据；未知或不符时注册前拒绝。配置变更与工程 .codex/config.toml 未核验时拒绝；样例 unverified 不能启用。**没有提供实际 Codex 的 verified 部署证书，现有未认证 Codex 实例升级后将不能注册。**这落实批准的未知能力拒绝政策，并不宣称本机全部通道已证明禁存。

launcher 固定允许 exec 或 stdio app-server，强制禁存覆盖与回执检查，拒绝恢复、保存文件和未授权审批，原始 stderr 进入空设备。Windows 在受控 Job Object 中启动 native；POSIX 进程组与逃逸限制显式保留待验证。Python SDK 内部默认接受审批，公开构造器不提供 handler 覆盖；launcher 阻止接受回复，遇到该路径失败，不把它当作人工授权。

API SDK 导入前关闭 DEBUG、正文日志与全局 tracing，Runner tracingDisabled，OpenAI logLevel off、store false。外部供应商留存不能由这些本地设置证明。完整设计见 docs/worker-codex-no-store-design.md。

## 实际命令与证据

- 经用户批准的两次 API `npm install --ignore-scripts --no-audit --no-fund` 成功，直接 openai 对齐 6.49.0；未升级其他 SDK。
- 本地 `codex exec --help`、`codex app-server --help`，读取官方文档；没有启动会话。
- 经用户批准的 `python -m pip download --no-deps --only-binary=:all: --no-cache-dir --dest <FD-005 专属临时目录> openai-codex==0.157.0` 成功；wheel SHA-256：e97c61d4fda9540d3db309e27bcf46cae6e12293c5b9d74e98fe3d285f8477e3。zipfile 静态读取，未安装/导入/下载依赖。
- `Get-FileHash -Algorithm SHA256` 核对八个 pi 来源；rg/Get-Content/zipfile 阅读针对性源码与声明。
- `node scripts/compile-worker-runtime.mjs` exit 0，一次通过、未重试。输出见 FD-005-compile-r5.log。TS 为内存语法转译，JS --check，Python 内存 compile；Go 两模块禁用网络/工具链下载，产物在专属临时目录结束后删除。
- 最后静态 `git status --short -- <FD 范围路径>`；保留已有 skills/README/发布配置改动，没有 reset、广泛 staging、提交或合并。工作区源码是审查对象，不以未提交 diff 代替全部 FD 范围。

未运行 TS 类型检查、测试/覆盖率、lint/formatter/vet、最终构建/打包、SDK 模型、部署端点、插件查询或真实 native 禁存/进程终止测试。没有把源码、编译和帮助输出写成行为通过。

## 独立验证与风险

Tester 从 FD 验收、公共声明和样例独立推导可观察场景，限 root tests/ 与独立报告；先提交精确离线命令，Planner 检查调用代码后授权。之后 PM 与独立 Reviewer 分别验收，Worker 不生成这两个阶段的通过记录。

风险：全通道真实禁存与主机/供应商数据策略未证明，缺失证据保持拒绝；Codex 内层工具政策与 Python usage 未观察，不伪造 API 工具证据；Windows Job/跨平台终止、租约与发布竞争、摘要关联和输出算法仍待行为验证。进程治理与路径校验不等同 OS 沙箱；无法确认的 I/O 保留资源与工作区供恢复。关键元数据失败阻止成功，远端 completed/unknown 发布需对账；不得把回滚包等同撤销远端副作用。

无最终发布产物或部署结果；独立验收结束前 FD 保持活动。
