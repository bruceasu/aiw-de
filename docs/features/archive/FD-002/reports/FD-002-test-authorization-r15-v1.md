# FD-002 Planner 测试决定：r15 v1（用户停止测试）

<!-- aiw-data: FD-002-test-authorization-r15-v1.json -->

## 决定

**denied**，用户明确决定测试耗时过长，本轮不再测试。initial与retry预算均为0，
不得运行。此决定记录用户停止指令，不是测试通过或对实现质量的结论。
用户最近批准的初验及一次相关修正重试已全部执行：最新96项执行、93通过、3失败、8未运行，
需求覆盖89.42%，分支not measured。[实际报告](FD-002-test-report-r12-attempt2.md)与全部七次证据保持。
PM已正式拒绝；此前批准的初验及相关修正重试均已消耗。本次绑定真实新事件，
不沿用已使用的批准或将静态修正写成通过。

## 拟执行命令与真实绑定

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

cwd：`D:/03_projects/AI-tools/aiw-dev`；原提案预计5–10分钟，并包含ADMIN总时限
230→420秒调整。用户决定不运行。绑定 `FD-002-000015-implementation-ready`、revision15、
digest `c4fc25a59e6f03a5521ba6e10cc3b5bcfe17eb5cbf01dbff119079fc2e6f60ae`、
原独立Tester `fd002-tester-20261005-15d090a2`。

## 修正、范围与风险

独立脚本准备从label排除嵌套表单控件文本、记录所有公开select候选及排除理由，
仍严格要求非空类别、真实缩小集合和刷新条件/条目保持。
截图准备滚动到每个真实视口，`captureBeyondViewport:false`且不使用超出视口的clip，
逐段持久化全部纵向内容、记录实际scroll/尺寸，并在finally恢复原滚动位置。
四角色、四宽度390/768/1280/1440、dense/long-ID/角色/失败截图矩阵和104/96库存保持。
宿主已静态审阅实际diff及脚本摘要；本轮不请求新预算，不作诊断、语法检查或运行验证。

只运行自有loopback应用、Chromium、TEMP直属SQLite/profile/PID，保存新 `evidence-r15-*`；
清理只针对自有进程和核对过的路径，无下载、外部服务、生产写入或最终构建。
系统commit/GPU/截图错误仍有风险；静态资源路径修正不能证明生产GPU/性能。
8项原未覆盖及Firefox单独延期保留104分母；Timeline全量读取成本与分支覆盖未测。

用户停止决定依据：当前对话中的明确指令“fd-002 的测试时间太长了，就不测了”。
记录引用 `denied:conversation:user-stop-fd002-tests-2026-10-05`。执行预算保持0。

Planner：`fd002-planner-20261005-96860f94`；human_approval：denied。
