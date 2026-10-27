# FD-061: Move Control Plane theme controls into Personalization

**Status:** Complete
**Revision:** 2
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

用户要求将 Control Plane 顶部 HUD/RPG/自动和时间设置入口统一放到侧栏“个性化”中。

## Options and decision

复用现有个性化弹窗及 AiwAppearance 的偏好校验/保存逻辑，内联主题和时间控件；不再打开第二个主题对话框。保留 aiw.appearance.v1 已有设置及秘书入口。

## Solution

appearance.mount 在 Control Plane 中将主题组插入 preferences body，切换立即生效，自动模式启用时间控件，错误留在弹窗说明；恢复默认同时重置主题。没有个性化弹窗的登录页面只应用偏好，不放浮动设置。秘书仍走既有 mount 路径。新增中日文。

## Scope

仅前端主题设置位置、文案与帮助，不改变主题存储结构、时间算法、业务状态或服务。静态 JS 更新可直接发布到现有 resources 位置，不需要中断正在执行的业务。

## Work items

- [x] 1.1 Control Plane 主题及时间控件内联个性化，移除顶部入口并保留原偏好。
- [x] 1.2 新增中日文和说明，准备静态检查与资源编译复制。

## Acceptance

个性化内可选 HUD/RPG/自动，自动时间可修改并校验；顶部不重复显示入口，无嵌套对话框。刷新保留偏好，恢复默认包含主题；秘书原入口不变。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- Self：仅前端设置位置迁移，复用既有存储与校验，无业务、权限或新持久化契约。静态检查 Control Plane/秘书分支、自动时间、偏好保留、恢复默认和跨标签同步。
- node --check appearance.js、console-translations.js 成功；离线 Maven compile 成功并复制静态资源，Java 无须重编译。认证只读 HTTP 核对实际服务返回新主题脚本及翻译成功，无须重启服务，不中断任务。
- 未新增或运行测试，未做真实浏览器交互/视觉验收。

## Sources

- 用户要求 Control Plane 主题设置也放到个性化中。

**Completed:** 2026-10-27
