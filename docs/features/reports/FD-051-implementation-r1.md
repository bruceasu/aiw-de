# FD-051 实现报告 r1

<!-- aiw-data: FD-051-implementation-r1.json -->

Worker Session：japanese-ui-20261024-583a41；来源 FD-051-000003-design-ready；基线 0392cd9。

## 实现与覆盖

i18n.js 增加 Japanese 字典，覆盖原目录 430 条声明中的 428 个唯一源键，另补共享导航、未登录及语言名称，总计 434 键。两条重复源键译文一致。保留产品标识、路径、错误代码及数值约束；授权、确认和执行语义不变。七类动态提示（确认所选步骤、跳转步骤、单步骤、刷新倒计时、注册 Worker、活动租约、READY 任务数量）均有日文规则，保留捕获的数字。

preferences.js 和 i18n.js 的白名单均支持 en/zh/ja，默认仍 en；fragments.html 添加日本語选项，沿用原跳过翻译的语言名称。ja 设置 document.lang=ja，原偏好保存、legacy aiw-locale 迁移、storage 标签页事件、preferences-changed、DOM 观察及属性翻译路径保留。非法语言回 en；缺失词条保留原文，用 Object.hasOwn 避免把继承属性当翻译。标题原文移到 DOM 翻译前缓存，修正先缓存已翻译标题导致语言切换无法恢复的既有问题。

文档更新选项、覆盖范围和秘书 Web/Tk 尚未接入的边界。未更改这些私人 UI 或业务内容。

## 实际检查及限制

git diff --check 通过；同一静态批次读取统计及 i18n 末段调用链，Python 只读解析声明和 JSON 字典确认 428 个源键无缺失、434 个日文键无空值（不执行 JS）。node --check src/main/resources/static/ui/i18n.js 和 node --check src/main/resources/static/ui/preferences.js 均通过，无输出错误。

静态 diff/stat 命令未限定路径，因此输出也列出了用户其他任务的既有差异；这些差异不属于本 FD 检查结论，不会暂存或提交。本次仅四个产品/文档文件和 FD 证据。

未运行测试、浏览器、HTTP、服务、网络、依赖下载、重启、打包或部署；实际切换、跨标签页同步、日文布局及标题显示未实测。目录外的文本保留原语言，不声称全部业务页面或秘书 UI 已完整日文化。

## 验收静态映射

1. preferences.normalize 与原 localStorage、迁移、storage 路径；共享 selector 和 locale 归一化。
2. 词典静态全覆盖、七类规则及 document.lang。
3. 原始文本 WeakMap、修正后的标题缓存、skip 与原文回退；不写入表单业务值。
4. 更新说明与真实检查/未运行项，范围提交保留其他改动。
