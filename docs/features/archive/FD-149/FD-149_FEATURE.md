# FD-149: 单任务执行与业务工作流编辑器

**Status:** Complete
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

简单任务用 TASK 与 END 模拟工作流，混淆了 Worker 自主执行和业务规则。可复用业务工作流需要独立描述确定性步骤、人工决定和调用 Agent 的位置。

## Options and decision

简单任务使用独立的单任务定义和完成路径。复用现有 Plan 存储、任务授权和 Worker Protocol v1。业务图按已声明的流程推进，规则和人工步骤可独立完成，只有显式 Agent 节点才通过同一任务派发入口提交工作。

## Solution

新增 JSON 字段等值规则，输入可选本次运行数据或上一 Agent 正式结果。纯业务图可运行和结束。参考 PaiAgent，将现有节点编辑区组织为节点面板、画布和属性区，运行画布支持查看指定步骤记录。

## Scope

新建简单任务、委派文本任务和新建简单文本安排使用单任务定义。旧数据和已开始的运行按原定义解码。问答继续复用检查点和受控内部续接记录，页面使用原逻辑任务编号。文件和命令权限保持独立授权。

## Work items

- [x] 1.1 单任务定义、派发、完成和兼容读取。
- [x] 1.2 可复用业务规则、人工处理和显式 Agent 节点。
- [x] 1.3 工作流编辑与按步骤查看运行记录。

## Acceptance

- 新建简单任务不含 END 节点，Worker 返回合格结果后直接完成，旧流程可照常读取与继续。
- 相同业务流程可以用不同 JSON 输入反复运行。规则匹配出口，人工决定能继续，纯业务流程创建零个 Worker 任务。
- 编辑器能配置规则、人工和 Agent 节点及连接，运行图能查看节点事件。

## Verification

- JDK 25 和 Maven 3.9.9 执行 `-o -Dmaven.repo.local=D:/.m2/repository -Dmaven.resources.skip=true -DskipTests compile` 通过。避免向正在运行的旧服务同步新页面。
- `node --check src/main/resources/static/ui/workflows.js` 通过。`git diff --check` 通过。
- Maven 同一离线配置执行 `-Dtest=WorkflowBusinessRoutingTest test`。初次两条通过。修正内部计划创建、已执行规则的保护与 Agent 结果解析后重跑，三条通过。覆盖单任务只派发一次、直接完成、幂等回放，真实 PlanService 的纯业务流程匹配、人工确认、不匹配和无效 JSON，以及带工作目录尾注的 Agent JSON 结果。
- 跨执行入口、持久格式和运行界面通过独立静态复审。浏览器、在线 Worker、真实模型和跨进程恢复尚未运行。[独立复审报告](reviews/FD-149-000002-review.md)

## Sources

- Issue: none
- [PaiAgent WorkflowEngine](D:/playground/PaiAgent/backend/src/main/java/com/paiagent/engine/WorkflowEngine.java)
- [PaiAgent ReActAgentNodeExecutor](D:/playground/PaiAgent/backend/src/main/java/com/paiagent/engine/executor/impl/ReActAgentNodeExecutor.java)
- [PaiAgent EditorPage](D:/playground/PaiAgent/frontend/src/pages/EditorPage.tsx)

**Completed:** 2026-11-23
**Disposition reason:** 单任务和业务规则路径经编译、定向回归与独立复审通过
