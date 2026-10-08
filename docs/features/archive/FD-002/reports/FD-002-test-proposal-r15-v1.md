# FD-002 Revision 15：独立定向测试命令提案 v1

<!-- aiw-data: FD-002-test-proposal-r15-v1.json -->

## 真实绑定与用户停止决定

真实事件 `FD-002-000015-implementation-ready`，revision **15**，LF正规化FD摘要
`c4fc25a59e6f03a5521ba6e10cc3b5bcfe17eb5cbf01dbff119079fc2e6f60ae`。
已由原独立Tester session `fd002-tester-20261005-15d090a2` claim，FD保持冻结。
用户因测试耗时过长明确指示本轮不再测试，决定引用
`denied:conversation:user-stop-fd002-tests-2026-10-05`。
原提案请求初验1与相关修正重试1，现均未获批准，当前initial/retry仍为 **0**，本轮停止运行请求。
启动器要求 `docs/features/reports/FD-002-test-authorization-r15-v1.json` approved且全部绑定匹配；
denied、pending或缺文件拒绝执行。本提案仅静态保留，不构成执行权。

## 范围、静态修正及事实限制

[r12真实报告](FD-002-test-report-r12-attempt2.md) 保留96执行、93通过、3失败、8未运行、
89.42%、exit1。原七次raw、脚本快照、报告和失败全部保持，不累加为本revision结果。
[r15未执行报告](FD-002-test-report-r15-not-run.md) 为0执行/0通过/0失败/104未运行，覆盖0.00%。

本轮只静态修正测试观察器和截图：

- label文本只在克隆中剔除input/select/textarea/button/option/optgroup，避免嵌套option文字与Category拼接。
  保存原始label/剔除后文本；不读取或修改产品实现/原始DOM。
- 在判断之前记录全部公开select的id/name、原始label关联、可读文本、visibility、option value/label与排除原因；
  仍要求唯一可见语义Category控件、非空类别value、真实条目缩小与刷新前后集合，不采用宽泛select兜底。
  “嵌套文字拼接”是自身helper的明确算法风险；不将尚未运行的修正写成已确认产品DOM根因或通过。
- 截图滚动并等待两次paint帧，直接 `Page.captureScreenshot({format:'png',captureBeyondViewport:false})`，不传clip。
  每段先按真实innerWidth/innerHeight/DPR检查≤2000 CSS px高及≤4000000像素，PNG真实尺寸随后核对。
  manifest记录actual scroll、viewport、conceptual document coverage rect、已覆盖终点、文件/像素尺寸和错误。
- 每段起点不得越过先前终点且必须推进；末段允许浏览器clamp造成重叠，必须覆盖原始和末次公开文档底部。
  不改变case视口宽度；超界、不能推进或capture失败如实blocked，已有段仍保留。finally恢复原scroll并记录验证结果。
- runtime仅绑定真实15/event/digest/evidence-r15/授权r15-v1；保留一处disable-gpu、唯一初始blank页、operation标签、
  原JVM/V8资源限制、四角色、96计划断言/104库存及单次操作等待。fixture和scenarios不改。
- 仅ADMIN子进程总时限由230秒改420秒，适应约480个真实视口段；这是未批准r15新命令的一部分，
  新授权请求须明确该变化，不使用旧grant。其他角色75秒、单次CDP12秒、lookup5秒及各操作等待保持。

没有运行测试、浏览器、诊断、语法/类型/自检或coverage。静态修正未验证，未生成本revision运行证据。

## 精确命令与预计成本

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

cwd：`D:/03_projects/AI-tools/aiw-dev`。无模式或额外参数；一次依次启动ADMIN、VIEWER、OPERATOR、APPROVER。
包含全部96计划case（实际到达者才计执行）及dense/long-ID的390/768/1280/1440矩阵、final-admin、
四角色和每个failed-case的完整纵向截图。

预计 **5–10分钟**，考虑900px真实视口下约480个全纵向分段及失败截图成本；
原应用readiness50秒、target lookup5秒、CDP12秒、其他角色75秒上限保持；仅ADMIN总上限230→420秒。
这是完整命令成本估计；若达到总上限，剩余case如实未运行，不额外扩大等待或自动重跑。
首段name.png、其余name-part-002.png，所有actual viewport/coverage/restore状态逐段保存。
不把造数、页面加载、图片存在或静态检查计为业务通过。

## 副作用、清理与覆盖

仅生成独立 `tests/fd002/evidence-r15-<时间>-<PID>`，保留四脚本快照、完整原始日志/截图/manifest/结果与临时DB副本。
四角色各自独立直属安全TEMP SQLite/profile、自有PID及loopback端口；串行启动。
终止仅自有PID，校验TEMP父目录与fd002-tester前缀才清理；活进程或清理/数据库保存失败保留原始路径。
无下载、外部服务、生产数据库写入、Maven/build、最终发布产物或其他人的进程修改。

104场景全适用，计划96/104（92.31%仅计划上界），当前revision尚未执行。
上一revision的93通过不累加为本次覆盖；branch coverage **not measured**，没有字节码仪器。
8未覆盖保持：no-js、firefox-graph、firefox-refresh、lease-auto-runtime、lease-pause-revocation、
concurrent-posts、timeline-tz-browser、missing-time。
Firefox仅本FD独立获延期，仍计104分母，见 [Firefox决定](FD-002-firefox-decision-r6.md)。
viewport截图减少对超长document backing的请求，但修正未执行，不保证Chrome/GPU或系统commit稳定。
分段数/GC成本增加，420秒ADMIN总上限仍可能阻断；生产GPU、性能及未覆盖行为仍未验证。
