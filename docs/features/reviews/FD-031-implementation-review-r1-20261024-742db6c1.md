# FD-031 独立实现审阅 r1

<!-- aiw-data: FD-031-implementation-review-r1-20261024-742db6c1.json -->

未发现阻断性实现缺陷，建议 `verification-passed`。这是一次独立静态实现审阅，不代表 Windows 窗口或真实服务流程已经运行验收。

- FD：FD-031，当前 Revision 5；源事件：`FD-031-000005-implementation-ready`。
- Reviewer Session：`fd031-reviewer-20261024-742db6c1`，已认领上述确切事件；与 Worker `secretary-desktop-20261024-a6529740` 分离。
- 实际审阅提交：`da3d738bbfb98106cc9eeafcc5257fe3b56a3bb2`；Worker 证据提交 `bd16961` 不改变实现。
- 比较范围：包含并发提交 `8126bca` 已纳入的初版桌面及配对代码、`ece0ae5` 的配对拆分，以及 `da3d738` 的修正；没有把 `8126bca` 的其它 Java、Web 或私人计划改动归入本 FD。
- 当前工作区另有 README、app.js、console.css、index.html、pairing.js 的未提交配对增量，本报告明确不覆盖，不提交、不覆盖这些内容。已核对本 FD 的三个 Python 桌面文件、main.py、启动脚本、素材及窄编译脚本与所审提交无差异。

## Findings 与设计反馈落实

本轮没有新增的阻断性或非阻断性代码 finding；未用风格偏好或未授权运行测试作为通过条件。

| 历史 finding | 结果 | 实际依据 |
| --- | --- | --- |
| D031-01：安排刷新静默更换版本 | 已落实 | `desktop.py:524` 深复制已载入工作，展示标题、私人正文、Plan、状态及版本；同一工作普通刷新只检测变化并禁用确认。只有选择其它工作或显式重新载入才接受新的快照。`desktop.py:551` 使用冻结 ID/revision 提交，服务端仍核对当前版本。 |
| D031-02：退出后原目标请求不可找回 | 已落实 | `desktop.py:676` 从持久化 `SELECTED_GOAL` 决策建立历史选择；`goal_query_id` 与当前预览的 request/digest/projection 分开。选择历史只更新查询槽和历史展示，`desktop.py:707` 只查询原 ID；不会从历史恢复提交授权。 |

## 验收覆盖

| FD Acceptance | 静态结果与边界 |
| --- | --- |
| 1：保留 Web、共用服务与记录 | 桌面只通过 `PersonalClient` 访问个人服务 API，没有打开私库、直接访问平台或导入 Web 会话；聊天和工作在相同 store 路由读写。Web 原功能的其余布局改动不属本次差异。 |
| 2：独立配对与会话边界 | `main.py:112` 先检查 Host/Origin，非登录路由随后检查当前个人会话及 CSRF，再生成配对码。码使用 24 字节随机源、五分钟、最多五次尝试、成功后清空；生成新码只替换未使用码，不变更现有 sessions。已提交的独立 pairing.js 只在点击后读取 CSRF 并单次生成，不自动登录或重试。 |
| 3：拖动、吸附、置顶及托盘恢复 | 核对 `desktop.py:746` 的所在显示器工作区限制与 32 像素边缘吸附，以及 `desktop_win32.py` 的 ctypes 参数、指针宽度、窗口过程链、通知图标和 Explorer 重建通知。托盘创建失败禁用隐藏；重建失败显示人物并禁用隐藏。人物素材已静态查看；原生显示效果未运行。 |
| 4：显式聊天、等待时 UI 可操作 | 点击打开聊天及工作选择；只有发送动作调用 chat。`desktop.py:247` 采用单次后台线程和队列，`drain` 在 Tk 主线程应用结果，网络等待不阻塞主线程；busy 阻止并发请求。 |
| 5：安排和策略确认 | 安排见 D031-01；策略载入固定已有 ID/revision，加载和成功保存后清空确认勾选，保留既有服务版本检查，不从旧 restrictionReceipt 构造禁止投影。没有新增平台能力或隐式启用。 |
| 6：精确目标、失效与原请求恢复 | `desktop.py:661` 保留服务返回的精确三元组，来源编辑和异步编辑版本变化使确认失效；`submit_goal` 在发送前锁定本次尝试，失败也不自动重新启用。`selected_goals.py` 的持久 PREVIEWED → SENDING CAS、UNKNOWN 原 ID 查询为既有服务合同，历史入口不会调用提交。 |
| 7：关闭 UI 不停止 Worker | 各表单关闭使用 withdraw；退出只清理托盘、内存客户端及 Tk 窗口，没有 Runtime.cancel、服务终止或 Worker 启动。busy 退出前提示已发送操作可能继续及核对原回执。 |
| 8：升级与重新配对说明 | 所审提交 README 明确首次更新需要重启服务、旧进程许可/会话仍遵循原确认规则；后续 UI 独立启动和重新配对不重启 Worker，也不自动启用动作申请。 |

同时核对了 POST 已返回但后续状态刷新失败的路径：`desktop_client.py:88` 保留返回结果及 CookieJar；界面显示刷新失败提示，不把结果当成未提交，也没有自动重放 POST。会话及 CSRF 仅保存在内存，显示状态移除 csrf；连接只接受带显式端口的本机地址、禁用环境代理及重定向，HTTPS 使用默认校验。

## 证据与实际命令

- 阅读 FD、独立设计报告、Worker 双证据、已解决的 scope blocker、`openspec/specs/secretary/spec.md`、完整桌面与配对源码及周边 Runtime/SelectedGoals 合同。
- 实际使用 `Get-Content -Encoding utf8`、`rg`、`Get-ChildItem` / `Get-Item`、`git status` / `log` / `show` / `diff` / `rev-parse` 作静态读取；使用 `view_image` 静态查看本地 PNG。未执行任何应用代码。
- 工作流命令：`aiw fd show FD-031`；`aiw fd claim FD-031 FD-031-000005-implementation-ready --session fd031-reviewer-20261024-742db6c1` 成功。读取了 claim/emit 帮助。
- 初期共享文件相对路径读取失败，已定位到 `.agents/skills/fd-review/skills/` 后补读；`aiw fd state ... --json` 及 `aiw fd show ... --json` 被 CLI 拒绝，随后使用支持的 `show`。这些失败未运行应用、未改动实现或伪造状态。
- 编译证据来自 Worker：`powershell -NoProfile -File scripts/compile-secretary-desktop.ps1 -Revision HEAD` 在 `da3d738` 通过；本轮阅读了脚本、版本及实际覆盖，不重复执行未变化的编译。它只编译四个 Python、两个 JavaScript 及启动脚本语法，不证明运行行为。

## 未执行与剩余风险

没有执行测试、Tk/Win32/浏览器、服务、真实配对、模型/平台请求、网络、下载、安装或打包，也没有创建下级 agent。本 Reviewer 不修改源码、不提交、不合并、不归档。

Windows 色键透明的实际边缘、显示缩放、多显示器拖动、托盘/Explorer 恢复和多界面真实会话仍未运行验证；它们保留为运行风险，没有被写成通过。结论仅支持上述已提交版本的本地流程通过，不是发布批准。当前未提交 Web 配对改动须保持独立范围，不能引用本报告声称已审阅。
