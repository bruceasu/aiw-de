# FD-005 Planner 测试授权 r36

<!-- aiw-data: FD-005-test-authorization-r36.json -->

## 审核结论

批准独立 Tester session `fd005-tester-20261005-r36-a4d7c9` 在仓库根目录执行且仅执行下列命令一次：

```text
node tests/fd005/r36/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r36.json
```

工作目录 `D:\03_projects\AI-tools\aiw-dev`；绑定 `FD-005-000036-implementation-ready`、FD 修订 36、摘要 `c91c8ebc64fedf695d33612b045483f5ece5e1b0c075f962f7472ad3d27518c1` 与同名 JSON 的五份调用文件 SHA-256。预计少于 15 秒；总测试子进程 45 秒截止、每角色入口子进程 5 秒截止。

## 调用代码与副作用审查

已静态检查 `tests/fd005/r36/` 的运行器、加载器、网络守卫与 API-16 两角色入口用例。该用例只启动 API Worker `src/index.ts`，输出合成角色、退出码、超时、允许名单错误类别、Node 错误码、模块类别和安全 basename；原始子进程 stderr 留在内存中供分类，不写盘。父运行器过滤 Node TAP 输出，只持久化上述脱敏元数据与计数。

子进程移除真实 API/Worker token，拦截 fetch/socket/DNS/TLS；没有真实服务、其他 Worker、launcher、平台、依赖安装或构建。副作用仅为 OS 临时目录 `fd005-r36-*` 和 `tests/fd005/evidence-r36/` 中以 `wx` 创建的两份证据；临时路径经 realpath 核验后删除。调用文件、命令、事件或 session 变化必须重新授权。
