# 术语表

**AI Worker: Digital Employee**：项目全称，正文简称 **AI Worker**，中文主题为“数字员工”。平台协调数字员工协作；单个 **Worker** 指执行进程；**控制平面（Control Plane）** 指 Java 管理服务；**Web Console** 指浏览器管理界面。

**Control Plane**：Java 应用，拥有 Lifecycle/Scheduling/Authority。

**Worker**：外部进程，声明 Capability 并 Pull Task。

**WorkTask**：DAG 中的一项持久化工作。

**TaskDependency**：Task 间前置依赖。

**Lease**：Task 当前由某 Worker 临时拥有的凭证，包括 Worker ID、Lease Token、Expire time。

**ExecutionAttempt**：一次具体执行历史；Retry 创建新 Attempt。

**Capability**：Worker 能力标签，例如 `java`、`coding`、`repo:core`。

**AutonomousRun**：一条自然语言 Requirement 的 Run-level Authority。

**Engineering Gate**：Review + Verification 后针对某个精确代码结果的工程判定。

**ApprovalRequest**：绑定精确 Gate/Revision 的 Human Authorization。

**Workspace Assignment**：Control Plane 给 Worker 的 repository/base/workspaceKey/branch 逻辑信息。

**Workspace Result**：Worker 返回的本地 resultRevision/changedFiles；新的 Worker 结果不允许 publicationRef。

**publicationRef**：历史跨机器代码交接引用；新的 Worker 不允许 Git 推送或提交这个字段。

**Worker Pull**：Worker 主动申请任务，不是 Control Plane Push 指定 Worker。

**Run Admission**：单个 AutonomousRun 的并发上限。

**Fleet Admission**：全局并发上限。

**activeSlots**：Run 层持久化并发 slot ledger。

**overcommittedSlots**：运行时收缩后允许出现的临时“现有任务超过新上限”。

**accounting delta**：容量 ledger 与真实 Task ownership 的差值，正常应为 0。
