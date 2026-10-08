# FD-004 Planner 测试授权，第 1 轮修正版

<!-- aiw-data: FD-004-test-authorization-r7-v2.json -->

## 审核结论

首次授权命令在 PowerShell → `mvn.cmd` 参数传递时被拆成未知生命周期阶段 `.test=R9WebConsoleApprovalIT`，0.28 秒退出，未进入测试。按仓库允许的一次命令拼写修正，批准精确命令：`$env:JAVA_HOME='C:\green\jdk-21.0.8'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; C:/green/apache-maven-3.9.9/bin/mvn.cmd -o '-Dit.test=R9WebConsoleApprovalIT' test-compile failsafe:integration-test failsafe:verify`。

- 工作目录：`D:/03_projects/AI-tools/aiw-dev/.wt/FD-004`。
- 绑定：来源事件 `FD-004-000007-implementation-ready`；FD revision 7；digest `b0a17fd4ee60f1d05ac3933e1a710a85d3f3ce31857ee53e307758dcf0001edb`；Tester Session `fd004-tester-20261005-ac4256a8`。
- 范围和副作用与初版授权相同：仅 `R9WebConsoleApprovalIT`，预计 1–3 分钟；工作树 `target/` 与系统临时 SQLite，离线、loopback HTTP。
- 这是同一低风险命令的一次拼写修正，不扩大测试范围；若再失败，不自动重试或换成更大范围命令。
