# FD-006: Workspace 管理：目录接入与可选 Git 支持

**Status:** Complete
**Revision:** 19
**Priority:** High
**Effort:** High (> 4 hours)
**Test policy:** Independent
**Evidence policy:** Dual
**Impact:** 单机 Control Plane 可管理普通文件夹和 Git 仓库；Git 初始化失败只产生警告，目录仍可用于直接执行 Run。

## Problem

用户需要在单机 Control Plane 中通过界面登记多个 Workspace 的根目录，并在每个 Workspace 下按需追加多个仓库目录。Workspace 页面需要列表、详情、修改和删除登记的入口；删除登记不删除用户文件。

Git 不是目录注册的前提。根目录或子 Repo 没有 Git 时可以尽力执行 git init；失败只提示警告，不应阻断 Workspace/Repo 登记或目录模式 Run。

现有 `removeChild` 会递归删除目录，与本 FD 已确定的“删除登记保留用户文件”要求冲突。实施时必须改为只解除显式登记；隐含目录没有登记可删。此处涉及用户数据，验收时应检查目录内容保持不变。

## Options and decision

| 方案 | 判断 |
|---|---|
| 保留静态 YAML 作为唯一配置入口 | 配置模型能列出多个 Workspace，但不能在界面创建、编辑和管理；不采用为最终入口。 |
| 在 Control Plane 持久化 Workspace 与显式 Repo，并提供管理 UI | 采用。每个 Workspace 保存独立根路径；显式子目录追加保存，未配置显式子目录时从文件系统发现一级目录。 |
| 将 Workspace 根目录隐式等同于普通子 Repo | 不采用。根目标使用保留的 root 语义和稳定 repositoryRef；可显式选择根目录为目标。

## Solution

Workspace 是持久化的本机项目容器。每个 Workspace 有稳定 ID、显示名称和本机绝对根路径；系统支持创建多个 Workspace，并通过列表、详情页查看、修改、禁用和删除登记。删除登记仅移除数据库信息，不删除本地目录、链接或克隆文件。Workspace 管理数据写入 Control Plane 持久化存储。

Workspace 的目标按以下规则确定：

1. 用户可将 Workspace 根目录明确选为目标，引用为 `workspaceId/root`，相对路径为 `.`。
2. 用户可在根目录下追加多个显式仓库：链接其他本地目录、克隆 Git 地址，或创建空白目录；引用为 `workspaceId/repoId`。
3. 若没有显式子仓库且未明确选根目录为目标，根目录的一级子目录自动成为隐含仓库；没有一级子目录时使用根目录目标。隐含仓库不写入仓库登记表。

显式子仓库优先于隐含发现；开始追加显式子仓库时，隐含列表停止作为新 Run 的选择项。切换目标前要求已有任务完成或取消，避免正在执行的目标路径消失。已有 Root/Child 数据继续兼容。

旧 `aidev.workspaces.*.repositories.*` YAML 配置继续作为运行时后备解析入口；托管登记优先，同名目标不得指向不同路径。新 Run 表单允许手动输入旧 repositoryRef，已有 YAML 配置不因引入持久化 UI 而自动导入、覆盖或删除。配置文档须说明旧入口与托管入口的优先级。

每个 Workspace 的 rootPath 是用户在本机 Control Plane 中登记的目录，可为已有目录或新建目录。Child Repo 路径必须解析到该 Workspace 根目录之下。链接目录的源路径可以在 Workspace 根目录之外，但链接本身位于受管理的 Repo 路径内。Link local 不得递归删除或改写源目录；若用户选择尽力 git init，成功时仅添加 Git 元数据。

Repo 可以是普通文件夹，也可以是 Git 仓库。根目录和新建目录对无 Git 目录尽力执行 git init；本地链接默认不改写源目录，只有操作员显式选择时才在链接源执行 git init。Git 命令缺失、初始化失败或目录只读时，保留 Workspace/Repo 登记并显示警告。失败不能让注册流程或目录模式 Run 失败。

Run 表单从所有 Workspace 的可用目标中选择。Base Revision 留空时使用目录模式，worker 直接操作对应目录，不要求 Git、不创建 worktree、不生成 Git revision/result；读写由操作系统权限决定。同一目标的目录模式 Run 串行执行，Run 内并行度为 1。填写 Base Revision 时走 Git 模式并解析完整 commit ID；不可解析的非空 revision 报错，不回退到目录模式或 HEAD。

## Scope

包含多 Workspace 持久化模型、Workspace 根路径与 Root/Child Repo 目标模式、管理 UI、Initialize/Link/Clone、可选 Git 状态与警告、同机 worker 路径指派、Git/目录两种 Run 模式、串行约束及文档。支持多个 Workspace 同时登记并独立配置。

不包含远程 worker 拓扑、凭据管理、自动 push/PR、Workspace/Repo 递归删除或迁移、多 Repo 原子 Run、分布式锁，或 Clone 以外的 Git 服务集成。目录模式的更改直接留在登记目录中，不提供 Git 回滚或 revision 级结果追踪。

## Files to Create/Modify

| File | Action | Purpose |
|---|---|---|
| src/main/resources/db/migration/V17__workspace_registry.sql | CREATE | 持久化多个 Workspace 的 ID、名称、rootPath、目标模式、Repo 路径、Git 状态和操作警告。 |
| src/main/java/me/asu/aidev/workspace/** | CREATE | Workspace/Repo 领域、持久化、目录登记、Git 初始化、Link/Clone 应用服务。 |
| src/main/java/me/asu/aidev/autonomous/application/WorkspaceRegistry.java | MODIFY | 从持久化模型读取多个 Workspace 和根/子 Repo 路径，不依赖单个静态配置项。 |
| src/main/java/me/asu/aidev/autonomous/application/GitRevisionResolver.java | MODIFY | 仅在用户提供 Base Revision 时解析完整 commit ID；目录模式跳过 Git resolver。 |
| src/main/java/me/asu/aidev/autonomous/application/AutonomousRequirementService.java、src/main/java/me/asu/aidev/work/** | MODIFY | 支持多个 Workspace 的目标选择和 Base Revision 缺省的目录模式任务。 |
| src/main/java/me/asu/aidev/execution/**、src/main/java/me/asu/aidev/worker/protocol/** | MODIFY | assignment 携带稳定 repositoryRef、同机路径和工作模式；目录模式 Git 字段可缺省。 |
| workers/runtime-core/** 及受支持的 worker adapters | MODIFY | Git 模式创建 worktree；目录模式直接使用已登记路径并串行执行。 |
| src/main/java/me/asu/aidev/webconsole/**、src/main/resources/templates/ui/** | MODIFY/CREATE | 多 Workspace 列表、创建/编辑/禁用、Root/Child Repo 模式配置和 Run 目标选择器。 |
| docs/current/05-API-USER-MANUAL.md、docs/current/07-CONFIGURATION-REFERENCE.md | MODIFY | 说明多 Workspace 管理、rootPath、Root/Child Repo 模式、Git 警告和 Run 行为。

## Work items

以下将原 6.3 拆为 6.3–6.4，原 6.8 拆为 6.9–6.12；其余原工作项依次细化为单一可审查结果。实施尚未开始，以下编号从本次设计刷新后固定。

- [x] 6.1 建立多 Workspace/Repo 持久化模型。**Size:** Small. **Difficulty:** Medium. **Dependencies:** none. **Completion:** 唯一 ID、名称、rootPath、目标模式与显式子仓库由兼容 SQLite/PostgreSQL 的增量迁移持久化。**Evidence:** 现有 V17/V18 迁移与 JDBC repository 提供该模型；仅静态检查，迁移执行待 Tester 报告。
- [x] 6.2 实现根目录登记和根目标。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 6.1. **Completion:** 登记已有或新建根目录；`workspaceId/root` 解析到 rootPath，Git 初始化失败仅告警。**Evidence:** 注册允许在已有父目录下创建新根目录；根目标和 Git 初始化告警已有实现，待独立运行验证。
- [x] 6.3 实现一级目录隐含发现。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 6.1. **Completion:** 无显式子仓库时仅发现一级目录，空目录回退根目标，生成稳定且不冲突的 repositoryRef。**Evidence:** 按一级目录生成稳定 ref，并在罕见 ID 冲突时拒绝解析以避免错误指派，待独立运行验证。
- [x] 6.4 实现显式本地链接与空白子目录。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 6.1, 6.3. **Completion:** 路径被限制在根目录内，链接源内容默认不被改写，显式选择 Git 初始化除外；首个显式子仓库切换时保护未完成任务。**Evidence:** append 在根目录下创建直接子项并保护已有任务；登记已有子目录也限制为一级路径；本地链接新增 Git 初始化选项，待独立运行验证。
- [x] 6.5 实现 Git URL Clone。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 6.4. **Completion:** 安全调用及超时；失败不登记不完整目标，错误不暴露凭据。**Evidence:** Clone 使用根目录内应用创建的临时目录，成功后移动到目标；失败清理受限的临时目录，UI 错误仅含退出码，待独立运行验证。
- [x] 6.6 实现 Git 状态、警告与失败恢复。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 6.2, 6.4, 6.5. **Completion:** Git init 失败不阻断目录模式；只清理由应用本次创建且确认归应用所有的目录。**Evidence:** `initializeGit` 将失败转成 warning；Clone 仅清理受限的应用临时目录，待运行验证。
- [x] 6.7 扩展 Worker assignment。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 6.2. **Completion:** 稳定 repositoryRef、同机路径和可空 baseRevision 到达 Worker，旧协议仍可解析。**Evidence:** `WorkerProtocolV1Controller.workspaceResponse` 包含 path、mode 和可空 revision，旧四字段构造器仍存在。
- [x] 6.8 更新共享 worker runtime。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 6.7. **Completion:** Git 模式按完整 SHA 使用 worktree；目录模式直接使用登记路径且不生成 Git WorkspaceResult。**Evidence:** `workers/runtime-core/workspace.mjs` 两种分支及 result/cleanup 分支可静态追踪，待运行验证。
- [x] 6.9 更新 Java worker adapter。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 6.7. **Completion:** 两种 assignment 均可执行；托管目标使用 assignment 路径。**Evidence:** Java adapter 消费 `repositoryPath` 并按 revision 是否为空分流，待运行验证。
- [x] 6.10 更新 Python worker adapter。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 6.7. **Completion:** 两种 assignment 均可执行；托管目标使用 assignment 路径。**Evidence:** Python adapter 读取 `repositoryPath`，目录模式直接使用目标路径并跳过 Git result/cleanup，待独立运行验证。
- [x] 6.11 更新 Go worker adapter。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 6.7. **Completion:** 两种 assignment 均可执行；托管目标使用 assignment 路径。**Evidence:** Go adapter 消费 `repositoryPath`；目录模式直接使用目标目录并跳过 Git result/cleanup，待独立运行验证。
- [x] 6.12 更新 process TypeScript adapters。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 6.8. **Completion:** 两个受支持的 process adapter 一致使用共享 runtime 与两种 assignment。**Evidence:** 两个入口均导入并调用共享 `runWorkerSlot`，两种模式由 shared runtime 处理，待运行验证。
- [x] 6.13 更新 Run 创建与 revision 解析。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 6.2, 6.7. **Completion:** 空 revision 走目录模式；非空 branch/tag/SHA 固定为目标仓库完整 commit，不可解析值拒绝且不回退。**Evidence:** `AutonomousRequirementService.start` 已按空值分流；resolver 现在对完整 SHA 也在目标仓库执行 Git 验证，待独立运行验证。
- [x] 6.14 限制目录模式并发。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 6.13. **Completion:** 同一目标目录任务串行且 Run 内并行度为 1；不同目标独立。**Evidence:** Run 创建约束 maxParallelism=1；`DirectWorkspaceConcurrencyBudget` 在 claim 事务使用共同 admission 锁，并对活跃目录任务的真实路径进行别名比较，防止不同 repositoryRef 指向同一目录时并发；待并发运行验证。
- [x] 6.15 提供 Workspace 列表与详情。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 6.2. **Completion:** 可创建、查看、改名、更新未使用根路径及启停多个 Workspace。**Evidence:** 已有列表和详情控制器/页面支持各操作；根目录表单说明新增目录规则，待独立运行验证。
- [x] 6.16 提供 Repo 管理与状态页面。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 6.3–6.6, 6.15. **Completion:** 可追加本地链接、Clone 和空白目录；显示来源、Git 状态与警告。**Evidence:** 详情页现在同时展示隐含、根和显式目标，继续只允许显式目标解除登记，待独立运行验证。
- [x] 6.17 更新 Run 表单目标选择。**Size:** Small. **Difficulty:** Low. **Dependencies:** 6.13, 6.15. **Completion:** 可选托管 Root/Child 目标，可留空 Base Revision，并能手动输入旧 YAML ref。**Evidence:** `/ui/runs/new` 注入托管 target；`new-run.html` 用 datalist 保留手动输入并允许空 revision，待页面验证。
- [x] 6.18 修正删除登记行为。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 6.1, 6.16. **Completion:** 删除显式 Repo 或 Workspace 登记只修改数据库；隐含目录无删除登记操作；用户文件、链接和 Clone 目录均保留。**Evidence:** `removeChild` 只执行登记记录删除，隐含目录直接拒绝；页面与确认文案明确保留目录，尚待独立运行验证。
- [x] 6.19 保持旧 YAML 目标兼容。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 6.2, 6.17. **Completion:** 未登记的旧 ref 仍可运行；托管/旧 ref 冲突被明确拒绝或保持原路径，不静默切换目标。**Evidence:** registry 保留 YAML 后备解析，并在同名目标路径不一致时拒绝解析；Run 表单允许手动输入旧 ref，待独立运行验证。
- [x] 6.20 更新操作文档。**Size:** Small. **Difficulty:** Low. **Dependencies:** 6.13–6.19. **Completion:** 说明目标规则、Git 警告、目录模式并发与写入风险、YAML 后备及安全删除。**Evidence:** 用户手册和配置参考说明新建根目录、不可回滚的目录写入、revision 校验、安全解除登记及 YAML 冲突。

独立验收由 Tester 阶段承担，按 AC01–AC13 报告已执行、未执行和风险；SQLite/PostgreSQL 迁移检查按实际证据报告，不以静态检查冒充执行。

## Acceptance

| ID | 场景 | 预期结果 |
|---|---|---|
| AC01 | 只登记 Workspace 根目录并选根目标 | Workspace 根目录本身成为唯一目标，relativePath 为 "."，repositoryRef 为 workspaceId/root。 |
| AC02 | 在界面创建多个 Workspace | 每个 Workspace 有独立 ID、名称和 rootPath；列表、详情页可查看、修改、禁用和删除登记，删除不移除文件。 |
| AC03 | 追加多个显式子仓库 | 本地链接、Git 克隆和空白目录均位于 Workspace 根目录下，各 repositoryRef 全局唯一。 |
| AC04 | Root 模式 Workspace 的 git init 成功 | 根目录 Repo 登记为 Git 可用，可选择 Git 模式或目录模式 Run。 |
| AC05 | Root 或 Child Repo 初始化 Git 因命令缺失、权限或只读失败 | Workspace/Repo 保留并显示警告；可以启动目录模式 Run，不因 Git 失败中断。 |
| AC06 | 接入普通或只读本机目录 | Link 成功且默认不改写源目录；显式选择 Git 初始化时尽力执行，失败只告警；源目录不被递归删除。 |
| AC07 | 使用 Git URL Clone | Clone 成功后 Repo 可用；失败状态可诊断且错误不暴露凭据。 |
| AC08 | Git 模式提交 branch、tag、短 SHA 或完整 SHA | Run 创建时解析完整 commit ID；后续任务继承同一基线。 |
| AC09 | 目录模式不填写 Base Revision | worker 直接使用所选 Root/Child Repo 目录，不要求 Git、不创建 worktree、不生成 Git revision/result；写入按文件系统权限执行。 |
| AC10 | 提交不可解析的非空 Base Revision | Run/task 不创建并返回可理解错误；不回退到目录模式或 HEAD。 |
| AC11 | 同一目标启动多个目录模式 Run | Run 串行执行且 Run 内并行度为 1；不同 Workspace/目标可独立执行。 |
| AC12 | 没有显式子仓库时发现一级子目录 | 一级子目录作为隐含仓库可被选择；若无一级子目录则回退根目标；追加首个显式子仓库后仅展示显式列表。 |
| AC13 | 路径越界、目标已存在或操作中途失败 | 拒绝越界和覆盖；不删除用户数据；操作状态可诊断和恢复。

## Verification

- 独立 Reviewer r2（`docs/features/reviews/FD-006-review-r2.md`）静态复核 F-01 已修复，结果为 **verification-passed**。共同 admission 锁与真实路径比较阻止不同 ref 的目录别名同时 claim；AC11 集成测试在业务断言前因本机 Maven JVM 原生内存不足退出，未运行通过。Tester r3 完整覆盖仍为 2/32，PM r3 已明确记录覆盖率与环境例外。
- 独立 Reviewer r1（`docs/features/reviews/FD-006-review-r1.md`）结果为 **changes-requested**：AC11 同一实际目录可经不同 repositoryRef 绕过目录模式串行锁。Worker r3 改为 claim 事务内统一 admission 锁和真实路径比较，已通过 Java compile-only，待独立复核和并发运行验证。Tester r2 仅完整覆盖 2/32 场景，PM 已记录覆盖率例外；未测场景没有被记为通过。
- 独立 Tester 通过 UI/API 验证 AC01–AC13，覆盖多个 Workspace、Root/Child Repo 模式、Git 初始化成功与失败、只读目录、两类 Run 和目录模式串行化。
- Worker 完成后运行一次 Java compile-only 检查；集成检查仅在另行授权后执行。Clone 场景使用本地临时 Git remote，不依赖公共网络。
- Review 检查 workspaceId/repositoryRef 唯一性、rootPath 与子路径约束、UI 持久化、多 Workspace 选择隔离、Root/Child 模式互斥、空 revision 分流和目录模式串行化。
- Worker 静态追踪 Workspace 登记、Worker assignment、两种执行模式和 Run 表单调用链；`git diff --check` 在首个修复切片退出码为 0。
- Worker 在 FD-006 工作树执行 `D:\green\apache-maven-3.9.9\bin\mvn.cmd -o -q -DskipTests compile`，设置 `JAVA_HOME=D:\green\jdk-21.0.8`；退出码 0。该命令仅编译主 Java 源码，不验证运行时行为。
- 独立场景测试、Go/Python/TypeScript 编译或运行、SQLite/PostgreSQL 迁移执行、并发与文件系统失败恢复检查均未运行，交由 Tester 如实覆盖和报告。

## Related

- [FD-001 Mission Control Overview and Shared Visual Foundation](archive/FD-001/FD-001_MISSION_CONTROL_OVERVIEW_AND_SHARED_VISUAL_FOUND.md)
- [FD-005 Worker Process Isolation](archive/FD-005/FD-005_WORKER_PI.md)
- docs/current/04-WORKER-PROTOCOL-AND-WORKERS.md：现有协议按 Git worktree 传递 workspace assignment；本 FD 扩展同机本地路径与无 revision 的目录模式。

## TODO and design gates

- 首版固定为单机 Control Plane + 同机执行 worker；远程 worker 需要后续定义共享路径或远程 Repo 契约。
- 多 Workspace 和 Root/Child Repo 配置由 UI 持久化；旧 YAML 目标保留运行时后备解析，不自动导入。托管与旧 ref 同名时不得静默改派到另一目录。
- Clone 复用本机 Git credential helper / SSH agent；不增加应用凭据输入、存储或 Secret Store。
- 删除/迁移不在首版范围；Workspace/Repo 只能禁用或解除登记，不递归删除用户目录。
- Root 模式以 workspaceId/root 标识根目录目标；Child Repo 模式下根目录不作为额外目标，避免重叠路径。
- 目录模式直接修改登记目录，Git revision/result 缺省；同一目标的目录模式 Run 必须串行，Run 内并行度为 1。
- Git 模式继续要求可解析的 Base Revision；非空但不可解析的 revision 不能回退到目录模式。

## Sources

- 用户于 2026-10-06 确认：Git 模式在 Run 创建时解析并固定完整 commit ID，后续阶段共享同一基线。
- 用户于 2026-10-06 确认：Workspace 可有多个 Repo；首版由单机 Control Plane 管理本机目录。
- 用户于 2026-10-06 确认：首版暂不实现 credentialRef。
- 用户于 2026-10-06 指定：支持新目录初始化、接入本机目录和 Git URL Clone。
- 用户于 2026-10-06 决定：Workspace/Repo 不强制要求 Git；尽力执行 git init，失败只告警，不阻断目录登记或目录模式 Run；目录模式直接使用目录，不设置 Git revision/result，写入能力由文件系统权限决定。
- 用户于 2026-10-06 补充：Workspace 可仅以根目录作为目标；多个 Workspace 必须可在 UI 中配置和管理。
- 用户于 2026-10-06 补充：Workspace 根目录单独设置；子仓库可逐个追加本地链接、Git 克隆或空白目录；未设置显式子仓库时一级子目录隐含为仓库；页面需支持列表、查看、修改和删除登记。
- Planner Session: fd006-planner-20261006-7c91a2e4；源事件: FD-006-000002-design-requested。

**Completed:** 2026-10-07
