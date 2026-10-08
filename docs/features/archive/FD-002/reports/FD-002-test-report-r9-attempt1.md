# FD-002 独立测试报告：Revision 9，第1次执行（总第4次）

<!-- aiw-data: FD-002-test-report-r9-attempt1.json -->

## 结论

本次精确命令exit **1**，实际执行 **3** 个业务断言，**3通过、0已执行行为失败**。
真实需求覆盖 **3/104（2.88%）**；**101** 项未运行，包括93项计划内被阻塞和8项原覆盖缺口。
计划96/104（92.31%）未完成。建议 **blocked**，当前revision 9不具备完整验收证据。
历史revision 6的36/104不累加到本轮覆盖，重复成功不重复计数。
业务代码分支覆盖 **not measured**，本命令无字节码覆盖仪器。

Tester：`fd002-tester-20261005-15d090a2`；implementation事件：`FD-002-000009-implementation-ready`；
实际FD revision **9**；正规化摘要 `baf9278f6c5241297e9b992a05ac3487c99989895404a7cae2dd3ccb6f8b75b1`。
没有改变FD、产品、场景库存或验收要求，没有提前emit报告，也没有自动使用剩余重试预算。

## 场景与证据

本次只通过VIEWER、OPERATOR、APPROVER三角色权限断言：Viewer/Approver隐藏Run控制，
带有效CSRF的控制POST返回403且保持ACTIVE；Operator可见动作且pause返回200并保存PAUSED。
ADMIN在登录导航阶段中断，尚未执行ADMIN-permission或任何章节/图/attempt/时间线/响应式/刷新断言。
93项计划内未执行，不推断为通过。其他三角色的截图已保存，截图本身没有计作业务断言。

原始目录：`tests/fd002/evidence-r9-20261005-075631-29524/`。
`results.json`保存完整104项、计数和基础设施问题；四个执行脚本副本与execution.json保存实际调用版本。
ADMIN/browser.log记录 `CDP timeout Runtime.evaluate`；ADMIN/diagnostics.json仅记录成功进入/ui/login，
没有section_associations记录，表明还没到章节定位器。该日志不提供具体evaluate操作标签，
不能精确区分登录提交与随后的导航等待。

已有ADMIN/app.log与ADMIN/hs_err_pid22512.log提供决定性环境证据：

- 16个迁移均已应用、应用已Started，随后native malloc无法分配 **136816 bytes**，`Chunk::new`。
- JVM启动约 **4.504秒**后崩溃；当前线程为 **C2 CompilerThread0**，正在编译StringConcatHelper.simpleConcat。
- JVM识别16个CPU，G1配置13个parallel workers、3个concurrent workers和13个refinement workers。
- heap已提交64 MB、使用42308 KB；metaspace使用约49 MB，没有Java heap OOM证据。
- 系统物理内存空闲1011 MB，但TotalPageFile18198 MB / **AvailPageFile仅1 MB**；进程private bytes293 MB、峰值313 MB。

这些文本支持系统commit资源几乎耗尽导致JVM native分配失败的判断。
JVM崩溃与随后CDP导航evaluate超时相符；不能单凭超时认定登录提交算法或产品页面失败。
Chrome updater IPC denied只是既有日志，未发起提权、权限探测或系统设置更改。
原始native/replay文件已直接写入本次ADMIN证据目录，保留不改。

本次summary仅报告ADMIN browser exit1，没有cleanup、DB副本保存或live_owned_pids异常。
各角色日志、原始tester.db和fixture.json仍在证据目录。runtime按own PID和安全TEMP直属路径清理；
未因本次异常新增进程清理命令或删除历史证据。

## 命令、授权与历史

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

工作目录：`D:/03_projects/AI-tools/aiw-dev`；无额外模式或参数，本revision实际运行一次。
执行前核对了event9最新/dispatched/session、FD正规化摘要和四脚本SHA256，均符合正式批准的
[r9-v1授权](FD-002-test-authorization-r9-v1.md)。
人类批准引用 `approved:conversation:call_vim7bxHX4FWji20Z2YrTI0DF`，初验预算已使用；
最多一次相关修正后的同命令重试仍未使用，需宿主审阅具体修正并明确通知，不能直接重复失败命令。

| 总次数 | 测试revision / 授权 | 实际结果 | 原始证据目录 |
|---|---|---|---|
| 1 | r6 / r6 | 24/104通过，native内存中断，exit1 | tests/fd002/evidence-r6-20261005-072411-32980 |
| 2 | r6 / r6-v2 | 36/104通过，章节定位器中断，exit1 | tests/fd002/evidence-r6-20261005-072852-34372 |
| 3 | r6 / r6-v3 | 36/104通过，章节定位器中断，exit1 | tests/fd002/evidence-r6-20261005-073707-7320 |
| 4 | r9 / r9-v1 | 3/104通过，ADMIN JVM native内存中断，exit1 | tests/fd002/evidence-r9-20261005-075631-29524 |

前三次报告/脚本快照/数据库/日志全部保持，历史授权入口为各自FD-002-test-authorization-r6*.md。
当前machine报告只统计本revision此轮实际执行，不把前三次成功写成本次结果。

## 未覆盖与风险

8个原未覆盖项仍在104分母：no-js、firefox-graph、firefox-refresh、lease-auto-runtime、
lease-pause-revocation、concurrent-posts、timeline-tz-browser、missing-time。
Firefox已由 [FD-002独立决定](FD-002-firefox-decision-r6.md) 延期，2项继续未覆盖，页面行为要求不变。
其余限制是未建立无脚本/非UTC上下文、未启动真实Worker租约协议、未发送同时POST及未造缺失可选时间。
纯静态排除项为空，104项全部适用。

拟提出的测试JVM局部资源限制须依据上述C2/G1/commit证据，保留heap业务容量、所有业务断言、
既有超时、应用/数据库隔离和清理边界。当前报告只反映已经执行的版本，后续修正不得冒充本次实际运行。
即使本地修正后通过，也不能证明生产环境性能或系统commit风险消失。
没有修改系统pagefile或停止他人的进程，没有下载、Maven、额外browser/diagnose、语法检查或coverage运行。

## 逐场景库存

以下104项由本次原始结果和既有库存逐项派生；93项计划内阻塞、8项未覆盖、3项实际通过。

| 场景ID | 验收 | 独立可观察行为 | 本次结果 |
|---|---|---|---|
| requirement-original | AC01 | 需求来自 Run 保存的 requirementTaskId，非首个展示任务 | 阻塞：未执行 |
| requirement-long | AC01/AC06 | 完整长需求可阅读且保留末尾文本 | 阻塞：未执行 |
| requirement-escaped | AC01/AC06 | 需求 HTML/script 作为转义文本呈现 | 阻塞：未执行 |
| requirement-window-outside | AC01 | 需求 task 在200窗口外仍读取原始关联指令 | 阻塞：未执行 |
| requirement-cross-run | AC01/AC04 | 需求关联归属异常时 unavailable，不能采用其他Run指令 | 阻塞：未执行 |
| requirement-missing | AC01/AC06 | 需求关联缺失时明确 unavailable | 阻塞：未执行 |
| status-lifecycle | AC01 | 生命周期与公开 Run API 保存状态一致 | 阻塞：未执行 |
| status-control | AC01 | 控制状态与公开 Run API 分别可阅读 | 阻塞：未执行 |
| status-effective | AC01 | 有效状态与公开 Run API 分别可阅读 | 阻塞：未执行 |
| dashboard-phase | AC01 | 当前阶段与公开 Dashboard 事实一致 | 阻塞：未执行 |
| task-counts | AC01 | 完整 Run task计数与 Dashboard一致 | 阻塞：未执行 |
| no-eta | AC01 | 任务完成数不生成 ETA 或工程完成百分比 | 阻塞：未执行 |
| single-edges | AC02 | 单实施图边与保存的依赖完全一致 | 阻塞：未执行 |
| single-direction | AC02 | 单实施 SVG 从前置任务指向 dependent | 阻塞：未执行 |
| parallel-edges | AC02 | 并行分支和 integration 真实边完全一致 | 阻塞：未执行 |
| parallel-levels | AC02 | 同层并行 task，integration 位于分支后方 | 阻塞：未执行 |
| final-review-level | AC02 | 最终 CODE_REVIEW 位于 integration 后方 | 阻塞：未执行 |
| verification-level | AC02 | INTEGRATION_VERIFICATION 位于 integration 后方 | 阻塞：未执行 |
| no-inferred-edge | AC02 | 同Run taskType/列表顺序不会生成未保存的边 | 阻塞：未执行 |
| repair-added | AC02 | 新增 repair 在局部刷新后显示真实 task 和边 | 阻塞：未执行 |
| repair-history | AC02 | repair 新增后原任务与原依赖仍保留 | 阻塞：未执行 |
| cycle-warning | AC02/AC06 | 循环依赖明确异常而不标完整 DAG | 阻塞：未执行 |
| cycle-table | AC02 | 循环的两条保存边仍可从文本/表格阅读 | 阻塞：未执行 |
| tasks-cap | AC02/AC04 | 201 task返回最近200并显示完整总量 | 阻塞：未执行 |
| tasks-order | AC02/AC04 | 最近任务窗口按真实createdAt/id正序稳定展示 | 阻塞：未执行 |
| outside-prerequisite | AC02/AC04 | 窗口外前置依赖保留真实ID和范围说明 | 阻塞：未执行 |
| outside-no-anchor | AC02/AC04 | 窗口外 task 不生成指向不存在目标的锚点 | 阻塞：未执行 |
| cross-run-edge | AC02/AC04 | 跨Run前置依赖真实ID与范围说明可读 | 阻塞：未执行 |
| cross-subject-edge | AC02/AC04 | 跨subject前置依赖真实ID与范围说明可读 | 阻塞：未执行 |
| edges-cap | AC02/AC04 | 1201候选边最多返回1200并明确截断 | 阻塞：未执行 |
| edges-total | AC02/AC04 | 完整Run边总数与展示task候选边数分别呈现 | 阻塞：未执行 |
| partial-graph | AC02/AC04 | 边截断/窗口外关系明确部分图 | 阻塞：未执行 |
| graph-hide | AC02/AC06 | Hide graph后完整任务表仍可阅读 | 阻塞：未执行 |
| graph-node-link | AC02 | SVG节点定位到对应真实task指令 | 阻塞：未执行 |
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
| ADMIN-permission | AC05 | Admin可见既有Run控制且拥有POST权限 | 阻塞：未执行 |
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
| no-js | AC04/AC06 | 禁用JavaScript仍可阅读返回timeline与任务表 | 未覆盖：本命令聚焦Chrome交互；另需无脚本浏览上下文 |
| firefox-graph | AC02/AC06 | Firefox依赖图、表格、窗口和锚点兼容 | 未覆盖：用户已单独批准FD-002 Firefox延期；本命令未运行Firefox，仍保留库存与兼容风险 |
| firefox-refresh | AC06 | Firefox筛选/选区/焦点/动效刷新保持 | 未覆盖：用户已单独批准FD-002 Firefox延期；本命令未运行Firefox，仍保留库存与兼容风险 |
| lease-auto-runtime | AC03 | 真实租约到期requeue后页面区别人工recover | 未覆盖：聚焦只读详情，自动租约恢复在隔离运行中关闭；仅覆盖已有记录表达 |
| lease-pause-revocation | AC05 | pause撤销真实活跃Worker租约后详情保持一致 | 未覆盖：未启动Worker协议进程，控制动作使用无租约Run |
| concurrent-posts | AC05 | 真正同时两个控制POST竞争与原子性反馈 | 未覆盖：本命令覆盖旧页面状态变化的串行竞争；同时写入需要独立时序夹具 |
| timeline-tz-browser | AC04 | 非UTC浏览器时区仍按UTC日期分组 | 未覆盖：本命令检查UTC午夜数据，未再启动其他时区上下文 |
| missing-time | AC03/AC04 | 缺失可选attempt started/finished时间明确未记录 | 未覆盖：当前夹具终态attempt包含时间；需额外缺失可选时间夹具 |
