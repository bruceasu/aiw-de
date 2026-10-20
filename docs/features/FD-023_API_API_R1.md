# FD-023: 所有控制平面 API 统一使用 /api/r1

**Status:** Open
**Revision:** 2
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户明确要求目前全部 API 属于 r1，将其他路径改为 `/api/r1/...`。现有路径混用 r6/r7/r8、未编号 `/api`、`/ui/api` 和 Worker 协议独立前缀，容易混淆版本和漏配认证。

## Options and decision

不保留旧路径 alias，也不以重定向代理旧写请求；按用户明确的全量路径替换要求同步仓库调用方。统一 `/api/r6|r7|r8/X` 和 `/api/X` 为 `/api/r1/X`；`/ui/api/X` 为 `/api/r1/X`。`/api/worker-protocol/v1` 为 `/api/r1/worker-protocol/v1`，保留 worker 协议内部 v1 后缀。普通 HTML 页面继续 `/ui/...`。不改变 JSON、状态码约定、身份和权限业务规则。

## Solution

1. 更新所有生产 controller 基础路径。Goal/Secretary/Delegation 混合 HTML/JSON controller 去掉 class `/ui`，页面方法明确 `/ui/...`，API 方法明确 `/api/r1/...`。
2. 旧控制台 API（goals、secretary、delegation、task-outputs、workflow-plans、plan-editor）迁出 `/ui` 后继续经过原人工登录、CSRF、当前角色和 owner 校验。六个 API 根共用路径分类，WebConsole interceptor 以确定优先级覆盖新根和原 UI。通用 DelegationManagementMvc 排除这些已受保护 API，避免重复身份处理、第一次启用被自身 legacy receipt 阻挡和过度要求 Operator。其余管理 API 保留首次代理启用后的认证；审批仍 Approver，删除仍 Admin。Worker bearer filter、额外专用身份 interceptor 和机器路由白名单同步新前缀。
2a. 独立设计评审 D1 已纳入：`/ui/workflow-plans` 迁至 `/api/r1/workflow-plans`；WorkflowEditor 的正常 JSON 方法及 WorkflowQuestion 迁至 `/api/r1/plan-editor/**`，其 HTML/form 路径仍 `/ui/plans...`。推荐设置只有异常处理器 ResponseBody，正常 GET/POST 为页面/重定向，保留 `/ui/system/recommendations`。共享认证分类包含六根。设计报告 `reviews/FD-023-design-review.md` 的唯一缺口已在此解决，不是实现通过。

3. 更新仓库 Worker（TS/JS/Python/Go/Java 示例）、浏览器脚本、现行源测试和当前文档/稳定规范的控制平面 URL。真实历史证据、归档 FD、原始覆盖数据和二进制不改写；不改变 OpenAI 等外部服务 URL。请求 JSON、worker runtime/profile 名称、测试类历史编号不因 URL 改名。
4. 不增加兼容 alias。现有外部客户端必须同步切换；本次不运行部署、服务或网络，也不安装构建产物。

## Scope

生产路由、认证路径分类、所有在库当前客户端与测试 URL、当前说明和稳定规范；不修改数据库、业务操作、历史运行结果或归档证据。

## Work items

每项规模小、难度中，预计半天以内；无未决业务政策。实现后默认静态及 compile-only，无测试执行授权。

- [ ] 1.1 服务端统一 API 路径并保持 HTML 页面路径；检查所有 controller 和公开 URL。无依赖。
- [ ] 1.2 同步机器认证、额外代理身份和控制台会话边界，处理迁出 UI 的首次启用及角色兼容；依赖1.1。完成标准：六个 console API 根受原边界保护，机器不受人工 session 误拦。
- [ ] 2.1 同步所有在库当前 Worker、浏览器客户端及源测试 URL；依赖1.2。完成标准：生产调用方无旧控制平面 URL，不改原始历史证据。
- [ ] 2.2 更新当前文档和稳定规范；依赖2.1。完成标准：说明canonical路径和无alias兼容影响。
- [ ] 3.1 允许范围内生产 compile-only 与一次最终静态检查，提交完整 Worker MD/JSON 证据；依赖2.2。不将静态或编译当运行时通过。

## Acceptance

- 所有本系统对外 JSON/文件 API 在 `/api/r1/...`，无 r6/r7/r8 或 `/ui/api` 生产映射/alias。
- 保留 `/ui` 页面、JSON和业务行为；各 Worker/浏览器请求使用与服务器一致的新地址。
- 新控制台 API 保留人工登录、CSRF、角色和 owner 校验，首次启用不会因自身 legacy receipt 死锁；Approver-only 用户保持授权中心原操作范围。
- 普通及专用 Worker 的注册/心跳/领取/上报/输出继续使用机器认证，Drain/Offline 保持管理写入；非空 context path 分类保持应用相对路径语义。
- 所有在库当前测试及当前文档同步；历史证据未重写。外部客户端路径兼容变更如实说明。

## Verification

默认静态审查和离线 compile-only。生产 Java 使用本地 JDK25/Maven；Worker TS 用内存语法编译、Python内存compile、Go直接编译到NUL、Java示例编译至临时classes、JS node --check，使用已有依赖，不联网或生成分发包。无测试、服务、HTTP、浏览器、迁移、安装或部署。独立设计评审覆盖公开接口及认证分类，最终独立 fd-review 后默认本地交付、清理和归档。

## TODO

先做有界独立设计评审；实施、独立实现评审、本地交付、清理、Complete 归档由真实阶段推进。

## Sources

- 用户2026-10-21指令：目前api都属于r1，把其他路径统一到 `/api/r1/...`。
