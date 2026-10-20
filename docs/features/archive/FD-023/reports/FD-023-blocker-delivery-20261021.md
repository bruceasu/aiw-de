# FD-023 本地交付冲突恢复

<!-- aiw-data: FD-023-blocker-delivery-20261021.json -->

- PM/source：`FD-023-000009-verification-passed`。交付前HEAD `e05f3ed`，父分支 `565282b`，原计划 `6f34bf4`。
- 事实：首次 local-merge 因 FD add/add 冲突失败。CLI 已撤销父分支失败 squash，并在隔离工作区保留合入 develop 的真实恢复 merge；主工作区干净、无 MERGE_HEAD。
- 原因：父分支将同一计划提交改写以纳入 ISSUE-00006 归档，和原计划的差异仅5个Issue文件的归档/决策记录。双方各自新增同名FD，父分支仍为初始Open/revision2，隔离分支为已实现且独立通过的Complete/revision9，无新业务要求或源码冲突。
- 一次有界恢复：使用 resolving-merge-conflicts 方法逐段比较FD；保留已通过评审FD的精确内容及匹配Complete索引，排除过时Design重复行。保留父分支全部Issue归档及元数据，不恢复旧活动Issue目录，不改用户独立修改。
- 实际检查：unmerged index为空，git diff --check无错误；相对已评审HEAD，src、workers、tests、scripts、规范、中英文当前文档和FD精确无差异。没有源/验收漂移，不因继承用户Issue归档重启或追加实现评审。没有运行测试、编译、网络、服务。
- 状态：冲突已解决，待记录恢复merge及继续本地交付/归档；不宣称尚未执行的交付已成功。无人工决定需求。
- 可复用改进：worktree分叉后避免改写共享计划提交；若父分支合法改写，按实际文件差异继承独立修改并保留已评审演进内容。没有扩大本FD到工具或技能修改。
- 模板：指定反馈模板不存在，按技能字段编写。


## 最终解决

2026-10-21：恢复提交 `480edca` 后本地交付成功，develop squash为 `6ef25e3`，CLI清理及Complete归档成功。交付Gate已解决；详见同目录 `FD-023-delivery.md`。
