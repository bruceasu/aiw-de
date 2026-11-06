# FD-074: Worker-owned plan locations and shared handoffs

**Status:** Complete
**Revision:** 5
**Review policy:** Independent
**Priority:** High
**Evidence policy:** Markdown

## Problem

Control Plane 将 Workspace 注册、本机目录、Git 基线和分支管理作为执行前提，阻碍空目录、非 Git 和多仓库任务。用户确认以全新契约实现，无历史兼容要求。

## Options and decision

采用 Plan 级可选 location。提供 location 时平台不检查目录/Git；未提供时平台创建 Plan 专用临时目录。Worker 自主准备环境。单仓库通常创建 Plan 专用 worktree，多仓库目录不逐仓库创建 worktree。相同目录只有独立 worktree 隔离后可并发，否则 Worker 串行协调；不同目录可并发。

## Solution

- 平台分配并持久记录 Plan location，仅清理平台拥有的临时目录。Plan 完成且所有执行停止后才允许清理；失败、暂停、重试保留现场。用户目录不自动 reset。取消先停止执行。
- 领取响应包含 location/Plan 身份；仓库与版本是可选任务信息，不再是通用执行前提。保留租约、停止确认、权限、预算和冻结配置约束。
- Worker 写共享 location 的 `.ai/handoffs/<task-key>/<attempt-id>/manifest.json`、`report.md`，完成接口提交引用/摘要。平台只记录交接元数据，不读取/上传修改文件。后续输入固定生产者 attempt 的引用。
- Worker 负责识别布局、隔离、锁定、执行和资源保留。worktree 交接仍写共享 Plan location；真实工作目录写入 manifest，供后续 Worker 定位。
- 撤出注册 Workspace/Git 必填门槛及平台本地合并/回退入口；更新 UI、维护中 Worker、稳定规范与文档。历史证据不改写。

## Scope

Java Control Plane、维护中的 Python/TypeScript/Go Worker、Web Console、稳定规范与相关文档。无联网、测试、最终制品、发布或安装。Worker 自主准备不扩大既有只读、停止和执行授权边界。

## Work items

- [x] 1.1 实现 Plan location 分配、持久身份与安全清理生命周期。
- [x] 1.2 实现交接汇报与固定上游输入，解除平台目录归档依赖。
- [x] 1.3 去除计划、配置、图、领取和完成的注册/Git 前提，撤出平台 Git 管理。
- [x] 1.4 更新维护中 Worker：目录准备、单仓库 worktree、多仓库串行、交接与现场保留。
- [x] 1.5 更新 UI、规范和文档，完成编译与自查；独立审查和交付状态记录在 Verification。

## Acceptance

1. 无 location 的非 Git 编码 Plan 可确认执行，所有步骤获得同一个平台创建的目录。
2. 显式 location 不触发平台 exists/Git/内容检查，不要求注册。
3. Worker 可在空目录执行；单仓库自主隔离，多仓库直接执行且共享目录串行。
4. 不上传修改文件；后续 Worker 获得精确 attempt 的交接引用。
5. 失败/重试保留现场；用户目录不 reset/删除；未知进程终止阻止清理。
6. 平台不运行 Git clone/init/checkout/worktree/merge/reset/branch-delete 准备或回退任务。

## TODO

- R1 的五项 finding 已集中修复，针对性编译与独立静态复查均通过，待继续 auto 本地交付、归档和 worktree 清理。

## Verification

- Independent：涉及公开协议、持久化、并发和跨模块职责调整。
- 计划：离线 Java compile、已有 Worker compile-only 脚本；不执行测试、最终构建或网络。
- 已读取计划、领取/完成、配置、执行权限、产物、Worker 和 UI 调用链。
- 创建命令：`aiw fd new "Worker-owned plan locations and shared handoffs" --worktree`。
- workspace.json：parent=develop，branch=feature/FD-074，worktree=.wt/FD-074。
- 实现提交：`c22ad21`。自查已追踪 location 分配、完成事务、manifest 来源/摘要、目录锁、停止与清理路径；`git diff --check` 无空白错误，有 Windows 行尾提示。
- Java：PATH 中无 `mvn`，首次命令未启动编译。改用 `JAVA_HOME=C:/green/jdk-25.0.1+8` 与 `C:/green/apache-maven-3.9.9/bin/mvn.cmd -o -Dmaven.repo.local=D:/.m2/repository -DskipTests compile`。首次实际编译发现 FactoryPlanLifecycle 三处旧 delivery 调用，删除后同一命令重试一次成功，编译 321 个 Java 源文件。
- Worker：首次脚本在编译前因缺少 TypeScript 包停止；改用已安装 Node 的内置 TypeScript 语法转换，无依赖下载。随后 `node scripts/compile-worker-runtime.mjs` 成功：TypeScript/JavaScript/Python 语法检查、Go 编译；脚本已删除临时 Go 二进制。
- 未运行：测试、类型检查、lint/formatter、真实模型/Worker、浏览器、数据库迁移、共享文件系统并发/中断、最终制品构建、网络、发布或安装。语法/编译通过不代表运行通过。
- 独立静态审查 R1：`reviews/FD-074-review-r1.md`，结论 CHANGES_REQUESTED。发现 Python 导入、Go 非 Git 完成门槛、Windows 报告摘要、清理失败恢复及证据/中文文档问题；完整历史保留于报告，上文已补齐实际编译证据。
- R1 修复：Python 从 `worker_runtime.git` 导入别名；Go 仅在实际返回 Git 结果时校验版本；Python 报告以独占 UTF-8 字节写入，避免 Windows 换行转换；清理使用确定性隔离目录及外部 owner 标记，删除失败后可定位并继续处理，实际删除完成才标记 cleaned；通过 UTF-8 patch 修复中文和本节证据。修复 Worker 事件 `FD-074-000003-changes-requested` 已由 `fd074-worker-root` 领取。
- R1 修复后的针对性编译：同一离线 Maven compile 命令成功（321 个 Java 源文件）；`node scripts/compile-worker-runtime.mjs` 再次成功（仅因本轮 Python/Go 源码修复重编译）。未运行测试、类型检查、模型、浏览器、迁移或网络。独立静态复查通过，详见 R2 报告。
- 目录锁不自动抢占；进程状态不明或残留锁需先明确原执行停止再协调恢复。隔离删除失败保留确定性路径和外部 owner 标记，成功删除后才更新 cleaned。
- 独立静态复查 R2：`reviews/FD-074-review-r2.md`，审查实现 `c5e450f`、修复差异 `c22ad21..c5e450f`。R1 五项均已解决，结论 PASS；未运行测试、真实执行或迁移。审查通过不代表运行验证通过，本地交付与归档仍待主代理完成。

## Sources

- 本会话已确认的目录职责、交接、并发、失败/取消及单/多仓库策略。
- openspec/specs/worker-pull-protocol/spec.md
- openspec/specs/goal-discussion/spec.md

**Completed:** 2026-11-06
