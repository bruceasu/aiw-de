# writing-great-skills：创建与维护 Agent Skills

## 用途与适用场景

需要创建、改善、只读审查或跨 Agent 平台适配 Skill 时使用。重点是任务选择准确、
指令与资源清晰、边界可维护，以及能够审查实际产物质量。

## 输入与输出

- 输入：使用场景、目标平台、现有 Skill、典型请求、已知失败和授权范围。
- 输出：相应模式的指令／支持材料改动，或审查发现、适配说明及验证限制。

## 调用示例

```text
$writing-great-skills Review this skill without changing files.
$writing-great-skills Improve this skill for the supplied use cases.
```

## 边界与资料

显式选择模式，只读审查不变成实施。原生平台 creator 是可选辅助；创建／修改文件
不证明触发准确或实际效果变好。运行评估、安装或发布仍需相应授权，不能把它们
隐含在文档修改中。方法和模式见 [SKILL.md](SKILL.md)。
