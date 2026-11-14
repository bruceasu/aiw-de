# FD-124: Worker 自动沉淀已完成任务的共享经验

**Status:** Complete
**Revision:** 15
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Dual

## Problem

当前成员只会使用人工确认的经验。已完成任务中的结论没有自动进入同一 Owner 的团队知识，后续成员无法复用。

## Options and decision

Worker 在原有最终模型回合末尾附加独立的经验元数据块；Python Worker 将其从任务结果中剥离，作为可选字段提交。原生问答运行时也只接收剥离后的任务结果。经验字段只允许简短、可复用的方法或已验证系统事实，明确排除私人事实、凭据、路径和原始用户内容。没有额外模型调用。

## Solution

增加一张按 Owner 隔离、以来源任务为唯一键的只追加经验表，记录来源 Worker、Plan／运行、PASS 任务的经验摘要和时间。只在任务确实分配给稳定团队成员且带有效经验摘要时写入；REPAIR_REQUIRED 不作为经验保存。完成重放沿用现有幂等路径，不重复生成经验。

未来任务构建成员资料时，读取该 Owner 最近的共享经验，合并成一个现有 `knowledge` 资料来源，沿用既有按需 Sources 能力和大小限制。历史任务的冻结上下文不重写。经验正文是从最终模型回合单独提取的经验字段，不自动读取或转存个人私库、附件、对话记录、工具参数、凭据或工作区文件。现有执行工具和权限不变。

## Scope

只做完成后经验元数据的生成、Owner 内共享、来源追溯和后续任务按需可读。没有有效经验摘要、PASS 结果或稳定成员分配的任务不创建经验。Skill 自动修改、版本历史与修改依据另列 FD，不在本次修改 Skill 内容。

## Work items

- [x] 1.1 用最小迁移增加 Owner 隔离的只追加经验记录。
- [x] 1.2 在权威任务完成事务中记录来源任务的 Worker 摘要，并保持重放幂等。
- [x] 1.3 将最近的共享经验接入后续成员 Sources，沿用现有预算且不改写冻结任务。
- [x] 1.4 编译并静态自查；独立审查通过。

## Acceptance

只有同一 Owner 下已分配成员的 PASS 完成记录会进入共享经验；每条记录可追溯到来源任务、Plan 和 Worker。未来任务能通过现有 Sources 按需读取，过往任务内容不变。缺少经验元数据时安全跳过；REPAIR_REQUIRED 不共享；重复完成不产生重复行；不将私人参考资料或凭据复制到共享经验。

## Verification

- 离线 Java Maven compile 通过，JDK 25.0.1；使用 FD-081 的 settings 和 D:/.m2/repository。首次调用因 PowerShell 将未引用的 Maven 属性拆开而在编译前失败；更正引用后通过。
- Python `python -m py_compile workers/process-python/process_worker.py` 通过。
- `git diff --check` 通过；Reviewer 未重跑。
- 未运行测试、迁移、服务、Worker 或真实模型调用。
- 首轮独立审查（FD revision 4）发现 P1：曾保存完整任务答复而不是独立经验；P2：曾把 REPAIR_REQUIRED 当作共享经验。两项已修复，详见 [首轮审查](reviews/review-20261114-r1.md)；当前代码等待限定复核。
- 第二轮限定审查（FD revision 8）发现 Native app-server 问答解析接收了未剥离的模型输出；已改为传入清理后的任务摘要，详见 [第二轮审查](reviews/review-20261114-r2.md)。
- 最终限定复核（`FD-124-000011-review-requested`）通过，详见 [最终审查](reviews/review-20261114-r3.md)。确认经验元数据贯通、仅 PASS 入库、Native 问答使用剥离后的报告摘要。未运行测试、迁移或运行时验证；隐私排除依赖模型遵守提示，风险记录在审查报告中。
- Close-gate 独立复核（`FD-124-000013-review-requested`）确认当前 FD、实现和三轮报告一致，通过。详见 [Close-gate 审查](reviews/review-20261114-r4.md)。此后不再修改本 FD 文档。
- 实施记录：[implementation.md](reports/implementation.md)。

## Sources

- `docs/FUNCTION-INVENTORY.md`，Worker 自动成长。
- `docs/features/archive/FD-113/FD-113_GIVE_MEMBERS_SCOPED_SETTINGS_AND_SOURCE_BACKED_E.md`，来源经验和隐私边界。
- `KnowledgeService.record`、`TeamMembers.materials`、Python Worker `CompletionReport`。

**Completed:** 2026-11-14
**Disposition reason:** 实施完成，独立复核通过；编译与静态证据记录在 FD。
