# Web Console

R9 在已经验收的 R8-S13 Fix2 Execution Platform 上提供完整的操作与监控界面。

访问：

```text
http://127.0.0.1:8080/ui
```

## 页面

```text
/ui                       Mission Control Overview 总览
/ui/runs/new              创建 Autonomous Run
/ui/runs                  Run 列表
/ui/runs/{runId}          Run 详情、Task、控制、Timeline
/ui/workers               Worker Fleet 与 Drain/Offline
/ui/approvals             Approval Inbox / 决策历史
/ui/system                Fleet Scheduling / Accounting diagnostics
```

## 当前可直接操作

WebUI 的所有写操作都调用现有 Application Service，不建立第二套 authority：

- 创建 Autonomous Requirement Run；
- Pause / Resume / Recover / Cancel；
- 修改单 Run `maxParallelism`；
- 修改 Fleet `maxParallelism`；
- Engineering Gate PASSED 后手工创建 ApprovalRequest；
- Approve / Reject；
- Drain Worker；
- 对 active lease 为 0 的 Worker 执行 Offline；
- 查看 Run / Task / Worker / Backlog / Scheduling / Timeline / Audit。

降低 Run/Fleet 并发上限时继续沿用已有语义：**不强杀现有 lease，只停止新的 claim，等待自然 drain**。

## Live Monitoring

Run 详情中的 **Replace Workspace** 可将尚未完成的任务改派到已登记且启用的 Workspace repository。操作员选择目录模式或 Git 模式，填写原因；Git revision 留空时，提交时解析并固定目标 HEAD。没有活动租约的任务会在同一事务内取消原任务、创建替代任务并重接尚未开始的下游依赖。CLAIMED/RUNNING 任务先向 Worker 请求停止，只有收到该 Worker 对原租约的停止确认，才会创建替代任务。原任务、attempt 和 instruction revision 保留；替换历史显示是否需要 Worker 停止、确认时间、请求、完成、冲突及拒绝原因。若待停止期间指令修改，或 Worker 自行完成、失败、租约变化或下游已推进，请求转为冲突，操作员需检查后重新决定。已完成任务使用 linked correction，不能替换 Workspace。

Overview/Fleet 分别每 5/8 秒读取现有 HTML URL，只替换标记的只读区域，保留表单、导航、焦点内容、展开详情和滚动位置。其他操作页继续整页刷新。编辑时暂停；还原初始表单值或重置表单后恢复；隐藏页面暂停，重新可见时读取。

最多一个请求在途，10 秒超时。失败保留上次快照，下一周期再试；快照时间表示最近完整应用的数据，30 秒没有完整更新时提示陈旧。登录跳转、角色变化或非预期 HTML 会停止轮询并提示重新进入。Worker 操作选项保留页面初次读取时的状态，通过 **Reload operation choices** 更新；提交时服务端仍校验实际状态。

首页最多展示 12 个非终态 Run、8 个关注 Run、8 个待审批项、8 个 Worker、每类 5 个等待任务以及最近 20 条活动。待审批与非终态 Run 总量另行准确统计。Fleet 分别最多展示 100 个 Worker、100 个 READY 任务和 100 条活动租约，同时显示对应的完整快照计数。READY 任务按优先级降序、创建时间升序、task ID 排序；租约按到期时间、task ID 排序。它们是当前快照的前 100 条预览，不提供跨快照分页。等待总量沿用互斥主阻塞优先级：policy、Run admission、capability、Worker capacity、Fleet admission。每条 READY 任务并列显示所有当前可观测限制；不推测历史 claim 决策。活动来自既有 Run/task/attempt/approval 权威记录，不是完整审计历史。

Fleet 中的 Run 链接仅在 subject type 为 `AUTONOMOUS_REQUIREMENT` 且存在对应 Run 时显示；其他 subject 或缺失 Run 原样显示类型/ID，不补造链接。活动租约显示已有的 Worker ID、task、attempt、状态和到期时间。心跳时间、容量和租约只作为可观测事实，不自动判定 healthy/unhealthy 或告警。降低 Fleet/Run 限额时已有租约继续自然排空；drain 停止新领取，已有租约继续；pause 撤销活动租约，cancel 不可逆。动作仍由服务端核对权限、CSRF 和当前状态；过期页面提交失败会显示实际错误。

机械轮廓、色相和身份标记由完整 Worker ID 确定，在首页和 Fleet 一致。管理状态、租约占用、runtime、capabilities 分别显示；失败 task 不代表 Worker 故障。已完成/当前任务数不表示 ETA 或工程完成百分比。

所有页面使用本地 CSS/SVG/系统字体的共享深色战术样式。**Motion: off** 只保存浏览器本地偏好，系统减少动效设置也停用脉冲。

## 审批证据

`/ui/approvals` 将待审批与最近 100 条已决定记录分区。待审批总数独立准确统计；已决定列表仅是最近窗口，不代表完整历史。每条记录展示已存结果 revision、source task/attempt、gate 关闭时间及 review/verification task/attempt 的 verdict 和 summary。Run Detail 在找到对应审批记录时展示同一份已存证据。

证据按审批记录中的 source task、result revision 和 gate 关闭时间关联。来源、review 或 verification 缺失或不一致时明确显示不可核对，不以当前 Git HEAD 或其他 attempt 补齐。摘要按普通文本显示。`publicationRef` 只作为已存标识；Web Console 不抓取外部文档、Git 全文或下载。审批动作继续使用既有 APPROVER/ADMIN、CSRF 和 `ApprovalService` 校验。Run Detail 将审批状态、证据和表单作为一个区域刷新，编辑或聚焦时暂停替换；过期或并发决定显示服务端实际错误。

## 可选登录和角色

Web Console 支持轻量 Session Authentication：

```text
VIEWER    只读
OPERATOR  创建 Run、Run 控制、Scheduling、Worker 控制
APPROVER  创建审批、Approve/Reject
ADMIN     全部 Web Console 操作
```

配置：

```text
AIW_WEB_CONSOLE_AUTH_ENABLED=false
AIW_WEB_CONSOLE_USERNAME=admin
AIW_WEB_CONSOLE_PASSWORD=
AIW_WEB_CONSOLE_ROLE=ADMIN
```

开启认证后，所有 WebUI POST 操作都要求 Session CSRF Token。

## 安全边界

Web Console 登录只保护 `/ui/**`。它**不会自动给现有 Management REST API 增加 Auth/RBAC**。默认仍绑定 `127.0.0.1`；在 Management API 安全强化完成以前，远程部署应放在受保护内网或反向代理/网络 ACL 后面，不要直接暴露公网。

## 技术方案

- Spring MVC
- Thymeleaf
- Vanilla CSS
- 少量 Vanilla JavaScript
- 单一 Spring Boot JVM
- 不需要 npm / Node 前端构建
- 不使用 SPA framework
- 不增加数据库表 / Flyway migration
