# FD-001 Worker 实施报告

<!-- aiw-data: FD-001-implementation-r3.json -->

**Worker session:** fd001-worker-20261004-b2aa6c85

**Source event:** FD-001-000003-design-ready

**范围：** REQ00001 M1；主工作区顺序实施，没有修改其他 worker 的既有变更。

**审查基线：** `master` 的 `d33dbbd12dd8aa906633fe4b518f73d14ee283ee`；只审查本 FD 的 webconsole/UI、两份指南及 FD-001 证据路径，其他未提交 worker/需求/FD 草案保留。

## 实施结果

已实现 Overview 的有界只读投影、共享深色战术样式、稳定 Worker SVG 身份、真实状态/租约、受保护的 HTML 局部轮询及中英文指南。原 URL、动作服务、CSRF/角色权限、schema 和公开 REST 保持原契约。详细字段来源、限量规则及修改表见 [历史实施进度](FD-001-implementation-progress-r3.md)。

Overview 由单份 Fleet 模型及私有参数化查询组合：12 个非终态 Run、8 个关注 Run、8 个 PENDING 审批、8 个 Worker、每类最多 5 个等待任务、最近 20 条跨 Run 活动。审批和非终态 Run 总量独立准确统计，100 条 Fleet 详情窗口不冒充全集；活动每个来源分支先限 20，再合并排序限 20，没有全 Run timeline 遍历。

共享 CSS 覆盖原表格、表单、认证/错误页及焦点；首页/Fleet 使用确定性完整 ID 的 SHA-256 形象与本地动效偏好。每 5/8 秒只替换只读区域，最多一个在途请求、10 秒超时、30 秒陈旧提示；保留焦点/选择/展开/滚动，隐藏/编辑暂停，错误保留快照，登录/角色/异常 HTML 停止更新。Worker 操作区保留初次加载选项，提供重载入口，提交仍由原服务校验。

## 实际执行与证据

| 检查 | 命令/结果 | 证据 |
|---|---|---|
| compile-only | JDK 21；`C:/green/apache-maven-3.9.9/bin/mvn.cmd -o -DskipTests compile`；175 sources，BUILD SUCCESS，7.891s | 工具输出；没有打包 |
| 4 个 Web Console IT | `mvn.cmd -o "-Dit.test=R9S1WebConsoleIT,R9WebConsoleSecurityIT,R9WebConsoleOperationsIT,R9WebConsoleApprovalIT" test-compile failsafe:integration-test failsafe:verify`；4/4 passed，14.166s | `target/failsafe-reports/TEST-me.asu.aidev.webconsole.*.xml`，用户授权 `call_FNlbt5rJg8Skp20kkHAKYv6m` |
| Chromium 视觉/交互 | `C:/green/python312/python.exe .ai/fd/FD-001/visual-runtime.py`；第三次 29 项通过，最后焦点联合断言失败；本次进程仍记 exit 1 | [原始结果](FD-001-visual-evidence/worker-browser-results.json)；18 张 PNG；前两次错误窗口断言原始记录同目录 |
| 焦点针对复验 | `C:/green/python312/python.exe .ai/fd/FD-001/visual-runtime.py focus`；修正 CDP 激活配置后 exit 0，2/2 passed | [焦点数据](FD-001-visual-evidence/focus-diagnostics.json)、[焦点结果](FD-001-visual-evidence/worker-focus-results.json)、表单/键盘焦点截图；用户授权 `call_bTOP5D1rp0XPNuk1O1VciNiW` |
| 静态检查 | 定向 `rg`/`Get-Content`、一次 `git diff --stat`/`git diff`/`git status --short` 检查本 FD 路径 | 原控制器/服务/CSRF 不变；新查询参数化/结果有界；没有暂存其他任务 |

所有应用/DB/浏览器验证都使用本机端口、专用临时数据库与浏览器 profile；没有访问生产 DB、下载依赖、请求外部服务或生成发布包。辅助脚本静态审阅后运行，其失败记录没有删除或改写。焦点失败来自未激活的 CDP 页面目标；复验记录 `windowFocused=true, focusVisible=true, outline=solid`，不需要修改页面样式。用户分别明确授权运行、修正后重跑和两次焦点诊断。

## Worker 验收映射与独立交接

| 验收 | 当前 Worker 证据 | 独立阶段仍需核对 |
|---|---|---|
| AC01/02/03/09 | 空态/真实计数、活跃/失败/暂停/审批、动态任务数、截断、参数化查询、四宽度截图 | 旧 pending 被大量已决定记录淹没、各等待原因细分、事件稳定排序/来源完整性 |
| AC04/08 | 四种 Worker 状态、跨页面同形象、真实租约、local motion/system reduced motion、键盘焦点 | 独立验证无角色推断、长 ID/能力与阅读入口 |
| AC05 | 20 条活动与真实来源/对象链接、安全文本；不是完整审计 | 缺失原因/关联、时间相同的稳定顺序 |
| AC06/10 | 原操作/审批/VIEWER 安全 IT、共享旧页面截图、中英文指南 | 完整 VIEWER/OPERATOR/APPROVER/ADMIN 入口矩阵、实际浏览器提交/错误反馈 |
| AC07 | 单请求/10 秒超时/30 秒陈旧/失败/异常 HTML/焦点/滚动/展开与输入保留 | 背景页暂停/恢复、编辑/重置/恢复、真实登录失效与角色变化等细分场景 |

此报告是 Worker 交接，未把部分覆盖描述为独立测试通过。Tester 必须基于 FD/公开契约独立列出可观测场景，写 root `tests/` 与 Dual 报告；PM 接受测试报告后再交独立 Reviewer。每个新测试命令需绑定事件、FD revision/digest 与 Tester session 的正式 Planner 授权。

## 未运行、用户决定与风险

- 用户明确「允许 Firefox 检查延期」（`call_jYhXte7wfCOj9ZcoHPSXYu2t`）；Firefox 未运行，保留兼容风险，功能要求未减少。
- 未运行覆盖率、formatter/linter/typecheck、全量最终构建、外部网络、部署/发布。
- 完整角色矩阵及暂停恢复等尚待独立验证，不能以本报告代替最终验收。高数据量查询成本未测；Worker 操作仍是初次加载快照，服务端提交检查保持权威。
- 仓库目前缺少 Skill 引用的通用 report/test JSON 模板文件；报告采用安装版 `aiw-fd.py` 的实际 `aiw.fd.evidence.v1` 字段契约与中文 Markdown/同名 JSON，交接由原 CLI 校验，不手写回执。
- M2–M4 和 REQ00001 整体状态不因 M1 Worker 就绪而标记完成。Reviewer 失败次数目前 0。
