# FD-083: Automatically protect local model credential keys and reset lost logins

**Status:** Complete
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

本机用户不应为了创建 ChatGPT 连接先手工管理环境变量密钥。用户确认采用 Plane 自动保护密钥，并允许密钥丢失后删除旧登录重新授权。

## Options and decision

Windows 本机默认使用当前账户 DPAPI 保护随机 AES 密钥，保护后的密钥文件位于当前用户 LocalAppData，按数据库安装标识隔离。AIW_MODEL_CREDENTIAL_KEY 保留为明确配置的高级覆盖；无效覆盖不静默回退。其他平台本轮仍使用显式密钥，不能将普通文件宣称为操作系统凭据保护。

## Solution

新增本机密钥存储与数据库密钥代次元数据。首次无旧密文时自动创建；密钥文件丢失/损坏时拒绝自动覆盖旧密文，界面提供明确的本机登录存储重置。重置要求当前配置管理员、CSRF、代次核对和显式清除确认，仅在没有其他 Owner 的活动凭据时可执行；撤销旧连接版本、清除密文和 Worker proof，再生成新代次。旧 Plan/Task 绑定保留，不能因此换账户。未知远端撤销不声称成功。

## Scope

Windows DPAPI、本机 AES 密钥元数据、新增重置 API、工厂登录设置与文档。保留原 OAuth/令牌刷新/Worker 租约边界。不执行真实登录、真实凭据删除、服务启动、系统安全设置修改或依赖下载。使用现有 JDK/.NET，不引入依赖。

## Work items

- [x] 1.1 实现本机 DPAPI 密钥保护、V32 安装代次及显式配置优先规则。
- [x] 1.2 增加 Owner 安全重置、旧代次失效和明确前端恢复入口。
- [x] 1.3 补充有限回归用例与文档，完成 compile-only 和自检；独立审查与归档由正式回执单独把关。

## Acceptance

1. Windows 全新安装无环境变量时可直接创建 ChatGPT 登录，重启复用同一密钥。
2. 密钥不出现在数据库明文、命令参数、子进程环境、API、安全投影或日志中；仅经受限辅助进程 stdin/stdout 处理，磁盘只保存 DPAPI 保护结果。
3. 已有不可解密密文时不静默生成替代密钥；明确重置后旧版本/在途刷新不能恢复，需重新登录。
4. 重置不删除 Plan、任务、工作文件或其他 Owner 的活动凭据，不解除原有连接绑定；远端撤销未确认明确呈现。
5. 显式配置密钥维持原部署合同，非法配置无静默回退；自动平台范围明确。

## Verification

- Independent：变更密钥存储、跨进程代次和本机凭据重置接口。
- 静态检查 CredentialVault、ModelConnections、OpenAiLogin、控制台/CSRF 边界和工厂页面；复用当前工作区，保留 FD-080 未提交审查文件。
- 已执行离线 `mvn -o -s .ai/fd/FD-081/offline-maven-settings.xml -Dmaven.repo.local=D:/.m2/repository -DskipTests test-compile`：339 个生产源、66 个测试源，成功，11.666 秒。沿用低内存 JDK 25 配置，未下载依赖，未运行用例。
- 已执行 `node scripts/compile-worker-runtime.mjs`：JavaScript/Python/TypeScript 语法及 Go compile-only 通过，无模型/网络调用或保留的分发产物。
- 新增 LocalModelVaultTest，系统保护使用纯测试替身、文件与 SQLite 均在测试临时目录。最初请求 LocalModelVaultTest,ModelCredentialsTest 离线运行授权时暂停等待；用户随后明确允许一次，最终实际结果见下方，不将等待或编译当作运行许可/通过。
- 真实 DPAPI、真实凭据、OAuth、服务、正式迁移及浏览器未执行。
- 自检已核对创建/复用/缺失/损坏、显式覆盖与旧密文升级、事务内代次锁和指纹校验、受限辅助进程及重置 Owner/确认/代次检查；定向差异空白检查通过。重置 API 复用已有受保护控制台路由及 CSRF 参数要求。

### 独立审查与最终证据

- Reviewer `fd083-reviewer-20261107-local-vault` 精确领取 `FD-083-000002-review-requested`，对基线 `01f827f` 至初始实现 `ecdeeec` 做整项独立静态审查，再复查修复 `51374119010c566217c13b29439d555dfa7895f2` 及直接影响路径。结论：通过，无剩余阻断。报告：[FD-083_REVIEW_20261107.md](reviews/FD-083_REVIEW_20261107.md)。FD-080 未提交文件、审查事件与未授权测试保持不动。
- 唯一 P2：辅助进程同步 stdin 写入发生在 waitFor 超时之前，可能连带占住事务/文件锁，executor.close 也可能无界等待读取。已改为写入/读取 Future 与进程等待共用单调截止时间，失败时有界终止等待、取消 I/O 和后台流关闭；两项阻塞输入/输出的 mock Process 回归已加入。没有为此运行真实 PowerShell/DPAPI。
- 最终修复后离线 `test-compile` 成功：339 生产源、66 测试源，9.440 秒，2026-11-07 21:36:18 +09。此前 Worker compile-only 通过；修复后未改动其覆盖源码。
- 用户明确允许的首次定向执行、无重跑：`mvn -o -s .ai/fd/FD-081/offline-maven-settings.xml -Dmaven.repo.local=D:/.m2/repository -Dtest=LocalModelVaultTest,ModelCredentialsTest test`，30 项全通过（14 + 16），failure/error/skip 均为 0，Maven 5.520 秒，21:36:32 +09。Reviewer 只读核对最终 Surefire XML 计数与关键断言，没有重复运行。
- 真实 DPAPI、系统账户/文件权限/辅助进程实测、真实凭据/OAuth/远端撤销、服务、浏览器、正式迁移/升级回滚均未执行；测试替身、临时 SQLite、编译和独立静态结论不冒充这些真实集成证据。

## Sources

- 用户确认：本机 Plane 自动保护密钥；忘记或丢失后允许删除再登录。
- FD-081 与 FD-082 已归档的认证/引导合同。

**Completed:** 2026-11-07
