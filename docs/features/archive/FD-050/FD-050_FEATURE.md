# FD-050: 桌面人物移除底部文字与底板

**Status:** Complete
**Revision:** 5
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户要求去掉桌面人物底部文字，观感突兀。底板及 pet_label 在 desktop.py 创建，desktop_pet.py 每帧更新文字，窗口为底栏多留 70px。

## Options and decision

直接移除底板、文字 item 及动画文字接线，缩小多余透明区域；保留表情气泡和详细窗口。不仅隐藏文字以免留下空底板。纯局部 UI 调整，无需独立设计审阅；最终独立实现审阅仍需。

## Solution

人物窗口高度改为插画高度+24，保留上边距、起伏及底部余量。PetAnimator 移除 label_item 参数及每帧文字更新，原状态到表情映射、动画/托盘生命周期不变。详细状态仍在原对话/工作窗口。

## Scope

仅 desktop.py 精确布局/接线、desktop_pet.py、desktop-assets/README.md 和本 FD 证据。主工作区 develop，desktop.py 其他未提交改动保留；index 从 HEAD 按本次精确替换构造，不暂存整个脏文件。无权限、服务、数据、依赖、网络、GUI运行或重启变化。

## Work items

- [x] 1.1 移除底栏及文字参数并收紧窗口，保留表情与生命周期。小/低，无依赖，证据为唯一构造调用和 Canvas 差异。
- [x] 1.2 更新说明、最小 Python 编译与静态检查，范围提交并独立审阅。小/低，依赖 1.1。

## TODO

1.1–1.2 已实施，暂存差异检查及当前/提交区 Python 编译通过；独立实现审阅 r1 通过，待 host 核对实际 receipt 后归档。

## Acceptance

1. 人物下方不创建文字或底板，不残留专用大空白（1.1）。
2. PetAnimator 构造调用与参数一致，不访问已删除 Canvas item；表情、起伏、右键开关、拖动与托盘不变（1.1）。
3. 附近文档说明状态移至详细窗口；编译/静态检查、未运行 GUI 和保留其他改动均如实报告（1.2）。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- Reviewer Session fd050-review-20261024-f4729a 已审阅 4f1dac8..e6bccda，三项验收静态支持，无阻塞发现；报告 docs/features/reviews/FD-050-review-r1.md 及 JSON。未重跑编译或运行测试/GUI，真实布局与交互未验证。通过事件以 CLI 实际 receipt 为准。

## Sources

- Issue: none
- 用户当前要求；desktop.py Canvas 构造，desktop_pet.py _draw，desktop-assets/README.md。
- Session: pet-caption-20261024-7e18c2；设计来源 FD-050-000002-design-requested。

**Completed:** 2026-10-24
