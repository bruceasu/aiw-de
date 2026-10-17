# setup-project：配置项目工程约定

## 用途与适用场景

用户明确要求建立或整理 AIW 项目约定时，检查仓库类型、现有 Agent 指令、Issue／FD
和文档结构，先形成具体配置提案，再应用获准范围。

## 输入与输出

- 输入：仓库现状、已有约定、目标 Agent 环境和用户要求。
- 输出：配置提案，以及确认后更新的本地指令／工作管理／领域说明等文档。

## 调用示例

```text
$setup-project Prepare AIW conventions for this repository.
```

## 边界与资料

调用其它 Skill、检查仓库或讨论流程不等于请求初始化。不要覆盖用户已有文档，
不自动创建 Issue、FD、外部 tracker 或运行安装／验证。标签和多上下文结构仅在
确有需要时提议。见 [SKILL.md](SKILL.md)、[本地流程](issue-tracker-local.md)
和 [领域文档](domain.md)。
