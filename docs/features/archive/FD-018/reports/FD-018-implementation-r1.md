# FD-018 实现报告

<!-- aiw-data: FD-018-implementation-r1.json -->

- Worker session：fd018-worker-20261017-c94e7b。
- 来源事件：FD-018-000003-design-ready；真实已领取。
- 基线：设计提交 2959fb6；实现提交 435bcbae，兼容修订与独立用例提交 9108cf45。最后的恢复 copy 条件和证据在本报告交接提交中。

## 实现结果与原因

推荐改为数据库系统级设置，默认启用，旧环境变量不再读取。管理员从 System 设置保存并查看审计；设置 revision 条件防止覆盖。配置保存与新请求先锁固定单例行，关闭后阻止新请求并以任务状态 CAS 取消排队分析，领取先赢则继续，保持运行与结果读取边界。

用户选择的 planner 是现有 AI Profile；model/effort 是精确约束，不能合成未批准组合。请求冻结完整候选内容及 catalog version。领取、现有 Protocol guidance 与 start 的 RecommendationTaskPolicy 使用同一快照，普通任务仍按既有 policy。无需修改 Worker wire 或启用 aiPreferences 回退。静态核对三套现有选择器后补齐 codex-cli/codex-sdk/codex-app-server/openai-api/openai-agents Runtime；实际跨进程调用未执行。

新增 actor-owned 服务端编辑上下文、请求关联、Plan 映射与恢复接口。URL context、近期列表及 Plan 分析链接可恢复已提交 goal/version、任务状态和结果；未提交编辑不保证恢复。已保存上下文不能重发分析，人工应用再保存明确创建新草稿，不改写原 Plan。新 Plan 与同一分析的映射可独立查询，不依赖近期列表上限。多标签页用 context revision 拒绝过期请求，requestKey 重复核对内容后幂等返回。历史未关联任务仍按原 ID 与 actor 读取。

推荐过期租约在原恢复事务中从活动状态 CAS 到 FAILED，attempt 转 EXPIRED，释放并发预算及 Worker 容量一次，并记审计；不自动回 READY。普通任务维持重排队。旧 token 不能完成或续租，结果读取不把失败状态解释为可应用。租约 60 秒加默认恢复间隔 5 秒不是停机/积压时的绝对墙钟上限；原模型调用也未必已停止，文档和 UI 提示手动重试费用风险。

V6 仅新增表。独立验收执行前发现 context 外键可能阻止已有 Plan 到期删除，已改为 SET NULL、映射 CASCADE，并加入 A08；不会删除推荐历史。回滚保留新增审计表，旧版本配置语义需重新核对。

## 已运行与版本

所有 Maven 命令均使用 `JAVA_HOME=D:/green/jdk-25.0.1+8`、`D:/green/apache-maven-3.9.9/bin/mvn.cmd`、隔离工作树，离线 `-o`，不生成发行包。

1. TDD：`-o -f tests/fd018/pom.xml -Dtest=RecommendationPolicyTest test`。首次 POM 错误是 jar 不能作 parent，未进入目标测试；修正为独立继承同一 Spring parent 的聚焦 POM后，相同断言观察到“不抛出未知 planner 错误”的 target Red；实现精确筛选后 1 test Green。原始输出为 tests/fd018/tdd-red.log、tdd-red-corrected.log、tdd-green.log。没有把准备错误记为 Red。
2. `-o -DskipTests compile`：成功，223 个 Java 源文件；日志 tests/fd018/compile.log。其后少量 Java 调整由下一项当前版本的真实编译覆盖。
3. 独立 fd-test 在 HEAD 9108cf4532a46894386530ed0acf2035a6e32180 执行 `-o -f tests/fd018/pom.xml -Dtest=RecommendationAcceptanceTest,RecommendationPolicyTest test`：成功，当前 223 Java 源文件及 2 测试类编译，9 tests、0 failures/errors/skipped，9.490 秒，无重跑。详见独立报告与 tests/fd018/acceptance-r1.log，8 项新场景加 1 项明确重用 TDD。
4. 静态 `git diff --check` 无空白错误；核对配置移除、JDBC 状态条件、HTTP 权限/现有 CSRF interceptor、三套选择器和 JS 恢复/撤回路径。部分定位命令曾使用不存在路径或 PowerShell 路径通配符失败，随后仅纠正具体读取；这些不是运行验证。

最后在 JS 将 copy 模板加载限制为上下文尚无已提交 goal，避免刷新恢复后又被原 copy 参数覆盖。Java/SQL 测试不因这个 JS 条件变动失效；本报告没有声称该交互经过浏览器执行。

## 证据边界与剩余风险

独立测试真实使用 SQLite JDBC、事务、task/attempt/worker 状态，预算接口仍为 mock；冻结选择未覆盖完整 HTTP claim/start 往返；权限 MockMvc 未装载全局 CSRF interceptor。已静态核对现有 interceptor 覆盖全部新增 POST。真实 PostgreSQL、并发压力、浏览器渲染/交互、跨语言实际 Worker、真实模型和费用均未执行。静态条件及内存单机测试不能冒充这些运行环境通过。

未运行全仓库测试、最终构建、formatter/linter/typecheck、下载或网络调用；未发布、push 或部署。工作区只含本 FD 的实现、用例及证据，无对已有 Worker Skill 提取内容的额外改动。独立 Reviewer 的 verdict、合并和归档仍待后续真实执行。
