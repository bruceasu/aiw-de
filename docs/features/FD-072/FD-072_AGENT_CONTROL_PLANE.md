# FD-072: 通用 Agent 身份、委托授权与 Control Plane 主动消息通道

**Status:** Design
**Revision:** 2
**Priority:** Medium
**Review policy:** Independen
**Evidence policy:** Markdown

## Problem

Control Plane 当前把个人自动化接入表达为“秘书”身份和授权。具体秘书实现虽然独立运行，但平台配对页面仍要求输入本机秘书地址并打开其页面；Control Plane 的交互因此依赖某个 Agent 产品的名称、部署地址和页面协议。远程 Agent 不一定可被平台反向访问，这种连接方向也不适用。

用户确认的目标是：Control Plane 管理人类身份、代理人类工作的 Agent 身份、委托授权和安全通讯，不在 Control Plane 领域模型中出现“秘书”这一产品概念。Agent 主动连接 Control Plane；Control Plane 不保存 Agent 回调地址，也不直接调用 Agent 的 HTTP 接口。个人秘书只是一个可替换的 Agent 客户端，保留自己的对话、私有数据和本地运行时。

当前代码证据：

- `src/main/resources/static/ui/secretary-setup.js` 要求本机秘书地址、打开该地址的本地页面，并用窗口消息完成配对交接；Java Control Plane 未发现主动访问秘书 URL 的服务端 HTTP 客户端。
- `personal-secretary/platform_client.py` 已由秘书进程主动请求 Control Plane 的 `/api/r1/authority`；`src/main/java/me/asu/aidev/authority/AuthorityBoundary.java` 验证现有专用个人身份和进程会话。
- `openspec/specs/secretary/spec.md`、`openspec/specs/control-plane-api/spec.md` 仍规定了以 secretary/personal identity 为中心的部分平台术语和协议。
- `D:/03_projects/sqlite-eventbus` 当前是独立 Go HTTP 服务，默认监听回环地址，数据保存在独立 SQLite 库；`go/server/internal/api/server.go` 的 API 未实现身份认证。SDK 中接受 `ws://`/`wss://` 的地址会改写成 HTTP(S)，这不是 WebSocket 支持。

以上为静态源码/文档观察，不代表运行时行为已验证。

## Options and decision

### A. Control Plane 继续回调 Agent 地址

拒绝。需要注册并维护 Agent 可达地址、端口、网络策略和在线状态；无法覆盖位于 NAT、防火墙或离线环境的 Agent，也会继续把产品部署形态带入 Control Plane。

### B. 直接部署 `sqlite-eventbus` 作为通用消息服务

暂不采用。复用现有实现能减少消息存储代码，但会增加 Go 进程和独立数据库；跨库提交无法与 Control Plane 的授权/业务状态原子化。当前 EventBus HTTP 入口无身份认证，不能直接承载敏感的 Agent 消息；远程使用还需额外实现认证、授权和 TLS。

### C. Control Plane 管理持久消息，Agent 主动领取（采用）

在 Control Plane 的现有数据库中保存待投递消息、投递状态和回执。Agent 发起 HTTPS 长轮询并领取消息；需要低延迟时，可后续加由 Agent 发起的 WebSocket 连接。WebSocket 只负责及时唤醒/传输，持久消息记录仍是断线恢复和去重的依据。

同步操作继续使用 Control Plane 现有 HTTPS API。异步消息是已受理操作的投递与结果通知渠道，不是新的授权入口，也不代替 Plan、Task、Worker 或业务结果的权威记录。可借鉴 `sqlite-eventbus` 的确认、位点、重试和死信语义，但首期不要求新增独立 EventBus 部署。

## Solution

### Domain model

| 概念 | 定义与所有权 | 不等同于 |
|---|---|---|
| Human Principal | Control Plane 中进行授权的人类主体；首期沿用现有单机 Owner/登录，不新建完整多租户账号系统 | Agent、Worker、审计字段中的操作者文本 |
| Agent | 已注册、具有独立机器凭据的软件主体，可以代表一个已授权的人类发起受限操作 | 某个名为“秘书”的产品、专业执行 Worker |
| Delegation Grant | Control Plane 记录的人类对 Agent 的委托：动作、目标范围、有效期、次数/预算（适用时）、版本及撤销状态 | Agent 自报能力、消息内容、私有配置 |
| Worker | AIW 现有专业任务执行主体，继续使用原有 Worker Pull/Lease 身份和生命周期 | Agent；Agent 不因代表人类而获得 Worker 租约 |
| Delivery Message | Control Plane 持久化的有界投递信封，具有固定 ID、接收 Agent、类型、版本、过期时间和回执状态 | 授权、业务状态、可自由执行的任意命令 |

每个受委托操作至少可追溯到：实际发起者 Agent、代表的人类主体、服务端解析的 Grant、具体目标、原始请求 ID、业务回执。客户端请求体中的 `ownerId`、`actor` 或 Grant ID 本身不得建立代表关系；Control Plane 必须从注册身份和当前 Grant 解析并复核。

### Trust and authorization

1. Control Plane 是 Agent 注册、凭据生命周期、Grant、撤权、目标校验和审计的权威方。
2. 人类通过当前 Control Plane 登录会话和 CSRF 保护的管理 UI 完成 Agent 配对及委托确认。机器双方的身份校验自动进行；不要求在 Agent 和 Control Plane 两侧重复人工批准。
3. 每个 Agent 使用独立密钥/凭据，支持禁用、轮换和撤销。凭据只证明 Agent 身份，不包含任意业务权限；每个敏感业务操作仍在实际执行业务效果前复核当前 Grant、目标、版本、限制和撤权状态。
4. 本机与远程是连接安全档案，而不是 Agent 声明即可指定的信任等级。本机档案限定同机连接并使用一次性配对与操作系统保护的凭据；localhost 单独不构成身份认证。远程档案强制验证 Control Plane TLS 主机身份，并使用 Agent 专属客户端证书进行 mTLS；不允许降级到明文或静默降低验证强度。
5. Control Plane 发起短期配对邀请，Agent 使用它主动连接并提交自己的公钥/证明。配对邀请只用于建立身份，不能直接授予业务动作。Agent 的服务器信任信息随受认证的 Control Plane 邀请配置提供；不得通过未经校验的 Agent URL 回调完成注册。
6. 连接凭据保存在 Agent 所在操作系统的安全存储中，不进入模型提示、日志、导出包、消息 payload 或 URL。

**实现门槛：** 本机配对邀请必须把 Agent 验证 Control Plane 所需的服务器信任信息安全交给 Agent；远程 mTLS 的证书签发/轮换实现，需要在 API 详细设计时固定；它们不得削弱以上验收约束。

### Message delivery contrac

- Agent 仅向 Control Plane 建立出站连接；Control Plane 不存储 Agent 网络地址、不开放回调 Agent 的接口，也不要求 Agent 暴露入站监听端口。
- 首期使用带认证的 HTTPS 领取/回执 API，可采用有界长轮询。消息写入 Control Plane 数据库；业务状态更新和 outbox 记录在同一数据库事务中提交，避免双写丢失。
- 投递语义为至少一次。每条消息使用稳定 ID 和幂等键；Agent 先持久记录处理/回执，再确认领取。重复投递必须返回或合并到同一原始结果，不得重复产生业务副作用。
- 传输失败、Agent 离线或回执未知时，保留原始消息与状态供查询；不得换 ID 自动重做有副作用的业务操作。到期、不可重试及死信状态要可审计、可人工核对。
- 消息信封只携带执行所必需的固定业务引用和最小输入；不传输完整私人对话、策略原文、模型推理、凭据或 Agent 私库快照。
- 收到消息不表示授权；Agent 也不能通过自定义消息类型、payload 字段或重放旧凭据扩大能力。
- WebSocket 不属于首期验收范围。若后续需要实时推送，Agent 主动建立 WSS；断线恢复仍按消息 ID/游标重新领取，且必须沿用同一身份验证和 Grant 检查。

### Pairing experience

1. 人类在 Control Plane 中创建一次性、有短有效期的 Agent 配对邀请，选择本机或远程档案。
2. 人类把邀请导入 Agent 客户端（复制短码/扫描 QR/受控桌面交接均可；具体 UI 由实现阶段选定）。不输入 Agent URL，也不把凭据放入 URL。
3. Agent 主动向邀请指定的 Control Plane 建立连接并证明其持有 Agent 密钥。
4. Control Plane 在当前人类会话中显示待配对 Agent 身份；人类只在 Control Plane 一次确认要注册的 Agent 和委托范围。
5. Control Plane 建立 Agent 身份与 Grant，返回可撤销的连接状态。重启、轮换、撤权和更换设备均有明确状态及回执。

UI 使用通用名称“Agent”或“代理 Agent”；Control Plane API、存储模型和审计事件不得以“秘书”作为身份类型。个人秘书代码仍可自称 personal-secretary，并作为一个 Agent 客户端实现 Agent Protocol。

## Scope

### In scope

- 将 Control Plane 的个人机器主体抽象为通用 Agent 身份，并保持 Human Principal、Agent、Grant 和 Worker 的职责分离。
- 设计并实现 Agent 注册、凭据证明、连接会话、Grant 管理/撤销及审计。
- 在 Control Plane 当前数据库内提供有界、持久、可去重的 Agent inbox/outbox、领取、回执和状态查询。
- 将秘书配对 UI 改为 Agent 邀请/主动注册流程，移除要求 Control Plane 页面填写和回调本机秘书 URL 的协议依赖。
- 保持专业 Worker Pull/Lease API 独立；首期用 HTTPS 长轮询，不实现 WebSocket。
- 协调更新 `openspec/specs/secretary/spec.md`、`openspec/specs/control-plane-api/spec.md`、`openspec/specs/worker-pull-protocol/spec.md` 及必要的 delegated-decisions 术语，使稳定规格与通用 Agent 模型一致。

### Out of scope

- 将 `sqlite-eventbus` 作为新服务直接部署、改造其 WebSocket 或复用其独立 SQLite 库。
- 将 Agent 私人对话、记忆、模型策略或私库迁入 Control Plane。
- Agent 市场、计费、租户系统、多用户共享 Agent 或公共 Worker。
- 用 Agent 身份替代专业 Worker 身份，或重写现有 Worker Pull/Lease 执行协议。
- 首期引入通用任意 topic/pub-sub、任意命令执行、浏览器到 Agent 的回调、Webhook 或 WebSocket。

### Migration gate

本 FD 描述的是用户要求的目标架构，不改变当前稳定规格或现有运行行为。相关 OpenSpec 仍反映当前实现；只有实现阶段按 Work Item 1.1 完成规格修订后，目标行为才成为新的稳定契约。

当前 Control Plane 已保存 personal identity、凭据摘要、Grant、消费记录和审计历史。**建议默认不迁移旧机器凭据或自动继承旧 Grant**；保留历史审计记录，要求 Agent 重新配对并由人类重新确认 Grant，确认新协议稳定后再撤销旧凭据/路由。

实施前确认：旧身份的历史 Grant 按上述默认策略全部重新授权。



## Work items

- [ ] 1.1 固化 Agent/Human/Worker/Grant 词汇、身份与委托关系、信任档案和消息交付不变量；将相关 OpenSpec 从秘书特定身份重述为通用 Agent 能力。
- [ ] 1.2 在 Control Plane 增加 Agent 注册、配对邀请、专用凭据/密钥证明、禁用/轮换/撤销及基于当前人类会话的管理入口；不兼容旧 secretary 路由。
- [ ] 1.3 将现有委托动作检查重构为 Agent + Human Principal + Grant 的服务端决策；所有业务入口统一调用该校验，并保持与专业 Worker Lease 的隔离。
- [ ] 1.4 在 Control Plane 现有数据库实现事务性 inbox/outbox、至少一次领取、稳定幂等键、回执、有限重试、未知结果保留和死信核对 API。
- [ ] 1.5 将 personal-secretary 适配为 Agent Protocol 客户端：主动注册、验证 Control Plane、长轮询领取、持久回执；从 Control Plane 配对页面移除本机地址和秘书 URL 回调依赖。
- [ ] 1.6 完成本机/远程安全档案、一次人类授权体验、文档和迁移路径；迁移门槛决策未确认前不得自动迁移身份或 Grant。
- [ ] 1.7 对完整实现进行独立安全/接口/持久化审查；按仓库授权范围执行窄编译和静态核对，未获授权的测试/运行/部署不执行。

## Acceptance

1. Control Plane 的领域模型、API、管理界面和审计不要求“秘书”身份类型；一个人可注册多个独立 Agent，专业 Worker 仍是不同身份和执行协议。
2. 人类可以在 Control Plane 完成 Agent 注册和精确范围授权；机器身份双向验证自动完成，用户只需在 Control Plane 一次人工确认。凭据撤销后旧会话和旧凭据不能继续操作。
3. 所有 Agent 通讯由 Agent 主动向 Control Plane 发起。Control Plane 不要求 Agent URL、入站端口或在线可达；离线期间的有效消息可在 Control Plane 恢复领取。
4. 本机档案不因 `localhost` 自动信任；连接限制本机并安全存储唯一 Agent 凭据。远程档案验证 Control Plane TLS 身份并验证 Agent 专属身份，拒绝降级及未注册设备。
5. 任何 Agent 消息都不能自行赋权。服务端以已注册 Agent、服务端关联的人类、当前 Grant 和精确目标为依据，在执行副作用前复核授权、版本、撤权和限制。
6. 消息可重复投递但业务请求按稳定 ID 去重；进程重启/断线后可查回执并恢复，不自动重放结果未知的副作用操作。消息与业务状态提交不会因跨数据库双写分离。
7. 私人对话、完整策略、模型推理、私库和连接密钥不会进入 Control Plane 消息或日志；审计包含 Agent、被代表的人类、Grant、目标、请求 ID 和结果状态。
8. `sqlite-eventbus` 不会成为首期独立运行依赖；WebSocket 不是首期要求。专业 Worker Pull/Lease 行为及其身份不因本 FD 改变。
9. 旧身份/Grant 的迁移按照 Migration gate 中经用户确认的策略执行；没有确认时 fail closed，不自动继承旧权限。

## Verification

- 本 FD 为设计文档；目前只进行了源码、稳定规格、现有 FD/Issue 记录和 `sqlite-eventbus` 文档/API 的静态读取。
- 未修改业务源码，未运行编译、测试、EventBus 服务、网络或部署验证。
- Review policy 为 Independent：实现涉及公开 Agent Protocol、身份/授权、持久消息和并发投递。
- 当前 readiness 仍为 Design：迁移门槛的旧 Grant 映射策略须在实现前确认；其他 API 细节由 Work Item 1.1 在既定不变量内确定。

## Sources

- 本会话用户确认：Control Plane 不应有“秘书”产品概念；统一管理 Human Principal、代表人类工作的 Agent、认证授权和通讯；Agent 主动连接，Control Plane 不回调 Agent。
- `docs/issues/archive/ISSUE-00008/issue-plan.md` 与 `docs/issues/archive/ISSUE-00008/sources/discussion-notes.md`：单机优先、保留归属/实际操作者/授权人/Worker 区分、通讯按场景组合；EventBus 是参考而非已选服务。
- `openspec/specs/secretary/spec.md
- `openspec/specs/control-plane-api/spec.md
- `openspec/specs/worker-pull-protocol/spec.md
- `openspec/specs/delegated-decisions/spec.md
- `src/main/resources/static/ui/secretary-setup.js
- `src/main/java/me/asu/aidev/authority/AuthorityHttpConfiguration.java
- `src/main/java/me/asu/aidev/authority/AuthorityBoundary.java
- `personal-secretary/platform_client.py
- `personal-secretary/platform_setup.py
- `D:/03_projects/sqlite-eventbus/README.md
- `D:/03_projects/sqlite-eventbus/go/server/internal/api/server.go
