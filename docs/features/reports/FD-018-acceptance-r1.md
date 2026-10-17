# FD-018 独立聚焦验收 r1

<!-- aiw-data: FD-018-acceptance-r1.json -->

结论：授权的聚焦命令通过；8 个新增场景及 1 个重用 TDD 测试均通过，0 failure/error/skipped。本报告不是全 FD 通过或交付裁决。

## 身份与独立性

- 测试会话：`/root/fd018_acceptance`，与实现 Host 分离；未修改生产代码、FD、Git 或 workflow 状态。
- 初始 oracle 基线为 `435bcbaeb2b67d1ccf66653d6708fd9c60aa75bb`，FD revision 3。先读 FD/Issue 并写 `tests/fd018/acceptance-scenarios.md` 固定 A01–A07，再读取实现与原测试以适配。
- 实际执行版本为 `9108cf4532a46894386530ed0acf2035a6e32180`，执行前工作树干净，无 dirty diff。Host 在执行前修正 Plan 删除外键并提交测试；A08 是 Host 提醒后从既有删除兼容性推导的追加回归，不声称属于先验独立场景。
- 独立预期来源：FD Acceptance 1–6、Solution D1–D4，以及 Issue 已确认规则 1–13。未采用实现返回值生成 expected；未采用 Worker 的测试结论作为 oracle。
- 访问实现材料：WorkflowRecommendationService、JdbcRecommendationRepository、RecommendationTaskPolicy、AiProfileCatalog、WorkTaskService、JdbcWorkTaskRepository、JdbcWorkerRepository、JdbcExecutionAttemptRepository、TaskExecutionService、RecommendationConsoleController、WebConsoleSecurityService、JdbcWorkflowPlanArchiveRepository、迁移 V1–V6 及测试 POM/原 TDD 测试。必要共享技能契约从 fd-test/skills 读取；根 docs/templates 指定报告模板未随当前仓库提供，采用现有 `aiw.fd.evidence.v1` 双格式封装，不虚构模板读取成功。

## 实际验证结果

| 场景 | 规则 | 实际检查 | 结果与边界 |
|---|---|---|---|
| A01 | Acceptance 1–2 / D1 | 真实迁移默认启用；关闭持久化；重构服务保留；revision CAS；审计 actor/时间/前后值；旧 revision 无额外审计 | 通过；不是跨进程重启，也未在两个旧环境变量值下分别启动 |
| A02 | Acceptance 3 / D2 | 请求冻结完整 profile/catalog-1 后改变外部 catalog；guidance/select 仍返回原模型，拒绝越界 id 和新 version | 通过；策略边界真实，catalog approved 集合用固定 mock；未覆盖完整 HTTP start 与 claim 集成 |
| A03 | Acceptance 3 | 无在线适配 Worker 拒绝且任务不落库；在线心跳新鲜但容量占满仍 READY 入队 | 通过；内存 SQLite 与真实 JDBC，未启动 Worker |
| A04 | Acceptance 4 | 关闭取消 READY，保留 CLAIMED；仍可查询；CLAIMED 撤回与新请求拒绝 | 通过；未逐一运行 BLOCKED/RUNNING/完成结果，未执行并发交错 |
| A05 | Acceptance 5 / D4 | 相同 key 幂等、不同内容冲突、旧 contextRevision 拒绝、重构服务恢复 goal/version/任务、跨 actor 隔离 | 通过；未覆盖浏览器刷新、旧结果应用按钮及 Plan 导航完整路径 |
| A06 | Acceptance 6 / D3 | TaskExecutionService + JDBC tasks/attempts/workers：CLAIMED 推荐含 stopRequested 到期 FAILED、attempt EXPIRED、lease 清除；普通任务 READY；真实 Worker 容量归零；重复恢复 0；旧 token 完成/心跳失败 | 通过；并发预算为 mock，断言两任务合计 release 两次而非验证各实际预算表；未模拟调度停机、RUNNING、complete/heartbeat 竞态或迟到 output HTTP |
| A07 | Acceptance 1 | 真实 security + 单机 MockMvc：OPERATOR POST 保存返回 403；设置与审计未变 | 通过；未挂 WebConsoleSecurityInterceptor，故不证明 CSRF 或 authentication 集成 |
| A08 | 保持既有 Plan 删除兼容 | FK 打开，真实迁移及 JDBC DELETE；绑定 Plan 可删，上下文保留、planId 置空、映射 CASCADE 删除 | 通过；覆盖外键边界，非完整 archive 定时删除服务 |
| TDD 重用 | Acceptance 3 | 原 RecommendationPolicyTest：显式未知 planner 必须抛错，不回退 | 通过；重用已有 oracle，不计新增独立保证 |

测试使用真实 SQLite 纯内存数据库，执行全部六个现有迁移；外键启用。每个测试独立连接，结束关闭连接，无磁盘数据库或外部副作用。写服务调用用真实 TransactionTemplate 包裹；未通过 Spring proxy，因此不证明注解事务配置集成。被替换对象为本场景范围外的 editor candidate validation、无依赖任务的 dependency service/repository、A02 catalog，以及 A06 concurrency budget；没有 mock 设置/上下文/任务/租约/attempt/容量状态规则。

静态断言检查：精确状态和数据库行数可识别“离线仍入队”“忙碌当离线”“重复建任务”“全量取消”“自动重派”“选择新 catalog”“跨 actor 读取”等错误。此为静态反例推演，未执行 mutation/fault injection。

## 命令、身份与预算

执行目录：`D:/03_projects/AI-tools/aiw-de/.wt/FD-018`。执行前已向 Host 报告精确命令、干净 HEAD、范围、约 10–60 秒及 target/log 副作用，Host 显式授权有限命令。

```powershell
$env:JAVA_HOME='D:/green/jdk-25.0.1+8'
& 'D:/green/apache-maven-3.9.9/bin/mvn.cmd' -o -f tests/fd018/pom.xml '-Dtest=RecommendationAcceptanceTest,RecommendationPolicyTest' test *> tests/fd018/acceptance-r1.log
```

- Maven exit 0；实际运行 9 / failures 0 / errors 0 / skipped 0；Maven 总耗时 9.490 秒，结束 `2026-10-17T23:07:10+09:00`。
- Maven 在该版本编译 223 个生产 Java 源文件和 2 个测试类；仅 test 阶段，不产发行包。离线 `-o`，无模型调用、Worker、应用服务器、外部数据库、依赖下载、网络或全仓库测试。
- 原始输出：`tests/fd018/acceptance-r1.log`；Surefire 中间报告：`tests/fd018/target/surefire-reports/`。保留 native access/Mockito 动态 attach/JDK 警告，未把警告解释成失败。
- 独立验收 effort：1 次运行，0 次重跑，0 次意外失败；不因通过继续扩测。此前 TDD effort 有一次 POM setup 失败后修正、一次有效预期 Red、一次 Green；本会话没有重演、重置或重新声明这些结果。
- 另执行定向 `Get-Content`、`rg`、`git rev-parse/status/diff --check`、`Get-FileHash` 进行静态阅读/身份检查；无测试外运行验证。初始部分资源定位读取因路径不存在失败，改按 fd-test 自带相对目录加载，不计目标行为运行。

执行测试快照 SHA256：

- RecommendationAcceptanceTest.java：`B4243CF9B1760D47C681929A9A550E842C62DD0B973BBF0D8C1C7655E1152EC6`
- RecommendationPolicyTest.java：`8765CAA73520D4CECF93140FC55E3C1CB9748FF7CAC25A0D11FB9BCDB4D18520`
- pom.xml：`E276D34923E6520F1B3C5901DA4FE5255D0BD69E0B1EEACF1745DEF9FFA61D11`

## 缺陷、未知与未运行范围

本次运行未发现失败。执行前静态发现的 context.plan_id 删除兼容问题由 Host 在上述固定版本修正，A08 已验证外键行为；不得称修复前执行过失败测试。初始化执行基线前后变化已明确披露。

未运行：PostgreSQL、真实并发/数据库锁交错、浏览器视觉与 JavaScript、真实模型/Worker、完整协议 start/claim 路径、旧环境变量 true/false 启动对照、已完成结果人工应用与 Plan 确认边界、真实各并发预算持久化及跨进程重启。以上是剩余验证范围，不是已通过或获得豁免的 Acceptance。最终独立 Reviewer 应结合代码/静态证据判断整个 FD，而不是将 9 项计数当作完整验收。
