# FD-042 独立实现审阅 r1

<!-- aiw-data: FD-042-implementation-r1.json -->

结论：**要求修改**。发现一项登录门禁绕过和一项新增测试夹具错误；未发现需要另行提出的结构异味。正常规范路径上的强制会话策略基本符合设计，但当前不能满足“所有平台页面需要登录”的验收。

- Reviewer Session：`fd042-review-20261024-b92afa6d`，独立于 Worker `platform-login-20261024-37ba34c3`。
- 已领取来源：`FD-042-000004-implementation-ready`，领取前为 pending Reviewer、Revision 4，FD 摘要一致。
- 审阅比较：`a654adf..1f5eb6a1c78939c87da91d8db6bed57de2ade9ae`，20个实现文件；Worker证据提交 `832531b`。
- 当前实现审阅失败次数：1。本报告不提交、合并、归档或改变运行服务。

## 硬性发现

### R042-01 / P1：原始 URI 的二次范围判断可绕过已注册的控制台门禁

位置：`src/main/java/me/asu/aidev/webconsole/security/WebConsoleSecurityInterceptor.java:25`–`28`；相关处理器 `webconsole/protocol/http/WebConsoleController.java:75`–`82`。

当前先从 `getRequestURI()` 取得原始路径，再以精确 `/ui` 或 `/ui/` 前缀判断page；两者不符且不匹配原始API前缀时直接 `return true`，发生在signedIn检查之前。请求 `/ui;v=1/workers` 在此判断中不是page，因而直接放行。Spring MVC匹配解析后的路径：本地BOM使用Spring7.0.9，缓存spring-web JAR的只读反汇编显示 `DefaultPathContainer.decodeAndParsePathSegment` 先剥离`;`参数并进行URI解码，`HTTP_PATH`启用此解析，`LiteralPathElement.matches`以 `PathSegment.valueToMatch()` 匹配。因此该地址仍对应 `/ui/workers`；其控制器直接读取fleet并返回页面，没有第二个会话检查。编码首段如 `/%75i/workers` 存在同类分类差异。

这不是HTTP复现结果，而是应用控制流和本地框架实现共同支持的静态缺陷。它违反FD验收1及control-plane-api的Mandatory browser console sessions要求。

最小修正：既有MappedInterceptor注册表已经决定浏览器门禁作用范围，不应再用原始URI重新提前放行。公共资源例外及page/API分类应使用与MVC一致、保留分段语义的解析路径，例如已解析RequestPath的pathWithinApplication与PathSegment.valueToMatch；无缓存测试请求可用相同RequestPath.parse语义。避免仅用PathContainer.value/getCachedPathValue误当成已去参数字符串。补充matrix参数/编码别名的源码回归。机器不进入浏览器门禁应通过实际MappedInterceptor注册匹配来证明，不能继续直接向gate传未注册URI并期待true；后者依赖了错误的执行上下文。

### R042-02 / P2：新增登录夹具断言使用过时重定向终点

位置：`src/test/java/me/asu/aidev/webconsole/R9S1WebConsoleIT.java:63`、`R9WebConsoleOperationsIT.java:72`、`R9WebConsoleApprovalIT.java:86`。

三个新@BeforeEach都用自动跟随重定向的HttpClient登录，随后断言最终URI为`/ui`。但当前固定提交的 `WebConsoleController.dashboard:54`–`56` 明确把 `/ui` 重定向到 `/ui/plans`。正确登录也会继续跟随，因此这些新setup会在进入测试正文前失败。这违反WI1.4要求受影响夹具使用可行真实登录流程；不是未授权运行的推测性测试要求。

最小修正：将新登录setup核对为实际受保护落地页/真实登录状态，保持身份、CSRF、VIEWER拒绝和审批actor断言。部分旧正文仍含历史Dashboard/旧Runs展示假设，这些在比较基线已存在；本次不要求重构全部历史流程或执行整个测试套件，但不能新增必失败的setup或把未运行源码当作通过证据。

## 其他验收核对

| 验收 | 静态结论 |
| --- | --- |
| 1. 全平台页面真实登录 | 未满足，R042-01存在路径分类绕过；正常规范路径无旧false/匿名ADMIN回退。 |
| 2. 页面/API分流及返回目标 | 规范路径GET/HEAD、API401、contextPath/query与模板action/href有支持；别名路径受R042-01影响。 |
| 3. 登录凭据、退出、角色和CSRF | 空密码拒绝、login豁免、原登录/退出Controller和已登录CSRF/角色逻辑保持；测试尚未运行，新夹具有R042-02。 |
| 4. 机器边界、旧数据与authority摘要 | 原路由注册表/机器凭据/lease未改；真实账号operator摘要原表达式保持，匿名来源不再认可，未增加数据迁移。 |
| 5. 当前说明、回归源与证据 | 当前文档/规范已同步且未虚构运行；新增夹具需修正，不可归档为完成。 |

匿名role返回VIEWER只是不带操作权限的值，authenticated仍为false，actor也会拒绝；enabled恒true且旧配置已删除，正常路径没有开关绕过。私有数据归属和旧版本回退约束已在文档说明。登录模板上下文感知改动与返回目标保留合理。未把整个REST根纳入浏览器认证。

## 实际命令与范围

读取仓库/核心/工管/fd-review和Implementation review方法，FD/Worker双报告/真实回执；生成独立session并精确claim。定向 `git diff --stat`、`git diff a654adf 1f5eb6a`、`git show`及rg检查固定源码、相邻处理器和路由。首批输出截断后只补读关键缺失段。

为确认路径匹配语义，仅读取本地Spring Boot4.0.8 BOM并用本机javap反汇编缓存Spring7.0.9的DefaultPathContainer、PathContainer.Options和LiteralPathElement。该操作读取class文件，没有运行框架请求、应用方法、生产模块或模型；没有网络/下载。按宿主明确提出的修正API问题，另做一次javap -public签名读取，确认Spring7.0.9支持MappedInterceptor(String[], String[], HandlerInterceptor)及matches(HttpServletRequest)；这是有界API发现，不是行为验证。

一次静态检查批次执行 `git diff --check a654adf 1f5eb6a`，只读比较20文件与当前工作区、原WebMvc/ApiRoutes/login Controller逐字保持，核对本地字节码与当前说明。检查通过仅表示范围/空白/文本一致，**不消除上述控制流缺陷**。

采纳Worker已记录的生产compile证据：`mvn -o '-Dmaven.test.skip=true' compile`，JDK25.0.1/Maven3.9.4，资源2+143、300生产源、2026-10-24T15:05:55+09:00成功。Reviewer未重跑；该命令没有编译/运行测试，也不证明登录行为。

## 未运行与下一步

未运行测试/测试编译、HTTP、浏览器、真实会话/Thymeleaf、机器lease、数据库、服务/网络、模型/Worker、打包/安装/部署/重启或回退。工作区脏AuthorityHttpConfiguration、ConfigurationUiOptions、旧模板、秘书代码和桌面脚本排除，不因共享工作区compile获得独立通过。

Worker应仅修复R042-01/02，更新对应回归源码与实际证据后通过真实Worker事件再次交接审阅。当前不允许用静态检查或生产编译替代未满足的登录门禁验收。
