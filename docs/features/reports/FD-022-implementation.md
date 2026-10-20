# FD-022 实现报告

<!-- aiw-data: FD-022-implementation.json -->

- Worker session：`01a15a47-fc32-7362-ac13-f660487b7632`
- source event：`FD-022-000003-design-ready`（已 claim）
- 实现基线：`develop` 的计划提交 `375ee82`；实现至 `2f10fcd`。
- 工作区：`.wt/FD-022`，分支 `feature/FD-022`，记录父分支 `develop`。
- 结论：1.1–5.5 已实现并完成允许的编译；请求独立实现评审，5.6 从 Worker 实现项取消，转为 PM 生命周期待办，尚待评审和本地交付。

## 已实现的事实及静态证据

1. 身份与规则（1.2–1.4）：V9 增量表、专用凭证摘要、普通 Worker 协议对保留身份的额外认证、重注册关闭、启动 nonce、显式启用、结构化授权版本、原文、到期、禁止优先、显式替换及当前人工权限复核。主要代码为 DelegationIdentityService、DelegationRule、DelegationGrantService、DelegateIdentityAdvice/Mvc。
2. 并发与计数（1.5–1.8）：DelegationBoundary 的首锁与目标 generation，V4 pending/未结算工作互斥纳入、历史基线、三类独立累计账本、幂等候选、冻结快照、失败另事务保留 HUMAN，以及人工接管/明确交回。主要代码为 DelegationAdmission、Budgets、Requests 和各原业务入口的边界钩子。
3. 托管与动作（1.9、2.1–4.4）：DelegationActions 覆盖 A01–A17 及审批/拒绝、创建/执行发布、删除/撤销的独立子动作；调用原业务服务，保留状态条件。模型 profile 白名单在领取 guidance 与实际 start 检查。发布验证文件、哈希、PASS 评审及规范目标，确认/续修/重试需要额外 EXECUTE 授权。SecretaryCoordinator.delegatedAdmission 更新托管快照并检查角色独立性。两个 legacyCompletion 仅包裹原固定 continuation。
4. 真实 Worker 链路（5.1）：decision-delegate profile、只读提示词、定向 EXTERNAL_WORK 候选、专用 key 传输且模型启动前清除环境 key；完成上报与业务处理分事务。Scheduler 负责校验候选和轮次结算，身份/授权默认关闭。
5. 人工界面（5.2–5.4）：自然语言只生成草稿；授权中心展示结构化规则、版本、可用原因、账本、待办与审计，提供启停/接管/交回；Plan/Worker/Secretary 导航。模板留存不可变来源版本，复制时清空目标、Worker、到期，不复制凭证/启用/历史消耗。
6. 认证兼容（用户已确认）：首次启用持久保护旧管理写入，人工 session、CSRF、动作角色和可信操作者字段；机器租约白名单独立。设计增量评审 AUTH1–AUTH3 后补充控制台 fail-closed、context-path 归一、数据库在途请求激活屏障与人工确认原进程停止后的遗留清理。报告 `FD-022-design-review-auth` 是历史 changes-needed，修复是否充分由本次实现 Reviewer 判断。
7. 文档（5.5）：`docs/current-zh/DELEGATED-DECISIONS.md` 及 delegated-decisions/secretary 稳定规范。FD Acceptance 表保留 Issue 全部 30 项到 Work Item 映射。

## 实际执行的检查

- Java：首次 `mvn` 入口不在 PATH，未启动编译。定位本地工具后，以 `JAVA_HOME=D:/green/jdk-25.0.1+8` 执行 `D:/green/apache-maven-3.9.9/bin/mvn.cmd -o -DskipTests=true -Dmaven.test.skip=true compile`，259 个生产源文件编译成功，无网络或依赖下载，无最终 jar。
- TypeScript：设置 `AIW_TYPESCRIPT_COMPILER` 为主工作区已有 TypeScript 包，执行 `node workers/process-typescript/scripts/compile.mjs`，内存语法编译通过；此检查不验证依赖类型。
- JavaScript：`node --check src/main/resources/static/ui/delegation.js` 通过；首次 runtime-core 路径拼写错误未找到模块，修正为 `node --check workers/process-typescript/runtime-core/attempt.mjs` 后通过。
- 最终静态命令：`git diff --check develop` 无错误；检查 diff stat 和工作区状态，源实现提交后无未提交文件。差异涉及本 FD 的 59 个文件。

未运行测试、lint、type-check、最终构建、数据库迁移、HTTP 请求、服务启动、网络、push、部署或发布。未把静态逻辑或语法编译描述成运行时验收通过。

## 假设与未验证行为

假设部署保留原数据库事务及 CAS 语义，人工认证配置和批准模型目录有效，管理员对“原进程已停止”的确认真实。专用凭证是进程配置秘密；仅有普通 capability 不获得代理权限。

未验证：Spring 实际启动装配、V9 在真实 SQLite/PostgreSQL 的迁移、首启并发和多实例恢复、真实 Worker/模型建议、浏览器操作、旧客户端兼容及发布/取消文件系统副作用。公开管理 API 的收紧是用户选择的兼容变更，须在实际上线前调整客户端；本次仅本地交付。以上风险不被编译消除。
