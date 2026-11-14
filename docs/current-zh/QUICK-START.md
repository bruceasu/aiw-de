# 快速开始：本机 Plane、Worker 与私人秘书

需要 JDK 25、Maven 3.9+、Python 3.11+。所选 Python 环境须装好 [Worker 依赖](../../workers/process-python/requirements.txt)；使用私人秘书时还需 [秘书依赖](../../personal-secretary/requirements.txt)。先用文字任务体验即可，无须 Git 仓库、工作区或 Codex CLI。

## 1. 启动本机 Plane

在项目根目录创建 `data` 目录，并编辑根目录 `.env`：

```dotenv
AIW_DB_URL=jdbc:sqlite:./data/aiw.db
AIW_BIND_ADDRESS=127.0.0.1
AIW_WEB_CONSOLE_USERNAME=admin
AIW_WEB_CONSOLE_PASSWORD=替换为你自己的管理员密码
```

数据库目录须先存在。默认启用 `sqlite` profile，其内置 URL 是 `jdbc:sqlite:d:/temp/data/aiwde.db`；请像上例一样显式设置 `AIW_DB_URL`，避免 Windows 默认路径不适用于当前设备。切换 PostgreSQL 还需同时调整 profile、驱动和数据库模式，不能只改 URL。管理员密码用于 Plane 登录和本机秘书关联的管理员 Owner，秘书不会再次要求输入该密码。

Windows PowerShell（项目根目录）：

```powershell
New-Item -ItemType Directory -Force data | Out-Null
.\scripts\startup.bat
```

Linux / macOS Bash（项目根目录）：

```bash
mkdir -p data
bash scripts/startup.sh
```

两种启动脚本都读取项目根目录 `.env`。若直接用 `mvn spring-boot:run`，须自行在该进程环境中设置同样的变量；Maven 不负责读取此文件。启动后打开 `http://127.0.0.1:8080/ui`。首次启动会执行数据库迁移；升级现有数据库前先保留备份。

## 2. 配置模型并开始工作

打开“Agent 工厂”，选择模型连接、实际模型和执行方式。API Key 可从对应 Python 进程的 `OPENAI_API_KEY` 或 `DEEPSEEK_API_KEY` 环境变量读取，无须填到网页；兼容服务按设置页选择变量名。模型可从目录选择，也可手动输入。ChatGPT 连接使用现有登录入口；Codex app-server 是备选 Runtime。

工厂按任务需要启动本机 Worker。任务失败时先查看原因和已有结果，再决定重做或修复。远程 Worker 自行管理其设备上的模型连接，不能假定能读取 Plane 本机的凭据文件。

- 简单请求：进入“简单任务”，填写目标并明确开始。
- 模糊需求：进入“许愿池”，用文字或可选附件与专业成员澄清，再保存需求快照、生成工作流草稿。
- 多步骤工作：进入“工作流”，核对任务、LLM 判断和人工问题节点后再开始。保存草稿不会开始业务工作。
- “Workers”查看实际任务与过程；“待处理”处理正式问题和授权。停止领取新任务会保留当前执行，标记离线不等于结束远程进程。

## 3. 私人秘书可以独立运行

在另一个终端使用已准备好秘书依赖的 Python：

```sh
python personal-secretary/main.py
```

默认打开 `http://127.0.0.1:8766`，私库位于用户目录 `.aiw-personal`。在设置中选择模型后即可独立对话，不需要先连接 Plane。秘书读取的是 `personal-secretary/.env`，不是 Plane 根目录 `.env`；直接运行和现有 `start.ps1` / `start.sh` 都遵循这一点。

选择连接本地 Plane 时，使用其 loopback 地址，秘书自动登记到本机配置的管理员 Owner，不做单独 Owner 登录。只发送明确选择并确认的需求或任务，不会上传整段私人历史。

暂停自动处理仍可对话和查看原任务状态；暂停本次执行、取消后续操作是另一组控制。快捷操作只填输入框，由你决定发送。会话日志支持标题／摘要筛选，回复旁可以查看真实运行；切换会话保留输入，刷新网页不会保留未发送草稿。

## 4. 不同设备与远程访问

本机模式保持绑定 `127.0.0.1`。需要远程访问时，先按 [远程 Agent mTLS 部署指南](../features/archive/FD-072/AGENT_MTLS_DEPLOYMENT.md) 配置 HTTPS、服务端信任和客户端证书；其中有 Windows PowerShell、Linux / macOS 的 CA、签发、文件权限和启动指引。

远程秘书在 Plane 的“Agent 管理”中取得专属配对邀请，由管理员确认身份并启用新进程；选择 REMOTE，不把远程地址当成本机自动信任连接。远程普通 Worker 使用管理员创建的专属 Worker 凭据，配置见 [Python Worker](../../workers/process-python/README.md)。每台设备自行配置模型，凭据路径不是跨设备共享凭据。

这些命令是操作说明；本轮仅完成编译与静态审查，没有启动服务、登录模型、联网调用或执行部署验收。
