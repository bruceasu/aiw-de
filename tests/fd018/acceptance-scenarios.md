# FD-018 独立验收场景（先于实现访问固定）

会话 `/root/fd018_acceptance`，基线 `435bcbaeb2b67d1ccf66653d6708fd9c60aa75bb`，FD revision 3。此时只读取 FD/Issue、规则与目录状态，未读取生产实现或已有测试。预期从 FD Acceptance 和 Solution D1–D4 推导；不采用实现输出作为 oracle。

| ID | 规则/边界 | 输入和前提 | 预期、关键断言与错误实现反例 |
|---|---|---|---|
| A01 | Acceptance 1–2, D1；持久化 | 新内存数据库执行迁移，保存关闭后重新构造服务 | 默认 enabled=true；保存后 enabled=false、revision 增加、审计含 actor/前后值；过期 revision 拒绝且无额外审计。拒绝仅内存保存、无 CAS 的实现。 |
| A02 | Acceptance 3, D2；配置筛选 | 明确 profile/model/effort 与 approved 集合冲突；本地默认配置 | 不支持的显式项拒绝、不返回默认；空项允许默认。拒绝静默回退（已有 TDD 重用须标明）。 |
| A03 | Acceptance 3；推荐创建 | 无在线适配 Worker，然后 ACTIVE 心跳新鲜但容量已满 | 离线拒绝且无任务；忙碌在线允许排队。拒绝 findAvailable 作为在线判断。 |
| A04 | Acceptance 4, D1；关闭/撤回 | 排队和已领取推荐并存，再关闭 | READY/BLOCKED 取消，CLAIMED/RUNNING 保留，新请求拒绝；运行中撤回拒绝。拒绝全部取消或关闭后继续入队。 |
| A05 | Acceptance 5, D4；持久化上下文 | actor A 提交请求；重复相同 key；另一标签旧 revision；actor B 查询 | 重复返回原任务不改 revision，不同内容相同 key/旧 revision 拒绝；重新构造服务可恢复最后 goal/version/任务；B 无权恢复。拒绝内存恢复、重复建任务、跨 actor 泄露。 |
| A06 | Acceptance 6, D3；租约恢复 | 过期推荐和普通任务，含 stopRequested | 推荐 FAILED，普通任务可重排；推荐不回 READY，旧 token 完成被拒绝；重复恢复不重复释放预算。拒绝推荐自动重派及过期结果接纳。 |
| A07 | Acceptance 1；HTTP 权限 | 非管理员向系统设置保存边界请求 | 被拒绝且无持久化修改。拒绝仅 UI 隐藏权限。 |

有限执行优先 JDBC 的 A01、A03–A05 及现有边界适配可行的 A06/A07。无法安全适配的场景报告未运行，不用 mock 替换被验证的规则。A02 可重用已存在测试，但不计新增独立 oracle。浏览器交互、真实 Worker/模型、PostgreSQL 并发与跨进程重启均不在执行授权内；串行 CAS 不等同真实并发测试。

追加 A08（实现访问后，兼容回归，单独披露）：FD 要求保留既有普通 Plan 合约，已有到期删除为本次范围外的既有行为。已绑定上下文的 Plan 删除应成功，保留 actor 上下文并清除失效 planId/映射；数据库外键不能意外阻止既有删除。用真实 SQLite 迁移与 JDBC DELETE 验证外键边界，不声称覆盖完整 archive service 定时流程。该场景独立于先前 A01–A07 的先验 oracle；Host 指出风险后从既有删除契约推导。

追加 A09（独立评审 R1 后的相关复核）：从 Host 交付的已确认共同支持集合契约推导，不以筛选函数当前输出作 oracle。`codex-app-server` 必须 `fast=false`；明确指定 fast=true profile 时，即使 approved 列表另有兼容 profile 也须拒绝并说明 fast=false 原因；明确指定非 fast app-server profile 时仍须返回该 profile，不得误封整个 runtime。输入分别为 fast/非 fast 两个 app-server profile 加可用 codex-cli profile；精确异常/选择断言可识别忽略 fast、退回其他 profile、误拒整个 app-server 的错误实现。此为同一有限验收 effort 在相关生产修正后的唯一复核运行，若意外失败立即静态诊断，不追加重跑。
