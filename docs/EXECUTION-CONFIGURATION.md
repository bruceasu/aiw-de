# 任务准备与工作方法

FD-030补充：平台Skill改为固定文件集，Claim新增`skillFiles`，并要求`skill-files-v1`；原Asset/config文本摘要保持不变。入口与辅助文件分别加载，动态配置不要求预装本地Skill库。详情见[平台Worker Skill文件集](WORKER-SKILL-FILES.md)。

本合同为 `aiw.execution.config.v1`，首期消费实现是 Python 的 OpenAI Agents 和 Unix/WSL Codex App Server。配置是职责、方法、内容引用和工具需求，权限仍由 [执行授权合同](EXECUTION-GUARD.md) 决定。Go/TypeScript 保留既有任务能力，不广告或领取新配置任务。

## 使用路径

1. 在授权页面绑定当前身份，确认目标与工具范围；默认仅记录预算和费用，预算及定价可选，显式 enforce 模式才要求完整配置；使用新运行库，不导入或删除旧数据。
2. 部署兼容 Worker：设置 `AIW_EXECUTION_GUARD=true`、`AIW_DYNAMIC_CONFIG=true`，以及原有机器凭证、仓库、Runtime 和后端配置。机器凭证不进入模型上下文。默认 launch role 仍用于旧任务，新任务逐次使用固定配置，不以 launch role 固定其职责。
3. 打开 `/ui/configurations` 的“准备任务”：选择关联计划和工作方法，描述目标/范围/完成标准，再选择工作仓库和已批准模型。计划来自当前账号已经确认的执行政策，政策版本自动保留；仓库只取自该政策。固定代码版本只从本计划已知步骤/任务带入，无法确定时需明确提供完整 Git 提交，页面不会运行 Git 或猜测 HEAD。真正依赖上游成果时，再展开可选设置，勾选当前账号同仓库的已完成任务。
4. 准备只做目录匹配、内容/引用/能力/政策校验，预算按部署计量模式处理；无模型、命令、进程或网络调用。成功时，PREPARING→READY、配置绑定、业务任务创建和固定上下文在同库事务提交。未就绪不创建可领取任务，也不增加业务任务消耗。
5. 页面以结果卡片说明任务已创建或具体缺口，不打印返回 JSON。准备成功后展开“查看当前任务进度”，按本次任务编号读取正式状态，点击“刷新此任务进度”更新；准备记录的 READY 只表示创建成功。关联计划链接只查看计划定义，不代表它包含这项配置任务。到“准备记录”载入原记录继续处理：相同内容保留原意图版本并使用服务器当前准备世代，修改内容才建立下一版本。内部 ID/版本/世代无需人工填写；普通刷新不覆盖草稿或原引用，“更新选择项”才明确采用新的政策/模型选项。结果未知时先刷新查原记录，必要时显式重试同一原对象，不自动换 ID/重放。READY 重放返回原任务，技术 Attempt 不重复创建业务任务或重置消耗。

## 自定义工作方法（可选）

角色现在也可从空白或默认模板组合：进入 `/ui/configurations?view=roles`，配置职责、共享技能、角色内步骤和整体交付要求，再预览、保存并选用于准备任务。内置角色仍可直接使用；业务角色不再必须来自固定模板。使用及单Worker流程边界见[组合角色](COMPOSABLE-ROLES.md)。

设计、架构、领域建模、诊断、冲突修复、研究、交接、教学与文章编辑也可作为附加 Skill。最多选六项，不会替换基础职责或增加工具权限；用途和组合示例见[附加技能配置指南](WORKER-OPTIONAL-SKILLS.md)。

原型制作可在这里勾选附加 `prototype` Skill，配置给现有 AI Worker；无需新增 Worker 类型。UI/逻辑原型的制作、预览权限与交付说明见[原型制作技能](WORKER-PROTOTYPE-SKILL.md)。

普通任务直接使用默认方法即可，不需要先组合 Skill。只有默认方法不够用时，切换“自定义工作方法”：

1. 选择基础方法和已确认范围中的仓库，勾选额外 Skill/本仓库参考知识，填写可选工作说明。基础方法自带的 Skill 不重复加入；模型、读取、搜索、Git、问答及必要的写入/命令需求按原方法推导。
2. 点击“预览工作方法”，核对基础方法、产出、仓库、引用和工具需求的中文摘要。找到相同方法时直接复用；否则点击“保存此方法”，保存的是该次预览返回的原候选。修改输入后必须重新预览，在途旧响应不能覆盖新输入。
3. 点击“用于准备任务”，再回到任务表单核对计划、同一仓库和模型，明确准备。保存方法不自动创建任务，不调用模型，也不授予任何工具或执行权限。

没有可引用资料时，可在下方展开“添加参考资料或 Skill 说明”，填写名称、仓库、类型和明确选定的正文。名称作为标题一起保存，引用、初始版本和摘要由页面生成；已登记内容不可原地修改。选择项只返回当前账号/仓库的资料元数据，不输出私人正文；旧无标题材料使用引用名，不截取正文作标题。

“准备任务”“准备记录”“自定义工作方法”切换显示。页面使用现有 Web Console 外壳，不再要求手写 Assembly/Candidate/Intent JSON，也不显示预留但尚不能执行的职责目录。确认成功后的列表刷新失败会保留实际成功结果和精确引用，不把它描述为未保存。

默认主方法静态派生自现有 11 份 Python TOML Profile；原 9 个 Skill 包加上 prototype 和 FD-040 九项附加技能，共 19 包。十一份主方法保持九字段方法和现有工程输出协议。`src/main/resources/execution/software-catalog.json` 是受审内容快照，不在启动时抓取外部材料。原 Profile/Skill 更新不会静默改写已有配置；更换快照需新版本和摘要。默认 verifier/integration-verifier 声明 COMMAND 需求，没有独立命令许可和兼容 Linux/WSL 隔离适配器时保持阻塞。

## 不可变引用及职责边界

`PreparedConfigRef={id,revision,digest,protocol,resultKind,skills[{ref,version,digest}],knowledge[{ref,version,digest}],toolRequirements}`。Skill 和知识都有明确版本；知识另有 Owner、仓库访问范围。配置正文只引用平台已登记对象，正文和内容摘要一并固定；引用不接受下载 URL 或新可执行工具。

`Payload={configRef,contentJson,workerCapabilities}`。digest 是 contentJson 原始 UTF-8 的 SHA-256；消费方先核对原文摘要再解析封闭字段，不通过重新序列化猜测摘要。正文包含声明式 Candidate 和实际选定的 Skill/知识内容。任务事实、目标和上游 Attempt 来自固定 WorkTask/TaskInput，不写成永久 Skill。

知识归知识管理职能，生产材料和消费材料不改变其治理归属。本期登记的是用户明确选定、具有当前 Owner/仓库范围的内容版本，不读取秘书私库、完整聊天、私人 policy 或凭证。既有 KnowledgeDocument 仍保存完成事实；这里不另建长期记忆或私人知识库。

十项职责中仅软件方法模板可执行；Agent Factory/知识治理服务与 Worker 模板是否实现分别说明。需求评审处理需求；行为审计保持预留，不能把代码审查改称行为审计。业务监测与安全处置分别授权，均未因目录预留而实现。常驻、按需拉起及闲置回收未在本期决定。

## 匹配、组合和声明式候选

先使用精确配置引用或语义完全匹配的既存配置。`propose` 根据已登记默认方法、额外 Skill、知识及方法说明生成可审阅的声明对象：能复用时返回 EXACT_REUSE；否则返回 SKILL_COMPOSITION 或 DECLARATIVE_CANDIDATE。此处是确定性的声明装配，不暗中调用模型。模型产生的候选也必须经过相同保存和校验入口；不会获得新的可执行工具或权限。

Assembly 字段为 id、revision、repositoryRef、category、defaultRole、additionalSkills、knowledge、toolRequirements、methodInstruction（可空）。输出 Candidate 包含 id/revision/ownerId/repositoryRef/category、九字段 method、skills/knowledge、toolRequirements、outputContract、source。primary/protocol Skills 必须与实际登记集合相符，包含 engineering-artifact-protocol；resultKind 为 REPORT/VERDICT/WORKSPACE，readOnly 与其一致。

FD-041 为原 propose/candidates/state 增加显式 `kind=role` 分支，接收闭合的角色定义或固定引用；缺省 `kind=method` 保留上述合同。组合角色编译为原九字段Method和Candidate，私有Skill包保存入口及role.json；不向Worker的封闭v1 JSON添加字段。原state追加当前Owner的roles名称/引用摘要，供人类名称显示。角色级delivery进入原outputContract，步骤completion单独保留。专业模板的输入/来源/产出限制不会因组合取消。

候选和知识版本只能由当前认证人类在其范围内登记。平台计划消费者可在自己的正式版本/控制事务中调用内部 `prepareAuthorized` 端口，必须对应已经确认的执行政策和 Owner；内部固定上下文端口不另授予权限，也没有机器 HTTP 写入口。后续问答/图拥有者消费正式继续事务；配置绑定可原样继承，问题或图的有效绑定仍由所属模块更新。

## 能力和模型

`WorkerCapabilities={protocolVersions,runtime,adapterVersions,platform,resourcePolicyDigest}`。登记同时要求实际受控适配器预检，记录实际 SDK 版本、适配器源码摘要或 Codex 协议摘要，不按版本号放行。基础 Worker 重新登记会先失效旧能力元数据，防止两次登记之间使用旧证据领取。繁忙但兼容的在线 Worker 不等于不兼容，容量仍由正式 claim 检查。

claim 在与 Lease/Attempt/额度预留相同事务固定所选能力元数据。Python 在 start 前重新计算本地能力摘要；start 固定已批准 AI Profile 的版本及参数，每次执行只使用该配置中的精确模型，不读取 Step 偏好覆盖或自动切换模型。start 后复核固定配置与输入，创建本次 Agent；两种后端继续通过相同 broker 执行工具。Profile/Skill/知识不能扩大已确认范围，未声明工具也在实际 broker 路径拒绝。

新配置任务要求 `execution-config-v1`、`execution-guard-v1` 和精确 `repo:` 能力；准备与 claim 使用相同完整要求，不沿用旧 flexible-work-v1。配置消费方严格核对 configuration/skill/knowledge 的完整引用集合与摘要；额外正式问题/checkpoint 等引用继续由 guard 固定、由来源消费者登记和校验，不能用于替代或隐藏缺失的配置引用。

每次任务单独建立上下文、Agent、工作区和本次回执，不缓存上一任务的专业配置或私有会话。取消、丢失 Lease 或进程退出仍按执行合同确认；UNKNOWN 保留资源，不能借配置切换释放或重用未确认执行。

## 接口与原因码

人类 POST 接口在 `/api/r1/authority/execution-configurations/`：`assets`、`candidates`、`propose`、`prepare`、`state`。使用当前会话、CSRF、Owner 和 operator 角色，封闭 DTO 及 32 KiB 请求上限。候选只传引用，大目录在服务器受审静态资源中。

FD-034 为 `state` 增加只读 `uiOptions`，保留原返回键和所有写 DTO：当前 Owner 的计划/政策、可选仓库/已知基线、完整配置引用、准备原输入、同仓库上游选项和资料元数据，以及与准备入口共用筛选的已批准模型。内置公共 Skill 可引用；个人资料和准备输入按 Owner 隔离。页面内部装配原协议，不增加新的权限、数据库表或自动业务请求。

机器 POST `/api/r1/worker-protocol/v1/workers/{workerId}/configuration` 登记 WorkerCapabilities，使用现有机器 token；任务领取载荷增加可空 executionConfiguration，旧任务保留原行为。新配置不能派给未声明协议的 Worker。

Preparation 字段为 intentId、intentVersion、generation、state、configRef、errorCode、taskId、missing、recoveryActions。Intent 包含 intentId、intentVersion、expectedGeneration、planId、policyRevision、repositoryRef、baseRevision、instruction、dependencies、runtime、aiProfileId，并且 defaultRole/configRef/candidate 三选一，其余显式为 null。

封闭原因码：INVALID_CONFIGURATION、UNSUPPORTED_RUNTIME、PREPARATION_FAILED、NO_COMPATIBLE_WORKER、AUTHORIZATION_REQUIRED、BUDGET_CONFIGURATION_REQUIRED、SOURCE_CHANGED。已知缺口给出修正、重新准备、接入兼容 Worker 或处理明确授权申请的动作；预算缺口仅在显式 enforce 模式阻止准备，默认 record-only 不要求预算申请；不自动安装、登录、下载、扩大资源或改用其他后端。

V13 仅新增版本、准备、Worker/Attempt 快照及绑定表，不改写历史迁移。本期编译和独立静态审查以 FD-027 双报告为准；本文不证明真实 SDK/协议、数据库迁移、进程终止或工具隔离已运行通过。
