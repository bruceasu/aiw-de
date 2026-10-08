# FD-006 Worker 实施报告 r2

<!-- aiw-data: FD-006-implementation-r2.json -->

**Worker Session:** `fd006-worker-20261007-r2-6da219`  
**Source event:** `FD-006-000011-test-rejected`  
**FD revision at claim:** 11

## 修复

独立 Tester 的 DIRECT Workspace 用例在记录 `workspace-ready` 时抛出 `EVIDENCE_FIELD_FORBIDDEN`。`WorkspaceSession.materialize` 记录 `repositoryRef`，而 `EvidenceRecorder.metadata` 的允许字段列表缺少它；该异常发生在目录写入前。已将 `repositoryRef` 加入证据元数据白名单，保留原有 workspace-ready 事件内容与字段长度限制。上一轮完整实施范围见 `FD-006-implementation-r1.md`。

## 静态证据与命令

- 已对照 Tester 的失败堆栈与 `workers/runtime-core/workspace.mjs`、`evidence.mjs` 的调用和字段校验路径。
- `node --check workers/runtime-core/evidence.mjs` 在 FD-006 工作树退出码 0。此次没有运行行为测试；Tester 需按新 FD 版本重新申请授权并复测。
- 上一轮 Java 离线 compile-only 已通过，本轮未改 Java 文件，未重新编译。

## 残余风险

此修复只消除证据字段拒绝，DIRECT 模式是否完整执行仍须独立复测。其他 31 个验收场景、分支覆盖率、数据库迁移、UI、Git Clone、并发和文件保护缺少运行证据；上一轮报告中的跨文件系统与数据库非原子风险仍在。
