# FD-002 Planner 测试授权：r9 v2（已批准的条件修正重试）

<!-- aiw-data: FD-002-test-authorization-r9-v2.json -->

## 决定与剩余预算

**approved**。本记录使用用户已经批准的一次相关修正重试：
`approved:conversation:call_vim7bxHX4FWji20Z2YrTI0DF`。
[r9-v1](FD-002-test-authorization-r9-v1.md) 的初验已使用，实际 exit 1，3/104
通过、101 未运行，完整 [attempt1 报告](FD-002-test-report-r9-attempt1.md)
及原始证据保持。这是同一批准中的条件重试，不增加运行总预算。

初验新增预算 0，剩余修正重试预算 1；仅执行下面精确命令一次。结束即耗尽本次
人类批准的两次额度，不自动追加诊断、重启或其他测试。

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

cwd：`D:/03_projects/AI-tools/aiw-dev`；预计 3–5 分钟。

## 具体修正及静态审阅

本次已有 ADMIN/app.log 与 hs_err_pid22512.log 记录：C2 CompilerThread0 的
native malloc 无法分配 136816 bytes；G1 使用 16 CPU 相关工作线程；heap 使用
42308 KB，系统可用 pagefile 仅 1 MB，进程 private bytes 293 MB。
该证据支持系统提交资源不足的判断，不认定登录算法或产品页面发生业务失败。

宿主已审阅实际 `runtime.py` diff：仅将授权入口指向本 v2，并为测试 JVM 添加
`-XX:ActiveProcessorCount=2`、`-XX:TieredStopAtLevel=1`、`-XX:+UseSerialGC`。
heap 64/512 MB、code cache 96 MB、原超时及 104/96 场景和业务断言保持。
此修正减少 C2/G1 相关测试资源需求；系统资源不足仍可能导致运行失败，
成功也不代表生产性能已验证。

没有修改产品或 FD，没有更改系统 pagefile 或处理其他进程。四脚本最终静态
SHA256 见同名 JSON；v1 执行版本由原始证据快照保留，不覆盖失败记录。

## 绑定与副作用

事件 `FD-002-000009-implementation-ready`；revision 9；digest
`baf9278f6c5241297e9b992a05ac3487c99989895404a7cae2dd3ccb6f8b75b1`；
Tester `fd002-tester-20261005-15d090a2`。启动器继续核对当前最新 receipt、
领取人、FD 摘要、批准命令和 cwd。

仍只运行四角色隔离的本地应用、Chromium、临时 SQLite/profile，写唯一
`tests/fd002/evidence-r9-*` 目录；清理仅自有 PID 及已校验的 TEMP 直属路径。
无下载、外部服务、生产数据写入、提权或最终构建。Firefox 已延期，原 8 项
未覆盖继续留在库存分母，分支覆盖未测。r9 当前实际覆盖仍为 2.88%，
计划 92.31% 不等于新运行结果。

Planner `fd002-planner-20261005-96860f94`；审阅放行时间
`2026-10-04T23:01:56+00:00`。新的测试报告和 PM 决定须依据这一次真实结果。
