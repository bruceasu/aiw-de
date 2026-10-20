# FD-023 API r1 统一实现

<!-- aiw-data: FD-023-implementation.json -->

- Worker：`01a15a47-fc32-7362-ac13-f660487b7632`；已 claim 源事件 `FD-023-000003-design-ready`。
- 基线：`6f34bf4`；源及文档实现至 `806fac9`；工作区 `.wt/FD-023`，父分支 develop。
- 全部5个实现项完成，请求独立实现评审；尚未本地交付/归档。

## 修改事实及静态证据

所有生产 JSON/文件 API 使用 `/api/r1`。既有 r6/r7/r8 和未编号路径统一；Worker 使用 `/api/r1/worker-protocol/v1`；原 console API、workflow-plans、plan-editor 和问题/文件 API 迁出 UI。Goal/Secretary/Delegation/WorkflowEditor 混合 controller 去除类 `/ui`，HTML 页面与 redirect form 显式保留原 `/ui` 路径。未引入旧路由 alias 或重定向。

WebConsoleApiRoutes 集中六个 console API 根，原安全拦截器覆盖它们并 order(-100)，保留登录、CSRF、actor、role 属性与 owner 校验。通用 DelegationManagementMvc 在清除人工上下文和登记 legacy receipt 之前排除这六根，避免首次启用自身请求阻挡，以及 Approver 被通用 Operator 检查误限。其余管理 API 的首次启用持久认证和审批角色模式同步新前缀；Worker bearer 和额外专用身份路径同步。所有分类保留 context-path 扣除。

在库 TS/JS/Python/Go/Java Worker、部署示例、浏览器脚本/模板、当前 Java IT 及独立源测试中的 URL 已同步。当前中文 API/操作文档、Worker README/传输说明、worker-pull 稳定规范同步；新增 control-plane-api 稳定规则。没有改写历史 evidence 目录、覆盖 JSON、归档 FD 或二进制缓存，也不改测试类历史编号。独立设计评审 D1 补充的两个 console 根已纳入，不将该设计报告当实现通过。

最终静态扫描当前源/文档无旧命名空间。曾以带边界的自动替换遗漏 `${path}` 插值的 workflow-editor 基础地址，实际差异检查中补正，并再次做该改动文件 node --check 与针对性残留扫描；没有用错误的零匹配结果掩盖遗漏。

## 实际编译与检查

- 本地 JDK25 + Maven3.9.9：`mvn.cmd -o -DskipTests=true -Dmaven.test.skip=true compile`，261个生产源文件通过，无测试、下载或最终 jar。
- `NODE_PATH` 指向主工作区已安装依赖，`node scripts/compile-worker-runtime.mjs` 通过：process Worker TS 内存语法、runtime-core JS 语法、Python 内存 compile、Go 编译，脚本清理临时分发输出，无运行 Worker 或网络。
- demo Go：`GOPROXY=off GOSUMDB=off GOTOOLCHAIN=local go build -o NUL .` 通过。
- 修改的浏览器/JS 示例：node --check 通过；demo Python：内存 compile 通过；3个 demo TypeScript 文件使用既有 TypeScript transpileModule 内存语法编译通过。TS 命令初次 PowerShell -e 引号丢失，改用 stdin 后通过；不是 TypeScript 源错误。
- `git diff --check` 无错误，检查controller、会话/机器认证、调用方差异及残留；已额外检查插值基础地址，不仅依赖简单字符串边界扫描。

自动审批审查拒绝了 Java 示例单独 javac 编译及临时目录清理命令，返回 `blocked by policy`，没有提供更具体原因；整个命令未执行，也没有通过其他路径绕过。该示例仅修改一个URL字符串，其独立编译未验证；生产 Java 的同语言 compile 已通过。详见 Gate 报告。

未运行测试、typecheck、服务、HTTP、浏览器、网络、数据库迁移、最终打包、安装、push 或部署。测试 URL 更新不表示测试通过；实际路由注册、浏览器登录和 Worker 认证交互尚未运行时验证。外部客户端必须切换新地址，不保留旧 alias。

## 未验证与假设

假设调用方按现有配置提供应用根 baseUrl，并同步新 canonical 地址。JSON及服务调用未改。Spring实际路由组合、拦截器运行顺序和真实客户端认证兼容仅有源码证据；独立 Java 示例编译受自动审批限制。以上限制不描述为运行时通过，当前请求没有测试授权。
