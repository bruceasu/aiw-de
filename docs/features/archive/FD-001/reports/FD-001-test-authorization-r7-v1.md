# FD-001 独立测试追加授权 r7 v1

<!-- aiw-data: FD-001-test-authorization-r7-v1.json -->

**Decision:** approved

**Implementation event:** FD-001-000007-implementation-ready

**FD revision:** 7

**FD digest:** 4d98e2a15e417e8c8986faa809dfe4aa4b5666a38b9b6aa3f8e8c926d92dc125

**Tester session:** fd001-tester-20261004-7303009b

**Command:** C:/green/python312/python.exe tests/fd001/runtime.py diagnose

**Working directory:** D:/03_projects/AI-tools/aiw-dev

**Scope:** 只运行一次 ADMIN 本地导航诊断，记录 HTTP 登录响应与新建 CDP 页面目标的导航/网络证据；无 seed、表单提交或业务矩阵。

**Expected duration:** 10-30 秒。

**Side effects:** 仅 loopback 临时 SQLite JVM、专用 Chrome profile 和 tests/fd001/evidence-r7 证据；退出终止自行启动的进程，清理路径须校验临时目录边界。原 r4 证据不覆盖；无外部服务、下载、生产数据或打包。

**Basis:** human-approved

**Human approval:** approved:conversation:call_mIrsO74rlMXpNaY6ZFVXER0L

**Risk review:** Planner 已静态阅读当前 Python 启动器、diagnose.mjs 的响应/事件记录与新页面目标。用户明确选择「授权一次诊断及条件复验」，补充前次已耗尽的运行预算。本记录只允许诊断一次，业务矩阵另有 v2 条件授权。任一步失败不自动追加等价重跑，不把基础设施失败或未运行场景描述为业务通过。

**Planner identity:** fd001-planner-host-20261004

**Decision time:** 2026-10-04T14:11:49+00:00

