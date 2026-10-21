# FD-024 Revision 3 两项发现定向复核

<!-- aiw-data: FD-024-design-recheck-r3.json -->

本次定向复核未发现这两项修订仍有阻断缺口：**R3-F1、R3-F2 均为 resolved（仅设计契约层面）**。完整 FD 仍为 **INCOMPLETE / changes-needed**，因为 G1 实际源库、可信 owner 映射、在途处置及切换/回退决定尚未解决。本报告不豁免 G1，不是实现 verdict，不证明认证或 task start 已实际修复。

- 来源事件：`FD-024-000003-design-requested`。
- 独立复核会话：`/root/fd024_design_r3`；Planner session：`01a15e5f-d79d-73e0-bb3b-480e76aa474c`。
- 范围仅为原报告 `FD-024-design-review-r3.md` 的两项实质修订，未重新全面评审。

## R3-F1：resolved

事实：§6.6 已规定准入记录身份/目标世代及 restrictionRevision；actual start 在权限首锁和同一 start 事务内重读身份启用/boot/世代、当前人类角色、能力版本/条件/有效期、restrictionRevision、全部适用 DENY、目标暂停/接管/世代/业务状态、模型白名单及历史独立性。变化使旧 admission 失效，必须新候选和新准入；明确涵盖 A01 后续评审、A04、A03、A05/A06 和 A12 固定 attempt。预留只在平台证明从未 start 时解除，实际消耗和 UNKNOWN 占用不返还。

判断：这关闭了“动作 ADMITTED 后新增 DENY/暂停，但旧专业 task 仍获准 start”的设计缺口，保留真实心跳和完成上报。实现是否在所有调用路径执行该谓词尚未验证。

## R3-F2：resolved

事实：§6.4 页面已改为 `/ui/authority`；人工管理与机器 API 按 method+path 闭集分流。人工页面/POST 要求已配置认证、当前 session、写入 CSRF、当前角色及永久认证锁存，禁止 auth-disabled 本地 ADMIN 回退；机器使用独立 bearer、owner/scope，不创建浏览器 session 或接受 cookie/共享 Worker token 替代身份。人工查询在受权服务端页面渲染，未列路由默认拒绝；路径注册与匹配表需一起更新。

判断：这给出了现有 WebConsoleSecurityInterceptor/WebConsoleApiRoutes 必须扩展的边界，并避免把整个 authority 命名空间误接入浏览器认证或全部豁免。候选契约已足以指导这项接线，实际代码尚未实现或验证。

## 证据与限制

仅使用一次 PowerShell `Get-Content -Encoding UTF8` 读取当前 FD §6.4 与 §6.6 相关段落，再用 `apply_patch` 生成这份报告和同名 JSON。依据原评审已核实的路由和 TaskPolicy 源码作定向判断，没有扩展新源码搜索。事实为文档修订，安全后果为静态设计推导；未运行测试、编译、服务、HTTP、网络、迁移、格式化、工作流事件或 git 提交。原评审保持历史原貌。
