# FD-001 独立修复复验（revision 11）

<!-- aiw-data: FD-001-test-r11.json -->

## 结论

当前修正复验 **4个行为断言全部通过**，0失败。覆盖本轮5个细分观察点；完整去重库存 **5/91=5.49%**，86场景未在本revision运行，源码branch未测。**Recommendation: pass**仅限三项Reviewer修复的实际黑盒复验，需PM显式继承/低覆盖例外，不能称整个FD测试全覆盖。

Tester `fd001-tester-20261004-7303009b` 独立于Worker/Reviewer；真实认领 `FD-001-000011-implementation-ready`。revision11，digest `bf8bbae3a950348233a32faf39393bbd4c8cd7e07aeff14464833dbd0fdb6377`。执行前核对owner/事件/摘要；没有修改业务源码或FD，没有阅读实现源码来编写case。

## 实际运行与授权

cwd `D:/03_projects/AI-tools/aiw-dev`；两次精确外层命令都是 `C:/green/python312/python.exe tests/fd001/runtime.py review-fixes`。

| 次数 | 正式授权 | 实际结果 |
|---|---|---|
| 初验 | [r11-v1](FD-001-test-authorization-r11-v1.md) | exit1，4业务断言：2pass/2fail；另1runner失败不计业务 |
| 相关修正复验 | [r11-v2](FD-001-test-authorization-r11-v2.md) | exit0，4业务断言：4pass/0fail，无runner失败，约5.65s |

用户本轮明确请求FD-001/002定向test/review。初验结果和原脚本保存在 [initial](/D:/03_projects/AI-tools/aiw-dev/tests/fd001/evidence-review1/initial/results.json)，未改写。相关脚本修正只将两处1200ms等待改为最多12秒观察真正更新，同时给测试JVM设置小heap和自身错误日志路径；原展开、焦点、选区、实际更新断言全部保留。复验前脚本副本保存在evidence-review2/scripts。本轮预算已耗尽，未追加其他测试/Maven/下载。

当前计数4/4是**最终有效执行的4个唯一case**；两轮合计8次业务断言执行，初验2fail单独留存而非隐去。本轮初验未观察到实际刷新，不足判产品故障；相关修正后通过，但不能证明初验唯一根因。r4历史基础设施失败、r7历史通过均保持原记录。

## 三项修复的公开行为证据

| case | 实际观察 |
|---|---|
| waiting-count-samples | No matching、Worker capacity、Fleet admission总数0且样例0；Run admission和Policy各1/1。只验证0/1分支，不声称5条截断分支通过 |
| waiting-overlap | 同一READY task同时paused与能力不匹配，仅在Policy主分类出现，同时样例显示Policy blocked/No matching capability |
| waiting-open-count-change | 焦点先移至motion控件；新增可准入Run08的未匹配READY后No matching从0→1，刷新实际应用，组仍open、外部焦点保留；观察158ms |
| selection-multi-region | 13,149字符选区跨runs/attention/workers/waiting/activity五区；未选metrics元素实际替换，选区anchor/focus节点、offset、全文及中间workers元素保持；观察165ms |

[当前原始结果](/D:/03_projects/AI-tools/aiw-dev/tests/fd001/evidence-review2/results.json)、[具体观察/时序](/D:/03_projects/AI-tools/aiw-dev/tests/fd001/evidence-review2/review-diagnostics.json)、[截图](/D:/03_projects/AI-tools/aiw-dev/tests/fd001/evidence-review2/review-fixes.png)。一个case可验证多个细分观察点，不据此扩大执行断言数。

仅用本地ADMIN临时SQLite/JVM和独立Chrome，绑定loopback；helper严格校验临时DB边界，只新增一条fixture READY。finally结束自身进程并校验临时profile清理路径，无生产DB、外部请求、打包或部署。

## 去重库存与历史支持

同名JSON包括完整91项逐场景库存：原S053直接映射Policy重叠、S078映射跨区选区，不重复新增；S030明确历史Run摘要展开，另加S089等待summary数字变化展开；S048–S052保留达到/超过5条的准确总量/截断分支未覆盖，0/1已观测分支拆为S090/S091。**88+3=91**，本轮passed为S053/S078/S089/S090/S091，其余均不计当前覆盖。覆盖率为5/91=5.49%，不是针对本次case选小分母宣称100%。

[r7报告](FD-001-test-r7.md) 的36/36和42/88=47.73%是revision7历史支持，每项历史来源在JSON列明；本轮未重跑，不加入当前计数。未授权源码coverage工具/插桩，Branch coverage=not measured，4/4不能推导源码分支。

## 未覆盖和剩余风险

需要PM针对最小修复范围明确继承证据与覆盖例外。>=5样例/截断、其余等待正值/其他条件组合、Firefox、全部角色服务端授权/CSRF/竞态、完整事件来源、hidden/timeout/陈旧等未在本revision运行；Firefox仍按用户允许延期。不能将精简4/4解释为完整FD或FD-002已完成。

