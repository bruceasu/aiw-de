# grill-with-docs：结合资料检验讨论质量

## 用途与适用场景

在需求初步理解、关键规则／方案讨论、决策交接等节点，用已有资料追问假设、范围、
例外和验收依据。它提高当前讨论的质量，不要求每个回合重新进行完整访谈。

## 输入与输出

- 输入：当前讨论检查点、资料、已确认答案和剩余缺口。
- 输出：有依据的结论、开放问题、风险和后续动作；提议答案保持为提议。

## 调用示例

```text
$grill-with-docs Check this requirement against the supplied notes.
```

## 边界与资料

Issue 模式由 issue-management 按节点选用，结论回原 Plan，遵守快照和提问限制。
工程模式中的持久写入按确认范围进行；访谈不自动批准、实施或发布。
行为合同见 [SKILL.md](SKILL.md)。
