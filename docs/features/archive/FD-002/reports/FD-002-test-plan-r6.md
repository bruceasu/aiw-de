# FD-002 独立黑盒测试计划：Revision 6

<!-- aiw-data: FD-002-test-plan-r6.json -->

本文件记录初始测试计划，不是通过报告。首次命令已运行，真实结果为24/104通过、
0已执行行为失败、80未执行，并因JVM native内存不足中断；见
`FD-002-test-report-r6-attempt1.md/.json`。唯一修正复验已运行：36/104通过、0已执行
行为失败、68未执行，并因测试定位器误匹配Run h1而中断；见
`FD-002-test-report-r6-attempt2.md/.json`。当前只静态修正定位器、改要求r6-v3授权，
初验和一次复验预算均已耗尽，下一次命令需新增明确预算。Tester 为
`fd002-tester-20261005-15d090a2`，已领取
`FD-002-000006-implementation-ready`；FD revision 为 6，摘要为
`7869eb2108ad9ac8811e65ea339ecde7d6c08d7394add7230ab85a2e5a178c8a`。

## 唯一拟运行命令

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

工作目录：`D:/03_projects/AI-tools/aiw-dev`。预计正常完成需 **3–5 分钟**。
每角色仅启动一次应用及 Chrome，不重跑失败断言、不自动重新启动应用。
应用就绪期限为每角色 50 秒；Chrome 就绪期限约 10 秒；ADMIN 浏览器脚本
上限 230 秒，其余各角色上限 75 秒。异常超时及清理会增加耗时。

执行前必须已有 Planner 的
`docs/features/reports/FD-002-test-authorization-r6-v3.json`，且 decision 为 approved，
事件、revision、digest、Tester session、精确命令和工作目录全部匹配。
启动器还检查最新事件仍为本次已派发 Tester 事件，以及当前 FD 正规化 LF 后
的 SHA-256。缺失、pending、denied 或任何不匹配均在启动进程前退出。

## 黑盒来源与边界

用例只依据 FD Acceptance/Scope、公共领域契约、
`docs/current-zh/WEB-CONSOLE-RUN-MISSION.md`、
`docs/current-zh/15-WEB-CONSOLE.md`、
`docs/current/05-API-USER-MANUAL.md` 及已有迁移表约束推导。
现有 `tests/fd001/runtime.py`、`browser.mjs`、`review-fixes.mjs` 仅提供本地
启动、登录、CDP 导航与焦点控制范例。没有读取 `src/main/java`、
`run-detail.html`、`console.js`、`mission.js` 或 `console.css` 实现源码。

运行时观察页面公开 DOM、公开 Run/Dashboard/Timeline API 与独立夹具期望。
页面选择器使用语义标题、表单 action、文本与 DOM 的公开可见属性。
选择器或 API 形状无法辨认时会记录失败或基础设施阻塞，不推断通过。

授权前的 Planner 静态意见已修正：图节点与边通过 getCTM/DOMPoint 转到共同
SVG 坐标系；标题在内容区前也能关联实际 section；任务总数保留公开字段路径，
有效状态采用公开 Dashboard；Reset filters 与缺关联的相同语义说明均可识别；
边计数按窗口分母及完整 Run 范围核对。所有显式刷新后的保持断言先在 9 秒有限
期限内只读轮询，确认至少一个未保护 region 真正替换；编辑暂停案例单独检查
输入/焦点与快照不变。Publication ref 的夹具保存在 COMPLETED attempt。
这些修正没有增加场景，没有降低页面宽度或真实图几何的检查要求。
最终依赖统计断言以 Run 计数标记、返回窗口分母和展示任务候选数识别语义，
不要求 full/entire/whole/all 措辞。201-task 边界夹具增加一条由已排除旧任务拥有的
真实依赖，因此完整 Run 为4条、窗口候选和返回均为3条；原有 edges-total 场景
与1201候选/1200返回的密集图一起核对。新增边不增加展示节点，也不改变200-task
窗口顺序、窗口外ID或跨Run/subject的既有期望。

## 场景库存及计划覆盖

完整逐项库存见 `tests/fd002/scenarios.json`。共有 **104** 项独立可观察场景，
其中 **96** 项有本命令对应业务断言，计划覆盖上限为 **92.31%**。
初始计划编写时实际执行数、通过数均为0；最新运行的实际需求覆盖为 **36/104（34.62%）**。
每条断言直接对应一个库存 ID，不能把截图生成、fixture 写入或进程启动计为
业务通过。宽度场景分别包含密集图与长 Run ID/INTEGRATION_VERIFICATION 页面。

| 主要范围 | 本命令计划场景数 | 独立可观察行为 |
|---|---:|---|
| AC01 | 12 | 原始 requirementTaskId、长文本/转义、窗口外/错误归属/缺关联、独立状态、阶段、计数、无虚构 ETA |
| AC02 | 22 | 单实施、并行/integration/final review/verification、无推断边、repair 保留历史、循环、任务200/边1200、窗口外/跨Run/subject、表格回退与锚点 |
| AC03 | 20 | attempt300、先过滤Run、task/编号/Worker/状态/实际时间、原因/类别/摘要、独立FAILED、RECOVERED/最近20条、自动机制与人工事件区分 |
| AC04 | 13 | timeline300及总量/截断、顺序/首尾/UTC分组、类别/文本/Reset、全文/稳定key、缺元数据、publication文本、完整内链 |
| AC05 | 13 | 四角色显示与POST权限、CSRF、pause/resume/recover/cancel反馈、不可逆、旧页面竞争、动作快照说明 |
| AC06 | 16 | 空态、完整长ID、390/768/1280/1440宽度、动效、5秒刷新、筛选/图开关/图内滚动/表单/选区/页面滚动/展开/键盘焦点 |

另有 **8** 项明确未覆盖：无 JavaScript 阅读、Firefox 图及布局、Firefox 刷新保持、
真实租约到期 requeue、pause 撤销真实活跃 Worker 租约、真正同时的控制 POST、
非 UTC 浏览器时区、缺失可选 attempt started/finished 时间。这些场景保留在分母中，
没有采用 FD-001 的 Firefox 延期决定。

业务代码分支覆盖为 **unavailable**：本命令没有字节码覆盖仪器；不能以需求覆盖
或 Chrome 成功代替分支覆盖。实际分母/通过数、阻塞与未覆盖原因将在执行后由
原始 `results.json` 导出，并由独立 Tester 再写正式 Dual 报告。

## 调用代码与隔离副作用

本命令调用以下所有测试文件：

- `tests/fd002/runtime.py`：授权/版本校验、四角色顺序启动、迁移就绪、证据收集、清理。
- `tests/fd002/fixture.py`：在专用临时 SQLite 写入独立夹具及 repair/tick 两种受限变化。
- `tests/fd002/browser.mjs`：Chrome CDP、登录、逐场景公开页面/API断言、截图、原始结果。
- `tests/fd002/scenarios.json`：104项场景库存与计划范围。

使用已有 `target/classes`、`src/main/resources` 与已有 failsafe XML 中的本地 jar
classpath；不调用 Maven，不编译，不下载依赖，不生成发行物。
修正复验的Java参数为 `-Xms64m -Xmx512m -XX:ReservedCodeCacheSize=96m`；
不强制Metaspace上限。ErrorFile/ReplayDataFile定向到当前角色证据目录。
迁移检查使用contextlib.closing，夹具也在事务结束后显式关闭SQLite连接。
每角色使用 `%TEMP%/fd002-tester-*` 中的全新 SQLite 和 Chrome profile。
数据库由应用 Flyway 完整迁移至 V16 后造数。故意缺失 requirement 的异常夹具仅在
该专用 SQLite 内暂时关闭 FK；普通 fixture 保留 CHECK/FK、租约字段一致性，
当前 Worker lease=0、Run slot=0，历史 attempt 不代表当前占有租约。

应用明确绑定 `127.0.0.1` 随机端口并关闭自动租约恢复、Telegram 和自动审批；
子进程环境去除 AIW/Spring/server/JVM 注入变量。浏览器关闭后台网络等组件，
阻断非 loopback DNS；CDP 拦截目标页面所有请求，仅允许当前临时应用地址，
外部请求记为失败并阻止。读取 Management API 仅指向该临时应用。
登录与操作只使用已知本地测试账号；不读取生产数据库或外部文件。

退出时先尝试停止自己创建的 Chrome PID 进程树，再独立尝试终止自己创建的 Java PID。
不得按进程名称终止其他 Chrome/Java。将 DB/WAL、应用/Chrome/浏览器日志、夹具清单、
诊断、逐断言结果及截图保留到
`tests/fd002/evidence-r6-<时间>-<PID>/`。仅递归删除已解析校验为系统临时目录直属、
名称以 `fd002-tester-` 开头且由本次创建的路径。
如果进程仍活跃、终止失败或数据库证据复制失败，则保留原临时路径并报告清理风险。

正常命令包含 ADMIN/VIEWER/OPERATOR/APPROVER 四角色。
ADMIN 包含 390/768/1280/1440 的密集图与长ID共8张截图、最终截图；
四角色各有角色截图；失败断言另存对应截图。断言结果逐条落盘，截断时不会把
未执行库存项写为通过。

## 已执行与未执行

已执行：定向 PowerShell 文件读取、`rg --files`、独立 `aiw fd claim`，以及一次
静态只读库存/断言映射核对和路径状态读取。静态核对得到库存104/计划96/未覆盖8，
没有缺少对应断言的 planned ID。

已执行一次本测试命令和其范围内Chrome/临时应用loopback请求，真实结果见首次报告。
尚未执行：定位器修正后的新增预算命令、Firefox、Python/Node语法检查、Maven、覆盖仪器、lint、格式器
或其他验证脚本。没有生成 `test-report-ready` 事件。

残余风险：公开 DOM 语义匹配与具体 API JSON 形状尚无运行证据；现有 TimelineService
全量读取成本不由输出窗口或这条 focused 命令证明有界。Firefox、真实 Worker 租约、
真正同时的写竞争及分支覆盖保留上述缺口。
