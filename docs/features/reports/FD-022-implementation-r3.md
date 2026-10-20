# FD-022 R4 恢复路径修复

<!-- aiw-data: FD-022-implementation-r3.json -->

- Worker：`01a15a47-fc32-7362-ac13-f660487b7632`。
- source event：`FD-022-000007-changes-requested`，已 claim。
- 完整实现与前轮修复报告保留，本次仅修 R4。已有 2 次 changes-requested，本次申请当前循环第 3 次、最后一次评审。

事实：DelegationActions.publicationCandidates 已改为复用 DelegationTaskPolicy.canClaim(task) 的当前许可判断，不再仅依据数据库旧行存在。仍有效的同 attempt 许可不会重复授权或扣费；旧 grant revision、到期、身份 generation、boot 或禁止条件使许可不可用时，可以通过原完整新候选、当前身份/授权/额度检查，为同一待执行任务补发新许可。旧消费与审计保留，不删除历史、不换 Plan、不自动交回。

事实：修复后同一离线 Java25 Maven `compile` 成功，260 个生产源文件；`git diff --check` 通过并检查唯一源差异。未运行测试、服务、迁移、HTTP、浏览器、模型、网络或部署。原已 APPLIED 的历史决定保持生效事实，补发是独立新决定，不声称旧决定回滚。

静态推导：旧绑定失效时 canClaim=false，因此 CONFIRMED 分支候选数可为 1；新许可绑定当前版本后 canClaim=true，同一任务不再进入补发候选。此为静态路径证据，不是运行时恢复通过。

未验证风险仍为数据库方言、Spring 装配、实际并发与跨实例恢复、真实发布副作用。下一步为独立最终实现评审；不增加验证权限或重置循环计数。
