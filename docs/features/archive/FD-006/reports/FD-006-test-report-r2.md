# FD-006 独立测试报告，第 2 轮

<!-- aiw-data: FD-006-test-report-r2.json -->

**来源事件：** `FD-006-000012-implementation-ready`  
**Tester Session：** `fd006-tester-20261007-r2-da1c0478`  
**测试基线：** FD revision 12；digest `4bdec80bc23b463d175d189a2e6ff844f73b884e21aada26f469b7bbd78b23db`

## 结论

新版本获准执行 4 个离线 Worker Workspace 用例，**4 个通过、0 个失败**。上一轮的 `EVIDENCE_FIELD_FORBIDDEN` 未再发生。DIRECT 根目录和子目录均直接写入 assignment 指定目录，清理后保留用户文件，且不创建 Git 元数据、worktree 或 Git result。Git 模式从指定的完整 commit 创建 worktree，清理后保留源仓库；不可用 commit 被拒绝。

完整需求场景覆盖率 **2/32（6.25%）**。另有 2 个 Git 场景仅验证 Worker 阶段，未验证 Control Plane 的 Run 创建行为，因此仍记为未覆盖。业务代码分支覆盖率 **未测量**。建议 PM 对低覆盖率、未验证的 UI/API 和文件安全行为作明确决定；本报告不把局部 Worker 通过推论为 FD 全部验收通过。

## 场景与证据

| 场景 | 验收 | 状态 | 实际证据或缺口 |
|---|---|---|---|
| AC01-ROOT | AC01 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC02-MULTI | AC02 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC02-EDIT | AC02 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC02-REMOVE | AC02 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC03-LINK | AC03 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC03-BLANK | AC03 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC03-UNIQUE | AC03 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC04-INIT | AC04 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC05-MISSING | AC05 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC05-READONLY | AC05 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC06-PRESERVE | AC06 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC06-OPTIN | AC06 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC07-CLONE | AC07 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC07-FAIL | AC07 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC08-BRANCH | AC08 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC08-TAG | AC08 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC08-SHORT | AC08 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC08-FULL | AC08 | 未完整覆盖 | Worker 从预先给定的完整 SHA 建立 worktree，HEAD 与该 SHA 相同；未验证 Control Plane 在 Run 创建时解析。 |
| AC08-INHERIT | AC08 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC09-DIRECT | AC09 | 通过 | Root/Child 均使用 assignment.repositoryPath，写入留在原目录，cleanup 后用户文件与写入仍存在，finalize 返回 null。 |
| AC09-NOWORKTREE | AC09 | 通过 | 两个 DIRECT 用例均验证目标无 .git 且未创建 worktreeRoot，finalize 返回 null。 |
| AC09-PERMISSION | AC09 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC10-INVALID | AC10 | 未完整覆盖 | Worker 拒绝不可用的完整 SHA 并保留源目录；未验证 Control Plane 拒绝创建 Run/task。 |
| AC11-SERIAL | AC11 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC11-INDEPENDENT | AC11 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC12-DISCOVER | AC12 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC12-FALLBACK | AC12 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC12-SWITCH | AC12 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC13-TRAVERSAL | AC13 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC13-COLLISION | AC13 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC13-FAILURE | AC13 | 未完整覆盖 | 未执行完整独立运行验证。 |
| LEGACY-FALLBACK | 兼容性 | 未完整覆盖 | 未执行完整独立运行验证。 |

共 32 个可观察场景，2 个完整通过、30 个尚未完整覆盖；没有纯静态排除项。4 个用例均已执行并通过，其中 2 个仅构成上述 Git 场景的部分证据。

## 命令与风险

- Planner 授权：`docs/features/reports/FD-006-test-authorization-r2.md`，仅绑定本轮事件、revision、digest、Session 和命令；没有沿用 r1 授权。
- 实际命令：在 `D:\03_projects\AI-tools\aiw-dev\.wt\FD-006` 执行 `node --test tests/fd006/direct-workspace.test.mjs` 一次；退出码 **0**，4 个用例全部通过，约 688 ms。
- 原始输出：`tests/fd006/evidence-r2/node-test-output.txt`。测试只在系统临时目录创建本地目录、Git 仓库和 worktree，测试自己的目录在成功后清理。
- 未运行 Workspace UI/API、数据库迁移、Clone、同目标并发、其他 Worker adapter 或分支覆盖率。Run 创建时对 branch/tag/short/full SHA 的解析、路径越界/冲突及删除登记后的文件保留尚无独立运行证据。
- 剩余风险：FD 的文件安全、登记持久化和调度约束仍主要依赖实现与静态审查。需求覆盖率低于 70%，如 PM 接受，应明确记录例外及其风险。
