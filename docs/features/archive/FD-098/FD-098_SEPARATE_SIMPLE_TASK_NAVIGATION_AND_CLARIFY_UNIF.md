# FD-098: Separate simple task navigation and clarify unified execution controls

**Status:** Complete
**Revision:** 5
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

简单任务与工作流侧栏指向同一页面，执行记录仅有状态与时间；暂停文案容易被理解为暂停 Worker，而实际上只阻止后续节点。

## Options and decision

分开页面模式，共用模板、脚本、执行器与存储。保留原 API 默认的全部执行列表，增加明确筛选，避免两套实现。

## Solution

新增 /ui/tasks 默认显示任务表单；/ui/workflows 默认显示工作流编辑器。简单任务按 definition_id IS NULL 筛选，工作流按非空筛选，先过滤再取最近 100 条。列表名称来自保存的执行快照；保持用户原文。暂停/继续文案明确作用范围，停止仍等待真实 Worker 回执。

## Scope

不改变执行、鉴权、持久化及旧软件开发引擎。旧入口的整体退役尚未完成，不做盲目重定向。按用户最新指令，先完成本项与后续用量展示的静态检查和编译，最后一起独立审查；审查前不归档。

## Work items

- [x] 1.1 区分任务和工作流入口、表单及列表，保留共享实现。
- [x] 1.2 名称、暂停范围及中英日文案一致。
- [x] 1.3 编译并保留静态证据，最后合并审查；当前尚未归档。

## Acceptance

两侧栏入口及高亮不同；各自最近 100 条列表分开。深链接仍能查看原执行/定义，任务目标不被自动翻译。界面不声称 Worker 在暂停后续步骤时已经停止。

## Verification

- 增加执行列表筛选参数和页面路径，使用 Independent；按用户要求最后统一审查。
- 2026-11-13：离线 Maven `-DskipTests test-compile`（FD-081 settings、D:/.m2/repository）及 `node --check` workflows.js/console-translations.js 通过，只编译、不执行测试。
- `git diff --check` 通过。静态追踪 executions 只有控制器一处调用，按固定筛选条件保留 actor 归属校验；界面用户名称跳过翻译。
- 未运行测试、浏览器、服务或真实 API；Thymeleaf 实际渲染和页面操作尚未验证。按用户要求待后续功能完成后统一审查。

- 2026-11-13：与 FD-099 合并独立静态审查，结果 changes-requested。报告 [review-20261113.md](reviews/review-20261113.md)，合并基线 2abe39b→3fe1baf，来源事件 FD-098-000002-review-requested，Reviewer codex-review-fd098-20261113。P1：执行列表使用 SQLite json_extract，破坏既有 PostgreSQL 模式，需改为取回JSON后用现有解码器提取标题。未运行测试或数据库验证。

- 2026-11-13：独立静态复核通过（verification-passed），P1 已解决，无剩余阻塞。报告 [review-20261113-recheck.md](reviews/review-20261113-recheck.md)，覆盖修复提交 aebdc63，来源事件 FD-098-000004-implementation-ready，Reviewer codex-review-fd098-20261113。FD-099 原源码与 pass 不变，共享页面阻塞已解除；未运行数据库、测试、浏览器、服务、网络或模型验证。

## Sources

- P1 修复：移除 SQLite 专属 json_extract。SQL 仍先按 actor/kind 过滤并限制 100 条，随后复用 db.decode 获取快照标题，并从响应中移除 run_json；不增加方言分支或额外抽象。后续复核仅覆盖本修复及关联列表契约。

- docs/FUNCTION-INVENTORY.md 第 04、05、13、28 项。

**Completed:** 2026-11-12
