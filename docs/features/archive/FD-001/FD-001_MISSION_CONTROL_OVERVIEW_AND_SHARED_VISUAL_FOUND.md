# FD-001: Mission Control Overview and Shared Visual Foundation

**Status:** Complete
**Revision:** 14
**Priority:** Medium
**Test policy:** Independent
**Evidence policy:** Dual

## Problem

操作者需要在首页迅速判断当前执行、容量、等待原因和需要人工处理的事项，并进入真实对象的详情。当前首页主要呈现最近创建的 Run、数字与文本表格；浅色面板/深色侧栏没有体现已确认的未来战术指挥风格，Workers 页也没有稳定的机器生命体识别。

本 FD 只实现 REQ00001 的 M1：Mission Control Overview 与共享视觉基础。用户在 2026-10-04 批准需求，并随后通过 `$fd-workflow FD-001 auto` 授权实施与独立测试/审查流程；M2–M4 保留在原需求中，不因本 FD 完成而标记整个需求完成。运行测试、浏览器、网络和发布仍遵循仓库独立授权预算。

## Options and decision

| 方案 | 优点 | 成本/限制 | 决定 |
|---|---|---|---|
| 仅更改现有 CSS | 改动小 | 无法补齐首页信息组织、真实活动和 Worker 身份表达 | 不采用 |
| 沿用 MVC/Thymeleaf，增加首页读取投影、共享组件与局部轮询 | 复用服务、权限、路由和部署；可覆盖首期体验 | 需新增有界只读查询并处理刷新状态 | 采用 |
| 引入 SPA 或 SSE/WebSocket | 可以提供更复杂的持续交互 | 新依赖、构建链或连接生命周期超出首期必要范围 | 首期不采用 |

UI 沿用英文，工程文档使用中文。首期采用系统字体、CSS 和仓库内 SVG，不下载字体、图片或图标库。保持单应用部署与现有浏览器动作入口。

## Solution

### 页面结构与视觉

一级导航保持现有 URL，展示为 Overview、Runs、Fleet、Approvals、System；New Run 保持 operator 权限入口。共享 fragments/CSS 同时覆盖现有详情、表单、认证和错误页，后续页面功能继续按原需求分期。

首页布局依次组织快照时间/刷新状态与新建入口、容量摘要、活跃 Run、需要关注、Fleet 身份卡、等待原因和最近活动。1280px 以上使用分栏；768–1279px 减少列数；窄至 390px 时单列并让长表格局部滚动。长 ID 可以换行/复制或进入详情，需求摘要有完整读取入口。

共享视觉采用深色分层面板、细边框、局部切角、青蓝强调色与克制的 HUD 信息标记。成功、警告、失败保留语义色与文字，普通说明用易读字体，技术 ID 选择性使用等宽字体。键盘焦点清楚可见，状态不能仅依靠颜色；不使用覆盖信息的装饰或持续大幅动画。

Worker 形象采用仓库内可复用的 SVG fragment：将完整 Worker ID 用确定性算法映射到机械轮廓、核心与身份标记，不使用随机数或表格位置生成身份。同一 ID 在首页和 Workers 页保持相同形象，同时显示真实 ID/runtime/capabilities。未知 role 只表达身份，不虚构专业角色。

| 真实数据 | 表达 |
|---|---|
| ACTIVE 且 activeLeases=0 | 待命形态与 ACTIVE/Idle 文字 |
| ACTIVE 且 activeLeases>0 | 核心局部脉冲与实际租约占用 |
| DRAINING | 排空标记，区分停止新 claim 与已有租约继续 |
| OFFLINE | 暗化形态与 OFFLINE 文字 |
| task 失败 | 在 task/Run 关注区域显示，不自动标记 Worker 损坏 |

`prefers-reduced-motion` 和页面环境动效开关均可停用装饰/脉冲，文字、占用与身份仍完整可读。开关只存浏览器本地偏好，不新增服务端持久化。

### 读取边界与数据映射

在 webconsole 内增加专用 Overview DTO/读取组合；controller 负责绑定模板，QueryService 负责协调既有读取，JDBC 只做参数化只读投影。允许增加私有读取 DTO/查询，不更改领域状态、Worker 协议、schema、公开 REST 契约或命令权威。

| 区域 | 权威来源与首期读取 | 范围与缺失反馈 |
|---|---|---|
| Fleet/Run/租约/容量摘要 | `FleetDashboard` 的 workers/admission/backlog/runs/execution 与 capturedAt | 总量来自汇总；详情列表不冒充全集；不推断健康等级 |
| 活跃 Run | fleet activeRuns；按展示的 Run ID 批量补 requirement 与已完成任务数 | 首页最多 12 个，显示展示数和汇总/截断说明；不能从 recentRuns(200) 筛选冒充全部活跃 |
| 人工关注 | attentionRuns 和既有 Run dashboard 的失败语义，增加批量失败摘要读取 | 最多 8 个关注 Run；原因缺失显示 unavailable，链接已有详情 |
| 待审批 | 在数据库先按 PENDING 过滤再限 8 条，另取准确 pending 总数 | 不沿用 recentApprovals(100) 后过滤作为总数；现有决定操作不变 |
| Worker 身份与占用 | WorkerSummary 与 ActiveLeaseSummary | 首页展示 8 个，优先活动租约、其次 ACTIVE，再按 ID；Workers 页沿用 fleet 有界详情，明确列表范围 |
| 等待原因 | backlog 分类与 ReadyTaskSummary | 各原因最多 5 条真实任务及总量，样例与权威总量均按 policy → Run admission → capability → Worker capacity → Fleet admission 的主阻塞优先级互斥分类；额外未满足条件在任务内分别显示，不推测历史 claim 决策 |
| 最近活动 | 复用 timeline projection 的已有权威事件来源，新增跨 Run、按 occurredAt 和稳定来源键排序的有界只读查询 | 最新 20 条，显示范围和对象链接；不循环读取全部 Run 的完整 timeline，不引入事件存储或新业务事件 |

活动 DTO 至少包含 occurredAt、runId、事件类型、可用的 taskId/attempt/workerId 与安全文本摘要。事件来源必须与现有 timeline 的归属和状态语义一致；不存在的关联显示缺失。需要合成的 RUN_STARTED/最终状态只能按现有 timeline 规则从权威 Run 字段派生，不用更新时间冒充业务事件。首期不提供完整历史浏览器或事件保留政策。

读取实现先核对现有 JDBC timeline/Run/attempt/approval 字段，再写查询，使用稳定排序与有界 LIMIT；不要为视觉增加 schema。显示事实采用同一份 Overview 模型，统计和详情范围分别标记。动态任务数量仅表示已完成/当前总数，图形不得暗示 ETA 或整个工程完成百分比。

### 刷新、错误与操作

首页和 Workers 页使用现有 URL 的 HTML 响应做局部读取更新，每 5s/8s 请求一次，单次请求超时 10s，最多一个请求在途；不新增推送连接或 REST API。只替换专门标记的只读区域，导航、表单、焦点、滚动位置及用户输入保持稳定；操作/筛选事件使用委托绑定或明确重新绑定。

刷新时继续保留最近成功快照。最后成功刷新超过 30s、请求失败或暂停时显示相应状态、快照时间和手动刷新入口，不宣称实时健康。收到登录跳转、非预期 HTML 或没有目标区域时停止局部更新并提示重新进入页面，不能把登录/错误页当作空数据。页面隐藏或正在编辑时暂停，恢复可见/编辑结束后重新读取；失败不立即连续重试。

已有创建、审批、drain/offline 等动作仍调用现有 action controller/application services，保留 CSRF、权限与状态竞争处理。本 FD 不新增管理权限，不把页面可见性当作授权。共享样式不能隐藏错误、确认、提交或取消语义。

### 兼容性与风险

- URL、领域状态、既有审批/调度/Worker Pull 权威保持稳定；API plugin 补充已由用户撤回，不纳入需求或本 FD。
- 首期主要影响共享模板/CSS/JavaScript与私有读取。共享样式必须核对所有已有页面，新增查询须检查参数化、有界结果和避免全 Run/N+1 扫描。
- 局部刷新引入超时与单请求约束，仅属于浏览器读取行为；不调整 Worker 重试、租约、后端事务或运行时 shutdown。
- Worker 已取得本地 Chromium 视觉/交互证据；独立 Tester 的两轮运行在登录基础设施阶段受阻，尚无独立业务通过结果。视觉与行为验收仍是交付要求，不能用静态检查或编译结果替代。

## Scope

包括统一导航/共享战术视觉、首页真实数据组织、首期机器 Worker 身份与状态、必要有界只读查询、空态/异常态、刷新新鲜度及已有操作入口的可用性。

不包括 M2 完整 Run 执行图/时间线工作流、M3 完整 Fleet 工作关联与调度重构、M4 证据浏览器/审批重构，也不包括自动健康分级、告警阈值、完整历史、外部制品全文、Worker Runtime 改写、schema 迁移、新权限、SPA、SSE/WebSocket、部署/发布或桌面宠物。

健康规则、完整历史和外部制品的后续范围仍在原需求中待决；它们不阻塞本 FD 的有界 M1 方案，也不是已经被批准的新增能力。

## Work items

- [x] 1.1 建立 Overview 私有 DTO/读取组合：活跃 Run、准确 pending 总数、批量失败摘要、跨 Run 活动与列表范围；核对既有 timeline 来源，不改 schema。证据：字段到权威来源映射与有界查询静态审查，2026-10-04 离线 Java 21 compile 成功；运行时 SQL 验证仍待授权。
- [x] 1.2 建立共享战术视觉 tokens/fragments/仓库内 SVG；同步导航和所有现有页面的基本样式。证据：共享组件/原权限静态映射，现有页截图及 1440/1280/768/390px 浏览器验证；实际键盘焦点诊断复验通过。
- [x] 1.3 重组 Overview 的活跃工作、容量、关注、等待原因、最近活动与详情入口。证据：本地明确标记的零值/活跃/失败/暂停/审批样例、13 个非终态 Run 的 12 卡片窗口、16 个 pending 的 8 条窗口、20 条活动；静态权威映射与截图。
- [x] 1.4 在首页和 Workers 页加入稳定机器生命体身份、真实状态/占用、动效开关和减少动效适配。证据：105 Worker 截断与 4 Worker 状态样例分开验证，同一 Worker 跨页面 SVG 一致，开关持久与系统减少动效通过。
- [x] 1.5 实现局部轮询、新鲜度、失败/暂停/登录失效反馈、编辑和阅读保护。Worker 证据：一次请求在途、10s 超时、30s 陈旧、失败/非预期 HTML 保留快照、焦点/滚动/展开保护通过；暂停/恢复及完整角色矩阵继续由独立 Tester 细分，不宣称全部 AC07 已验收。
- [x] 1.6 更新 Web Console 中英文操作指南，整理 M1 验收映射及独立 Tester/Reviewer 交接材料。Worker 实际证据见正式报告；独立 Tester、PM 和 Reviewer 的正式结果按后续生命周期追加，Worker 勾选不代表这些角色已通过。

依赖顺序：1.1 → 1.3；1.2 → 1.3/1.4；1.3/1.4 → 1.5；其余完成后进入 1.6。按 auto 授权在主工作区顺序实施，保留其他任务的未提交变更；不创建 Task、OpenSpec change 或隔离 worktree。

主要文件：`webconsole/application/WebConsoleQueryService.java`、新增 Overview DTO/私有读取类、`webconsole/protocol/http/WebConsoleController.java`、`templates/ui/dashboard.html`、`workers.html`、`fragments.html`、新增 Worker SVG fragment、`static/ui/console.css`、`console.js` 及 `docs/current*/15-WEB-CONSOLE.md`。均相对 `src/main/java/me/asu/aidev/` 或 `src/main/resources/` 所属目录；不修改 Worker 包。

## Acceptance

| 编号 | 来源 | 可观测完成条件 |
|---|---|---|
| AC01 | R01/R11、A01/A02/A12 | 首屏区域形成完整战术指挥视觉；无数据时显示真实零值/空态；活跃工作与关注事项有详情入口；不注入演示数据 |
| AC02 | R01/R03、A02/A03 | 活跃 Run 不被最近创建列表替代；失败/暂停/审批/各类等待事实可区分，准确统计与有限详情分开显示 |
| AC03 | R02/R04、A04 | 已完成/当前任务数与阶段分别展示；repair/新增任务改变分母时不声称 ETA 或工程完成度 |
| AC04 | R06/R12、A08/A13 | 同一 Worker 首页/Workers 形象稳定，ID 和真实状态可读；Idle/Executing/DRAINING/OFFLINE 与租约事实对应，失败 task 不等于 Worker 故障 |
| AC05 | R07/R08、A07 | 活动按真实事件时间与稳定键排序，最多 20 条；真实对象可进入详情；缺失原因/关联和截断范围明确，不宣称完整历史 |
| AC06 | R09、A09 | VIEWER/OPERATOR/APPROVER/ADMIN 的现有入口与服务端权限一致；CSRF、错误和状态竞争反馈不被样式/刷新破坏 |
| AC07 | R10、A11 | 正常局部刷新保持输入/焦点/阅读位置；慢请求、失败、暂停、隐藏页和登录失效有准确反馈；可恢复，不显示假成功/假实时 |
| AC08 | R11/R12、A12/A13 | 390/768/1280/1440px 宽度可访问主要内容/操作；长文本/ID 可读；键盘焦点可见；关闭动效后身份与状态仍可辨认 |
| AC09 | M1 数据边界 | pending 总数在数据库按状态计算；有限窗口不冒充全部审批/活跃任务；跨 Run 活动不扫描全部 Run 完整 timeline |
| AC10 | M1 完成范围 | 共享样式覆盖现有页面且不损坏已有操作；指南注明新鲜度、范围和后续阶段；M1 完成不会将 M2–M4 或整个需求标记完成 |

## Verification

2026-10-04 Worker 已完成 1.1–1.5 的源码修改及 1.6 的中英文指南/交接材料。Worker 实际证据、验收映射与独立阶段剩余场景见 [正式实施报告](reports/FD-001-implementation-r3.md)，历史调试保留在 [进度报告](reports/FD-001-implementation-progress-r3.md)。本次沿用已认领 Worker 来源事件 `FD-001-000003-design-ready`，session 为 `fd001-worker-20261004-b2aa6c85`。工作项勾选只记录 Worker 完成，不代替独立验收。

已执行一次 compile-only：将 `JAVA_HOME` 设为 `C:/green/jdk-21.0.8`，运行 `C:/green/apache-maven-3.9.9/bin/mvn.cmd -o -DskipTests compile`；175 个 Java 源文件编译成功，耗时 7.891 秒，仅更新 `target/classes`，未执行测试或打包。

实施阶段先检查变更调用链、字段来源、状态映射、权限、空态和查询范围，再按仓库规则运行一次最窄 compile-only 检查；测试、浏览器、验证脚本、网络及最终产物构建没有默认授权。

独立 Tester 的后续授权记录必须绑定实际实施事件、FD revision/digest 和 Tester session；本 FD 的交接不授予命令执行预算。后续具体命令与浏览器场景需先提出并按仓库规则授权，不写“已通过”。

视觉证据要求：使用本地明确标识的验收数据，展示空态、活跃/失败/暂停/审批、Worker 四种状态、长文本、截断、刷新错误/暂停及减少动效。验证 Chromium 的 1440/1280/768/390px 可访问性；不宣称全平台支持。2026-10-04 用户明确「允许 Firefox 检查延期」：本机没有独立 Firefox，当前不下载依赖，Firefox 的 1440/1280px 验证保留为未运行及残余兼容风险；不减少页面功能要求。

2026-10-04 用户另行明确授权四个 Web Console IT 的离线定向命令和本地浏览器验证。四个 IT 已执行，4 tests / 0 failures / 0 errors / 0 skipped，耗时 14.166 秒。Worker 浏览器辅助脚本位于 `.ai/fd/FD-001/visual-runtime.py` 和 `visual-browser.mjs`，已静态检查其本机 URL、临时 DB 路径、后台进程与终止逻辑；只读取编译类路径，启动临时 SQLite 应用/Chromium 并保存验收数据截图，不下载依赖或读取生产 DB。独立 Tester 的命令仍需按事件/session 另行记录正式授权。

浏览器验证实际记录：前两次在错误的全集/窗口假设处失败；根据既有 Fleet 100 条明细排序拆分截断和四状态样例后，用户明确授权再运行一次。第三次 30 项检查中 29 项通过，表单程序化聚焦/outline 联合断言失败，进程 exit 1，不能标记整套通过；已有四宽度/状态/刷新等 18 张截图。当前只准备后续焦点诊断命令，等待单独授权，不自动继续重跑。原始记录和风险见进度报告。

后续焦点诊断（各次均获明确授权）：首次记录正确输入/activeElement 但 focusVisible=false / outline=none，失败证据保留；辅助脚本增加 CDP 目标激活与 focus emulation，第二次 `visual-runtime.py focus` exit 0，document.hasFocus=true、focusVisible=true、outline=solid，程序化和 Tab 键盘焦点 2 项通过。修正只影响浏览器验证环境，没有更改页面样式。前述历史失败不改写为成功，当前已用针对同一焦点问题的新证据解决 Gate。

保留命令实际结果、场景覆盖、未运行项和截图；如视觉/行为检查未获授权，记录残余风险，不能据此宣称已达到视觉验收。Reviewer 需独立检查 M1 行为、视觉证据和变更范围；未来报告遵循 Independent Test 与 Dual Evidence policy。

2026-10-04 独立 Tester 已在明确新增授权及诊断条件确认后提交 [r7 测试报告](reports/FD-001-test-r7.md)：36 个行为断言全部通过、42/88 场景覆盖（47.73%）、46 个场景未覆盖、源码分支未测。PM 已按 [r8 决定](reports/FD-001-test-decision-r8-v1.md) 记录低覆盖和分支不可用例外并接受报告；这不代表全部功能验收。r4 受阻和 r5 拒绝仍保留为历史。

独立 Reviewer session `fd001-reviewer-20261004-9195c0ad` 已真实领取 `FD-001-000009-test-accepted`，静态评审提交 [r9 评审报告](reviews/FD-001-review-r9.md)，结论为 changes-requested：等待分类总量/样例口径不一致，等待组动态计数破坏展开状态，跨区域文本选区保护不完整。Reviewer 未运行新的测试或浏览器；尊重 PM 覆盖例外和用户 Firefox 延期。需修正 F1–F3 后继续独立流程，不能据当前结果归档。

2026-10-05 独立修正复验已提交 [r11 测试报告](reports/FD-001-test-r11.md)：最终 4 个唯一行为断言全部通过，当前 5/91 场景覆盖（5.49%），86 场景未在此 revision 重跑，源码分支未测。初验 2 项通过/2 项失败和 runner 失败单独保留；相关脚本修正后的复验不回写历史，也不证明初验唯一根因。PM 已按 [r12 决定](reports/FD-001-test-decision-r12-v1.md) 明确接受本次修正范围的低覆盖、分支不可用和未变基线历史支持例外。

独立 Reviewer 已真实领取 `FD-001-000013-test-accepted`，提交 [r13 修正评审](reviews/FD-001-review-r13.md)：F1–F3 已解决，没有新增需要交回 Worker 的具体缺陷，评审结论 verification-passed。当前 waiting 分类/额外条件、真实计数变化的展开保持及跨五区选区保留均有直接复验证据。其余未变部分结合既有真实运行和独立静态审查判断；不宣称全部场景在此 revision 重跑。Reviewer 沿用独立 session，无新增运行，正式结果由真实 CLI 交接记录。

## TODO

修正阶段新增证据：[r10 修正交接](reports/FD-001-implementation-repair-r10.md)、[r11 定向复验](reports/FD-001-test-r11.md) 和 [r13 独立评审](reviews/FD-001-review-r13.md)。F1–F3 源码/说明及实际独立复验已完成；一次离线 Java 21 compile 成功（175 sources，4.079s）。历史通过/失败均保留，不作为当前覆盖重复计数。

- Worker 源码、指南、报告和当前焦点 Gate 已完成；四个 Web Console 回归、浏览器 29 项及后续 2 个焦点复验有真实结果，历史失败完整保留。
- 独立 Tester 已提交 [r4 受阻报告](reports/FD-001-test-r4.md)：88 个细分场景，0 个独立覆盖，36 个准备但未运行的业务断言；两次登录基础设施失败不算产品通过或产品行为失败。
- PM 已按 [r5 决定](reports/FD-001-test-decision-r5-v1.md) 正式拒绝报告，来源 `FD-001-000005-test-report-ready`；Worker 已认领真实恢复事件 `FD-001-000006-test-rejected`，保持原 session。
- Tester 在用户「授权一次诊断及条件复验」后完成 revision 7 的诊断和定向业务验证，36/36 通过；PM 已记录 47.73% 覆盖及分支未测的例外并接受报告。诊断支持当前新 page target，但不证明历史 ERR_ABORTED 的唯一根因；r4 原始证据及脚本版本保留。
- Worker 已认领 `FD-001-000010-changes-requested` 并修正 r9 F1–F3；两份指南、编译及独立复验已补齐。当前 4/4、5/91 以及 PM 例外和初验失败来源有正式记录；本轮运行预算已使用完，不自动追加。
- r13 独立评审结论通过，Reviewer 失败结果仍累计 1/3。后续关闭/归档由 host 另行处理；未覆盖分支、规模成本和 Firefox 风险保留，M2–M4 及整需求不因此标记完成。
- 如需改变 schema、权限、公开契约或业务范围，返回设计/人类决定。
- M2–M4 与原需求中的后续未决事项另行设计，不复制到本 FD 的完成清单。

## Sources

- Issue: REQ00001-web-control-plane
- [CLI 捕获并批准的需求计划](../requirements/REQ00001-web-control-plane/requirement-plan.md)
- [保留的原计划入口](../requirements/REQ00001-web-control-plane/plan.md)
- [当前能力与差距](../requirements/REQ00001-web-control-plane/current-state.md)
- [需求元数据与批准记录](../requirements/REQ00001-web-control-plane/requirement.toml)
- [Web Console 指南](../current/15-WEB-CONSOLE.md)、[生命周期](../current/03-AUTONOMOUS-ENGINEERING-LIFECYCLE.md)
- 2026-10-04 用户确认：「批准需求，并将 M1 交接到 FD」。撤回的 plugin 补充不作为设计来源。
- 静态源码：WebConsoleController/QueryService/JdbcWebConsoleReadRepository、FleetDashboard、AutonomousRunDashboard/TimelineService、现有 dashboard/workers/fragments 与 console.css/console.js。
- Planner session：`fd001-planner-20261004-86fbb56c`；已认领来源事件 `FD-001-000002-design-requested`。

**Completed:** 2026-10-04
