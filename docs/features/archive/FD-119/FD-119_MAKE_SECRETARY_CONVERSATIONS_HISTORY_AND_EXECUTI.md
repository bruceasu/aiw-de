# FD-119: Make secretary conversations history and execution details direct

**Status:** Complete
**Revision:** 5
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 34, 37. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

沿用现有组件实现游戏式会话操作、自由输入、常驻历史入口、回复旁过程查看和委派执行直达。未确认的托盘、悬浮人物及旧桌面入口取舍不擅自删除。

Dependencies: FD-116, FD-117（均已交付）。复用现有 app.js 的会话分页、运行过程查看器及按钮样式；新增常驻会话日志、标题／摘要筛选、三个只填输入框的快捷操作。浏览器内 Map 保存各会话未发送输入，不增加数据库状态。回复按钮通过本机既有 session/CSRF 保护下的 POST /api/run/message 按真实 messageId 查询旧运行与委派回执；复用原只读 public 投影，不返回模型连接绑定、凭据或完整任务正文。只在已有个人 SQLite 记录上查询，无迁移、外部协议或依赖变化。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

## Work items

- [x] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [x] 1.2 实施已确认行为，删除因此不再需要的代码。
- [x] 1.3 编译受影响源码、自查差异并提交独立审查；最终结论由 Reviewer 记录。

## Acceptance

历史可方便查看且不丢当前输入；过程来自真实模型／工具记录；常用操作直接可用；中英日与现有风格一致。

## Verification

2026-11-14 独立聚焦复核通过：`codex-review-fd119-20261114` 领取 `FD-119-000004-implementation-ready`，核对 `aeeaa59` 的聚焦消息精确刷新、控制后 state/revision 更新以及序号／messageId 迟到响应保护。首轮 P2 已关闭，保留一次失败历史。报告：[reviews/review-recheck-20261114.md](reviews/review-recheck-20261114.md)。Reviewer 未运行测试、编译、验证脚本、浏览器、API、服务或网络，不把编译证据称为运行通过。

2026-11-14 独立静态审查：`codex-review-fd119-20261114` 领取 `FD-119-000002-review-requested`，审查 `d3088d7`，结论 Changes requested。一项 P2：超出最近 30 条的历史消息运行，在控制／核对成功后未更新精确查询缓存，继续沿用旧状态和 revision，后续控制被拒绝。报告：[reviews/review-20261114.md](reviews/review-20261114.md)。精确消息查询与 session/CSRF、安全投影未另列阻塞；Reviewer 未执行测试、编译、验证脚本、浏览器、服务或网络，不将已有编译证据称为运行通过。

Independent：涉及私人记录读取 API 与跨模块 UI，必须独立静态审查。拟运行 scripts/compile-secretary-runtime.ps1 和 node --check personal-secretary/ui/i18n.js；不执行测试、服务、模型、网络或迁移。补充旧消息精确查询及连接信息不外泄的测试源码，仅编译。页面重载不保留未发送输入；历史筛选明确限于标题和摘要，不声称全文搜索。浏览器交互仍未运行验证。

- 实际执行 `./scripts/compile-secretary-runtime.ps1`（Python、已有测试源码、app.js、PowerShell 语法）、`node --check personal-secretary/ui/i18n.js`，均 exit 0；提示文案调整后再次仅编译变动 JS。
- `git diff --check` exit 0，仅既有 CRLF 规范化提示。静态追踪真实消息 → SQLite 精确 messageId → 原 public 投影 → 既有运行过程查看器；链接只来自该消息的委派回执。
- 未运行测试或浏览器，未启动服务、SDK 或真实 API，未安装依赖。运行验收未完成。
- 首轮 P2 已修正：保存当前聚焦的精确消息 ID，状态刷新（包括控制／核对后的刷新）对打开的过程查看器重取该消息的运行投影，更新旧运行的 state/revision；请求序号与消息 ID 共同防止迟到响应覆盖后选消息。修正后 `node --check personal-secretary/ui/app.js` exit 0；未运行测试。

## Sources

- docs/FUNCTION-INVENTORY.md, 34, 37.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。

**Completed:** 2026-11-14
