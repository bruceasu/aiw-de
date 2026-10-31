# FD-069: Qt 窗口标题栏改为半透明

**Status:** Complete
**Revision:** 4
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

Qt 桌面窗口的标题栏与工作区共享接近不透明的底色；在透明顶层窗口上，标题栏容易与桌面背景融在一起，用户会误以为背后的窗口处于前景。

## Options and decision

- 继续使用工作区的 240/255 透明度：两者难以区分，不能明确表达用户要求。
- 为标题栏设置独立透明度：选择 200/255（约 78% 不透明），保留现有主题渐变和强调色边框，同时与工作区底色区分。
- 关闭整个窗口透明：会改变人物、WebView 和工作区的既有透明效果，范围超出请求。

## Solution

仅在 Qt 外壳的透明模式下，为标题栏渐变单独生成主题色并应用 200/255 alpha。工作区、底栏、标签文字、按钮及 `--opaque` 回退行为保持现状。更新外观说明，明确各区域透明度。

## Scope

- 修改 `personal-secretary/pet/desktop_webview.py` 中标题栏渐变的颜色生成。
- 更新 `docs/APPEARANCE.md` 中 Qt 透明度说明。
- 不改变窗口拖动、主题同步、WebView、托盘或人物的行为。

## Work items

- [x] 1.1 为标题栏引入独立 alpha，并更新外观文档。
- [x] 1.2 执行桌面 Python 编译检查并静态检查差异。

## Acceptance

- 透明模式下，标题栏渐变使用主题色且 alpha 为 200/255，明显比工作区的 240/255 更透。
- `--opaque` 下标题栏继续使用不透明主题色。
- 标题文字、按钮、边框和窗口交互不因本次改动而改变。
- 外观文档准确说明标题栏和工作区的透明度差异。

## Verification

- Review policy Self：修改局限于 Qt 标题栏的颜色透明度和对应说明，不涉及接口、权限、持久化或跨模块影响。
- 编译检查通过：`powershell -NoProfile -ExecutionPolicy Bypass -File personal-secretary/scripts/compile-secretary-desktop.ps1`，输出 `Desktop compile-only passed`。
- 静态检查：查看 `git diff -- personal-secretary/pet/desktop_webview.py docs/APPEARANCE.md`；确认透明模式使用独立 200 alpha，`--opaque` 仍返回主题原色，工作区/底栏保持既有 240 alpha，文档同步说明。
- 未运行 Qt GUI 或桌面视觉验证；透明度观感仍取决于系统桌面合成器。

## Sources

- 用户请求：Qt 标题栏使用半透明，避免与背后窗口混淆。

**Completed:** 2026-10-31
