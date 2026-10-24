# FD-052: GUI 启动秘书时自动载入托盘桌面宠物

**Status:** Open
**Revision:** 3
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户希望秘书在图形桌面环境启动时同时载入 desktop pet，默认只驻留托盘。现有 main 只自动开浏览器；Tk 自动连接会打开连接/对话窗口；工作区 Qt 前置版本启动会同时显示伙伴与工作台。用户已明确授权将 Qt 前置文件（desktop_webview.py、requirements-desktop.txt、启动脚本）纳入本次交付，其他未提交功能仍保留。

## Options and decision

选择复用两套既有 GUI，默认隐藏且仅在托盘可用后保持隐藏，无托盘显示可恢复窗口。Qt 前置入口同时沿用用户既有“人物下方不显示文字底板”偏好。服务 GUI 启动按本机环境选择已安装的 Qt，Windows 无 Qt 时回退 Tk；Windows 当前 window station 不可见时不自动启动；无可用桌面依赖或 headless 时不影响服务/浏览器。增加内部 DesktopLease 单实例避免服务自动载入与手动 GUI 重复。新增 --no-desktop-pet 抑制自动人物启动，GUI 启动服务时同时传 --no-browser/--no-desktop-pet，避免递归。公共 CLI、进程与窗口生命周期有变化，必须独立设计审阅；最终实现审阅另行进行。

## Solution

新增 desktop_startup.py：选择桌面入口、启动无控制台子进程及按 origin 持有 OS 锁。Windows 使用当前会话 named mutex，POSIX 用私人锁文件持有非阻塞 flock；崩溃后 OS 释放，退出幂等释放。服务仅在 desktop_session_available 且本地 loopback 可连接时自动启动宠物，保留原浏览器策略；--no-browser 仅控制浏览器，--no-desktop-pet 单独控制人物。Qt/Tk 默认托盘，--show-pet/-ShowPet 明确显示人物；自动连接成功不弹出对话或模型设置。Qt 隐藏加载 Web 页面，托盘仍可打开聊天/工作台。退出 GUI 保持服务继续，不杀后台进程。

## Scope

新 desktop_startup.py；main.py、desktop_client.py、desktop.py 的精确启动接线；用户授权的 Qt 文件与 requirements-desktop.txt/start-desktop.ps1/start-desktop.sh；desktop-assets/README.md、本 FD 证据。主工作区 develop，脏的 main/desktop/启动 PS 只提交本次精确修改及明确授权的 GUI 前置部分，不提交其他 Runtime/主题/Web 模板改动。无安装依赖、服务启动/重启、模型、GUI、网络、打包或部署执行授权。主要风险是隐藏后失去入口、重复进程和启动递归；可见回退、OS 锁与双禁止参数防止。

## Work items

- [x] 1.1 实现桌面选择/单实例与服务自动载入，防止 GUI 启动服务递归。小/中，无依赖，证据为启动参数、环境门控和锁释放。
- [x] 1.2 Qt/Tk 默认托盘与安静连接，显式显示选项及无托盘回退，纳入批准的 Qt 前置。小/中，依赖 1.1，证据为可见性路径及菜单。
- [x] 1.3 更新说明、静态/最小 Python 与 PowerShell 语法编译、范围提交及独立审阅。小/低，依赖 1.2。

## TODO

独立设计审阅 r1 ready，1.1–1.3 已实施；暂存差异检查、仓库编译脚本、三份提交区 Python 源码编译及 PowerShell 语法编译通过，待独立实现审阅。

## Acceptance

1. GUI 桌面环境且本机可连接的秘书服务启动时自动载入宠物；SSH/headless/远程 origin 不尝试，宠物启动失败不终止服务（1.1）。
2. GUI 启动服务带两个抑制参数，不递归载入；同 origin 的 Tk/Qt 共享单实例，退出/崩溃不留永久锁（1.1）。
3. Qt/Tk 启动有托盘时默认不显示人物、连接/对话/工作台；托盘可打开对话或显示人物，--show-pet 可显式显示（1.2）。
4. 无托盘时可见窗口保底，隐藏不会造成所有入口丢失；关闭桌面不停止秘书服务（1.2）。
5. 原自动浏览器行为和 --no-browser 作用保留，新增 --no-desktop-pet 可独立关闭自动人物；CLI 文档同步（1.1–1.3）。
6. 仅授权 GUI 前置和本次接线纳入，其他脏改动保留；检查与未验证项如实报告（1.3）。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.

## Sources

- Issue: none
- 用户当前要求及“纳入 Qt GUI 前置文件并统一调整”明确选择。
- main.desktop_session_available/serve、desktop_client.auto_connect、Tk start_connection/connect、Qt ready/tray、private_config.PrivateProcessLock 既有模式（只读参考）。
- Session: gui-pet-tray-20261024-c81e62；设计来源 FD-052-000002-design-requested。

- 独立设计证据：docs/features/reviews/FD-052-design-r1.md 及 JSON，ready，无阻塞。
