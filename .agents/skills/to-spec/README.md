# to-spec：维护稳定能力规格

## 用途与适用场景

把已确认的 Issue／FD 决定整理成稳定能力规则及可观察场景，或独立维护规格文档。
Planner 在 FD 内使用同一写作方法，不必为每次规格变化再启动一个独立流程。

## 输入与输出

- 输入：相关能力、已确认决定、现有规格和明确写入范围。
- 输出：更新的规则／场景、提议与当前状态说明，以及需要 Planner 协调的受影响项。

## 调用示例

```text
$to-spec Update the stable spec from these confirmed decisions.
```

## 边界与资料

不实施代码、不拆工程任务、不派发角色或改 FD 状态。只有明确请求才创建／维护
OpenSpec change；稳定规格修改本身不要求开启完整 FD 生命周期。关键缺口应报告，
不能从模板中猜测业务规则。见 [SKILL.md](SKILL.md)。
