# FD-001 Planner 修正后单次复验授权：Revision 11，v2

<!-- aiw-data: FD-001-test-authorization-r11-v2.json -->

## 决策

**approved**，使用本轮唯一一次修正后复验预算。用户明确请求
`$fd-workflow auto test and review for FD-001, FD-002`；核心 validation 允许相关
修正后一次聚焦复验。初验的 4 个业务断言中 2 通过、2 未观察到实际刷新而失败，
结果和初验脚本保留在 `tests/fd001/evidence-review1/initial/`，不改写为通过。

## 精确命令及绑定

```text
C:/green/python312/python.exe tests/fd001/runtime.py review-fixes
```

工作目录 `D:/03_projects/AI-tools/aiw-dev`，预计 1–2 分钟。
事件仍为 `FD-001-000011-implementation-ready`，FD revision 11，摘要
`bf8bbae3a950348233a32faf39393bbd4c8cd7e07aeff14464833dbd0fdb6377`，
Tester `fd001-tester-20261004-7303009b`，Planner `fd001-planner-20261004-86fbb56c`。
执行前核对当前领取、FD 内容和本授权，不新增命令或场景。

## 实际修正与静态证据

已审阅 `runtime.py` 和 `review-fixes.mjs` 的实际 diff，以及首次诊断与日志。
两个固定 1200ms 等待改为最多 12 秒观察计数变化或未保护区节点替换；这覆盖
公开异步请求的 10 秒超时，并保存耗时、刷新标签、快照和超时状态。展开、焦点、
选区端点/offset/全文/中间节点以及未保护区实际替换的断言全部保留。
当前仅有验证时序假设，不预先宣称产品正确或初验失败已解决。

本次临时 JVM 使用 `-Xms64m -Xmx512m -XX:ReservedCodeCacheSize=96m`，
ErrorFile/ReplayDataFile 定向本次证据目录，避免与另一 FD 的默认 JVM 竞争。
两 FD 的复验按 FD-001、FD-002 顺序执行。未改变产品、FD 或测试范围。

副作用仍仅本地随机 loopback 端口、专用临时 SQLite/Chrome、自己的进程；
无 Maven、下载、外部服务或生产数据。本次输出改为 `tests/fd001/evidence-review2/`，
保留首次证据与复验脚本快照。若仍失败则报告，不自动再次运行。

批准引用 `approved:conversation:20261005-auto-test-review-FD001-FD002`；
记录时间 `2026-10-04T22:26:33+00:00`。Firefox 仍按 FD-001 既有用户决定延期，
分支覆盖和旧矩阵未在本次重测。
