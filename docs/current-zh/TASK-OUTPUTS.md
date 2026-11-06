# Plan 任务交接

执行位置、Worker 交接文件、固定引用、报告摘要及清理流程见 [Plan location 与共享交接](../PLAN-LOCATIONS-AND-HANDOFFS.md)。

新任务不上传修改过的源文件。Worker 将报告和 manifest 写到共享目录 `.ai/handoffs/`；平台登记生产者 attempt、manifest 路径和 SHA-256。平台数据库保留执行摘要，文件内容由接收 Worker 读取。

报告登记不等于评审通过。独立评审仍按任务依赖执行。失败诊断、执行历史与租约状态由现有任务表和 attempt 表提供。

正常完成或最终取消的 Plan，确认全部执行停止后才能清理平台拥有的临时目录；用户声明的目录不删除、不 reset。清理后本地交接文件不再保证可读。
