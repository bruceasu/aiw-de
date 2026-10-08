# FD-009 Planner 测试授权，第 2 轮 B

<!-- aiw-data: FD-009-test-authorization-r3.json -->

批准 Tester 在 FD-009 worktree 根目录执行一次 `$env:AIW_AGENT_RUNTIME='openai-agents'; & .\workers\process-typescript\scripts\run-role.ps1 requirements codex-sdk`，仅验证已有 Agents 环境值与 Codex 参数冲突时角色启动脚本在 Node 启动前拒绝。绑定实现事件 `FD-009-000012-implementation-ready`、FD 修订 12、Tester Session `fd009-tester-r2-20261007-95171d`。

已静态检查脚本的公开入口：冲突分支向 stderr 输出并以 2 退出，先于环境改写与 Node 调用。命令离线且只影响临时 PowerShell 进程的环境，预计 2 秒内完成；不写文件、不联网、不构建。此授权只覆盖这一方向的脚本行为。
