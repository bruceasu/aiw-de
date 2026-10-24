# 桌面秘书形象

`secretary.png` 是桌面 UI 使用的 1024 × 1536 PNG 插画，含透明 alpha 通道。由内置 `imagegen` 工具根据本功能需求生成，未使用外部人物参考或照片。运行时由 Tk 读取并按整数比例缩小显示，项目无需在线下载图片。人物仍是单张站立插画：轻量动画在现有 Canvas 上做轻微起伏和完成时稍明显的起伏反馈，配可眨眼的矢量表情气泡，不修改原插画面部，也不是多姿态或 Live2D 模型。

表情始终配中文状态：等待回复、实际工作中、已安排、待确认、暂停和结果待核对分别表达。日常暂停读取 `assistant.paused`，不把未启用专业平台误称秘书暂停。轮询不会伪装工作开始，单次请求成功不会伪装任务完成。只有已观察的工作进入 `DONE` 才短时提示“工作已完成”；聊天回复只提示“有新回复”。首次读取历史完成记录及错误后重新连接不触发庆祝。

人物右键菜单的“人物动画”可关闭运动及眨眼，状态文字和静态表情仍更新。隐藏到托盘后取消动画回调，显示时恢复；退出先取消回调。动画仅使用 Tk 主线程中的一个约 120ms 定时器，不新增线程、依赖或图像素材。拖动、置顶、点击对话和托盘行为保留。升级后需重新启动桌面界面，后台服务无需因此重启；实际显示、CPU 占用、托盘恢复和关闭行为仍需 Windows GUI 验证，未将源码编译当作运行通过。

生成模式：内置工具，`transparent_background=true`。已静态查看图片，并检查 PNG 的 ARGB 格式及边角透明度。Windows 色键透明与实际屏幕上的边缘显示效果尚未运行验收。

最终提示词：

```text
Use case: stylized-concept. Asset type: a transparent PNG character for a Windows desktop secretary app. Create one beautiful adult female office secretary, about 28 years old, in polished 2D editorial anime illustration. She is standing, full body, facing the viewer, with a warm calm smile and an approachable professional manner. Long dark brown hair, tasteful navy blazer and skirt, ivory blouse, a small teal accent, simple dark shoes. One hand rests naturally near her waist; the other holds a slim closed notebook. Natural adult proportions, graceful silhouette, detailed expressive face, clean confident outlines, restrained shading. Keep the entire hair and both shoes inside the frame, with a small transparent margin. Center one character in a portrait composition. Real transparent background with alpha, no scenery, no floor, no cast shadow, no text, no logo, no frame, no extra characters. Keep the face and silhouette clear when shown about 320 pixels tall.
```

实现参考：[Python Tk 图片说明](https://docs.python.org/3/library/tkinter.html#images)、[Tk Windows 窗口属性](https://web.tcl.tk/man/tcl9.1/TkCmd/wm.html)、[Windows 通知图标结构](https://learn.microsoft.com/windows/win32/api/shellapi/ns-shellapi-notifyicondataw)。
