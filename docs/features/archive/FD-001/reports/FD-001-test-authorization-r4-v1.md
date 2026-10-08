# FD-001 独立测试运行授权 v1

<!-- aiw-data: FD-001-test-authorization-r4-v1.json -->

**Decision:** approved

**Implementation event:** FD-001-000004-implementation-ready

**FD revision:** 4

**FD digest:** 2afb5c00dbfab6f9e7060aa8b71e08188cd4de190d3024e33271e113a4c849e5

**Tester session:** fd001-tester-20261004-7303009b

**Command:** C:/green/python312/python.exe tests/fd001/runtime.py

**Working directory:** D:/03_projects/AI-tools/aiw-dev

**Scope:** FD-001 本机 Chromium 黑盒；四角色显示/New Run/Worker 入口、5/8 秒更新、编辑/重置、阅读保护、旧 pending、状态/身份/动效/四尺寸、真实 cookie 失效；未覆盖行为须显式记录。只运行本命令一次，不复跑 Maven。

**Expected duration:** 100–150 秒。

**Side effects:** 依次启动四个绑定 127.0.0.1 的临时 SQLite JVM、临时 Chrome profile；只写 tests/fd001/evidence-r4 的日志/截图/JSON；finally 终止自行启动的进程，并对 profile 绝对路径及临时目录前缀核验后尝试删除。没有生产 DB 写入、下载、外部访问、发布包或权限提升。

**Basis:** human-approved

**Human approval:** approved:conversation:call_FNlbt5rJg8Skp20kkHAKYv6m

**Risk review:** Planner 已静态审阅 tests/fd001/runtime.py 与 browser.mjs。修正了 seed 的 source_task 唯一性/decision_note/decided_by/外键/租约账，确认 loopback 与专用临时路径、后台进程和清理边界、CDP 超时/激活/焦点模拟、公开 data-refresh-label、重置后结束输入焦点、同一 DOM 抽取方式，以及系统 reduce 不受 local off 混淆。固定测试密码仅属于临时 fixture。没有读取功能实现源码来替 Tester 推导业务断言。HTTP 请求限于临时本机应用与 Chrome debugger。Firefox 用户另行允许延期，未覆盖项不能算 passed；不增加默认运行重试预算。

**Planner identity:** fd001-planner-host-20261004

**Decision time:** 2026-10-04T14:00:01+00:00

## 中断恢复核对

原独立 Tester 已通过 followup 恢复同一会话，明确此前只准备/修正脚本、未运行；真实回执仍归该 session 所有，FD 文件 SHA-256 与 revision 4 回执一致，Reviewer outcome 数为 0。此授权未重置审查计数，也未替换已有 owner。用户再次要求继续 FD-001 test/review，先前明确的本地验证授权及 Firefox 延期决定仍有效。

报告使用中文 Markdown + 同名 JSON，必须引用本授权与实际命令，并区分执行通过、部分覆盖、未覆盖/未运行。若进程失败，保存所有结果，不自动重跑，不把失败断言接受为通过。
