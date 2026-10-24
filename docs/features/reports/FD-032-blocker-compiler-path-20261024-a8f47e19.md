# FD-032 编译入口反馈（已解决）

<!-- aiw-data: FD-032-blocker-compiler-path-20261024-a8f47e19.json -->

- 阶段/角色：Worker 编译，来源事件 `FD-032-000004-design-ready`。
- 事实：当前 PowerShell 找不到 mvn，默认 JAVA_HOME 是 JDK 8；首个 Maven 命令未启动编译。未下载、安装或执行最终构建。
- 原因与恢复：从既有 FD-029 编译记录定位现成本机工具，核对 `C:/green/jdk-25.0.1+8` 和 Maven 3.9.9 入口后，临时设置 JAVA_HOME 并离线 compile，通过 291 个生产源；结束后还原原环境。用户随后提供的 `C:/green/bin/setup-jdk25.ps1` 已只读核对，未重复编译或执行其版本命令。
- 当前状态：resolved，2026-10-24；无待人类决定的问题，不需安装依赖。下一步独立静态审阅。
- 可复用改进：后续编译优先读取已知本机 JDK 环境入口，不假定当前 PATH 已配置；在实现报告保留实际 JDK/Maven 版本。共享 compile 脚本若日后提供 platform-only 参数，可避免检查其它项目，但不在本 FD 中扩大修改。
- 模板情况：要求的两份 BLOCKER_FEEDBACK_TEMPLATE 文件在仓库缺失，使用技能要求的完整字段和既有 Dual 结构；不伪造模板存在。
