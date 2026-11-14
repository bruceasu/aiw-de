# FD-112: Simplify local admin login and Worker enrollment

**Status:** Complete
**Revision:** 5
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 23, 24. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

本机限定模式自动管理员，开放远程时必须登录；统一首期管理员角色。本机 Worker 自动登记，外部使用管理员接入凭据，重连保留身份，撤销阻止连接和接单。

Dependencies: none.

- `WebConsoleSecurityService`：仅显式回环绑定、未启用远程/转发的部署自动建立管理员会话。Host、Origin、实际连接地址和转发头都参与本机判断。远程模式即使从本机访问也需要密码，实际远程连接必须 HTTPS。保留 CSRF 和危险操作确认，首期只有管理员。
- `WorkerAccess`：工厂进程获得本次 Plane 生命周期的独立凭据；外部凭据绑定稳定 Worker ID，V49 只存摘要及管理元数据。停止接单允许原执行回报；撤销拒绝后续协议请求，不宣称远端进程已停止。
- MVC 按规范化路径鉴权；普通旧管理 API 默认需要管理员会话，Worker 只能使用专用协议。`AuthorityBoundary` 将认证身份与注册、接单和租约操作中的 Worker ID 核对；模型连接仍保留租约及来源证明。
- 工厂页面提供创建（令牌仅显示一次）、刷新、停止接单、恢复和撤销。Python 客户端远程必须 HTTPS，不跟随可能携带凭据的重定向。外部 Worker 自行管理模型认证。

部署：默认 `AIW_BIND_ADDRESS=127.0.0.1` 无须控制台密码。对外绑定或反向代理需设置 `AIW_REMOTE_ACCESS=true` 和 `AIW_WEB_CONSOLE_PASSWORD`，并配置 HTTPS。工厂直连默认跟随 Plane 的 TLS 设置；证书不能覆盖回环地址时设置 `aidev.agent-factory.control-plane-url` 为可校验证书的地址，使用系统 CA 信任，不跳过验证。旧 Plane 全局 `aidev.worker-protocol.token` 已移除；外部 Worker 的 `AIW_WORKER_TOKEN` 改为页面创建的该 Worker 专属令牌。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

## Work items

- [x] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [x] 1.2 实施已确认行为，删除因此不再需要的代码。
- [x] 1.3 编译受影响源码、自查差异并提交独立审查。

## Acceptance

本机与远程信任模式明确；不能通过来源地址绕过远程登录；身份不因进程重启重复创建；撤销不暗示远端进程已停止。

## Verification

- 独立聚焦复核 `1cfa8b0`（来源 `FD-112-000004-implementation-ready`）：Pass，首轮 P1/P2 均关闭。配置登记在查写前核对凭据绑定 ID；专用协议委托前置机器鉴权，保留服务 ID/租约检查，自身 drain/offline 不再要求浏览器会话。结论已追加到 [同一审查报告](reviews/review-20261114.md)。Reviewer 未运行测试、编译或运行时验证，原证据限制保持。

- 独立静态审查（`4b1c1d1 → a6ef057`，来源 `FD-112-000002-review-requested`）：Changes requested，1 项 P1——ExecutionConfigurations.registerWorker 未核对当前凭据绑定 ID，A 凭据可用 URL/body 的 B 身份写 B 配置元数据；1 项 P2——Worker drain/offline 通过机器鉴权后又被第二层路由要求 human session/CSRF。详见 [审查报告](reviews/review-20261114.md)，两项沿用同轮 `FD-112-000003-changes-requested`。限定范围未发现其他阻塞；Reviewer 未运行测试、编译、迁移或运行时验证。

独立审查：鉴权、权限、持久化及跨模块变更，提交独立 Reviewer 后根据结论继续。

- JDK25 离线 `mvn -q -o -s D:/03_projects/AI-tools/aiw-de/.ai/fd/FD-081/offline-maven-settings.xml -Dmaven.repo.local=D:/.m2/repository -DskipTests test-compile` 通过。首次发现两个旧全局令牌前置判断，移除后一次重编译通过。
- `python -m py_compile workers/process-python/process_worker.py`、两个修改脚本的 `node --check`、`git diff --check` 通过。
- 新增 WorkerAccessTest，调整现有凭据及协议测试源；仅编译，未运行测试、服务、迁移、浏览器、真实认证或模型调用。
- 待运行验收：本机自动管理员、远程登录/TLS、一次显示的令牌、跨 ID 拒绝、停止接单与撤销。V49 未执行；旧全局令牌不再兼容，需创建新接入凭据。

修复首轮审查：配置登记入口增加同一 `AuthorityBoundary.requireWorker` 核对；专用 Worker 路径统一委托前置机器鉴权，删除重复动作清单，使自身 drain/offline 不再误入浏览器会话检查。新增配置跨 ID 拒绝用例源码，仅编译。修复后重新编译并提交有界复审。

## Sources

- docs/FUNCTION-INVENTORY.md, 23, 24.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。

**Completed:** 2026-11-14
