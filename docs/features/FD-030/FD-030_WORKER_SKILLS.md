# FD-030: Worker Skills 由平台统一管理，按任务引用固定版本

**Status:** Open
**Revision:** 3
**Priority:** Medium
**Evidence policy:** Dual

## Problem

平台任务的 Skill 在三语言包中重复维护；已有配置冻结的是拼接文本，不能还原 references/assets/scripts 文件集。管理员需要唯一平台来源，任务与续行保留固定版本，三语言按同一合同消费，独立部署仍可显式指定自备目录。批准来源为 ISSUE-00014（本次用户明确 approve/promote/auto）。

## Options and decision

比较可变共享目录、独立下载服务及随受认证 Claim 传递不可变文件集。选择首期 Claim 携带完整声明文件集：复用现有认证/租约响应，避免新增网络下载与压缩解包合同；代价为每次领取传输完整包。文件集在任务创建/配置准备时持久冻结，续行显式继承。Runtime 只注入 SKILL.md 和清单，辅助文件按需读取。缓存是派生内容，不参与版本选择。

独立 skill-files-v1 能力与 aiw.skill.files.v1 协议，不冒充 config-v1/guard-v1。现有文本 Asset digest 保持文本语义；文件集另有 manifestDigest。内置平台目录迁入 src/main/resources/worker-skills，旧包内副本仅保留为独立部署兼容内容，不再是平台任务的来源。

## Solution

文件集 JSON 包含 ref/version/source/ownerId/repositoryRef、manifestDigest 及排序 files(path,size,sha256,base64)。manifest 固定为 ASCII 行 `path\t<size>\t<sha256>\n`（路径仅 ASCII 安全字符，无制表/换行），按路径字节序排序后 SHA256。最多每包128文件、每文件1MiB、每包4MiB、每任务16包/8MiB。必须有 SKILL.md；拒绝绝对路径、反斜线、空段、点段、盘符、Windows设备名、尾点/空格、大小写冲突和不支持类型。注册只收字节数据，不跟随链接，不执行脚本。

平台内置资源启动读取显式索引并严格验证，不扫描用户目录。任务创建时为已知平台 Profile 固定其协议/主Skill；配置任务在 bindTask 时用配置原资产引用解析并覆盖创建时默认绑定，仍在同一事务。自定义文本 Skill 可形成仅 SKILL.md 的包；文件集 Asset.content 使用显式文件集协议，保持原 content 文本摘要且校验内部文件摘要。任务引用持久化，不按 latest 解析；配置续行及非配置重试沿用绑定。未识别的外部Skill明确准备失败。领取策略要求实际 skill-files-v1；Claim只返回本任务文件集，无跨任务资源查询接口。

三语言消费者验证整个包、引用与路径；不以同名自备目录覆盖。包随 Claim 取得，无额外模型网络工具。三语言SDK及AppServer均使用显式只读read_skill_file(ref,path,offset,limit)，offset/limit为字节范围，最多8192字节，返回base64、完整文件摘要及分页信息。辅助文件只存在于本次Attempt校验后内存，避免同用户shell改写只读磁盘文件的漏洞。scripts仅分发数据，不执行。Python受控模型线程继续关闭原生工具；TS/Go不放宽现有工具边界。codex-cli没有动态资源reader，不宣告新能力、不能领取平台受管Skill任务；仍保留其独立本地目录模式。AppServer拒绝动态工具合同则本次执行明确失败，不回退CLI/本地文件。

首期不做磁盘缓存及自动GC：Claim提供全部授权字节，Attempt结束即释放内存；平台无删除接口，引用版本持久保留。只保存Skill，不存知识或任务正文。source采用platform-builtin（公共内置包，owner/repo为空）或platform-asset（owner/repo必须等于已冻结配置candidate）。包的身份/版本/manifestDigest需与Claim独立references逐项匹配；配置包同时匹配原Asset文本digest。后续版本更新新增引用，不替换既有绑定。continueBinding/任务替换必须先恢复原任务固定包，不能采用创建时的当前默认包；普通重试复用同task绑定。

默认绑定只允许首次准备且未Claim时在同事务被匹配配置替换；首个Claim及之后绑定不可修改。继任/替换任务在首次Claim前显式复制原绑定，已有不同绑定拒绝覆盖。

兼容：不删除用户DB、不迁移旧部署；V17新增任务资源绑定。平台部署必须同时更新三语言Worker；不支持能力者不能领取平台Skill任务。保留AIW_PROCESS_SKILLS_ROOT用于旧的独立Profile入口；平台响应有固定引用就忽略本地同名版本。版本/文件集校验失败在模型启动前终止。登记仍走已有资产管理鉴权，Skill不增加工具权限。

## Scope

覆盖平台内置Skill规范源、不可变任务引用、三语言消费、受控读取及部署说明。排除Skill市场、离线调度、完整TS/Go config/guard协议移植、脚本执行、任意URL下载、自动缓存回收、安装发布及旧数据迁移。

## Work items

- [ ] 1.1 文件集合同与平台规范源：小/中，<=半日；无依赖；完成为清单验证、内置索引和文本摘要分离。
- [ ] 1.2 平台固定引用与Claim：小/中，<=半日；依赖1.1；完成为持久绑定、配置/续行继承、能力准入及精确Claim载荷。
- [ ] 1.3 Python消费与资源reader：小/中，<=半日；依赖1.2；完成为完整校验、入口/索引提示及两现有后端只读读取。
- [ ] 1.4 TypeScript消费与资源reader：小/中，<=半日；依赖1.2；完成为独立能力、包校验、SDK读取与AppServer内存动态reader（CLI不宣告能力）。
- [ ] 1.5 Go消费与资源reader：小/中，<=半日；依赖1.2；完成同1.4，不宣称完整config/guard能力。
- [ ] 1.6 部署/稳定规范与交付证据：小/低，<=半日；依赖1.3–1.5；完成为共同切换说明、局部编译及独立实现审查。

## Acceptance

ISSUE-00014验收逐项映射：同版本跨Worker/三语言一致、更新保留旧引用（1.1/1.2/1.3–1.5）；无预装取得内容及损坏拒绝、部分文件拒绝（1.1/1.3–1.5）；范围隔离及不增加知识/工具权限（1.2/1.3–1.5）；不兼容Worker不可领取（1.2）；自备目录保留且不覆盖平台版本（1.3–1.5/1.6）；相对路径与原始字节保留、辅助文件按需读取（1.1/1.3–1.5）。所有失败发生于模型启动前或明确资源读取失败，不回退最新/同名内容。独立部署不要求存在本仓库目录。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- 当前授权不含测试、服务、DB/Flyway、HTTP、模型、网络、安装、打包、push或部署。Java离线compile；Python内存语法编译；TS采用已有compile脚本；Go仅编译包并将中间产物置临时目录，不保留发行包、不下载依赖。具体命令与结果由Worker报告记录。
- 涉及协议、持久化及执行边界，design-ready前须一次独立静态设计审查；实现后独立fd-review，最多3次失败。未运行行为保持未验证。

## TODO

- 完成独立设计审查并收敛发现，再发design-ready。
- 按工作项逐项实现与提交；编译/静态证据；独立审查后本地交付及归档。

## Sources

- Issue: ISSUE-00014
