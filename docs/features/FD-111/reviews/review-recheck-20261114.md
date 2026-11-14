# FD-111 凭据文件生命周期聚焦复核

- 静态代码结论：Pass，首轮唯一 P1 已关闭，限定复核未发现新增阻塞。
- **最终 Java 编译尚未完成；本结论不解除归档前补齐最终编译证据的要求。**
- 来源：`FD-111-000005-implementation-ready`，已精确认领；独立 Reviewer：`codex-review-fd111-20261114`。
- 工作树：`.wt/FD-111`；修复头 `816b190`，针对首轮 `8435e4b` 的 finding 及直接路径复核。首轮报告保留。

## 静态结果

ModelConnections.save 在事务提交成功后清理 prior 的旧文件；保存失败时清理本次 issued 文件。discardUnreferenced 先查询数据库当前引用，已采用的新文件不被失败/清理逻辑误删。旧文件清理异常发生在提交后、不会转入“新文件保存失败”的清理分支，并明确报告已保存但旧文件清理失败。

OpenAiLogin.callback 在 exchange 返回后保留 issued；回调取消、会话失效或保存失败时，finally 清理未采用文件。若已提交但随后报告清理失败，数据库引用保护仍保留当前有效文件。清理失败有明确状态，未假报登录成功。import 在写入新文件之前先校验 prior 归属/版本，减少无效调用产生文件。

新增用例源码覆盖成功替换退役旧引用、失败保存清理新文件、当前有效引用不清理，未运行。修复未改变原刷新 CAS/未知结果不重发，也未扩展异机凭据或租约范围。

## 证据与限制

实际执行 aiw fd show/claim、定向 git diff/stat 和 Get-Content，随后写报告、更新 Verification 并发送 Reviewer 静态结论。未运行测试、编译、脚本、迁移、服务、网络、真实 API/模型或安装，未派子 Agent。

Worker 在本次实质修复后用低内存 192 MiB/SerialGC 尝试编译，仍因 JVM Native malloc 失败中止，无最终源码编译成功证据。用户已选择先继续静态整理、稍后编译；因此本次只确认代码 finding 关闭，**不能将最终 HEAD 记为已编译通过，也不能据此直接归档交付**。最终编译需后续补齐，原真实 OAuth/文件保护/锁的运行证据限制保持。

## bootstrap 收拢补充复核：静态 Pass

- 来源 `FD-111-000007-review-requested` 已由同一 Reviewer 精确认领；限定差异 `63a1126 → d365481`，不重审已关闭的文件清理 P1。
- Java 新连接以空 client 交给 Python authorize，由 Python 选择 bootstrap；旧连接继续核对回调 client 与既有签发 ID 相等。新连接缺少 client 或仍返回 bootstrap 时，Python exchange 在 token HTTP 前拒绝；真实签发 client 仍进入原 nonce/client/subject/JWT 校验，未增加绕过条件。
- 新增源码用例检查 Python authorize 选择 bootstrap，以及 bootstrap exchange 不调用 request_json；未执行。限定静态复核未发现阻塞。
- 实际只执行 aiw fd show/claim、定向 git diff/stat 和 Get-Content，再追加结论、更新 Verification、发送事件；未运行测试、编译、脚本、服务、网络、安装或子 Agent。**最终 Java/Python 编译证据仍待补齐，静态 Pass 不解除归档前门槛；不记作编译/运行通过。**
