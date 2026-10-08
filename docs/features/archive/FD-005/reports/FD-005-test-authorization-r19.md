# FD-005 Planner 测试授权 r19

<!-- aiw-data: FD-005-test-authorization-r19.json -->

## 审核结论

批准独立 Tester session `fd005-tester-20261005-58dba13b` 执行且仅执行以下命令一次：

```text
工作目录：D:\03_projects\AI-tools\aiw-dev
node tests/fd005/r19/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r19.json
```

授权绑定 `FD-005-000019-implementation-ready`、FD 修订 19、该事件的 SHA-256 `8b50c27f32a1b75e3eef572f5f96eddfa57b5cd367ddb1af7000920df8b3f84d`，以及同名 JSON 中列出的四个测试文件摘要。预计 15–45 秒；子进程有 30 秒截止。

## 调用代码与副作用审查

已静态检查 `tests/fd005/r19/` 下的 `run-offline.mjs`、`register-loader.mjs`、`typescript-loader.mjs` 和 `worker.test.mjs`。15 个合成用例只以 `workers/process-typescript-with-api` 的 `createApiAttemptAdapter` 和角色配置为受测入口；公共 attempt 仅与该适配器组合调用。用例覆盖九角色、角色错配、无 verdict、只读变脏、工具失败、完成响应未知，以及真实本地 Git 工作树删除失败。未调用其他 Worker、launcher 或平台入口。

运行器清除 API/Worker token，并在测试进程禁用 fetch、net、tls、dns。Git 夹具仅在 OS 临时目录创建本地仓库和 worktree，无 remote；锁定的 worktree 在同一临时目录内解锁并删除。运行器在核对临时路径父目录后递归删除自己的 `fd005-r19-*` 目录，只以 `wx` 在 `tests/fd005/evidence-r19/` 新建 `node-test-output.txt`、`execution.json`。该命令不下载依赖，不连接真实 Control Plane 或模型 API，不写生产目录。若文件摘要、FD revision/digest、Tester session 或命令改变，本授权失效，须重新审查。

这是针对上述精确命令、文件和副作用的低风险 Planner 授权；不授权其他测试、网络、发布构建或真实平台验证。
