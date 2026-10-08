# FD-002 独立测试报告：Revision 6，第1次执行

<!-- aiw-data: FD-002-test-report-r6-attempt1.json -->

## 结论

本次授权命令实际执行 **24** 个业务断言，**24通过、0已执行行为失败**，命令整体 exit **1**。
需求场景覆盖为 **24/104（23.08%）**。其余 **80** 项未执行，其中72项因 ADMIN 流程中断而阻塞、8项为已列明的覆盖缺口。
计划96/104（92.31%）没有达成，不能作为实际覆盖。

建议为 **blocked**：现有证据不足以进入测试验收。业务代码分支覆盖为 **unavailable**，本命令没有字节码覆盖仪器。
已执行断言没有发现产品行为失败；命令中断不能证明其后的功能正确。

Tester：`fd002-tester-20261005-15d090a2`。来源事件：`FD-002-000006-implementation-ready`。
FD revision：6；摘要：`7869eb2108ad9ac8811e65ea339ecde7d6c08d7394add7230ab85a2e5a178c8a`。没有修改 FD 或产品，没有发送 test-report-ready。

## 实际结果及阻塞证据

ADMIN 已通过原始需求、长文本/转义、独立状态、公开 Dashboard 阶段和计数、单实施与并行依赖图方向/层级、
integration/final review/verification、repair刷新及历史保持、表格回退与真实锚点。
四角色权限都实际通过：ADMIN/OPERATOR 有合法 pause POST 权限，VIEWER/APPROVER 的带CSRF POST被拒绝且Run保持ACTIVE。

ADMIN 在导航 `/ui/runs/fd2-cycle` 时中断，原始 `ADMIN/browser.log` 和 `diagnostics.json` 记录：
`Error: net::ERR_INSUFFICIENT_RESOURCES`。此时 cycle 的两个断言尚未开始，后续 ADMIN 场景也没有执行。

`ADMIN/app.log` 记录 JVM native malloc 失败（申请1000816字节），并显示已经完成 V1–V16 迁移和应用启动。
这说明本次存在运行环境资源阻塞；当前证据不足以把它归为某项产品验收失败。
JVM同时把 `hs_err_pid29228.log` 和 `replay_pid29228.log` 写到仓库根目录。
PM核对路径后已原样移动到本次 `ADMIN/` 证据目录；Tester没有移动或修改它们，
异常副作用的原始内容仍保留。

四角色自身 Chrome PID 进程树已按对应 cleanup 日志终止。
但临时目录删除均遇 `PermissionError(13)` 文件占用，以下路径记录为残余清理风险：
- `C:\Users\suk\AppData\Local\Temp\fd002-tester-l7tawolt`
- `C:\Users\suk\AppData\Local\Temp\fd002-tester-5uuq6aj_`
- `C:\Users\suk\AppData\Local\Temp\fd002-tester-sn934uar`
- `C:\Users\suk\AppData\Local\Temp\fd002-tester-qgwouytr`

应用/Chrome/浏览器日志、逐断言结果、原始数据库和夹具均保留在
[`evidence-r6-20261005-072411-32980`](../../../tests/fd002/evidence-r6-20261005-072411-32980/)。
ADMIN在截图矩阵前中断，不能把计划中的密集图/长ID截图或宽度检查写为已完成；另三个角色完成角色检查和截图。

## 命令与授权

精确执行命令：

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

工作目录：`D:/03_projects/AI-tools/aiw-dev`。仅运行一次，没有自动重跑。
授权为 `docs/features/reports/FD-002-test-authorization-r6.md/.json`，
human approval：`approved:conversation:20261005-auto-test-review-FD001-FD002`。
启动器核对了同一事件、FD revision/digest、Tester session和精确命令。

原始覆盖计数与全部104项运行状态：`tests/fd002/evidence-r6-20261005-072411-32980/results.json`。
角色证据：`tests/fd002/evidence-r6-20261005-072411-32980/{ADMIN,VIEWER,OPERATOR,APPROVER}/results.json`，
应用日志 `app.log`、Chrome日志 `chrome.log`、浏览器日志 `browser.log` 与 `diagnostics.json`。
测试脚本原样副本保存在该证据目录根下，原始输入在各角色 `fixture.json`，数据库在 `tester.db`。

尚未执行：重试、Firefox、额外诊断测试、Python/Node语法检查、Maven、覆盖仪器、格式器、lint、全仓测试或发行构建。
分支覆盖原始证据不可用，原因是未安装/启用字节码覆盖仪器。
静态准备及本次证据读取只用于解释结果，不计业务覆盖。

## 逐场景库存与真实状态

以下104项与JSON `data.scenarios`一致。“阻塞”表示计划内未执行，“未覆盖”表示已明确未纳入本命令；均不计通过。

| 场景ID | 验收 | 独立可观察行为 | 本次结果 |
|---|---|---|---|
| requirement-original | AC01 | 需求来自 Run 保存的 requirementTaskId，非首个展示任务 | 通过 |
| requirement-long | AC01/AC06 | 完整长需求可阅读且保留末尾文本 | 通过 |
| requirement-escaped | AC01/AC06 | 需求 HTML/script 作为转义文本呈现 | 通过 |
| requirement-window-outside | AC01 | 需求 task 在200窗口外仍读取原始关联指令 | 阻塞：未执行 |
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

## 后续与风险

当前覆盖低于70%，且失败/恢复、窗口截断、timeline、控制竞争和响应式/阅读保持等主要行为未获运行证据，
PM不能从24项通过推断整个FD已完成。Firefox本轮未运行，FD-002尚无延期决策；未移用FD-001的决定。

一次相关修正后复验预算尚未使用。当前启动器已设置JVM heap/code cache上限、
诊断输出位置，并显式关闭SQLite连接；下一次仅接受新的r6-v2授权。
本报告不自行授予复验权限。首次临时目录的文件占用风险仍记录；仓库根崩溃诊断已由PM原样整理到本次ADMIN证据目录。
