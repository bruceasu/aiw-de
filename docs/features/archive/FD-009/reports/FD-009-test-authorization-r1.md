# FD-009 Planner 测试授权，第 1 轮

<!-- aiw-data: FD-009-test-authorization-r1.json -->

## 审核结论

批准 Tester 在 `D:\03_projects\AI-tools\aiw-dev\.wt\FD-009` 执行一次 `node --test tests/fd009/cli-contract.test.mjs`。此授权仅适用于 FD 修订 8、事件 `FD-009-000008-implementation-ready` 和 Tester 会话 `fd009-tester-20261007-f817be`。

已静态检查测试代码。三个黑盒用例通过短时 PowerShell 子进程检查：旧包目录不存在、未指定运行时被拒绝、未知运行时被拒绝。命令离线运行，预计 15 秒内完成；测试本身不写文件、不联网、不构建产物。每个子进程设有 10 秒超时。范围仅限上述 CLI 契约，不代表两个后端的正向启动或真实服务连接已获验证。
