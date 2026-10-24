# FD-044 编译路径问题反馈（已解决）

<!-- aiw-data: FD-044-blocker-compile-20261024-r1.json -->

- 阶段/角色：Worker；来源 `FD-044-000003-design-ready`，Session `worker-context-20261024-61db8e4a`。
- 状态：**resolved**。没有待用户决策、未批准安装或未解决编译错误。

## 观察与原因

首选 `node scripts/compile-worker-runtime.mjs` 在读取 TypeScript 时报 MODULE_NOT_FOUND。确认本地没有该依赖，脚本在任何 Worker 编译前中断。Java 离线编译独立成功。

随后一组包含原生语法编译、临时目录创建、Go 编译及清理的 PowerShell 命令被自动批准审查拒绝，未执行其中任何命令。工具只返回 `blocked by policy`，没有具体原因，不能将“临时清理”臆断为唯一原因；没有换壳或提权执行原清理动作。

另一次静态命令在 diff --check 发现 README 末尾多余空行后停止，没有把后续未执行断言记成通过。已修正文档，仅复查该文件，并首次完成余下静态断言。

## 实际解决

使用同一 PowerShell 环境中更窄、无临时文件清理的编译方式：Node 24.14.0 原生 `--experimental-strip-types --check` 检查改变的 TS 文件，`node --check` 检查三个界面 JS；标准库 `compile()` 检查六个改变的生产 Python 文件，不导入 SDK/产品模块；Go 以 `GOPROXY=off GOSUMDB=off GOTOOLCHAIN=local` 执行 `go build -buildvcs=false -o NUL .`，输出到 Windows 空设备，不保留/运行可执行文件。全部通过，环境变量恢复。

后续共享模型偏好修正只改变 Python runtime，再对该文件做一次语法编译，通过；没有重跑未改变的 Java/Go/TS 检查或原失败脚本。无依赖安装/下载、提权、测试、服务或模型执行。

## 可复用改进

在缺本地 TypeScript 编译依赖时，先区分“缺编译器环境”与源码错误。对于可擦除 TS 语法，可在明确标注不做类型检查的前提下使用现有 Node 原生语法编译；Windows Go 编译可使用 NUL，避免临时分发产物与删除步骤。

fd-workflow 引用的两个 blocker 模板在本仓库缺失，已在 .agents/docs 模板范围查找，并采用 FD-014 既有 `aiw.fd.blocker.v1` 反馈结构；没有声称读过缺失模板。FD-014 已记录相同模板问题，建议后续维护恢复引用。本 FD 不扩大到宿主技能/模板修复。

下一步：独立实现审阅，按真实回执交付归档；具体源码/命令版本见 Worker r1。静态/编译不证明真实迁移、并发或模型效果。
