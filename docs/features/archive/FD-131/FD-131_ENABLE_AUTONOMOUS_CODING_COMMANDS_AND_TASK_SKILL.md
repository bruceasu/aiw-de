# FD-131: 通用 Coding Worker 自主执行

**Status:** Complete
**Revision:** 7
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Dual

## 目标

让现有 Coding Worker 在一次任务里编辑代码、执行项目的构建和测试、根据输出修复并重跑。首期使用可信执行账号，Sandbox 不作为前置条件。

## 方案

- 简单任务和工作流显式授予自主命令权限，默认关闭。任务同时启用工作区工具。工作流的授权适用于启用工作区工具的任务节点。计划调度和秘书委托继续遵循现有权限边界。
- Plane 冻结权限并校验每次命令的有效租约。固定命令继续走原入口。Windows 使用 PowerShell 和 Job Object，Linux 使用 sh 和进程组。两个 Runtime 的任务命令共用已有工具代理。
- 命令运行使用经过筛选的环境变量，输出设上限。文件和网络访问由执行账号及部署环境决定。Linux 普通进程组无法确认脱离组的后代已经退出，暂不开放自主命令。
- 沿用可选 Skills、任务资料和现有版本冻结。用 Java S3 SDK 场景验证，没有真实云资源访问要求。

## Work items

- [x] 1 接通工作区授权、命令执行和回执。
- [x] 2 在 OpenAI Agents SDK 和 Codex App Server 的受控入口注册同一工具。
- [x] 3 在两条 Runtime 验证代码修复和重跑。通过 Plane 验证停止，并离线验证 Windows 执行器在 Worker 异常退出时的进程清理。
- [x] 4 接通工作流选定的冻结 Skill，并登记 `java-s3-sdk`。
- [x] 5 对照验证普通 Java 和 Java S3 任务的资料装载。

## 验收

1. 普通 Java 任务能够看到测试失败，修复后重跑并记录命令、结果和改动。
2. 默认关闭自主命令，未授予权限或租约失效时拒绝。取消须核对进程清理结果，无法确认时保留现场。
3. 普通任务不加载 AWS 资料。S3 任务能使用固定的专业资料，并在没有真实云账号凭证的环境完成验证。

## Verification

- 2026-11-19 [合并恢复复核](reviews/FD-131-review-20261119-recovery.md) 通过。保留 develop 的语言规则、FD 权限文案及 R2 的授权重置。

- 2026-11-19 [独立复核 R2](reviews/FD-131-review-20261119-r2.md) 验证通过。R1 P2 已关闭，下一项简单任务需要重新勾选自主命令。

- 2026-11-19 [独立审查 R1](reviews/FD-131-review-20261119-r1.md) 要求修改。新建简单任务不得沿用上一项任务的自主命令授权。

- 独立审查以 73e28002 为基线，包含首轮实现 17eb95c9 和本工作树后续改动。权限及进程生命周期变更需独立审查。
- Windows 启动器等待实际命令结束，返回其退出码。命令输出超出 16 KiB 时保留最后 16 KiB。
- 通过 JDK 25 离线 Maven `-DskipTests compile`。修改过的 Python 源码通过 `compile()`，`workflows.js` 通过 `node --check`，`git diff --check` 无错误。
- 用一份临时 Java 项目副本准备一个有失败断言的小缺陷。分别在两种 Runtime 勾选工作区工具和自主命令，要求先复现失败再修复重跑。核对命令、退出状态和交付差异。在另一次任务用可观察的子进程验证取消和异常退出。
- 在另一个临时项目上选择 `java-s3-sdk` 并附上项目版本对应的 API 资料，要求使用现有 mock 验证请求构造。对照普通 Java 任务的冻结 Skill 清单。
- 2026-11-19 在 6e6f9c27 上以 PowerShell 管道执行一次内联 Python（`python -`，禁用字节码写入）。在临时目录直接调用 `task_shell.run_shell`，耗时约 7 秒。实测 PowerShell 输出和退出码 7 正确返回。取消命令和强制结束测试 Worker 后，均已启动的 Python 子进程都未能在预定时间写入存活标记。
- 此次仅覆盖 Windows 命令执行器。两条 Runtime 的任务闭环、Plane 停止回执和 Java/S3 对照尚未实测，尚无独立审查结论。

## 端到端验收步骤

1. 用同一份最小 Java 工程的两份临时 Git 副本，分别通过两个 Runtime 运行。用 JDK 自带的 javac 和 java 重现一次失败。修正实现后重跑，保留断言，核对两次实际退出状态和最终差异。
2. 通过测试 Plane 取消长命令，核对收到停止回执，并确认子进程未写入预定的延时标记。
3. 普通任务不选 S3 Skill。对照任务选择 java-s3-sdk 并附上项目版本对应的 API 资料，使用测试替身验证请求构造。核对冻结清单、按需读取记录和本地验证结果。
4. 测试 Plane 仅监听回环地址。单独使用临时数据库和 Worker 工作目录。真实模型调用只使用明确授权的测试连接。

## DeepSeek 验收进展

- 2026-11-19 用临时 SQLite 和仅监听回环地址的 Plane 准备 OpenAI Agents SDK 的 DeepSeek Chat Completions 路径，模型为 deepseek-flash。测试 Java 项目基线由 javac 编译成功，java Verify 按预期失败。
- 最初的提交暴露两个问题。工作流角色表缺少已实现的 workspace-work，提交返回 422。补齐角色后，任务已入库且匹配 Worker 已上线，但旧的固定 Skill 摘要条件仍挡住了领取。已针对冻结文件包路径修正该条件，保留 Worker 对固定文件包的精确核对和传统固定 Skill 的原规则。
- 两处 Java 修正通过 JDK 25 离线 Maven -DskipTests compile。

- 修正后，原临时任务成功被领取。SQLite 记录一个已完成的 MODEL 操作。验证计时包含重启后原 Worker 的 90 秒心跳有效期。观察时限届满时，任务仍为 RUNNING，未记录 SHELL 操作，停机后保留一条 UNKNOWN Git 操作。
- 用另一个临时库和干净 Java 副本复验，任务提交的 HTTP 响应仍超时，未得到修复重跑通过的证据。下一次应独立跟踪提交与执行，提交超时按原请求 ID 查询，保持测试 Plane 运行至终态或明确停止。

- 2026-11-19 按原请求查回任务后，DeepSeek 首次请求使用绝对路径的 ls，Plane 在工具开始前拒绝该参数。Worker 现在先在本地校验路径并提示用相对路径，避免把参数错误标记为未知操作。
- 复验用另一份临时项目。提交响应再次超时，重启后接续同一条任务。临时库记录 4 次 MODEL、3 次 READ 和 1 次 SHELL 成功。模型发出 write 后，尝试以 MODEL_EXCEPTION_USERERROR 失败。源码核对发现写入风险日志传入 Evidence 不接受的 classification 字段，已改用许可的 code 字段。该次运行未完成第二次 Shell 与交付。

- 2026-11-19 复验观察到两次 SHELL、一次 WRITE 均完成，交付被 RUN_ALREADY_FINALIZING 拒绝。修正后，仅 Worker 收尾 Git 步骤可以在封口后经过原 Guard 和当前租约执行。模型工具仍不能在封口后调用。把提交后的任务推进移至独立线程，避免 SQLite 在原连接释放前同步开启新写事务。
- 在隔离的测试 Plane 使用 DeepSeek Chat Completions 的 deepseek-flash 复验，按原请求 ID 查回任务。只有一次提交。工作流和任务均为 COMPLETED。交付记录的两次命令退出码为 1、0，结果提交 f818757ee379293c2438395f7958e0ed693c48da。实际 Git 差异只有 Calculator.java，将减法改为加法，断言文件未修改。该次启动通过离线 Maven spring-boot:run 编译并使用临时 SQLite。
- OpenAI Agents SDK 修复、重跑和交付通过。Plane 停止回执、Codex App Server 及 Java/S3 对照仍待验收。

## 停止与 Java/S3 复验

- 2026-11-19 首次停止测试收到 Worker 回执，任务已取消，但工作流仍为 STOPPING。修正 Guard 停止处理，在同一事务通知工作流完成。复验使用 deepseek-flash，等 PowerShell 下的 Python 子进程写入启动标记后，通过 Plane 请求停止。任务进入 CANCELLED，工作流进入 STOPPED，数据库有停止请求和回执。在超过子进程预定运行时间后，仍没有延时标记。
- 普通 Java 任务固定的 Skill 只有 engineering-artifact-protocol。S3 任务额外固定了 java-s3-sdk，Worker 记录读取了 SKILL.md。首次验收发现模型使用的 Skill 读取参数被拒绝后终止，已补充参数约束和可供模型纠正的提示。复验使用本地缓存的 AWS SDK for Java 2.10.66 以及内存请求接收器，实际 javac/java 检查分别退出 1、0；工作流完成，交付记录列出 Publisher.java。实际差异是改用调用者提供的对象键，没有 Git 结果提交。未访问真实 AWS。
- 复验均使用 Maven 离线启动和临时 SQLite，已核对任务回执、冻结清单、命令退出码及实际文件差异。Codex 修复闭环及独立审查仍待完成。

- 静态核对 Codex 本机登录工厂启动入口，发现默认 host-actions 未选择运行控制所需的 v2。托管 Worker 在启用运行控制时现在使用 v2 作为默认值。仅完成 Python 编译及静态检查，等待 Codex 连接确认后实测。

## Codex App Server 验收

- 2026-11-19，使用授权的本机登录、gpt-6.2-sol 与 host-actions v2。在临时 SQLite 和单独的 Java 仓库上，离线 Maven 启动测试 Plane，实际执行两次 PowerShell 编译与检查，退出码依次为 1、0。任务和工作流均为 COMPLETED。交付提交 2e1061ed659eafeac047d7f88160d99311c05f4f 仅把 Calculator.java 中的减法改为加法，未修改断言。
- 停止端到端使用 DeepSeek，两个 Runtime 的 Shell 子进程均由同一 Windows 命令执行器管理。独立审查待完成。

- 已修正首轮审查报告中的 P2。简单任务提交成功及进入新建入口时清除自主命令勾选。JavaScript 语法及差异检查通过，等待聚焦复核。

## 交付恢复

- 归档提交 97406f7b 首次向 develop 交付时，仅 workflows.js 的翻译字典冲突。以 develop 的语言调整为基础，保留 FD-131 的两个中文提示及自主命令权限重置。其余父分支内容由 Git 合入。node --check 和差异检查通过，等待聚焦复核。

## Sources

- [需求稿](../../../research/AIW_General_Coding_Agent_Requirements.md)
- [开发说明书](../../../research/AIW_General_Coding_Agent_Capability_Enhancement_Spec.md)

**Previously completed:** 2026-11-19

**Completed:** 2026-11-19
