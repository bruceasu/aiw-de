# 当前能力清单

## Autonomous Engineering

- 自然语言 Requirement Intake；
- Requirement Analysis；
- Solution Architecture；
- Implementation Planning；
- 1～16 个独立 WorkItem；
- Parallel Coding；
- Git Worktree 隔离；
- Parallel Result Integration；
- Code Review + Verification；
- Repair Loop；
- Engineering Gate；
- Manual/Automatic Human Approval；
- Telegram Approve/Reject。

## Worker Platform

- External Pull Worker；
- Dynamic register/heartbeat/drain/offline；
- Capability matching；
- Worker maxConcurrency；
- Python/TS/JS/Go/Java 示例；
- Codex SDK/CLI；
- Optional Bearer Token；
- Worker 本地路径不进入 Control Plane。

## Reliability

- Atomic claim；
- Lease/Heartbeat；
- Lease Expiry Recovery；
- Attempt History；
- Crash 与 explicit failure 分离；
- Idempotent task materialization；
- Completion authority validation；
- Pause/Resume/Cancel/Recover。

## Scheduling

- Capability filter；
- Priority；
- Aging；
- Run fairness；
- Worker capacity；
- Run concurrency budget；
- Runtime Run limit；
- Fleet global ceiling；
- Runtime Fleet limit；
- 收缩不 kill 健康 Lease。

## Observability

- Run Status；
- Run Dashboard；
- Timeline；
- Fleet/Backlog Dashboard；
- Operator/Failure audit；
- Run/Fleet scheduling audit；
- accounting diagnostics。

## Persistence

- JDBC only；
- SQLite local；
- PostgreSQL shared；
- Flyway V1-V14；
- 9 tables。

## 当前明确未做

- Production Management API Auth/RBAC；
- PR/Merge/Release/Deploy；
- Capability-level quota；
- 完整 Multi-repo final authority；
- 已充分验证的 PostgreSQL 多实例 stress/chaos；
- General Skill/Tool permission governance。

当前更合理的是先验证真实 Autonomous Engineering 效果，而不是继续增加 Scheduler 功能。

## Web Console

- 创建 Autonomous Run；
- Dashboard / Run Detail / Timeline；
- Pause / Resume / Recover / Cancel；
- Run/Fleet Runtime Scheduling Control；
- Worker Drain / Offline；
- Approval Request / Approve / Reject；
- 全部控制台业务页面要求真实 Session Login，保留 CSRF；
- VIEWER / OPERATOR / APPROVER / ADMIN。
