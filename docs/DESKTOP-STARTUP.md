# 秘书桌面与托盘启动

在有图形桌面的本机环境启动个人秘书服务时，同时请求启动 desktop pet，默认隐藏在系统托盘。原浏览器自动打开行为保留。SSH、无显示会话、Windows 非交互 window station、非本机服务 origin 不自动启动人物。

自动选择已安装的 PySide6 Qt GUI；Windows 未安装 Qt 时使用原 Tk 人物。Linux 没有 Qt 时保留浏览器/服务入口，不安装或下载依赖。Qt 手工安装依赖见 `workers/personal-secretary/requirements-desktop.txt`，此文件只供用户自行安装。

GUI 自动连接成功不会弹出人物、工作台、对话或模型设置。点击托盘图标可打开对话，托盘右键可显示人物或打开工作与安排、待确认和设置。托盘不可用时显示可见窗口，防止入口丢失；Tk 隐藏时暂停人物动画。新纳入的 Qt 伙伴也不显示人物下方文字/按钮底板，沿用既有简洁人物偏好。

Windows 手动运行 `workers/personal-secretary/start-desktop.ps1` 默认托盘启动；`-ShowPet` 显示人物，`-LegacyTk` 使用 Tk，`-Opaque` 供 Qt 使用实色背景。Linux 用 `start-desktop.sh`，可传 `--show-pet`。直接运行 `desktop.py` 或 `desktop_webview.py` 同样默认托盘，可传 `--service <本机地址>` 和 `--show-pet`。显示选项仅对新实例生效；已有实例请从原托盘找回。

服务命令 `main.py --no-browser` 只关闭浏览器自动打开，`main.py --no-desktop-pet` 单独关闭自动人物。GUI 自动启动服务时会传两个参数，防止服务再启动一份 GUI。同一 origin 的 Tk 与 Qt 共用 OS 单实例；重复启动不创建新窗口或新服务。Windows 锁为会话内 named mutex，POSIX 锁由存活进程持有；正常退出或进程崩溃后 OS 释放，无需删除锁文件。

退出桌面只关闭界面，服务继续运行；服务停止后桌面也不会因为轮询失败自动重启服务。手工重新启动 GUI 仍可复用或按原方式启动缺失的本地服务。人物启动请求失败不会停止服务，且不自动重试。启动请求成功不等于已验证托盘创建成功。

本次开发只做源码静态检查与语法编译，未启动 GUI/服务，未执行模型、依赖安装或网络请求。真实 Windows/Linux 托盘、锁竞争、桌面检测和后台启动行为仍需运行验证。
