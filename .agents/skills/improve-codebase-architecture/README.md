# improve-codebase-architecture：寻找架构改善机会

## 用途与适用场景

现有系统职责分散、接口负担重或难以理解时，先探索架构摩擦，列出可以深入讨论的
模块改善候选。也可由当前FD在出现明确结构障碍时按需加载，不要求扫描整个项目。

## 输入与输出

- 输入：代码范围、领域词汇、已有架构决定和关注点。
- 输出：默认简短Markdown，对比现状、局部改善和有必要时的较大重构，说明证据、
  成本、风险及下一步；FD内结果写回已有设计，不另建计划。
- 请求HTML时才写`.ai/tmp/architecture-review-<timestamp>.html`，使用离线资源，
  不依赖CDN、不自动打开。额外Agent、grilling和多方案设计均不是默认步骤。

## 调用示例

```text
$improve-codebase-architecture Find improvement opportunities in this package.
```

## 边界与资料

探索不等于实施。领域文档修改、进一步子 Agent 设计和运行操作仍遵守各自范围与
授权，不因为生成报告而获得重构权限。超出当前FD的调整仅作为后续建议，不自动
创建新需求或为方便测试而扩大重构。见 [SKILL.md](SKILL.md)。
