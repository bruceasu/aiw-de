# FD-005 Planner 测试授权，第二轮（r8）

<!-- aiw-data: FD-005-test-authorization-r8.json -->

## 审核结论

批准以下一条命令执行一次。依据 fd-workflow Auto 3a，由 Planner 审查可检查、离线且仅影响指定测试目录的执行代码；本授权不沿用首轮，也不授权改码后的重跑。

- Planner：fd005-planner-20261004-cf390565；决策时间：2026-10-04T23:22:30+00:00。
- 实施事件：FD-005-000009-implementation-ready；FD revision：9。
- FD SHA-256：6d51664531db4418fe20c5f05dbd0ddf797ce64f8689e48d096221970330627b。
- Tester session：fd005-tester-20261005-7865d383。
- 工作目录：D:/03_projects/AI-tools/aiw-dev。

```text
node tests/fd005/r8/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r8.json
```

## 范围与审查证据

授权在首次执行前更新：已复核 Git guard 的 Go ldflags 将两个变量由 `-X=<引用值>` 改为 `-X <引用值>`，保证 Windows Git 路径的空格留在完整变量值内。新 runner SHA-256 为 aedf7391c0d21e33670138e2ce4e9214184fb355101657e6288ca1eb635204c8，其余 24 项摘要未变；旧摘要绑定从未执行，本文件只授权最终版本。

已审查完整入口、内存 TypeScript 加载器、SDK Responses/SSE fixture、控制平面内存响应、固定假插件、本地 Git guard，以及复用首轮文件的全部差异；25 个调用文件、原场景分母源、公开示例和已安装 SDK/编译器 manifest 的摘要与提案一致，当前 FD 与领取收据匹配。逐文件 SHA-256 保存于同名 JSON。

原 222 个适用行为继续保留，总计 285 个适用、261 个准备场景。REPORT-manifest 仅部分准备，未测行为不可按准备状态计为通过。真实跨语言 Worker、真实 Codex 平台全部会话禁存、发布竞态、升级回滚及运维场景仍需独立证据。

## 时长与文件副作用

预计 60–180 秒，总上限 375 秒。三个临时 Go 编译的上限分别为 75、45、45 秒，Node 为 180 秒；已拥有 PID 树终止辅助进程与 close 观察各最多 5 秒。无自动重试。

仅写入 tests/fd005/r8/.tmp-fd005-tester-20261005-7865d383-<UUID>/ 和 tests/fd005/evidence-r8/。所有 HOME、SDK home、临时目录、Go cache、合成工作区、本地 Git 仓库及 bare remote 均在该专属临时根内；证据用 wx 所有权文件拒绝覆盖。删除前核对 realpath 与 owner；无法确认资源终态时保留目录。临时编译程序不作为发布产物保存。

## 风险判断与边界

使用已安装 Agents 0.3.9、OpenAI 6.49.0 和 TypeScript，生产 API 模块只在内存转译，拒绝启动 Worker 主循环。模型与控制平面响应均来自固定 .invalid 地址的内存 fetch，socket、DNS、TLS 调用被拒绝；导入前关闭 SDK tracing 和模型、工具正文日志。Go 自动工具链及代理下载关闭。

已确认 PATH 中 Git 仅解析到 C:/Program Files/Git/cmd/git.exe。测试专用 guard 限制其 cwd 为专属根，隔离配置与 HOME，固定合成身份，禁用 hooks、签名和 credential helper，仅允许 file 协议。插件、native 和控制平面输入均为合成 fixture，不构成真实服务或部署认证。

无依赖安装、网络、真实模型/CP/Codex/GitHub、用户凭据、发布构建或产品修改。按已审查边界属于 Planner 可批准的低风险执行，human_approval 为 not required。测试代码、FD 版次/摘要、会话或命令变化时，本记录失效，须重新审查；原始失败记录和未测项必须如实保留。
