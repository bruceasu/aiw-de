# FD-005 独立测试报告 r33

<!-- aiw-data: FD-005-test-report-r33.json -->

## 结论

独立 Tester `fd005-tester-20261005-r33-7e49c2` 领取 `FD-005-000033-implementation-ready`；对应 FD 修订 33、SHA-256 `b221e8017434890b39232c406446065ef4e24c777f51697c135484d56895b005`。Planner 只授权一条离线命令。本轮实际执行一次，约 1.3 秒，**exit 1：2 项中 1 项通过、1 项失败**。未重跑或更改原始证据。

API-33 通过：planner 的 manifest 原文保留在 `complete` 第 4 实参 `report.summary`，`verdict=PASS`，合成模型请求仍使用既有模型路径。API-16 未通过：生产入口子进程对首个保留角色 `release-readiness` 在 **module-or-syntax-load-error** 类别退出，未触发网络守卫，但也未到达可证明的角色拒绝；断言在第一个角色处中止，第二角色未运行。错误只按允许名单保存类别，不保存原始 stderr、路径、密钥或环境。不能把退出码 1 解释为正确拒绝。

33 个适用 API Worker 场景中，历史完整通过 22 项，本轮新增 API-33，累计 **23/33（69.70%）**。API-16 失败，另外 9 项仍未完整覆盖。业务代码分支覆盖率 **未测量**，没有原始分支计数。建议 PM 退回。

## 场景与证据

| 场景 | 状态 | 证据与边界 |
|---|---|---|
| API-01 | 通过 | r19 requirements 角色会话 |
| API-02 | 通过 | r19 architect 角色会话 |
| API-03 | 通过 | r19 planner 角色会话 |
| API-04 | 通过 | r19 coder 角色会话 |
| API-05 | 通过 | r19 integrator 角色会话 |
| API-06 | 通过 | r19 reviewer 角色会话 |
| API-07 | 通过 | r19 verifier 角色会话 |
| API-08 | 通过 | r19 integration-verifier 角色会话 |
| API-09 | 通过 | r19 repairer 角色会话 |
| API-10 | 通过 | r19 reviewer 错配任务拒绝 |
| API-11 | 通过 | r19 缺失 verdict 拒绝 |
| API-12 | 通过 | r19 只读工作区变脏拒绝 |
| API-13 | 通过 | r19 未解决工具失败不被完成文本掩盖 |
| API-14 | 通过 | r19 完成响应未知不重发 |
| API-15 | 通过 | r19 Git 工作树删除失败不成功上报 |
| API-16 | **失败／未完整覆盖** | r33 生产入口在模块或语法加载阶段退出；r30 原失败保留。旧 adapter 拒绝仅为部分证据 |
| API-17 | 通过 | r23/r27 Reviewer 写工具拒绝 |
| API-18 | 通过 | r23/r27 Coder 指定工作区编辑 |
| API-19 | 未完整覆盖 | Profile/报告别名部分通过，注册能力和领取路由未执行 |
| API-20 | 未完整覆盖 | 独立读取部分通过，缺并发时序及上限证据 |
| API-21 | 未完整覆盖 | 同文件写编辑顺序部分通过，Shell 冲突排队未执行 |
| API-22 | 通过 | r30 排队取消与失败后后续操作 |
| API-23 | 未完整覆盖 | 模型取消部分通过，工具超时和全部资源终态未执行 |
| API-24 | 通过 | r27 租约丢失抑制完成上报 |
| API-25 | 未完整覆盖 | drain 与第二次信号强制终止未执行 |
| API-26 | 未完整覆盖 | push 期间租约丢失与远端对账未执行 |
| API-27 | 通过 | r27/r30 多字节、截断与搜索部分完成 |
| API-28 | 通过 | r27 不完整工具参数无写入 |
| API-29 | 通过 | r27 多区域编辑唯一性、重叠及 BOM/CRLF |
| API-30 | 未完整覆盖 | 历史预算与可关闭压缩未执行 |
| API-31 | 未完整覆盖 | 关键证据故障部分通过，全会话 G2 和平台认证未覆盖 |
| API-32 | 未完整覆盖 | 插件版本、错误、超时、权限矩阵未执行 |
| API-33 | **通过** | r27 旧 Profile/报告模型路径；r33 manifest 原文、PASS verdict、模型请求路径。r30 错位断言失败仍为历史事实 |

同名 JSON 的 `scenarios` 逐项列出 33 项，`passed` 正好 23 项；部分通过均未计入完整覆盖。历史 r30 的 2 项失败没有被改写，本轮仅确认 API-33 新证据。

## 命令、授权与剩余风险

- 唯一实际执行命令：`node tests/fd005/r33/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r33.json`，工作目录 `D:\03_projects\AI-tools\aiw-dev`；exit 1。
- 授权：[r33 Markdown](FD-005-test-authorization-r33.md) 与同名 JSON，绑定事件、修订、摘要、Tester session 和五个调用文件 SHA-256。
- 原始证据：[执行摘要](../../../tests/fd005/evidence-r33/execution.json)、[Node 测试输出](../../../tests/fd005/evidence-r33/node-test-output.txt)。生产入口原始 stderr 仅在测试进程内有界处理，证据保存允许名单类别，故无法进一步区分具体模块或语法错误。
- 合成 Responses 与 Control Plane，禁真实 socket/DNS/TLS，清除真实 API/Worker token；临时工作区位于 OS temp，执行后清理。本轮未运行其他 Worker、launcher、真实模型或控制平面、远端 Git、平台 G2、覆盖率工具、构建、编译和网络命令。
- 剩余风险：API-16 行为仍未证明；完整场景覆盖低于 70%，分支覆盖未测；9 项其余场景未完整覆盖。r30 和 r33 的失败与证据均应保留。
