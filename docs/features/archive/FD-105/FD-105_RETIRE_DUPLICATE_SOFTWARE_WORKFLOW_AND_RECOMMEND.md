# FD-105: Retire duplicate software workflow and recommendation paths

**Status:** Complete
**Revision:** 5
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 02, 04, 08, 13, 15. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

软件开发作为普通工作流应用；退役旧专用工作台和隐式流转，清理不再被调用的推荐服务。保留执行结果与必要工具和 Skills，默认不破坏历史记录。

Dependencies: FD-104, FD-108, FD-116（均已交付）。统一工作流已承接文字与文件工作，秘书委派已有正式统一入口。本次删除旧 WorkflowRecommendationService 及其创建／恢复／应用推荐 API；原任务、报告、冻结模型接单策略保留，历史通过已有任务详情读取。删除旧 Graph 软件计划采纳路径，秘书确认的选定需求由现有 goal-proposals 接口直接存为 Owner 对应账户的普通许愿草稿，不创建／执行 Plan。保留原请求原文、摘要和幂等回执；已有旧回执只读返回，不凭查询重放创建。原软件交付只读报告端点保留以查看真实历史。未删除底层通用 Graph 或必要 Skills。

风险：有意移除重复的公开创建／推荐 API；不保留空实现或自动转发兼容层。无数据库迁移，现有持久化记录不删除。需求入池与原请求记录在同一事务；机器只能写入自身已认证 Owner 对应 actor 的非执行草稿，不能指定其他账户或人类身份。需求总正文仍受现有许愿池 16000 字限制。模型与运行权限没有增加。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

## Work items

- [x] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [x] 1.2 实施已确认行为，删除因此不再需要的代码。
- [x] 1.3 编译受影响源码、自查差异并提交独立审查；结论由 Reviewer 记录。

## Acceptance

只保留一套日常创建与控制入口；旧推荐与隐式评审不再驱动新工作；历史执行仍可查看；无调用方的代码删除。

## Verification

2026-11-14 独立聚焦复核通过：`codex-review-fd105-20261114` 领取 `FD-105-000004-implementation-ready`，核对 `8404b41a` 的独立失效夹具删除闭合、历史证据保留及任务详情真实入口说明，两项 P2 已关闭。保留首轮失败报告；复核报告：[reviews/review-recheck-20261114.md](reviews/review-recheck-20261114.md)。Reviewer 未运行额外验证、测试、编译、浏览器、API、服务或网络，不把编译称为运行通过。

2026-11-14 独立静态审查：`codex-review-fd105-20261114` 领取 `FD-105-000002-review-requested`，核对 `5efce607`，结论 Changes requested。两项 P2：独立 tests/fd018 验收源码仍引用已删除推荐服务；历史任务详情仍承诺复制但新入口已只跳转空统一工作流。报告：[reviews/review-20261114.md](reviews/review-20261114.md)。新入池 Owner／事务／幂等和历史控制未另列阻塞。Reviewer 未运行测试、编译、脚本、浏览器、API、服务或网络，不将编译称为运行通过。

已删除 WorkflowRecommendationService、只验证已退役推荐功能的 WorkflowRecommendationResultTest、旧 plan-editor 模板及 JS 中整段创建／推荐逻辑。旧新建页面跳转统一工作流；旧 PlanEditor 草稿创建、WorkflowPlan 新建／复制及推荐恢复／采纳端点已移除。保留已有 Plan 详情、停止／控制、原报告和冻结模型任务策略；历史记录没有删除，通用 Graph 与软件 Skills 不动。

GoalProposals 现只负责将已确认的选定原文存入普通许愿池，并记录原请求回执；Owner 对应 actor 由 authority_owner 读取，不接受调用方指定。机器身份、当前 boot、原始摘要、请求幂等继续核验；同一事务写草稿和回执。旧回执无 goalId 时原样返回，不因重发或查询新增草稿。删除旧 software-development 提案列表／Graph 采纳；只读历史交付报告保留。秘书回执提供许愿池直达链接与不启动任务的说明。

实际检查：

- JDK25 + `MAVEN_OPTS=-Xmx384m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC`，离线 `mvn -q -o -s D:/03_projects/AI-tools/aiw-de/.ai/fd/FD-081/offline-maven-settings.xml -Dmaven.repo.local=D:/.m2/repository -DskipTests test-compile`。首轮仅新增测试 Map 通配符断言类型错误，修正该源码后一次重试 exit 0；无测试执行，仅 Jansi/Unsafe 警告。
- `node --check src/main/resources/static/ui/workflow-editor.js`、`personal-secretary/ui/app.js`、`personal-secretary/ui/i18n.js` 均 exit 0；`git diff --check` exit 0，仅既有 CRLF 提示。
- 新增 SecretaryWishIntakeTest 源码核对 Owner 归属、幂等、不重建旧回执；只编译。静态检查真实调用方、MVC 路由、已退役编辑器代码与同事务持久化路径。
- 未运行测试、浏览器、服务、模型、网络、迁移，未安装依赖。新需求入池和旧运行控制仍待运行验收。接口移除是此轮明确的简化，外部旧调用方需要改用统一工作流／许愿池。
- 首轮两项 P2 已修正：删除 tests/fd018 下仍引用已退役推荐服务的独立验收源码，保留原验收场景／日志并注明历史性质，冻结模型策略夹具继续保留；历史任务详情的“复制计划”改为真实的“打开工作流”，不承诺跳转会复制旧内容。这些修正未改 Java 生产实现。
- 修正后对受影响的独立夹具执行同一离线配置的 `mvn -q -o -f tests/fd018/pom.xml ... -DskipTests test-compile` exit 0，保留的 RecommendationPolicyTest 和生产源码可编译；未运行该夹具测试。`git diff --check` exit 0。

## Sources

- docs/FUNCTION-INVENTORY.md, 02, 04, 08, 13, 15.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。

**Completed:** 2026-11-14
