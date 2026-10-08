# FD-002 观察器与截图修正后的定向命令提案：Revision 12，v1

<!-- aiw-data: FD-002-test-proposal-r12-v1.json -->

本提案仅供静态审阅，运行与重试预算现均为0。此前五次原始证据、快照、报告和4个真实失败保持。
本revision没有实际运行结果，不能继承r9的74/104（71.15%）或把计划覆盖记为通过。

## 真实绑定与命令

| 项目 | 绑定 |
|---|---|
| 已认领implementation事件 | FD-002-000012-implementation-ready |
| FD revision | 12 |
| FD LF正规化SHA256 | 8a272107c44241a585978e51becdf1d769f5d17736f122b276fea088bb6178b9 |
| 独立Tester session | fd002-tester-20261005-15d090a2 |
| 必須正式批准的授权 | docs/features/reports/FD-002-test-authorization-r12-v1.json |
| 工作目录 | D:/03_projects/AI-tools/aiw-dev |
| 预计耗时 | 4–8分钟，含长页全部分段；受本地内存/截图成本影响，不是整体硬截止时间 |

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

请求新增一次执行及最多一次仅相关修正后的同命令重试，均须正式记录人类批准。
不自动重跑，不加参数/模式/diagnose。条件重试需要相关修正、宿主静态审阅和PM明确通知。
启动器在创建进程前严格检查最新dispatched receipt、session所有权、revision/digest、
当前FD正规化摘要、正式授权approved及event/session/command/cwd匹配；缺失/pending/旧绑定均退出。

## 本次静态修改

- attempt-run-filter：从公开单节点/行的完整ID读取，不对整节拼接文本使用word boundary计数；
  严格比较a2至a301全部300项集合，继续拒绝a1与外Run，记录actual IDs/节点来源/missing/extra。
- timeline rows：纳入合法details，以真实时间内容识别条目，避免wrapper/嵌套正文重复统计；
  可见性检查自身及全部祖先hidden/display/visibility/content-visibility、关闭details及getClientRects。
- Category：选取可见且语义label为Category的select，记录id/name/label、每个option的value/label/disabled，
  按实际非空value选择；保留非空、非空结果、缩小集合及文本过滤正文匹配。
  刷新前后同时记录条目key/正文/可见性/公开分类属性，并比较原控件值及完整可见集合保持。
- Node调用仅新增 `--max-semi-space-size=1 --max-old-space-size=128`，针对已有semi-space commit失败限制V8资源。
  JVM的ActiveProcessorCount=2、TieredStopAtLevel=1、SerialGC、heap64/512m和codecache96m保持。
- 截图仍覆盖原全部页面内容和矩阵，改为按公开layout metrics同宽纵向clip分段。
  每段最多2000 CSS px高且按DPR控制PNG至多400万像素；首段name.png，后续name-part-002.png等。
  每段请求和保存均持久化总高度/clip/文件名/像素尺寸，禁止无界整页bitmap；中断保留已生成分段。
- 仅变更runtime.py、browser.mjs、README及本提案；fixture/scenarios不改，所有case ID和104/96库存保持。
  不改产品/FD/旧报告/原5次快照，不读生产实现，不做语法检查或其他验证运行。

## 调用链、范围与覆盖口径

runtime依次为ADMIN、VIEWER、OPERATOR、APPROVER各启动一次独立JDK21应用、SQLite、Chrome profile。
使用已有classes和本地依赖，src/main/resources优先；不编译、不Maven、不下载。
V1–V16完成后fixture.seed造数，Node browser.mjs做公开DOM/API黑盒断言；repair/tick仅调用本地fixture.py。
四角色、原四宽度390/768/1280/1440、dense/long ID/失败/角色截图及96计划断言全部保留，若步骤未到达如实未运行。
截图或夹具写入本身不计通过，未用全页文字或数量近似放宽真实边、完整ID集合、筛选和布局断言。

104项适用、96项计划，92.31%只为计划上限；r12目前真实覆盖未执行。
历史r9最近结果为78执行/74通过/4失败/26未运行，仅作历史参照，不能累加。
分支覆盖not measured：没有字节码仪器；静态排除为空。

8项保留未覆盖：no-js、firefox-graph、firefox-refresh、lease-auto-runtime、lease-pause-revocation、
concurrent-posts、timeline-tz-browser、missing-time。
Firefox有本FD独立人类延期决定，其2项仍在分母；其余为无脚本/非UTC上下文、真实Worker租约、
同时POST及缺失可选时间夹具未包含。不能因延期或本命令未覆盖删除库存。

## 副作用、证据和限制

新建 `tests/fd002/evidence-r12-<时间>-<PID>/` 保存四脚本快照、execution、原始日志/results/diagnostics、
完整截图分段、fixture和临时DB副本，不覆盖五次历史。
各角色TEMP直属fd002-tester-*临时SQLite/profile；只请求loopback，无外部文件读取或生产DB写入。
只终止自身创建的Java PID和Chrome PID树；校验TEMP直属解析路径与前缀后删除，异常/活进程保留路径和日志。
一个清理失败仍独立尝试另一进程终止，DB拷贝失败不跳过必要清理。

应用readiness最多50秒、Chrome启动10秒、Node ADMIN 230秒/其余75秒均保持，不重启或重跑失败角色。
分段增加截图调用次数；V8限制可能增加GC频率，系统commit资源不足仍可能导致失败。
没有改系统pagefile、探测权限或停止别人的进程。这是测试环境修正，不证明生产性能或运行资源充分。
预算0期间只进行了静态文件阅读/编辑与native claim，未测试、浏览器、网络diagnose、语法检查或coverage运行。
