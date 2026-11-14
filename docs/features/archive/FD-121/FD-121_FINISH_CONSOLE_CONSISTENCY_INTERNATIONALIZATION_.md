# FD-121: Finish console consistency internationalization and deployment guidance

**Status:** Complete
**Revision:** 5
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 28. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

收尾统一页面、弹窗、侧栏和状态用语、中英日文案；同机简单启动，远程提供可照做的配置指引。复用原样式与部署工具，不换 UI 框架。

Dependencies: FD-104, FD-105, FD-112, FD-119, FD-120（均已交付）。沿用已有 shared CSS、侧栏、plan-windows.js 和中英日字典，不另建组件。补齐通用窗口最大化／还原与关闭提示、退役页面转向统一工作流的文案，以及本机信任模式的身份显示。更新现有 README 与两份 Quick Start，纠正 JDK21、隐含 SQLite、强制 Git/Codex 及旧推荐入口；分别列出 Windows／Linux／macOS 同机启动、秘书独立运行和远程配置入口，链接已有完整 mTLS 各系统命令。无需改部署框架、认证、凭据、模型或后台运行策略。

保持已确认的操作语义：停止领取不停止当前任务，标记离线不宣称杀死远程进程，全局暂停只针对自动启动。真实状态／缺失数据沿用原投影。桌面形象、托盘、主题等未确认取舍不删除。由于用户要求最终独立审查，以及公共窗口与全局文案受影响，本 FD 使用 Independent。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

## Work items

- [x] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [x] 1.2 实施已确认行为，删除因此不再需要的代码。
- [x] 1.3 编译受影响源码、自查差异并提交独立审查；结论由 Reviewer 记录。

## Acceptance

操作作用范围清楚；最大化、关闭、焦点与刷新行为一致；缺失数据不编造；本机和远程部署步骤分别明确。

## Verification

2026-11-14 独立聚焦复核通过：`codex-review-fd121-20261114` 领取 `FD-121-000004-implementation-ready`，核对 `031b98cf` 的 README 和中英 Quick Start，默认 sqlite profile／URL 及切换配置说明已与实现一致。首轮 P2 已关闭，保留一次失败历史。报告：[reviews/review-recheck-20261114.md](reviews/review-recheck-20261114.md)。仅文档修正，无需重编译；Reviewer 未运行测试、编译、脚本、浏览器、服务或网络，未作部署验收。

2026-11-14 独立静态审查：`codex-review-fd121-20261114` 领取 `FD-121-000002-review-requested`，核对 `19d80f64`，结论 Changes requested。一项 P2：README／中英 Quick Start 的默认 PostgreSQL 说明未考虑当前默认 sqlite profile 的 URL 覆盖。报告：[reviews/review-20261114.md](reviews/review-20261114.md)。窗口生命周期、标签语言切换和仅展示 Local access 未另列阻塞。Reviewer 未执行测试、编译、脚本、浏览器、服务或网络，不将已有编译证据称为运行通过。

已实现：共用浮动窗口的最大化／还原 title、aria-label、aria-pressed 与当前状态一致，关闭提示和语言切换沿用原组件；没有增加新弹窗或自动打开逻辑，保留关闭回焦、Esc、拖动和最大化定位。侧栏及顶部栏的无登录本机模式显示“本机访问”，不再误写“未登录”；没有改认证或角色。中英日补齐新工作流入口和窗口操作提示，保留既有字典和工作内容的翻译排除规则。

README 与中英文 Quick Start 改为当前工作流／简单任务／许愿池／秘书入口，JDK25、显式 SQLite 或 PostgreSQL、两个程序各自 .env 的真实路径；Windows 和 Linux/macOS 分别给出现有启动命令，秘书可独立运行。本机自动登记、远程秘书配对启用与 mTLS、普通远程 Worker 专属凭据及各设备自行管理模型分开说明；链接原 mTLS 指南的各系统完整 CA／签发／权限／启动步骤。不添加部署脚本或依赖。秘书 README 删除默认 Codex 和必须配置自定义模型命令等旧说明。

实际检查：

- `node --check src/main/resources/static/ui/plan-windows.js` 与 `node --check src/main/resources/static/ui/i18n.js` exit 0；`git diff --check` exit 0。静态核对窗口监听、共享模板状态、字典与实际 application.yaml、startup.bat/sh、秘书 main/env_config/start 脚本。
- 本 FD 无 Java/Python 生产源变更，不重跑已有通过的 Java 编译；基线 FD-105 的根 `-DskipTests test-compile` 与受影响独立夹具 `tests/fd018` 编译均已通过。本文档启动／安装步骤未执行。
- 未运行测试、浏览器、服务、SDK、模型、网络、迁移或部署，没有安装依赖。窗口实际尺寸、焦点、主题与三种语言页面，以及真实同机／跨设备部署仍待运行验收；静态审查不作运行通过声明。
- 首轮 P2 已修正：默认数据库说明此前只看基础 application.yaml，漏掉默认启用的 sqlite profile。现已对照 application-sqlite.yaml，将 README／中英 Quick Start 统一写明默认 sqlite profile 和内置 Windows URL，推荐显式设置当前设备路径；PostgreSQL 需要匹配的 profile／驱动／模式，不能仅改 URL。此轮只改文档，没有更改运行配置或重复编译。

## Sources

- docs/FUNCTION-INVENTORY.md, 28.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。

**Completed:** 2026-11-14
