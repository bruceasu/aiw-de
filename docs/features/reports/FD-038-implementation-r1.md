# FD-038 Worker 实现 r1

<!-- aiw-data: FD-038-implementation-r1.json -->

Session secretary-desktop-20261024-a6529740；来源 FD-038-000003-design-ready。比较 d4e9efd..e34651f，当前失败审阅0次。源码提交8b4b466、78963c0、446ec39、e34651f，均为主工作区范围提交。

## 实现与来源

WORKER-SKILL-METHODS.md 将27个宿主入口逐项归类为增量吸收、已有覆盖或排除；重点读取相关FD workflow/testing、handoff、writing-great-skills、grill-with-docs、设计/诊断等方法正文，没有声称逐字审计所有辅助文件。已有任务拆分、四维审阅、oracle、诊断等保持，补充12个规范资源：共享交接、编码局部步骤、规划验收变化、决策澄清、设计边界走查、审阅修复覆盖、验证缺口和阶段交付边界。

新增 engineering-artifact-protocol/references/skill-authoring.md 和 goal-discussion/references/task-methods.md，分别由原主入口有条件加载。新参考显式加入index，原有8个task-methods更新；所有提示用Easy English、保留原Artifact/REPORT/VERDICT、角色和权限边界。不复制宿主FD CLI、worktree、自动归档/交付、外部服务、发布或子代理能力。Skill内容仍不能授权测试、网络或新工具。

编码局部清单在现有进度/报告文字中表达验收对应、完成/待办/阻塞和下一步。它不是新持久TODO、正式WorkItem或自动恢复状态机；自查不代表独立评审。原身份/版本、未知操作和消耗预算要在交接中保留，不能改请求或新会话重置执行权。需求/讨论已确认答案复用，未决关键规则返回其拥有者，不强行把所有问题做成执行任务。

## 资源与兼容

平台源src/main/resources/worker-skills共9声明包，12个变更资源精确同步为三语言36个副本。未覆盖advisory或其它非平台角色，不创建工具/调度/Schema。修改文件集会形成新manifest版本；SkillFiles仍在创建/准备阶段冻结完整字节，历史Task/重试/续行不替换。本FD不改execution/software-catalog.json历史文本资产，其文本摘要和文件集清单版本仍独立；新平台任务按Claim固定字节消费。

同步WORKER-SKILL-FILES说明、来源矩阵及task-orchestration稳定规格，只规定方法与执行权边界，不虚构新增运行时能力。现有其它Java、模板、秘书/Python/模型/启动脚本未暂存，宿主.agents/skills也未改。

## 实际证据与限制

一次范围git diff --check及内联只读静态检查通过：27源入口在矩阵中；显式文件/包/引用路径和新增链接存在；声明唯一及文件数/大小在原限额内；12源与36副本字节一致；历史文本目录与宿主源无修改。检查未导入/执行生产模块或Skill脚本。

用C:/green/bin/setup-jdk25.ps1（JDK25.0.1/Maven3.9.4）执行mvn -o -Dmaven.test.skip=true compile通过，结束2026-10-24T13:05:46+09:00；复制128份应用资源和2份配置资源，Java up to date。环境恢复；打印的打包/启动示例没有执行。当前工作区其它代码不因此纳入本FD审阅。

未运行测试、模型/对照评估、真实Worker/SDK/工具/恢复、浏览器、数据库/迁移、服务、网络/下载、最终构建/打包、安装、重启或发布。静态与资源编译只支持结构一致性，不证明模型任务质量、触发准确率、成本下降或恢复效果。已有任务有意保留旧Skill；运行环境需按正常更新流程使用新资源。无未解决blocker；下一步独立Reviewer审实际方法、权限与输出边界、声明和副本后由host正常归档。
