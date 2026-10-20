# FD-020 定向补充设计复核 r2

<!-- aiw-data: FD-020-design-review-r2.json -->

结论：**ready**。revision 4 已覆盖用户新增的 Python SDK 替换要求，以及原生 App Server 的主要生命周期和迁移边界。下列两处文字残留应由 Planner 在 design-ready 前同步；无需再次派发设计审阅。

- FD：`docs/features/FD-020_FEATURE.md`，revision 4。
- 来源事件：`FD-020-000002-design-requested`。
- 审阅会话：`fd020-design-review-r1-20261021`。
- 范围：仅复核相对 r1 新增的 Python App Server、SDK 删除及启动迁移设计；r1 的共同角色、独立打包、Java 冻结规则检查继续适用，但其 SDK 接入意见已被新版方案替代。

## 发现

1. **低：Work Item 1.4 仍写“五种已支持 runtime”。** 新决定实际是四种 runtime：`openai-agents`、`openai-api`、`codex-cli`、`codex-app-server`。应改为四种，避免 Worker 重新加入已取消的 `codex-sdk`。
2. **低：Solution 后风险段仍写“新增 runtime 均已有实现”。** Python 原生 App Server 是本 FD 新增适配，应改为“协议与 Go/TS 有仓库内参考，Python 适配为新实现”，保留其未经运行验证的风险说明。

## 事实与判断

- 当前 Python SDK 依赖集中在 requirements、模型适配、进程 Worker 及 attempt 中 SDK 特有的取消/证据逻辑。新版 1.2.1–1.2.4 将传输、生命周期、旧路径移除及角色权限分开，支持逐项审阅；不能仅删除 requirements 而保留 SDK 客户端上下文或配置分支。
- 仓库 Go/TS 已有 stdio JSONRPC 与 ephemeral/thread/turn 流程，足以作为本地实现参考。新版明确有界帧、队列、RPC、输出、未知宿主请求拒绝、账号检查及策略回执校验，避免由 prompt 替代权限控制。
- 每 attempt 独立进程、Unix process group 管理及正常完成前确认终态，可以与现有 Attempt 的取消回调和 resource_uncertain 接缝衔接。必须等清理确认后才解除回调、报告完成或释放 workspace；进程组无法确认停止时保留资源并停止 slot。native Windows 注册前拒绝可以避免将未实现的全树终态保证当作可用能力。
- 显式 `codex-sdk` 配置拒绝、默认切换 `codex-app-server`、旧 profiles/冻结请求不自动改写以及运维迁移说明，共同构成清晰的破坏性兼容策略。用户已明确要求替换，因此无需再要求业务确认。
- Java 只新增 `openai-api` 即可；Python 使用已有 `codex-app-server` runtime 名称。应同步 Python runtime 登记、profile 选择与证据标签，避免残留 `codex-python-sdk` 身份造成诊断和选型混淆。

## 实现验收提示

新增适配不是直接复制现有旧 Python `run_operation` 包装。该包装取消时因 SDK 无全树证据而设置资源不明；新适配应使用自己的可取消 RPC 与有界进程清理，确保错误、超时、早退及正常完成四条路径都达到同一终态检查。`fast=true`、审批模式旧变量及 sandbox 旧拼写也应明确拒绝或显式映射，不能在后端不支持时静默忽略。

## 未验证与限制

仅使用 `Get-Content`、`rg`、`Select-Object` 读取 revision 4、当前 Python requirements/模型边界/取消流程及 Go/TS App Server 的相关源码。未执行测试、编译、脚本、真实 CLI、模型或网络，未查外部 SDK/协议文档。本结论是静态设计就绪意见，不能证明本机 CLI 协议适配或生产禁存认证通过；独立实现 Reviewer 仍须按实际 diff 检查。
