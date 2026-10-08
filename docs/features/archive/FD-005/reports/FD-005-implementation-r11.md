# FD-005 Worker 实施报告 r11

<!-- aiw-data: FD-005-implementation-r11.json -->

- Worker：fd005-worker-20261004-2916aea6。
- 来源：FD-005-000011-test-rejected，领取时 FD Revision 11，摘要 eab06b4b8aa4a940bba0b4df48d5fc32b92a3c820e1eaf50430e81a37e6008ba。
- 结论：本轮修复源码与公共契约已落实，尚未执行新的授权测试；整体 FD 尚未验收。

## 修改与原因

1. **原始参数与 SDK 错误传播。** Agents 0.3.9 实际 tool.mjs 读取 errorFunction；旧 toolErrorFunction 选项不生效。新增 hosted-tool.ts，工作区、插件和 history_source 工具都经同一适配器。在 SDK 解析前验证原始 JSON，额外字段、错误类型或损坏 JSON 进入公共 ToolHost 的失败记录，不能仅靠 SDK 错误文本继续成功。errorFunction=null 保持不可恢复异常的传播，仍支持明确声明可修正的 RuntimeFault。
2. **完整期限和终态。** Evidence 初始化、阶段日志、工作区只读基线、报告验证及成功准备均进入 AttemptRunner 活动追踪。取消后的保存、失败上报、资源等待及清理共用一个独立有界窗口。正常成功记录 settled，并保存 ready-to-report 后发送 complete；响应未知不能重发 complete 或改发 fail。正常完成不再误记 PROCESS_TREE_UNCERTAIN。
3. **未知资源保留。** 进程终止未确认时协调器保留自身和活动租约，拒绝后续操作；普通已结束的失败仍释放租约。工作区清理取得排他租约、受独立期限约束；未知资源时不删除工作区或清空 adapter。runCommand 的原进程及 Windows taskkill 等待均有 5000ms 上限，返回未知不代表进程已经结束。
4. **公共 slot。** 两个 TS 生产入口共用 runWorkerSlot，公开 resourceUncertain 最终布尔结果。资源未知使 slot 永久停止领取，即使观察回调或 drain 出错也不能恢复；主机回调停止其他 slot 并进入 drain。角色工厂在模型启动前拒绝不可执行角色。
5. **协议兼容。** TIMEOUT 只保留在内部 RuntimeFault；failureCategory 映射为已有 TOOL_ERROR。控制平面六个公开错误类别、现有报告和模型指导保持原契约。API 模型并行工具保持开启，SDK 与直接 OpenAI 依赖未再次升级。

具体路径、命令和来源见同名 JSON。公开入口与行为分别记录在 [公共核心契约](../../../workers/runtime-core/PUBLIC_CONTRACT.md)、[API 接口](../../../workers/process-typescript-with-api/PUBLIC_INTERFACES.md) 与 [Worker 契约](../../../workers/WORKER_RUNTIME_CONTRACT.md)。

## 静态证据和实际命令

实际领取：aiw fd claim FD-005 FD-005-000011-test-rejected --session fd005-worker-20261004-2916aea6。静态读取了核心调用链、两个 TS 入口、固定 SDK tool.mjs、FD 与 r8 失败证据。未运行本轮编译、类型检查、测试、覆盖、格式化、网络或构建。首轮 compile-only exit 0 和 r8 三项临时编译 exit 0 都是历史结果，不能证明新源码通过。

两个 FD 文本编辑脚本因 PowerShell 管道编码匹配失败而未写入；已改用 apply_patch 完成编辑，没有继续重试该失败命令。

## 历史失败与新验证边界

[r8 测试](FD-005-test-report-r8.md) 实际执行 261 个用例，256 个原始 pass-case、5 个失败。REPORT-manifest 仅部分执行，完整通过为 255/285=89.47%；全 FD 分支尚未测量。core 629/777=80.95% 和 API 内存转译 94/116=81.03% 仅为局部统计。[PM 已退回](FD-005-test-decision-r8.md)，未豁免失败。

非法额外工具参数未阻止成功上报已经到达目标断言。普通 Error 用例没有建立可修正错误前提；两个摘要用例只发出普通请求，没有到达摘要；Git finalize 缺少保留的 argv/stderr 与临时终态诊断。这些缺口必须在独立 r11 用例中建立完整前提并保存诊断，不能靠静态推测结论。旧用例、报告、授权及原始证据保持原始事实。

## 尚未验证与风险

新的入口冻结后，Planner 按实际全部执行文件、命令、事件、FD revision/digest 与 Tester session 授权唯一离线执行。24 个真实或跨语言场景和一个部分兼容场景仍保留在分母。真实 Codex 全会话禁存未认证，生产默认拒绝准入；未调用模型、业务端点或发布服务，没有最终产物。未知进程需要人工确认终止和对账后再恢复，不能自动解锁或回滚。

Reviewer 应结合 [首次全范围实施报告](FD-005-implementation-r5.md) 与 [r8 修复](FD-005-implementation-r8.md) 检查整个 FD；部分源码早于当前 diff，不能只审未提交变更。本轮 Reviewer outcome 仍为 0。
