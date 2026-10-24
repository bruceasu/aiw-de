# FD-042 实现报告 r1

<!-- aiw-data: FD-042-implementation-r1.json -->

- Worker Session：`platform-login-20261024-37ba34c3`。
- 来源：`FD-042-000003-design-ready`；比较基线：`a654adf`；实现：`1f5eb6a`，20个源码/测试/配置/文档/规格文件。
- 独立实现审阅失败次数0，无待决设计问题。用户本轮全页面登录要求覆盖先前普通页面免登录选择；独立设计r1已ready，contextPath与旧版本回退两项建议已纳入。

## 改动和验收

1. **WI1.2**：`WebConsoleSecurityService` 的authenticated只认signedIn，enabled兼容方法恒true，保留四参数构造但忽略首boolean，用固定注入值而非旧配置。匿名username为空、role为VIEWER、actor拒绝、operator来源无匿名回退；真实账号authority摘要算法未改。application.yaml删除启用变量，用户名/非空密码及角色机制保持。
2. **WI1.3**：`WebConsoleSecurityInterceptor` 对全部已注册控制台路径统一会话门禁，仅login及指定console.css/js资源放行。页面GET/HEAD未登录重定向，保留contextPath及原path/query；现有控制台API/写请求返回401。已登录CSRF/角色和Owner链保持；原WebMvc/API路由注册和登录Controller未改。登录模板action/href增加Thymeleaf上下文感知属性。
3. **WI1.4**：更新`R9WebConsoleSecurityIT`，覆盖旧false开关、空密码/空会话、匿名权限、页面/API分流、contextPath/query、资源豁免/机器路径不创建浏览器会话、登录退出及原CSRF/VIEWER拒绝。Operations/Approval/S1源夹具显式用测试账号登录，S1保留cookie，Approval断言真实actor。测试只有源码修改，未编译或执行。
4. **WI1.5**：当前中英文配置/快速启动/安全/能力说明、README与control-plane-api/goal-discussion稳定规范同步。历史FD报告保持。旧匿名数据不迁移/删除/认领；回退旧版本必须明确设置旧enabled=true并保留非空凭据及部署验证，不能静默恢复匿名策略。

## 实际检查

- 一次范围静态批次：`git diff --check a654adf -- <20个范围文件>`及内联只读匿名分支、启用配置移除、context-aware登录URL、原路由注册/登录Controller逐字保持和当前文档措辞检查；审阅实际四个生产文件差异及测试源码。全部通过，未导入或执行生产代码、测试或脚本。
- `C:/green/bin/setup-jdk25.ps1`后执行一次 `mvn -o '-Dmaven.test.skip=true' compile`，JDK25.0.1/Maven3.9.4，2026-10-24T15:05:55+09:00成功。复制2+143资源，javac release25编译300生产源文件；环境恢复。未编译/运行测试、未下载、未生成发行包，未执行脚本打印的package/run示例。原GraphPlans unchecked提示未另行扩大检查。
- 发现只读S1夹具原先也依赖默认免登录，定向读取后加入真实VIEWER登录；这属于相同受影响控制台源夹具，不扩展运行范围。一次文本搜索因shell引号未匹配，改用简单/ui模式找到实际四个测试文件；无运行验证。

## 范围与未验证事项

未运行测试、测试编译、HTTP/浏览器/真实会话/Thymeleaf渲染、机器租约、数据库、服务/网络、模型/Worker、打包/安装/部署/重启或回退。静态及生产编译不证明这些行为通过，回归源码也不作为通过结果。

没有修改机器token/lease、整个REST边界、数据库表/迁移、真实配置凭据或.env；没有生成默认密码或把匿名历史分配给当前人。WebConsole注册范围仍仅原控制台页面/数据路径，其他REST保持各自边界。匿名角色VIEWER只作为非特权返回值，不等于认证成功。

主工作区原有AuthorityHttpConfiguration、ConfigurationUiOptions、旧configurations/fragments/questions模板、秘书Python/UI/模型/启动器和桌面编译脚本均排除，没有混入提交；共享工作区编译不等于批准其他改动。

下一步：独立fd-review检查20文件实际策略/路由/调用者/模板和测试源断言，宿主核对真实通过回执与范围后归档。没有部署或重启，当前运行实例尚未宣称采用新策略。
