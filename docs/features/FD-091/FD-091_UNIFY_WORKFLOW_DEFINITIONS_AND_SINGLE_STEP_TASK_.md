# FD-091: Unify workflow definitions and single-step task execution

**Status:** In Progress
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

现有顺序 Plan、图计划和专业配置入口分散，通用工作需要理解软件开发角色和仓库配置。根据已确认功能清单，首先让单步骤任务与可视化工作流共用定义、独立执行记录和现有 Worker 执行路径。

## Options and decision

复用现有 WorkTask、原生执行观察／问答、模型循环与位置管理，不新增 Worker runtime。工作流核心集中在一个 Java 源文件；HTTP 与页面沿用现有模块职责。定义的每次运行冻结自身副本，执行节点映射为现有 Plan 的任务槽；新的流转逻辑接管这些运行的完成事件，不再触发隐式 Reviewer。

## Solution

提供任务、LLM 判断、人工输入和结束节点。业务分支依据模型返回的明确 choice，必须属于声明的分支；缺失或错误不能猜测路由。单步骤任务使用同一结构。可视化显示节点、分支、当前状态与历史。参考资料通过显式上下文输入，不冒充已接通知识库。新增两张表保存定义与运行快照，已有执行记录不迁移或删除。

入口为 `/ui/workflows`，侧栏与首页指向此处；简单任务直接输入执行。可用“售后示例”载入模拟规则和流程，保存后启动；此时才派发真实 Worker。定义和执行查询 API 为 `/api/r1/workflows`，执行详情位于 `/api/r1/workflows/executions/{id}`。启动请求携带固定 requestId，结果未知时重发同一请求返回原记录；不自动重提。当前通用 Worker 为只读文本执行，界面明确说明，不假装支持写文件或实际退款。

## Scope

本 FD 交付通用文本任务、业务分支和人工等待的端到端入口及售后示例。不在本轮修改登录权限模型、实现成员成长、替换秘书或清理全部旧入口。模型生成运行中补丁、文件执行工具和知识库检索在统一核心可用后继续。旧入口暂保留供现有执行查看，不新增历史兼容适配。

## Work items

- [x] 1.1 定义与运行持久化，单步骤与多节点使用同一核心。
- [x] 1.2 复用 Worker 执行，增加通用文本执行配置及明确分支结果。
- [x] 1.3 可视化页面、执行详情、人工回答和暂停／继续／停止。
- [x] 1.4 售后规则与示例输入，说明实际验证范围。

## Acceptance

一个定义可启动多个独立运行；简单任务直接执行。业务判断仅选择声明分支，人工输入等待后继续。暂停不派发下一节点，停止请求保留实际回执与历史。无仓库也能创建通用报告任务。页面展示真实路径与结果；用户输入和模型输出以文本渲染。不得调用真实模型验证而未获授权。

## Verification

- Review policy 为 Independent：涉及运行持久化、公共 API 与跨模块完成事件。
- 计划仅离线编译 Java、受影响 Python 与页面脚本，并作静态调用链检查；本轮不安装依赖、不运行测试、不迁移真实数据库、不调用模型。
- 2026-11-13：离线 Maven `-o -s .ai/fd/FD-081/offline-maven-settings.xml -Dmaven.repo.local=D:/.m2/repository -DskipTests compile` 首次发现 Run 字段误写为方法调用，修正后重试通过，编译 363 个生产源。
- 同一编译命令批次中，通过 Python 内置 `compile()` 编译受影响的 process_profiles.py，解析新增 TOML 和 Skill 索引，通过 `node --check src/main/resources/static/ui/workflows.js`。
- 未运行测试、服务、数据库迁移、浏览器或真实模型。售后示例仅是内置模拟输入，未宣称运行通过。完整知识库、动态流程修改和写文件任务不在本次交付范围。
- 2026-11-13：独立静态审查结果为 changes-requested；报告 [review-20261113.md](reviews/review-20261113.md)，覆盖 997818c 与 bb2e380，来源事件 FD-091-000002-review-requested，Reviewer codex-review-fd091-20261113。阻塞项：停止 ACK 会将任务重新置为 READY，可能重复执行；另需补充页面的只读文本执行说明。
- 修复 P1：在现有 `acknowledgeStop` 事务中、资源与租约释放后，由统一路由取消刚释放的 READY 任务并结束对应执行，原子保留 STOPPED 事件；不允许任务在事务提交后重新进入队列。完成与停止竞态继续保留真实结果，由状态查询收敛。
- 修复 P2：页面共用位置明确说明只读文本能力，不写文件、不实际退款或发送消息，提供中英日文案。
- 两项修复后离线 Maven compile 与新页面 JS 编译再次通过；未扩大到测试或运行验证。待独立 Reviewer 聚焦复核。

## Sources

- [已确认功能清单](../../FUNCTION-INVENTORY.md)，重点为 03–05、07、12–16。
