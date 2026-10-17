# triage：整理用户反馈队列

## 用途与适用场景

多个反馈来源、Issue 或外部 PR 需要整理时使用。它保留用户原始问题，识别信息缺口
与相关工作，给出影响、优先级理由和下一步，不重复完整的软件需求分析。

## 输入与输出

- 输入：待整理队列、来源内容、已有记录与允许访问的范围。
- 输出：有来源的队列表、重复／相关候选、缺失信息、建议动作与负责人。

## 调用示例

```text
$triage Sort these reports and recommend the next action for each.
```

## 边界与资料

已有代码或历史拒绝不自动关闭请求；深度澄清交 Issue，设计交 FD，PR 正确性交独立
评审。默认本地建议，外部读取／评论／标签／关闭需要明确授权。Brief 不是权威执行
规格。见 [SKILL.md](SKILL.md)、[交接摘要](AGENT-BRIEF.md) 和
[历史范围决定](OUT-OF-SCOPE.md)。
