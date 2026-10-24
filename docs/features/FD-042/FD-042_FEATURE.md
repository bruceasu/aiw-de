# FD-042: 平台页面统一强制登录

**Status:** Complete
**Revision:** 7
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户明确要求平台页面都需登录，覆盖此前会话保留普通页面免登录的选择。当前WebConsoleSecurityService.authenticated在auth.enabled=false时返回true，role/username回退ADMIN/local-console；因此普通/ui、Goal Workshop等仍可匿名使用。个人授权等少数页面已强制真实会话，但策略不统一。事实来自静态源码，不是浏览器复现。

## Options and decision

仅将开关默认值改true仍能通过false绕过，不满足要求。选择取消匿名模式：登录判断永远基于signedIn，enabled兼容方法永远true，旧boolean构造参数保留但忽略，不再从启用配置获得匿名授权。application.yaml移除启用变量。保持真实用户名/非空密码、会话、CSRF和角色机制，无新账号或凭证生成。

控制台拦截器依照Spring MVC解析后的PathSegment.valueToMatch统一要求真实会话，不用原始URI重判范围并提前放行（实际范围由原MappedInterceptor注册控制），免登录仅保留/ui/login及现有console.css/console.js公共资源。未登录页面GET/HEAD返回登录跳转，保留contextPath及原query（含组合角色view参数）；路径别名返回按段编码的规范页面，保留路径字符的编码语义；现有控制台JSON/文件API或其他方法未登录返回401。登录模板的form action和CSS href同时改为Thymeleaf上下文感知URL，避免非空contextPath的登录提交中断；其他已登录CSRF/角色/Owner校验保持。登录页始终开放；空密码拒绝全部登录尝试。

## Solution

WebConsoleSecurityService移除匿名ADMIN/local-console判定：authenticated=signedIn，未登录username为空、role为非特权VIEWER，actor/高权限动作不接受匿名；currentOperatorAuthority只认可已配置真实账号及当前operator角色，不再认可旧匿名actor来源。正常真实账号authority摘要保持原算法。登录/退出及持久人工认证锁存不删除。

WebConsoleSecurityInterceptor合并重复的持久锁存/普通登录判断，所有已注册控制台路径采用统一会话策略。原路径注册表保持，不把/api/r1整根套入浏览器认证，Worker租约和个人秘书机器接口仍用原token/专用凭证，未新增匿名豁免或机器登录要求。

兼容与上线：旧auth.enabled及AIW_WEB_CONSOLE_AUTH_ENABLED不再控制访问，即使外部旧配置为false也需登录。原四参数Java构造接口保留用于兼容，但首boolean明确忽略。旧免登录actor数据仍保留原Owner/actor，不迁移、不删除、不自动认领；旧匿名来源operator授权不能继续被解释为有效真实授权。未配置密码时只展示登录提示，不创建默认密码或绕过认证。回退旧版本会恢复其可选认证语义：回退前必须对旧版本显式设置AIW_WEB_CONSOLE_AUTH_ENABLED=true，保留用户名/非空密码，并由部署方确认匿名请求仍被拒绝，不能直接沿用false或缺省。正常部署/重启或回退另行处理，本FD不操作环境或读取.env秘密。

修改现有安全回归断言及Operations/Approval及只读S1夹具，使其用测试凭据真实登录；仅源编辑，不运行测试。更新当前中英文配置/快速启动/安全指南及相关稳定规范，历史FD证据不改。

## Scope

仅控制台Service/Interceptor/application配置、相邻安全测试夹具和当前文档/规格、FD证据。保留AuthorityHttpConfiguration等其他任务脏文件，保留机器凭据及旧REST各自边界，不扩展为整个REST认证重构。主工作区范围提交，沿用会话授权，不创建worktree。Auth策略变更先做有界独立设计审阅，然后独立实现审阅。

## Work items

- [x] 1.1 完成登录统一策略、机器边界/旧数据/回退与配置兼容并取得独立设计审阅。规模小、难度中，无依赖；明确用户新政策优先，不需重复确认。
- [x] 1.2 移除Service匿名认证/ADMIN/actor授权和配置开关，保持构造/真实账号摘要兼容。规模小、难度低，依赖1.1；false/空会话也不能获得已认证状态或高权限。
- [x] 1.3 统一拦截器页面/API未登录分流与原query返回，保留登录资源/上下文感知模板URL/CSRF/角色和机器分离。规模小、难度中，依赖1.2；无开放的新业务页面。
- [x] 1.4 更新安全回归与受影响的Operations/Approval及只读S1测试夹具，使用真实测试登录；不执行或编译测试。规模小、难度中，依赖1.2–1.3；旧开关false不能绕过、登录资源/返回目标（含contextPath/query）和未登录API拒绝都有断言。
- [x] 1.5 更新当前说明与稳定规格，完成一次范围静态检查及离线生产compile-only，提交双报告后独立fd-review。规模小、难度低，依赖全部实现；未运行项明确。

## TODO

WI1.1–1.5已完成。R042-01/02修正经独立r2静态复核通过，无剩余阻断；宿主待核对真实通过回执、FD摘要和实现范围后本地归档。测试/真实登录/浏览器/机器运行仍未验证，未部署或重启。

## Acceptance

1. 所有/ui和/ui/**业务页面（含goals/roles等查询页）都需要真实登录；只有登录页及指定静态资源可匿名，false旧开关不能恢复匿名ADMIN（1.2–1.3）。
2. 未登录页面GET/HEAD进入登录页并保留规范路径、原query及contextPath；控制台JSON/文件API及写请求返回401而非业务数据/登录HTML（1.3）。
3. 登录页无需开关，凭据未配显示提示且拒绝登录；真实登录后仍按原角色/CSRF/Owner执行，退出后恢复未登录（1.2–1.4）。
4. 机器路由/凭据/lease不新增浏览器会话要求；不自动迁移旧local-console数据或续用匿名operator授权，真实账号原权限摘要保持（1.2–1.3）。
5. 当前文档/稳定规格及源码测试反映新策略，静态/离线编译和真实未运行限制可审阅；独立实现通过后归档（1.4–1.5）。

## Verification

实现1f5eb6a，比较基线a654adf，共20源码/测试/配置/当前说明和规格文件。Worker双报告：[Markdown](../reports/FD-042-implementation-r1.md) / [JSON](../reports/FD-042-implementation-r1.json)。一次范围diff --check及只读匿名分支/退役配置/context-aware登录URL/原路由与登录Controller/当前文档检查通过；测试只编辑和静态审阅。

C:/green/bin/setup-jdk25.ps1后mvn -o '-Dmaven.test.skip=true' compile一次成功，2026-10-24T15:05:55+09:00，资源2+143、javac25编译300生产源；环境恢复。没有测试编译/执行或依赖下载，未执行脚本打印的打包/启动示例。

未运行真实HTTP/浏览器/会话/Thymeleaf、机器lease、数据库、服务/网络、模型/Worker、打包/部署/重启/回退；静态/生产编译/回归源码不当作运行通过。没有读取.env或实际密码/token；其他任务脏改动排除。

独立设计[r1](../reviews/FD-042-design-r1.md)及同名JSON为ready，contextPath模板与旧版本回退两个问题已解决。没有其他未决设计Gate。

独立Reviewer Session fd042-review-20261024-b92afa6d 已领取FD-042-000004-implementation-ready，审阅a654adf..1f5eb6a的20文件及相邻路由/处理器/本地Spring7.0.9解析与匹配class实现。结论要求修改：R042-01（P1门禁绕过）与R042-02（P2新增测试setup错误）。一次范围静态一致性检查通过，但不消除控制流缺陷；没有运行框架请求、测试或编译。双报告：[Markdown](../reviews/FD-042-implementation-r1.md) / [JSON](../reviews/FD-042-implementation-r1.json)。这是本轮第1次实现审阅失败；该段保留r1历史，后续修复及复核见下。


独立r2：同一Reviewer Session fd042-review-20261024-b92afa6d 领取新来源FD-042-000006-implementation-ready，定向复核1f5eb6a..d7bf98d的7个修正路径，结合原a654adf基线20个实现路径。R042-01已用解析路径段分类、删除raw URI提前放行、按段编码next并补MappedInterceptor回归；R042-02已修正三个新setup终点及安全场景落地页。结论静态通过，两发现已解决，无新增阻断。失败次数保持1，不重置。

一次定向diff --check/20路径漂移与旧注册表/登录Controller保持检查通过。采纳Worker在d7bf98d上的新生产compile成功记录：2026-10-24T15:13:33+09:00，资源2+143、300生产源；Reviewer未重跑。测试未编译/执行；旧S1/Operations正文业务流程期待仍是已记录的历史限制，不宣称整套测试通过。r2双报告：[Markdown](../reviews/FD-042-implementation-r2.md) / [JSON](../reviews/FD-042-implementation-r2.json)。宿主需核对真实事件和范围后归档。

## Sources

- Issue: none
- 用户本轮要求平台页面全部登录，优先于本会话此前保留普通页面免登录的选择。
- WebConsoleSecurityService/Interceptor/WebMvcConfiguration/ApiRoutes，WebConsoleAuthController、login模板、相邻测试与application.yaml、当前安全/配置指南。
- Worker与个人授权机器路径保持现有分离；仅静态核对，脏AuthorityHttpConfiguration不作为本FD提交。
- Planner/Worker Session：platform-login-20261024-37ba34c3；真实来源FD-042-000002-design-requested。
