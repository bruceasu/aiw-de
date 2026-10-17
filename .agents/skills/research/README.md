# research：基于一手来源的资料调查

## 用途与适用场景

需要查清官方文档、源代码、规范或接口约束，或为设计选择收集事实时使用。
它将调查交给后台 Agent，让主会话继续独立工作。

## 输入与输出

- 输入：明确问题、已有资料、调查范围、允许的来源／网络范围及保存位置约定。
- 输出：一份带来源引用的 Markdown 研究记录，区分来源事实、推断和未解决问题。
- 优先存到仓库已有研究记录位置；没有约定时选择合理位置并说明。

## 调用示例

```text
$research Investigate this API constraint using primary sources.
```

## 边界与资料

入口要求后台研究 Agent，但不因此放宽子 Agent、网络或下载预算。不能调用外部
来源时明确限制，不把二手转述或推断伪装成已查证事实。研究结论可支持 Issue／FD，
不会自动创建工程任务、实现代码或证明运行正确。见 [SKILL.md](SKILL.md)。

## AIW auxiliary CLI

```text
aiw research [new] [TOPIC] [--source PATH] [--provider NAME] [--model NAME]
aiw research list
aiw research resume ID
aiw research recover ID
```

Command mode uses [the auxiliary adapter](references/auxiliary-cli.md): discuss,
then /run from a ready brief; results are new artifacts, never automatically run.
Teach courses are independent and resumable. Research command mode uses supplied
local sources, not automatic background/web research. Agent invocation above
retains its separate base method. See repository docs/usage/aiw-auxiliary.md.
