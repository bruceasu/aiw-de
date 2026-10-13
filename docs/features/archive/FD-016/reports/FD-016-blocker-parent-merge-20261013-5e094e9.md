# FD-016 父分支合并冲突反馈

<!-- aiw-data: FD-016-blocker-parent-merge-20261013-5e094e9.json -->

阶段/角色：本地交付 / PM；来源 `FD-016-000015-verification-passed`。状态：待合并后确认解决；无需人类决定。

父分支期间新增 FD-017（c97afdd），并增加 TypeScript build/start-all 脚本（fa2595a）。local-merge 的 squash 在 Feature Index 冲突，CLI 已撤回失败 squash、验证父分支干净并将 master 合入工作树，不是已交付。

读取实际来源 FD/历史与冲突后，保留 FD-016 当前真实通过状态及 FD-017 Design 行；用户脚本内容保留。发现新的 start-all/build 入口不在 FD-016 显式发行清单，已将它们加入，未运行构建、fleet、CLI 或测试。当前新整合结果需再次独立审查，再显式 local-merge；不能凭之前通过收据宣称已审查父分支新增包装组合。

可复用改进：由源 FD 重建并核对索引冲突；父分支运行时/发行材料变化应触发最终整合审查。指定 blocker 模板仍缺失，沿用已归档相同 schema，未伪称读取模板。

恢复补充：active request-review 对 Complete 状态拒绝；尝试领取 PM 通过交接也被 CLI 拒绝（PM/人工交接不可由 agent claim）。两次拒绝均未创建替代收据，没有重试或改写事件。随后通过明确 operator 的 set-status Pending Verification，再 request-review 正式请求整合审查，旧通过仍作为历史。

实际解决：用户授权第四次独立审查，FD-016-000021-verification-passed 对当前整合结果通过；显式重跑 local-merge 成功，master squash `41f7451`，FD-Source `603fd6f871054e7af8cd162116ebfc71d9b23d51`。CLI 验证后清理 feature/FD-016 与工作树，保留真实事件收据。此反馈现已解决，不将之前失败的交付尝试当作成功。
