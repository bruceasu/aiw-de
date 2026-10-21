# 前言

|Worker| 类型|推荐语言|原因|
|------|-----|-------|---|
|Secretary Agent|Python|对话、工具调用、任务规划|
|Requirement Analyst|Python|需求理解、结构化分析|
|Software Architect|Python|多阶段推理、工具协作|
|Coding Agent|Go / Python|优先复用 Codex CLI|
|Code Review Agent|Python|模型推理和代码分析|
|Deployment Engineer|Go|低依赖、易分发|
|Build Engineer|Go|调用 Maven、Jenkins、Git|
|Scheduler|Java|持久化、可靠调度|
|Control Plane|Java|事务、权限、状态管理|
|Web Console|Java + 少量 JS|不必引入完整 Node 后端|

Typescript 作为原型还是比较不错的。

Python 与 TypeScript 的 AI 生态对比
|维度|Python|TypeScript|优势|
|----|------|----------|---|
|LLM API 集成|成熟|成熟|持平|
|OpenAI Agents SDK|官方支持|官方支持|持平|
|多 Agent 编排|非常丰富|丰富|Python|
|RAG / 知识库|非常丰富|丰富|Python|
|Embedding / 向量检索|非常成熟|成熟|Python|
|本地模型推理|很强|较弱|Python|
|模型训练、微调|绝对优势|很有限|Python|
|MCP 工具集成|成熟|成熟|持平|
|Web / 实时交互|成熟|很强|TypeScript|
|浏览器自动化|成熟|很强|TypeScript|
|Agent Web UI|成熟|很强|TypeScript|
|CLI 工具开发|成熟|成熟|取决于需求|
|部署与依赖管理|较复杂|较复杂|无明显赢家|
|静态类型约束|可选、逐步增强|原生开发体验更好|TypeScript|

