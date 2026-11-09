# 远程 Agent mTLS 部署指南

本指南从证书准备到服务启动，说明如何为远程 Agent 配置双向 TLS。示例适用于 Windows PowerShell，以及 Linux、macOS 的 Bash；OpenSSL 命令相同。生产环境优先使用组织的 CA/PKI。文中的自建 CA 只适合受控的私有环境或验证部署。

Control Plane 会验证 Agent 客户端证书的 CA 链和已批准的 SHA-256 指纹；Agent 会验证 Control Plane 服务端证书。证书签发、私钥保护和 CA 生命周期由部署方负责。Control Plane 不保存 CA 私钥。

## 1. 准备工具和工作目录

安装组织批准的 OpenSSL 3.x 和 JDK（需要 `keytool`），并确认命令可用：

~~~text
openssl version
keytool -help
~~~

证书中的服务端 SAN 必须包含 Agent 实际访问的 DNS 名称或 IP。下面假设地址是 `plane.example.com`，请在生成前替换成自己的真实地址。若使用 IP 访问，SAN 也必须包含该 IP。

### Windows PowerShell

用有权管理证书文件的账户打开 PowerShell，创建独立目录：

~~~powershell
New-Item -ItemType Directory -Force C:\aiw-mtls | Out-Null
Set-Location C:\aiw-mtls
~~~

使用 Windows 原生 `openssl.exe`。此工作目录包含 CA 签发私钥，只应由 PKI/证书管理员访问；不要把它作为服务运行目录，也不要把 CA 私钥放进项目仓库或同步目录。后续只把运行所需的叶证书、PKCS#12 文件和 truststore 复制到服务专用密钥目录。

### Linux 或 macOS

在 Bash 中创建目录并限制新文件权限：

~~~bash
umask 077
mkdir -p "$HOME/aiw-mtls"
cd "$HOME/aiw-mtls"
~~~

保持 `umask 077`。此工作目录包含 CA 签发私钥，只应由 PKI/证书管理员访问；不要把它作为服务运行目录。后续只把运行所需的叶证书、PKCS#12 文件和 truststore 复制到服务专用密钥目录。

## 2. 创建证书扩展配置

在工作目录中新建 `plane-server.ext`。将示例 DNS/IP 替换成实际对外地址；没有固定 IP 时删除 `IP.1`：

~~~ini
[server_cert]
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @server_names
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid,issuer

[server_names]
DNS.1 = plane.example.com
IP.1 = 203.0.113.20
~~~

再新建 `agent-client.ext`：

~~~ini
[agent_cert]
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature
extendedKeyUsage = clientAuth
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid,issuer
~~~

两份配置文件可用任意纯文本编辑器保存为 UTF-8/ASCII。不要把真实 CA 私钥或服务私钥写进配置文件。

## 3. 生成私有 CA 和叶证书

建议为 Plane 服务端证书和 Agent 客户端证书使用**不同的 CA**。这样 Agent 只信任服务端 CA，Plane 的 truststore 只信任 Agent CA。以下命令在 PowerShell 和 Bash 中相同；CA 私钥会提示输入加密口令。生产有效期应服从组织 PKI 政策，示例中的年限仅供私有环境参考。

### 3.1 生成两个 CA

~~~sh
openssl genpkey -algorithm RSA -aes-256-cbc -pkeyopt rsa_keygen_bits:4096 -out plane-ca.key
openssl req -x509 -new -key plane-ca.key -sha256 -days 3650 -subj "/CN=AIW Plane Server CA" -addext "basicConstraints=critical,CA:TRUE,pathlen:0" -addext "keyUsage=critical,keyCertSign,cRLSign" -addext "subjectKeyIdentifier=hash" -out plane-ca.crt

openssl genpkey -algorithm RSA -aes-256-cbc -pkeyopt rsa_keygen_bits:4096 -out agent-ca.key
openssl req -x509 -new -key agent-ca.key -sha256 -days 3650 -subj "/CN=AIW Agent Client CA" -addext "basicConstraints=critical,CA:TRUE,pathlen:0" -addext "keyUsage=critical,keyCertSign,cRLSign" -addext "subjectKeyIdentifier=hash" -out agent-ca.crt
~~~

`plane-ca.key` 和 `agent-ca.key` 是签发用 CA 私钥，离线保存并备份；**不要**复制到 Plane 或 Secretary 主机。实际部署若使用组织 PKI，可跳过本节，改为让 PKI 分别签发服务端和 Agent 客户端 CSR，并取得对应 CA 证书。

### 3.2 签发 Plane HTTPS 服务端证书

服务端私钥先以 PEM 生成，稍后会打包进有口令保护的 PKCS#12 文件：

~~~sh
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out plane.key
openssl req -new -key plane.key -subj "/CN=plane.example.com" -out plane.csr
openssl x509 -req -in plane.csr -CA plane-ca.crt -CAkey plane-ca.key -CAcreateserial -out plane.crt -days 397 -sha256 -extfile plane-server.ext -extensions server_cert
~~~

如果使用组织 CA，把 `plane.csr` 和所需的 SAN 提交给 PKI，要求返回包含 `serverAuth` 用途和完整中间链的 PEM 证书。证书名称必须匹配 Secretary 使用的 Control Plane URL。

### 3.3 为每个 Agent 单独签发客户端证书

每个 Agent 使用独立私钥和证书。这里以 `secretary-01` 为例：

~~~sh
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out secretary-01.key
openssl req -new -key secretary-01.key -subj "/CN=secretary-01" -out secretary-01.csr
openssl x509 -req -in secretary-01.csr -CA agent-ca.crt -CAkey agent-ca.key -CAcreateserial -out secretary-01.crt -days 397 -sha256 -extfile agent-client.ext -extensions agent_cert
~~~

Secretary 当前通过 Python TLS 接口读取客户端私钥文件，示例生成的 Agent PEM 私钥没有额外口令；必须用 Windows ACL 或 Unix 文件权限保护它，并只提供给对应 Secretary 进程。不要在多个 Agent 间复制同一客户端私钥。

使用组织 CA 时，将 CSR 交给 PKI，要求签发包含 `clientAuth` 用途的证书，并提供完整中间证书链。Agent 连接时需提交叶证书和所需的中间链；Plane truststore 应信任对应 CA。

## 4. 创建 Plane 的 PKCS#12 keystore 和 truststore

将服务端证书和私钥打包成 Spring HTTPS 使用的 keystore。OpenSSL 会交互式询问 PKCS#12 口令。下面的自建 CA 直接签发叶证书，因此这里不把根 CA 证书发送给客户端：

~~~sh
openssl pkcs12 -export -name aiw-control-plane -inkey plane.key -in plane.crt -out plane-keystore.p12
~~~

如果服务端证书来自组织 CA，只有存在中间 CA 时才用 `-certfile` 附加中间证书链；不要附加根 CA。然后将**Agent 客户端 CA 公钥证书**导入 Plane 的 PKCS#12 truststore；`keytool` 会提示设置 truststore 口令：

~~~sh
keytool -importcert -noprompt -alias aiw-agent-ca -file agent-ca.crt -keystore agent-truststore.p12 -storetype PKCS12
~~~

这里的 `agent-ca.crt` 应替换为组织为 Agent 证书签发的 CA 证书。truststore 不应导入 Agent 私钥，也不应导入 Plane 服务端 CA 来代替 Agent CA。

将 `plane-keystore.p12`、`agent-truststore.p12` 安装到 Plane 服务账户可读的受保护目录；只授予 Plane 服务账户读取这些文件的权限。CA 私钥不需要部署到应用主机。妥善保管两个 store 的口令，并通过服务管理器或秘密管理设施注入环境；不要把口令写在命令参数、版本库或日志中。

Windows 可在管理员 PowerShell 中为只包含运行时文件的目录设置 ACL。将账户替换为实际运行 Plane 服务的账户：

~~~powershell
$secretDir = "C:\ProgramData\AIW\plane-secrets"
$runtimeAccount = "NT SERVICE\AiDevManager" # 替换为实际服务账户
New-Item -ItemType Directory -Force $secretDir | Out-Null
icacls $secretDir /inheritance:r
icacls $secretDir /grant:r "*S-1-5-18:(OI)(CI)F" "*S-1-5-32-544:(OI)(CI)F" "$($runtimeAccount):(OI)(CI)R"
~~~

将 keystore 和 truststore 复制到该目录后，设置环境变量路径为该目录下的文件。Unix 系统可将运行时文件放在独立目录，并用 `chown` 和 `chmod 600` 只授予相应服务账户读取权限。不要将 `plane-ca.key` 或 `agent-ca.key` 复制到此目录。

## 5. 配置并启动 Control Plane

启用 Spring `agent-mtls` profile。环境变量中的 keystore 和 truststore 密码必须分别匹配刚才设置的口令。

### Windows PowerShell 当前进程示例

~~~powershell
$env:SPRING_PROFILES_ACTIVE = "agent-mtls"
$env:AIW_AGENT_BIND_ADDRESS = "0.0.0.0"
$env:AIW_AGENT_HTTPS_PORT = "8443"
$env:AIW_TLS_KEYSTORE = (Resolve-Path .\plane-keystore.p12).Path
$env:AIW_TLS_KEYSTORE_TYPE = "PKCS12"
$env:AIW_AGENT_TRUSTSTORE = (Resolve-Path .\agent-truststore.p12).Path
$env:AIW_AGENT_TRUSTSTORE_TYPE = "PKCS12"
$env:AIW_AGENT_LOCAL_PROFILE_ENABLED = "false"
~~~

### Linux 或 macOS 当前进程示例

~~~bash
export SPRING_PROFILES_ACTIVE=agent-mtls
export AIW_AGENT_BIND_ADDRESS=0.0.0.0
export AIW_AGENT_HTTPS_PORT=8443
export AIW_TLS_KEYSTORE="$PWD/plane-keystore.p12"
export AIW_TLS_KEYSTORE_TYPE=PKCS12
export AIW_AGENT_TRUSTSTORE="$PWD/agent-truststore.p12"
export AIW_AGENT_TRUSTSTORE_TYPE=PKCS12
export AIW_AGENT_LOCAL_PROFILE_ENABLED=false
~~~

还需从操作系统服务配置或秘密管理设施设置 `AIW_TLS_KEYSTORE_PASSWORD` 和 `AIW_AGENT_TRUSTSTORE_PASSWORD`。上面的 PowerShell 设置只对当前 PowerShell 进程及其子进程生效；作为 Windows 服务运行时，应在服务账户对应的服务配置中设置，而不是依赖交互式终端。

若仅在当前终端启动服务，可交互式读取口令，避免把明文写入命令历史：

~~~powershell
function Set-MaskedSecret([string]$name, [string]$prompt) {
  $secret = Read-Host $prompt -AsSecureString
  $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
  try {
    [Environment]::SetEnvironmentVariable($name, [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer), "Process")
  } finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
  }
}
Set-MaskedSecret "AIW_TLS_KEYSTORE_PASSWORD" "Plane keystore password"
Set-MaskedSecret "AIW_AGENT_TRUSTSTORE_PASSWORD" "Agent truststore password"
~~~

~~~bash
read -r -s -p "Plane keystore password: " AIW_TLS_KEYSTORE_PASSWORD; export AIW_TLS_KEYSTORE_PASSWORD; printf '\n'
read -r -s -p "Agent truststore password: " AIW_AGENT_TRUSTSTORE_PASSWORD; export AIW_AGENT_TRUSTSTORE_PASSWORD; printf '\n'
~~~

这些交互式环境变量只对当前进程及其子进程有效。常驻服务应使用服务管理器或秘密管理设施配置。

相关配置项：

| 环境变量 | 用途 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=agent-mtls` | 启用 HTTPS 和 Agent 客户端证书校验 |
| `AIW_AGENT_BIND_ADDRESS` | 监听地址，默认 `0.0.0.0` |
| `AIW_AGENT_HTTPS_PORT` | HTTPS 端口，默认 `8443` |
| `AIW_TLS_KEYSTORE` / `AIW_TLS_KEYSTORE_PASSWORD` | 服务端证书、私钥及证书链的 PKCS#12 文件和口令 |
| `AIW_TLS_KEYSTORE_TYPE` | keystore 类型，默认 `PKCS12` |
| `AIW_AGENT_TRUSTSTORE` / `AIW_AGENT_TRUSTSTORE_PASSWORD` | 只信任 Agent 客户端 CA 的 truststore 和口令 |
| `AIW_AGENT_TRUSTSTORE_TYPE` | truststore 类型，默认 `PKCS12` |
| `AIW_AGENT_LOCAL_PROFILE_ENABLED` | 此监听器是否接受 LOCAL 档案；公开远程监听器应保持 `false` |

Agent API 必须在 Spring 实际收到并验证客户端 X.509 证书的 HTTPS 连接上运行。可由 Control Plane 直接终止 TLS，或由四层设备透传 TLS。不要在只终止 TLS 的 HTTP 反向代理后部署此监听器：当前应用不信任由代理转发的证书头。`client-auth: want` 允许普通浏览器访问 TLS 页面，但远程 Agent API 仍强制验证客户端证书和 Agent 专属凭据。

## 6. 配置 Secretary Agent

把 Agent 证书、私钥和 Plane 服务端 CA 公钥证书安装到运行 Secretary 的主机。将私有 CA 的证书传给 Agent；不要传输 `plane-ca.key` 或 `agent-ca.key`。

### Windows PowerShell 当前进程示例

~~~powershell
$env:AIW_AGENT_CLIENT_CERT = (Resolve-Path .\secretary-01.crt).Path
$env:AIW_AGENT_CLIENT_KEY = (Resolve-Path .\secretary-01.key).Path
$env:AIW_AGENT_SERVER_CA = (Resolve-Path .\plane-ca.crt).Path
~~~

### Linux 或 macOS 当前进程示例

~~~bash
export AIW_AGENT_CLIENT_CERT="$PWD/secretary-01.crt"
export AIW_AGENT_CLIENT_KEY="$PWD/secretary-01.key"
export AIW_AGENT_SERVER_CA="$PWD/plane-ca.crt"
chmod 600 secretary-01.key
~~~

如果 Plane 服务端证书由系统默认信任的公共 CA 签发，可省略 `AIW_AGENT_SERVER_CA`。使用私有 CA 时应配置该变量，且 CA 文件必须是服务端签发 CA 的 PEM 公钥证书。以上环境变量要由启动 Secretary 的实际进程继承；持久部署时应配置在相应服务管理器中。

Secretary 设置中选择远程 Control Plane，并填写与服务端证书 SAN 相匹配的 `https://` URL。客户端会拒绝 HTTP 降级；首次配对还需要服务端证书校验和 Agent 客户端证书。之后在 Control Plane 的 `/ui/agents` 核对 Agent ID 与客户端证书 SHA-256 指纹并批准，再启用当前 Agent 进程。

## 7. 验证证书与 TLS

先在证书生成目录验证证书用途、签发 CA 和服务端名称：

~~~sh
openssl verify -CAfile plane-ca.crt -purpose sslserver -verify_hostname plane.example.com plane.crt
openssl verify -CAfile agent-ca.crt -purpose sslclient secretary-01.crt
openssl pkcs12 -in plane-keystore.p12 -info -noout
keytool -list -keystore agent-truststore.p12 -storetype PKCS12
~~~

证书校验应返回 `OK`；PKCS#12 检查会提示输入口令。部署完成后，可以从 Agent 网络运行以下握手检查：

~~~sh
openssl s_client -connect plane.example.com:8443 -servername plane.example.com -verify_hostname plane.example.com -CAfile plane-ca.crt -verify_return_error -cert secretary-01.crt -key secretary-01.key
~~~

检查输出中的服务端校验结果为 `Verify return code: 0 (ok)`，且握手成功。该命令只验证 TLS 层；没有提供 Agent 应用凭据时，后续 API 返回 HTTP 401 不代表 TLS 失败。按 `Ctrl+C` 结束等待中的连接。

## 8. 证书续期、轮换与故障处理

- **Plane 服务端叶证书续期：** 用相同 CA 为新的私钥/CSR 签发包含正确 SAN 和 `serverAuth` 的证书，重建 `plane-keystore.p12` 并重启服务。若 CA 未变化，Agent 的 CA 配置无需改变。
- **单个 Agent 客户端证书轮换：** 为该 Agent 生成新的独立密钥和客户端证书，更新 `AIW_AGENT_CLIENT_CERT`、`AIW_AGENT_CLIENT_KEY` 并重启 Secretary；在 Control Plane 发起证书轮换，人工核对新 SHA-256 指纹并批准。轮换后还需重新建立 Agent 会话并启用进程。
- **CA 轮换：** 先在 Plane truststore 中同时加入旧、新 Agent CA，再逐个为 Agent 签发并批准新证书；确认全部 Agent 已迁移后，再移除旧 CA。Plane 服务端 CA 轮换时，应先把新 CA 分发到 Agents，再切换服务端证书。
- **Agent 私钥泄露或遗失：** 撤销旧 Agent 凭据并重新配对/签发，不要继续信任旧私钥。若 CA 私钥泄露，应按组织 PKI 的事件响应流程更换 CA，并重新签发受影响证书。
- **握手失败：** 核对实际 URL 主机名与服务端 SAN、系统时间、证书有效期、各自方向的 CA 文件/truststore、keystore/truststore 口令、证书用途，以及反向代理是否透传了原始 TLS。

## 官方工具参考

- [OpenSSL req：生成私钥、CSR 和自签名证书](https://docs.openssl.org/3.5/man1/openssl-req/)
- [OpenSSL x509：签发和检查证书](https://docs.openssl.org/3.5/man1/openssl-x509/)
- [OpenSSL X.509 扩展配置](https://docs.openssl.org/3.3/man5/x509v3_config/)
- [OpenSSL pkcs12：创建和检查 PKCS#12 文件](https://docs.openssl.org/3.5/man1/openssl-pkcs12/)
- [Java keytool：导入 CA 证书和检查 truststore](https://docs.oracle.com/en/java/javase/25/docs/specs/man/keytool.html)
