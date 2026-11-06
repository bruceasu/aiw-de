# FD-077: Bind recommendation goals to the original request

**Status:** In Progress
**Revision:** 1
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

用户未修改目标，推荐却返回 INVALID: Recommendation output was rejected: recommendation goal or source changed，无法继续处理。前轮只读核对最近三次持久化结果：requestKey/inputVersion 一致、source=AI，但模型将 340 字符目标改写为 327 字符，且差异不只在空白。现实现要求模型逐字回传平台已经保存的目标，造成推荐结果被错误拒绝。

## Options and decision

不依赖加强提示词或重复调用模型解决文本复制问题。目标和来源由平台基于原始任务请求绑定；模型负责理由与步骤。继续严格校验 requestKey/inputVersion 和全部步骤约束。已有完成报告在读取时重新解析，无需改数据库或再次调用模型。

## Solution

WorkflowRecommendationService 在校验请求关联与理由后，要求 plan 为 JSON 对象，复制对象并写入冻结任务 Envelope 的 goal 和固定 source=AI，再按 WorkflowPlanSpec 解析、执行既有能力与依赖校验。模型回传的 goal/source 不作为权威输入；可省略。顶层 location 及步骤 repositoryRef/baseRevision 仍不得由模型指定。

新任务提示词说明由平台填写 goal/source，模型仅返回 plan.steps。页面/API 的 Result 与 WorkflowPlanSpec 不变；仍需操作者应用、保存及确认，读取结果不创建或执行业务 Plan。

## Scope

当前工作区直接实施，遵循 fd-workflow。仅涉及推荐服务、边界回归用例及邻近文档。保留用户修改的 application-sqlite.yaml 和 scripts/startup.bat。无数据库更新、迁移、进程启动、网络或模型调用。测试仅编译，不执行。

## Work items

- [x] 1.1 修复目标与来源的平台绑定，更新模型输出提示并阻止模型指定 location。
- [x] 1.2 添加结果恢复、关联拒绝和步骤/目录边界回归用例，更新说明并离线编译。
- [x] 1.3 完成编译与差异自检，记录交付限制并准备独立审查。

## Acceptance

1. 已完成且请求关联正确的推荐，即使模型改写或未返回 goal/source，也返回 READY_TO_APPLY；目标严格保留冻结请求的原文，source 固定 AI，建议步骤保留。
2. requestKey 或 inputVersion 不匹配、理由无效、plan 非对象仍返回 INVALID。
3. 未授权 location/repositoryRef/baseRevision、非法角色/能力、步骤依赖等仍被拒绝；不自动创建或执行业务 Plan。
4. 已存报告通过同一 get/recover 路径可重新读取；不修改历史报告、不重试模型。其他步骤错误仍可能阻止旧结果应用，不承诺所有旧结果都恢复。

## Verification

- Independent：调整模型输出与可信请求之间的验证边界，需要一次独立静态审查。
- 计划检查：离线 Maven test-compile、一次差异检查、独立静态审查。不得将编译或静态审查表述为运行测试通过。
- 未授权测试执行、真实服务启动、浏览器、数据库写入、模型调用、网络或最终制品构建。
- 已完成：保留关联校验后，基于冻结 Envelope 填入 goal/source，再走 WorkflowPlanSpec 与真实 WorkflowEditorService.validateCandidate；新增 location 拒绝分支。新提示词仅要求返回建议步骤，API Result 格式和人工应用流程保持不变。
- 新增 WorkflowRecommendationResultTest 的七个用例，覆盖旧报告改写恢复、缺省元数据、请求和版本不匹配、无效理由/plan、目录与仓库越权、角色与依赖以及操作者隔离；仅编译，未运行。
- 实际命令：设置 JAVA_HOME=C:/green/jdk-25.0.1+8 后，`C:/green/apache-maven-3.9.9/bin/mvn.cmd -o -Dmaven.repo.local=D:/.m2/repository -DskipTests test-compile` 成功，321 份主源码和 55 份测试源码完成编译，无依赖下载。
- `git diff --check` 通过；静态审阅推荐服务和邻近说明的差异。只读查询最近三个完成报告的字段及步骤元数据，均未包含 location/repositoryRef/baseRevision 授权内容，步骤属于支持的分析/编码角色，未发现另一个明显的结构阻塞；这不是执行恢复成功的证据。
- 剩余风险：需由运行中的服务载入新代码后确认真实页面恢复。没有启动/重启服务、运行测试或再次请求模型；原数据库报告及用户配置保留。
- 首次 request-review 因将审查与归档本身列为未完成 Work Item 被拒绝，未创建审查请求；已将 Work Item 修正为实际完成的交付准备，独立审查与归档继续作为后续流程门禁。

## TODO

- 实现、编译和自检已完成；等待独立静态审查，随后完成归档和本地提交。

## Sources

- 用户本轮推荐失败报告及“我现在无法继续处理”的修复请求。
- 前轮只读诊断：三个 WR 请求关联均匹配，原目标 340 字符、模型回传 327 字符。
- docs/current-zh/FLEXIBLE-WORKFLOW-PLANS.md
