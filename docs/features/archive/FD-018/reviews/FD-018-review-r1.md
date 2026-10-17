# FD-018 独立实现评审 r1

<!-- aiw-data: FD-018-review-r1.json -->

结论：请求补正（changes-requested）。发现一项确定的配置兼容缺陷，尚不能通过整个 FD。

## 发现

### R1 / P1：把 Go App Server 无法选择的 fast Profile 判定为可用

- 当前规则：FD Solution D2 要求使用三语言 Worker 共同支持的 provider/runtime/fast/effort 集合，领取、guidance、start 使用一致的可执行冻结配置；Acceptance 3 要求不支持的显式配置说明不可用原因，不静默回退。
- `src/main/java/me/asu/aidev/workflow/application/RecommendationProfiles.java:23` 在 codex-app-server 分支接受 `fast=true`。`RecommendationTaskPolicy.matches` 及 `WorkflowRecommendationService.availability` 只按 Runtime 前缀和在线角色匹配，因此一个 `codex-app-server` / fast Profile 在仅 Go App Server 分析 Worker 在线时仍显示可用、允许提交和领取。
- 实际现有调用路径：`workers/process-go/main.go:320` 使用 `cfg.AgentRuntime` 调用 `chooseAIProfile`；该文件第 154 行的筛选条件仅在 `runtime == "codex-cli"` 时接受 Fast，其余 Runtime 要求 `!Fast`。因此 App Server 的上述唯一冻结候选被全部丢弃，Worker 在调用 `/start` 之前返回 `no approved codex-app-server profile`，推荐无法执行。服务端 start 的快照校验无法补救这个前置失败。
- 最小补正：将推荐兼容集合保守收窄为 App Server `fast=false`，或使就绪和领取依据真实 Worker 可执行能力排除该组合。不要仅修改报告，也不要用默认 Profile 回退掩盖显式设置。此发现来自现有源码静态控制流，不宣称运行复现。

## 身份与范围

- Reviewer Session：`fd018-reviewer-20261017-independent-r1`，与实现 Worker 分离。
- 真实 claim：`FD-018-000004-implementation-ready`；事件 FD revision 4，原实现证据 `FD-018-implementation-r1.md`。
- 比较基线：`2959fb6`；被审实现 HEAD：`0e6b53548644545ec29258434cb9441ce56e2781`。工作树 FD/index 修改是 CLI emit 后的真实生命周期元数据，不视为源码漂移。
- 已审阅已批准 Issue 的 13 条规则、FD、设计 r2、双格式实现和独立验收报告、实际 Java/SQL/JS/模板差异，以及相邻 Worker 选择器、任务领取和执行调用路径。
- 配置保存/请求的 settings 锁与 revision、排队取消的状态 CAS、推荐租约 FAILED/attempt EXPIRED/容量释放、actor 上下文恢复与版本、人工保存草稿、Plan 删除 SET NULL/CASCADE 均有具体代码路径支持。未另报风格或推测性缺陷。

## 证据和限制

实际读取 `RecommendationAcceptanceTest` 的 A01–A08、原 `RecommendationPolicyTest`、验收原始日志以及 TDD Red/Green 日志。9 项通过事实成立：8 个新场景与 1 个重用 TDD，0 failures/errors/skipped；独立执行版本为 `9108cf4532a46894386530ed0acf2035a6e32180`。Red 是未知 planner 未抛异常的目标断言失败，首次 POM 准备错误没有被计为有效 Red。最后 JS copy 条件修改未改变被测 Java/SQL，因此不使该后端证据失效，但没有浏览器通过证据。

测试没有覆盖 R1 的 Go App Server fast 候选选择。已有测试中的 catalog/部分预算是 mock，SQLite 单连接不证明 PostgreSQL 或并发交错；HTTP 权限测试不包含 CSRF interceptor。保持报告原有边界，不新增强制测试门槛，也不把未运行行为标为通过。

实际命令：定向 `Get-Content`、`rg/rg --files`、`git diff --stat`、`git diff 2959fb6 0e6b535 -- <相关路径>`、`git rev-parse HEAD`、`git status --short`；`aiw fd --help/show/claim/emit --help`；真实 claim 命令采用上述 Session。初始少数共享资源路径、receipt 子命令和 PowerShell 通配路径定位失败后改为实际资源路径读取，不属于行为验证。

本 Reviewer 未运行测试、编译、构建、网络、模型、Worker 或子代理。未验证浏览器交互、真实跨语言协议、PostgreSQL、并发压力、实际预算持久化及真实模型费用。未 commit、merge 或 archive。

将以本报告为 artifact 对真实来源事件发出 `changes-requested`，返回 Worker 补正 R1；发布与部署不在范围内。
