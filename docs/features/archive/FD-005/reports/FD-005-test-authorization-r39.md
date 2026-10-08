# FD-005 Planner 测试授权 r39

<!-- aiw-data: FD-005-test-authorization-r39.json -->

## 审核结论

批准独立 Tester session `fd005-tester-20261005-r39-f5c82a` 在仓库根目录执行且仅执行下列命令一次：

```text
node tests/fd005/r39/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r39.json
```

工作目录 `D:\03_projects\AI-tools\aiw-dev`；绑定 `FD-005-000039-implementation-ready`、FD 修订 39、摘要 `2dc44b6b895a1c1aeece81a568c214c94f9c78b96486d25b4b32abf920e9e770` 和同名 JSON 的五份调用文件 SHA-256。预计少于 15 秒；总测试进程 45 秒截止，每角色入口子进程 5 秒截止。

## 调用代码与副作用审查

已静态检查 `tests/fd005/r39/` 五份文件。API-16 夹具使用 API Worker `src/index.ts`，对两个保留角色分别启动进程再统一断言；父运行器指定 TAP reporter，并从内存输出构造受控 JSON，记录 testStarted、assertionsReached、角色退出码/类别、允许的 Node 错误码、模块类别及安全 basename、计数。原始 stdout/stderr 不写盘，诊断字段按允许名单重建，可区别测试自身未启动与入口加载或角色拒绝。

子进程移除真实 API/Worker token，使用合成 key，拦截 fetch/socket/DNS/TLS；没有真实服务、其他 Worker、launcher、平台、依赖安装或构建。副作用限定为 OS 临时目录 `fd005-r39-*` 和 `tests/fd005/evidence-r39/` 中以 `wx` 新建的 `diagnostic.json`、`execution.json`；临时路径核验后删除。调用文件、命令、事件或 session 变化须重新审查授权。
