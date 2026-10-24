# FD-041 独立设计审阅 r1

结论：**changes-needed**。同一 Worker 内执行多技能流程、私有 Skill 文件包编译为原 v1 Candidate 的总体方向可行；以下三项设计缺口解决前不宜 design-ready。本报告不是实现审阅或运行通过证明。

<!-- aiw-data: {"schema":"aiw.fd.evidence.v1","kind":"design-review","fd":"FD-041","source_event":"FD-041-000002-design-requested","session":"fd041-design-review-20261024-9f24b8d1","recommendation":"changes-needed","reviewed_commit":"30c4e6e","findings":["DR-1","DR-2","DR-3"]} -->

- 审阅对象：[FD-041_FEATURE.md](../FD-041/FD-041_FEATURE.md)，设计提交 `30c4e6e`。
- 独立审阅 Session：`fd041-design-review-20261024-9f24b8d1`，不同于 Planner `composable-role-20261024-cf71803a`。
- 实际来源事件：`FD-041-000002-design-requested`。未领取或发出事件，未修改 FD、实现或生命周期。
- 范围：只读角色声明、配置装配/保存、Skill 文件集和 Python 动态配置合同；其他任务的脏文件排除。

## 必须修正的发现

### DR-1：默认角色 Skill 不是无条件可组合的方法，模板导入需要明确兼容规则（高）

位置：FD 第 38–54 行；`src/main/resources/execution/software-catalog.json` 的 `goal-discussion`、`software-requirements-analyst`、`workflow-stage-reviewer` 等 Skill；`workers/process-python/execution_configuration.py:70`。

事实：现有 primary Skills 仍带有强角色边界。`goal-discussion` 只适用于该角色，禁止 workspace 读取并固定 REPORT；需求分析 Skill 明确要求不做方案设计；阶段审阅 Skill 只用于 `workflow-review` 并固定最终 VERDICT 行。Python 把所有 Skill 入口作为执行指令提供给模型。附加的九项方法 Skill 保留 primary role，也不会自动消除这些旧角色约束。

缺口：FD 允许将任意固定 Skill 放入步骤、修改产出并从默认方法导入，但未规定哪些公共 Skill 可作为自由组合步骤、旧角色 Skill 的适用条件如何校验，或模板导入后哪些更改必须拒绝。例如，导入 goal-discussion 后增加代码研究步骤并选 WORKSPACE，将产生相互矛盾的指令；把需求分析与架构角色入口同时作为步骤也存在冲突。仅重编译生成的 role.json 能证明结构一致，不能证明这些指令兼容。

可行修正：在 FD 明确区分“可组合方法”与“带固定职责/输入/结果约束的旧角色入口”，定义默认模板的实际导入映射及冲突拒绝规则。至少为有代表性的 goal-discussion、requirements、reviewer 模板写出具体步骤、工具/产出条件和一个被拒绝的修改例子。若暂不抽取旧入口中的纯方法，应显式保留其限制并阻止不兼容组合，不能依靠一句新入口提示覆盖旧 Skill，也不能导入一个只有名称的空壳步骤。无需改变现有默认任务行为。

### DR-2：声明缺少角色级交付要求，无法完整表达可配置角色（中）

位置：FD 第 31、38–42、54、83 行；`ConfigurationContract.Candidate.outputContract`。

事实：当前 RoleComposition 列举了 goal、resultKind 和各步骤 completion，没有角色级交付说明。现有 Candidate 已有独立 `outputContract` 字段。

缺口：resultKind 只决定 REPORT/VERDICT/WORKSPACE 外层格式；step.completion 表达单步何时完成，都不能明确表达整个角色应交付哪些内容及其质量要求。验收 1 和角色定义却要求用户能够自定义产出。这一信息若只由编译器生成固定句子，会丢失用户对角色的核心配置。

可行修正：增加一个有界、可读的角色级 `delivery` 或 `outputInstructions` 字段，包含在 role.json、确定性摘要、预览、保存、读取/复制及表单中；编译进原 Candidate.outputContract，同时始终保留 resultKind 对应的固定协议要求。给出两个共享相同技能、但目标和整体交付要求不同的角色示例。该修正不需要扩大 v1 Candidate 字段集合。

### DR-3：回退策略须覆盖组合配置的续行，不能暗示旧服务仍能解码（中）

位置：FD 第 40、58 行；`ConfigurationContract.java:31`；`ExecutionConfigurations.java:111`、`:135`。

事实：现有 Control Plane 解码 Method 时用 id 规则校验 name。FD 将 name 放宽为人类文本，所以新的中文角色名在旧服务中无法解码。续行复制原绑定，不等于后续读取该配置能成功；原 get 路径仍解码 Content/Candidate/Method。

缺口：FD 已写“新配置应暂停使用”，但随后写“正式续行保持其原冻结配置”，可能被理解为组合角色在旧服务上仍可续行。保持字节不可变与旧版本可执行是两个不同保证。

可行修正：明确回退后不得新建、准备、重试或续行使用组合配置的任务，原冻结资料仍保留；恢复新版后才可继续。既有内置方法配置按原合同继续使用。或提供另一种有静态依据的旧版兼容策略，不能仅以 Candidate 外形未变证明语义兼容。

## 已有设计依据和非阻断事项

- `SkillFiles.configured:45–58` 已支持 Owner/仓库限定的 `aiw.skill.files.v1` Document，且 `SkillFileContract.Document` 校验文件顺序、摘要、大小和精确 SKILL.md。SKILL.md + role.json 可以直接复用该机制；编译器需保持排序并在配置保存前完成等价验证。
- Python `execution_configuration.load:94–129` 接收精确 v1 字段并从 Method 构造 ProcessProfile，没有业务角色枚举白名单。保持原 Candidate 形状支持现有 Python 消费路径，是有静态依据的设计判断，尚非实际运行结果。
- 原 `registerAsset` 和 `saveAuthorized` 使用同一个 AuthorityRepository。新增外层事务和原 boundary.lock 可以容纳角色资产注册及配置保存；必须放在经 Spring 代理的事务入口，避免只依赖同类自调用上的事务注解。当前设计已要求整体回滚。
- 显式 `kind=role` 查询参数保留旧 body 合同；复用 `/ui/configurations?view=roles` 可避免新增页面白名单。本次未修改也未依赖脏权限文件。
- 最多八步、六种共享 Skill、八知识，与现有 protocolSkills 八项/skills 十六项上限留有空间；重复引用一致性及未知保存结果处理已覆盖主要失败路径。
- 角色流程仍是模型遵循的说明，跨 Worker 仍是平台编排；FD 已避免把提示步骤描述为强制调度或独立通过的审阅。

## 证据、执行记录与限制

实际只执行了本地只读发现命令：

1. PowerShell `Get-Content`：四项 core 提示、work-management、fd-workflow planning、FD-041；并执行 `git status --short`。中文 FD 在默认输出编码下显示异常，随后改用 UTF-8 Python 读取，没有修改文件。
2. PowerShell 内联 `python -X utf8 -`：按行读取 FD、ConfigurationContract、ExecutionConfigurations 的定向片段；报告三个猜测路径不存在，没有运行生产模块。
3. 内联 Python 调用 `rg --files` 定位 SkillFiles、SkillFileContract、ConfigurationController、execution_configuration。首个定位命令含不存在的 `src/main/resources/execution/skills` 路径，失败后只做一次删去该错误路径的更正重试；随后读取对应文件和 catalogue 的相关 Skill 内容。
4. 针对 DR-1 的具体证据缺口，追加一次有界读取：解析 catalogue 得到真实 default primarySkill 映射及相关入口前 65 行，读取 AuthorityBoundary；查找既有 design-review JSON 范例未得到文件。
5. 写入报告后，执行一次只读内联 Python，解析同名 JSON，核对 Markdown 中恰有一个 aiw-data 注释且关键元数据、三项发现编号一致；通过。

未运行：测试、编译、服务、浏览器、模型任务、网络、依赖下载、工作流 claim/emit、其他代理、git 提交或归档。未执行静态验证脚本或生产代码。本次仅新建此报告和同名 JSON 证据。

剩余风险：尚未实现事务入口、闭合 DTO、确定性编译和界面状态处理；上述静态合同符合不能代替实现审阅。用户选择的模型内部多技能流程也尚未获得真实模型行为证据。
