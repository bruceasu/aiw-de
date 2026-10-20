# FD-019 独立设计审阅 R1

<!-- aiw-data: FD-019-design-review-r1.json -->

结论：**changes-needed**。同轮复核已确认 D1、D3、D4 的主要设计边界收敛；D2 的输入/结果/运行目录契约与 D5 拆项仍须在 design-ready 前写入 FD。这里只审阅设计，不代表实现验证通过。

- 审阅 FD revision：3。
- 来源事件：`FD-019-000002-design-requested`。
- Planner Session：`fd019-planner-20261021-a71c93`。
- 独立审阅 Session：`fd019-design-review-20261021-r1`。
- 业务依据：ISSUE-00007 Plan revision 3，以及父会话转交的用户三项 yes 和 file-convert help。后续确认优先于 Plan 尚未更新的待澄清文字。

## 发现

### D1 高：执行前创建工作区与既有计划保存校验存在冲突

事实：`WorkflowEditorService.validateSpec` 第 113 行要求需要工作区的角色在实际保存时已有 `repositoryRef`；`draft` 和 `confirm` 都调用该校验。FD 同时要求实际 WorkflowPlan 保持既有校验、无显式工作区的计划直到 start 前才创建目录。

风险：含 coding 等步骤的编辑快照可以保留，但无法提前变成有效 WorkflowPlan；只在 start 钩子创建目录不能解决保存失败。若绕过校验保存，又违背已确认的保存契约。

所需修改：明确区分编辑快照与实际计划，选择并记录完整转换顺序。例如执行入口先为编辑快照分配工作区、填充缺失引用，再经既有 draft/confirm 创建真实计划，最后由用户触发既有 start；或者采用已有校验可接受的受管目标注册方案，并说明物理目录延迟创建的兼容性。明确幂等键、部分失败后的恢复、显式引用优先规则，以及新入口与原 start 的关系。不得静默放宽旧计划校验。

### D2 高：讨论 Worker 尚缺可执行角色、工具边界与结果接入契约

事实：`workers/process-typescript/src/profiles.ts` 的 `ROLE_FILES` 是固定角色集合，当前没有 goal-discussion；`instructions.ts.resolveTaskProfile` 强校验 primarySkill，通用 prompt 还将上游输入描述为 authoritative。`RecommendationTaskPolicy` 与 guidance/start 中的既有冻结分支仅识别 recommendation subject，不能直接覆盖新的 goal subject。

风险：仅增加 capability/Skill 文件无法启动新角色；复用通用 prompt 可能与“附件是数据”冲突。只读文件权限也不等于知识读取范围隔离。现有结果包含报告/输出文件语义，FD 未说明如何成为聊天消息和如何阻止越界载荷。

所需修改：指定新 profile/角色注册、REPORT 结果及 Skill 加载路径、启动配置和可匹配 Worker 条件；明确无 workspace 讨论的运行目录以及仅已选资料可见的工具策略。新角色 prompt 应明确目标正文、附件及知识文本是非指令数据，不继承通用 authoritative 描述的执行含义。定义 task subject、冻结请求关联、报告字段/大小/无效结果规则与完成写回入口；让领取、guidance、start 使用同一 goal policy，并列出需要修改的 Java 与 TypeScript 接缝。保持旧 recommendation 路径不变。

### D3 高：CAS 未覆盖跨记录幂等与删除后结果的原子裁决

事实：FD 规定 expectedRevision、不可变版本、重复转正/创建计划防重和删除恢复后拒绝旧结果，但未给出唯一约束、事务顺序、请求状态与删除世代的具体契约。

风险：单纯先读 revision 后插入版本/消息/计划关联，仍会在并发重复请求或删除竞争时留下重复对象、孤立真实计划、或接受删除前的旧回复。SQLite 和 PostgreSQL 下仅有事务注解不足以表达同一裁决。

所需修改：写明服务端 revision 单调递增及 delete/restore 的世代策略；冻结请求保存 actor、goal ID、来源 revision/世代、requestKey、task ID 和状态；为转正、发送请求和每版本计划交接定义数据库唯一键与事务 CAS 顺序。对 Worker 完成/删除、重复完成、双标签编辑、转正/编辑以及计划创建失败后的恢复给出明确胜负规则。若任务可重试或租约过期，说明如何选择最终可接受结果并避免重复消息；不得只依赖内存锁。

### D4 高：个人私有目标交给通用计划后的读取边界未定义

事实：WebConsoleSecurityService.actor 来源于配置用户名；认证关闭时固定为 `web:local-console`，不是每浏览器或每自然人的身份。FD 只声明 goal 查询按 actor 隔离，同时把目标内容交给既有计划/任务/输出链路。

风险：界面会让用户误以为不同浏览器会话天然私有；目标正文、附件文本或其来源也可能通过通用计划详情、Worker task instruction 或输出下载入口进入不同权限域。此处为未定义边界风险，尚未证明已有接口存在泄漏。

所需修改：声明首版“个人”是现有控制台 principal 的 actor；认证关闭为共享本地身份，不承诺浏览器会话隔离。明确交接计划、编辑快照、推荐请求和附件/知识来源的归属检查及数据最小化规则；若既有计划属于操作员共享域，应在交接时明确展示这一边界，并确认与个人私有验收相容，不能在设计中默认把私有属性延伸到未核查的接口。需保持私有的内容则必须增加对应服务端检查，而不只在 goal 控制器隐藏入口。

### D5 中：Work Items 仍把多个独立结果标为小/中

事实：1.2 同时包含存储/下载隔离、外部进程受限转换、知识选择及来源冻结；1.3 同时跨角色启动、三处冻结策略、结果接入与异步竞争；1.4 同时包含转正交接、持久编辑器、AI Steps 及真实计划幂等；1.6 包含完整 UI 流程和文档。

风险：每项的完成范围过宽，技术接缝与安全决定隐藏在实现中，无法独立判断达到“小、低或中难度、半天左右”的规划要求。

所需修改：按独立结果拆为子项，例如附件存储/下载、转换适配、知识快照；Worker profile/只读输入、冻结调度策略、完成消息接入；正式版本、编辑快照恢复、AI Steps、有效计划交接；UI 草案/对话、资料/转正/编辑交接。给出依赖与逐项完成证据，并更新 Acceptance 映射。D1–D4 的工程决定应先写入 FD，再将相应实现项视为 ready。

## 已覆盖与证据边界

事实：FD 已记录关键缺失可经明确接受转正但保持未就绪、可恢复删除、版本化且旧计划不变、默认 OpenAI 与显式 Codex、临时目录保留，以及 `aiw file-convert -i <file> -o - --format md` 的固定参数调用和不安装依赖。上传不执行 HTML、随机附件 ID、显式附件外发选择等方向合理。

假设：附件外发同意采用产品内逐次/明确选择落实，并非将用户提供 help 视为已授权本会话调用模型。报告未自行裁定新的业务政策；Planner 可用已有契约收敛工程细节，若 D4 确实造成私有性政策冲突再回到用户。

未验证：没有运行应用、迁移、浏览器、多进程、真实模型、文件转换、测试、编译或网络。静态判断不声称 runtime pass。

实际命令仅为 `Get-Content`、`rg` 的本地只读发现以及 `apply_patch` 写入本报告和 JSON；部分首次路径读取失败，随后按真实安装布局修正。未修改 FD、Issue、实现代码或流程状态，未发出 implementation verification 事件。未执行额外验证命令。

## 同轮修正复核

父会话在本轮审阅期间修正了同一 revision 3 的 FD，审阅者随后只重读该文件，没有扩展代码发现范围。

- D1 主要设计问题已解决：无工作区编码计划保留编辑快照，用户明确准备时创建目录并填引用，经旧 editor.draft 保存 DRAFT，随后走既有独立确认。实现必须把目录创建/注册与 plan 关联的部分失败恢复纳入已有幂等规则。
- D3 主要设计问题已解决：已记录数据库唯一键、任务和请求及 revision 同事务、重复请求比较冻结输入、删除/恢复 epoch 与 request ID 去重。实现审查仍须检查实际条件更新是否使删除/结果写回原子裁决，以及租约重试是否只接纳有效终态。
- D4 主要设计问题已解决：已明确 console actor 的私有边界、关闭认证的共享 local-console，以及任务与计划进入既有运维可见面。需在 UI 交接与使用文档说明，不把数据库管理员或管理 API 隔离列为本功能承诺。
- D2 部分解决：新增 TS profile、ROLE_FILES 和启动/管理入口已写入 FD。仍需明确 REPORT 结果/大小/无效处理、无工作区隔离运行目录、仅所选资料可见的工具策略，以及新 prompt 不将资料视为 authoritative 指令。
- D5 尚待拆项；当前六个组合条目未改变。

下一步：Planner 补齐 D2、D5 后可继续既有 design-ready 流程；这两项不需要新业务澄清或自动增加一次设计审阅。以上复核是设计静态证据，不是代码实现完成或运行通过。
