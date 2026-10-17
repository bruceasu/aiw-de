# FD-018: AI 推荐功能的运行时配置与模型调用职责边界

**Status:** Open
**Revision:** 3
**Priority:** Medium
**Evidence policy:** Dual

## Problem

ISSUE-00005 已获用户批准并通过 CLI 晋升。本 FD 交付推荐的系统配置与可靠任务体验，完整需求基线见 Issue。当前推荐依赖启动开关和 EXTERNAL_WORK policy；页面只保存内存请求；租约到期默认重新排队。管理员无法在系统中统一调整推荐，用户刷新后丢失入口，失联还可能触发未经确认的重复模型调用。

## Options and decision

沿用 Worker 模型调用路径。Control Plane 直接调用或双路径增加凭据与费用治理职责，且与已批准方案 A 不符，不采用。

采用数据库中的单例系统设置、逐请求冻结配置和服务端请求关联；不以浏览器偏好或旧环境变量作为设置来源。新增独立表而不更改既有任务字段，保持普通业务任务兼容。

用户在 2026-10-17 明确：planner 选择现有 AI Profile（Runtime、模型、强度的配置组合），不是指定 Worker。

## Solution

### 已有边界与设计约束

- 管理配置在 `/ui/system` 展示，保存使用 `requireAdmin` 和现有 CSRF 边界；推荐创建及结果查询沿用 operator 和 actor 所有权校验。
- 无设置记录时启用；彻底删除 `AIW_FLEXIBLE_WORKFLOW_RECOMMENDATIONS_ENABLED` 控制及首次迁移用途。
- 配置记录 enabled、planner、model、effort、revision、操作者与时间；变更审计保留前后配置。请求冻结有效配置，后续编辑不会改变已提交任务。
- 配置与入队在事务中串行化。禁用拒绝新请求并以状态条件取消 READY/BLOCKED 推荐；CLAIMED/RUNNING 继续。撤回与领取由已有任务状态条件裁决，禁止先读状态后无条件写入。
- Worker 就绪判断必须包括心跳、ACTIVE、分析角色与所需 Runtime；不能用 `findAvailable()` 单独判断在线，因为它排除忙碌 Worker。在线且适配但忙碌可排队，全部离线拒绝请求。
- 显式 planner/model/effort 不得静默回退。需使领取、Worker 的 guidance 与 start 校验一致；无显式项才沿用 Worker 默认。现有普通 Plan Step 的偏好回退不在本 FD 中更改。
- 新增服务端推荐请求记录，关联 actor、编辑上下文/Plan、请求 key、inputVersion、goal、任务 ID 和冻结配置。恢复接口仅返回当前操作者的相应上下文；浏览器 URL 只存上下文标识，刷新后从服务端恢复。新 Plan 编辑上下文与已保存 Plan 的关联不能只依赖 localStorage。
- 推荐租约到期后终止为 FAILED，不回到 READY。沿用现有 60 秒租约与恢复调度周期作为有限等待；有效心跳继续续租。原 lease/token 作废后迟到结果被既有所有权与状态条件拒绝，手动重试创建新 request key。对外说明原模型调用可能尚未结束及重复费用风险。
- 配置关闭不阻止历史结果查询及人工应用。仅 READY/BLOCKED 显示撤回，CLAIMED/RUNNING/结束均隐藏；新建结果始终不自动确认或执行 Plan。

### 数据演进与兼容

新增 Flyway 迁移，在 SQLite 与 PostgreSQL 使用现有可移植 SQL 类型。不将旧开关值迁入数据库。旧推荐任务和普通任务保留读取及所有权语义；新关联字段对旧任务缺失时不得伪造恢复关联。部署回滚保留新增表和历史审计，不自动 drop 数据；旧版本只读其原有字段。新增默认启用行为是用户确认的兼容变化，管理员应在升级后检查系统设置。

### 设计审阅 D1–D4 的落实

- D1：迁移创建固定 id=1 的设置行并插入 enabled=true 默认值，避免缺行锁。创建请求及保存设置都先执行该行的无值更改 UPDATE 获取数据库写锁；保存使用 expectedRevision CAS。其后仅创建请求取得 context 行锁并写 task，请求保存为 Plan 时 context→Plan；关闭 settings→task。领取沿用 admission→worker→task，不获取 settings/context，因此没有反向锁依赖。关闭以 cancelInactive 的状态 CAS 为线性化点：领取先赢则 CLAIMED 继续，取消先赢则不可领取。SQLite UPDATE 串行化写者，PostgreSQL UPDATE 锁行。保存设置与请求入队不能穿越彼此提交顺序。
- D2：planner 可空；非空必须为 EXTERNAL_WORK policy 中的 profile id。model/effort 可空，非空是对 approved profile 的精确筛选条件；与指定 planner 不符时拒绝，无覆盖、不创建新模型组合。未指定 planner 时 Worker 在剩余 approved profiles 中选择；全部空且未配置 policy 时使用 Worker 本地默认。冻结 JSON 存 catalog version、完整 profile 内容及候选集合（不是启动后再次按 id 解析）；Protocol guidance 只提供该冻结集合；start 按冻结 id/version/runtime 校验并写 attempt 选型审计，不按当前 catalog 重新解释。统一限定现有三语言支持的 openai provider、已知 runtime；openai-api 禁 fast 且 effort 仅 none/minimal/low/medium/high，codex-sdk/cli 接受已支持 effort。领取共用同一 runtime 筛选，旧 Worker 无须理解新增字段，因为沿用既有 aiGuidance 且无 aiPreferences 回退字段。没有适配集合时给出配置原因。后端拒绝越出冻结集合的 start。外部 provider 拒绝模型时任务失败，不自动回退。
- D3：仅 workflow-recommendation subject 的过期任务用原 taskId/worker/token/expiry/CLAIMED-or-RUNNING CAS 终结 FAILED，清空 lease；同一恢复事务将 attempt 转 EXPIRED、释放各并发预算及 Worker capacity 一次，并记录 RECOMMENDATION_LEASE_EXPIRED。不受 stopRequested 永久阻塞；普通任务沿用 requeueExpired。heartbeat 在到期前获胜可延期，complete 在有效 lease 下获胜则不进入恢复；恢复先赢则迟到 heartbeat/complete/output 由原状态条件拒绝，不生成可应用结果。60 秒租约加默认 5 秒调度周期是健康调度下的等待，非系统停机或积压时的严格墙钟上界。
- D4：新增 actor-owned 编辑上下文（UUID、goal、inputVersion、revision、可空 planId）；POST contexts 创建空上下文，GET contexts/id 返回上下文和最近请求 Result。URL `context` 参数保存上下文 ID，服务器另列出 actor 的近期上下文供离开后导航恢复。推荐请求额外含 contextId/contextRevision；settings→context 锁后，校验 expected revision，增加服务端 revision 并保存本次 goal/inputVersion。requestKey 重复先核对全部原始关联返回原结果，不修改上下文；不同内容冲突拒绝。多标签页同 revision 的新请求只有一方成功，另一方提示刷新。刷新恢复最后提交的 goal/inputVersion；未提交的浏览器编辑不承诺恢复，编辑后在本地比较 goal/version 阻止旧结果应用。保存草稿新增上下文保存入口，在同事务创建 Plan 后绑定，context 只能绑定一次；Plan detail 展示恢复分析链接。历史 WR 无 context 时仍可凭原任务 ID及所有权查看，不伪造上下文。查询不因 disabled 拒绝。

具体结构为 `workflow_recommendation_settings`、append-only `workflow_recommendation_audit`、`workflow_recommendation_context`、`workflow_recommendation_request`；requestKey/taskId 唯一，JSON 保存冻结 guidance。所有恢复查询先校验 actor。无新增依赖或 Worker wire 字段。

## Scope

包含系统设置、持久化与审计、推荐就绪原因、配置快照与严格匹配、排队取消、失联失败、刷新/导航恢复、撤回按钮和相关文档及聚焦测试。

不包含 Control Plane 模型调用、通用模型管理、自动启动 Worker、Plan 自动审批/执行、既有业务 Step 偏好规则改造、部署或真实模型付费调用。Issue 原文“当前不改代码”是当时 discovery 阶段限制；本轮用户已明确授权新 FD auto with test 的实现与本地交付。

## Work items

- [x] 1.1 完成 planner 与模型配置契约及独立设计审阅。小/中；无依赖；标准：接口、数据和筛选决策完整且无实质未决项。
- [ ] 1.2 新增系统设置与审计持久化。小/中；依赖 1.1；标准：默认启用、版本更新、管理员保存、审计及兼容说明。
- [ ] 1.3 实现就绪检查与请求冻结配置。小/中；依赖 1.2；标准：离线拒绝、忙碌排队、无静默回退、有效配置可追溯。
- [ ] 1.4 实现关闭取消与失联失败。小/中；依赖 1.3；标准：状态条件防竞态、运行继续、失联不自动重派、迟到结果拒绝。
- [ ] 1.5 服务端关联与恢复查询。小/中；依赖 1.3；标准：同一上下文恢复请求状态及结果，跨 actor 隔离，旧任务兼容。
- [ ] 1.6 系统与编辑器 UI。小/中；依赖 1.2–1.5；标准：复用现有样式、不可用原因、恢复入口、仅排队撤回、人工应用。
- [ ] 1.7 聚焦验证、文档与独立验收测试。小/中；依赖 1.2–1.6；标准：真实结果与执行范围、预算及未验证风险写入证据。
- [ ] 1.8 独立实现评审与本地交付归档。小/中；依赖 1.7；标准：Reviewer 当前版本通过，合并主分支并由 CLI 归档。

## Acceptance

Issue 的 13 条已确认规则均为验收基线，不能因可选测试未执行而豁免。

1. 管理员保存开关与 planner/model/effort 无需重启；重启/刷新保留；非管理员不能修改；审计包含操作者、时间和前后值。
2. 无持久化值默认启用，旧环境变量 true/false 都不影响。
3. 页面区分禁用、配置不匹配、无在线分析 Worker；在线且忙碌仍可入队。显式不支持配置说明原因且不回退。
4. 关闭阻止新任务、取消排队；运行中继续并可查阅和手动应用结果。撤回仅对排队任务可见且服务端同样限制。
5. 刷新、离开再打开相同编辑上下文/Plan 可恢复状态和结果，不取消任务，不显示他人请求；过期 goal/version 不能误应用。
6. 失联等待租约到期后失败，不自动重派，拒绝旧 lease 的迟到完成；手动重试与原请求可区分。
7. 推荐只生成候选，原 Plan 确认、修复授权及独立评审边界保持。

## Verification

用户明确选择 auto with test：确定性核心规则采用一轮适当的 TDD Red/Green；实现后独立 fd-test 会话先从本 FD/Issue 推导场景，再适配测试设施，最后独立 Reviewer。

- 计划 TDD：现有有效 AI policy 加在线忙碌 Worker 时默认允许推荐（旧服务 enabled=false 返回 false，作为可运行 target Red）；同一断言在新实现 Green。根 tests/fd018/pom.xml 继承项目 POM，仅选择 tests/fd018/java；命令 `D:/green/apache-maven-3.9.9/bin/mvn.cmd -o -f tests/fd018/pom.xml -Dtest=RecommendationPolicyTest test`，工作目录为已记录 worktree，JAVA_HOME 使用现有 D:/green/jdk-25.0.1+8。Red/Green 各一次，相关修复后最多一次意外失败重跑；target/ 为可删除中间物，不产出发行包。
- 独立验收命令仍为该测试 POM 的限定测试集合，由独立 fd-test 先写场景和精确选择并记录 HEAD/新增测试 diff；不得启动全仓库测试。编译命令为同一 Maven 安装的 `-o -DskipTests compile`。
- 测试放根 `tests/fd018/`，复用 JUnit/Mockito 与已有 Maven 依赖，不下载依赖、不调用模型、不启动真实 Worker、不连接外部数据库。
- 授权范围：一个聚焦 TDD Red/Green 周期、一个独立验收命令；意外失败仅在相关修复后一次重跑，耗尽转静态诊断，跨会话保留台账。不得扩大为全仓库测试或构建。
- Java 使用最窄离线 Maven compile；Go/其他 Runtime 如需修改，先核对本地约束和必要编译命令，不自动扩大测试。
- 静态核对配置、事务锁顺序、迁移、HTTP 权限、Worker 指导与服务端 start 校验、恢复及按钮状态路径。独立设计与实现评审分别提供双格式报告。
- 真实模型、浏览器视觉/交互和 PostgreSQL 运行验证不在本轮命令预算；明确报告未验证，不以静态检查冒充通过。

## TODO

planner 含义已解决，独立设计 r2 推荐 ready。按 1.2–1.8 继续隔离实现、测试、评审与本地交付。

## Sources

- Issue: ISSUE-00005
- `docs/issues/ISSUE-00005/issue-plan.md`：已确认并批准的用户规则。
- `WorkflowRecommendationService`、`WorkflowEditorController`、`workflow-editor.js`：现有创建/查询/撤回及页面行为。
- `AiProfileCatalog`、`TaskClaimService`、`WorkerProtocolV1Controller`、`TaskExecutionService`：AI policy、领取、指导与租约恢复。
- `JdbcWorkTaskRepository`、`JdbcWorkerRepository`、`WorkerLiveness`：状态条件、在线/容量区别与 90 秒心跳窗口。
