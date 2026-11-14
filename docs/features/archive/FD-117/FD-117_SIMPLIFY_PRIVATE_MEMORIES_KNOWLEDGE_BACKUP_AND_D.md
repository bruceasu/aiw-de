# FD-117: Simplify private memories knowledge backup and deletion

**Status:** Complete
**Revision:** 6
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 30, 36. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

原始对话、个人记忆、知识资料分开，按需读取并保留来源；查看、纠正、可恢复删除、备份与恢复。备份不含凭据，永久删除明确确认，不增加同步系统。

Dependencies: FD-107（已交付）。复用秘书现有 Store、Conversations、配对会话和 CSRF；不增加数据库表、迁移、同步服务或第三方依赖。持久化和删除行为改变，采用独立静态审查。

- 记忆保留原始来源和纠正时间；移除后不进入相关记忆检索，可恢复。按需查看来源原文，已丢失来源直接说明。
- 对话普通移除改为进入已有工作回收列表，保留原文和记忆；永久删除沿用已有工作删除流程及忙碌／回执检查。草稿复用已有删除历史恢复；永久删除清除内容历史，保留无正文的编号／版本标记，防旧窗口误写新资料。
- 新增已认证且受 CSRF 保护的 `POST /api/backup/restore`，显式确认后复制对话、工作、记忆及被动草稿／模板。新编号避免覆盖当前资料；工作从 TRASHED 开始，不恢复旧 Plan 绑定、路径授权、定时、运行状态或待发送请求。
- 同一原备份按摘要幂等导入。完整源包复用已有 `personal_import_batch`／`personal_legacy_record` 作为只读副本，后续导出带扁平 `archivedBackups`，包括旧回执、安排、策略和历史；这些副本从不作为执行数据读取。
- 独立审查 P1 已修复：`personal_import_batch` 只记录实际完成的恢复；归档副本只写 `personal_legacy_record`，不提前占用原包的恢复去重标记。补充“仅归档后仍可显式恢复原包”的回归源码。
- 恢复接口正文上限 16 MiB，页面限制源文件 15 MiB；其他 POST 保持 32 KiB。私人凭据配置、模型密钥和登录会话仍不参与导出／导入。用户主动写进对话的私人原文不会自动脱敏，页面明确提示妥善保管。
- 外部知识库继续走现有显式连接和按需读取，不增加另一套知识库／共享机制。此次不启用跨设备同步或跨 Owner 授权；导入只作为当前私库的新资料。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

## Work items

- [x] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [x] 1.2 实施已确认行为，删除因此不再需要的代码。
- [x] 1.3 编译受影响源码、自查差异并提交独立审查；审查结论单独记入 Verification。

## Acceptance

能找到来源并纠正错误；私人内容不自动共享；删除可恢复；备份恢复有清晰范围且不覆盖模型凭据。

## Verification

- 独立聚焦复核 `d5301d6`（来源 `FD-117-000005-implementation-ready`）：Pass，首轮唯一 P1 已关闭。仅归档不再占用实际恢复标记，主包复制与去重标记仍在同一事务；再次导入实际已恢复包保持幂等。详见 [复核报告](reviews/review-recheck-20261114.md)。新增用例源码已阅读但未执行；Reviewer 未运行测试、编译或运行时验证，原证据限制保持。

- 独立静态审查（`8fa549a → a5e5b36`，来源 `FD-117-000003-review-requested`）：Changes requested，1 项 P1——仅归档包也写入实际恢复去重标记，导致之后显式导入原包被误判为已恢复。详见 [审查报告](reviews/review-20261114.md)。限定范围未发现其他阻塞；Reviewer 未运行测试、编译或运行时验证。

已通过 `python -m py_compile personal-secretary/store.py personal-secretary/conversations.py personal-secretary/main.py tests/secretary/test_private_restore.py`，以及 `node --check` 检查 app.js、i18n.js。待独立审查。新增离线回归源码覆盖恢复隔离／去重／来源、无部分导入、可恢复删除／纠正及版本冲突；按用户限制不运行测试。未运行服务、迁移、恢复真实私库或调用模型／网络。

## Sources

- docs/FUNCTION-INVENTORY.md, 30, 36.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。

**Completed:** 2026-11-14
