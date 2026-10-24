# FD-031 工作区与提交基线 Gate（已解决）

<!-- aiw-data: FD-031-blocker-scope-20261024.json -->

- 阶段/角色：Worker；当前源事件 `FD-031-000004-design-ready`。前置工作区决策在 Planner 源事件 `FD-031-000002-design-requested` 期间提出。
- 观察：初始主工作区含多个任务的未提交变更，未配置 FD worktree。用户随后明确选择“在当前工作区完成，仅提交 FD-031”。
- 原因：默认隔离流程要求干净主工作区，且不能把其它任务内容混入 FD；按用户选择转为直接工作区流程后该 Gate 已解决。
- 后续观察：准备从旧 HEAD 构造配对差异时，多行 `python -c` 辅助脚本因 PowerShell 引号处理而未能解析；改用 UTF-8 stdin 后，旧基线断言失败，仍未写索引。Git 元数据显示期间新增 `8126bca Fix worker`，已包含初版配对/桌面文件及其它任务内容。
- 恢复：未重写或撤销该外部提交；核对其实际差异后，只提交剩余 FD-031 配对拆分与桌面修正为 `ece0ae5`、`da3d738`。在后者上按提交内容编译成功，未把旧辅助脚本失败当作源编译/运行测试结果。
- 当前状态：resolved（2026-10-24），无需额外人工决定；独立实现审阅为正常下一阶段。没有未解决的工作区/基线 Gate。
- 可复用改进：PowerShell 多行 Python 使用 UTF-8 stdin；共享主工作区以实际 HEAD 和范围差异为依据，不假定独立任务期间分支不前进；交付编译绑定 Git revision。
- 模板限制：技能指定的 docs/templates/BLOCKER_FEEDBACK_TEMPLATE.md / DATA_TEMPLATE.json 均不存在，定向文件搜索也未找到替代模板；按技能必要字段和已有 aiw.fd.evidence.v1 blocker-feedback 结构记录，不创建或替换仓库模板。
