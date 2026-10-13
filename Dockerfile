# syntax=docker/dockerfile:1
ARG NODE_IMAGE=node:24-bookworm-slim
ARG JAVA_IMAGE=maven:3-eclipse-temurin-25-noble

FROM ${NODE_IMAGE} AS node-runtime

# Shared tool environment. Application artifacts are mounted at runtime.
FROM ${JAVA_IMAGE} AS tools
ARG CODEX_VERSION=latest
COPY --from=node-runtime /usr/local/ /usr/local/
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       bash ca-certificates git libstdc++6 openssh-client procps ripgrep tini \
    && rm -rf /var/lib/apt/lists/* \
    && npm install --global --include=optional --no-audit --no-fund \
       "@openai/codex@${CODEX_VERSION}" \
    && npm cache clean --force

FROM tools AS runtime
# 注意: 这里创建了 aiw 用户和组，并设置了相关目录的权限，确保容器以非 root 用户运行。
# groupid and userid are set to 10001 for the aiw user and group.
RUN groupadd --gid 10001 aiw \
    && useradd --uid 10001 --gid aiw --create-home --shell /bin/bash aiw \
    && mkdir -p /opt/aiw/data /opt/aiw/worker /workspace /home/aiw/.codex /home/aiw/.m2 \
    && chown -R aiw:aiw /opt/aiw /workspace /home/aiw

WORKDIR /opt/aiw

ENV AIW_BIND_ADDRESS=0.0.0.0 \
    AIW_PORT=8080 \
    AIW_DB_URL=jdbc:sqlite:/opt/aiw/data/aidev.db \
    SPRING_PROFILES_ACTIVE=sqlite \
    MAVEN_CONFIG=/home/aiw/.m2

USER aiw
EXPOSE 8080
VOLUME ["/opt/aiw/data"]
ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/opt/aiw/app.jar"]
