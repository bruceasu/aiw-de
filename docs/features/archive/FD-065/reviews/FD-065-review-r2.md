# FD-065 独立复审 r2

<!-- aiw-data: FD-065-review-r2.json -->

**Reviewer session：** `reviewer-FD065-20261031-r2`  
**Source event：** `FD-065-000007-implementation-ready`  
**审查基线：** `aee3f7f4f9d4329493fb9ed491e7338e6bbdebb0` → `4b74e1ee99550ac995d49724f8ad6bd68b556484`

## 结论

**changes-requested。** r1 的 F1、F2、F3、F5、F6、F7 已有相应修复；F4 的必需响应校验仍不完整，且 Worker r2 Dual 报告不可读。未运行 fixture、测试、网络/API 或运行时验证；Work Item 1.5 已标记为本阶段取消，相关验收仍属未验证，不据此声称通过。

## 发现

### R2-F1 · P2 · API 信封与 diagnostics 元素校验不完整

位置：`personal-secretary/knowledge_client.py:86-98, 160-166`；消费者：`personal-secretary/assistant_tools.py:175`。

API 合同的成功和错误信封均要求 `requestId`，但通用请求处理只验证 `apiVersion` 与 `data`，接受缺少或类型错误的 `requestId`。此外，`read()` 只检查 `diagnostics` 是数组；工具层随后对每个元素直接调用 `.get()`。若服务端返回非对象元素，会泄漏未映射的 `AttributeError`，而不是受控的 `KnowledgeError`。这仍未满足 FD 的逐 endpoint 必需字段、类型校验与受控协议错误要求。

**修复要求：** 校验 envelope 的非空 `requestId`；在 `read()` 中验证每个 diagnostics 项的对象及 `code`、`message` 字段，再交给工具层。

### R2-F2 · P2 · Worker r2 Dual Markdown 报告文本损坏

位置：`docs/features/FD-065/reports/FD-065-worker-r2.md`。

按 UTF-8 读取，报告包含 281 个字面 ASCII 问号，包括标题和正文，不能作为可读的人类报告核对其实现说明和验证声明。JSON sidecar 可读，但不能替代 Dual policy 要求的人类报告。

**修复要求：** 用可读中文重生成 Markdown，并保留与 sidecar 一致的事实和未验证声明。

## r1 发现复核

- **F1：已修复。** 配置 UI 文案以 HTML 数字实体保存；FD Work Item 1.5 如实标为本阶段取消且仍未验证。
- **F2：已修复。** 普通模型设置提交通过 `knowledgeSettingsDirty` 避免触发知识库重新配置。
- **F3：已修复。** GET 与显式只读搜索最多重试一次，并处理连接错误及 429/503/504 的 `Retry-After`。
- **F4：部分修复。** endpoint 字段校验已扩展；仍有 R2-F1 所述缺口。
- **F5：已修复。** 搜索摘要和正文共用 30,000 字符预算，超额搜索结果会被省略并反映到完整性/更多结果字段。
- **F6：已修复。** 可信来源校验将候选 HTTPS origin 与已配置 origin 的端口一并比较。
- **F7：已修复。** 创建结果无法确认时持久化 `unknown`，并保留原幂等键、请求体及服务身份以供对账。
- **F8：未验证，非本轮测试结论。** FD 将 fixture 与跨服务验收列为单独授权阶段；本轮未运行相关检查，不声称通过。

## 命令与限制

实际执行：读取 FD、API 合同、Worker 与 r1 报告；`aiw fd show FD-065`；`aiw fd claim --session reviewer-FD065-20261031-r2 FD-065 FD-065-000007-implementation-ready`；`git rev-parse HEAD`、`git merge-base develop HEAD`、定向 `git diff`/`rg`/源码读取；用 Python UTF-8 读取 Worker 报告并统计字面问号。

未执行：测试、fixture、编译、浏览器验证、API/网络请求、服务运行、构建或验证脚本。静态审查不能证明端到端行为；真实 API 响应与 fixture 覆盖仍未验证。
