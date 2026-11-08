# FD-085 独立实现审查 A

- 结论：**CHANGES REQUESTED**。一个阻断 SDK 模型调用的确定问题。
- Reviewer session：`codex-fd085-reviewer-20261108`；已认领源事件 `FD-085-000003-review-requested`。
- 审查范围：当前工作区 `develop` 的 FD-085 未提交 tracked 与新增文件，包含工厂配置、模型路由、Goal 图片、Python SDK/DeepSeek/Files、相关用例和文档。父会话在审查期间没有修改实现；无单独实现提交可供引用。

## P1：SDK 完成模型回合时 Evidence 拒绝 `usageSource`，导致任务失败

位置：`workers/process-python/openai_api_runtime.py:360` 的 `_GuardedModel._validate()`；`workers/process-python/worker_runtime.py:49` 的 `Evidence.fields` 与 `Evidence.record()`。

每个有效模型完成回合，无论用量是否可知，都会调用 `self._request.attempt.evidence.record("model-terminal", ..., usageSource=...)`。但 `Evidence.fields` 与 `context_fields` 都没有 `usageSource`；真实 `Evidence.record()` 因 `set(values) - self.fields - self.context_fields` 非空，设置 `self.failed = True` 并抛出 `CRITICAL_EVIDENCE_FAILED`。这发生在 `_validate()` 向 Runner 返回模型响应和执行任何模型提出的工具之前；流式与非流式的 `_GuardedModel` 路径都调用该方法，因此不仅 DeepSeek Chat/Responses，已有 OpenAI SDK 路径也不能成功完成模型回合。该字段调用在历史代码已有，但本 FD 修改并继续依赖它；不满足本 FD 使 SDK 连接可执行及保留已有 OpenAI 行为的验收要求。

建议将有界的 `usageSource` 安全元数据加入白名单，并对真实 `Evidence` 与 `_GuardedModel` 的 completed 回合建立有意义的本地回归；保留 UNKNOWN 用量和其它字段拒绝语义。当前记录的 32 项通过测试未覆盖真实 Evidence 与 SDK 完成回合交界，不能证明该链路运行成功。

## 审查证据与限制

- 静态检查 FD 的已确认范围及 32 项既有测试记录；查看 `git status --short`、`git diff --stat` 和相关 Java/Worker/UI tracked diff，直接读取新增 `deepseek_chat.py`、`api_connection.py`、`model_images.py`、`GoalImages.java`、`ModelCapabilities.java`、相关测试及 SDK/历史/权限调用链。
- 定向检查现有提示与证据字段白名单。已检查原生消息及工具结果配对、DeepSeek 独立 Key 与固定端点、服务商/协议冻结与 Worker 领取、图片选择与摘要脱敏、只清理本请求创建的 Files；这些静态观察不等于真实 SDK/API 验证。
- 未执行任何测试、编译、依赖安装、网络、真实 API 或服务；沿用 FD 已记录的先前定向测试结果，不将其扩大为运行通过。真实 SDK 版本、DeepSeek 协议/模型、OAuth、Files 服务和 UI 行为仍未验证。
- 发现后保持实现文件不变，交回 Worker 统一修复；独立报告不等于允许归档或交付。
