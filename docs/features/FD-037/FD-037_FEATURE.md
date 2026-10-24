# FD-037: 未登录控制台身份显示为未登录

**Status:** Open
**Revision:** 3
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户反馈未登录时右上角显示 Admin，应显示“未登录”。代码事实：免登录访问模式的内部角色为 ADMIN；Interceptor 已通过 consoleAuthEnabled 传入真实 signedIn。公共模板页头和侧栏直接输出 consoleRole，混淆登录状态与内部访问角色。未做浏览器复现。

## Options and decision

采用已有真实 signedIn 属性控制两处角色文字，未登录显示“未登录”，登录后显示实际 consoleRole。保留原免登录访问/权限策略。无需 API、持久化或安全策略变化，不需额外设计审阅；最终独立实现审阅仍必需。

## Solution

仅修改 fragments.html 两处角色表达式与静态回退文字。consoleAuthEnabled 已是 signedIn，会话真实状态决定展示，不猜测用户名或只看 auth.enabled。登录/退出按钮、内部角色、local-console actor 与自动刷新判定原值不变。

fragments.html 目前存在其它任务的导航改动，本 FD 只暂存两处角色标签精确补丁。补充附近说明。执行范围静态检查与离线 Maven compile；编译/资源复制不代表 Thymeleaf 或浏览器运行通过。

## Scope

延续主工作区范围提交，限角色文字、附近说明和本 FD 证据。保留其它导航、秘书/Python/任务准备/配置/模型的并行差异，不暂存或恢复它们。不运行测试、浏览器、数据库、服务、网络、下载、打包、安装、重启或发布。

## Work items

- [x] 1.1 修正公共页头与侧栏角色标签，精确保留并行模板差异。规模小、难度低，无依赖；证据为两处条件与原 signedIn 属性对应。
- [x] 1.2 更新说明，提交编译及静态 Worker 双证据。规模小、难度低，依赖 1.1；独立 Reviewer 结论属于下一交接阶段。

## TODO

1.1–1.2 已完成，d1a5cf7 两处标签与说明已范围提交；待独立实现审阅，通过后归档。

## Acceptance

1. 未登录时页头与侧栏角色文字显示“未登录”，静态回退文字也不为 ADMIN（1.1）。
2. 已登录显示实际角色，登录/退出入口继续与真实 signedIn 一致（1.1）。
3. 保留其它页面免登录访问、内部角色/actor、权限按钮与自动刷新逻辑（1.1）。
4. 不提交并行导航及其它任务改动；明确编译不代表浏览器/模板运行通过（1.1–1.2）。

## Verification

只读检查原 Interceptor/Service、公共模板、登录/退出及自动刷新使用的原角色。执行一次范围 git diff --check。使用 C:/green/bin/setup-jdk25.ps1 后离线 mvn -o -Dmaven.test.skip=true compile，不下载/跑测试或打包。没有修改 Java；当前工作区其它 Java 即使参与编译，也不属于本 FD 交付/审阅范围。Thymeleaf/浏览器、真实登录退出、数据库和服务均不运行。

## Sources

- 用户要求；fragments.html；WebConsoleSecurityInterceptor.preHandle；WebConsoleSecurityService.signedIn/role；docs/current-zh/09-SECURITY-AUTHORITY-AND-TRUST-BOUNDARIES.md。
- Planner/Worker Session secretary-desktop-20261024-a6529740；来源 FD-037-000002-design-requested；无关联 Issue。

- Worker 双证据：../reports/FD-037-implementation-r1.md/.json；范围静态 diff --check 通过，mvn -o -Dmaven.test.skip=true compile 通过（JDK25，2026-10-24T12:52:44+09:00，无需重编 Java，生产资源复制完成）。实际源码 d1a5cf7；模板其它导航差异未提交，服务/浏览器/真实登录和测试未运行。
