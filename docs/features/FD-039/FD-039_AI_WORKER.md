# FD-039: 原型制作作为可组合技能配置给现有 AI Worker

**Status:** Complete
**Revision:** 5
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户澄清：原型制作属于可配置 Skill，不需要独立 Worker；需要时应装配给现有 AI Worker。FD-038 只吸收原型提案方法，还没有可选的完整制作资源。当前工厂已支持 additionalSkills → method.protocolSkills → 固定资产与文件集；无需新增角色或执行器。

## Options and decision

添加公共 prototype 组合技能，复用现有自定义工作方法/附加 Skill 入口。采用完整 SKILL.md + UI/逻辑辅助方法与新增不可变资产；不创建 prototype Worker、角色模板或进程。不更改 Schema/API、权限或调度策略，属于已有声明式扩展，无需额外设计审阅；最终独立实现审阅仍必需。

## Solution

平台 prototype 是辅助技能，按需配置给现有方法，保留原 primarySkill、职责、REPORT/VERDICT/WORKSPACE 和工程交付协议。入口明确激活、输入输出、制作/预览/验证边界；按任务问题选择逻辑或UI参考。正文 Easy English，保留宿主 prototype 的单一问题、可运行最小产物、显式状态、界面变体和观察结论，不复制自动分支、提交、集成或无权限执行。

制作要求写权限；命令/预览需要原工具及命令许可。技能不自动获得 COMMAND、启动服务、浏览器、下载或真实数据库权限；可在受限任务中交付原型代码、启动说明和明确未运行状态。仅建议/原型不能被报告为生产实现，推广和清理按原任务/平台范围处理。已有只读 Worker 可分析或报告权限缺口，不因附加技能成为可写角色。

增加 prototype 三个声明文件（入口、UI、逻辑）；index.files 加入该包，不增加 index.profiles 角色。software-catalog.json 只追加新的 prototype 文本资产版本和摘要，原9资产/11方法/协议保持。SkillFiles按现有目录将其作为公共包，组合配置固定引用与清单，旧任务/配置不重写。同步三语言独立部署的同名资源并更新说明/规格。

## Scope

延续当前主工作区范围提交：prototype资源/索引/新增目录资产、三语言副本、使用说明/稳定规格及FD证据。保留并行秘书/模型/Python/Java/模板差异；不改宿主.agents技能，不创建独立Worker，不修改运行时、工具、Schema或数据库，不制作或执行本任务之外的实际原型。

## Work items

- [x] 1.1 编写prototype入口及逻辑/UI参考，明确组合语义、任务范围与制作/预览证据。规模小、难度中，无依赖；证据为资源与原宿主方法对比。
- [x] 1.2 声明文件集、追加新资产及三语言副本，接入既有附加Skill配置链。规模小、难度中，依赖1.1；原资产/角色/协议保持。
- [x] 1.3 更新使用说明/规格，提交静态一致性/资源编译及Worker双证据。规模小、难度低，依赖前项；独立审阅结论属于下一阶段。

## TODO

1.1–1.3 已完成，资源/说明源码 aaa172c；独立实现审阅 r1 静态通过，无阻断发现。待 host 核对真实通过事件和审阅后无实现漂移，完成范围归档。

## Acceptance

1. prototype作为可选附加Skill进入既有工厂目录/选择项；不新增Worker或主角色（1.1–1.2）。
2. Skill包含实际制作逻辑和UI原型的方法、可运行交接说明及观察/未验证结果；不止于提案（1.1）。
3. 原主方法、输出、工具和权限保持；只读不能写，预览/命令缺权限时明确未运行而非自动获取（1.1）。
4. 新包在显式清单，新增资产hash对应实际正文，旧资产/方法不改；三语言副本一致且原冻结任务保持（1.2）。
5. 使用说明可指导从自定义方法勾选prototype并配置给现有Worker，静态/编译与真实执行边界清晰（1.3）。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- 一次范围diff --check与只读JSON/引用/摘要/副本/旧资产不变/组合字段一致性检查，不执行生产模块、技能脚本或实际原型。离线mvn -o -Dmaven.test.skip=true compile复制生产资源，不代表工厂构造器、UI、模型、Worker或原型运行通过。无测试、网络、下载、服务、浏览器、数据库、打包和重启。
- 独立 Reviewer `fd039-review-20261024-b84d2e61` 已认领来源 `FD-039-000004-implementation-ready`，静态审阅 `09e2485..aaa172c` 的实际 19 文件和现有配置/文件集/Python消费路径，通过且无整改项；报告 `../reviews/FD-039-implementation-r1.md` 与同名 JSON。未重复编译或运行模型/Worker/原型，真实效果及质量仍未验证；三语言副本不增加动态配置支持范围。

## Sources

- Issue: none

- 来源：用户澄清；.agents/skills/prototype/SKILL.md、LOGIC.md、UI.md（适配方法，不调用宿主流程）；ConfigurationContract.Assembly、ExecutionConfigurations.propose/content、ConfigurationUiOptions.assets、configuration-methods.js、SkillFiles.configured 与 Python execution_configuration.load。Planner/Worker Session secretary-desktop-20261024-a6529740；FD-039-000002-design-requested。

- Worker 双证据：../reports/FD-039-implementation-r1.md/.json；范围09e2485..aaa172c。静态diff及只读资产/索引/引用/副本检查通过：1新增prototype-v1、旧9资产/11方法/主角色映射不变、3声明文件与9一致副本；既有additionalSkills链保持。离线mvn -o -Dmaven.test.skip=true compile通过（2026-10-24T13:44:12+09:00，131应用资源+2配置资源复制，Java up to date）。未运行工厂实例、UI、模型、Worker、真实原型、浏览器、测试、网络、数据库、打包或重启。
