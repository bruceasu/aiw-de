# FD-035 实现与编译证据 r1

<!-- aiw-data: FD-035-implementation-r1.json -->

Worker Session：`secretary-desktop-20261024-a6529740`，已实际认领 `FD-035-000003-design-ready`。设计比较基线 `7f8c427`；实现分片 `b7946ac`、`bb5e5b4`、`6c22d14`、最终源码 `313568a`。本周期实现审阅失败数为 0，待首次独立实现审阅。

## 已实现

- `AuthorityPageController.questions` 保留原 questionPlans，附加当前 Owner 政策计划的可读标题及部署计量模式。标题仅取同 actor 的 WorkflowPlan，找不到则使用真实 ID。未改变任何写 DTO、服务权限、CSRF 或事务。
- 页面使用 Console shell、共用导航、局部主题样式和键盘分区。默认问题收件箱，按待回答/等待推进/历史筛选；左列表右详情显示原文、必要上下文、要求、来源版本、原答和阻塞原因。无原始 JSON/pre 输出或 JSON 输入。
- 专业问题文本回答，选项只填草稿；授权问题显示结构化中文维度/增加量/累计上限，明确批准或拒绝，不展示服务端的机器 context。只有未生效的 delegated 回答提供带原因的显式覆盖；不可改写的人类原答和 APPLIED/SUPERSEDED 只读。
- `questions.js` 拥有唯一读写协调器。读请求捕获计划和序号；切换清除旧版本，迟到响应丢弃。草稿按计划/问题保存原 requestVersion/answerRevision；刷新不重填草稿，采用新版本需显式核对。写入捕获原 body，冻结重复按钮/计划选择。
- 连接中断、无法解析写响应和 5xx 保留原操作并锁定新写；先读同一计划核对，只有同 messageId 原答或同 requestId 申请可证实已记录。回答/额度可显式重试原 body，不自动换 ID；控制、绑定、送达不提供重放。人类可在读取后明确结束核对。成功写入后的读取失败单独说明，不反转写入成功。
- `question-controls.js` 复用协调器，显示实际任务状态与停止请求/确认，取消/改绑/解绑使用带目标与影响范围的 Console dialog。默认 record-only 不显示额度申请表单；严格模式的高级申请使用安全整数，重复相同表单保留原 requestId。
- 通知说明绑定为 Owner 所有计划共用、Bot 启用由部署决定；逐题明确发送，保留 READY/SENDING/UNKNOWN 和有效 DELIVERED 防重。UNKNOWN 不因过期解锁；100 条送达上限触发保守禁发。读取失败关闭通知写入，不抹掉已读取问题。送达记录可切回当前计划对应问题。

## 设计意见与静态证据

已落实独立设计审阅 `FD-035-design-review-20261024-9f2c6b17` 的四项约束：账号级绑定/100 条截断防重、authorization 结构化展示、精确版本/安全整数、没有同 ID 不推断失败。静态读取实际 QuestionContract/Controller/FormalQuestions/TelegramQuestions、ExecutionLedger/Guard/Accounting、TaskState 与 SQL 计量/停止字段、WorkflowPlanSpec 目标不为空的契约、Console 样式和旧页面。

一次范围内 `git diff --check 7f8c427 -- <FD paths>` 通过；定向检索未发现新页面的 pre/innerHTML，JSON.stringify 仅用于内部 HTTP 序列化或表单指纹。按主工作区既有选择只提交本 FD；其它私人秘书/Python/模型/启动脚本和 FD-033 草案/反馈未暂存或修改。独立审阅应核对真实源码、DOM 与处理路径，而非仅采用本摘要。

## 实际编译

2026-10-24 11:32:41 JST，在 `313568a` 源码上使用用户提供的 `C:/green/bin/setup-jdk25.ps1`，JDK 25.0.1 / Maven 3.9.4，`mvn -o -Dmaven.test.skip=true compile` 成功，292 个生产 Java 文件；两份 `node --check src/main/resources/static/ui/questions.js`、`node --check src/main/resources/static/ui/question-controls.js` 成功。环境脚本只运行版本信息并打印示例，package 示例没有执行；JAVA_HOME/MAVEN_HOME/PATH 在结束后还原。保留既有 Jansi/Unsafe、compilerVersion 与 GraphPlans 未检查操作提示。

未运行测试、浏览器/Thymeleaf、数据库/迁移、服务、真实登录/回答/控制/通知、乱序/断线恢复、模型、网络/下载、打包、安装、重启或发布。编译只证明语法/类型，不能证明实际会话、SQL、DOM、窄屏或异步交互通过；运行平台仍需更新并重启。FD-033 的授权范围问题独立待定。

下一步：独立实现 Reviewer 认领真实 implementation-ready，审阅全部验收与上述限制；通过后主 PM 继续本 FD 范围提交与 Complete 归档。
