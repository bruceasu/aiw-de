# FD-047 实现报告 r1

<!-- aiw-data: FD-047-implementation-r1.json -->

Worker Session：secretary-now-20261024-873d19。来源 FD-047-000003-design-ready。基线 08f70d0。

## 修改与静态依据

秘书后端 main.py 原 /api/run-work 已按 id/revision 校验并调用 daily.schedule 使用当前服务端时间，无需 UI 时间；/api/schedule 保留未来整分钟与时区校验。daily.schedule 原有专业任务及运行中工作防重，tick 保留暂停处理。此次不修改任何 Python 或后端接口。

Web 安排表单默认 executionMode=now，时间输入初始隐藏、disabled、非 required。updateExecutionMode 随选择同步 hidden/disabled/required 和按钮文本，保留已填写时间与工作选择。马上执行请求分流到 run-work，仅传 id/revision，不发模式和 dueMs；定时路径保留 schedule、workId/revision、日期转换及时区。原 aria-busy 和 submit.disabled 覆盖整个提交。马上成功仅说明提交及进度自动更新，并提醒暂停时需继续工作，不称已完成。工作卡片改为“安排执行”；README 和周边描述同步。

## 验收映射

1. HTML 默认马上执行，时间非必填且禁用；切换到 scheduled 后恢复必填。
2. 提交捕获 executionMode 并在 API 前删除该 UI 字段；马上只用服务器既有当前时间接口；定时仍经原日期转换。
3. 切换只修改输入状态，不清空值；原 post 刷新、busy 防重和服务器校验不变。
4. README 解释两种方式、暂停及提交不等于完成。

## 实际检查与限制

git diff --check 通过；同一静态批次读取产品差异及 diff --stat；node --check workers/personal-secretary/ui/app.js exit 0。已检查 scripts/compile-personal-secretary.ps1，它包含无关 Java、Python、TypeScript 等编译，本次仅改 Web JS，故使用最小 Node 语法编译，未运行该多模块脚本。

未运行测试、浏览器、HTTP、模型、服务、重启、部署、网络或依赖下载；实际 UI 交互及任务执行未实测。一次预防性 patch 因预期行不存在而未应用，随后定向读取确认原 post 分流位置本就正确，无需改动，不作为验证通过证据。
