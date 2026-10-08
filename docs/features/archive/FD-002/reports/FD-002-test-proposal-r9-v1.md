# FD-002 第四次定向命令提案：Revision 9，v1

<!-- aiw-data: FD-002-test-proposal-r9-v1.json -->

本提案只供静态审阅，不授予执行权。此前三次精确命令预算已使用，第四次尚未执行。
第3次真实blocked报告已通过 `FD-002-000007-test-report-ready` 交PM，后经PM拒绝返回Worker。
不得把本次准备、Worker的compile-only成功或前3次重复成功计为新测试通过。

## 真实绑定与精确命令

| 项目 | 绑定 |
|---|---|
| 最新已认领事件 | `FD-002-000009-implementation-ready` |
| FD revision | 9 |
| FD LF正规化SHA256 | `baf9278f6c5241297e9b992a05ac3487c99989895404a7cae2dd3ccb6f8b75b1` |
| 独立Tester session | `fd002-tester-20261005-15d090a2` |
| 必须批准的授权文件 | `docs/features/reports/FD-002-test-authorization-r9-v1.json` |
| 工作目录 | `D:/03_projects/AI-tools/aiw-dev` |
| 预计耗时 | 3–5分钟；这不是整套硬截止时间 |

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

命令无附加模式或参数，请求一次新增运行预算及最多一次仅相关修正后的同命令重试，
不自动重跑。当前两类预算均为零；条件重试须有相关修正和PM明确通知，不能直接重复失败命令。
启动器在创建进程和证据目录前检查最新receipt、dispatched状态、session所有权、revision、
digest及当前FD正规化摘要，并要求正式授权decision=approved与事件/版本/摘要/session/command/cwd全部匹配。
授权缺失、pending、denied、旧版本或错绑定均拒绝执行。

## 调用链与计划断言

`runtime.py`依次为ADMIN、VIEWER、OPERATOR、APPROVER启动各自的临时应用和Chrome；
每个角色只启动一次。应用从已有JDK21 classes及本地依赖启动，`src/main/resources`优先，
沿用已存在failsafe报告里的classpath，不调用Maven、编译、下载或最终构建。
应用完成V1–V16 SQLite初始化后，`fixture.py.seed`写入本角色隔离数据库。
随后Node执行 `browser.mjs`；repair/tick造数仍由其调用本地Python `fixture.py`完成，
不触及生产数据库。

本命令包含四角色启动、全部96个既有计划断言和原截图矩阵，前提是运行到达对应步骤。
截图包括密集图与长Run ID在390/768/1280/1440四宽度、final-admin、每个角色和失败断言截图；
仅截图存在、夹具写入或页面可载入不能计为业务通过。未到达的步骤如实记未运行。
断言范围仍为AC01–06：真实需求/状态/计数、真实依赖图与修复历史、任务和边窗口、
attempt/FAILED/RECOVERED真实来源、有限时间线/筛选/展开、角色/CSRF/动作/状态竞争、
长ID/窄屏/动效与刷新后的阅读和编辑保持。没有新增场景，也没有降低业务断言。

章节定位器已静态改为遍历所有语义标题候选，按公开当前URL排除Run身份标题，
依次尝试aria-labelledby、真实enclosing section/region及标题后的内容。
无关联候选继续，所有候选均无有意义关联才失败；保留table/details等适用结构要求，
拒绝整页文本兜底。候选、排除理由、选中结构和关联依据保存diagnostics.json。
本次仅更新启动器的真实事件/版本/摘要/证据目录/授权文件绑定，定位器与其他断言未再改动。

## 覆盖口径与未覆盖

库存104项，计划96项，计划上限92.31%只描述准备范围。
前三次最新实际结果为36/104（34.62%）、36通过、0已执行业务失败、68未运行；
这属于revision 6历史证据，并非revision 9实际覆盖。新revision尚无执行结果。
分支覆盖未测：本地黑盒命令没有字节码分支仪器，不能推算覆盖率。

| 保留未覆盖的场景ID | 本命令限制 |
|---|---|
| no-js | 未创建禁用JavaScript上下文 |
| firefox-graph | 用户已单独批准FD-002 Firefox延期，仍保留库存与兼容风险 |
| firefox-refresh | 同上，延期不改变页面行为要求 |
| lease-auto-runtime | 隔离应用关闭自动租约恢复，只核对已有记录与机制表达 |
| lease-pause-revocation | 未启动Worker协议进程，控制动作使用无租约Run |
| concurrent-posts | 覆盖串行旧页面竞争，未发送真正同时POST |
| timeline-tz-browser | 检查UTC午夜数据，未建立非UTC浏览器上下文 |
| missing-time | 终态attempt夹具包含时间，未添加缺失可选时间夹具 |

Firefox独立决定见 [FD-002-firefox-decision-r6.md](FD-002-firefox-decision-r6.md)。
8项均继续留在104项分母，不能因延期或本命令未覆盖而删除。

## 副作用、清理与证据

- 只向 `tests/fd002/evidence-r9-<时间>-<PID>/` 新目录写脚本快照、execution元数据、
  原始日志、角色断言结果、diagnostics、截图、夹具清单和临时DB副本；不覆盖前3次证据或报告。
- 为各角色创建 `%TEMP%/fd002-tester-*` 直属临时目录、SQLite和独立Chrome profile。
  recursive cleanup前要求路径解析后仍为该TEMP直属且前缀正确。
- Java heap初始64m/最大512m、code cache96m；JVM native/replay诊断写入本角色证据目录。
  SQLite迁移检查及夹具连接显式关闭。只有本地loopback请求，Chrome外部请求拦截，无下载。
- 清理只停止本角色自身创建的Chrome PID树与Java PID，不按进程名杀进程。
  独立try/finally保证一个清理失败仍尝试另一个；保留日志/DB失败不会跳过必要进程终止。
  活进程或清理异常时保留临时路径，并将PID/路径/错误写入证据，不删除持有中的目录。
- 每角色应用readiness最多50秒，Chrome调试启动等待10秒，Node主体ADMIN最多230秒、
  其余角色各75秒；轮询只读状态，不重启失败应用，不重跑业务。异常可延长清理和总体耗时。

当前仅进行了文件阅读、native claim与一次静态读取/哈希；未运行测试、语法检查、
浏览器、coverage或网络诊断。待宿主写入pending正式授权并取得人类批准后，再由PM明确通知执行。
