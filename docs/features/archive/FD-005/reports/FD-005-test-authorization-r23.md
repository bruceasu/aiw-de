# FD-005 Planner 测试授权 r23

<!-- aiw-data: FD-005-test-authorization-r23.json -->

## 审核结论

批准独立 Tester session `fd005-tester-20261005-b07656ac` 在仓库根目录执行且仅执行以下命令一次：

```text
node tests/fd005/r23/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r23.json
```

工作目录为 `D:\03_projects\AI-tools\aiw-dev`。授权绑定 `FD-005-000023-implementation-ready`、FD 修订 23、该事件摘要 `5687310002741b048722a581e2f01409df3d38bf1c536ddec841a43df726caf2` 及同名 JSON 的四个测试文件 SHA-256。预计 5–45 秒，测试子进程 45 秒截止。

## 调用代码与副作用审查

已静态检查 `tests/fd005/r23/` 的 `run-offline.mjs`、`register-loader.mjs`、`typescript-loader.mjs` 和 `worker.test.mjs`。九个新场景对应 API-16、17、18、19、23、24、27、29、33，受测入口是 API Worker 适配器、ApiRuntime 和其 SDK 路径，公共 attempt 仅与 API 适配器组合调用。测试使用内存 Responses 与内存控制平面替身；fetch 只接受 `https://fd005.invalid/v1/responses` 的合成响应，net/tls/dns 被拒绝，API/Worker token 从子进程环境移除。不启动其他 Worker、launcher 或平台入口。

副作用限于 OS 临时目录 `fd005-r23-*` 的合成文件，以及 `tests/fd005/evidence-r23/` 内以 `wx` 新建的 `node-test-output.txt`、`execution.json`。运行器用 realpath 核对临时目录父路径后清理该目录；没有 Git 命令、remote、真实服务、依赖下载或生产目录写入。文件、命令、FD 版本或 Tester session 变化时授权失效，须重新审查。本授权不包含其他测试、网络或最终构建。
