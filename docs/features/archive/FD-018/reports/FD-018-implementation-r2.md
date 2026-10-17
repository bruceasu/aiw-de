# FD-018 实现返修报告

<!-- aiw-data: FD-018-implementation-r2.json -->

- Worker session：fd018-worker-20261017-c94e7b。
- 真实来源事件：FD-018-000005-changes-requested，已领取。
- 原实现报告、设计与第 1 轮评审均保留；当前累计 1 次 changes-requested，未重置评审计数。

## R1 修正

控制面原先接受 codex-app-server + fast=true，但现有 Go chooseAIProfile 在该 runtime 过滤 fast。现采用共同支持集合：推荐的 codex-app-server 要求 fast=false；普通任务不变。筛选先应用显式 planner/model/effort，再过滤可执行组合；显式 planner 不能因为被过滤而回退到其他 Profile。不兼容原因现在说明 fast/effort 限制并列出实际组合。

修复提交：27f172597226e75ae60c01794e5c015f50fa74ac。独立 fd-test 从确认规则新增 A09，保留原 9 个测试；测试未提交 diff 的 SHA 和实际基线详见 r2 报告。执行原限定离线 Maven 命令：`D:/green/apache-maven-3.9.9/bin/mvn.cmd -o -f tests/fd018/pom.xml -Dtest=RecommendationAcceptanceTest,RecommendationPolicyTest test`，JAVA_HOME 为 D:/green/jdk-25.0.1+8，工作目录为已记录 FD-018 worktree。exit 0，10 tests、0 failures/errors/skipped，8.964 秒；命令编译当前源代码和测试，不产出发行包。

这是相关源码修正后唯一复核运行，不是新一轮无限测试。原 TDD/验收/评审失败与台账不删除或重置。未运行 Go、浏览器、PostgreSQL、真实并发、完整 Worker 协议或模型；其边界与原报告相同。当前修正已由静态实际选择器及新增独立回归支持，兼容缺陷反馈标为 resolved；新的独立实现 verdict 仍待 Reviewer，不能以测试通过替代。

请求独立 Reviewer 对本次修订和最终整体实现重新给出 verdict；通过后 host PM 继续原自动本地交付与归档授权。
