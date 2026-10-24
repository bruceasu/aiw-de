# FD-046: Goal 删除请求绑定与统一确认弹窗修复

**Status:** Complete
**Revision:** 5
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户确认删除仍返回 Bad Request，且浏览器原生确认框不符合系统风格。静态证据：GoalController.Revision(long revision, boolean confirmed) 被删除/恢复/步骤分析/应用复用；页面这些请求遗漏 confirmed。pom 使用 Boot 4.0.8，本地依赖 BOM 指向 Jackson 3.1.5；javap 读取该 jar 的 DeserializationFeature 显示 FAIL_ON_NULL_FOR_PRIMITIVES 默认 true，缺失 primitive 布尔不能依赖旧版默认 false。此为与症状匹配的请求缺陷，未作真实 HTTP 复现。

## Options and decision

选择补齐前端已有请求字段，而非放宽全局 Jackson 配置或修改后端确认语义。确认框复用 console.js 的 aiwConfirm，不自行创建另一套样式。仅遵守既有接口，无公开接口、权限、存储或并发设计变化，不需独立设计审阅；仍需独立实现审阅。

## Solution

删除经明确确认后发送 confirmed:true；恢复、分析及应用发送 confirmed:false；正式化及准备执行保持各自既有显式确认值。同页全部 window.confirm 替换为 await aiwConfirm；模板先加载 console.js 再加载 goals.js。取消不写入，原权限、CSRF、revision、未知结果核对及编辑保护不变。

## Scope

仅 goals.js、goals.html、附近用户说明及本 FD 证据。沿用主工作区 develop 范围提交。无 Java/依赖/权限/数据库/协议变更。主要风险为漏掉复用 Revision 的调用或异步弹窗取消处理，逐一静态核对。无测试、浏览器、HTTP、数据库、重启或部署授权。

## Work items

- [x] 1.1 补齐所有 Revision 请求字段，核对 Controller 对应调用。小/低，无依赖，证据为请求字面量与 DTO。
- [x] 1.2 接入控制台统一异步确认弹窗，保持取消语义。小/中，依赖 1.1，证据为四处确认及脚本加载顺序。
- [x] 1.3 更新说明，静态检查和 JS 语法编译，提交 Worker 证据及独立审阅。小/低，依赖 1.2。

## TODO

1.1–1.3 已实现，git diff --check 与 node --check 通过；独立实现审阅 r1 通过，待 host 核对真实 receipt 后归档。实际 HTTP 结果未验证。

## Acceptance

1. 删除/恢复/分析/应用请求均有明确 revision 和 confirmed 值，避免遗漏 primitive 字段；正式化与准备执行保持真实确认（1.1）。
2. 页面四处确认均使用统一 aiwConfirm，删除为 danger 样式，取消不提交、不丢弃本地编辑（1.2）。
3. console.js 先于 goals.js 加载，复用现有样式、转义和焦点管理；原删除核对/鉴权/CSRF/revision 不变（1.1–1.2）。
4. 文档及证据区分请求缺陷静态证据、实际检查和未验证运行结果（1.3）。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- 独立 Reviewer fd046-review-20261024-f371c8 已认领 FD-046-000004-implementation-ready，静态审阅 7afec60..bf4f65f 六文件及相关原调用链，无阻塞发现；报告 ../reviews/FD-046-review-r1.md 及同名 JSON。本轮失败审阅 0。Reviewer 未重跑编译或执行测试/浏览器/HTTP/数据库；实际通过以 CLI receipt 为准。

## Sources

- Issue: none
- 用户反馈、FD-045；GoalController.Revision 及对应路由；goals.js、goals.html、console.js、console.css；Boot 4.0.8 本地 BOM 与 Jackson 3.1.5 DeserializationFeature 字节码。
- Session: goal-delete-binding-20261024-44a708；设计来源 FD-046-000002-design-requested。
