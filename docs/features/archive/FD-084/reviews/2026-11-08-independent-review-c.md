# FD-084 最终聚焦复核 C

- 结论：**CHANGES REQUESTED**。剩余一项直接影响 B1 恢复/状态路径的确定代码缺陷。
- Reviewer session：`codex-fd084-reviewer-20261108-a`，复用原独立 session。
- 源事件：`FD-084-000010-implementation-ready`；先读取真实 pending receipt 再认领；receipt/FD Revision 为 **10**。
- 工作区：`D:/03_projects/AI-tools/aiw-de/.wt/FD-084`；parent=`develop`，branch=`feature/FD-084`。
- 差异：`f2ee9df..2c9439904551573bd6f5543a8bef6b649a9826aa`，仅复核 B1/B2 及直接影响的取消、对账、状态和桌面提交路径，没有重做全面审查。
- 这是累计**第三次失败**，保留报告 A/B 和三次失败上限；Reviewer 在提交本结论后停止，不开启额外修复/复核循环。

## 剩余 finding

### C1 / B1 恢复路径 / P1：未决副作用查询不能处理宿主自己持久化的空结果

位置：`personal-secretary/secretary_runs.py:316`（`unresolved_effects`），调用方 `public()`、`finish(CANCELLED)` 和 `reconcile()`；产生该记录的既有 `invoke()` 在约第 222 行。

事实：新 helper 的笔记分支执行 `r.get("result", {}).get("state")`。`result` 字段不存在时默认值有效，但它显式为 `None` 时不会使用默认字典。`invoke()` 在重试已知 NOT_EXECUTED 回执时明确执行 `self.save(prior, state="STARTED", ..., result=None)`；再次遇到 KnowledgeNotExecuted 后只改变 state，因此合法持久结果是 `state=NOT_EXECUTED, result=null`。

可追踪触发：Codex 明确备用驱动在同一请求中两次提出相同 knowledge_save_note，而用户没有入库意图或当前凭据没有 create_note。每次本地 KnowledgeNotExecuted 都在文件/知识工具副作用前拒绝；备用循环把 ValueError 作为工具反馈并继续，第二次调用复用相同工具键，留下上述 null 结果。此时不存在未知笔记副作用。

结果：本轮新增 `public()` 对每个展示运行调用 helper；状态 NOT_EXECUTED 不满足第一段 UNKNOWN/STARTED 条件，笔记分支继续对 None 调用 `.get`，产生 AttributeError。该记录持久化在私库，后续 `/api/state` 每次读取都会再次失败，重启服务不能消除。取消、对账也复用该 helper，因而受相同空值问题影响。

违反：FD Acceptance 4/10 的已知未执行与未知副作用区分、运行恢复合同；新增状态投影必须能读取宿主自身合法保存的回执格式。此判断完全来自本地控制流，没有宣称已运行复现。

最小修正：先按回执状态区分已知未执行与未知结果；读取 result.state 前把显式 null/非字典结果安全处理为无结果，不把缺少结果本身推断为已受理保存。补充真实 NOT_EXECUTED→重试→再次本地拒绝的回执样例，核对 public/cancel/reconcile 可以读取。无需放松删除门槛或允许重放未知副作用。

## B1/B2 已落实的部分

- 两个删除入口共享 `assert_runtime_deletable`，实际删除事务在同一 Store RLock 内再检查；非终态、hasUnknownEffect/hasWaitingTool、STARTED/UNKNOWN/WAITING 以及 pending/unknown 工具结果都会阻断删除。已安全终结的运行继续按原范围清理私人图。
- 取消关闭未准入 WAITING 回执，保留在途/未知记录。取消后对账保留 CANCELLED，知识查询传 queryOnly，远端 CREATION_NOT_FOUND 时不再 POST；不能验证时仍阻断删除。上述空结果缺陷 C1 尚需修正。
- LegacyTk 现在传稳定 requestId；PersonalClient 缓存原内容/目标/ID，未知结果保留缓存。Tk 持有原 payload/client，轮询自动选中新会话不改变重试目标，连接变化会阻断旧提交。UNKNOWN 不清空输入；已知成功后新请求取得新 ID，已知 4xx 只清对应提交，另发请求通过显式本机确认结束原重试记录。内存记录不承诺跨进程保留，README 已说明重启后先核对旧回执。
- 新测试源码覆盖删除入口、事务复查、取消后 pending/unknown、queryOnly 和桌面提交缓存；没有 C1 的 result:null 重试样例。本轮未执行测试，不根据断言数量宣称通过。

## 证据与命令

- `Get-Content -Encoding utf8` 读取真实事件 receipt、FD 本轮交接、相关源文件/测试，结合 `Select-Object -Skip/-First` 定位调用链。
- `git status --short`（起始仅 FD 文档有事件状态修改）；`git diff --stat f2ee9df..2c94399`；`git diff f2ee9df..2c94399 -- <B1/B2 相关路径>`；`git rev-parse HEAD`。
- `aiw fd claim FD-084 FD-084-000010-implementation-ready --session codex-fd084-reviewer-20261108-a` 成功。
- apply_patch 新建本报告并追加 FD Verification；随后一次 `git diff --check` 为本轮唯一编辑后静态检查。
- 正式事件命令：`aiw fd emit FD-084 changes-requested --producer reviewer --artifact docs/features/FD-084/reviews/2026-11-08-independent-review-c.md --source-event FD-084-000010-implementation-ready`。

Worker 记录最终 compile-only 成功，编译范围已含 desktop*.py；Reviewer 检查脚本与源码，没有重跑编译。未运行测试、模型、服务/GUI、网络/依赖下载、真实 OAuth/DPAPI/知识库/迁移或额外代理；未修改实现代码，未 commit/merge/push/发布/归档。真实运行兼容性仍未验证；未获测试授权本身不是本轮缺陷。
