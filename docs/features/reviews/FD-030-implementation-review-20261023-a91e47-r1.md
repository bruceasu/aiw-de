# FD-030 独立实现审查
<!-- aiw-data: FD-030-implementation-review-20261023-a91e47-r1.json -->

结论：changes-requested。新接入的 TS SDK 后端仍有精确版本门禁，与用户明确的不限定 SDK 版本指示冲突；其余文件集验收的静态路径未发现阻断缺陷。

Reviewer session：`fd030-review-20261023-a91e47-r1`，独立于 Worker `fd030-host-20261023-a91e47`。已检查 show/resume 并 claim 精确来源事件 `FD-030-000004-implementation-ready`。比较基线 `b84cd77`；审查 HEAD `91322bd9461493c4be1ddec0c7e925b8fa4b9e37`，Worker 源码提交 `2c0ae9c`。审查时 FD 的 Pending Verification / Revision 4 为生命周期工具更新，未改实现。

## 发现与验收覆盖

R030-01（中，需求冲突）：`workers/process-typescript/src/api-adapter.ts:76` 的 createApiBackend 在登记前将 Agents/core/provider 固定为 0.20.0、OpenAI 固定为 7.32.0、Zod 固定为 4.6.7，否则抛出 API_SDK_BASELINE_MISMATCH。本 FD 将该 SDK 后端接入三语言受管资源，继续保留运行门禁会拒绝其他版本，即使所需接口实际兼容。用户此前“不用限定 codex 和 openai agent sdk 的版本，有兼容问题就修改”的指示持续有效，PM 在本轮明确确认适用于新接入的 TS 后端。这是旧代码在新接入中继续生效的合同冲突。最小修正：去掉运行时版本硬相等拒绝，保留 installedVersion 的实际元数据及必要接口/能力检查与明确失败；无需安装、升级依赖或运行模型。

- 平台源与版本：实际核对 `SkillFileContract`、`SkillFiles`、显式资源索引及稳定规范。文件字节与独立清单摘要、原 Asset 文本摘要分别建模；绑定保存完整包而非 latest 指针。路径、文件数/字节、大小写与文件目录冲突、Base64、每文件摘要、清单顺序及精确 SKILL.md 均有校验。三语言保持同一 ASCII 清单合同。
- 生命周期：核对 Task 创建事务、配置 bindTask 覆盖默认绑定的未 Claim 限制、V17、Claim 准入及载荷、历史清理。普通重试复用原 Task；问答续行由 `ExecutionYieldService.continueTask` 直接插入 successor 后继承原绑定，无重新解析默认库；Workspace 替换走 createReplacing 继承，已有不同绑定拒绝覆盖。私有包不能转移到其他 repository。
- 消费与隔离：Python 在模型前核对 Task/Attempt、独立 references、准备配置的原版本/文本摘要及 owner/repository；TS/Go 静态 Profile 仅收公共内置包。三语言模型只注入入口/清单，辅助文件从本 Attempt 已校验内存读取；清单外路径拒绝。平台包存在时不采用本地同名目录；缺失/损坏不静默回退。能力与 runtime 双重 Claim 准入，Codex CLI 不宣告资源能力。
- 工具调用：核对 Python SDK、GuardBroker 与 AppServer，TS hosted tool 与 AppServer transport，Go Responses 函数工具与 AppServer 请求回路；只增加读取声明数据，不自动执行 scripts，不新增网络或 workspace 权限。Python 暂停关闭工具和 Attempt 检查、TS AbortSignal/host 及 handler 清理、Go context 与 thread/turn 路由保留原执行边界。TS profile 加载的 Symbol 资源可经普通对象 spread 保留，原 execution.task 同时保留同一 reader，未发现提示词与运行工具断链。
- 证据：已读取批准来源 Issue Plan、FD Work Items/Acceptance、设计 r1/r2 链路、Worker 报告及其 JSON、解决后的 blocker、实际 diff 和编译脚本。Worker 明确记录 Java 288 源离线 compile 成功，Worker 编译首轮 Go VCS 失败及源修复后一次 retry；脚本为 TS/Python/JS 语法编译和 Go 编译，不能当作 TS 类型检查或测试。复制资源的 8 处原有 Markdown 硬换行未被误报为 whitespace 全通过。

## 实际操作与限制

本 Reviewer 只执行 Get-Content、rg、Get-ChildItem、git diff/status/rev-parse 等定向只读检查，以及 aiw fd show/resume/claim/emit 生命周期操作；写入本独立审查对及 FD Verification。最初共享参考路径误指到 `.agents/skills/skills/`，随后改读本 skill 的 `skills/`；一次 rg 的 Windows 通配路径报错后使用已读具体路径继续。均未运行项目代码。

未执行测试、编译、类型检查、服务、DB/Flyway、HTTP、模型、SDK/AppServer、网络、安装、打包、push 或部署；不重复 Worker 命令。静态证据支持当前验收，但真实动态工具兼容、数据库事务、重启/并发/取消及带宽/存储成本仍未运行验证。首期无磁盘缓存或 GC，私有动态配置仍仅 Python 支持，这与 FD 范围一致。

本轮不要求依赖升级、额外运行或扩大动态配置范围；只要求修复实际运行门禁与用户指示的冲突。一次报告修改时 PowerShell 输入编码造成中文匹配失败，静态校验发现后已更正报告及 FD；未改变审查结果或实现源码。

实现审查失败次数累计 1/3；真实回执为 FD-030-000005-changes-requested，交回 Worker。Reviewer 不提交、合并或归档。
