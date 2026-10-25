# 个人秘书 Skills

此目录只保存个人秘书自己的、按需加载的助手方法，不复用 `workers/` 下专业 Worker 的 Skills。秘书先读取目录清单，再按当前请求加载相关 `SKILL.md`；Skill 是指导文本，不会添加工具、文件范围、权限或代码执行能力。

## 当前 Skills

- `daily-life-discovery`：通过简短对话梳理用户主动提出的习惯、偏好和日常摩擦点。
- `goal-discussion`：在当前对话中讨论个人、产品或开发需求，帮助用户先想清楚再决定是否正式化。
- `personal-task-planning`：把用户提供的任务整理为清晰的下一步和可行顺序。
- `delegation-planning`：讨论是否需要专业委托并准备边界清楚的交接；不会自行启动委托。
- `handoff`：为其他人或 Agent 起草有依据的交接文稿；不会自行发送或分派。
- `personal-research`：用秘书现有的搜索与阅读能力核实资料、分析证据并给出来源。
- `teach`：围绕用户想学的内容进行简明讲解和可选练习。
- `edit-article`：起草或修改报告、文章和备忘录。
- `polish-and-translate`：总结、扩写、润色或翻译文本，适配目标读者和语气。

需求讨论与交接默认只在当前对话中完成。Skill 只提供方法，不会自动建立平台工作、调用委托、发送消息或增加权限；只有用户明确要求后，秘书才按系统提示和现有授权处理后续动作。

## 搜索与筛选

搜索日期：2026-10-25。曾参考公开 Skill 搜索目录和以下候选方法，再按秘书现有的对话、`search`、`read_url` 等能力筛选和改写；没有运行 Skill 安装器。目录内容以本项目维护的秘书版流程为准。

- [Skills 搜索目录说明](https://www.skills.sh/docs)：可发现可复用流程，但安装前应审阅内容。这里仅用于发现与审查，没有运行安装器。
- [daily-life-discovery](https://github.com/magnus919/agent-skills/tree/main/daily-life-discovery)：适合对话式整理生活习惯，强调同意、隐私和区分观察与推断；据其 [MIT 许可](https://github.com/magnus919/agent-skills/blob/main/LICENSE.md)审阅。
- [task-planner](https://github.com/xcrrr/claude-skills/tree/main/skills/productivity/task-planner)：提供拆分任务、明确下一步和排序的思路；原内容含团队/日历假设，未将这些假设带入秘书 Skill。仓库声明 [MIT 许可](https://github.com/xcrrr/claude-skills/blob/main/LICENSE)。
- [web-researcher](https://github.com/xcrrr/claude-skills/tree/main/skills/research/web-researcher)：提供来源核验和综合研究的思路；原流程有秘书当前工具不支持的假设，因此这里限制为现有搜索和网页读取能力。仓库声明 [MIT 许可](https://github.com/xcrrr/claude-skills/blob/main/LICENSE)。

未选用需要 Gmail、日历、Drive 等连接器的日程仪式 Skill；也未选用依赖长期数据库/全面个人资料采集的通用 Personal Assistant Skill，或包含无条件服从指令且许可不清楚的候选项。
