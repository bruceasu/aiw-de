# FD-016 第一次审查修复报告

<!-- aiw-data: FD-016-implementation-local-r2.json -->

Worker `fd016-worker-20261013-0f3443b8`；来源 `FD-016-000013-changes-requested`；修复基准 `be29dfe`，最新实现 `d513357`。完整实现基线与范围见 [首轮报告](FD-016-implementation-local-r1.md)。

R1：App Server thread 与 turn 使用同一有效 sandbox，写角色的显式 read-only 不再被提升。

R2：Windows taskkill 非零始终未知；父 close 不能代替进程树确认。因 Node 不能证明原生 Windows 正常退出后的子操作已结束，两个 TypeScript Codex 模式在启动、注册和任务正文前拒绝 CODEX_WINDOWS_PROCESS_TREE_UNVERIFIED；系统 npm 原生解析代码保留，不宣传 Windows Runtime 可用。Unix/WSL 或 Windows Agents API 可按各自准入使用。该限制落实 FD Acceptance 11 的未知平台拒绝要求，生产平台隔离/认证归 FD-015。服务 EOF 后进程组若仍存在，执行一次有界终止，未知仍保留。

R3：消费 item/completed 与 turn/completed 的权威 agentMessage，按 id/phase 校验；final_answer 优先，旧无 phase 消息只在明确完成后使用；commentary/delta 不独立形成报告。消息数、完整正文及增量分别有界，重复 id 冲突拒绝。

另修复 Reviewer 提到的线程资源风险：在已确认 turn 终态后请求 thread/unsubscribe，按固定官方 schema 检查 unsubscribed/notLoaded/notSubscribed 回执，未知释放保留 resourceUncertain；删除本地 inbox 不冒充服务端释放。

实际运行一次 `node scripts/compile-worker-runtime.mjs`，退出 0：TypeScript/JS/Python 语法和 Go 离线编译，无类型检查/SDK/发行产物；临时依赖链接已移除。定向 `git -c core.whitespace=cr-at-eol diff --check be29dfe...HEAD` 通过，静态检查修复差异及真实交接。只读查询授权范围内的固定 ThreadUnsubscribeResponse schema 成功。没有运行测试、CLI、模型、类型检查、最终构建、部署或凭据操作。

当前 review count 1（changes-requested），三项可操作修复完成，提交新的独立审查；尚未通过、合并或归档。生产认证仍未执行；真实协议/并发/取消与 SDK 类型风险保持未验证。原范围 blocker 已解决，未新增未解决 Gate。
