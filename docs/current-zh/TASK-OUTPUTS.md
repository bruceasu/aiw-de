# 工作区任务产出

新任务报告与声明的附件保存在持久工作区 `.aiw/o/<SHA256(attemptId)>/`，
不放在任务结束后删除的临时 worktree。Control Plane 登记精确版本、SHA-256 和执行来源，
任务状态仍由现有任务/执行表决定；登记文件不等于任务完成或评审通过。

报告由 Worker 程序保存，模型不直接修改共享归档。每个 attempt 独立；只允许当前有效租约登记。
模型可以声明工作区内的附件，运行时在清理前收集，拒绝链接和路径越界。正式文档先共享，
通过人工确认的入库计划写入项目现有文档目录，并由独立 stage-reviewer 验收。

TypeScript Worker 的 REPORT/WORKSPACE 最终回复可以只有一个非空 `aiw-output-files` 声明块。
没有额外正文时，报告保留其中内联 DOCUMENT 的原文；仅声明附件或源文件时，报告使用明确标为
“Declared task output files”的文件清单，不声称已评审文件内容。文件内容、来源、路径和大小仍须
通过原有收集校验后才能完成任务。空列表、空内容或缺少来源不能代替报告；VERDICT 任务仍须
在声明块外给出正文和最终 `AIW_VERDICT`，不能从附件推断通过。

迁移采用新增表，不修改既有表和完成协议字段。回滚应用时保留新增表和 `.aiw` 文件；
不要修改已应用的 Flyway 迁移或自动删除历史交付。新协议能力由领取响应显式声明。

## 文件与状态

每个归档包含 `report.md`、`report.json`、`manifest.json`；灵活计划任务还包含 `plan.md`、
`plan.json`，作为该执行使用计划的快照；声明的文件进入 `artifacts/`。完整计划、任务、执行 ID 和 UTC 时间保存在清单和数据库中，不再叠加到目录名。
历史 `.aiw/plans/...` 目录保留，仍按数据库登记位置读取，不自动搬迁。Windows 新归档文件路径限制为240字符，超限明确拒绝；Worker 快照支持扩展路径，仍检查旧目录，不忽略不可读文件或目录联接。文件使用 SHA-256 和字节数登记，版本不可覆盖。

磁盘 manifest 中 `REGISTERED` 只表示已登记候选；读取接口返回现有 execution_attempt 的状态，
另用 reviewState 区分未完成候选、尚无独立 PASS 评审、已有独立 PASS 评审和评审报告。
任务完成与文档被独立评审通过不是同一个状态。
删除计划历史前保存执行状态及独立 PASS 评审来源快照，交付记录和文件不随计划历史删除。完成步骤的输出默认供
依赖任务使用，登记但未完成或失败的输出不会成为普通依赖输入。失败报告按可用租约尽力保存。
此版不共享正在生成的文件流；中间产出指已完成步骤的草稿、报告和附件。

数据库保留内容副本，下载不依赖远端机器的绝对路径。工作区文件与数据库不是一个事务：
数据库登记失败可能留下未登记的候选目录；目录不能据此认定完成，不自动重跑业务。
本版不自动删除交付物，磁盘与数据库容量由管理员管理。

## 接口

任务页面“查看失败诊断”使用项目统一弹窗，分段显示原因、影响步骤及恢复建议；技术字段和格式化JSON折叠显示，可复制给AI分析。弹窗只读，不发起业务重试；关闭会取消未完成的读取。

原生 Python 编码 Worker 在基线留空时，读取已分配仓库当前 `HEAD`，通过持有的运行租约请求 `POST /api/r1/worker-protocol/v1/tasks/{taskId}/workspace-baseline` 固定完整提交，再使用平台生成的 worktree/branch 创建隔离工作区。首次固定后重试保持该基线，明确指定版本不变；只包含已提交版本，不带入源目录未提交修改。非Git或无提交目录报 `GIT_HEAD_UNAVAILABLE`。该入口拒绝受控执行/动态配置任务，不替代其范围授权。

本地 Git 合并按本次变更文件在当前工作区、目标提交和结果提交中的有效 `filter` 属性检查过滤器；仅安装/配置 Git LFS 不会阻止未使用过滤器的文件合并。实际应用过滤器时提示对应文件；无法读取属性（包括 Git 不支持 `check-attr --source`）时停止合并并明确说明。工作区差异、前后保留检查及实际合并在各自 Git 子进程中禁用已配置过滤器，不修改原仓库配置，不执行未修改文件的过滤器；状态名与同名过滤器配置存在歧义时保守拒绝。非 UTF-8 路径/输出明确报错，避免路径替换造成误判。原有评审、脏文件及自定义合并驱动保护保留。

- `GET /api/r1/plan-editor/plans/{planId}/tasks/{taskId}`：使用控制台认证读取任务、执行历史及重试条件。每次失败的 `diagnostic` 含 `code`、`summary`、`nextAction`、`specific`，可由已认证 AI 助手分析，不返回租约凭证或原始异常文本。旧 `OPERATION_FAILED` 明确标记具体原因未记录，不推断为缺授权；新 Worker 会上报路径过长、文件缺失、访问拒绝等具体错误码。任务页面也提供该 JSON 链接。

- `POST /api/r1/worker-protocol/v1/tasks/{taskId}/outputs`：登记报告和附件。
  请求含 workerId、leaseToken、attemptId、verdict、summary、resultRevision、files。
  files 含 path、kind（DOCUMENT/ATTACHMENT）、contentBase64。
- `POST /api/r1/worker-protocol/v1/tasks/{taskId}/outputs/read`：读取固定输入 bundle。
  请求含 workerId、leaseToken、attemptId、bundleId。仅允许当前运行租约及依赖/文档入库的固定来源。
- `GET /api/r1/task-outputs/tasks/{taskId}`：按执行版本列出交付物。
- `GET /api/r1/task-outputs/files/{fileId}`：下载文件，响应头包含 SHA-256。
- `POST /api/r1/task-outputs/files/{fileId}/publication-plans`：创建正式文档入库草稿。
  请求含 reviewTaskId、targetPath、reason。来源须已完成，且有依赖该来源的已完成 PASS 评审。
  来源与评审须由不同 Worker ID 完成；带文档或附件的步骤会要求评审 Worker 具备 task-outputs-v1。

SQLite 默认连接池只有一个连接。交付物列表通过单条关联查询读取清单、执行状态和评审状态，
行映射回调只解析当前结果，不能再发起 JDBC 查询，否则会等待外层查询占用的连接而超时。

Worker 接口沿用现有 Worker token，额外检查租约归属、有效期、执行 ID 和停止请求。
领取任务时按上游 TaskInput 的 attemptId 固定输入绑定；后续读取不会重新选择最新结果。
自动生成的计划快照供人查看，不在每个上游输入中重复注入；任务上下文仍使用现有计划指令。
UI 接口沿用登录会话；创建入库计划要求 Operator 权限及既有 CSRF 校验。
任务交付物区域沿用控制台中英文切换，包含执行/评审状态、文件大小单位、入库表单和固定提示；
工作区目录及文件路径保留原文。服务器返回的诊断详情仍保留原文。
完成协议字段保持不变，领取响应只新增可选 outputStore；旧 Worker 的有工作区完成报告
由 Control Plane 兜底登记。旧 Worker 没有报告时，只保存明确标记 NO_REPORT 的完成元数据，
不伪造模型报告。没有对应工作区的任务不声明本地输出存储能力。
旧服务器没有 outputStore 时，Worker 不提取输出文件代码块，完整正文继续作为原报告提交。

## 正式文档入库

任务详情页选中来源 Markdown 文档、填入通过的评审任务、目标项目路径及原因，即可生成
入库草稿。确认草稿后，由具有 coding 和 task-outputs-v1 能力的 Worker 写入目标文件，
再由具有 workflow-review 和 task-outputs-v1 能力的独立 Worker 验收。
程序检查目标内容与来源摘要完全一致，其他项目文件未改变；有 Git 基线时仍使用原有本地提交流程。
来源文件、评审任务、目标路径保存在登记记录及报告 JSON 中，结果提交保存在输出清单中。
不得推送远端，不自动初始化 Git。需要编辑正文时先产生新版本并重新评审，再入库。

当前默认人工确认。可通过既有计划确认接口明确确认生成的入库计划；不增加后台自动发布触发器。
任务产出不包含原始提示词、模型工具转录和 DEBUG 日志。

## 上线与验证

先部署含 V3 迁移的 Control Plane，再更新和重启 TypeScript Worker。需要迁移失败回滚时
保留新增表；恢复旧应用不会自动删除文件。归档写入失败会阻止本次成功回报，失败回报
仍受现有 lease 和未知完成响应规则约束。产出较大时还受 Worker 原有上下文预算约束，超限明确拒绝。

尚未运行迁移、测试、真实 Worker、浏览器或部署验证；这些不能由编译检查代替。

本次已通过离线 `mvn -o -DskipTests compile`、Worker TypeScript 语法编译、
`npm --prefix extern-workers/process-typescript run build`，以及 outputs/attempt/workspace/task-detail 的
`node --check`。静态核对了输入版本、租约、路径、独立评审、清理和旧协议调用链。
