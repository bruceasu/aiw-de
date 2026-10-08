# FD-002 独立测试报告：Revision 15，用户停止后未执行

<!-- aiw-data: FD-002-test-report-r15-not-run.json -->

## 结论

用户因FD-002测试耗时过长明确指示本轮不再测试。本revision实际为
**0执行、0通过、0失败、104未运行**，需求场景覆盖 **0.00%**；业务代码分支覆盖 **not measured**。
建议 **blocked**。零失败来自没有运行，不能表示产品或静态修正已通过。
104项全部适用，96项计划内因用户拒绝未执行，8项原未覆盖仍保留分母；无纯静态排除。

独立Tester：`fd002-tester-20261005-15d090a2`，已claim的真实implementation事件
`FD-002-000015-implementation-ready`，FD revision **15**，LF正规化摘要
`c4fc25a59e6f03a5521ba6e10cc3b5bcfe17eb5cbf01dbff119079fc2e6f60ae`。
该摘要是报告绑定的FD版本，不是本轮运行证据。没有手改FD正文或生成虚假receipt。

## 用户决定与实际命令

停止决定引用：`denied:conversation:user-stop-fd002-tests-2026-10-05`。
正式授权入口由Planner维护：[FD-002-test-authorization-r15-v1.md](FD-002-test-authorization-r15-v1.md)，
decision为denied、basis为human-declined、initial/retry预算均0。
Tester未编辑该授权文件或Planner决定。

本轮以下静态保留命令 **未执行**：

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

计划cwd `D:/03_projects/AI-tools/aiw-dev`。没有测试、浏览器、诊断、语法/类型检查、构建、coverage、
下载或补充运行，也没有新r15应用进程、SQLite、Chrome profile、截图、manifest或results运行目录。
本阶段仅进行自有文件静态读写、自审和获准的Git/native工作流；这些不计业务断言或覆盖。
JSON的commands为空，raw_coverage_evidence为not measured。

## 静态准备及未验证范围

自有测试脚本与 [r15命令提案](FD-002-test-proposal-r15-v1.md) 静态保留，尚未运行：

- label读取在克隆中剔除嵌套表单控件，保存所有公开select的原始label/可读文本、identity、visibility、options和排除原因。
  仍要求唯一语义Category、非空value、条目缩小与刷新集合，未证明本轮类别筛选可用。
- 截图改为滚动后的实际viewport、captureBeyondViewport=false、不传clip、逐段完整纵向coverage及finally恢复scroll。
  四宽度、dense/long-ID、角色/失败矩阵仍在计划中，没有本轮完整性或资源稳定性证据。
- runtime绑定真实r15、独立session与r15授权入口。ADMIN总上限230→420秒仅是未批准提案的一部分；
  其他75秒、CDP12秒、lookup5秒保持。没有使用新总时限运行，也没有复用旧grant。

不读取生产实现，不把静态算法、页面结构推断、脚本存在或历史截图计为本轮通过。

## 历史结果与残余风险

[r12第2次真实报告](FD-002-test-report-r12-attempt2.md) 记录
**96执行、93通过、3失败、8未运行**，需求覆盖 **93/104（89.42%）**，exit1；
原始目录 `tests/fd002/evidence-r12-20261005-084058-32068`。
这些仅属历史revision12，不能累加为当前revision15的覆盖或通过。
原七次证据、脚本快照、报告和历史失败保持。

历史3失败仍为timeline-category-filter、refresh-filter、width-1440。
前两项在控件观察抛错，非空类别与筛选/刷新集合未到达；既有截图有可见Category，不能写成产品控件缺失。
后者布局断言先完成，但dense-1440/补充失败截图捕获中断，long-id-1440未到达，完整矩阵未完成。
本轮未复验，不能断言这些失败已修复。Chrome/GPU、系统commit压力、截图成本和生产性能风险仍保留。

原8缺口：no-js、firefox-graph、firefox-refresh、lease-auto-runtime、lease-pause-revocation、
concurrent-posts、timeline-tz-browser、missing-time。
Firefox两项获 [本FD独立人类延期](FD-002-firefox-decision-r6.md)，仍未运行、不记通过、保留104分母。
无脚本/非UTC上下文、真实Worker租约撤销与自动恢复、同时POST及缺少可选时间夹具仍无验证。
所有关键产品行为在本轮均未验证；由PM记录当前缺口与后续决定。

## 逐场景库存

机器数据与下表均保持104项：0passed、96blocked（用户拒绝运行）、8uncovered（原缺口）；104未运行。

| 场景ID | 验收 | 独立可观察行为 | 本revision结果 |
|---|---|---|---|
| requirement-original | AC01 | 需求来自 Run 保存的 requirementTaskId，非首个展示任务 | 未执行：用户拒绝运行 |
| requirement-long | AC01/AC06 | 完整长需求可阅读且保留末尾文本 | 未执行：用户拒绝运行 |
| requirement-escaped | AC01/AC06 | 需求 HTML/script 作为转义文本呈现 | 未执行：用户拒绝运行 |
| requirement-window-outside | AC01 | 需求 task 在200窗口外仍读取原始关联指令 | 未执行：用户拒绝运行 |
| requirement-cross-run | AC01/AC04 | 需求关联归属异常时 unavailable，不能采用其他Run指令 | 未执行：用户拒绝运行 |
| requirement-missing | AC01/AC06 | 需求关联缺失时明确 unavailable | 未执行：用户拒绝运行 |
| status-lifecycle | AC01 | 生命周期与公开 Run API 保存状态一致 | 未执行：用户拒绝运行 |
| status-control | AC01 | 控制状态与公开 Run API 分别可阅读 | 未执行：用户拒绝运行 |
| status-effective | AC01 | 有效状态与公开 Run API 分别可阅读 | 未执行：用户拒绝运行 |
| dashboard-phase | AC01 | 当前阶段与公开 Dashboard 事实一致 | 未执行：用户拒绝运行 |
| task-counts | AC01 | 完整 Run task计数与 Dashboard一致 | 未执行：用户拒绝运行 |
| no-eta | AC01 | 任务完成数不生成 ETA 或工程完成百分比 | 未执行：用户拒绝运行 |
| single-edges | AC02 | 单实施图边与保存的依赖完全一致 | 未执行：用户拒绝运行 |
| single-direction | AC02 | 单实施 SVG 从前置任务指向 dependent | 未执行：用户拒绝运行 |
| parallel-edges | AC02 | 并行分支和 integration 真实边完全一致 | 未执行：用户拒绝运行 |
| parallel-levels | AC02 | 同层并行 task，integration 位于分支后方 | 未执行：用户拒绝运行 |
| final-review-level | AC02 | 最终 CODE_REVIEW 位于 integration 后方 | 未执行：用户拒绝运行 |
| verification-level | AC02 | INTEGRATION_VERIFICATION 位于 integration 后方 | 未执行：用户拒绝运行 |
| no-inferred-edge | AC02 | 同Run taskType/列表顺序不会生成未保存的边 | 未执行：用户拒绝运行 |
| repair-added | AC02 | 新增 repair 在局部刷新后显示真实 task 和边 | 未执行：用户拒绝运行 |
| repair-history | AC02 | repair 新增后原任务与原依赖仍保留 | 未执行：用户拒绝运行 |
| cycle-warning | AC02/AC06 | 循环依赖明确异常而不标完整 DAG | 未执行：用户拒绝运行 |
| cycle-table | AC02 | 循环的两条保存边仍可从文本/表格阅读 | 未执行：用户拒绝运行 |
| tasks-cap | AC02/AC04 | 201 task返回最近200并显示完整总量 | 未执行：用户拒绝运行 |
| tasks-order | AC02/AC04 | 最近任务窗口按真实createdAt/id正序稳定展示 | 未执行：用户拒绝运行 |
| outside-prerequisite | AC02/AC04 | 窗口外前置依赖保留真实ID和范围说明 | 未执行：用户拒绝运行 |
| outside-no-anchor | AC02/AC04 | 窗口外 task 不生成指向不存在目标的锚点 | 未执行：用户拒绝运行 |
| cross-run-edge | AC02/AC04 | 跨Run前置依赖真实ID与范围说明可读 | 未执行：用户拒绝运行 |
| cross-subject-edge | AC02/AC04 | 跨subject前置依赖真实ID与范围说明可读 | 未执行：用户拒绝运行 |
| edges-cap | AC02/AC04 | 1201候选边最多返回1200并明确截断 | 未执行：用户拒绝运行 |
| edges-total | AC02/AC04 | 完整Run边总数与展示task候选边数分别呈现 | 未执行：用户拒绝运行 |
| partial-graph | AC02/AC04 | 边截断/窗口外关系明确部分图 | 未执行：用户拒绝运行 |
| graph-hide | AC02/AC06 | Hide graph后完整任务表仍可阅读 | 未执行：用户拒绝运行 |
| graph-node-link | AC02 | SVG节点定位到对应真实task指令 | 未执行：用户拒绝运行 |
| attempt-cap | AC03/AC04 | 301 attempt返回最近300并显示总量/截断 | 未执行：用户拒绝运行 |
| attempt-run-filter | AC03/AC04 | 较新的其他Run attempt不会挤占本Run窗口 | 未执行：用户拒绝运行 |
| attempt-task-link | AC03 | 历史 attempt关联保存的task ID | 未执行：用户拒绝运行 |
| attempt-number | AC03 | 历史 attempt展示实际attemptNo | 未执行：用户拒绝运行 |
| attempt-worker | AC03 | 保存历史Worker ID/Fleet链接，无当前租约断言 | 未执行：用户拒绝运行 |
| attempt-state | AC03 | FAILED/EXPIRED/COMPLETED历史状态原样可读 | 未执行：用户拒绝运行 |
| attempt-times | AC03 | attempt实际创建/开始/结束时间可读 | 未执行：用户拒绝运行 |
| attempt-failure-category | AC03 | 保存的failure category可读 | 未执行：用户拒绝运行 |
| attempt-reason-paused | AC03 | EXPIRED OPERATOR_PAUSED原因为暂停 | 未执行：用户拒绝运行 |
| attempt-reason-cancelled | AC03 | EXPIRED OPERATOR_CANCELLED原因为取消 | 未执行：用户拒绝运行 |
| attempt-reason-missing | AC03/AC04 | EXPIRED缺原因显示未记录且不推断自动恢复 | 未执行：用户拒绝运行 |
| attempt-summary | AC03 | 报告摘要独立显示，不冒充根因 | 未执行：用户拒绝运行 |
| failure-latest-outside | AC03/AC04 | 最新 FAILED即使在timeline窗口外仍独立显示 | 未执行：用户拒绝运行 |
| failure-event-time | AC03 | FAILED展示保存的事件时间 | 未执行：用户拒绝运行 |
| failure-event-actor | AC03 | FAILED展示保存的actor/task/detail | 未执行：用户拒绝运行 |
| recovery-event-time | AC03 | RECOVERED使用事件实际时间和actor | 未执行：用户拒绝运行 |
| recovery-event-no-attempt | AC03/AC04 | Run事件无attempt/Worker来源时不补造链接 | 未执行：用户拒绝运行 |
| recovery-history-cap | AC03/AC04 | FAILED/RECOVERED最近20条/完整23条/截断可读 | 未执行：用户拒绝运行 |
| auto-recovery-explanation | AC03 | 租约自动重新排队机制与人工recover分别说明 | 未执行：用户拒绝运行 |
| retry-not-recovery-time | AC03 | 后续attempt创建时间不冒充人工恢复时间 | 未执行：用户拒绝运行 |
| timeline-cap | AC04 | 最近300条/总量/截断与公开timeline API一致 | 未执行：用户拒绝运行 |
| timeline-chronology | AC04 | 返回窗口保持公开API occurredAt与同时间排序 | 未执行：用户拒绝运行 |
| timeline-boundaries | AC04 | 实际首尾时间与公开API窗口一致 | 未执行：用户拒绝运行 |
| timeline-utc-groups | AC04 | 跨UTC午夜条目按UTC日期分组 | 未执行：用户拒绝运行 |
| timeline-category-filter | AC04 | 类别筛选只隐藏不匹配的返回条目 | 未执行：用户拒绝运行 |
| timeline-text-filter | AC04 | 文本筛选匹配窗口中的实际正文 | 未执行：用户拒绝运行 |
| timeline-filter-scope | AC04 | 筛选明确只搜索返回窗口 | 未执行：用户拒绝运行 |
| timeline-reset | AC04 | Reset恢复整个返回窗口 | 未执行：用户拒绝运行 |
| timeline-full-detail | AC04 | 展开可阅读完整长事件detail | 未执行：用户拒绝运行 |
| timeline-detail-key | AC04/AC06 | 事件展开使用稳定key并在刷新后保持 | 未执行：用户拒绝运行 |
| timeline-missing-metadata | AC04 | 缺失actor/task/detail明确未记录 | 未执行：用户拒绝运行 |
| publication-text-only | AC04 | publication ref作为文本且不请求外部文件 | 未执行：用户拒绝运行 |
| no-broken-links | AC04 | 页面所有任务/attempt内链均指向存在目标 | 未执行：用户拒绝运行 |
| ADMIN-permission | AC05 | Admin可见既有Run控制且拥有POST权限 | 未执行：用户拒绝运行 |
| VIEWER-permission | AC05 | Viewer隐藏Run控制并拒绝有CSRF的POST | 未执行：用户拒绝运行 |
| OPERATOR-permission | AC05 | Operator可见Run控制且拥有POST权限 | 未执行：用户拒绝运行 |
| APPROVER-permission | AC05 | Approver隐藏Run控制并拒绝有CSRF的POST | 未执行：用户拒绝运行 |
| csrf-required | AC05 | 已登录Admin无CSRF写入被拒绝且状态不变 | 未执行：用户拒绝运行 |
| pause-submit | AC05 | 合法pause产生反馈与PAUSED事实 | 未执行：用户拒绝运行 |
| resume-submit | AC05 | 合法resume产生反馈与ACTIVE事实 | 未执行：用户拒绝运行 |
| recover-submit | AC05 | 合法recover产生反馈与RUNNING事实 | 未执行：用户拒绝运行 |
| cancel-warning | AC05 | Cancel存在不可逆/确认提示 | 未执行：用户拒绝运行 |
| cancel-submit | AC05 | 合法cancel产生CANCELLED事实 | 未执行：用户拒绝运行 |
| cancel-irreversible | AC05 | 已cancelled Run无法resume | 未执行：用户拒绝运行 |
| stale-action-conflict | AC05 | 旧页面pause在状态已改变时显示竞争错误 | 未执行：用户拒绝运行 |
| action-snapshot-reload | AC05 | 动作快照说明及Reload action availability入口可读 | 未执行：用户拒绝运行 |
| empty-graph | AC06 | 零task/attempt/dependency的缺失关联Run明确空态 | 未执行：用户拒绝运行 |
| long-id | AC06 | 完整长Run/task ID在任务表可阅读 | 未执行：用户拒绝运行 |
| width-390 | AC06 | 390px窄屏页面不横向溢出且图内部滚动 | 未执行：用户拒绝运行 |
| width-768 | AC06 | 768px页面不横向溢出 | 未执行：用户拒绝运行 |
| width-1280 | AC06 | 1280px页面不横向溢出 | 未执行：用户拒绝运行 |
| width-1440 | AC06 | 1440px页面不横向溢出 | 未执行：用户拒绝运行 |
| motion-toggle | AC06 | Motion off在导航后保持并关闭动效 | 未执行：用户拒绝运行 |
| reduced-motion | AC06 | 系统减少动效设置关闭装饰动画 | 未执行：用户拒绝运行 |
| refresh-auto | AC06 | 5秒周期真实更新只读事实 | 未执行：用户拒绝运行 |
| refresh-filter | AC06 | 刷新后类别/文本筛选条件保持并重新应用 | 未执行：用户拒绝运行 |
| refresh-graph-state | AC06 | 刷新后图开关及图内滚动保持 | 未执行：用户拒绝运行 |
| refresh-form-focus | AC06 | 编辑动作表单时输入/焦点保持并暂停刷新 | 未执行：用户拒绝运行 |
| refresh-selection | AC06 | 阅读选区的节点/文字/offset刷新后保持 | 未执行：用户拒绝运行 |
| refresh-scroll | AC06 | 局部刷新保留页面滚动位置 | 未执行：用户拒绝运行 |
| refresh-details | AC06 | 任务展开details刷新后保持 | 未执行：用户拒绝运行 |
| keyboard-focus | AC06 | Tab键真实焦点及可见outline可用 | 未执行：用户拒绝运行 |
| no-js | AC04/AC06 | 禁用JavaScript仍可阅读返回timeline与任务表 | 未覆盖：本命令聚焦Chrome交互；另需无脚本浏览上下文 |
| firefox-graph | AC02/AC06 | Firefox依赖图、表格、窗口和锚点兼容 | 未覆盖：用户已单独批准FD-002 Firefox延期；本命令未运行Firefox，仍保留库存与兼容风险 |
| firefox-refresh | AC06 | Firefox筛选/选区/焦点/动效刷新保持 | 未覆盖：用户已单独批准FD-002 Firefox延期；本命令未运行Firefox，仍保留库存与兼容风险 |
| lease-auto-runtime | AC03 | 真实租约到期requeue后页面区别人工recover | 未覆盖：聚焦只读详情，自动租约恢复在隔离运行中关闭；仅覆盖已有记录表达 |
| lease-pause-revocation | AC05 | pause撤销真实活跃Worker租约后详情保持一致 | 未覆盖：未启动Worker协议进程，控制动作使用无租约Run |
| concurrent-posts | AC05 | 真正同时两个控制POST竞争与原子性反馈 | 未覆盖：本命令覆盖旧页面状态变化的串行竞争；同时写入需要独立时序夹具 |
| timeline-tz-browser | AC04 | 非UTC浏览器时区仍按UTC日期分组 | 未覆盖：本命令检查UTC午夜数据，未再启动其他时区上下文 |
| missing-time | AC03/AC04 | 缺失可选attempt started/finished时间明确未记录 | 未覆盖：当前夹具终态attempt包含时间；需额外缺失可选时间夹具 |
