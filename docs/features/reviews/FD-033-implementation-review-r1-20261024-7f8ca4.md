# FD-033 独立实现审阅 r1

<!-- aiw-data: FD-033-implementation-review-r1-20261024-7f8ca4.json -->

结论：**verification-passed**。本轮未发现需要返工的正确性、仓库规则或验收缺陷；结论限于下述源码及静态证据，不代表运行、迁移或发布验收。

- Reviewer Session：`fd033-implementation-review-20261024-r1-7f8ca4`，与 Worker 独立。
- 实际领取来源：`FD-033-000004-implementation-ready`；读取最新 pending 交接后以本 Session claim。
- 比较基线：`8ce1590`；最终源码：`8628bda`。`0a8a5df`、`2f05b43` 为随后 FD/Worker 证据提交。
- 已读取 FD Revision 4、Worker r1 双证据、稳定 secretary 规格、使用说明、原设计 D1–D5 与已 resolved 的范围反馈。用户确认 ALL 包含本人现有及后续适用目标，共享次数，无遗留范围问题。设计报告原结论仍是 changes-needed，不能称原报告已通过。

## 验收与实际调用链

| 验收范围 | 静态证据与判断 |
| --- | --- |
| AC1、AC2、AC6：功能分组与简化表单 | authority.html 与 action-permissions.js 使用服务端六类动作目录，主要字段为秘书、动作、到期和次数，可选范围折叠。内部状态、步骤、轮次和 JSON 不再成为人类授权输入；卡片显示范围、使用量、剩余、到期、效力和撤销。文字经 textContent/Option 输出，复用 console.css 色彩和确认框。旧详细记录保留在兼容折叠区。 |
| AC2、AC3：账号及精确范围 | PermissionTargetResolver 从正式 creator、execution_policy、冻结 context、审批源任务、成果/发布记录核对归属，冲突拒绝；不以仓库或任意 authority_target 代替正式来源。TASK 仅允许可证明精确来源的动作，步骤动作不冒称 TASK；A13 与普通 Worker 使用明确人工归属，机器不能首次认领。 |
| AC3、AC4：跨目标硬次数 | V18 以当前 Owner/Worker/action 唯一当前期，permission/request 唯一消费。ActionAdmissionService 在原事务和去重之后调用 AuthorityBudgets.consume 的父钩子；失败回滚不扣父次数，旧 settle 不返还父次数，record-only 不绕过父上限。AuthorityActions 的间接 A12 同事务使用原请求单独消费；协调 START 在迁入 A05/发布权限后明确拒绝旁路。 |
| AC3、AC4：最后一次受理与原边界 | grantActive 只核对有效期、角色及实际范围，不把额度归零当失效；TaskPolicy 对新子 Grant 另要求真实 permission_use，保留最后已受理 permit。原人工暂停、限制版本、目标/进程世代、固定尝试、未知效果和业务条件仍在原受理/Claim/开始路径。 |
| AC4、AC5：原请求、问答及兼容 | 原候选保存选择摘要、完整 Envelope、projection 与 boot，重试返回原值，隐藏发布子能力不能另造直接请求。新问答读取仅接受可信关联与原问题/来源/投影，旧 read_scope 不扩大；历史回执分支核对原 Worker，且不依赖父仍有效。进入新 regime 后旧 issue 及旧同动作能力不能回退旁路，替代/撤销保留历史。 |
| AC5：私人恢复与增量迁移 | business_actions.py 先保存 decision 的原选择，再准备，保存原 Envelope 后才进入旧 outbox；未知先读原请求，STALE 不重建，原 outbox 仍先核对回执。main.py/platform_client.py 仅审阅提交中的精确新增接线。V18 新建四表/唯一键，不改历史迁移或数据；新增 HTTP 路由分别经机器身份或人工登录/CSRF 与当前角色边界。 |

额外核对了 AuthorityRepository 的 Map 键排序与 Python store.encode，原投影/Envelope 的序列化比较与现有固定协议一致。平台候选只返回信封及必要摘要，没有把包含 spec、instruction/report 的完整快照送给新机器接口。新期确认明确替代同动作旧期，页面与私人适配均保留原 UUID；未知写结果不会自动创建另一授权期。

## 命令与证据边界

实际执行为本地只读 `Get-Content`、`rg`、`git diff/show/log/status`，以及 `aiw fd show`、子命令帮助和上述真实 claim；辅助路径/不支持的 receipt 子命令查找错误不作为通过证据。写入本审阅双证据与 FD Verification 后，仅做一次范围内静态检查，再发出本 Reviewer 的真实结论事件。未编辑实现源码、提交、归档或启动其它代理。

Worker 报告记录 `8628bda` 的离线 JDK 25 Maven compile（297 个生产源）、两个 JS 的 node --check、Python 新模块及已提交 main.py/platform_client.py 的内存 compile 通过；本 Reviewer 审核其修订覆盖与报告，没有重新执行这些检查。其它私人秘书/模型/启动脚本和 configurations/fragments/questions 模板的工作区差异全部排除；所审范围的其它源码相对 `8628bda` 无未提交漂移。

未运行测试、数据库/迁移、服务/HTTP/真实身份与 CSRF、Thymeleaf/浏览器、异步故障恢复、模型、网络/下载、最终构建、打包、重启或发布。这些运行行为和布局仍有未验证风险；按当前 FD 的静态/编译证据政策，不把未授权运行验收增设为归档门槛。通过后由 host 检查交付门槛并继续本地范围交付/归档。
