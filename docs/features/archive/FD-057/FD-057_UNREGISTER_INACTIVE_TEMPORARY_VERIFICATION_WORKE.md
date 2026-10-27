# FD-057: Unregister inactive temporary verification Workers by API

**Status:** Complete
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

此前验证数独流程启动的 goal-v5/v6/flow 等 Worker 并非用户自行接入；虽已离线，仍占注册列表。用户要求通过 API 清理。现有 API 只有 drain/offline，不支持保留历史的注销。

## Options and decision

新增人类管理员会话注销 API 与独立的人工注销记录，不把临时验证 Worker 冒认为工厂 Worker，不删除 Worker 外键历史。清理依据真实注册日志中的 ID 和当前 API 心跳快照。

## Solution

POST /api/r1/worker-registrations/{id}/unregister，接受 expectedHeartbeatMs、reason。复用控制台登录、管理员权限与 CSRF，拒绝代理身份；事务中锁定并 CAS 核对心跳/无活动租约、任务、尝试，仅允许离线或心跳过期。设置 OFFLINE、写人工注销标记和审计。重复注销幂等；明确重新注册清除人工标记，普通心跳不会恢复人工注销。集群、配置列表及权限选择项排除注销记录，历史详情保留。

## Scope

仅注销接口与列表可见性，使用日志佐证的 23 条离线验证 Worker。不清理在线工厂 Worker、不删工作或尝试、不调用模型。任务推进的 DRAFT 交接问题另外核对，不混入此注销设计。

## Work items

- [x] 1.1 实现管理员注销接口、心跳 CAS/租约保护和独立历史标记。
- [x] 1.2 注册视图和选择项过滤，重新注册恢复及附近文档。
- [x] 1.3 按真实日志冻结目标清单，准备编译/静态审查与限定 API 清理。

## Acceptance

- 明确日志来源的离线临时 Worker 经 API 注销后不再显示于注册列表。
- 正在执行、恢复心跳或已改变的 Worker 拒绝注销；历史实体与尝试不删除。
- 管理员会话和 CSRF 必须有效；不能通过共享 Worker token 或代理身份注销。
- 手工注销不授予工厂归属；重复请求不重复操作，重新注册恢复显示。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- 日志注册行与 API 集群交叉核对得到 23 条 OFFLINE、activeLeases=0 目标。清单在本机临时目录 aiw-goal-execution/fd057-worker-cleanup-targets.json，清理前可见注册数 25。
- 独立审查检查鉴权、CAS、租约保护、持久化/事务与历史保留；通过后备份并加载，仅用正式 API 执行清理。
- 离线 compile-only 成功，318 源；git diff --check 通过。本次未新增或运行单元测试。
- 静态审查无阻断后备份 D:\temp\data\aidev-r3.before-FD057-20261027-083234.db（4,554,752 bytes，integrity_check=ok），重启加载器 PID 20376，HTTP 200，V23 迁移加载。
- 使用正式控制台管理员会话及 CSRF，逐条 POST 新注销 API，共 23 条返回 UNREGISTERED。API 注册列表 25 -> 2，goal-v6-architect 等目标全部不再显示；2 条在线 factory Worker 保留。清理前后 Worker 历史实体、work_task 和 execution_attempt 数量完全相同，未调用模型。
- 每条操作均核对 API 当前心跳快照，注销结果随每次返回保存到本机临时目录 aiw-goal-execution/fd057-worker-cleanup-targets.json。没有直接修改数据库状态或删除历史。未做完整浏览器、拒绝路径或 PostgreSQL 并发运行测试。
- 独立审查通过：`reviews/20261027-independent-static-review.md`，Reviewer `codex-fd057-review-20261027`，来源 `FD-057-000002-review-requested`。已静态核对鉴权/CSRF、CAS 与事务及历史保留，并读取23条真实注销回执，无阻断发现；Reviewer 未额外执行测试、API 或模型。

## Sources

- 用户明确要求“请用 API 清理”，并指出 goal-v6-architect 等由此前验证流程启动。

**Completed:** 2026-10-26
