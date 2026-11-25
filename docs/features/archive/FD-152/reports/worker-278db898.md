# FD-152 复审修复

- 实现提交 `278db898221680fc2034fcdfde1ed739e2df8513`
- Worker 事件 `FD-152-000004-work-requested`
- 会话 `codex-fd152-worker-review-repair`

修复了兼容空闲容量回收、跨 Profile 的目录写入互斥、任务显式 Profile 与员工默认模型的优先级，以及经理超时后的本机进程树退出确认。

离线 Java 编译通过。使用环回 Plane、临时 SQLite 和模型替身核查了回收、模型确定和重叠目录的结果。完整命令、失败回合、证据位置与限制见 [FD Verification](../FD-152_CONTROL_PLANE_PYTHON_WORKER.md#verification)。
