# 桌面秘书形象

`secretary.png` 是桌面 UI 使用的 1024 × 1536 PNG 插画，含透明 alpha 通道。由内置 `imagegen` 工具根据本功能需求生成，未使用外部人物参考或照片。运行时由 Tk 读取并按整数比例缩小显示，项目无需在线下载图片。首版为单张站立形象，移动由窗口拖动实现，状态由真实请求提示展示，不声称已经实现多姿态角色动画。

生成模式：内置工具，`transparent_background=true`。已静态查看图片，并检查 PNG 的 ARGB 格式及边角透明度。Windows 色键透明与实际屏幕上的边缘显示效果尚未运行验收。

最终提示词：

```text
Use case: stylized-concept. Asset type: a transparent PNG character for a Windows desktop secretary app. Create one beautiful adult female office secretary, about 28 years old, in polished 2D editorial anime illustration. She is standing, full body, facing the viewer, with a warm calm smile and an approachable professional manner. Long dark brown hair, tasteful navy blazer and skirt, ivory blouse, a small teal accent, simple dark shoes. One hand rests naturally near her waist; the other holds a slim closed notebook. Natural adult proportions, graceful silhouette, detailed expressive face, clean confident outlines, restrained shading. Keep the entire hair and both shoes inside the frame, with a small transparent margin. Center one character in a portrait composition. Real transparent background with alpha, no scenery, no floor, no cast shadow, no text, no logo, no frame, no extra characters. Keep the face and silhouette clear when shown about 320 pixels tall.
```

实现参考：[Python Tk 图片说明](https://docs.python.org/3/library/tkinter.html#images)、[Tk Windows 窗口属性](https://web.tcl.tk/man/tcl9.1/TkCmd/wm.html)、[Windows 通知图标结构](https://learn.microsoft.com/windows/win32/api/shellapi/ns-shellapi-notifyicondataw)。
