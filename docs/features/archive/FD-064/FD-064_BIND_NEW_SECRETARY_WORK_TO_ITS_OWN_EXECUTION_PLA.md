# FD-064: Bind new secretary work to its own execution plan

**Status:** Complete
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

Robot头像新工作未关联计划，秘书因可见列表只有一个已完成旧计划便自动套用，5次提交全部409且无编码任务。平台诊断scopeReady=true，409却笼统显示范围变化；本轮回复DONE又与工作完成混淆。

## Options and decision

新工作先创建自身幂等草稿，已关联工作仅用原计划，不以列表数量关联其他计划。保持每计划范围确认，不复制旧授权，不修改已完成计划。

## Solution

平台选项附带状态，新请求在任务准备前拒绝非草稿计划，返回明确结构化冲突；已受理原请求的读取/继续保持幂等。秘书保留允许的错误码/安全字段用于报告；未关联工作走现有PersonalPlans与AuthorizationRequests流程。详情将回复结束与工作结果分开，并展示job.result。已有失败安排恢复时先幂等准备自身计划，不需要模型生成另一份任务。

## Scope

秘书professional_tasks/platform_client/daily_assistant及UI、平台准备服务/控制器、相邻定向回归和说明。沿用定向测试、独立审查、备份重启及原任务恢复授权。不直接改数据库、不扩大权限、不执行Robot实现或重新提交旧失败请求。

## Work items

- [x] 1.1 自身计划绑定与明确冲突，保留原请求/权限保护。
- [x] 1.2 区分回复结束与工作完成，显示真实处理结果及国际化。
- [x] 1.3 完成定向测试和编译，准备独立审查后备份加载，原Robot工作只准备自身草稿与范围申请。

## Acceptance

只有旧已完成计划时不得POST专业任务；为本工作只创建一个惰性草稿并进入现有范围引导。绑定计划与模型传入不符拒绝；非草稿新请求返回明确错误；原受理请求不重复。回复DONE不误示工作完成。Robot原工作/历史保留，未实际执行不得宣称完成。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- Independent：跨秘书/平台的计划绑定及授权边界。只执行定向测试、离线编译，不安装依赖、不推送。
- `scripts/compile-secretary-runtime.ps1`（Python内存编译及app.js语法）和Java离线`mvn -o -DskipTests compile`319源成功；`node --check personal-secretary/ui/i18n.js`成功。
- Python `python -B -m unittest discover -s tests/fd064 -p test_plan_binding.py -v`10项通过（真实临时私库，平台HTTP模拟）：单个旧计划/无关草稿不复用、自己的草稿幂等/刷新已完成回执、固定绑定、有效自身草稿提交、未知请求阻止替换、安全409字段、原失败恢复在摘要或模型前准备自身计划。Java `mvn -o -Dtest=PersonalPlanSelectionTest test`2项通过（临时SQLite，Factory模拟）：终态拒绝且无准备记录和明确409结构。首次测试源码因AssertJ泛型编译错误未执行测试，修正断言后唯一复跑成功，未修改生产规则适应测试。
- 独立静态审查无阻断后备份平台D:/temp/data/aidev-r3.before-FD064-20261028-223034.db（7598080字节）及原私库同目录personal.before-FD064-20261028-223034.sqlite（655360字节），均integrity ok。确认无运行任务/未知专业请求后通过本机shutdown正常停止旧秘书12800，重启平台及秘书8820；通过现有人工authorize接口actions=[]、planId=null仅启用新boot沿用原授权，不新增动作或计划范围。
- 原Robot工作仅安排一次恢复准备，命中无模型恢复分支，工作revision18至21，现NEEDS_CONFIRMATION，关联自身新草稿FP-262e4ca6-008e-37b6-a5eb-9b169480ec05，平台DRAFT/v0/0任务；旧计划仍COMPLETED/v2，原5个专业请求仍REJECTED且总数不增加。现有授权等待申请指向新计划，原工作和上下文/历史保留；新范围未确认，不复制旧授权，也未执行Robot修改。
- 运行回执%TEMP%/aiw-goal-execution/fd064-runtime-receipt.json、fd064-platform-receipt.json。秘书app.js认证HTTP200且内容与本地源码完全相等，i18n.js200包含新翻译；首次检查中文标记因PowerShell管道编码变问号而false，改为比较完整UTF8响应与源码字节确认一致，非服务资源失败。app.js最新语法检查通过。真实浏览器视觉未验收。
- 独立审查通过：`reviews/20261028-independent-static-review.md`，Reviewer `codex-fd064-review-20261028`，来源 `FD-064-000002-review-requested`。已核对固定工作关联、幂等草案/安全409及回复状态分离和原Robot无模型准备回执；没有重复专业提交、旧计划不变、未复制权限。Reviewer未额外执行测试、模型、API或服务操作。

## Sources

- 用户要求修正Robot头像工作错误复用已完成旧计划的问题。

**Completed:** 2026-10-28
