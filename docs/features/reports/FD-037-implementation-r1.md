# FD-037 Worker 实现 r1

<!-- aiw-data: FD-037-implementation-r1.json -->

Session secretary-desktop-20261024-a6529740；真实来源 FD-037-000003-design-ready。比较基线244b62b，源码d1a5cf7，当前实现审阅失败0次。

免登录访问模式内部使用ADMIN，旧公共模板直接显示该角色导致误解。服务器已把真实signedIn传为consoleAuthEnabled；两个角色标签现按它决定显示真实角色或“未登录”，静态回退也是“未登录”。仅显示变化，不修改原免登录访问、内部角色/actor、权限按钮、登录/退出和自动刷新角色判定。同步附近安全说明。

fragments.html已有其它任务导航文案差异；根据HEAD生成仅含两处角色替换的补丁，git apply --cached后提交，原工作区导航差异完整保留。源码提交仅两个span与说明；其它秘书/Python/模型/启动器、配置/专业任务Java、authority及模板差异均排除。没有API、持久化或认证策略变化，不另做设计审阅。

实际读取原Service.signedIn/role、Interceptor真实请求属性、公共模板与登录入口、console.js角色刷新判定。范围git diff --check和最终diff检查通过。使用C:/green/bin/setup-jdk25.ps1（JDK25.0.1、Maven3.9.4）执行mvn -o -Dmaven.test.skip=true compile，通过，结束2026-10-24T12:52:44+09:00：生产资源已复制，Java up to date。环境恢复，脚本打印的package/run示例未执行。编译仅证明该目标成功，不代表模板表达式/渲染通过；其它工作区Java不纳入本FD审阅。

未运行测试、Thymeleaf/浏览器、真实登录/退出/HTTP、数据库、服务/模型、网络/依赖下载、最终构建/打包、安装、重启或发布。无未解决blocker；下一步独立Reviewer检查244b62b..d1a5cf7实际精确补丁与全部验收，通过后host正常归档。
