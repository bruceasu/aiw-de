# FD-003 独立测试报告（revision 8）

<!-- aiw-data: FD-003-test-report-r8.json -->

## 结论

Tester Session：`fd003-tester-20261005-ad16f437`；来源事件：`FD-003-000008-implementation-ready`；FD revision：8；FD digest：`d558c9409d4a0e1af7a5c6207dd66c5c00e88e507b3e8463b86259d0b78889ad`。

依据 FD 验收、REQ00001 M3 场景和公开 Web Console 指南，列出 50 个可观察场景。用户明确要求本轮不做测试：执行 0 项，通过 0 项，失败 0 项，未执行 50 项。需求场景覆盖率 **0/50（0.00%）**；业务代码分支覆盖率 **未测量**。本报告是场景库存和覆盖风险记录，不构成运行验收通过。未编写或运行测试代码。

## 场景与证据

| ID | 验收 | 独立可观察行为 | 结果 |
|---|---|---|---|
| worker-identity | AC01 | 同一 Worker 在 Overview、Fleet 与执行入口保持相同机械身份，并能看到真实 Worker ID | 未执行 |
| worker-status-active-idle | AC01 | ACTIVE 且没有活动租约时，管理状态与待命占用分别可读 | 未执行 |
| worker-status-executing | AC01 | ACTIVE 且有活动租约时，管理状态与实际占用分别可读 | 未执行 |
| worker-status-draining | AC01 | DRAINING 时说明停止新领取且现有执行继续 | 未执行 |
| worker-status-offline | AC01 | OFFLINE 时状态文字、身份和工作事实仍可读 | 未执行 |
| worker-metadata | AC01 | 有记录的 runtime 与 capabilities 原样显示，长值可完整阅读 | 未执行 |
| worker-metadata-missing | AC01 | 缺少 runtime/node 信息时明确未知，不虚构角色或节点 | 未执行 |
| worker-lease-link | AC01 | 活动租约显示真实 task、Worker、attempt、过期时间及可用 Run 入口 | 未执行 |
| worker-lease-missing | AC01 | 租约的可选字段缺失时显示未知，不推断历史 claim 原因 | 未执行 |
| ready-primary-policy | AC02 | policy 阻挡优先归入 policy 主原因，完整总数与 Backlog 一致 | 未执行 |
| ready-primary-run | AC02 | 无 policy 阻挡而 Run 准入受限时归入 Run admission | 未执行 |
| ready-primary-capability | AC02 | 前两项允许而无兼容 Worker 时归入 capability，不能误写容量限制 | 未执行 |
| ready-primary-capacity | AC02 | 存在兼容 Worker 但无可领取 Worker 时归入 Worker capacity | 未执行 |
| ready-primary-fleet | AC02 | 前述条件允许而 Fleet 准入受限时归入 Fleet admission | 未执行 |
| ready-claimable | AC02 | 所有当前准入条件允许时显示可领取 | 未执行 |
| ready-overlap | AC02 | 一个 READY 任务同时受多个条件限制时并列显示全部当前事实，主分类仍互斥 | 未执行 |
| ready-evidence-unknown | AC02 | Run 或关联证据缺失时标记未知，不猜测阻挡原因 | 未执行 |
| ready-current-only | AC02 | READY 分类和限制说明明确是当前快照，不冒充历史 claim 决策 | 未执行 |
| ready-task-scope | AC02 | 从积压分类可定位受影响的真实任务，详情筛选明确仅在返回窗口内生效 | 未执行 |
| run-link-valid | AC02 | AUTONOMOUS_REQUIREMENT subject 且保存的 Run 存在时链接正确 Run | 未执行 |
| run-link-missing | AC02 | AUTONOMOUS_REQUIREMENT subject 对应 Run 缺失时保留原始 ID 并显示 Run unavailable | 未执行 |
| run-link-other-subject | AC02 | 其他 subject 类型只显示原始类型和 ID，不生成 Run 链接 | 未执行 |
| fleet-limit-unlimited | AC03 | Fleet 或 Run 限额为 0 时明确表示无限，并展示实际占用与可用槽位 | 未执行 |
| fleet-limit-saturated | AC03 | 使用量达到限额时显示无可用槽位和相应新 claim 限制 | 未执行 |
| fleet-limit-overcommitted | AC03 | 占用超过新限额时显示 overcommitted，已有租约仍继续 | 未执行 |
| fleet-limit-contraction | AC03 | 把限额调低到占用以下时提交前说明自然排空，成功后呈现真实限制 | 未执行 |
| worker-drain | AC03 | 对 ACTIVE Worker 提交 drain 后停止新领取且已有租约继续 | 未执行 |
| pause-effect | AC03 | Run pause 明确区别于自然排空，活动租约按现有规则撤销 | 未执行 |
| cancel-effect | AC03 | Run cancel 显示不可逆后果，不写成自然排空 | 未执行 |
| role-viewer | AC04 | VIEWER 无管理动作入口，有效 POST 也被服务端拒绝 | 未执行 |
| role-operator-admin | AC04 | 有权限角色只看到当前可用动作，提交后以实际结果反馈 | 未执行 |
| csrf | AC04 | 缺少 CSRF 的写入请求被拒绝，状态不变且不显示成功 | 未执行 |
| offline-precondition | AC04 | 有活动租约的 Worker 不能 offline，服务端拒绝并反馈真实原因 | 未执行 |
| stale-action | AC04 | 页面打开后状态变化导致动作不再可用时，POST 反馈竞争错误而非假成功 | 未执行 |
| action-feedback | AC04 | 成功与失败均通过 redirect/flash 显示，并与重新读取的状态一致 | 未执行 |
| fleet-worker-limit | AC05 | Worker 详情超过 100 条时显示前 100、真实总数和当前快照范围 | 未执行 |
| fleet-ready-limit | AC05 | READY 详情超过 100 条时显示前 100、真实 Backlog 总数和局部筛选范围 | 未执行 |
| fleet-lease-limit | AC05 | 活动租约超过 100 条时显示前 100、真实总数和当前快照范围 | 未执行 |
| ready-order | AC05 | READY 详情按优先级降序、创建时间升序、task ID 升序稳定显示 | 未执行 |
| lease-order | AC05 | 活动租约按过期时间升序、task ID 升序稳定显示 | 未执行 |
| worker-order | AC05 | Worker 列表按管理状态、Worker ID 稳定显示 | 未执行 |
| heartbeat-fact | AC05 | 显示心跳时间与 capturedAt；过期或缺失数据不自动评为健康或故障 | 未执行 |
| refresh-edit | AC05 | 编辑限额等表单时自动刷新保留输入、焦点和操作提示 | 未执行 |
| refresh-stale | AC05 | 刷新中断或快照陈旧时保留最后一次成功快照并给出新鲜度提示 | 未执行 |
| empty-fleet | AC06 | 无 Worker、READY 任务或租约时显示真实零值和有意义空态 | 未执行 |
| long-values | AC06 | 长 Worker/task/Run ID 与 capability 在窄窗口仍可阅读和复制 | 未执行 |
| no-capacity | AC06 | 可用容量为零时数字、限制语义和可用动作仍清楚 | 未执行 |
| reduced-motion | AC06 | 关闭动效或系统减少动效时身份、状态、占用与动作仍可辨认 | 未执行 |
| keyboard-focus | AC06 | 键盘导航能到达主要链接和操作，焦点可见 | 未执行 |
| narrow-window | AC06 | 窄窗口中主要 Fleet 内容和操作无需页面级横向溢出即可访问 | 未执行 |

全部场景均为产品行为，保留在分母中；无纯静态验收排除项。没有执行记录、截图、服务响应、浏览器观察或分支插桩数据。FD 和公开指南只提供验收依据，Worker 的编译与静态说明不计入本 Tester 的运行结果。

## 命令与风险

- 本 Tester 实际执行的测试、浏览器、覆盖率、验证、格式化和网络命令：**无**。
- 测试授权记录：无；用户已明确要求本轮不做测试。
- 原始覆盖率证据：无；没有运行测试或分支插桩，因此不能计算业务代码分支覆盖率。
- 剩余风险：Fleet 模板实际渲染、Run 关联、READY 多条件诊断、有限列表范围、表单权限与 CSRF、状态竞争、租约自然排空、局部刷新、窄窗口及减少动效均无本轮运行证据。PM 需按用户决定记录覆盖例外及这些风险；独立 Reviewer 可检查静态实现与证据，但不能把未执行场景认定为通过。

本报告推荐状态：`blocked`（运行覆盖为 0，需 PM 明确例外决定）。
