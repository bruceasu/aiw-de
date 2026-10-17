# handoff：准备可继续工作的交接文档

## 用途与适用场景

用户明确希望换会话或交给另一 Agent 继续时，压缩当前上下文，保留真实状态、已
确认决定、版本证据和下一步，减少接手方重复探索。

也适用于普通讨论、写作和学习，以及 research／prototype／edit-article／teach
辅助会话，无需创建 Issue／FD。teach 交接保留课程身份、教学位置、练习反馈和下一课。

## 输入与输出

- 输入：当前目标、已完成工作、证据、Issue／FD 链接、未决项和接手范围。
- 输出：保存到当前约定位置的精简交接文档，指向已有权威材料，不重复整份计划。

默认约一页，保留目标、决定、当前状态、下一步、权限与已消耗预算、阻断和证据。
适用时记录工作目录、分支、HEAD、未提交范围和观察时间。跨 Agent／环境时再补
必要工具、路径映射与未转移的本地资料，不自动安装或迁移。未知值明确标注。

## 调用示例

```text
$handoff Prepare a continuation brief for the next session.
$handoff Prepare a brief for another agent, including workspace and access requirements.
$handoff Summarize this course's stopping point, practice feedback and next lesson.
```

## 边界与资料

只提到“下一个 Agent”不自动触发。准备文档不等于派发新会话、批准计划、修改 FD
状态或提交代码；实际角色交接仍使用 FD 事件规则。未确认想法与已完成事实分开。
接手前核对当前规则、版本、原授权和真实状态；预算不会因换会话重置。进行中或
结果未知的模型请求、合并、提交和派发先核查证据，不能直接重放或接管。辅助会话
的 pending／锁使用已有恢复规则；交接文档不是新的授权或状态记录。
位置、内容和结束条件见 [SKILL.md](SKILL.md)。
