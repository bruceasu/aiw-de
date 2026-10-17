# grill-me：显式启动深入访谈

## 用途与适用场景

这是要求进行 grilling 访谈的简短入口。适合直接请求对计划、想法或设计进行深入追问；
它没有另一套独立分析或工程流程。

## 输入与输出

- 输入：希望检验的想法、计划和当前约束。
- 输出：grilling 的交互问题和用户实际确认的结论。

## 调用示例

```text
$grill-me Challenge this plan before I commit to it.
```

## 边界与资料

需要显式调用。等待用户作答，不自动写实现或生命周期记录；Issue 默认选用的
grill-with-docs 不意味着自动启动 grill-me。见 [SKILL.md](SKILL.md)。
