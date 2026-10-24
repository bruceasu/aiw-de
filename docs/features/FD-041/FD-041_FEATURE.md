# FD-041: 可组合角色与共用技能流程

**Status:** Design
**Revision:** 2
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户确认：角色是技能的集合与流程的编排，共同能力应可跨角色组合，角色不应固定。FD-039/040 已登记可复用技能，但当前 `/ui/configurations` 的装配入口仍要求一个内置 defaultRole，再追加技能；propose 保留基础方法的 primarySkill、roleCapability、readOnly 和 resultKind，content 还以默认目录的 roleCapability 作准入。它实现了附加技能，尚未表达用户可定义的角色。

用户目标是复用能力并按任务组合角色，而不是为每项能力新增 Worker 进程或为新增角色修改代码白名单。新增角色应能说明目标、所用技能、流程及交付要求，由现有工厂装配执行。

事实与未决项分开：上面限制来自代码静态读取；用户已确认角色组合原则；用户进一步确认同一Worker执行角色内流程，跨Worker沿用平台编排；未运行现有Worker或流程。

## Options and decision

1. 仅继续给固定角色添加附加 Skill：变化小，但仍不能由技能与流程定义新角色，不满足目标。
2. 同一 Worker 在一次正式任务中按所配置的角色流程使用多个技能，跨 Worker 的执行沿用现有平台图编排：符合角色作为工作模板的定义，保留当前任务/Attempt 和工具执行边界。推荐。需要明确哪些步骤由模型遵循、哪些状态由平台记录，不能把提示顺序称为逐步骤强制调度。
3. 每个角色内技能步骤都是平台独立 Task：可独立记录、调度与恢复，但会改变角色执行粒度、上下文传递及任务状态语义，不能与方案2混为一谈。当前 GraphContract/GraphRunner 已有 TASK/IF/GOTO/ASK/END 和固定配置引用，可作为该方案的复用基础。

用户已选择方案2：同一Worker按流程使用多个技能；跨Worker继续由平台编排。该决策已解除原Gate。

具体接口方案比较：直接扩展v1 Method/Candidate会破坏封闭JSON和Python精确字段集合；新增角色表会重复既有不可变配置存储。选择把结构化角色定义及其入口作为一个Owner/仓库范围的私有Skill文件包，编译为原v1 Candidate。共享技能只引用，不复制。沿用既有HTTP操作，通过显式kind=role选择新闭合DTO，原请求默认kind=method；不添加授权白名单、不改v1数据形状、不新增表。

## Solution

已确认的领域边界：

- Skill 是可复用的方法单元；research、domain-modeling、handoff 等可由多个角色引用同一固定版本，不按角色复制内容。
- 角色是职责目标、技能引用、流程规则及交付要求的组合模板；内置角色是默认模板，新增业务角色不要求新增枚举、Worker启动Profile或进程。
- Worker 是执行载体；Agent Factory 根据正式任务装配角色及上下文。
- 模型、工具需求、执行授权与角色定义分开。组合描述需要的能力，实际执行仍使用原授权及适配器。
- 任务保留已选角色/技能/流程的固定版本；角色更新用于后续显式选择，不追写旧任务、重试或续行。

### 声明和编译

新增 `RoleComposition`（protocol=aiw.role.composition.v1）：id/revision、repositoryRef/category、人类可读name、goal、delivery（角色级交付要求）、resultKind、steps、knowledge、toolRequirements。每步包含key/title、固定Skill引用、instruction、whenCondition和completion；角色内顺序由列表确定，条件是模型遵循的说明，不执行脚本或表达式。最多8步、6种非协议Skill、8知识，字段有长度/非空/重复/版本一致校验，协议资产自动加入。允许同一技能在不同步骤复用，同ref不能引用不同版本/摘要。

`ComposedRoles` 纯声明编译器生成：私有Skill资产（SKILL.md + role.json，标准aiw.skill.files.v1 Document）；原v1 Method/Candidate。primarySkill是该固定角色包，protocolSkills为工程成果协议及去重的步骤技能；roleCapability使用通用技术执行标记composed-role-v1，业务角色不再按内置枚举限制。Method.name继续使用原机器id规则，角色的可读名称只存在role.json；state新增当前Owner的roles摘要（name/methodName/repositoryRef/reference），供界面显示及复制，不输出角色正文。Worker前缀独立。按resultKind导出readOnly；保持原结果/工具一致校验。delivery编译进原outputContract，和每步completion分别保存，原REPORT/VERDICT/WORKSPACE外层格式不变。

旧专业主Skill与共享方法分开处理：从默认模板导入真实primary Skill和原instruction，不声称这些包是无约束的通用方法。若步骤引用内置方法的primary Skill，编译/预览/保存按目录核对其resultKind/readOnly，冲突则拒绝；保留Skill原任务类型、输入材料、冻结来源和职责边界，声明不能豁免。自然语言的职责/输入冲突无法仅凭类型校验消除时，Worker通过原问题/缺口机制报告，不假装成功。界面导入模板时说明这些约束；从空白只选共享方法则不继承某个业务角色。共享方法之间仍不复制正文或换版本。

入口逐步说明执行目标、使用哪些Skill、条件、产出和完成要求，最终报告在现有描述字段记录各步完成/不适用/阻塞及真实证据。它是同一正式Task/Attempt内的模型工作方法，不是平台逐步状态机；不宣称独立恢复、强制技能调用或自审独立通过。跨Worker仍用现有GraphContract。

### 保存与校验

`propose?kind=role` 仅构造预览：临时角色资产和其余已登记资产一起严格校验，不写库；返回可读候选/精确复用引用及定义摘要。`candidates?kind=role` 接收相同定义，在原AuthorityBoundary锁/事务中登记私有Skill资产并保存原Candidate，失败整体回滚；重复相同输入复用原版本，不换ID。`state?kind=role` 用PreparedConfigRef读取当前Owner的原配置，核对为组合角色后返回role.json供复制编辑；老state空请求保持。

新角色准入不只是删除白名单：通用技术标记只在私有角色包结构、role.json和编译候选完全匹配时接受；仍校验实际所有引用、Owner/仓库、版本/摘要和工具/结果。预览和保存共用编译器。定义序列化保持确定性（工具按enum顺序），同ID修订不同内容按原不可变冲突拒绝。复制编辑生成新系统ID，不冒充原修订或更新旧任务。

角色文件包复用SkillFiles.configured；Python已有动态配置直接按固定Candidate构建ProcessProfile，不需修改运行协议、运行后端或启动Profile。Go/TypeScript不因此获得动态配置能力。

### 界面与兼容

角色编辑入口为 `/ui/configurations?view=roles`，由原已受保护路径渲染新模板，复用Console外壳和configurations.css。页面支持从空白或默认方法导入目标/产出/技能步骤、编辑步骤（增删/上移下移/条件/完成要求）、预览、保存、复制既有组合角色与选用于任务准备。配置/保存不运行模型或创建任务。

通过已有干净的configuration-methods.js加入角色入口链接；不改工作区已有脏模板或脏权限文件。保存后跳转 `/ui/configurations?method=<id>&revision=<revision>`，从当前Owner服务器返回的已保存引用中选中对应版本，不信任URL自带内容，不自动准备。更新选项/在途响应不能覆盖草稿。保存响应未知时保留原定义，先刷新核对同ID/修订/定义；确认不存在后才显式重试原对象。JSON只在协议内传递，界面显示表单和摘要。整体交付要求为独立必填字段。state.roles提供的友好名称优先显示，方法机器标识无需用户记忆。

兼容及发布策略：只增加新DTO/页面及现有操作的显式分支，不新增依赖、迁移或授权。11份默认方法、19个公共Skill资产及旧配置不变；对新组合配置由原配置/SkillFiles版本固定，后续编辑不追写旧任务。升级仅Control Plane新界面/服务，现有支持config-v1和skill-files-v1的Python Worker继续消费同形v1任务。正常部署另行处理，本FD不部署或启动服务。服务回退不再提供创建/编辑组合角色入口；机器Method.name和v1封闭字段保持，使旧读取路径仍可解码已固定配置/文件集。已有任务及正式续行仍使用原固定内容，不改成默认角色；回退后的具体领取/执行行为未经运行验证，需要部署方按实际证据决定是否暂停这些任务，不能自动重建或删除它们。

需要独立设计审阅具体闭合DTO兼容、私有角色包与候选匹配、无写入预览、原子保存/未知结果、原保护路径及主工作区隔离；修复所有重大设计问题后才design-ready。

## Scope

拟覆盖配置服务/装配合同、现有自定义工作方法界面、必要的角色声明资源与对应 Worker 消费、使用文档和稳定规格。按最终执行粒度缩小到一个完整可用路径，不另建第二套工作流引擎，不重写全部角色和历史技能，不混入工作区已有秘书/授权/UI等其他任务改动。

当前在主工作区做设计。沿用本会话已选择的主工作区范围提交方式；后续实现和提交仅限本FD。无测试/模型/服务/浏览器/网络/下载/部署/重启授权。

## Work items

- [ ] 1.1 完成执行粒度、责任及兼容方案和独立设计审阅。规模小、难度中，无依赖；完成标准是确认方案及全部重大设计发现解决。
- [ ] 1.2 实现闭合RoleComposition/Step与确定性私有角色包编译/验证。规模小、难度中，依赖1.1；同技能复用/版本冲突、范围/上限、角色级delivery、旧模板输入/产出约束及role.json再编译匹配有明确路径。
- [ ] 1.3 实现预览/原子保存/按固定引用读取的kind=role接口分支及通用技术标记准入。规模小、难度中，依赖1.2；旧DTO/授权保持、预览无写入、原事务覆盖、重复/不可变冲突和隔离明确。
- [ ] 1.4 实现角色表单、技能步骤顺序/条件/完成、预览保存及复制编辑。规模小、难度中，依赖1.3；复用Console样式，未知保存结果与过期响应不覆盖草稿。
- [ ] 1.5 接入原受保护页面view=roles、原工作方法入口与任务准备的固定引用选择。规模小、难度低，依赖1.4；从模板导入、从空白创建、保存后显式选用均不自动创建任务。
- [ ] 1.6 更新指南/稳定规格，执行一次范围静态检查和最小离线compile-only检查，产出Worker双报告后独立实现审阅。规模小、难度低，依赖全部实现；真实执行和逐步强制未验证。

## TODO

执行粒度已确认；完成有界独立设计审阅并吸收发现后发design-ready，再按现有工厂/文件集合同实施完整路径。

## Acceptance

1. 用户可以从空白组合角色，或由默认模板派生；自定义名称、目标、技能、流程和产出，不改业务角色枚举或Worker启动Profile（1.1–1.5）。
2. 两个不同角色能引用相同research/domain-modeling/handoff固定版本，各自有不同流程与交付要求；复用不是拷贝新技能正文（1.2–1.5）。
3. 流程配置与真实执行粒度一致；声明式步骤、模型遵循和平台正式状态明确区分，不宣称未实现的逐步骤强制/恢复（1.1、1.2、1.6）。
4. 原默认角色/保存配置/已冻结任务保持可用；角色/技能更新不重写旧任务，不通过组合获得额外工具或授权（1.2、1.3、1.5）。
5. 缺引用/版本/可用工具、无兼容Worker、结果未知或配置变化时返回明确缺口；不自动换角色、重放未知写入或扩大作用域（1.2–1.5）。
6. 有中文配置指南、真实检查记录和未运行限制；独立实现审阅通过后才可归档（1.6）。

## Verification

目前仅定向静态读取配置/工厂/图流程/Worker/Profile与相邻页面。未运行生产模块、测试或实际任务。完整设计后按根预算执行一次范围静态检查和适用最小compile-only检查；Maven离线且使用C:/green/bin/setup-jdk25.ps1，不下载依赖或生成发行包。无默认测试、模型或运行环境验证；静态及编译不作为行为通过证据。

公开合同/兼容设计采用独立设计审阅；完成实现后使用独立fd-review，两者不互相替代。设计待独立审阅；未编译或实现。

## Sources

- Issue: none
- 用户确认角色由技能集合和流程编排构成，共同能力应可组合，角色不固定；进一步选择同Worker执行角色流程，跨Worker由平台编排。
- FD-039/040 的附加Skill资源和指南；ConfigurationContract.Method/Assembly、ExecutionConfigurations.content/propose/defaultCandidate、AgentFactory.prepareAuthorized。
- GraphContract.Task/Node、GraphRunner.prepare；Python execution_configuration.load / ProcessProfile；configuration-methods.js 的基础方法与附加Skill入口。
- Planner Session：composable-role-20261024-cf71803a；真实来源：FD-041-000002-design-requested。
