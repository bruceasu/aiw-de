# FD-044: Worker 上下文、可组合能力与人工交互补齐

**Status:** Open
**Revision:** 3
**Priority:** High
**Evidence policy:** Dual

## Problem

用户确认将 Pi 调研中九项有价值机制补充到系统：技能入口渐进加载、当前补充与后续排队、模型/展示结果分层，以及上下文压缩、并发修改协调、执行扩展点、阶段展示、能力包和探索分支。此前选择继续适用：同 Worker 使用角色内多技能，跨 Worker 由平台编排；消耗仅记录，不新增预算门禁；保留旧任务及固定配置。

事实：FD-005 的 TypeScript runtime-core 已有工具资源协调、HistoryCompactor/原文分页、模型响应完整性保护和收尾；Python 受控 Broker 已统一串行操作，API 有取消、终态和计量保护。SkillFiles 冻结完整文件，但三语言将所选入口正文全部注入。已有组合角色、固定资产、正式问答续行及 FD-007 指令修订；这些与执行中新增输入不同。历史 docs/worker-pi-integration-plan.md 不代表所有内容已实施，不能据其重做基础。

## Options and decision

仅补文章/方法说明不能交付交互和执行能力。替换全体运行时为 Pi、让 Skill 自主装代码/代理或绕开冻结任务不符合现有边界。选择：沿现有资源/运行时/Broker/角色和受保护配置路径补纵向能力。

人机实时交互和历史压缩先在已具备动态配置/受控工具的 Python API 后端落实；其他后端明确显示真实支持矩阵，不静默换后端或伪称实时支持。渐进入口覆盖三语言既有 skill-files 读取链。已有 TypeScript 压缩/资源协调继续原行为。九项均有可用入口或已有实现的明确接入，不声称全运行时能力相同。

## Solution

### 1. 渐进技能与能力包

保持完整 Skill 字节、manifest、已有固定资产和传输 shape。默认主技能、engineering-artifact-protocol 保持完整入口；其他已选技能先给 ref/version/有界 description/目录及明确读取指令，执行相关步骤前读其完整 SKILL.md。无有效描述时保留正文，避免隐式漏合同；不凭文件名猜技能语义。read_skill_file 继续精确读取已冻结字节，不能发现未批准技能或执行脚本。三语言使用相同规则。

能力包是共享 Skill 引用、工作说明、工具需求的可复用组合，不是另一种 Worker 或角色生命周期。定义 aiw.capability.pack.v1，含服务器生成短 id、name、repositoryRef、description、skills（固定引用）、work 和原闭合枚举内的 toolRequirements；不包含可执行模块、网络安装、任意工具定义或模型路由。保存为既有 Owner/仓库范围不可变 knowledge 资产，服务核对包内固定 Skill 引用；角色编辑器可保存已选步骤为包，再导入任一新/旧角色步骤。选择工具仍由原角色编译器和授权校验；六技能/八步等旧上限保持。

### 2. 受控 Worker 工作台与输入合同

新页面 /ui/configurations?view=workbench，沿用 Console 外壳，列当前 Owner 最近尝试并可展开阶段、操作记录、输入收据、探索记录和能力支持。手动刷新、文本安全渲染、身份/epoch 检查，无 JSON 倾倒。任务详情/配置/角色页提供入口。原观察页与 v1 DTO 保持。

新增 aiw.worker.workbench.v1 内部边界。人类使用已保护的 execution-configurations/state/candidates 的明确 kind 分支；机器复用 execution-context/read 的新 kind，原 fixed/read 和 method/role 不变。无新匿名/个人机器路线，不改已有脏 AuthorityHttpConfiguration。人类 currentOwner+session/CSRF/operator，机器精确 live task/Attempt/Lease；不返回凭证、机器会话、原始完整提示词或隐藏推理。

新增便携 V19 追加表，存工作台 session（attempt、task、owner、后端、phase、更新时间）、输入收据（owner+requestId 唯一、原 attempt/revision、mode、文本、digest、state、交付来源）和探索节点。只给新运行时主动开启的尝试建 session，不回填旧数据，不修改原指令/输入快照或账本。Owner 由现有 execution_policy 归属确定，不接受客户端 owner；受控事务沿用 boundary.lock，SQLite/PostgreSQL 使用 TEXT/BIGINT/INTEGER、参数 SQL。旧服务回退不读新表，旧任务仍原冻结内容；回退前暂停新交互会话，新记录保留不自动重放。

输入分 STEER（下一模型请求边界补充当前工作）和 FOLLOW_UP（本轮报告结束后、正式结果收尾前继续处理）。仅同一原任务/Attempt/指令修订和原授权范围，不能替代换目标的正式修订或创建别的业务任务。文本有界，系统生成 requestId，重试相同 ID+digest 返回原记录，内容冲突拒绝。机器新kind不能直接沿用原read中running=false的宽松校验：open、reserve、link、ACK、drain/seal必须明确verifyLease(task,lease,true,true)，确认Task/Attempt均RUNNING、无stop request、当前live lease及原instructionRevision一致，归属由服务器固定上下文取得。人类发送也核对当前Attempt/revision及session阶段。只给明确开启支持的 Python API session 发送；老 Worker/Codex/Go/TS 不显示虚假可用控制，仍可使用原问答/指令修订流程。

Python API wrapper 在模型请求前收集 STEER，在原 SDK input 的有界、验证过的前缀位置追加 user-data 消息并在后续请求重建相同顺序；不修改冻结 system/task/Skill，不把文本当权限。消息领取在同一事务中将QUEUED变为RESERVED，保存不可变批次ID、mode、消息序列与内容digest；模型操作begin后、实际模型请求前，将批次绑定该Attempt的确切STARTED MODEL操作。只有该绑定操作成功且模型终态确认后ACK为DELIVERED（意为已递交，非理解/验收），不能用任意其他成功MODEL冒充。未知请求/失联/失败后批次保持RESERVED或显示UNKNOWN，不自动返队、换ID或重新执行未知工具。SDK input 前缀变化无法确认时停止依赖操作。

当 SDK 产生最终报告时，Worker 原 Attempt 内读取 FOLLOW_UP，有则追加固定补充输入继续同一角色，最终报告需覆盖原任务与已递交补充。所有模型调用和摘要共用原总体期限与实际总调用上限，不因新一轮重新获得次数。上下文只属于当前 Attempt，不伪称持久模型会话。

机器DRAIN/seal与人类enqueue共用锁：有QUEUED则原子领取剩余输入为下一批；没有QUEUED、RESERVED/UNKNOWN批次及STARTED/UNKNOWN操作时才将session标FINALIZING，拒绝后来新增输入；有待处理输入则不封口并继续。已进入收尾或终态后未执行输入显示实际状态，不能默默重启 Task。取消只撤回QUEUED；RESERVED已经领取不能显示已撤回。人类写入统一保留owner+requestId的原命令digest/结果收据，响应未知先核对原收据、仅重试同一ID+内容，不能生成新ID重放。停止/撤权仍沿原机制，不承诺撤销已发生副作用。后端不支持时不自动降级或假装成功。

### 3. 上下文、工具结果与执行扩展点

Python API 引入与已验证 TypeScript 算法同原则的 HistoryWindow：保留冻结 instructions、近期对话和完整 call/result 配对；只压缩可见旧消息，不包含隐藏推理。原可见文本保存为 Attempt 内有界源并提供分页读取；摘要标派生数据、给来源 digest，保留目标/决定/未完成/未知副作用/版本和证据入口，不更新权限或续行预算。不以字符估算当精确 token。

使用同一个已选模型和受控 MODEL 记录进行有界摘要，计入实际用量、原期限和原模型调用总数；不换模型、不增加自动重试。最多两次成功压缩；失败、无安全切点、摘要过大明确失败，不假装已保全历史。输入/保留容量是技术安全边界，非新费用或执行预算门禁。可用原配置明确技术阈值，不声称固定节省比例。

工具结果内部保留完整结构与证据摘要，为模型提供有界 content 和 sourceId，详情保留结构化 exit/status/digest/完整性/分页引用。模型不接收整段 UI metadata；人类工作台只展示已有受控操作元数据及来源，不默认上传原始日志或仓库正文。原始大结果仅在当前 Worker Attempt 的上下文源中可读，不能称持久云端日志。

Python Broker 统一 begin/execute/finish/unknown/settle 扩展点；复用原可重入锁覆盖完整读改写和命令/Git互斥，不增加进程间锁保证。未受控 API 原 ToolContext 锁继续保留。统一输出适配和上下文扩展在现有受控边界内，不允许技能注册任意可执行钩子。TypeScript 原工具宿主和资源协调不重写。

### 4. 阶段与探索分支

工作台区分执行前、模型处理中、工具处理中、等待后续输入、收尾以及平台真正完成/失败/等待人工。机器 phase 是带时间的上报提示；最终状态只来自原任务/Attempt，停止请求不显示已停止。操作表直接反映 STARTED/UNKNOWN/成功/失败；最后上报陈旧时明示时间，不猜当前细分状态。不会由一次文本结束或自报 verdict 推导完成。

探索分支是同一任务下可回查的方案记录树：保存人类可读名称、问题/结论、parentId、原 Attempt/revision、可选结果版本及选择/放弃原因。父节点与引用必须同 Owner、任务，短 id 服务器生成，不允许循环改父；选择记录与原节点分开保留。页面比较同级方案；可明确把一个方案请求送入上述当前/排队输入，仍受原任务范围限制。

这不是底层模型 transcript 的 /tree 回退或自动执行分支，也不修改物理工作区。目标是满足“保留 A/B 探索过程、试另一方案并比较结论”，不新增平台 Task 状态或绕过跨 Worker 编排。记录结果版本只接受已存在的同任务尝试结果，不猜 commit 或自动 git reset。

## Scope

上述九项的完整有界实现：三语言 Skill 入口、Python API 上下文与交互/工具结果、既有受控 Broker 扩展、Owner 工作台、能力包组合 UI、探索记录树、稳定规格/指南/实际证据。保留当前主工作区模式，只提交本 FD；已有秘书和旧模板/权限脏改动不纳入。

不安装/依赖 Pi、替换 SDK、自动代理/模型路由、全局财务或质量门禁、自由执行插件、模型 transcript/文件回滚、跨进程锁、部署/重启。Codex/TS/Go 实时输入和新增 Python 历史压缩不伪称已支持，真实支持矩阵是验收的一部分。

## Work items

每项规模小，难度低或中，预计半日内可独立评审；实现前保持编号。

- [x] 1.1 完成九项映射、兼容/Owner/输入状态设计和一次有界独立设计审阅。中；无依赖；无重大未决问题才就绪。
- [ ] 1.2 Python 固定技能入口渐进披露，必需入口与无描述回退保持。低；依赖1.1；原冻结字节/工具不变。
- [ ] 1.3 TS/Go 对齐相同入口规则及旧传输兼容。低；依赖1.2；无动态配置后端扩张。
- [ ] 1.4 V19 与工作台闭合合同、Owner/Lease/session状态。中；依赖1.1；旧记录不回填/不改原快照。
- [ ] 1.5 人类输入的幂等发送/撤回、机器读取/ACK/seal与并发拒绝边界。中；依赖1.4；只绑定原Attempt/revision/原权限。
- [ ] 1.6 Python API 输入重建、终态ACK、FOLLOW_UP同Attempt续行及真实支持矩阵。中；依赖1.5；总期限/总调用次数不重置。
- [ ] 1.7 Python 可追溯历史源与安全配对压缩。中；依赖1.2；摘要同模型/受控计量，未知与技术限额明确。
- [ ] 1.8 Broker结果content/details分层、分页源、原锁与统一收尾扩展。中；依赖1.7；无新增权限/秘密泄露或锁保证扩大。
- [ ] 1.9 能力包服务：原私有knowledge资产内固定技能组合，核对引用和仓库，导入仍由原角色编译。中；依赖1.1；不执行包代码或改变旧资产。
- [ ] 1.10 角色页保存/导入能力包的可读UI。低；依赖1.9；既有数量/输出约束保持，无JSON输入。
- [ ] 1.11 Owner/任务范围探索树、不可变父引用及选择记录服务。中；依赖1.4；只接受真实结果引用，无代码回退。
- [ ] 1.12 工作台最近尝试/阶段/输入状态/安全控制入口。中；依赖1.5、1.6、1.8；最终状态来自平台，陈旧上报不猜。
- [ ] 1.13 探索分支比较、保存与明确递交UI及相邻页面链接。低；依赖1.11、1.12；身份与异步结果隔离。
- [ ] 1.14 当前指南、历史计划状态说明、稳定规格、一次静态检查/编译与Worker双证据。低；依赖全部实现；不声称真实运行。

## TODO

独立设计r1已ready，RESERVED批次/精确MODEL绑定/严格RUNNING Lease及DRAIN/seal修正已纳入。1.2–1.13 源码已实施，正在进行范围静态检查、生产编译及双证据；最终由独立Reviewer判定，不新增测试授权。

## Acceptance

1. 所选附加技能入口可按需读取，必需工程/主技能和没有可靠描述的入口保留，三语言冻结字节兼容（1.2–1.3）。
2. 当前Owner只能控制自己的明确支持Attempt；STEER/FOLLOW_UP/撤回/已递交/收尾状态明确，未知不重放，旧Worker不伪称支持，原任务及授权不被覆盖（1.4–1.6、1.12）。
3. Python长任务可安全压缩并回查可见原文，完整tool pair/未知效果/固定依据保留，摘要同模型计量且不重置限额（1.7）。
4. 工具返回和阶段可读、可追溯；现有修改互斥/取消/收尾正确，文本结束不当作完成，现有TS机制继续（1.8、1.12）。
5. 人类能保存/复用技能能力包，原Owner/仓库/版本/角色编译和工具权限保持（1.9–1.10）。
6. 能保存、比较、选择同任务探索分支并明确发请求，原结果和决定可回查，不伪称模型或文件回滚（1.11、1.13）。
7. 文档/支持矩阵/现有资产/默认合同保持一致，真实静态/编译边界和独立实现审阅完成后归档（1.14及生命周期）。

## Verification

默认一次范围 git diff --check + 仅标准库源码/JSON/hash/Owner SQL/消息状态/DOM引用/目录一致性静态核对。随后 scripts/compile-worker-runtime.mjs（生产语法/临时Go编译、不留分发产物、离线）和 C:/green/bin/setup-jdk25.ps1 + mvn -o '-Dmaven.test.skip=true' compile，分别覆盖受影响模块。UI JavaScript 另做原生node --check语法编译。无测试/测试编译、数据库迁移执行、SDK/模型/Worker/服务/应用HTTP/浏览器、依赖下载、打包部署或重启。因本地缺Python SDK源码，仅补充读取OpenAI官方结果/流式文档核对to_input_list续行表面；未安装SDK，低层ModelSettings/provider实际兼容仍未运行证明。fd-workflow仅授权一次必要设计审阅与独立实现审阅（失败周期最多3轮）；静态/编译不冒充并发、模型方法或运行验证。

独立设计[r1](../reviews/FD-044-design-r1.md)及同名JSON为ready，确认输入领取/撤回竞争已解决。

## Sources

- 用户确认将前两轮九项建议补充到系统；既有主工作区/角色组合/仅记录预算决定保留。
- docs/research/pi-coding-agent-assessment-2026-10-24.md；外部官方资料已在前两轮核对，本实施不重新联网。
- FD-005/007/030/041/043，SkillFiles/Reader、TS runtime-core、Python Broker/API、角色编译/配置API、原Guard与V12–V18。
- Planner/Worker Session worker-context-20261024-61db8e4a；真实来源 FD-044-000002-design-requested。
