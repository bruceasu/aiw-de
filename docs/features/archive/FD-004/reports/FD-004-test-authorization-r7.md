# FD-004 Planner 测试授权，第 1 轮

<!-- aiw-data: FD-004-test-authorization-r7.json -->

## 审核结论

批准一条精确命令：`$env:JAVA_HOME='C:\green\jdk-21.0.8'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; C:/green/apache-maven-3.9.9/bin/mvn.cmd -o -Dit.test=R9WebConsoleApprovalIT test-compile failsafe:integration-test failsafe:verify`。

- 工作目录：`D:/03_projects/AI-tools/aiw-dev/.wt/FD-004`。
- 绑定：来源事件 `FD-004-000007-implementation-ready`；FD revision 7；digest `b0a17fd4ee60f1d05ac3933e1a710a85d3f3ce31857ee53e307758dcf0001edb`；Tester Session `fd004-tester-20261005-ac4256a8`。
- 范围：先编译测试类，再仅运行 `R9WebConsoleApprovalIT` 既有黑盒 HTTP 集成用例；预计 1–3 分钟。该旧用例只能证明基本审批提交，不代表 FD-004 全部验收场景。
- 已检查调用代码：`src/test/java/me/asu/aidev/webconsole/R9WebConsoleApprovalIT.java` 使用随机本地端口、系统临时 SQLite、内存 HTTP 客户端和 Spring 测试上下文；未见外部网络、真实业务库、权限提升或工作区外业务数据写入。
- 副作用：工作树 `target/` 下编译与测试产物，以及系统临时目录 SQLite 文件；命令 `-o` 离线。授权依据是聚焦、可检查且副作用限于临时路径。
- 仅此命令获得 Planner 低风险授权；任何扩展范围或变更命令须重新审查。本授权不声明测试通过，也不批准浏览器、外部网络或最终构建。
