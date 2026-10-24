# FD-043 独立设计审阅（第 1 轮）

<!-- aiw-data: FD-043-design-r1.json -->

结论：**ready**。审阅对象为提交 `6e43c86` 对应的 FD-043 Revision 2；真实来源事件为 `FD-043-000002-design-requested`。本结论允许进入实现，不代表实现、运行效果或发布已通过。

Reviewer Session：`fd043-design-20261024-9e12c4b7`。Planner Session：`delegation-methods-20261024-8a2bb0a5`。本审阅没有领取或发送工作流事件。

## 事实与判断

设计将文章中的委派判断、可回查交接及消耗记录落实到现有组合技能和账本，没有另建自动派发器。新 Skill 提供建议；角色内仍由同一 Worker 使用多个技能，跨 Worker 调度仍属平台。没有引入未经验证的模型阈值、节省比例或预算门禁，符合用户已确认方向。

只读记录按服务器当前 Owner 过滤 policy，再关联同一 task/attempt；固定配置还要求 binding Owner 一致。输出采用允许展示的字段，不直接序列化 attempt、binding 或完整 delivery。这足以构成合理的数据边界。实现仍需逐项落实 JOIN 条件和字段白名单。

新增 observations 分支放在现有 `role(kind)` 校验之前，可以保留原 method/role 的 DTO 和默认行为。提交版本的 `AuthorityHttpConfiguration` 已将该 POST 路径列为 human route，并要求真实登录、CSRF 和 operator；页面仍复用已保护的 `/ui/configurations`。本次没有把工作区中其他权限改动当作设计前提。

记录范围明确为最多最近 100 个有 meter 的尝试；不是全局总量。重复读取仅统计显示 attempt 内成功 READ 的相同 resource/result_digest 再次出现，不能证明浪费、全部读取或质量。上报 verdict、正式交付引用和独立验收也已分开。未测 overhead、质量和节省比例明确缺测，避免将建议包装成已验证的平台能力。

未发现必须修改后才能实现的设计缺口。

## 实现时需要保留的细节

1. `ExecutionLedger` 在开始前即建立 meter，token 初值不证明已完成测量；累计 token 也不是重试增量。页面应保留未开始、结算和 UNKNOWN 状态，缓存 token 作为输入子集显示。
2. `reconcileMeter` 可补齐耗时或费用，但不会清除原 `usage_unknown` / `time_unknown`。非空人工校正值和原 UNKNOWN 标记应同时保留；不能把校正费用解释成完整 token 已知，或仅因旧标记而丢弃已有校正数值。FD 要求展示原值及状态，已覆盖这个边界。
3. 友好角色名必须来自该任务固定配置的原 role.json，而非当前目录的同名角色。共享方法更新只影响新 manifest，旧任务文件不追写；不改已有 GraphRecommendations 持久请求内容，可避免旧请求重放与新提示不一致。
4. 新 Skill 在旧目录不存在，回退限制应按 FD 保留。查询与页面不能顺带调用结算、对账、准备配置、自动重试或执行预算拦截。

## 假设与未验证事项

假设现有固定配置/SkillFiles 合同保持不变，实施者将按设计使用服务器 Owner、绑定 Owner 检查和固定文件解析，不接受客户端 Owner 或角色名推断归属。

本次未运行 SQL、数据库、应用、浏览器、HTTP、模型、测试或编译。因此未证明实际 JOIN 返回、并发刷新一致性、页面身份切换、真实模型遵循或性能；这些不能由本设计结论替代。记录视图应保持只读观察性质，不作为实时授权或账本结算依据。不存在要求额外 runtime 门槛的审阅发现。

## 证据与实际命令

静态核对 FD-043、工作流设计审阅规则、现有接口和账本契约；重点源证据为 `ConfigurationController.state/role`、`ExecutionLedger.reserve/usage/settle/reconcileMeter`，以及 `git show 6e43c86:src/main/java/me/asu/aidev/authority/AuthorityHttpConfiguration.java`。参考现有 FD-042 design-review sidecar 的报告格式。

执行了两批初始 PowerShell 内嵌 Python UTF-8 定向读取；第二批输出截断，未依赖不可见内容作新增结论。随后执行三次必要补读：前两次包含未命中的包路径，并读取了提交版权限边界或当前 FD；取得 Planner 提供的准确路径后，最后一次读取了上述 controller、ledger 和归档报告示例。路径补读使发现批次数超过原三批计划；未因此扩大审阅主题或执行运行验证。

只写本报告及 JSON 配对文件；未改 FD、实现、其他脏文件，未 claim/emit、提交或归档。未执行报告写入后的额外验证命令。文档报告无需编译。

下一步由 Planner 决定发送 design-ready；实现后仍需独立实现审阅。
