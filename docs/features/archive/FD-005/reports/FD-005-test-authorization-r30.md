# FD-005 Planner 测试授权 r30

<!-- aiw-data: FD-005-test-authorization-r30.json -->

## 审核结论

批准独立 Tester session `fd005-tester-20261005-e8931791` 在仓库根目录执行且仅执行下列命令一次：

```text
node tests/fd005/r30/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r30.json
```

工作目录 `D:\03_projects\AI-tools\aiw-dev`；绑定 `FD-005-000030-implementation-ready`、FD 修订 30、摘要 `422bebacd953340895e93a6d254266eecf58b8ed26d77c8dc5d8202fea359e17` 与同名 JSON 的五份调用文件 SHA-256。预计小于 45 秒；父运行器对子进程设 45 秒上限，生产入口子进程各设 5 秒上限。

## 调用代码与副作用审查

已逐份静态检查 `tests/fd005/r30/` 的运行器、加载器、入口网络守卫和用例。r30 只组合 API Worker 生产适配器、ApiRuntime/Agents SDK 与其调用的公共 attempt，并用合成 Responses/Control Plane 观察 API Worker 行为；生产入口拒绝角色用例仍由该 Worker 的 `index.ts` 执行。新增场景涵盖 API-16、20、21、22、27、31、33，旧 r27 路径复核。API-20/21 与 API-31 仅按实际断言作部分证据，不预先计完整覆盖。

Responses 只允许 `https://fd005.invalid/v1/responses` 的合成 fetch；清除真实 API/Worker token，禁用 socket、DNS、TLS。API-22 固定 shell 字符串只在独占 OS 临时工作区写 `ready.flag` 并最多等待 600 毫秒，不访问网络或仓库文件。副作用限定为 OS 临时目录 `fd005-r30-*` 的合成工作区、`tests/fd005/evidence-r30/` 内以 `wx` 新建两份原始证据；临时路径经 realpath 核对后清理。无其他 Worker、launcher、平台、真实服务、依赖安装或最终产物。任何调用文件、命令、事件或 session 变化均需重新授权。
