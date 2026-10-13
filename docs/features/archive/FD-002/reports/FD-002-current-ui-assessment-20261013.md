# FD-002 当前 UI 与验收适用性报告

日期：2026-10-13（Asia/Tokyo）
检查基线：`c97afdd`；检查前工作区干净。
性质：当前用户授权的只读静态评估及报告撰写，不是独立 Reviewer 结果，不改变 FD 状态。

## 结论

FD-002 的业务验收要求仍大部分适用，当前页面没有整体替代它。需要废弃的是旧执行器的直接复用方式和无上限的重型验证方案，而不是需求真实性、真实依赖、时间线筛选、刷新保持及权限要求。

当前不能据此标记 Complete，也没有依据判定“功能已全部被替代”。推荐保持 Deferred，先采用当前页面的有限验收方案；若要完成归档，先明确修改旧验收政策，再按修订范围验收与独立审查。

## 检查范围与比较限制

检查了 FD revision 18、r12 最后一次实际测试、r14 恢复记录、r15 未执行报告，以及当前 Run 模板、Mission 查询/读取、mission.js、共享样式/刷新脚本和旧测试执行器。

本地相关文件的 Git 历史只显示 `4f16802`（2026-10-13，Initial）。到当前 HEAD 的 scoped diff 仅列出 Zone.Identifier 删除，没有 UI 源码内容变化。归档报告引用的是更早实施基线，当前可见历史不足以重建该版本。这个结果只证明导入以来未见相关源码变化，不能证明用户没有优化 UI，也不能证明当前页面与历史测试版本相同。

因此，本报告对照“FD 要求与当前实现”，不声称完成了优化前后的精确回归比较。用户实际部署的页面也未连接核实。

## 六项验收要求的当前适用性

| 要求 | 当前静态证据 | 判定与建议 |
| --- | --- | --- |
| AC01 原始需求、Run 状态、阶段与计数 | run-detail.html 保留需求与 lifecycle/control/effective 展示；Mission 服务读取 runDashboard，原需求按保存的关联 ID 查询 | 仍有效。无需因视觉优化废弃；静态来源支持存在，不等于模板渲染通过 |
| AC02 真实依赖及动态任务历史 | Mission 查询从保存的 dependency 组合图；详情保留 SVG、表格、窗口外/跨 subject/循环提示 | 仍有效。真实边展示可以用受控数据验收；旧 Worker 的完整执行链不应成为页面验收前置 |
| AC03 失败、执行与恢复证据 | 页面/读取保留 attempt、FAILED/RECOVERED、原因与时间；未知关联明确处理 | 展示仍有效。真实租约恢复/撤销属于运行行为，须与 UI 展示检查分开，不能冒充已通过 |
| AC04 有限历史与范围 | 当前保留任务 200、依赖 1200、attempt 300、信号 20、时间线 300 的窗口/范围设计 | 仍有效。可静态核对边界与提示，实际边界渲染尚未复验；时间线输出有限不代表数据库读取成本有限 |
| AC05 动作权限、CSRF、不可逆与竞争反馈 | 当前页面继续有原 Run 操作与 CSRF，并增加任务指令修订、纠正任务、工作区替换等入口 | 原要求有效，但旧清单不覆盖当前全部新增动作。新增动作的完整验收应归其所属 FD；本 FD 只核对共用安全/刷新是否被影响 |
| AC06 长文本、窄窗口、减少动效与阅读保持 | 当前样式保留换行、图内滚动、响应式与 reduced-motion；mission.js 保留图开关/滚动与筛选，共享脚本提供局部刷新基础 | 仍有效，视觉实现需以当前页面为准。旧整页截图不构成当前视觉基线；仅静态检查不能证明无溢出或刷新焦点稳定 |

未发现上述核心要求已取消的明确记录。当前仍是 AR Run 页面，JdbcWebConsoleMissionReadRepository 查询限定 `AUTONOMOUS_REQUIREMENT`；它未成为 FD-011 灵活计划详情页。FD-017 将负责灵活计划 UI，两者不能直接互相替代。

## 历史三个失败是否还值得补验

| 历史失败 | 当前证据 | 建议 |
| --- | --- | --- |
| timeline-category-filter | 当前仍有类别控件、条目 data-category，以及 mission.js 筛选逻辑；r14/r15 已调整测试标签读取，但未运行 | 功能仍在，短时补验有意义。不能从旧定位失败推断当前产品故障，也不能从代码存在判定已修复 |
| refresh-filter | 控件位于刷新区外；MutationObserver 在区域替换后重用筛选条件 | 需求仍有效。检查一次刷新前后选中值和可见条目集合即可，不必重复全套场景 |
| width-1440 | 当前有长文本换行与内部滚动样式；历史报告记录截图 GPU/捕获失败，不是已确认的页面溢出 | 只需当前布局的溢出、长文本、图滚动检查及少量视口截图；不重复几万像素页面的完整纵向截图 |

历史 r12 的 93/104 通过只证明当时版本；r15 的 0 执行也不抹除历史证据。但两者都不能作为当前版本的验收通过。

## 已确定过时或需要重设的验收设施

1. `tests/fd002/runtime.py` 硬编码 Windows Java/Node/Chrome/Python 路径、Windows 进程选项与 taskkill；当前工作区为 Linux 路径，不能原样运行。
2. 执行器绑定 revision 15、旧 session、`FD-002-000015-implementation-ready`、固定摘要、活动 FD 路径和原授权文件路径；FD 已归档为 revision 18，原 r15 授权为 denied。不能沿用旧授权，也不能修改历史记录伪造匹配。
3. classpath 从旧 Failsafe XML 提取并按 Windows 分隔符处理；当前环境没有经核实的同一编译/依赖基础，不能假定可用。
4. browser.mjs 会启动四角色检查，对 390/768/1280/1440 的 dense 与 long-ID 页面逐段截图。历史 dense 页面约九万 CSS 像素，截图达数百段，这是成本来源，与“当前布局能否阅读”不是同一验证粒度。
5. 执行器 ADMIN 超时配置 420 秒，其他角色各 75 秒，另有启动、等待和截图成本；它不是一次轻量快速检查。
6. 原 104 项库存混合展示、交互、权限、真实租约、并发、跨浏览器等要求。不能把所有未执行项删除后沿用原通过比例；应为新验收建立单独清单，并明确哪些延期及风险。

## 建议的有限收尾方案（未执行）

第一步先修订 FD 的 Verification/旧 Independent policy，保留原历史报告和失败记录，明确允许的新范围及延期项。此次评估没有授权变更该政策或启动验收。

第二步使用当前页面及受控代表性 Run 数据，安排一次浏览器检查，总预算最多 15 分钟，不自动重试、不真实调用 AI、不等待 Process Worker 完整开发链：

- 原始需求、状态、任务表与真实依赖图可读且一致。
- 类别筛选一次，刷新后筛选保持一次，文本筛选/Reset 一次。
- 390 与 1440 宽度检查长文本、横向溢出和图内滚动；每个宽度只保留必要视口截图。
- 图节点定位、记录展开，以及一次刷新前后阅读位置/输入保持。
- 至少核对 Viewer 写入口不可操作；CSRF 和服务端权限先做静态核对，有相关实现变更再单独确定 focused 请求检查。

超时或环境故障立即停止，报告具体未完成项。不得以“无失败”代替“已执行”。测试设施准备所需时间单独报告，不把准备过程隐藏在 15 分钟运行预算中。

Firefox、无 JS、真实租约恢复/暂停撤销、并发提交、非 UTC 浏览器和缺失时间等原未覆盖项，若暂缓，应逐项记录风险；不自动视为不适用，不冒充通过。当前新版 Worker 与旧 AR 类型不兼容，因此真实执行链的验证必须另行解决契约，不能借 UI FD 隐式恢复兼容。

最后由独立 Reviewer 按修订后的验收与真实证据判断；用户若决定停止投入，可保持 Deferred 或按明确理由 Closed。当前没有足够证据标记 Complete 或“已替代”。

## 实际命令、未运行项与残余风险

实际执行：`git status --short`、`git log`（全局及相关路径）、`git diff --stat 4f16802 HEAD -- <UI 路径>`、定向 `rg`/`cat`/`sed`；只写本报告。不改业务代码、FD 状态、旧报告或测试授权，不作 Git 提交。

读取 `docs/templates/REPORT_DATA_TEMPLATE.json` 时文件不存在；本报告为用户要求的普通评估文档，不是 FD 生命周期交接报告，因此未新增或假造 Dual/Reviewer 证据。

未运行：旧验收脚本、浏览器、应用、Worker、编译、测试、网络、数据库迁移及最终构建。纯文档报告不需要编译。

残余风险：当前页面实际渲染/交互未验证；优化前版本不足以精确比较；历史失败未获当前复验；新增操作可能影响布局/刷新；时间线仍可能全量读取；实际部署版本未核实。

## 证据入口

后续决定（2026-10-13）：用户明确放弃 FD-002，不再继续实现或验收。CLI 拒绝对已归档 FD 直接执行 Closed，故保留 Deferred 归档状态，在原 FD 追加处置决定；本报告的验收方案仅保留为历史建议，不再作为当前待执行计划。

- [FD-002 revision 18](../features/archive/FD-002/FD-002_RUN_MISSION_DETAIL_AND_TIMELINE.md)
- [r12 实际测试](../features/archive/FD-002/reports/FD-002-test-report-r12-attempt2.md)
- [r14 恢复记录](../features/archive/FD-002/reports/FD-002-implementation-recovery-r14.md)
- [r15 未执行报告](../features/archive/FD-002/reports/FD-002-test-report-r15-not-run.md)
- [当前 Run 页面](../../src/main/resources/templates/ui/run-detail.html)
- [当前筛选与图交互](../../src/main/resources/static/ui/mission.js)
- [当前 Mission 组合查询](../../src/main/java/me/asu/aidev/webconsole/application/WebConsoleMissionQueryService.java)
- [当前 Mission 数据读取](../../src/main/java/me/asu/aidev/webconsole/jdbc/JdbcWebConsoleMissionReadRepository.java)
- [旧执行器](../../tests/fd002/runtime.py)、[旧浏览器检查](../../tests/fd002/browser.mjs)
