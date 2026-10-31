# FD-070: 秘书侧栏收起与桌面人物缩放图标易读

**Status:** Complete
**Revision:** 4
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

个人秘书主界面无法收窄左侧导航；桌面人物缩放时，Qt 画布把状态徽标、数字和图标一并缩放，缩小时难以辨认、放大时又显得过大。

## Options and decision

在现有左上角头像上提供双击切换，收起后保留导航图标和可访问名称，并保存选择。桌面人物的缩放只影响人物绘制；状态徽标和图标维持易读的像素尺寸，按窗口尺寸定位。

## Solution

使用个人秘书现有的头像和导航图标完成侧栏展开/收起。头像支持双击鼠标以及键盘激活；侧栏偏好保存在当前浏览器。Qt 悬浮人物按滑块比例缩放肖像，徽标、计数图标和数字用固定尺寸绘制。为缩小人物时的状态区保留最低透明窗口空间，避免图标被裁剪。

## Scope

- 修改 `personal-secretary/ui/appearance.js`、`appearance.css`、`i18n.js`，把现有头像挂载为可操作按钮，加入侧栏切换与图标提示。
- 修改 `personal-secretary/pet/desktop_webview.py`，解耦人物肖像与状态图标的缩放。
- 更新 `docs/APPEARANCE.md`。
- 保持移动端横向导航、现有人物尺寸偏好范围和主题/语言行为。

## Work items

- [x] 1.1 实现可持久化的侧栏收起状态、头像双击切换、键盘操作和图标提示。
- [x] 1.2 让 Qt 人物肖像独立缩放，保持状态图标/计数可读且不裁切。
- [x] 1.3 更新外观说明，执行桌面 Python 编译检查并检查最终差异。

## Acceptance

- 桌面宽度下双击左上角秘书头像可在完整侧栏和图标侧栏间切换，刷新后恢复上次选择。
- 图标侧栏保留可识别导航图标、悬停提示和屏幕阅读器名称；键盘可聚焦头像并用 Enter/Space 切换。
- 窄屏下继续使用现有横向导航布局，不套用桌面图标侧栏。
- 25%–125% 人物缩放只改变肖像大小；状态灯、三类计数图标和数字不随人物变得过小或过大，也不会越出绘制区域。
- 外观文档描述与实现一致。

## Verification

- Review policy Self：改动局限于秘书 UI 的侧栏呈现和 Qt 肖像/状态绘制，没有改变服务 API 或数据模型。
- 编译检查通过：`powershell -NoProfile -ExecutionPolicy Bypass -File personal-secretary/scripts/compile-secretary-desktop.ps1`，输出 `Desktop compile-only passed`。
- 静态检查：查看 `git diff -- personal-secretary/ui/appearance.js personal-secretary/ui/appearance.css personal-secretary/ui/i18n.js personal-secretary/pet/desktop_webview.py docs/APPEARANCE.md`；确认折叠样式只保留导航图标，窄屏布局不改变列结构；Qt 绘制只对肖像应用人物比例，状态和计数绘制使用固定像素尺寸并限制在最小窗口范围内。
- 未运行测试或 Qt/Web GUI 验收；运行视觉仍受桌面合成器和屏幕尺寸影响。

## Sources

- 用户请求：侧栏可缩成图标并通过双击左上角人物头像切换；桌面人物缩放时邻近图标需保持清晰。

**Completed:** 2026-10-31
