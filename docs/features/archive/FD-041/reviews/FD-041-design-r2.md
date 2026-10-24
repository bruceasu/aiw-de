# FD-041 独立设计定向复核 r2

结论：**ready**。提交 `476361b` 中的三项修正已消除 r1 的重大设计缺口，可据此继续设计交接。本结论仅针对 DR-1/2/3 的设计修正，不是实现审阅、运行通过或部署许可。

<!-- aiw-data: {"schema":"aiw.fd.evidence.v1","kind":"design-review","fd":"FD-041","source_event":"FD-041-000002-design-requested","session":"fd041-design-recheck-20261024-15e8a3cb","recommendation":"ready","reviewed_commit":"476361b","rechecks":["DR-1","DR-2","DR-3"],"unresolved_findings":[]} -->

- 复核对象：[FD-041_FEATURE.md](../FD-041/FD-041_FEATURE.md)，提交 `476361b` 的对应三处设计。
- 前次报告：[FD-041-design-r1.md](FD-041-design-r1.md)。本报告保留前次发现及其历史，不改写 r1。
- 独立 Session：`fd041-design-recheck-20261024-15e8a3cb`，不同于 Planner `composable-role-20261024-cf71803a`。
- 实际来源事件：`FD-041-000002-design-requested`。未 claim/emit，未修改 FD、实现或回执。

## 三项修正

| 发现 | 当前设计证据 | 结论 |
|---|---|---|
| DR-1：旧角色 Skill 的组合约束 | FD 第 42 行明确保留默认 primary Skill、原 instruction 及原任务类型、输入、冻结来源和职责限制；目录可判定的 resultKind/readOnly 冲突在编译、预览、保存时拒绝。其余自然语言冲突经原问题/缺口路径报告，不能视为成功。空白组合与旧模板分别说明。第 73 行将这些约束纳入实施项。 | 已解决设计缺口。没有把类型检查夸大为完整语义检测，也没有授权新入口覆盖原角色约束。实现审阅仍须核对实际入口提示和拒绝路径。 |
| DR-2：整体交付要求 | 第 38 行增加独立 delivery；第 40 行明确编译进既有 outputContract，和单步 completion 分别保存，并保留 REPORT/VERDICT/WORKSPACE 外层格式；第 58 行要求表单独立必填。 | 已解决。角色级交付和单步完成条件已有明确归属，不需扩展 v1 Candidate 字段集合。 |
| DR-3：旧服务解码与回退 | 第 40 行保留 Method.name 原机器 id 合同，将人类名称移至 role.json，并通过当前 Owner 的 state.roles 摘要显示；第 60 行不再通过改变 name 校验承诺兼容，明确回退后领取/执行未经运行验证，由部署证据决定是否暂停，固定内容不得自动重建、删除或换成默认角色。 | 已解决本次静态设计缺口。r1 指出的旧 Method.name 解码失败前提已移除；没有声称旧服务执行已被验证。 |

## 边界与剩余验证

事实：本次只定向读取 FD 第 26–65、70–101 行，核对三处修正和对应 Work Item/验收。没有重新审阅全设计或其他代码。用户已解除执行粒度 Gate；反馈证据本身不提供新的运行或工具授权。

推断：在按上述约束实现的前提下，三项修正与 r1 已读取的既有 Candidate、Skill 文件包及 Python 动态配置合同相容。该推断不等同于实现正确或真实任务遵循流程。

仍未验证：确定性编译、角色级 delivery 在全部读取/复制路径上的保留、目录冲突校验及语义冲突报告、外层事务和未知保存结果、回退后的真实领取/续行。它们留待既定实现与独立实现审阅，不构成本次新增设计阻断。

实际命令：一次 PowerShell 内联 `python -X utf8 -`，仅按行读取上述 FD 范围。之后只写入本报告及同名 JSON，未运行命令验证、测试、编译、模型、服务、网络、其他代理、工作流命令、提交或归档。
