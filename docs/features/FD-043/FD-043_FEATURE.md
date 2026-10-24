# FD-043: 吸收委派判断与可追溯交接及执行记录

**Status:** Design
**Revision:** 2
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户提供Harness/Sub-Agent文章并要求吸收有价值部分。已讨论结论：吸收委派判定、可回查的证据交接与消耗/结果记录；不照搬1万token、0.3开销比、60%节省或固定厂商型号。保持同一Worker角色内多技能、平台跨Worker编排，以及先记录不增加预算拦截。

事实：平台已有可组合角色、共享research/handoff等技能、冻结SkillFiles、图编排及execution_meter/operation/delivery；账本保存真实token/耗时/费用及UNKNOWN标记，但人类配置入口缺少简洁的关联记录视图。规划和交接已有部分方法，本次补足委派原因、决策边界和原证据索引，不另建生命周期。

## Options and decision

仅增加文章笔记不能落实可配置能力或人类记录查看；另建自动Sub-Agent/固定专家进程、模型路由硬阈值和预算门禁违反已确认方向。选择：新增可组合delegation-planning Skill；增强现有规划/共享证据/交接方法；基于现有账本提供当前Owner的只读执行记录投影和页面。

不新增预算、任务自动派发、工具权限、依赖或数据库表/迁移，不重新实现采集。没有采集的启动开销/模型质量分数不填估计值；结论自报不等同独立验收。新增只读kind分支涉及数据范围和兼容，取得有界独立设计审阅后才就绪。

## Solution

### 委派判断和证据

新增public delegation-planning-v1，入口可单独勾选，支持本地继续、隔离研究、独立审阅和独立并行的建议。以依赖、共享决策、上下文/证据、完成标准和真实工具为依据；连续debug保持状态，独立审阅不受输入量阈值限制。模型建议只用已提供/已批准的配置或能力要求，不改变本次固定模型。结果写入原REPORT/VERDICT/WORKSPACE描述字段，不增新顶层输出Schema、不直接spawn或创建Task。

在implementation-planner的既有task-methods、engineering-artifact-protocol的既有evidence方法和handoff入口补实际可用的决策/交接要求：有界问题、输入与版本、重要决定/反证/失败、原始产物/证据位置、访问缺口和下一个消费者；摘要可回查，关键证据选择性重读有理由即可，不机械禁止重读。原未知操作与已消耗权限/重试限额保留。

只为新Skill追加一个不可变资产与index.files，旧19资产、11默认方法及profiles不改；原文件变化由SkillFiles生成新manifest版本，已有任务仍保留旧字节。三语言独立副本同步。角色和原附加列表增加中文标签，不新增Worker类型。

### 只读执行记录

新增ExecutionObservations投影，数据来自现有execution_meter，JOIN execution_policy按服务器currentOwner过滤、JOIN work_task和匹配task/attempt的execution_attempt，LEFT JOIN固定配置及正式delivery。按attempt创建时间倒序最多100条；明确是最近尝试的记录，不是全局总计。

显示task/attempt/plan、任务标题、工作方法/主技能、实际已选模型/runtime/profile、执行状态与上报verdict、实际累计输入/缓存/输出token、已结算耗时/费用（含原价格unit）、记录状态和UNKNOWN标记、结果revision/正式交付digest与source引用及未检查/未决项数量。缓存token是输入的子集，不重复相加。未知用量和未开始项不伪装成已知零；费用缺计价或未知不伪装0；只显示已有持久数据，不推算运行中最终耗时或质量。

配置工作方法名通过当前Owner的固定配置引用解析，组合角色读原role.json的人类名称；请求内缓存同一配置读取。绑定Owner不一致拒绝，不按机器ID猜归属。未配置的旧任务用主Skill标识且标明范围，不伪称某业务角色。只返回允许展示的元数据，不返回lease token、凭证、机器会话、私有正文、命令参数或完整completion_summary。正式source最多显示16项并给总数，完整事实仍在原交付记录。

可记录的重复读取：仅对显示attempt内、已成功的READ操作，按相同resource与result_digest统计再次读取数；它是现有工具记录的计数，不代表浪费、所有读取或对结果质量的判断。失败、UNKNOWN和不同结果不算同一重复；不据此限流/阻断。

不采集/不猜测overhead比例、真实质量分数、节省百分比、未出现的模型用量，页面明示未采集与自报结论性质。现有账本更新/结算逻辑完全不改。人工校正可能补充time/cost但原UNKNOWN标记保持，因此并列返回/展示已有值和历史标记，不因标记掩盖已存校正值。

### API和页面

复用POST /api/r1/authority/execution-configurations/state?kind=observations，输入仍为闭合Empty，输出aiw.execution.observations.v1只读投影；新增分支在现有role(kind)校验前识别，原method/role行为保持。沿用该路径真实session/CSRF/操作角色及Owner边界，不新增匿名或机器读取入口，不增加权限。只读查询无写入或外部调用。

页面/ui/configurations?view=observations复用原已保护路径、Console外壳/样式。提供简洁表格、指标缺口和可展开证据引用，手动刷新，不显示JSON，不自动执行任务。角色页和自定义工作方法区提供链接，不改已有脏旧模板/权限文件。DOM以textContent/value展示用户文字，无自动打开证据或外部访问。身份变化或过期响应不混入当前账号记录。

### 兼容与边界

不改变账本/Guard/v1 Candidate或Worker传输协议、数据写入/计价/结算、机器能力及模型选择。新投影不创建权威记录，字段含义明确为已有事实，旧任务/重试/问答续行不更新Skill内容。旧配置缺记录时显示范围限制，不假装零成本或无执行。

新增分支随Control Plane资源正常更新，旧Worker继续原合同；回退旧Control Plane没有新记录视图，但账本与原配置保留。新技能引用在旧目录不存在，回退前暂停新技能配置的创建/准备；已有固定任务按原冻结文件合同，不自动换角色/重建未知任务。无新迁移，无部署/服务/网络授权。外部网页上轮已阅读并交叉核对官方来源，本轮不重新联网。

## Scope

新Skill及显式注册、3组副本；现有3处方法资源；ExecutionObservations/配置只读入口/页面查询视图；新页面JS/CSS和现有干净角色/方法入口标签；中文指南及稳定规格、FD证据。主工作区只提交本FD范围，保留既有秘书/授权/旧模板等脏改动。

不做自主派发器、全局token预算/费用阈值、固定厂商模型路由、整个遥测平台、学习评分/排行榜、同Worker技能独立Task化或发布部署。共享方法补充是提示方法，真实模型遵循尚需后续有界评估。

## Work items

- [x] 1.1 明确吸收内容/现有基础/只读Owner范围和缺测含义，完成有界独立设计审阅。规模小、难度中，无依赖；无重大未决问题。
- [ ] 1.2 实现delegation-planning可配置Skill、追加公共资产/index并同步3语言副本。规模小、难度低，依赖1.1；旧19资产/11方法不变，入口不是自动spawn权限。
- [ ] 1.3 增强规划、共享协议和handoff可追溯证据方法，同步各自3语言副本。规模小、难度低，依赖1.1；原输出/未知状态/权限边界保持，旧任务字节不追写。
- [ ] 1.4 实现当前Owner只读记录投影，准确关联attempt/模型/配置/交付，UNKNOWN与同结果读取计数保持真实。规模小、难度中，依赖1.1；无写入、秘密字段或推测质量。
- [ ] 1.5 接入原受保护的kind/view分支、新记录页和现有角色/方法入口，新增Skill友好名称。规模小、难度中，依赖1.2、1.4；手动刷新/范围提示，无自动执行和JSON干扰。
- [ ] 1.6 更新指南/规格，一次范围静态检查与最小离线生产编译，提交Worker双证据、独立实现审阅后归档。规模小、难度低，依赖全部实现；不把静态和编译当模型/数据库/浏览器通过。

## TODO

独立设计审阅ready；按现有Skill/配置/账本接口实施、独立审阅、范围归档。

## Acceptance

1. delegation-planning可以组合给现有角色，输出有依据的委派建议，不自动派发、固定模型/阈值或新增Worker类型（1.2、1.5）。
2. 规划/交接保存可回查的原始证据、版本、决定/失败/未知状态及下一消费者，摘要不冒充权威或新权限（1.3）。
3. 当前账号可查看最多最近100个有账本记录的尝试，真实模型/用量/耗时/费用/结果与正式引用可关联；UNKNOWN/未采集不伪装0或质量评分（1.4–1.5）。
4. 非本Owner记录/秘密不可读取，入口保持登录/CSRF/角色，不新增机器读取；查询不写状态、不新增预算拦截，重复READ计数不是浪费判定（1.4–1.5）。
5. 旧资产/方法及冻结任务兼容，新增包/副本/manifest一致，当前文档/稳定规范与证据边界相符，独立实现审阅通过后归档（1.2–1.6）。

## Verification

默认一次范围diff --check和只读目录/索引/hash/副本/路由/SQL Owner条件/字段/UNKNOWN/DOM引用与旧合同比较，随后C:/green/bin/setup-jdk25.ps1 + mvn -o '-Dmaven.test.skip=true' compile。无测试、模型、生产方法/脚本、SQL/DB/服务、HTTP/浏览器、网络/下载、打包/部署/重启。仅独立设计与实现审阅由fd-workflow明确授权，其他代理预算为零。静态和生产compile不能证明实际技能产出/数据库查询/前端运行。独立设计[r1](../reviews/FD-043-design-r1.md)及同名JSON为ready，特别保留校正值/UNKNOWN和冻结角色名边界。

## Sources

- Issue: none
- 用户确认吸收文章有价值部分；角色内多技能/平台跨Worker编排、数据先记录不限制的既有决定。
- 文章 https://www.heyuan110.com/zh/posts/ai/2026-04-13-harness-subagent-architecture/；上轮已核对Cognition/Anthropic一手说明，采用方法而非未经验证的数字和品牌规则。
- 现有COMPOSABLE-ROLES/WORKER-OPTIONAL-SKILLS、planner/handoff/protocol方法、SkillFiles/index/catalogue、ExecutionLedger与V12/V13、Attempt模型字段及配置页面。
- Planner/Worker Session delegation-methods-20261024-8a2bb0a5；真实来源FD-043-000002-design-requested。
