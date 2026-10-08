# FD-002 Planner 修正后单次复验授权：Revision 6，v2

<!-- aiw-data: FD-002-test-authorization-r6-v2.json -->

## 决策

**approved**，使用本轮唯一一次相关修正后复验预算。用户明确请求
`$fd-workflow auto test and review for FD-001, FD-002`；核心 validation 允许相关
修正后一次聚焦复验。首次运行被临时 ADMIN JVM 的 native malloc 失败中断，
24 项通过、0 项已执行业务失败、80 项未运行；blocked 不等于验收通过。

## 精确命令和版本

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

工作目录 `D:/03_projects/AI-tools/aiw-dev`，预计 3–5 分钟。
绑定事件 `FD-002-000006-implementation-ready`、FD revision 6、摘要
`7869eb2108ad9ac8811e65ea339ecde7d6c08d7394add7230ab85a2e5a178c8a`、
Tester `fd002-tester-20261005-15d090a2`、Planner `fd002-planner-20261005-96860f94`。
启动器只接受本 v2 授权并检查精确命令、cwd、当前事件/FD 摘要和领取人。

## 已审阅的实际修正

已读取首次 JVM 崩溃诊断，并审阅 `runtime.py` 与 `fixture.py` 的实际 diff。
测试 JVM 使用 `-Xms64m -Xmx512m -XX:ReservedCodeCacheSize=96m`，
ErrorFile/ReplayDataFile 定向当前角色证据目录；没有改变产品或业务断言。
本次等 FD-001 复验结束后串行执行，降低测试应用竞争。

迁移检查、seed 和 mutate 通过 `contextlib.closing` 显式关闭 SQLite 连接，
同时保留夹具事务提交/回滚。原 `with connection` 只管理事务，文件仍可能被
Python 连接占用；修正只作用于专用临时 SQLite，避免清理时文件占用。

所有 104 项库存和原 96 项计划断言保持不变。8 项缺口、分支覆盖 unavailable
以及 Timeline 全量读取成本风险不变，不能把计划覆盖或首轮 24 通过写成全验收。
Firefox 尚未获延期，本次仍只运行既定 Chromium 命令，之后按单独决定处理。

## 副作用和证据

限自己的四角色临时应用、loopback 端口、SQLite、Chrome profile/PID，
无 Maven、依赖下载、外部服务、生产数据或发行物。
每次生成唯一 `tests/fd002/evidence-r6-<时间>-<PID>/`，保存脚本快照、日志、
截图、数据库和逐断言结果，不覆盖首次证据。

首次证据为 `tests/fd002/evidence-r6-20261005-072411-32980/`，首次 Dual 报告为
`FD-002-test-report-r6-attempt1.md/.json`。首轮 JVM 将诊断写到根目录的异常
副作用已记录；PM 校验归属后将两文件原样移入其 ADMIN 证据目录。

复验仅一次；失败时报告实际结果，不自动继续运行。
批准引用 `approved:conversation:20261005-auto-test-review-FD001-FD002`，
记录时间 `2026-10-04T22:28:10+00:00`。
