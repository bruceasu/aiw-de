# FD-005 Planner 测试授权 r33

<!-- aiw-data: FD-005-test-authorization-r33.json -->

## 审核结论

批准独立 Tester session `fd005-tester-20261005-r33-7e49c2` 在仓库根目录执行且仅执行下列命令一次：

```text
node tests/fd005/r33/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r33.json
```

工作目录 `D:\03_projects\AI-tools\aiw-dev`；绑定 `FD-005-000033-implementation-ready`、FD 修订 33、摘要 `b221e8017434890b39232c406446065ef4e24c777f51697c135484d56895b005` 与同名 JSON 中五份调用文件 SHA-256。预计少于 45 秒；测试子进程 45 秒截止，生产入口子进程各 5 秒截止。

## 调用代码与副作用审查

已静态检查新 `tests/fd005/r33/` 的运行器、加载器、入口网络守卫和两项用例。API-16 对保留角色启动 API Worker 生产入口，记录限定类别的启动错误、退出码与超时，不保存原始 stderr、路径或密钥；网络守卫在进程内阻止注册请求。API-33 通过 API Worker 的真实适配器、ApiRuntime/Agents SDK 和合成 Responses/Control Plane 检查 `complete` 第四实参中的 planner 报告、manifest 与模型路径。测试范围仅 API Worker 组合路径，不独立运行公共核心、其他 Worker、launcher 或平台。

子进程清除真实 API/Worker token；合成 fetch 只允许 `https://fd005.invalid/v1/responses`，并禁用 socket/DNS/TLS。副作用仅为 OS 临时目录 `fd005-r33-*` 合成工作区及 `tests/fd005/evidence-r33/` 以 `wx` 新建的两份原始证据；临时目录经 realpath 校验后删除。无真实服务、远端 Git、依赖安装、构建或生产目录写入。调用文件、命令、事件或 session 变化须重新审查授权。
