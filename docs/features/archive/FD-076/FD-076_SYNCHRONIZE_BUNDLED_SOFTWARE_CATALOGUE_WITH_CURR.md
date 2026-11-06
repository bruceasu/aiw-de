# FD-076: Synchronize bundled software catalogue and require explicit factory configuration

**Status:** Complete
**Revision:** 3
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

用户报告 Spring 启动失败，SkillFiles 构造器解析内置 software-catalog.json 时被 AuthorityRepository.decode 拒绝。静态核对确认：FD-074 已将代码和 Worker Profile 的可写结果改为 DELIVERY，但打包 JSON 的 coder/integrator/repairer 仍为 WORKSPACE；两个变更过的内置 Skill 文本也未同步到目录资产。

## Options and decision

新环境的工厂设置尚未保存，但运行时下拉框首项默认显示 Codex；讨论 Worker 准备又将未配置与后端不匹配合并为同一个错误。用户确认：未配置时应默认未选择，并明确提示需要选择和保存。

同步内置资源到已确认的新契约，保留严格的解码和摘要校验。资源消费者无需恢复 WORKSPACE 兼容分支。补充直接实例化 SkillFiles/ExecutionConfigurations 的离线回归测试，覆盖编译无法发现的资源结构错误。

## Solution

从维护中的 Python Profile 同步三个角色的 resultKind/instruction；从平台 SKILL.md 同步两个已变更资产的内容、SHA-256 和内容派生版本。不改数据库或已冻结任务数据。回归测试仅加载资源与构造器，不启动 Spring、数据库、端口或模型。

## Scope

本轮小范围后续缺陷使用当前 Git 工作区；无现存 FD worktree。修复打包目录、添加资源回归用例，并修复工厂设置初始显示及讨论错误提示。不修改用户 application-sqlite.yaml 和 startup.bat，不联网、不启动真实服务。

## Work items

- [x] 1.1 同步三项角色配置和两项内置 Skill 资产，保持严格协议约束。
- [x] 1.2 添加构造器资源回归用例、编译并记录静态证据；运行用例需单独授权。
- [x] 1.3 工厂运行时默认未选择且必填；未保存时明确提示，区分未配置与后端不匹配。

## Acceptance

1. 目录内三个可写角色使用 DELIVERY，角色指令与维护中的 Profile 一致。
2. 两个变更的 Skill 资产内容与平台文件一致，新摘要与版本不复用原有内容身份。
3. 构造器资源回归用例可编译；授权运行时不访问数据库、网络或真实模型。
4. 未配置的工厂默认运行时未选择、模型为空；运行时与模型必须填写，提示保存后才生效。已保存的配置继续正常显示。
5. 未配置时返回明确的设置指引；只有已配置且后端确实不匹配时才返回不匹配提示。

## Verification

- 只读核对新环境的工厂审计记录为空，目标后端为 codex；原因是界面首项与已保存配置混淆。运行时增加空占位项并设为必填；载入未配置状态时明确清空运行时和模型，显式 URL 预填仍保留但提示未保存。已有保存设置正常回填。
- requestDiscussion 先单独返回未配置提示，再执行既有后端匹配判断；补齐中文、英文、日文文案。不自动保存、不修改授权或 Worker 启动条件。

- Review policy Self：同步静态资源到已确认契约，无新的权限、协议或数据库行为变更。
- 已对照用户异常栈、SkillFiles:21、AuthorityRepository.decode、ConfigurationContract.Method 和实际 JSON 三项旧枚举。
- 验证范围：离线 test-compile 与变更 JavaScript 语法编译检查；资源回归测试未获执行授权，不阻塞静态修复交付。
- 生产启动、数据库迁移、浏览器、模型、网络和最终制品构建均未运行。
- 已同步 coder/integrator/repairer 的 resultKind 与指令；两个 Skill 内容由平台文件读取，并生成新的内容摘要与内容派生版本，未改已冻结数据。
- 实际命令：`JAVA_HOME=C:/green/jdk-25.0.1+8`，`C:/green/apache-maven-3.9.9/bin/mvn.cmd -o -Dmaven.repo.local=D:/.m2/repository -DskipTests test-compile` 成功，54 份测试源码编译完成；新版资源已复制到 target/classes。无依赖下载。
- 回归用例仅直接构造资源消费者，不配置 datasource，不启动 Spring 容器或端口。已请求授权单次执行 `BundledSoftwareCatalogueTest`，尚未执行，不能据编译声称启动已通过。
- 修复工厂提示后，再次离线执行同一 `test-compile` 成功，321 份主源码及 54 份测试源码编译完成；`node --check src/main/resources/static/ui/agent-factory.js` 与 `node --check src/main/resources/static/ui/console-translations.js` 均成功。静态审阅表单必填校验、设置回填与错误分支；`git diff --check` 和 `git diff --cached --check` 通过。
- 剩余未验证项：实际 Spring 启动、浏览器的必填交互与语言切换，以及资源回归测试执行；均未冒充通过。用户配置与启动脚本不纳入提交。

## TODO

- 实现、自检与编译已完成，无阻塞事项；按 Self 流程归档并提交本次范围。运行测试仍需授权。

## Sources

- 用户本轮 Spring 启动异常。
- docs/features/archive/FD-074/FD-074_WORKER_OWNED_PLAN_LOCATIONS_AND_SHARED_HANDOFFS.md

**Completed:** 2026-11-06
