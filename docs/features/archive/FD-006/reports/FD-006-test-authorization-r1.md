# FD-006 Planner 测试授权，第 1 轮

<!-- aiw-data: FD-006-test-authorization-r1.json -->

## 审核结论

批准 Tester 会话 `fd006-tester-20261007-4c865139` 在 `D:\03_projects\AI-tools\aiw-dev\.wt\FD-006` 执行一次 `node --test tests/fd006/direct-workspace.test.mjs`。授权仅适用于 FD-006 revision 9、digest `50f9e057844a7f9334c21b3afdbbf7cc176f5204cbff6a91bbd502e9e4e1c596` 和 `FD-006-000009-implementation-ready`。

已审查测试文件及其导入的 `workers/runtime-core` 的 Workspace、Attempt、Evidence 代码。测试只调用 DIRECT Workspace 公开接口，预计 5–20 秒；在系统临时目录创建测试自有目录与 evidence，结束后删除该临时目录，不启动服务、不访问网络、不修改仓库内用户文件。失败或资源未稳定时会保留测试自有目录以供调查。风险为本机临时目录可能因失败而残留；范围可控，按 Planner 低风险授权。
