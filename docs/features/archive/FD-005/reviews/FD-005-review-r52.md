# FD-005 独立审查 r52

<!-- aiw-data: FD-005-review-r52.json -->

**结论：changes-requested。** Reviewer session：`fd005-reviewer-20261006-r52-84d21c7b`；来源事件：`FD-005-000052-review-requested`；审查对象为 FD 修订 52，SHA-256 `8612c48ab78ed15c1e0ce5afec5ed084200cfcf3df6268231d109951e35b6a62`。审查基线：`5b1a48baa9c3dd7ca575bab25c11d452f3cce912`（当前 HEAD）。

## 审查范围与结论依据

- FD 当前验收与 Verification 记录；Worker 实施报告 r42；PM 覆盖决定 r50；Tester 报告、授权及原始执行摘要/诊断 r39；Reviewer 历史报告 r44、r47。
- r50 明确豁免本次处置的 API-16 行为验证门槛，并接受 69.70%（23/33）需求场景覆盖率及未测分支风险。该决定被如实记录为 PM 流程覆盖，不表示 Acceptance 1 行为通过。
- r39 原始执行摘要记录唯一授权命令 exit 1；受控诊断记录 1 个行为测试失败，两个保留角色均在模块或语法加载阶段退出，未证明注册前拒绝。分支覆盖率未测量。Reviewer 逐项比对后确认授权列出的五个调用文件 SHA-256 均匹配。
- Worker r42 报告称没有生产代码改动；静态源码可见 `loadConfig()` 在 `client.register()` 前调用 `assertExecutableRole()`，但这不是 API-16 的运行证据。r47 的两项发现中，旧测试计划已在当前 Verification 中注明为历史记录，历史审查结果也在该节列出。

## Finding

### F-1 — Verification 后部仍保留过期的 Reviewer outcome 计数

FD 的 Verification 前文已记录 r44/r45 与 r47 的 `changes-requested`，并说明旧计数应被 supersede；但同一节后部仍写着“Reviewer outcome 仍为 0”（`docs/features/FD-005_WORKER_PI.md`，当前第 198 行）。这与本文件自身已记录的两轮审查结果冲突，读者无法确定“0”是历史快照还是当前计数。r47 曾要求校正该计数，此句仍未明确标成历史记录。

**要求：** 将该句明确标记为早期历史快照并附时间/版本，或移除当前计数；保留 r44/r45、r47 的原始审查结果。此项只需修正 FD 文档记录，不涉及生产代码、重跑测试或覆盖率检查。Reviewer 保持 FD 原文不变，将此处留给 PM/Worker 按当前流程处理。

## 决策与剩余风险

除 F-1 文档矛盾外，本轮 PM 豁免范围、r39 失败事实、场景覆盖与分支状态均被如实记录。没有将 API-16 或其他未运行行为记为通过。风险仍包括 Acceptance 1/API-16 未获运行证明、10/33 场景未完整覆盖、业务分支未测量，以及真实服务、远端 Git、平台 G2 与非 API Worker 本轮未验证。r42 Tester handoff 没有可见结果，本审查不推断它执行或通过。

## 命令与未运行检查

实际执行了 `aiw fd --help`、`aiw fd resume FD-005`、`aiw fd claim --help`、领取正式事件的 `aiw fd claim FD-005 FD-005-000052-review-requested --session fd005-reviewer-20261006-r52-84d21c7b`、`aiw fd emit --help`、`rg --files`/`rg -n` 的定向查找、针对报告/事件/原始 JSON 与源码的 `Get-Content -Encoding utf8`/`Select-String`、r39 五个授权调用文件的 `Get-FileHash -Algorithm SHA256` 摘要核验，以及 `git rev-parse HEAD`、`git branch --show-current`、FD 相关源码/测试路径的 `git diff --name-status HEAD -- ...`。`aiw fd show FD-005` 因当前控制台 GBK 编码无法输出 Unicode 字符而报编码错误；FD 与正式回执随后通过 UTF-8 读取核对。

未运行测试、覆盖率、编译、构建、格式化、网络或其他运行时检查；未修改生产代码或被审 FD。审查报告通过真实 CLI 以 `changes-requested` 结果交回。
