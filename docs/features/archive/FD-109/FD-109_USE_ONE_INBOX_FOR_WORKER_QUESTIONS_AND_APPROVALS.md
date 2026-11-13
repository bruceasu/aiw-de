# FD-109: Use one inbox for Worker questions and approvals

**Status:** Complete
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 16. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

在现有 /ui/questions 顶部聚合流程人工节点、正式问题和固定工具操作确认。无需先选 Plan 才发现请求；按需展开原专业详情与控制。点击直达所属执行或对应问题／审批，回答与批准仍使用原协议，不新建问题状态或授权层。

已核对 AuthorityPageController、FormalQuestions、ToolApprovals、问题／工作台脚本与既有权限拦截。新增有界只读聚合：每类最多一百条，只查询本人 actor／Owner；固定审批复用原 refresh 校验，过期／失效记录不算待人工决定。原 API 的版本、一次性消费和真实回执保持不变。

## Scope

只统一发现与直达，不复制原回答／审批表单，不赋予新权限。秘书对同一记录的接入随 FD-116 完成。已回答／已允许但未推进的记录单独标记，不能说任务已执行。

## Work items

- [x] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [x] 1.2 聚合已有记录、折叠原按计划详情、定位具体问题／审批，不复制动作协议。
- [x] 1.3 编译受影响源码、自查差异，准备交独立审查。

## Acceptance

用户能从问题直达相关执行；允许和拒绝有真实回执；已批准操作不重复申请；过期或被停止的问题不能继续执行。

## Verification

- 独立静态审查（`82e36de → 1ba86ff`，来源 `FD-109-000002-review-requested`）：Pass，限定范围未发现阻塞性 finding。已核对 actor/角色、原来源授权链、工具确认刷新与真实状态、三类有界窗口、详情深链及独立刷新。详见 [审查报告](reviews/review-20261114.md)。Reviewer 未运行测试、编译或运行时验证；真实角色访问、SQL 数据与页面交互限制保持。

新增人类只读聚合入口，影响读取范围与深链，采用独立静态审查。未运行验收。

- 原 authority 登录／CSRF 边界保留；只增加人类 questions/inbox 白名单，不开放给 Agent machine 路由。端点再次要求当前人类和操作／审批角色，SQL 按 actor／Owner 过滤。
- 工具确认复用 ToolApprovals.refresh/view，可能落地原有到期／失效状态，不批准、不消费授权、不执行操作；等待静止回执不算当前可处理。
- 每类查询限制一百条；未答／待决定在 SQL 截断前优先，避免最近已处理项挤掉待处理项；前端筛选只针对本次列表，不冒充全量检索。
- 新增一份小脚本只负责只读列表；旧问题／审批仍在原脚本处理，深链选定原计划和具体记录。刷新保留原回答草稿与筛选。
- `git diff --check`，pending-inbox.js、questions.js、tool-approvals.js、console-translations.js 的 `node --check`，以及使用父仓库 FD-081 离线 settings、D:/.m2/repository、JDK25 低内存配置的 Maven `-DskipTests test-compile` 已通过。
- 自查修正截断前优先级后，同一离线 Maven test-compile 再次通过；补充打开具体计划时滚动到详情后，pending-inbox.js 的 node --check 通过。未运行测试、浏览器、服务、数据库或真实 API；没有新状态表、迁移、授权层或依赖安装。真实跳转定位、角色与刷新效果尚未运行验证。

## Sources

- docs/FUNCTION-INVENTORY.md, 16.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。

**Completed:** 2026-11-13
