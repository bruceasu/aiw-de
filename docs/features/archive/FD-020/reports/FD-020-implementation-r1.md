# FD-020 实现报告 R1

<!-- aiw-data: FD-020-implementation-r1.json -->

Worker Session `fd020-worker-20261021-f8a62c`，来源 `FD-020-000005-design-ready`；父分支 develop `1625cf5`，隔离 feature/FD-020，源码至 `98227e9`，报告提交另计。

## 交付内容

三语言共同 goal-discussion 的 profile/Skill 规范源放在 workers/shared/goal-discussion，三包独立部署副本逐字节一致，提供窄同步工具。Python/Go新增角色和默认无工具API/空目录，三语言均拒绝讨论workspace assignment；Go上下文不额外加载fallback规则，TS也关闭讨论的history工具；角色能力均保留为reserved，不能通过技术能力冒充。Java OpenAI类别增加openai-api，Codex类别保留CLI/App Server，未知类别拒绝，冻结关联和调度链不变。

根据用户中途明确变更，Python删除codex-sdk支持与openai-codex依赖，新App Server适配使用标准库，每attempt独立子进程组和ephemeral thread。账号、会话/路径、cwd、审批和sandbox回执核对，RPC/帧/队列/输出有上限，宿主审批拒绝、未知交互不执行。正常/异常均在返回前终止并核对进程组和I/O，无法确认时resource_uncertain并停止slot。取消回调沿用Attempt/租约；不使用旧SDK run_operation包装。原生Windows在注册前拒绝，Unix/WSL或Agents路径及迁移要求写明。默认approval deny-all/never，旧auto-review/full-access配置拒绝；不静默迁移旧profiles/冻结任务，不跨提供方回退。

workers/README.md维护角色×语言×后端源码支持矩阵，并明确TS-only recovery-decider及advisory；各包README、环境/运维文档、工作台说明及stable goal spec同步。没有新建OpenSpec change或新数据库表。

## 实际验证

- `NODE_PATH=D:/03_projects/AI-tools/aiw-de/workers/process-typescript/node_modules node scripts/compile-worker-runtime.mjs`：两次 exit0。覆盖TS内存语法、JS语法、Python内存语法和Go离线编译；第二次因Go上下文/TS无工具修正重编，临时Go产物已由脚本删除；不做类型检查，不下载依赖。
- `JAVA_HOME=D:/green/jdk-25.0.1+8 D:/green/apache-maven-3.9.9/bin/mvn.cmd -o -DskipTests compile`：exit0，231个Java源文件，无发行包。
- 同步工具通过 Python `compile()` 做内存语法检查，未执行其main/写入或--check。
- 一次 `git diff --check develop...HEAD` exit0；同批读取stat/status/log及8份资产SHA256。profile规范源和三副本均为`2e2786f6...2c0c88d`；Skill均为`24c263e7...13d8d32`。
- 其他实际操作：定向Get-Content/rg、FD new/claim/emit、Git范围提交与wt add、规范资产的Copy-Item，未调用SDK/CLI/模型。独立设计r1/r2报告为静态证据，r1因用户变更明确不再覆盖当前范围。

未运行测试、SDK导入验证、真实App Server、假服务协议运行、租约/取消竞争、浏览器、服务、模型、网络、依赖安装、最终打包、部署或生产禁存认证。编译和静态回执检查不能证明安装CLI的实际协议兼容、进程树行为或跨平台运行。Python App Server是新适配，保留该限制；源码矩阵不等同运行认证。既有历史文档与未执行测试不当成当前运行成功。
