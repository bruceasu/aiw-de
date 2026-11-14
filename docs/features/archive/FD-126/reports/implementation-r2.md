# FD-126 Worker 修复报告

<!-- aiw-data: implementation-r2.json -->

- **来源事件：** `FD-126-000007-changes-requested`

## 修复

1. 启动状态映射现在明确说明缺少 Codex App Server 时如何配置 `AIW_CODEX_PATH`，并提示可使用已配置的外部 Worker；平台不会自动安装。
2. Windows 命令专用的临时 Codex 配置显式启用 `windows.sandbox = "unelevated"`。配置校验要求这个设置存在；命令权限或沙箱配置不被接受时失败关闭，不回退到无沙箱执行。
3. 执行指南将 cgroup 创建和清理限定为 Linux/WSL，并说明 Windows 使用 Job Object 结束进程树、确认活动进程数为零。

## 验证

- `python -m py_compile workers/process-python/guarded_command.py workers/process-python/guarded_app_server.py`：通过。
- 离线 Maven `-DskipTests compile`：通过，编译 367 个 Java 源文件。首次调用使用了 worktree 内不存在的 settings 文件路径，未开始编译；改用主仓库 settings 路径后通过。
- 未运行测试或启动 Windows Codex App Server。实际 Windows 沙箱和命令边界仍未做运行验证。

请求对修复和 Windows 沙箱显式启用策略进行独立复审。
