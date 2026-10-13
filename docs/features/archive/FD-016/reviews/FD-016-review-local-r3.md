# FD-016 第三次整合独立审查：请求修改

<!-- aiw-data: FD-016-review-local-r3.json -->

Reviewer session：`fd016-reviewer-20261013-a9e28b61`；已领取来源 `FD-016-000017-review-requested`。整合基准前次审查提交 `5e094e9`，当前 HEAD `b7eda116fa12bc6f082d139b8525335e5de30ce7`；父分支新增 FD-017 与 build/start-all 后的整合结果单独审查，未继承旧通过为当前通过。

- **P2 R4：整合后的 README 宣称已被移除的迁移错误与过期清单**。位置 `workers/process-typescript/README.md:8`。新手段落仍说 codex-cli/codex-app-server 会报迁移未完成（第 8–10、181–182 行），但两个真实 adapter 已实现，现行边界是本地交付、原生 Windows 提前拒绝与运行验证未执行。第 84–85 行及后续分发说明还说 start-all 未进入清单，但清单已包含。造成同一文档内部与实际代码矛盾。 修复：保留父分支新增使用说明，按当前实现重写迁移、平台与发行说明；不宣称真实运行或生产认证通过。 对应 Acceptance 8, 12。

- **P2 R5：发行清单要求不存在的 bin 目录，打包必然在复制前失败**。位置 `workers/process-typescript/scripts/package-release.mjs:21`。releaseFiles 强制包含 bin，随后第 33–34 行逐项 access；实际 HEAD Git 树与定向 rg 文件列表均无 workers/process-typescript/bin，父分支新 README 也明确记录该阻塞。即使 dist 已由 build 生成，release 仍在不存在的 bin 上失败，不能分发新 start-all。此项为历史脚本缺陷在当前明确整合发行范围中的未解决行为，不是一次构建失败证据。 修复：按实际需要移除不存在且未使用的 bin 清单项及对应 package files 定义，或提供真实必需内容；静态核对其余非生成发布源文件存在，文档同步。无需为证明这一点运行最终构建。 对应 Acceptance 8、Work Items 1.20/1.24。

实际静态核对新增 build.mjs/start-all.mjs、package scripts、发行清单、README、索引与整合 diff。launcher 显式选择三模式并为九角色设置独立 ID，禁止冲突 runtime/共享 worker ID，没有恢复咨询角色或本地 Codex 包；build 只调用本地编译器，没有自动安装。索引与当前 FD-016 Pending Verification、FD-017 Design 来源一致，没有丢失父分支 FD。前次 R1/R2/R3 的实际代码修复仍存在，未被整合覆盖。但当前文档与发布行为尚未统一，因此本次 changes-requested。

父分支合并 blocker 当前待最终 local-merge 交付后更新；不要求其提前宣称已交付，也不将该待交付状态另设为本轮阻塞。保留原通过事件、两轮报告及真实新 Reviewer receipt，没有伪造 Worker 完成。

实际命令只有只读真实 receipt/status/HEAD、`git diff --stat 5e094e9...HEAD`、定向 `git diff`、`git show`、`git ls-tree`、`rg --files` 与源码读取，以及 `aiw fd claim FD-016 FD-016-000017-review-requested --session fd016-reviewer-20261013-a9e28b61`。报告保存后一次 `git diff --check` 检查文档空白；只提交审查报告与 FD/index 管理变化。最初读取误用 scripts/build.mjs、scripts/start-all.mjs 路径，静态更正到根 build.mjs/start-all.mjs，未运行这些脚本。

未执行测试、类型检查、真实 CLI/模型、launcher、build/release、部署或凭据操作，未评估可选 fd-test。本地静态范围与生产认证留 FD-015 的决定不变；SDK 类型、并发/取消与平台行为仍未经运行验证。原生 Windows TypeScript Codex 仍明确拒绝，未宣称可运行。

累计三次 Reviewer outcome：changes-requested、verification-passed、changes-requested；当前通过被新的整合审查结果取代，不能据旧 pass 合并或归档。返回真实 Worker handoff，由 PM 按 Auto 的累计审查和恢复规则决定后续，不自动制造额外通过。
