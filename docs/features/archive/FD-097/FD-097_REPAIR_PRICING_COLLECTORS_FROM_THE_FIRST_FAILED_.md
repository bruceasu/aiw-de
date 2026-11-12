# FD-097: Repair pricing collectors from the first failed page without historical baselines

**Status:** Complete
**Revision:** 3
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

价格修复虽然已有显式按钮，但整条链仍要求历史成功页面和两次结构失败，导致新安装不能修复首次解析失败。

## Options and decision

仅使用当前失败页面与失败时脚本，移除历史成功页面及重复失败门槛，不引入新的修复框架。

## Solution

材料、领取、读取、审阅、批准运行和状态投影统一绑定失败指纹、脚本摘要、失败页面摘要和 URL。只交付 collector.py 与 changed.page；Worker 查找最新官方页面供管理员审阅并明确批准。材料协议升为 v2，Plane 与 Worker 一起更新并重启，不保留旧协议兼容层。

## Scope

新增 V45 迁移删除修复材料的 last_success_sha256 与 last_success_sample_base64 两列；保留价格与采集历史。旧迁移不修改。网络不可达、脚本缺失等没有有效失败页面的情形报告原因并允许重新采集，本轮不凭空创建修复材料。不改变审批、租约、脚本安装或价格发布的规则。

## Work items

- [x] 1.1 移除旧基线和重复失败依赖，统一当前失败材料校验。
- [x] 1.2 更新 Python 材料协议、页面提示与受影响用例。
- [x] 1.3 离线编译并提交独立静态审查；通过后更新进度并归档。

## Acceptance

没有成功历史时，首次有效结构失败即可由用户启动修复。脚本已改变、失败材料过期、无有效租约、已停止或未经明确批准时仍拒绝对应操作。只有最新官方页面的候选通过管理员批准后才能运行、安装及发布。测试夹具覆盖首次失败及既有租约/停止/材料篡改规则；本轮不运行测试或迁移。

## Verification

- 涉及持久化迁移和跨 Java/Python 材料协议，使用独立审查。
- 2026-11-13：离线 `mvn -q -o -s .ai/fd/FD-081/offline-maven-settings.xml -Dmaven.repo.local=D:/.m2/repository -DskipTests test-compile` 通过，仅编译生产和测试源，不执行测试。
- Python 内存 compile（price_repair_materials.py 和 tests/fd088/test_price_repair_materials.py）、`node --check src/main/resources/static/ui/model-usage.js`、`git diff --check` 通过。
- 静态检查价格生产链中没有 last_success_sha256、last-success.page、failure_count>=2 或旧材料协议引用；旧 V40/V39 历史迁移原样保留，新 V45 单独删除两列。
- 既有材料与租约/停止/篡改用例改为无成功记录、仅一次失败的夹具；Java/Python 用例只编译，未运行。未执行迁移、服务、浏览器或真实 API。

- 2026-11-13：独立静态审查通过（verification-passed），未发现阻塞项。报告 [review-20261113.md](reviews/review-20261113.md)，覆盖 dddf01c 对 07d3130，来源事件 FD-097-000002-review-requested，Reviewer codex-review-fd097-20261113。仅静态核对迁移引用、v2材料与首次失败链路；未运行测试、迁移、服务、浏览器、网络或模型，不宣称运行验收通过。

## Sources

- docs/FUNCTION-INVENTORY.md 第 21、22 项；FD-096 的历史材料限制待办。

**Completed:** 2026-11-12
