# issue-management：从用户问题到软件需求

## 用途与适用场景

澄清一个缺陷、功能或修改请求：用户要解决什么，是否值得做，系统应具备什么行为，
哪些规则和验收条件仍不明确。按缺口加载内部方法，而不是固定执行整套访谈。

## 输入与输出

- 输入：用户诉求、已有 Issue、来源、约束、例子和已确认决定。
- 输出：原 Issue Plan、未决问题、范围／业务拆分和决策；批准后可交给编号 FD。
- 术语不清时补充领域澄清；大型需求在同一 Plan 中保存决策依赖，不另建执行计划。

## 调用示例

```text
$issue-management Clarify this request and its acceptance examples.
```

## 边界与资料

Issue 不直接实施代码。创建、捕获、批准和推广遵守现有确认规则；提议不等于批准，
父 Issue 批准不自动批准子项。业务关键未知不能改名为工程问题后绕过。
见 [SKILL.md](SKILL.md) 与按需使用的
[决策依赖方法](references/decision-dependencies.md)。
