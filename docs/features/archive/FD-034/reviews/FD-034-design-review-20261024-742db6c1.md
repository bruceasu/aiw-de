# FD-034 独立设计审阅

<!-- aiw-data: FD-034-design-review-20261024-742db6c1.json -->

结论：**ready**。未发现必须阻止实施的设计问题。本结论是设计就绪建议，不是实现验证或运行通过。

审阅对象为 `docs/features/FD-034/FD-034_FEATURE.md` Revision 2，以及 Planner 在本次审阅中补充的具体约束；来源事件为 `FD-034-000002-design-requested`。仅修改本报告与同名 JSON。

## 已核对的事实

- 现有页面要求人工填写内部意图版本、世代和政策版本，并直接输出完整 JSON，不能满足用户的可理解流程要求。改为选择计划、方法、批准模型、可选上游和中文结果，能直接解决问题；自定义方法作为可选入口符合当前服务能力。
- `ExecutionConfigurations.currentOwner()` 只接受绑定唯一 Owner 的认证人类。新 `ConfigurationUiOptions.snapshot()` 先调用此方法，再按该 Owner 读取政策、准备输入和配置；个人资料另按仓库限定，公共内置 Skill 作为单独来源。不能把全量数据库行或正文直接作为下拉选项返回。
- `ExecutionConfigurations.get()` 比较完整 `PreparedConfigRef`，仅有 id/revision/digest 不足以通过原契约。方案明确提供完整不可变引用并原样提交，不改变旧 state 键或写 DTO，兼容性边界合理。
- `AgentFactory.prepareAuthorized()` 对同一意图版本比较原始固定内容，READY 重放返回原任务，BLOCKED 重试校验当前 generation，新内容要求下一 intentVersion。方案沿用这些语义，不自行自动重试，也不把 READY 称为执行成功。
- 计划仓库来自本 Owner 政策规则；可靠完整基线只能来自所选计划步骤或已有任务。无可靠基线时明确填写完整提交，不执行 Git、不猜 HEAD，避免额外运行或权限扩展。
- `AgentFactory` 当前准入比一般 AI 批准目录更窄。提取纯读取 `supportedProfiles()` 并在界面和准入共用，可以避免推荐已知不支持的运行时、provider、fast 或 effort 组合。
- 现有 `propose` 是确定性装配，返回精确复用引用或完整 Candidate，不调用模型。保存已返回的同一 Candidate、预览输入变化即失效，能保持其不可变版本边界。

## 实施时应落实的最小约束

以下属于已有设计原则的落实建议，不新增验收 Gate，也不要求第二轮设计审阅。

1. `src/main/resources/static/ui/configurations.js`：载入准备记录时保留服务器原 Intent 的全部字段及精确引用。当前政策、模型或资料选项变化后，应显示“原选择已变化/不可用”并让用户显式修正，不能在刷新或重建下拉框时静默用首项覆盖原输入。修改确认后的内容才创建下一版本。
2. 同一文件：Planner 补充的 Builder fingerprint 与请求序号必须同时检查返回时的草稿，覆盖“预览请求在途时编辑”和“响应乱序”，不能只在已有预览后清空状态。保存按钮只发送对应预览返回的原 Candidate；保存成功后的刷新失败保留已返回的引用与成功状态。
3. 新 `ConfigurationUiOptions`、`ConfigurationController`：只给下拉框提供当前 Owner/仓库内的名称、精确引用及必要摘要；原准备输入是该 Owner 主动恢复任务所需的数据，与资料正文分开建模。上游任务从归属本 Owner 的执行上下文关联读取，并限制当前仓库，不能仅凭全局 COMPLETED 或仓库相同推断归属。
4. `ExecutionConfigurations` 和页面装配：额外 Skill 不重复加入基础方法已有的 primary/protocol Skill；保持 Assembly 的数量限制及原工具需求推导，不把用户内容登记解释为工具或权限授予。

## 覆盖与边界

Acceptance 1–2 由表单、分区切换、中文摘要和入口说明覆盖；Acceptance 3 由稳定 Intent、当前 generation、下一版本及未知响应先核对覆盖；Acceptance 4 由显式预览/保存和输入指纹覆盖；Acceptance 5 由服务端 Owner/仓库过滤、完整引用及原准入覆盖；Acceptance 6 由现有 Console 组件、准备记录恢复及附近文档覆盖。Work items 1.1–1.5 与其对应，没有发现必须增加数据库迁移、授权策略或外部契约决策的缺口。

FD-033 的动作授权范围仍待用户选择，与本 FD 独立；本次不替用户决定。其它私人秘书 Python、模型和启动脚本改动完全排除。

## 证据和未验证内容

静态读取了根规则、共享核心规则、工作管理、规划与代码审阅模式；配置 HTML/JS 和共享 Console 样式；ConfigurationContract、ConfigurationController、ExecutionConfigurations、AgentFactory、AiProfileCatalog、WorkflowPlanSpec、ExecutionGuard 的相关 Owner/绑定路径；正式上游结果查询及执行配置文档。软件目录用 `ConvertFrom-Json` 静态解析，只输出角色、产出、协议 Skill 数量及资料类型摘要：11 个默认方法、9 个内置 Skill。

实际使用 `Get-Content`、`rg`、`rg --files`、`Select-Object` 和静态 JSON 解析。第一次请求的 review.md 不存在，随后定位并读取实际 code-review.md；一次带 glob 的 Windows 路径搜索未匹配，已改为对迁移目录做限定内容检索。两者均未启动应用或修改数据。

未运行应用、浏览器、数据库、测试、编译、网络、模型、依赖下载或下级 Agent；未修改源码、FD、事件或提交。页面布局、真实会话及请求竞态的运行表现尚未验证，后续独立实现审阅应检查代码证据；无需因此增设未授权的运行验收 Gate。
