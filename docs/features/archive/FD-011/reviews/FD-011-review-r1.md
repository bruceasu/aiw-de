# FD-011 独立审查 r1

<!-- aiw-data: FD-011-review-r1.json -->

未发现阻塞性问题，结果为 **verification-passed**。这是静态独立审查，不代表数据库迁移、HTTP 请求或并发运行已通过。

Reviewer Session：`fd011-reviewer-20261013-7d093b`，与 Worker 分离。已核对并领取来源事件 `FD-011-000005-implementation-ready`。审查 FD revision 5；基线 `2a96a71e984e0591441c383e03a4dd1a492c2cfc`（master），实现 HEAD `02162e00d7c6fa9ac7b589dce17ec48643c314d1`。输入包括已批准 ISSUE-00001 revision 3、当前 FD、Worker r1 双证据及实际生产源码/文档 diff。

## 验收证据

1. 草稿只写计划表；confirm 才创建 WorkTask。入口受 `/ui/**` 会话与 CSRF 拦截器覆盖，写方法要求 operator，actor 取自会话；AI source 和旧 Worker token 不参与权限判定。沿用既有默认本地免认证政策，生产启用认证要求已写入文档。
2. 1–32 个步骤按前置依赖校验，仅创建所选 EXTERNAL_WORK 图；copy 复用不可变 spec 并生成新的 DRAFT。没有自动补齐自主流程。
3. Pull 先由 supports 筛选必需能力，偏好只在有效优先级相同时按匹配数降序排序；通配 Worker 匹配全部偏好。偏好不进入准入条件。
4. 构造模型检查重复/前向/缺失/自依赖、技能、指令与已有工作区规则。confirm 的状态/version CAS、任务和偏好写入均在应用事务内；运行时异常回滚，同一确认重试不重复创建任务。
5. withdraw 先以 CAS 变更计划，在事务中按 ID 锁住所有任务并重新读取状态；任一 CLAIMED/RUNNING 抛错回滚。非活动取消保留已完成结果，计划和任务控制事件保存 actor/reason。原子 READY claim 与取消条件协调竞争；未声称可中断运行任务。
6. V21 追加表，未改历史迁移或 WorkTask/Worker JSON。原有无偏好比较顺序、能力/AI 准入、并发预算、容量、租约和依赖结果路径保留。旧 TaskClaimService 构造入口仍可用。文档说明默认关闭入口、CSRF 参数、不可变草稿和升级/回滚边界。

## 实际命令与限制

执行 `aiw fd show FD-011`、`aiw fd claim --help`、`aiw fd emit --help`、exact event claim、`git diff --stat master`、`git diff master -- src/main`、文档 diff、`git status --short`、`git rev-parse HEAD master`，以及 `cat`/`sed`/`rg` 定向读取规则、Issue、模型/服务/仓库/锁与领取/依赖/安全路径。部分初始路径不存在，随后以实际仓库路径读取；未执行缺失文件中的命令。

Worker 报告记录用户授权后的 `mvn -DskipTests compile` 成功（194 源文件、9.980 秒）；Reviewer 未重跑编译。未运行测试、应用、HTTP 请求、SQLite/PostgreSQL 迁移、并发验证、格式/lint/type 检查、最终构建或网络；未查看 fd-test 报告。

JSON 持久化往返、两种数据库迁移和并发锁/回滚行为仅有静态支持，真实运行仍有残余风险。默认本地免认证为继承的控制台政策；生产须按文档开启认证。先前 compile Gate 已有 resolved 证据，不将其历史 unresolved 文本当作当前阻塞。
