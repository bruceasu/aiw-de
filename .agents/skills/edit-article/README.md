# edit-article：改善文章结构与表达

## 用途与适用场景

已有文章草稿需要重组结构、改善连贯性或压缩冗余时使用。先按标题拆分章节，分析
读者理解前后内容所需的信息依赖，再逐节修改。

## 输入与输出

- 输入：文章草稿、目标读者、表达目的和需要保留的内容／风格。
- 输出：待确认的章节结构，以及修改后的文章或明确的修改建议。

## 调用示例

```text
$edit-article Improve the structure and clarity of this draft.
```

## 边界与资料

当前入口要求先确认章节安排，并把每段限制在最多 240 个字符。它不是自动事实
核查、资料研究或发布工具；需要这些操作时应另行明确范围。不会隐式创建 FD、
OpenSpec 记录或 Git 提交。见 [SKILL.md](SKILL.md)。

## AIW auxiliary CLI

```text
aiw edit-article [new] [TOPIC] [--source PATH] [--provider NAME] [--model NAME]
aiw edit-article list
aiw edit-article resume ID
aiw edit-article recover ID
```

Command mode uses [the auxiliary adapter](references/auxiliary-cli.md): discuss,
then /run from a ready brief; results are new artifacts, never automatically run.
Teach courses are independent and resumable. Research command mode uses supplied
local sources, not automatic background/web research. Agent invocation above
retains its separate base method. See repository docs/usage/aiw-auxiliary.md.
