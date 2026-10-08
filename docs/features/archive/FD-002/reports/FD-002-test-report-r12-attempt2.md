# FD-002 独立测试报告：Revision 12，第2次执行（总第7次）

<!-- aiw-data: FD-002-test-report-r12-attempt2.json -->

## 结论

唯一条件重试实际执行 **96** 个场景断言，**93通过、3失败、8未运行**，命令exit **1**。
需求场景覆盖 **93/104（89.42%）**；计划96/104（92.31%）不能替代真实结果。
建议 **blocked**。3个原始失败均保留，不因控件定位或截图原因改写为通过。
业务代码分支覆盖 **not measured**，没有字节码分支仪器，不能由场景覆盖推算。
本轮不累加初验3项或之前revision的通过。104项全部适用，无纯静态排除。

独立Tester：`fd002-tester-20261005-15d090a2`；源事件 `FD-002-000012-implementation-ready`；
实际FD revision **12**；LF正规化摘要
`8a272107c44241a585978e51becdf1d769f5d17736f122b276fea088bb6178b9`。
本轮initial1与相关条件retry1均已使用，运行预算 **0**。停止运行，未改FD、未emit。

## 三个实际失败

| 场景 | 原始异常 | 可确认范围 |
|---|---|---|
| timeline-category-filter | `Error: visible semantic Category control count 0 []`（CDP exceptionDetails） | 控件观察器在选择非空类别/验证筛选集合之前抛错；类别筛选行为未验证 |
| refresh-filter | 同一`filterCategory`异常 | 刷新前类别观察就抛错；类别与文本条件同时保持/重新应用未验证 |
| width-1440 | `Unable to capture screenshot` | dense-1440第21段与补充失败截图第2段捕获失败；1440矩阵未完成 |

Category失败的现有截图 `ADMIN/failed-timeline-category-filter-part-002.png` 清楚显示
Timeline中的Category下拉（All categories）、Reset filters和7/7 returned entries match；
后续part003显示真实事件正文可见。因此零候选异常不能说明页面没有可见类别控件。
观察器只保存已通过语义label/visibility筛选的候选，没保存被排除控件的id/label/options，
现有证据不足以确定其具体DOM关联原因，也没有实际选择某个非空类别后的条目集合证据。
这是观察风险与未完成的验证；两项历史结果仍为失败，不补写passed。

width-1440场景内的页面宽度断言先执行，随后同一case截取dense/long-ID矩阵；
本次记录的错误是Page.captureScreenshot失败，没有记录该宽度的页面溢出断言失败。
保留原case失败，不能据前置断言通过声称截图矩阵或整个场景完成。
ADMIN/chrome.log在对应捕获时记录：

```text
Invalid message: VALIDATION_ERROR_DESERIALIZATION_FAILED
Message 1508190960 rejected by interface viz.mojom.CopyOutputResultSender
GPU process exited unexpectedly: exit_code=3
```

这支持截图基础设施阻断，但不能仅据现有日志确定唯一根因。
Chrome updater IPC拒绝日志没有触发权限探测或提权。
原始根summary仅记ADMIN browser exit1；已有diagnostics无CDP基础设施异常、external_requests或runtime_errors。
没有原始summary中的清理、数据库保存或live-owned-PID异常。

## 已验证范围与未覆盖

本次已完整到达96个计划case，包括真实DAG/并行与integration/CODE_REVIEW/verification/repair/cycle、
201-task与1201边窗口、301-attempt完整300 ID集合、原始需求关联/长文本/无数据、
FAILED/RECOVERED真实time/actor与attempt状态/原因、timeline窗口/排序/UTC/正文/Reset/详情、
四角色权限、CSRF、pause/resume/recover/cancel及不可逆/旧状态反馈、
390/768/1280布局矩阵、motion、图内部滚动/展开/选区/表单焦点等刷新保持。
通过只依据本次逐条业务断言；图片存在、fixture写入和页面加载不计通过。
上述广义范围内的3个失败仍按逐case结果扣除，不把宽泛AC计为单个测试。

原8缺口保持未覆盖并保留分母：no-js、firefox-graph、firefox-refresh、lease-auto-runtime、
lease-pause-revocation、concurrent-posts、timeline-tz-browser、missing-time。
Firefox两项已由 [本FD独立人类决定](FD-002-firefox-decision-r6.md) 延期，仍未运行、不记通过。
没有无脚本/非UTC浏览器上下文、真实Worker租约撤销/自动恢复、同时POST或缺少可选时间夹具的追加运行。

## 截图与manifest完整性

原始目录：`tests/fd002/evidence-r12-20261005-084058-32068`。
按各角色diagnostics的逐段manifest共 **201段saved**；2个capture失败留下requested状态和错误。
每段同公开layout宽度、最多2000 CSS px高和4000000实际PNG像素。
390/768/1280的dense与long-ID全纵向截图完整；四角色及final-admin截图完整；
两个Category失败截图各4段完整。dense-1440仅保存前20段（总高92670 CSS px），
第21段失败；其补充失败截图仅首段保存，第2段失败，long-id-1440没有到达。
因此完整四宽度截图矩阵 **未完成**，不能仅以manifest已记录或已有首段作为完成。
逐组状态在下表，文件名/clip/实际像素尺寸完整保留于各角色diagnostics.json。

## 命令、授权及历史

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

cwd：`D:/03_projects/AI-tools/aiw-dev`，本次无附加参数或模式。
执行前必要只读核对正式v2批准、四脚本SHA、最新receipt12/session与FD摘要，全部匹配。
本次实际runtime只新增一处Chrome `--disable-gpu`，browser复用唯一初始blank page，记录CDP operation标签；
原5秒target查找、12秒CDP、230/75秒角色超时、业务断言、JVM/V8限制和截图矩阵保持。
这些环境调整不验证生产GPU或性能，也不保证系统commit充足。

| r12执行 | 正式MD授权入口 | 真实结果 | 独立证据目录 |
|---|---|---|---|
| 初验 | [r12-v1](FD-002-test-authorization-r12-v1.md) | 3通过/0失败/101未运行，2.88%，exit1 | evidence-r12-20261005-083112-13176 |
| 唯一条件重试 | [r12-v2](FD-002-test-authorization-r12-v2.md) | 93通过/3失败/8未运行，89.42%，exit1 | evidence-r12-20261005-084058-32068 |

两次均引用 `approved:conversation:call_91WOuZ4AVFLiYni2IuZLDKaE`，没有新增grant或自动重跑。
原始results/execution、v2四脚本快照、四角色日志/fixture/数据库及逐段截图/diagnostics均保留。
前六次证据、脚本快照和报告保持；r9原4个失败不会因本轮不同结果被改写。
本轮新增结果中attempt-run-filter与timeline-text-filter实际通过，Category两项仍阻断，
不能把r9四失败简单全部归为修复完成。
没有运行额外诊断、语法检查、coverage、下载、Maven或生产数据库写入；仅自有PID和安全TEMP路径清理。
仍未读取生产实现源码。运行预算耗尽，任何进一步运行需新授权。

## 截图组明细

| 角色/截图组 | 页面高度 CSS px | 已保存/已请求段 | manifest状态 |
|---|---:|---:|---|
| ADMIN/failed-timeline-category-filter | 6806 | 4/4 | 完整 |
| ADMIN/failed-refresh-filter | 6775 | 4/4 | 完整 |
| ADMIN/dense-390 | 94912 | 48/48 | 完整 |
| ADMIN/long-id-390 | 11766 | 6/6 | 完整 |
| ADMIN/dense-768 | 96153 | 49/49 | 完整 |
| ADMIN/long-id-768 | 10876 | 6/6 | 完整 |
| ADMIN/dense-1280 | 92703 | 47/47 | 完整 |
| ADMIN/long-id-1280 | 8073 | 5/5 | 完整 |
| ADMIN/dense-1440 | 92670 | 20/21 | 不完整：Unable to capture screenshot |
| ADMIN/failed-width-1440 | 92670 | 1/2 | 不完整：Unable to capture screenshot |
| ADMIN/final-admin | 3516 | 2/2 | 完整 |
| ADMIN/role-ADMIN | 3516 | 2/2 | 完整 |
| VIEWER/role-VIEWER | 3490 | 2/2 | 完整 |
| OPERATOR/role-OPERATOR | 4343 | 3/3 | 完整 |
| APPROVER/role-APPROVER | 3670 | 2/2 | 完整 |

## 逐场景库存

| 场景ID | 验收 | 独立可观察行为 | 本轮结果 |
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
| attempt-run-filter | AC03/AC04 | 较新的其他Run attempt不会挤占本Run窗口 | 通过 |
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
| timeline-category-filter | AC04 | 类别筛选只隐藏不匹配的返回条目 | 失败：原始失败：visible semantic Category control count 0 []。既有截图显示控件可见；观察器在选择非空类别及条目集合/刷新断言前阻断，具体DOM关联原因未定，不记通过或产品控件缺失。 |
| timeline-text-filter | AC04 | 文本筛选匹配窗口中的实际正文 | 通过 |
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
| csrf-required | AC05 | 已登录Admin无CSRF写入被拒绝且状态不变 | 通过 |
| pause-submit | AC05 | 合法pause产生反馈与PAUSED事实 | 通过 |
| resume-submit | AC05 | 合法resume产生反馈与ACTIVE事实 | 通过 |
| recover-submit | AC05 | 合法recover产生反馈与RUNNING事实 | 通过 |
| cancel-warning | AC05 | Cancel存在不可逆/确认提示 | 通过 |
| cancel-submit | AC05 | 合法cancel产生CANCELLED事实 | 通过 |
| cancel-irreversible | AC05 | 已cancelled Run无法resume | 通过 |
| stale-action-conflict | AC05 | 旧页面pause在状态已改变时显示竞争错误 | 通过 |
| action-snapshot-reload | AC05 | 动作快照说明及Reload action availability入口可读 | 通过 |
| empty-graph | AC06 | 零task/attempt/dependency的缺失关联Run明确空态 | 通过 |
| long-id | AC06 | 完整长Run/task ID在任务表可阅读 | 通过 |
| width-390 | AC06 | 390px窄屏页面不横向溢出且图内部滚动 | 通过 |
| width-768 | AC06 | 768px页面不横向溢出 | 通过 |
| width-1280 | AC06 | 1280px页面不横向溢出 | 通过 |
| width-1440 | AC06 | 1440px页面不横向溢出 | 失败：原始失败：Unable to capture screenshot。布局断言先完成，dense截图第21段与补充失败截图第2段捕获失败，long-id-1440未到达；整个case和矩阵未完成。 |
| motion-toggle | AC06 | Motion off在导航后保持并关闭动效 | 通过 |
| reduced-motion | AC06 | 系统减少动效设置关闭装饰动画 | 通过 |
| refresh-auto | AC06 | 5秒周期真实更新只读事实 | 通过 |
| refresh-filter | AC06 | 刷新后类别/文本筛选条件保持并重新应用 | 失败：原始失败：visible semantic Category control count 0 []。既有截图显示控件可见；观察器在选择非空类别及条目集合/刷新断言前阻断，具体DOM关联原因未定，不记通过或产品控件缺失。 |
| refresh-graph-state | AC06 | 刷新后图开关及图内滚动保持 | 通过 |
| refresh-form-focus | AC06 | 编辑动作表单时输入/焦点保持并暂停刷新 | 通过 |
| refresh-selection | AC06 | 阅读选区的节点/文字/offset刷新后保持 | 通过 |
| refresh-scroll | AC06 | 局部刷新保留页面滚动位置 | 通过 |
| refresh-details | AC06 | 任务展开details刷新后保持 | 通过 |
| keyboard-focus | AC06 | Tab键真实焦点及可见outline可用 | 通过 |
| no-js | AC04/AC06 | 禁用JavaScript仍可阅读返回timeline与任务表 | 未覆盖：本命令聚焦Chrome交互；另需无脚本浏览上下文 |
| firefox-graph | AC02/AC06 | Firefox依赖图、表格、窗口和锚点兼容 | 未覆盖：用户已单独批准FD-002 Firefox延期；本命令未运行Firefox，仍保留库存与兼容风险 |
| firefox-refresh | AC06 | Firefox筛选/选区/焦点/动效刷新保持 | 未覆盖：用户已单独批准FD-002 Firefox延期；本命令未运行Firefox，仍保留库存与兼容风险 |
| lease-auto-runtime | AC03 | 真实租约到期requeue后页面区别人工recover | 未覆盖：聚焦只读详情，自动租约恢复在隔离运行中关闭；仅覆盖已有记录表达 |
| lease-pause-revocation | AC05 | pause撤销真实活跃Worker租约后详情保持一致 | 未覆盖：未启动Worker协议进程，控制动作使用无租约Run |
| concurrent-posts | AC05 | 真正同时两个控制POST竞争与原子性反馈 | 未覆盖：本命令覆盖旧页面状态变化的串行竞争；同时写入需要独立时序夹具 |
| timeline-tz-browser | AC04 | 非UTC浏览器时区仍按UTC日期分组 | 未覆盖：本命令检查UTC午夜数据，未再启动其他时区上下文 |
| missing-time | AC03/AC04 | 缺失可选attempt started/finished时间明确未记录 | 未覆盖：当前夹具终态attempt包含时间；需额外缺失可选时间夹具 |
