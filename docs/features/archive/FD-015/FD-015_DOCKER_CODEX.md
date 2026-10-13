# FD-015: Docker 隔离与 Codex 生产禁存准入

**Status:** Deferred
**Revision:** 3
**Priority:** Medium
**Evidence policy:** Dual

## Problem

FD-014 按用户决定收敛为本地 Go Runtime 与双认证支持。Docker 隔离、生产禁存准入及 Linux 运行证据转入本 FD，当前仅登记后续范围，不实施或部署。

## Options and decision

选择 Docker 作为后续隔离机制；普通临时目录、结束删除和 ephemeral 标志不足以证明全通道禁存。保留原生产候选要求，当前不宣称满足。

## Solution

后续核定 Codex 可写状态的不可交换内存边界，以及主机 swap/core dump/诊断、工具子进程、凭据刷新和服务端留存政策证据。缺少对应证据时，在生产注册或发送正文前拒绝启动。

%% NEEDS_INPUT: 本 FD 恢复设计时核定 Docker 启动入口、可写内存与主机证据契约；目前用户明确暂缓，无需现在回答。

## Scope

承接 FD-014 原 1.6.2、1.6.3、1.6.4.3、1.6.5、1.6.6。包含两种认证的生产政策、Docker/主机隔离、Linux 生命周期和同政策 Exec 回滚。排除实际发布/部署授权与其他语言实现。

## Work items

- [ ] 1.1 核定 Docker/主机隔离与证据契约。Size: S; difficulty: Pending security/platform design; dependencies: FD-014 固定版本研究。完成依据：启动入口、内存、swap、core dump、诊断、工具进程和凭据更新边界；对应原 1.6.2。
- [ ] 1.2 实现生产启动前和会话前证据核验。Size: S; difficulty: Medium after 1.1; depends on 1.1。完成依据：配置/版本/认证/隔离证据绑定，未知或变更拒绝，无静默 fallback；对应原 1.6.3。
- [ ] 1.3 核定 Docker 双认证与服务端留存政策。Size: S; difficulty: Medium after 1.1; depends on 1.1, 1.2。完成依据：API Key/ChatGPT 实际身份、刷新机制和各自政策，不能跨认证复用结论；对应原 1.6.4.3。
- [ ] 1.4 收集 Linux 生命周期、重启和资源终态证据。Size: S; difficulty: Medium; depends on 1.2。经明确授权收集并发、取消/断连、lease loss、父进程终止、停止/重启证据；未知终态不释放占用资源；对应原 1.6.5。
- [ ] 1.5 收集全通道禁存与同政策 Exec 回滚证据。Size: S; difficulty: Medium after 1.1; depends on 1.2, 1.3, 1.4。经明确授权覆盖运行写入监测和结束扫描、各认证服务端政策、Exec 回滚；对应原 1.6.6。

## Acceptance

1. 隔离与证据契约经设计确认，缺少证据拒绝生产启动。
2. 本机全部核定存储通道和服务端政策满足项目禁存要求；Docker 名称或 ephemeral 不充当合格证据。
3. Linux 生命周期、资源终态与 Exec 回滚有实际证据；Windows 历史结果和本地编译不能代替。
4. 独立审查通过后才可声明生产候选就绪；不自动发布或部署。

## Verification

当前未运行 Docker、真实认证、模型调用、Linux 生命周期或禁存验证。后续设计与具体运行命令需另行核定；本次登记不授权执行。编译和静态审查采用仓库默认预算。

## Sources

- FD-014_CODEX_RUNTIME_GO.md 及其后续归档，ISSUE-00004。
- docs/worker-codex-no-store-design.md。
- FD-014-no-store-source-review-linux-r1 及生产准入 blocker pairs（随 FD-014 归档）。
- 用户 2026-10-13 明确授权拆分本地交付与后续 Docker/生产工作。

**Closed:** 2026-10-13
**Disposition reason:** 用户要求当前完成 FD-014 本地交付；Docker、生产禁存准入与 Linux 运行证据登记为后续范围，本次不实施或部署。
