# FD-092 修复聚焦复核

- 结论：changes-requested。
- Reviewer：`codex-review-fd092-20261113`。
- 来源事件：`FD-092-000004-implementation-ready`，已认领。
- FD Revision：4；修复提交 `e5d6d865e113f98787612d91b86315923314512a`，比较基线 `705f9df`。
- 首轮报告保留于 [review-20261113.md](review-20261113.md)。本次沿停止与输入通道关联链复核，没有运行测试。

## 阻塞发现

### P1：Python 仍仅为 Codex 注册、打开原生通道，API 输入链实际不可达

位置：`workers/process-python/process_worker.py:167` 和 `workers/process-python/native_run.py:227`。

FD-092 的当前验收包括 OpenAI API Worker 的原生通道接入和统一工作流补充输入。服务端已允许 openai-agents，但 Worker 配置仍只有在 `runtime == "codex-app-server"` 时调用 `native_run.capabilities()`。因此普通 API Worker 不会注册领取要求的 worker-loop-turn-v1、questions-v2、native-config 与 handoff-store 能力，新增 workflow-inputs-v1 也不会注册。

即便外部配置提供了这些能力，`open_run()` 仍在 `config.runtime != "codex-app-server"` 时直接返回 None；`process_worker.py:532–535` 因而不会创建 NativeRun，不会打开执行通道，也不会设置 attempt.workflow_inputs。新增 SDK 投影、ACK、DRAIN 路径对 OpenAI Worker 均不可达。这个静态可追溯的集成缺陷阻断 FD 的核心目标。

最小修正：在上述两个既有运行时条件中同时允许 openai-agents，并保留原 guarded/Broker 分流和能力指纹检查；不需要新 runtime、依赖或测试门禁。

## 首轮 P1 修复结果

首轮停止竞态修复已通过静态复核：ACK/UNKNOWN/DRAIN 使用非 running gate，但仍保留当前原任务、Attempt、worker、lease token 和租约有效性校验；STOPPING 可记录既有批次事实，DRAIN 返回 hasPending=false，RESERVE 不取得新内容。ACK/UNKNOWN 不因本地取消直接跳过；输入元数据 RPC 失败只标记 failed，不再错误设置执行资源未知。模型错误后 failed/pending 同时阻断新的 project 与 SDK 重试建议；已 APPLIED 批次 ACK 幂等，不重复追加上下文，UNKNOWN/APPLIED 状态冲突拒绝。原生资源未知的既有保护仍由原执行链维护。

这是静态证据；未验证真实 SDK、停止并发或网络丢回执。

## 命令与未验证范围

实际执行：`aiw fd show FD-092`，`git show` 定向读取 e5d6d86 修复，`git show 9d305a0:workers/process-python/native_run.py`，`git status --short`，相关源码的 PowerShell `Get-Content`/`Select-Object` 与定向 `rg`；`aiw fd claim FD-092 FD-092-000004-implementation-ready --session codex-review-fd092-20261113`。一次 rg 的 PowerShell 文件通配路径失败后改为精确路径读取，未运行代码。本报告以 `aiw fd emit FD-092 changes-requested --producer reviewer --artifact docs/features/FD-092/reviews/review-20261113-recheck.md --source-event FD-092-000004-implementation-ready` 记录结果。

Worker 记录修复后 Java/Python 编译通过；Reviewer 未复跑。未运行测试、构建、服务、迁移、网络、浏览器或真实模型；未创建子 Agent，未修改产品源码。只写报告及 Verification。

上一轮未验证范围继续保留。当前未通过的原因是已确认入口不可达，而非缺少运行测试；修复后只需聚焦上述两处入口及其关联链路。
