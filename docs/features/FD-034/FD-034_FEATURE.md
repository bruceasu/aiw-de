# FD-034: 专业配置页面改为可理解的任务准备与方法表单

**Status:** In Progress
**Revision:** 6
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户反馈“专业配置与准备”页面输出大量 JSON，没有使用价值且干扰阅读；“准备一个软件任务”和“组合与声明式候选”都不知道如何使用。当前页面把意图 ID、版本、世代、政策版本等内部字段设为人工必填，把准备状态直接 JSON.stringify 到 pre，并要求使用者手写 Assembly/Candidate/Intent JSON。

本需求与 FD-033 的缺省动作授权范围独立；该范围问题仍待用户选择，本 FD 不替用户决定或修改动作权限。

## Options and decision

- 给原 JSON 表单增加注释：仍要求理解接口，不能解决普通人无法使用的问题。
- 只隐藏 JSON 输出：缺少任务步骤、选择项和可恢复结果，仍然不能使用。
- 保留现有业务协议，用面向人的任务准备/方法装配表单和状态卡片替换调试界面；增加当前 Owner 的只读选择项，系统维护请求身份和版本。采用此方案，不新增模型调用、自动授权或业务编排引擎。

## Solution

### 页面流程

复用 Web Console 外壳、颜色、字体和表单/卡片。页面切换显示“准备任务”“准备记录”“自定义工作方法”，不堆叠长页。默认任务入口给出三步说明：确认计划执行范围 → 描述任务并选择方法/批准模型 → 查看已创建任务或缺口。自定义方法明确为可选，不是准备普通任务的前置条件。

任务表单选择当前 Owner 已有执行政策的计划，自动带入政策版本、可用仓库和已知固定基线；没有可靠基线时明确要求填写完整提交并说明用途，不能猜 HEAD 或执行 Git。工作方法采用中文名称并说明产出是代码、报告还是审查结论；模型从已有批准且受准备入口支持的配置中选择。可选正式上游任务从当前 Owner 已完成任务选择。意图 ID/版本/世代由界面维护，不再作为人工字段。

提交结果显示中文状态、任务入口、缺口和恢复动作，不打印原始响应 JSON。READY 仅表示任务创建；可在结果卡片展开并刷新本次精确 taskId 的真实任务状态，读取同样受当前 Owner 验证；计划定义链接不能声称包含 configured-plan 任务进度。BLOCKED 可载入同一记录修正或显式重试。未修改内容时使用同一意图版本及当前准备世代，修改内容后建立下一版本；提交中禁用重复按钮。响应未知时保留原意图，要求先刷新核对，不能自动换 ID/重放；记录列表能够从服务器载入原输入和已知状态。刷新失败不能把先前已成功的保存/准备描述为失败。

选择项刷新不能覆盖用户已载入/提交的原 Intent；原引用或模型不再可选时应明确显示不可用，不默默替换。异步候选使用输入 fingerprint 和请求序号核对，旧响应不能覆盖新草稿；保存与准备捕获自己的原对象，成功后只更新该原对象的结果。上游选项必须从当前 Owner 的 execution_context 所属政策关联，并按所选仓库过滤。

### 可选自定义方法

“组合与声明式候选”改称“自定义工作方法（可选）”：选择基础方法与仓库，勾选已登记的额外 Skill/知识，填写可选方法说明，预览结构化中文摘要。系统装配 Assembly；复用已有配置时可直接选用，否则显式保存原 Candidate，再选入准备表单。预览后修改任一输入即使旧预览失效，不提交其它草稿的旧 Candidate；不自动调用模型或准备任务。

登记参考资料采用名称、类型、仓库和正文表单，生成短引用及初始版本、计算摘要；名称作为内容标题一起保存，明确显示。只提交用户选定内容，按原有 Owner/仓库校验。原始 JSON 输入、完整返回 JSON 和预留职责大表从普通流程移除；不改变接口能力，也不把未实现职能显示为可执行方法。

### 服务及兼容边界

新增小型只读 UI 选项装配组件：在 currentOwner 验证后返回当前 Owner 的政策/仓库/已知基线、已完成上游任务、准备原输入、已保存配置的精确 PreparedConfigRef、可引用 Skill/知识元数据，以及已批准且支持的模型。只返回下拉框需要的标题/引用/版本/摘要，不返回资料正文、凭证或私人聊天。内置公共 Skill 仍可选；个人资料必须按 Owner 和仓库过滤。现有 state 返回键保持兼容，以附加 uiOptions 提供这些数据；原有写接口和业务验证不变。

模型筛选提取为 AgentFactory 的纯读取方法，准备入口与界面共用，避免界面推荐后端必然拒绝的配置。页面仅固定用户选择，不静默切换模型、工具、范围、仓库或基线。固定引用和实际准入仍由原服务验证；默认预算仅记录的 FD-032 行为不变。无新表、迁移、依赖、公共写接口或后台自动请求重试。

## Scope

本 FD 修改 configurations.html/js、新页面局部样式、只读 UI 选项组件、state 附加元数据及必要的模型筛选复用，更新附近使用说明和稳定 UI 规格。沿用当前主工作区，仅提交本 FD 文件/差异；FD-033 草案与其它私人秘书/模型任务改动保留，不纳入提交。

不改授权语义、不执行迁移/模型/服务/浏览器，不启动测试、下载依赖、打包或发布。存在 Owner/仓库数据选择和异步草稿恢复设计，实施前做一次独立静态设计审阅；最终另做独立实现审阅。

## Work items

- [x] 1.1 明确页面步骤、只读 Owner 选项及异步草稿恢复设计，完成独立设计审阅。规模小、难度中，无依赖；证据为本设计及 FD-034-design-review-20261024-742db6c1 双报告，ready，已纳入其原 Intent/fingerprint/上游过滤约束。
- [x] 1.2 提供 Owner 过滤的表单选择项，并复用后端模型筛选。规模小、难度中，依赖 1.1；证据为 ConfigurationUiOptions、state 附加 uiOptions 和 AgentFactory.supportedProfiles，尚待最终编译/审阅。
- [x] 1.3 用清晰表单与准备记录替换意图/JSON 输入输出，维护原请求/版本/世代和失败恢复。规模小、难度中，依赖 1.2；证据为 configurations.html/js/css，原输入/版本/世代内存维护，未知响应先刷新核对，再显式重试原对象，记录恢复与选择项更新分开。
- [x] 1.4 提供可选方法装配、摘要预览、保存/复用/选用以及资料登记表单。规模小、难度中，依赖 1.2；证据为 configuration-methods.js、对应表单和摘要面板，额外 Skill 排除基础引用、输入指纹/序号使旧预览失效，保存/登记提交原对象并区分后续刷新失败。
- [x] 1.5 对齐控制台布局/文案与使用说明，执行范围内编译并提交独立实现审阅证据。规模小、难度中，依赖前述条目；控制台外壳与导航、附近说明/稳定规格已同步，ebeacaa 的离线 Java compile 和两个浏览器脚本语法编译通过，未运行真实页面。
- [x] 1.6 修正 r1 的 R034-01，提供经 Owner 验证的本次任务状态及刷新入口，计划链接仅指向定义。规模小、难度中，依赖 1.3；uiOptions.taskProgress 复用既有 Owner/context/policy 关联查询的 taskId/state，结果卡片区分创建结果与实际执行状态，读取缺失时明确未知。3b251cd 的 Java/已修改 JS 编译通过，待独立复审。

## TODO

- R034-01 已修正并完成范围内编译，等待 r2 独立实现审阅，通过后仅提交本 FD 审阅证据并自动归档；动作授权 FD-033 的缺省范围选择仍单独待确认。

## Acceptance

1. 普通任务不需要填写内部 ID、意图版本、准备世代、政策版本或 JSON；计划/方法/模型/上游可选择，有缺少基线等前提时明确指出（1.2、1.3）。
2. 页面不会自动展开原始响应、Assembly、Candidate 或 Intent JSON；结果以可理解的状态、缺口、恢复按钮和可刷新的精确任务状态入口呈现（1.3–1.6）。
3. 相同输入重试保持原意图版本，使用服务器当前世代；修改内容创建下一版本，READY 不被误称执行成功，提交中或结果未知不自动重复准备（1.3）。
4. 默认方法能直接准备；自定义方法可通过选择/勾选/正文说明完成预览与保存，不手写结构，不调用模型，预览输入变化后原候选不可保存（1.4）。
5. 所有个人资料、准备输入、计划、配置和上游选择限定当前 Owner/仓库；提交精确引用，原业务授权/版本/兼容检查保持，不输出私有正文到选项列表（1.2–1.4）。
6. 沿用现有控制台视觉和可访问表单，准备与配置记录可恢复查看，附近文档说明真实点击路径、前提和未运行验证边界（1.5）。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- 静态核对契约、Owner 过滤、原请求/异步失效和页面语义；源码完成后一次 JDK 25 离线 Maven compile 与配置脚本 node --check。使用已知本机环境，不下载依赖、不打包，不运行测试/浏览器/数据库/服务或模型。
- 独立设计审阅：../reviews/FD-034-design-review-20261024-742db6c1.md 与同名 JSON，ready；未执行运行验证。
- 实现比较基线 8e7342c；实现分片 1ca288a、9d2dae1、e9bb9ae、ebeacaa。所有相关 Java/资源/说明/稳定规格已提交，其它私人秘书/模型/启动脚本和 FD-033 草案/反馈不在范围内。
- 2026-10-24 11:17 JST：使用用户提供的 `C:/green/bin/setup-jdk25.ps1` 设置 JDK 25/Maven 3.9.4 环境，`mvn -o -Dmaven.test.skip=true compile` 通过，292 个生产源。脚本仅运行版本显示，打印的 package 示例没有执行；环境在结束后还原。
- `node --check src/main/resources/static/ui/configurations.js` 与 `node --check src/main/resources/static/ui/configuration-methods.js` 均通过。范围内 git diff --check 通过；定向静态检索只剩内部请求序列化/指纹使用 JSON，不再向页面输出原始 JSON 或要求 JSON 输入。
- 未运行：测试、Thymeleaf 渲染、浏览器/响应式布局、数据库、实际登录/准备/候选保存、乱序请求与中断恢复、模型、网络下载、打包、安装、重启和发布。编译与静态证据不是运行通过。
- Worker 双证据：../reports/FD-034-implementation-r1.md 与同名 JSON。
- r1 独立实现审阅：../reviews/FD-034-implementation-review-r1-20261024-c6b9087d.md 与同名 JSON，实际 FD-034-000006-changes-requested，唯一 R034-01/P2；本周期失败累计 1 次。
- Worker 已认领该来源事件并在 3b251cd 修正 R034-01；2026-10-24 11:21:52 JST 同一 JDK25/Maven3.9.4 离线 compile 再次通过（292 个生产源），已修改 configurations.js 的 node --check 通过。未改 configuration-methods.js，沿用 r1 证据。详见 ../reports/FD-034-implementation-r2.md 与同名 JSON；原未运行边界保持，等待 r2 独立实现审阅。
- 独立实现审阅第 1 轮：../reviews/FD-034-implementation-review-r1-20261024-c6b9087d.md 与同名 JSON，changes-requested；来源 FD-034-000005-implementation-ready，独立 Session fd034-reviewer-20261024-c6b9087d，审阅 ebeacaa1872178d69411eb59cfcebe906d3e40b1。R034-01 为静态确认的实际任务入口/进度语义缺陷；未重复编译、运行测试或服务。

## Sources

- Issue: none
- 用户（2026-10-24）：JSON 输出干扰使用，软件任务准备和组合/声明式候选均看不懂。
- 来源事件 FD-034-000002-design-requested；Planner/Worker Session secretary-desktop-20261024-a6529740。
- templates/ui/configurations.html、static/ui/configurations.js、ConfigurationController、ConfigurationContract、ExecutionConfigurations、AgentFactory、AiProfileCatalog、WorkflowPlanSpec、docs/EXECUTION-CONFIGURATION.md。
