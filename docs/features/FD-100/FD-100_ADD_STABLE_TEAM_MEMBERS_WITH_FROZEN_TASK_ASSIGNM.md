# FD-100: Add stable team members with frozen task assignments and controlled lifecycle

**Status:** In Progress
**Revision:** 1
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

成员目前依赖短期进程 ID，没有自己的名称、职责和经验。用户已明确采用稳定成员身份，进程按需启动或复用，重启不丢失成员资料。

## Options and decision

只增加稳定成员、每次执行的成员快照和任务归属三张直接关系表；复用现有流程、循环、进程池、Skill 文件和任务领取策略，不再建立一套成员运行时。

## Solution

成员私属于创建者 actor，名称、职责、内置 Skills、人工经验和状态持久化。工厂创建，团队页修改／导出／停止接单／恢复／解散。简单任务和 TASK/DECISION 节点可以指定成员；启动执行时冻结成员设定，分派时加入职责、经验及真实 Skill 包，任务和历史结果关联稳定成员。成员不占有共享进程，不给工具增加权限。

## Scope

V46 只增加三张表，不迁移或重写旧任务；未选成员的任务继续使用原流程。停止接单使该成员未领取任务等待，不中止已运行任务；恢复后可继续领取。只有关联流程与任务都结束或停止才允许解散；解散为只读归档，不删除历史或共享知识，不终止共享进程。人工经验可编辑和导出，不是自动学习或核实过的知识；共享知识库、模型/工具权限自我修改、文件执行、自动成员分配不在本批。

## Work items

- [x] 1.1 稳定成员 CRUD、版本冲突、可接单状态、资料与人工经验导出。
- [x] 1.2 工作流节点／简单任务选人、执行快照、真实 Skill 绑定和领取门禁；问答产生后继任务时同事务继承成员归属。
- [x] 1.3 工厂创建与团队管理界面、进程与成员区分、国际化。
- [x] 1.4 静态检查、源码编译并提交最后统一独立审查；通过后归档。

## Acceptance

不能读取或改动别人和只读角色的成员；直接成员 ID 读取不受列表上限影响。不同进程承接的任务保持同一成员归属。修改资料不改变已启动执行的快照。成员停止接单与领取通过同一事务锁串行，不影响共享进程中其他成员。解散拒绝未完成或可重试的流程；保留导出与历史。Skill 是实际已注册文件，不能借此增加文件写入或其他工具权限。

## Verification

- 持久化、公开 API、领取门禁与跨模块影响，使用 Independent。按用户要求先静态检查与编译，最后审查。
- 计划离线 Java test-compile 和相关页面脚本 node --check；不执行测试、迁移、服务、浏览器、模型或真实 API。
- 2026-11-13：离线 Maven `-DskipTests test-compile`（FD-081 settings、D:/.m2/repository）及 team-members.js/workflows.js/workers.js/console-translations.js 的 node --check 通过；补齐旧 WorkflowWorkerProvisioningTest 的新依赖，只编译、不运行。
- 静态自检 git diff --check 通过。成员元数据读取与领取门禁分开；领取/成员操作使用现有 authority_lock 串行，自动进程准备和 Fleet READY 诊断同步考虑停止接单。名单读取失败不阻塞执行详情/控制，不清空已有选择。
- 人工问答的 NativeQuestions 后继说明保留 old.instruction；WorkTaskService.createReplacing 同事务继承原 Skill 字节和成员归属，避免后继任务绕开停止接单或丢失身份。
- 工厂不为停止接单的成员准备新进程；空闲退出检查也不把此类 READY 工作当作可领取需求。既有 FactoryWorkerRegistryTest 最小夹具补充三张空成员关联表，只编译、不执行。

## Sources

- docs/FUNCTION-INVENTORY.md 第 06、09、10、11 项；用户明确“采用稳定成员、进程按需复用”。
