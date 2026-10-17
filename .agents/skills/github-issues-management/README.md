# github-issues-management：管理远程 GitHub Issue

## 用途与适用场景

用户通过自然语言要求查询、创建、修改、评论、标记或关闭 GitHub Issue 时使用。
它通过 aiw-github 插件操作远程记录，与本地 AIW Issue／FD 生命周期分开。

## 输入与输出

- 输入：明确仓库、目标 Issue／查询范围、期望动作，以及必要凭据和授权。
- 输出：当前远程状态、具体动作检查点，以及实际操作后的 Issue 编号／URL。

## 调用示例

```text
$github-issues-management Prepare an update for issue 42 in owner/repo.
```

## 边界与资料

现有 Issue 写入前先读取最新远程状态，不能依赖陈旧摘要。当前入口要求每次写入
展示检查点，并在当前会话收到 `confirm` 或“确认”后执行；网络和凭据规则仍适用。
不会顺带批准本地 Issue、合并 FD 或修改 OpenSpec。见 [SKILL.md](SKILL.md)。
