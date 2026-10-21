# FD-024: 个人秘书 Worker 独立实现、私有数据库与平台授权边界拆分

**Status:** Complete
**Revision:** 10
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户已明确：秘书是属于个人的 Worker，即使平台未来支持多人使用，也应允许用户提供自己的秘书实现；秘书及代理决策业务数据应存放在秘书自己的数据库。

代码现状：V8 将 secretary_work/message/request/participant/admitted_task 建在平台主库；V9 又将代理规则、模板、草稿、身份、额度、候选、审计及发布许可混合建入 delegation_*。SecretaryRepository/DelegationRepository 使用平台 JdbcTemplate；DelegationRequests 在同一平台事务中校验、扣额、执行并记审计。SecretaryCoordinator 和调度器直接参与平台 Plan 推进。

仅增加第二个 DataSource 无法实现独立个人 Worker：既有模块仍共享服务和锁，也无法让自定义实现脱离平台表结构。本 FD 负责拆开业务所有权、运行边界和持久化，并保留现有授权、累计额度、人工优先及幂等保障。当前实现依据为 FD-021、FD-022；API canonical 前缀依据为 FD-023。

## Options and decision

| 方案 | 影响 | 决定 |
| --- | --- | --- |
| 继续主库加 owner 字段 | 能做逻辑隔离，但个人秘书实现仍依赖平台 schema/部署 | 不满足独立实现目标 |
| 平台进程内增加秘书 DataSource | 分开存储，仍由平台运行个人策略，跨库原事务失效 | 不作为目标架构 |
| 独立个人秘书进程及私有 DB，通过公开协议调用平台 | 用户可替换实现；平台保持可信授权和执行事实，需设计协议、回执与迁移 | 采用 |

已确认的方向：秘书业务 DB 归用户；平台承载通用任务和执行。工程建议：提供一个默认 SQLite 私库的参考秘书 Worker，使用可替换存储接口；平台不得读取个人 DB、依赖其表名或要求用户使用参考语言/模型。实施前仍需完成下述协议与迁移决策，不能把建议描述成已实现。

## Solution

### 1. 职责和数据归属

| 数据/职责 | 所有者 | 平台可保留的必要信息 |
| --- | --- | --- |
| 对话、个人偏好、记忆、安排、提醒及其版本 | 个人秘书 DB | 用户明确提交的业务目标、必要关联 ID |
| 代理原文、结构化个人策略、禁止规则、模板、草稿、推理和个人待办 | 个人秘书 DB | 人工显式授予的有效能力范围、版本和约束；不复制整套个人策略/原文 |
| 秘书自己的请求、定时处理、outbox、回执缓存和决策历史 | 个人秘书 DB | 平台接收的动作请求及其稳定回执 |
| 用户身份、Worker 注册、凭证验证、owner 绑定 | 平台 | 平台权威记录；Worker 不能用 actor 文本自行指定授权人 |
| 平台有效授权、撤权/接管世代、强制预算、幂等键和固定 attempt 许可 | 平台 | 执行动作所需最小权威状态；不以秘书可编辑的计数代替 |
| Plan、专业执行/评审、审批结果、发布和业务审计 | 平台 | 原业务事实；秘书可缓存自己有权读取的回执 |

自然语言和个人策略表达“何时、为何申请操作”；平台能力授权表达“该用户允许此 Worker 对哪些对象执行哪些动作”。个人规则可以进一步收窄能力，不能自行增加平台能力。平台只执行显式授权范围内的固定动作。用户自己修改私库不得改变平台权限或重置平台累计消耗。

不得按 secretary_ / delegation_ 前缀整批搬表。现有 secretary_work/message 的个人内容、delegation_grant/history/template/draft/request 的策略和决策内容应迁入私库；其中平台权限证明、动作请求和幂等回执需拆为各自所有者的记录。secretary_participant/admitted_task、delegation_identity/security/legacy_request/target/budget/task/model_scope/model_constraint/publication_plan/publication_attempt 中的身份、隔离、并发及执行强制状态须逐列判定并转为平台通用模型。旧 audit 需区分个人决策过程与平台执行审计，保留历史关联、原始时间和责任主体。

### 2. 可替换的个人 Worker

参考秘书在独立进程/用户控制的环境中运行，私库默认 SQLite；每个秘书实例绑定一个已认证 owner 和独立数据目录。多个用户不能复用默认文件；仅为既有单用户提供经人工绑定的本地配置，不假装已有完整多人账号模型。一个用户可运行多个实现，平台按稳定 owner/Worker/授权识别，累计平台限额不能因换实例清零。

模型调用、秘书聊天、个人定时安排、原文解析、策略求值和草稿维护移到个人进程。平台不得继续通过 EXTERNAL_WORK 等任务载荷、日志或审计隐式保存整段私人对话/策略；用户有意提交的任务内容仍属于平台业务数据。部署参考实现不要求平台管理员安装用户代码。私库路径、备份/导出、迁移版本、连接和生命周期由个人 Worker 管理，凭证按现有安全配置方式提供，不进提示词或普通导出包。

### 3. 通用平台协议及可信人工授权

所有平台 API 沿用 `/api/r1/...`；本轮资源名、动作矩阵和错误契约见 §6.3–6.5；实现仍等待完整设计准入。现有 Worker 租约协议保持职责独立，不能靠共享 bearer、capability 或普通注册获得代表用户操作的权限。

- 人工通过平台可信登录/session/CSRF 明确绑定 owner、Worker、公钥/专用凭证及能力上限；签发、更新、撤销和首次启用由平台验证当前角色，秘书不能自签平台授权。
- 个人 Worker 通过机器认证提交固定动作信封，绑定稳定 requestId、owner 的平台映射、Worker、授权 ID/版本、具体目标/step/attempt、预期业务版本、必要动作参数及证据引用。服务端忽略未经认证的 owner/actor 声明，不接受任意 SQL、shell、反射调用或开放表达式。
- 对纯平台数据库动作，在一个本地业务事务中完成当前能力/人类角色/撤权/目标版本/独立性/累计限额校验、固定动作准入及幂等回执落库。涉及文件/Git/外部系统副作用的动作必须按第4节另行记录单次准入和未知结果，不能承诺数据库事务能回滚外部效果。当前权限和人类操作优先于未生效的秘书请求。
- 对既有操作进行完整适配：A01–A17，包括 APPROVE/REJECT、发布 CREATE/EXECUTE、删除 SCHEDULE/UNDO 等全部20个子动作；保留执行/评审/代理三方历史隔离、模型 start 白名单、固定发布 attempt 许可及 V4/V5 兼容规则。平台代理执行保障不再依赖秘书私库表或平台内部 ThreadLocal 由私库代码触发。
- 回执提供可认证查询的 requestId、请求摘要、结果/版本及状态。幂等键作用域为持久平台实例标识+稳定 ownerId+requestId，明确不包含 Worker、grant ID/版本、凭证世代或进程boot；换 Worker、授权、凭证或重启仍命中原回执。相同键不同内容拒绝；历史请求授权版本保留为审计字段，不能替换去重域。去重记录保留期及备份恢复约束由G2冻结，不能因授权到期/撤销而清除。业务“已接受/已准入”与任务“运行/完成”分别表示，平台回执不声称文件副作用已回滚。
- 撤权后的历史回执读取与新执行权限分别校验：原owner可用当前可信身份对账，失效Worker凭证不因此获得访问；替换Worker需重新获准最小读取范围。读取回执不重新校验为可执行、不再次消费额度或产生副作用。
- 个人 Worker 最小读取权限仅涵盖授权 owner/目标所需状态、结果及回执；其他用户、其他秘书的私人数据和授权不可读取。

### 4. 跨库一致性、人工接管和重启

不采用跨库事务或平台直连私库。秘书在私库事务中保存决定及待发送 outbox；以固定 requestId 请求平台，收到/查询平台权威回执后更新私库。平台成功后响应丢失、私库写入失败或进程退出时，恢复只重查/重发同一请求，不生成新 requestId 猜测结果、不返还未确认的消耗。

对外部副作用，平台须在允许副作用前持久记录唯一执行意图/单次准入及必要额度占用；授权检查、人工接管与生效的精确线性化点在G2冻结。若发生“外部副作用已执行或可能执行，但平台最终业务/回执事务未提交”的窗口，已有意图保留为 IN_PROGRESS/UNKNOWN 类可查询状态，不得因数据库回滚/超时推断未执行。UNKNOWN 不自动重放、不返还额度、不换requestId或授权重试；仅允许无副作用的状态查询/证据核验，证据不足交由人工对账。完成证据如何取得、哪些适配器支持安全结果查询以及何时可人工重试须逐动作设计，不能用一个通用retry掩盖差异。该协议未冻结前，涉及副作用的适配实现保持gated。

个人策略失效/撤销/到期时停止本地新建议；平台能力到期/撤销/目标变更/人工接管时在原业务首锁边界拒绝旧候选。平台保存必要的接管与撤权记录，即使秘书离线也可生效。交回必须人工明确操作后创建基于当前世代的新候选；Worker 和平台重启、恢复心跳、私库还原均不自动重新启用代理。已生效的动作、消费及真实运行结果保留，撤权不表示撤销外部副作用。

将现有“秘书托管 Plan”约束转换为通用受托 Worker 的平台准入与角色隔离约束；秘书离线时可以记录真实专业完成，但未经新的有效准入不得继续推进。停止/取消及人工恢复路径可在平台使用，不依赖原个人 DB 可用。首次代理启用后的旧管理写入认证要求不能因拆库或停用旧模块而解除。

### 5. UI、兼容与迁移建议

平台 UI 只维护平台身份/有效能力、业务状态和执行回执。个人对话、规则、模板和待办的 UI/API 由秘书实现提供，可嵌入受控入口或外部访问；具体浏览器身份转交方案由 Gate G3 冻结，不建立默认公开 localhost API、宽松 CORS 或跨进程共享平台 session 数据库。

保留已有原始迁移 V8/V9，不修改已发布 schema 历史、不默认删表。建议显式选择迁移：暂停旧秘书/代理产生新动作；先撤销旧候选并对执行中、预留/未结算任务及待发布 attempt 作清单；平台业务及历史消耗留在平台。按已验证 owner/目标 Worker 导出私人数据和版本化关联，带摘要/manifest；目标私库以幂等导入批次接收并核对行数、归属与关联，最后标记旧业务写入只读。迁移工具先做只读预览，真正迁移须另行授权。

迁移不自动启用任何新秘书/代理、不复制有效凭证或重新签发旧能力；由用户在平台重新绑定和确认。平台强制累计账本沿用稳定目标键及已消费历史，不以导出的个人计数覆盖。无法验证归属的 local-console/共享或历史记录保持封存，禁止按当前登录人或文件路径猜 owner。

迁移后的回退默认停用新路径并保留两侧数据供对账；仅当没有新执行且核验可安全恢复时才允许恢复旧写入，不能一键还原旧 DB 从而重复执行/重置消费。实际源库、owner 映射、在途处置及切换窗口见 G1，未确认前不运行迁移/删除/自动切换。

### 6. 本轮工程契约（仅设计产物）

本节是 Planner 基于现有代码提出的工程契约，不是已实现行为，也不是运行验证。与上文的开放建议冲突时，以本节明确限定的设计为准；G2/G3 的两项独立设计发现经修订及定向复核解决，G1 后续人工决定已将工具实现与实际迁移执行分离。参考实现选择 Python 标准库进程（`sqlite3`、`http.server`、`urllib`），复用已有 Python Worker 的进程组织方式，不为此增加平台依赖。平台仍为现有 Java/Spring 模块。此语言选择不限制自定义实现。

#### 6.1 模块、替换边界与替代方案

比较两个公开边界：一是平台接受“规则并替秘书求值”，二是秘书仅提交固定业务动作。前者仍把策略及私人上下文带回平台，无法满足独立实现；采用后者。平台负责认证、最小能力、当前状态/历史独立性检查、预算和执行事实；个人进程负责自然语言、模型选择、私人调度及 outbox。平台只接收主动提交的动作参数，不接受个人策略全文。

新增平台包 `authority`，其应用入口 `ActionAdmissionService` 接收可信 Principal 和固定信封；`HumanAuthorityService` 管理 owner 绑定、能力、撤权与接管；`ActionReceiptRepository` 只访问平台新表。20 动作由闭集适配器调用原业务服务。`ManagedPlanAdmission` 替换 SecretaryPlanGate 的“由秘书仓储判断是否托管”机制，持有平台 Plan→owner/manager/世代和 task→attempt 准入信息。它在 Plan/Task 应用服务入口执行，不由个人代码设置 ThreadLocal。旧代码内部过渡 ThreadLocal 不作为新协议的信任来源。

个人进程模块为 `store`、`platform_client`、`outbox`、`scheduler`、`policy`、`ui`。`store` 只暴露个人工作/消息/策略版本、outbox 与回执缓存的原子操作；客户端只知道公开 API。替换 store 或 Worker 不影响平台 schema；平台不得打开私库或执行用户代码。时钟、模型调用和平台 HTTP 为可替换依赖，不额外创建测试框架。模型调用复用显式配置的端点/命令，只在用户运行参考 Worker 时使用；本轮 auto 不启动它。

#### 6.2 V8/V9 逐列归属与保留映射（Work 1.1）

表中列组覆盖原迁移中的每一列；“拆分”不是把旧行整体复制两次。新模型使用新 schema；V8/V9 保持原样。历史只读封存和实际数据移动仍受 G1 限制。

| 旧表 | 个人数据库列及解释 | 平台保留/转换列及解释 |
| --- | --- | --- |
| secretary_lock | 私库内部事务自行序列化，不迁移旧锁 | id/version 不作为授权；旧锁只供旧模式 |
| secretary_work | id、actor（历史标签，不作身份）、revision、generation、state、due_ms、payload、updated_ms；plan_id 仅为外部关联 | 以新 owner/managed_plan 记录 plan_id 和必要关联，独立维护平台世代，不复制私人 payload/state |
| secretary_request | id、work_id、generation、phase、plan_version、source_attempt、consumed 为私人协调/outbox 历史 | 新动作回执只保留 requestId、已提交的 Plan/step/attempt、业务版本及消费事实；不以旧 consumed 证明新动作已执行 |
| secretary_participant | 可缓存自己有权读取的角色历史 | work_id 映射为已核验 Plan 关联；worker_id/role 转为平台历史参与记录，不能随私库更改或删除 |
| secretary_admitted_task | 可缓存 task_id/关联 | task_id、work_id 关联、generation 转为平台单次准入；旧准入不自动变成新授权 |
| secretary_message | id、work_id、actor、input_hash、user_text、reply、created_ms、source_revision 全部个人所有 | 无新副本；已有平台 EXTERNAL_WORK 聊天/协调载荷与日志列入显式历史清理清单，不在自动流程删除 |
| delegation_lock | 无 | id/version 仅旧模式；新权限边界独立序列化 |
| delegation_security | 无 | id/legacy_auth_required 保持永久管理写入认证要求；不能随旧模式停用而清零 |
| delegation_legacy_request | 无私人正文 | id、server_boot、method、path、state、started_ms、finished_ms 保留管理入口/在途审计；新日志禁止请求正文和凭证 |
| delegation_identity | 私库只保留公开 Worker 关联和外部凭证引用 | worker_id、key_hash、generation、enabled_boot、changed_by、changed_ms 为平台安全事实；新凭证重新登记，不导出有效凭证 |
| delegation_target | 无权威副本 | target_key、generation、admitted_ms 转平台目标与接管世代；共享限额不随 Worker/私库重建 |
| delegation_grant | id/owner/worker_id/revision/state/effective_ms/expires_ms 为私人版本关联；original_text、rule_json、template_id、template_revision 留私库 | 人工另行确认最小有效动作/目标/参数约束、有效期和累计上限；authority 转为平台身份/角色校验依据；不直接执行旧 rule_json |
| delegation_history | grant_id、revision、snapshot、changed_by、changed_ms 为私人历史；混合 snapshot 按字段拆分 | 已执行动作的授权 ID/版本、可信变更人和时间在平台审计保留，不复制私人原文 |
| delegation_budget | 只读缓存 | target_key、action、limit_value、used_value、reserved_value 及其历史为平台账本；禁止导入个人计数覆盖 |
| delegation_request | id、snapshot 中私人策略/模型推理、detail 中私人决策文本及其时间关联留私库 | grant_id/revision、worker_id/generation、target_key/generation、task_id、state、created_ms/updated_ms 和必要执行 detail 转历史平台审计；旧 fingerprint 不作为新去重键；新摘要不含私人策略 |
| delegation_audit | detail 中个人推理、未提交策略及其 id/关联/actor/event/created_ms 留私库 | 同一历史 id 建立拆分关联；target_key/grant_id/request_id、可信 actor/event/created_ms 及执行事实保留；不丢原时间或责任主体，不将自由文本直接视为平台事实 |
| delegation_template | id、owner、revision、original_text、rule_json、source_grant、source_revision、created_ms 全部个人所有 | 无新副本；历史引用保持可对账，不产生能力 |
| delegation_draft | id、owner、original_text、created_ms 全部个人所有 | 无新副本 |
| delegation_template_history | template_id、revision、owner、snapshot、created_ms 全部个人所有 | 无新副本 |
| delegation_model_constraint | 可缓存授权摘要 | task_id、grant_id/revision、catalog_version、profiles_json 为实际 task start 的强制约束，转平台模型准入记录 |
| delegation_task | 可缓存结算回执 | task_id、target_key、allowed_attempt、reserved、started、settled 为平台准入/结算；不能由私人状态重置 |
| delegation_model_scope | 私人策略可引用模型偏好 | target_key、role、catalog_version、profiles_json、grant_id/revision 的有效白名单为平台已确认约束，非私人推理 |
| delegation_publication_plan | 可缓存已提交目标 | plan_id、target_key 为固定发布对象权威关联 |
| delegation_publication_attempt | 可缓存任务回执 | task_id、allowed_attempt、grant_id/revision、worker_generation、enabled_boot 为平台单次许可历史；新启动不得沿用旧 boot 启用状态 |

恢复/替换 Worker 仅复制个人数据和公开关联。相同 owner/Plan/step 的执行轮次、技术重试和仅重评各用独立账本；非 step 动作用服务器核验的稳定目标+子动作。若 V4/V5 历史无法核验、预留未结算或存在旧秘书协调任务，则新路径拒绝准入，保留人工处理。旧任务历史不能因为停用旧模式而丢失执行/评审/代理独立性约束。

#### 6.3 身份与有效能力（Work 1.2 的安全边界）

当前 Web Console 是单配置账号，并非多人账号系统。新 owner 使用持久随机 ID，由**已开启认证**的 Console 登录主体在绑定时创建；未认证的 local-console 不允许登记新个人 Worker。owner 记录绑定现有 configured username 和角色配置指纹；账号/角色配置改变即使旧 session 仍存在也不得扩权，须重新绑定/启用。不同 owner 的隔离由平台记录约束，不能假称已有多账号 UI。

人工在平台同源 session+CSRF 页面确认 owner、Worker、凭证摘要及精确能力。参考方案沿用独立高熵 bearer 凭证，平台只存 SHA-256 哈希，以常量时间比较；禁止共享 Worker token 或 capability 充当此凭证。登记默认 DISABLED，人工 enable 绑定当前 boot；Worker/平台重启和凭证轮换后均需人工明确重新启用，历史读取另有权限。凭证从专用环境变量/受限本地配置读取；不进 URL、模型提示词、导出包或日志。远程必须 HTTPS；明文 HTTP 仅在双方明确配置的回环地址开发模式使用。普通 Worker 租约协议及 X-AIW-Delegate-Key 继续保护旧 reserved identities，不复用为新能力。

能力包含 ownerId、workerId、action、精确 server targetKey、可选 step、参数约束、允许状态、最小轮次、maximumUses、effectiveAt/expiresAt、revision、targetGeneration；缺项或未知字段拒绝。人工批准上限必须展示，不从私人自然语言自动签发。现有角色分类保留：A11 为 APPROVER/ADMIN，A17 为 ADMIN，其余为 OPERATOR/ADMIN；操作时重新核验当前人类角色和 owner 绑定。另有 READ_STATE/READ_RECEIPT 只读范围，不能推导执行权或读取其他 owner 的授权清单。

私人 DENY 原文不进入平台，但其**有效禁止范围**必须在平台强制执行，避免移走 rule_json 后丢失否决语义。采用平台最小“禁止投影”：精确 action/target/step、允许状态/轮次条件、私人版本不可逆摘要、投影版本、生效时间和明确替代的旧禁止版本。它不含理由、对话或推理。适用禁止优先于任何 ALLOW；Worker 只可提交收窄/新增禁止，不能删除、替代或扩大。解除禁止或扩张上限必须由人工在平台确认。

私人 UI 更新有效策略采用先暂停目标→平台确认新收窄投影→私库原子保存策略与确认版本的顺序。任一网络/写入失败保持暂停，旧 outbox 不升级版本；重复发布同一投影只返回原结果。平台要求动作引用当前 restrictionRevision，不匹配则拒绝。恢复旧私库先对账平台当前世代与投影，不得用旧版本扩大权限。UI 在平台确认前只能显示“待同步，执行暂停”，不能宣称 DENY 已生效。用户绕过 UI 任意改私库不能改变平台投影或预算；平台不能感知未提交的文件修改。平台无法安全投影的自由表达禁止规则不得转换为可执行 ALLOW，保持暂停并要求人工澄清。

#### 6.4 请求、回执与错误契约（Work 1.2）

所有新端点在 `/api/r1/authority`，仅承担平台权限/业务事实：人工使用 `POST /owners/bind`、`POST /workers`、`POST /workers/{id}/enable|disable`、`POST /grants`、`POST /grants/{id}/revoke`、`POST /targets/{id}/takeover|return`、`POST /budgets/{id}/supplement`。固定操作由静态路由定义，竖线仅此处表示枚举，不是表达式 API。对应页面位于平台 `/ui/authority`，不载入私库。

认证路由按 method+path 闭集分流：上述人工 POST 及 `/ui/authority` 纳入 WebConsoleApiRoutes/WebConsoleSecurityInterceptor 的显式人工匹配，强制当前已配置认证、有效 session、写入 CSRF（沿用 `_csrf`）、当前角色及不可清零的 legacy_auth_required 锁存；禁止利用拦截器在 auth disabled 时的本地 ADMIN 回退。机器 `GET /state`、`GET /grants`、`POST /actions`、`GET /receipts/{requestId}`、`POST /restrictions` 进入单独 bearer 认证+owner/scope 拦截器，不创建浏览器 session、不接受 cookie 或共享 Worker token 代替身份。机器 `POST /restrictions` 仅可收窄自身 owner/目标；不得触发人工替代或解除禁止。人工查询通过 `/ui/authority` 的服务端受权读取路径渲染，不借用机器 GET 接口的 cookie 兼容。未列入 method+path 的 authority 路由默认拒绝，不能仅用 URL 前缀整根套浏览器认证或整根豁免。两类拦截器在业务进入前完成身份判定；应用首锁内仍重查当前角色、scope 和版本，不依赖 session 中缓存的角色。新增路径必须在路由注册与闭集匹配表中一起更新，否则不得开放。

机器使用 `GET /state?target=...`、`GET /grants`（仅本身份的有效摘要）、`POST /actions`、`GET /receipts/{requestId}`、`POST /restrictions`（仅收窄/暂停）；所有 owner 从认证 principal 映射，不信任 body 的 owner/actor。人工读取回执须为当前 owner 或具审计权限 ADMIN；替换 Worker 须显式获得原 owner 的最小 READ_RECEIPT 范围，失效凭证始终拒绝。不得提供跨 owner 的全表列表。

动作信封 v1：`protocolVersion`、UUID `requestId`、`grantId/grantRevision`、`restrictionRevision`、`targetId/step`、`action`、`expectedTargetGeneration`、`expectedVersion`、`expectedTaskVersion`（适用时）、`taskId/attempt`（适用时）、`parameters`、不可执行的业务证据 ID。服务器按闭集 DTO 校验参数；reason 是用户主动提交的业务理由，限 2000 字符，不得自动附带策略/对话。请求整体限 32 KiB；无 arbitrary method、SQL、shell 或反射调用字段。

去重主键为**持久 platformInstanceId + ownerId + requestId**。摘要为确定性规范 JSON 的业务字段 SHA-256：协议版本、action、服务器归一化 target/step、预期业务版本、task/attempt、parameters、证据 ID；对象键排序、UTF-8、整数、不允许浮点/重复 JSON key。grant/Worker/凭证/boot/restrictionRevision 作为审计及首次准入条件，不进入业务摘要；重发必须使用原信封，不因新授权改写业务参数。第一次 requestId 关联的 Worker/授权版本永久保留。相同键不同业务摘要返回冲突，相同摘要先返回原回执，不再次检查为可执行；查询仍必须通过当前只读身份/范围检查。

平台实例 ID 存平台 metadata，不随启动变化；恢复备份须同时恢复实例 ID、回执、预算、准入与审计至一致点。禁止在承诺不明/丢失去重历史的还原库上重新启用动作；先冻结委托并人工对账。回执及 requestId tombstone 默认无限保留；将来删除/压缩需要单独保留去重及审计约束设计，本 FD 不实现 TTL。新平台实例不是旧动作的自动重试目标，个人客户端发现实例 ID 变化立即暂停。

回执：`requestId`、`actionDigest`、`state`、`targetVersion`、`admissionId`、`taskId/attempt`、`resultRef`、`errorCode`、`createdAt/updatedAt`，无私人推理。状态为 REJECTED、APPLIED、ADMITTED、IN_PROGRESS、SUCCEEDED、FAILED、UNKNOWN；ADMITTED 仅指已持久化准入，不代表专业任务已启动/完成。已终结回执不被授权更新覆盖。请求先通过身份/大小/结构校验，再查同键回执，再在串行化边界内校验并插入最终/准入回执；业务拒绝记录 REJECTED，不消费额度。结构错误和未认证请求不创建可被攻击者占用的回执。

HTTP：401 为无效机器/会话凭证；403 为范围/当前角色不允许（不泄露其他 owner 资源）；404 为本身份不可见或不存在；409 为相同键不同业务摘要、版本/世代变化或旧路径在途冲突；422 为不可表达的动作条件/预算不足/明确业务拒绝；410 为新模式下已退役私人业务接口；202 为新 ADMITTED/IN_PROGRESS/UNKNOWN（UNKNOWN 只查询，不重新执行）；200 为已完成或既有回执。响应统一含稳定 `code`、requestId（存在时）和安全 message；不返回堆栈、凭证或完整请求。

#### 6.5 20 个固定子动作及准入矩阵（Work 1.2）

下表所有行都需要同 owner 的有效能力、当前 restriction/target 世代和现有角色；版本为调用时服务器重读并对照 expectedVersion，不能仅把当前版本偷偷传给旧服务。每行都采用共享去重和独立预算。所谓 DB 准入只对应用方法已核实无文件/Git/外部效果时成立；实际专业执行在后续 task attempt 中，不能混为动作请求事务。

| 动作 | 目标/必要参数与额外约束 | 现有入口/平台结果；副作用边界 |
| --- | --- | --- |
| A01 | Plan+step；scope、reason、当前原评审 task/version、可选独立 model grant | continueAuthorizedRepair；一轮执行+独立评审的 task/attempt 准入；预留执行轮次，结算按实际 start/history |
| A02 | Plan+step；profiles、catalogVersion、EXECUTION/REVIEW/BOTH；仅目录已批准型号，无受影响活跃任务 | configureModels 等效平台白名单；APPLIED，不在此调用模型；实际 start 再校验 |
| A03 | Plan+step+明确 task/attempt；failureCategory、sideEffectsConfirmed、minimumIntervalSeconds、reason；不允许含糊 CURRENT 作为新信封目标 | retryFailedTask；ADMITTED 的新 attempt，不能把未知效果当已失败或同意自动重放 |
| A04 | Plan+step；原成果/评审 task/version、warningConfirmed=true、reason | resumeReview；只新评审准入，不重跑执行，不借用执行轮次 |
| A05 | Plan；reason、当前 DRAFT/业务版本 | confirm；DB 创建/准入专业 task；发布 Plan 还须独立 A12_EXECUTE 固定许可 |
| A06 | Plan；targetStep、reason；不得越过原跳转 Gate | jump；DB 推进/准入；发布分支仍需单次发布许可 |
| A07 | Plan；reason | stop；记录停止及租约控制事实，不声称外部执行已撤销 |
| A08 | Plan；reason | withdraw；原撤回约束，不抹除历史 attempt |
| A09 | Plan；reason | complete；只完成符合原业务 Gate 的 Plan，不伪造专业结果或回滚文件 |
| A10 | Plan；reason | cancel；原取消/任务控制，已运行结果照实记录 |
| A11_APPROVE | approval ID；reason、审批源 task/版本 | decideApproval APPROVE；当前 APPROVER/ADMIN，历史执行/评审/代理 Worker 两两独立 |
| A11_REJECT | 同上，独立能力/计数 | decideApproval REJECT；不得由 APPROVE 能力推出拒绝权 |
| A12_CREATE | file ID、reviewTaskId、targetPath、sourceHash、reason | publication；验证已评审源/路径后仅创建固定发布 Plan，返回 Plan ID，无执行权 |
| A12_EXECUTE | 固定发布 Plan、file/review/path/hash、明确 task+next attempt | requirePublication+固定 attempt 许可；ADMITTED；后续真实文件写入/结果用 §6.6，不自动重试 |
| A13 | GLOBAL；maxParallelism、minimum、maximum、allowUnlimited、minimumIntervalSeconds、reason | updateMaxParallelism；0 明确表示无限且需单独同意，限制 0..65535；本地 DB 配置 APPLIED，不扩大 owner 全局范围 |
| A14 | 普通 Worker ID、reason | drain；DB 状态变更，排除个人委托身份；不能借此 enable 委托 |
| A15 | 普通 Worker ID、reason；activeLeases=0 | offline；DB 状态变更，活跃租约不得强退 |
| A16 | Plan；reason；原 archivable 条件、真实结果及版本 | archive；只现有平台归档记录，未核实的扩展文件归档必须单列持久意图，不在 DB 事务中执行 |
| A17_SCHEDULE | 已归档 Plan；reason、原保留窗口 | scheduleDeletion；只排期，不立即删文件；实际清理器独立执行意图/对账 |
| A17_UNDO | Plan；reason；尚在撤销窗口且删除未生效 | undoDeletion；独立能力/计数；UNKNOWN 或已开始清理不能假装撤销成功 |

读取只返回目标 Plan/Task 状态、版本、必要结果引用、允许动作/剩余额度和本 owner 回执。读取成果正文沿用原成果授权，不能因 READ_STATE 获得任意文件下载。精确 sourceHash/路径/目录版本变化必须新人工核验，不以客户端给出的字符串直接信任文件内容。

#### 6.6 事务、状态与故障恢复（Work 1.3）

全局权限首锁取代旧 delegation_lock；其后按原顺序取受托 Plan 边界、按 ID 排序的 task 锁、Plan/业务锁。人类管理写入、签发/撤权/接管、预算补充和机器准入均从此首锁进入；不能有反向持锁回调。新/旧模式切换的同目标准入也取首锁。SQLite 用已有单写事务，PostgreSQL 用固定权限行 UPDATE 行锁；不新增跨库事务或以 JVM 锁替代持久序列化。

纯 DB 动作：锁内重读身份/当前人类角色/能力、禁止投影、世代、原业务快照/历史参与/预算，按子动作调用原应用服务，同事务写消费、准入及回执；失败整笔回滚，再在独立权限事务记录无效果的 REJECTED。若连接故障导致 commit 是否成功未知，先查询原 requestId，不能先创建拒绝或再次执行。请求并发由主键唯一约束兜底，冲突方只读取胜出回执。

专业 task admission 和真正开始区分两次校验：准入时持久记录 request/target/owner、角色、task+精确 next attempt、身份与目标世代、restrictionRevision、模型白名单及预留。actual start 在平台权限首锁及同一 start 事务内重读当前身份/启用 boot/身份世代、owner 当前角色、能力版本/条件/有效期、restrictionRevision 和全部适用 DENY 投影、目标暂停/接管/世代与业务状态、模型白名单及历史独立性；任一世代/版本变化、适用禁止或暂停均使旧 admission 失效，不消费或自动续发许可。以平台确认从未 start 的记录解除对应预留，不返还已实际消耗或 UNKNOWN 占用；后续必须新候选、新权限校验及新准入，不能以原回执 APPLIED/ADMITTED 证明当前仍可执行。这些检查适用于 A01 完整轮次后续评审、A04 仅重评、A03 技术重试、A05/A06 推进及 A12 的每个固定专业 attempt；评审是整轮组成也不豁免新 DENY 或暂停。通过后才消费预留并记 start。心跳及真实完成可在撤权后记录，但不能产生新推进/新 attempt。自动 lease reclaim、修复轮次、重启均无权复用旧许可；发起者需新的有效动作和剩余额度。交回目标只恢复提交新候选的可能，不重跑任何旧 request。

外部效果只允许由带唯一 effectIntentId 的固定专业 attempt 执行。平台在允许 effect/start 前持久提交 PREPARED 意图、单次准入和预算占用；与撤权的线性化点是该 start 事务提交。撤权先提交则不允许启动；start 先提交则它是已生效动作，撤权只阻止后续动作并保留真实完成，不能承诺撤回外部效果。之后不持平台 DB 事务跨文件/Git/网络 IO。执行者以现有 lease token 和固定 attempt 上报结果，平台验证输出 hash/commit/目标引用后在独立事务结算并写终态。

| 故障窗口 | 唯一允许恢复动作 | 禁止行为 |
| --- | --- | --- |
| 私库决定/outbox 未提交 | 无平台动作；重新讨论/提交用户决定 | 把未提交本地操作称作已发送 |
| outbox 已提交，尚未发出 | 原 requestId 原业务摘要发送 | 自动生成新 requestId 绕过去重 |
| 平台 DB 准入回滚，确认无回执且无已发放 effect/start | 重发原 request；重新完整校验当前权限 | 沿用旧快照跳过权限 |
| 平台 commit/响应不明 | 查询原 request；服务端原键去重 | 先退款、生成 REJECTED 或创建新请求 |
| 平台成功，私库缓存写入失败/响应丢失 | 查询回执并原子更新私库 | 再消费/再执行 |
| PREPARED 但未到 start | 查询准入和精确 attempt 状态；撤权/接管可阻止 start | 给其他 attempt 补发旧许可 |
| 已 start，执行者失联或结果事务未提交 | IN_PROGRESS 到 UNKNOWN，保留预算占用和意图；只查询/核验 | timeout 当失败、自动重发或返还额度 |
| 完成事实已持久化，回执未更新 | 平台用同意图同 attempt 的既有完成事实补记回执；无外部调用 | 再次启动 task |
| 目标/身份世代变化或还原旧私库 | 本地暂停、先同步当前权威版本；旧请求仍只查原回执 | 自动 enable、交回或升级旧 outbox |

逐类外部核验：发布文件需固定路径、源 hash、完成 attempt 的成果证据；目标已存在同 hash 仅为对账线索，不能单独证明是谁写入。Git 类工作用固定 repository/base/result commit 和 attempt 报告，不执行“重试 Git”探测；删除清理用固定资源清单+清理意图的逐项完成证据，单独“不存在”不能证明本请求成功。停止/取消只核验平台状态和真实租约完成，不对文件回滚作推断。A16 当前是平台元数据归档；后续若实际实现包含外部效果，必须按意图协议处理，不能借名称视为纯 DB。

证据不足保持 UNKNOWN 并转人工对账；本 FD 不提供一键“重放未知结果”。人工如确认需要新的实际执行，必须先记录对旧意图的处理结论和证据、明确接受重复效果风险，再通过新的业务批准/授权和额度决定发起；不是 outbox 自动新 requestId。模型/专业执行故障并不自动解除意图，私库恢复也不能抹除它。

#### 6.7 私人 UI、安全与兼容（Work 1.4）

比较平台反向代理与独立个人 origin：反向代理会令平台访问用户机器并承载私人响应，增加 SSRF、cookie 混用与私人日志风险；采用独立个人 origin。平台只提供外链及身份/有效权限/回执 UI，不 iframe、不在平台页面 fetch localhost。参考服务只监听明确配置的 loopback 地址，严格 Host 校验；用户远程部署时自行配置 HTTPS 反向代理及固定公开 origin，未配置远程认证则拒绝非回环监听。参考 UI 无默认公用账号、无自动接受 localhost Origin。

个人 UI 有独立本地登录：每实例首次启动提供一次性控制台配对码（短期、用后作废、限次；永不打印平台凭证），验证后设该服务自己的 HttpOnly/SameSite=Strict cookie 和 CSRF token；HTTPS 时加 Secure。校验 Host、Origin/Referer 与 CSRF，拒绝跨源写入、凭证放 URL、宽松 CORS 和 WebSocket 绕过；不复制平台 session/数据库。配对码只用于个人本地 UI，不能成为平台 grant；登录不自动启用能力。所有 UI 使用现有 Console 的颜色/字体/间距和交互惯例，实际实现前读取相关样式。

平台 owner/凭证绑定由用户在两个服务分别明确操作；无浏览器身份自动转交。平台页面仅显示“打开个人秘书”，用户地址来自人工登记且仅允许 http/https（HTTP 仅回环）、无 userinfo/脚本/平台内网代理请求；点击是浏览器导航，不是平台服务器取 URL。个人前端不读取平台 cookie；其后端用专用机器凭证请求必要平台数据。私人 DOM/日志不得上传平台。

用户在实现过程中明确要求“无需兼容旧模式，保持代码干爽”。据此取消先前 legacy/personal 双模式设计：删除旧平台秘书的服务、仓储、调度器、控制器及私人页面；业务服务统一依赖 authority 的授权与受托 Plan 边界。不存在模式开关、旧服务适配器或透明转发。旧 /api/r1/secretary、/api/r1/delegation 与旧私人页面不再提供（404），由新的 /ui/authority 管理最小平台事实。这个公开 API 变更由用户明确授权，不将其描述为兼容发布。

V8/V9 历史 SQL 不改，已有数据不删、不导出或自动认领。V10 只新增平台 authority 表，并以只读存在性清单初始化 retired_data_present Gate；有旧私人数据时新委托仍不启用。正常运行只读取该平台 Gate，不依赖旧私人 schema 或运行旧流程。实际迁移与解除该执行 Gate 均不在本轮自动流程内。专业 Worker 租约协议保持原址，个人身份不得注册成专业租约 Worker。

新托管准入沿用原秘书的有界协调语义，但使用显式公开协议：人工通过 /managed-plans 确认当前登录人创建的干净 Plan 草稿、业务版本/摘要、manager 和有效期；已验证同 owner 的发布草稿也可单独确认。个人进程通过 GET /identity、GET /management-state 读取必要事实，通过 POST /coordination 提交固定源 task/attempt、业务版本/摘要、目标世代和禁止投影版本。初始确认与每个完成源只能消费一次；该协议不授予额外修复、模型或发布权限。真实专业完成在平台正常落库，后续推进等待新准入。A01/A12 整次准入中的后续评审沿用原始能力版本并在 start 重查当前约束；DRAFT 到 CONFIRMED 是固定动作的正常后置状态，以事务捕获的 post_version 和当前 DENY 校验，不要求动作生效后仍保持原 DRAFT 前置状态。

外部效果对账是显式人工操作：POST /effects/{task}/reconcile 只接受固定 attempt、无歧义的已发生/确认未发生结论和证据引用；保留原意图历史，不重放、不退款。后续真实重试仍须新的明确能力、候选、独立消费和固定 attempt，UNKNOWN 未对账时不得绕过。回执持久记录稳定 targetKey，Plan 清理后仍可在独立只读权限下对账。禁止投影只含最小键字段、条件及私有版本的加盐摘要；盐和原文仅留私库。

#### 6.8 迁移设计与执行界限（Work 1.5/5.x）

工具设计为显式 `preview`、`export`、`import`、`reconcile`、`switch`，不是 auto 启动迁移。preview 只接受操作者指定源库和 owner 映射文件，不扫描猜库、不写源库；输出 schema 版本、表/列计数、可核验 owner 分组、未映射/混合敏感记录、在途/未结算清单及阻断原因。预览 manifest 包含批次 UUID、源实例/schema、映射摘要、每表行数、规范化内容 hash、关联和时间范围、目标 owner/Worker/store 标识，无活动凭证。无法确定归属的记录只标封存，不导出给当前用户。

正式 export/import 需要用户提供源库、可用备份、owner 证明和目标私库；同 batch+摘要重复导入返回原结果，不同摘要拒绝。使用暂存及单私库事务，核对引用/数量/摘要后标记批次提交；平台回执/预算不从私人导入包重写。source→target 映射保留旧 ID，无自动启用或补发旧动作。switch 只有在旧生产者已暂停、在途清单有逐项处理、备份与回退路径经人工确认后才接受；工具缺证据即拒绝，不因用户运行了命令而推断已确认。

回退先停新路径并保存两侧数据和平台意图/账本；有任何新 start/执行或 UNKNOWN 时禁止直接恢复旧库/旧调度。只允许人工对账后决定继续新路径或单独安全恢复。清理私人历史（包括旧 EXTERNAL_WORK/attempt 正文）须独立清理计划、备份和可验证归属，绝不在本次 auto 执行。实际 G1 执行资料仍未提供；1.5仅记录已确认的工具范围和未来执行输入，不代表实际迁移已获准。

### 7. 实施前 Gates

| ID | 状态 | 决定/所需证据 | 影响 |
| --- | --- | --- | --- |
| G1 | 工具实现范围已由人工确认；实际执行保持禁用 | 用户2026-10-21明确选择仅实现迁移工具、实际迁移及切换保持禁用，归属不明记录默认封存；工具不得内置源库/owner猜测或自动启用路径 | 不阻止工具实现；实际导出/导入/切换/清理仍需未来环境证据及单独授权，本轮不执行 |
| G2 | 本轮工程设计已形成；发现已定向复核解决 | §6.2–6.6 的全列归属、最小能力/20动作、稳定去重/回执、禁止投影、任务start及UNKNOWN恢复 | 1.1–1.3设计产物完成；不代表实现或运行验证通过，完整FD仍受G1阻塞 |
| G3 | 本轮交互/安全设计已形成；发现已定向复核解决 | §6.4/6.7 的独立个人origin、独立认证、人工/机器认证闭集分流、旧入口兼容矩阵 | 1.4设计产物完成；实际旧模式切换仍受G1及额外迁移授权限制 |

这些是设计草案的实际未决项，创建 FD 不代表批准新协议或迁移策略。后续可以先推进不依赖实际数据的设计工作；不得将依赖 Gate 的实现标为低风险或已就绪。

G1执行限制：实际迁移源库、可信owner映射、在途处置与切换/回退窗口仍未提供；它们不再作为本轮工具实现的输入，不能因此猜测或执行历史迁移。导出/导入入口必须默认拒绝实际写入，切换入口不可执行；未来启用执行须另行明确确认及环境核验，不能仅因工具存在或配置了路径而视为获准。

G2/G3 的设计发现已在独立定向复核中解决，见 `reviews/FD-024-design-recheck-r3.md`；该历史报告记录当时整份 FD 为 changes-needed/INCOMPLETE，原因是 G1 未解决。后续人工决定已解决工具实现前置，但没有提供实际环境证据或豁免实际迁移执行 Gate；不能把定向复核解释为实现通过。

## Scope

包括：参考个人秘书 Worker/存储接口；V8/V9逐表逐列归属拆分；最小平台能力、幂等执行和回执；全部现有代理能力与安排/协调行为迁移；UI归属与安全接线；显式历史迁移预览/导入/切换/回退设计及现行文档。

不包括：现在运行迁移或删库、完整平台多人账号/组织系统、云同步/共享秘书私库、跨用户策略分享、生产部署、push、安装包发布、扩展新的业务代理动作或重写专业 Worker。仅把现有不满足多人隔离的假设暴露并设准入约束，不宣称已实现多人安全。

## Work items

下列项逐项一个可审阅结果。规模均以小（预计半天以内）为目标，常规难度中；1.x负责高风险设计的有界产物，其输出需评审，后续实现保持 gated。若细化后仍过大则继续拆分，不因表格估算豁免 Gate。本轮在用户确认范围后完成全部实现项；勾选表示源码与交接证据已形成，不表示测试或运行验证通过。

- [x] 1.1 V8/V9逐列数据归属及保留映射，区分私人原文和平台最小权威数据，明确私有DENY同步要求。依赖/准入：本FD，设计工作。规模/难度：小/中。产物：§6.2/6.3；独立设计评审及定向复核见Verification，不代表已迁移数据。
- [x] 1.2 冻结平台能力、20子动作及读取范围的请求/返回/角色/幂等矩阵，包含版本冲突和已准入语义。依赖/准入：1.1，设计工作。规模/难度：小/中。产物：§6.3–6.5及R3-F2修订；不是已实现API。
- [x] 1.3 冻结跨库outbox/回执、托管准入及启停/撤权/接管/重启状态；给出每个故障窗口的唯一恢复动作。依赖/准入：1.2；G2工程设计产物。规模/难度：小/中。产物：§6.4/6.6及R3-F1修订；无运行验证。
- [x] 1.4 冻结个人UI的部署/认证与旧API兼容矩阵，不转交平台session存储。依赖/准入：1.1–1.3；G3工程设计产物。规模/难度：小/中。产物：§6.4/6.7及R3-F2修订；未开放新UI或停用旧路由。
- [x] 1.5 制定owner映射输入、预览manifest及停写/在途/切换/回退方案，记录G1所需人工决定。依赖：1.1。规模/难度：小/中。2026-10-21用户确认工具实现范围，实际环境清单改为未来执行时必填输入；本轮不收集、猜测或执行实际迁移，见§6.8和G1。保留原工作项的安全目的，不取消归属核验或回退保护。
- [x] 2.1 独立参考个人Worker进程骨架、单owner绑定、私库定位及生命周期/存储接口，无平台Jdbc依赖。依赖/准入：G2关闭、1.1。规模/难度：小/中。
- [x] 2.2 私库迁移版本及个人安排/消息/上下文保存，多个实例/用户数据目录明确隔离。依赖/准入：2.1。规模/难度：小/中。
- [x] 2.3 私有代理原文、规则/版本、模板/草稿及个人待办持久化，无自动签发平台能力。依赖/准入：2.2、1.1。规模/难度：小/中。
- [x] 3.1 平台可信owner/Worker绑定和最小能力的人工签发/撤权入口，默认关闭。依赖/准入：G2关闭、1.2。规模/难度：小/中。
- 3.2 执行与回执范围父项：由3.2.1–3.2.3承担，不另计实现结果；G2关闭后再按冻结的故障边界确认规模。
- [x] 3.2.1 纯DB动作的受限执行信封、摘要及原子消费/回执，去重域独立于grant/Worker。依赖：3.1、1.3。规模小/难度中，G2未关不实施。
- [x] 3.2.2 外部副作用的持久执行意图、单次准入、UNKNOWN保留及自动重试禁止。依赖：3.2.1、逐动作故障协议冻结。规模小/难度中，故障设计高风险且gated。
- [x] 3.2.3 历史回执最小读取与对账，撤权后查询/执行分离、保留期及恢复约束。依赖：3.2.1–3.2.2。规模小/难度中。
- 3.3 A01–A04范围父项：由以下子项承担，各适配器给出角色/版本/预算/拒绝分支静态映射，不另计父结果。
- [x] 3.3.1 A01完整轮次准入及预留/结算映射。依赖：3.2子项。规模小/难度中。
- [x] 3.3.2 A02模型配置与实际start约束映射。依赖：3.2子项。规模小/难度中。
- [x] 3.3.3 A03技术重试及副作用/间隔确认映射。依赖：3.2子项。规模小/难度中。
- [x] 3.3.4 A04仅重评与原成果引用映射。依赖：3.2子项。规模小/难度中。
- 3.4 A05–A10范围父项：由以下子项承担，不另计父结果。
- [x] 3.4.1 A05/A06确认和跳转的原版本/准入映射。依赖：3.2子项。规模小/难度中。
- [x] 3.4.2 A07/A08停止和撤回的原租约控制映射。依赖：3.2子项。规模小/难度中。
- [x] 3.4.3 A09/A10人工完成及取消的真实结果/副作用边界映射。依赖：3.2子项、对应UNKNOWN协议。规模小/难度中，未知窗口未冻结时gated。
- 3.5 A11/A12范围父项：由以下子项承担，不另计父结果。
- [x] 3.5.1 A11审批/拒绝的当前角色与历史参与独立性映射。依赖：3.2子项。规模小/难度中。
- [x] 3.5.2 A12创建发布计划的独立权限/账本映射。依赖：3.2子项。规模小/难度中。
- [x] 3.5.3 A12执行发布的固定attempt许可及跨入口防绕过映射。依赖：3.5.2、发布UNKNOWN协议。规模小/难度中，副作用故障设计未冻结时gated。
- 3.6 A13–A17范围父项：由以下子项承担，不另计父结果。
- [x] 3.6.1 A13 Fleet区间、间隔及次数映射。依赖：3.2子项。规模小/难度中。
- [x] 3.6.2 A14/A15普通Worker Drain/Offline及活动租约限制映射。依赖：3.2子项。规模小/难度中。
- [x] 3.6.3 A16归档的原Gate及真实结果映射。依赖：3.2子项。规模小/难度中。
- [x] 3.6.4 A17延迟删除/撤销的独立权限/账本和原窗口映射。依赖：3.2子项。规模小/难度中。
- [x] 3.7 平台最小读取/回执隔离，其他owner或私有DB记录不可访问。依赖/准入：3.1–3.6。规模/难度：小/中。
- [x] 4.1 个人Worker本地outbox与平台回执对账，不明结果重查原ID。依赖/准入：2.3、3.2。规模/难度：小/中。
- [x] 4.2 个人定时安排/协调与平台通用受托准入接线，保留过时目标不追赶和专业任务独立性。依赖/准入：1.3、2.2、3.3–3.7、4.1。规模/难度：小/中。
- [x] 4.3 私人策略求值/模型建议迁出平台，撤权/到期/人工接管/明确交回全链路保持。依赖/准入：4.1–4.2。规模/难度：小/中。
- [x] 4.4 平台UI只维护能力/执行事实，私人UI和新入口按冻结边界接线。依赖/准入：G3关闭、3.7、4.3。规模/难度：小/中。
- 5.1 工具实现父项；由5.1.1–5.1.3分别交付，不另计父结果。工具实现获准不等于执行获准。
- [x] 5.1.1 只读源库预览、精确owner映射及归属不明封存manifest。依赖：1.1、1.5；规模小/难度中；完成条件为工具不写源库、不猜owner、不包含活动凭证，未映射记录仅封存。
- [x] 5.1.2 版本化export候选包及内容/行数/关联摘要校验，默认只在内存形成候选，实际导出保持禁用。依赖：5.1.1；规模小/难度中；完成条件为不泄露混合权限/私人字段、不默认写包、不含有效凭证。
- [x] 5.1.3 私库import规划、批次幂等与重复摘要冲突校验；实际导入及切换保持禁用。依赖：5.1.2、2.3；规模小/难度中；完成条件为可审阅导入计划、相同批次摘要不重入、不同摘要拒绝、无平台预算/权限覆盖路径。用户后续授权前不提供CLI执行绕过开关。
- [x] 5.2 移除旧秘书调度/业务写入实现和兼容模式；保留历史SQL与数据封存、实际迁移/切换/回退禁用及平台原有业务保障。依赖/准入：5.1、3.x、4.x。规模/难度：小/中；用户已明确不兼容旧模式，实际数据操作仍未授权。
- [x] 5.3 自定义秘书接入契约与中英文运维/兼容文档，稳定规范按已确认决策更新。依赖/准入：1.x–5.2。规模/难度：小/中。
- [x] 5.4 实际实现后的允许范围compile-only/静态证据与独立实现评审交接材料。依赖/准入：全部实现项完成。规模/难度：小/中；当前不运行。

以上稳定ID供后续细化，拆分时保留 lineage。1.1–1.4只勾选实际形成且经过独立设计审阅的文档产物，1.5记录真实人工范围决定；所有实现项现已形成实现及compile-only证据，待独立实现Reviewer核验；设计产物本身不能替代实现证据。交付/清理/归档属于将来的PM生命周期，不列为Worker交接前的循环依赖项。

## Acceptance

| 验收 | 可观察结果 | Work Items/预期证据 |
| --- | --- | --- |
| AC1 | 不同用户的参考秘书使用独立私库；平台无私人schema依赖；同用户可替换自定义实现 | 1.1、2.1–2.3、5.3：存储边界、部署契约 |
| AC2 | 对话、规则、模板、个人安排/决策只留私库；用户主动提交的任务与平台最小权威记录明确分开 | 1.1、2.x、4.3：字段/载荷/日志清单 |
| AC3 | Worker改私库、伪造owner或仅有共享token/capability均不能签发/扩大平台授权 | 1.2、3.1、3.7：认证调用链和拒绝路径 |
| AC4 | 相同requestId+摘要最多一份平台动作/消费；换授权/Worker不改变去重域；撤权后查询与执行分离；外部效果与DB提交之间的UNKNOWN不重放/返还/换ID，响应丢失及重启按回执对账 | 1.3、3.2.1–3.2.3、4.1：故障窗口状态表 |
| AC5 | 平台收到撤权/接管后阻断未生效请求；恢复心跳/重启不交回，已提交事实和历史消费保留 | 1.3、3.1–3.2、4.3：序列化与版本证据 |
| AC6 | A01–A17全部20子动作保留原角色、状态、三账本、专业独立性、模型start和发布attempt保障 | 1.2、3.3–3.6：逐动作映射，不以“可扩展接口”代替落地 |
| AC7 | 换秘书/授权/实例或还原私库不清零平台历史预算；G1迁移不导入新的活动凭证或自动启用 | 3.1–3.2、5.1–5.2：累计键及迁移manifest |
| AC8 | 秘书离线平台仍可人工撤权/停止并记录真实结果；未经新准入不自动继续托管Plan | 1.3、4.2–4.3：状态和原业务服务边界 |
| AC9 | 私人UI不因同机localhost获得平台session或其他owner数据；平台只暴露必要状态 | 1.4、3.7、4.4：认证/跨源/所有权设计 |
| AC10 | 归属不明记录不猜owner；旧库不删、历史不改，执行后回退不重放；切换先完成人工决定 | 1.5、5.1.1–5.1.3、5.2：预览、对账及回退拒绝条件 |

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.

最初请求仅创建FD。2026-10-21 用户随后请求 `FD-024 auto`，并确认取消原 Planner 交接及重新建立设计交接；当前授权为默认完整本地生命周期，但不包括测试、实际数据迁移、网络、发布或部署。设计未就绪时不创建实现worktree、不emit implementation-ready、不把未执行检查标为通过。

独立设计评审按 fd-workflow 的公开接口、认证和持久化风险条件执行一次有界审查，作为设计证据；有未决Gate时保持Design，不emit design-ready给Worker。完整设计冻结后，后续实现另按实际授权推进；不能以本草案代替既有FD-021/022的已实现规范。

原独立设计评审为 `reviews/FD-024-design-review.md`及同名JSON，结论 changes-needed/INCOMPLETE。F1外部副作用UNKNOWN底线和F2不依赖可换授权的去重域/历史查询边界已补入本草案，较宽实现组已拆为子项。此次 auto 补充 §6 后执行一次独立静态设计评审，见 `reviews/FD-024-design-review-r3.md`及同名 JSON，结论 changes-needed/INCOMPLETE：R3-F1 为 actual start 必须重查当前 DENY/暂停/世代；R3-F2 为新人工/机器路径需明确加入认证分流。Planner 已分别修订 §6.6、§6.4，修订后仅定向回查这两项；原报告保留为历史，未伪称原评审已通过。这段保留初次评审的历史限制；后续G1人工决定和独立定向复核已完成，真实 design-ready 来源为 FD-024-000006-design-ready。

未来实现默认仅编译与静态审查，测试/迁移/服务/故障注入须具体授权。迁移的实际owner映射、在途清单、备份、回退及跨库故障窗口必须有可审查证据，未执行的检查如实标为未验证，不能把编译或模型建议当作运行时安全通过。

本轮独立定向复核已完成：`reviews/FD-024-design-recheck-r3.md`及同名JSON确认R3-F1/R3-F2在候选设计中resolved；原报告保持不变。复核明确整份FD仍为changes-needed/INCOMPLETE，G1未豁免。1.1–1.4完成只代表设计文档产物；没有独立实现Reviewer结果、编译或实际迁移证据。

独立实现审核第 1 轮（1/3）为 **changes-requested**，报告 `reviews/FD-024-implementation-review-r1.md` 及同名 JSON；Reviewer `reviewer-fd024-r1-20261021-4c735`，来源 `FD-024-000007-implementation-ready`，比较 `c182720..5136c40932297ccac01897c9c1ea0656af90dced`。静态确认三项实现缺陷：R1-F1 V10 未继承永久人工认证锁存；R1-F2 间接发布未重查 PUBLICATION 目标接管/世代；R1-F3 人工普通重试覆盖 UNKNOWN 意图且绕过明确对账。Worker 需修正后重新交接；本轮未运行测试/编译/服务/网络/迁移，不把未执行检查当作通过。G1 按最新用户决定仍是未来实际迁移/切换的执行 Gate，未作为工具实现阻塞。上述记录属于本 FD Verification 的独立审核结果，工作项复选框不代表已通过验收。

独立实现审核第 2 轮（2/3）为 **verification-passed**，报告 `reviews/FD-024-implementation-review-r2.md` 及同名 JSON；Reviewer `reviewer-fd024-r2-20261021-90c42`，来源 `FD-024-000009-implementation-ready`，修复比较 `b25b487..d8470cd98461f3559bc82a39c254eb1aff547b23`（完整原始基线 `c182720`）。R1-F1 永久认证锁存继承、R1-F2 独立发布目标准入/启动检查、R1-F3 人工 UNKNOWN 对账及历史保留均已静态确认解决；没有新的阻断发现。结合第 1 轮完整实现审阅及两份 Worker 报告，现有源码证据支持 AC1–AC10 的既定实现范围。未重跑编译/测试/SQL/服务/网络；不声称运行安全或真实迁移/切换通过。完整本地交付和归档交由 PM 按正常门禁继续，实际迁移仍禁用。

## TODO


- 所有实现项已有源码与文档，第 2 轮独立 fd-review 已通过；由 PM 继续正常本地交付与归档门禁。
- 实际数据迁移、切换、清理、部署、网络与测试均未执行。旧私人数据由持久 Gate 封存；用户已明确取消旧模式兼容，不恢复旧调度器或添加适配层。
- 两份既有阻塞反馈分别为 resolved 和 resolved-for-tool-implementation。复用建议（Planner恢复接口、缺失反馈模板、工具/实际执行Gate分离）留待独立范围受审处理，本轮不额外修改技能或CLI。

## Sources

- 用户2026-10-21确认的方向：个人秘书独立实现与DB，代理业务属于秘书；随后明确请求创建FD，未请求实施或执行迁移。
- 后续授权：2026-10-21 请求 `FD-024 auto`，并回复 `confirm` 允许恢复 Planner 交接。当前源事件为 `FD-024-000003-design-requested`，Planner session 为 `01a15e5f-d79d-73e0-bb3b-480e76aa474c`。旧事件取消及新事件受管 force-emit 均有 PM 审计，见已解决的 `reports/FD-024-blocker-planner-owner-20261021-5e9c2a.md`；未强制标记设计或实现就绪。
- G1人工决定（2026-10-21）：用户回复“本次仅实现迁移工具，实际数据迁移与切换保持禁用；默认封存归属不明记录”。据此把实际环境资料保留为未来执行Gate，而非工具实现前置；不授权测试、实际导出/导入/模式切换或归属猜测。
- `docs/features/archive/FD-021/`：现有秘书安排/协调实现历史。
- `docs/features/archive/FD-022/FD-022_WORKER.md`：当前代理能力及认证/预算/发布保障。
- `docs/features/archive/FD-023/FD-023_API_API_R1.md`：当前API前缀与会话/机器边界。
- `src/main/resources/db/migration/V8__secretary.sql`、`V9__delegated_decisions.sql`；`secretary/application`及`secretary/jdbc`当前代码。
- `openspec/specs/secretary/spec.md`、`openspec/specs/delegated-decisions/spec.md`、`openspec/specs/worker-pull-protocol/spec.md`、`openspec/specs/control-plane-api/spec.md`。

## 实现阶段决定与证据记录

- 2026-10-21 用户明确：继续完整 FD-024；迁移部分只实现工具，不执行迁移或切换。其后明确取消旧模式兼容并要求保持代码简洁。本FD按上述指示取消双模式并移除旧实现，保留历史迁移与数据；这不是未授权范围缩减。
- Worker：01a15e5f-d79d-73e0-bb3b-480e76aa474c，源事件 FD-024-000006-design-ready。隔离坐标：parent develop，branch feature/FD-024，worktree .wt/FD-024，创建后已核对workspace记录。
- 第一次编译入口因PowerShell拆分未加引号的 Maven -D 参数而失败，未进入源码编译；脚本参数加引号后，离线Java25生产源码251文件、Python内存编译及两个浏览器JS语法编译通过。没有执行测试、运行服务/模型、网络、真实迁移或最终jar构建。后续源码修订须在交接前编译当前版本，不能沿用旧源码的通过证据。

### 本轮实现定位与验收证据

| Work Items | 实际产物 |
| --- | --- |
| 2.1–2.3 | workers/personal-secretary 的 Store 协议、身份绑定 SQLite、版本/回执历史、个人进程与私人 UI |
| 3.1–3.2 | authority 的人工身份/进程会话、最小能力、禁止投影、原子动作回执、单次准入及 UNKNOWN 对账 |
| 3.3–3.6 | AuthorityActions 的20闭集适配、AuthorityBudgets、AuthorityTaskPolicy、原业务服务与实际 start 接线 |
| 3.7 | owner/Worker/目标读取范围分离、独立回执读取、长期稳定目标引用及人类审计页面 |
| 4.1–4.3 | 私库决定/outbox原子落库、原请求对账、私人调度/策略/模型、撤销/到期/接管和重启停用 |
| 4.4 | 独立个人origin/session/CSRF、平台最小授权页、同源与机器认证闭集分流 |
| 5.1.1–5.1.3 | scripts/secretary_migration 离线只读预览、内存候选、完整性/关联/归属及幂等导入规划；写入/切换入口禁用 |
| 5.2 | 旧服务/仓储/调度/API/页面与3个旧专用profile移除；历史SQL不改、旧数据不动、无兼容模式 |
| 5.3–5.4 | 中英文操作文档、4份稳定规范、compile-only脚本与Worker双文件报告 |

参考进程每次启动使用新UUID申明会话，使旧身份世代失效；人工启用须确认当前generation和boot，不能以旧页面启用新进程。自定义实现隐瞒物理重启不可由普通凭证远程检测，不因此声称已检测任意物理重启。私人提醒单独按确认时间存储本地通知；本地取消仅阻止尚未发送的新请求，对已发送请求继续查询，不声称撤回平台效果。私有策略具有版本历史、显式到期与撤销；过期/修订/撤销后，旧待发决定不再发送，已发送未知结果仅对账。

本轮检查命令、真实结果及未验证范围统一见 reports/FD-024-implementation.md 和同名JSON；没有运行测试、HTTP、服务、模型、真实数据库迁移、并发/故障注入或最终分发构建。独立实现评审尚待完成。

**Completed:** 2026-10-21
