# FD-006 独立测试报告，第 1 轮

<!-- aiw-data: FD-006-test-report-r1.json -->

**来源事件：** `FD-006-000009-implementation-ready`  
**Tester Session：** `fd006-tester-20261007-4c865139`  
**测试基线：** FD revision 9；digest `50f9e057844a7f9334c21b3afdbbf7cc176f5204cbff6a91bbd502e9e4e1c596`

## 结论

独立执行 1 个针对 DIRECT Worker assignment 的离线行为测试，退出码 1：0 通过、1 失败。测试在 evidence 元数据处理处抛出 `RuntimeFault: EVIDENCE_FIELD_FORBIDDEN`，未完成对目录写入和保留行为的断言。该结果不能证明 AC09 的业务行为通过；触发点可能是测试夹具或新增 assignment 字段的 evidence 契约，需 Worker 定位。

可观察需求场景覆盖率 **0/32（0%）**；业务代码分支覆盖率 **未测量**。建议退回 Worker 处理该失败并针对新版本重新复测。

## 场景与证据

| 场景 | 验收 | 状态 | 可观察行为 |
|---|---|---|---|
| AC01-ROOT | AC01 | 未覆盖 | 只登记根目录并明确选根目标时，ref 为 workspaceId/root，relativePath 为 . |
| AC02-MULTI | AC02 | 未覆盖 | 创建两个 Workspace 后各自 ID、名称、根路径独立且列表详情可见 |
| AC02-EDIT | AC02 | 未覆盖 | 修改名称或未使用根路径并禁用后，目标状态持久化 |
| AC02-REMOVE | AC02 | 未覆盖 | 解除 Workspace 登记后本地目录和文件仍存在 |
| AC03-LINK | AC03 | 未覆盖 | 追加本地链接后目标位于根目录内并指向所选来源 |
| AC03-BLANK | AC03 | 未覆盖 | 追加空白子目录后生成可选择的显式目标 |
| AC03-UNIQUE | AC03 | 未覆盖 | 多个显式子仓库产生全局唯一 repositoryRef |
| AC04-INIT | AC04 | 未覆盖 | 根目录 git init 成功后可分别选择 Git 模式和目录模式 |
| AC05-MISSING | AC05 | 未覆盖 | Git 命令缺失时保留 Workspace 或 Repo 登记并显示警告 |
| AC05-READONLY | AC05 | 未覆盖 | 只读或权限不足导致 git init 失败时仍可启动目录模式 Run |
| AC06-PRESERVE | AC06 | 未覆盖 | 默认接入外部本地目录不改写源内容或递归删除 |
| AC06-OPTIN | AC06 | 未覆盖 | 显式选择 Git 初始化时尽力执行，失败仅产生警告 |
| AC07-CLONE | AC07 | 未覆盖 | 从本地 Git remote 克隆成功后 Repo 可用 |
| AC07-FAIL | AC07 | 未覆盖 | 克隆失败时可诊断且页面错误不暴露凭据 |
| AC08-BRANCH | AC08 | 未覆盖 | Git 模式 branch 在 Run 创建时解析为完整 commit ID |
| AC08-TAG | AC08 | 未覆盖 | Git 模式 tag 在 Run 创建时解析为完整 commit ID |
| AC08-SHORT | AC08 | 未覆盖 | Git 模式短 SHA 在 Run 创建时解析为完整 commit ID |
| AC08-FULL | AC08 | 未覆盖 | Git 模式完整 SHA 由所选仓库验证后保持不变 |
| AC08-INHERIT | AC08 | 未覆盖 | 后续任务继承 Run 创建时固定的同一 commit 基线 |
| AC09-DIRECT | AC09 | 已执行但失败 | DIRECT Worker 使用 assignment 指定的普通目录写入、保留目录并返回空 Git 结果 |
| AC09-NOWORKTREE | AC09 | 未覆盖 | 目录模式无需 Git 且不创建 worktree 或 Git revision |
| AC09-PERMISSION | AC09 | 未覆盖 | 目录模式写入遵从文件系统权限 |
| AC10-INVALID | AC10 | 未覆盖 | 不可解析的非空 Base Revision 返回错误且 Run 或 task 不创建 |
| AC11-SERIAL | AC11 | 未覆盖 | 同一目标的多个目录模式 Run 串行且各 Run 并行度为 1 |
| AC11-INDEPENDENT | AC11 | 未覆盖 | 不同 Workspace 或目标的目录模式 Run 可独立执行 |
| AC12-DISCOVER | AC12 | 未覆盖 | 无显式子仓库时根目录一级子目录成为隐含可选目标 |
| AC12-FALLBACK | AC12 | 未覆盖 | 无一级子目录时自动回退根目标 |
| AC12-SWITCH | AC12 | 未覆盖 | 追加首个显式子仓库后隐含列表不再作为新 Run 目标 |
| AC13-TRAVERSAL | AC13 | 未覆盖 | 越界子路径被拒绝且目标外用户文件不变 |
| AC13-COLLISION | AC13 | 未覆盖 | 已存在目标或冲突 ref 被拒绝且不覆盖用户数据 |
| AC13-FAILURE | AC13 | 未覆盖 | 目录或克隆操作中途失败后状态可诊断并保留非应用拥有的数据 |
| LEGACY-FALLBACK | 兼容性 | 未覆盖 | 未登记旧 YAML ref 仍可手动输入并解析，托管冲突不静默改派 |

以上共 32 个独立场景：0 通过、1 执行失败、31 未执行。AC09-DIRECT 的测试用例是 `tests/fd006/direct-workspace.test.mjs`，仅操作由测试创建的系统临时目录。没有纯静态排除项。

## 命令与风险

- Planner 授权：`docs/features/reports/FD-006-test-authorization-r1.md`，绑定本报告的事件、revision、digest、Session 与唯一命令。
- 实际命令：在 `D:\03_projects\AI-tools\aiw-dev\.wt\FD-006` 执行 `node --test tests/fd006/direct-workspace.test.mjs` 一次；退出码 **1**，Node 报告 `tests 1; pass 0; fail 1`，耗时约 125 ms。
- 原始异常摘要：`RuntimeFault: EVIDENCE_FIELD_FORBIDDEN`；栈位置 `workers/runtime-core/evidence.mjs:17:31`、`:83:58`。失败发生在业务断言完成前；本报告未把它判为已覆盖。
- 未运行 UI/API、数据库迁移、Git Clone、并发、其他 Worker adapter、分支覆盖率或未获授权的测试命令。失败时测试自有临时目录可能保留，尚无人工确认的清理证据。
- 剩余风险：目录模式 Worker 的直接写入及保留、路径边界、删除保护与串行化均缺少通过的独立运行证据。后续修复需重新绑定授权并复测；不得沿用本次失败结果作为通过证据。
