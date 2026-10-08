# FD-010 Worker 实现报告，r1

<!-- aiw-data: FD-010-implementation-r1.json -->

来源事件 `FD-010-000003-design-ready`；Worker Session `fd010-worker-20261007-72be51`。四项 Work Item 已完成并在 `feature/FD-010` 中分项提交。

## 改动与原因

- 将 `workers/runtime-core` 的 20 个源码、类型及契约文件移至 `workers/process-typescript/runtime-core`，修正模块契约内的相对链接。TypeScript 门面改为直接从包内静态导出，相关类型导入同样指向包内，删除源码/打包双路径回退。
- `build` 不再运行 `stage-runtime.mjs`，该脚本已删除；发布清单从包内复制 `runtime-core/`，根目录 compile-only 脚本扫描新目录。发布目录中的相对布局保持 `dist/` 与同级 `runtime-core/`。
- 19 个现行 `tests/fd005`、`tests/fd006` 导入改指包内模块；未改断言或历史运行证据。当前 Worker 契约、运维指南及 README 改指新路径，旧顶层源码目录删除。

## 静态与执行证据

- 核对了直接引用：两个现有后端继续经 `src/runtime-core.ts` 使用同一公共 attempt/slot；当前源码、脚本和现行测试不再从旧顶层目录导入。Git 对目录移动识别为重命名，未改运行时 `.mjs` 逻辑。
- 执行一次项目内无产物语法编译：`$env:AIW_TYPESCRIPT_COMPILER='D:\03_projects\AI-tools\aiw-dev\workers\process-typescript\node_modules\typescript'; node scripts/compile.mjs`，工作目录 `workers/process-typescript`，退出码 0。它不进行类型、依赖或运行时验证。
- 执行 `git diff --check master...HEAD`，退出码 0。移动后首次 `git add -A -- workers/runtime-core ...` 因已删除的源目录 pathspec 被拒绝；随后只用现存路径正确暂存并提交，没有重跑编译或扩大验证。

## 未执行与剩余风险

未运行测试、类型检查、最终构建、release 打包、真实 Codex CLI/OpenAI API 或 Control Plane 集成。源码/发布包的模块解析、缺失模块失败路径、原有 attempt 语义和外部硬编码旧仓库路径仍缺独立运行证据；独立 Tester 与 Reviewer 应据实核对。没有推送、部署或发布。
