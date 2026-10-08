# FD-005 实施报告 r36

<!-- aiw-data: FD-005-implementation-r36.json -->

- Worker session：`fd005-worker-20261005-69fe2eb5`
- 来源事件：`FD-005-000035-test-rejected`
- 撰写时 FD 修订：35，文件 SHA-256：`6db7e374a668268cc1aaf1cf5d5af7bf97f5e341646ce4a464bdfadba45478ef`

## 静态诊断与交接

[Tester r33](FD-005-test-report-r33.md) 的唯一获批命令 exit 1；API-33 经正确的第 4 实参断言通过，API-16 在首个保留角色处归类为 `module-or-syntax-load-error`，未触发网络守卫但未到达角色拒绝。第二保留角色未执行。[PM r34](FD-005-test-decision-r34.md) 因失败及 23/33（69.70%）完整场景退回。旧证据未重跑或改写。

静态对照显示 API Worker 包正式启动脚本为 `node dist/index.js`，r33 夹具直接执行 `src/index.ts` 并通过测试自定义加载器转译。`index.ts` 顶层静态导入多个 TS 模块，运行 `loadConfig()` 前还动态导入 ApiRuntime 并检查 SDK 版本。加载失败可能发生在这些前置环节；现有宽泛类别不足以定位具体阶段。生产 `loadConfig()` 内确实先检查保留角色，随后才可能 `client.register()`，但该源码顺序不能证明 r33 子进程实际到达此处。尚无具体生产缺陷证据，本轮不改生产源码。

下一轮独立 Tester 仅在 API Worker 范围内，用新夹具保存允许名单中的具体 Node 加载错误码和失败模块**类别或安全 basename**，不保存 stderr 原文、路径、密钥或环境值；修正加载方式后才重新验证两个保留角色均在注册前拒绝。若运行证据指向生产源码缺陷，Worker 再做局部修复。不能把新诊断当成 r33 测试已通过。

## 命令与风险

- `aiw fd claim FD-005 FD-005-000035-test-rejected --session fd005-worker-20261005-69fe2eb5`。
- 定向静态读取包启动配置、`index.ts`、测试加载器、r33 报告/原始元数据；`git diff --check` exit 0（仅行尾转换提示）。本轮未运行测试、编译、构建、联网或独立其他 Worker。
- API-16 仍未覆盖；累计 23/33 完整场景，分支未测。真实服务、远端 Git、平台 G2 和非 API 运行时尚无本轮行为证明。
