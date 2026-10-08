# FD-006 独立测试报告，第 3 轮

<!-- aiw-data: FD-006-test-report-r3.json -->

**来源事件：** `FD-006-000016-implementation-ready`

**Tester Session：** `fd006-tester-20261007-r3-805960c9`

**测试基线：** FD revision 16；digest `fdc97a28f228774cd652e4922886dda98698b856f00184b0ac72df956cb0670c`

## 结论

获准复核的 4 个离线 Worker Workspace 用例全部通过，退出码 0。另一个针对 AC11 的隔离 HTTP 集成用例退出码 1，原因是 **Maven JVM 在 Spring 启动前无法分配 94736 bytes 原生内存**。该用例没有执行任何 HTTP claim 断言，不能据此判断目录别名串行化是否正确，也不能当作产品行为失败。

32 个可观察需求场景中，完整覆盖仍为 **2/32（6.25%）**。本轮执行 4 个行为测试，全部通过；另有 1 个集成测试命令在业务断言前因环境问题退出，未计入行为测试。其余 28 个行为场景未执行。业务代码分支覆盖率未测量。AC11 保持未覆盖，建议 PM 将环境阻断和低覆盖率作为明确决定依据。

## 场景与证据

| 场景 | 验收 | 状态 | 证据或缺口 |
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
| AC11-SERIAL | AC11 | 环境阻断 | 已执行隔离 HTTP 测试命令，但 Maven JVM 在 Spring 启动前 native memory 分配失败；没有发出 HTTP claim，AC11 未验证。 |
| AC11-INDEPENDENT | AC11 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC12-DISCOVER | AC12 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC12-FALLBACK | AC12 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC12-SWITCH | AC12 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC13-TRAVERSAL | AC13 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC13-COLLISION | AC13 | 未完整覆盖 | 未执行完整独立运行验证。 |
| AC13-FAILURE | AC13 | 未完整覆盖 | 未执行完整独立运行验证。 |
| LEGACY-FALLBACK | 兼容性 | 未完整覆盖 | 未执行完整独立运行验证。 |

通过的两项为 AC09-DIRECT、AC09-NOWORKTREE。Git 完整 SHA 与无效 SHA 的两个 Worker 用例通过，但未验证 Control Plane 在 Run 创建时的行为，故 AC08-FULL 和 AC10-INVALID 仍未算完整覆盖。没有纯静态排除项。

## 命令、清理与风险

- 授权记录：`docs/features/reports/FD-006-test-authorization-r3.md` 绑定复核命令；`FD-006-test-authorization-r4.md` 绑定隔离集成命令及用户在对话中的明确批准 `approved:conversation:call_7fc9a5953d29450a8f37db403a1ff6dc`。两者均绑定本轮 revision、digest、事件和 Tester Session。
- 在 FD-006 工作树执行 `node --test tests/fd006/direct-workspace.test.mjs` 一次：退出码 **0**，4/4 通过，约 670 ms；原始输出 `tests/fd006/evidence-r3/node-test-output.txt`。
- 在同一工作树执行 `node --test tests/fd006/claim-alias.test.mjs` 一次：退出码 **1**，0/1 通过，约 401 ms；原始输出 `tests/fd006/evidence-r3/claim-alias-test-output.txt`。异常为 `Isolated Control Plane exited before becoming ready`；JVM 头部记录 `Native memory allocation (malloc) failed to allocate 94736 bytes`，进程为 Maven Launcher。没有重试或改用其他命令。
- 集成夹具的 finally 正常完成，没有附加清理异常；依据夹具控制流，本次 Maven 子进程已退出、测试端口在关闭后才删除临时目录。没有再启动新进程作独立复核。
- JVM 在工作树根生成了被 Git 忽略的 `hs_err_pid5888.log`（约 42.5 KB）和 `replay_pid5888.log`（约 173.6 KB）。只读取前者头部以确认内存错误，没有提交两份日志。尝试确认路径后用 PowerShell `Remove-Item -LiteralPath` 删除时，工具自动策略以 `blocked by policy` 拒绝，未给具体原因；未换壳重试。后续 FD worktree 清理须处理这两份文件。
- 静态读取了 FD、公开 UI/API 与 Worker 协议、邻近测试夹具和配置；用 `git status --short`、`git status --short --ignored` 确认报告/测试待提交及两份忽略日志存在。未运行数据库迁移验证、真实并发 claim、UI、Clone、其他 Worker adapter 或分支覆盖率。

## 剩余风险

Reviewer 指出的 AC11 路径别名互斥尚无运行证据；本轮失败属于测试环境，不能替代功能结论。此前未覆盖的注册删除用户文件保护、路径越界/冲突、Run 创建 revision 解析、SQLite/PostgreSQL 迁移和其他 Worker adapter 仍需依赖后续验证或 PM 明确例外。
