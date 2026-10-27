# FD-060 独立静态审查

- Reviewer：`codex-fd060-review-20261028`。
- 来源事件：`FD-060-000002-review-requested`，已 claim。
- 比较基线：`2b97f53ee089ea797e662f43f55de298be7e89b0` 上本 FD 增量；不重复审查同时保留的 FD-059 草案编辑变更。
- 最终结论：**通过**。本轮 Python 3.11 junction 缺口修复关闭；已读取安全诊断 API、真实新短归档与原失败步骤恢复成功证据。整个主题计划仍在继续，未宣称业务全量完成。

## 发现与本轮修复

**P1：Python 3.11 的 junction 检测原先被静默跳过。** Worker README 支持 Python 3.11+，新增 snapshot 用 `getattr(path, "is_junction", lambda:False)`，但该 Path helper 自 3.12 才提供；3.11 上不能保证阻止遍历到目录联接的外部内容。已改为基于 `lstat().st_reparse_tag == 0xA0000003` 拒绝 Windows MOUNT_POINT，不依赖该 helper。新增假 metadata 用例验证此路径，无需管理员权限或创建真实 junction；执行者报告单独定向运行通过。问题关闭。

## 已核对

- 新归档路径为 `.aiw/o/<完整 SHA256(attemptId)>`，不再重复拼接计划/任务/时间。已存目录不迁移，读取保留数据库 directory；文件、manifest、字节/hash 登记、不可覆盖契约与原路径安全检查继续使用。
- Windows 新目标文件超 240 字符明确拒绝，实际相对路径仍经既有校验；历史 snapshot 使用幂等扩展路径，保留文件内容/容量检查，不忽略 `.aiw` 历史或无法读取的目录。walk onerror 抛出而非静默忽略。
- 分类保留合规固定代码，将操作系统路径/权限/缺失/IO/解码错误映射为安全代码；扩展路径下真实缺失不会只按字符串长度误认路径过长。fail 上报不再硬编码 OPERATION_FAILED，证据与服务端 fail 使用相同分类函数。
- 新 GET 任务详情复用现有控制台会话边界和计划成员校验；只在匹配本计划任务后查询 attempt/retry。WorkTask 与 Attempt DTO 不含 lease/token 字段，诊断由安全代码映射固定 summary/nextAction，未增加原始异常或模型配置字段。
- 历史 OPERATION_FAILED 明确标记原因未记录，不反推缺少审批。FAILED UI 提供任务诊断/重试入口，独立审查 REPAIR_REQUIRED 仍使用评审恢复流程，不混作同一授权原因。

## 实际审查证据与限制

- Reviewer 只运行 `git diff/status/rev-parse`、`Get-Content`、`rg` 与真实 `aiw fd show/claim`，未运行测试/构建、模型、API、用户计划或服务。
- 已读 TaskOutputPathTest、WorkflowFailureDiagnosticTest 及 Python test_workspace_paths 的关键断言；执行者报告 Java 十六项、Python 原五项及新增 junction 单项通过、两份 JS 语法检查通过。无全库或浏览器验收；不把这些改称 Reviewer 独立执行。
- 实际 Windows 长路径原目录快照由执行者只读复现并在修复后成功，未写项目文件。历史 attempt 不改写；只有之后的新失败才会携带新分类码。

## 实际运行证据复核

- 已读取 `fd060-readonly-receipt.json`：原计划任务诊断 API 与页面链接可读、noLeaseCredentials=true、historicalCauseNotInvented=true、只读 writes=0。两条历史 generic 记录保持 specific=false，不把现推断回填成旧日志事实。
- FD Verification 记录数据库备份 `D:/temp/data/aidev-r3.before-FD060-20261028-005007.db`、integrity ok 与平台重启。随后 attempt3 仍被更新前旧 Python 进程误领而失败；执行者明确记录仅重启 Java 不足，并在无活动执行时通过认证 drain、精确 PID/创建时间/命令核对后停止四个旧进程，再经 API offline，没有手改数据库。
- 环境实际刷新后的唯一纠正重试 attempt4 `b9f585cf-84c0-4e62-903d-675d3c50c834` 已 COMPLETED/PASS，执行者证据包含模型前快照通过、context-built、model-terminal、report-posted，后续独立阶段审查 PASS。它证明本次原路径阻塞解除，不证明后续整计划全部完成。
- 已读取 `fd060-short-archive-receipt.json`：真实 `.aiw/o/<64位摘要>` 目录长度114、最长文件126；manifest、report.md/json、plan.md/json 存在，executionState=COMPLETED、reviewState=PASS，旧历史保留。
- 未主动制造新生产失败验证具体 fail 码持久化；该路径由分类单项/Java诊断测试与实际 fail 调用静态追踪支持。Reviewer 没有发起模型、重试、API、服务或进程操作，未运行完整浏览器/全库验收。
