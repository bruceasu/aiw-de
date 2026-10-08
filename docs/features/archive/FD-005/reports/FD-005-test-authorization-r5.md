# FD-005 Planner 测试授权 r5

<!-- aiw-data: FD-005-test-authorization-r5.json -->

## 审核结论

批准一次精确离线命令：`node tests/fd005/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r5.json`，工作目录 `D:/03_projects/AI-tools/aiw-dev`。预计 60–150 秒；子命令上限 75/45/120 秒，必要的终止辅助进程最多额外 5 秒，总预算 245 秒。

授权绑定 FD-005-000006-implementation-ready、FD revision 6、digest f29cb07ce849cbbc92c64095bcac4e37ce873ef160d3d3ff6fcf819b38ee5015、Tester fd005-tester-20261005-7865d383 以及同名 JSON 中八个已审查文件的摘要；代码、FD 或事件改变后本授权失效，不能重复运行以获得通过。

已静态阅读 harness、场景清单、core/session 用例、假 native、fixture、reporter 和最终入口，复核摘要。代码只调用 Node 内建模块、公开 runtime-core、已有 Go 工具链和假程序。先编译两个程序到专属 .tmp-*，再串行运行两组用例与 Node V8 覆盖率；Go 网络、工具链获取和 CGO 关闭。HOME/USERPROFILE/CODEX_HOME、临时目录与 Go cache/module cache 专属，不使用主机认证；发现真实祖先工程配置时保留未覆盖而不读取正文。

副作用仅在 tests/fd005 的拥有标记临时根与 evidence-r5，保存合成输入的逐例结果、命令输出和覆盖率；不安装依赖、启动真实 Codex/SDK 会话、调用模型/控制平面或外部服务、不修改产品源码或创建发布物。原始覆盖率只代表实际插桩的代码，不能代替全 FD 分支率。

超时只操作本命令已拥有的 PID；终止辅助进程有 5 秒上限。终态未知时保留临时根；删除前核对 realpath、专属名称和 owner。合成 policy/proof 有 syntheticTestOnly 标记，绑定假程序，不能认证真实平台禁存。

依据 fd-workflow auto 的 Planner 低风险命令规则：命令聚焦、离线、全部调用代码可检查、写入仅限指定/临时目录。因此本条不需额外人类批准；它没有扩大既有网络、模型、安装、真实部署或主机权限授权。

Planner：fd005-planner-20261004-cf390565；决策时间：2026-10-04T22:38:28+00:00。测试结果尚未发生，不记录通过。
