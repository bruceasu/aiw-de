# FD-002 定位器修正后的定向命令提案：Revision 6，v3

<!-- aiw-data: FD-002-test-proposal-r6-v3.json -->

本提案待批准，不授予执行权。初验和一次相关修正复验预算已经使用；Tester没有运行第三次命令。
第2次Dual报告为 `FD-002-test-report-r6-attempt2.md/.json`，真实36/104通过、0已执行行为失败、68未执行。

拟执行的精确命令仍为：

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

工作目录：`D:/03_projects/AI-tools/aiw-dev`；预计3–5分钟。
同一Tester session `fd002-tester-20261005-15d090a2`、事件 `FD-002-000006-implementation-ready`、
FD revision 6及摘要 `7869eb2108ad9ac8811e65ea339ecde7d6c08d7394add7230ab85a2e5a178c8a`。

静态修正仅把内容章节匹配限制为h2/h3/h4，排除描述Run身份的h1，避免Run ID包含attempt时被误认作章节标题。
不读取产品实现源码，不降低任何实际图形、计数、截断、状态、权限、刷新或响应式断言。
启动器只接受新的 `docs/features/reports/FD-002-test-authorization-r6-v3.json`，pending/缺失/不匹配均退出。

库存仍104项，脚本计划96项，计划上限92.31%；真实覆盖仍为第2次的34.62%，直到新增命令实际完成才重新统计。
Firefox、真实租约、同时POST等原8个缺口保留在分母中，Firefox没有延期决定。
分支覆盖因无字节码仪器保持unavailable。

调用的runtime/fixture/browser/scenarios和既有隔离范围相同：四角色依次在临时SQLite/Chrome中运行；
JVM heap64m/512m、code cache96m，SQLite连接显式关闭，诊断输出在角色证据目录；
仅本地loopback请求，阻止外部请求，无下载或生产DB写入，只停止自己创建的PID树并删除校验过的临时目录。
每次生成新evidence目录，初验/第2次脚本快照、结果、日志和数据库全部保留，不覆盖。

新增预算应只允许上面精确命令一次，不自动重跑，不增模式/参数，不加diagnose或额外浏览器测试。
