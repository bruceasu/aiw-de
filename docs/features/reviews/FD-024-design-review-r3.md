# FD-024 Revision 3 独立静态设计评审

<!-- aiw-data: FD-024-design-review-r3.json -->

结论：**changes-needed；design_status 为 INCOMPLETE**。本报告只审查候选设计，不是实现验收，不关闭 G1，不创建或消费工作流交接。

## 阻断发现

### R3-F1（高）：已准入任务的 actual start 必须重新核验禁止投影和暂停世代

位置：`FD-024_WORKER.md:134`、`:136`、`:189`；Work 1.3、3.2.2、4.3；AC5、AC6。

事实：§6.3 要求私人策略先暂停目标、同步平台禁止投影，动作引用当前 restrictionRevision；§6.6 明确列出的 actual start 校验只有身份、角色、撤权、模型及独立性。尚未明确把新禁止、目标暂停、restrictionRevision 与 targetGeneration 的变化落实到已经 ADMITTED、尚未 start 的 task/attempt。现有 `DelegationTaskPolicy.java:46` 起在发布任务 start 的准入判定中重读适用 DENY，不能因迁移遗漏这个关口。

后果（静态推导）：动作已 ADMITTED 后，用户提交新 DENY 或完成暂停，旧 task 仍可能通过列出的 start 校验。仅使旧 outbox 不升级版本，不能阻止平台已经保存的 task admission；这与“收到收窄后阻止未生效动作”和 DENY 优先冲突。

最小修正：冻结统一的实际 start 谓词，在同一权限首锁和 start 事务内重读当前目标暂停/接管世代、身份世代、能力当前条件及有效期、restrictionRevision 和适用 DENY，并明确哪些变化使旧 admission 失效。禁止自动把旧 admission 升级到新版本；必须由新候选/动作重新准入。确认 A01/A04 的后续评审 task 与 A12 固定发布 attempt 都进入这个关口，保留真实完成上报。

### R3-F2（中）：新权限路由的人工与机器认证接入点尚未冻结

位置：`FD-024_WORKER.md:140`、`:142`、`:209`；Work 1.2、1.4、3.1、4.4；AC3、AC9。

事实：候选设计把页面放在 `/authority`，把人工管理和机器动作都放在 `/api/r1/authority`。现有 `WebConsoleSecurityInterceptor.java:30` 仅对 `/ui`、`/ui/**` 和 `WebConsoleApiRoutes.matches` 路由应用 session/CSRF 边界；`WebConsoleApiRoutes.java:7` 的 ROOTS 不含 authority。候选设计写明了人工 session/CSRF 和机器身份，但没有指定新路径如何接入及如何分流。

后果（静态推导）：直接复用现有拦截器不能保护新页面和人工路由；简单把 authority 整根加入 ROOTS 又会把机器 API 强制送入浏览器登录/CSRF 路径。实现者仍需自行决定安全路由，G3 还不能视作已冻结。

最小修正：页面采用现有 `/ui/authority` 或显式加入受保护范围；逐路由列明人工管理/读取采用当前 session、CSRF、当前角色、永久认证锁存，机器动作/查询采用专用 bearer 和 owner/scope；明确认证禁用时人工绑定失败关闭，以及同一资源按请求方法分流的规则。更新相应路由匹配与注册位置的设计，禁止整根命名空间同时无条件套用两种认证。

## 已有设计覆盖及保留 Gates

- V8/V9：已核对两份迁移全部表和列；§6.2 有逐列组归属，私人原文与平台安全/预算/历史事实分开，没有发现必须整行搬迁的指令。EXTERNAL_WORK/attempt 私人载荷作为显式历史清理对象，清理仍需独立授权，不能声称旧平台已不存在私人历史。
- DENY：最小投影、禁止优先、只收窄机器权限、人工解除、离线失败暂停方向成立；R3-F1 是已准入任务尚缺的具体执行关口。旧 `DelegationConditions` 的状态/最小轮次解释必须保持一致。
- 20 子动作：§6.5 与 `DelegationRule.Action` 的 20 个枚举一致，APPROVE/REJECT、CREATE/EXECUTE、SCHEDULE/UNDO 分开；三类预算、历史参与隔离、模型实际 start 和固定发布 attempt 均有设计要求。本次没有证明全部业务调用链已正确实现。
- 原 F1：§6.6 已补 PREPARED/start 的线性化点、UNKNOWN 占用保留、禁止自动重放/退款/换 ID 与证据不足转人工；底线已纳入候选设计。尚非运行验证。
- 原 F2：§6.4 明确持久实例+owner+requestId 去重，排除 grant/Worker/凭证/boot，明确历史查询与执行分离、无限保留及备份恢复约束；原歧义已消除。
- G3：独立 origin、本地独立登录、Host/Origin/CSRF、无平台 cookie 转交及不反向代理个人 URL 的选择合理；R3-F2 修正前不建议关闭。
- G1：实际源库、owner 映射、在途处置、备份与切换/回退窗口未提供，仍待人工决定；本报告不假定“无需迁移”。全局 legacy/personal 默认兼容及拒绝同目标双写是明确候选政策，不代表已有运行证据。

## 就绪度、工作拆分与限制

设计仍不宜 handoff Worker。先由 Planner 将 R3-F1/F2 的谓词和认证路由写入 1.2–1.4，再判定 G2/G3；G1 保留在依赖它的历史迁移、旧入口停用和切换路径。3.3–3.6 已按动作进一步拆分，较 Revision 2 清楚。3.2.2 同时覆盖意图存储、start/lease recovery 关口和 UNKNOWN 对账，建议按这些边界保留子项；4.2/4.3 仍需明确个人调度与平台自动推进关口的各自产物。拆分建议本身不新增业务需求或运行验收条件。

事实依据来自一次有界本地静态阅读：FD Revision 3、前次评审、V8/V9、delegated-decisions/worker-pull-protocol 稳定规范，以及 DelegationActions、DelegationAdmission、SecretaryPlanGate、DelegationRule/Conditions/TaskPolicy、WebConsoleSecurityInterceptor/ApiRoutes、WorkerProtocolV1Controller 与 LeaseRecoveryScheduler 的相关代码。未完成全库或所有工作线程的实现调用链证明。

实际命令：PowerShell `Get-Content`、`Select-String`、`Select-Object`、`rg`，以及 `apply_patch` 写本报告和 JSON；一次只读 `ConvertFrom-Json/ConvertTo-Json` 元数据检查确认 JSON 可解析、来源事件及结论匹配、两条发现和唯一 aiw-data 链接。首次未显式指定 UTF8 的中文读取出现乱码，后续改为 UTF8；一次查询不存在的 `docs/specs` 和一次 Windows `src/main/java/.../*` 搜索失败，未据此推断内容。未运行测试、编译、服务、HTTP、网络、迁移、格式化、工作流命令或提交。本报告的后果为静态推导，运行行为未经验证。
