# fd-workflow：轻量 FD 工作流

默认流程：简短计划 → 实现与自查 → 本地交付归档。

主会话负责规划、实现和收尾。普通 FD 在 Verification 中记录自查结果；
涉及重要接口、权限、持久化、并发、跨模块影响、影响范围不清，或明确要求
review 时，在交付前增加一次独立 Reviewer。交接和单个 Work Item 不触发审查。

新 FD 默认使用 Review policy: Self、Evidence policy: Markdown。
没有策略标记的历史 FD 和旧 Independent Tester FD 保留独立审查。
历史 Dual 证据继续使用中文 Markdown 与同名 JSON，不重写旧报告。

普通进度、验证和阻塞集中写在 FD，不要求逐项提交或重复 Worker 报告。
默认 new 不创建逐阶段事件；--handoff、角色 runner 或既有交接仍使用真实收据。

完整实现请求包含授权范围内的本地交付与归档；只规划、只实现、部分工作项、
不提交和不合并等明确限制优先。测试、网络、push、发布和部署仍需各自授权。

工作区规则：非 Git 不创建 worktree，也不初始化 Git；Git 普通请求默认在当前
工作区，只有明确要求 worktree 或显式 auto 时才隔离。普通流程的自动继续
不等于选择 auto。Git auto 从规划、实现、审查到归档都在同一 worktree 中。
新 FD 通过 new --worktree 先创建 worktree 再写计划；归档提交完成后，工具
将实现与归档一起 squash 到记录的父分支，验证后清理。

入口见 [SKILL.md](SKILL.md)。规划、实现、审查和交付细节按阶段加载。
安装前阅读 [升级说明](UPGRADE.zh-CN.md)，共享资源按
[依赖说明](references/dependencies.md) 定位。当前 CLI 与共享合同需要同步更新。
