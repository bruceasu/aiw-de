# FD-010 Planner 测试授权，第 1 轮

<!-- aiw-data: FD-010-test-authorization-r1.json -->

## 审核结论

批准独立 Tester 在 `D:\03_projects\AI-tools\aiw-dev\.wt\FD-010` 执行一次 `node tests/fd010/package-contract.mjs`。授权仅绑定实现事件 `FD-010-000005-implementation-ready`、FD 修订 5、摘要 `a811fce4969b6c1306b3e23e5ebb3a2ba081b7941b8d467d8870893aa93f2fdd` 和 Tester Session `fd010-tester-20261007-11617f`。

已静态检查测试脚本：四项离线黑盒断言分别检查包内 ESM 入口及共用导出、旧顶层入口无法导入、缺少证据时 attempt 拒绝执行回调、UTF-8 输出预算。脚本只导入包内模块并在内存中断言；不写文件、不启子进程、不联网、不安装依赖、不生成构建产物，预计 5 秒内完成。该命令不覆盖 Worker 启动、发布包、两个 SDK 后端或完整 attempt 生命周期。
