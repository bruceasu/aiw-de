# FD-020 独立实现审查 R1

<!-- aiw-data: FD-020-review-r1.json -->

结论：**通过静态实现审查**；未发现要求返工的确定性缺陷。运行验证未进行，此结论不代表真实模型、CLI 协议或进程树运行认证。

Reviewer Session：`fd020-reviewer-20261021-7c2d91`。已核对并领取来源事件 `FD-020-000006-implementation-ready`。审查基线 `1625cf5`，实现及报告提交 `af42880`；依据 FD revision 6、设计 r2 和 Worker R1 报告，设计 r1 已被用户范围变更替代。

## 静态证据

- 三包 goal-discussion 的 profile、Skill 及规范源一致；Python ROLE_FILES/启动脚本、Go TOML 加载和 TS 角色配置可达。三语言都保留该 capability，禁止通过额外 capability 冒充角色。共同 REPORT、只读、EXTERNAL_WORK 契约没有变更控制平面权威。
- Python/Go/TS 讨论路径拒绝 assignment 并创建独立空目录；API 不注册讨论工具，Go 对意外 function_call 明确拒绝，Python 沿用 GuardedModel 工具校验，TS 也关闭 history 工具。Go 不为讨论加载 fallback 根目录规则。Codex 保持只读、禁网络，临时目录不是 OS 沙箱。
- Java 只给 OpenAI 类别增加 openai-api，未知类别和删除的 SDK 路径不会匹配；冻结 profiles、领取/启动选择和 guidance 仍用已有链条，未修改历史冻结批准资料。
- Python 已删除 SDK 依赖、导入、client context 和 runtime 选择分支。配置阶段拒绝旧 SDK、auto-review、full-access，App Server 默认配置及 profile 过滤一致；native Windows 注册前拒绝，文档明确 WSL/Unix 或 Agents 路径及显式配置/队列迁移要求。
- 新 Python App Server 使用每 attempt 的独立进程组；JSONRPC、输入帧、队列、通知和输出有界。初始化、账户、ephemeral/path、cwd、approval、sandbox 回执均检查，其字段契约与仓库既有 TS AppServerRuntime 一致。未知宿主交互拒绝，正常输出需 completed 终态，失败工具/非零退出/空输出不会回报成功。
- 取消通过 attempt callback/租约传播；未确认终态时尝试 interrupt，正常及异常出口都经过 close 的进程组/I/O 终止确认。无法确认则标记 resource_uncertain、保留工作区/锁并停止继续领取，不走成功报告。服务进程启动失败在无已创建 session 时直接失败。
- README 矩阵区分源码支持与运行认证，列明 TS-only recovery-decider、不可执行 advisory、Python 无 CLI/SDK 与 Go 无 Agents。共享同步工具只写三个固定包的两个资产，运行时不依赖仓库 shared 目录。

## 验证与限制

本 Reviewer 实际执行：定向 `Get-Content`、`rg`、`Select-Object`、`git diff --stat 1625cf5 af42880`、`git diff 1625cf5 af42880 -- <受审路径>`、`aiw fd show FD-020`、`aiw fd --help`、精确 claim，以及报告后的差异空白检查和生命周期 emit。数次读取因猜测路径不存在而失败，随后按真实技能/映射路径读取；未把这些失败当验证通过。

Worker 报告的编译、资产 hash、diff 检查是其既有证据，本 Reviewer 未重跑，也未将语法编译表述为类型或运行验证。

未运行测试、编译、类型检查、验证/同步脚本、SDK 导入、真实或假 App Server、模型、服务、网络、浏览器、取消竞争或生产禁存验证。安装的 CLI 是否满足现有回执字段、Unix 后代进程行为、租约/取消竞争及真实端点兼容仍未验证；Windows Codex 路径明确拒绝。这些限制已在 FD 和运行矩阵公开记录，非新增返工项。

仅更新本报告及 FD Verification，未实现新范围，未合并、发布或归档。
