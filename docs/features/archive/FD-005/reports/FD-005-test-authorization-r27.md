# FD-005 Planner 测试授权 r27

<!-- aiw-data: FD-005-test-authorization-r27.json -->

## 审核结论

批准独立 Tester session `fd005-tester-20261005-bdc1bff5` 在仓库根目录执行且仅执行下列命令一次：

```text
node tests/fd005/r27/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r27.json
```

工作目录 `D:\03_projects\AI-tools\aiw-dev`；绑定 `FD-005-000027-implementation-ready`、FD 修订 27、摘要 `dbc2bc71b3bb6d1fda4bd3fdfde2915fb68ae91a1e70baac6670f9dd31b139c7` 和同名 JSON 中的四份调用文件 SHA-256。预计 5–45 秒，子进程 45 秒截止。

## 调用代码与副作用审查

已静态检查 `tests/fd005/r27/` 下的运行器、注册器、TypeScript 加载器和用例文件。用例只组合 API Worker 的生产适配器、ApiRuntime/Agents SDK 与公共 attempt；Responses 和 Control Plane 均为离线合成输入。输出预算改为符合生产最低门槛的 4096，复核 API-27/29 并打印不含工具内容的终端元数据；新增 API-28 的不完整工具参数拒绝。其余 r23 场景保留为本轮独立夹具。测试文件不修改 r23 或历史原始证据，不启动其他 Worker、launcher 或平台入口。

子进程清除 API/Worker token；fetch 只接受指定的合成 `fd005.invalid` Responses 地址，net/tls/dns 均被拒绝。副作用仅为 OS 临时目录 `fd005-r27-*` 的合成工作区，以及 `tests/fd005/evidence-r27/` 内以 `wx` 新建两份原始证据；临时路径核对后清理。无 Git、remote、真实服务、依赖下载或生产目录写入。任何文件、命令、事件或 session 变化须重新审查授权。
