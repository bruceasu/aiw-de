# resolving-merge-conflicts：有依据地处理合并冲突

## 用途与适用场景

已有 merge／rebase 冲突现场时，结合目标 FD、双方历史和变更意图处理语义冲突。
完整 FD 自动交付发生实际冲突时，可按主流程授权进行一次有界尝试。

## 输入与输出

- 输入：冲突状态、FD、双方提交历史、相关代码和当前恢复范围。
- 输出：有依据的解决修改及说明，或保留现场的未决冲突与阻塞记录。

## 调用示例

```text
$resolving-merge-conflicts Inspect and resolve this in-progress FD merge.
```

## 边界与资料

不盲选一侧，不把 stage／commit 当作语义解决。无法安全处理时保留合并状态、未
合并条目及明确已解决修改，不擅自 abort、reset 或清场。恢复改变已评审实现后，
按主流程重新取得独立评审；不默认运行测试或推送。见 [SKILL.md](SKILL.md)。
