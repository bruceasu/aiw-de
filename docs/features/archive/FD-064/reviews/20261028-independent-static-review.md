# FD-064 独立静态审查

- Reviewer：`codex-fd064-review-20261028`。
- 来源事件：`FD-064-000002-review-requested`，已 claim。
- 比较基线：`e3d04eb3f69d4841f44f7ada85b31bcab00ad3ba` 上本 FD 的秘书/专业任务准备变更；排除其他历史 dirty、desktop_webview.py、Web plan 页面改动。
- 最终结论：**通过**。静态无阻断；已读取原 Robot 一次无模型准备回执，绑定自身 DRAFT、等待原范围确认，没有提交专业执行或改动旧计划。

## 核对结果

- 未绑定工作先以自身 work ID 创建/查询惰性 PersonalPlan，不因列表仅一项或模型给出旧 planId 而选择历史计划；只有当前 work 上明确关联的三个计划字段参与后续选择，模型不符会拒绝。
- PersonalPlans 持久化单一请求键，CREATED 也读取平台最新状态；中断/UNKNOWN 只 GET 原请求，不重新 POST。不匹配 owner/Worker/平台实例或 receipt 时不认定可用，已有专业请求 SENDING/UNKNOWN/ACTIVE 时不创建替代草案。
- 定向恢复仅用于未绑定且存在 REJECTED 专业记录的安排；在摘要/模型前准备自身草案并抛现有 AuthorizationRequired，保留原任务/上下文，不 POST 新专业任务、不复制旧计划授权。取消/移除工作及已有不确定专业请求受保护。
- 平台计划选择仍经 enabled/managed/read scope/owner 过滤和 Authority 锁；选项附状态，新非 DRAFT 请求在工厂准备前返回 typed409。已有同身份 preparation 仍进入既有不可变 intent/范围校验，不替换原 accepted 任务；continue/read 路径保留。
- 409 客户端只接受明确白名单 code 和受长度约束的 planId/planState，忽略 message/raw exception/额外字段，安全 rejection 留存于原决策；提示明确不是连接凭据问题，不放宽范围。
- 专业 work 的聊天完成不再写 work DONE，详情区分“本轮回复结束”与实际工作完成，并显示 job.result。DONE 提示从 data-i18n-skip 的原始值移到可翻译段落，新增中日文映射。

## 证据与限制

- Reviewer 实际仅静态 git 差异/源码/测试/FD 阅读、真实 `aiw fd show/claim`；未测试、编译、调用平台 API/模型、启动业务执行或修改产品源码。
- 已读十项 Python 测试（真实临时私库、模拟 HTTP）及两项 Java 临时 SQLite 测试断言；执行者报告十加二通过、运行时编译和319源 Java compile成功。首次 Java 测试编译泛型错误后只做相关修复重跑，FD 保留失败历史；不冒充 Reviewer 独立运行。
- 未执行真正授权后提交/实现 Robot、界面视觉或跨进程并发实验；只准备草案不等于已委派/执行/完成，scopeReady 不等于历史计划可承接新工作。

## 原工作运行证据复核

- 已读取 `fd064-runtime-receipt.json` 和 `fd064-platform-receipt.json`：原 Robot 工作 `ebc325ad-d907-489a-9e88-3c01076a171a` 现 NEEDS_CONFIRMATION/revision21，新关联 `FP-262e4ca6-008e-37b6-a5eb-9b169480ec05` 为 DRAFT/v0、taskCount=0。
- 自身 PersonalPlan 请求保留明确 Robot 目标与工作原 ID，receipt.creationGrantsExecution=false；等待授权 action 为 professional_options、planId 正确指向新草案，不指向旧已完成计划。
- 原专业请求数仍为5且全部 REJECTED，旧计划仍 COMPLETED/v2。没有为修复重复 POST 专业任务、复制旧范围或伪称 Robot 已实现。
- FD Verification 记录平台与原私库备份/integrity ok、无活动/未知请求时正常停止旧秘书和重启加载；新 boot 仅沿用原授权，actions=[]/planId=null 未增加代理动作/计划范围。脚本 HTTP200 与完整 UTF-8 源码一致，首次中文 marker 管道编码失真保留记录，未称浏览器视觉验收通过。
- 所有运行操作由执行者实施；Reviewer 只静态读取证据，未调用模型、API、测试、服务或专业执行。
