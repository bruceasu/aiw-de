# FD-040: 将设计诊断研究等方法注册为可配置的 Worker 附加技能

**Status:** Complete
**Revision:** 5
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户点名codebase-design、improve-codebase-architecture、domain-modeling、diagnosing-bus、resolving-merge-conflicts、teach、research、handoff、edit-article，要求未吸收内容应做成符合平台的skills。diagnosing-bus按仓库已有diagnosing-bugs理解，避免建立拼写错误的别名。FD038只将部分方法嵌入既有角色；FD039已证明可通过公共资产/附加Skill配置链接入完整辅助技能。

## Options and decision

沿用prototype的附加Skill模式，把九项做成可单独配置、具有完整输入/方法/输出/边界的技能，不仅新增名称或引用外部宿主路径。不新增Worker角色/进程或Schema，不复制宿主CLI、自动代理/发布/持久状态。纯提示与已有声明式资产扩展，无API/权限/持久化策略变化，不需额外设计审阅；最终独立实现审阅仍必需。

## Solution

新增九个平台包，每包一个自包含SKILL.md，采用Easy English；各自有触发/非触发、输入、实际步骤、完成与缺口处理，保留主方法和原REPORT/VERDICT/WORKSPACE。代码设计关注接口/职责；架构改进关注已有结构摩擦和渐进方案；领域建模关注术语/状态/不变量；诊断区分症状原因；冲突修复保留双方意图及现场；研究产生有来源结论；交接保留当前事实/权限；教学提供目标相关讲解/练习；文章编辑保留作者意图和依据。

只加载所选技能，不默认运行全部方法。与主角色冲突时保留主职责，返回明确缺口或通过正常流程调整基础方法；附加技能不把只读变为可写、不替换主输出格式。研究依已提供或许可来源/工具，不因源宿主要求background agent而派代理；教学基于当前提供的目标/反馈，不自动创建课程私有状态；文章在既有报告返回修订或在明确文件范围内编辑；交接不是平台派发。

冲突技能只处理真实在途冲突且需明确修复任务，诊断本身不授予修复/运行。缺少base、双方历史或完成Git操作的适配器时保留现场并报告，不绕过broker运行裸命令、reset、abort或选整边。测试/网络/浏览器/依赖/写入/Git等继续由原工具及任务授权决定。

index.files增加九包，profiles保持。software-catalog.json只追加九个*-v1不可变文本资产，既有十个资产、十一主方法和协议保持。同步三语言对应副本。原additionalSkills上限6保持，按任务选需要的子集，不把九项强行塞进一个配置。SkillFiles仍冻结完整包；已有任务/配置不重写。

## Scope

主工作区范围提交：九个技能入口、显式索引/新增目录资产、三语言27对应副本、能力/选择指南与来源矩阵/规格、FD证据。复用既有Factory/UI/Worker装配，不改Java/JS、工具、数据库、宿主.agents技能、运行后端或部署。不执行原技能任务、模型、研究代理、测试、冲突操作或外部写入。

## Work items

- [x] 1.1 明确九项与现有方法差异、角色兼容和工具边界。规模小、难度低，无依赖；证据为FD与技能指南。
- [x] 1.2 实现代码设计、架构改进、领域建模三个完整辅助Skill。规模小、难度中，依赖1.1；各自有独立用途，不只重复通用规则。
- [x] 1.3 实现故障诊断与真实冲突修复Skill。规模小、难度中，依赖1.1；有限实验/修复权限、操作状态和不明现场保留明确。
- [x] 1.4 实现研究与交接Skill。规模小、难度低，依赖1.1；保留来源、结论、修订/原身份/剩余权限，不自动外联或派发。
- [x] 1.5 实现教学与文章编辑Skill。规模小、难度低，依赖1.1；实际讲解/练习/修订方法，既有输出和资料范围内完成。
- [x] 1.6 注册九资产/文件集、同步三语言27副本并更新说明/稳定规格。规模小、难度中，依赖1.2–1.5；保留旧资产/方法/profiles及上限。
- [x] 1.7 完成静态内容/装配/资源一致性与离线资源编译，提交Worker双证据交独立审阅。规模小、难度低，依赖全部实现；Reviewer结论属于下一交接阶段。

## TODO

WI 1.1–1.7 已完成；独立 Reviewer 静态实现审阅通过，宿主待核对真实回执、范围并完成本地交付与归档。实际模型、界面与九类任务未运行验证。

## Acceptance

1. 九个正确命名的Skill可通过既有目录/附加选择链配置，不新增独立Worker（1.1–1.6）。
2. 各Skill有实际工作步骤、输入/产出/完成与缺口处理，内容与所命名用途匹配，不仅保留提案或空壳（1.2–1.5）。
3. 不扩大主职责、输出、工具或任务权；明确研究代理/课程状态/外部来源/文章写入/冲突及诊断的宿主差异（1.1–1.5）。
4. 九资产摘要/显式资源及27副本一致，旧十资产/十一方法/profiles和附加上限6保持，旧任务不重写（1.6–1.7）。
5. 人可按中文指南选择合适子集，理解研究等受现有工具支持约束；检查只证明结构/编译，真实模型行为未验证（1.1、1.6–1.7）。

## Verification

实现提交 b05b5ffd539fad168ec0d3e0fd981b8c38cc8da6（比较基线30f8883）。Worker双报告：[Markdown](../reports/FD-040-implementation-r1.md) / [JSON](../reports/FD-040-implementation-r1.json)。

一次范围diff --check及内联只读JSON/命名/链接/SHA256/旧资产/副本检查通过；定向核对既有选择和组合链。九新增资产/入口、27副本一致；原十资产、十一方法、八Profile映射及原文件列表保持，附加上限6不变。

C:/green/bin/setup-jdk25.ps1后首次Maven参数被PowerShell拆分，生命周期解析失败；仅修正引号后，mvn -o '-Dmaven.test.skip=true' compile于2026-10-24T13:53:24+09:00成功，复制2+140资源，Java已最新，环境变量恢复。未运行测试或依赖下载，未执行脚本打印的打包/运行示例。

未运行模型/Worker/实际技能任务、工厂实例/界面、SDK/工具/恢复、服务/数据库/浏览器/网络、打包/发布/重启；结构及编译不证明运行与质量。共享工作区其他未提交改动不属于本FD审阅。

独立 Reviewer Session `fd040-review-20261024-7d2c965a` 已领取 `FD-040-000004-implementation-ready`，审阅 `30f8883..b05b5ffd539fad168ec0d3e0fd981b8c38cc8da6` 的43文件实现与实际装配合同，未发现硬性违约或需修改的结构异味，五项验收均获静态证据支持。独立摘要/索引/旧字段/27副本检查通过；工作区五个文件的字节差异确认为CRLF行尾，未发现实质实现漂移。Reviewer未重跑编译、测试或模型。双报告：[Markdown](../reviews/FD-040-implementation-r1.md) / [JSON](../reviews/FD-040-implementation-r1.json)。

## Sources

用户列举九项；.agents/skills中对应SKILL与相关方法（读取为适配来源，不是调用九个宿主工作流）；FD038方法矩阵、FD039附加prototype；现有ConfigurationContract/ExecutionConfigurations/ConfigurationUiOptions/SkillFiles和Worker读取合同。

Planner/Worker Session secretary-desktop-20261024-a6529740；真实来源 FD-040-000002-design-requested；无关联Issue。
