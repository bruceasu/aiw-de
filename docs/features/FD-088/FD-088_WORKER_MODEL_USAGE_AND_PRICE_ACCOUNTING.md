# FD-088: Worker model usage and price accounting

**Status:** In Progress
**Revision:** 1
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

Worker 用量和费用仅在部分受控执行中可见，普通任务难以按请求追溯成本。现有价格与预算绑定，不能覆盖新模型、变价、缺价回填和订阅连接。服务商页面更新后，采集器需要及时恢复。

## Options and decision

建立独立的请求用量与内部价格账本，保留既有受控结算和人工核对。采集器独立版本化，后台刷新；失败保留已验证版本。价格未知不阻塞记录，通过可追溯的幂等回填补充估算。自动修复由 Worker 修改采集模块并经验证交付。用户确认在历史价格不可证实时显示未知或单独参考估算；支持独立模块热更新及跨设备交付。

## Solution

具体协议、数据及失败处理见 [用量和价格设计](../../WORKER-USAGE-AND-PRICING-DESIGN.md)。本 FD 为实施状态和 Work Items 的权威来源。

- 每次模型调用记录稳定请求 ID、attempt、时间、提供方、模型、连接类别、用量和未知状态；完整保留已知部分，跨重试幂等。
- 内部价格表支持币种、模型、输入／缓存／输出及服务档位、时间窗口和版本。价格计算是估算，不能伪造服务商实际账单。
- Plane 启动后异步刷新一次，此后每日刷新；各来源隔离、校验后原子发布，网络或解析失败保留旧记录。缺价待补，不将未知记为零。
- 判断来源结构改变后创建去重 Worker 修复任务；Worker 核对官方来源、修改独立采集器，验证后交付并热加载，其他机器通过交付物传递。临时网络问题不得触发修复风暴。
- 任务和 Plan 页面展示用量与估算；预算默认仅记录，后续在本 FD 内提供明确可选的预算提醒和控制，不改变历史结算值。

## Scope

OpenAI 和 DeepSeek API Worker 及 Plane；Codex app-server 不可见的用量标记未知。ChatGPT 订阅不按 API 单价表示账单。其他供应商通过适配器扩展，不推断自定义模型的价格。保留先前 Worker 循环和侧栏未提交修改，不把它们作为本 FD 的交付内容。

## Work items

- [ ] 1.1 定义数据库迁移与请求级用量上报，覆盖普通及受控任务、去重与未知值。
- [ ] 1.2 内部时段价格表、版本和安全幂等回填；保持既有费用账本不变。
- [ ] 1.3 OpenAI、DeepSeek 官方来源的独立采集器、启动及每日后台刷新，失败保留上次有效价格。
- [ ] 1.4 采集器异常去重、Worker 修复任务、验证和跨设备交付与热更新。
- [ ] 1.5 按请求、任务、Plan 展示已知／未知用量、适用价格和估算；可选预算提醒与控制。
- [ ] 1.6 静态自查、相关编译、独立评审及本地交付。

## Acceptance

同一请求重复上报不重复累计；失败、未知和部分用量不被记为零。价格时间条件及版本可追溯，缺价可按有依据的历史价格回填，参考估算有标记，订阅不被当作 API 实际账单。更新失败不阻止 Plane 或任务；结构变化时仅有一个对应修复任务，验证失败不启用变更。用户能在执行与 Plan 视图理解成本覆盖情况与限额状态。

## Verification

- Independent：涉及跨模块协议、持久化迁移、并发及自动代码交付；当前尚无独立评审。
- 用户此前要求额外测试、依赖安装及真实 API 调用须先授权；本 FD 不自动运行这些操作。编译与静态检查仅证明有限事实。
- 2026-11-10：已基于现有代码和官方价格页面记录方案。新增独立价格匹配与 token 估算规则，支持时间窗口、上下文区间、缓存写入、缺失／冲突状态及订阅标识；尚未接入持久化、采集、Worker 或页面，不能视为完整价格账本。
- 2026-11-10：使用 `setup-jdk25.ps1` 后执行离线 Maven `-DskipTests compile`，344 个生产源编译成功；`git diff --check` 无空白错误。未运行测试、真实 API 或独立评审；所有 Work Items 仍未完成。

## Sources

- [用量和价格设计](../../WORKER-USAGE-AND-PRICING-DESIGN.md)
- [现有受控计量](../../../src/main/java/me/asu/aidev/execution/guard/ExecutionLedger.java)
- [OpenAI 模型列表](https://developers.openai.com/api/reference/resources/models/methods/list)、[OpenAI 价格](https://developers.openai.com/api/docs/pricing)、[DeepSeek 价格](https://api-docs.deepseek.com/quick_start/pricing/)
