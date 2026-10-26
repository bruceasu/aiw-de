# 原规则迁移映射

行号对应原始上传 fd-workflow/SKILL.md（409 行），不是优化版行号。

| 原位置 | 新位置 | 保留重点 |
|---|---|---|
| 1–44 入口/范围/例子 | SKILL.md | 默认生命周期、阶段限制、测试选择 |
| 46–54 Resolve | SKILL.md Resolve and enter | FD 唯一计划与创建事件 |
| 56–105 Design/splitting | references/planning.md | 就绪、大小、ID、验收修改、design-ready |
| 107–147 session/recovery | references/handoffs.md | 唯一 session、禁止抢占、refresh/reopen/correct-reason |
| 149–172 lifecycle contract | SKILL.md + dependencies.md | 本地授权范围、独立 Reviewer、外部 contract |
| 174–204 blocker records | references/blockers.md | MD/JSON、source_event、修复记录和恢复检查 |
| 206–256 preflight/workspace | references/preflight.md | runner、预算、worktree、direct 模式、PM override |
| 257–280 plan/implement/test | SKILL.md + planning/implementation/testing.md | 原阶段顺序及 implementation-ready 前测试 |
| 281–306 review/repair | references/review.md | 独立 claim/pass、三次上限、自动继续条件 |
| 307–332 delivery/cleanup | references/delivery.md | recorded parent、squash、失败恢复、归档提交 |
| 333–335 interruption | SKILL.md + references/review.md | 恢复重读与预算不重置 |
| 337–381 recovery | references/delivery.md | 有界冲突处理、保护状态、变更后重新审查 |
| 383–405 release/archive | references/delivery.md + SKILL.md | 发布隔离、归档与显式例外 |
| 407–409 references/precedence | SKILL.md + dependencies.md | 按需查阅；依赖/CLI 不一致需报告，不猜测授权 |

原有 references/testing.md、角色 prompts 和条件设计方法保留。
platforms、portable-operations、templates 中已确认漂移以原主流程语义校正。
本映射是静态检查记录，不是运行测试通过声明。
