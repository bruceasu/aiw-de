# FD-001 独立测试运行授权 v2

<!-- aiw-data: FD-001-test-authorization-r4-v2.json -->

**Decision:** approved

**Implementation event:** FD-001-000004-implementation-ready

**FD revision:** 4

**FD digest:** 2afb5c00dbfab6f9e7060aa8b71e08188cd4de190d3024e33271e113a4c849e5

**Tester session:** fd001-tester-20261004-7303009b

**Command:** C:/green/python312/python.exe tests/fd001/runtime.py

**Working directory:** D:/03_projects/AI-tools/aiw-dev

**Scope:** 首轮相同本地 Chromium 黑盒命令的一次基础设施修正重试，覆盖四角色入口、局部轮询、阅读/编辑/重置保护、pending 窗口、身份/状态/动效、四宽度及 cookie 失效；未覆盖行为必须明确记录。

**Expected duration:** 100-150 秒。

**Side effects:** 四个依次启动的 loopback 临时 SQLite JVM 与 Chrome profile；只写 tests/fd001/evidence-r4 的日志/截图/JSON，保留 attempt1；终止自行启动的进程，清理 profile 前校验临时目录边界。无生产数据、下载、外部服务、发布包或部署。

**Basis:** human-approved

**Human approval:** approved:conversation:call_FNlbt5rJg8Skp20kkHAKYv6m

**Risk review:** 首轮在登录/数据库清理环节失败，业务断言执行数为 0，不能算产品失败或通过。原始证据已存 attempt1。Planner 静态审阅了 SQLite 显式 close、原生表单 requestSubmit、URL/readyState 有界等待、仅针对跳转 context-destroyed 的瞬态处理、导航诊断、CDP 迟到响应处理和清理异常保留。依据用户既有本地验证授权及 validation.md 的相关修正后一次重试规则，批准本命令只运行一次；若再次失败，保留结果并停止等价重跑。

**Planner identity:** fd001-planner-host-20261004

**Decision time:** 2026-10-04T14:05:56+00:00

## 授权边界

保持当前 Tester claim、FD revision/digest 和 session；不接管或重新认领。不会重跑 Maven、下载 Firefox、扩展集成/全库范围。Firefox 延期仍适用。报告分别记录首轮基础设施失败、第二次实际业务结果以及未运行场景，不将历史失败改写为通过。

