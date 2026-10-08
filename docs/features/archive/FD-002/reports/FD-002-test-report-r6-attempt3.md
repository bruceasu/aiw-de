# FD-002 独立测试报告：Revision 6，第3次执行

<!-- aiw-data: FD-002-test-report-r6-attempt3.json -->

## 结论

新增一次预算批准后的命令实际执行 **36** 个业务断言，**36通过、0已执行行为失败**，命令 exit **1**。
真实需求覆盖为 **36/104（34.62%）**；**68** 项未执行，包括60项受测试定位器阻塞、8项原有覆盖缺口。
计划96/104（92.31%）未达成。重复执行同一场景只计一次覆盖。

建议 **blocked**。没有观测到失败的产品业务断言；但测试基础设施仍未能完成后续主要功能验证。
业务代码分支覆盖为 **unavailable**，没有字节码覆盖仪器。
初验、一次修正复验和人类新增批准的一次运行均已使用，未自行重跑或发布test-report-ready。

Tester：`fd002-tester-20261005-15d090a2`；来源事件：`FD-002-000006-implementation-ready`。
实际测试FD revision为6；摘要：`7869eb2108ad9ac8811e65ea339ecde7d6c08d7394add7230ab85a2e5a178c8a`。
本报告不改变FD、产品或验收范围。

## 真实通过范围与定位器阻塞

实际通过原始需求/长文本/转义、窗口外需求、独立状态/阶段/真实任务总量，
全部22项依赖图行为，以及ADMIN/VIEWER/OPERATOR/APPROVER四角色Run控制权限。
依赖图行为包括单实施、并行/integration/final review/verification、repair保留历史、循环、
201-task取最近200/稳定顺序、窗口外与跨Run/subject、1201边取1200、Run4条/展示候选3条、
图方向/层级、关闭回退和真实锚点。

ADMIN读取 `/ui/runs/fd2-attempt-window` 的attempt区之前，测试章节定位器仍抛出：
`Error: associated public section unavailable fd2-attempt-window`，
原始堆栈为测试脚本 `browser.mjs:155`。
上一轮“仅h1匹配”的诊断过窄；排除h1以后，身份ID仍从其他标题级别被选入，静态修正不足。
该错误来自测试定位器，不是一个产品行为断言失败。
attempt窗口及后面的失败/恢复/timeline/控制/布局等断言没有执行，不能推断它们通过。

本次启动器没有报告内存或临时目录清理异常；四角色均顺序运行，其他三个角色权限与截图完成。
ADMIN在宽度截图矩阵前中断，未完成密集图/长ID矩阵。
全部输入、原始数据库、日志、脚本快照和结果保存在 `tests/fd002/evidence-r6-20261005-073707-7320`。
前两轮证据及报告保留，没有覆盖。

## Firefox独立决定

人类已单独允许FD-002 Firefox延期，记录：
`docs/features/reports/FD-002-firefox-decision-r6.md/.json`，
批准引用 `approved:conversation:call_GbKQIKiYeysoTKpcQOL96T3G`。
范围仅Firefox图/布局与刷新保持；不豁免页面行为，不改变其他未覆盖项。
`firefox-graph` 和 `firefox-refresh` 仍在104分母中标未覆盖，不记通过。
本报告没有移用FD-001的延期。

## 命令、授权及原始证据

三个经分别授权的执行都使用唯一精确命令：

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

工作目录 `D:/03_projects/AI-tools/aiw-dev`，无额外模式或参数。

| 次数 | 授权 | 原始结果 | 证据目录 |
|---|---|---|---|
| 1 | r6 | 24通过，native内存中断，exit1 | `tests/fd002/evidence-r6-20261005-072411-32980` |
| 2 | r6-v2 | 36通过，章节定位器中断，exit1 | `tests/fd002/evidence-r6-20261005-072852-34372` |
| 3 | r6-v3；新增预算批准call_f5f0M7Pjmeh3oz6wSaJcGAno | 36通过，章节定位器仍中断，exit1 | `tests/fd002/evidence-r6-20261005-073707-7320` |

授权记录均为 `docs/features/reports/FD-002-test-authorization-r6*.md/.json`，
绑定event6/revision6/digest/Tester session/精确命令。
当前执行计数与104场景真实状态：`tests/fd002/evidence-r6-20261005-073707-7320/results.json`。
角色断言：各角色 `results.json`；阻塞原始证据：`ADMIN/browser.log`、`ADMIN/diagnostics.json`。
应用/Chrome/清理日志、原始 `tester.db`、`fixture.json` 和执行脚本副本也在证据目录内。
首次崩溃诊断已由PM原样移入首次ADMIN证据目录，内容没有修改。

没有运行第四次命令、Firefox、额外diagnose或测试、语法检查、Maven、分支覆盖仪器、lint、格式器或发行构建。
静态准备和证据读取不计业务覆盖，截图生成或fixture写入也不计通过。
分支覆盖的原始证据不可用，因为没有字节码覆盖仪器。

## 逐场景库存

以下104项与JSON `data.scenarios`一致。阻塞或延期未覆盖均不计通过。

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
| firefox-graph | AC02/AC06 | Firefox依赖图、表格、窗口和锚点兼容 | 未覆盖：已批准延期 |
| firefox-refresh | AC06 | Firefox筛选/选区/焦点/动效刷新保持 | 未覆盖：已批准延期 |
| lease-auto-runtime | AC03 | 真实租约到期requeue后页面区别人工recover | 未覆盖 |
| lease-pause-revocation | AC05 | pause撤销真实活跃Worker租约后详情保持一致 | 未覆盖 |
| concurrent-posts | AC05 | 真正同时两个控制POST竞争与原子性反馈 | 未覆盖 |
| timeline-tz-browser | AC04 | 非UTC浏览器时区仍按UTC日期分组 | 未覆盖 |
| missing-time | AC03/AC04 | 缺失可选attempt started/finished时间明确未记录 | 未覆盖 |

## 剩余风险

真实覆盖34.62%低于70%。主要attempt/失败/恢复、timeline、CSRF与控制竞争、空态与错误需求关联、
响应式/动效/刷新阅读保持还缺运行证据。Firefox延期不解决这些缺口，也不替代分支覆盖。
后续应先纠正只选择内容语义标题的测试定位器，再由PM按预算与实际证据决定继续验证路径；
本报告不授予任何后续执行权，Tester等待PM串行发布决定。

