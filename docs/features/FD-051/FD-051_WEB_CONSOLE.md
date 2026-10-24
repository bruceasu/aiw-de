# FD-051: Web Console 增加日文界面语言

**Status:** Complete
**Revision:** 5
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户要求 i18n 增加日文。Web Console 当前 i18n.js 仅 EN→ZH，preferences.js 语言白名单仅 en/zh，fragments.html 只有两个选项。既有词条目录 430 项（含重复源键）；动态确认/步骤/刷新/Fleet 数量另用规则翻译。秘书 Web 与 Tk 没有接入此目录，不在本次扩展范围。

## Options and decision

扩展现有机制，增加内嵌 JA 词典及 en/zh/ja 白名单，不新增依赖、异步目录加载或服务端语言 API。补齐原有词条，并追加共享导航原中文/Goals 词条。未知词条保留原文，无中文二次回退或内容丢失。纯本地界面扩展，不需独立设计审阅；最终实现独立审阅必需。

## Solution

preferences 归一化接受 ja，默认仍 en，旧偏好及 aiw-locale 迁移保留。i18n 共用原始文本 WeakMap，切换可恢复 EN/ZH/JA，标题原文在首次 DOM 翻译前缓存，避免切换后锁定已翻译标题；document.lang 日文设 ja。日文动态规则保持全部数字和标识。语言选项显示本族文字日本語，保留 data-i18n-skip。通过原 preferences 事件、localStorage 和 storage 同步。更新用户说明并明确词条外原文保留。

## Scope

static/ui/i18n.js、preferences.js、templates/ui/fragments.html、docs/current-zh/15-WEB-CONSOLE.md 及本 FD。主工作区 develop，仅范围提交，保留其他页面模板和秘书/Worker 脏改动。不修改接口、权限、Worker 提示词、业务数据、日期格式或默认语言；不运行浏览器、网络、测试、服务、重启、部署或依赖下载。

## Work items

- [x] 1.1 补齐 JA 词典及动态规则，保持未知词条与原始文本。小/中，无依赖，证据为目录键覆盖及规则对照。
- [x] 1.2 接入偏好、语言选项、lang、存储和事件同步。小/低，依赖 1.1，证据为调用链与白名单。
- [x] 1.3 更新文档、一次静态覆盖/差异检查和两份 JS 语法编译，提交证据并独立审阅。小/低，依赖 1.2。

## TODO

1.1–1.3 已实施，428 个原目录唯一源键日文覆盖完整（含共享导航共 434 键），七类动态规则齐全；静态覆盖及两份 JS 语法编译通过。独立实现审阅 r1 通过，无阻塞发现；待 host 核对真实 receipt 后归档。

## Acceptance

1. 个性化偏好可选日本語，默认 en 和既有 zh 不变，ja 可保存/恢复、接收跨标签页事件，非法语言回 en（1.2）。
2. JA 覆盖既有唯一目录键及共享导航，七类动态数字提示保持数字，document.lang 为 ja（1.1–1.2）。
3. EN/ZH/JA 切换基于原文，已有 skip 区域不变；缺失词条保留原文，不改用户表单值/业务数据（1.1）。
4. 文档说明覆盖范围和未验证项；仅本次范围文件提交，保留其他任务改动（1.3）。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- 独立实现审阅：docs/features/reviews/FD-051-review-r1.md 及同名 JSON；Reviewer Session fd051-review-20261024-d7318f，来源 FD-051-000004-implementation-ready，覆盖 0392cd9..bc1c8e9。四项验收获静态支持，无阻塞发现，失败审阅数 0。未运行浏览器、测试或网络，实际语言切换和日文布局未实测；通过以 CLI receipt 为准。

## Sources

- Issue: none
- 用户当前要求；i18n.js、preferences.js、fragments.html 语言面板、docs/current-zh/15-WEB-CONSOLE.md。
- Session: japanese-ui-20261024-583a41；设计来源 FD-051-000002-design-requested。
