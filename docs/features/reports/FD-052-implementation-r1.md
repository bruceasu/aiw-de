# FD-052 实现报告 r1

<!-- aiw-data: FD-052-implementation-r1.json -->

Worker Session：gui-pet-tray-20261024-c81e62；来源 FD-052-000003-design-ready；基线 400426b。独立设计 r1 ready。

## 授权与范围

用户明确选择“纳入 Qt GUI 前置文件并统一调整”，因此纳入原未跟踪 desktop_webview.py、requirements-desktop.txt、start-desktop.sh 和已有 Qt 选择的 start-desktop.ps1。未纳入其他 Runtime、SDK、Markdown、Web 主题或业务模板的未提交改动。main.py、desktop.py 上保留其他任务改动，通过相同的精确替换从 HEAD 派生 index blob，仅提交本次启动接线；desktop_client.py 仅追加一个后台启动抑制参数。

## 实现

desktop_startup.py 选择本机可用 GUI：SSH/headless 不启动，Windows 还检查当前 window station 可见标记；本机有 PySide6 时 Qt，Windows 无 Qt 时 Tk，其他缺依赖情况返回不启动，服务/浏览器继续。子进程无控制台、无 shell、无凭证命令参数，未安装依赖，不循环重试。main 仅本地可自动连接 origin 且 GUI 环境自动请求人物；原浏览器行为保留，新增 --no-desktop-pet 独立控制。GUI 启动服务附 --no-browser 与 --no-desktop-pet，切断服务再启动 GUI 的循环。

Qt/Tk 在 GUI/自动连接副作用前按 origin 取得 DesktopLease。Windows 会话 named mutex 保持句柄，重复句柄检测后退出；POSIX 私人文件 flock 持有至退出。路径/初始化错误不假装已有实例；OS 关闭句柄/文件即释放，无需删除永久标记，atexit 与显式退出可幂等释放。

Tk 初始 withdraw，托盘可用则暂停人物动画并保持隐藏；无托盘或 --show-pet 显示人物。自动连接不依赖未创建的设置控件，不弹出对话或模型设置；手动连接保留原显式流程。Qt 默认不 show 人物/工作台，连接后隐藏加载本地 Web 页面；无托盘显示可恢复窗口，运行期检测托盘消失时显示人物。托盘可开聊天/工作、显示人物；修正 Qt QAction 布尔信号覆盖菜单参数的 lambda 接线。Qt 新入口沿用既有不显示人物底部文字的要求，移除该前置版本底部信息/按钮板并收紧窗口。退出桌面不停止服务。

start-desktop.ps1 默认隐藏进程并支持 -ShowPet、-LegacyTk、-Opaque；sh 入口沿用透传参数，默认同 Qt 行为。新增 docs/DESKTOP-STARTUP.md 并更新资产说明。Qt 主题设置仅在已存在的 Web appearance 能力可用时同步，不将其他未提交主题资源纳入。

## 验收静态映射

1. main 的 local_attach/桌面门控、helper 的 SSH/WindowStation/DISPLAY 与依赖选择，失败返回或捕获 OSError；服务不等待 GUI 启动完成。
2. desktop_client 双抑制参数；两套 GUI 在自动连接前共用 origin 锁，退出/崩溃由 OS 释放。
3. Tk withdraw/automatic 分支、Qt 初始与 ready 分支、托盘菜单及显式显示参数。
4. Tk 无托盘 show、Qt 无托盘/托盘丢失回退、窗口关闭路径；无关闭服务动作。
5. 原 --no-browser 保留，新增 --no-desktop-pet；文档说明分别作用。
6. 精确暂存与白名单仅含批准前置和任务文件，其他工作保留。

## 实际检查与限制

git diff --cached --check 通过；同一静态批次读取暂存统计和 main/client/Tk/PowerShell 关键差异。实际运行现有 scripts/compile-secretary-runtime.ps1，通过：仅 stdlib compile() 与 node --check，不导入 SDK 或启动 GUI。该脚本含其他任务的工作区 Python/JS，编译成功只代表语法，不属于那些功能的审阅通过。随后 compile() 额外在内存编译三份已暂存 main.py、desktop.py、desktop_client.py，exit 0。PowerShell Parser.ParseFile 检查 start-desktop.ps1，无语法错误，未执行启动脚本。

未运行测试、GUI、服务、模型、HTTP、网络、依赖安装、停机/重启、打包或部署。Linux sh 包装只静态读取，未执行。真实 Windows/Linux 托盘、OS 锁竞争、Qt 动态库可用性、交互桌面检测、进程递归与窗口恢复尚未实测。启动请求成功不代表托盘已创建，静态/编译不等于运行验收。
