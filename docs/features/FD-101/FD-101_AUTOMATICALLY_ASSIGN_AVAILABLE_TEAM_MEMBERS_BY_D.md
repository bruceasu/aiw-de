# FD-101: Automatically assign available team members by declared Skills

**Status:** In Progress
**Revision:** 1
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FD-100 提供稳定成员与手动分配，但新任务默认匿名执行，团队不能自动承接工作。

## Options and decision

直接在现有工作流推进中选择成员，不增加调度 LLM、进程框架或新的分配表。仅按显式声明的 Skills 匹配；不从职责文字猜测能力。

## Solution

- 新提交默认 AUTO；也可 MEMBER 指定成员或 GENERAL 明确不使用团队成员。节点可声明最多八项内置 Skills。旧执行的匿名节点保持原意。
- AUTO 在节点就绪时，选择同一用户、ACTIVE、拥有全部所需 Skills 的成员。所有成员都有 general-work 基础能力。优先未完成任务较少者，再按最近任务较早、成员 ID 排序。实际并发仍由原进程容量控制。
- 首次选定后冻结资料与 Skills；重试或循环回到节点不重新选人。手动选择仍在流程启动时冻结，暂停接单或缺少 Skills 时明确拒绝。
- 无匹配时 WAITING_MEMBER，不创建任务、不消耗步骤。现有五秒周期任务每批扫描最多二十项、游标轮转。原因不变不反复写入。停止/暂停后不分配。
- 沿用 authority_lock，先锁 authority 再锁 execution，串行化资料修改、选择和执行控制。V47 仅新增关联索引。
- 许愿池内部草稿生成明确使用 GENERAL，避免全新安装被成员配置阻塞。

## Scope

完成自动分配、Skills 条件、等待原因和 UI 国际化。不增加成员独占进程、智能能力推断、自我进化或文件工具权限。

## Work items

- [x] 1.1 分配模式、Skills 校验与确定性成员选择。
- [x] 1.2 等待、周期恢复与冻结/控制事务。
- [x] 1.3 简单任务和流程编辑 UI、说明；清单将在审查通过后登记。
- [ ] 1.4 编译、静态独立审查、提交与归档。

## Acceptance

新工作可自动由匹配成员承接；无成员、缺少 Skills、匹配成员暂停时有明确原因，条件满足后自动推进。显式选择和已冻结分配不替换；暂停/停止阻止新派工。查询按用户隔离。GENERAL 保留通用执行方式。

## Verification

涉及执行状态、并发、公开输入和索引迁移，采用独立静态审查。仅允许编译和静态检查；未授权测试、迁移执行、服务启动、网络或模型调用。

- 已完成 `git diff --check` 和改动调用链自查：模式/成员 ID 一致性、用户隔离、冻结资料、游标扫描、停止和暂停、补充上下文。修正暂停等待后仍提示自动继续的文案。
- 离线编译：加载 `C:\green\bin\setup-jdk25.ps1`，`MAVEN_OPTS=-Xmx512m -XX:MaxMetaspaceSize=256m`，执行 `mvn -q -o -s .ai/fd/FD-081/offline-maven-settings.xml -Dmaven.repo.local=D:/.m2/repository -DskipTests test-compile`，通过。未运行测试。
- `node --check src/main/resources/static/ui/workflows.js`、`node --check src/main/resources/static/ui/team-members.js` 通过。自查调整 Java 表达式可读性与暂停文案后，同一 Maven 编译和 workflows.js 编译再次通过。
- 原 `WorkflowWorkerProvisioningTest` 的 SQL mock 与断言更新，以区分独立于进程自动启动设置的成员匹配扫描；仅编译，不声称测试通过。
- V47 索引迁移、实际数据库锁竞争、浏览器操作和自动分配运行效果尚未执行验证。没有新增权限或依赖。

## Sources

- 用户确认的稳定成员与按需复用进程；docs/FUNCTION-INVENTORY.md 第 11 项；FD-100。
