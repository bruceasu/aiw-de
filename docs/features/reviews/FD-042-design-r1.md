# FD-042 独立设计审阅 r1

<!-- aiw-data: FD-042-design-r1.json -->

结论：ready。统一真实会话、取消匿名ADMIN和保留机器认证分离的主方案可行。原稿两处兼容缺口已由Planner在本次审阅期间修正，并经定向静态核对解决；没有剩余设计阻断。

- 独立 Reviewer Session：`fd042-design-20261024-c6f0ba07`；Planner：`platform-login-20261024-37ba34c3`。
- 来源：`FD-042-000002-design-requested`，由Planner领取。本次不claim/emit，不作实现判定。
- 初始设计：`110bee2` 中 `docs/features/FD-042/FD-042_FEATURE.md`；最终另定向核对该FD工作区的DR-1/DR-2修订。未将其他任务的脏改动作为待交付内容。

## 原设计发现及本次解决

| ID | 位置与事实 | 影响及建议 |
| --- | --- | --- |
| DR-1 | FD第16、47行承诺保留contextPath及原目标。现有 `login.html` 的CSS href和form action均为根绝对路径 `/ui/...`。 | 非空contextPath下，拦截器即使正确跳转到该上下文的登录页，表单仍会提交到根 `/ui/login`，不能完成承诺的登录返回流程。把登录模板的action/href改为Thymeleaf上下文感知链接，并纳入源码回归断言；不必重构其他页面或执行运行验证。 |
| DR-2 | FD第24行声明上线兼容，第34行列了回退结果，但尚未定义回退到旧代码时旧启用开关的策略。旧Service默认 `auth.enabled=false`，application.yaml原默认也为false。 | 回退若只撤回新代码，会恢复匿名访问，违背本轮已确认政策。明确回退旧版本时必须显式保留旧认证开关为true及非空凭据，或禁止回退到无法保持强制登录的配置；操作仍由部署方执行，本FD不修改.env或启动环境。 |

最终定向复核：FD已将登录模板action/href明确为Thymeleaf上下文感知链接，并将contextPath/query返回目标纳入源码断言；回退条款明确在旧版本启动前必须保留旧开关为true及用户名/非空密码，并由部署方验证匿名拒绝。两项均已解决，不需用户再批准已经明确的登录政策；没有扩大REST认证或运行授权。

## 已支持的设计边界

- 不只改默认值，而是让authenticated仅依赖signedIn、enabled兼容入口恒为true，四参数构造的旧boolean不再授予匿名权限；移除local-console/ADMIN回退及旧匿名operator来源，保持真实账号摘要算法。
- `/ui/login`及指定静态资源保持可访问，未配密码仍拒绝认证，因此不会为解除锁定创建默认密码或匿名入口。
- WebMvc现有注册表覆盖/ui及/ui/**、显式ConsoleApiRoutes；页面GET/HEAD可跳转，JSON/文件API返回401而非登录HTML，现有已登录CSRF/角色/Owner规则保留。
- 已提交AuthorityHttpConfiguration在普通浏览器分支之前识别机器租约及个人专用凭证，当前设计不改路由注册或将/api/r1整根纳入浏览器会话，因此机器身份分离有现有基础。
- 旧actor/Owner记录不迁移、不删除，避免把旧匿名数据隐式改属当前账号；旧匿名operator授权不再认可属于已明确的新认证政策。
- Security/Operations/Approval夹具确实存在原true/false配置，改为真实测试登录并添加false不能绕过等断言属于本改动的直接维护。测试只编辑、未执行，不能标为通过。

## 实际检查与限制

执行了三次定向只读发现批次：读取根/四核心/工管/规划与Java规则、FD；读取Service/Interceptor/WebMvc/ApiRoutes/AuthController/login模板及固定提交的Authority路由；读取WebConsole相关Advice、actor调用者及两个受影响测试夹具。使用定向rg/rg --files与git show。第三批输出少量截断，未把截断的辅助内容当成完整审计；两项发现依据的FD、模板及旧Service内容均完整读取。

没有执行测试、编译、浏览器、HTTP、服务、数据库、模型、网络或其他代理，没有读取.env或凭证，没有改实现、FD、回执或工作流。本次仅写独立设计双报告；未提交或归档。在Planner告知修正后，只补读FD中Thymeleaf、回退旧开关及contextPath/query的对应行，记录当前FD摘要；未运行任何行为验证。

剩余风险：认证会话/CSRF/退出、非根上下文完整交互及机器领取仍需实际运行证据；现有静态材料不能证明这些行为已通过。本次DR-1/DR-2已解决，可进入design-ready；进入实现后仍须由独立fd-review评估实际源码和证据。
