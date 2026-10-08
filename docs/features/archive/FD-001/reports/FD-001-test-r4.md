# FD-001 独立测试报告（revision 4）

<!-- aiw-data: FD-001-test-r4.json -->

## 结论

**Recommendation:** blocked

独立 Tester 已通过真实 CLI 认领 event 4，session `fd001-tester-20261004-7303009b`。仅从 FD、公开路由和现有指南推导黑盒场景，没有阅读功能 Java/CSS/JS/模板实现。两个正式授权命令均在浏览器登录基础设施阶段失败，**业务断言执行 0、通过 0、失败 0，准备 36 个断言但未运行**。前期交流估算37个，最终按唯一 test case ID 清点为36个。不能将这解释为产品通过或产品业务失败。

本报告交 PM 做风险与后续决策；没有自动追加第三次运行。

## 绑定与授权

- 来源：`FD-001-000004-implementation-ready`；FD revision 4。
- digest：`2afb5c00dbfab6f9e7060aa8b71e08188cd4de190d3024e33271e113a4c849e5`。
- cwd：`D:/03_projects/AI-tools/aiw-dev`。
- 两次实际命令均为 `C:/green/python312/python.exe tests/fd001/runtime.py`。
- 授权分别为 [v1](FD-001-test-authorization-r4-v1.md)、[v2](FD-001-test-authorization-r4-v2.md)，同名 JSON 绑定相同事件、revision、digest、Tester session；v2仅批准相关基础设施修正后一次重试。

## 实际执行记录

| 次数 | 退出码 | 真实结果 | 业务断言 |
|---|---|---|---|
| 1 | 1 | ADMIN 登录 fetch 失败，随后 Windows 临时 DB 清理 WinError32 遮蔽原异常；原始 browser JSON 保存 | 0 |
| 2 | 1 | 四角色 Page.navigate('/ui/login') 全部 net::ERR_ABORTED，isDownload=false；4 infrastructure + 4 runner 条目 | 0 |

第二轮明确关闭 seed 的 SQLite 连接，使用原生登录表单、真实 URL/readyState 有界检查与导航诊断，保持前次失败。第一轮证据 [attempt1](/D:/03_projects/AI-tools/aiw-dev/tests/fd001/evidence-r4/attempt1/ADMIN-results.json)，第二轮 [原始结果](/D:/03_projects/AI-tools/aiw-dev/tests/fd001/evidence-r4/results.json) 与各角色 navigation.json。launcher 原 stdout 的 executed=8 是基础设施条目数，**本正式报告将其与行为测试分开，不能视为8个产品行为失败**。

副作用限于 loopback 临时 JVM/SQLite、独立 Chrome profile 和 tests/fd001/evidence-r4。finally 终止自行启动的进程；首轮临时 DB 清理失败保留为环境残留风险。没有下载、访问外部服务、写生产 DB、复跑 Maven、修改业务源码或 FD。

## 覆盖率与细分库存

- 适用需求场景：88，独立已覆盖：0；Requirements coverage：**0%**。
- JSON `data.scenarios` 将同一AC下不同可观察条件分开：准备断言关联场景 42 个 blocked，未编写独立断言场景 46 个 uncovered。没有把宽泛AC标题当一个case。
- Branch coverage：**not measured**。仅公开UI黑盒，无插桩/coverage工具授权，且命令在业务断言前失败；无法推断分支覆盖。
- Prepared/unrun behavior tests：36，executed/pass/fail：0/0/0。一个断言可对应多个观察场景，例如四状态、阅读保护；这不改变执行数。

完整逐项库存见同名 JSON。角色显示/New Run/Worker入口只覆盖所准备的细分断言；审批入口矩阵、服务端权限/CSRF/竞态均明确未覆盖。等待分类文本只验证标题可读，五条件准确总量/样本与重叠未覆盖。正常周期、输入/重置、四宽度、系统减少动效、真实cookie失效已准备但未执行；hidden、角色变化、超时、单请求、30秒陈旧、文本选择、完整事件权威来源未覆盖。

## 未运行与残余风险

Firefox 用户明确允许延期，本机未下载，不能算通过。Worker既有四IT通过和浏览器证据仅作继承背景，本Tester没有复跑，也未将其计入独立覆盖。无成功登录页后截图，无法提供独立视觉通过结论。要求覆盖率为0，独立验收仍有实质缺口；需要PM明确决策/恢复措施，再另行授权验证。此阶段不建议直接进入通过审查或归档。
