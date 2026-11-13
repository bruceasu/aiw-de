# FD-107: Read task Skills and cited knowledge on demand

**Status:** Complete
**Revision:** 5
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 06, 12, 30. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

复用现有执行副本与 Sources 分页工具，按任务明确导入工作方法／自定义 Skill 文本或参考知识。资料以内容摘要作为版本，随定义保存并在执行时冻结，不新建资料服务或数据表。最多八份资料、每份 128 KiB、合计 512 KiB。只把已选择资料交给本次获得任务的 Worker；不自动读取私人库或工作目录外文件。

已核对 WorkflowRuns、任务领取响应、AgentRunRequest、共享 SDK 循环、Sources、原 Skill 包和工作流 UI。原有内置 Skill 包机制保留；新增资料正文不进入初始模型提示，只提供 kind/title/sourceId/sha256/bytes 目录。新增只读精确搜索，复用原分页读取；引用必须基于实际读取。资料不能授予工具权限。

含资料任务要求 task-materials-v1，仅新 Python SDK Worker 登记该能力，避免备用或旧 Worker 静默忽略资料。空资料保持原路径。原生问答后继继续使用同一执行副本。后续 FD-106 调整未来步骤时必须保持原执行资料版本，不改写已经派发任务的上下文。

## Scope

仅任务显式资料导入、按需读取及引用。自定义 Skill 首期为文本工作方法，不是可执行插件。长期共享资料目录、私人记忆、自动分享和知识运营不纳入本次，由 FD-113/117 完成；不引入嵌入模型或检索框架。

## Work items

- [x] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [x] 1.2 实施已确认行为，复用既有 Sources 与执行快照。
- [x] 1.3 编译受影响源码、自查差异，准备交独立审查。

## Acceptance

方法与事实来源分开；Worker 能按需读取实际内容；缺少资料时询问而非编造；资料访问、引用和共享范围可核对。

## Verification

- 独立静态审查（`a057f48 → 6aae5a8`，来源 `FD-107-000002-review-requested`）：Changes requested，发现 1 项 P2——现有 PlanLocationProtocolTest 未注入领取响应新增必需的 workflowRuns 依赖，原领取用例会空指针。详见 [独立审查报告](reviews/review-20261114.md)。Reviewer 未运行测试、编译或运行时验证；限定范围未发现其他阻塞。资料仍为任务级显式输入，长期共享目录与个人记忆留给后续 FD-113/117。

涉及任务输入协议、资料持久化内容与用户范围，采用独立静态审查，不以编译冒充运行验收。

- `git diff --check` 通过；定向核对 Spec 构造、任务领取、原生能力登记、请求传递、Sources 清理及用户归属路径。
- Python 七个修改／新增源文件 `py_compile`、workflows.js 的 `node --check` 通过。
- 加载 JDK25，使用父仓库 FD-081 离线 settings、D:/.m2/repository、`MAVEN_OPTS=-Xmx512m -XX:MaxMetaspaceSize=256m` 的 `mvn -q -o ... -DskipTests test-compile` 通过。
- 新增五个纯单元回归用例，涵盖目录不含正文、方法与事实分开、摘要／大小限制、检索范围与空资料；只编译未执行。
- 未运行测试、浏览器、服务、迁移、网络、真实模型或安装。实际资料领取、能力匹配、页面导入与 SDK 按需读取仍待运行验证。
- 核对 FD-105 时发现秘书仍调用旧提案 API，因此先完成相应依赖再退役。更新 FD-105/114 的草案依赖，避免提前断开当前调用或形成依赖环；不将这部分设计调整算作后端退役完成。

### 独立审查修复

- 独立聚焦复核 `5911526`（来源 `FD-107-000004-implementation-ready`）：Pass，原唯一 P2 已关闭。新依赖 mock provider、空资料返回及响应断言与原领取夹具一致，无产品源码变化；详见 [复核报告](reviews/review-recheck-20261114.md)。Reviewer 未执行测试或编译，首轮运行证据限制保持。

首轮 `FD-107-000003-changes-requested` 的 P2：PlanLocationProtocolTest 手工创建控制器但漏注入新依赖，原领取用例会空指针。已在该现有夹具注入 WorkflowRuns 的 mock provider 和空资料返回，补充空资料协议断言；不添加生产兼容分支。同一离线 Maven test-compile 再次通过，未执行测试；产品源码保持首轮审查版本。

## Sources

- docs/FUNCTION-INVENTORY.md, 06, 12, 30.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。

**Completed:** 2026-11-13
