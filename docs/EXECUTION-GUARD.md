# 受控执行部署与人工核对

FD-029 的接口版本为 `aiw.execution.guard.v1`。这是源码实现及部署约束说明；离线编译不证明真实沙箱、并发或恢复已通过。默认工作流没有执行测试、模型、数据库迁移或真实项目命令。

## 当前计量模式（FD-032）

默认 `AIW_EXECUTION_ACCOUNTING_MODE=record-only`（对应 `aidev.execution.accounting-mode`）：动作累计次数、任务数、Attempt 时间/用量/费用、预留、回跳和未知项继续记录，但预算缺省、额度用完、超额、未知计量不阻止准备、领取或推进。审计和人工核对入口保留，历史记录无需先核对才能启动无关新工作；不会自动产生阻塞执行的预算追加问题。授权页显示实际模式，预算追加表单在仅记录时不显示。

政策可省略 `budget`，或只填写已知的非负参考数据；单次预留可以为零。缺价格时已启动 Attempt 的费用记录为未知/null，保留实际 token 和时间；确认从未启动的 Attempt 记录已知零执行费用。按冻结原政策的价格结算，不用后来价格重算历史。

需要恢复预算限制时，部署方显式设置 `AIW_EXECUTION_ACCOUNTING_MODE=enforce` 并重启平台。此模式要求完整限额、定价与正的单次预留，恢复下面所述的余额、未知计量、原/当前价格兼容及回跳预算检查，且不清空历史计数或改写费用。其它值拒绝启动。切回严格模式前应核对现有缺省配置与未知记录；它们可能重新使新工作等待。仅记录模式本身不设累计成本上限。

两种模式都保留登录、Owner/Worker、精确工具/动作范围、固定输入、人工暂停/撤销、命令沙箱和 Fleet/Worker 并发容量。原任务未知外部操作不自动重放，也不自动宣告停止；真实 Task/Attempt/Lease 在途仍占用资源并防止并发替代执行。已有表和记录不迁移、不删除。本次源码修改不会自动重启当前运行服务。

## 启用顺序

1. 用户自行准备新运行库。V11 要求运行任务及 Attempt 表为空；遇到已有运行数据会停止，不删除或迁移旧数据。V11 扩展 SQLite/PostgreSQL 状态约束，V12 添加固定依据、意图、账本和交付记录。
2. 配置人类控制台认证及 `AIW_WORKER_TOKEN`。没有共享机器凭证不能确认执行政策；浏览器 Cookie 不能代替任务的 Worker/Attempt/Lease。

   Windows 本机可运行 `./scripts/configure-local-worker-token.ps1`，在仓库根目录的私有 `.env` 中生成缺省共享令牌；已有非空值保持不变，脚本不输出凭证，并拒绝写入已受 Git 跟踪的 `.env`。平台 `scripts/startup.bat` / `scripts/startup.sh` 会读取此文件。专业 Worker 启动时也必须加载同一份环境中的 `AIW_WORKER_TOKEN`，不要为每个进程单独生成新值。配置后需重启平台。秘书的个人身份凭证与此令牌用途不同，不能互相替代。网页准备文件范围时会提前检查此项，避免完成计划确认后才因平台缺项回滚。
3. Python Worker 使用 `AIW_EXECUTION_GUARD=true`。启动时读取本地 Profile/Skill，按摘要声明 `fixed-profile:`、`fixed-skill:` 能力。执行前重新校验实际 Skill。旧 Go/TypeScript 不声明本协议能力。
4. 通过 `/ui/authority` 确认执行范围和固定输入。默认仅记录，预算可选；严格模式还需确认预算。每个新受控业务任务在创建事务中记录一次 TASKS；技术 Attempt 不增加 TASKS。以前尚未执行的任务首次绑定时补记，重复绑定不重复消耗。
5. 准备输入、工具、后端或命令能力有缺口时不领取。FD-027 负责新增动态配置/知识的准备，FD-028 负责正式问题和续行绑定，FD-026 负责图及循环转移；它们消费本协议，不复制账本。

## 政策与固定依据

政策 JSON 字段对应 `GuardContract.Policy`，包含以下内容：

| 字段 | 含义 |
| --- | --- |
| protocol | 固定为 `aiw.execution.guard.v1` |
| ownerId、planId、revision、expiresAt | 当前人类 Owner、精确计划、从 1 递增的版本、到期毫秒时间 |
| rules | `tool`、`repositoryRef`、`pathPrefix` 的封闭列表；不填写就不授权 |
| commands | 有独立明确许可的命令配置，见下文；默认空列表 |
| budget | 可省略；包含 timeMs、tasks、costMicros、attemptTimeMs、attemptCostMicros、price，严格模式要求完整配置 |

工具枚举为 READ、SEARCH、WRITE、GIT、COMMAND、MODEL。资源是规范的仓库相对路径，根使用 `.`。实际范围是最初确认政策与当前有效政策的交集。命令还须保持最初配置与当前配置完全相同。政策版本变化不会扩大已固定任务的权限；撤销世代变化和到期阻止新操作。已提交 STARTED 意图的操作属于已授权在途，后续真实结果仍可报告。

严格模式下，timeMs、tasks、costMicros 或 price 未配置时，等待用户配置再自动执行；attemptTimeMs 和 attemptCostMicros 要求正整数预留。仅记录时上述预算字段不作为运行门槛。预留不代表硬上限。price 明确 unit、revision、inputPerMillion、cachedPerMillion、outputPerMillion，费率单位为每百万 token 的费用微单位。费用是应用估算，不是服务商账单。

固定输入 JSON 对应 `GuardContract.Bind`：planId、policyRevision、sourceDecisionId、configurationDigest、references。references 的每项包含 kind、id、revision、sha256；同 kind/id 不能重复。当前既存固定 Profile 路径使用 kind=skill，ID 为 Skill 包名，摘要按 Worker 的 `skill_package` UTF-8 文本计算。configurationDigest 为 `ProcessProfile` 的 dataclass 字段经键排序、紧凑 JSON 编码后的 SHA-256。Worker 声明相同摘要才可领取。报告、代码和真实上游 Attempt 来自固定 TaskInput；新增动态配置和知识由 FD-027 扩展准备与校验。

任务和政策指向相同 Plan，必须具有固定的完整 Git 基线和 `execution-guard-v1` 能力。输入固定后不能原地改写 instruction；补充输入建立续行任务。声明分类、Profile、Skill 或知识不会产生工具权限。

## 双后端和版本兼容

OpenAI Agents 只注册 broker FunctionTool，不暴露任意 shell、网络、安装或部署工具。依赖不锁版本，按所需 API 判定兼容，记录实际安装版本。没有接口时修改适配层，不自动安装或更新包。

Codex App Server 的模型路径要求 Unix/WSL，以及操作者预先准备、位于项目之外的 `AIW_CODEX_GUARD_HOME`。不能使用当前个人 Codex home，不复制其登录凭证。模型的 cwd 是空目录，项目文件只通过 dynamicTools 访问。

专用 home 的 config.toml 必须使用命名 Profile（默认 `aiw-model`），根目录 deny、`:minimal` read、网络关闭，不授予任何项目根或额外文件权限。禁止旧 sandbox 设置、MCP、项目层、私人指令、插件、hooks 和外部环境。干净 home 中不能有额外的 Skill/插件/指令来源。

启动前导出本地协议 schema，检查 dynamicTools、item/tool/call、用量、命名权限及 command/exec 的必需字段；不使用 CLI 版本白名单。配置来源必须可核对。模型路径通过 `experimentalFeature/list` 核验全部有效功能均关闭，并在创建线程后再次核验；配置含未知 flag、缺少必须状态、分页不完整、存在生效原生功能或无法确认 activePermissionProfile 时拒绝。额外功能默认开启的新版本会报兼容缺口，不能静默忽略。

此严格预检可能拒绝带有默认配置、管理层或自动附加能力的安装。应根据真实协议/配置证据修改适配层并重新审查边界，不能绕过检查。参考 [App Server](https://learn.chatgpt.com/docs/app-server)、[权限](https://learn.chatgpt.com/docs/permissions) 和 [配置参考](https://learn.chatgpt.com/docs/config-file/config-reference)。

## 文件及本地 Git

文件操作拒绝绝对路径、上级路径、链接、重解析点、硬链接和受保护元数据，限制读写大小。write 需要前次读取的 SHA-256；新文件使用 ABSENT。目录通过 ls 显式探索，grep 只搜索一个文件。同一工作区的工具和命令串行执行。

Git 使用最小环境，禁用 hooks、fsmonitor、签名、pager、自动维护、外部 diff/textconv；拒绝执行型 filters、includes、merge drivers 等配置，每次操作复核配置摘要。工作区不 fetch、不 push。git_integrate 只能合并固定 TaskInput 中相同仓库的真实结果提交；冲突作为实际结果返回，须解决后提交。正式交付绑定真实结果版本及改变文件。成功工作区和本地结果分支作为证据保留，不在自动收尾中删除。

## 验证命令与进程收尾

Windows 的 OpenAI 模型/文件路径可用；验证命令能力只声明于兼容的 Linux/WSL Worker。需显式设置 `AIW_GUARD_COMMAND_ENABLED=true`、宿主已委托的 `AIW_COMMAND_CGROUP_ROOT`，以及 JSON 数组 `AIW_COMMAND_TOOLCHAIN_ROOTS`。宿主须事先启用 cgroup v2 cpu、memory、pids 控制器。本流程不安装 CLI、配置系统或提权。

每个命令配置明确 id、argv 数组、仓库相对 cwd、permissionProfile、timeoutMs、outputBytes、memoryBytes、processLimit。平台不接受模型提供的任意命令字符串。工具链只读根来自部署配置，不能是宿主根、用户目录或宽泛私库。

命令使用独立的无凭证 App Server `command/exec`，不调用模型或查询账户。可信 launcher 先进入操作专属 cgroup，再 exec App Server；后代继承该组。生成的命名 Profile 仅允许精确工作区和必要工具链，禁止网络、受保护元数据、命令 home 及宿主 socket/cgroup 入口。命令环境不继承模型或平台凭证。

正常返回、取消和失败均执行 cgroup.kill，并确认 cgroup.events 的 populated=0，才认定全部进程结束。失败或不确定时保留 UNKNOWN、工作区和槽，不伪报停止。系统权限、Profile 或进程约束不能确认时不声明命令能力。

## 阈值、暂停、追加与 UNKNOWN

暂停会阻止新派发和推进；预算到限仅在严格模式阻止新工作。在途按当前有效权限收尾，消耗真实结算；并行时间相加，失败也计入。完成事实与推进分开持久化，暂停中的成果不丢失。配置恢复或追加后应用待推进事实，不重新执行原任务。

领取事务在同一目标准入锁内为精确 Attempt 预留时间和费用，与 Lease/Attempt 一并提交；严格模式不领取预留不足的任务；仅记录模式继续保留预留数据但不据此拒绝。已提交的 claim 属于在途，后来的暂停、预算到限或其他任务预留不会阻止它开始。start 仍复验 Lease、撤权/到期、固定依据和未知效果，实际执行计时从 start 开始。未启动的领取到取消/失联确认前继续占用资源与预留；确认结束后实际执行时间和模型费用为零。已经启动但无法确定真实消耗的执行仍保留 UNKNOWN 和预留。

回跳账本按 Plan+loopId，初始执行另计；严格模式默认最多回跳三次，仅记录模式不按此额度阻止回跳。每个业务转移 ID 幂等消耗一次；追加不清零。人类明确追加目标、维度、loopId（仅回跳）、增加量和累计上限。秘书须有 A18_BUDGET 的当前有限委托、审批角色及对应固定参数，使用现有身份、世代、禁止和回执体系；私人 policy 不上传。

开始意图提交后丢失响应或结果时，只查询原 operationId。不能改 ID 重做。过期受控 Lease 不按旧恢复路径回队，未确认进程和工具结束前不释放资源。工具效果未知由当前 Owner/审批人核验，秘书只转交证据。执行时间或费用不能确定时保留相应预留及 UNKNOWN，不把人工等待当作已知执行时长。

控制台依次支持：核对每个未知效果 → 核验全部进程静止并确认停止 → 按证据核对未知计量维度。已知消耗不可重写。核对结果、停止确认和追加都有固定身份/摘要冲突检查，不自动退款、重放或回滚。

安全挂起先进入 YIELDING 并保留 Lease/槽；所有工具及执行静止后 ack 才进入 WAITING_INPUT、终结 Attempt 为 YIELDED 并释放槽。继续操作在正式回答 APPLIED 事务中建立唯一后继，将原任务置为非成功终态 CONTINUED；依赖和输入通过同一 lineage 解析最终真实成果。问题及计划/图有效绑定由其拥有者在该事务完成，不在本模块复制问题或图状态。
