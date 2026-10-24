# FD-042 修正实现报告 r2

<!-- aiw-data: FD-042-implementation-r2.json -->

- Worker Session：`platform-login-20261024-37ba34c3`。
- 已领取来源：`FD-042-000005-changes-requested`。当前实现审阅失败次数1，未重置周期或测试预算。
- 总比较：`a654adf..d7bf98d`，仍为20个实现文件；本轮修正：`1f5eb6a..d7bf98d`，7文件。
- 保留r1 Worker/Reviewer历史；r1静态检查与编译不代替本轮修正和独立r2结论。

## 发现与修正

**R042-01 / P1**：原始getRequestURI与Spring路由解析不一致，别名可落入原范围return true。现改用已解析RequestPath，缺缓存时RequestPath.parse；逐段PathSegment.valueToMatch构造门禁分类路径。删除范围提前放行，范围完全由既有MappedInterceptor注册控制。公共登录资源/页面判断与实际解析路径一致。

登录next逐段UriUtils.encode构造规范URI，附原query及contextPath。编码前缀可回到正常/ui页面，路径中的编码#或分隔符不变成片段/另一段；矩阵别名返回规范页面，不声称原matrix表示原样保留。原登录Controller和路由表未改。

回归源码补matrix/编码页面别名、编码API、缓存解析路径、编码#返回目标。机器隔离不再错误地直接调用浏览器gate后期待true，而使用真实MappedInterceptor匹配断言机器路径不进入gate、不创建会话。仍未编译或运行测试。

**R042-02 / P2**：按现有WebConsoleController的/ui→/ui/plans重定向，三个新增BeforeEach.signIn终点改为/ui/plans。安全场景登录后直接读取受保护的/ui/plans；保留原401/CSRF/角色/actor断言。

当前S1/Operations正文仍引用旧Dashboard/Runs流程，属于修改前已有的历史测试限制，不因本次源夹具更新就宣称其整套用例可通过；本FD不重写全部旧业务流程测试。

## 实际证据

- 在真实changes-requested后核对回执、FD摘要及失败次数，使用原Worker Session精确claim；定向读取当前Controller确认/ui/plans真实落地。
- 针对新改动执行一次 `git diff --check 1f5eb6a -- <7文件>` 与只读解析路径/无rawURI提前放行/按段编码目标/三setup终点及新增源码回归检查，通过。未执行Spring、测试或HTTP。上一轮的全范围检查保留为其历史证据，未将它写成对修正的执行通过。
- Java已变，使用C:/green/bin/setup-jdk25.ps1后对d7bf98d重新执行 `mvn -o '-Dmaven.test.skip=true' compile`，JDK25.0.1/Maven3.9.4，2026-10-24T15:13:33+09:00成功；资源2+143、javac25编译300生产源。环境恢复。测试未编译/执行，无依赖下载/发行包；脚本打印的打包/启动示例未执行。这次由新源码修正触发，不是重复不变命令求通过。
- 仍未运行测试、HTTP/浏览器/真实session/Thymeleaf、机器lease、数据库/服务/网络、模型/Worker、部署/重启/回退。生产编译和回归源不能证明这些行为通过。

## 边界与下一步

所有r1范围、旧flag失效、机器凭证分离、真实角色/CSRF/Owner与旧匿名记录保留规则不变。未读取.env或真实凭证；其他工作区脏改动未混入。当前修正有静态/编译证据，R042-01/02的独立关闭结论等待Reviewer r2，下一步用新真实implementation-ready交接；不得借历史r1编译或源码断言自审通过。
