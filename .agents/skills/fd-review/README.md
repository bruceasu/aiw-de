# fd-review：独立评审编号 FD

## 用途与适用场景

对编号 FD 的实际代码、配置或文档实现进行独立审查，核对正确性、仓库标准、验收
要求和证据。它检查实现本身，不只是检查交付物是否存在。

## 输入与输出

- 输入：FD、精确角色交接事件、Worker 报告、实际 diff、规格和相关证据。
- 输出：发现或通过结论，中文评审报告与匹配 JSON，以及相应正式 FD 事件。

## 调用示例

```text
$fd-review Review FD-XXX against its acceptance and current implementation.
```

## 边界与资料

Reviewer 必须独立于 Worker 会话；不修改实现或执行合并。可检查相关测试事实，
但不自动运行测试、增加覆盖率门槛或再启动一整套 code-review。通过不代表未执行的
行为已经验证，也不授权发布。见 [SKILL.md](SKILL.md)。
