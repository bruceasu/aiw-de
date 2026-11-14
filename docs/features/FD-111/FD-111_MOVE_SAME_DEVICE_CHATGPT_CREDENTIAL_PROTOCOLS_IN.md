# FD-111: Move same-device ChatGPT credential protocols into Python

**Status:** In Progress
**Revision:** 4
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FUNCTION-INVENTORY: 18. 剩余范围尚未按已确认的简化方向完成。原有代码存在不等于本轮优化已完成。

## Options and decision

优先修改现有代码，以函数和直接数据结构完成当前需要；不引入新框架、单实现抽象、重复状态库或无用兼容层。

## Solution

Python 集中管理登录、显式导入、刷新与凭据文件；Plane 只协调同机操作、记录位置和状态，不解析外部 Token 格式。异机 Worker 自管连接，不分发凭据。实施前核实官方授权契约。

Dependencies: FD-110.

最小改法：保留既有同机浏览器回调、账号归属、Worker 证明及租约权限检查。将 OpenAiOAuth 改为本机 Python 脚本调用入口；协议 URL、OIDC 校验、导入、刷新和撤销由一个 Python 文件处理，复用秘书已有的 PyJWT 校验和受保护文件读写，不重复实现密码学。

Python 保存凭据文件，只向 Plane 返回文件引用及公开状态元数据；模型实际访问时仅通过原精确租约接口取得短期 access token，不分发 refresh token。文件锁覆盖刷新，先标记刷新进行中，再提交旋转后的完整令牌组；结果不确定就要求重新登录，不自动重发旋转请求。Plane 原刷新串行和版本检查继续保留。

V50 将旧密文列改为凭据文件引用并使旧连接失效，历史执行不删除；旧连接需重新登录或显式导入，不自动读取旧密文。密钥库仍只用于原 Worker 证明及其已有生命周期，不被当作模型 Token 格式解析器。脚本路径由现有 Worker 安装目录确定，不接受网页提供的执行路径。Python 的 PyJWT[crypto] 是现有秘书登录依赖，用于成熟验签；本轮只声明所需依赖，不执行安装。

官方契约（2026-11-14 阅读）：[登录](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)、[账户与刷新](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)。保持每次回调 URI 一致、使用签发的 client_id 交换／刷新，原子替换旋转令牌；导入不假定 Codex 与 ChatGPT Responses 凭据通用。

## Scope

仅实施上述已确认要求；未确认选择待澄清，不自动增加权限。保留真实历史和执行结果。

## Work items

- [x] 1.1 核对附近实现，记录最小具体改法与受影响契约。
- [x] 1.2 实施已确认行为，删除因此不再需要的代码。
- [x] 1.3 编译受影响源码、自查差异并提交独立审查。

## Acceptance

同机登录、导入、刷新和失效可观察；同一旋转令牌的刷新串行；日志不回显 Token；异机不会误用 Plane 本地路径。

## Verification

- 独立静态审查（`48e16be → 8435e4b`，替代来源 `FD-111-000003-review-requested`）：Changes requested，1 项 P1——普通重登录/导入替换未退役旧凭据文件，保存/取消失败也会遗留未采用的新文件。详见 [审查报告](reviews/review-20261114.md)。最终 HEAD 的 Java 编译因 JVM 原生内存失败未完成，明确不记为通过；Reviewer 未运行测试、编译、脚本或运行时验证。

独立审查：认证协议、文件持久化与跨模块变更。

Java 与测试源通过 JDK25 离线 test-compile；首次修复删除旧密文兼容测试时误移除的测试 helper 后重编译通过。Python 新协议与离线用例通过 py_compile；git diff --check 通过。新增文件引用、轮换后旧引用失效、刷新不确定不重发、路径拒绝及撤销本地清除的测试源码，未运行。

最终状态限制：上述 Java 通过后，又删除了不再适用的“存在 Token 文件则拒绝初始化 Worker proof 密钥”旧判断，并改写对应测试。对此最终差异的编译因 JVM 原生内存分配失败中止（`Native memory allocation failed`），未获得最终源码编译通过证据；未自动重跑，等待后续有授权且内存恢复的编译。故前述通过只适用于这两处最后修改之前，不能称整个最终 HEAD 已编译通过。

首轮审查修复：成功替换后退役不再被 DB 引用的旧文件；保存失败和回调取消清理未采用的新文件。删除前先查当前引用，避免误删已采用文件；清理失败明确报告。新增替换／失败清理／有效引用保护用例源码。对此实质修复进行一次低内存编译（192 MiB heap、SerialGC），仍因原生内存分配失败中止，未出现源码诊断，不能算通过；本轮停止编译重试，等待用户释放内存。静态复审可继续，归档交付前补齐最终编译证据。

令牌文件采用新旧分开的引用，避免旧数据库 generation 读到新一代 Token。Plane 已有刷新 CAS、账号归属和精确租约仍保留；旧模型密文验钥兼容逻辑删除，Worker proof 密钥逻辑保留。复用的秘书 Python 文件需随同机安装保留。

未安装依赖、运行测试、服务、迁移、浏览器或真实 OAuth/模型/API；V50、实际 DPAPI/文件锁、登录导入刷新及页面状态仍待运行验收。源码编译与静态复审不代表已登录成功。

## Sources

- docs/FUNCTION-INVENTORY.md, 18.
- 用户要求：完成剩余优化，逐个使用 fd-workflow auto，并遵守 Small Software。
