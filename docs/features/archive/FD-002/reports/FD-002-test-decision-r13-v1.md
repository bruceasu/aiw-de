# FD-002 PM 测试决定：r13 v1

<!-- aiw-data: FD-002-test-decision-r13-v1.json -->

## 决定

**rejected**，返回 Worker 处理具体可修复的验证问题，尚不交接 Reviewer。
依据 [独立报告](FD-002-test-report-r12-attempt2.md)、本轮原始汇总和实际 Category 失败截图：
96项执行、93通过、3失败、8未运行，exit1，需求覆盖89.42%，分支覆盖not measured。
不累加前轮通过，不将观察器/截图异常改写为通过，不授失败或覆盖例外。

## 原始失败与静态处理

类别筛选及刷新过滤在定位Category控件时抛错，尚未实际选择非空类别、验证缩小集合或刷新保持。
截图中可见Category、Reset filters和7/7条目，不能将零helper候选写成产品缺失。
当前helper将嵌套表单控件的文本并入label，存在词边界失配；应修正语义标签读取，
并保留所有公开select的原始label/option/可见性及排除原因，不放宽验收断言。
1440px场景先完成宽度断言，但dense第21段和补充失败截图第2段capture失败，
long-ID1440未到达，矩阵仍未完成。Chrome CopyOutputResultSender/GPU退出为关联证据，
不能认定唯一根因；应静态准备真正按可见视口逐屏滚动采集并恢复滚动位置，完整纵向内容和矩阵保持。

## 真实绑定

PM消费 `FD-002-000013-test-report-ready`，revision13，digest
`8f2c6ffdc2bc37b6ba9f127e31fb9990ccb5eb19397f32cb3cd8b0d83e14603c`。
该事件指向实际tested implementation12，Tester为原独立session。
PM不领取Tester/Reviewer角色，不修改冻结FD或合成receipt；以本决定真实emit test-rejected。

## 授权与风险

本次人类批准 `approved:conversation:call_91WOuZ4AVFLiYni2IuZLDKaE` 的初验和条件重试均已执行。
下一次命令需完成具体静态修正、绑定真实新Tester事件并重新获得明确运行预算。
此决定不授权测试、浏览器、诊断或扩大范围。原七次日志/快照/失败及201段截图保持。
Firefox既有独立人类延期不变，其两项与其余六项缺口仍在104分母；分支未测。
Timeline全量读取成本、生产GPU性能和系统commit充足均未验证。

PM：`fd002-pm-host-20261005`；决定时间：2026-10-04T23:49:40+00:00。
