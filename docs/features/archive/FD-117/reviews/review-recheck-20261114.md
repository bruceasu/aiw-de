# FD-117 恢复去重 P1 聚焦复核

- 结论：Pass；首轮唯一 P1 已关闭，无新增 finding。
- 来源：`FD-117-000005-implementation-ready`，已精确认领；独立 Reviewer：`codex-review-fd117-20261114`。
- 工作树：`.wt/FD-117`；修复提交 `d5301d6`。保留首轮报告，仅复核归档/实际恢复标记与直接事务影响。

静态事实：归档循环现在仅写 personal_legacy_record，不写 personal_import_batch；两表原 schema 没有外键约束，归档仍可独立保留。只有主包实际资料复制完成后才在同一事务尾写入 personal_import_batch。只归档的 A 不会命中恢复去重；显式恢复 A 后才持有该标记，再次恢复 A 继续返回 duplicate。复制、历史和标记仍在同一事务，失败不会留下成功恢复标记。没有恢复旧 outbox、授权或执行状态的新增路径。

新增用例源码覆盖空 carrier 只归档原包、之后恢复原包返回 3 条且非 duplicate、再次导入返回 duplicate 并保持一份工作，断言对应首轮缺陷；未运行该用例，不将源码断言视为通过。

实际执行 aiw fd show/claim、定向 git show 和 Get-Content，随后写报告、更新 Verification 并发送 Reviewer 结果。Worker 记录 store.py 与用例重新 py_compile 成功，Reviewer 未重跑。未运行测试、编译、验证脚本、服务、数据库、真实 API/模型、网络或安装，未派子 Agent。原实际恢复与 UI 交互的运行证据限制保持；这是有界静态复核。
