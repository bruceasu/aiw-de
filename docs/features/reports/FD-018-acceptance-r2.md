# FD-018 独立聚焦验收 r2

<!-- aiw-data: FD-018-acceptance-r2.json -->

结论：Reviewer R1 相关修复后的唯一聚焦复核运行通过，10 tests，0 failures/errors/skipped；不代表全 FD 验收或交付裁决。历史 r1 报告及日志保留。

## 版本与独立性

测试会话 `/root/fd018_acceptance`，来源 `FD-018-000005-changes-requested`。实现 HEAD `27f172597226e75ae60c01794e5c015f50fa74ac`，FD revision 3；执行前仅 `tests/fd018/acceptance-scenarios.md` 和 `RecommendationAcceptanceTest.java` 增加 14 行，未改生产代码或 Git/workflow。Host 执行期间不写工作树。

原 A01–A07 在访问实现前从已批准 FD/Issue 推导，A08 为既有 Plan 删除兼容回归。新增 A09 从 Host 交付的已确认共同支持集合规则独立推导：推荐的 codex-app-server profile 必须 fast=false；显式 fast=true 不得回退；非 fast app-server 仍可选。随后读取 CP RecommendationProfiles、FD D2 及 Go main.go:150–166，确认 Go chooseAIProfile 仅对 codex-cli 放行 fast。未把当前 CP 输出或 Worker 结论作 oracle。

A09 固定示例为 app-fast、app-normal 和 cli-normal；断言明确 app-fast 抛 IllegalArgumentException 且说明 fast=false，即使其他两个可用也不回退；app-normal 精确返回自身。该断言能识别忽略 fast、退回其他候选、误封整个 app-server 的错误实现。此为静态反例推演，没有执行 mutation。未改原 TDD 断言；新增 A09 在原验收类。

## 实际命令与结果

目录 `D:/03_projects/AI-tools/aiw-de/.wt/FD-018`，执行前已向 Host 报告身份、命令、范围、耗时与副作用。使用既有 JDK 25 与 Maven 3.9.9，离线运行：

```powershell
$env:JAVA_HOME='D:/green/jdk-25.0.1+8'
& 'D:/green/apache-maven-3.9.9/bin/mvn.cmd' -o -f tests/fd018/pom.xml '-Dtest=RecommendationAcceptanceTest,RecommendationPolicyTest' test *> tests/fd018/acceptance-r2.log
```

exit 0，总耗时 8.964 秒，结束 `2026-10-17T23:13:43+09:00`；验收类 9 tests，原 TDD 类 1 test，全部通过，无跳过。原始输出 `tests/fd018/acceptance-r2.log`；target/surefire-reports 是中间产物，未生成发行包。JDK native access/Mockito 动态 attach 警告保留，没有忽略失败。

本次重跑实际覆盖 A01 设置默认/审计/CAS，A02 冻结 guidance/选择，A03 离线拒绝与忙碌排队，A04 关闭 READY/保留 CLAIMED，A05 上下文幂等与 actor 隔离，A06 推荐租约到期 FAILED/普通重排/attempt EXPIRED/容量释放/旧 token 拒绝，A07 HTTP OPERATOR 保存 403，A08 Plan 删除外键，以及新增 A09 fast 兼容过滤。原 TDD 的未知 planner 不回退为重用证据，不计新增独立 oracle。

执行测试文件 SHA256：`C988797C9D3AA0C60296DA7051235C9941D7D893C5AC3531D80ADD220D064817`；场景 SHA256：`EF8304A1603E8C078B1C536888B384E62D6CCF64F39AEDA3FDB6F35397D62618`。POM 与原 TDD 未变，对应 r1 已记录 hashes。

预算连续：独立验收 effort 已执行 r1 一次；本次仅因相关生产修正执行一次授权复核，无意外失败，无新增重跑。此前 TDD setup 失败/有效 Red/Green 记录保留，不因会话或 Reviewer 轮次重置。执行后不继续扩大测试。

## 范围与未知

真实 SQLite 内存库、V1–V6 迁移、FK 开启、真实任务/attempt/Worker JDBC 与 TransactionTemplate；A02 catalog、A06 并发预算及无关 editor/dependency 边界使用 mock。MockMvc 使用真实 security，但未装 CSRF interceptor；事务由测试显式包裹，未验证 Spring proxy 配置。

未运行 Go runtime、真实 Worker/模型、完整 HTTP start/claim、浏览器 JS/视觉、PostgreSQL、并发锁交错、真实预算表、跨进程重启、旧环境变量启动对照和全仓库测试。A04 未覆盖全部 BLOCKED/RUNNING/完成结果组合；A06 未覆盖实际竞态或迟到 output HTTP。A09 验证 CP 精确过滤边界，Go 约束仅静态核对，不声称跨语言端到端已通过。r1 报告进一步保留各场景替换与边界说明；本轮原有 9 项实际重新执行。

实际运行没有失败，无待诊断 cause；独立 Reviewer 仍须判断整体实现及契约。此会话只新增测试/场景、日志和报告，不发 FD 事件，不作交付裁决。
