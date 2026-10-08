# FD-002 Worker 恢复交接：r14

<!-- aiw-data: FD-002-implementation-recovery-r14.json -->

## 当前结果与真实来源

Worker：`fd002-worker-20261005-88571fc5`，已真实领取
`FD-002-000014-test-rejected`，依据 [PM决定](FD-002-test-decision-r13-v1.md)继续恢复。
[最新独立报告](FD-002-test-report-r12-attempt2.md)实际96项执行、93通过、3失败、8未运行，
覆盖89.42%，分支not measured；原七轮快照和失败全部保持，不累加历史成功。
3项失败不能接受，Reviewer尚未启动，失败审查次数0。

## 静态依据及具体恢复

最新实际已通过完整300 attempt ID集合和时间线文本筛选，以及原DAG/归属/历史窗口、
控制/CSRF/状态竞争、三个宽度和阅读/编辑保持等对应断言。
两个Category case在观察器定位控件时抛错，未完成非空类别/缩小集合/刷新保持。
实际截图可见Category与7/7条目；原helper直接拼接label.textContent会包含嵌套select选项，
与词边界匹配不相容。独立Tester准备从语义label中排除控件文本，并记录全部公开select候选、
可见性、label/options及排除依据；验收断言保持严格。

1440px布局前置断言完成，同case随后发生截图capture错误；dense仅前20段，long-ID未到达。
Chrome CopyOutputResultSender/GPU错误是关联证据，不确认唯一根因。
独立Tester准备逐屏滚动、仅截当前真实视口、逐段持久化全部纵向内容，并finally恢复原scroll。
全部四宽度、dense/long-ID、角色/失败截图矩阵和库存保持；未获得修正后运行证据。

本次更新FD验证事实与恢复交接，测试脚本仍由独立Tester在指定根tests路径静态准备。
生产实现的原范围与r5/r8/r11来源可供后续Reviewer完整审查。
最近源码compile-only仍为r11：JDK21离线 `mvn.cmd -o -DskipTests compile`，BUILD SUCCESS0.802秒。
本次没有新生产源码修改，未重复编译或追加测试/浏览器/诊断/语法检查/下载/最终构建。

## 下一阶段

交接真实新的implementation-ready给原独立Tester；新FD摘要须在领取后核对并绑定实际脚本。
本轮人类批准的初验与相关重试已经全部使用；在具体修正、宿主静态审阅、精确提案与提交完成后，
再请求绑定新事件的一次运行及相关修正重试预算。此交接不授予运行权限。
原8项缺口、Firefox独立延期、分支未测及Timeline全量读取成本保持为残余风险；
资源路径修正不能证明生产GPU/性能或系统commit充足。
