# FD-034 独立实现审阅 r1

<!-- aiw-data: FD-034-implementation-review-r1-20261024-c6b9087d.json -->

结论：**changes-requested**。发现 1 项影响真实使用路径的问题；这是本轮实现审阅第 1 次未通过，不是测试或运行验证失败。

Reviewer Session：`fd034-reviewer-20261024-c6b9087d`，独立于 Worker `secretary-desktop-20261024-a6529740`。已读取最新回执并实际认领 `FD-034-000005-implementation-ready`。比较基线为 `8e7342c852ed42dbcad94ba6ca90b435ec5cdc8a`，完整实现提交为 `ebeacaa1872178d69411eb59cfcebe906d3e40b1`；Worker 证据提交为 `445623f`。相关实现文件在审阅时与此提交一致，其它私人秘书/Python/模型/启动脚本及 FD-033 改动排除并保留。

## 必须修正

### R034-01 · P2 · 准备成功后提供的进度入口不包含本次创建的任务

- 位置：`src/main/resources/static/ui/configurations.js:139`、`:140`，以及成功提示所在的准备提交回调；任务创建契约见 `src/main/java/me/asu/aidev/execution/config/AgentFactory.java:91`。
- 当前要求：FD Solution 要求提交结果提供任务入口，Acceptance 2 要求以可理解的结果与任务链接呈现；用户需要知道准备之后如何继续，而不是得到另一个看不到该任务的页面。
- 静态事实：本入口创建的 `CF-*` 任务使用 `subjectType="configured-plan"`。READY 卡片和提交提示却将用户引向“查看关联计划与进度”。对于已有 WorkflowPlan，链接为 `/ui/plans/{id}`，其详情接口 `WorkflowPlanController.get()` 调用 `WorkflowPlanService.steps()`；后者仅查询 `SUBJECT="flexible-plan"`，因此不包含该 `CF-*` 任务。非 WorkflowPlan 的政策链接回到 `/ui/software-development` 总页，也没有选定本次任务。卡片只输出不可点击的任务编号，准备记录仍只记录 READY，不能据此查看这项任务的实际执行进度。再次载入已运行或已完成的 READY 记录还会继续显示“等待 Worker 处理”。
- 影响：普通用户完成新表单后，按页面给出的下一步仍无法找到本次任务，容易误将另一个计划的状态当成本次任务进度。仅隐藏 JSON、改名或显示编号不能落实所承诺的可继续使用路径。
- 最小修正：提供针对该精确 `taskId`、经当前 Owner 验证的实际任务状态或真实任务入口；可在本页增加可刷新查看的任务结果卡片，不需要改动原任务的 subject、执行或授权语义。原计划链接可保留作为查看政策所属计划的辅助入口，但文案不能声称那里包含该配置任务进度。根据实际任务状态显示等待、执行中、已完成/失败等，或明确区分“准备时已创建”和当前状态未知。

## 其它验收证据

- 已读取实际 Java、HTML、两份 JS、局部 CSS、共享导航、附近说明及稳定规格，不只依赖 Worker 报告。普通准备表单不要求 JSON、内部身份或版本，分区切换、默认方法、中文方法和产出说明均有实现；原始响应没有被写入页面。
- `ConfigurationUiOptions` 先验证 currentOwner，政策、配置、准备输入和个人资料按 Owner 查询；上游由 execution_context 关联本 Owner 政策，并在表单按仓库限定。个人资料列表只输出必要元数据，标题只取显式 Markdown 标题；完整原 Intent 是该 Owner 的恢复输入。PreparedConfigRef 包含原服务校验所需完整字段。
- `supportedProfiles()` 与准备入口共用批准/provider/runtime/effort/fast 筛选。准备请求仍由原服务进行政策、引用、版本和 Worker 准入检查，没有新增权限授予。
- 载入原记录后，相同语义重试复制原 Intent 并只更新 generation，保留依赖列表原顺序；修改内容计算下一版本。普通刷新不重填表单或替换原政策、模型、引用；未知准备结果先查询后显式重试原对象，提交中和待核对时相关按钮/字段确实禁用。
- 方法预览同时核对输入指纹及序号，编辑在途草稿会丢弃旧响应；保存提交预览的原 Candidate，资料登记固定原正文及摘要。保存/登记响应未知时持有原对象，state 核对后才允许显式重试。成功返回后的列表刷新失败保留成功与返回引用，保存后“用于准备任务”按钮路径可达。默认方法的工具推导与原实现一致，额外 Skill 排除基础已有引用。
- 界面沿用现有 Console 颜色、表单和卡片，支持有名称的字段、键盘分区和状态播报。未把布局偏好或未授权运行验证当作额外 Gate。

## 命令、范围和未验证内容

实际执行的是本地静态读取和工作流操作：`Get-Content -Encoding UTF8`、`Select-Object`、`Select-String`、限定路径的 `rg`/`rg --files`、`ConvertFrom-Json` 解析目录摘要、`git status --short`、`git diff --stat/--name-only/实际文件差异`、`git log -5 --oneline`、`git rev-parse`，以及 `aiw fd show/claim` 和对应 CLI help。几个按惯常名称假设的目录/页面不存在、一个 PowerShell 路径通配形式不适用，随后按实际位置读取；这些仅为静态检索，未启动应用或改动业务数据。首批读取的默认编码不正确，后续全部以 UTF-8 读取关键证据和源码。

Worker 报告记录的 JDK 25 离线 Maven compile（292 个生产源）和两份 `node --check` 成功被作为已有证据审阅，本 Reviewer 没有重复运行，也没有把它们称为本会话执行的检查。未发现实现与这些已报告的检查范围矛盾，但语法编译不能验证上述任务入口语义。

未运行测试、浏览器/Thymeleaf、数据库、服务、登录/准备/候选保存、异步中断场景、模型、网络下载、打包、安装、重启或发布；没有派生下级 Agent。真实布局和会话/数据库流程仍未验证。以上问题由源码调用路径即可确认，不需要额外运行权限。修正后应针对实际改动再次交独立静态审阅。
