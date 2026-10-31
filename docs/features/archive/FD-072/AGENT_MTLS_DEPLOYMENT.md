# FD-072 远程 Agent mTLS 部署

远程 Agent 档案必须使用双向 TLS。Control Plane 验证 Agent 客户端证书的受信 CA 链和已批准的 SHA-256 指纹；Agent 验证 Control Plane 服务端证书。TLS 证书签发、私钥保管和 CA 轮换由部署方负责，Control Plane 不保存 CA 私钥。

## Control Plane

启用 Spring `agent-mtls` 配置，并为 HTTPS 服务端配置 PKCS#12 密钥库、只信任 Agent 客户端证书签发机构的 truststore，以及各自的密码：

| 环境变量 | 用途 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=agent-mtls` | 启用 HTTPS 与 Agent 客户端证书验证配置 |
| `AIW_AGENT_BIND_ADDRESS` | 监听地址，默认 `0.0.0.0` |
| `AIW_AGENT_HTTPS_PORT` | HTTPS 端口，默认 `8443` |
| `AIW_TLS_KEYSTORE` | 服务端证书、私钥及证书链的 PKCS#12 文件 |
| `AIW_TLS_KEYSTORE_PASSWORD` | 服务端密钥库密码 |
| `AIW_TLS_KEYSTORE_TYPE` | 密钥库类型，默认 `PKCS12` |
| `AIW_AGENT_TRUSTSTORE` | 信任 Agent 客户端证书发行 CA 的 truststore |
| `AIW_AGENT_TRUSTSTORE_PASSWORD` | 客户端证书 truststore 密码 |
| `AIW_AGENT_TRUSTSTORE_TYPE` | truststore 类型，默认 `PKCS12` |
| `AIW_AGENT_LOCAL_PROFILE_ENABLED` | 是否在此监听器接受 LOCAL 档案，默认 `false`；公开或反代的远程监听器应保持禁用 |

Control Plane URL 的主机名必须与服务端证书 SAN 一致。`client-auth: want` 允许普通浏览器访问 TLS 页面；Agent 的远程配对、会话和 API 仍由 HTTP 边界强制要求受信客户端证书及专属凭据。不要把客户端证书作为未验证的转发请求头传给应用。

当前配置支持 Control Plane 自行终止 TLS，或由四层设备透传 TLS。若反向代理在应用前终止 TLS，必须让 Spring 请求实际取得已验证的客户端 X.509 证书链；当前实现不信任代理传入的证书转发头。此 `agent-mtls` 监听器默认关闭 LOCAL 档案；不要为公网监听器重新开启。LOCAL Agent API 仅可通过直接 loopback 请求使用，不得经公网反向代理发布。代码会拒绝常见转发头，但该检查不能替代反向代理的网络隔离规则。

## Agent

每个远程 Agent 使用独立的 PEM 客户端证书和私钥。启动或配对前配置：

| 环境变量 | 用途 |
| --- | --- |
| `AIW_AGENT_CLIENT_CERT` | Agent 客户端 PEM 证书及必要的中间证书链 |
| `AIW_AGENT_CLIENT_KEY` | 对应的 PEM 私钥 |
| `AIW_AGENT_SERVER_CA` | 可选的 Control Plane 私有 CA PEM 文件；省略时使用系统信任库 |

在 Agent 设置中选远程档案，并填入 `https://` Control Plane 来源。客户端拒绝 HTTP 降级；首次配对也必须同时验证 Control Plane 服务端证书和出示 Agent 客户端证书。私有密钥应由操作系统账户保护，不要写入仓库、配对码或日志。

本机档案只接受显式 loopback HTTP，不应配置上述证书路径。非远程监听器也会拒绝带转发头的 LOCAL 请求；若部署反代，应让 Agent 通过 loopback 直连本机监听器。远程和本机是两种独立连接档案，重新配对不会迁移旧凭据或旧 Grant。

## 客户端证书轮换

1. 由部署方为同一 Agent 签发新客户端证书，并安全地安装证书及私钥。
2. 更新 Agent 的证书路径并重启 Agent，使 HTTPS 客户端重新加载证书。
3. Agent 使用新证书请求轮换；Control Plane 只接受其 truststore 信任的证书，并记录新指纹为待审批状态。
4. 人类在 `/ui/agents` 核对 Agent ID 和 SHA-256 指纹后明确批准或拒绝。
5. 批准会使旧进程会话失效。Agent 用新证书重新建立会话；人类再明确启用该 Agent。

证书轮换不会增加、复制或改变 Grant。拒绝、过期或 CA 不受信的证书不能执行轮换。若客户端私钥遗失或怀疑泄露，应撤销 Agent 凭据并重新配对，而不是继续信任旧凭据。
