# publish-github-issue：发布本地 Issue／FD 的远程摘要

## 用途与适用场景

用户明确要求把本地 Issue／FD 发布或更新到 GitHub Issue 时使用。不要求存在
OpenSpec change；有 change 时可补充规格摘要，也兼容明确选择 change 的旧用法。

## 输入与输出

- 输入：明确的 Issue／FD、可选 change、目标仓库、现有映射与发布范围。
- 输出：远程 Issue URL 和本地映射记录，保留原始变更的权威归属。

## 调用示例

```text
$publish-github-issue Publish the selected OpenSpec change to owner/repo.
$publish-github-issue Publish ISSUE-00001 and its selected FD progress to owner/repo.
$publish-github-issue Publish FD-073 as an independent GitHub Issue.
```

## 边界与资料

必须明确请求外部发布；目标不唯一时不能猜。发布失败不改变 FD 状态、归档或
清理资源；不自动关闭 Issue 或把远程投影变成工程计划。凭据、联网和远程写入
仍遵守现有授权。具体映射和投影规则见 [SKILL.md](SKILL.md)。

默认一个本地 Issue 对应一个远程 Issue，关联 FD 更新自己的来源块；没有 Issue
的 FD 可独立发布。明确要求分开跟踪时可保留独立 FD 映射。映射位于
`docs/external/github/{issue|fd}/<canonical-id>.json`，不随本地归档失效。

保护人工内容和其他 FD 的块。写入前保存 pending 凭据，远程结果未知或映射保存
失败时先恢复，不自动重复创建。映射冲突或失效必须报告，不能悄悄换目标。
旧 change 映射和 helper 保留兼容；迁移需明确来源与授权，不自动删除旧证据。
完整规则见 [发布与恢复说明](references/publication.md)。
