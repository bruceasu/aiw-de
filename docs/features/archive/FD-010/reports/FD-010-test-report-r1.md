# FD-010 独立测试报告，第 1 轮

<!-- aiw-data: FD-010-test-report-r1.json -->

来源实现事件 `FD-010-000005-implementation-ready`；Tester Session `fd010-tester-20261007-11617f`；所测 FD 修订 5，摘要 `a811fce4969b6c1306b3e23e5ebb3a2ba081b7941b8d467d8870893aa93f2fdd`。

## 结论

经 Planner 授权，仅执行一次 `node tests/fd010/package-contract.mjs`，退出码 0，四项黑盒断言均通过。19 个可观察场景中覆盖 4 个，需求场景覆盖率 **4/19 = 21.05%**；业务代码分支覆盖率未测量。通过仅说明包内 ESM 入口可导入、旧顶层入口不可导入，以及两个公共模块行为。源码 Worker、发布包和在线后端均未运行。

## 场景与证据

| ID | 可观察场景 | 结果 | 证据或未覆盖原因 |
| --- | --- | --- | --- |
| S01 | 包内 ESM 入口可导入，并暴露共享 attempt、slot 与 Control Plane API | 通过 | 测试输出 `ok 1` |
| S02 | 旧顶层 ESM 入口导入失败 | 通过 | 测试输出 `ok 2`，断言 `ERR_MODULE_NOT_FOUND` |
| S03 | 无证据 Recorder 时 attempt 拒绝执行操作 | 通过 | 测试输出 `ok 3`，回调未执行 |
| S04 | UTF-8 输出受公共字节预算约束 | 通过 | 测试输出 `ok 4` |
| S05 | Codex 后端从源码启动并使用包内模块 | 未覆盖 | 需要后端依赖、CLI 与启动验证 |
| S06 | Agents 后端从源码启动并使用包内模块 | 未覆盖 | 需要后端依赖与启动验证 |
| S07 | Codex 后端从发布包启动并解析内部模块 | 未覆盖 | 未授权构建/发布包生成 |
| S08 | Agents 后端从发布包启动并解析内部模块 | 未覆盖 | 未授权构建/发布包生成 |
| S09 | 包内模块缺失时 Worker 明确失败且不回退 | 未覆盖 | 需要受控缺失模块场景与 Worker 启动 |
| S10 | 两后端保留原 runtime 标签和 Worker 默认身份 | 未覆盖 | 未启动两个后端 |
| S11 | 注册、Pull、lease 与心跳的 Control Plane 请求形状不变 | 未覆盖 | 未连接/模拟 Control Plane |
| S12 | attempt 成功时结果与终态正常 | 未覆盖 | 未运行完整 attempt |
| S13 | attempt 失败时错误类别与终态正常 | 未覆盖 | 未运行完整 attempt |
| S14 | attempt 超时后停止并按契约上报 | 未覆盖 | 未运行计时/取消场景 |
| S15 | attempt 取消后停止并按契约上报 | 未覆盖 | 未运行完整取消场景 |
| S16 | 资源终态不确定时停止 claim 并保留工作区 | 未覆盖 | 未模拟不确定资源 |
| S17 | 成功与失败后的工作区清理遵循原契约 | 未覆盖 | 未建立工作区 |
| S18 | 证据终态与上报顺序遵循原契约 | 未覆盖 | 未运行完整证据流程 |
| S19 | 发布目录包含包内 `runtime-core/` 且无需包外复制 | 未覆盖 | 未授权 release 打包 |

旧路径引用清零、脚本/文档路径、源文件数量与依赖不变属于静态检查，不计入可观察行为场景；本报告未把 Worker 的静态检查当作 Tester 执行结果。

## 命令与风险

Planner 授权：`docs/features/reports/FD-010-test-authorization-r1.md`。工作目录：`D:\03_projects\AI-tools\aiw-dev\.wt\FD-010`。实际测试命令仅为：

```text
node tests/fd010/package-contract.mjs
ok 1 - package-local runtime entry imports and exposes shared attempt/slot APIs
ok 2 - removed top-level runtime entry cannot be imported
ok 3 - attempt without evidence refuses the operation
ok 4 - UTF-8 output remains within its public byte budget
1..4
exit code: 0
```

未运行 `node --test`、`npm test`、构建、发布、在线模型或 Control Plane 集成；没有执行覆盖率命令。残余风险是包内模块能导入不等于两个 Worker 启动路径或发布目录能正常解析，完整 attempt、租约、证据、清理和资源不确定分支仍未独立验证。建议 PM 仅在明确接受 21.05% 场景覆盖和未测量分支覆盖率的例外后决定是否进入 Reviewer。

