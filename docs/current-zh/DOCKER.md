# Java 25、Node.js 24 与 Codex 容器

根目录 Dockerfile 只提供运行与工具环境：Java 25 JDK、Maven、Node.js 24、npm、系统 Codex CLI、Git、ripgrep 与 tini。镜像不复制项目源码、不编译 Java 或 Worker、不包含应用产物；外部编译完成后在运行时挂载。默认命令启动挂载的 Java 控制平面，Worker 可在另一个容器中单独运行。

Codex 默认使用 npm 的 `latest` 标签，可通过 `CODEX_VERSION` 构建参数固定版本。项目 Worker 当前契约使用 0.162.1，使用其他版本前需核对 Worker 版本检查。安装方式依据 [OpenAI Codex CLI 官方文档](https://developers.openai.com/codex/cli/)。基础镜像依据 [官方 Maven 镜像列表](https://github.com/docker-library/official-images/blob/master/library/maven)与 [官方 Node 镜像列表](https://github.com/docker-library/official-images/blob/master/library/node)。

项目 pom.xml 仍以 Java 21 为编译目标；本镜像使用 Java 25 运行，默认 JVM 命令包含 SQLite JDBC 的 native-access 参数。

## 构建环境镜像

在仓库根目录执行：

```sh
docker build -t ai-worker:java25-node24 .
```

构建仅下载基础镜像、系统包与系统 Codex CLI（包括其平台原生包），不执行 Maven package、npm ci 或 Worker build。镜像保留 Maven/npm 工具供需要时使用，但不会自动编译项目。Codex 全局安装保留 optional 平台包。

构建参数：`JAVA_IMAGE`（默认 `maven:3-eclipse-temurin-25-noble`）、`NODE_IMAGE`（默认 `node:24-bookworm-slim`）与 `CODEX_VERSION`（默认 `latest`）。主版本镜像标签和 latest 随上游更新；需要可重复发行时指定经验证的镜像 digest 和 Codex 版本。Docker 可能复用安装层缓存，重新构建不一定重新解析 latest。

Node 二进制从 Debian bookworm 镜像复制到 Ubuntu noble；替换镜像时保持架构与 glibc 兼容，不使用 Alpine/musl 替换其中单个阶段。

## 挂载外部 JAR 启动

先在容器外按项目现有方式生成可通过 `java -jar` 启动的 Spring Boot JAR。下面示例使用当前 pom.xml 对应文件名；若版本变化，替换 source 路径：

```sh
docker run --rm --name aiw-control \
  -p 127.0.0.1:8080:8080 \
  --mount "type=bind,source=$(pwd)/target/ai-dev-manager-10-9.0.0-SNAPSHOT.jar,target=/opt/aiw/app.jar,readonly" \
  -v aiw-data:/opt/aiw/data \
  ai-worker:java25-node24
```

挂载源必须是已经存在的 JAR 文件。`--mount` 在源不存在时直接报错，避免把缺失文件误建为目录。JAR 只读挂载；容器不负责生成或更新它。更换外部产物后停止并重新创建容器，不在运行中覆盖 JAR。镜像无需随每次应用发布重建。

打开 `http://127.0.0.1:8080/ui`。容器监听 0.0.0.0，示例只将端口发布到宿主机 localhost。SQLite 数据独立保存在命名卷中。沿用既有 Flyway 启动行为，使用已有数据库前按运维说明备份。

容器用户 UID/GID 为 10001，需要对挂载 JAR 的读权限和数据目录的写权限。仓库可独立挂载，例如 `-v /absolute/repository:/workspace/repository`；在控制台配置容器内路径。

## Codex 与外部 Worker 产物

覆盖默认命令即可使用工具环境，无需挂载 JAR：

```sh
docker run --rm -it -v aiw-codex:/home/aiw/.codex \
  ai-worker:java25-node24 codex
```

凭据仅在运行时提供，不放入镜像。Codex 卷保存可写状态，不代表全通道禁存。

TypeScript Worker 同样在容器外编译和准备依赖。挂载目录须包含 `dist/`、`node_modules/`、`package.json`、`profiles/`、`skills/` 与 `runtime-core/`，依赖须适配容器 Linux/CPU 架构。示例展示产物挂载和启动入口；角色与 Runtime 等配置从单独的 Worker 环境文件提供：

```sh
docker run --rm \
  --env-file /absolute/worker.env \
  --mount type=bind,source=/absolute/worker-release,target=/opt/aiw/worker,readonly \
  ai-worker:java25-node24 node /opt/aiw/worker/dist/index.js
```

按 [TypeScript Worker 指南](../../workers/process-typescript/README.md)配置角色、Runtime、认证、准入与工作区；需要执行仓库操作时另外挂载可写 Workspace。通过 Docker network 让 `AIW_CONTROL_PLANE_URL` 指向控制平面容器；Worker 内 localhost 不指向其他容器。两端仓库引用和路径映射须一致。

镜像未包含 Go/Python 工具链。不改变旧 AR 与新版 EXTERNAL_WORK 的兼容性，不绕过已有生产准入；FD-015 生产验证仍是独立范围。

## 验证状态

本次仅静态检查 Dockerfile 与挂载说明，未执行镜像构建、容器启动、项目编译、测试、依赖下载或模型调用。实际镜像、外部产物、挂载权限、认证和 Worker 生命周期尚未验证。
