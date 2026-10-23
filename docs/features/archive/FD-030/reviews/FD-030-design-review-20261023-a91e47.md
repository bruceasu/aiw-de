# FD-030 独立设计审查

<!-- aiw-data: FD-030-design-review-20261023-a91e47.json -->

审查设计 Revision 2，来源事件 `FD-030-000002-design-requested`。结论：**changes-needed**。本报告仅为设计证据，不改变生命周期，不代表实现或运行通过。

事实：现有 Python 配置消费会注入扁平 Asset 文本；TypeScript/Go 以本地 Profile/SkillsRoot 加载，尚无完整 config/guard 接入。FD 选择独立 skill-files-v1、随受认证 Claim 内联资源及原文本摘要分离，符合 Issue 留给 FD 的工程裁量。无需新增下载端点。

## 阻断项

1. **D030-01：原生快照缺少可落实的写保护边界。** TypeScript 的 `sandboxForProfile` 允许 `danger-full-access`；同用户工具可修改文件 mode 并覆盖只读资源。非链接、新目录、提示词路径及只读位不能独立保证执行期间固定内容。请定义真正禁止写快照的沙箱/挂载与精确读范围，并把预检失败映射为不宣告能力或明确准备失败；也可改为受控读取。该风险需在设计中解决，不能仅作实现后警告。
2. **D030-02：冻结覆盖时机和继任路径未闭合。** Question 直接构造继任 Task，WorkspaceReplacement 则通过普通任务创建路径。请明确新建默认绑定只可在首次未 Claim 的准备阶段被匹配配置覆盖；Question 续行、技术重试及 WorkspaceReplacement 必须复制原绑定，不重新解析当前内置源。Claim 后不得改写绑定。
3. **D030-03：scope 与自报清单之间缺少权威核对规则。** manifest 仅摘要文件，包中的 source/owner/repo 仍可独立变化。请给出 Claim 固定引用与包的逐项核对、从任务/配置取得的 owner/repo、内置共享 source 的明确例外和重复/额外包拒绝规则。文件 hash 正确不等于当前任务有权消费该文件。

以上均为可由 Planner 在当前批准范围内收敛的工程合同，无新增业务范围要求。细节及静态来源见 JSON。

## 证据及限制

已作针对性 Get-Content/rg 静态读取；未运行测试、编译、服务、数据库、HTTP、模型、Worker、网络或沙箱验证。报告只反映设计充分性；三语言共同运行效果仍未验证。未更改源码、FD 状态或 Git，未委派其他 Agent，未读取实验目录或凭证。

