# FD-063: Check effective file filters before Git delivery

**Status:** Complete
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

Git交付仅因系统配置filter.lfs而阻止普通HTML合并，未确认此次文件是否使用过滤器。

## Options and decision

检查本次变更文件在当前工作区、目标HEAD和结果提交中的有效filter属性；不以已安装过滤器为依据。属性启用或无法可靠读取时仍阻止自动合并。

## Solution

使用git check-attr -z --stdin及--source读取提交属性，以NUL分隔路径避免特殊字符和Windows命令行长度限制。严格验证返回三元组和路径，错误关闭自动合并。unset/unspecified遇同名filter配置时歧义拒绝。Git输出严格UTF8，无法解码则明确报错，避免非法路径替换后误查其他文件。全树工作区diff、merge-tree及实际merge子进程禁用配置中的clean/smudge/process/required，避免未修改文件的clean驱动被隐式执行；严格验证配置键，只影响子进程，不改原配置或索引。保留现有自定义合并驱动、脏文件、身份及结果评审保护，无数据库/API结构变化。

## Scope

修改WorkflowGitDeliveryService、相邻Git交付测试、错误翻译及交付说明。沿用用户已批准的平台定向测试、独立静态审查、备份重启授权；不绕过当前业务计划的独立评审、不自动重跑其补正。

## Work items

- [x] 1.1 按文件有效属性判定过滤器，错误说明包含路径及检查失败原因。
- [x] 1.2 定向覆盖未使用过滤器允许、实际启用拒绝、提交内属性及特殊路径、失败拒绝；完成编译并准备独立审查及备份加载。

## Acceptance

仅有全局/仓库过滤器配置不阻止不使用过滤器的文件；工作区/目标/结果任一启用的filter均保守拦截。检查失败不执行合并。既有Git交付回归通过，API显示正确预检，真实业务评审未过仍不能合并。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- Independent：修改本地合并前外部程序执行保护，需要独立审查。无依赖下载、不运行全库测试。
- `mvn -o -DskipTests compile`成功，319源；首次`mvn -o -Dtest=WorkflowGitDeliveryServiceTest test`18项全部通过。独立审查修复后一次相关定向复跑21项全部通过（原11项及10项新增），包含完整preview/merge不执行marker clean过滤器、dirty notes字节保留、状态名歧义拒绝、真实生产gitWithInput辅助的NUL stdin校验；其余测试在隔离配置的真实临时Git仓库执行。
- `node --check`检查i18n.js及console-translations.js成功。只读差异检查确认未修改业务授权、评审结果或数据库schema。
- 共享契约从已安装C:/green/aiw/skills加载；当前Git 2.45.1支持--source。不支持的Git保留明确检查失败提示，不默认放行。
- 独立静态审查提出reserved属性值歧义、未修改dirty文件在全树diff/merge中可能运行clean、非UTF8路径替换三个边界，均已修复。未追加模型调用/业务补正。
- 复查无阻断且全局无活动任务后，备份D:/temp/data/aidev-r3.before-FD063-20261028-080129.db（7598080字节，integrity ok），重启Control Plane加载。认证HTTP新翻译资源200；真实sudoku2.htm在工作区/HEAD/b6d24a7结果均为filter=unspecified。运行回执%TEMP%/aiw-goal-execution/fd063-runtime-receipt.json。
- 最新API及git确认主题计划现已COMPLETED/DELIVERED，原仓库master为6f7412709cb42e3d903f26c3a84e24cb64ca78df，干净且相对原基线仅sudoku2.htm变化。该交付在重启前备份中已是DELIVERED（actor web:admin），不是本次修复自动合并的证明；本轮未重做业务审批/补正/合并。预检alreadyMerged=true、blockedReason=null。业务review-4已PASS，但其报告仍明确未运行浏览器实测；本轮不声称视觉验收通过。
- 独立审查通过：`reviews/20261028-independent-static-review.md`，Reviewer `codex-fd063-review-20261028`，来源 `FD-063-000002-review-requested`。本轮三项过滤器边界发现已修复关闭，已核对21项执行者回归及实际属性加载回执；不把加载前已交付业务状态当成本次自动合并生产证明。Reviewer未额外执行测试、模型、API或进程操作。

## Sources

- 用户要求修正未实际使用文件过滤器时的合并误拦截。

**Completed:** 2026-10-27
