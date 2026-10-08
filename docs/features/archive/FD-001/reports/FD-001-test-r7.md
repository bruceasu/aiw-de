# FD-001 独立测试报告（revision 7）

<!-- aiw-data: FD-001-test-r7.json -->

## 结论

**Recommendation:** pass

本次**实际执行的36个行为断言全部通过**，失败0、未运行已准备断言0，没有额外基础设施/runner失败。独立覆盖为 **42/88 distinct场景，47.73%**；其余46个场景未覆盖，源码分支覆盖未测。此推荐仅证明执行用例通过，**不代表所有AC完成**；交PM决定是否记录低覆盖/分支不可用例外后进入独立审查。

Tester保持独立session `fd001-tester-20261004-7303009b`，真实CLI认领 `FD-001-000007-implementation-ready`；FD revision7 / digest `4d98e2a15e417e8c8986faa809dfe4aa4b5666a38b9b6aa3f8e8c926d92dc125`。未阅读功能实现Java/CSS/JS/模板源码；从FD、公开路由/指南独立推导黑盒。继承Worker证据不计Tester执行或覆盖。

## 实际命令与授权

cwd：`D:/03_projects/AI-tools/aiw-dev`。

| 命令（各仅一次） | 绑定授权 | 结果 |
|---|---|---|
| C:/green/python312/python.exe tests/fd001/runtime.py diagnose | [r7-v1](FD-001-test-authorization-r7-v1.md) | exit0；HTTP200/text/html，新page target最终/ui/login complete；0业务断言 |
| C:/green/python312/python.exe tests/fd001/runtime.py | [r7-v2](FD-001-test-authorization-r7-v2.md) | host核对诊断后执行，exit0；36业务断言passed |

用户新增「一次诊断及条件复验」预算，r7-v2包括host真实诊断证据/放行时间。没有诊断失败后继续，没有自动重复同等命令。代码位于 [runtime.py](/D:/03_projects/AI-tools/aiw-dev/tests/fd001/runtime.py)、[browser.mjs](/D:/03_projects/AI-tools/aiw-dev/tests/fd001/browser.mjs)、[diagnose.mjs](/D:/03_projects/AI-tools/aiw-dev/tests/fd001/diagnose.mjs)。副作用限临时SQLite、loopback JVM、Chrome临时profile及assigned tests/fd001证据；finally终止自身进程，清理前校验路径边界。本轮未生成cleanup错误日志。无下载/外部服务/生产DB/全库测试/发布构建。

## 本次证据与覆盖

[原始36结果](/D:/03_projects/AI-tools/aiw-dev/tests/fd001/evidence-r7/results.json)、各角色日志/navigation.json以及 [诊断原始证据](/D:/03_projects/AI-tools/aiw-dev/tests/fd001/evidence-r7/diagnose-v1/navigation-diagnosis.json)。JSON逐场景列出适用范围、status和已执行test_cases；一个断言可验证多个观察条件，例如四状态与阅读保护，场景数不等于测试断言数。

| 独立已执行范围 | 实际证据 |
|---|---|
| 四角色显示、新建Run与Worker操作入口 | 每角色3断言，12/12；不含完整审批/服务端权限矩阵 |
| 当前状态/身份/有限窗口 | Idle/Executing/DRAINING/OFFLINE、跨页面SVG、12/14 Run、8/16旧PENDING（另有110条较新决定记录）、缺失原因/暂停文字 |
| 活动与内容 | 最多20条、重复读取顺序稳定、requirement按文本处理、不编造ETA/百分比；不证明所有权威来源/排序键 |
| 刷新/编辑/阅读 | 实际5/8秒更新、展开/焦点/滚动保持、System编辑暂停及reset+blur恢复、真实cookie失效保留快照且停止 |
| 响应式与动效 | 390/768/1280/1440px无整页横溢、local motion持久、local on时系统reduce关闭脉冲、真实Tab可见focus |

四宽度截图：[390](/D:/03_projects/AI-tools/aiw-dev/tests/fd001/evidence-r7/tester-390.png)、[768](/D:/03_projects/AI-tools/aiw-dev/tests/fd001/evidence-r7/tester-768.png)、[1280](/D:/03_projects/AI-tools/aiw-dev/tests/fd001/evidence-r7/tester-1280.png)、[1440](/D:/03_projects/AI-tools/aiw-dev/tests/fd001/evidence-r7/tester-1440.png)。独立人工读取1440和390原图，看到深色面板、展开长摘要、四状态及窄屏单列；此视觉观察不额外增加自动业务断言或独立覆盖。

Requirements coverage = 42 / 88 = **47.73%**。Branch coverage = **not measured**：仅公开UI黑盒，没有覆盖率工具/插桩授权；不能从36/36推断源码覆盖。

## 历史失败保留

r4两轮真实失败与blocked报告 [FD-001-test-r4.md](FD-001-test-r4.md) 及对应v1/v2授权均保留。第一轮登录fetch失败/DB清理WinError32；第二轮四角色初始导航net::ERR_ABORTED，8条基础设施记录、0行为执行。r4报告曾由PM拒绝，不改写为通过。本轮明确创建新page target后导航诊断成功，能够支持本次测试，**尚不能据此证明前次ERR_ABORTED唯一根因**。

历史脚本依实际authored patch恢复，说明位于tests/fd001/evidence-r4/SCRIPT_VERSIONS.md；不冒称执行时已留不可变快照。本次脚本副本与证据留存于evidence-r7，旧r4不覆盖。

## 未覆盖与剩余风险

46项逐项见JSON，主要包括：真实空态、旧活跃Run不受recent窗口影响、5等待条件准确总数/样本及重叠、repair分母、Worker100截断、所有活动权威来源/缺失关联、审批角色入口/服务器授权/CSRF/状态竞态、单请求/10秒timeout/失败重试/30秒陈旧、真实hidden恢复、局部编辑、文本选择、非预期HTML/真实角色变化、全部既有页面/指南/阶段范围。

Firefox用户明确允许延期，未下载、未运行，兼容风险保留。低覆盖和分支不可用需要PM明确例外及风险；执行通过并不消除未覆盖项，也不授权本Tester继续追加运行。

