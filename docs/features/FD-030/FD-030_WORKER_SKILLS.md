# FD-030: Worker Skills 由平台统一管理，按任务引用固定版本

**Status:** Complete
**Revision:** 7
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

- [x] 1.1 文件集合同与平台规范源：小/中，<=半日；无依赖；已定义文件/清单校验和独立文件集摘要；9个内置Skill共62文件迁为平台规范源。编译在全量源码完成后统一记录。
- [x] 1.2 平台固定引用与Claim：小/中，<=半日；依赖1.1；已接入创建、配置绑定、续行、Workspace替换、能力准入及Claim文件集。V17新增绑定表，平台资源版本以files-加manifest摘要命名；配置原资产版本/文本digest另存溯源字段。
- [x] 1.3 Python消费与资源reader：小/中，<=半日；依赖1.2；已接入文件集校验、配置来源匹配、入口/索引提示、SDK/AppServer及受控broker只读内存读取。动态配置不再要求启动时预装本地Skill库。
- [x] 1.4 TypeScript消费与资源reader：小/中，<=半日；依赖1.2；已接入静态Profile文件集校验、SDK及AppServer内存reader、入口/清单提示与来源证据，CLI不宣告能力。不扩展config/guard协议。
- [x] 1.4.1 SDK兼容门禁修复：小/低，<=半日；依赖1.4；按R030-01及用户既定“不限定Codex/OpenAI Agents SDK版本”决策，移除TS运行时精确版本相等检查，保留实际版本元数据。依赖清单和既有适配器接口仍是安装/运行输入，不把语法编译称兼容性认证。
- [x] 1.5 Go消费与资源reader：小/中，<=半日；依赖1.2；已接入严格JSON/文件集校验、SDK/AppServer内存reader、只注入入口与清单，并记录固定摘要；CLI无新能力，不宣称完整config/guard能力。
- [x] 1.6 部署/稳定规范与交付证据：小/低，<=半日；依赖1.3–1.5；已写共同切换说明、稳定规范和编译证据；独立实现审查作为后续生命周期Gate，不将其预报通过。

## Acceptance

ISSUE-00014验收逐项映射：同版本跨Worker/三语言一致、更新保留旧引用（1.1/1.2/1.3–1.5）；无预装取得内容及损坏拒绝、部分文件拒绝（1.1/1.3–1.5）；范围隔离及不增加知识/工具权限（1.2/1.3–1.5）；不兼容Worker不可领取（1.2）；自备目录保留且不覆盖平台版本（1.3–1.5/1.6）；相对路径与原始字节保留、辅助文件按需读取（1.1/1.3–1.5）。所有失败发生于模型启动前或明确资源读取失败，不回退最新/同名内容。独立部署不要求存在本仓库目录。

## Verification

- 独立修复复核r2静态通过：`../reviews/FD-030-implementation-review-20261023-a91e47-r2.md`及同名JSON，Reviewer `fd030-review-20261023-a91e47-r2`，来源`FD-030-000006-implementation-ready`；审查HEAD `e51d662`、源码`e5ba004`，R030-01已解决，沿用r1未改完整范围静态覆盖。累计失败1/3保留；未运行测试/SDK/模型/服务。真实verification-passed由Reviewer发出，PM继续本地交付与归档。
- 独立实现审查changes-requested：`../reviews/FD-030-implementation-review-20261023-a91e47-r1.md`及同名JSON；Reviewer `fd030-review-20261023-a91e47-r1`，来源`FD-030-000004-implementation-ready`，审查HEAD `91322bd`、源码`2c0ae9c`、比较基线`b84cd77`。R030-01要求移除TS SDK后端精确版本运行门禁并保留实际元数据，满足用户不限定SDK版本的持续指示。其余静态路径未发现阻断缺陷；未重复编译或运行测试/服务/模型，真实运行仍未验证；实现审查失败1/3。真实回执`FD-030-000005-changes-requested`交回Worker。
- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- 当前授权不含测试、服务、DB/Flyway、HTTP、模型、网络、安装、打包、push或部署。Java离线compile；Python内存语法编译；TS采用已有compile脚本；Go仅编译包并将中间产物置临时目录，不保留发行包、不下载依赖。具体命令与结果由Worker报告记录。
- 涉及协议、持久化及执行边界，design-ready前须一次独立静态设计审查；实现后独立fd-review，最多3次失败。未运行行为保持未验证。
- 独立设计r1发现原生磁盘快照/继任绑定/来源范围问题；已改内存reader并收敛冻结来源，r2实际ready，见`../reviews/FD-030-design-review-20261023-a91e47-r2.md`（保留r1）。
- 2026-10-23 23:13:47 JST离线Java compile成功，288生产源。Worker编译脚本首轮因Go VCS stamp exit128失败；关闭编译专用VCS stamping后一次重试通过，覆盖TS/JS/Python语法和Go编译，无发行物保留、无依赖下载。
- 详细合同及兼容限制见`../../WORKER-SKILL-FILES.md`。文件集version=`files-<manifestDigest>`，`assetVersion/assetDigest`保留原Asset版本/文本摘要；引用与包另逐项匹配。首期私有配置资产仍由Python config路径消费，TS/Go静态Profile仅接收公共内置包，不伪装其他协议。

## TODO

- 已完成设计及实现工作项，等待独立实现Reviewer；通过后按原授权本地交付及归档，不含发布/部署。

## Sources

- Issue: ISSUE-00014

## 修复验证

R030-01来源`FD-030-000005-changes-requested`（累计失败1/3）：`e5ba004`移除TS精确SDK版本运行门禁，保留版本元数据及现有接口调用的失败路径。已执行一次`node workers/process-typescript/scripts/compile.mjs`，使用本机既有TypeScript编译器，语法编译成功；未执行类型检查、依赖安装或SDK/模型。Java/Python/Go源码未变化，沿用原实际编译证据。修复报告`../reports/FD-030-implementation-20261023-a91e47-r2.md`，等待独立复核。
