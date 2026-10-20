# FD-022 本地交付恢复记录

<!-- aiw-data: FD-022-blocker-delivery-20261021.json -->

- 角色/阶段：PM delivery；source event `FD-022-000009-verification-passed`。
- 已通过评审源码：`a0862a2`，通过报告 `../reviews/FD-022-review-r3.md`；交付前 FD HEAD `8f56860`。
- 观察事实：PM 尝试 claim 该 PM handoff 被 CLI 拒绝，提示 PM/human handoff 不能由 agent session claim。没有伪造或强占 receipt；宿主继续使用技能已授权的 PM local-merge 路径，不要求重复用户批准。
- 观察事实：首次 `aiw git wt local-merge FD-022` 因 FD 和稳定规范 add/add 冲突失败。CLI 已撤销父分支失败 squash，并将 develop 合入隔离分支以供恢复；主工作区干净，无 MERGE_HEAD。隔离分支保留真实 merge。
- 确认原因：分支原计划提交 `375ee82` 与父分支计划提交 `b37d109` 是同一基线的不同提交；两提交实际文件差异仅为父分支独立的 `workers/worker-语言选型建议.md` 更新。双方各自新增同名 FD/spec，导致 add/add，不是源代码语义冲突。
- 有界恢复：逐个比较 FD/spec 内容，父分支为初始 Open/revision3，隔离分支为已实现并独立通过的 Complete/revision9，规范保留已确认认证与固定 attempt 增量。保留隔离分支已评审 FD/spec 的精确内容；索引保留匹配其状态的 Complete 行，排除自动合入的过时 Open 重复行。完整保留父分支独立的 Worker 语言建议修改，不将它宣称为本 FD 实现。
- 实际检查：unmerged index 已空；git diff --check 无错误；对照原 HEAD，src、Worker runtime、FD 与稳定规范精确无差异，源/FD digest 未漂移；只继承父分支独立文档及新增恢复证据。因此不触发实现超出当前 pass 的重审；当前三次评审上限未重置。
- 当前状态：冲突已解决，等待记录恢复 merge 后继续本地交付与 archive；不宣称已经交付。无人工决定需求。未运行测试、构建、编译、网络或服务。
- 可复用改进：创建 worktree 后避免改写其已共享的计划提交；交付遇到 add/add 先比较父提交文件差异，再保留确实已评审的演进内容。未在本 FD 改动 Git 工具或技能。
- 模板：指定 BLOCKER_FEEDBACK 模板不存在，按技能字段编写。
