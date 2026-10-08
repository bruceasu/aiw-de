# FD-008 Worker 实施报告 r1

<!-- aiw-data: FD-008-implementation-r1.json -->

- FD：FD-008，Revision 4；来源事件：`FD-008-000004-work-requested`；Worker Session：`fd008-worker-20261007-63a1e2`。
- 实施提交：`882f3d4`（迁移、替换事务和 lease fencing）、`378059d`（Worker 停止确认）、`7f1b156`（Run 页面、权限入口与审计）。
- 原任务与 attempt 均保留。无租约任务在事务内取消并创建替代任务；活动任务先记录待停止请求，收到原 Worker 的 ACK 后再取消、创建并重接依赖。普通完成、失败、租约回收及暂停/取消会将待处理请求转为冲突。拒绝的 UI 请求另行记录操作者和原因。
- 目标来自启用的 Workspace registry；Git 模式在受理时解析 commit；新任务复制规定字段、前置依赖并更新 repository capability。页面沿用 Web Console 样式，展示替换历史和任务链接。操作说明已更新。

## 检查和限制

- `git diff --check`：通过；只验证文本空白和补丁格式。
- `mvn -o -q -DskipTests compile`：失败，JVM 启动时原生内存分配失败（约 2 MB）；未进入源码编译。
- 带 `MAVEN_OPTS=-Xmx256m -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=160m` 的同一 compile-only 命令：仍因原生内存不足失败（约 0.6 MB）；未进入源码编译。不再重复尝试。
- 未运行自动测试、Worker 协议测试、UI 渲染、SQLite/PostgreSQL 迁移或服务启动；这些结果均不能声称通过。既有授权仅覆盖 FD-006 的精确测试命令，不覆盖 FD-008。
- 残余风险：Java、Go、Python、JavaScript 尚未得到编译或运行验证；迁移兼容性和 ACK/发布竞争尚无运行证据。Worker 在发布开始后收到停止信号不会确认替换，请求将等待租约变化并记录冲突，以避免不确定副作用下创建替代任务。
