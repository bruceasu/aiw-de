# ask-asu：流程导航与按需咨询

## 用途与适用场景

不清楚当前 Issue／FD 处于哪个阶段、由谁处理、下一步应做什么时使用。它读取实际
状态、交接事件和授权范围，给出路由建议；不是自动执行入口。

默认导航不额外调用模型。明确需要第二意见且已有授权时，可以选择一次宿主子Agent
或aiw ask咨询；不会自动同时调用两者。子Agent不意味着更强模型或更高推理强度。

## 输入与输出

- 输入：请求、Issue／FD ID、已有证据和阶段限制。
- 输出：当前阶段、下一角色／Skill、建议命令及阻塞原因。
- 咨询额外输出：具体问题、上下文与授权边界、已知模型设置、实际结果及不确定性。

## 调用示例

```text
$ask-asu Explain the current stage and next step for FD-XXX.
$ask-asu Prepare a bounded second-opinion question for this design; do not call a model yet.
$ask-asu Use one authorized read-only host agent to assess this specific trade-off.
```

这是宿主 Skill 调用。需要实际实施时，明确转入 fd-workflow。

## 边界与资料

导航不修改文件、不执行建议命令或派发角色。咨询另需实际调用、上下文传输和可能
历史落盘的授权；缺少授权先准备问题，不自动执行。aiw ask目前没有单次model/provider/
reasoning参数，使用已有配置；auto可尝试多个后端，因此本模式要求已知固定provider。
它会尝试写入~/.aiw/ask历史，目录授权不保证强隔离，不能当成完全不落盘的只读命令。

失败不自动重试、切换模型或递归派发；咨询仅为建议，不替代人类决定、fd-review，
也不重置测试／修复预算或diagnosis-only。能力与使用边界见
[consultation.md](references/consultation.md)。
行为合同见 [SKILL.md](SKILL.md)，流程参考见
[development-workflow.md](references/development-workflow.md)。
