# FD-002 独立测试报告：Revision 6，第2次执行

<!-- aiw-data: FD-002-test-report-r6-attempt2.json -->

## 结论

唯一一次相关修正复验实际运行 **36** 个业务断言，**36通过、0已执行行为失败**，命令 exit **1**。
需求场景覆盖为 **36/104（34.62%）**。其余 **68** 项未执行：60项因测试定位器中断而阻塞，8项为原先列明的覆盖缺口。
本报告依据本次执行结果统计；首次24项与本次重复的通过项没有相加。两次原始证据分别保存。
计划96/104（92.31%）没有实现，不能替代真实覆盖。

建议 **blocked**。没有观测到失败的产品业务断言，但后续主要功能未测试，不能把0断言失败当成整体验收通过。
业务代码分支覆盖为 **unavailable**，没有字节码覆盖仪器。
本轮唯一一次修正复验预算已使用；Tester已停止执行，没有自动复跑或发布test-report-ready。

Tester：`fd002-tester-20261005-15d090a2`；来源：`FD-002-000006-implementation-ready`。
FD revision：6；摘要：`7869eb2108ad9ac8811e65ea339ecde7d6c08d7394add7230ab85a2e5a178c8a`。FD、产品与验收范围均未修改。

## 实际覆盖及测试阻塞

本次通过原始需求/长文本/转义、独立状态/阶段/真实任务总量，以及全部22项依赖图相关行为：
单实施、并行/integration/final review/verification、无推断边、repair保留历史、循环警告及真实依赖表、
200-task窗口、稳定顺序、窗口外和跨Run/subject关系、1200-edge截断、完整Run总量与候选窗口差异、
部分图、图关闭回退及真实锚点。边界夹具的完整Run4条/候选3条与密集图1201/1200均有实际通过证据。
四角色Run控制权限也均实际通过。

ADMIN导航到 `/ui/runs/fd2-attempt-window` 后，测试代码调用
`sections('attempt|execution history')` 时，把页面h1的Run ID `fd2-attempt-window`
误匹配为内容标题，随后关联section失败。浏览器原始错误为：
`Error: associated public section unavailable fd2-attempt-window`，
堆栈定位到测试脚本 `browser.mjs:154`。
这是测试定位器错误；attempt窗口断言尚未开始，后续ADMIN测试均未执行。
没有证据表明该页面有产品HTTP或模板错误，不能把这个定位器错误归因到产品。

本次JVM内存限制和SQLite显式关闭已生效，启动器没有报告首次的native内存或目录清理异常。
本次所有原始角色日志、数据库、夹具和测试脚本副本保存在：
`tests/fd002/evidence-r6-20261005-072852-34372`。首次原始证据仍在 `tests/fd002/evidence-r6-20261005-072411-32980`；
首次报告为 `FD-002-test-report-r6-attempt1.md/.json`，
JVM根目录副作用已由PM原样整理到首次ADMIN证据目录。

## 命令、授权和证据

两次精确命令均为：

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

工作目录：`D:/03_projects/AI-tools/aiw-dev`。
第一次获r6授权，因JVM native资源不足中断；仅在静态修正JVM heap/code cache上限、
崩溃诊断位置和SQLite连接关闭后，获得r6-v2 approved授权运行本次一次修正复验。
两份授权均绑定相同事件、FD revision/digest、Tester session及精确命令。
记录为 `docs/features/reports/FD-002-test-authorization-r6*.md/.json`。

当前真实计数：`tests/fd002/evidence-r6-20261005-072852-34372/results.json`。
角色断言：`tests/fd002/evidence-r6-20261005-072852-34372/{ADMIN,VIEWER,OPERATOR,APPROVER}/results.json`。
定位器原始错误：`tests/fd002/evidence-r6-20261005-072852-34372/ADMIN/browser.log` 与 `ADMIN/diagnostics.json`。
应用/Chrome/进程清理：各角色 `app.log`、`chrome.log`、`chrome-cleanup.log`。
输入：各角色 `fixture.json` 和 `tester.db`；执行脚本副本位于证据目录根下。

当前通过数以业务断言为准，不将启动、fixture写入、截图生成或清理计入覆盖。
ADMIN在宽度/长ID截图矩阵前中断；该矩阵未完成，只有另三角色完成角色截图。
分支覆盖原始证据不可用，原因是没有字节码覆盖仪器。
未运行Firefox、额外测试或诊断命令、语法检查、Maven、lint、格式器、发行构建或第三次复验。

## 逐场景库存

全部104项在以下表格和JSON `data.scenarios`逐项列出；阻塞/未覆盖均不计通过。

| 场景ID | 验收 | 独立可观察行为 | 本次结果 |
|---|---|---|---|
| requirement-original | AC01 | 需求来自 Run 保存的 requirementTaskId，非首个展示任务 | 通过 |
| requirement-long | AC01/AC06 | 完整长需求可阅读且保留末尾文本 | 通过 |
| requirement-escaped | AC01/AC06 | 需求 HTML/script 作为转义文本呈现 | 通过 |
| requirement-window-outside | AC01 | 需求 task 在200窗口外仍读取原始关联指令 | 通过 |
| requirement-cross-run | AC01/AC04 | 需求关联归属异常时 unavailable，不能采用其他Run指令 | 阻塞：未执行 |
| requirement-missing | AC01/AC06 | 需求关联缺失时明确 unavailable | 阻塞：未执行 |
| status-lifecycle | AC01 | 生命周期与公开 Run API 保存状态一致 | 通过 |
| status-control | AC01 | 控制状态与公开 Run API 分别可阅读 | 通过 |
| status-effective | AC01 | 有效状态与公开 Run API 分别可阅读 | 通过 |
| dashboard-phase | AC01 | 当前阶段与公开 Dashboard 事实一致 | 通过 |
| task-counts | AC01 | 完整 Run task计数与 Dashboard一致 | 通过 |
| no-eta | AC01 | 任务完成数不生成 ETA 或工程完成百分比 | 通过 |
| single-edges | AC02 | 单实施图边与保存的依赖完全一致 | 通过 |
| single-direction | AC02 | 单实施 SVG 从前置任务指向 dependent | 通过 |
| parallel-edges | AC02 | 并行分支和 integration 真实边完全一致 | 通过 |
| parallel-levels | AC02 | 同层并行 task，integration 位于分支后方 | 通过 |
| final-review-level | AC02 | 最终 CODE_REVIEW 位于 integration 后方 | 通过 |
| verification-level | AC02 | INTEGRATION_VERIFICATION 位于 integration 后方 | 通过 |
| no-inferred-edge | AC02 | 同Run taskType/列表顺序不会生成未保存的边 | 通过 |
| repair-added | AC02 | 新增 repair 在局部刷新后显示真实 task 和边 | 通过 |
| repair-history | AC02 | repair 新增后原任务与原依赖仍保留 | 通过 |
| cycle-warning | AC02/AC06 | 循环依赖明确异常而不标完整 DAG | 通过 |
| cycle-table | AC02 | 循环的两条保存边仍可从文本/表格阅读 | 通过 |
| tasks-cap | AC02/AC04 | 201 task返回最近200并显示完整总量 | 通过 |
| tasks-order | AC02/AC04 | 最近任务窗口按真实createdAt/id正序稳定展示 | 通过 |
| outside-prerequisite | AC02/AC04 | 窗口外前置依赖保留真实ID和范围说明 | 通过 |
| outside-no-anchor | AC02/AC04 | 窗口外 task 不生成指向不存在目标的锚点 | 通过 |
| cross-run-edge | AC02/AC04 | 跨Run前置依赖真实ID与范围说明可读 | 通过 |
| cross-subject-edge | AC02/AC04 | 跨subject前置依赖真实ID与范围说明可读 | 通过 |
| edges-cap | AC02/AC04 | 1201候选边最多返回1200并明确截断 | 通过 |
| edges-total | AC02/AC04 | 完整Run边总数与展示task候选边数分别呈现 | 通过 |
| partial-graph | AC02/AC04 | 边截断/窗口外关系明确部分图 | 通过 |
| graph-hide | AC02/AC06 | Hide graph后完整任务表仍可阅读 | 通过 |
| graph-node-link | AC02 | SVG节点定位到对应真实task指令 | 通过 |
| attempt-cap | AC03/AC04 | 301 attempt返回最近300并显示总量/截断 | 阻塞：未执行 |
| attempt-run-filter | AC03/AC04 | 较新的其他Run attempt不会挤占本Run窗口 | 阻塞：未执行 |
| attempt-task-link | AC03 | 历史 attempt关联保存的task ID | 阻塞：未执行 |
| attempt-number | AC03 | 历史 attempt展示实际attemptNo | 阻塞：未执行 |
| attempt-worker | AC03 | 保存历史Worker ID/Fleet链接，无当前租约断言 | 阻塞：未执行 |
| attempt-state | AC03 | FAILED/EXPIRED/COMPLETED历史状态原样可读 | 阻塞：未执行 |
| attempt-times | AC03 | attempt实际创建/开始/结束时间可读 | 阻塞：未执行 |
| attempt-failure-category | AC03 | 保存的failure category可读 | 阻塞：未执行 |
| attempt-reason-paused | AC03 | EXPIRED OPERATOR_PAUSED原因为暂停 | 阻塞：未执行 |
| attempt-reason-cancelled | AC03 | EXPIRED OPERATOR_CANCELLED原因为取消 | 阻塞：未执行 |
| attempt-reason-missing | AC03/AC04 | EXPIRED缺原因显示未记录且不推断自动恢复 | 阻塞：未执行 |
| attempt-summary | AC03 | 报告摘要独立显示，不冒充根因 | 阻塞：未执行 |
| failure-latest-outside | AC03/AC04 | 最新 FAILED即使在timeline窗口外仍独立显示 | 阻塞：未执行 |
| failure-event-time | AC03 | FAILED展示保存的事件时间 | 阻塞：未执行 |
| failure-event-actor | AC03 | FAILED展示保存的actor/task/detail | 阻塞：未执行 |
| recovery-event-time | AC03 | RECOVERED使用事件实际时间和actor | 阻塞：未执行 |
| recovery-event-no-attempt | AC03/AC04 | Run事件无attempt/Worker来源时不补造链接 | 阻塞：未执行 |
| recovery-history-cap | AC03/AC04 | FAILED/RECOVERED最近20条/完整23条/截断可读 | 阻塞：未执行 |
| auto-recovery-explanation | AC03 | 租约自动重新排队机制与人工recover分别说明 | 阻塞：未执行 |
| retry-not-recovery-time | AC03 | 后续attempt创建时间不冒充人工恢复时间 | 阻塞：未执行 |
| timeline-cap | AC04 | 最近300条/总量/截断与公开timeline API一致 | 阻塞：未执行 |
| timeline-chronology | AC04 | 返回窗口保持公开API occurredAt与同时间排序 | 阻塞：未执行 |
| timeline-boundaries | AC04 | 实际首尾时间与公开API窗口一致 | 阻塞：未执行 |
| timeline-utc-groups | AC04 | 跨UTC午夜条目按UTC日期分组 | 阻塞：未执行 |
| timeline-category-filter | AC04 | 类别筛选只隐藏不匹配的返回条目 | 阻塞：未执行 |
| timeline-text-filter | AC04 | 文本筛选匹配窗口中的实际正文 | 阻塞：未执行 |
| timeline-filter-scope | AC04 | 筛选明确只搜索返回窗口 | 阻塞：未执行 |
| timeline-reset | AC04 | Reset恢复整个返回窗口 | 阻塞：未执行 |
| timeline-full-detail | AC04 | 展开可阅读完整长事件detail | 阻塞：未执行 |
| timeline-detail-key | AC04/AC06 | 事件展开使用稳定key并在刷新后保持 | 阻塞：未执行 |
| timeline-missing-metadata | AC04 | 缺失actor/task/detail明确未记录 | 阻塞：未执行 |
| publication-text-only | AC04 | publication ref作为文本且不请求外部文件 | 阻塞：未执行 |
| no-broken-links | AC04 | 页面所有任务/attempt内链均指向存在目标 | 阻塞：未执行 |
| ADMIN-permission | AC05 | Admin可见既有Run控制且拥有POST权限 | 通过 |
| VIEWER-permission | AC05 | Viewer隐藏Run控制并拒绝有CSRF的POST | 通过 |
| OPERATOR-permission | AC05 | Operator可见Run控制且拥有POST权限 | 通过 |
| APPROVER-permission | AC05 | Approver隐藏Run控制并拒绝有CSRF的POST | 通过 |
| csrf-required | AC05 | 已登录Admin无CSRF写入被拒绝且状态不变 | 阻塞：未执行 |
| pause-submit | AC05 | 合法pause产生反馈与PAUSED事实 | 阻塞：未执行 |
| resume-submit | AC05 | 合法resume产生反馈与ACTIVE事实 | 阻塞：未执行 |
| recover-submit | AC05 | 合法recover产生反馈与RUNNING事实 | 阻塞：未执行 |
| cancel-warning | AC05 | Cancel存在不可逆/确认提示 | 阻塞：未执行 |
| cancel-submit | AC05 | 合法cancel产生CANCELLED事实 | 阻塞：未执行 |
| cancel-irreversible | AC05 | 已cancelled Run无法resume | 阻塞：未执行 |
| stale-action-conflict | AC05 | 旧页面pause在状态已改变时显示竞争错误 | 阻塞：未执行 |
| action-snapshot-reload | AC05 | 动作快照说明及Reload action availability入口可读 | 阻塞：未执行 |
| empty-graph | AC06 | 零task/attempt/dependency的缺失关联Run明确空态 | 阻塞：未执行 |
| long-id | AC06 | 完整长Run/task ID在任务表可阅读 | 阻塞：未执行 |
| width-390 | AC06 | 390px窄屏页面不横向溢出且图内部滚动 | 阻塞：未执行 |
| width-768 | AC06 | 768px页面不横向溢出 | 阻塞：未执行 |
| width-1280 | AC06 | 1280px页面不横向溢出 | 阻塞：未执行 |
| width-1440 | AC06 | 1440px页面不横向溢出 | 阻塞：未执行 |
| motion-toggle | AC06 | Motion off在导航后保持并关闭动效 | 阻塞：未执行 |
| reduced-motion | AC06 | 系统减少动效设置关闭装饰动画 | 阻塞：未执行 |
| refresh-auto | AC06 | 5秒周期真实更新只读事实 | 阻塞：未执行 |
| refresh-filter | AC06 | 刷新后类别/文本筛选条件保持并重新应用 | 阻塞：未执行 |
| refresh-graph-state | AC06 | 刷新后图开关及图内滚动保持 | 阻塞：未执行 |
| refresh-form-focus | AC06 | 编辑动作表单时输入/焦点保持并暂停刷新 | 阻塞：未执行 |
| refresh-selection | AC06 | 阅读选区的节点/文字/offset刷新后保持 | 阻塞：未执行 |
| refresh-scroll | AC06 | 局部刷新保留页面滚动位置 | 阻塞：未执行 |
| refresh-details | AC06 | 任务展开details刷新后保持 | 阻塞：未执行 |
| keyboard-focus | AC06 | Tab键真实焦点及可见outline可用 | 阻塞：未执行 |
| no-js | AC04/AC06 | 禁用JavaScript仍可阅读返回timeline与任务表 | 未覆盖 |
| firefox-graph | AC02/AC06 | Firefox依赖图、表格、窗口和锚点兼容 | 未覆盖 |
| firefox-refresh | AC06 | Firefox筛选/选区/焦点/动效刷新保持 | 未覆盖 |
| lease-auto-runtime | AC03 | 真实租约到期requeue后页面区别人工recover | 未覆盖 |
| lease-pause-revocation | AC05 | pause撤销真实活跃Worker租约后详情保持一致 | 未覆盖 |
| concurrent-posts | AC05 | 真正同时两个控制POST竞争与原子性反馈 | 未覆盖 |
| timeline-tz-browser | AC04 | 非UTC浏览器时区仍按UTC日期分组 | 未覆盖 |
| missing-time | AC03/AC04 | 缺失可选attempt started/finished时间明确未记录 | 未覆盖 |

## 剩余风险与后续

覆盖34.62%低于70%。attempt/失败/恢复、timeline、CSRF/恢复/取消/竞争、空态/错误需求关联、
响应式/动效/刷新阅读保持等尚无完整运行证据，PM仍需处理测试阻塞。
Firefox本轮未运行，延期问题尚未决定；没有转移FD-001的决定。
分支覆盖也不能由36个通过断言替代。

如继续执行，应先修正仅匹配实际内容标题的测试定位器并获得新的执行预算。
本报告不自行授权任何后续命令，Tester保持等候PM串行发布决定。

