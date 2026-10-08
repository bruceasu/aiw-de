# FD-002 独立测试报告：Revision 12，第1次执行（总第6次）

<!-- aiw-data: FD-002-test-report-r12-attempt1.json -->

## 结论

本轮指定命令实际执行 **3** 个业务断言，**3通过、0失败、101未运行**，命令exit **1**。
需求场景覆盖 **3/104（2.88%）**；其中93个计划内断言未到达，8项原未覆盖仍保留分母。
计划96/104（92.31%）未完成，建议 **blocked**。零业务断言失败不代表未执行的产品行为通过。
分支覆盖 **not measured**，没有字节码分支仪器，不能推算。
前五次运行及revision9的4个失败保持，不累加历史通过，也不据本轮未执行认定旧失败已修复。

Tester：`fd002-tester-20261005-15d090a2`；implementation事件 `FD-002-000012-implementation-ready`；
实际FD revision **12**；LF正规化摘要
`8a272107c44241a585978e51becdf1d769f5d17736f122b276fea088bb6178b9`。
initial1已使用，剩余相关修正后的条件retry1需PM审阅及明确通知；未自动复跑、未emit、未改FD。

## 实际断言及阻断证据

| 已执行场景 | 实际公开页面/API断言结果 |
|---|---|
| VIEWER-permission | 控制入口隐藏，带CSRF的pause POST返回403，Run保持ACTIVE |
| OPERATOR-permission | 控制入口可见，pause返回200，公开Run API为PAUSED |
| APPROVER-permission | 控制入口隐藏，带CSRF的pause POST返回403，Run保持ACTIVE |

ADMIN/browser.log确切异常为：

```text
INFRASTRUCTURE Error: CDP timeout Runtime.evaluate
    at Timeout._onTimeout (.../tests/fd002/browser.mjs:29:127)
```

ADMIN/diagnostics.json只有 `/ui/login` 导航和该基础设施异常，没有章节关联或ADMIN业务断言。
ADMIN/app.log显示16个迁移成功、应用Started及DispatcherServlet初始化完成；现有证据目录没有
hs_err/replay文件，browser.log没有Node native fatal文本。ADMIN/chrome.log记录：

```text
[37660:32868:1005/083132.322:ERROR:content\browser\gpu\gpu_process_host.cc:1130] GPU process exited unexpectedly: exit_code=34
```

这些证据支持测试环境/CDP阻断，不能确定GPU退出与evaluate超时的因果顺序，也不能认定产品登录错误。
本轮导航诊断缺少确切evaluate操作阶段，因此不把“登录提交”或“等待跳转”作为已确认根因。
raw summary只有ADMIN browser exit1，没有清理、数据库保存或live-owned-PID异常。

## 截图与原始证据

原始目录：`tests/fd002/evidence-r12-20261005-083112-13176`。
其中保留execution.json、根results.json、四脚本快照、四角色fixture.json/tester.db、
app/chrome/browser/cleanup日志及各角色results/diagnostics。脚本快照反映真正运行的v1；
后续静态v2修改没有覆盖它们。

| 本轮截图 | 公开页面高度 | 保存分段 | 实际状态 |
|---|---:|---:|---|
| VIEWER/role-VIEWER | 3490 CSS px | 2 | 完整 |
| OPERATOR/role-OPERATOR | 4343 CSS px | 3 | 完整 |
| APPROVER/role-APPROVER | 3670 CSS px | 2 | 完整 |
| ADMIN及dense/long-ID四宽度矩阵 | 未到达 | 0 | 未运行 |

这7段图片及逐段manifest均按本轮公开宽度743、每段≤2000 CSS px和≤4000000 PNG像素保存；
图片存在不计业务覆盖。ADMIN无业务断言失败，因此没有failed-case截图；其基础设施阻断时也未生成截图。
四宽度、图内部滚动、焦点、选区、详细展开、筛选、后续控制动作及图/时间线主体均未到达。

## 实际命令、授权与静态后续

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

cwd：`D:/03_projects/AI-tools/aiw-dev`；本revision仅执行上述命令一次，无附加参数。
正式授权入口：[FD-002-test-authorization-r12-v1.md](FD-002-test-authorization-r12-v1.md)，
人类依据 `approved:conversation:call_91WOuZ4AVFLiYni2IuZLDKaE`。
执行前必要只读核对receipt12 dispatched/session、当前FD LF摘要及四脚本SHA，全部与授权匹配。
命令先保存独立快照，四角色按原计划依次启动，原JVM/V8限制和所有阶段超时保持。
没有追加诊断、浏览器、语法检查、coverage、下载、Maven、生产DB写入或其他人的进程清理。

宿主仅允许针对现有GPU退出证据静态准备Chrome `--disable-gpu`、复用唯一初始blank页与确切CDP operation诊断；
runtime将指向未来r12-v2授权入口。该修正未运行，不改变本报告结果。
具体待审同命令提案见 [FD-002-test-proposal-r12-v2.md](FD-002-test-proposal-r12-v2.md)。
登录方式、等待算法、12000ms CDP/230及75秒角色超时、断言与矩阵不放宽。
测试GPU禁用不验证生产GPU或性能，系统commit不足和环境阻断残余风险仍在。

## 历史与未覆盖

| 历史运行 | 保留实际结果 | 独立原始目录 |
|---|---|---|
| r6第1次 | 24/104，exit1 | evidence-r6-20261005-072411-32980 |
| r6第2次 | 36/104，exit1 | evidence-r6-20261005-072852-34372 |
| r6第3次 | 36/104，exit1 | evidence-r6-20261005-073707-7320 |
| r9第1次 | 3/104，exit1 | evidence-r9-20261005-075631-29524 |
| r9第2次 | 74通过/4失败/26未运行，exit1 | evidence-r9-20261005-080308-35924 |

这些目录均位于tests/fd002，所有原始报告/快照保持。r9四失败仍为attempt-run-filter、
timeline-text-filter、timeline-category-filter、refresh-filter；本轮未到达这些断言。
8原缺口：no-js、firefox-graph、firefox-refresh、lease-auto-runtime、lease-pause-revocation、
concurrent-posts、timeline-tz-browser、missing-time。
Firefox两项获 [本FD独立人类延期](FD-002-firefox-decision-r6.md)，仍保留104分母、不记通过。
纯静态排除为空；104项全部适用。完整逐项库存与机器数据一致，3passed、93blocked、8uncovered。

## 逐场景库存
| 场景ID | 验收 | 独立可观察行为 | 本轮结果 |
|---|---|---|---|
| requirement-original | AC01 | 需求来自 Run 保存的 requirementTaskId，非首个展示任务 | 阻断：未执行 |
| requirement-long | AC01/AC06 | 完整长需求可阅读且保留末尾文本 | 阻断：未执行 |
| requirement-escaped | AC01/AC06 | 需求 HTML/script 作为转义文本呈现 | 阻断：未执行 |
| requirement-window-outside | AC01 | 需求 task 在200窗口外仍读取原始关联指令 | 阻断：未执行 |
| requirement-cross-run | AC01/AC04 | 需求关联归属异常时 unavailable，不能采用其他Run指令 | 阻断：未执行 |
| requirement-missing | AC01/AC06 | 需求关联缺失时明确 unavailable | 阻断：未执行 |
| status-lifecycle | AC01 | 生命周期与公开 Run API 保存状态一致 | 阻断：未执行 |
| status-control | AC01 | 控制状态与公开 Run API 分别可阅读 | 阻断：未执行 |
| status-effective | AC01 | 有效状态与公开 Run API 分别可阅读 | 阻断：未执行 |
| dashboard-phase | AC01 | 当前阶段与公开 Dashboard 事实一致 | 阻断：未执行 |
| task-counts | AC01 | 完整 Run task计数与 Dashboard一致 | 阻断：未执行 |
| no-eta | AC01 | 任务完成数不生成 ETA 或工程完成百分比 | 阻断：未执行 |
| single-edges | AC02 | 单实施图边与保存的依赖完全一致 | 阻断：未执行 |
| single-direction | AC02 | 单实施 SVG 从前置任务指向 dependent | 阻断：未执行 |
| parallel-edges | AC02 | 并行分支和 integration 真实边完全一致 | 阻断：未执行 |
| parallel-levels | AC02 | 同层并行 task，integration 位于分支后方 | 阻断：未执行 |
| final-review-level | AC02 | 最终 CODE_REVIEW 位于 integration 后方 | 阻断：未执行 |
| verification-level | AC02 | INTEGRATION_VERIFICATION 位于 integration 后方 | 阻断：未执行 |
| no-inferred-edge | AC02 | 同Run taskType/列表顺序不会生成未保存的边 | 阻断：未执行 |
| repair-added | AC02 | 新增 repair 在局部刷新后显示真实 task 和边 | 阻断：未执行 |
| repair-history | AC02 | repair 新增后原任务与原依赖仍保留 | 阻断：未执行 |
| cycle-warning | AC02/AC06 | 循环依赖明确异常而不标完整 DAG | 阻断：未执行 |
| cycle-table | AC02 | 循环的两条保存边仍可从文本/表格阅读 | 阻断：未执行 |
| tasks-cap | AC02/AC04 | 201 task返回最近200并显示完整总量 | 阻断：未执行 |
| tasks-order | AC02/AC04 | 最近任务窗口按真实createdAt/id正序稳定展示 | 阻断：未执行 |
| outside-prerequisite | AC02/AC04 | 窗口外前置依赖保留真实ID和范围说明 | 阻断：未执行 |
| outside-no-anchor | AC02/AC04 | 窗口外 task 不生成指向不存在目标的锚点 | 阻断：未执行 |
| cross-run-edge | AC02/AC04 | 跨Run前置依赖真实ID与范围说明可读 | 阻断：未执行 |
| cross-subject-edge | AC02/AC04 | 跨subject前置依赖真实ID与范围说明可读 | 阻断：未执行 |
| edges-cap | AC02/AC04 | 1201候选边最多返回1200并明确截断 | 阻断：未执行 |
| edges-total | AC02/AC04 | 完整Run边总数与展示task候选边数分别呈现 | 阻断：未执行 |
| partial-graph | AC02/AC04 | 边截断/窗口外关系明确部分图 | 阻断：未执行 |
| graph-hide | AC02/AC06 | Hide graph后完整任务表仍可阅读 | 阻断：未执行 |
| graph-node-link | AC02 | SVG节点定位到对应真实task指令 | 阻断：未执行 |
| attempt-cap | AC03/AC04 | 301 attempt返回最近300并显示总量/截断 | 阻断：未执行 |
| attempt-run-filter | AC03/AC04 | 较新的其他Run attempt不会挤占本Run窗口 | 阻断：未执行 |
| attempt-task-link | AC03 | 历史 attempt关联保存的task ID | 阻断：未执行 |
| attempt-number | AC03 | 历史 attempt展示实际attemptNo | 阻断：未执行 |
| attempt-worker | AC03 | 保存历史Worker ID/Fleet链接，无当前租约断言 | 阻断：未执行 |
| attempt-state | AC03 | FAILED/EXPIRED/COMPLETED历史状态原样可读 | 阻断：未执行 |
| attempt-times | AC03 | attempt实际创建/开始/结束时间可读 | 阻断：未执行 |
| attempt-failure-category | AC03 | 保存的failure category可读 | 阻断：未执行 |
| attempt-reason-paused | AC03 | EXPIRED OPERATOR_PAUSED原因为暂停 | 阻断：未执行 |
| attempt-reason-cancelled | AC03 | EXPIRED OPERATOR_CANCELLED原因为取消 | 阻断：未执行 |
| attempt-reason-missing | AC03/AC04 | EXPIRED缺原因显示未记录且不推断自动恢复 | 阻断：未执行 |
| attempt-summary | AC03 | 报告摘要独立显示，不冒充根因 | 阻断：未执行 |
| failure-latest-outside | AC03/AC04 | 最新 FAILED即使在timeline窗口外仍独立显示 | 阻断：未执行 |
| failure-event-time | AC03 | FAILED展示保存的事件时间 | 阻断：未执行 |
| failure-event-actor | AC03 | FAILED展示保存的actor/task/detail | 阻断：未执行 |
| recovery-event-time | AC03 | RECOVERED使用事件实际时间和actor | 阻断：未执行 |
| recovery-event-no-attempt | AC03/AC04 | Run事件无attempt/Worker来源时不补造链接 | 阻断：未执行 |
| recovery-history-cap | AC03/AC04 | FAILED/RECOVERED最近20条/完整23条/截断可读 | 阻断：未执行 |
| auto-recovery-explanation | AC03 | 租约自动重新排队机制与人工recover分别说明 | 阻断：未执行 |
| retry-not-recovery-time | AC03 | 后续attempt创建时间不冒充人工恢复时间 | 阻断：未执行 |
| timeline-cap | AC04 | 最近300条/总量/截断与公开timeline API一致 | 阻断：未执行 |
| timeline-chronology | AC04 | 返回窗口保持公开API occurredAt与同时间排序 | 阻断：未执行 |
| timeline-boundaries | AC04 | 实际首尾时间与公开API窗口一致 | 阻断：未执行 |
| timeline-utc-groups | AC04 | 跨UTC午夜条目按UTC日期分组 | 阻断：未执行 |
| timeline-category-filter | AC04 | 类别筛选只隐藏不匹配的返回条目 | 阻断：未执行 |
| timeline-text-filter | AC04 | 文本筛选匹配窗口中的实际正文 | 阻断：未执行 |
| timeline-filter-scope | AC04 | 筛选明确只搜索返回窗口 | 阻断：未执行 |
| timeline-reset | AC04 | Reset恢复整个返回窗口 | 阻断：未执行 |
| timeline-full-detail | AC04 | 展开可阅读完整长事件detail | 阻断：未执行 |
| timeline-detail-key | AC04/AC06 | 事件展开使用稳定key并在刷新后保持 | 阻断：未执行 |
| timeline-missing-metadata | AC04 | 缺失actor/task/detail明确未记录 | 阻断：未执行 |
| publication-text-only | AC04 | publication ref作为文本且不请求外部文件 | 阻断：未执行 |
| no-broken-links | AC04 | 页面所有任务/attempt内链均指向存在目标 | 阻断：未执行 |
| ADMIN-permission | AC05 | Admin可见既有Run控制且拥有POST权限 | 阻断：未执行 |
| VIEWER-permission | AC05 | Viewer隐藏Run控制并拒绝有CSRF的POST | 通过 |
| OPERATOR-permission | AC05 | Operator可见Run控制且拥有POST权限 | 通过 |
| APPROVER-permission | AC05 | Approver隐藏Run控制并拒绝有CSRF的POST | 通过 |
| csrf-required | AC05 | 已登录Admin无CSRF写入被拒绝且状态不变 | 阻断：未执行 |
| pause-submit | AC05 | 合法pause产生反馈与PAUSED事实 | 阻断：未执行 |
| resume-submit | AC05 | 合法resume产生反馈与ACTIVE事实 | 阻断：未执行 |
| recover-submit | AC05 | 合法recover产生反馈与RUNNING事实 | 阻断：未执行 |
| cancel-warning | AC05 | Cancel存在不可逆/确认提示 | 阻断：未执行 |
| cancel-submit | AC05 | 合法cancel产生CANCELLED事实 | 阻断：未执行 |
| cancel-irreversible | AC05 | 已cancelled Run无法resume | 阻断：未执行 |
| stale-action-conflict | AC05 | 旧页面pause在状态已改变时显示竞争错误 | 阻断：未执行 |
| action-snapshot-reload | AC05 | 动作快照说明及Reload action availability入口可读 | 阻断：未执行 |
| empty-graph | AC06 | 零task/attempt/dependency的缺失关联Run明确空态 | 阻断：未执行 |
| long-id | AC06 | 完整长Run/task ID在任务表可阅读 | 阻断：未执行 |
| width-390 | AC06 | 390px窄屏页面不横向溢出且图内部滚动 | 阻断：未执行 |
| width-768 | AC06 | 768px页面不横向溢出 | 阻断：未执行 |
| width-1280 | AC06 | 1280px页面不横向溢出 | 阻断：未执行 |
| width-1440 | AC06 | 1440px页面不横向溢出 | 阻断：未执行 |
| motion-toggle | AC06 | Motion off在导航后保持并关闭动效 | 阻断：未执行 |
| reduced-motion | AC06 | 系统减少动效设置关闭装饰动画 | 阻断：未执行 |
| refresh-auto | AC06 | 5秒周期真实更新只读事实 | 阻断：未执行 |
| refresh-filter | AC06 | 刷新后类别/文本筛选条件保持并重新应用 | 阻断：未执行 |
| refresh-graph-state | AC06 | 刷新后图开关及图内滚动保持 | 阻断：未执行 |
| refresh-form-focus | AC06 | 编辑动作表单时输入/焦点保持并暂停刷新 | 阻断：未执行 |
| refresh-selection | AC06 | 阅读选区的节点/文字/offset刷新后保持 | 阻断：未执行 |
| refresh-scroll | AC06 | 局部刷新保留页面滚动位置 | 阻断：未执行 |
| refresh-details | AC06 | 任务展开details刷新后保持 | 阻断：未执行 |
| keyboard-focus | AC06 | Tab键真实焦点及可见outline可用 | 阻断：未执行 |
| no-js | AC04/AC06 | 禁用JavaScript仍可阅读返回timeline与任务表 | 未覆盖：本命令聚焦Chrome交互；另需无脚本浏览上下文 |
| firefox-graph | AC02/AC06 | Firefox依赖图、表格、窗口和锚点兼容 | 未覆盖：用户已单独批准FD-002 Firefox延期；本命令未运行Firefox，仍保留库存与兼容风险 |
| firefox-refresh | AC06 | Firefox筛选/选区/焦点/动效刷新保持 | 未覆盖：用户已单独批准FD-002 Firefox延期；本命令未运行Firefox，仍保留库存与兼容风险 |
| lease-auto-runtime | AC03 | 真实租约到期requeue后页面区别人工recover | 未覆盖：聚焦只读详情，自动租约恢复在隔离运行中关闭；仅覆盖已有记录表达 |
| lease-pause-revocation | AC05 | pause撤销真实活跃Worker租约后详情保持一致 | 未覆盖：未启动Worker协议进程，控制动作使用无租约Run |
| concurrent-posts | AC05 | 真正同时两个控制POST竞争与原子性反馈 | 未覆盖：本命令覆盖旧页面状态变化的串行竞争；同时写入需要独立时序夹具 |
| timeline-tz-browser | AC04 | 非UTC浏览器时区仍按UTC日期分组 | 未覆盖：本命令检查UTC午夜数据，未再启动其他时区上下文 |
| missing-time | AC03/AC04 | 缺失可选attempt started/finished时间明确未记录 | 未覆盖：当前夹具终态attempt包含时间；需额外缺失可选时间夹具 |
