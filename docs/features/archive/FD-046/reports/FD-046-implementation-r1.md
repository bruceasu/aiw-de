# FD-046 实现报告 r1

<!-- aiw-data: FD-046-implementation-r1.json -->

Worker：goal-delete-binding-20261024-44a708。来源 FD-046-000003-design-ready。基线 7afec60。

## 静态诊断与修改

用户明确报告删除返回 Bad Request。GoalController.Revision 包含 primitive boolean confirmed，goals.js 的删除/恢复/分析/应用原请求未提供该字段。Boot 4.0.8 本地 BOM 选择 Jackson 3.1.5；实际 javap 只读反汇编显示 FAIL_ON_NULL_FOR_PRIMITIVES 初始化 iconst_1（true），不能依赖缺失布尔自动为 false。原响应为 Spring 通用 Bad Request，与 DTO 解析前拒绝相符。实际用户请求及服务器异常日志未取得，因此这是静态支持的具体请求缺陷，未声称线上根因已实测。

仅产品三文件变化：goals.js 补齐删除 confirmed:deleted、步骤分析两处及应用 confirmed:false；正式化 checkbox、准备执行 confirmed:true 保持原语义。四处浏览器确认全部改为 await window.aiwConfirm，删除及放弃编辑使用 danger，取消路径仍在任何修改或请求之前返回。goals.html 先 defer 加载 console.js，再加载 goals.js，复用现有弹窗、样式、文本转义、焦点及取消处理。附近说明同步更新。

未更改后端 DTO、全局 Jackson、鉴权、CSRF、归属、revision、存储或实际执行确认。FD-045 的未知结果只读核对保留。

## 验收证据

1. 对照 Controller 各 Revision 路由与前端字面量：删除、恢复、formalize、analyze 两处、apply、prepare 全部有 confirmed。
2. 页面四处确认均为 await aiwConfirm；删除为 danger，取消不会写入，切换及 reload 不丢弃编辑。
3. defer 脚本按顺序加载，原 console.css 已引用，未新增弹窗样式和 DOM 拼接；原请求保护不变。
4. 文档及本报告区分静态推断与未运行验证。

## 实际检查及未验证项

实际：git diff --check 通过；同一静态批次检查差异和统计；node --check src/main/resources/static/ui/goals.js exit 0。只读 javap 命令针对本地 Jackson 3.1.5 jar，没有依赖下载或产品执行。无 Java 修改，不运行 Maven。

未运行测试、HTTP、浏览器、数据库、服务启动/重启、部署或网络请求。真实删除成功及弹窗外观/键盘交互尚未实测。一次 rg 使用 PowerShell 字面量 glob 路径无效，后续改用现有目录与 -g 过滤；历史测试日志仅文件路径清单，无运行证据。
