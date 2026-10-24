# FD-052 独立实现审阅 r1

<!-- aiw-data: FD-052-review-r1.json -->

结论：verification-passed；未发现阻塞验收的静态缺陷。此结论不代表真实桌面运行通过。

Reviewer Session：fd052-reviewer-20261024-b92f43。精确来源事件：FD-052-000004-implementation-ready。审阅 FD revision 4；实现提交 115ee4dcddd20f7030eaabb283fcc74d5887214d，对比基线 400426b。Worker Session 为 gui-pet-tray-20261024-c81e62，与本 Reviewer 独立。

## 发现与验收证据

无阻塞发现，无需因本轮静态审阅修改实现。

1. 服务接线：main.serve 在本地 origin、desktop_session_available 门控后调用启动 helper；helper 再检查 SSH、Windows 可见 window station 或 Linux display。Popen 不等待 GUI 就绪；无依赖返回 False、OSError 不终止服务，浏览器原分支保留。
2. 递归与单实例：desktop_client 启动服务同时传 --no-browser、--no-desktop-pet；Qt/Tk 在自动连接前取得同一 origin 的 DesktopLease。Windows named mutex 持有句柄，POSIX flock 持有文件对象，退出释放并以 atexit 兜底；初始化错误不会误报已有实例。
3. 默认托盘与安静连接：Tk 初始 withdraw，托盘存在时暂停动画；automatic=True 不先创建连接窗口，成功后不调用 show_chat/show_settings，相关状态更新对尚未创建控件有保护。Qt 初始不 show；ready 只隐藏加载页面；菜单 lambda 吸收 QAction 布尔参数。显式 --show-pet 可显示人物。
4. 可恢复入口与退出：Tk 无可用 TrayIcon 时显示人物，hide_pet 需要 active；既有 TaskbarCreated 恢复失败事件显示人物。Qt 无托盘显示人物及工作台，轮询发现托盘消失时显示人物；窗口关闭和菜单退出未终止后台服务。Qt WebView 以独立内存 profile 获取本机 /api/state 会话，未引入平台桥接。
5. 兼容及文档：--no-browser 仍只控制浏览器，--no-desktop-pet 独立控制人物；Windows/Unix 包装参数及 docs/DESKTOP-STARTUP.md 与实现一致。主题同步在 Web appearance 能力存在时才生效，不要求把其他任务主题文件纳入本 FD。
6. 范围：已逐项读取 400426b..115ee4d 的 13 个变更文件统计和相关源码差异。Qt 前置、桌面依赖声明和包装脚本属于 FD 的明确批准范围。main.py/desktop.py 当前工作区相对该提交仍有其他任务差异，本次结论覆盖上述提交的本 FD 交付内容，不替其他未提交功能背书。

## 静态证据与执行限制

读取 fd-review、共享合同及 Implementation review 方法、FD、Worker 双证据、独立设计报告、相关 secretary 稳定规格和真实源码及附近调用路径。规格未要求额外桌面运行验收；FD Verification 明确以编译和静态审阅为默认。

实际命令：Get-Content、Get-ChildItem、Select-Object、Select-String、rg、git show、git diff（含 --stat）、aiw fd --help/show/claim/emit 子命令帮助；领取上述来源事件。git diff 400426b 115ee4d --check 通过。报告完成后使用 aiw fd emit FD-052 verification-passed --producer reviewer --artifact docs/features/reviews/FD-052-review-r1.md --source-event FD-052-000004-implementation-ready 记录结论。

Worker 报告所列编译通过作为既有证据读取，本 Reviewer 未重跑，也未声称独立观察了当时执行。未运行测试、编译、GUI、服务、模型、网络、依赖安装、打包或部署；未改实现、未提交、未合并、未归档。

剩余风险：真实 Windows/Linux 的托盘创建与恢复、锁竞争、动态 Qt 依赖、桌面可见性和子进程启动尚未实测。仅检测 PySide6 包存在不能证明 Qt 动态库可用，启动请求成功也不能证明托盘创建成功。相关限制在 Worker 报告及用户文档已明确保留。
