# FD-010 独立审查报告，第 1 轮

<!-- aiw-data: FD-010-review-r1.json -->

Reviewer Session `fd010-reviewer-20261007-a63de4`；来源事件 `FD-010-000007-test-accepted`；审查基线为 `master` 的 `de04beced35168c34a57b536c70c3b82acea1425`，审查提交为 `feature/FD-010` 的 `844d1d4dce9043fdd73a43cbe7c066a6b42483d9`。**结论：Verification 通过。**

## 发现与判定

未发现阻断 FD 验收的实现缺陷。20 个原顶层模块文件作为迁移进入 `workers/process-typescript/runtime-core/`；运行时 `.mjs` 与 `index.d.mts` 为 100% 重命名，契约文档只改相对链接。`src/runtime-core.ts` 改为静态导出 `../runtime-core/index.mjs`，其源码位置与编译后的 `dist/runtime-core.js` 都解析到包内同一目录；其他 TypeScript 类型导入也指向该入口。旧顶层目录不在实际差异的目标树中，两个 AgentRuntime 后端仍经统一 Worker 入口和此门面使用公共 attempt、slot 与 Control Plane 实现。

`package.json` 的 npm 文件清单包含 `runtime-core/`，构建脚本不再运行已删除的 `stage-runtime.mjs`，发布脚本从包内目录复制到与 `dist/` 同级的位置。根目录编译入口扫描新目录；19 处现行 FD-005/FD-006 测试导入只改相对路径。限定文本检索在当前源码、脚本、测试和文档中仅找到 FD 设计说明及 FD-010 新测试对旧路径的反向断言；历史归档保持原文。移动后的公共契约、协议文档、README 和运维链接指向现有文件。

**非阻断证据瑕疵：**当前 `git diff --check master...HEAD` 报告 `FD-010-test-report-r1.md` 和同名 JSON 的文件末尾多余空行。Worker 此前的空白检查在 Tester 报告加入前运行；不能把它说成当前完整差异通过。该两处格式问题不影响源码、发布路径或测试结论，可在后续维护中清理。

## 独立测试与 PM 决策核对

Tester 的唯一执行命令 `node tests/fd010/package-contract.mjs`、工作目录、Session `fd010-tester-20261007-11617f`、实现事件 `FD-010-000005-implementation-ready`、FD 修订 5 和摘要 `a811fce4969b6c1306b3e23e5ebb3a2ba081b7941b8d467d8870893aa93f2fdd`，均与 Planner 授权一致。测试脚本中有四项断言；Tester 报告记录 `ok 1` 至 `ok 4`、`1..4`、退出码 0，未把其余 15 项列为已运行。没有独立的外部输出日志，本次以 Tester 的原始输出摘录和脚本为证据，未重跑命令。

需求场景覆盖率为 4/19（21.05%），业务分支覆盖率未测量；PM r1 对两者作了明确例外，决定接受测试证据后交 Reviewer。该例外不豁免发布包、源码 Worker、两个后端和完整 attempt 生命周期的行为要求。静态差异支持本次路径/所有权迁移，未执行的行为仍保留为风险，不记为测试通过。

## 实际命令、未运行检查与风险

实际执行 `aiw fd show/resume/claim`、`git status --short`、`git diff --stat/--name-status master...HEAD`、限定文件的 `git diff master...HEAD`、`git rev-parse HEAD master`、`git diff --check master...HEAD`、`rg` 和 `Get-Content`。一次较宽的 `git diff` 因宿主内存不足失败，后续缩小路径完成核对；一次检索误包含巨大历史 coverage JSON，已改为限制文件类型和目录的检索。未运行测试、构建、类型检查、发布打包、网络请求或真实后端/Control Plane 集成。

残余风险：源码与发布包的实际模块加载、Codex/Agents 注册、lease、成功/失败/超时/取消、资源终态、证据和工作区清理缺少独立运行时证据；外部硬编码旧仓库路径的使用者仍需迁移。PM 已记录低覆盖例外，发布前若需要运行时保证，应单独授权聚焦启动与发布包检查。
