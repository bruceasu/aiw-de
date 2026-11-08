# FD-084 聚焦复核 B

- 结论：**CHANGES REQUESTED**，剩余两项代码问题；这是同一 Reviewer 的第二次失败结论，保留首轮历史及三次失败上限。
- Reviewer session：`codex-fd084-reviewer-20261108-a`。
- 源事件：`FD-084-000007-implementation-ready`；读取真实 pending receipt 后由本 session 认领。receipt/实际 FD Revision 均为 **7**，不是交接前预计的 6。
- 工作区：`D:/03_projects/AI-tools/aiw-de/.wt/FD-084`，parent=`develop`，branch=`feature/FD-084`。
- 修复提交：`f2ee9df0ad73a46bf7da9d5aa2fe7cd11cd1b43b`；本轮只复核 `27f29a1..f2ee9df` 中 R1–R5 修复及受影响路径，没有重新进行全面审查。审查开始时仅 FD 文档有事件状态修改。

## 剩余 findings

### B1 / R1 / P1：新增删除逻辑会销毁尚未对账的运行和工具回执

位置：`personal-secretary/store.py:236`；入口 `conversations.py:59`、`:86`。

事实：运行正文的关联图清理已经加入同一事务，但两个删除入口仍只检查旧 work 状态、busy、专业任务、旧确认/平台 outbox。它们不检查新的 PAUSED/RECONCILING 运行、hasUnknownEffect 或 UNKNOWN/STARTED 的 SECRETARY_TOOL。后台线程退出后 busy 会清除；聊天模式的暂停/工具异常也不会必然把纯会话 work 从 DRAFT 改成旧 guard 阻止的状态。

触发：纯会话中的文件发布已经发生但回执丢失，或笔记创建处于未知状态；原运行进入 RECONCILING 后用户删除对话。旧入口允许继续，新事务将 SECRETARY_RUN、工具 UNKNOWN 回执、历史及控制一并删除，原 DAILY_PLAN 也被删除；之后不再有原 run/job 供原副作用核对。先取消后移除/永久删除也可能有同样问题：取消不是撤销，未知副作用仍然存在，不能只看到 CANCELLED 就删除恢复证据。

违反：首轮 R1 已明确要求对仍有恢复依赖的 PAUSED/RECONCILING 运行先阻断删除；FD Acceptance 5/10 要求暂停保留请求及未知副作用先对账；README 的删除限制仍要求处理中/待核对操作先处理。新清理满足隐私删除，但遗漏了允许删除的前置条件。

最小修正：让普通会话删除和永久工作删除共享当前运行/副作用检查，并在实际删除事务中防止状态变化绕过检查。未终结运行需先明确结束；终态但仍有未知/在途副作用或 pending/unknown 保存的，也应保留恢复证据并提示先核对。已安全终结的运行仍按 R1 删除全部私人图。

测试源码限制：`test_delete_purges_own_runtime_graph_and_future_backup` 直接调用 Store 删除，并且把本来仍 RUNNING 的运行作为成功样例；它能检查清理范围，不能证明公开删除入口会保护待对账运行。应把允许删除样例设为真正可删除状态，并补两类入口的阻断场景；本轮没有运行这些测试。

### B2 / R4 / P2：强制提交 ID 后，仍受支持的 LegacyTk 客户端无法发送任何聊天

位置：`personal-secretary/main.py:344`；调用方 `pet/desktop.py:519`、`:531`，传输 `pet/desktop_client.py:52`、`:212`。

事实：HTTP 入口现在拒绝所有缺少 requestId 的聊天请求，但仓库现有 Tk `send_chat()` 仍只发送 workId/text。PersonalClient.operate 直接交给 request，request 原样编码，没有补充提交 ID。README 第 41/59 行和 `scripts/start-desktop.ps1 -LegacyTk` 仍明确提供该客户端。

结果：LegacyTk 每条消息都会得到 422“缺少提交标识，请刷新页面”，刷新或重启并不能让这份源码产生 requestId。这是 R4 修复改变公共输入合同后遗漏了现有调用方，不是对未知旧客户端的假设。

最小修正：让仍支持的 Tk 调用方使用稳定提交 ID，同一结果不明的请求保留 ID/原内容与目标，明确新请求再生成新 ID，并正确展示 UNKNOWN；不要通过为每个 HTTP 重试临时补新 ID 来规避服务端检查。

## 已支持的修复与具体边界

- R1 的正文清理范围：workId 根及 runId 子项被原事务收集，后续备份不再导出它们，其它 work 不在删除集合内。剩余问题是 B1 的删除前置条件。
- R2：客户端与连接快照同时捕获；保存记录包含地址/serviceId/credentialRef/令牌摘要。保存重放和状态查询都先比较原身份，缺失身份拒绝；运行对账另比较冻结 binding。静态调用链支持阻止首轮换主体查后重发，不声称真实知识库主体已经联调。
- R3：SDK final_output 解析后仅接受 finish，其他动作在 host.perform 前拒绝；SDK 未委派专业任务的最终结果也直接拒绝，没有再进入外层纠错 SDK 循环。
- R4：Web 侧和服务侧加入持久提交 ID、payload/目标一致性及原消息返回；新会话在保存提交后创建。接收后、排队前中断的分支返回 UNKNOWN 且不重新排队，符合本次 FD 明确记录的保守恢复选择。提交原文保存在本地记录，未知回复没有被标成执行成功。现存 Tk 调用方仍需 B2 修正。
- R5：DAILY_PLAN 持久终态经统一 `_decision()` 同步 RunJournal；STARTED/UNKNOWN 工具先保留 RECONCILING，工具退出后 stopped 再同步实际 job；finish 保护已有终态，因此静态支持快速回执和取消后晚到回执不复活运行。失败/取消的排队 follow-up 标为 NOT_APPLIED，正常成功可释放。新增测试使用人工替身与显式交错步骤，尚未执行，不等于真实并发测试通过。

## 命令和证据

- 只读：真实 `000007-implementation-ready.json`，`git status --short`、`git diff --stat 27f29a1..f2ee9df`、`git diff 27f29a1..f2ee9df -- <修复相关路径>`、`git rev-parse HEAD`；`Get-Content -Encoding utf8`/`Select-Object` 阅读修复源码、FD、新测试、LegacyTk 调用方；`rg -n`/`rg --files` 定位其边界和合同。
- `aiw fd claim FD-084 FD-084-000007-implementation-ready --session codex-fd084-reviewer-20261108-a` 成功。几次 LegacyTk 文件名定位和 PowerShell 通配符路径未匹配，随后读仓库真实 `pet/desktop.py`、`pet/desktop_client.py`；没有权限或执行验证失败。
- 用 apply_patch 新建本报告并追加 FD Verification；随后一次 `git diff --check` 作为本轮唯一编辑后静态检查。
- 正式结论命令：`aiw fd emit FD-084 changes-requested --producer reviewer --artifact docs/features/FD-084/reviews/2026-11-08-independent-review-b.md --source-event FD-084-000007-implementation-ready`。

Worker 记录的修复后 compile-only 和 diff-check 退出 0 按其范围保留；Reviewer 没有重跑编译。未运行测试、服务/模型/浏览器、网络、依赖下载、真实 OAuth/DPAPI/知识库/迁移、额外代理。未改实现代码，未 commit/merge/push/发布/归档。未获测试授权本身不作为缺陷；本轮阻断仅来自 B1/B2。
