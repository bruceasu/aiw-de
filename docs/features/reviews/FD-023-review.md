# FD-023 独立实现评审

<!-- aiw-data: FD-023-review.json -->

结论：**changes-requested**。发现一项当前文档遗漏；未发现本轮差异造成的明确生产路由或认证缺陷。

- Reviewer：`fd023-reviewer-20261021-01`，独立于 Worker；已 claim `FD-023-000004-implementation-ready`。
- 审查范围：`6f34bf4..ea00861`，FD Revision 4、Worker MD/JSON、设计 D1、稳定规范和实际源差异；本轮为第1次实现评审。

## R1：英文现行文档仍指向旧 API（中等，阻断）

FD Work 2.2 和验收要求所有当前文档同步。`docs/current/README.md` 明确该目录是当前操作和用户文档，不是归档；其中10份文件仍引用旧地址。例如 `docs/current/04-WORKER-PROTOCOL-AND-WORKERS.md:10`、`:41` 使用 `/api/worker-protocol/v1`，`docs/current/QUICK-START.md:43` 使用 `/api/r8/fleet/dashboard`，`:203` 使用 `/api/r7/approvals/.../decision`。新服务不提供旧地址 alias，照此注册 Worker、查看 Fleet 或审批将访问已移除的路由。Worker“当前文档无旧命名空间”的结论因此不完整。

最小修正：同步 `docs/current/` 下03、04、05、06、07、09、11、16、QUICK-START、BOSS-INTRO中的本系统 API 地址，并说明 canonical 前缀和无 alias 兼容影响；同步修正 Worker 证据。保留 release validation、历史 FD/证据和外部服务 URL。无需为文档修正重跑编译或测试。

## 已支持的行为与证据

实际核对生产 controller 的类/方法映射及混合 HTML/JSON controller：API 迁入 `/api/r1`，HTML/form 保留 `/ui`，没有旧 API alias。核对全部改动 Worker、浏览器脚本、模板（包含 `${path}` 插值）、源测试和中文现行文档的 URL 差异，及隐藏目录在内的有界残留扫描；除上述英文目录外，未发现其他当前本系统调用源遗漏。历史验证记录及外部 OpenAI 文档 URL 不属于迁移对象。

六个 console API 根由共享分类同时供 WebConsole interceptor 和管理 interceptor 使用；order(-100) 先认证，后者在 clearHuman/legacy receipt 前排除这些根。首次启用不登记自身 receipt；Approver 的授权中心操作不被额外 Operator 检查缩减。原 session、CSRF、actor、role、owner 服务调用保留。bearer filter、delegate 路径及 body 身份认证、lease 操作白名单均同步；Drain/Offline继续管理写入。分类先扣除 context path。以上为静态支持，未宣称运行时认证通过。

Worker 报告记录生产 Java25 离线 compile（261文件）、compile-worker-runtime、示例 Go 和修改 JS/Python/TS 语法检查通过；本 Reviewer 未重跑。Java示例单独 javac/清理被自动审批拒绝，未执行或绕过，该限制保留；仅URL字面量变化不构成本轮新增源编译问题。

## 实际操作与限制

实际执行：`aiw fd show`、`aiw fd claim ... --session fd023-reviewer-20261021-01`、`aiw fd emit --help`；`git diff --stat/--numstat`、定向 `git diff`、`git log`、`git status --short`；`rg`（含PCRE残留和隐藏目录扫描）、`Get-Content`及PowerShell定向摘录；写入本评审和FD记录。初次共享资源相对路径及一次README通配/config路径输入有误，按真实位置修正读取，未将出错扫描当作无残留证据。

未运行测试、编译、typecheck、服务、HTTP、浏览器、网络、迁移、最终构建或子代理；未重试被自动审批拒绝的命令。实际 Spring 映射与认证交互、非空 context path 的端到端行为仍未运行验证。外部客户端需同步改址，兼容影响已由FD明确接受。本结论阻断点是已确认的当前文档缺口，不是要求新增运行授权。
