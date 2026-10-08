# FD-001 独立修正评审（revision 13）

<!-- aiw-data: FD-001-review-r13.json -->

## 结论

**verification-passed。** r9 的 F1–F3 已解决，本轮没有新增需要交回 Worker 的具体缺陷。Reviewer 失败结果仍累计 **1/3**，没有把首轮 changes-requested 改为通过。

这是基于当前修正源码、当前定向运行、既有真实证据和 PM 明确例外的评审结论，不能解释为全部场景或全部平台已在当前 revision 重跑。结果事件由本 Reviewer 在 host 允许串行交接后通过真实 CLI 发出；本报告不执行关闭、归档、合并或发布。

Reviewer `fd001-reviewer-20261004-9195c0ad` 已真实领取 `FD-001-000013-test-accepted`，与 Worker `fd001-worker-20261004-b2aa6c85`、Tester `fd001-tester-20261004-7303009b` 不同。领取时 FD revision 13 / SHA-256 `f9dac54d598c14ce8f670f7f51b64243746d52a9925d51c84f1873e533c4d8ff` 与 receipt 一致。

审查修正差异：`ae19d463436fe9730b07e7106ecbdfacfab98845` → `a870b97d6a77204fb0cbdfe73f7016fd46a362bf`，仅本 FD 的私有 QueryService、dashboard 模板、console.js、双语指南和证据；原始实现及全范围判断沿用 [r9 独立评审](FD-001-review-r9.md) 的基线与结果。已核对这些产品文件无未提交差异。其他 Worker、FD-002 及外部更新不纳入本次评审。

## F1–F3 关闭依据

| 发现 | 当前源码 | 当前公开行为证据 | 结论 |
|---|---|---|---|
| F1 总量/样例口径 | 五组谓词与 Fleet 原 `policy → Run admission → capability → Worker capacity → Fleet admission` 的互斥分支一致；其他未满足条件在任务内标注；模板及双语指南解释口径 | 三个类别为 0/0，Run admission 和 Policy 为 1/1；paused 且 capability 不匹配的 task 仅属于 Policy，同时显示两个未满足条件 | resolved；没有修改 Fleet 权威计数或调度 |
| F2 动态 summary 导致展开丢失 | `data-detail-key=g.label` 不含数量、任务或时间 | 焦点移到组外 motion 控件；真实新增 READY 后该组从 0→1，仍 open，外部焦点保持 | resolved；158ms 内观察到更新 |
| F3 跨区域选区被替换 | Selection 的全部非折叠 Range 用 `intersectsNode(region)` 保护相交区域；原焦点及部分陈旧提示保留 | 13,149 字符选区跨五区；未选 metrics 实际替换，选区全文、端点节点/偏移及中间 Worker 节点均保持 | resolved；165ms 内观察到未选区域更新 |

静态追踪确认没有变更依赖、schema、持久化、权限、公开协议、Worker 生命周期、轮询周期、单请求约束或超时。分类样例依然来自原有 100-task 明细窗口，每组最多 5 条；较大的正值/截断分支没有在当前定向运行中验证。

## 独立测试与授权真实性

已核对 [r11 测试报告](../reports/FD-001-test-r11.md)及同名 JSON、`tests/fd001/evidence-review2/results.json`、诊断 JSON、留存脚本和实际截图。当前四个唯一业务 case 全部 passed，无 runner 失败；5 个细分观察点映射为 S053/S078/S089/S090/S091，完整库存 91 项，**5/91=5.49%**，另外 86 项当前未运行。

`waiting-count-samples` 的宽泛 case 名称没有被当作 5 条截断分支通过：S048–S052 仍 uncovered，零值和单样例另外拆分。Selection 用例要求未选区域真的替换，同时检查端点、偏移、全文和中间节点，排除了“根本没有刷新”也通过的路径。Policy 重叠的原始结果及截图显示两项条件，未把主分类与全部条件混淆。

已核对 [r11-v1](../reports/FD-001-test-authorization-r11-v1.md)、[r11-v2](../reports/FD-001-test-authorization-r11-v2.md)：两次相同精确命令 `C:/green/python312/python.exe tests/fd001/runtime.py review-fixes` 均绑定事件 11、revision 11、digest `bf8bbae3a950348233a32faf39393bbd4c8cd7e07aeff14464833dbd0fdb6377`、同一独立 Tester session 及 cwd；用户授权引用一致。v2 在相关脚本修正后单独记录，输出路径另存，未放宽展开/选区/真实更新的断言。

初验两项业务断言失败及 runner 失败在 `evidence-review1/initial` 保留；相关修正复验后为 4/4，两轮合计 8 次业务断言执行。初验没有观察到实际刷新，当前通过支持修复行为，但没有证明其唯一根因。历史 r7 的 36/36、42/88=47.73% 没有计入当前覆盖率；Worker 的四个 IT、29 个浏览器通过项和两个焦点通过项也没有重复计数。

已核对 [PM r12 决定](../reports/FD-001-test-decision-r12-v1.md)：明确接受本次修正范围的低于 70% 覆盖、branch coverage unavailable 及继承未变基线的例外，未接受初验失败，也未豁免功能要求。Reviewer 尊重该决定和用户 Firefox 延期，不额外要求已延期检查。

## 全范围与剩余风险

| 条件 | 评审支持 |
|---|---|
| AC01/AC04/AC08 | 既有本地空态、四状态、跨页身份、四宽度/焦点/动效真实证据；确定性身份、转义与布局源码；本轮新增窄屏实际截图 |
| AC02/AC03/AC09 | 原 PENDING 过滤与准确总量、活跃窗口独立测试；完成数/阶段读取及有界批量/活动 SQL 静态追踪；本轮 F1 定向复验 |
| AC05 | r9 已检查真实 timeline 来源、事件时间与稳定来源键；该投影本轮未改，完整事件来源/缺失关联运行覆盖仍有限 |
| AC06 | 原安全/审批/操作 IT、静态原路由及服务端 CSRF/角色检查；本轮未更改这些边界，全角色服务端矩阵未在当前 revision 重跑 |
| AC07 | 历史 5/8 秒更新、编辑/重置、真实登录失效及 Worker 超时/陈旧等证据；本轮 F2/F3 实际复验；完整 hidden/错误组合没有新增运行 |
| AC10 | 双语指南同步准确范围和新等待口径；M1 与 M2–M4 区分，没有将整个 REQ00001 标记完成 |

未覆盖的 86 项不是通过项。源码分支未测、>=5 例子/截断、其他等待条件组合、完整事件/缺失关联、服务器授权/CSRF/竞态、隐藏/超时/陈旧、大数据量成本及 Firefox 仍有验证限制。没有发现其中存在新的明确源码缺陷；此结论不提供当前全量运行或发布保证。未来归档的相对外部图片链接需由 host 在实际归档阶段核对，当前不扩展 CLI 范围。

## 本 Reviewer 实际操作

实际运行：`aiw fd claim FD-001 FD-001-000013-test-accepted --session fd001-reviewer-20261004-9195c0ad`；定向 `git diff ae19d463436fe9730b07e7106ecbdfacfab98845 HEAD -- <本FD相关路径>`；`git diff -- <相同产品路径>`；`git show HEAD --format=fuller --no-patch`；定向 `rg`、`Get-Content -Encoding UTF8`、FD `Get-FileHash`；view_image 读取已有 `evidence-review2/review-fixes.png`。写双格式报告并更新 FD Verification/TODO 后做一次定向静态读取。

没有新增测试、浏览器运行、网络、Maven、compile、覆盖率、formatter/lint/typecheck、验证脚本或构建。Worker 的一次修正 compile 日志已读取，175 sources、BUILD SUCCESS、4.079s；该命令不是本 Reviewer 执行。

准备交接的精确命令：`aiw fd emit FD-001 verification-passed --producer reviewer --artifact docs/features/reviews/FD-001-review-r13.md --source-event FD-001-000013-test-accepted`。仅在 host 串行允许后由本 Reviewer 执行，其真实 receipt 是正式事件结果。
