# FD-002 Revision 12：条件重试命令提案 v2

<!-- aiw-data: FD-002-test-proposal-r12-v2.json -->

## 绑定及授权边界

真实事件 `FD-002-000012-implementation-ready`，revision **12**，LF正规化FD摘要
`8a272107c44241a585978e51becdf1d769f5d17736f122b276fea088bb6178b9`，
独立Tester session `fd002-tester-20261005-15d090a2`。FD与receipt保持冻结。
人类授权 `approved:conversation:call_91WOuZ4AVFLiYni2IuZLDKaE` 的initial1已使用；
剩余条件retry1须本次相关修正、PM静态审阅及明确通知。此提案本身不构成执行权，不申请额外运行。
启动器要求正式 `FD-002-test-authorization-r12-v2.json` 为approved且所有绑定匹配。

## 真实失败证据与最小修正

[本轮报告](FD-002-test-report-r12-attempt1.md) 保留3/104通过、0业务断言失败、101未执行、exit1；
原始目录 `tests/fd002/evidence-r12-20261005-083112-13176`、六轮历史快照/报告均不改写。
ADMIN/browser.log明确 `CDP timeout Runtime.evaluate`；diagnostics最后到 `/ui/login`，
ADMIN/app.log正常迁移16/Started，没有现有hs_err/replay或Node native fatal文本；
ADMIN/chrome.log记录08:31:32.322 GPU进程 `exit_code=34`。其他三角色各1项权限断言通过。
现有证据不足以确定GPU退出与evaluate超时的因果顺序，不能认定产品登录错误或断言通过。

本次仅做以下测试环境和诊断修正：

- Chrome新增 `--disable-gpu`；原启动参数没有此项。
- browser复用 `/json/list` 中唯一type=page且url=about:blank的初始页面，去掉 `/json/new` 创建第二个blank页。
  保持原5秒target查找超时；没有唯一候选或其CDP身份缺失时明确阻断，不另建tab兜底。
- CDP超时记录当次method及operation：导航、等待、安装公开观察器、登录提交/等待、场景、截图。
  不保存evaluate源码、表单密码或完整CDP参数。
- runtime授权入口换为真实revision12的v2；事件/session/digest不变。

不改变登录提交方式、导航等待算法、5秒target查找/12000ms CDP超时、230/75秒角色超时、JVM/V8限制、业务断言、
fixture、104/96库存、所有宽度及截图矩阵。没有运行诊断、语法检查、浏览器或复验。

## 精确命令与成本

```text
C:/green/python312/python.exe tests/fd002/runtime.py
```

cwd：`D:/03_projects/AI-tools/aiw-dev`。无模式或附加参数，一次依次启动ADMIN、VIEWER、OPERATOR、APPROVER，
包含全部96个计划断言（仅实际到达者计执行）及完整dense/long-ID四宽度、角色、失败截图矩阵。
预计 **4–8分钟**，计入全纵向分段截图成本；原阶段超时不扩大，不自动重跑。
截图按公开layout metrics同宽覆盖全纵向；每段≤2000 CSS px高、≤4000000实际PNG像素；
首段name.png、后续name-part-002.png，逐段manifest保存clip、文件、实际像素和状态。
图片存在、造数完成或页面加载不作为业务断言通过。

副作用限独立 `tests/fd002/evidence-r12-<时间>-<PID>`、直属安全TEMP SQLite/profile、
本命令自有进程/loopback端口。终止仅自有PID，清理校验TEMP父目录与fd002-tester前缀；
日志、数据库、脚本快照、截图/manifest保留，活进程或清理失败时保留临时路径。
无生产数据库写入、下载、外部服务、Maven/build或发布产物。

## 覆盖和风险

104场景全适用，计划96（92.31%仅计划上界），本次重试尚未运行，不能采用历史通过作为实际覆盖。
分支覆盖 **not measured**，没有字节码仪器。
8项仍未覆盖：no-js、firefox-graph、firefox-refresh、lease-auto-runtime、lease-pause-revocation、
concurrent-posts、timeline-tz-browser、missing-time。
Firefox仅本FD已独立获延期，其2项仍保留分母，见 [Firefox决定](FD-002-firefox-decision-r6.md)。
禁用测试GPU不验证生产GPU/性能，也不保证系统commit充足；evaluate阻断仍可能重现。
若条件重试仍失败，停止运行并保留真实结果。
