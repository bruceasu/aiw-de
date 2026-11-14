# FD-110: Simplify provider connections model catalogs and effort settings

**Status:** Complete
**Revision:** 4
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 17, 19. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

按服务商和协议整理连接默认值、模型自动获取及手动输入、真实支持的推理参数。OpenAI 兼容 API 为主，app-server 备用；默认固定模型，只在明确允许的范围内选择。

Dependencies: none. 核对发现：Worker 已统一 SDK 循环和显式 API 协议；工厂已有按服务商选择／目录失败手动输入／默认省略强度参数。但秘书“其他 Chat Completions”仍走旧文本 HTTP 分支，目录也丢弃逐模型强度元数据。此次补齐真实差异，不新增运行框架。

- 秘书统一服务商选择与独立 API 协议；API Key 的 Responses／Chat Completions 都进入同一个 SDK Runner、工具校验、暂停与授权循环；ChatGPT 仍使用现有 Responses 模型适配器，共用业务循环。Codex 仅作显式备选，不自动回退。
- 复用已安装 SDK 的 Converter 处理 Chat 消息、工具和推理内容；只在完整 stop／tool_calls 终态返回 ModelResponse，旧文本 HTTP 实现删除。不强制关闭并行工具调用；后续执行仍受既有批次确认／中断校验约束。
- 同一 Chat 适配函数供工具循环与“检查已保存配置”／摘要等文本调用使用；协议写入本次连接绑定。现有 chat 配置改走 SDK，保留原地址和已有凭据，不为原本无认证的配置擅自启用环境变量。
- DeepSeek 目录的可选 effort.supported_levels 原样按受支持值整理；没有元数据时显示默认及明确标注的手动覆盖，不根据模型名称猜能力。已知目录不支持的当前强度要求用户重新选择，不静默改写已保存模型。
- 工厂目录、保存、凭据使用检查统一允许带供应商路径和版本的模型 ID，160 字以内，与现有 Worker 选型契约一致；页面保留手动输入，不再重复另一份更窄字符规则。
- 不新增依赖、表或模型探测请求；不改变已确认的权限和模型切换允许范围。默认固定已选模型，不因失败擅自重试、换模型或换付费来源。持久化模型配置与运行协议变化需独立审查。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

## Work items

- [x] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [x] 1.2 实施已确认行为，删除因此不再需要的代码。
- [x] 1.3 编译受影响源码、自查差异并提交独立审查；结论单独记录。

## Acceptance

API Key 与 ChatGPT 共用业务循环；界面只显示适用配置；目录失败仍能手动输入；实际模型、强度及获准变更原因有记录。

## Verification

- 独立静态审查（`6ff6e9e → cc77574`，来源 `FD-110-000003-review-requested`）：Pass，限定范围未发现阻塞性 finding。已核对表单协议回填/保存、旧配置迁移和凭据保留、SDK 转换签名、终态/工具门控、运行绑定与未知用量，以及工厂模型名称规则。详见 [审查报告](reviews/review-20261114.md)。Reviewer 未运行测试、编译、网络或运行时验证；真实服务兼容性与 UI 操作限制保持。

已通过受影响 Python 源码和两份回归源码的 `python -m py_compile`、秘书 app.js／i18n.js 的 `node --check`、JDK 25 低内存离线 Maven `-DskipTests test-compile` 及 `git diff --check`。新增源码用例覆盖完整终态门槛、推理内容回传、默认省略参数、未知用量、原 Chat 配置不擅自使用环境变量、目录强度元数据和自定义模型名。未运行任何测试、服务、模型、真实凭据导入或模型目录请求；仅读取官方文档和本地 SDK 源码。待独立静态审查，编译不等于运行通过。

## Sources

- docs/FUNCTION-INVENTORY.md, 17, 19.
- [OpenAI：模型目录](https://developers.openai.com/api/reference/resources/models/methods/list)、[推理强度依模型而定](https://developers.openai.com/api/docs/guides/reasoning)（2026-11-14 读取）。
- [DeepSeek：模型目录元数据](https://api-docs.deepseek.com/api/list-models/)、[Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/)、[思考模式](https://api-docs.deepseek.com/guides/thinking_mode/)（2026-11-14 读取）。
- 已安装 SDK 源码 agents/models/chatcmpl_converter.py、reasoning_content_replay.py；仅静态读取，未加载 SDK 发出请求。
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。

**Completed:** 2026-11-14
