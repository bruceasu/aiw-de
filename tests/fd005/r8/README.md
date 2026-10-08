# FD-005 r8 独立离线测试

当前仅已准备，尚未执行。所有行为期望来自 FD、公开声明/契约、角色 TOML 与公开 SDK 类型。首轮 tests/fd005 与 evidence-r5 保持原样；本轮引用原 cases.mjs 保留 222 全分母，再增加独立 API/SDK/CP/插件/Workspace 行为。

## 绑定与唯一命令提案

- Tester session：fd005-tester-20261005-7865d383。
- 源事件：FD-005-000009-implementation-ready；已领取。
- Tested FD revision：9。
- Tested FD digest：6d51664531db4418fe20c5f05dbd0ddf797ce64f8689e48d096221970330627b。
- cwd：D:/03_projects/AI-tools/aiw-dev。
- 命令：`node tests/fd005/r8/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r8.json`。
- 只有 Planner 双授权（.md/.json）绑定以上事件、revision、digest、session、精确命令与全部调用文件 SHA-256 后，才执行一次。不改代码后沿用授权，不自动重跑。

## 完整受控流程

1. run-offline 校验授权、FD摘要、精确收据及拥有session、所有入口/fixture和原分母源SHA。用wx创建evidence-r8/run-owner.json，禁止覆盖上一执行证据。
2. 创建独有 tests/fd005/r8/.tmp-<session>-<UUID>。所有HOME/USERPROFILE/CODEX_HOME/XDG配置、TEMP、Go cache/modcache、程序、任务文件、Git本地仓库和bare remote都在该目录。仅证据写到tests/fd005/evidence-r8。
3. 本地Go分别临时编译fake-native.go（75秒）、实际Go launcher（45秒）和git-guard.go（45秒）。GOTOOLCHAIN=local、GOPROXY/GOSUMDB=off、GOENV=off、CGO=0、buildvcs=false；不获取工具链或依赖。所有二进制只在独有临时目录，不保留发布物。
4. git-guard嵌入该临时根和已安装Git绝对路径；仅允许受控cwd，隔离HOME/system/global配置，固定合成身份，关闭hooks/签名/credential helper，GIT_ALLOW_PROTOCOL=file。实际Git仅处理本地仓库与本地bare remote。
5. Node（180秒）使用register-loader，在内存用已安装TypeScript.transpileModule转译实际API生产.ts。相对.js入口仅映射到同一生产src内的.ts；禁止加载Worker index.ts主循环。不输出实现正文、源码图或编译发布目录，不替换生产适配器/ApiRuntime/SDK。
6. 使用已安装Agents0.3.9/OpenAI6.49.0，导入前禁tracing及模型/工具logging。内存fetch提供公开Responses/SSE；只接收专用.invalid URL，socket/DNS/TLS调用被拒绝，不打开socket。不读取实际凭据或用户会话。
7. Node执行公开core、假native/launcher、九角色实际API、实际SDK、AI guidance、实际CP内存传输、实际AiwAdapter+固定假Node插件以及公共Workspace本地Git黑盒。假模型/CP/插件/native均显著合成限定；不证明真实模型、服务、Python/Go Worker或Codex禁存平台。
8. 保存JSONL原始事件/调用元数据、版本与生产模块摘要、执行记录和V8覆盖率。Node非零退出也保存场景真实结果，未到目标断言者不得写通过。REPORT-manifest只覆盖报告结构，完整规划manifest/线上审批消费仍partial。
9. 子进程超时只终止已拥有PID树/组；Windows taskkill helper最多5秒，随后最多5秒确认close。未确认则resourceUncertain、断开拥有的pipe、保留临时根，不扩大kill。清理前realpath+owner校验，递归删除仅该专属根；工作区资源未终态也保留。

预计60–180秒；批准的最长总时限375秒，覆盖三个编译上限、Node上限、5秒终止helper、5秒close观察及证据/清理余量。主命令不会安装、联网、调用真Codex/模型/CP/GitHub、修改产品、构建发布包或跑lint/typecheck。输出每个子命令最多8MiB，单attempt证据4MiB；测试证据不删除，临时Go缓存及合成任务文件在安全清理后删除。

## 结论边界

原222个适用行为不能因本轮离线范围而移出分母。真实端点、真实Codex平台全通道禁存、跨语言Worker/固定Python SDK、升级/回滚/真实drain与租约发布竞争缺少授权或部署证据，保持uncovered/blocked。分支覆盖只报告实际被仪器观察的公共core/内存转译API JavaScript，不冒称全FD或SDK部署分支覆盖。
