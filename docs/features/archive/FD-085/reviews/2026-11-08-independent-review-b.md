# FD-085 P1 修复聚焦复核 B

- 结论：**PASS（独立静态审查）**。报告 A 的 P1 已修复，本次限定范围无剩余 findings；不代表真实 SDK、模型或外部服务联调通过。
- Reviewer session：`codex-fd085-reviewer-20261108`，沿用独立审查 session，已认领源事件 `FD-085-000006-implementation-ready`。FD-085 仍在根工作区，审查对象为当前未提交源码及测试，未伪造提交版本。
- `_GuardedModel._validate()` 写入的 `usageSource` 已纳入实际 `Evidence.fields`；`Evidence.record()` 对该字段只允许 `provider-response`、`unknown`，无须扩大其他元数据字段的许可。已知/未知用量的完成回合不再因该字段被拒绝，未认可值仍失败。
- 新增 `test_evidence_usage.py` 使用真实私有 Evidence writer 与 `_GuardedModel._validate()`，SDK 类型用替身；源码断言未知及已知用量成功写入、缓存数量正确和非法字段值拒绝。测试未执行，不能记为通过。用户明确禁止本轮额外测试。
- 静态阅读真实 000006 receipt、修复代码、P1 原调用点、新测试与 FD 验证记录；认领真实 receipt。后续执行一次 `git diff --check` 作为唯一编辑后静态检查。Worker 报告编译和测试源码语法检查通过，Reviewer 未重复运行。
- 沿用 A 及 FD 中真实 32 项既有定向测试记录及其范围；本轮未运行任何测试、编译、服务、网络、真实 API 或依赖安装。真实 SDK/模型、文件 API、浏览器与跨进程运行仍未验证。独立审查通过仅解除本地交付门槛，不是发布或真实服务运行批准。
