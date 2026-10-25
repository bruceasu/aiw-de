# Worker 工作台与上下文能力

FD-044 沿现有 Worker、固定技能、角色编译和受控工具补充 Pi 调研中的九项机制。没有安装 Pi 或增加 Worker 类型，没有新增费用或执行预算门禁。

## 入口与执行阶段

打开 `/ui/configurations?view=workbench`，或从组合角色、执行记录、自定义工作方法及任务详情进入。选择当前账号最近 100 个有计量记录的尝试，无须填写内部 ID。“执行情况”“补充与排队”“探索方案”切换显示；手动刷新，不自动运行任务。

阶段与工具详情来自已有记录，并显示读取时间。模型一轮文字结束后可能还有工具与收尾，完成/失败始终以原平台 Task/Attempt 为准。停止请求不是已停止，陈旧上报不能当作当前细分状态。

## 补充和排队

新的执行中输入由主动开启工作台的 **Python API 受控 Worker** 提供。

1. STEER 在下一模型请求的安全边界补充当前工作；FOLLOW_UP 在当前 SDK 运行产生报告后、正式结果收尾前继续。
2. 两种方式都属于原 Task/Attempt、角色与指令版本，保留原权限、总体期限和实际模型总调用上限。换目标、换范围继续走正式指令修订，不靠补充文字换权限。
3. 收据区分 QUEUED、RESERVED、DELIVERED、CANCELLED。只可撤回 QUEUED；RESERVED 已被 Worker 领取。DELIVERED 要求该批次绑定的原 MODEL 操作成功，不代表理解、完成或独立验收。
4. 提交响应未知时先核对原收据，再明确重试相同原请求。未知模型/工具结果不自动重放，原尝试结束后未执行内容不自动转到另一尝试。
5. 输入领取与最终封口有事务边界：有输入则继续领取；没有待领取/未确认批次、活动/未知操作才封口。封口后拒绝新输入，不默默重启任务。

补充文字不能撤销已发生的操作。正式问答让出任务时停止新增模型请求，原问答续行合同保持。

## 方案探索与比较

保存方案名称、问题、做法和依据，可选同任务父方案，附上本次尝试已有的结果版本。勾选两至三个方案比较；采用/放弃均保留原因、操作者和时间，展开来源可回查本方案全部选择记录。父引用不可改，父子同 Owner、同任务；新选择不覆盖旧结论。

“带入补充草稿”只填输入框，核对长度后再明确提交。记录、比较和选择不自动启动模型、不切换底层 transcript、不回退文件。它提供可回查的 A/B 记录，不增加平台任务状态。

## 共享能力包

在角色编辑步骤中展开“将本步技能与做法保存为能力包”，填写名称，明确是否需要写入或命令，再保存。它复用固定技能引用和“本步做什么”；执行条件及完成标准仍属于当前角色。

其他角色可在同仓库导入为新步骤，重新设置条件、交付并预览。需要写入的包须与 WORKSPACE 产出相容，原专业技能/六技能/八步等检查保持。工具需求只是声明，不授予权限。

定义 `aiw.capability.pack.v1` 使用原 Owner/仓库范围的不可变 knowledge 资产。系统生成标识，响应未知按原请求核对，不覆盖原版本。包不包含可执行插件、安装命令、模型路由或任意工具定义；普通知识不因名称前缀相似被隐藏。

## 技能、历史与结果

三语言 reader 继续校验完整冻结文件。主技能、工程协议及无法可靠提取描述的入口保留全文；其他已选技能先给摘要/版本/目录，使用前须用 `read_skill_file` 读完原 SKILL.md。没有“最新同名目录”回退或新增脚本权限。

Python API 按字节安全边界压缩旧可见历史，保留完整 call/result 配对、近期消息、固定 instructions 和已认证补充指令的精确原文。摘要是派生数据，带来源；原可见数据在本次尝试内存中，可通过 `read_context_source` 分页回查，不归档隐藏推理。

摘要使用原已选模型、受控 MODEL 计量、期限和实际总调用次数，最多两次压缩，不换模型或续次数。无安全切点、源容量不足、摘要失败或仍放不下时明确失败。字节安全限制不是精确 token 窗口或财务预算。

小工具结果保持原结构，大结果给完整有效的模型 envelope、摘要与分页入口；不截成损坏 JSON。完整数据与模型预览分开。容量不足明确 `fullOutputAvailable=false`，不能为找回输出重复副作用。内存源在运行结束清除，不是持久云端日志；工作台只展示受控操作元数据，默认不上传原始日志或仓库正文。

## 实际支持

| 后端 | 渐进平台技能入口 | 新实时输入 | 历史与工具基础 |
| --- | --- | --- | --- |
| Python API 受控动态配置 | 支持 | 本次 session 开启后支持 | 新可回查压缩、大结果源；复用 Broker 原操作锁、授权和收尾 |
| Python API 原独立模式 | 有平台文件集时支持 | 不提供 | 有上下文源与压缩，不因此获得平台 guard/config 能力 |
| Python Codex App Server | 支持 | 本次不提供 | 原资源读取、问答、取消和受控工具路径 |
| TS/Go 已支持文件集的 API/App Server | 对齐 | 本次不提供 | 保留 TS 可选压缩、资源协调及其他后端真实能力 |
| 原 TS/Go Codex CLI | 原合同不支持 | 不提供 | 不通过新名称伪称支持 |

Broker 原可重入锁覆盖整段读改写、命令/Git；原独立 API 保留 ToolContext 锁，TS 保留原资源协调。没有新增跨进程锁保证。执行扩展点是可信运行时的 begin/execute/finish/unknown/settle，不允许 Skill 自行安装可执行钩子。

## 协议、发布顺序与证据

人类复用 `execution-configurations/state|candidates` 明确 kind；原 method/role/observations 保留，默认 state 仅追加 `capabilityPacks` 索引。机器复用 `execution-context/read?kind=workbench`，严格校验 RUNNING Task/Attempt、live Lease、无 stop request、原版本和服务器 Owner。原 fixed 读取、会话/CSRF/操作角色及工具授权保持。

V19 仅追加工作台/批次/收据/探索表，不回填旧尝试，不改原任务快照、账本、公共资产与默认方法。正常更新先提供新控制平面及迁移，再更新/登记 Worker。旧 Worker 继续原合同；回退前停止新交互、核对未确认批次并保留数据，新 API Worker 不对旧服务静默降级或重放输入。

实际数据库迁移、SDK/模型/Worker、浏览器、并发、问答/取消、回退和效果评估未运行；静态与生产编译不能代替运行证明。本次不安装、部署或重启服务。

算法复用本仓库 [FD-005 context.mjs](../extern-workers/process-typescript/runtime-core/context.mjs) 的安全切点与可见来源思路，原 [Pi 来源记录](../extern-workers/process-typescript/runtime-core/SOURCE-PROVENANCE.md) 及 [Python 参考许可](../workers/process-python/THIRD-PARTY-NOTICES.md) 保留，没有嵌入 Pi SDK。

SDK 概念核对使用 [OpenAI 官方结果与状态](https://developers.openai.com/api/docs/guides/agents/results)、[运行与流式结束](https://developers.openai.com/api/docs/guides/agents/running-agents)：本地续行用 `to_input_list()`，流结束后处理报告。当前环境缺对应 Python SDK 源码，具体 provider/ModelSettings 兼容仍需运行证据，官方文档不等于本系统运行通过。
