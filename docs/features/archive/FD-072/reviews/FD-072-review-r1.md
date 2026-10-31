# FD-072 独立审查 r1

## 结果

**Changes requested。** 有一项 P2 验收缺口：当前配对、Grant 授权和进程启用分成多个人工操作，不符合 FD 对 Control Plane 一次人工确认的要求。此前两项 P2 修复经本次静态复核均已落实。

## 审查基线

- FD：`docs/features/FD-072/FD-072_AGENT_CONTROL_PLANE.md`
- 交接事件：`FD-072-000005-review-requested`；Reviewer session：`fd072-reviewer-20261031`
- 工作树：`D:\03_projects\AI-tools\aiw-de\.wt\FD-072`，分支 `feature/FD-072`
- 基线提交：`098690d974b025a28aefa8e414d8382b2a66d735`（`docs(fd-072): record agent control plane design`）
- 审查对象：基线上的完整当前工作树差异，包括 49 个已跟踪文件的修改/删除及 9 个未跟踪实现/部署文件；差异尚未提交。

## Findings

### P2 — 配对、授权和启用未实现为一次人工确认

**位置：** FD 第 57、82–85、128、134 行；`src/main/java/me/asu/aidev/authority/AgentIdentityService.java:53-80`；`src/main/resources/static/ui/agents.js:30-38`；`src/main/resources/templates/ui/authority.html:31`；`src/main/resources/static/ui/action-permissions.js:112-116`；`personal-secretary/README.md:323`。

**可复现证据：** 向 `/api/r1/authority/agents/pair` 提交有效邀请后，`AgentIdentityService.pair` 立即插入 `identity_kind='AGENT'` 身份并返回 `PAIRED_UNAUTHORIZED`，没有先把待配对身份交给人类确认。`/ui/agents` 的界面明确提示配对后仍需单独创建 Grant；动作授权在 `/ui/authority` 的独立确认表单中提交。Agent 建立新进程会话后会被置为禁用，页面另有“Enable or disable an Agent”的确认操作；README 也要求单独批准 Grant 并显式启用。

**验收影响：** FD 第 85 行要求 Control Plane 展示待配对身份，并由人类在一次确认中批准 Agent 注册和委托范围；Acceptance 2（第 134 行）要求用户只在 Control Plane 做一次人工确认。当前实现既自动注册已配对身份，又把 Grant 批准与 Agent 启用拆成后续人工步骤，因此该验收未满足。虽然新身份在 Grant/启用前不能执行授权动作，当前设计也保持 fail-closed，但用户仍需多次操作，且无法在配对确认时核对身份与精确授权范围。

**建议：** 将已证明身份的 Agent 暂存为待确认状态，并提供一次 Control Plane 确认，把身份、精确新 Grant 和所需启用绑定到同一明确操作；在该操作完成前保持不可执行。若进程启动/证书轮换必须保留独立启用确认，则应先调整 FD 的一次确认验收并由产品决策确认，而不能把当前行为标记为满足该验收。

## 本次复核通过的修复点与安全证据

- `BusinessActionPermissions.consume`（`src/main/java/me/asu/aidev/authority/BusinessActionPermissions.java:124-138`）处于事务中：已消费请求幂等早退；使用次数与 revision 一次递增、use 记录、`PERMISSION_CHANGED` outbox 和审计写入在同一事务路径。通知版本为更新后 `p.revision()+1`。本次静态复核未发现重复消费重复通知的问题。
- `AgentMessageService.acknowledge`（`src/main/java/me/asu/aidev/authority/AgentMessageService.java:74-90`）对未确认消息拒绝已过 lease 或消息期限的 ACK，并在 CAS UPDATE 中再次检查两项时间条件；已经 ACK 的相同有效投递 token 仍返回幂等确认。
- 远程传输静态路径（`AgentTransportSecurity.java:21-59`、`AuthorityHttpConfiguration.java:50-63`）要求 TLS、安全层提供受信任客户端证书，并对正常远程请求匹配已批准 SHA-256 指纹；缺少 TLS/证书或指纹不符会拒绝。配对与轮换例外只接受 CA 信任链上的证书，轮换新指纹仍需人类批准。没有发现允许远程 HTTP 降级或跳过证书验证的代码路径。
- 旧协议身份被标记为历史主体；新协议只认证 `AGENT` 身份。配对创建全新身份，撤销会改变身份类型、轮换凭据摘要、失效会话并撤销活动 Grant/Permission，历史记录保留。未发现旧凭据或旧 Grant 自动迁移为 Agent 权限。
- Outbox 仅允许 `GRANT_CHANGED` / `PERMISSION_CHANGED`，信封仅引用资源 ID 与版本等投递元数据；秘书客户端拉取当前授权状态后再 ACK。静态字段检查与当前 FD 决策相符。

## 执行记录与范围限制

实际执行的审查/工作流命令包括：

- `aiw fd --help`、`aiw fd claim --help`、`aiw fd emit --help`
- `aiw fd show FD-072`、`aiw fd claim FD-072 FD-072-000005-review-requested --session fd072-reviewer-20261031`
- `aiw fd show-report FD-072`、`aiw fd show-review FD-072`
- `git status --short`、`git rev-parse HEAD`、`git log -1 --format=...`、`git diff --stat`、`git diff --name-status HEAD`
- `rg` 和 `Get-Content`：只读检查 FD/OpenSpec、迁移、认证/授权服务、UI、Agent 客户端、消息协议及相关差异。

本次未运行测试、编译、验证脚本、应用或浏览器；未发起网络请求、未启动 EventBus、未执行部署或证书签发/轮换。FD 中记录的编译成功属于实现阶段证据，本 Reviewer 未重跑。mTLS 的真实 CA/truststore、TLS 终止/透传和证书轮换部署行为尚未在运行环境验证；本报告对 mTLS 的结论仅是源代码静态检查。
