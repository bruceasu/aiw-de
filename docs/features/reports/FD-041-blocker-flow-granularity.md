# FD-041 执行粒度设计待决记录（已解决）

<!-- aiw-data: FD-041-blocker-flow-granularity.json -->

- 阶段/角色：Design / Planner；Session：composable-role-20261024-cf71803a。
- 来源事件：FD-041-000002-design-requested。
- 原因：用户确认角色可组合，但角色内技能流程是同一Worker工作方法还是每步独立Task尚未确定；两者影响状态、上下文与装配合同，因此设计未标就绪。
- 实际处理：建立FD草案，提出异步执行粒度问题；未实施依赖该选择的代码、未编造默认同意。
- 解决：用户明确选择“同一个Worker按流程使用多个技能；跨Worker的流程继续由平台编排”。已写入FD，原Gate解除，没有剩余人类决策。
- 后续：按同Task/Attempt角色方法、原v1配置和Skill文件集作兼容设计，执行有界独立设计审阅；不把提示步骤描述为平台逐步强制状态机。
- 可复用改进：设计讨论早期区分角色内方法流程与跨Worker任务编排；不因角色可组合就复制第二套调度器。
- 模板限制：当前仓库没有Skill引用的docs/templates/BLOCKER_FEEDBACK_TEMPLATE.md，因此按共享合同要求的事实/来源/尝试/状态/决策/改进字段记录；没有因此更改规则或生成虚假回执。
