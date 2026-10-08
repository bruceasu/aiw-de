# FD-002 Planner 测试授权：r9 v1（已批准）

<!-- aiw-data: FD-002-test-authorization-r9-v1.json -->

## 决定

**approved**，用户明确授权一次复验及一次相关修正重试。此前三次预算均已使用：最新 36/104（34.62%），
0 个已执行业务断言失败，68 项未运行；第三次仍被章节关联定位器中断。
AGENTS.md/core validation 的一次修正重试已用完，fd-workflow 要求新授权
绑定当前真实事件。本次新增预算已获明确的人类答复，引用
`approved:conversation:call_vim7bxHX4FWji20Z2YrTI0DF`。

## 已准备的精确命令

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

cwd：`D:/03_projects/AI-tools/aiw-dev`；预计 3–5 分钟。
[独立 Tester 提案](FD-002-test-proposal-r9-v1.md)及
[Worker 恢复报告](FD-002-implementation-recovery-r8.md)是本次可审阅依据。
宿主已静态审阅实际章节 helper 和启动器/夹具 diff，修正后的语义章节 helper
逐一关联候选、排除 URL Run 身份标题，保持原业务断言；
界面修正包括图视口滚动位置保存和长阶段折行，离线 compile-only 已通过。

新增一次复验，并仅在发生相关源码或脚本修正时允许一次相同命令重试。
运行预算为 1，条件修正重试预算为 1；不得直接重复失败命令或追加诊断。
后续新事件仍必须重新核对 receipt 并写对应授权，不得沿用旧摘要或伪造证据。

## 绑定

事件：`FD-002-000009-implementation-ready`；revision：9；digest：
`baf9278f6c5241297e9b992a05ac3487c99989895404a7cae2dd3ccb6f8b75b1`。
Tester：`fd002-tester-20261005-15d090a2`；Worker Session 与 Tester 不同。
启动器在创建进程前检查最新事件、领取人、FD 内容、精确授权命令和工作目录。
最终审阅脚本 SHA256 记录在同名 JSON 的 `script_sha256`；该记录是静态证据，
不代表新测试已运行。

## 范围、副作用与风险

同一 104 项场景库存，计划 96 项，四角色及 Chromium 四宽度。
96/104（92.31%）仅为计划上限，不是实际覆盖率。Firefox 两项按已获用户决定
延期，其他未覆盖仍留分母；分支覆盖未测，不额外安装或运行覆盖工具。

仅启动自有 loopback 临时应用、Chrome 和临时 SQLite/profile；新增唯一
`tests/fd002/evidence-r9-*` 目录保存脚本快照、结果、截图和日志。
临时 JVM 限制 heap 64m/512m、code cache 96m；SQLite 连接显式关闭。
清理限于自有 PID 和已经核对的临时路径。没有下载、外部服务、生产数据写入、
权限提升或最终产物构建；保留前三次全部证据，不覆盖失败记录。

本轮尚无新运行结果，独立审查须等待测试报告及 PM 接受决定。
Planner：`fd002-planner-20261005-96860f94`；
`human_approval: approved:conversation:call_vim7bxHX4FWji20Z2YrTI0DF`；
批准时间：`2026-10-04T22:55:59+00:00`。原 pending 草案保存在提交 `d392219`。
