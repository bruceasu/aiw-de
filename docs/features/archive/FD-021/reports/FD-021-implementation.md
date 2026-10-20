# FD-021 实施报告

<!-- aiw-data: FD-021-implementation.json -->

- Worker：`codex-root-fd021-20261021`。
- 来源事件：`FD-021-000003-design-ready`，已实际领取。
- 工作区：`.wt/FD-021`，分支 `feature/FD-021`，记录父分支 `develop`。
- 范围：ISSUE-00008 第 10 节确认的 A+B、独立协调 Worker、站内反馈及用户发起分析。
- 状态：实现就绪，独立 Reviewer 尚未出具实现结论；不代表运行验收或已经交付。

## 改动与依据

新增 secretary 模块及 V8 增量表，保存带 actor 的工作、冻结安排、对话请求、协调请求与参与者。
revision 用于操作 CAS，generation 用于确认输入/安排失效；Plan 唯一关联和协调 phase 唯一键约束重复。
单机互斥行串行化秘书写入、领取与执行策略，持锁到同一事务结束。

秘书对话为独立 secretary-chat 上下文和只读角色；消息保留源工作 revision，建议不替换保存内容或授权。
首期能力为当前平台 Plan、本地提醒和真实进度查询；不创造未知外部操作能力。

一次性时间确认绑定保存内容、显示的 Plan version/hash、有效 operator 配置与明确时区/偏移。
扫描在取得锁后读取时间，超过目标分钟即要求重新确认；不恢复补跑。改期/编辑使旧安排失效。
保存失败不会返回已安排成功。取消仅记录事实并阻止后续领取/推进，在途结果仍可上报。

独立协调角色只返回 PROCEED 或 NEEDS_HUMAN；平台核对冻结状态、Plan、原报告 attempt 和
consumed CAS 后才应用原确定性推进。原开发评审及其原始报告保留。角色参与记录双向禁止
同一 Plan 的协调与专业执行/评审共用 Worker。旧非托管 Plan 保持原路径。
托管的旧 confirm/jump/retry/repair、通用 correction/指令改写/强制重排入口受集中 gate 约束。
运行中取消不伪造停止/回滚；失败/租约中断的专业工作不自动重复。用户可显式重试只读协调，
已成功应用的协调不能重复请求。关联 Plan 清理后保留秘书证据并转人工处理。

Web Console 新增 Secretary 页面及会话/CSRF API，复用现有 styles、组件与导航。
新增稳定规范和中文操作/回退文档。process-typescript 提供两个新角色及启动入口；Python/Go 未扩展。

## 已审阅静态证据

ISSUE-00008 的 Plan/来源/批准日志；FD-021 及独立设计评审初评和 ready 复核；现有
WorkflowPlanService、WorkflowStepCompletedHandler、WorkTaskService、TaskClaimService、
TaskExecutionService、CompletionDispatcher、控制台身份/CSRF、Goal 对话、迁移和 Worker profiles。
检查确定性任务创建、源报告、托管入口、事务和锁顺序、旧 Plan 兼容、SQL 表名与 UI 文本输出。
对照源规范与既有控制台页面，未用静态分析冒充实际时间调度或浏览器通过。

## 实际命令与结果

- `Get-Content` / `rg` / `Get-Command` / `Get-ChildItem`：定向读取及工具定位。早期共享文件相对路径、个别候选源码路径不存在，已转向实际目录，没有修改外部项目。
- `aiw issue capture ISSUE-00008 issue-plan --file .ai/issues/drafts/issue-00008-approved-scope.md`；`approve ... APPROVED --by user ...`；`promote ISSUE-00008`：成功，生成 FD-021。
- `aiw fd claim` / `emit design-ready`：已真实记录 Planner 和 Worker handoff。独立设计评审指出 D1/D2，设计补齐后独立聚焦复核 ready。
- scoped `git add` / `git commit`、`aiw git wt add FD-021`：首个 wt add 因批准命令生成的 decision-log 尚未提交而拒绝；提交该本轮审计文件后重试成功。workspace.json 四个坐标已核对，未改无关内容。
- 初次 `mvn -o -DskipTests compile`：PATH 无 mvn，未进入编译。
- 修正本机入口后：`$env:JAVA_HOME='C:/green/jdk-25.0.1+8'; & 'C:/green/apache-maven-3.9.9/bin/mvn.cmd' -o -DskipTests compile` 通过，240 个生产 Java 源文件。补充真实时间读取、协调重试限制及 Plan 删除处理等相关源码修复后重新 compile，通过；最后一次日志为同目录 FD-021-compile.log。Maven/JDK 原生访问与弃用警告不影响退出 0。
- `node --check workers/process-typescript/src/profiles.ts`、`node --check workers/process-typescript/start-all.mjs`、`node --check src/main/resources/static/ui/secretary.js` 均退出 0；JS 后续修改后仅对该文件再检查，退出 0。
- `git diff --check`：退出 0。没有运行验证脚本、格式化、linter 或完整 TypeScript 类型检查。

## 未运行与剩余风险

未运行测试、数据库迁移、服务、Worker、模型、网络、浏览器、停机/休眠恢复、并发压力、最终制品构建、发布或部署。
源码通过编译与语法检查不能证明跨 SQLite/PostgreSQL 的实际事务、时区恢复、模板渲染和 Worker 集成成功。
这些为明确保留的运行验证限制，不伪造通过证据；独立实现评审仍须检查实际代码。

新增表仅向前迁移，不回填历史归属。升级需备份；回退先停用秘书扫描与 Worker、处理在途任务并保留表和证据。
无认证仍共享 local-console；不宣称租户隔离或代理授权。单机全局互斥行限制吞吐，多节点不在首期承诺内。

本轮尚无未解决的 blocker feedback 记录；工具入口和 decision-log 工作区 Gate 已实证解除。
独立 Reviewer 通过后继续本地 squash、CLI 清理及归档，不 push 或发布。
