# FD-001 PM 测试决定 r8 v1

<!-- aiw-data: FD-001-test-decision-r8-v1.json -->

**Disposition:** accepted

**Tester report:** docs/features/reports/FD-001-test-r7.md

**FD revision:** 8

**FD digest:** cb71ae57807dd1e49d370c94d49cbdfdc772553fe7990502a781f7dcdf14005b

**Requirements coverage:** 47.73%

**Branch coverage:** not measured

**Rationale:** 已核对 r7 原始结果：36 个业务断言全部 passed，没有失败或 infrastructure 条目。一次导航诊断另存证据，不计入业务数。88 个细分场景中 42 个有独立执行映射，46 个仍未覆盖，没有用 Worker 结果扩充独立计数。改动的关键可观测路径已有执行：旧 pending 与活跃窗口、四角色读取/New Run/Worker 入口、5/8 秒更新、展开/焦点/滚动、编辑/reset、真实会话失效、身份/动效及四宽度。原四个 Web Console IT 和 Worker 视觉/错误状态结果支持原操作边界；新增查询及权限/CSRF 调用链有静态证据。本决定接受报告进入独立 Reviewer，不直接宣称 FD 完成。

**Exceptions:** 对本次 M1 UI/只读投影审查，明确批准独立需求覆盖率低于 70%（实际 47.73%，42/88）和分支覆盖未测的例外。没有覆盖率插桩、下载或全量测试授权。原四个 IT、Worker 29 项浏览器通过与焦点复验、当前独立截图及静态证据供 Reviewer 综合判断，全部不计独立覆盖。46 个未覆盖场景保持原状态，功能要求未被豁免。Firefox 仅按用户明确决定延期。

**Residual risk:** 五类等待总量/样例/重叠、全部事件来源/缺失关联、完整审批/服务端角色/CSRF/竞态矩阵、隐藏页/角色变化/文本选择/错误恢复及 repair 分母没有独立运行覆盖。高数据量查询成本与 Firefox 兼容未测。Reviewer 仍需指出具体代码/证据缺陷；覆盖例外不允许把未运行场景写成通过。

**PM identity:** fd001-pm-host-20261004

**Decision time:** 2026-10-04T14:14:56+00:00

## 剩余场景的证据边界

| 未覆盖组 | 可供 Reviewer 核对的真实证据 | 独立计数处理 |
|---|---|---|
| S043-S047、S082-S083、S086 | Worker 空态/旧页/截断截图、当前 1440/390 图；模板转义、链接和范围提示 | 仍未覆盖 |
| S048-S065、S085 | QueryService 等待谓词、私有批量投影、每事件来源有界 SQL；独立活动窗口/重复读取排序 | 仍未覆盖，须审查数据语义和缺失反馈 |
| S066-S071 | 原 action/security controller、角色方法和 CSRF 调用链；已运行的四个 IT | 全矩阵仍未覆盖 |
| S072-S081 | Worker 单请求/10s timeout/30s stale/失败/异常 HTML 结果；浏览器 guards 静态追踪；独立更新/阅读/编辑/cookie 结果 | 未执行细分场景仍未覆盖 |
| S084、S087-S088 | Firefox 明确延期；中英文指南与独立 M2-M4 记录 | 未覆盖/延期不改为通过 |

历史 r4 blocked 报告、r5 拒绝决定和首两轮基础设施失败不改写。用户新增诊断及条件复验的授权与 r7 两个绑定记录已核对。没有继续重跑、复跑 Maven 或新增构建。Reviewer 失败次数仍为 0；后续必须通过真实 Reviewer 事件才能完成验收。

