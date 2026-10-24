# FD-048 独立设计审阅 r1

<!-- aiw-data: FD-048-design-r1.json -->

结论：ready，无阻塞设计发现。设计来源 FD-048-000002-design-requested；本报告是独立设计反馈，不是实现审阅通过事件。

## 设计与边界

1. 追加私人 work JSON 的 executionMode，能区分同进程正常排队与重启恢复；现有 store.put/content 路径保留其他字段，无需 SQL schema 迁移。main.py 的 run-work 与 runtime.schedule 本已分离，分别指定 IMMEDIATE 和 TIMED 不需要改变公共请求。
2. 构造阶段将未开始 IMMEDIATE 转确认、将过期 TIMED 转 MISSED_TIME，tick 在暂停/启动之前再次判定过期，可以覆盖停机和在线暂停跨窗口的两类错过。未来 TIMED 正常等待；一分钟窗口沿用旧协调器既有容差，不新增无限补跑。
3. 无模式的旧 SCHEDULED 无法可靠推断原始意图，保守进入确认是明确、可恢复的兼容策略。完成、取消、移除及已开始任务不进入未开始恢复分支，避免复活历史任务。已有 DAILY_RUNNING → NEEDS_HUMAN/INTERRUPTED 及 RUNNING decision → UNKNOWN 应保持。
4. 复用 run-work/schedule/cancel 能沿用工作版本、专业任务 pending、busy、暂停和取消保护。Web 恢复列表及桌面报告应让执行、改期和取消可见；中断任务的“已核对结果，重新执行”必须是明确人工动作，不能由刷新、启用或定时 tick 触发。

## 实现时核对点

- 模式仅接受 IMMEDIATE/TIMED；未知值与缺失值统一进入确认，不能默认成为马上执行。
- 过期判定应先于 paused 分支；同进程 IMMEDIATE 不按 dueMs+60000 过期。
- 每次人工重新安排必须写入新模式、时间和 revision；取消继续使用已有 cancel_record，不靠仅隐藏卡片改变状态。
- 仅对尚未开始的 daily SCHEDULED 应用恢复分流；保持专业任务实际结果核对，不把重新排队描述为完成。

## 静态证据与限制

读取 FD、planning reference、daily_assistant.__init__/schedule/tick/_start/run、main.py run-work/schedule 及 runtime.schedule，与父会话已读取的 store、Web、桌面调用路径核对。只执行 Get-Content 等只读命令和本报告写入。未运行测试、编译、HTTP、浏览器、模型、服务或数据库；未修改业务实现、FD 状态或发出 Reviewer 事件。真实停机恢复、线程竞争和 UI 交互仍需后续实现证据，不以设计可行替代运行验证。
