# FD-024 第 1 轮审核修复交接

<!-- aiw-data: FD-024-implementation-r2.json -->

Worker：`01a15e5f-d79d-73e0-bb3b-480e76aa474c`；已认领源事件 `FD-024-000008-changes-requested`。第 1 轮独立审核结果为 changes-requested，保留原报告，未将其改写为通过。完整实现与原始限制见 `FD-024-implementation.md`。

本次修复对应全部三项明确发现：

- **R1-F1**：尚未部署的 V10 初始化直接继承 V9 已持久化的 `legacy_auth_required`，保留永久认证锁存。现有 Console 拦截器在锁存为真且认证关闭时拒绝 UI/API；没有恢复旧秘书执行模式。
- **R1-F2**：`AuthorityGrantService.targetCurrent` 统一检查能力对应目标的 owner、暂停、世代和禁止版本。所有新准入的 permitted 谓词都检查当前目标。发布单次许可额外保存独立 PUBLICATION 目标及 owner/世代/禁止版本；直接、间接发布写入许可时检查目标，claim 与 actual start 再比较保存值，主 PLAN 边界仍保留。
- **R1-F3**：人工替换许可共用 `checkReplacement`，在统一首锁下拒绝 UNKNOWN 和 IN_PROGRESS；普通技术重试在修改任务前先检查。新建和重用任务的人工许可共用写入函数，替换前保留旧 task+attempt 快照与回执关联。新许可清空旧授权/身份/request 字段，旧意图必须先完成有证据的明确对账；没有自动退款或重放。

2026-10-21 23:14:47 JST，使用原报告的完整 compile-only 命令通过：离线编译 251 个 Java 生产源码文件，Python 内存编译、三个 JavaScript 语法检查及修改的 TypeScript profile 语法转换通过。针对修复静态核对 SQL 列与参数、许可生成和 actual-start 调用链、人工重试事务及 UNKNOWN 对账路径；此前 `git diff --check` 无空白错误，只有换行归一化提示。

未运行测试、SQL/Spring、HTTP/浏览器、模型、并发或故障注入、网络、实际迁移、切换或分发构建。SQL 运行、装配和并发仍未经运行验证。本轮是待独立审核的修复，不是 Worker 自评通过。继续完整 FD、无旧兼容模式、迁移工具执行禁用及归属不明封存的用户决定保持有效。
