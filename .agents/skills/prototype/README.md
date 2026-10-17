# prototype：用一次性原型回答设计问题

## 用途与适用场景

在正式实现前验证“逻辑／状态是否合理”或“界面应该怎样呈现”。先固定问题，再选
逻辑终端原型或 UI 多方案探索；不是直接构建可发布产品。

## 输入与输出

- 输入：待验证问题、上下文、可用技术栈和允许创建／执行的范围。
- 输出：明确标记为一次性的最小原型、启动方式、可见状态和它支持／未支持的结论。
- 逻辑分支可交互推动状态，UI 分支可比较不同界面变体。

## 调用示例

```text
$prototype Explore whether this state model is understandable.
$prototype Compare UI approaches for this interaction.
```

## 边界与资料

默认内存状态，避免真实持久化和生产影响。运行、依赖、外部资源及保存原型分支的
操作仍遵守仓库范围和授权。已接受结论交回原 Issue／FD，再进入正式实现；不能
把临时代码直接当成交付或测试证据。见 [SKILL.md](SKILL.md)、
[逻辑分支](LOGIC.md) 和 [UI 分支](UI.md)。

## AIW auxiliary CLI

```text
aiw prototype [new] [TOPIC] [--source PATH] [--provider NAME] [--model NAME]
aiw prototype list
aiw prototype resume ID
aiw prototype recover ID
```

Command mode uses [the auxiliary adapter](references/auxiliary-cli.md): discuss,
then /run from a ready brief; results are new artifacts, never automatically run.
Teach courses are independent and resumable. Research command mode uses supplied
local sources, not automatic background/web research. Agent invocation above
retains its separate base method. See repository docs/usage/aiw-auxiliary.md.
