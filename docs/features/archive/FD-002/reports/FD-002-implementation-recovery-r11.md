# FD-002 Worker 恢复报告：r11

<!-- aiw-data: FD-002-implementation-recovery-r11.json -->

## 来源与结论

Worker Session：`fd002-worker-20261005-88571fc5`；真实来源事件
`FD-002-000011-test-rejected` 已由该 Worker 领取。
[PM r10 决定](FD-002-test-decision-r10-v1.md)拒绝有失败且不完整的
[r9 attempt2](FD-002-test-report-r9-attempt2.md)。Reviewer 尚未启动，失败累计 0。

最新实际测试为 78 项执行、74 通过、4 失败、26 未运行，需求覆盖 71.15%，
分支未测。五次历史运行及四个失败均保留，不将修正准备计作测试通过。

## 界面修正

失败截图显示 Timeline 总量 7/7，却显示 0/0 匹配并隐藏所有条目。
静态调用链为 `run-detail.html` 条目标记 → `mission.js` 的
`[data-timeline-entry]` 查询 → 类别/文本筛选 → 日期组可见性与匹配计数。
将条目标记从空 Thymeleaf 表达式改为静态 `data-timeline-entry` 属性，
`data-category` 继续由真实保存类别生成。这个局部修正使每个条目具备明确标记，
不改变窗口、来源、数据权限、schema 或公开 API。

attempt 计数的原观察器对整段 DOM textContent 使用 word boundary；
相邻元素文本可拼为 `a301Attempt`，无法据此证明完整 300 个 ID 集合。
后续独立观察器将逐行读取完整 ID 并严格比较 a2–a301；原失败结果保持。
筛选观察也须核对真实条目和祖先可见性，保留非空、正文匹配和类别缩小断言。

## 运行环境及测试准备

r9 初验因 C2 native 内存分配失败中断，修正后的 JVM 已实际运行到 78 项断言。
但 Node 后续以 Committing semi space failed/exit134 中断；其日志 heap 约 10–16 MB，
不能声称 JavaScript 大堆容量已耗尽。本机系统提交资源不足仍是现实限制。

独立 Tester 正在 assigned tests/fd002 路径准备完整 ID/可见性诊断，限制 Node
semi space/old space，并为长页面显式分段截图；所有页面和四宽度仍须覆盖。
这些仅为后续命令准备，未新增运行，不能证明生产性能或本轮修正通过。

## 实际编译命令

```text
C:/green/apache-maven-3.9.9/bin/mvn.cmd -o -DskipTests compile
```

cwd：`D:/03_projects/AI-tools/aiw-dev`；JDK 21；exit 0；BUILD SUCCESS，0.802 秒。
Java classes 已是最新，复制 2+31 项资源。证据
`.ai/fd/FD-002/compile-recovery-r11.txt`。临时编译 JVM 使用
`-Xms32m -Xmx256m -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1
-XX:+UseSerialGC -XX:ReservedCodeCacheSize=64m` 限制资源。
没有执行新测试、模板渲染、浏览器、诊断、覆盖工具或最终构建。

## 交接与风险

用户此次批准的一次复验与一次相关修正重试均已实际使用。后续 Tester 必须领取
真实新 implementation-ready 事件，绑定新 revision/digest，先准备和静态审阅
命令，再取得新增明确运行预算。不得沿用 r9 旧授权或重写旧失败。

当前四个失败的修正尚无复验证据；18 项计划内未到达和 8 项原未覆盖仍非通过。
Firefox 两项继续按已批准政策延期；分支未测、原 Timeline 全量读取成本未测。
系统提交资源不足仍可能中断后续命令。FD-001 已完成独立审查与归档，不能代替
FD-002 的验收。尚未取得 Reviewer pass 或交付 Complete。
