# FD-021 独立实现评审 R3

<!-- aiw-data: FD-021-review-r3.json -->

结论：**verification-passed**。R1-1、R1-2、R2-1 均已在当前实现解决；本轮未发现剩余阻塞或需另列的确定缺陷。此为第三轮独立静态评审，不重置前两轮失败历史，不代表运行验收或发布批准。

- Reviewer：`codex-reviewer-fd021-r3-20261021`，独立于实施 Worker。
- 已领取来源：`FD-021-000008-implementation-ready`，FD Revision 8。
- 当前实现：`dc4c802142e72e3519ea86ab764203725f2038a0`；全 FD 比较基线 `4b61b2f`，本轮差异基线 `3167fb7`。
- 业务依据：ISSUE-00008 第 10 节、当前 FD 与 `openspec/specs/secretary/spec.md`；审查范围为当前完整 FD，结合 R1 全量静态追踪和 R2/R3 修复差异。

## 原 findings 的复核

| Finding | 当前证据 | 结论 |
|---|---|---|
| R1-1：旧 Plan 普通 gate 引入反向行锁 | 普通 check/checkTask 不锁 Plan；通用创建仅对 DRAFT 尝试 UPDATE；旧 CONFIRMED Plan 的完成不再经 gate 获取新 Plan 行锁。初始确认和秘书关联仍持 Plan 锁互斥；有既有任务的草稿不可关联。专业任务需要当前 generation 准入记录及 ACTIVE 状态。 | 已解决 |
| R1-2：源报告缺失回滚协调终态 | 专业完成在源报告/attempt 缺失时保存 NEEDS_HUMAN；协调消费显式处理源 task/input/report/attempt 缺失并返回空推进，保留 consumed 及真实终态。 | 已解决 |
| R2-1：协调依赖锁与停止控制反序 | control(stop/complete)、withdraw、clearStaleControl、取消、问题答复及归档/purge 在任务或 Plan 写锁前取得同一秘书锁；requestStop/acknowledgeStop 在 tasks.lock 前只串行化，不以取消状态拒绝停止确认。afterStopAcknowledged 在调用者已持秘书锁的事务中继续。协调再锁 Plan→依赖源任务时，相关托管控制不能同时持有反向锁。 | 已解决 |

本轮实际读取了 DependencyResolver、停止确认调用链、TaskExecutionService 恢复租约路径、WorkspaceReplacement 入口、WorkflowQuestionService、取消服务、ArchiveService/Repository 及 purge scheduler。WorkspaceReplacement 的原请求限定 AUTONOMOUS_REQUIREMENT；到 Plan 的停止确认回调由新增的预先串行化入口调用。租约恢复不调用 Plan 控制或秘书写入口；没有在其 task 写锁后新增秘书锁。

## 修复边界与全 FD 结论

事实：serializePlan 仅对已有秘书关联获取全局锁；普通旧 Plan 不新增该写锁。serializeTask 只是串行化，允许取消后 CLAIMED/RUNNING 的真实停止确认与资源释放。对托管工作绕过安排的旧修改、重试、跳步、返工仍在适用 gate 拒绝，不通过该串行化 hook 放宽准入。

事实：purge 在同一秘书锁内操作，新增 SQL 只移除本秘书请求指向本待清理 Plan 专业任务的依赖边；秘书工作、请求、协调任务及其报告不删除，其他外部引用沿用旧约束。Plan 清理后，尚在运行的协调消费按既有缺失 Plan 分支转人工。新增准入表不对旧专业任务建立阻止 purge 的外键。该静态结论未冒充执行迁移或清理成功。

其余当前实现沿用 R1/R2 已审查且未变的证据：actor/CSRF、秘书独立对话及建议不安排、分钟/时区确认与错过处理、generation/CAS/唯一 phase、版本和授权复核、取消后的真实终态保留、协调与专业身份隔离、一次消费与原始报告推进、托管旧入口保护、Spring 依赖、Web Console 页面与 TypeScript 两个新增角色。没有扩展到重复安排、外部通知、自动分析或代理授权。

Worker 的 R3 compile 日志记录离线 Maven 3.9.9 / JDK 25 编译 240 个生产 Java 源文件成功，耗时 5.119 秒。Reviewer 读取日志和对应源码差异，未重跑编译。JS/TS 本轮未改；此前 node --check 只提供语法证据，不证明完整类型或 Worker 集成。

## 实际操作、假设与未验证范围

实际命令：`aiw fd show`、`aiw fd claim`、`git show --stat`、`git diff 3167fb7..dc4c802`、`git diff dc4c802 -- src/main/java`、`git rev-parse HEAD`、`Get-Content`、`Select-Object`、`rg`；使用 apply_patch 写双证据及 FD Verification。首个 show 因终端 GBK 编码不能输出 BOM 返回错误，设置 `PYTHONIOENCODING=utf-8` 后正确回读并领取；一次早期迁移文件名不存在，仅用已读实际 SQL 判断，未重复失败路径。最终静态核对 JSON、aiw-data 及 diff；正式 emit verification-passed 后回读 receipt。

假设：维持单机、当前控制台 actor 和已有专业 Worker 契约，不把静态通过扩大为多租户或上线保证。

未验证：测试、并发复现、PostgreSQL/SQLite 运行、迁移/purge 执行、服务启动、真实 Worker/模型、网络、浏览器、完整 TypeScript 类型检查与最终构建均未运行。Reviewer 未编译、未派生子代理、未修实现、未合并或归档。残余风险是这些运行集成边界尚无执行证据；按当前 FD 明确的静态/compile-only 验证政策，不另立未获授权的测试门槛。
