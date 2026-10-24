# FD-052 独立设计审阅 r1

<!-- aiw-data: FD-052-design-r1.json -->

结论：ready，无阻塞设计发现。设计来源 FD-052-000002-design-requested；本报告仅为独立设计反馈，不是实现验证通过事件。

## 设计与边界

1. main.py 已有 desktop_session_available 和本地 loopback 判定，适合限制自动人物只在图形桌面、本机服务启动。原浏览器选择保留，新增 --no-desktop-pet 与 --no-browser 分工明确，不把用户关闭浏览器误当作停用人物。
2. desktop_client.auto_connect 当前启动 main 只传 --no-browser；补上 --no-desktop-pet 能直接切断 GUI→服务→GUI 的递归。服务绑定端口并写入本地连接资料后再启动 GUI，可让子进程沿用原会话入口；不得在服务启动路径等待子 GUI 完成后才开始 serve_forever。
3. Qt/Tk 当前都在成功连接时主动显示对话/工作台，因此默认托盘不仅要去掉初始 show，还需调整自动连接完成分支。Qt 可在隐藏状态加载已有 Web 页面；Tk 需将自动连接与手工连接控件分离，防止访问尚未创建的 service/code/connection 控件。
4. Qt 的 isSystemTrayAvailable 与 Tk 的 TrayIcon.active 可作为初始隐藏条件；无托盘必须保留可见人物或工作台，且窗口关闭/隐藏操作不能让所有入口都消失。退出桌面不关闭个人服务，符合原边界。
5. 按 origin 共享 OS 单实例可避免 Tk/Qt 和服务自动载入并存。持有对象应覆盖整个 GUI 生命周期；重复实例不启动新 GUI 或新服务。Windows 会话级 named mutex 与 POSIX 持有 flock 不依赖永久标记，正常退出或崩溃后由 OS 释放。
6. 用户已明确批准纳入 Qt 前置及启动脚本；这不扩展到其他 Runtime、主题资源或业务模板的未提交修改。完整提交差异仍须在 Worker 交接前核对。

## 实现时核对点

- 单实例获取须先于 GUI 自动连接/启动服务；锁失败须区分“已有实例”和真实初始化错误，不将权限或路径错误默认为已有实例。
- 只有托盘实际可用才保持默认隐藏；自动连接成功不得再调用 show_chat/open_view 等激活窗口的路径。
- --show-pet 对新实例明确显示人物；已有实例时不重复启动，文档可提示通过既有托盘找回。
- 自动进程启动失败不终止服务，不安装依赖、不自动重试；无可用 Qt 且非 Windows 时保留浏览器/手动服务入口。
- Tk 隐藏人物时暂停原动画回调，显示再恢复；关闭先释放动画和单实例资源，不能杀后台服务。

## 静态证据与限制

读取 FD、planning reference、main 的环境/服务启动分支、desktop_client.auto_connect、Tk start_connection/connect/托盘生命周期、未跟踪 Qt ready/open_view/closeEvent/托盘代码及启动脚本。只执行 Get-Content/Select-String 等只读命令及本报告写入。未运行测试、编译、GUI、服务、网络、依赖安装或模型；未修改实现、FD 状态或工作流事件，未提交。真实 OS 锁竞争、托盘恢复、进程启动及默认可见性需后续实现证据，不以静态设计可行代替实测。
