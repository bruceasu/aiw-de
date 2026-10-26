# fd-workflow 轻量流程升级说明

默认流程改为简短计划、实现与自查、本地交付归档。仅在风险或明确要求需要时，
交付前增加一次独立审查。复审聚焦问题和修复影响，不因角色交接反复审查。

主会话合并规划、实现与管理职责；真实委派或 runner 仍使用原角色和事件协议。
普通进度、实际验证及阻塞集中写在 FD；小 FD 可以一次实现提交。
少见的冲突、交接恢复和清理失败规则继续按需读取。

新 FD 默认声明 Review policy: Self、Evidence policy: Markdown。
重要接口、权限、持久化、并发、跨模块影响、范围不清或要求 review 时选择 Independent。
无标记的历史 FD、旧 Independent Tester FD 及 Dual 证据保留兼容行为。

本次修改同时涉及共享 work-management 合同、仓库模板、aiw-fd CLI 和稳定规格。
安装时应同步相应版本；单独替换 SKILL.md 无法改变旧 CLI 的强制审查门槛。
旧工具不支持 Self 正常归档时应报告版本差异，不把 force 当成普通完成路径。
保留现有 FD、收据及历史报告，勿批量转换旧策略。

aiw fd new 默认不创建 handoff；--handoff 或配置 runner 时保留托管交接。
request-review 可直接接收就绪的 Open/In Progress/Complete FD。
Self 归档不产生 Reviewer 通过事件；Independent 仍需当前有效独立通过。

本地交付、归档与发布是不同操作。用户阶段、Git 与运行授权限制继续生效。

工作区默认改为当前目录。非 Git 不创建 worktree，也不初始化 Git；Git 只有
明确要求隔离或显式 auto 时使用 worktree。auto 的计划、实现、审查与归档都
写在 worktree 内。new --worktree 与 git wt add --prepare 支持先建 worktree
再写新 FD；最终交付支持定位 worktree 内已归档的 FD，不要求父分支有活动 FD。
归档提交随实现一起交付，验证后再删除 worktree。需同步 FD 和 Git 插件版本。
未执行的测试、检查和平台行为不得写成通过。[RULE-MAP](RULE-MAP.md)
仅保留此前文档拆分的映射背景，当前规则以 SKILL 和共享合同为准。
