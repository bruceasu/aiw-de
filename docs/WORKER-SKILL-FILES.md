# 平台管理的 Worker Skill 文件集

运行时不以Codex/OpenAI Agents SDK的精确版本相等作为准入条件；记录实际安装版本，按所需接口和已有适配器合同处理兼容问题。编译不证明任意SDK版本均已验证兼容。

FD-030 将平台任务的 Skill 规范源集中到 `src/main/resources/worker-skills/`。显式 `index.json` 声明文件和各主 Skill 的协议依赖。内置文本目录 `execution/software-catalog.json` 的原文本摘要保持原义；文件集另有清单摘要。修改文件集会形成新的 `files-<manifestDigest>` 版本。任务创建时保存完整版本，已有任务、同 Task 重试、正式问答续行和 Workspace 替换不会追随新版本。

平台部署需一起更新 Control Plane 和三种 Worker。V17 只增加任务绑定表；本次不迁移旧运行数据，也不删除数据库。旧服务生成的无新能力标记任务仍是旧合同，不属于新平台资源保障；切换使用新建任务。旧 Worker、Codex CLI 及不支持新读取合同的后端不能领取要求 `skill-files-v1` 的任务，不会回退同名目录或旧执行器。

| Worker | 新平台文件集消费 | 说明 |
| --- | --- | --- |
| Python | OpenAI Agents / Codex App Server | 静态 Profile 和既有动态配置；动态配置仍要求原 guard、模型、预算和权限合同。 |
| TypeScript | OpenAI Agents / Codex App Server | 静态 Profile；不因此实现 Python 的 config-v1/guard-v1。 |
| Go | OpenAI API / Codex App Server | 静态 Profile；不因此实现 config-v1/guard-v1。 |
| TypeScript / Go Codex CLI | 不支持新平台文件集任务 | 保留原独立本地 Profile 资源入口；无动态资源读取工具，不宣告新能力。 |

Worker 自动声明已实现的能力，不能在 `AIW_WORKER_CAPABILITIES` 中手工添加 `skill-files-v1`。Python 的 `AIW_DYNAMIC_CONFIG=true` 仍须 `AIW_EXECUTION_GUARD=true`；动态配置无需预装本地 Skill 库。保留 `AIW_PROCESS_SKILLS_ROOT` 和原本地 Profile 加载能力，用于独立部署的旧合同。平台 Claim 有固定文件集时，本地目录完全不参与版本选择；指定目录不会自动导入平台。

首期使用受认证 Claim 响应的 `skillFiles` 字段：`protocol`、当前 `taskId/attemptId`、独立 `references` 和 `packages`。没有额外下载 URL 或模型网络权限，不要求预装完整资源库，也不需要制品服务。每次 Claim 传输完整声明字节，代价是响应体较大。首期不做磁盘缓存和自动回收；Attempt 内存保存已校验字节，辅助文件通过只读工具按需加载，不受本地同名文件或磁盘缓存改写影响。平台仍保留任务引用的完整版本；用户显式清理整个任务历史会同时清理其绑定。

每个包的字段为 `protocol/ref/version/source/ownerId/repositoryRef/assetVersion/assetDigest/manifestDigest/files`。`source=platform-builtin` 表示公共内置包，owner/repository 必须为空；`platform-asset` 必须与固定配置的 owner/repository 一致，且原 Skill 资产的 ref/version/文本 digest 必须匹配。TS/Go 静态 Profile 消费者仅接收公共内置包；私有配置资产仍由已有 Python 配置路径消费，不能借资源共享越权领取其他所有者配置。

`files` 按 ASCII 路径字节序排序，每项是 `path/size/sha256/base64`，保留原始字节和相对路径。清单 SHA256 的输入为逐文件拼接的 ASCII `path<TAB>size<TAB>sha256<LF>`。仅允许 `[A-Za-z0-9._/-]` 路径字符，拒绝绝对路径、反斜线、点段、空段、设备名、尾点、`.git`、大小写/文件目录冲突；必须有精确 `SKILL.md`，入口须为 UTF-8。每包最多128文件、每文件1MiB、每包4MiB、每任务16包/8MiB。原资产登记接口32KiB请求及配置512KiB上下文上限仍适用，这些更窄限制不会因文件集而解除。

自定义 Skill 可沿用资产登记接口，以文本形成仅含 `SKILL.md` 的包；显式完整文件集使用 Asset.content 的 JSON 文档 `{"protocol":"aiw.skill.files.v1","files":[...]}`，正文的原文本 digest 与内部文件/清单 digest 分别校验。JSON文档不能以文本方式静默回退。身份/配置版本仍按既有不可变登记合同；公开内置与私有资产来源明确区分。

Runtime 只注入各入口和声明文件索引。调用 `read_skill_file(ref,path,offset,limit)` 读取辅助文件；offset/limit 按字节计，limit 为1–8192。返回 `encoding=base64`、分页字节、完整文件 SHA256、size、offset、nextOffset 和 complete；二进制也不损失字节。工具只能读本次声明文件，没有目录枚举、写入、脚本执行或工具注册功能。取消/Lease丢失继续由原 Attempt/心跳处理；问答暂停关闭资源工具。App Server 不支持动态工具时明确报错，不能改用 CLI 或本地目录继续。

本文件描述实现合同。编译/静态审查不能证明真实 SDK、App Server、数据库、并发、取消或模型运行通过；实际证据见 FD-030 报告。

## 从宿主工程技能吸收方法

FD-039 新增可组合的公共 `prototype` 技能包（入口、逻辑与 UI 三文件），通过原有附加 Skill 目录装配给现有 Worker；不增加主角色或工具权限。新目录资产仅追加 `prototype-v1`，旧资产和方法保持，使用步骤见[原型制作技能](WORKER-PROTOTYPE-SKILL.md)。

FD-038 将 `.agents/skills` 的可复用方法适配到既有 Worker 角色，详见[来源与去向矩阵](WORKER-SKILL-METHODS.md)。现有拆分、审阅、诊断和测试方法上补充局部步骤、交接/修订覆盖、决策聚焦和按需 Skill 编写参考。入口与辅助文件保持各自声明，本文不授权宿主 FD 命令、代理、测试、安装或发布。

编码步骤和继续说明使用现有报告字段；没有新增平台持久 TODO、恢复状态机或输出协议。新增参考进入 `index.json`，本次修改同步到三语言同名独立部署副本。受影响文件集产生新 manifest 版本，既有已冻结任务仍使用旧内容。`execution/software-catalog.json` 的历史文本资产版本/摘要继续保持原义，不把它称为更新后文件集的完整正文。
