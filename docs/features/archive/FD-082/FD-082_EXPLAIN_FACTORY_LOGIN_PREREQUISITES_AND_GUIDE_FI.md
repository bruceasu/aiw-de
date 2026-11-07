# FD-082: Explain factory login prerequisites and guide first ChatGPT connection

**Status:** Complete
**Revision:** 2
**Review policy:** Self
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

用户选择 Python Worker + ChatGPT 后没有已保存连接，创建连接按钮为灰色。静态调用链确认：创建不依赖已有连接，但按钮要求凭证状态接口返回 vaultConfigured=true；密钥缺失/无效或接口读取失败时保持禁用。原因原先显示在其他位置，创建入口又默认折叠，无法清楚知道下一步。尚未读取用户服务环境，不能断言其密钥确实缺失。

## Options and decision

保留已有加密存储和服务端校验，直接在创建区呈现阻塞原因及配置步骤。无需修改认证合同、默认生成持久密钥或降低凭证保护要求。

## Solution

创建区自动展开当前类型的空连接状态；按钮旁区分检查中、状态读取失败、存储未配置和已有登录进行中。配置状态读取失败可就地重试；未配置时展示首次安装的 PowerShell 操作步骤，提醒恢复和保留原密钥。更新静态资源版本及相邻说明。

## Scope

仅修改工厂页面、页面脚本和使用说明。不更改后端接口、存储、权限或 OAuth 流程，不读取或修改真实凭据/部署配置。

## Work items

- [x] 1.1 在创建区展示禁用原因、空连接引导和配置重试入口。
- [x] 1.2 更新首次配置说明，完成静态自检和 compile-only，记录未验证项并按普通流程归档。

## Acceptance

1. Python + ChatGPT 无连接时直接看到新连接创建区，不要求先选已有连接。
2. 登录按钮禁用时就地说明原因；接口失败与缺少密钥分别表述。
3. 存储未就绪时明确告知首次配置和恢复原密钥的步骤；未绕过现有禁用条件。
4. 重新读取状态成功后按实际状态恢复按钮；登录进行中维持互斥。

## Verification

- Review policy Self：仅修正现有认证条件的呈现和入口引导，不更改认证、权限、持久化或公开接口。
- 已静态追踪 agent-factory.js 的 synchronize/beginLogin/reloadConnections、ModelConnections.state、CredentialVault.ready 以及 application.yaml 的环境变量映射。
- 已执行一次 `node scripts/compile-worker-runtime.mjs`，通过 JavaScript/Python/TypeScript 语法及 Go compile-only；未运行测试、服务、浏览器或网络，未保留分发产物。
- 自检覆盖：按钮仍要求 vaultConfigured=true；已有登录保持互斥；缺少/无效密钥、加载中和接口失败分别展示；失败可在创建区重试。新增就地重试后，旧初始化响应须匹配 connectionEpoch，避免覆盖较新的配置检查结果。
- 首次配置命令仅展示在页面，不由开发过程执行；保护已有密钥、同一启动会话和跨重启保留同一密钥均有明确提示。
- 用户仅确认按钮灰色，具体运行环境的 vaultConfigured 和接口错误仍未取得。

## Sources

- 用户报告：Python Worker + ChatGPT 无已保存连接，登录按钮灰色。
- docs/MODEL-AUTHENTICATION.md。
- 已归档 FD-081 的现有认证合同与独立审查。

**Completed:** 2026-11-07
