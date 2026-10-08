# FD-005 修复实施报告，revision 8

<!-- aiw-data: FD-005-implementation-r8.json -->

来源事件：`FD-005-000008-test-rejected`；Worker session：`fd005-worker-20261004-2916aea6`。本报告补充 [首轮实施 r5](FD-005-implementation-r5.md)，整个 FD 的源码范围仍按首轮清单与当前 FD 工作项审查。

## 原因与修改

独立 [测试报告 r5](FD-005-test-report-r5.md) 的一次真实离线执行得到 78 通过、60 失败；[PM r5](FD-005-test-decision-r5.md) 已退回。没有修改首轮用例、授权或原始日志。

1. EvidenceRecorder 接受控制平面既有 attemptNo，与 taskId/attemptId 共同关联；在创建目录前校验身份，拒绝非有限数值。源码和声明列明相同元数据字段，完整正文/密钥仍拒绝。
2. AttemptRunner 支持构造时传 Recorder，保留第一次 step 前赋值的原用法；缺少证据以固定配置错误拒绝。记录 phase 后、回调启动前再检查取消。等待资源超时设 resourceUncertain，移除重复宽限计时的窗口。
3. native 清除 DEBUG/DEBUG_*、NODE_DEBUG/NODE_DEBUG_NATIVE、RUST_BACKTRACE 及既有日志/AIW/OTEL 通道，强制 RUST_LOG=off。
4. RPC pending 请求记录操作及线程身份。start/read 回执必须明确 ephemeral=true/path=null；read 的线程 ID 必须匹配请求，待处理 ID 不能重用。线程通知给出持久化路径时拒绝。
5. 从生产 index 提取 createApiAttemptAdapter，将现有九角色、输入、session 和报告接到相同公共 attempt。类型不匹配明确 CONFIGURATION；生产仍使用实际 ApiRuntime、当前 SDK 与模型政策。

字段、预算及前置要求见 [公共输入契约](../../../workers/runtime-core/PUBLIC_CONTRACT.md)；实际 API 导出与宿主输入见 [适配入口](../../../workers/process-typescript-with-api/PUBLIC_INTERFACES.md)。更新 FD、公共能力矩阵与 launcher 说明，移除“尚无测试”和“全部工作项未完成”的过期表述。

## 静态证据及实际命令

静态核对 EvidenceRecorder/AttemptRunner、ToolHost、输出算法、native RPC 与环境，及控制平面 ClaimedTaskResponse：后者确有 attemptId 和 attemptNo，因此原 API session ID 使用 attemptId 保持正确。51 个前置失败的具体新增字段是 attemptNo；另外六个 Runner 夹具原先没有 Recorder，需独立 Tester 根据公开契约处理，不能把首轮失败改成通过。

实际执行：

- `aiw fd claim FD-005 FD-005-000008-test-rejected --session fd005-worker-20261004-2916aea6`，成功。
- `git diff --check` 限于本轮修改路径，exit 0，只有 Git 的 LF/CRLF 提示。

首轮 `node scripts/compile-worker-runtime.mjs` 的 exit 0 为当时源码的编译证据；本轮没有重复默认编译或测试，不能拿它声明新源码已通过。Tester 后续准备、临时编译及运行须绑定新的实现事件和授权。未运行类型检查、lint、formatter、最终构建、真实模型/控制平面/插件服务或联网命令。

## 尚未验证与下一步

首轮 222 场景、138 用例、78 通过/60 失败的证据保持原样；84 场景未运行。下一位独立 Tester 对新公共契约准备复测，补齐可离线观察的实际 SDK/适配与公共控制平面行为，另写精确命令、版本化测试文件及授权。

真实 Codex 平台禁存证据仍缺失，保持默认拒绝准入；合成 native 策略只验证入口，不认证真实部署。未回退 G1/G2、没有真实部署、提交或合并。整体 FD 尚未验收，Reviewer 尚未开始。
