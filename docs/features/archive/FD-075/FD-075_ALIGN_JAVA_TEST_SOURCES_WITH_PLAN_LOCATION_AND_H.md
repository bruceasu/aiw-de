# FD-075: Align Java test sources with Plan location and handoff contracts

**Status:** Complete
**Revision:** 2
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

用户报告 FD-074 后 Java testCompile 出现 39 个错误。旧测试继续引用已移除的 Git delivery 操作、WorkspaceResultRequest 和旧完成请求构造器。上一轮仅执行 main compile，遗漏了这些测试源码的同步。

## Options and decision

按已确认的新契约更新测试，不为过时测试恢复生产 API。将平台 Git 合并测试替换为交接身份、有效租约和不可变引用测试；将旧 Workspace 集成测试替换为当前 location/handoff 完成协议单元测试；保留工厂独立评审的有效覆盖。

## Solution

仅修改测试源码。覆盖无 Git 结果完成、准确的生产者引用、缺失报告/交接拒绝、失效租约与交接不可覆盖；清理退役接口断言。执行离线 `test-compile`，不执行测试、不启动 Spring/模型/网络。通过后按既有 auto 授权完成本地交付归档。

## Scope

FD-074 的 Java 测试契约跟进。生产实现、数据库迁移与用户配置不在本次变更范围。

## Work items

- [x] 1.1 更新三个过时测试的覆盖，符合新的 Plan location 与交接契约。
- [x] 1.2 离线编译全部 Java 测试源码并记录实际结果。

## Acceptance

1. 测试源码不再引用 Git delivery 的 merge/preview/capture/enqueue 或旧 Workspace 完成请求。
2. 新用例保持报告、交接来源、租约和独立评审的行为约束；非 Git 结果可空。
3. `mvn -o -DskipTests test-compile` 成功，不通过恢复旧 API、排除测试目录或跳过测试编译掩盖错误。

## Verification

- Review policy Self：仅调整测试与本 FD 记录，生产契约不变。
- 用户提供的 39 个编译错误与当前生产接口已静态对照。
- 计划：离线 test-compile；不执行测试、最终制品构建、网络或依赖下载。
- auto worktree：.wt/FD-075，branch=feature/FD-075，parent=develop。父目录 application-sqlite.yaml 有既有修改，不属于测试契约修复。
- 已实现：退役 WorkflowGitDeliveryServiceTest 与 R5WorkspaceProtocolIT，替换为 TaskHandoffsTest 和 PlanLocationProtocolTest；工厂评审保留非 Git 交付来源、无基线及重复评审拒绝断言，删除平台合并预览断言。
- 自查：对照生产 DTO、交接记录的租约/生产者/不可变约束和完成调用顺序，未修改生产源码、Maven 测试目录或编译排除项。
- 实际执行：`JAVA_HOME=C:/green/jdk-25.0.1+8`，`C:/green/apache-maven-3.9.9/bin/mvn.cmd -o -Dmaven.repo.local=D:/.m2/repository -DskipTests test-compile` 成功，编译 321 份主源码和全部 53 份测试源码；未重试、未下载依赖。
- 未执行测试、Spring 启动、模型/Worker、数据库迁移、浏览器、网络或最终制品构建。test-compile 成功不代表测试断言已运行通过。
- `git diff --cached --check` 成功；暂存范围仅本 FD 与上述测试源码。
- 本地交付尚未完成：两次 `aiw git wt local-merge FD-075` 均在 dirty-parent 隔离索引路径报 R5WorkspaceProtocolIT.java not uptodate。父目录定向 diff 确认该文件内容无修改，只有既有 application-sqlite.yaml 改动；刷新真实索引后仍失败。
- 已静态定位安装的 aiw-git/git-wt.py：隔离索引由 read-tree 新建后 stat 缓存为零，未 refresh 即执行 read-tree -m -u。未修改工具，未手动绕过受支持交付流程。用户现已明确确认仅临时保存 application-sqlite.yaml、交付后恢复其内容和暂存状态；执行前记录 SHA-256 与暂存状态，恢复后核对。

## TODO

- 实现、自查、测试源码编译及 FD 归档已完成。用户已确认临时保存并恢复配置，待执行受支持的 squash 交付、配置恢复核对及 worktree 清理。

## Sources

- 用户本轮 Maven testCompile 错误日志。
- docs/features/archive/FD-074/FD-074_WORKER_OWNED_PLAN_LOCATIONS_AND_SHARED_HANDOFFS.md
- docs/PLAN-LOCATIONS-AND-HANDOFFS.md

**Completed:** 2026-11-06
