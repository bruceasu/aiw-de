# FD-063 独立静态审查

- Reviewer：`codex-fd063-review-20261028`。
- 来源事件：`FD-063-000002-review-requested`，已 claim。
- 比较基线：`2b97f53ee089ea797e662f43f55de298be7e89b0` 上本 FD 的 Git 交付过滤器增量，排除同时保留的其他 FD 修改。
- 最终结论：**通过**。本轮三项过滤器边界问题已修复关闭；已核对修复后二十一项执行者测试及真实属性只读回执。当前业务计划在本次加载前已交付，不能用该状态证明本次自动合并路径在生产执行过。

## 发现

1. **P1：未检查的非本次变更文件仍可通过全树 diff 执行 clean driver。** filterBlock 仅检查 HEAD/result changed 路径，之后预检重叠检查和合并前后 binary digest 使用全工作树 git diff。`--no-ext-diff/--no-textconv` 不禁用 clean filter；未出现在本次提交差异中的脏文件若带 filter，可在这些 diff 中执行外部程序。原全局保护移除后应明确禁止只读 diff 的过滤器执行，或保守拒绝进入这些读取的有效过滤器，不能只靠 changed 列表。
2. **P1：保留属性返回值与同名过滤器字符串有歧义。** check-attr 返回 `unset/unspecified` 时既可能是 -filter/未设置，也可能是 filter=unset/filter=unspecified；如果存在同名 clean/smudge/process 驱动，当前代码会放行并在合并时执行。需结合配置对歧义值保守拒绝，保留失效关闭原则。
3. **P2：非 UTF-8 文件名可能使属性检查查询错误路径。** Git 允许原始非 UTF-8 文件名，而 helper 以默认替换策略解码，再将 Java 字符串重编码到 stdin；替换后的路径可能得到 unspecified，原文件实际属性未被检查。路径/属性输出应严格解码或拒绝无法可靠往返的路径，不静默替换放行。

## 已核对

- 本轮修复：全树工作区 diff、merge-tree 和实际 merge 都先以 NUL 列举配置键、严格验证键名，再仅对该子进程用 `-c` 清空 clean/smudge/process、将 required 设 false，不修改仓库配置/索引。HEAD/result 纯提交 diff 不进行工作区转换；cached diff 只读 index。
- reserved 值若有同名过滤器配置（包括 required）保守拒绝；Git stdout 改严格 UTF-8 解码，无法可靠查询路径时不静默替换放行。原三项发现关闭。

- NUL stdin 避免命令行长度、pathspec 特殊字符和空格/换行问题；响应按路径顺序、filter 键、三元组数量及末尾 NUL 严格核对，非零退出/异常明确拒绝。
- 当前工作树、HEAD、结果提交三处均查询属性；配置存在但属性不应用的常规文件不再被全局 LFS 安装误拦。
- 自定义 merge driver 全局保护、原基线/脏修改/未跟踪文件/合并目标复查、评审完成与外部写入 UNKNOWN 保护继续存在。
- stdin/stdout 使用独立虚拟线程、进程等待和读写 join 有界；超时/线程未完成会拒绝并终止子进程。没有新增 shell 字符串拼接。
- 国际化仅翻译固定前缀，保留后面的文件名；使用既有 originals/rendered 文本管理，路径作为文本呈现。

## 证据与限制

- Reviewer 实际仅静态 git 差异、源码/测试/FD 阅读及 `aiw fd show/claim`；未运行 Git fixture、测试、构建、网络、模型或服务。
- 已读初版新增七项及附近既有测试：未使用全局/其他文件过滤器、当前/目标/结果属性、Unicode/显式禁用、读取失败关闭；执行者报告初版十八项通过。本轮再增 reserved 同名配置、脏旁路文件 marker 从 preview 到 merge 终点不执行/内容保持，以及真实 production gitWithInput 的 NUL stdin 三项。修复后二十一项全部通过，compile-only 319 源成功；未运行非法字节文件名 fixture，严格 decoder 的 REPORT 路径以源码支持。
- 不把仅 HTML 属性核对等同于当前主题业务计划已通过评审；本 FD 不批准或合并仍待业务验收的结果。

## 实际加载与状态证据核对

- 已读取 `%TEMP%/aiw-goal-execution/fd063-runtime-receipt.json`：认证翻译资源 HTTP 200；sudoku2.htm 在 worktree、HEAD、b6d24a7 结果提交均为 filter=unspecified。FD Verification 记录备份 `D:/temp/data/aidev-r3.before-FD063-20261028-080129.db`、integrity ok、无活动任务时重启加载。
- 同一回执列出主题计划最终 COMPLETED/DELIVERED、work-4 与 review-4 PASS，结果为 `6f7412709cb42e3d903f26c3a84e24cb64ca78df`。执行者核对这次交付在重启前备份中已存在、actor web:admin，本轮没有再批准或合并。因此本 FD 的合并行为证明来自真实临时 Git 回归，生产只做属性/加载检查与 alreadyMerged 预检。
- 业务审查报告仍未提供浏览器实测，本 Reviewer 不把数据库 PASS/交付状态转述为浏览器视觉验收完成。未额外模型调用、业务补正、API、测试或进程操作。
