# FD-002 Planner 条件重试授权：r12 v2

<!-- aiw-data: FD-002-test-authorization-r12-v2.json -->

## 决定及剩余预算

**approved / human-approved**。人类依据：
`approved:conversation:call_91WOuZ4AVFLiYni2IuZLDKaE`。
同一授权的初验已执行：3通过、0业务失败、101未运行，exit1，覆盖2.88%。
[初验报告](FD-002-test-report-r12-attempt1.md)与全部原始快照保持。
此版本只使用剩余的一次相关修正重试，初验预算0、条件重试预算1；不新增人类预算，
不自动复跑，不授权其他诊断、参数模式、语法检查或更大验证范围。

## 实际修正与静态审阅

初验 Chrome 日志记录 GPU 进程 exit34，CDP 在登录页 evaluate 超时，尚不能确认唯一因果。
宿主完成一次实际 diff/SHA256 审阅：测试 Chrome 新增一处 `--disable-gpu`，
browser 复用 `/json/list` 中唯一的初始 page/about:blank，不另建第二个 tab；
没有唯一候选则诚实阻断。CDP 超时新增操作阶段，启动器切换当前 v2 授权入口。
原登录操作、导航等待、5秒目标查找、12秒CDP、230/75秒角色超时、四角色、
四宽度、截图矩阵、严格业务断言、夹具及104/96库存保持。
四脚本精确摘要见同名 JSON；[独立提案](FD-002-test-proposal-r12-v2.md)作为入口。

## 真实事件及精确命令

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

cwd：`D:/03_projects/AI-tools/aiw-dev`；预计4–8分钟，包含完整长页分段。
绑定 `FD-002-000012-implementation-ready`、revision12、
digest `8a272107c44241a585978e51becdf1d769f5d17736f122b276fea088bb6178b9`、
原 Tester `fd002-tester-20261005-15d090a2`，须核对最新已领取 receipt 和 FD 摘要。
仅在正式记录本授权与宿主明确通知后，同一命令执行一次；结果失败时停止运行并保留证据。

## 范围、证据和限制

104项库存，96项计划，8项未覆盖保留分母；92.31%仅为计划上限。
分支覆盖not measured，Firefox两项既有人类延期保持。当前只有初验3/104真实通过，
此前r9的74/104及4个失败仅作为历史，不能累加或判定修复通过。
只用自有loopback应用、Chromium、TEMP直属SQLite/profile/PID，
保存新的 `tests/fd002/evidence-r12-*`，清理仅限自有进程及已核对临时路径。
无下载、外部服务、生产写入或最终构建；不会覆盖前六次日志、数据库副本和截图。
禁用测试GPU和减少冗余target未运行复验，不能证明生产GPU性能或系统commit充足；
系统资源不足仍可能中断，Timeline全量读取的生产成本仍未测。

Planner：`fd002-planner-20261005-96860f94`。
