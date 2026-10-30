# 秘书接入 HANDOFF

> FD-039 已完成本地实现并通过最终独立复查。以下是当前代码接口，HTTP 尚未经运行或跨仓库联调；不能作为部署通过的证据。离线检查范围见[实现状态](IMPLEMENTATION_STATUS.md)。

## 边界

秘书直接访问 Monitor Agent HTTP API；首期不验证秘书身份，也没有用户身份或角色级数据授权。只有访问接口即可查询任务、查询 Issue 或提交任务，暴露到跨设备网络前需由部署方控制网络访问。秘书仓库不在本 FD 的交付范围。

服务默认监听 `127.0.0.1:8765`；跨设备地址需由启动参数指定。模型默认 `gpt-6-luma`，后端为 OpenAI Agents SDK；内置后端默认不启用模型请求，配置 `MONITOR_MODEL_ENABLED=1` 后才会发起模型请求。外接 Runtime 由部署者配置并负责其行为。客户数据外发和日志保留政策仍待确定，不能据此声称已完成生产数据治理。内置 SDK 追踪在每次运行中关闭。

## API

所有请求和响应使用 UTF-8 JSON。各任务的 `request_id` 是全局唯一去重键；相同键和相同请求返回已有任务，相同键和不同请求返回 HTTP 409。首次提交返回 HTTP 202 和任务信息；任务异步执行。

```http
POST /tasks
Content-Type: application/json

{"request_id":"case-001","role":"support","goal":"整理这个问题并保存 AIW Issue：……"}
```

`role` 可为 `operations`、`operator` 或 `support`。任务字段包含 `task_id`、`request_id`、`status`、`knowledge_version`、`result`、`events` 和 `cancel_requested`。

- `GET /tasks/{task_id}`：获取任务和步骤结果。
- `GET /requests/{request_id}`：以原请求键查找任务。
- `POST /tasks/{task_id}/input`：等待补充时提交 `{"message":"……"}`，沿用原知识快照。
- `POST /tasks/{task_id}/cancel`：请求取消。排队或等待输入时可取消；运行中只设置取消请求，结果可能是 `unknown`。
- `GET /issues/{issue_id}`：通过 AIW 查询 Issue；响应中的 `status` 说明查询是否成功。

任务状态为 `queued`、`running`、`waiting_input`、`completed`、`failed`、`partial`、`cancelled` 或 `unknown`。`events` 中工具步骤的 `started` 表示曾开始，不能视为成功；最终结果才标识该步骤结果。进程重启后，原有 `running` 任务标记为 `unknown`，不能自动重放；排队任务可继续执行。连接中断后先按原 `request_id` 查询。

## AIW Issue

默认项目根目录为 `/mnt/temp/customer-profile`，用 `MONITOR_ISSUE_ROOT` 覆盖；AIW Issue 相对位置为 `docs/issues/`。通过 AIW CLI 创建编号和捕获 Issue Plan，不自行编号。Issue 查询在 AIW 状态摘要之外读取已登记 Plan，并核对摘要；没有登记 Plan 或内容不一致时不会报告正文查询成功。该目录须存在，部署方须确保 AIW 可写并与其他写入者共用同一套编号状态。创建返回 Issue ID 不表示已批准、派发或修复；AIW 捕获 Plan 可能推进 Issue 状态。

## 未完成事项

秘书连接器、端到端调用、模型及真实 MT4 运行、可靠的自动语义冲突判断、完整 MT4 查询能力、取消期间的外部进程停止确认和数据保留策略均未验证或尚未完成。不要把本文件当作接入验收结果。
