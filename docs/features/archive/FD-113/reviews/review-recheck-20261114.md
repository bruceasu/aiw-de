# FD-113 三项 finding 聚焦复核

- 结论：Pass，首轮 2 项 P1、1 项 P2 均关闭，限定复核无新增 finding。
- 来源：`FD-113-000005-implementation-ready`，已精确认领；独立 Reviewer：`codex-review-fd113-20261114`。
- 工作树：`.wt/FD-113`；修复头 `a2ccc49`，对首轮 `f0cb3c3` 复核修复及直接影响。首轮报告保留。

## 静态复核结果

1. **选型链路 P1 关闭。** TeamMembers.guidance 保留全局 catalog.version，收窄移至 AuthorityTaskPolicy.guidance，领取响应不再二次叠加。NativeRunClaimPolicy 原路径、TaskExecutionService 的续接校验和控制器取得同一成员收窄结果。AuthorityTaskPolicy.selection 独立核对实际 profile/version 属于冻结成员范围，之后原 AiProfileCatalog.select 的全局版本与 runtime 检查仍执行；没有通过绕过校验解决版本问题。
2. **表单范围 P1 关闭。** modelOptions 按完整 profile 字段比较，删除或同 id 改值的旧对象保留为失效选项；selectedModels 从选项持有的完整旧对象读取。保存时失效对象明确报错，只有用户修改选择才采用当前完整值；刷新与语言切换重建时保留原完整选择，不因修改备注自动清空或替换。
3. **经验来源 P2 关闭。** evidence 改查 worker_team_task 与 assignment/run/任务关联，且要求任务 COMPLETED/FAILED，继续按本人 actor 限定；只有预分配或尚未结束任务的节点不再成为来源。新增 SQLite 用例源码对照真实结果、未派发未来节点和未结束任务，未执行。

直接关联的 ModelConnections.claimed 补丁使用现有 WorkflowAiPreferences 的冻结任务模型作为 model_auth_task 默认值；无偏好仍保留原 Worker 模型。原 canClaim、Owner、连接/版本、Worker proof、计划绑定及后继复制检查保留，未改账户或扩展凭据范围。成员派发快照由既有 assignment 提供，未改用 live 成员设置。

## 证据与限制

实际执行 aiw fd show/claim、定向 git diff/stat、Get-Content，读取修复源码及关联校验和新增用例；随后写报告、更新 Verification 并发送 Reviewer 结果。Worker 记录离线 Java test-compile 和 JS 语法检查成功，Reviewer 未重跑，也不把源码用例当作运行通过。

未运行测试、编译、迁移、验证脚本、浏览器、服务、真实 API/模型、网络或安装，未派子 Agent。本次仅是上述 finding 与直接调用影响的静态复核；原实际模型选择、派发、凭据和 UI 操作的运行证据限制保持。
