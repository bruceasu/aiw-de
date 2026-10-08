# FD-002 独立黑盒测试

首次已运行：24/104通过，其余未执行，运行因JVM native内存不足中断。
第2、3次运行均为36/104通过，0行为断言失败，68未运行；测试定位器误将Run身份标题
当作内容章节而中断，仅排除h1的第3次修正不足。第3次真实blocked报告已交PM。
r9第1次（总第4次）3/104通过，ADMIN JVM native内存分配失败；第2次（总第5次）
78执行/74通过/4失败/26未运行，覆盖71.15%，ADMIN Node commit失败，均exit1。
五次原始证据、脚本快照和报告保持，4个失败不改写为通过。
r12初验（总第6次）3执行/3通过/0业务断言失败/101未运行，覆盖2.88%，exit1。
ADMIN登录页后CDP Runtime.evaluate超时，Chrome日志记录GPU进程exit34；没有JVM/Node native崩溃文本。
这一轮的原始证据和脚本快照保持，不将环境阻断写成产品通过。
r12条件重试（总第7次）96执行/93通过/3失败/8未运行，覆盖89.42%，exit1；Category两个case
在控件观察器抛错，非空类别/筛选/刷新集合未到达；width-1440在截图捕获失败，长ID该宽度截图未到达。
七次原始证据、脚本快照、失败及Dual报告保持；本轮静态修正尚未运行。

当前静态修正从单个公开行/节点读取完整attempt ID，严格比较a2..a301全部300项并记录actual集合；
timeline条目纳入details并去掉重复wrapper/嵌套正文，检查祖先隐藏、关闭details和真实布局；
Category选取可见语义label控件，记录id、value/label选项及前后条目集合，保留非空/缩小/正文断言。
章节候选继续排除当前URL身份标题，并保留真实内容结构，不使用整页文本兜底。

真实Tester事件 `FD-002-000015-implementation-ready` 已由原独立session
`fd002-tester-20261005-15d090a2` 认领。启动器绑定revision 15，摘要
`c4fc25a59e6f03a5521ba6e10cc3b5bcfe17eb5cbf01dbff119079fc2e6f60ae`。
用户因耗时过长明确拒绝本轮继续测试。本revision未运行，initial/retry预算均为0；不执行本轮命令或补充验证。
label/viewport截图修正仅静态保留，真实未执行报告见 [r15报告](../../docs/features/reports/FD-002-test-report-r15-not-run.md)。
完整场景见 [scenarios.json](scenarios.json)，独立计划见
[FD-002-test-plan-r6.md](../../docs/features/reports/FD-002-test-plan-r6.md)。

静态保留的命令（本轮用户已拒绝运行）：

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

工作目录为仓库根目录。命令要求 revision 15 对应的 approved 授权 JSON、当前
Tester claim 和 FD 摘要匹配；随后在隔离 SQLite/Chrome 中依次检查四角色。
当前启动器要求 `FD-002-test-authorization-r15-v1.json`，并限制JVM初始/最大heap
为64m/512m、code cache为96m；SQLite迁移检查和夹具连接显式关闭。
测试JVM新增ActiveProcessorCount=2、TieredStopAtLevel=1、UseSerialGC以减少本次C2/G1相关资源，
Node新增max-semi-space-size=1、max-old-space-size=128限制V8资源；本轮待授权ADMIN总上限由230改420秒，其他角色仍75秒。
保留已执行的环境修正：Chrome一处 `--disable-gpu`，复用 `/json/list` 中唯一type=page/url=about:blank的初始页面，
以及CDP超时记录确切operation阶段。没有唯一初始页面时阻断，不额外创建tab；原5秒target查找超时不变。
登录操作、导航等待算法、12000ms CDP超时和业务断言不变。禁用测试GPU不能证明生产GPU性能或系统commit充足，
也不能据现有GPU退出日志认定它是evaluate超时的唯一原因。
不更改系统pagefile或其他进程，不能据此验证生产性能，系统commit不足风险仍在。
结果只写入本目录新建的 `evidence-r15-<时间>-<PID>` 子目录，清理仅针对自身 PID 与已校验临时路径。
不要把96/104计划覆盖写成真实通过，也不要复用 FD-001 的运行授权。

本次精确命令的范围、副作用与原8项未覆盖清单见
[FD-002-test-proposal-r15-v1.md](../../docs/features/reports/FD-002-test-proposal-r15-v1.md)。
最近真实结果见 [FD-002-test-report-r12-attempt2.md](../../docs/features/reports/FD-002-test-report-r12-attempt2.md)。
Firefox已获本FD单独延期，其2项场景继续保留为未覆盖；仍不授予额外运行预算。

本轮标签观察器克隆可观察label，只在克隆中剔除嵌套表单控件，保留Category语义；原始DOM不变。
全部select的原始label、剔除后文本、id、visible、options value/label和排除原因均保存，仍仅接受唯一
可见语义Category控件；非空value、真实条目缩小、正文和刷新集合断言保持，不按宽泛select回退。

本轮截图滚动后等待两次paint帧，直接捕获当前实际视口，`captureBeyondViewport:false`，不传clip。
每段先检查实际innerWidth/innerHeight/DPR：至多2000 CSS px高、4百万像素，PNG尺寸再核对。
manifest记录公开文档总高度、actual scroll/viewport、conceptual document coverage rect、文件与实际像素；
每段起点不得超过前段已覆盖终点，必须推进，允许末段滚动clamp重叠且覆盖全文底部。不能推进、超界或
capture失败均如实blocked。finally恢复原scroll并记录实际恢复坐标；不改case视口宽度、单次操作等待或业务断言。
首段保留name.png，其余为name-part-002.png等；四宽度、dense/long ID、角色及失败全纵向矩阵保持。
预计完整命令5–10分钟，包含约480个视口分段及失败截图成本；仅ADMIN子进程总上限230→420秒。
其他角色75秒、CDP12秒、lookup5秒和各操作等待保持；新授权须明确该总时限调整，不能复用旧grant。
若达到总上限，剩余case如实unrun。
该策略未运行，不保证Chrome/GPU或系统commit稳定，不把图片存在计为业务通过。

依赖统计断言识别 Run 返回数/总数及当前展示任务的候选数，不强制 full/entire
等措辞。201-task 边界夹具含一条属于被排除旧任务的真实边，因此完整 Run 为4条，
展示窗口候选为3条；原有 edges-total 场景同时检查这组不同总数与密集图截断。
