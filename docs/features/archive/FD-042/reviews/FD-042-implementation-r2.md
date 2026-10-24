# FD-042 独立实现审阅 r2

<!-- aiw-data: FD-042-implementation-r2.json -->

结论：**通过本次定向静态复核**。R042-01和R042-02已解决，未发现本轮修正引入的新阻断问题或需修改的结构异味。本结论结合r1未变化部分的静态审阅，不代表测试、HTTP、浏览器或真实会话已经运行通过。

- Reviewer Session：`fd042-review-20261024-b92afa6d`，独立于Worker `platform-login-20261024-37ba34c3`。
- 已领取新来源：`FD-042-000006-implementation-ready`；领取前为pending Reviewer、Revision6，FD摘要一致。
- 总源码范围：`a654adf..d7bf98d53747c59970796b6db9605facff78218a` 的原20个实现路径；定向修正：`1f5eb6a..d7bf98d` 的7个相关路径。FD和历史证据提交不混称为源码文件。
- Worker r2双报告及证据提交：`e6c6f38`。r1双报告保留，当前周期实现审阅失败次数仍为1，未重置。

## 两项发现的复核

| 发现 | 修正及静态结论 |
| --- | --- |
| R042-01 / P1 | `WebConsoleSecurityInterceptor.consolePath` 使用已有解析RequestPath，缺少缓存时用RequestPath.parse，按PathSegment.valueToMatch分类。原raw URI范围return true已删除，作用范围由原MappedInterceptor注册决定。公共例外及page/API分流不会因编码或matrix别名而走提前放行。登录next逐段UriUtils.encode，保留段分隔及原query/contextPath，避免编码首段被旧safeNext拒绝或编码#变成片段。新增matrix/编码页面、编码API、编码路径字符及缓存路径断言；机器回归改为实际MappedInterceptor不匹配，不再依赖直接调用未注册gate。已解决。 |
| R042-02 / P2 | S1、Operations和Approval三个新增BeforeEach终点均改为实际 `/ui/plans`，安全场景登录后直接读取该受保护落地页。现有controller的 `/ui`→`/ui/plans`链与新setup一致，原身份/CSRF/VIEWER及actor断言保留。已解决。 |

`UriUtils.encode`按段处理后再用原分隔元素拼接target，原query只作为query附加；门禁匹配路径与可重定向URI分别构造。它不会把路径中的解码字符直接写成新的query、fragment或分段。原登录Controller、控制台注册表及其他机器认证路径均未改。

本轮只检查两项修复及影响，未重做无变化部分：r1已确认的signedIn-only、旧flag失效、空密码拒绝、匿名actor/权限拒绝、真实operator摘要、原CSRF/角色/Owner与旧记录归属保持，结合修正后满足当前FD五项验收的静态证据要求。没有数据迁移、凭据生成、部署或重启。

S1/Operations正文仍有比较基线已有的旧Dashboard/Runs期待，这不是本轮新增setup错误；已在Worker报告记为历史测试限制。本审阅未要求恢复全部旧业务流程，也没有把源码夹具或生产编译说成整套测试通过。

## 实际检查

读取r2双报告及真实回执，核对摘要后以原独立session精确claim新事件；定向读取固定修正差异。首次未限定路径的diff也包含历史FD/报告提交，后续按r1原20个实现路径限定，确认实际修正7文件，未将元数据变动混入源码结论。

一次静态批次执行 `git diff --check 1f5eb6a d7bf98d -- <7个修正路径>`，只读比较原20路径的当前工作区与固定提交，核对注册表/登录Controller保持、解析门禁和三处新增setup终点。全部通过。复用r1本地Spring7.0.9解析/匹配及MappedInterceptor签名证据；本轮未重跑框架、javap、HTTP或测试。

采纳Worker因相关Java修正而更新的生产compile记录：`mvn -o '-Dmaven.test.skip=true' compile`，修订d7bf98d，JDK25.0.1/Maven3.9.4，2026-10-24T15:13:33+09:00成功，资源2+143、300生产源。Reviewer未重跑；未编译/执行测试，不证明登录行为。

## 未运行、风险与下一步

未运行测试/测试编译、HTTP/浏览器/真实session/Thymeleaf、机器lease、数据库/服务/网络、模型/Worker、打包/安装/部署/重启或回退。真实登录、CSRF、退出、context及机器链路效果仍需运行证据；当前静态通过不作生产发布证明。旧版本回退的显式旧enabled=true及非空凭据要求保持。

工作区脏AuthorityHttpConfiguration、ConfigurationUiOptions、旧模板、秘书代码和桌面脚本排除，没有因为共享工作区compile获得独立批准。本Reviewer未修改实现、提交、合并、归档或发布。

宿主PM应核对真实verification-passed回执、当前FD摘要及原20个实现路径未漂移后，按授权完成限定范围本地归档。
