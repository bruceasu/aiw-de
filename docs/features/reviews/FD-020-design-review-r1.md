# FD-020 独立设计审阅 r1

<!-- aiw-data: FD-020-design-review-r1.json -->

结论：**ready**。未发现必须阻止 design-ready 的设计缺口。本结论只覆盖 FD-020 revision 3 的设计，不代表实现验收通过。

后续范围变更：本报告写出后，用户要求 Python 移除 `codex-sdk`、新增 `codex-app-server`。本报告不覆盖该新范围，不能作为更新后设计的就绪证据；需待新版 FD 后定向审阅。

- FD：`docs/features/FD-020_FEATURE.md`
- 来源事件：`FD-020-000002-design-requested`
- 审阅会话：`fd020-design-review-r1-20261021`
- 范围：共享资产、Python/Go 角色身份与运行边界、Java backend/runtime 筛选及冻结规则。

## 事实与判断

1. Python 的 `ROLE_FILES` 和 Go 的 TOML loader 已支持该 profile 的字段结构；三包使用同字节副本并继续从本包加载，可保持独立部署。共享源及窄同步工具是合理的维护接缝，不需要新增运行引擎。
2. Python 与 Go 目前的额外 capability 禁用集合未包含 `goal-discussion`；FD 1.2/1.3 明确补入 reserved capability，可防止其他角色通过环境变量声明该能力。Python 默认 Codex Worker ID 仍是通用名称；新增角色的 launcher 应设置独立 ID，避免同时启动其他角色时注册冲突。
3. 两语言当前无 assignment 时都回退至 fallback workspace，且只读 API 仍有读取工具。FD 明确新增独立空目录、提前拒绝 assignment，以及 API 不创建/发送工具并拒绝意外调用，覆盖了这些已有实现接缝。仅设置 readOnly 或写提示词并不足够；设计已避免此问题。
4. `GoalTaskPolicy` 冻结的 profiles 会在 claim、guidance 和 select 中再次关联 runtime。只扩展 backend/runtime 分类集合，同时保留以上关联，可让 Python SDK 和 Go API 参与而不绕过已有 profile 策略。未知 backend 显式拒绝还能收紧当前非 OpenAI 一律进入 Codex 分支的行为。

## 实现时应核对的接缝

以下是设计已有要求的具体落点，不构成新的设计 Gate：

- Python SDK 与 Go CLI/App Server 当前传入 `sandbox_workspace_write.network_access`，不能据此推断 read-only 禁网已经生效。应像已有 TS 适配一样补全 read-only 对应配置；App Server 的线程/turn policy 与返回策略检查也必须一致。仅固定内部布尔值不足以完成验收。
- 工具拒绝应在真正分派前发生。Go 的 `function_call` 不能进入 `runWorkspaceTool`；Python 的无工具列表应与 guarded model 的响应校验一致。该角色无 assignment 的目录不能沿用 Git worktree 的删除流程。
- 同一规范 profile 的复制不能覆盖不同语言已有其他角色；同步工具应限定三个固定目标，独立包的 skills/profiles 布局和协议 Skill 继续可加载。
- `f.profiles().isEmpty()` 表示 Worker 默认值分支。扩展 runtime 后，历史空 profiles 请求也会允许同 backend 新 runtime 领取；这是 FD 中“未配置 profiles 时同 backend 可领取”的规则。不要在文档中将其误写成历史空请求的候选 runtime 完全不变。历史非空 profiles 则必须保持原组合限制。

## 未验证与限制

本次只用 `Get-Content`、`rg`、`Select-Object` 读取本地 FD、planning 规则、预算与通信规则，以及相关 Java、Python、Go、TS 源码/profile；未运行测试、编译、验证脚本、网络、模型或 Worker。两次读取不存在的模板/任务提示路径未获得内容，未替代为虚构模板规则。未检查外部 SDK 文档或运行行为。

独立实现 Reviewer 仍需检查实际 diff 是否落实现有验收，尤其是 read-only 禁网配置、Worker 身份、工具调用分派和冻结 profile 边界。
