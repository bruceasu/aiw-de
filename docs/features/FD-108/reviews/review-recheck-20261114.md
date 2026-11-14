# FD-108 首轮 P1 聚焦复核

- 结论：Changes requested，2 项 P1；首轮报告及失败历史保留。
- 来源：`FD-108-000004-implementation-ready`，已精确认领；独立 Reviewer：`codex-review-fd108-20261114`。
- 工作树：`.wt/FD-108`；限定差异 `01ca86c → 4a4db9d`，只复核首轮问题和直接影响。

## P1：按业务 name 猜 Profile 文件名导致既有 guarded 角色启动失败

guarded_preflight.capabilities 新增读取 `PROFILE_DIR/(profile.name+'.toml')`，但真实文件由 process_profiles.ROLE_FILES 映射选择，文件名与 name 不保证一致。coder 真实文件是 coding-executor.toml，name 为 coding-work-package-executor；requirements 文件为 requirement-analyst.toml，name 为 software-requirements-analyst。新登记逻辑对所有 guarded Profile 执行，因此这些既有角色会因不存在文件抛 FileNotFoundError，无法启动。general/workspace 恰好同名。

最低修复：从实际加载来源或既有 ROLE_FILES/PROFILES 映射取得真实文件，保留真实配置身份和统一换行摘要核对，不按业务名称猜路径。

## P1：guarded Codex 缺少位置型问答的 handoff 存储能力

formal_questions.ask 对所有 runtime 的无 Git 目录都生成位置型 checkpoint，声明 native_run.storage_capability 返回的 handoff-store:*。FormalQuestions.open 和 QuestionClaimPolicy 要求 Worker 登记该能力，但 guarded_preflight 仅在 openai-agents 分支加入它，codex-app-server 没有。

因此无资料的 Codex 工作区任务可匹配 Profile 后，普通目录提问仍被服务端以 Checkpoint store is not registered by this Worker 拒绝。最低修复：按实际位置型问答/共享存储实现登记 handoff 能力，和 FD-107 资料消费能力分别判断；继续禁止备用 runtime 虚报资料消费。

## 已支持的修复与证据限制

general/workspace 的冻结 worker-profile 摘要与业务 configurationDigest 已分开，旧 fixed-profile 路径保留；但上述其他角色定位回归仍需修复。guarded SDK 已登记 task-materials-v1，首轮 SDK 资料能力缺失关闭。

普通目录 SDK 问答先保存位置型 handoff/树摘要；服务端核对 location、当前 Attempt 登记的 manifest/sha，checkpoint.revision 统一取 handoff.sha256。继承和领取核对存储能力，Worker 续接在目录锁内验证 manifest/现场树，Git 校验保留。新增位置型提问及现场变化用例源码已阅读，未执行；Codex 登记缺口如上。

实际执行 aiw fd show/claim、定向 git diff/stat、rg 和 Get-Content，随后写报告、更新 Verification 并发送 Reviewer 事件。报告落盘首次因 FD 上下文变化未成功，按当前 Verification 标题修正；未运行产品或验证脚本。

未运行测试、编译、迁移、服务、SDK 诊断、真实 API/模型、网络或安装，未派子 Agent。**最终编译仍按用户要求延期，未补齐，不能记为通过，也不能据此归档交付。** 实际领取、问答回执/续接与文件现场校验仍无本轮运行证据。
