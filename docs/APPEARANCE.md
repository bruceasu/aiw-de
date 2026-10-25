# 秘书的 HUD / RPG / Lady 主题

Control Plane 保留自己的 HUD / RPG 主题；秘书 Web 与 Qt 桌面秘书另有 HUD、RPG、Lady 三种外观，并使用 `personal-secretary/ui/` 下自己的脚本、样式、语言脚本和人物图片。Control Plane 使用 `src/main/resources/static/ui/` 下自己的资源。主题只影响呈现，不包含模拟工作、虚构消耗、等级或经验值。

## 人物与布局

- **RPG**：棕发卡通伙伴、任务手册、暖金装饰。
- **HUD**：银蓝发智能助理、青色光线与几何面板。
- **Lady**：原有秘书形象，使用深靛蓝底色、银紫主色与浅青点缀，作为安静的月夜主题。
- Control Plane、秘书 Web 与 Qt 内嵌工作台不再创建窗口内的伙伴浮层，也不显示欢迎区域的大幅人物插画，避免遮挡工作区。
- 左上角品牌区使用圆形 Q 版秘书头像，私人对话中秘书回复旁使用 32px 头像；HUD / RPG / Lady 分别使用 `appearance-avatar-hud.png` / `appearance-avatar-rpg.png` / `appearance-avatar-lady.png`，随主题同步切换。头像素材缺失时尝试既有主题人物作为回退。头像没有全身位移动作；原有状态栏、计数和导航继续承担状态及操作入口。
- 桌面 PET 随主题更换同一套透明 PNG。默认窗口和人物区域透明，不绘制人物背后的矩形面板。人物上下不显示装饰标题和说明；真实状态使用状态灯与图标计数，悬停可查看含义。只有显式使用 `-Opaque` / `--opaque` 时填充整个窗口背景。透明效果仍取决于桌面合成器。

人物由内置 imagegen 生成，素材及完整提示词见 [素材记录](APPEARANCE-ASSETS.md)。现有原人物保留供旧 Tk 界面和缺失素材时回退。

### 窗口内的 Q 版头像

以现有 `appearance-rpg.png` / `appearance-hud.png` 为角色参考生成了 HUD 与 RPG 透明头像；本次再以原始秘书形象生成 `appearance-avatar-lady.png`，沿用相同的方形头肩构图，并加入月夜银紫与浅青光感。秘书实际使用的 PNG 均位于 `personal-secretary/ui/assets/`，不依赖用户目录。头像采用近景构图，确保缩小后面部仍清晰。

生成要点（Easy English）：Create a square chibi secretary avatar. Use the reference only for character identity. Show the head and shoulders, with a large readable face and the full hair silhouette inside the frame. Keep a friendly professional expression, clean anime game UI shading and a transparent background. No text, frame, scenery, hands or full body. RPG uses brown hair, warm brown eyes, a teal ribbon and restrained gold details. HUD uses silver-blue hair, cyan eyes and a navy-and-white collar with cyan details.

## 语言、动作与状态

秘书 Web 使用自己的 `personal-secretary/ui/i18n.js`，在“设置 → 外观与语言”提供主题与简体中文、English、日语切换，右上角不再展示偏好控件。默认使用已保存的语言；秘书首次使用时按浏览器语言选择支持的语言，否则使用英文。Control Plane 保留自己的语言资源和偏好设置入口。语言按服务 origin 分别保存；同 origin 的浏览器窗口同步切换。Qt 桌面保存语言偏好，并跟随内嵌页面更新窗口标题、托盘菜单和提示。旧 Tk 界面仍使用原文案，不在这次语言改动范围内。

翻译覆盖主题设置、秘书静态页面、常用动态状态与桌面菜单；日期和排序跟随所选语言。用户输入、模型回复、任务标题、原始 JSON、文件内容保持原文；后台返回的原始诊断也不会自动改写。

独立 PET 的 HUD 与 RPG 人物各有常态和眨眼表情；Lady 使用既有秘书图像，暂时没有专用眨眼帧。空闲时有轻微呼吸动作；HUD 与 RPG 会定期眨眼，鼠标移入 PET 时会短暂回应。系统启用减少动态效果，或控制台关闭动画时，停止这些动作与表情切换。这是图片表情帧与轻量位移动画，不是骨骼或口型驱动。窗口内的头像保持静态。

人物区不再显示上下方的装饰文字。绿色状态点表示秘书可用，暖色表示暂停，灰色表示尚未就绪；图标计数分别表示进行中、待确认和已完成，悬停或辅助技术可读取标签。计数只取已有私人状态，不向模型发请求；没有私人状态时隐藏计数。人物素材加载失败时显示警告标记并尝试原人物回退，可通过提示判断是否需要重启更新后的服务。

独立分发需包含秘书自己的 `ui/appearance.js`、`ui/appearance.css`、`ui/i18n.js` 和 `ui/assets/` 下的人物图片，包括三张 `appearance-avatar-*.png` 头像、`appearance-secretary.png` 与两张 `appearance-*-wink.png` 表情素材。更新这些资源后应重启个人服务并刷新页面，退出旧 Qt 进程再重新启动。

### 升级后语言没有变化

Qt 主工作台内嵌 Web 页面，两边共用翻译脚本。已启动的旧 Python 服务可能继续从磁盘读取新版 HTML，却仍使用启动时的旧资源路由，使新增 `/i18n.js` 返回 401；单独刷新页面或只重启桌面无法更新后台路由。需要重启个人服务，退出旧 Qt 桌面并重新启动，再刷新浏览器。

语言选择器现在直接在页面中提供，不依赖人物主题脚本创建。翻译脚本没有加载或初始化失败时，会禁用语言选择并显示独立提示；普通状态刷新不会覆盖该提示。高级功能按钮也使用独立展开状态，不再通过比较已经翻译的按钮文字判断行为。

PET 的托盘主题菜单先直接应用本地人物及桌面外观，并保存偏好，再同步内嵌页面；页面尚未就绪时保留待同步选择，页面载入后发送。这样不会因 Web 主题组件尚未加载而使本地人物切换无响应。更改 Python 桌面代码后仍需退出旧进程才能生效。

## 本地服务与桌面退出

Qt PET 菜单提供启动、停止本地 HTTP 服务，以及“退出秘书并停止本地服务”。后者确认端口关闭后退出桌面；停止失败则保留桌面并报告原因。停止前提示当前请求会中断，正常关闭后保留私库和设置，单独停止时 PET 仍可用于再次启动服务。“退出桌面界面（服务继续运行）”仍保留为独立选项，关闭工作台 × 仅隐藏窗口。

`GET /api/service/control` 仅对直接本机请求提供控制会话，不读取私人工作或对话，也不等待业务锁。桌面停止前刷新控制会话，避免旧 CSRF 或业务处理阻塞停止入口。`POST /api/service/shutdown` 只接受直接本机连接、有效个人会话和 CSRF 校验，反向代理或远程会话不能停止服务。该操作不向模型暴露工具，也不通过进程名或 PID 强制结束服务。返回 `STOPPING` 表示已接受停止请求，桌面随后等待监听端口关闭（最多 30 秒）；无法确认时明确报告，不假定停止成功。服务先关闭监听，再取消并等待后台清理，清理可能继续约 25 秒；端口关闭不等于所有后台清理已结束。尚未完成的请求应在下次启动后核对已有结果。

手工入口为 `personal-secretary/stop.ps1`：默认使用当前 Python 调用 `stop_service.py` 正常停止；`-Port` 指定端口，`-Python` 指定解释器。脚本不会自动启动服务。只有显式使用 `-Force` 且正常停止未确认时，才核对监听 PID、Python 进程名、本仓库 `main.py` 完整路径、进程创建时间和当前 Windows 用户，再强制结束这一进程并检查端口。它不会批量结束 Python、Qt 或其他端口进程，也不删除私人数据。强制停止可能中断写入，之后应核对结果；运行中的旧代码不会因磁盘更新自动获得新停止路由，可使用此手工入口完成一次停止。

桌面显式退出统一设置退出状态，停止轮询、动画与未完成的启动意图，隐藏托盘，允许窗口关闭后结束事件循环。启动／停止菜单操作期间禁用重复操作，后台返回的旧回调不能重新显示已经退出的窗口。

## 使用

- Control Plane 在页面顶部选择 **HUD** 或 **RPG**；秘书在“设置 → 外观与语言”选择，立即切换并保持该主题。
- 秘书可手动选择 **HUD**、**RPG** 或 **月夜 · Lady** 并保持该主题；Control Plane 仍只提供 HUD / RPG。
- 选择 **自动**，按本机时间切换。默认 00:00–08:00 使用 Lady，08:00–19:00 使用 HUD，19:00–00:00 使用 RPG。
- **时间设置**可调整 HUD 与 RPG 的开始时间；两者必须不同。两个时点按实际先后切换，Lady 填补午夜前后未覆盖的时段。
- 手动选择后停止自动切换，再选择“自动”恢复。页面重新激活或电脑唤醒后重新核对时间。
- 偏好按浏览器 origin 保存在本机 localStorage。同 origin 的页面同步主题；Control Plane 与秘书服务通常使用不同端口，分别保存偏好。存储不可用时仍可在当前页面切换，并提示不能保存。
- 桌面端与秘书 Web 使用相同页面；桌面外壳只将外观偏好保存到当前系统用户的 Qt 设置，并按个人服务地址区分。不复制浏览器 cookie 或平台登录。

HUD 使用青色边角、细网格与几何面板。RPG 使用暖金边框、衬线标题与伙伴插画。Lady 使用深靛蓝、银紫和浅青色。正文、警告/成功/失败语义和业务操作保持清晰，窄屏显示同一主题的简化布局。

### 窗口与图标

秘书工作区采用双层边框、分层面板和描边按钮：HUD 使用青色边角和网格，RPG 使用暖金双线与角饰，Lady 使用银紫圆角和浅青细线。导航和计数使用本地 SVG 线条图标，不依赖系统字体中的特殊符号。窗口内以品牌与消息头像表示秘书，不再提供人物浮动入口。

Qt 外壳配套绘制 HUD 折角、RPG 暖金边框、Lady 银紫圆角、主题徽标和窗口控制图标，托盘图标也随主题切换。人物周围保留透明背景。这里实现的是效果图的边框、图标和面板语言，业务界面保留现有导航和信息布局；不是将效果图作为背景贴图。语言选项固定显示 English、简体中文、日本語，HTML 使用字符实体保护中文和日文名称。

窗口画板直接接收当前主题，外框、标题栏渐变、底栏、WebView 底色和菜单悬停色一同更新。主按钮的边框、渐变与悬停色也使用主题变量：RPG 为暖金，HUD 为青色，避免通用按钮样式覆盖 RPG 配色。Python 外壳更新须重新打开 Qt 桌面，CSS 更新须刷新页面；不需要修改模型或重新登录。

Web → Qt 的状态快照通过 `JSON.stringify` 明确返回字符串，由 Python 有界解析；不依赖特定 Qt/PySide 版本将 JavaScript 对象自动转换为 Python 字典。此快照只含主题、语言、状态与计数，不含聊天正文。如果页面已显示 RPG，而原生标题仍显示 HUD、语言也未同步，应检查这条快照传递链路。正常运行时原生外壳在下一轮轮询（约三秒）同步；切换后应同时看到标题中的 RPG 与暖金边框。该传递修复尚未做 Qt 运行验收。

Qt 工作台默认使用透明顶层窗口和透明 WebEngine 背景。页面收到桌面标记后，以约 94% 不透明度绘制完整工作区底色，空白与卡片间隙也有遮罩；侧栏、标题栏和卡片使用同等或叠加后更高的不透明度，减少桌面文字干扰。文字、按钮与人物不降低整体透明度。画板不在 WebView 后重复铺底；独立浏览器页面不设置此标记，维持正常背景。`-Opaque` / `--opaque` 同时让工作台与人物使用不透明回退。透明效果依赖桌面合成器，尚未运行 Windows/Linux 视觉验收。

托盘和人物共用一个三语菜单，创建原生窗口前先恢复已保存语言；打开菜单时也请求最新页面状态快照，后续切换沿用同一 JSON 同步链路。网页不可用时保留已保存语言，不依赖网页成功载入才首次显示对应菜单。

上述视觉调整仅完成源码与编译检查，实际 Windows 缩放、窗口边角、托盘图标和 Web 页面观感仍需运行验收。

## 桌面实现

Windows / Linux 使用 Python + PySide6 Qt WebEngine，共用秘书 Web 功能，不另建模型、授权、文件或平台业务入口。原 Tk 界面保留为 Windows 的显式 `-LegacyTk` 入口；三种主题的主要桌面实现为 Qt。

先在启动桌面的同一 Python 环境安装 `personal-secretary/requirements-desktop.txt`，然后：

```powershell
cd personal-secretary
python -m pip install -r requirements-desktop.txt
./start-desktop.ps1
```

Linux：

```sh
cd personal-secretary
python3 -m pip install -r requirements-desktop.txt
sh ./start-desktop.sh
```

安装命令由用户在需要时执行；代码不会自动下载依赖。Linux 还需要发行版所需的 Qt/X11/Wayland 图形运行库及中文字体。桌面启动器复用或按已有机制启动本机个人服务，使用原有会话/CSRF 认证。关闭工作台保留人物入口；退出桌面不会停止后台服务。

人物窗口提供对话、任务、待确认入口和真实工作计数，右键菜单可切换主题与打开设置。系统支持时显示托盘，缺少托盘时仍能从人物打开工作台。透明背景或合成器显示异常时，可使用 `./start-desktop.ps1 -Opaque` 或 `sh ./start-desktop.sh --opaque`。Wayland 的窗口定位、置顶和托盘行为由桌面环境决定，代码不提升权限或禁用 WebEngine 沙箱来绕过限制。

主 WebView 仅加载所选本机个人服务；外部 HTTP/HTTPS 链接交系统浏览器。没有向网页暴露 Python 方法、文件系统或命令执行桥。正文仍在个人服务，不进入外观偏好。

## 资产与验证边界

### Control Plane 国际化

Control Plane 的公共导航加载 `preferences.js`、`console-translations.js` 和 `i18n.js`，登录页也显式加载这三份脚本，沿用当前浏览器的语言偏好。`console-translations.js` 维护控制台专用的英文、简体中文、日文词条，补充授权、任务配置、问题、组合角色、Worker 工作台、执行记录和图计划等页面的静态说明及常见动态反馈。新增页面文案也应同时维护三语词条，不能仅接入语言下拉框。

动态计数和系统字段前缀使用明确模式，编号、用户输入、原始 JSON、代码和凭证不送去模型翻译。执行记录与 Worker 工作台的数字、日期及授权到期显示使用当前语言。主题和语言仍分别按各自服务地址保存；控制台与秘书不自动共享不同端口的偏好。控制台静态资源随 Java 应用分发，已有运行实例可能需要重新启动才能加载新增资源；这里没有执行重启或浏览器验收。

秘书服务从自己的 `ui/` 和 `ui/assets/` 目录提供外观脚本、样式、语言脚本与人物图片，不再读取 `src/main/resources/static/ui/`。Control Plane 保留自己的外观资源。既有 `desktop-assets/secretary.png` 保留给旧 Tk 界面，并作为 Qt 人物素材缺失时的回退。

`scripts/compile-secretary-runtime.ps1` 包含 Python 源码和主题/秘书 JavaScript 的离线语法编译，不导入 Qt、不启动 GUI、不下载依赖、不调用模型。主题视觉、Windows WebEngine/透明窗、Qt 依赖版本和 Linux 不同合成器仍需单独运行验收；编译结果不代表这些行为已验证。

技术依据：[Qt WebEngine 平台概览](https://doc.qt.io/qtforpython-6.9/overviews/qtwebengine-overview.html)、[QWebEnginePage 导航控制](https://doc.qt.io/qtforpython-6/PySide6/QtWebEngineCore/QWebEnginePage.html)、[QSystemTrayIcon](https://doc.qt.io/qtforpython-6/PySide6/QtWidgets/QSystemTrayIcon.html)。
