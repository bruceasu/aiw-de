# FD-002 独立测试报告：Revision 9，第2次执行（总第5次）

<!-- aiw-data: FD-002-test-report-r9-attempt2.json -->

## 结论

相关资源修正后的唯一重试实际执行 **78** 个业务断言，**74通过、4失败**，命令exit **1**。
需求场景覆盖 **74/104（71.15%）**；**26** 项未运行，包括18项计划内未到达和8项原未覆盖。
计划96/104（92.31%）未完成。建议 **blocked**，覆盖超过70%不能消除4个失败或替代未运行的关键行为。
本轮不累加上轮3个成功，历史revision 6的36个成功也不作为本revision本轮结果。
业务代码分支覆盖 **not measured**，没有字节码分支仪器，不能推算。

Tester：`fd002-tester-20261005-15d090a2`；implementation事件：`FD-002-000009-implementation-ready`；
实际测试FD revision **9**；LF正规化摘要：`baf9278f6c5241297e9b992a05ac3487c99989895404a7cae2dd3ccb6f8b75b1`。
本次初验与条件重试预算均已用尽，停止运行。没有改FD、生产实现或历史结果，也没有提前emit。

## 实际通过范围

实际断言通过需求原始关联/完整长文本/转义与缺失归属、独立状态/阶段/任务计数、真实依赖图
及单实施/并行集成/最终审查/验证/repair历史、循环、200任务与1200边窗口、真实范围说明与链接。
attempt总量/截断、保存的task/worker/state/time/category/reason/summary，FAILED/RECOVERED真实来源，
20条恢复范围、自动恢复说明和重试时间区别均有已执行成功断言。
有限timeline总量/截断/边界/UTC日期/排序/全文/缺失字段/Reset、事件key、展开/选区/页面滚动保持、
5秒刷新、publication文本、空态/长ID及四角色基础权限也有已执行成功断言。
这些仅表示表中具体断言通过，不扩大为整条宽泛AC验收或生产性能结论。

## 四个真实失败

| 场景ID | 原始失败 | 当前归因限制 |
|---|---|---|
| attempt-run-filter | returned attempt IDs differ from newest300 | 外Run泄漏检查及a301存在/a1不存在检查通过，但distinct ID计数失败；未保存实际ID集合/计数，无法区分读取问题与定位/计数问题 |
| timeline-text-filter | text filter did not limit entries | 可见返回条目集合没有满足非空且全部含BEFORE MIDNIGHT的断言；保留失败 |
| timeline-category-filter | Error: category options absent | helper选到的select没有非空value候选；未保存选中控件/完整options数据，无法仅凭displaylabel规则归因 |
| refresh-filter | filter not reapplied | 控件值保持检查通过，刷新后的条目集合没有缩小；受过滤结果/条目定位两种可能影响 |

全部4个失败原样保留在原始results、日志和本报告机器计数中，没有改写为passed。
其中3个失败截图已保存：failed-timeline-text-filter.png、failed-timeline-category-filter.png、
failed-refresh-filter.png；attempt-run-filter截图未保存，不能声称它有截图证据。
已有failed-timeline-category-filter截图直接显示：Timeline总量7/7，筛选输入已Reset为空，
匹配计数却为0/0且全部条目不可见，Category只显示All categories。
当前用户可见的时间线阅读/筛选不一致须保留为失败，不能仅以可能的测试定位误差豁免。

## Node中断与未运行范围

ADMIN通过long-id后，Node进程exit **134**：
`FATAL ERROR: Committing semi space failed. Allocation failed - JavaScript heap out of memory`。
原日志GC数字约10–16 MB，支持native commit分配失败风险，不能据此声称大JavaScript堆容量已耗尽。
本次ADMIN/app.log显示应用启动成功，未记录上轮同样的JVM native崩溃。
Chrome.log记录GPU进程exit34；只作为既有环境日志，没有额外GPU/权限探测。
本次raw summary只报告ADMIN browser exit134，没有清理、DB保存或live-owned-PID异常。

Node在宽度截图矩阵、图内部滚动保持、动效和后续动作之前中断。
18个计划内未运行场景：csrf-required、pause-submit、resume-submit、recover-submit、cancel-warning、
cancel-submit、cancel-irreversible、stale-action-conflict、action-snapshot-reload、width-390、width-768、
width-1280、width-1440、motion-toggle、reduced-motion、refresh-graph-state、refresh-form-focus、keyboard-focus。
这些不计通过。四角色基础权限已执行，但ADMIN最终截图及四宽度矩阵没有完成；其他3角色截图保存。
截图/fixture写入/页面加载本身不计业务覆盖。

## 静态测试契约风险与后续最小方案

只读取了本次既有日志、public-DOM诊断/截图、自己的helpers/scenarios/fixture，没有读产品实现源码。
这些静态发现不修改本次实际结果，也不授予新运行权：

- `rows()`只枚举li/tr/article，合法details事件结构不在候选中；`visibleRows()`只检查自身hidden/display，
  缺少祖先隐藏与关闭details状态的检查。已有章节trace正确选择mission-timeline且记录DETAILS/TIME结构。
  后续可按真实公开事件条目选择最外层details/稳定事件key，并核对所有祖先可见性；必须仍逐条核对正文、
  分类和真实数量，不能以整页文本或过滤控件值替代条目断言。
- `filterCategory()`以宽泛id/aria-label或option文字识别控件。类别测试优先匹配展示label中的run，
  但已有任意非空option.value回退，因此本次“options absent”不能只归因label措辞。
  后续应限定可见的语义Category控件，按实际非空value选择，保存控件identity、value/label全集和前后条目集合。
- attempt夹具静态定义本Run301条attempt以及更新的外Run305条，全部使用真实task归属。
  当前断言只从章节textContent用ID正则计数，失败时不输出实际集合。
  章节textContent可能把相邻DOM文本拼为a301Attempt等连续单词，使word boundary计数漏项。
  后续应按每个公开行/单节点完整ID读取，在同场景保存实际ID列表/数量及期待2–301集合，
  严格比较a2至a301的完整集合并读取真实attempt行/锚点关联，
  继续要求准确300条和归属过滤，不能删除计数断言或把首尾存在当完整集合相等。

以上是未执行的最小修正提案；当前脚本、4个失败及所有历史报告/快照都保持。
具体产品问题与测试定位问题需独立恢复阶段进一步分开，当前证据不足的地方如实未定归因。

## 命令、授权与全部证据

本revision两次均使用同一精确命令，无模式或附加参数：

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

工作目录 `D:/03_projects/AI-tools/aiw-dev`。
执行前核对event9最新/dispatched/session、FD摘要和四脚本SHA256，均与正式v2授权相符。
本次测试JVM新增ActiveProcessorCount=2、TieredStopAtLevel=1、UseSerialGC；heap64/512m、codecache96m、
业务断言/库存/超时保留，授权路径为r9-v2。该测试环境资源修正不能验证生产性能，也不能保证系统commit充分。

| r9次数 | 正式授权 | 实际结果 | 原始目录 |
|---|---|---|---|
| 1 | [r9-v1](FD-002-test-authorization-r9-v1.md) | 3/104通过，ADMIN JVM native内存崩溃，exit1 | tests/fd002/evidence-r9-20261005-075631-29524 |
| 2 | [r9-v2](FD-002-test-authorization-r9-v2.md) | 74通过/4失败/26未运行，ADMIN Node commit失败，exit1 | tests/fd002/evidence-r9-20261005-080308-35924 |

两次批准引用同一人类决定 `approved:conversation:call_vim7bxHX4FWji20Z2YrTI0DF`，initial与related corrected retry均已使用。
当前原始results.json、execution.json、四脚本快照、各角色results/diagnostics/browser/app/chrome/cleanup日志、
fixture.json、tester.db及截图全部在本次原始目录，前4次证据/报告保持，没有覆盖。
前三次revision 6历史为24/104、36/104、36/104，见既有r6-attempt1/2/3 Dual报告，不累加覆盖。
没有追加重试、应用重启、diagnose、语法检查、coverage、下载、Maven或生产数据库写入。
没有修改系统pagefile或停止其他人的进程。

## 保留未覆盖

8项原缺口仍适用并保留分母：no-js、firefox-graph、firefox-refresh、lease-auto-runtime、
lease-pause-revocation、concurrent-posts、timeline-tz-browser、missing-time。
Firefox两项已由 [FD-002独立人类决定](FD-002-firefox-decision-r6.md) 延期，仍不记通过。
其他限制为未启动无脚本/非UTC上下文、未运行真实Worker租约与同时POST、未造缺失可选时间。
纯静态排除为空；104项全部适用。4失败、未运行的关键动作/窄屏/图滚动与环境commit风险仍阻止完整验收。

## 逐场景库存

以下104项与JSON一致：74passed、22blocked（4已执行失败与18未执行）、8uncovered。

| 场景ID | 验收 | 独立可观察行为 | 本次结果 |
|---|---|---|---|
| requirement-original | AC01 | 需求来自 Run 保存的 requirementTaskId，非首个展示任务 | 通过 |
| requirement-long | AC01/AC06 | 完整长需求可阅读且保留末尾文本 | 通过 |
| requirement-escaped | AC01/AC06 | 需求 HTML/script 作为转义文本呈现 | 通过 |
| requirement-window-outside | AC01 | 需求 task 在200窗口外仍读取原始关联指令 | 通过 |
| requirement-cross-run | AC01/AC04 | 需求关联归属异常时 unavailable，不能采用其他Run指令 | 通过 |
| requirement-missing | AC01/AC06 | 需求关联缺失时明确 unavailable | 通过 |
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
| attempt-cap | AC03/AC04 | 301 attempt返回最近300并显示总量/截断 | 通过 |
| attempt-run-filter | AC03/AC04 | 较新的其他Run attempt不会挤占本Run窗口 | 失败：returned attempt IDs differ from newest300 |
| attempt-task-link | AC03 | 历史 attempt关联保存的task ID | 通过 |
| attempt-number | AC03 | 历史 attempt展示实际attemptNo | 通过 |
| attempt-worker | AC03 | 保存历史Worker ID/Fleet链接，无当前租约断言 | 通过 |
| attempt-state | AC03 | FAILED/EXPIRED/COMPLETED历史状态原样可读 | 通过 |
| attempt-times | AC03 | attempt实际创建/开始/结束时间可读 | 通过 |
| attempt-failure-category | AC03 | 保存的failure category可读 | 通过 |
| attempt-reason-paused | AC03 | EXPIRED OPERATOR_PAUSED原因为暂停 | 通过 |
| attempt-reason-cancelled | AC03 | EXPIRED OPERATOR_CANCELLED原因为取消 | 通过 |
| attempt-reason-missing | AC03/AC04 | EXPIRED缺原因显示未记录且不推断自动恢复 | 通过 |
| attempt-summary | AC03 | 报告摘要独立显示，不冒充根因 | 通过 |
| failure-latest-outside | AC03/AC04 | 最新 FAILED即使在timeline窗口外仍独立显示 | 通过 |
| failure-event-time | AC03 | FAILED展示保存的事件时间 | 通过 |
| failure-event-actor | AC03 | FAILED展示保存的actor/task/detail | 通过 |
| recovery-event-time | AC03 | RECOVERED使用事件实际时间和actor | 通过 |
| recovery-event-no-attempt | AC03/AC04 | Run事件无attempt/Worker来源时不补造链接 | 通过 |
| recovery-history-cap | AC03/AC04 | FAILED/RECOVERED最近20条/完整23条/截断可读 | 通过 |
| auto-recovery-explanation | AC03 | 租约自动重新排队机制与人工recover分别说明 | 通过 |
| retry-not-recovery-time | AC03 | 后续attempt创建时间不冒充人工恢复时间 | 通过 |
| timeline-cap | AC04 | 最近300条/总量/截断与公开timeline API一致 | 通过 |
| timeline-chronology | AC04 | 返回窗口保持公开API occurredAt与同时间排序 | 通过 |
| timeline-boundaries | AC04 | 实际首尾时间与公开API窗口一致 | 通过 |
| timeline-utc-groups | AC04 | 跨UTC午夜条目按UTC日期分组 | 通过 |
| timeline-category-filter | AC04 | 类别筛选只隐藏不匹配的返回条目 | 失败：Error: category options absent |
| timeline-text-filter | AC04 | 文本筛选匹配窗口中的实际正文 | 失败：text filter did not limit entries |
| timeline-filter-scope | AC04 | 筛选明确只搜索返回窗口 | 通过 |
| timeline-reset | AC04 | Reset恢复整个返回窗口 | 通过 |
| timeline-full-detail | AC04 | 展开可阅读完整长事件detail | 通过 |
| timeline-detail-key | AC04/AC06 | 事件展开使用稳定key并在刷新后保持 | 通过 |
| timeline-missing-metadata | AC04 | 缺失actor/task/detail明确未记录 | 通过 |
| publication-text-only | AC04 | publication ref作为文本且不请求外部文件 | 通过 |
| no-broken-links | AC04 | 页面所有任务/attempt内链均指向存在目标 | 通过 |
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
| empty-graph | AC06 | 零task/attempt/dependency的缺失关联Run明确空态 | 通过 |
| long-id | AC06 | 完整长Run/task ID在任务表可阅读 | 通过 |
| width-390 | AC06 | 390px窄屏页面不横向溢出且图内部滚动 | 阻塞：未执行 |
| width-768 | AC06 | 768px页面不横向溢出 | 阻塞：未执行 |
| width-1280 | AC06 | 1280px页面不横向溢出 | 阻塞：未执行 |
| width-1440 | AC06 | 1440px页面不横向溢出 | 阻塞：未执行 |
| motion-toggle | AC06 | Motion off在导航后保持并关闭动效 | 阻塞：未执行 |
| reduced-motion | AC06 | 系统减少动效设置关闭装饰动画 | 阻塞：未执行 |
| refresh-auto | AC06 | 5秒周期真实更新只读事实 | 通过 |
| refresh-filter | AC06 | 刷新后类别/文本筛选条件保持并重新应用 | 失败：filter not reapplied |
| refresh-graph-state | AC06 | 刷新后图开关及图内滚动保持 | 阻塞：未执行 |
| refresh-form-focus | AC06 | 编辑动作表单时输入/焦点保持并暂停刷新 | 阻塞：未执行 |
| refresh-selection | AC06 | 阅读选区的节点/文字/offset刷新后保持 | 通过 |
| refresh-scroll | AC06 | 局部刷新保留页面滚动位置 | 通过 |
| refresh-details | AC06 | 任务展开details刷新后保持 | 通过 |
| keyboard-focus | AC06 | Tab键真实焦点及可见outline可用 | 阻塞：未执行 |
| no-js | AC04/AC06 | 禁用JavaScript仍可阅读返回timeline与任务表 | 未覆盖：本命令聚焦Chrome交互；另需无脚本浏览上下文 |
| firefox-graph | AC02/AC06 | Firefox依赖图、表格、窗口和锚点兼容 | 未覆盖：用户已单独批准FD-002 Firefox延期；本命令未运行Firefox，仍保留库存与兼容风险 |
| firefox-refresh | AC06 | Firefox筛选/选区/焦点/动效刷新保持 | 未覆盖：用户已单独批准FD-002 Firefox延期；本命令未运行Firefox，仍保留库存与兼容风险 |
| lease-auto-runtime | AC03 | 真实租约到期requeue后页面区别人工recover | 未覆盖：聚焦只读详情，自动租约恢复在隔离运行中关闭；仅覆盖已有记录表达 |
| lease-pause-revocation | AC05 | pause撤销真实活跃Worker租约后详情保持一致 | 未覆盖：未启动Worker协议进程，控制动作使用无租约Run |
| concurrent-posts | AC05 | 真正同时两个控制POST竞争与原子性反馈 | 未覆盖：本命令覆盖旧页面状态变化的串行竞争；同时写入需要独立时序夹具 |
| timeline-tz-browser | AC04 | 非UTC浏览器时区仍按UTC日期分组 | 未覆盖：本命令检查UTC午夜数据，未再启动其他时区上下文 |
| missing-time | AC03/AC04 | 缺失可选attempt started/finished时间明确未记录 | 未覆盖：当前夹具终态attempt包含时间；需额外缺失可选时间夹具 |
