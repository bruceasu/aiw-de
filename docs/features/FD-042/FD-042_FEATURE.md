# FD-042: 平台页面统一强制登录

**Status:** Design
**Revision:** 2
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户明确要求平台页面都需登录，覆盖此前会话保留普通页面免登录的选择。当前WebConsoleSecurityService.authenticated在auth.enabled=false时返回true，role/username回退ADMIN/local-console；因此普通/ui、Goal Workshop等仍可匿名使用。个人授权等少数页面已强制真实会话，但策略不统一。事实来自静态源码，不是浏览器复现。

## Options and decision

仅将开关默认值改true仍能通过false绕过，不满足要求。选择取消匿名模式：登录判断永远基于signedIn，enabled兼容方法永远true，旧boolean构造参数保留但忽略，不再从启用配置获得匿名授权。application.yaml移除启用变量。保持真实用户名/非空密码、会话、CSRF和角色机制，无新账号或凭证生成。

控制台拦截器统一要求真实会话，免登录仅保留/ui/login及现有console.css/console.js公共资源。未登录页面GET/HEAD返回登录跳转，保留contextPath、原path/query（含组合角色view参数）；现有控制台JSON/文件API或其他方法未登录返回401。登录模板的form action和CSS href同时改为Thymeleaf上下文感知URL，避免非空contextPath的登录提交中断；其他已登录CSRF/角色/Owner校验保持。登录页始终开放；空密码拒绝全部登录尝试。

## Solution

WebConsoleSecurityService移除匿名ADMIN/local-console判定：authenticated=signedIn，未登录username为空、role为非特权VIEWER，actor/高权限动作不接受匿名；currentOperatorAuthority只认可已配置真实账号及当前operator角色，不再认可旧匿名actor来源。正常真实账号authority摘要保持原算法。登录/退出及持久人工认证锁存不删除。

WebConsoleSecurityInterceptor合并重复的持久锁存/普通登录判断，所有已注册控制台路径采用统一会话策略。原路径注册表保持，不把/api/r1整根套入浏览器认证，Worker租约和个人秘书机器接口仍用原token/专用凭证，未新增匿名豁免或机器登录要求。

兼容与上线：旧auth.enabled及AIW_WEB_CONSOLE_AUTH_ENABLED不再控制访问，即使外部旧配置为false也需登录。原四参数Java构造接口保留用于兼容，但首boolean明确忽略。旧免登录actor数据仍保留原Owner/actor，不迁移、不删除、不自动认领；旧匿名来源operator授权不能继续被解释为有效真实授权。未配置密码时只展示登录提示，不创建默认密码或绕过认证。回退旧版本会恢复其可选认证语义：回退前必须对旧版本显式设置AIW_WEB_CONSOLE_AUTH_ENABLED=true，保留用户名/非空密码，并由部署方确认匿名请求仍被拒绝，不能直接沿用false或缺省。正常部署/重启或回退另行处理，本FD不操作环境或读取.env秘密。

修改现有安全回归断言及Operations/Approval夹具，使其用测试凭据真实登录；仅源编辑，不运行测试。更新当前中英文配置/快速启动/安全指南及相关稳定规范，历史FD证据不改。

## Scope

仅控制台Service/Interceptor/application配置、相邻安全测试夹具和当前文档/规格、FD证据。保留AuthorityHttpConfiguration等其他任务脏文件，保留机器凭据及旧REST各自边界，不扩展为整个REST认证重构。主工作区范围提交，沿用会话授权，不创建worktree。Auth策略变更先做有界独立设计审阅，然后独立实现审阅。

## Work items

- [x] 1.1 完成登录统一策略、机器边界/旧数据/回退与配置兼容并取得独立设计审阅。规模小、难度中，无依赖；明确用户新政策优先，不需重复确认。
- [ ] 1.2 移除Service匿名认证/ADMIN/actor授权和配置开关，保持构造/真实账号摘要兼容。规模小、难度低，依赖1.1；false/空会话也不能获得已认证状态或高权限。
- [ ] 1.3 统一拦截器页面/API未登录分流与原query返回，保留登录资源/上下文感知模板URL/CSRF/角色和机器分离。规模小、难度中，依赖1.2；无开放的新业务页面。
- [ ] 1.4 更新安全回归与受影响的Operations/Approval测试夹具，使用真实测试登录；不执行或编译测试。规模小、难度中，依赖1.2–1.3；旧开关false不能绕过、登录资源/返回目标（含contextPath/query）和未登录API拒绝都有断言。
- [ ] 1.5 更新当前说明与稳定规格，完成一次范围静态检查及离线生产compile-only，提交双报告后独立fd-review。规模小、难度低，依赖全部实现；未运行项明确。

## TODO

独立设计审阅ready；contextPath登录模板和旧版本回退两处已补齐。继续实现、源码回归更新、静态/编译及独立实现审阅后范围归档。

## Acceptance

1. 所有/ui和/ui/**业务页面（含goals/roles等查询页）都需要真实登录；只有登录页及指定静态资源可匿名，false旧开关不能恢复匿名ADMIN（1.2–1.3）。
2. 未登录页面GET/HEAD进入登录页并保留原path/query及contextPath；控制台JSON/文件API及写请求返回401而非业务数据/登录HTML（1.3）。
3. 登录页无需开关，凭据未配显示提示且拒绝登录；真实登录后仍按原角色/CSRF/Owner执行，退出后恢复未登录（1.2–1.4）。
4. 机器路由/凭据/lease不新增浏览器会话要求；不自动迁移旧local-console数据或续用匿名operator授权，真实账号原权限摘要保持（1.2–1.3）。
5. 当前文档/稳定规格及源码测试反映新策略，静态/离线编译和真实未运行限制可审阅；独立实现通过后归档（1.4–1.5）。

## Verification

默认一次范围diff --check及只读配置/匿名分支/路由/调用者/文档/测试源码检查，随后C:/green/bin/setup-jdk25.ps1和mvn -o '-Dmaven.test.skip=true' compile，只编译生产源码，不执行/编译测试或下载。独立设计和实现静态审阅各按有界Skill授权进行，不派额外代理。未运行测试、服务/数据库、浏览器/HTTP、模型/Worker、打包/部署/重启；静态和compile不作运行通过证据。没有读取或打印.env、密码或机器凭证。独立设计报告：[Markdown](../reviews/FD-042-design-r1.md)及同名JSON，结论ready，原两处发现已解决。

## Sources

- Issue: none
- 用户本轮要求平台页面全部登录，优先于本会话此前保留普通页面免登录的选择。
- WebConsoleSecurityService/Interceptor/WebMvcConfiguration/ApiRoutes，WebConsoleAuthController、login模板、相邻测试与application.yaml、当前安全/配置指南。
- Worker与个人授权机器路径保持现有分离；仅静态核对，脏AuthorityHttpConfiguration不作为本FD提交。
- Planner/Worker Session：platform-login-20261024-37ba34c3；真实来源FD-042-000002-design-requested。
