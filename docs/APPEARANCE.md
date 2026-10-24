# HUD / RPG 双主题

Control Plane、秘书 Web 与 Qt 桌面秘书共用 `src/main/resources/static/ui/appearance.js`、`appearance.css` 和 `appearance-secretary.png`。图片复用已存在的秘书素材，主题不包含模拟工作、虚构消耗、等级或经验值。

## 使用

- 页面顶部选择 **HUD** 或 **RPG**，立即切换并保持该主题。
- 选择 **自动**，按本机时间切换。默认白天从 08:00 开始使用 HUD，夜间从 19:00 开始使用 RPG。
- **时间设置**可调整两个开始时间；两者必须不同，也支持跨午夜的白天时段。
- 手动选择后停止自动切换，再选择“自动”恢复。页面重新激活或电脑唤醒后重新核对时间。
- 偏好按浏览器 origin 保存在本机 localStorage。同 origin 的页面同步主题；Control Plane 与秘书服务通常使用不同端口，分别保存偏好。存储不可用时仍可在当前页面切换，并提示不能保存。
- 桌面端与秘书 Web 使用相同页面；桌面外壳只将外观偏好保存到当前系统用户的 Qt 设置，并按个人服务地址区分。不复制浏览器 cookie 或平台登录。

HUD 使用青色边角、细网格与几何面板。RPG 使用暖金边框、衬线标题与伙伴插画。正文、警告/成功/失败语义和业务操作保持清晰，窄屏显示同一主题的简化布局。

## 桌面实现

Windows / Linux 使用 Python + PySide6 Qt WebEngine，共用秘书 Web 功能，不另建模型、授权、文件或平台业务入口。原 Tk 界面保留为 Windows 的显式 `-LegacyTk` 入口；双主题的主要桌面实现为 Qt。

先在启动桌面的同一 Python 环境安装 `workers/personal-secretary/requirements-desktop.txt`，然后：

```powershell
cd workers/personal-secretary
python -m pip install -r requirements-desktop.txt
./start-desktop.ps1
```

Linux：

```sh
cd workers/personal-secretary
python3 -m pip install -r requirements-desktop.txt
sh ./start-desktop.sh
```

安装命令由用户在需要时执行；代码不会自动下载依赖。Linux 还需要发行版所需的 Qt/X11/Wayland 图形运行库及中文字体。桌面启动器复用或按已有机制启动本机个人服务，使用原有会话/CSRF 认证。关闭工作台保留人物入口；退出桌面不会停止后台服务。

人物窗口提供对话、任务、待确认入口和真实工作计数，右键菜单可切换主题与打开设置。系统支持时显示托盘，缺少托盘时仍能从人物打开工作台。透明背景或合成器显示异常时，可使用 `./start-desktop.ps1 -Opaque` 或 `sh ./start-desktop.sh --opaque`。Wayland 的窗口定位、置顶和托盘行为由桌面环境决定，代码不提升权限或禁用 WebEngine 沙箱来绕过限制。

主 WebView 仅加载所选本机个人服务；外部 HTTP/HTTPS 链接交系统浏览器。没有向网页暴露 Python 方法、文件系统或命令执行桥。正文仍在个人服务，不进入外观偏好。

## 资产与验证边界

秘书服务直接提供上述共享静态资产，不维护第二份主题代码。因此独立分发个人秘书时，也必须包含 `src/main/resources/static/ui/appearance.*` 和 `appearance-secretary.png` 的相对目录。既有 `desktop-assets/secretary.png` 仍用于 Qt 人物窗口。

`scripts/compile-secretary-runtime.ps1` 包含 Python 源码和主题/秘书 JavaScript 的离线语法编译，不导入 Qt、不启动 GUI、不下载依赖、不调用模型。主题视觉、Windows WebEngine/透明窗、Qt 依赖版本和 Linux 不同合成器仍需单独运行验收；编译结果不代表这些行为已验证。

技术依据：[Qt WebEngine 平台概览](https://doc.qt.io/qtforpython-6.9/overviews/qtwebengine-overview.html)、[QWebEnginePage 导航控制](https://doc.qt.io/qtforpython-6/PySide6/QtWebEngineCore/QWebEnginePage.html)、[QSystemTrayIcon](https://doc.qt.io/qtforpython-6/PySide6/QtWidgets/QSystemTrayIcon.html)。
