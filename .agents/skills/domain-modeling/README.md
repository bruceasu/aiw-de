# domain-modeling：澄清概念与业务语言

## 用途与适用场景

当术语含义冲突、概念归属或关系不清、状态和不变量存在不同理解时使用。
例如区分“实现完成”“本地交付”和“发布就绪”；日常读取词汇表不必启动完整建模。

## 输入与输出

- 输入：当前问题、概念冲突、已确认规则、可用来源和写入范围。
- 输出：定义与区别、关系／归属、适用的状态／不变量、正常和边界例子、来源及未知。

## 调用示例

```text
$domain-modeling Clarify completion, delivery, and release readiness.
```

## 边界与资料

Issue 的 Requirement 模式只使用宿主快照，不扫描代码或写稳定文件。工程模式按授权
维护词汇；稳定行为归 specs，当前方案归 FD，重要长期取舍才记 ADR，以链接避免重复。
见 [SKILL.md](SKILL.md)、[词汇格式](CONTEXT-FORMAT.md) 和 [ADR 格式](ADR-FORMAT.md)。
