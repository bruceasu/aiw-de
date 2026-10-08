# FD-002 Worker 恢复报告：r8

<!-- aiw-data: FD-002-implementation-recovery-r8.json -->

## 来源与结论

Worker Session：`fd002-worker-20261005-88571fc5`；已领取真实来源事件
`FD-002-000008-test-rejected`。PM [r7 决定](FD-002-test-decision-r7-v1.md)
拒绝的是不完整测试报告，尚无 Reviewer 失败，累计仍为 0。
2.1–2.6 已实现，恢复修正和编译已完成；独立测试及验收待继续。

## 修正与静态证据

- `mission.js` 保存可见、仍连接 DOM 的图视口滚动位置，隐藏前保存，显示及
  局部刷新替换后恢复。静态调用路径包含 capture scroll、图开关和 direct-child
  MutationObserver；避免从隐藏或移除视口读取已丢失的位置。
- `console.css` 仅增加 `.mission-page .compact-metric` 的长词折行，覆盖长阶段和
 状态卡片文字；未改变共享轮询、权限或服务契约。
- 独立 Tester 修正 `tests/fd002/browser.mjs` 的章节 helper：从语义标题候选逐一
  关联公开内容，排除与当前 URL Run ID 相同的身份标题，无关联候选跳过，只有
  全部无关联才中断。仍要求适用的 table/details 等结构，拒绝整页文本兜底；
  候选、排除及关联依据写入 diagnostics.json。104 项库存和 96 项计划断言不变。
- FD 同步用户已批准的 Firefox 延期、三轮真实结果及 PM 拒绝决定；FD-001
  依赖链接更新为已归档路径。历史报告和原脚本快照保持。

原实现范围及权威字段见 [r5 报告](FD-002-implementation-r5.md)。本次是局部
界面与测试定位修正，不改变 schema、公开 API、Run 动作或持久化行为。

## 实际命令

JDK 21，`JAVA_HOME=C:/green/jdk-21.0.8`，
`MAVEN_OPTS=-Xms64m -Xmx512m -XX:ReservedCodeCacheSize=96m`：

```text
C:/green/apache-maven-3.9.9/bin/mvn.cmd -o -DskipTests compile
```

cwd：`D:/03_projects/AI-tools/aiw-dev`；exit 0；BUILD SUCCESS，1.136 秒。
Java classes 已是最新，复制 2+31 项资源；这不证明 JS 或浏览器交互通过。
原始输出：`.ai/fd/FD-002/compile-recovery-r8.txt`。
没有执行新测试、诊断、语法检查、覆盖率工具、下载或最终构建。

## 真实测试状态及下一交接

同一旧 r6 命令共运行三次：首次 24/104，其后两次均 36/104（34.62%）。
最新 36 项为不同场景的实际通过；0 个已执行业务失败，60 项计划内阻塞和
8 项未覆盖。初次 JVM 内存中断已修正；后两次章节关联中断不能算产品失败，
也不能将重复通过累加或把未运行写成通过。三轮原始证据、MD/JSON 报告均保留。

下一 implementation-ready 必须生成真实新 Tester 事件；原独立 Tester 再绑定
该事件、revision/digest 和自己的 Session，准备新精确命令授权。
此前初验、修正复验及用户单独批准的追加一次均已用完；新 handoff 不新增预算，
未获新明确答复不得第四次运行，也不得进入 Reviewer 代替测试。

## 残余风险

attempt、失败恢复、时间线、动作、刷新与四尺寸的验收仍不完整；新定位器和
图滚动/长阶段修正尚未运行。Firefox 两项按用户决定延期，仍在库存分母中；
分支覆盖未测，原 TimelineService 的全量读取成本未测。FD-001 已独立审查归档，
其结果不能替代 FD-002 的测试。无需再请求 Firefox 决定。
