# 公共 Worker 实现来源

FD-005 的公共 ESM 模块是 aiw-dev 的 Worker 实现。保留控制平面协议及 SDK 执行路径；没有嵌入 pi 的 Agent、UI、Provider、持久化会话树或调度系统。

## pi 参考

用户提供的源码目录：`D:/03_projects/AI-tools/pi`。该目录没有 Git 元数据，不能给出虚构 commit；参考日期 2026-10-05。下列摘要由实际文件的 Get-FileHash -Algorithm SHA256 取得，运行效果没有测量。

| pi 来源 | 融合内容 | aiw-dev 实现 |
|---|---|---|
| packages/agent/src/agent-loop.ts、agent.ts | 调用关联、钩子与等待实际活动资源终态 | tool-host.mjs、attempt.mjs、model-guard.mjs；保留 Agents SDK 循环 |
| packages/coding-agent/src/core/tools/truncate.ts | UTF-8 边界、首尾保留、行/字节预算和截断说明 | output.mjs；本实现将完整 JSON envelope 和提示标记纳入预算 |
| packages/coding-agent/src/core/tools/edit.ts、edit-diff.ts | 修改前校验、换行/BOM、局部差异 | edit.mjs；采用严格唯一匹配、同一原始内容上的全部编辑计划 |
| packages/coding-agent/src/core/tools/bash.ts | 有界输出、终止与资源收尾 | process.mjs；采用主机参数数组、限定环境及平台差异 |
| packages/coding-agent/src/core/compaction/compaction.ts、utils.ts | 派生摘要、调用/结果成对及保留可回查来源 | context.mjs；默认关闭、同模型与期限、上限两次，原始来源仅内存保留 |

以上是机制与算法参考，Worker 的协议、权限、并发和生命周期按本项目契约实现。任何后续复制或改编 pi 源码继续附带 [MIT 许可](THIRD-PARTY-NOTICES.md)。没有复制 Codex SDK 源码或修改 node_modules 来接入禁存。

| 参考源文件 | SHA-256 |
|---|---|
| packages/agent/src/agent-loop.ts | e773e822a2587caf314e6bb66ea6370fce3468b7eecd26d6a67afe673f81eabc |
| packages/agent/src/agent.ts | 7024a3145a6ebc951a0f030164adb1dfb12d33d6e0dbbac15ef0efda49c87457 |
| core/tools/truncate.ts | 8e4507c3ed7ca7548cf7c2d7f77d07f2ae63a38b756789cbb44e9062db6c8762 |
| core/tools/edit.ts | 31a368c14cf5437ecac157765660001b16063302d9bd746303d6d1b1dcb5625d |
| core/tools/edit-diff.ts | f85a9809eb44b9828236050cf38a8e45933e5cfd583a186e9a0e55a664dd3e8d |
| core/tools/bash.ts | de2b54d4f4bfbb844ce6d8077f63d715f7d8e80079dd9689b561e45b6679c81b |
| core/compaction/compaction.ts | d5aebd41333957b57fa3f1bee2a18b3c00b5d47bb1b4af6f913cf8cd791099c8 |
| core/compaction/utils.ts | cc0baada8a2e31bcf1dbc18948c69ba2264fb2ad4d2c583ceb772fd3c3233674 |

core/ 相对 packages/coding-agent/src/。没有对参考目录进行构建或行为测试。

## aiw、SDK 与本仓库

- aiw 参考安装插件的实际 Skills/GitHub 命令与输出 schema，`aiw-adapter.mjs` 固定解释器、插件摘要和操作；未将 FD/Issue 推进机制变为生产 Worker tool。
- Agents SDK 0.3.9 和 OpenAI 6.49.0 的实际安装源码用于核对 Model、stream、usage、tool call ID 和 parallel 设置；SDK 仍作为依赖使用，没有把其实现复制到公共模块。
- Codex SDK 0.155.1 的公开选项、spawn 和事件定义用于直接启动已安装 CLI。经授权下载的 Python 0.157.0 wheel 仅用于阅读公开接口，未安装/导入；摘要见 SDK_CAPABILITIES.md。实际 Codex 平台禁存尚未验证，未知组合拒绝注册。
- WorkspaceResult、六种 failureCategory、complete/fail 非幂等语义来自当前 TaskExecutionService；不增加 Worker 工作流权威。
- 冻结 R2 `skills/` 树和角色 Profile 保持原材料来源，未改写其正文。Go/Python 共享行为契约，分别实现本语言的 I/O/进程适配。

来源说明不等于行为证明。独立 Tester 和 Reviewer 对照公共契约、实际源码及命令证据验收。
