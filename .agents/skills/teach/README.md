# teach：跨会话教学与练习

## 用途与适用场景

围绕一个学习目标持续学习知识或能力，而不只是解释一句术语。先理解学习动机与
现有水平，再安排短小课程、互动练习、回忆和间隔复习。

## 输入与输出

- 输入：想学什么、为什么学、已有经验、学习偏好和现有教学工作区。
- 输出：学习目标 `MISSION.md`、来源 `RESOURCES.md`、`lessons/*.html` 课程、
  `reference/*.html` 速查材料、`learning-records/*.md` 及可复用教学 assets。
- 后续课程根据既有学习记录继续，任务和难度围绕学习目标展开。

## 调用示例

```text
$teach Help me learn state machines through short lessons and practice.
```

## 边界与资料

需要显式选择教学模式。资料查找、生成文件和打开浏览器仍遵守仓库授权；更改学习
目标先按入口要求确认。不隐式创建 FD、修改产品实现或把完成课程当作工程验收。
见 [SKILL.md](SKILL.md)、[目标格式](MISSION-FORMAT.md)、
[资源格式](RESOURCES-FORMAT.md) 和 [学习记录格式](LEARNING-RECORD-FORMAT.md)。

## AIW auxiliary CLI

```text
aiw teach [new] [TOPIC] [--source PATH] [--provider NAME] [--model NAME]
aiw teach list
aiw teach resume ID
aiw teach recover ID
```

Command mode uses [the auxiliary adapter](references/auxiliary-cli.md): discuss,
then /run from a ready brief; results are new artifacts, never automatically run.
Teach courses are independent and resumable. Research command mode uses supplied
local sources, not automatic background/web research. Agent invocation above
retains its separate base method. See repository docs/usage/aiw-auxiliary.md.
