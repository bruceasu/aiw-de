# fd-workflow：编号 FD 主流程

## 用途与适用场景

创建／完善 FD，或执行设计已就绪的工作项，完成独立评审、本地交付和归档。
FD 保存唯一工程计划；角色包括 PM、Planner、Worker 和独立 Reviewer。

## 输入与输出

- 输入：明确请求或已批准 Issue、FD ID、规格、工作项及阶段限制。
- 输出：编号 FD、实现、实际证据、独立评审记录，以及范围允许时的本地交付与归档。

## 调用示例

```text
$fd-workflow FD-XXX auto
$fd-workflow FD-XXX implementation only; no review or merge
$fd-workflow FD-XXX auto with test
```

这些是宿主请求，不是新 CLI 参数。“带测试”等同义表达也能选择测试支持。

## 边界与资料

完整实现默认自动继续交付；更窄限制优先。工作区、当前独立通过事件和证据必须
满足交付条件。默认不授权测试、网络、push 或部署；测试支持有独立命令预算和诊断
降级规则。见 [SKILL.md](SKILL.md)、[规划](references/planning.md)、
[实现](references/implementation.md) 和 [可选测试](references/testing.md)。
