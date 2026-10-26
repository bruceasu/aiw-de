# FD-056: Reuse and retire idle factory Workers

**Status:** Complete
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

工厂 Worker 标识绑定设置审计版本，保存相同配置也可能创建新身份，旧离线记录持续占据注册列表。用户进一步明确：若旧 Worker 不重用，其离线记录应移除。任务历史有外键引用 Worker，不可直接删除历史实体。

## Options and decision

按实际兼容配置生成稳定身份，在线兼容进程继续复用，已退出且无未恢复租约的进程下次使用同一 ID 重启注册。清理以离线或心跳过期为依据，不按空闲时间关闭在线进程；先前提出的空闲五分钟回收不实施。注册可见性与历史实体分离。

## Solution

新增 V22 factory_worker_registration 记录工厂归属和注销时间。工厂启动前记录身份，事务回滚沿用原有清理；登记成功清除注销标记。定时任务仅为有工厂归属、离线或心跳过期、无活动租约/运行任务/运行尝试的记录注销。Worker 集群、配置协议列表和权限选择项排除已注销工厂记录，历史查询仍可按 ID 追溯。旧工厂归属通过本机历史状态回执导入，不能仅凭 factory- 前缀认定。

## Scope

工厂身份稳定性、退出进程重启复用、注册视图和相关选择项、生命周期记录及测试文档。外部 Worker、任务结果、旧授权不删除，不强制终止执行中任务；不改变在线空闲 Worker 的轮询行为。

## Work items

- [x] 1.1 改为按兼容配置生成稳定 Worker ID，退出后可原 ID 重新注册。
- [x] 1.2 新增可追溯注销和旧工厂来源导入，注册列表及选择项排除已注销记录。
- [x] 1.3 离线编译、七项定向测试与文档更新，准备独立审查和受限运行核对。

## Acceptance

- 相同角色/仓库/实际运行配置保持 ID，不因设置审计版本变化生成新 ID；不同兼容边界不复用。
- 已退出且无未恢复租约时可原 ID 重启；失败初始化不会在同一设置版本无限重试。
- 仅已证明工厂归属的无活动工作离线记录退出注册视图，外部同前缀 Worker 不受影响。
- 重新注册恢复显示，历史任务和尝试保留，有未恢复执行的 Worker 不注销。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- `mvn -o -DskipTests=true -Dmaven.test.skip=true compile` 成功，316 源；沿用本会话已授权平台定向测试，`mvn -o -Dtest=FactoryWorkerRegistryTest test` 7 项通过，无失败/错误/跳过。临时 SQLite 验证注销幂等、历史保留、来源保护、在线保留、过期注销、租约/任务/尝试保护及注册恢复/身份兼容。
- Independent：涉及进程复用、Worker 注册生命周期、持久化和多个管理视图；先静态审查，再按既有授权备份数据库、重启并限定 API 核对列表与历史数量，不调用模型。
- 同一次独立审查修正休眠恢复缺陷：注销仅修改注册可见性，不将过期 ACTIVE 永久改为 OFFLINE。定时清理先取 Worker 行锁，再改注销标记；register/heartbeat 同样先更新 Worker 再清标记，保持锁顺序一致。ACTIVE 恢复心跳可重新显示，人工 OFFLINE 心跳不会恢复。修复后最终 compile-only 成功，同一组 7 项测试复跑全部通过，包含恢复心跳及人工离线保护。
- 无活动工作时备份 `D:\temp\data\aidev-r3.before-FD056-20261027-081651.db`（4,550,656 bytes，integrity_check=ok），重启加载器 PID 14876。登录页 HTTP 200，Flyway V22 success=1。
- 真实 API 注册视图由 28 条降至 25 条，3 条旧 coder 工厂记录注销；本机回执证明归属。另有 2 条工厂记录持续发送新鲜心跳、API 状态 ACTIVE，按要求保留供复用。Worker 历史实体仍为 28、工作项 31、执行尝试 37，没有删除历史数据。
- API/数据库核对回执保存在本机临时目录 `aiw-goal-execution/fd056-registration-receipt.json`；本轮未调用模型。未进行完整浏览器、Windows 真实休眠恢复、完整进程退出后再启动或 PostgreSQL 并发实验。稳定身份与注册恢复有定向测试及静态证据，不把它们描述为已执行真实模型重启验收。
- 独立审查通过：`reviews/20261027-independent-static-review.md`，Reviewer `codex-fd056-review-20261027`，来源 `FD-056-000002-review-requested`。本轮失联恢复缺陷已修复关闭；已核对最终源码、修复后测试断言及真实列表/历史回执，未额外执行测试、API 或模型。

## Sources

- 用户要求工厂 Worker 应复用，不能复用的旧离线 Worker 从注册列表移除，以减少干扰。

**Completed:** 2026-10-26
