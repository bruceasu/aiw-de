# FD-049: 桌面秘书轻量动画与真实状态表情

**Status:** Design
**Revision:** 2
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户要求桌面宠物简单支持动画和表情，并正确表达真实状态。目前 Tk Canvas 显示单张 secretary.png，文字把 enabled 与日常暂停混为一谈；请求开始及错误只替换状态行。人物可拖动、托盘隐藏，缺少动画控制。

## Options and decision

选择现有 Tk Canvas 上轻微起伏/点头及独立绘制的可眨眼表情气泡，不引入 Live2D、Pillow、素材生成或在线依赖，不改变原插画面部。新增独立 desktop_pet.py 负责状态投影和单个 UI 定时器，桌面接线。无公共接口、持久化、权限或工作调度变化，无需独立设计审阅；最终独立实现审阅必需。

## Solution

状态以最近成功读取的 assistant/modelConfigured/work/messages/decisions 为准：未连接/请求结果待核对优先；暂停与待设置准确显示；待确认、阻塞、实际运行、等待回复、已安排和待机区分。轮询不冒充工作开始；提交成功不冒充任务完成。仅对已观察任务从非 DONE 到 DONE 或消息从 PENDING 到 DONE 显示短时“工作已完成”或“有新回复”，首次读取历史 DONE 不庆祝；错误后重新连接不推断离线期间的完成事件。动画可右键关闭，隐藏/退出取消定时器，恢复不重复创建；只在 Tk 主线程操作 Canvas，不新开线程。

## Scope

desktop_pet.py、desktop.py 中精确动画接线修改、desktop-assets/README.md 与本 FD 证据。主工作区 develop 仅提交本 FD。desktop.py/README 等已有其他任务改动，必须保留；保存本次前后差异并只将本次 patch 应用到 Git index，不整文件暂存，不改脏 README。生命周期风险为残留 after 回调或关闭后访问 Tk；用单一 timer ID、隐藏/关闭先取消和 closed 标记处理。无 GUI/服务/模型/网络/测试/依赖下载/重启/打包授权。

## Work items

- [ ] 1.1 实现纯状态投影及完成事件识别。小/中，无依赖，证据为真实字段、优先级及首次读取/错误分支。
- [ ] 1.2 实现 Tk 轻动画、表情气泡及桌面生命周期/请求接线。小/中，依赖 1.1，证据为单 timer、开关、隐藏/关闭与轮询区分。
- [ ] 1.3 更新资产说明、完成最小 Python 编译及静态检查，范围提交和独立审阅。小/低，依赖 1.2。

## TODO

实施 1.1–1.3，保留其他任务脏改动。

## Acceptance

1. 人物有轻微起伏，表情气泡能眨眼并区分待机、等待、运行、完成、暂停、待确认与异常，均配中文文字（1.1–1.2）。
2. 任务完成仅由已观察的真实 DONE 转变触发；回复仅称有新回复；首次历史记录、仅请求成功、未知/失败不展示任务完成；后台轮询不伪装工作（1.1）。
3. 日常暂停取 assistant.paused 而非专业平台 enabled；连接失败或结果未知时不继续展示旧的成功状态（1.1–1.2）。
4. 右键可关闭动画；隐藏托盘停止回调、显示恢复单一回调、退出先取消。保留拖动、置顶、点击对话及后台服务（1.2）。
5. 不新增依赖或重画原人物，不提交其他工作改动；语法/静态检查与未实测事项如实记录（1.3）。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.

## Sources

- Issue: none
- 用户当前授权；desktop.py 的 Canvas/update_state/run/drain/poll_state/operate/show_pet/hide_pet/close；desktop-assets/README.md 与秘书状态投影。
- Session: desktop-expression-20261024-a19d05；设计来源 FD-049-000002-design-requested。
