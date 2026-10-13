# FD-016 本地 Worker 实现报告

<!-- aiw-data: FD-016-implementation-local-r1.json -->

Worker session：`fd016-worker-20261013-0f3443b8`；来源 `FD-016-000011-work-requested`。基准 master `3e13f49`，工作树 `feature/FD-016`。本报告只声明本地源码与编译，等待独立审查。

三语言统一 EXTERNAL_WORK、精确 skillRef 和启动角色，冻结可信技能，按 resultKind 返回报告。写角色有固定 Git workspace，审查 revision 对齐 inputs；只读不 stage/commit/push，verifier 不再额外注册集成能力。控制平面 fail 只接受 RUNNING，所以取得 start 租约后、模型或工作区业务动作前校验任务，错误可按既有协议回报。旧任务和八字段 Profile 不兼容。

TypeScript 移除 Codex SDK 的原决定保持：没有本地 SDK/CLI/平台包，两个模式使用系统 CLI 0.162.1。Exec 独立进程、stdin/JSON/ephemeral；App Server 一个受管 stdio 服务，每 attempt 独立 ephemeral thread，校验 thread/start 与 thread/read 元数据、版本/权限；请求/turn/事件关联，定向 interrupt 并等待实际终态。共享期限、租约、外围 workspace 排他协调和非空结果。未知终止保留工作区并停止领取；不自动回退。

Agents 0.20.0 / OpenAI 7.32.0 / Zod 4.6.7 以及两个 SDK 子包核对；修复 Zod 类型、raw usage 与输出预算，保持 AbortSignal、工具 settled、tracingDisabled/log off/store:false，并禁止 provider continuation。更新三语言 README、公共契约/运维、启动 env、发行拒绝规则、现有测试断言与单步分析/编码→审查模板；未自动改控制平面历史 profiles。

实际命令：`aiw git wt add FD-016`；`aiw fd claim FD-016 FD-016-000011-work-requested --session fd016-worker-20261013-0f3443b8`；逐项 git add/commit；compile-only 共三次（首轮、明确授权的最终检查、相关源码修正后一次）；定向 diff 空白检查与静态源码/schema 读取。父分支在预检时干净，工作树坐标已核对。编译临时依赖链接已移除。

`node scripts/compile-worker-runtime.mjs` 三次退出 0：TS/JS/Python 语法与 Go 编译，无类型检查、SDK 执行或保留发行产物。Git diff 空白检查通过。协议仅查询官方 rust-v0.162.1 固定 schema；最初不存在的 Thread.json/InitializeResponse 路径未通过，改为嵌入 Thread 定义和 v1 InitializeResponse 后核定。真实 --version/app-server/exec/turn、模型、认证、测试、发行构建、部署未运行。

用户确认按 Go 本地交付范围，生产认证归 FD-015，已解决 [范围反馈](FD-016-blocker-local-scope-20261013-0f3443b8.md)。ephemeral/store:false 不证明供应商零留存或全部 SQLite/日志禁存。TypeScript 不借用 Go 运行证据；并发、取消、未知资源、Windows/Unix 安装路径及 SDK 类型/行为仍有未运行风险。Python 原 SDK 准入保留；本 FD 的 Codex npm 依赖删除范围是 TypeScript。
